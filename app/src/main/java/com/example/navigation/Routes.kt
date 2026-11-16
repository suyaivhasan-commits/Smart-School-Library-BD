package com.example.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val LIBRARY_HUB = "library_hub"
    const val BOOKMARKS = "bookmarks"
    const val PROFILE = "profile"

    private fun encodeSegment(raw: String, fallback: String = "_"): String {
        val cleaned = raw.trim().ifBlank { fallback }
        return Uri.encode(cleaned)
    }

    const val CLASS_DETAIL = "class_detail/{classNumber}"
    fun classDetail(classNumber: Int) = "class_detail/$classNumber"

    const val TEXTBOOK_LIST = "textbooks/{classNumber}/{stream}"
    fun textbookList(classNumber: Int, stream: String = "ALL") =
        "textbooks/$classNumber/${encodeSegment(stream, "ALL")}"

    const val GUIDE_LIST = "guides/{classNumber}/{stream}"
    fun guideList(classNumber: Int, stream: String = "ALL") =
        "guides/$classNumber/${encodeSegment(stream, "ALL")}"

    const val BOOK_DETAIL = "book_detail/{bookId}"
    fun bookDetail(bookId: String) = "book_detail/${encodeSegment(bookId)}"

    const val GUIDE_DETAIL = "guide_detail/{guideId}"
    fun guideDetail(guideId: String) = "guide_detail/${encodeSegment(guideId)}"

    const val PDF_READER = "pdf_reader/{contentType}/{contentId}?initialPage={initialPage}"
    fun pdfReader(contentType: String, contentId: String, initialPage: Int = 0) =
        "pdf_reader/${encodeSegment(contentType, "BOOK")}/${encodeSegment(contentId)}?initialPage=$initialPage"

    const val NOTEBOOK = "notebook"
    const val GENERAL_KNOWLEDGE = "general_knowledge"
    const val EXAM_PREP = "exam_prep?classFilter={classFilter}"
    fun examPrep(classFilter: Int = 0) = "exam_prep?classFilter=$classFilter"

    const val EXAM_SESSION = "exam_session/{examId}"
    fun examSession(examId: String) = "exam_session/${encodeSegment(examId)}"

    const val READING_HISTORY = "reading_history"
    const val SUBSCRIPTION = "subscription?preselectClass={preselectClass}&preselectStream={preselectStream}"
    fun subscription(preselectClass: Int = 0, preselectStream: String = "ALL") =
        "subscription?preselectClass=$preselectClass&preselectStream=${encodeSegment(preselectStream, "ALL")}"

    const val PREMIUM_GATE = "premium_gate/{classNumber}/{stream}?expired={expired}"
    fun premiumGate(classNumber: Int, stream: String = "ALL", expired: Boolean = false) =
        "premium_gate/$classNumber/${encodeSegment(stream, "ALL")}?expired=$expired"

    const val NOTICES = "notices"
    const val SETTINGS = "settings"
    const val WELCOME_INTRO = "welcome_intro"
    const val LEGAL_INFO = "legal_info/{docType}"
    fun legalInfo(docType: String) = "legal_info/${encodeSegment(docType, "ABOUT")}"
}
