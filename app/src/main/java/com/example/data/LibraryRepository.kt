package com.example.data

import android.content.Context
import com.example.firebase.FirebaseProvider
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.example.firebase.toSafeModel
import com.example.firebase.toSafeModels
import com.example.model.AcademicStream
import com.example.model.AppSettings
import com.example.model.Bookmark
import com.example.model.DefaultCatalogSeed
import com.example.model.EducationLevel
import com.example.model.GuideNote
import com.example.model.Notice
import com.example.model.ReadingProgress
import com.example.model.SchoolClass
import com.example.model.Subject
import com.example.model.Textbook
import com.example.model.sortedBySerialOrder
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await

class LibraryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseProvider.getFirestore(context),
        auth = FirebaseProvider.getAuth(context)
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be authenticated")

    private fun observeSettingsSafe(): Flow<AppSettings> =
        db.collection("app_settings").document("global")
            .snapshots()
            .map { snapshot -> snapshot.toSafeModel(AppSettings::class.java) ?: AppSettings() }
            .catch { err ->
                if (err is CancellationException) throw err
                emit(AppSettings())
            }

    fun observeActiveEducationLevels(): Flow<List<EducationLevel>> {
        val path = "education_levels"
        val levelsFlow = db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot -> snapshot.toSafeModels(EducationLevel::class.java) }
        return combine(levelsFlow, observeSettingsSafe()) { remoteList, settings ->
            val deletedIds = settings.deletedDefaultLevelIds.toSet()
            val remoteById = remoteList.associateBy { it.id }
            val defaults = DefaultCatalogSeed.defaultEducationLevels.filter { it.id !in deletedIds }
            val merged = buildList {
                defaults.forEach { def ->
                    add(remoteById[def.id] ?: def)
                }
                remoteList.filter { remote -> defaults.none { it.id == remote.id } }
                    .forEach { add(it) }
            }
            merged.filter { it.isActive }
                .sortedWith(compareBy<EducationLevel> { it.sortOrder }.thenBy { it.minClassNumber }.thenBy { it.nameEn })
        }.catch { error ->
            if (error is CancellationException) throw error
            if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
            throw error
        }
    }

    fun observeActiveClasses(): Flow<List<SchoolClass>> {
        val path = "classes"
        val classesFlow = db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot -> snapshot.toSafeModels(SchoolClass::class.java) }
        return combine(classesFlow, observeSettingsSafe()) { remoteList, settings ->
            val deletedIds = settings.deletedDefaultClassIds.toSet()
            val remoteById = remoteList.associateBy { it.id }
            val defaults = DefaultCatalogSeed.defaultClasses.filter { it.id !in deletedIds }
            val merged = buildList {
                defaults.forEach { def ->
                    add(remoteById[def.id] ?: def)
                }
                remoteList.filter { remote -> defaults.none { it.id == remote.id } }
                    .forEach { add(it) }
            }
            merged.filter { it.isActive }.sortedBySerialOrder()
        }.catch { error ->
            if (error is CancellationException) throw error
            if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
            throw error
        }
    }

    fun observeActiveStreams(): Flow<List<AcademicStream>> {
        val path = "streams"
        return db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toSafeModels(AcademicStream::class.java).sortedBy { it.sortOrder }
                list.ifEmpty { DefaultCatalogSeed.defaultStreams }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeActiveSubjects(): Flow<List<Subject>> {
        val path = "subjects"
        return db.collection(path)
            .whereEqualTo("isActive", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(Subject::class.java).sortedBy { it.sortOrder }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observePublishedBooks(limitCount: Long = 300L): Flow<List<Textbook>> {
        val path = "books"
        return db.collection(path)
            .whereEqualTo("isPublished", true)
            .limit(limitCount)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(Textbook::class.java)
                    .filter { it.isPublished }
                    .sortedWith(
                        compareByDescending<Textbook> { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                            .thenBy { it.classNumber }
                            .thenBy { it.titleBn }
                    )
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observePublishedGuides(limitCount: Long = 300L): Flow<List<GuideNote>> {
        val path = "guides"
        return db.collection(path)
            .whereEqualTo("isPublished", true)
            .limit(limitCount)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(GuideNote::class.java)
                    .filter { it.isPublished }
                    .sortedWith(
                        compareByDescending<GuideNote> { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                            .thenBy { it.classNumber }
                            .thenBy { it.chapter }
                    )
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observePublishedNotices(limitCount: Long = 30L): Flow<List<Notice>> {
        val path = "notices"
        return db.collection(path)
            .whereEqualTo("isPublished", true)
            .limit(limitCount)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(Notice::class.java)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    fun observeAppSettings(): Flow<AppSettings> {
        val path = "app_settings/global"
        return db.collection("app_settings").document("global")
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModel(AppSettings::class.java) ?: AppSettings()
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.GET, path)
                throw error
            }
    }

    // --- Bookmarks (Whole Book, Individual Page, Guide, GK) ---

    fun observeUserBookmarks(userId: String): Flow<List<Bookmark>> {
        val path = "bookmarks"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(Bookmark::class.java)
                    .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun toggleBookmark(
        bookmarkType: String,
        targetId: String,
        titleBn: String,
        subtitleBn: String = "",
        classNumber: Int = 5,
        pageNumber: Int = 0,
        coverUrl: String = "",
        pdfUrl: String = "",
        isPremium: Boolean = false
    ): Result<Boolean> {
        val uid = requireUserId()
        val safeTarget = targetId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docId = "${uid}_${bookmarkType}_${safeTarget}_p${pageNumber}".take(120)
        val docRef = db.collection("bookmarks").document(docId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.delete().await()
                Result.success(false)
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "bookmarkType" to bookmarkType,
                    "targetId" to targetId.take(128),
                    "titleBn" to titleBn.take(200),
                    "subtitleBn" to subtitleBn.take(200),
                    "classNumber" to classNumber.coerceIn(0, 30),
                    "pageNumber" to pageNumber.coerceIn(0, 5000),
                    "coverUrl" to coverUrl.take(1000),
                    "pdfUrl" to pdfUrl.take(1000),
                    "isPremium" to isPremium,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun removeBookmarkById(bookmarkId: String): Result<Unit> {
        val docRef = db.collection("bookmarks").document(bookmarkId)
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

    // --- Reading Progress & History ---

    fun observeReadingProgress(userId: String): Flow<List<ReadingProgress>> {
        val path = "reading_progress"
        return db.collection(path)
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModels(ReadingProgress::class.java)
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, path)
                throw error
            }
    }

    suspend fun saveReadingProgress(
        contentId: String,
        contentType: String,
        titleBn: String,
        subjectNameBn: String,
        classNumber: Int,
        stream: String,
        coverUrl: String,
        pdfUrl: String,
        isPremium: Boolean,
        lastPage: Int,
        totalPages: Int
    ): Result<Unit> {
        val uid = requireUserId()
        val safeContent = contentId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val docId = "${uid}_${safeContent}".take(120)
        val docRef = db.collection("reading_progress").document(docId)
        val safeTotal = totalPages.coerceAtLeast(1)
        val safeLast = lastPage.coerceIn(1, safeTotal)
        val percent = ((safeLast.toFloat() / safeTotal.toFloat()) * 100f).toInt().coerceIn(0, 100)
        val normalizedStream = if (stream in listOf("ALL", "SCIENCE", "HUMANITIES", "BUSINESS")) stream else "ALL"
        return try {
            val existing = docRef.get().await()
            val payload = mapOf(
                "userId" to uid,
                "contentId" to contentId.take(128),
                "contentType" to contentType,
                "titleBn" to titleBn.take(200),
                "subjectNameBn" to subjectNameBn.take(120),
                "classNumber" to classNumber.coerceIn(1, 30),
                "stream" to normalizedStream,
                "coverUrl" to coverUrl.take(1000),
                "pdfUrl" to pdfUrl.take(1000),
                "isPremium" to isPremium,
                "lastPage" to safeLast,
                "totalPages" to safeTotal,
                "progressPercent" to percent,
                "createdAt" to (if (existing.exists()) (existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()) else FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Paginated textbook query helper for large textbook libraries.
     */
    suspend fun fetchBooksPage(
        classNumber: Int?,
        lastSnapshot: DocumentSnapshot? = null,
        pageSize: Long = 20L
    ): Result<Pair<List<Textbook>, DocumentSnapshot?>> {
        val path = "books"
        return try {
            var query: Query = db.collection(path)
                .whereEqualTo("isPublished", true)
            if (classNumber != null) {
                query = query.whereEqualTo("classNumber", classNumber)
            }
            query = query.limit(pageSize)
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snap = query.get().await()
            val items = snap.toSafeModels(Textbook::class.java)
            val nextCursor = snap.documents.lastOrNull()
            Result.success(items to nextCursor)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }
}
