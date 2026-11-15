const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const SUPER_ADMIN_UID = "yyskhmPvvfeNl572FnVhZgYAaQx2";
const STUDENT_A_UID = "student_rahim_101";
const STUDENT_B_UID = "student_karim_202";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("1. Unauthenticated users cannot read or write protected collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("books").where("isPublished", "==", true).get());
  await assertFails(unauthDb.collection("users").doc(STUDENT_A_UID).get());
  await assertFails(unauthDb.collection("user_notes").get());
});

test("2. Normal student can create own student profile, CANNOT self-assign super_admin or change role", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const now = new Date();

  // Attempt privilege escalation during registration -> MUST FAIL
  await assertFails(
    studentDb.collection("users").doc(STUDENT_A_UID).set({
      userId: STUDENT_A_UID,
      name: "রহিম উদ্দিন",
      email: "rahim@example.com",
      role: "super_admin",
      selectedClass: 8,
      selectedStream: "NONE",
      isActive: true,
      accountDeletionRequested: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid student registration -> MUST SUCCEED
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_A_UID).set({
      userId: STUDENT_A_UID,
      name: "রহিম উদ্দিন",
      email: "rahim@example.com",
      role: "student",
      selectedClass: 8,
      selectedStream: "NONE",
      isActive: true,
      accountDeletionRequested: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Attempt role modification on update -> MUST FAIL
  await assertFails(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      role: "super_admin",
      updatedAt: new Date(),
    })
  );

  // Shadow update with ghost field -> MUST FAIL
  await assertFails(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      name: "রহিম",
      isVerifiedAdmin: true,
      updatedAt: new Date(),
    })
  );

  // Valid profile update (class & name) -> MUST SUCCEED
  await assertSucceeds(
    studentDb.collection("users").doc(STUDENT_A_UID).update({
      name: "মোঃ রহিম উদ্দিন",
      selectedClass: 9,
      selectedStream: "SCIENCE",
      updatedAt: new Date(),
    })
  );
});

test("3. Trusted Super Admin UID ONLY can manage classes, subjects, books, guides, and notices", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  const bookPayload = {
    titleBn: "আমার বাংলা বই",
    subjectId: "bangla_5",
    subjectNameBn: "বাংলা",
    classNumber: 5,
    stream: "ALL",
    academicYear: "2026",
    edition: "NCTB",
    language: "BN",
    pageCount: 140,
    pdfUrl: "https://example.org/nctb/bangla5.pdf",
    isPremium: false,
    isPublished: true,
    allowDownload: true,
    isPopular: true,
    createdAt: now,
    updatedAt: now,
  };

  // Normal student attempting to write book, class, guide, notice -> MUST FAIL
  await assertFails(studentDb.collection("books").doc("book_1").set(bookPayload));
  await assertFails(
    studentDb.collection("classes").doc("class_5").set({
      classNumber: 5,
      nameBn: "পঞ্চম শ্রেণি",
      nameEn: "Class 5",
      hasStreams: false,
      isActive: true,
      sortOrder: 1,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    studentDb.collection("notices").doc("notice_1").set({
      titleBn: "নতুন বই যুক্ত হয়েছে",
      bodyBn: "সকল শ্রেণির নতুন বই যুক্ত করা হয়েছে।",
      category: "GENERAL",
      isPublished: true,
      isImportant: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Trusted Super Admin writing book, guide, class, notice (including enriched library metadata) -> MUST SUCCEED
  await assertSucceeds(
    adminDb.collection("books").doc("book_1").set({
      ...bookPayload,
      author: "জাতীয় শিক্ষাক্রম ও পাঠ্যপুস্তক বোর্ড (NCTB)",
      publisher: "NCTB, বাংলাদেশ",
      shortDescription: "পঞ্চম শ্রেণির শিক্ষার্থীদের বাংলা সাহিত্য ও ব্যাকরণ পাঠ্যবই।",
      description: "পঞ্চম শ্রেণির শিক্ষার্থীদের জন্য নির্ধারিত সম্পূর্ণ বাংলা মূল পাঠ্যবই।",
      tableOfContents: ["অধ্যায় ১: এই দেশ এই মানুষ", "অধ্যায় ২: সুন্দরবনের প্রাণী"],
    })
  );
  await assertSucceeds(
    adminDb.collection("guides").doc("guide_1").set({
      titleBn: "অধ্যায় ১ সম্পূর্ণ গাইড ও অনুশীলনী",
      subjectId: "bangla_5",
      subjectNameBn: "বাংলা",
      subjectNameEn: "Bangla",
      classNumber: 5,
      stream: "ALL",
      chapter: "অধ্যায় ১",
      guideType: "CHAPTER_NOTE",
      author: "Smart School একাডেমিক টিম",
      publisher: "Smart School Library BD",
      edition: "২০২৬ সংস্করণ",
      shortDescription: "অধ্যায়ভিত্তিক সংক্ষিপ্ত নোট ও প্রশ্নোত্তর।",
      summaryContent: "বিস্তারিত অধ্যায়ভিত্তিক আলোচনা ও অনুশীলনীর সমাধান।",
      tableOfContents: ["পাঠ পরিচিতি", "সৃজনশীল প্রশ্নোত্তর"],
      pageCount: 18,
      isPremium: true,
      isPublished: true,
      allowDownload: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("classes").doc("class_5").set({
      classNumber: 5,
      nameBn: "পঞ্চম শ্রেণি",
      nameEn: "Class 5",
      hasStreams: false,
      isActive: true,
      sortOrder: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student querying published books & guides -> MUST SUCCEED; querying all books without filter -> MUST FAIL
  await assertSucceeds(studentDb.collection("books").where("isPublished", "==", true).get());
  await assertSucceeds(studentDb.collection("guides").where("isPublished", "==", true).get());
  await assertFails(studentDb.collection("books").get());
});

test("4. Cross-user isolation for notes, bookmarks, reading progress, and user profiles", async () => {
  const studentADb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const studentBDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    studentADb.collection("user_notes").doc("note_a1").set({
      userId: STUDENT_A_UID,
      title: "গণিতের সূত্র",
      content: "(a+b)^2 = a^2 + 2ab + b^2",
      subjectTag: "গণিত",
      colorIndex: 1,
      isPinned: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    studentADb.collection("bookmarks").doc("bm_a1").set({
      userId: STUDENT_A_UID,
      bookmarkType: "PAGE",
      targetId: "book_1",
      titleBn: "আমার বাংলা বই",
      pageNumber: 12,
      isPremium: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    studentADb.collection("reading_progress").doc(`${STUDENT_A_UID}_book_1`).set({
      userId: STUDENT_A_UID,
      contentId: "book_1",
      contentType: "BOOK",
      titleBn: "আমার বাংলা বই",
      classNumber: 5,
      stream: "ALL",
      isPremium: false,
      lastPage: 15,
      totalPages: 120,
      progressPercent: 12,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student B cannot read or delete Student A's private data
  await assertFails(studentBDb.collection("user_notes").doc("note_a1").get());
  await assertFails(studentBDb.collection("bookmarks").doc("bm_a1").get());
  await assertFails(studentBDb.collection("reading_progress").doc(`${STUDENT_A_UID}_book_1`).get());
  await assertFails(studentBDb.collection("user_notes").where("userId", "==", STUDENT_A_UID).get());
});

test("5. Subscription security: student can ONLY submit PENDING request with verified plan price & unique TrxID lock; ONLY Super Admin approves & creates active subscription", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  // Super Admin creates active Class 5 6-month plan (priceBdt = 60)
  await assertSucceeds(
    adminDb.collection("subscription_plans").doc("plan_c5_6m").set({
      nameBn: "পঞ্চম শ্রেণি প্যাক",
      classNumber: 5,
      stream: "ALL",
      durationMonths: 6,
      priceBdt: 60,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student attempting to submit already APPROVED request -> MUST FAIL
  const badBatch = studentDb.batch();
  badBatch.set(studentDb.collection("subscription_requests").doc("req_bad"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    planId: "plan_c5_6m",
    planNameBn: "পঞ্চম শ্রেণি প্যাক",
    classNumber: 5,
    stream: "ALL",
    durationMonths: 6,
    amountBdt: 60,
    paymentMethod: "bKash",
    transactionReference: "TRX987654",
    normalizedTrxId: "trx_TRX987654",
    status: "APPROVED",
    createdAt: now,
    updatedAt: now,
  });
  badBatch.set(studentDb.collection("used_transaction_ids").doc("trx_TRX987654"), {
    normalizedTrxId: "trx_TRX987654",
    transactionReference: "TRX987654",
    requestId: "req_bad",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(badBatch.commit());

  // Student submitting valid PENDING request + atomic TrxID lock + Admin notification -> MUST SUCCEED
  const validBatch = studentDb.batch();
  validBatch.set(studentDb.collection("subscription_requests").doc("req_1"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    planId: "plan_c5_6m",
    planNameBn: "পঞ্চম শ্রেণি প্যাক",
    classNumber: 5,
    stream: "ALL",
    durationMonths: 6,
    amountBdt: 60,
    paymentMethod: "bKash",
    transactionType: "Send Money",
    receiverNumber: "01711223344",
    transactionReference: "TRX987654",
    normalizedTrxId: "trx_TRX987654",
    senderNumber: "01812345678",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  validBatch.set(studentDb.collection("used_transaction_ids").doc("trx_TRX987654"), {
    normalizedTrxId: "trx_TRX987654",
    transactionReference: "TRX987654",
    requestId: "req_1",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  validBatch.set(studentDb.collection("notifications").doc("notif_admin_1"), {
    recipientId: SUPER_ADMIN_UID,
    senderUid: STUDENT_A_UID,
    title: "নতুন পেমেন্ট রিকোয়েস্ট (৳60)",
    message: "রহিম • পঞ্চম শ্রেণি প্যাক • bKash • TrxID: TRX987654",
    notificationType: "PAYMENT_REQUEST_SUBMITTED",
    relatedPaymentRequestId: "req_1",
    studentName: "রহিম",
    planName: "পঞ্চম শ্রেণি প্যাক",
    amountBdt: 60,
    paymentMethod: "bKash",
    transactionId: "TRX987654",
    isRead: false,
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(validBatch.commit());

  // Student attempting to self-approve request or write own active subscription -> MUST FAIL
  await assertFails(
    studentDb.collection("subscription_requests").doc("req_1").update({
      status: "APPROVED",
      updatedAt: new Date(),
    })
  );

  const expiry = new Date(now.getTime() + 180 * 24 * 60 * 60 * 1000);
  const subPayload = {
    userId: STUDENT_A_UID,
    planId: "plan_c5_6m",
    planNameBn: "পঞ্চম শ্রেণি প্যাক",
    classNumber: 5,
    stream: "ALL",
    durationMonths: 6,
    amountBdt: 60,
    paymentMethod: "bKash",
    transactionReference: "TRX987654",
    status: "ACTIVE",
    startDate: now,
    expiryDate: expiry,
    approvedByUid: SUPER_ADMIN_UID,
    requestId: "req_1",
    createdAt: now,
    updatedAt: now,
  };

  await assertFails(studentDb.collection("subscriptions").doc("sub_1").set(subPayload));

  // Trusted Super Admin approves request, locks TrxID as APPROVED, activates subscription, and notifies student -> MUST SUCCEED
  const approveBatch = adminDb.batch();
  approveBatch.update(adminDb.collection("subscription_requests").doc("req_1"), {
    status: "APPROVED",
    verifiedBy: SUPER_ADMIN_UID,
    verifiedAt: new Date(),
    updatedAt: new Date(),
  });
  approveBatch.update(adminDb.collection("used_transaction_ids").doc("trx_TRX987654"), {
    status: "APPROVED",
    updatedAt: new Date(),
  });
  approveBatch.set(adminDb.collection("subscriptions").doc("sub_1"), subPayload);
  approveBatch.set(adminDb.collection("notifications").doc("notif_stu_approved_1"), {
    recipientId: STUDENT_A_UID,
    senderUid: SUPER_ADMIN_UID,
    title: "পেমেন্ট অনুমোদিত হয়েছে!",
    message: "আপনার পেমেন্ট যাচাই সফল হয়েছে এবং সাবস্ক্রিপশন চালু করা হয়েছে।",
    notificationType: "PAYMENT_APPROVED",
    relatedPaymentRequestId: "req_1",
    isRead: false,
    createdAt: new Date(),
    updatedAt: new Date(),
  });
  await assertSucceeds(approveBatch.commit());
});

test("6. Exam answer key security: blocked before submission, unlocked after student submits attempt", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    adminDb.collection("exam_answer_keys").doc("exam_1").set({
      examId: "exam_1",
      correctIndices: [1, 2],
      explanations: ["ঢাকা বাংলাদেশের রাজধানী।", "শাপলা জাতীয় ফুল।"],
      isPublished: true,
      updatedAt: now,
    })
  );

  // Before submitting exam_attempts/{uid}_exam_1, student CANNOT read answer key!
  await assertFails(studentDb.collection("exam_answer_keys").doc("exam_1").get());

  // Student submits exam attempt
  await assertSucceeds(
    studentDb.collection("exam_attempts").doc(`${STUDENT_A_UID}_exam_1`).set({
      userId: STUDENT_A_UID,
      examId: "exam_1",
      examTitleBn: "মডেল টেস্ট ১",
      selectedAnswers: [1, 0],
      score: 0,
      totalMarks: 2,
      correctCount: 0,
      totalQuestions: 2,
      status: "SUBMITTED",
      createdAt: now,
      updatedAt: now,
    })
  );

  // After submission exists, student CAN read answer key for exam_1!
  await assertSucceeds(studentDb.collection("exam_answer_keys").doc("exam_1").get());
});

test("7. Direct Storage upload metadata: Super Admin can publish only when both coverStoragePath and pdfStoragePath exist; non-admin rejected", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  const incompletePublishedBook = {
    titleBn: "আমার বাংলা বই",
    subjectId: "subj_5_bangla",
    subjectNameBn: "বাংলা",
    classNumber: 5,
    stream: "ALL",
    academicYear: "2026",
    edition: "NCTB",
    author: "NCTB",
    publisher: "NCTB",
    shortDescription: "পঞ্চম শ্রেণির মূল পাঠ্যবই।",
    language: "BN",
    pageCount: 138,
    coverUrl: "",
    pdfUrl: "books/class_5/book_direct_1/original_book.pdf",
    coverStoragePath: "",
    pdfStoragePath: "books/class_5/book_direct_1/original_book.pdf",
    coverFileName: "",
    coverFileSizeBytes: 0,
    pdfFileName: "amar-bangla-boi-class-5.pdf",
    pdfFileSizeBytes: 19608371,
    isPremium: false,
    isPublished: true,
    allowDownload: false,
    isPopular: false,
    createdAt: now,
    updatedAt: now,
  };

  // Publishing a direct-upload book without coverStoragePath -> MUST FAIL
  await assertFails(adminDb.collection("books").doc("book_direct_1").set(incompletePublishedBook));

  const completePublishedBook = {
    ...incompletePublishedBook,
    coverUrl: "book_covers/class_5/book_direct_1/cover.jpg",
    coverStoragePath: "book_covers/class_5/book_direct_1/cover.jpg",
    coverFileName: "amar-bangla-cover.jpg",
    coverFileSizeBytes: 2202009,
  };

  // Normal student attempting to publish complete book -> MUST FAIL
  await assertFails(studentDb.collection("books").doc("book_direct_1").set(completePublishedBook));

  // Trusted Super Admin publishing complete book with both Storage paths -> MUST SUCCEED
  await assertSucceeds(adminDb.collection("books").doc("book_direct_1").set(completePublishedBook));
});

test("8. Super Admin Guide/Note direct upload (COMPLETE_GUIDE) and Subject/Notice create & update succeed", async () => {
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  const directGuidePayload = {
    titleBn: "ষষ্ঠ শ্রেণির গণিত সম্পূর্ণ গাইড",
    titleEn: "Class 6 Complete Math Guide",
    classNumber: 6,
    stream: "ALL",
    subjectId: "subj_6_math",
    subjectNameBn: "গণিত",
    subjectNameEn: "Mathematics",
    chapter: "সম্পূর্ণ বই",
    guideType: "COMPLETE_GUIDE",
    academicYear: "2026",
    language: "BN",
    author: "Smart School Library BD",
    publisher: "Smart School Library BD",
    edition: "NCTB 2026",
    shortDescription: "অধ্যায়ভিত্তিক পূর্ণাঙ্গ সমাধান।",
    summaryContent: "বিস্তারিত গণিত অনুশীলনী সমাধান।",
    tableOfContents: ["অধ্যায় ১: স্বাভাবিক সংখ্যা|1"],
    pdfUrl: "books/class_6/guide_direct_1/original_book.pdf",
    coverUrl: "book_covers/class_6/guide_direct_1/cover.jpg",
    coverStoragePath: "book_covers/class_6/guide_direct_1/cover.jpg",
    pdfStoragePath: "books/class_6/guide_direct_1/original_book.pdf",
    coverFileName: "math-guide-cover.jpg",
    coverFileSizeBytes: 1500000,
    pdfFileName: "math-guide-c6.pdf",
    pdfFileSizeBytes: 14500000,
    pageCount: 240,
    isPremium: false,
    isPublished: true,
    allowDownload: false,
    createdAt: now,
    updatedAt: now,
  };

  await assertSucceeds(adminDb.collection("guides").doc("guide_direct_1").set(directGuidePayload));
  await assertSucceeds(
    adminDb.collection("guides").doc("guide_direct_1").set({
      ...directGuidePayload,
      titleBn: "ষষ্ঠ শ্রেণির গণিত গাইড (সংস্করণ ২)",
      updatedAt: new Date(),
    })
  );

  // Subject create & update preserving createdAt
  const subjectPayload = {
    nameBn: "গণিত",
    nameEn: "Mathematics",
    classNumber: 6,
    stream: "ALL",
    isActive: true,
    sortOrder: 1,
    createdAt: now,
    updatedAt: now,
  };
  await assertSucceeds(adminDb.collection("subjects").doc("subj_6_1").set(subjectPayload));
  await assertSucceeds(
    adminDb.collection("subjects").doc("subj_6_1").set({
      ...subjectPayload,
      isActive: false,
      updatedAt: new Date(),
    })
  );

  // Notice publish
  await assertSucceeds(
    adminDb.collection("notices").doc("notice_1").set({
      titleBn: "নতুন বই যুক্ত হয়েছে",
      bodyBn: "পঞ্চম থেকে দশম শ্রেণির নতুন পাঠ্যবই ও গাইড যুক্ত করা হয়েছে।",
      category: "GENERAL",
      isImportant: true,
      isPublished: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("9. Payment Methods CRUD & Query Alignment: Super Admin manages bKash/Nagad/Rocket/Bank Transfer; students read active methods only", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  const bkashMethod = {
    methodType: "BKASH",
    methodName: "bKash",
    accountNumber: "01711223344",
    accountHolderName: "Smart School Library BD",
    accountType: "Personal",
    transactionType: "Send Money",
    paymentInstructions: "উপরের বিকাশ পার্সোনাল নম্বরে নির্ধারিত ফি Send Money করুন।",
    isActive: true,
    displayOrder: 1,
    createdAt: now,
    updatedAt: now,
  };

  // Normal student CANNOT create payment method -> MUST FAIL
  await assertFails(studentDb.collection("payment_methods").doc("pm_bkash").set(bkashMethod));

  // Super Admin creates bKash (active) and Nagad (disabled) -> MUST SUCCEED
  await assertSucceeds(adminDb.collection("payment_methods").doc("pm_bkash").set(bkashMethod));
  await assertSucceeds(
    adminDb.collection("payment_methods").doc("pm_nagad_off").set({
      ...bkashMethod,
      methodType: "NAGAD",
      methodName: "Nagad",
      accountType: "Agent",
      transactionType: "Cash Out",
      isActive: false,
      displayOrder: 2,
    })
  );

  // Bank Transfer without bankName -> MUST FAIL schema check
  await assertFails(
    adminDb.collection("payment_methods").doc("pm_bank_bad").set({
      methodType: "BANK_TRANSFER",
      methodName: "Bank Transfer",
      accountNumber: "1502203948572",
      accountHolderName: "Smart School Library BD",
      accountType: "Bank Account",
      transactionType: "Bank Transfer",
      bankName: "",
      isActive: true,
      displayOrder: 3,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid Bank Transfer with bankName, accountHolderName, branchName, routingNumber -> MUST SUCCEED
  await assertSucceeds(
    adminDb.collection("payment_methods").doc("pm_bank_ok").set({
      methodType: "BANK_TRANSFER",
      methodName: "Dutch-Bangla Bank",
      accountNumber: "1502203948572",
      accountHolderName: "Smart School Library BD",
      accountType: "Bank Account",
      transactionType: "Bank Transfer",
      bankName: "Dutch-Bangla Bank PLC",
      branchName: "Motijheel Branch",
      routingNumber: "090271726",
      paymentInstructions: "ব্যাংক হিসাবে নির্ধারিত ফি জমা দিয়ে রেফারেন্স আইডি লিখুন।",
      isActive: true,
      displayOrder: 3,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Student querying active payment methods -> MUST SUCCEED; unfiltered list or reading disabled method -> MUST FAIL
  await assertSucceeds(studentDb.collection("payment_methods").where("isActive", "==", true).get());
  await assertFails(studentDb.collection("payment_methods").get());
  await assertFails(studentDb.collection("payment_methods").doc("pm_nagad_off").get());
});

test("10. Price Anti-Tampering, Duplicate Transaction ID Prevention, and Cross-User Notification Isolation", async () => {
  const studentADb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const studentBDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    adminDb.collection("subscription_plans").doc("plan_c8_6m").set({
      nameBn: "অষ্টম শ্রেণি ৬ মাস প্যাক",
      classNumber: 8,
      stream: "ALL",
      durationMonths: 6,
      priceBdt: 80,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("payment_methods").doc("pm_bkash_live").set({
      methodType: "BKASH",
      methodName: "bKash",
      accountNumber: "01799887766",
      accountType: "Merchant",
      transactionType: "Payment",
      isActive: true,
      displayOrder: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // 1. Price tampering attempt (submitting amountBdt: 10 instead of 80) -> MUST FAIL
  const tamperBatch = studentADb.batch();
  tamperBatch.set(studentADb.collection("subscription_requests").doc("req_tamper"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    planId: "plan_c8_6m",
    planNameBn: "অষ্টম শ্রেণি ৬ মাস প্যাক",
    classNumber: 8,
    stream: "ALL",
    durationMonths: 6,
    amountBdt: 10,
    paymentMethodId: "pm_bkash_live",
    paymentMethod: "bKash",
    transactionType: "Payment",
    receiverNumber: "01799887766",
    transactionReference: "BKS555111",
    normalizedTrxId: "trx_BKS555111",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  tamperBatch.set(studentADb.collection("used_transaction_ids").doc("trx_BKS555111"), {
    normalizedTrxId: "trx_BKS555111",
    transactionReference: "BKS555111",
    requestId: "req_tamper",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(tamperBatch.commit());

  // 2. Spam notification without a matching subscription_requests doc -> MUST FAIL
  await assertFails(
    studentADb.collection("notifications").doc("notif_spam").set({
      recipientId: SUPER_ADMIN_UID,
      senderUid: STUDENT_A_UID,
      title: "Fake Payment",
      message: "Fake",
      notificationType: "PAYMENT_REQUEST_SUBMITTED",
      relatedPaymentRequestId: "nonexistent_req",
      isRead: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // 3. Valid Student A submission with verified price (80), verified method snapshot, unique TrxID -> MUST SUCCEED
  const okBatchA = studentADb.batch();
  okBatchA.set(studentADb.collection("subscription_requests").doc("req_ok_a"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    planId: "plan_c8_6m",
    planNameBn: "অষ্টম শ্রেণি ৬ মাস প্যাক",
    classNumber: 8,
    stream: "ALL",
    durationMonths: 6,
    amountBdt: 80,
    paymentMethodId: "pm_bkash_live",
    paymentMethod: "bKash",
    transactionType: "Payment",
    receiverNumber: "01799887766",
    transactionReference: "BKS999888",
    normalizedTrxId: "trx_BKS999888",
    senderNumber: "01811112222",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  okBatchA.set(studentADb.collection("used_transaction_ids").doc("trx_BKS999888"), {
    normalizedTrxId: "trx_BKS999888",
    transactionReference: "BKS999888",
    requestId: "req_ok_a",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(okBatchA.commit());

  // 4. Duplicate Transaction ID reuse by Student B -> MUST FAIL
  const dupBatchB = studentBDb.batch();
  dupBatchB.set(studentBDb.collection("subscription_requests").doc("req_dup_b"), {
    userId: STUDENT_B_UID,
    userName: "করিম",
    planId: "plan_c8_6m",
    planNameBn: "অষ্টম শ্রেণি ৬ মাস প্যাক",
    classNumber: 8,
    stream: "ALL",
    durationMonths: 6,
    amountBdt: 80,
    paymentMethodId: "pm_bkash_live",
    paymentMethod: "bKash",
    transactionType: "Payment",
    receiverNumber: "01799887766",
    transactionReference: "BKS999888",
    normalizedTrxId: "trx_BKS999888",
    senderNumber: "01933334444",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  dupBatchB.set(studentBDb.collection("used_transaction_ids").doc("trx_BKS999888"), {
    normalizedTrxId: "trx_BKS999888",
    transactionReference: "BKS999888",
    requestId: "req_dup_b",
    userId: STUDENT_B_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(dupBatchB.commit());

  // 5. Notification privacy: Student B cannot read Student A's notifications; Student A can mark own notification read
  await assertSucceeds(
    adminDb.collection("notifications").doc("notif_for_a").set({
      recipientId: STUDENT_A_UID,
      senderUid: SUPER_ADMIN_UID,
      title: "পেমেন্ট যাচাই সম্পন্ন",
      message: "আপনার পেমেন্ট অনুমোদিত হয়েছে।",
      notificationType: "PAYMENT_APPROVED",
      relatedPaymentRequestId: "req_ok_a",
      isRead: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(studentADb.collection("notifications").where("recipientId", "==", STUDENT_A_UID).get());
  await assertFails(studentBDb.collection("notifications").where("recipientId", "==", STUDENT_A_UID).get());
  await assertFails(studentBDb.collection("notifications").doc("notif_for_a").get());
  await assertSucceeds(
    studentADb.collection("notifications").doc("notif_for_a").update({
      isRead: true,
      updatedAt: new Date(),
    })
  );
});

test("11. Complete 21-Collection Coverage: Streams, GK, Exam Tests, App Settings, Student Delete Isolation, and Legacy Lock Approval", async () => {
  const studentADb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const studentBDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  // Streams CRUD
  const streamPayload = {
    code: "SCIENCE",
    nameBn: "বিজ্ঞান বিভাগ",
    nameEn: "Science",
    isActive: true,
    sortOrder: 1,
    createdAt: now,
    updatedAt: now,
  };
  await assertFails(studentADb.collection("streams").doc("stream_sci").set(streamPayload));
  await assertSucceeds(adminDb.collection("streams").doc("stream_sci").set(streamPayload));
  await assertSucceeds(studentADb.collection("streams").where("isActive", "==", true).get());
  await assertFails(studentADb.collection("streams").get());

  // General Knowledge CRUD
  const gkPayload = {
    category: "BANGLADESH",
    questionBn: "বাংলাদেশের জাতীয় কবির নাম কী?",
    answerBn: "কাজী নজরুল ইসলাম",
    explanationBn: "বিদ্রোহী কবি কাজী নজরুল ইসলাম বাংলাদেশের জাতীয় কবি।",
    isPublished: true,
    isDemo: true,
    createdAt: now,
    updatedAt: now,
  };
  await assertFails(studentADb.collection("general_knowledge").doc("gk_1").set(gkPayload));
  await assertSucceeds(adminDb.collection("general_knowledge").doc("gk_1").set(gkPayload));
  await assertSucceeds(studentADb.collection("general_knowledge").where("isPublished", "==", true).get());
  await assertFails(studentADb.collection("general_knowledge").get());

  // Exam Preparation CRUD
  const examPayload = {
    titleBn: "নবম শ্রেণি বিজ্ঞান মডেল টেস্ট",
    examType: "MODEL",
    classNumber: 9,
    stream: "SCIENCE",
    subjectNameBn: "পদার্থবিজ্ঞান",
    durationMinutes: 20,
    totalMarks: 2,
    questionCount: 2,
    questions: [
      { id: "q1", questionBn: "বলের একক কী?", optionsBn: ["জুল", "নিউটন", "ওয়াট", "প্যাসকেল"] },
      { id: "q2", questionBn: "কাজের একক কী?", optionsBn: ["জুল", "নিউটন", "ভোল্ট", "অ্যাম্পিয়ার"] },
    ],
    isPublished: true,
    isDemo: true,
    createdAt: now,
    updatedAt: now,
  };
  await assertFails(studentADb.collection("exam_preparation").doc("exam_sci_1").set(examPayload));
  await assertSucceeds(adminDb.collection("exam_preparation").doc("exam_sci_1").set(examPayload));
  await assertSucceeds(studentADb.collection("exam_preparation").where("isPublished", "==", true).get());

  // App Settings CRUD (app_settings/global)
  const settingsPayload = {
    downloadsEnabled: true,
    allowTextbookDownload: true,
    allowGuideDownload: false,
    allowNoteDownload: true,
    defaultTheme: "SYSTEM",
    defaultLanguage: "BN",
    announcementBannerBn: "নতুন শিক্ষাবর্ষের বই যুক্ত হয়েছে!",
    contactEmail: "support@smartschoolbd.example",
    updatedAt: now,
  };
  await assertFails(studentADb.collection("app_settings").doc("global").set(settingsPayload));
  await assertSucceeds(adminDb.collection("app_settings").doc("global").set(settingsPayload));
  await assertSucceeds(studentADb.collection("app_settings").doc("global").get());

  // Student A note delete works for owner, fails for Student B
  await assertSucceeds(
    studentADb.collection("user_notes").doc("note_del_1").set({
      userId: STUDENT_A_UID,
      title: "খসড়া নোট",
      content: "পরীক্ষার প্রস্তুতি",
      colorIndex: 0,
      isPinned: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(studentBDb.collection("user_notes").doc("note_del_1").delete());
  await assertSucceeds(studentADb.collection("user_notes").doc("note_del_1").delete());

  // Super Admin creating used_transaction_ids lock directly when approving a legacy request
  await assertSucceeds(
    adminDb.collection("used_transaction_ids").doc("trx_LEGACY777").set({
      normalizedTrxId: "trx_LEGACY777",
      transactionReference: "LEGACY777",
      requestId: "req_legacy_1",
      userId: STUDENT_A_UID,
      status: "APPROVED",
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("12. Deep Security Audit: Unpublished content direct get, Subscription tampering, Role escalation, and Cross-user CRUD", async () => {
  const studentADb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const studentBDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  // 1. Unpublished content direct ID get: Student A MUST FAIL, Super Admin MUST SUCCEED
  await assertSucceeds(
    adminDb.collection("books").doc("book_draft_secret").set({
      titleBn: "খসড়া অপ্রকাশিত বই",
      subjectId: "subj_math_5",
      subjectNameBn: "গণিত",
      classNumber: 5,
      stream: "ALL",
      academicYear: "2026",
      edition: "খসড়া",
      language: "BN",
      pageCount: 50,
      pdfUrl: "books/class_5/book_draft_secret/original_book.pdf",
      isPremium: true,
      isPublished: false, // DRAFT / UNPUBLISHED
      allowDownload: false,
      isPopular: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(studentADb.collection("books").doc("book_draft_secret").get());
  await assertSucceeds(adminDb.collection("books").doc("book_draft_secret").get());

  await assertSucceeds(
    adminDb.collection("guides").doc("guide_draft_secret").set({
      titleBn: "খসড়া গাইড",
      classNumber: 5,
      stream: "ALL",
      subjectId: "subj_math_5",
      subjectNameBn: "গণিত",
      chapter: "অধ্যায় ১",
      guideType: "CHAPTER_NOTE",
      isPremium: true,
      isPublished: false, // DRAFT / UNPUBLISHED
      allowDownload: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(studentADb.collection("guides").doc("guide_draft_secret").get());
  await assertSucceeds(adminDb.collection("guides").doc("guide_draft_secret").get());

  // 2. Student cannot create, update, or extend own subscription
  const fakeSubPayload = {
    userId: STUDENT_A_UID,
    planId: "plan_c5_12m",
    planNameBn: "১২ মাসের ফ্রি প্যাক",
    classNumber: 5,
    stream: "ALL",
    durationMonths: 12,
    amountBdt: 0,
    status: "ACTIVE",
    startDate: now,
    expiryDate: new Date(now.getTime() + 365 * 24 * 60 * 60 * 1000),
    approvedByUid: SUPER_ADMIN_UID,
    createdAt: now,
    updatedAt: now,
  };
  await assertFails(studentADb.collection("subscriptions").doc(`sub_${STUDENT_A_UID}_c5_ALL`).set(fakeSubPayload));

  // Legitimate admin creates subscription
  await assertSucceeds(adminDb.collection("subscriptions").doc(`sub_${STUDENT_A_UID}_c5_ALL`).set(fakeSubPayload));

  // Student tries to extend or update status
  await assertFails(
    studentADb.collection("subscriptions").doc(`sub_${STUDENT_A_UID}_c5_ALL`).update({
      expiryDate: new Date(now.getTime() + 730 * 24 * 60 * 60 * 1000),
      updatedAt: new Date(),
    })
  );

  // Student B cannot read Student A's subscription
  await assertFails(studentBDb.collection("subscriptions").doc(`sub_${STUDENT_A_UID}_c5_ALL`).get());
  await assertFails(studentBDb.collection("subscriptions").where("userId", "==", STUDENT_A_UID).get());

  // 3. Student B cannot read, update, or delete Student A's reading progress or bookmarks
  await assertSucceeds(
    studentADb.collection("bookmarks").doc(`${STUDENT_A_UID}_bm_test`).set({
      userId: STUDENT_A_UID,
      bookmarkType: "BOOK",
      targetId: "book_test",
      titleBn: "পরীক্ষার বই",
      pageNumber: 1,
      isPremium: false,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(studentBDb.collection("bookmarks").doc(`${STUDENT_A_UID}_bm_test`).get());
  await assertFails(studentBDb.collection("bookmarks").doc(`${STUDENT_A_UID}_bm_test`).delete());

  await assertSucceeds(
    studentADb.collection("reading_progress").doc(`${STUDENT_A_UID}_prog_test`).set({
      userId: STUDENT_A_UID,
      contentId: "book_test",
      contentType: "BOOK",
      titleBn: "পরীক্ষার বই",
      classNumber: 5,
      stream: "ALL",
      isPremium: false,
      lastPage: 10,
      totalPages: 100,
      progressPercent: 10,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(studentBDb.collection("reading_progress").doc(`${STUDENT_A_UID}_prog_test`).get());
  await assertFails(studentBDb.collection("reading_progress").doc(`${STUDENT_A_UID}_prog_test`).delete());
});

test("13. Payment Tampering Attacks: Price, Receiver, Status injection, and Duplicate TrxID race prevention", async () => {
  const studentADb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const studentBDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  // Setup valid plan (Class 7, 3 months, priceBdt: 50) and valid active payment method
  await assertSucceeds(
    adminDb.collection("subscription_plans").doc("plan_c7_3m").set({
      nameBn: "সপ্তম শ্রেণি ৩ মাস",
      classNumber: 7,
      stream: "ALL",
      durationMonths: 3,
      priceBdt: 50,
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    adminDb.collection("payment_methods").doc("pm_rocket_real").set({
      methodType: "ROCKET",
      methodName: "Rocket",
      accountNumber: "017887766550",
      accountType: "Personal",
      transactionType: "Send Money",
      isActive: true,
      displayOrder: 1,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Attack 1: Receiver Number Tampering (student sends request claiming receiver is 01999999999) -> MUST FAIL
  const tamperReceiverBatch = studentADb.batch();
  tamperReceiverBatch.set(studentADb.collection("subscription_requests").doc("req_tamper_recv"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    planId: "plan_c7_3m",
    planNameBn: "সপ্তম শ্রেণি ৩ মাস",
    classNumber: 7,
    stream: "ALL",
    durationMonths: 3,
    amountBdt: 50,
    paymentMethodId: "pm_rocket_real",
    paymentMethod: "Rocket",
    transactionType: "Send Money",
    receiverNumber: "01999999999", // FAKE RECEIVER
    transactionReference: "RCK112233",
    normalizedTrxId: "trx_RCK112233",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  tamperReceiverBatch.set(studentADb.collection("used_transaction_ids").doc("trx_RCK112233"), {
    normalizedTrxId: "trx_RCK112233",
    transactionReference: "RCK112233",
    requestId: "req_tamper_recv",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(tamperReceiverBatch.commit());

  // Attack 2: Status Injection on Create (student tries to create with status = "APPROVED") -> MUST FAIL
  const statusInjectionBatch = studentADb.batch();
  statusInjectionBatch.set(studentADb.collection("subscription_requests").doc("req_status_inject"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    planId: "plan_c7_3m",
    planNameBn: "সপ্তম শ্রেণি ৩ মাস",
    classNumber: 7,
    stream: "ALL",
    durationMonths: 3,
    amountBdt: 50,
    paymentMethodId: "pm_rocket_real",
    paymentMethod: "Rocket",
    transactionType: "Send Money",
    receiverNumber: "017887766550",
    transactionReference: "RCK445566",
    normalizedTrxId: "trx_RCK445566",
    status: "APPROVED", // ILLEGAL PRE-APPROVAL
    verifiedBy: SUPER_ADMIN_UID,
    createdAt: now,
    updatedAt: now,
  });
  statusInjectionBatch.set(studentADb.collection("used_transaction_ids").doc("trx_RCK445566"), {
    normalizedTrxId: "trx_RCK445566",
    transactionReference: "RCK445566",
    requestId: "req_status_inject",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(statusInjectionBatch.commit());

  // Valid Student A request with correct price and correct snapshot -> MUST SUCCEED
  const validBatchA = studentADb.batch();
  validBatchA.set(studentADb.collection("subscription_requests").doc("req_legit_a"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    planId: "plan_c7_3m",
    planNameBn: "সপ্তম শ্রেণি ৩ মাস",
    classNumber: 7,
    stream: "ALL",
    durationMonths: 3,
    amountBdt: 50,
    paymentMethodId: "pm_rocket_real",
    paymentMethod: "Rocket",
    transactionType: "Send Money",
    receiverNumber: "017887766550",
    transactionReference: "RCK778899",
    normalizedTrxId: "trx_RCK778899",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  validBatchA.set(studentADb.collection("used_transaction_ids").doc("trx_RCK778899"), {
    normalizedTrxId: "trx_RCK778899",
    transactionReference: "RCK778899",
    requestId: "req_legit_a",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(validBatchA.commit());

  // Attack 3: Student B tries to use the exact same TrxID (RCK778899) -> MUST FAIL (Duplicate Lock)
  const dupBatchB = studentBDb.batch();
  dupBatchB.set(studentBDb.collection("subscription_requests").doc("req_dup_b"), {
    userId: STUDENT_B_UID,
    userName: "করিম",
    planId: "plan_c7_3m",
    planNameBn: "সপ্তম শ্রেণি ৩ মাস",
    classNumber: 7,
    stream: "ALL",
    durationMonths: 3,
    amountBdt: 50,
    paymentMethodId: "pm_rocket_real",
    paymentMethod: "Rocket",
    transactionType: "Send Money",
    receiverNumber: "017887766550",
    transactionReference: "RCK778899",
    normalizedTrxId: "trx_RCK778899",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  dupBatchB.set(studentBDb.collection("used_transaction_ids").doc("trx_RCK778899"), {
    normalizedTrxId: "trx_RCK778899",
    transactionReference: "RCK778899",
    requestId: "req_dup_b",
    userId: STUDENT_B_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(dupBatchB.commit());

  // Attack 4: Student A tries to approve their own request -> MUST FAIL
  await assertFails(
    studentADb.collection("subscription_requests").doc("req_legit_a").update({
      status: "APPROVED",
      verifiedBy: SUPER_ADMIN_UID,
      updatedAt: new Date(),
    })
  );

  // Attack 5: Student B tries to read Student A's request -> MUST FAIL
  await assertFails(studentBDb.collection("subscription_requests").doc("req_legit_a").get());
});

test("14. Predefined Annual Packages, Non-Admin Role-Claim Escalation Defense, and Admin Approve/Reject Flow", async () => {
  const studentADb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const studentBDb = testEnv.authenticatedContext(STUDENT_B_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  // Defense 1: Even if a user profile doc somehow has role == 'super_admin', rules MUST reject admin actions unless UID matches TRUSTED_SUPER_ADMIN_UID
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(STUDENT_B_UID).set({
      userId: STUDENT_B_UID,
      name: "Spoofed Admin",
      email: "spoof@test.com",
      role: "super_admin",
      selectedClass: 9,
      selectedStream: "SCIENCE",
      isActive: true,
      accountDeletionRequested: false,
      createdAt: now,
      updatedAt: now,
    });
  });
  await assertFails(
    studentBDb.collection("books").doc("spoof_book").set({
      titleBn: "Spoofed",
      subjectId: "s1",
      subjectNameBn: "বাংলা",
      classNumber: 5,
      stream: "ALL",
      academicYear: "2026",
      edition: "NCTB",
      language: "BN",
      pageCount: 10,
      pdfUrl: "https://example.org/spoof.pdf",
      isPremium: false,
      isPublished: true,
      allowDownload: false,
      isPopular: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Defense 2: Open price tampering using a fake unseeded planId MUST FAIL
  const fakePlanBatch = studentADb.batch();
  fakePlanBatch.set(studentADb.collection("subscription_requests").doc("req_fake_plan"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    planId: "plan_fake_1bdt",
    planNameBn: "ফেক প্ল্যান",
    classNumber: 5,
    stream: "ALL",
    durationMonths: 12,
    amountBdt: 1,
    paymentMethodId: "",
    paymentMethod: "bKash",
    transactionType: "Send Money",
    receiverNumber: "01711223344",
    transactionReference: "FAKE112233",
    normalizedTrxId: "trx_FAKE112233",
    senderNumber: "01812345678",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  fakePlanBatch.set(studentADb.collection("used_transaction_ids").doc("trx_FAKE112233"), {
    normalizedTrxId: "trx_FAKE112233",
    transactionReference: "FAKE112233",
    requestId: "req_fake_plan",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(fakePlanBatch.commit());

  // Defense 3: Predefined Annual Plan Class 5 (plan_annual_c5, 250 BDT, 12m) with wrong price (100 BDT) -> MUST FAIL
  const tamperAnnualBatch = studentADb.batch();
  tamperAnnualBatch.set(studentADb.collection("subscription_requests").doc("req_tamper_c5"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    planId: "plan_annual_c5",
    planNameBn: "পঞ্চম শ্রেণি — বার্ষিক প্যাকেজ",
    classNumber: 5,
    stream: "ALL",
    durationMonths: 12,
    amountBdt: 100,
    paymentMethodId: "",
    paymentMethod: "Nagad",
    transactionType: "Send Money",
    receiverNumber: "01711223344",
    transactionReference: "NGD555666",
    normalizedTrxId: "trx_NGD555666",
    senderNumber: "01812345678",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  tamperAnnualBatch.set(studentADb.collection("used_transaction_ids").doc("trx_NGD555666"), {
    normalizedTrxId: "trx_NGD555666",
    transactionReference: "NGD555666",
    requestId: "req_tamper_c5",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(tamperAnnualBatch.commit());

  // Valid Flow: Student submits Predefined Annual Class 9 Science (350 BDT, 12m, unseeded method, optional screenshot omitted) + Admin notification -> MUST SUCCEED
  const validAnnualBatch = studentADb.batch();
  validAnnualBatch.set(studentADb.collection("subscription_requests").doc("req_annual_c9_sci"), {
    userId: STUDENT_A_UID,
    userName: "রহিম",
    userEmail: "rahim@test.com",
    planId: "plan_annual_c9_science",
    planNameBn: "নবম শ্রেণি (বিজ্ঞান বিভাগ) — বার্ষিক প্যাকেজ",
    classNumber: 9,
    stream: "SCIENCE",
    durationMonths: 12,
    amountBdt: 350,
    paymentMethodId: "",
    paymentMethod: "Nagad",
    transactionType: "Send Money",
    receiverNumber: "01711223344",
    accountHolderName: "",
    transactionReference: "NGD998877",
    normalizedTrxId: "trx_NGD998877",
    senderNumber: "01911223344",
    screenshotUrl: "",
    status: "PENDING",
    adminNote: "",
    rejectionReason: "",
    verifiedBy: "",
    verifiedAt: null,
    createdAt: now,
    updatedAt: now,
  });
  validAnnualBatch.set(studentADb.collection("used_transaction_ids").doc("trx_NGD998877"), {
    normalizedTrxId: "trx_NGD998877",
    transactionReference: "NGD998877",
    requestId: "req_annual_c9_sci",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  validAnnualBatch.set(studentADb.collection("notifications").doc("notif_annual_admin"), {
    recipientId: SUPER_ADMIN_UID,
    senderUid: STUDENT_A_UID,
    title: "নতুন পেমেন্ট রিকোয়েস্ট (৳350)",
    message: "রহিম • নবম শ্রেণি (বিজ্ঞান বিভাগ) — বার্ষিক প্যাকেজ • Nagad • TrxID: NGD998877",
    notificationType: "PAYMENT_REQUEST_SUBMITTED",
    relatedPaymentRequestId: "req_annual_c9_sci",
    studentName: "রহিম",
    planName: "নবম শ্রেণি (বিজ্ঞান বিভাগ) — বার্ষিক প্যাকেজ",
    amountBdt: 350,
    paymentMethod: "Nagad",
    transactionId: "NGD998877",
    isRead: false,
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(validAnnualBatch.commit());

  // Spoofed role_admin CANNOT reject or approve Student A's request -> MUST FAIL
  await assertFails(
    studentBDb.collection("subscription_requests").doc("req_annual_c9_sci").update({
      status: "REJECTED",
      rejectionReason: "Spoof reject",
      verifiedBy: STUDENT_B_UID,
      updatedAt: new Date(),
    })
  );

  // Trusted Super Admin rejects a request with reason, releases RESERVED TrxID lock, and notifies student -> MUST SUCCEED
  const rejectBatch = adminDb.batch();
  rejectBatch.update(adminDb.collection("subscription_requests").doc("req_annual_c9_sci"), {
    status: "REJECTED",
    rejectionReason: "প্রদত্ত নম্বরে পেমেন্ট পাওয়া যায়নি।",
    adminNote: "প্রদত্ত নম্বরে পেমেন্ট পাওয়া যায়নি।",
    verifiedBy: SUPER_ADMIN_UID,
    verifiedAt: new Date(),
    updatedAt: new Date(),
  });
  rejectBatch.delete(adminDb.collection("used_transaction_ids").doc("trx_NGD998877"));
  rejectBatch.set(adminDb.collection("notifications").doc("notif_reject_a"), {
    recipientId: STUDENT_A_UID,
    senderUid: SUPER_ADMIN_UID,
    title: "পেমেন্ট রিকোয়েস্ট বাতিল হয়েছে",
    message: "কারণ: প্রদত্ত নম্বরে পেমেন্ট পাওয়া যায়নি।",
    notificationType: "PAYMENT_REJECTED",
    relatedPaymentRequestId: "req_annual_c9_sci",
    isRead: false,
    createdAt: new Date(),
    updatedAt: new Date(),
  });
  await assertSucceeds(rejectBatch.commit());
});

test("15. Education Levels, Extended Classes (11-30), Deleted Default IDs, and Custom Multi-Duration Plans CRUD", async () => {
  const studentDb = testEnv.authenticatedContext(STUDENT_A_UID).firestore();
  const adminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID).firestore();
  const now = new Date();

  // 1. Education Levels: Student blocked from writing; Super Admin creates & updates; Student reads active only
  const hscLevel = {
    code: "HSC",
    nameBn: "একাদশ–দ্বাদশ (HSC)",
    nameEn: "HSC Level",
    subtitleBn: "উচ্চ মাধ্যমিক ও কলেজ শাখা",
    subtitleEn: "Higher Secondary Level",
    descriptionBn: "কলেজ পর্যায়ের পাঠ্যবই ও সৃজনশীল গাইড।",
    minClassNumber: 11,
    maxClassNumber: 12,
    sortOrder: 2,
    isActive: true,
    createdAt: now,
    updatedAt: now,
  };
  await assertFails(studentDb.collection("education_levels").doc("level_hsc").set(hscLevel));
  await assertSucceeds(adminDb.collection("education_levels").doc("level_hsc").set(hscLevel));
  await assertSucceeds(studentDb.collection("education_levels").where("isActive", "==", true).get());
  await assertFails(studentDb.collection("education_levels").get());

  // 2. Extended Classes (Class 11 & Class 12) + Level attachment + Super Admin delete
  await assertSucceeds(
    adminDb.collection("classes").doc("class_11").set({
      classNumber: 11,
      nameBn: "একাদশ শ্রেণি",
      nameEn: "Class 11",
      hasStreams: false,
      isActive: true,
      sortOrder: 11,
      educationLevelId: "level_hsc",
      educationLevelNameBn: "একাদশ–দ্বাদশ (HSC)",
      educationLevelNameEn: "HSC Level",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    adminDb.collection("classes").doc("class_12").set({
      classNumber: 12,
      nameBn: "দ্বাদশ শ্রেণি",
      nameEn: "Class 12",
      hasStreams: false,
      isActive: true,
      sortOrder: 12,
      educationLevelId: "level_hsc",
      educationLevelNameBn: "একাদশ–দ্বাদশ (HSC)",
      educationLevelNameEn: "HSC Level",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(studentDb.collection("classes").where("isActive", "==", true).get());

  // 3. Super Admin records deleted default IDs in app_settings/global; Student cannot modify
  const settingsWithDeletes = {
    downloadsEnabled: true,
    allowTextbookDownload: true,
    allowGuideDownload: false,
    allowNoteDownload: true,
    defaultTheme: "SYSTEM",
    defaultLanguage: "BN",
    deletedDefaultClassIds: ["class_5"],
    deletedDefaultLevelIds: ["level_graduate"],
    deletedDefaultPlanIds: ["plan_annual_c5"],
    updatedAt: now,
  };
  await assertFails(studentDb.collection("app_settings").doc("global").set(settingsWithDeletes));
  await assertSucceeds(adminDb.collection("app_settings").doc("global").set(settingsWithDeletes));

  // 4. Custom Level-Wide Plan (classNumber: 0, 12m) & Class 11 Custom Plan (3m) + Verified Payment Submission
  await assertSucceeds(
    adminDb.collection("subscription_plans").doc("plan_hsc_all_12m").set({
      nameBn: "এইচএসসি স্তর পূর্ণাঙ্গ প্যাকেজ",
      nameEn: "HSC Full Level Pack",
      descriptionBn: "একাদশ ও দ্বাদশ শ্রেণির সকল বই ও গাইড",
      benefits: ["সব মূল পাঠ্যবই", "সম্পূর্ণ গাইড লাইব্রেরি"],
      classNumber: 0,
      stream: "ALL",
      durationMonths: 12,
      priceBdt: 450,
      educationLevelId: "level_hsc",
      educationLevelNameBn: "একাদশ–দ্বাদশ (HSC)",
      educationLevelNameEn: "HSC Level",
      isActive: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Price tampering against dynamic level-wide plan (100 != 450) -> MUST FAIL
  const badLevelPayBatch = studentDb.batch();
  badLevelPayBatch.set(studentDb.collection("subscription_requests").doc("req_hsc_bad"), {
    userId: STUDENT_A_UID,
    userName: "সাকিব হাসান",
    planId: "plan_hsc_all_12m",
    planNameBn: "এইচএসসি স্তর পূর্ণাঙ্গ প্যাকেজ",
    classNumber: 0,
    stream: "ALL",
    durationMonths: 12,
    amountBdt: 100,
    paymentMethod: "bKash",
    transactionReference: "HSCBAD1234",
    normalizedTrxId: "trx_HSCBAD1234",
    senderNumber: "01711223344",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  badLevelPayBatch.set(studentDb.collection("used_transaction_ids").doc("trx_HSCBAD1234"), {
    normalizedTrxId: "trx_HSCBAD1234",
    transactionReference: "HSCBAD1234",
    requestId: "req_hsc_bad",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(badLevelPayBatch.commit());

  // Valid payment submission for dynamic level-wide plan (450 BDT, classNumber: 0) -> MUST SUCCEED
  const okLevelPayBatch = studentDb.batch();
  okLevelPayBatch.set(studentDb.collection("subscription_requests").doc("req_hsc_ok"), {
    userId: STUDENT_A_UID,
    userName: "সাকিব হাসান",
    planId: "plan_hsc_all_12m",
    planNameBn: "এইচএসসি স্তর পূর্ণাঙ্গ প্যাকেজ",
    classNumber: 0,
    stream: "ALL",
    durationMonths: 12,
    amountBdt: 450,
    paymentMethod: "bKash",
    transactionReference: "HSCOK9988",
    normalizedTrxId: "trx_HSCOK9988",
    senderNumber: "01711223344",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
  });
  okLevelPayBatch.set(studentDb.collection("used_transaction_ids").doc("trx_HSCOK9988"), {
    normalizedTrxId: "trx_HSCOK9988",
    transactionReference: "HSCOK9988",
    requestId: "req_hsc_ok",
    userId: STUDENT_A_UID,
    status: "RESERVED",
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(okLevelPayBatch.commit());
});




