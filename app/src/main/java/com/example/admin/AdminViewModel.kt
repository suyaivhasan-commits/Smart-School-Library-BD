package com.example.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdminRepository
import com.example.data.BookStorageUploader
import com.example.data.DirectUploadDraft
import com.example.data.DirectUploadState
import com.example.data.LibraryRepository
import com.example.data.SelectedCoverInfo
import com.example.data.SelectedPdfInfo
import com.example.firebase.SecurityConstants
import com.example.model.AcademicStream
import com.example.model.AppSettings
import com.example.model.EducationLevel
import com.example.model.ExamTest
import com.example.model.GeneralKnowledgeItem
import com.example.model.GuideNote
import com.example.model.Notice
import com.example.model.SchoolClass
import com.example.model.Subject
import com.example.model.Subscription
import com.example.model.SubscriptionPlan
import com.example.model.SubscriptionRequest
import com.example.model.Textbook
import com.example.model.UiState
import com.example.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object AdminErrorTranslator {
    fun toBengaliMessage(err: Throwable): String {
        var cur: Throwable? = err
        while (cur != null) {
            if (cur is StorageException) {
                return when (cur.errorCode) {
                    StorageException.ERROR_NOT_AUTHORIZED,
                    StorageException.ERROR_NOT_AUTHENTICATED -> {
                        "Firebase Storage-এ ফাইল আপলোডের অনুমতি প্রত্যাখ্যাত হয়েছে (HTTP 403)। সমাধান: Firebase Console (smart-school-library-bd) -> Storage -> Rules-এ গিয়ে প্রজেক্টের storage.rules ফাইলটি পেস্ট করে Publish করুন এবং সুপার অ্যাডমিন অ্যাকাউন্টে লগইন নিশ্চিত করুন।"
                    }
                    StorageException.ERROR_BUCKET_NOT_FOUND,
                    StorageException.ERROR_PROJECT_NOT_FOUND,
                    StorageException.ERROR_OBJECT_NOT_FOUND -> {
                        "Firebase Storage বাকেট প্রস্তুত নয় (HTTP 404)। সমাধান: Firebase Console (smart-school-library-bd) -> Storage মেনুতে গিয়ে 'Get Started' চাপে স্টোরেজ চালু করুন এবং storage.rules পাবলিশ করুন।"
                    }
                    StorageException.ERROR_QUOTA_EXCEEDED -> {
                        "Firebase Storage-এর ফ্রি কোটা পূর্ণ হয়ে গেছে। অনুগ্রহ করে Firebase Console থেকে স্টোরেজ ব্যবহার পরীক্ষা করুন।"
                    }
                    StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> {
                        "ইন্টারনেট সংযোগ ধীর বা বিচ্ছিন্ন হওয়ায় আপলোড সময়সীমা অতিক্রম করেছে। পুনরায় চেষ্টা করুন।"
                    }
                    else -> {
                        "স্টোরেজ আপলোড ত্রুটি (${cur.errorCode}): ${cur.localizedMessage ?: "অজানা ত্রুটি"}"
                    }
                }
            }
            if (cur is FirebaseFirestoreException) {
                return when (cur.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        "Firestore ডাটাবেজে সংরক্ষণের অনুমতি প্রত্যাখ্যাত হয়েছে (PERMISSION_DENIED)। সমাধান: Firebase Console (smart-school-library-bd) -> Firestore Database -> Rules-এ গিয়ে প্রজেক্টের আপডেটকৃত firestore.rules ফাইলটি পেস্ট করে Publish করুন।"
                    }
                    FirebaseFirestoreException.Code.UNAUTHENTICATED -> {
                        "সুপার অ্যাডমিন সেশন যাচাই করা যায়নি। অনুগ্রহ করে একবার লগআউট করে পুনরায় লগইন করুন।"
                    }
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> {
                        "ইন্টারনেট সংযোগ দুর্বল হওয়ায় ডাটাবেজে পৌঁছানো যায়নি। সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।"
                    }
                    else -> {
                        "ডাটাবেজ ত্রুটি (${cur.code.name}): ${cur.localizedMessage ?: "অজানা ত্রুটি"}"
                    }
                }
            }
            cur = cur.cause
        }
        return err.localizedMessage ?: "অপারেশন সম্পন্ন করা যায়নি।"
    }

    fun toEnglishMessage(err: Throwable): String {
        var cur: Throwable? = err
        while (cur != null) {
            if (cur is StorageException) {
                return when (cur.errorCode) {
                    StorageException.ERROR_NOT_AUTHORIZED,
                    StorageException.ERROR_NOT_AUTHENTICATED ->
                        "Firebase Storage upload denied (HTTP 403). Publish storage.rules in Firebase Console (smart-school-library-bd -> Storage -> Rules)."
                    StorageException.ERROR_BUCKET_NOT_FOUND,
                    StorageException.ERROR_PROJECT_NOT_FOUND,
                    StorageException.ERROR_OBJECT_NOT_FOUND ->
                        "Firebase Storage bucket not initialized (HTTP 404). Open Firebase Console -> Storage -> Get Started, then publish storage.rules."
                    else ->
                        "Storage error (${cur.errorCode}): ${cur.localizedMessage ?: "Upload failed"}"
                }
            }
            if (cur is FirebaseFirestoreException) {
                return when (cur.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        "Firestore write denied (PERMISSION_DENIED). Publish updated firestore.rules in Firebase Console (smart-school-library-bd -> Firestore Database -> Rules)."
                    else ->
                        "Firestore error (${cur.code.name}): ${cur.localizedMessage ?: "Write failed"}"
                }
            }
            cur = cur.cause
        }
        return err.localizedMessage ?: "Operation failed."
    }
}

class AdminViewModel(
    private val currentUserId: String,
    private val adminRepository: AdminRepository,
    private val libraryRepository: LibraryRepository,
    private val bookStorageUploader: BookStorageUploader? = null
) : ViewModel() {

    init {
        require(SecurityConstants.isTrustedSuperAdmin(currentUserId)) {
            "Unauthorized AdminViewModel instantiation attempt"
        }
    }

    private fun <T> Flow<T>.asUiState(tag: String): StateFlow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { err ->
                if (err is CancellationException) throw err
                Log.w("AdminVM", "Admin stream error ($tag)", err)
                emit(UiState.Error(AdminErrorTranslator.toBengaliMessage(err)))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val usersState: StateFlow<UiState<List<UserProfile>>> = adminRepository.observeAllUsers().asUiState("users")
    val educationLevelsState: StateFlow<UiState<List<EducationLevel>>> = adminRepository.observeAllEducationLevels().asUiState("educationLevels")
    val classesState: StateFlow<UiState<List<SchoolClass>>> = adminRepository.observeAllClasses().asUiState("classes")
    val streamsState: StateFlow<UiState<List<AcademicStream>>> = adminRepository.observeAllStreams().asUiState("streams")
    val subjectsState: StateFlow<UiState<List<Subject>>> = adminRepository.observeAllSubjects().asUiState("subjects")
    val booksState: StateFlow<UiState<List<Textbook>>> = adminRepository.observeAllBooks().asUiState("books")
    val guidesState: StateFlow<UiState<List<GuideNote>>> = adminRepository.observeAllGuides().asUiState("guides")
    val gkState: StateFlow<UiState<List<GeneralKnowledgeItem>>> = adminRepository.observeAllGk().asUiState("gk")
    val examsState: StateFlow<UiState<List<ExamTest>>> = adminRepository.observeAllExams().asUiState("exams")
    val plansState: StateFlow<UiState<List<SubscriptionPlan>>> = adminRepository.observeAllPlans().asUiState("plans")
    val paymentMethodsState: StateFlow<UiState<List<com.example.model.PaymentMethodConfig>>> =
        adminRepository.observeAllPaymentMethods().asUiState("paymentMethods")
    val requestsState: StateFlow<UiState<List<SubscriptionRequest>>> = adminRepository.observeAllSubscriptionRequests().asUiState("requests")
    val subscriptionsState: StateFlow<UiState<List<Subscription>>> = adminRepository.observeAllSubscriptions().asUiState("subscriptions")
    val adminNotificationsState: StateFlow<UiState<List<com.example.model.AppNotification>>> =
        adminRepository.observeAdminNotifications().asUiState("adminNotifications")
    val noticesState: StateFlow<UiState<List<Notice>>> = adminRepository.observeAllNotices().asUiState("notices")
    val appSettingsState: StateFlow<UiState<AppSettings>> = libraryRepository.observeAppSettings().asUiState("settings")

    private val _focusedPaymentRequestId = MutableStateFlow<String?>(null)
    val focusedPaymentRequestId: StateFlow<String?> = _focusedPaymentRequestId.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _isErrorFeedback = MutableStateFlow(false)
    val isErrorFeedback: StateFlow<Boolean> = _isErrorFeedback.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _directUploadState = MutableStateFlow<DirectUploadState>(DirectUploadState.Idle)
    val directUploadState: StateFlow<DirectUploadState> = _directUploadState.asStateFlow()

    fun clearFeedback() {
        _feedbackMessage.value = null
        _isErrorFeedback.value = false
    }

    fun focusPaymentRequest(requestId: String?) {
        _focusedPaymentRequestId.value = requestId
    }

    fun resetDirectUploadState() {
        if (_directUploadState.value !is DirectUploadState.Uploading) {
            _directUploadState.value = DirectUploadState.Idle
        }
    }

    fun uploadBookOrGuide(
        draft: DirectUploadDraft,
        selectedCover: SelectedCoverInfo?,
        selectedPdf: SelectedPdfInfo?
    ) {
        val uploader = bookStorageUploader
        if (uploader == null) {
            _directUploadState.value = DirectUploadState.Failed(
                errorMessageBn = "স্টোরেজ আপলোডার প্রস্তুত নয়।",
                errorMessageEn = "Storage uploader is not configured."
            )
            return
        }
        if (_directUploadState.value is DirectUploadState.Uploading) return

        viewModelScope.launch {
            _isBusy.value = true
            _directUploadState.value = DirectUploadState.Uploading(
                progressPercent = 1,
                activeFileName = selectedCover?.displayName ?: selectedPdf?.displayName ?: draft.titleBn,
                stageLabelBn = "আপলোড প্রস্তুতি চলছে...",
                stageLabelEn = "Preparing upload..."
            )
            val result = uploader.uploadAndPublish(
                draft = draft,
                selectedCover = selectedCover,
                selectedPdf = selectedPdf,
                onProgress = { progressState ->
                    _directUploadState.value = progressState
                }
            )
            _isBusy.value = false
            result.onSuccess { success ->
                _directUploadState.value = success
                _isErrorFeedback.value = false
                _feedbackMessage.value = success.messageBn
            }.onFailure { err ->
                val bnMsg = AdminErrorTranslator.toBengaliMessage(err)
                val enMsg = AdminErrorTranslator.toEnglishMessage(err)
                _isErrorFeedback.value = true
                _feedbackMessage.value = bnMsg
                _directUploadState.value = DirectUploadState.Failed(
                    errorMessageBn = bnMsg,
                    errorMessageEn = enMsg,
                    canRetry = true
                )
            }
        }
    }

    private fun runAdminTask(successText: String, block: suspend () -> Result<*>) {
        viewModelScope.launch {
            _isBusy.value = true
            val res = block()
            _isBusy.value = false
            res.onSuccess {
                _isErrorFeedback.value = false
                _feedbackMessage.value = successText
            }.onFailure { err ->
                _isErrorFeedback.value = true
                _feedbackMessage.value = AdminErrorTranslator.toBengaliMessage(err)
            }
        }
    }

    fun bootstrapMissingClassesAndStreams() = runAdminTask("অনুপস্থিত শ্রেণি ও বিভাগ সফলভাবে প্রস্তুত হয়েছে।") {
        adminRepository.initializeMissingClassesAndStreams()
    }

    fun saveEducationLevel(level: EducationLevel) = runAdminTask("শিক্ষা স্তরের তথ্য সফলভাবে সংরক্ষিত হয়েছে।") {
        adminRepository.saveEducationLevel(level)
    }

    fun deleteEducationLevel(levelId: String) = runAdminTask("শিক্ষা স্তর মুছে ফেলা হয়েছে।") {
        adminRepository.deleteEducationLevel(levelId)
    }

    fun saveClass(schoolClass: SchoolClass) = runAdminTask("শ্রেণির তথ্য সফলভাবে সংরক্ষিত হয়েছে।") {
        adminRepository.saveSchoolClass(schoolClass)
    }

    fun deleteClass(classId: String) = runAdminTask("শ্রেণি সফলভাবে মুছে ফেলা হয়েছে।") {
        adminRepository.deleteSchoolClass(classId)
    }

    fun saveStream(stream: AcademicStream) = runAdminTask("বিভাগের তথ্য সফলভাবে সংরক্ষিত হয়েছে।") {
        adminRepository.saveStream(stream)
    }

    fun saveSubject(subject: Subject) = runAdminTask("বিষয় সফলভাবে সংরক্ষিত হয়েছে।") {
        adminRepository.saveSubject(subject)
    }

    fun deleteSubject(subjectId: String) = runAdminTask("বিষয় মুছে ফেলা হয়েছে।") {
        adminRepository.deleteSubject(subjectId)
    }

    fun saveBook(book: Textbook) = runAdminTask("পাঠ্যবই ডাটাবেজে সফলভাবে সংরক্ষিত হয়েছে।") {
        adminRepository.saveBook(book)
    }

    fun deleteBook(bookId: String) = runAdminTask("পাঠ্যবই সফলভাবে মুছে ফেলা হয়েছে।") {
        adminRepository.deleteBook(bookId)
    }

    fun saveGuide(guide: GuideNote) = runAdminTask("গাইড/নোট সফলভাবে সংরক্ষিত হয়েছে।") {
        adminRepository.saveGuide(guide)
    }

    fun deleteGuide(guideId: String) = runAdminTask("গাইড/নোট সফলভাবে মুছে ফেলা হয়েছে।") {
        adminRepository.deleteGuide(guideId)
    }

    fun saveGk(item: GeneralKnowledgeItem) = runAdminTask("সাধারণ জ্ঞান প্রশ্ন সফলভাবে সংরক্ষিত হয়েছে।") {
        adminRepository.saveGkItem(item)
    }

    fun deleteGk(gkId: String) = runAdminTask("সাধারণ জ্ঞান প্রশ্ন মুছে ফেলা হয়েছে।") {
        adminRepository.deleteGkItem(gkId)
    }

    fun saveExam(exam: ExamTest, correctIndices: List<Int>, explanations: List<String>) =
        runAdminTask("এমসিকিউ পরীক্ষা ও সুরক্ষিত উত্তরপত্র সফলভাবে সংরক্ষিত হয়েছে।") {
            adminRepository.saveExamWithAnswerKey(exam, correctIndices, explanations)
        }

    fun deleteExam(examId: String) = runAdminTask("পরীক্ষা মুছে ফেলা হয়েছে।") {
        adminRepository.deleteExam(examId)
    }

    fun toggleUserActive(userId: String, active: Boolean) =
        runAdminTask(if (active) "শিক্ষার্থী অ্যাকাউন্ট সক্রিয় করা হয়েছে।" else "শিক্ষার্থী অ্যাকাউন্ট নিষ্ক্রিয় করা হয়েছে।") {
            adminRepository.setUserActiveStatus(userId, active)
        }

    fun savePaymentMethod(method: com.example.model.PaymentMethodConfig) =
        runAdminTask("পেমেন্ট মাধ্যম সফলভাবে সংরক্ষিত হয়েছে।") {
            adminRepository.savePaymentMethod(method)
        }

    fun togglePaymentMethod(method: com.example.model.PaymentMethodConfig, active: Boolean) =
        runAdminTask(if (active) "পেমেন্ট মাধ্যম সক্রিয় করা হয়েছে।" else "পেমেন্ট মাধ্যম নিষ্ক্রিয় করা হয়েছে।") {
            adminRepository.togglePaymentMethodActive(method, active)
        }

    fun deletePaymentMethod(methodId: String) =
        runAdminTask("পেমেন্ট মাধ্যম মুছে ফেলা হয়েছে।") {
            adminRepository.deletePaymentMethod(methodId)
        }

    fun savePlan(plan: SubscriptionPlan) = runAdminTask("সাবস্ক্রিপশন প্ল্যান সংরক্ষিত হয়েছে।") {
        adminRepository.saveSubscriptionPlan(plan)
    }

    fun deletePlan(planId: String) = runAdminTask("সাবস্ক্রিপশন প্ল্যান মুছে ফেলা হয়েছে।") {
        adminRepository.deleteSubscriptionPlan(planId)
    }

    fun markNotificationRead(notificationId: String) {
        viewModelScope.launch {
            adminRepository.markAdminNotificationRead(notificationId)
        }
    }

    fun markAllNotificationsRead(notifications: List<com.example.model.AppNotification>) =
        runAdminTask("সকল নোটিফিকেশন পঠিত হিসেবে চিহ্নিত হয়েছে।") {
            adminRepository.markAllAdminNotificationsRead(notifications)
        }

    fun approveRequest(request: SubscriptionRequest, note: String) {
        val relatedIds = (adminNotificationsState.value as? UiState.Success)?.data
            .orEmpty()
            .filter { !it.isRead && it.relatedPaymentRequestId == request.id }
            .map { it.id }
        runAdminTask("পেমেন্ট অনুমোদিত হয়েছে এবং শিক্ষার্থীর সাবস্ক্রিপশন সাথে সাথে সক্রিয় হয়েছে!") {
            adminRepository.approveSubscriptionRequest(request, note, relatedIds)
        }
    }

    fun rejectRequest(request: SubscriptionRequest, rejectionReason: String) {
        val relatedIds = (adminNotificationsState.value as? UiState.Success)?.data
            .orEmpty()
            .filter { !it.isRead && it.relatedPaymentRequestId == request.id }
            .map { it.id }
        runAdminTask("পেমেন্ট রিকোয়েস্ট বাতিল করা হয়েছে এবং শিক্ষার্থীকে নোটিফিকেশন পাঠানো হয়েছে।") {
            adminRepository.rejectSubscriptionRequest(request, rejectionReason, relatedIds)
        }
    }

    fun saveNotice(notice: Notice) = runAdminTask("নোটিশ সফলভাবে সংরক্ষিত হয়েছে।") {
        adminRepository.saveNotice(notice)
    }

    fun deleteNotice(noticeId: String) = runAdminTask("নোটিশ মুছে ফেলা হয়েছে।") {
        adminRepository.deleteNotice(noticeId)
    }

    fun saveSettings(settings: AppSettings) = runAdminTask("অ্যাপ ও ডাউনলোড সেটিংস সফলভাবে আপডেট হয়েছে।") {
        adminRepository.saveAppSettings(settings)
    }
}
