package com.example.data

import android.content.Context
import com.example.firebase.FirebaseProvider
import com.example.firebase.OperationType
import com.example.firebase.SecurityConstants
import com.example.firebase.handleFirestoreError
import com.example.firebase.toSafeModel
import com.example.firebase.toSafeModels
import com.example.model.AppSettings
import com.example.model.DefaultCatalogSeed
import com.example.model.EducationLevel
import com.example.model.ExamAnswerKey
import com.example.model.ExamAttempt
import com.example.model.ExamTest
import com.example.model.GeneralKnowledgeItem
import com.example.model.Subscription
import com.example.model.SubscriptionPlan
import com.example.model.SubscriptionRequest
import com.example.model.UserNote
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await

enum class PremiumAccessDecision {
    ALLOWED_FREE,
    ALLOWED_ADMIN,
    ALLOWED_ACTIVE_SUBSCRIPTION,
    DENIED_UNAUTHENTICATED,
    DENIED_ACCOUNT_INACTIVE,
    DENIED_SUBSCRIPTION_EXPIRED,
    DENIED_SUBSCRIPTION_REQUIRED
}

data class GradedExamResult(
    val examId: String,
    val examTitleBn: String,
    val selectedAnswers: List<Int>,
    val correctIndices: List<Int>,
    val explanations: List<String>,
    val correctCount: Int,
    val totalQuestions: Int,
    val earnedScore: Int,
    val totalMarks: Int,
    val isDemo: Boolean
)

class StudyRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseProvider.getFirestore(context),
        auth = FirebaseProvider.getAuth(context)
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in")

    // --- 1. Personal Notebook (Strictly Private to Owner) ---

    fun observeUserNotes(userId: String): Flow<List<UserNote>> {
        val path = "user_notes"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(UserNote::class.java).sortedWith(
                    compareByDescending<UserNote> { it.isPinned }
                        .thenByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                )
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun createNote(
        title: String,
        content: String,
        subjectTag: String = "",
        colorIndex: Int = 0,
        isPinned: Boolean = false
    ): Result<String> {
        val uid = requireUserId()
        val noteId = "note_${UUID.randomUUID().toString().replace("-", "")}"
        val docRef = db.collection("user_notes").document(noteId)
        return try {
            val payload = mapOf(
                "userId" to uid,
                "title" to title.trim().take(200),
                "content" to content.trim().take(20000),
                "subjectTag" to subjectTag.trim().take(80),
                "colorIndex" to colorIndex.coerceIn(0, 5),
                "isPinned" to isPinned,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(noteId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        noteId: String,
        title: String,
        content: String,
        subjectTag: String = "",
        colorIndex: Int = 0,
        isPinned: Boolean = false
    ): Result<Unit> {
        requireUserId()
        val docRef = db.collection("user_notes").document(noteId)
        return try {
            val updates = mapOf(
                "title" to title.trim().take(200),
                "content" to content.trim().take(20000),
                "subjectTag" to subjectTag.trim().take(80),
                "colorIndex" to colorIndex.coerceIn(0, 5),
                "isPinned" to isPinned,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(noteId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("user_notes").document(noteId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getNoteById(noteId: String): Result<UserNote?> {
        val docRef = db.collection("user_notes").document(noteId)
        return try {
            val snap = docRef.get().await()
            Result.success(snap.toSafeModel(UserNote::class.java))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    // --- 2. General Knowledge ---

    fun observePublishedGk(): Flow<List<GeneralKnowledgeItem>> {
        val path = "general_knowledge"
        return db.collection(path)
            .whereEqualTo("isPublished", true)
            .snapshots()
            .map { snapshot ->
                val remote = snapshot.toSafeModels(GeneralKnowledgeItem::class.java)
                remote.ifEmpty { DefaultCatalogSeed.demoGkItems }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    // --- 3. MCQ Examinations & Secure Post-Submission Grading ---

    fun observePublishedExams(): Flow<List<ExamTest>> {
        val path = "exam_preparation"
        return db.collection(path)
            .whereEqualTo("isPublished", true)
            .snapshots()
            .map { snapshot ->
                val remote = snapshot.toSafeModels(ExamTest::class.java)
                remote.ifEmpty { DefaultCatalogSeed.demoExamBundles.map { it.exam } }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeExamHistory(userId: String): Flow<List<ExamAttempt>> {
        val path = "exam_attempts"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(ExamAttempt::class.java)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    /**
     * Submits the student's selected answers FIRST to `/exam_attempts/{uid}_{examId}` (locking the answers),
     * and ONLY AFTER submission exists fetches `/exam_answer_keys/{examId}` to grade & record score.
     */
    suspend fun submitAndGradeExam(
        exam: ExamTest,
        selectedAnswers: List<Int>
    ): Result<GradedExamResult> {
        val uid = requireUserId()
        val normalizedSelections = selectedAnswers.map { it.toLong() }

        // Handle built-in clearly labeled DEMO test
        if (exam.isDemo) {
            val demoBundle = DefaultCatalogSeed.demoExamBundles.firstOrNull { it.exam.id == exam.id }
            if (demoBundle != null) {
                var correct = 0
                demoBundle.correctIndices.forEachIndexed { idx, expected ->
                    if (selectedAnswers.getOrNull(idx) == expected) correct++
                }
                val perQuestionMark = (exam.totalMarks.toFloat() / exam.questionCount.coerceAtLeast(1)).coerceAtLeast(1f)
                val earned = (correct * perQuestionMark).toInt().coerceAtMost(exam.totalMarks)
                // Save attempt history in Firestore for authenticated student
                saveDemoExamAttemptSilently(uid, exam, normalizedSelections, earned, correct)
                return Result.success(
                    GradedExamResult(
                        examId = exam.id,
                        examTitleBn = exam.titleBn,
                        selectedAnswers = selectedAnswers,
                        correctIndices = demoBundle.correctIndices,
                        explanations = demoBundle.explanations,
                        correctCount = correct,
                        totalQuestions = exam.questionCount,
                        earnedScore = earned,
                        totalMarks = exam.totalMarks,
                        isDemo = true
                    )
                )
            }
        }

        val attemptId = "${uid}_${exam.id}"
        val attemptRef = db.collection("exam_attempts").document(attemptId)
        val keyRef = db.collection("exam_answer_keys").document(exam.id)

        return try {
            val existingAttempt = attemptRef.get().await()
            if (!existingAttempt.exists()) {
                val createPayload = mapOf(
                    "userId" to uid,
                    "examId" to exam.id,
                    "examTitleBn" to exam.titleBn.take(200),
                    "selectedAnswers" to normalizedSelections,
                    "score" to 0,
                    "totalMarks" to exam.totalMarks.coerceAtLeast(1),
                    "correctCount" to 0,
                    "totalQuestions" to exam.questionCount.coerceAtLeast(1),
                    "status" to "SUBMITTED",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                attemptRef.set(createPayload).await()
            } else {
                // Retake reset to SUBMITTED
                attemptRef.update(
                    mapOf(
                        "selectedAnswers" to normalizedSelections,
                        "score" to 0,
                        "correctCount" to 0,
                        "status" to "SUBMITTED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Now that /exam_attempts/{uid}_{examId} exists, security rules permit reading the published answer key
            val keySnap = keyRef.get().await()
            val keyObj = keySnap.toSafeModel(ExamAnswerKey::class.java)
                ?: error("এই পরীক্ষার উত্তরপত্র এখনো প্রকাশিত হয়নি।")

            val correctIndices = keyObj.correctIndices.map { it.toInt() }
            var correctCount = 0
            correctIndices.forEachIndexed { index, ans ->
                if (selectedAnswers.getOrNull(index) == ans) {
                    correctCount++
                }
            }
            val totalQ = exam.questionCount.coerceAtLeast(1)
            val earnedScore = ((correctCount.toFloat() / totalQ.toFloat()) * exam.totalMarks.toFloat())
                .toInt()
                .coerceIn(0, exam.totalMarks)

            attemptRef.update(
                mapOf(
                    "score" to earnedScore,
                    "correctCount" to correctCount,
                    "status" to "GRADED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                GradedExamResult(
                    examId = exam.id,
                    examTitleBn = exam.titleBn,
                    selectedAnswers = selectedAnswers,
                    correctIndices = correctIndices,
                    explanations = keyObj.explanations,
                    correctCount = correctCount,
                    totalQuestions = totalQ,
                    earnedScore = earnedScore,
                    totalMarks = exam.totalMarks,
                    isDemo = false
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, attemptRef.path)
            Result.failure(e)
        }
    }

    private suspend fun saveDemoExamAttemptSilently(
        uid: String,
        exam: ExamTest,
        normalizedSelections: List<Long>,
        earned: Int,
        correct: Int
    ) {
        val attemptId = "${uid}_${exam.id}"
        val ref = db.collection("exam_attempts").document(attemptId)
        try {
            val snap = ref.get().await()
            if (!snap.exists()) {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "examId" to exam.id,
                        "examTitleBn" to exam.titleBn.take(200),
                        "selectedAnswers" to normalizedSelections,
                        "score" to earned,
                        "totalMarks" to exam.totalMarks.coerceAtLeast(1),
                        "correctCount" to correct,
                        "totalQuestions" to exam.questionCount.coerceAtLeast(1),
                        "status" to "GRADED",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (_: Exception) {
            // Non-blocking for demo practice mode
        }
    }

    // --- 4. Class-Based Subscriptions, Dynamic Payment Methods & Verification ---

    private fun observeSettingsSafe(): Flow<AppSettings> =
        db.collection("app_settings").document("global")
            .snapshots()
            .map { snapshot -> snapshot.toSafeModel(AppSettings::class.java) ?: AppSettings() }
            .catch { err ->
                if (err is CancellationException) throw err
                emit(AppSettings())
            }

    fun observeActivePlans(): Flow<List<SubscriptionPlan>> {
        val path = "subscription_plans"
        val plansFlow = db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot -> snapshot.toSafeModels(SubscriptionPlan::class.java) }
        return combine(plansFlow, observeSettingsSafe()) { remoteList, settings ->
            val deletedIds = settings.deletedDefaultPlanIds.toSet()
            val remoteById = remoteList.associateBy { it.id }
            val defaults = DefaultCatalogSeed.defaultAnnualPackages.filter { it.id !in deletedIds }
            val merged = buildList {
                defaults.forEach { def ->
                    add(remoteById[def.id] ?: def)
                }
                remoteList.filter { remote -> defaults.none { it.id == remote.id } }
                    .forEach { add(it) }
            }
            merged.filter { it.isActive }
                .sortedWith(
                    compareBy<SubscriptionPlan> { it.classNumber }
                        .thenBy { it.durationMonths }
                        .thenBy { it.stream }
                )
        }.catch { error ->
            if (error is CancellationException) throw error
            if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
            throw error
        }
    }

    fun observeActivePaymentMethods(): Flow<List<com.example.model.PaymentMethodConfig>> {
        val path = "payment_methods"
        return db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(com.example.model.PaymentMethodConfig::class.java)
                    .sortedWith(
                        compareBy<com.example.model.PaymentMethodConfig> { it.displayOrder }
                            .thenBy { it.methodName }
                    )
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeUserSubscriptions(userId: String): Flow<List<Subscription>> {
        val path = "subscriptions"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(Subscription::class.java)
                    .sortedByDescending { it.expiryDate ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeUserSubscriptionRequests(userId: String): Flow<List<SubscriptionRequest>> {
        val path = "subscription_requests"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(SubscriptionRequest::class.java)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeUserNotifications(userId: String): Flow<List<com.example.model.AppNotification>> {
        val path = "notifications"
        return db.collection(path)
            .whereEqualTo("recipientId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(com.example.model.AppNotification::class.java)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        requireUserId()
        if (notificationId.isBlank()) return Result.success(Unit)
        val docRef = db.collection("notifications").document(notificationId)
        return try {
            docRef.update(
                mapOf(
                    "isRead" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Submits a manual payment verification request atomically with:
     * 1. Backend-verified Plan price (student cannot spoof or tamper price),
     * 2. Immutable snapshot of selected active Payment Method (receiver number + transaction type),
     * 3. Unique Transaction ID lock (`used_transaction_ids/{normalizedTrxId}`),
     * 4. Real-time Admin payment notification (`notifications/{notifId}`).
     * Always created with status = "PENDING". Never activates subscription client-side.
     */
    suspend fun submitSubscriptionPaymentRequest(
        userName: String,
        userEmail: String,
        plan: SubscriptionPlan,
        paymentMethod: String,
        transactionReference: String,
        senderNumber: String,
        screenshotUrl: String = "",
        selectedMethodConfig: com.example.model.PaymentMethodConfig? = null,
        fallbackReceiverNumber: String = ""
    ): Result<String> {
        val uid = requireUserId()
        val cleanSender = senderNumber.trim()
        if (cleanSender.length < 4 || cleanSender.length > 60) {
            return Result.failure(
                IllegalArgumentException("সঠিক প্রেরক মোবাইল নম্বর / অ্যাকাউন্ট নম্বর প্রদান করুন।")
            )
        }
        val cleanTrx = transactionReference.trim()
        if (!com.example.model.PaymentMethodConstants.hasMinimumAlphanumericChars(cleanTrx, 4) || cleanTrx.length > 100) {
            return Result.failure(
                IllegalArgumentException("সঠিক Transaction ID / ব্যাংক রেফারেন্স নম্বর প্রদান করুন (কমপক্ষে ৪ অক্ষর/সংখ্যা)।")
            )
        }

        val normalizedTrxId = com.example.model.PaymentMethodConstants.normalizeTransactionId(cleanTrx)
        val reqId = "req_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val notifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val reqRef = db.collection("subscription_requests").document(reqId)
        val lockRef = db.collection("used_transaction_ids").document(normalizedTrxId)
        val adminNotifRef = db.collection("notifications").document(notifId)

        return try {
            // 1. Check duplicate Transaction ID directly against server (avoiding stale cache) before batch commit
            val existingLock = try {
                lockRef.get(Source.SERVER).await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                lockRef.get().await()
            }
            if (existingLock.exists()) {
                return Result.failure(
                    IllegalStateException("এই Transaction ID ($cleanTrx) ইতিমধ্যে ব্যবহৃত হয়েছে। একই Transaction ID দিয়ে একাধিকবার সাবস্ক্রিপশন নেওয়া যাবে না।")
                )
            }

            // 2. Verify Plan from Firestore server (or canonical 10 predefined annual plans) so price can never be altered
            val planId = plan.id.trim()
            if (planId.isBlank()) {
                return Result.failure(IllegalArgumentException("সঠিক সাবস্ক্রিপশন প্ল্যান নির্বাচন করুন।"))
            }
            val planDocRef = db.collection("subscription_plans").document(planId)
            val planSnap = try {
                planDocRef.get(Source.SERVER).await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                planDocRef.get().await()
            }
            val verifiedPlan: SubscriptionPlan = if (planSnap.exists()) {
                val remotePlan = planSnap.toSafeModel(SubscriptionPlan::class.java)
                    ?: return Result.failure(IllegalStateException("নির্বাচিত সাবস্ক্রিপশন প্ল্যানটি পড়া যায়নি।"))
                remotePlan
            } else {
                val predefined = DefaultCatalogSeed.defaultAnnualPackages.firstOrNull { it.id == planId }
                    ?: com.example.model.PaymentMethodConstants.findPredefinedAnnualPlan(plan.classNumber, plan.stream)
                    ?: return Result.failure(IllegalStateException("নির্বাচিত সাবস্ক্রিপশন প্যাকেজটি পাওয়া যায়নি।"))
                predefined
            }

            if (!verifiedPlan.isActive) {
                return Result.failure(IllegalStateException("নির্বাচিত সাবস্ক্রিপশন প্যাকেজটি বর্তমানে নিষ্ক্রিয় (Inactive) রয়েছে।"))
            }
            if (verifiedPlan.priceBdt <= 0) {
                return Result.failure(IllegalStateException("এই প্যাকেজের মূল্য এখনো নির্ধারিত হয়নি। শূন্য বা অবৈধ মূল্যে সাবস্ক্রিপশন নেওয়া যাবে না।"))
            }

            // 3. Verify active Payment Method snapshot from server if selected from Firestore `payment_methods`
            var verifiedMethodId = ""
            var verifiedMethodName = paymentMethod.trim().ifBlank { "bKash" }.take(60)
            var verifiedTransactionType = com.example.model.PaymentMethodConstants.TRX_SEND_MONEY
            var verifiedReceiverNumber = fallbackReceiverNumber.trim().take(60)
            var verifiedHolderName = ""

            if (selectedMethodConfig != null) {
                val remoteMethod = if (selectedMethodConfig.id.isNotBlank()) {
                    val methodDocRef = db.collection("payment_methods").document(selectedMethodConfig.id)
                    val methodSnap = try {
                        methodDocRef.get(Source.SERVER).await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        try {
                            methodDocRef.get().await()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (methodSnap != null && methodSnap.exists()) {
                        methodSnap.toSafeModel(com.example.model.PaymentMethodConfig::class.java) ?: selectedMethodConfig
                    } else if (methodSnap != null && !methodSnap.exists()) {
                        return Result.failure(IllegalStateException("নির্বাচিত পেমেন্ট মাধ্যমটি বর্তমানে সক্রিয় নেই। অনুগ্রহ করে পেজটি রিফ্রেশ করে আবার চেষ্টা করুন।"))
                    } else {
                        selectedMethodConfig
                    }
                } else {
                    selectedMethodConfig
                }

                if (!remoteMethod.isActive) {
                    return Result.failure(IllegalStateException("এই পেমেন্ট মাধ্যমটি বর্তমানে বন্ধ রয়েছে। অনুগ্রহ করে অন্য সক্রিয় মাধ্যম নির্বাচন করুন।"))
                }
                verifiedMethodId = remoteMethod.id
                verifiedMethodName = remoteMethod.methodName.take(60)
                verifiedTransactionType = remoteMethod.transactionType
                verifiedReceiverNumber = remoteMethod.accountNumber.ifBlank { fallbackReceiverNumber }.take(60)
                verifiedHolderName = buildString {
                    if (remoteMethod.isBankTransfer && remoteMethod.bankName.isNotBlank()) {
                        append(remoteMethod.bankName)
                        if (remoteMethod.accountHolderName.isNotBlank()) {
                            append(" • ")
                            append(remoteMethod.accountHolderName)
                        }
                    } else {
                        append(remoteMethod.accountHolderName)
                    }
                }.take(150)
            }

            val safeStudentName = userName.trim().ifBlank { "শিক্ষার্থী" }.take(120)
            val safePlanName = verifiedPlan.nameBn.ifBlank { plan.nameBn }.ifBlank { "প্রিমিয়াম প্ল্যান" }.take(120)

            val requestPayload = mapOf(
                "userId" to uid,
                "userName" to safeStudentName,
                "userEmail" to userEmail.trim().take(200),
                "planId" to verifiedPlan.id,
                "planNameBn" to safePlanName,
                "classNumber" to verifiedPlan.classNumber.coerceIn(0, 30),
                "stream" to verifiedPlan.stream.ifBlank { "ALL" },
                "durationMonths" to verifiedPlan.durationMonths,
                "amountBdt" to verifiedPlan.priceBdt.coerceAtLeast(1),
                "paymentMethodId" to verifiedMethodId,
                "paymentMethod" to verifiedMethodName,
                "transactionType" to verifiedTransactionType,
                "receiverNumber" to verifiedReceiverNumber,
                "accountHolderName" to verifiedHolderName,
                "transactionReference" to cleanTrx,
                "normalizedTrxId" to normalizedTrxId,
                "senderNumber" to cleanSender,
                "screenshotUrl" to screenshotUrl.trim().take(350000),
                "status" to "PENDING",
                "adminNote" to "",
                "rejectionReason" to "",
                "verifiedBy" to "",
                "verifiedAt" to null,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val lockPayload = mapOf(
                "normalizedTrxId" to normalizedTrxId,
                "transactionReference" to cleanTrx,
                "requestId" to reqId,
                "userId" to uid,
                "status" to "RESERVED",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val adminNotificationPayload = mapOf(
                "recipientId" to SecurityConstants.TRUSTED_SUPER_ADMIN_UID,
                "senderUid" to uid,
                "title" to "নতুন পেমেন্ট রিকোয়েস্ট (৳${verifiedPlan.priceBdt})",
                "message" to "$safeStudentName • $safePlanName • $verifiedMethodName ($verifiedTransactionType) • TrxID: $cleanTrx",
                "notificationType" to "PAYMENT_REQUEST_SUBMITTED",
                "relatedPaymentRequestId" to reqId,
                "studentName" to safeStudentName,
                "planName" to safePlanName,
                "amountBdt" to verifiedPlan.priceBdt.coerceAtLeast(1),
                "paymentMethod" to verifiedMethodName,
                "transactionId" to cleanTrx,
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val batch = db.batch()
            batch.set(reqRef, requestPayload)
            batch.set(lockRef, lockPayload)
            batch.set(adminNotifRef, adminNotificationPayload)
            batch.commit().await()

            Result.success(reqId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reqRef.path)
            val fsException = generateSequence<Throwable>(e) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsException?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                fsException?.code == FirebaseFirestoreException.Code.ALREADY_EXISTS
            ) {
                val raceLockExists = try {
                    lockRef.get(Source.SERVER).await().exists()
                } catch (_: Exception) {
                    false
                }
                if (raceLockExists) {
                    return Result.failure(
                        IllegalStateException("এই Transaction ID ($cleanTrx) ইতিমধ্যে ব্যবহৃত হয়েছে। একই Transaction ID দিয়ে একাধিকবার সাবস্ক্রিপশন নেওয়া যাবে না।")
                    )
                }
            }
            Result.failure(e)
        }
    }

    companion object {
        private fun Subscription.matchesClassOrLevel(
            targetClass: Int,
            plans: List<SubscriptionPlan>,
            educationLevels: List<EducationLevel>
        ): Boolean {
            if (classNumber == targetClass) return true
            if (classNumber != 0) return false
            val plan = plans.firstOrNull { it.id == planId } ?: return false
            val levelId = plan.educationLevelId.takeIf { it.isNotBlank() } ?: return false
            val level = educationLevels.firstOrNull { it.id == levelId }
                ?: DefaultCatalogSeed.defaultEducationLevels.firstOrNull { it.id == levelId }
                ?: return false
            return level.minClassNumber > 0 &&
                level.maxClassNumber >= level.minClassNumber &&
                targetClass in level.minClassNumber..level.maxClassNumber
        }

        /**
         * Evaluates class-based (and stream-based for Classes 9 & 10) premium access eligibility.
         * One class subscription unlocks BOTH Original Textbooks AND Guides/Notes of that class,
         * while keeping other classes (and non-matching streams in 9-10) locked.
         */
        fun checkPremiumEligibility(
            isContentPremium: Boolean,
            contentClass: Int,
            contentStream: String,
            currentUserUid: String?,
            userProfile: UserProfile?,
            userSubscriptions: List<Subscription>,
            nowMillis: Long = System.currentTimeMillis(),
            plans: List<SubscriptionPlan> = emptyList(),
            educationLevels: List<EducationLevel> = emptyList()
        ): PremiumAccessDecision {
            if (currentUserUid.isNullOrBlank()) return PremiumAccessDecision.DENIED_UNAUTHENTICATED
            if (SecurityConstants.isTrustedSuperAdmin(currentUserUid)) return PremiumAccessDecision.ALLOWED_ADMIN
            if (userProfile != null && !userProfile.isActive) return PremiumAccessDecision.DENIED_ACCOUNT_INACTIVE

            // If content is outside Classes 5..10 and not premium, it is free
            if (contentClass !in 5..10 && !isContentPremium) return PremiumAccessDecision.ALLOWED_FREE

            // 1. Filter by exact class (or matching Education Level for level-wide subscriptions)
            val matchingClassSubs = userSubscriptions.filter {
                it.matchesClassOrLevel(contentClass, plans, educationLevels)
            }
            if (matchingClassSubs.isEmpty()) {
                return PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED
            }

            // 2. Strict stream-level matching for Classes 9 and 10
            val matchingStreamSubs = if (contentClass in 9..10) {
                val normContentStream = contentStream.uppercase().trim()
                if (normContentStream == "ALL" || normContentStream.isBlank()) {
                    // Common subjects for Class 9/10 (e.g. Bangla, English, Math): accessible by any active stream subscription in this class
                    matchingClassSubs
                } else {
                    // Stream-specific subject (SCIENCE, HUMANITIES, BUSINESS): match exact stream OR "ALL" (if admin added an ALL-stream / level-wide plan)
                    matchingClassSubs.filter {
                        it.stream.equals(normContentStream, ignoreCase = true) ||
                            it.stream.equals("ALL", ignoreCase = true)
                    }
                }
            } else {
                // Classes 5–8 and 11+: subscription unlocks all subjects of that class
                matchingClassSubs
            }

            if (matchingStreamSubs.isEmpty()) {
                return PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED
            }

            if (matchingStreamSubs.any { it.isCurrentlyActive(nowMillis) }) {
                return PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION
            }

            return PremiumAccessDecision.DENIED_SUBSCRIPTION_EXPIRED
        }

        fun evaluateClassAndStreamAccess(
            classNumber: Int,
            stream: String,
            currentUserUid: String?,
            userProfile: UserProfile?,
            userSubscriptions: List<Subscription>,
            nowMillis: Long = System.currentTimeMillis(),
            plans: List<SubscriptionPlan> = emptyList(),
            educationLevels: List<EducationLevel> = emptyList()
        ): Boolean {
            if (currentUserUid.isNullOrBlank()) return false
            if (SecurityConstants.isTrustedSuperAdmin(currentUserUid)) return true
            if (userProfile != null && !userProfile.isActive) return false

            val classSubs = userSubscriptions.filter {
                it.matchesClassOrLevel(classNumber, plans, educationLevels)
            }
            if (classSubs.isEmpty()) return false

            val streamSubs = if (classNumber in 9..10) {
                val norm = stream.uppercase().trim()
                if (norm == "ALL" || norm.isBlank()) {
                    classSubs
                } else {
                    classSubs.filter {
                        it.stream.equals(norm, ignoreCase = true) ||
                            it.stream.equals("ALL", ignoreCase = true)
                    }
                }
            } else {
                classSubs
            }

            return streamSubs.any { it.isCurrentlyActive(nowMillis) }
        }
    }
}
