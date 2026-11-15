package com.example.data

import android.content.Context
import com.example.firebase.FirebaseProvider
import com.example.firebase.OperationType
import com.example.firebase.SecurityConstants
import com.example.firebase.handleFirestoreError
import com.example.firebase.toSafeModel
import com.example.firebase.toSafeModels
import com.example.model.AcademicStream
import com.example.model.AppNotification
import com.example.model.AppSettings
import com.example.model.ContentClassGroupHelper
import com.example.model.DefaultCatalogSeed
import com.example.model.EducationLevel
import com.example.model.ExamAnswerKey
import com.example.model.ExamTest
import com.example.model.GeneralKnowledgeItem
import com.example.model.GuideNote
import com.example.model.Notice
import com.example.model.PaymentMethodConfig
import com.example.model.PaymentMethodConstants
import com.example.model.SchoolClass
import com.example.model.Subject
import com.example.model.Subscription
import com.example.model.SubscriptionPlan
import com.example.model.SubscriptionRequest
import com.example.model.Textbook
import com.example.model.UserProfile
import com.example.model.sortedBySerialOrder
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseProvider.getFirestore(context),
        auth = FirebaseProvider.getAuth(context)
    )

    private fun requireSuperAdminUid(): String {
        val uid = auth.currentUser?.uid
        check(SecurityConstants.isTrustedSuperAdmin(uid)) {
            "অননুমোদিত প্রবেশাধিকার: শুধুমাত্র অনুমোদিত সুপার অ্যাডমিন এই কাজটি করতে পারবেন।"
        }
        return uid!!
    }

    // --- Admin Streams (All documents including unpublished/inactive) ---

    fun observeAllUsers(): Flow<List<UserProfile>> {
        val path = "users"
        return db.collection(path).snapshots()
            .map { it.toSafeModels(UserProfile::class.java) }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    private fun observeSettingsSafe(): Flow<AppSettings> =
        db.collection("app_settings").document("global")
            .snapshots()
            .map { snapshot -> snapshot.toSafeModel(AppSettings::class.java) ?: AppSettings() }
            .catch { err ->
                if (err is CancellationException) throw err
                emit(AppSettings())
            }

    fun observeAllEducationLevels(): Flow<List<EducationLevel>> {
        val path = "education_levels"
        val levelsFlow = db.collection(path).snapshots()
            .map { it.toSafeModels(EducationLevel::class.java) }
        return combine(levelsFlow, observeSettingsSafe()) { remoteList, settings ->
            val deletedIds = settings.deletedDefaultLevelIds.toSet()
            val remoteById = remoteList.associateBy { it.id }
            val defaults = DefaultCatalogSeed.defaultEducationLevels.filter { it.id !in deletedIds }
            buildList {
                defaults.forEach { def -> add(remoteById[def.id] ?: def) }
                remoteList.filter { remote -> defaults.none { it.id == remote.id } }.forEach { add(it) }
            }.sortedWith(compareBy<EducationLevel> { it.sortOrder }.thenBy { it.minClassNumber }.thenBy { it.nameEn })
        }.catch { e ->
            if (e is CancellationException) throw e
            if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    fun observeAllClasses(): Flow<List<SchoolClass>> {
        val path = "classes"
        val classesFlow = db.collection(path).snapshots()
            .map { it.toSafeModels(SchoolClass::class.java) }
        return combine(classesFlow, observeSettingsSafe()) { remoteList, settings ->
            val deletedIds = settings.deletedDefaultClassIds.toSet()
            val remoteById = remoteList.associateBy { it.id }
            val defaults = DefaultCatalogSeed.defaultClasses.filter { it.id !in deletedIds }
            buildList {
                defaults.forEach { def -> add(remoteById[def.id] ?: def) }
                remoteList.filter { remote -> defaults.none { it.id == remote.id } }.forEach { add(it) }
            }.sortedBySerialOrder()
        }.catch { e ->
            if (e is CancellationException) throw e
            if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    fun observeAllStreams(): Flow<List<AcademicStream>> {
        val path = "streams"
        return db.collection(path).snapshots()
            .map {
                it.toSafeModels(AcademicStream::class.java).sortedBy { s -> s.sortOrder }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAllSubjects(): Flow<List<Subject>> {
        val path = "subjects"
        return db.collection(path).snapshots()
            .map {
                it.toSafeModels(Subject::class.java)
                    .sortedWith(compareBy<Subject> { s -> s.classNumber }.thenBy { s -> s.sortOrder })
            }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAllBooks(): Flow<List<Textbook>> {
        val path = "books"
        return db.collection(path).snapshots()
            .map { it.toSafeModels(Textbook::class.java) }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAllGuides(): Flow<List<GuideNote>> {
        val path = "guides"
        return db.collection(path).snapshots()
            .map { it.toSafeModels(GuideNote::class.java) }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAllGk(): Flow<List<GeneralKnowledgeItem>> {
        val path = "general_knowledge"
        return db.collection(path).snapshots()
            .map { it.toSafeModels(GeneralKnowledgeItem::class.java) }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAllExams(): Flow<List<ExamTest>> {
        val path = "exam_preparation"
        return db.collection(path).snapshots()
            .map { it.toSafeModels(ExamTest::class.java) }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAllPlans(): Flow<List<SubscriptionPlan>> {
        val path = "subscription_plans"
        val plansFlow = db.collection(path).snapshots()
            .map { it.toSafeModels(SubscriptionPlan::class.java) }
        return combine(plansFlow, observeSettingsSafe()) { remoteList, settings ->
            val deletedIds = settings.deletedDefaultPlanIds.toSet()
            val remoteById = remoteList.associateBy { it.id }
            val defaults = DefaultCatalogSeed.defaultAnnualPackages.filter { it.id !in deletedIds }
            buildList {
                defaults.forEach { def -> add(remoteById[def.id] ?: def) }
                remoteList.filter { remote -> defaults.none { it.id == remote.id } }.forEach { add(it) }
            }.sortedWith(
                compareBy<SubscriptionPlan> { p -> p.classNumber }
                    .thenBy { p -> p.durationMonths }
                    .thenBy { p -> p.stream }
                    .thenBy { p -> p.nameBn }
            )
        }.catch { e ->
            if (e is CancellationException) throw e
            if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
            throw e
        }
    }

    fun observeAllPaymentMethods(): Flow<List<PaymentMethodConfig>> {
        val path = "payment_methods"
        return db.collection(path).snapshots()
            .map {
                it.toSafeModels(PaymentMethodConfig::class.java)
                    .sortedWith(compareBy<PaymentMethodConfig> { m -> m.displayOrder }.thenBy { m -> m.methodName })
            }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAllSubscriptionRequests(): Flow<List<SubscriptionRequest>> {
        val path = "subscription_requests"
        return db.collection(path).snapshots()
            .map {
                it.toSafeModels(SubscriptionRequest::class.java)
                    .sortedByDescending { r -> r.createdAt ?: Timestamp(0, 0) }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAllSubscriptions(): Flow<List<Subscription>> {
        val path = "subscriptions"
        return db.collection(path).snapshots()
            .map {
                it.toSafeModels(Subscription::class.java)
                    .sortedByDescending { s -> s.expiryDate ?: Timestamp(0, 0) }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAdminNotifications(): Flow<List<AppNotification>> {
        val path = "notifications"
        return db.collection(path)
            .whereEqualTo("recipientId", SecurityConstants.TRUSTED_SUPER_ADMIN_UID)
            .snapshots()
            .map {
                it.toSafeModels(AppNotification::class.java)
                    .sortedByDescending { n -> n.createdAt ?: Timestamp(0, 0) }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    fun observeAllNotices(): Flow<List<Notice>> {
        val path = "notices"
        return db.collection(path).snapshots()
            .map {
                it.toSafeModels(Notice::class.java)
                    .sortedByDescending { n -> n.createdAt ?: Timestamp(0, 0) }
            }
            .catch { e ->
                if (e is CancellationException) throw e
                if (e is Exception && auth.currentUser != null) handleFirestoreError(e, OperationType.LIST, path)
                throw e
            }
    }

    // --- A & B: Class & Stream Management (Preserves existing documents!) ---

    private suspend fun updateDeletedDefaultIds(
        mutate: (AppSettings) -> AppSettings
    ) {
        val settingsRef = db.collection("app_settings").document("global")
        val snap = settingsRef.get().await()
        val current = snap.toSafeModel(AppSettings::class.java) ?: AppSettings()
        val updated = mutate(current)
        if (updated != current || !snap.exists()) {
            settingsRef.set(updated.toWriteMap()).await()
        }
    }

    suspend fun initializeMissingClassesAndStreams(): Result<Int> {
        requireSuperAdminUid()
        var createdCount = 0
        return try {
            val settingsSnap = db.collection("app_settings").document("global").get().await()
            val settings = settingsSnap.toSafeModel(AppSettings::class.java) ?: AppSettings()
            val deletedClasses = settings.deletedDefaultClassIds.toSet()
            val deletedLevels = settings.deletedDefaultLevelIds.toSet()

            for (level in DefaultCatalogSeed.defaultEducationLevels.filter { it.id !in deletedLevels }) {
                val ref = db.collection("education_levels").document(level.id)
                if (!ref.get().await().exists()) {
                    ref.set(level.toWriteMap(isCreate = true)).await()
                    createdCount++
                }
            }
            for (cls in DefaultCatalogSeed.defaultClasses.filter { it.id !in deletedClasses }) {
                val ref = db.collection("classes").document(cls.id)
                if (!ref.get().await().exists()) {
                    ref.set(cls.toWriteMap(isCreate = true)).await()
                    createdCount++
                }
            }
            for (stream in DefaultCatalogSeed.defaultStreams) {
                val ref = db.collection("streams").document(stream.id)
                if (!ref.get().await().exists()) {
                    ref.set(stream.toWriteMap(isCreate = true)).await()
                    createdCount++
                }
            }
            Result.success(createdCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "classes")
            Result.failure(e)
        }
    }

    suspend fun saveEducationLevel(level: EducationLevel): Result<Unit> {
        requireSuperAdminUid()
        val cleanCode = level.code.trim().uppercase()
            .ifBlank { level.nameEn.trim().uppercase().replace(Regex("[^A-Z0-9_]+"), "_") }
            .ifBlank { "LEVEL" }
            .take(40)
        val docId = level.id.ifBlank {
            "level_${cleanCode.lowercase()}_${UUID.randomUUID().toString().replace("-", "").take(6)}"
        }
        val docRef = db.collection("education_levels").document(docId)
        return try {
            val existing = docRef.get().await()
            val model = if (existing.exists()) {
                level.copy(id = docId, code = cleanCode, createdAt = existing.getTimestamp("createdAt") ?: level.createdAt)
            } else {
                level.copy(id = docId, code = cleanCode)
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            if (DefaultCatalogSeed.defaultEducationLevels.any { it.id == docId }) {
                updateDeletedDefaultIds { cur ->
                    cur.copy(deletedDefaultLevelIds = cur.deletedDefaultLevelIds.filterNot { it == docId })
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteEducationLevel(levelId: String): Result<Unit> {
        requireSuperAdminUid()
        if (levelId.isBlank()) return Result.failure(IllegalArgumentException("অবৈধ শিক্ষা স্তর আইডি।"))
        val docRef = db.collection("education_levels").document(levelId)
        return try {
            docRef.delete().await()
            if (DefaultCatalogSeed.defaultEducationLevels.any { it.id == levelId }) {
                updateDeletedDefaultIds { cur ->
                    cur.copy(deletedDefaultLevelIds = (cur.deletedDefaultLevelIds + levelId).distinct())
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveSchoolClass(schoolClass: SchoolClass): Result<Unit> {
        requireSuperAdminUid()
        val safeClassNumber = schoolClass.classNumber.coerceIn(1, 30)
        val docId = schoolClass.id.ifBlank { "class_$safeClassNumber" }
        val docRef = db.collection("classes").document(docId)
        return try {
            // Prevent duplicate class numbers across active/saved classes
            val remoteClasses = db.collection("classes").get().await().toSafeModels(SchoolClass::class.java)
            val duplicate = remoteClasses.firstOrNull { it.classNumber == safeClassNumber && it.id != docId }
            if (duplicate != null) {
                return Result.failure(
                    IllegalStateException("শ্রেণি $safeClassNumber (${duplicate.displayNameBn}) ইতিমধ্যে বিদ্যমান। একই নম্বর দিয়ে ডুপ্লিকেট শ্রেণি তৈরি করা যাবে না।")
                )
            }
            val existing = docRef.get().await()
            val model = if (existing.exists()) {
                schoolClass.copy(
                    id = docId,
                    classNumber = safeClassNumber,
                    createdAt = existing.getTimestamp("createdAt") ?: schoolClass.createdAt
                )
            } else {
                schoolClass.copy(id = docId, classNumber = safeClassNumber)
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            if (DefaultCatalogSeed.defaultClasses.any { it.id == docId }) {
                updateDeletedDefaultIds { cur ->
                    cur.copy(deletedDefaultClassIds = cur.deletedDefaultClassIds.filterNot { it == docId })
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSchoolClass(classId: String): Result<Unit> {
        requireSuperAdminUid()
        if (classId.isBlank()) return Result.failure(IllegalArgumentException("অবৈধ শ্রেণি আইডি।"))
        val docRef = db.collection("classes").document(classId)
        return try {
            docRef.delete().await()
            if (DefaultCatalogSeed.defaultClasses.any { it.id == classId }) {
                updateDeletedDefaultIds { cur ->
                    cur.copy(deletedDefaultClassIds = (cur.deletedDefaultClassIds + classId).distinct())
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveStream(stream: AcademicStream): Result<Unit> {
        requireSuperAdminUid()
        val docId = stream.id.ifBlank { "stream_${stream.code.lowercase()}" }
        val docRef = db.collection("streams").document(docId)
        return try {
            val existing = docRef.get().await()
            val model = if (existing.exists()) {
                stream.copy(createdAt = existing.getTimestamp("createdAt") ?: stream.createdAt)
            } else {
                stream
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    // --- C: Subject Management ---

    suspend fun saveSubject(subject: Subject): Result<Unit> {
        requireSuperAdminUid()
        val docId = subject.id.ifBlank { "subj_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
        val docRef = db.collection("subjects").document(docId)
        return try {
            val existing = docRef.get().await()
            val model = if (existing.exists()) {
                subject.copy(createdAt = existing.getTimestamp("createdAt") ?: subject.createdAt)
            } else {
                subject
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubject(subjectId: String): Result<Unit> = deleteAdminDoc("subjects", subjectId)

    // --- D: Book Management ---

    suspend fun saveBook(book: Textbook): Result<Unit> {
        requireSuperAdminUid()
        val isNewCreate = book.id.isBlank()
        val docId = if (isNewCreate) {
            "book_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        } else {
            book.id
        }
        val docRef = db.collection("books").document(docId)
        return try {
            val existing = docRef.get().await()
            if (isNewCreate && existing.exists()) {
                return Result.failure(IllegalStateException("একই আইডির একটি পাঠ্যবই ইতিমধ্যে বিদ্যমান রয়েছে।"))
            }
            val model = if (existing.exists()) {
                book.copy(createdAt = existing.getTimestamp("createdAt") ?: book.createdAt)
            } else {
                book
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteBook(bookId: String): Result<Unit> = deleteGroupedContentDocs("books", bookId)

    // --- E: Guide Management ---

    suspend fun saveGuide(guide: GuideNote): Result<Unit> {
        requireSuperAdminUid()
        val isNewCreate = guide.id.isBlank()
        val docId = if (isNewCreate) {
            "guide_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        } else {
            guide.id
        }
        val docRef = db.collection("guides").document(docId)
        return try {
            val existing = docRef.get().await()
            if (isNewCreate && existing.exists()) {
                return Result.failure(IllegalStateException("একই আইডির একটি গাইড ইতিমধ্যে বিদ্যমান রয়েছে।"))
            }
            val model = if (existing.exists()) {
                guide.copy(createdAt = existing.getTimestamp("createdAt") ?: guide.createdAt)
            } else {
                guide
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteGuide(guideId: String): Result<Unit> = deleteGroupedContentDocs("guides", guideId)

    private suspend fun deleteGroupedContentDocs(collection: String, primaryId: String): Result<Unit> {
        requireSuperAdminUid()
        val cleanId = primaryId.trim()
        if (cleanId.isBlank()) return Result.failure(IllegalArgumentException("Invalid document id"))
        val targetGroupKey = ContentClassGroupHelper.groupKeyForId(cleanId)
        val primaryRef = db.collection(collection).document(cleanId)
        return try {
            val matchedRefs = linkedMapOf(cleanId to primaryRef)
            val allDocs = runCatching { db.collection(collection).get().await().documents }.getOrElse { emptyList() }
            for (doc in allDocs) {
                if (ContentClassGroupHelper.groupKeyForId(doc.id) == targetGroupKey) {
                    matchedRefs[doc.id] = doc.reference
                }
            }
            if (matchedRefs.size <= 1) {
                primaryRef.delete().await()
            } else {
                val batch = db.batch()
                matchedRefs.values.forEach { batch.delete(it) }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, primaryRef.path)
            Result.failure(e)
        }
    }

    // --- F: General Knowledge Management ---

    suspend fun saveGkItem(item: GeneralKnowledgeItem): Result<Unit> {
        requireSuperAdminUid()
        val docId = item.id.ifBlank { "gk_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection("general_knowledge").document(docId)
        return try {
            val existing = docRef.get().await()
            val model = if (existing.exists()) {
                item.copy(createdAt = existing.getTimestamp("createdAt") ?: item.createdAt)
            } else {
                item
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteGkItem(gkId: String): Result<Unit> = deleteAdminDoc("general_knowledge", gkId)

    // --- G: Examination & Protected Answer Key Management ---

    suspend fun saveExamWithAnswerKey(
        exam: ExamTest,
        correctIndices: List<Int>,
        explanations: List<String>
    ): Result<Unit> {
        requireSuperAdminUid()
        val docId = exam.id.ifBlank { "exam_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val examRef = db.collection("exam_preparation").document(docId)
        val keyRef = db.collection("exam_answer_keys").document(docId)
        return try {
            val existing = examRef.get().await()
            val model = if (existing.exists()) {
                exam.copy(createdAt = existing.getTimestamp("createdAt") ?: exam.createdAt)
            } else {
                exam
            }
            val answerKey = ExamAnswerKey(
                id = docId,
                examId = docId,
                correctIndices = correctIndices.map { it.coerceIn(0, 3).toLong() },
                explanations = explanations.map { it.take(1000) },
                isPublished = exam.isPublished
            )
            val batch = db.batch()
            batch.set(examRef, model.toWriteMap(isCreate = !existing.exists()))
            batch.set(keyRef, answerKey.toWriteMap())
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, examRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteExam(examId: String): Result<Unit> {
        requireSuperAdminUid()
        val examRef = db.collection("exam_preparation").document(examId)
        val keyRef = db.collection("exam_answer_keys").document(examId)
        return try {
            val batch = db.batch()
            batch.delete(examRef)
            batch.delete(keyRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, examRef.path)
            Result.failure(e)
        }
    }

    // --- H: User Management (Activate / Deactivate — Never exposes passwords) ---

    suspend fun setUserActiveStatus(userId: String, isActive: Boolean): Result<Unit> {
        requireSuperAdminUid()
        val docRef = db.collection("users").document(userId)
        return try {
            docRef.update(
                mapOf(
                    "isActive" to isActive,
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

    // --- I: Payment Methods, Subscription Plans, Payment Notifications & Approval Management ---

    suspend fun savePaymentMethod(method: PaymentMethodConfig): Result<String> {
        requireSuperAdminUid()
        if (method.accountNumber.trim().length < 4) {
            return Result.failure(IllegalArgumentException("সঠিক অ্যাকাউন্ট / মোবাইল নম্বর প্রদান করুন (কমপক্ষে ৪ অঙ্ক)।"))
        }
        if (method.isBankTransfer) {
            if (method.bankName.trim().length < 2) {
                return Result.failure(IllegalArgumentException("ব্যাংক ট্রান্সফারের জন্য ব্যাংকের নাম আবশ্যক।"))
            }
            if (method.accountHolderName.trim().length < 2) {
                return Result.failure(IllegalArgumentException("ব্যাংক ট্রান্সফারের জন্য হিসাবধারীর নাম (Account Holder Name) আবশ্যক।"))
            }
        }
        val docId = method.id.ifBlank {
            "pm_${method.methodType.lowercase()}_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        }
        val docRef = db.collection("payment_methods").document(docId)
        return try {
            val existing = docRef.get().await()
            val model = if (existing.exists()) {
                method.copy(id = docId, createdAt = existing.getTimestamp("createdAt") ?: method.createdAt)
            } else {
                method.copy(id = docId)
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun togglePaymentMethodActive(method: PaymentMethodConfig, isActive: Boolean): Result<Unit> {
        return savePaymentMethod(method.copy(isActive = isActive)).map { }
    }

    suspend fun deletePaymentMethod(methodId: String): Result<Unit> =
        deleteAdminDoc("payment_methods", methodId)

    suspend fun saveSubscriptionPlan(plan: SubscriptionPlan): Result<Unit> {
        requireSuperAdminUid()
        val safeClass = plan.classNumber.coerceIn(0, 30)
        val safeDuration = if (plan.durationMonths in listOf(1, 3, 6, 12)) plan.durationMonths else 12
        val safeStream = if (safeClass in 9..10) {
            plan.stream.uppercase().trim().takeIf { it in setOf("ALL", "SCIENCE", "HUMANITIES", "BUSINESS") } ?: "ALL"
        } else {
            "ALL"
        }
        val safePrice = plan.priceBdt.coerceIn(1, 100_000)
        val docId = plan.id.ifBlank { "plan_${UUID.randomUUID().toString().replace("-", "").take(14)}" }
        val docRef = db.collection("subscription_plans").document(docId)
        return try {
            val existing = docRef.get().await()
            val base = plan.copy(
                id = docId,
                classNumber = safeClass,
                durationMonths = safeDuration,
                stream = safeStream,
                priceBdt = safePrice
            )
            val model = if (existing.exists()) {
                base.copy(createdAt = existing.getTimestamp("createdAt") ?: plan.createdAt)
            } else {
                base
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            if (DefaultCatalogSeed.defaultAnnualPackages.any { it.id == docId }) {
                updateDeletedDefaultIds { cur ->
                    cur.copy(deletedDefaultPlanIds = cur.deletedDefaultPlanIds.filterNot { it == docId })
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubscriptionPlan(planId: String): Result<Unit> {
        requireSuperAdminUid()
        if (planId.isBlank()) return Result.failure(IllegalArgumentException("অবৈধ প্যাকেজ আইডি।"))
        val docRef = db.collection("subscription_plans").document(planId)
        return try {
            docRef.delete().await()
            if (DefaultCatalogSeed.defaultAnnualPackages.any { it.id == planId }) {
                updateDeletedDefaultIds { cur ->
                    cur.copy(deletedDefaultPlanIds = (cur.deletedDefaultPlanIds + planId).distinct())
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun markAdminNotificationRead(notificationId: String): Result<Unit> {
        requireSuperAdminUid()
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

    suspend fun markAllAdminNotificationsRead(unreadNotifications: List<AppNotification>): Result<Unit> {
        requireSuperAdminUid()
        val unread = unreadNotifications.filter { !it.isRead && it.id.isNotBlank() }
        if (unread.isEmpty()) return Result.success(Unit)
        return try {
            unread.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { n ->
                    val ref = db.collection("notifications").document(n.id)
                    batch.update(
                        ref,
                        mapOf(
                            "isRead" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "notifications")
            Result.failure(e)
        }
    }

    suspend fun approveSubscriptionRequest(
        request: SubscriptionRequest,
        adminNote: String = "পেমেন্ট যাচাই সম্পন্ন ও অনুমোদিত হয়েছে।",
        relatedAdminNotificationIds: List<String> = emptyList()
    ): Result<Unit> {
        val adminUid = requireSuperAdminUid()
        val reqRef = db.collection("subscription_requests").document(request.id)
        val normalizedStream = if (request.classNumber in 9..10) {
            request.stream.uppercase().trim().ifBlank { "SCIENCE" }
        } else {
            "ALL"
        }
        val subId = if (request.classNumber == 0) {
            "sub_${request.userId}_plan_${request.planId}".take(120)
        } else {
            "sub_${request.userId}_c${request.classNumber}_$normalizedStream".take(120)
        }
        val subRef = db.collection("subscriptions").document(subId)
        val normalizedTrxId = request.normalizedTrxId.ifBlank {
            PaymentMethodConstants.normalizeTransactionId(request.transactionReference)
        }
        val lockRef = db.collection("used_transaction_ids").document(normalizedTrxId)
        val studentNotifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val studentNotifRef = db.collection("notifications").document(studentNotifId)

        return try {
            val nowMillis = System.currentTimeMillis()
            val dhakaTz = BANGLADESH_TIMEZONE
            val currentYear = Calendar.getInstance(dhakaTz).apply { timeInMillis = nowMillis }.get(Calendar.YEAR)

            // Rule 6: পুরোনো রেকর্ডে বছর না থাকলে অনুমান করে বর্তমান বছরের অনুমোদন দেওয়া যাবে না।
            if (request.createdAt != null) {
                val reqYear = Calendar.getInstance(dhakaTz).apply {
                    timeInMillis = request.createdAt.toDate().time
                }.get(Calendar.YEAR)
                if (reqYear < currentYear) {
                    return Result.failure(
                        IllegalStateException("এই পেমেন্ট রিকোয়েস্টটি পূর্ববর্তী শিক্ষাবর্ষের ($reqYear)। বর্তমান শিক্ষাবর্ষে ($currentYear) এটি অনুমোদন করা যাবে না।")
                    )
                }
            }

            db.runTransaction { transaction ->
                val freshReqSnap = transaction.get(reqRef)
                if (!freshReqSnap.exists()) {
                    throw IllegalStateException("পেমেন্ট রিকোয়েস্টটি ডাটাবেজে পাওয়া যায়নি।")
                }
                val currentStatus = freshReqSnap.getString("status") ?: request.status
                if (currentStatus != "PENDING") {
                    throw IllegalStateException("এই পেমেন্ট রিকোয়েস্টটি ইতিমধ্যে প্রক্রিয়াকৃত ($currentStatus) হয়েছে।")
                }

                // Guard against approving a duplicate Transaction ID already approved for another request
                val lockSnap = transaction.get(lockRef)
                if (lockSnap.exists()) {
                    val lockOwnerReqId = lockSnap.getString("requestId").orEmpty()
                    val lockStatus = lockSnap.getString("status").orEmpty()
                    if (lockOwnerReqId.isNotBlank() && lockOwnerReqId != request.id && lockStatus == "APPROVED") {
                        throw IllegalStateException(
                            "সতর্কতা: এই Transaction ID (${request.transactionReference}) অন্য একটি অনুমোদিত পেমেন্টে ইতিমধ্যে ব্যবহৃত হয়েছে!"
                        )
                    }
                }

                val existingSub = transaction.get(subRef)
                val existingExpiryMillis = existingSub.getTimestamp("expiryDate")?.toDate()?.time ?: 0L
                val existingStatus = existingSub.getString("status").orEmpty()

                // Never extend expired subscriptions automatically.
                // Only if existing subscription is CURRENTLY ACTIVE and in the future, start from its recorded startDate
                val isCurrentlyActiveRenewal = existingSub.exists() && existingStatus == "ACTIVE" && existingExpiryMillis > nowMillis
                val startTs: Timestamp = if (isCurrentlyActiveRenewal) {
                    existingSub.getTimestamp("startDate") ?: Timestamp.now()
                } else {
                    Timestamp.now()
                }

                // Subscriptions approved in current year expire on December 31 at 11:59:59 PM Bangladesh time (Asia/Dhaka).
                val expiryDate = calculateAnnualExpiryDate(
                    approvalTimeMillis = nowMillis,
                    hasActiveFutureSub = isCurrentlyActiveRenewal,
                    existingExpiryMillis = existingExpiryMillis
                )
                val expiryTs = Timestamp(expiryDate)

                transaction.update(
                    reqRef,
                    mapOf(
                        "status" to "APPROVED",
                        "adminNote" to adminNote.trim().ifBlank { "পেমেন্ট যাচাই সম্পন্ন ও অনুমোদিত হয়েছে।" }.take(500),
                        "rejectionReason" to "",
                        "verifiedBy" to adminUid,
                        "verifiedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.set(
                    subRef,
                    mapOf(
                        "userId" to request.userId,
                        "planId" to request.planId,
                        "planNameBn" to request.planNameBn,
                        "classNumber" to request.classNumber,
                        "stream" to normalizedStream,
                        "durationMonths" to request.durationMonths,
                        "amountBdt" to request.amountBdt,
                        "paymentMethod" to request.paymentMethod,
                        "transactionReference" to request.transactionReference,
                        "status" to "ACTIVE",
                        "startDate" to startTs,
                        "expiryDate" to expiryTs,
                        "approvedByUid" to adminUid,
                        "requestId" to request.id,
                        "createdAt" to (if (existingSub.exists()) (existingSub.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()) else FieldValue.serverTimestamp()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.set(
                    lockRef,
                    mapOf(
                        "normalizedTrxId" to normalizedTrxId,
                        "transactionReference" to request.transactionReference,
                        "requestId" to request.id,
                        "userId" to request.userId,
                        "status" to "APPROVED",
                        "createdAt" to (if (lockSnap.exists()) (lockSnap.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()) else FieldValue.serverTimestamp()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                transaction.set(
                    studentNotifRef,
                    mapOf(
                        "recipientId" to request.userId,
                        "senderUid" to adminUid,
                        "title" to "পেমেন্ট অনুমোদিত হয়েছে — সাবস্ক্রিপশন চালু!",
                        "message" to "আপনার ${request.planNameBn} (৳${request.amountBdt}, TrxID: ${request.transactionReference}) পেমেন্টটি যাচাই ও অনুমোদিত হয়েছে। আপনার প্রিমিয়াম সাবস্ক্রিপশন এখন সক্রিয়!",
                        "notificationType" to "PAYMENT_APPROVED",
                        "relatedPaymentRequestId" to request.id,
                        "studentName" to request.userName.take(120),
                        "planName" to request.planNameBn.take(120),
                        "amountBdt" to request.amountBdt.coerceAtLeast(0),
                        "paymentMethod" to request.paymentMethod.take(60),
                        "transactionId" to request.transactionReference.take(100),
                        "isRead" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                relatedAdminNotificationIds.filter { it.isNotBlank() }.distinct().take(20).forEach { nId ->
                    transaction.update(
                        db.collection("notifications").document(nId),
                        mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp())
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, subRef.path)
            Result.failure(e)
        }
    }

    suspend fun rejectSubscriptionRequest(
        request: SubscriptionRequest,
        rejectionReason: String,
        relatedAdminNotificationIds: List<String> = emptyList()
    ): Result<Unit> {
        val adminUid = requireSuperAdminUid()
        val cleanReason = rejectionReason.trim().ifBlank { "ট্রানজেকশন তথ্য যাচাই করা যায়নি।" }.take(500)
        val reqRef = db.collection("subscription_requests").document(request.id)
        val normalizedTrxId = request.normalizedTrxId.ifBlank {
            PaymentMethodConstants.normalizeTransactionId(request.transactionReference)
        }
        val lockRef = db.collection("used_transaction_ids").document(normalizedTrxId)
        val studentNotifId = "notif_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val studentNotifRef = db.collection("notifications").document(studentNotifId)

        return try {
            val freshReqSnap = reqRef.get().await()
            if (!freshReqSnap.exists()) {
                return Result.failure(IllegalStateException("পেমেন্ট রিকোয়েস্টটি ডাটাবেজে পাওয়া যায়নি।"))
            }
            val currentStatus = freshReqSnap.getString("status") ?: request.status
            if (currentStatus != "PENDING") {
                return Result.failure(IllegalStateException("এই পেমেন্ট রিকোয়েস্টটি ইতিমধ্যে প্রক্রিয়াকৃত ($currentStatus) হয়েছে।"))
            }

            val lockSnap = lockRef.get().await()
            val batch = db.batch()
            batch.update(
                reqRef,
                mapOf(
                    "status" to "REJECTED",
                    "rejectionReason" to cleanReason,
                    "adminNote" to cleanReason,
                    "verifiedBy" to adminUid,
                    "verifiedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            // Release RESERVED lock if owned by this rejected request so a legitimate payer is not blocked by someone else's typo
            if (lockSnap.exists() &&
                lockSnap.getString("requestId") == request.id &&
                lockSnap.getString("status") == "RESERVED"
            ) {
                batch.delete(lockRef)
            }
            batch.set(
                studentNotifRef,
                mapOf(
                    "recipientId" to request.userId,
                    "senderUid" to adminUid,
                    "title" to "পেমেন্ট রিকোয়েস্ট বাতিল হয়েছে",
                    "message" to "আপনার ${request.planNameBn} (৳${request.amountBdt}, TrxID: ${request.transactionReference}) পেমেন্ট রিকোয়েস্টটি বাতিল হয়েছে। কারণ: $cleanReason",
                    "notificationType" to "PAYMENT_REJECTED",
                    "relatedPaymentRequestId" to request.id,
                    "studentName" to request.userName.take(120),
                    "planName" to request.planNameBn.take(120),
                    "amountBdt" to request.amountBdt.coerceAtLeast(0),
                    "paymentMethod" to request.paymentMethod.take(60),
                    "transactionId" to request.transactionReference.take(100),
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            relatedAdminNotificationIds.filter { it.isNotBlank() }.distinct().take(20).forEach { nId ->
                batch.update(
                    db.collection("notifications").document(nId),
                    mapOf("isRead" to true, "updatedAt" to FieldValue.serverTimestamp())
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, reqRef.path)
            Result.failure(e)
        }
    }

    suspend fun rejectSubscriptionRequest(
        requestId: String,
        adminNote: String = "ট্রানজেকশন তথ্য যাচাই করা যায়নি।"
    ): Result<Unit> {
        requireSuperAdminUid()
        val reqSnap = db.collection("subscription_requests").document(requestId).get().await()
        val reqObj = reqSnap.toObject(SubscriptionRequest::class.java)?.withDocumentId(requestId)
            ?: return Result.failure(IllegalStateException("পেমেন্ট রিকোয়েস্টটি পাওয়া যায়নি।"))
        return rejectSubscriptionRequest(reqObj, adminNote)
    }

    // --- J: Notice Management ---

    suspend fun saveNotice(notice: Notice): Result<Unit> {
        requireSuperAdminUid()
        val docId = notice.id.ifBlank { "notice_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val docRef = db.collection("notices").document(docId)
        return try {
            val existing = docRef.get().await()
            val model = if (existing.exists()) {
                notice.copy(createdAt = existing.getTimestamp("createdAt") ?: notice.createdAt)
            } else {
                notice
            }
            docRef.set(model.toWriteMap(isCreate = !existing.exists())).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteNotice(noticeId: String): Result<Unit> = deleteAdminDoc("notices", noticeId)

    // --- K: Centralized Download & App Settings ---

    suspend fun saveAppSettings(settings: AppSettings): Result<Unit> {
        requireSuperAdminUid()
        val docRef = db.collection("app_settings").document("global")
        return try {
            docRef.set(settings.toWriteMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    private suspend fun deleteAdminDoc(collection: String, docId: String): Result<Unit> {
        requireSuperAdminUid()
        val docRef = db.collection(collection).document(docId)
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

    companion object {
        val BANGLADESH_TIMEZONE: TimeZone = TimeZone.getTimeZone("Asia/Dhaka")

        /**
         * Calculates annual subscription expiry in accordance with Bangladesh Academic Year rules:
         * - Subscriptions approved in 2026 expire on December 31, 2026 at 11:59:59 PM Asia/Dhaka.
         * - Subscriptions approved in 2027 expire on December 31, 2027 at 11:59:59 PM Asia/Dhaka.
         * - Never extends expired subscriptions automatically.
         */
        fun calculateAnnualExpiryDate(
            approvalTimeMillis: Long = System.currentTimeMillis(),
            hasActiveFutureSub: Boolean = false,
            existingExpiryMillis: Long = 0L
        ): Date {
            val dhakaTz = BANGLADESH_TIMEZONE
            val approvalCal = Calendar.getInstance(dhakaTz).apply { timeInMillis = approvalTimeMillis }
            val approvalYear = approvalCal.get(Calendar.YEAR)

            val targetYear = if (hasActiveFutureSub && existingExpiryMillis > approvalTimeMillis) {
                val existingCal = Calendar.getInstance(dhakaTz).apply { timeInMillis = existingExpiryMillis }
                val existingYear = existingCal.get(Calendar.YEAR)
                if (existingYear >= approvalYear) approvalYear + 1 else approvalYear
            } else {
                approvalYear
            }

            val expiryCal = Calendar.getInstance(dhakaTz).apply {
                set(Calendar.YEAR, targetYear)
                set(Calendar.MONTH, Calendar.DECEMBER)
                set(Calendar.DAY_OF_MONTH, 31)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 0)
            }
            return expiryCal.time
        }
    }
}
