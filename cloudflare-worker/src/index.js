/**
 * Smart School Library BD — Cloudflare Workers Secure Storage Backend
 * 
 * Functions:
 * 1. Cryptographically verifies Firebase ID Tokens (RS256 via Google JWKS).
 * 2. Validates Super Admin UID for file uploads.
 * 3. Verifies Firestore document existence, publication, and student subscriptions.
 * 4. Signs AWS SigV4 Presigned URLs for Private Backblaze B2 S3 API.
 * 5. Provides cached Cover Image delivery via Cloudflare Edge.
 */

// In-memory cache for Google Public Certificates
let cachedGoogleCerts = null;
let certsExpiry = 0;

const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
  "Access-Control-Max-Age": "86400",
};

export default {
  async fetch(request, env, ctx) {
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    const url = new URL(request.url);

    try {
      if (url.pathname === "/api/health" && request.method === "GET") {
        return jsonResponse({
          status: "ok",
          service: "Smart School Library BD Storage Backend",
          timestamp: Date.now(),
        });
      }

      // 1. Upload Ticket Endpoint (Super Admin Only)
      if (url.pathname === "/api/storage/upload-ticket" && request.method === "POST") {
        return await handleUploadTicket(request, env);
      }

      // 2. Read Ticket Endpoint (Authorized Students & Admin)
      if (url.pathname === "/api/storage/read-ticket" && request.method === "POST") {
        return await handleReadTicket(request, env);
      }

      // 3. Cover Ticket Endpoint
      if (url.pathname === "/api/storage/cover-ticket" && request.method === "POST") {
        return await handleCoverTicket(request, env);
      }

      // 4. Cached Cover Delivery Endpoint (GET /covers/*)
      if (url.pathname.startsWith("/covers/") && request.method === "GET") {
        return await handleCoverProxy(request, env, ctx);
      }

      return jsonResponse({ error: "Endpoint not found" }, 404);
    } catch (err) {
      console.error("Worker Execution Error:", err);
      return jsonResponse({ error: err.message || "Internal server error" }, 500);
    }
  },
};

// ============================================================================
// 1. UPLOAD TICKET HANDLER (Super Admin Only)
// ============================================================================
async function handleUploadTicket(request, env) {
  const token = await verifyAuthToken(request, env);
  const superAdminUid = env.SUPER_ADMIN_UID || "yyskhmPvvfeNl572FnVhZgYAaQx2";

  if (!token || token.uid !== superAdminUid) {
    return jsonResponse(
      { error: "Unauthorized: Only the Super Admin may obtain upload tickets." },
      403
    );
  }

  const body = await request.json().catch(() => ({}));
  const { category, classNumber, itemId, fileType, fileName, contentType } = body;

  if (!classNumber || !itemId || !fileType) {
    return jsonResponse({ error: "Missing required upload parameters" }, 400);
  }

  const clsNum = Math.max(1, Math.min(12, parseInt(classNumber, 10) || 5));
  const cleanId = String(itemId).replace(/[^a-zA-Z0-9_-]/g, "").slice(0, 80);
  const cleanType = String(fileType).toUpperCase();

  let storagePath = "";
  if (cleanType === "COVER") {
    const ext = getExtension(fileName, contentType, "jpg");
    storagePath = `book_covers/class_${clsNum}/${cleanId}/cover.${ext}`;
  } else {
    storagePath = `books/class_${clsNum}/${cleanId}/original_book.pdf`;
  }

  const uploadUrl = await generatePresignedUrl({
    env,
    method: "PUT",
    path: storagePath,
    contentType: contentType || (cleanType === "COVER" ? "image/jpeg" : "application/pdf"),
    expiresInSeconds: 900, // 15 minutes
  });

  return jsonResponse({
    uploadUrl,
    storagePath,
    category: category || "ORIGINAL_TEXTBOOKS",
    classNumber: clsNum,
    itemId: cleanId,
    expiresInSeconds: 900,
  });
}

// ============================================================================
// 2. READ TICKET HANDLER (Content & Subscription Validation)
// ============================================================================
async function handleReadTicket(request, env) {
  const token = await verifyAuthToken(request, env);
  if (!token) {
    return jsonResponse({ error: "Unauthorized: Valid login required" }, 401);
  }

  const body = await request.json().catch(() => ({}));
  const { storagePath, docId, collection = "books" } = body;

  if (!storagePath) {
    return jsonResponse({ error: "storagePath is required" }, 400);
  }

  const cleanStoragePath = sanitizeStoragePath(storagePath, env.B2_BUCKET);
  const superAdminUid = env.SUPER_ADMIN_UID || "yyskhmPvvfeNl572FnVhZgYAaQx2";

  // Super Admin always bypasses subscription checks
  if (token.uid === superAdminUid) {
    const downloadUrl = await generatePresignedUrl({
      env,
      method: "GET",
      path: cleanStoragePath,
      expiresInSeconds: 1800, // 30 minutes
    });
    return jsonResponse({ downloadUrl, storagePath: cleanStoragePath, expiresInSeconds: 1800 });
  }

  // Server-side verification of Book/Guide document from Firestore
  if (docId) {
    const firestoreDoc = await fetchFirestoreDoc(env, collection, docId, request.headers.get("Authorization"));
    if (firestoreDoc && firestoreDoc.fields) {
      const isPublished = firestoreDoc.fields.isPublished?.booleanValue ?? true;
      if (!isPublished) {
        return jsonResponse({ error: "Content is not published" }, 403);
      }

      const isPremium = firestoreDoc.fields.isPremium?.booleanValue ?? false;
      if (isPremium) {
        const classNumber = firestoreDoc.fields.classNumber?.integerValue || 5;
        const stream = firestoreDoc.fields.stream?.stringValue || "ALL";

        const hasActiveSub = await verifyStudentSubscription(
          env,
          token.uid,
          classNumber,
          stream,
          request.headers.get("Authorization")
        );

        if (!hasActiveSub) {
          return jsonResponse(
            { error: "SUBSCRIPTION_REQUIRED", message: "সক্রিয় সাবস্ক্রিপশন প্রয়োজন।" },
            403
          );
        }
      }
    }
  }

  const downloadUrl = await generatePresignedUrl({
    env,
    method: "GET",
    path: cleanStoragePath,
    expiresInSeconds: 1800, // 30 minutes
  });

  return jsonResponse({ downloadUrl, storagePath: cleanStoragePath, expiresInSeconds: 1800 });
}

// ============================================================================
// 3. COVER TICKET HANDLER
// ============================================================================
async function handleCoverTicket(request, env) {
  const token = await verifyAuthToken(request, env);
  if (!token) {
    return jsonResponse({ error: "Unauthorized" }, 401);
  }

  const body = await request.json().catch(() => ({}));
  const { storagePath } = body;
  if (!storagePath) {
    return jsonResponse({ error: "storagePath is required" }, 400);
  }

  const cleanStoragePath = sanitizeStoragePath(storagePath, env.B2_BUCKET);
  const downloadUrl = await generatePresignedUrl({
    env,
    method: "GET",
    path: cleanStoragePath,
    expiresInSeconds: 3600, // 1 hour
  });

  return jsonResponse({ downloadUrl, storagePath: cleanStoragePath, expiresInSeconds: 3600 });
}

// ============================================================================
// 4. CACHED COVER PROXY (GET /covers/*)
// ============================================================================
async function handleCoverProxy(request, env, ctx) {
  const url = new URL(request.url);
  const rawPath = url.pathname.replace(/^\/covers\//, "");
  const cleanPath = sanitizeStoragePath(rawPath, env.B2_BUCKET);

  if (!cleanPath.startsWith("book_covers/")) {
    return jsonResponse({ error: "Invalid cover path" }, 400);
  }

  const cache = caches.default;
  let response = await cache.match(request);
  if (response) {
    return response;
  }

  const presignedGetUrl = await generatePresignedUrl({
    env,
    method: "GET",
    path: cleanPath,
    expiresInSeconds: 300,
  });

  const b2Response = await fetch(presignedGetUrl);
  if (!b2Response.ok) {
    return new Response("Cover image not found", { status: b2Response.status });
  }

  const headers = new Headers(b2Response.headers);
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("Cache-Control", "public, max-age=604800, s-maxage=604800"); // 7 days edge cache

  response = new Response(b2Response.body, {
    status: 200,
    headers,
  });

  ctx.waitUntil(cache.put(request, response.clone()));
  return response;
}

// ============================================================================
// FIREBASE ID TOKEN CRYPTOGRAPHIC VERIFICATION (RS256 via Google JWKS)
// ============================================================================
async function verifyAuthToken(request, env) {
  const authHeader = request.headers.get("Authorization") || "";
  if (!authHeader.startsWith("Bearer ")) return null;

  const jwtString = authHeader.substring(7).trim();
  const parts = jwtString.split(".");
  if (parts.length !== 3) return null;

  try {
    const header = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[0])));
    const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1])));

    const projectId = env.FIREBASE_PROJECT_ID || "smart-school-library-bd";
    const now = Math.floor(Date.now() / 1000);

    if (payload.aud !== projectId) return null;
    if (payload.iss !== `https://securetoken.google.com/${projectId}`) return null;
    if (payload.exp < now) return null;
    if (!payload.sub || payload.sub !== payload.user_id) return null;

    // Fetch Google Public Keys & verify RSA signature
    const certs = await getGooglePublicCerts();
    const certPem = certs[header.kid];
    if (!certPem) return null;

    const cryptoKey = await importRsaPublicKey(certPem);
    const dataToVerify = new TextEncoder().encode(`${parts[0]}.${parts[1]}`);
    const signature = base64UrlDecode(parts[2]);

    const isValid = await crypto.subtle.verify(
      { name: "RSASSA-PKCS1-v1_5" },
      cryptoKey,
      signature,
      dataToVerify
    );

    return isValid ? { uid: payload.sub, email: payload.email, payload } : null;
  } catch (err) {
    console.error("Token verification error:", err);
    return null;
  }
}

async function getGooglePublicCerts() {
  const now = Date.now();
  if (cachedGoogleCerts && certsExpiry > now) {
    return cachedGoogleCerts;
  }
  const res = await fetch(
    "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com"
  );
  if (!res.ok) throw new Error("Failed to fetch Google public certs");

  const cacheControl = res.headers.get("cache-control") || "";
  const maxAgeMatch = cacheControl.match(/max-age=(\d+)/);
  const maxAge = maxAgeMatch ? parseInt(maxAgeMatch[1], 10) : 3600;

  cachedGoogleCerts = await res.json();
  certsExpiry = now + maxAge * 1000;
  return cachedGoogleCerts;
}

async function importRsaPublicKey(certPem) {
  const cleanPem = certPem
    .replace(/-----BEGIN CERTIFICATE-----/g, "")
    .replace(/-----END CERTIFICATE-----/g, "")
    .replace(/\s+/g, "");

  const der = base64ToArrayBuffer(cleanPem);
  // Extract SubjectPublicKeyInfo from X.509 DER certificate
  const spki = extractSpkiFromCert(der);

  return await crypto.subtle.importKey(
    "spki",
    spki,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"]
  );
}

// Minimal ASN.1 DER parser to extract SubjectPublicKeyInfo from X.509 cert
function extractSpkiFromCert(certDer) {
  const bytes = new Uint8Array(certDer);
  // Standard RSA SPKI header sequence pattern in DER
  const rsaOid = [0x2a, 0x86, 0x48, 0x86, 0xf7, 0x0d, 0x01, 0x01, 0x01];
  for (let i = 0; i < bytes.length - rsaOid.length - 8; i++) {
    let match = true;
    for (let j = 0; j < rsaOid.length; j++) {
      if (bytes[i + j] !== rsaOid[j]) {
        match = false;
        break;
      }
    }
    if (match) {
      // Step back to start of AlgorithmIdentifier and enclosing SubjectPublicKeyInfo SEQUENCE
      let start = i - 4;
      while (start > 0 && bytes[start] !== 0x30) start--;
      if (start > 0 && bytes[start - 2] === 0x30) {
        start = start - 2;
      }
      return bytes.slice(start);
    }
  }
  return certDer;
}

// ============================================================================
// SERVER-SIDE FIRESTORE REST API QUERYING
// ============================================================================
async function fetchFirestoreDoc(env, collection, docId, authHeader) {
  const projectId = env.FIREBASE_PROJECT_ID || "smart-school-library-bd";
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/${collection}/${docId}`;

  const headers = {};
  if (authHeader) headers["Authorization"] = authHeader;

  const res = await fetch(url, { headers });
  if (!res.ok) return null;
  return await res.json();
}

async function verifyStudentSubscription(env, uid, classNumber, stream, authHeader) {
  const projectId = env.FIREBASE_PROJECT_ID || "smart-school-library-bd";
  const subDocId = `sub_${uid}_c${classNumber}_${stream || "ALL"}`;
  const url = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/subscriptions/${subDocId}`;

  const headers = {};
  if (authHeader) headers["Authorization"] = authHeader;

  const res = await fetch(url, { headers });
  if (!res.ok) {
    // Try fallback document without stream
    const fallbackId = `sub_${uid}_cls${classNumber}`;
    const fallbackUrl = `https://firestore.googleapis.com/v1/projects/${projectId}/databases/(default)/documents/subscriptions/${fallbackId}`;
    const fallbackRes = await fetch(fallbackUrl, { headers });
    if (!fallbackRes.ok) return false;
    const doc = await fallbackRes.json();
    return checkSubscriptionDoc(doc);
  }

  const doc = await res.json();
  return checkSubscriptionDoc(doc);
}

function checkSubscriptionDoc(doc) {
  if (!doc || !doc.fields) return false;
  const status = doc.fields.status?.stringValue;
  if (status !== "ACTIVE") return false;

  const expiryDate = doc.fields.expiryDate?.timestampValue;
  if (!expiryDate) return false;

  const expTime = new Date(expiryDate).getTime();
  return expTime > Date.now();
}

// ============================================================================
// AWS SIGNATURE VERSION 4 PRESIGNED URL GENERATOR (Backblaze B2 S3 API)
// ============================================================================
async function generatePresignedUrl({ env, method, path, contentType, expiresInSeconds = 900 }) {
  const keyId = env.B2_KEY_ID;
  const secretKey = env.B2_APPLICATION_KEY;
  const bucket = env.B2_BUCKET || "smart-school-library-files";
  const endpoint = (env.B2_ENDPOINT || "https://s3.us-east-005.backblazeb2.com").replace(/\/$/, "");
  const region = env.B2_REGION || "us-east-005";

  if (!keyId || !secretKey) {
    throw new Error("Backblaze B2_KEY_ID and B2_APPLICATION_KEY must be configured in Worker Secrets");
  }

  const endpointUrl = new URL(endpoint);
  const host = endpointUrl.host;

  const now = new Date();
  const amzDate = now.toISOString().replace(/[:-]|\.\d{3}/g, "");
  const dateStamp = amzDate.slice(0, 8);

  const cleanObjectPath = path.startsWith("/") ? path.slice(1) : path;
  const canonicalUri = `/${bucket}/${cleanObjectPath}`;
  const credentialScope = `${dateStamp}/${region}/s3/aws4_request`;

  const queryParams = {
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

async function getSignatureKey(key, dateStamp, regionName, serviceName) {
  const kDate = await hmacSha256Bytes(new TextEncoder().encode("AWS4" + key), dateStamp);
  const kRegion = await hmacSha256Bytes(kDate, regionName);
  const kService = await hmacSha256Bytes(kRegion, serviceName);
  return await hmacSha256Bytes(kService, "aws4_request");
}

async function hmacSha256Bytes(keyBuffer, dataString) {
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyBuffer,
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  return await crypto.subtle.sign("HMAC", cryptoKey, new TextEncoder().encode(dataString));
}

async function hmacSha256Hex(keyBuffer, dataString) {
  const signatureBytes = await hmacSha256Bytes(keyBuffer, dataString);
  return bufferToHex(signatureBytes);
}

async function sha256Hex(str) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(str));
  return bufferToHex(digest);
}

// ============================================================================
// HELPER UTILITIES
// ============================================================================
function sanitizeStoragePath(rawPath, bucketName) {
  let cleaned = String(rawPath || "").trim();
  if (cleaned.startsWith("b2://")) {
    cleaned = cleaned.slice(5);
    if (bucketName && cleaned.startsWith(bucketName + "/")) {
      cleaned = cleaned.slice(bucketName.length + 1);
    }
  }
  return cleaned.replace(/^\/+/, "");
}

function getExtension(fileName, contentType, fallback) {
  if (fileName && fileName.includes(".")) {
    const ext = fileName.split(".").pop().toLowerCase().replace(/[^a-z0-9]/g, "");
    if (["jpg", "jpeg", "png", "webp", "pdf"].includes(ext)) return ext;
  }
  if (contentType) {
    if (contentType.includes("png")) return "png";
    if (contentType.includes("webp")) return "webp";
    if (contentType.includes("pdf")) return "pdf";
  }
  return fallback;
}

function encodeRfc3986(str) {
  return encodeURIComponent(str).replace(/[!'()*]/g, (c) => "%" + c.charCodeAt(0).toString(16).toUpperCase());
}

function bufferToHex(buffer) {
  return Array.from(new Uint8Array(buffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function base64UrlDecode(str) {
  let base64 = str.replace(/-/g, "+").replace(/_/g, "/");
  while (base64.length % 4) base64 += "=";
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

function base64ToArrayBuffer(base64) {
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
}

function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...CORS_HEADERS,
      "Content-Type": "application/json; charset=utf-8",
    },
  });
}
