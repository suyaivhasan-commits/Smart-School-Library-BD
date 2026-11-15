/**
 * Smart School Library BD — Supabase Edge Function Storage Gateway (Production Grade)
 * Function Name: b2-storage-gateway
 * Runtime: Supabase Edge Runtime (Deno + Web Standards)
 *
 * Core Capabilities & Strict Security:
 * 1. RS256 Firebase ID Token verification via Google's official JWKS.
 * 2. Distinct, structured storage paths for Textbooks (books/) vs Guides (guides/).
 * 3. Fail-closed authorization: Every PDF read request is verified against the
 *    registered Firestore document. No Super Admin bypass for path mismatches.
 * 4. Authenticated cover access with private B2 presigning, preventing unauthorized leaks.
 * 5. Strict subscription enforcement:
 *    - Classes 5–8: Single grade-level active subscription (sub_<uid>_c<class>_ALL).
 *    - Classes 9–10: Stream-isolated active subscription (sub_<uid>_c<class>_<STREAM>).
 *      No cross-stream or ALL fallback allowed for Classes 9–10.
 * 6. AWS SigV4 Presigned URLs for Private Backblaze B2 S3 API.
 * 7. Path traversal prevention (%2e, .., backslashes, malformed characters).
 */

interface GoogleJwk {
  kid: string;
  kty: string;
  alg: string;
  use: string;
  n: string;
  e: string;
}

interface VerifiedToken {
  uid: string;
  email?: string;
  payload: Record<string, unknown>;
}

interface SafePathInfo {
  valid: boolean;
  type: "PDF" | "COVER";
  category: "ORIGINAL_TEXTBOOKS" | "GUIDES_AND_NOTES";
  collection: "books" | "guides";
  classNumber: number;
  docId: string;
  cleanPath: string;
  reason?: string;
}

// In-memory cache for Google Public JWKS in Deno isolate
let cachedJwks: GoogleJwk[] | null = null;
let jwksExpiry = 0;

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization, x-client-info, apikey",
  "Access-Control-Max-Age": "86400",
};

// Default Configuration Constants
const DEFAULT_B2_ENDPOINT = "https://s3.us-east-005.backblazeb2.com";
const DEFAULT_B2_REGION = "us-east-005";
const DEFAULT_B2_BUCKET = "smart-school-library-files";
const DEFAULT_FIREBASE_PROJECT_ID = "smart-school-library-bd";
const DEFAULT_SUPER_ADMIN_UID = "yyskhmPvvfeNl572FnVhZgYAaQx2";

const GOOGLE_JWKS_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

Deno.serve(async (req: Request) => {
  // CORS Preflight
  if (req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: CORS_HEADERS });
  }

  const url = new URL(req.url);
  const path = url.pathname;

  try {
    // 1. Health Check Endpoint
    if (req.method === "GET" && (path.endsWith("/health") || path === "/health")) {
      return jsonResponse({
        status: "ok",
        service: "Smart School Library BD - Secure B2 Storage Gateway",
        runtime: "Supabase Edge Runtime (Deno)",
        classesSupported: [5, 6, 7, 8, 9, 10],
        categoriesSupported: ["ORIGINAL_TEXTBOOKS", "GUIDES_AND_NOTES"],
        timestamp: Date.now(),
      });
    }

    // 2. Upload Ticket Endpoint (Super Admin Only)
    if (req.method === "POST" && (path.endsWith("/upload-ticket") || path.includes("/upload-ticket"))) {
      return await handleUploadTicket(req);
    }

    // 3. Read Ticket Endpoint (Authorized Students & Super Admin)
    if (req.method === "POST" && (path.endsWith("/read-ticket") || path.includes("/read-ticket"))) {
      return await handleReadTicket(req);
    }

    // 4. Cover Ticket Endpoint
    if (req.method === "POST" && (path.endsWith("/cover-ticket") || path.includes("/cover-ticket"))) {
      return await handleCoverTicket(req);
    }

    // 5. Cover Proxy Endpoint (GET .../covers/*)
    if (req.method === "GET" && path.includes("/covers/")) {
      return await handleCoverProxy(req);
    }

    return jsonResponse(
      {
        error: "Endpoint not found",
        path,
        availableRoutes: [
          "POST /api/storage/upload-ticket (or /upload-ticket)",
          "POST /api/storage/read-ticket (or /read-ticket)",
          "POST /api/storage/cover-ticket (or /cover-ticket)",
          "GET /covers/*",
          "GET /api/health (or /health)",
        ],
      },
      404
    );
  } catch (err: unknown) {
    const error = err as Error;
    console.error("Gateway Exception:", error);
    return jsonResponse(
      { error: error.message || "Internal server error" },
      500
    );
  }
});

// ============================================================================
// 1. UPLOAD TICKET HANDLER (Super Admin Only, Category-Isolated Paths)
// ============================================================================
async function handleUploadTicket(req: Request): Promise<Response> {
  const token = await verifyFirebaseToken(req);
  const superAdminUid = Deno.env.get("SUPER_ADMIN_UID") || DEFAULT_SUPER_ADMIN_UID;

  if (!token || token.uid !== superAdminUid) {
    return jsonResponse(
      { error: "Unauthorized: Only the designated Super Admin may obtain upload tickets." },
      403
    );
  }

  const body = await req.json().catch(() => ({}));
  const { category, classNumber, itemId, fileType, fileName, contentType } = body;

  if (!category || !classNumber || !itemId || !fileType) {
    return jsonResponse(
      { error: "Missing required upload parameters (category, classNumber, itemId, fileType)" },
      400
    );
  }

  // Validate Category strictly
  const cleanCategory = String(category).trim();
  if (cleanCategory !== "ORIGINAL_TEXTBOOKS" && cleanCategory !== "GUIDES_AND_NOTES") {
    return jsonResponse({ error: "Invalid category. Allowed: ORIGINAL_TEXTBOOKS, GUIDES_AND_NOTES" }, 400);
  }

  // Validate Class Number: Strictly 5 to 10
  const clsNum = parseInt(String(classNumber), 10);
  if (isNaN(clsNum) || clsNum < 5 || clsNum > 10) {
    return jsonResponse(
      { error: "Invalid classNumber. Smart School Library BD supports Classes 5 to 10." },
      400
    );
  }

  // Validate Item ID
  const cleanId = String(itemId).trim();
  if (!/^[a-zA-Z0-9_-]{3,80}$/.test(cleanId)) {
    return jsonResponse({ error: "Invalid itemId format. Must be alphanumeric (3-80 chars)." }, 400);
  }

  // Validate File Type
  const cleanType = String(fileType).toUpperCase().trim();
  if (cleanType !== "COVER" && cleanType !== "PDF") {
    return jsonResponse({ error: "Invalid fileType. Must be 'COVER' or 'PDF'." }, 400);
  }

  // Validate Content Type and Construct Separated Storage Paths
  const cleanContentType = String(contentType || "").trim().toLowerCase();
  let storagePath = "";

  if (cleanCategory === "ORIGINAL_TEXTBOOKS") {
    if (cleanType === "COVER") {
      const validCoverMimes = ["image/jpeg", "image/jpg", "image/png", "image/webp"];
      if (cleanContentType && !validCoverMimes.includes(cleanContentType)) {
        return jsonResponse({ error: "Invalid cover contentType. Allowed: JPEG, PNG, WEBP." }, 400);
      }
      const ext = getExtension(fileName, cleanContentType, "jpg");
      storagePath = `book_covers/class_${clsNum}/${cleanId}/cover.${ext}`;
    } else {
      if (cleanContentType && cleanContentType !== "application/pdf") {
        return jsonResponse({ error: "Invalid PDF contentType. Must be application/pdf." }, 400);
      }
      storagePath = `books/class_${clsNum}/${cleanId}/original_book.pdf`;
    }
  } else {
    // GUIDES_AND_NOTES
    if (cleanType === "COVER") {
      const validCoverMimes = ["image/jpeg", "image/jpg", "image/png", "image/webp"];
      if (cleanContentType && !validCoverMimes.includes(cleanContentType)) {
        return jsonResponse({ error: "Invalid cover contentType. Allowed: JPEG, PNG, WEBP." }, 400);
      }
      const ext = getExtension(fileName, cleanContentType, "jpg");
      storagePath = `guide_covers/class_${clsNum}/${cleanId}/cover.${ext}`;
    } else {
      if (cleanContentType && cleanContentType !== "application/pdf") {
        return jsonResponse({ error: "Invalid PDF contentType. Must be application/pdf." }, 400);
      }
      storagePath = `guides/class_${clsNum}/${cleanId}/original_guide.pdf`;
    }
  }

  const uploadUrl = await generatePresignedUrl({
    method: "PUT",
    path: storagePath,
    contentType: cleanType === "COVER" ? (cleanContentType || "image/jpeg") : "application/pdf",
    expiresInSeconds: 900, // 15 minutes
  });

  return jsonResponse({
    uploadUrl,
    storagePath,
    category: cleanCategory,
    classNumber: clsNum,
    itemId: cleanId,
    expiresInSeconds: 900,
  });
}

// ============================================================================
// 2. READ TICKET HANDLER (Fail-Closed, Firestore Verified, Stream-Enforced)
// ============================================================================
async function handleReadTicket(req: Request): Promise<Response> {
  const token = await verifyFirebaseToken(req);
  if (!token) {
    return jsonResponse({ error: "Unauthorized: Valid Firebase login required" }, 401);
  }

  const body = await req.json().catch(() => ({}));
  const { storagePath, docId: clientDocId, collection: clientCollection } = body;

  const bucketName = Deno.env.get("B2_BUCKET") || DEFAULT_B2_BUCKET;
  const pathValidation = validateSafeStoragePath(storagePath, bucketName);

  if (!pathValidation.valid || pathValidation.type !== "PDF") {
    return jsonResponse(
      { error: `Invalid storage path: ${pathValidation.reason || "Malformed path"}` },
      400
    );
  }

  const { classNumber: pathClass, docId: pathDocId, collection: pathCollection, cleanPath } = pathValidation;

  // Verify client docId matches path docId if supplied
  if (clientDocId && String(clientDocId).trim() !== pathDocId) {
    return jsonResponse(
      { error: "Client-supplied docId does not match storage path document ID." },
      400
    );
  }

  const superAdminUid = Deno.env.get("SUPER_ADMIN_UID") || DEFAULT_SUPER_ADMIN_UID;
  const isSuperAdmin = token.uid === superAdminUid;
  const authHeader = req.headers.get("Authorization");

  // Determine primary and secondary Firestore collections
  const primaryCollection = (clientCollection === "guides" || pathCollection === "guides" || pathDocId.startsWith("guide"))
    ? "guides"
    : "books";
  const alternateCollection = primaryCollection === "books" ? "guides" : "books";

  // FAIL-CLOSED: Fetch document in Firestore using caller's auth token
  let firestoreDoc = await fetchFirestoreDoc(primaryCollection, pathDocId, authHeader);
  if (!firestoreDoc || !firestoreDoc.fields) {
    firestoreDoc = await fetchFirestoreDoc(alternateCollection, pathDocId, authHeader);
  }

  if (!firestoreDoc || !firestoreDoc.fields) {
    return jsonResponse(
      { error: "Fail-closed: Registered book/guide document not found in Firestore." },
      404
    );
  }

  const fields = firestoreDoc.fields;

  // 1. Strict Path Match Verification (NO Super Admin bypass)
  const registeredPdfPath = sanitizeStoragePath(
    fields.pdfStoragePath?.stringValue || fields.pdfUrl?.stringValue || "",
    bucketName
  );

  if (registeredPdfPath !== cleanPath) {
    return jsonResponse(
      { error: "Fail-closed: Requested storage path does not match registered Firestore record." },
      403
    );
  }

  // 2. Publication Status Verification
  // Missing or false publication strictly means UNPUBLISHED (fail-closed)
  const isPublished = fields.isPublished?.booleanValue === true;
  if (!isPublished && !isSuperAdmin) {
    return jsonResponse(
      { error: "Fail-closed: This content is not published." },
      403
    );
  }

  // 3. Premium & Subscription Entitlement Check
  const isPremium = fields.isPremium?.booleanValue === true;
  if (isPremium && !isSuperAdmin) {
    const docClass = parseInt(
      String(fields.classNumber?.integerValue || fields.classNumber?.stringValue || pathClass),
      10
    );
    const docStream = String(fields.stream?.stringValue || "ALL").toUpperCase().trim();

    const hasActiveSubscription = await verifyStudentSubscription({
      uid: token.uid,
      classNumber: docClass,
      stream: docStream,
      authHeader,
    });

    if (!hasActiveSubscription) {
      return jsonResponse(
        {
          error: "SUBSCRIPTION_REQUIRED",
          message: "সক্রিয় সাবস্ক্রিপশন প্রয়োজন। অনুগ্রহ করে সংশ্লিষ্ট শ্রেণির সাবস্ক্রিপশন সক্রিয় করুন।",
        },
        403
      );
    }
  }

  // Generate 30-minute Presigned GET URL
  const downloadUrl = await generatePresignedUrl({
    method: "GET",
    path: cleanPath,
    expiresInSeconds: 1800, // 30 minutes
  });

  return jsonResponse({
    downloadUrl,
    storagePath: cleanPath,
    expiresInSeconds: 1800,
  });
}

// ============================================================================
// 3. COVER TICKET HANDLER (Fail-Closed, Firestore Verified)
// ============================================================================
async function handleCoverTicket(req: Request): Promise<Response> {
  const token = await verifyFirebaseToken(req);
  if (!token) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  const body = await req.json().catch(() => ({}));
  const { storagePath } = body;

  const bucketName = Deno.env.get("B2_BUCKET") || DEFAULT_B2_BUCKET;
  const pathValidation = validateSafeStoragePath(storagePath, bucketName);

  if (!pathValidation.valid || pathValidation.type !== "COVER") {
    return jsonResponse({ error: "Invalid cover storage path" }, 400);
  }

  const { docId, collection, cleanPath } = pathValidation;
  const authHeader = req.headers.get("Authorization");

  // Verify document in Firestore
  let doc = await fetchFirestoreDoc(collection, docId, authHeader);
  if (!doc || !doc.fields) {
    const fallbackCollection = collection === "books" ? "guides" : "books";
    doc = await fetchFirestoreDoc(fallbackCollection, docId, authHeader);
  }

  if (!doc || !doc.fields) {
    return jsonResponse({ error: "Book/guide record not found in database." }, 404);
  }

  const registeredCover = sanitizeStoragePath(
    doc.fields.coverStoragePath?.stringValue || doc.fields.coverUrl?.stringValue || "",
    bucketName
  );

  // Exact path match required for all
  if (registeredCover !== cleanPath) {
    return jsonResponse({ error: "Cover path does not match database record." }, 403);
  }

  const downloadUrl = await generatePresignedUrl({
    method: "GET",
    path: cleanPath,
    expiresInSeconds: 3600, // 1 hour
  });

  return jsonResponse({
    downloadUrl,
    storagePath: cleanPath,
    expiresInSeconds: 3600,
  });
}

// ============================================================================
// 4. COVER PROXY (GET .../covers/* with Authenticated Firestore Check)
// ============================================================================
async function handleCoverProxy(req: Request): Promise<Response> {
  const url = new URL(req.url);
  const coversIndex = url.pathname.indexOf("/covers/");
  const rawPath = coversIndex !== -1 ? url.pathname.slice(coversIndex + 8) : "";

  const bucketName = Deno.env.get("B2_BUCKET") || DEFAULT_B2_BUCKET;
  const pathValidation = validateSafeStoragePath(rawPath, bucketName);

  if (!pathValidation.valid || pathValidation.type !== "COVER") {
    return new Response("Invalid cover path", { status: 400, headers: CORS_HEADERS });
  }

  const { docId, collection, cleanPath } = pathValidation;

  // Authenticate user token for cover request (do not depend on unauthenticated Firestore reads)
  const token = await verifyFirebaseToken(req);
  if (!token) {
    return new Response("Unauthorized: Authentication token required for cover image", {
      status: 401,
      headers: CORS_HEADERS,
    });
  }

  const authHeader = req.headers.get("Authorization");

  // Verify document in Firestore using caller's auth header
  let doc = await fetchFirestoreDoc(collection, docId, authHeader);
  if (!doc || !doc.fields) {
    const fallbackCollection = collection === "books" ? "guides" : "books";
    doc = await fetchFirestoreDoc(fallbackCollection, docId, authHeader);
  }

  if (!doc || !doc.fields) {
    return new Response("Cover image not found in database", { status: 404, headers: CORS_HEADERS });
  }

  // Publication check
  if (doc.fields.isPublished?.booleanValue !== true) {
    return new Response("Book unpublished", { status: 403, headers: CORS_HEADERS });
  }

  // Path match check
  const registeredCover = sanitizeStoragePath(
    doc.fields.coverStoragePath?.stringValue || doc.fields.coverUrl?.stringValue || "",
    bucketName
  );
  if (registeredCover !== cleanPath) {
    return new Response("Cover path mismatch with registered record", { status: 404, headers: CORS_HEADERS });
  }

  const presignedGetUrl = await generatePresignedUrl({
    method: "GET",
    path: cleanPath,
    expiresInSeconds: 300, // 5 minutes
  });

  const b2Response = await fetch(presignedGetUrl);
  if (!b2Response.ok) {
    return new Response("Cover image not found in storage", {
      status: b2Response.status,
      headers: CORS_HEADERS,
    });
  }

  const headers = new Headers(b2Response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  // Private cache only for authorized user, no public proxy caching
  headers.set("Cache-Control", "private, no-transform, max-age=86400");

  return new Response(b2Response.body, {
    status: 200,
    headers,
  });
}

// ============================================================================
// FIREBASE ID TOKEN CRYPTOGRAPHIC VERIFICATION (Official Google JWKS)
// ============================================================================
async function verifyFirebaseToken(req: Request): Promise<VerifiedToken | null> {
  const authHeader = req.headers.get("Authorization") || "";
  // Support both "Bearer <token>" and "Firebase <token>" (from Android Coil)
  const tokenMatch = authHeader.match(/^(?:Bearer|Firebase)\s+(.+)$/i);
  if (!tokenMatch) return null;

  const jwtString = tokenMatch[1].trim();
  const parts = jwtString.split(".");
  if (parts.length !== 3) return null;

  try {
    const header = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[0])));
    const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1])));

    // 1. Algorithm: RS256 only
    if (header.alg !== "RS256" || !header.kid) {
      console.warn("Invalid JWT algorithm or missing kid");
      return null;
    }

    const projectId = Deno.env.get("FIREBASE_PROJECT_ID") || DEFAULT_FIREBASE_PROJECT_ID;
    const now = Math.floor(Date.now() / 1000);

    // 2. Audience Check
    if (payload.aud !== projectId) {
      console.warn(`JWT audience mismatch: expected ${projectId}, got ${payload.aud}`);
      return null;
    }

    // 3. Issuer Check
    if (payload.iss !== `https://securetoken.google.com/${projectId}`) {
      console.warn(`JWT issuer mismatch: ${payload.iss}`);
      return null;
    }

    // 4. Expiration Check
    if (typeof payload.exp !== "number" || payload.exp < now) {
      console.warn("JWT token expired");
      return null;
    }

    // 5. Issued At Check (reject tokens issued > 30s in future)
    if (typeof payload.iat === "number" && payload.iat > now + 30) {
      console.warn("JWT token iat in future");
      return null;
    }

    // 6. User Identity Check
    if (!payload.sub || typeof payload.sub !== "string" || payload.sub !== payload.user_id) {
      console.warn("JWT invalid subject claim");
      return null;
    }

    // 7. Cryptographic Signature Verification via Google JWKS
    const jwks = await getGoogleJwks();
    let matchingJwk = jwks.find((k) => k.kid === header.kid);

    // Refresh keys once if key ID is rotated
    if (!matchingJwk) {
      cachedJwks = null;
      jwksExpiry = 0;
      const refreshedJwks = await getGoogleJwks();
      matchingJwk = refreshedJwks.find((k) => k.kid === header.kid);
    }

    if (!matchingJwk) {
      console.warn(`No Google JWK found for kid: ${header.kid}`);
      return null;
    }

    const cryptoKey = await crypto.subtle.importKey(
      "jwk",
      matchingJwk,
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
      false,
      ["verify"]
    );

    const dataToVerify = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const signatureBytes = base64UrlDecode(parts[2]);

    const isValid = await crypto.subtle.verify(
      { name: "RSASSA-PKCS1-v1_5" },
      cryptoKey,
      signatureBytes,
      dataToVerify
    );

    if (!isValid) {
      console.warn("JWT signature verification failed");
      return null;
    }

    return {
      uid: String(payload.sub),
      email: payload.email ? String(payload.email) : undefined,
      payload,
    };
  } catch (err) {
    console.error("Token verification exception:", err);
    return null;
  }
}

async function getGoogleJwks(): Promise<GoogleJwk[]> {
  const now = Date.now();
  if (cachedJwks && jwksExpiry > now) {
    return cachedJwks;
  }

  const res = await fetch(GOOGLE_JWKS_URL);
  if (!res.ok) {
    throw new Error(`Failed to fetch Google JWKS (HTTP ${res.status})`);
  }

  const cacheControl = res.headers.get("cache-control") || "";
  const maxAgeMatch = cacheControl.match(/max-age=(\d+)/);
  const maxAge = maxAgeMatch ? parseInt(maxAgeMatch[1], 10) : 3600;

  const data = await res.json();
  cachedJwks = (data.keys || []) as GoogleJwk[];
  jwksExpiry = now + maxAge * 1000;
  return cachedJwks;
}

// ============================================================================
// FIRESTORE SUBSCRIPTION & DOCUMENT VERIFICATION
// ============================================================================
// deno-lint-ignore no-explicit-any
async function fetchFirestoreDoc(collection: string, docId: string, authHeader: string | null): Promise<any> {
  const projectId = Deno.env.get("FIREBASE_PROJECT_ID") || DEFAULT_FIREBASE_PROJECT_ID;
  const cleanDocId = encodeURIComponent(docId);
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collection}/${cleanDocId}`;

  const headers: Record<string, string> = {};
  if (authHeader) headers["Authorization"] = authHeader;

  const res = await fetch(url, { headers });
  if (!res.ok) return null;
  return await res.json();
}

interface SubscriptionCheckParams {
  uid: string;
  classNumber: number;
  stream: string;
  authHeader: string | null;
}

async function verifyStudentSubscription({
  uid,
  classNumber,
  stream,
  authHeader,
}: SubscriptionCheckParams): Promise<boolean> {
  const projectId = Deno.env.get("FIREBASE_PROJECT_ID") || DEFAULT_FIREBASE_PROJECT_ID;
  const cls = parseInt(String(classNumber), 10);

  let targetSubDocIds: string[] = [];

  if (cls >= 5 && cls <= 8) {
    // Classes 5–8: Exactly one grade-level subscription unlocks that class
    targetSubDocIds = [`sub_${uid}_c${cls}_ALL`];
  } else if (cls === 9 || cls === 10) {
    // Classes 9–10: Stream-isolated subscriptions
    const normStream = stream.toUpperCase().trim();
    if (normStream === "ALL" || normStream === "") {
      // Common subject for Class 9/10: Accessible if student has an active subscription for ANY stream in this class
      targetSubDocIds = [
        `sub_${uid}_c${cls}_SCIENCE`,
        `sub_${uid}_c${cls}_HUMANITIES`,
        `sub_${uid}_c${cls}_BUSINESS`
      ];
    } else if (normStream === "SCIENCE" || normStream === "HUMANITIES" || normStream === "BUSINESS") {
      // Strict: Must match exact stream. No cross-stream allowed.
      targetSubDocIds = [`sub_${uid}_c${cls}_${normStream}`];
    } else {
      return false;
    }
  } else {
    // Unsupported class
    return false;
  }

  for (const docId of targetSubDocIds) {
    const subUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/subscriptions/${encodeURIComponent(docId)}`;
    const headers: Record<string, string> = {};
    if (authHeader) headers["Authorization"] = authHeader;

    try {
      const res = await fetch(subUrl, { headers });
      if (!res.ok) continue;

      const doc = await res.json();
      if (!doc || !doc.fields) continue;

      // Active status check
      const status = doc.fields.status?.stringValue;
      if (status !== "ACTIVE") continue;

      // Expiration check
      const expiryDate = doc.fields.expiryDate?.timestampValue;
      if (!expiryDate) continue;

      const expTime = new Date(expiryDate).getTime();
      if (expTime > Date.now()) {
        return true;
      }
    } catch {
      continue;
    }
  }

  return false;
}

// ============================================================================
// AWS SIGNATURE VERSION 4 PRESIGNED URL GENERATOR (Backblaze B2 S3 API)
// ============================================================================
interface PresignedUrlOptions {
  method: "GET" | "PUT";
  path: string;
  contentType?: string;
  expiresInSeconds?: number;
}

async function generatePresignedUrl({
  method,
  path,
  contentType,
  expiresInSeconds = 900,
}: PresignedUrlOptions): Promise<string> {
  const keyId = Deno.env.get("B2_KEY_ID");
  const secretKey = Deno.env.get("B2_APPLICATION_KEY");
  const bucket = Deno.env.get("B2_BUCKET") || DEFAULT_B2_BUCKET;
  const endpoint = (Deno.env.get("B2_ENDPOINT") || DEFAULT_B2_ENDPOINT).replace(/\/$/, "");
  const region = Deno.env.get("B2_REGION") || DEFAULT_B2_REGION;

  if (!keyId || !secretKey) {
    throw new Error(
      "Missing B2_KEY_ID or B2_APPLICATION_KEY. Please configure them in Supabase Edge Function Secrets."
    );
  }

  const endpointUrl = new URL(endpoint);
  const host = endpointUrl.host;

  const now = new Date();
  const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, "");
  const dateStamp = amzDate.slice(0, 8);

  const cleanObjectPath = path.startsWith("/") ? path.slice(1) : path;

  // Canonical URI: each path segment RFC 3986 encoded
  const canonicalUri = `/${encodeRfc3986(bucket)}/${cleanObjectPath
    .split("/")
    .map(encodeRfc3986)
    .join("/")}`;

  const credentialScope = `${dateStamp}/${region}/s3/aws4_request`;

  const queryParams: Record<string, string> = {
    "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
    "X-Amz-Credential": `${keyId}/${credentialScope}`,
    "X-Amz-Date": amzDate,
    "X-Amz-Expires": String(expiresInSeconds),
    "X-Amz-SignedHeaders": contentType ? "content-type;host" : "host",
  };

  const sortedKeys = Object.keys(queryParams).sort();
  const canonicalQueryString = sortedKeys
    .map((k) => `${encodeRfc3986(k)}=${encodeRfc3986(queryParams[k])}`)
    .join("&");

  let canonicalHeaders = `host:${host}\n`;
  if (contentType) {
    canonicalHeaders = `content-type:${contentType.trim()}\n` + canonicalHeaders;
  }

  const canonicalRequest = [
    method,
    canonicalUri,
    canonicalQueryString,
    canonicalHeaders,
    queryParams["X-Amz-SignedHeaders"],
    "UNSIGNED-PAYLOAD",
  ].join("\n");

  const canonicalRequestHash = await sha256Hex(canonicalRequest);

  const stringToSign = [
    "AWS4-HMAC-SHA256",
    amzDate,
    credentialScope,
    canonicalRequestHash,
  ].join("\n");

  const signingKey = await getSignatureKey(secretKey, dateStamp, region, "s3");
  const signature = await hmacSha256Hex(signingKey, stringToSign);

  return `${endpoint}${canonicalUri}?${canonicalQueryString}&X-Amz-Signature=${signature}`;
}

async function getSignatureKey(
  key: string,
  dateStamp: string,
  regionName: string,
  serviceName: string
): Promise<ArrayBuffer> {
  const kDate = await hmacSha256Bytes(new TextEncoder().encode("AWS4" + key), dateStamp);
  const kRegion = await hmacSha256Bytes(kDate, regionName);
  const kService = await hmacSha256Bytes(kRegion, serviceName);
  return await hmacSha256Bytes(kService, "aws4_request");
}

async function hmacSha256Bytes(keyBuffer: ArrayBuffer, dataString: string): Promise<ArrayBuffer> {
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyBuffer,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  return await crypto.subtle.sign("HMAC", cryptoKey, new TextEncoder().encode(dataString));
}

async function hmacSha256Hex(keyBuffer: ArrayBuffer, dataString: string): Promise<string> {
  const signatureBytes = await hmacSha256Bytes(keyBuffer, dataString);
  return bufferToHex(signatureBytes);
}

async function sha256Hex(str: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(str));
  return bufferToHex(digest);
}

// ============================================================================
// SECURITY & PATH SANITIZATION UTILITIES
// ============================================================================
function validateSafeStoragePath(rawPath: string | undefined | null, bucketName: string): SafePathInfo {
  if (!rawPath || typeof rawPath !== "string") {
    return {
      valid: false,
      type: "PDF",
      category: "ORIGINAL_TEXTBOOKS",
      collection: "books",
      classNumber: 0,
      docId: "",
      cleanPath: "",
      reason: "Empty storage path",
    };
  }

  const trimmed = rawPath.trim();

  // Reject path traversal, backslashes, percent-encoded traversal, null bytes, double slashes
  if (
    trimmed.includes("..") ||
    trimmed.includes("\\") ||
    trimmed.includes("%2e") ||
    trimmed.includes("%2E") ||
    trimmed.includes("%2f") ||
    trimmed.includes("%2F") ||
    trimmed.includes("%5c") ||
    trimmed.includes("%5C") ||
    trimmed.includes("\0") ||
    trimmed.includes("//")
  ) {
    return {
      valid: false,
      type: "PDF",
      category: "ORIGINAL_TEXTBOOKS",
      collection: "books",
      classNumber: 0,
      docId: "",
      cleanPath: "",
      reason: "Path traversal or malformed characters detected",
    };
  }

  const clean = sanitizeStoragePath(trimmed, bucketName);

  // PDF Schema: books/class_<5-10>/<docId>/original_book.pdf OR guides/class_<5-10>/<docId>/original_guide.pdf
  const pdfMatch = clean.match(
    /^(books|guides)\/class_(5|6|7|8|9|10)\/([a-zA-Z0-9_-]{3,80})\/(original_book|original_guide)\.pdf$/
  );
  if (pdfMatch) {
    const coll = pdfMatch[1] as "books" | "guides";
    return {
      valid: true,
      type: "PDF",
      category: coll === "guides" ? "GUIDES_AND_NOTES" : "ORIGINAL_TEXTBOOKS",
      collection: coll,
      classNumber: parseInt(pdfMatch[2], 10),
      docId: pdfMatch[3],
      cleanPath: clean,
    };
  }

  // Cover Schema: book_covers/class_<5-10>/<docId>/cover.<ext> OR guide_covers/class_<5-10>/<docId>/cover.<ext>
  const coverMatch = clean.match(
    /^(book_covers|guide_covers)\/class_(5|6|7|8|9|10)\/([a-zA-Z0-9_-]{3,80})\/cover\.(jpg|jpeg|png|webp)$/
  );
  if (coverMatch) {
    const coll = coverMatch[1] === "guide_covers" ? "guides" : "books";
    return {
      valid: true,
      type: "COVER",
      category: coll === "guides" ? "GUIDES_AND_NOTES" : "ORIGINAL_TEXTBOOKS",
      collection: coll,
      classNumber: parseInt(coverMatch[2], 10),
      docId: coverMatch[3],
      cleanPath: clean,
    };
  }

  return {
    valid: false,
    type: "PDF",
    category: "ORIGINAL_TEXTBOOKS",
    collection: "books",
    classNumber: 0,
    docId: "",
    cleanPath: clean,
    reason: "Path does not match approved books/guides schema for classes 5 to 10",
  };
}

function sanitizeStoragePath(rawPath: string | undefined | null, bucketName: string): string {
  let cleaned = String(rawPath || "").trim();
  if (cleaned.startsWith("b2://")) {
    cleaned = cleaned.slice(5);
    if (bucketName && cleaned.startsWith(bucketName + "/")) {
      cleaned = cleaned.slice(bucketName.length + 1);
    }
  }
  return cleaned.replace(/^\/+/, "");
}

function getExtension(fileName: string | undefined, contentType: string | undefined, fallback: string): string {
  if (fileName && fileName.includes(".")) {
    const ext = fileName.split(".").pop()?.toLowerCase().replace(/[^a-z0-9]/g, "");
    if (ext && ["jpg", "jpeg", "png", "webp", "pdf"].includes(ext)) return ext;
  }
  if (contentType) {
    if (contentType.includes("png")) return "png";
    if (contentType.includes("webp")) return "webp";
    if (contentType.includes("pdf")) return "pdf";
  }
  return fallback;
}

function encodeRfc3986(str: string): string {
  return encodeURIComponent(str).replace(
    /[!'()*]/g,
    (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase()
  );
}

function bufferToHex(buffer: ArrayBuffer): string {
  return Array.from(new Uint8Array(buffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function base64UrlDecode(str: string): Uint8Array {
  let base64 = str.replace(/-/g, "+").replace(/_/g, "/");
  while (base64.length % 4) base64 += "=";
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function jsonResponse(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...CORS_HEADERS,
      "Content-Type": "application/json; charset=utf-8",
    },
  });
}
