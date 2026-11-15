package com.example.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuthRepository
import com.example.data.GradedExamResult
import com.example.data.LibraryRepository
import com.example.data.PremiumAccessDecision
import com.example.data.StudyRepository
import com.example.model.AcademicStream
import com.example.model.AppSettings
import com.example.model.Bookmark
import com.example.model.EducationLevel
import com.example.model.ExamAttempt
import com.example.model.ExamTest
import com.example.model.GeneralKnowledgeItem
import com.example.model.GuideNote
import com.example.model.Notice
import com.example.model.ReadingProgress
import com.example.model.SchoolClass
import com.example.model.Subject
import com.example.model.Subscription
import com.example.model.SubscriptionPlan
import com.example.model.SubscriptionRequest
import com.example.model.Textbook
import com.example.model.UiState
import com.example.model.UserNote
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudentSessionViewModel(
    private val currentUserId: String,
    private val authRepository: AuthRepository,
    private val libraryRepository: LibraryRepository,
    private val studyRepository: StudyRepository
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    fun retryAllStreams() {
        retryTrigger.value += 1
    }

    private fun translateStudentError(err: Throwable): String {
        val fsEx = generateSequence<Throwable>(err) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fsEx != null) {
            return when (fsEx.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "ডাটাবেজে প্রবেশের অনুমতি পাওয়া যায়নি (PERMISSION_DENIED)। অনুগ্রহ করে পুনরায় লগইন করে চেষ্টা করুন।"
                FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                    "আপনার সেশনের মেয়াদ শেষ হয়েছে। অনুগ্রহ করে পুনরায় লগইন করুন।"
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "ইন্টারনেট সংযোগ দুর্বল বা বিচ্ছিন্ন। সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।"
                FirebaseFirestoreException.Code.ALREADY_EXISTS ->
                    "এই তথ্যটি ইতিমধ্যে ডাটাবেজে সংরক্ষিত রয়েছে।"
                else -> "ডাটাবেজ ত্রুটি (${fsEx.code.name}): ${fsEx.localizedMessage ?: "ডেটা লোড করা যায়নি"}"
            }
        }
        return err.localizedMessage ?: "ডেটা লোড করা যায়নি। পুনরায় চেষ্টা করুন।"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observeAsUiState(tag: String, flowProvider: () -> Flow<T>): StateFlow<UiState<T>> =
        retryTrigger
            .flatMapLatest {
                flowProvider()
                    .map<T, UiState<T>> { UiState.Success(it) }
                    .catch { err ->
                        if (err is CancellationException) throw err
                        Log.w("StudentSessionVM", "Error observing $tag", err)
                        emit(UiState.Error(translateStudentError(err)))
                    }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        observeAsUiState("userProfile") { authRepository.observeUserProfile(currentUserId) }

    val educationLevelsState: StateFlow<UiState<List<EducationLevel>>> =
        observeAsUiState("educationLevels") { libraryRepository.observeActiveEducationLevels() }

    val classesState: StateFlow<UiState<List<SchoolClass>>> =
        observeAsUiState("classes") { libraryRepository.observeActiveClasses() }

    val streamsState: StateFlow<UiState<List<AcademicStream>>> =
        observeAsUiState("streams") { libraryRepository.observeActiveStreams() }

    val subjectsState: StateFlow<UiState<List<Subject>>> =
        observeAsUiState("subjects") { libraryRepository.observeActiveSubjects() }

    val booksState: StateFlow<UiState<List<Textbook>>> =
        observeAsUiState("books") { libraryRepository.observePublishedBooks() }

    val guidesState: StateFlow<UiState<List<GuideNote>>> =
        observeAsUiState("guides") { libraryRepository.observePublishedGuides() }

    val noticesState: StateFlow<UiState<List<Notice>>> =
        observeAsUiState("notices") { libraryRepository.observePublishedNotices() }

    val appSettingsState: StateFlow<UiState<AppSettings>> =
        observeAsUiState("appSettings") { libraryRepository.observeAppSettings() }

    val bookmarksState: StateFlow<UiState<List<Bookmark>>> =
        observeAsUiState("bookmarks") { libraryRepository.observeUserBookmarks(currentUserId) }

    val readingProgressState: StateFlow<UiState<List<ReadingProgress>>> =
        observeAsUiState("readingProgress") { libraryRepository.observeReadingProgress(currentUserId) }

    val notesState: StateFlow<UiState<List<UserNote>>> =
        observeAsUiState("notes") { studyRepository.observeUserNotes(currentUserId) }

    val gkState: StateFlow<UiState<List<GeneralKnowledgeItem>>> =
        observeAsUiState("gk") { studyRepository.observePublishedGk() }

    val examsState: StateFlow<UiState<List<ExamTest>>> =
        observeAsUiState("exams") { studyRepository.observePublishedExams() }

    val examHistoryState: StateFlow<UiState<List<ExamAttempt>>> =
        observeAsUiState("examHistory") { studyRepository.observeExamHistory(currentUserId) }

    val plansState: StateFlow<UiState<List<SubscriptionPlan>>> =
        observeAsUiState("plans") { studyRepository.observeActivePlans() }

    val paymentMethodsState: StateFlow<UiState<List<com.example.model.PaymentMethodConfig>>> =
        observeAsUiState("paymentMethods") { studyRepository.observeActivePaymentMethods() }

    val subscriptionsState: StateFlow<UiState<List<Subscription>>> =
        observeAsUiState("subscriptions") { studyRepository.observeUserSubscriptions(currentUserId) }

    val subscriptionRequestsState: StateFlow<UiState<List<SubscriptionRequest>>> =
        observeAsUiState("subRequests") { studyRepository.observeUserSubscriptionRequests(currentUserId) }

    val userNotificationsState: StateFlow<UiState<List<com.example.model.AppNotification>>> =
        observeAsUiState("userNotifications") { studyRepository.observeUserNotifications(currentUserId) }

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _latestExamResult = MutableStateFlow<GradedExamResult?>(null)
    val latestExamResult: StateFlow<GradedExamResult?> = _latestExamResult.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser?.let { user ->
                authRepository.ensureUserProfile(user).onFailure { err ->
                    if (authRepository.currentUser != null) {
                        _statusMessage.value = translateStudentError(err)
                    }
                }
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun markUserNotificationRead(notificationId: String) {
        viewModelScope.launch {
            studyRepository.markNotificationRead(notificationId)
        }
    }

    fun evaluatePremiumAccess(
        isPremium: Boolean,
        classNumber: Int,
        stream: String
    ): PremiumAccessDecision {
        val profile = (userProfileState.value as? UiState.Success)?.data
        val subs = (subscriptionsState.value as? UiState.Success)?.data.orEmpty()
        val plans = (plansState.value as? UiState.Success)?.data.orEmpty()
        val levels = (educationLevelsState.value as? UiState.Success)?.data.orEmpty()
        return StudyRepository.checkPremiumEligibility(
            isContentPremium = isPremium,
            contentClass = classNumber,
            contentStream = stream,
            currentUserUid = currentUserId,
            userProfile = profile,
            userSubscriptions = subs,
            plans = plans,
            educationLevels = levels
        )
    }

    fun evaluateClassAccess(
        classNumber: Int,
        stream: String = "ALL",
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val profile = (userProfileState.value as? UiState.Success)?.data
        val subs = (subscriptionsState.value as? UiState.Success)?.data.orEmpty()
        val plans = (plansState.value as? UiState.Success)?.data.orEmpty()
        val levels = (educationLevelsState.value as? UiState.Success)?.data.orEmpty()
        return StudyRepository.evaluateClassAndStreamAccess(
            classNumber = classNumber,
            stream = stream,
            currentUserUid = currentUserId,
            userProfile = profile,
            userSubscriptions = subs,
            nowMillis = nowMillis,
            plans = plans,
            educationLevels = levels
        )
    }

    fun hasActiveSubscriptionForClass(
        classNumber: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (com.example.firebase.SecurityConstants.isTrustedSuperAdmin(currentUserId)) return true
        return evaluateClassAccess(classNumber, "ALL", nowMillis)
    }

    fun getActiveStreamsForClass(
        classNumber: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Set<String> {
        if (com.example.firebase.SecurityConstants.isTrustedSuperAdmin(currentUserId)) {
            return setOf("SCIENCE", "HUMANITIES", "BUSINESS", "ALL")
        }
        val subs = (subscriptionsState.value as? UiState.Success)?.data.orEmpty()
        return subs.filter { it.classNumber == classNumber && it.isCurrentlyActive(nowMillis) }
            .map { it.stream.uppercase() }
            .toSet()
    }

    fun toggleBookmark(
        bookmarkType: String,
        targetId: String,
        titleBn: String,
        subtitleBn: String = "",
        classNumber: Int = 5,
        pageNumber: Int = 0,
        coverUrl: String = "",
        pdfUrl: String = "",
        isPremium: Boolean = false
    ) {
        viewModelScope.launch {
            val result = libraryRepository.toggleBookmark(
                bookmarkType = bookmarkType,
                targetId = targetId,
                titleBn = titleBn,
                subtitleBn = subtitleBn,
                classNumber = classNumber,
                pageNumber = pageNumber,
                coverUrl = coverUrl,
                pdfUrl = pdfUrl,
                isPremium = isPremium
            )
            result.onSuccess { added ->
                _statusMessage.value = if (added) "বুকমার্কে সংরক্ষিত হয়েছে" else "বুকমার্ক সরানো হয়েছে"
            }.onFailure { err ->
                _statusMessage.value = "ত্রুটি: ${err.localizedMessage ?: "বুকমার্ক সংরক্ষণ করা যায়নি"}"
            }
        }
    }

    fun removeBookmark(bookmarkId: String) {
        viewModelScope.launch {
            libraryRepository.removeBookmarkById(bookmarkId)
                .onSuccess { _statusMessage.value = "বুকমার্ক মুছে ফেলা হয়েছে" }
                .onFailure { _statusMessage.value = "মুছে ফেলা যায়নি: ${it.localizedMessage}" }
        }
    }

    fun recordReadingProgress(
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
    ) {
        viewModelScope.launch {
            libraryRepository.saveReadingProgress(
                contentId = contentId,
                contentType = contentType,
                titleBn = titleBn,
                subjectNameBn = subjectNameBn,
                classNumber = classNumber,
                stream = stream,
                coverUrl = coverUrl,
                pdfUrl = pdfUrl,
                isPremium = isPremium,
                lastPage = lastPage,
                totalPages = totalPages
            )
        }
    }

    fun saveNote(
        existingNoteId: String?,
        title: String,
        content: String,
        subjectTag: String,
        colorIndex: Int,
        isPinned: Boolean,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val res = if (existingNoteId.isNullOrBlank()) {
                studyRepository.createNote(title, content, subjectTag, colorIndex, isPinned).map { }
            } else {
                studyRepository.updateNote(existingNoteId, title, content, subjectTag, colorIndex, isPinned)
            }
            res.onSuccess {
                _statusMessage.value = "নোট সফলভাবে সংরক্ষিত হয়েছে"
                onComplete()
            }.onFailure {
                _statusMessage.value = "নোট সংরক্ষণ ব্যর্থ হয়েছে: ${it.localizedMessage}"
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            studyRepository.deleteNote(noteId)
                .onSuccess { _statusMessage.value = "নোট মুছে ফেলা হয়েছে" }
                .onFailure { _statusMessage.value = "ত্রুটি: ${it.localizedMessage}" }
        }
    }

    fun submitExam(
        exam: ExamTest,
        selectedAnswers: List<Int>,
        onFinished: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            studyRepository.submitAndGradeExam(exam, selectedAnswers)
                .onSuccess { graded ->
                    _latestExamResult.value = graded
                    onFinished(true)
                }
                .onFailure { err ->
                    _statusMessage.value = "পরীক্ষা জমা দেওয়া যায়নি: ${err.localizedMessage}"
                    onFinished(false)
                }
        }
    }

    fun submitPaymentRequest(
        studentNameOverride: String,
        plan: SubscriptionPlan,
        paymentMethod: String,
        transactionReference: String,
        senderNumber: String,
        screenshotUrl: String = "",
        selectedMethodConfig: com.example.model.PaymentMethodConfig? = null,
        fallbackReceiverNumber: String = "",
        onResult: (Result<String>) -> Unit = {}
    ) {
        val profile = (userProfileState.value as? UiState.Success)?.data
        val resolvedName = studentNameOverride.trim().ifBlank { profile?.name ?: "শিক্ষার্থী" }
        viewModelScope.launch {
            val result = studyRepository.submitSubscriptionPaymentRequest(
                userName = resolvedName,
                userEmail = profile?.email ?: "",
                plan = plan,
                paymentMethod = paymentMethod,
                transactionReference = transactionReference,
                senderNumber = senderNumber,
                screenshotUrl = screenshotUrl,
                selectedMethodConfig = selectedMethodConfig,
                fallbackReceiverNumber = fallbackReceiverNumber
            )
            result.onSuccess {
                _statusMessage.value =
                    "আপনার পেমেন্ট রিকোয়েস্ট সফলভাবে জমা হয়েছে। অ্যাডমিন পেমেন্ট যাচাই করার পর আপনার সাবস্ক্রিপশন চালু হবে।"
            }.onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "পেমেন্ট রিকোয়েস্ট জমা দেওয়া যায়নি।"
            }
            onResult(result)
        }
    }

    fun updateProfile(
        name: String,
        selectedClass: Int,
        selectedStream: String,
        accountDeletionRequested: Boolean? = null
    ) {
        viewModelScope.launch {
            authRepository.updateOwnProfile(
                name = name,
                selectedClass = selectedClass,
                selectedStream = selectedStream,
                accountDeletionRequested = accountDeletionRequested
            ).onSuccess {
                _statusMessage.value = if (accountDeletionRequested == true) {
                    "অ্যাকাউন্ট মুছে ফেলার অনুরোধ নথিভুক্ত হয়েছে।"
                } else {
                    "প্রোফাইল সফলভাবে আপডেট হয়েছে।"
                }
            }.onFailure { err ->
                _statusMessage.value = "প্রোফাইল আপডেট ব্যর্থ: ${err.localizedMessage}"
            }
        }
    }
}
