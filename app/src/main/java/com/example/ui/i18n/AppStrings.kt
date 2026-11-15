package com.example.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppLanguage(val code: String) {
    BN("BN"),
    EN("EN");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: BN
    }
}

data class AppStrings(
    val language: AppLanguage,
    val appName: String,
    val appTagline: String,
    val loading: String,
    val retry: String,
    val cancel: String,
    val confirm: String,
    val save: String,
    val delete: String,
    val edit: String,
    val add: String,
    val close: String,
    val searchHint: String,
    val navHome: String,
    val navLibrary: String,
    val navBookmarks: String,
    val navProfile: String,
    val welcomePrefix: String,
    val welcomeSubtitle: String,
    val classesHeader: String,
    val continueReading: String,
    val recentBooks: String,
    val popularBooks: String,
    val latestNotices: String,
    val learningProgress: String,
    val otherFeatures: String,
    val featureNotebook: String,
    val featureGk: String,
    val featureExam: String,
    val featureBookmarks: String,
    val featureHistory: String,
    val featurePremium: String,
    val featureNotifications: String,
    val featureSettings: String,
    val originalTextbooks: String,
    val guidesAndNotes: String,
    val allBooksSub: String,
    val allGuidesSub: String,
    val selectStreamTitle: String,
    val streamScience: String,
    val streamHumanities: String,
    val streamBusiness: String,
    val freeBadge: String,
    val premiumBadge: String,
    val demoBadge: String,
    val readNow: String,
    val pagesLabel: String,
    val allFilter: String,
    val emptyStateTitle: String,
    val emptyStateBody: String,
    val offlineError: String,
    val loginTitle: String,
    val registerTitle: String,
    val emailLabel: String,
    val passwordLabel: String,
    val fullNameLabel: String,
    val loginButton: String,
    val registerButton: String,
    val googleSignIn: String,
    val forgotPassword: String,
    val logoutButton: String,
    val subscriptionTitle: String,
    val premiumRequiredTitle: String,
    val premiumRequiredBody: String,
    val subscribeNow: String,
    val adminPanelTitle: String,
    val onboardingContinue: String
)

fun stringsFor(language: AppLanguage): AppStrings = when (language) {
    AppLanguage.BN -> AppStrings(
        language = AppLanguage.BN,
        appName = "Smart School Library BD",
        appTagline = "পড়ো, জানো, এগিয়ে যাও...",
        loading = "লোড হচ্ছে...",
        retry = "পুনরায় চেষ্টা করুন",
        cancel = "বাতিল",
        confirm = "নিশ্চিত করুন",
        save = "সংরক্ষণ করুন",
        delete = "মুছে ফেলুন",
        edit = "সম্পাদনা",
        add = "নতুন যোগ করুন",
        close = "বন্ধ করুন",
        searchHint = "বই, বিষয় বা নোট খুঁজুন...",
        navHome = "হোম",
        navLibrary = "লাইব্রেরি",
        navBookmarks = "বুকমার্ক",
        navProfile = "প্রোফাইল",
        welcomePrefix = "স্বাগতম",
        welcomeSubtitle = "পড়াশোনা হোক আরও সুন্দর!",
        classesHeader = "শ্রেণি নির্বাচন করুন",
        continueReading = "পড়া চালিয়ে যান",
        recentBooks = "সাম্প্রতিক খোলা বই",
        popularBooks = "জনপ্রিয় পাঠ্যবই",
        latestNotices = "সর্বশেষ নোটিশ",
        learningProgress = "শেখার অগ্রগতি",
        otherFeatures = "অন্যান্য সুবিধাসমূহ",
        featureNotebook = "নোটবুক",
        featureGk = "সাধারণ জ্ঞান",
        featureExam = "পরীক্ষার প্রস্তুতি",
        featureBookmarks = "আমার বুকমার্ক",
        featureHistory = "পড়ার ইতিহাস",
        featurePremium = "প্রিমিয়াম সাবস্ক্রিপশন",
        featureNotifications = "নোটিফিকেশন",
        featureSettings = "সেটিংস",
        originalTextbooks = "মূল পাঠ্যবই",
        guidesAndNotes = "গাইড ও নোট",
        allBooksSub = "সকল অনুমোদিত পাঠ্যবই",
        allGuidesSub = "অধ্যায়ভিত্তিক গাইড, নোট ও সাজেশন",
        selectStreamTitle = "বিভাগ নির্বাচন করুন",
        streamScience = "বিজ্ঞান বিভাগ",
        streamHumanities = "মানবিক বিভাগ",
        streamBusiness = "ব্যবসায় শিক্ষা বিভাগ",
        freeBadge = "ফ্রি",
        premiumBadge = "প্রিমিয়াম",
        demoBadge = "ডেমো (DEMO)",
        readNow = "পড়ুন",
        pagesLabel = "পৃষ্ঠা",
        allFilter = "সব",
        emptyStateTitle = "কোনো তথ্য পাওয়া যায়নি",
        emptyStateBody = "এই বিভাগে এখনো কোনো অনুমোদিত কনটেন্ট প্রকাশিত হয়নি।",
        offlineError = "ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।",
        loginTitle = "লগইন করুন",
        registerTitle = "নতুন অ্যাকাউন্ট খুলুন",
        emailLabel = "ইমেইল ঠিকানা",
        passwordLabel = "পাসওয়ার্ড",
        fullNameLabel = "শিক্ষার্থীর পূর্ণ নাম",
        loginButton = "লগইন করুন",
        registerButton = "নিবন্ধন করুন",
        googleSignIn = "Google দিয়ে সাইন ইন করুন",
        forgotPassword = "পাসওয়ার্ড ভুলে গেছেন?",
        logoutButton = "লগ আউট",
        subscriptionTitle = "সাবস্ক্রিপশন ও প্রিমিয়াম প্ল্যান",
        premiumRequiredTitle = "প্রিমিয়াম কনটেন্ট",
        premiumRequiredBody = "এই শ্রেণির প্রিমিয়াম পাঠ্যবই ও গাইড পড়তে সক্রিয় সাবস্ক্রিপশন প্রয়োজন।",
        subscribeNow = "সাবস্ক্রিপশন করুন",
        adminPanelTitle = "সুপার অ্যাডমিন প্যানেল",
        onboardingContinue = "চলুন শুরু করি →"
    )
    AppLanguage.EN -> AppStrings(
        language = AppLanguage.EN,
        appName = "Smart School Library BD",
        appTagline = "Read, Learn, Move Forward...",
        loading = "Loading...",
        retry = "Retry",
        cancel = "Cancel",
        confirm = "Confirm",
        save = "Save",
        delete = "Delete",
        edit = "Edit",
        add = "Add New",
        close = "Close",
        searchHint = "Search books, subjects, or notes...",
        navHome = "Home",
        navLibrary = "Library",
        navBookmarks = "Bookmarks",
        navProfile = "Profile",
        welcomePrefix = "Welcome",
        welcomeSubtitle = "Make your learning journey brighter!",
        classesHeader = "Select Your Class",
        continueReading = "Continue Reading",
        recentBooks = "Recently Opened Books",
        popularBooks = "Popular Textbooks",
        latestNotices = "Latest Notices",
        learningProgress = "Learning Progress",
        otherFeatures = "More Study Tools",
        featureNotebook = "Notebook",
        featureGk = "General Knowledge",
        featureExam = "Exam Prep",
        featureBookmarks = "My Bookmarks",
        featureHistory = "Reading History",
        featurePremium = "Premium Plans",
        featureNotifications = "Notifications",
        featureSettings = "Settings",
        originalTextbooks = "Original Textbooks",
        guidesAndNotes = "Guides & Notes",
        allBooksSub = "All authorized NCTB textbooks",
        allGuidesSub = "Chapter notes, CQ, MCQ & suggestions",
        selectStreamTitle = "Select Academic Stream",
        streamScience = "Science Stream",
        streamHumanities = "Humanities Stream",
        streamBusiness = "Business Studies Stream",
        freeBadge = "Free",
        premiumBadge = "Premium",
        demoBadge = "DEMO",
        readNow = "Read",
        pagesLabel = "pages",
        allFilter = "All",
        emptyStateTitle = "No Content Found",
        emptyStateBody = "No authorized content has been published in this category yet.",
        offlineError = "Please check your internet connection and try again.",
        loginTitle = "Sign In",
        registerTitle = "Create Student Account",
        emailLabel = "Email Address",
        passwordLabel = "Password",
        fullNameLabel = "Student Full Name",
        loginButton = "Sign In",
        registerButton = "Create Account",
        googleSignIn = "Sign in with Google",
        forgotPassword = "Forgot Password?",
        logoutButton = "Log Out",
        subscriptionTitle = "Class Subscriptions",
        premiumRequiredTitle = "Premium Content",
        premiumRequiredBody = "An active class subscription is required to access this premium textbook or guide.",
        subscribeNow = "View Subscription Plans",
        adminPanelTitle = "Super Admin Panel",
        onboardingContinue = "Get Started →"
    )
}

val LocalAppStrings = staticCompositionLocalOf { stringsFor(AppLanguage.BN) }

private val bnDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')

fun Int.formatLocalized(lang: AppLanguage): String = toString().formatDigits(lang)
fun Long.formatLocalized(lang: AppLanguage): String = toString().formatDigits(lang)
fun String.formatLocalized(lang: AppLanguage): String = formatDigits(lang)

fun String.formatDigits(lang: AppLanguage): String {
    if (lang == AppLanguage.EN) return this
    val sb = StringBuilder(length)
    for (c in this) {
        if (c in '0'..'9') {
            sb.append(bnDigits[c - '0'])
        } else {
            sb.append(c)
        }
    }
    return sb.toString()
}

fun classTitle(classNumber: Int, lang: AppLanguage): String = when (classNumber) {
    5 -> if (lang == AppLanguage.BN) "পঞ্চম শ্রেণি" else "Class 5"
    6 -> if (lang == AppLanguage.BN) "ষষ্ঠ শ্রেণি" else "Class 6"
    7 -> if (lang == AppLanguage.BN) "সপ্তম শ্রেণি" else "Class 7"
    8 -> if (lang == AppLanguage.BN) "অষ্টম শ্রেণি" else "Class 8"
    9 -> if (lang == AppLanguage.BN) "নবম শ্রেণি" else "Class 9"
    10 -> if (lang == AppLanguage.BN) "দশম শ্রেণি" else "Class 10"
    11 -> if (lang == AppLanguage.BN) "একাদশ শ্রেণি" else "Class 11"
    12 -> if (lang == AppLanguage.BN) "দ্বাদশ শ্রেণি" else "Class 12"
    0 -> if (lang == AppLanguage.BN) "শিক্ষা স্তর / সাধারণ" else "Education Level"
    else -> if (lang == AppLanguage.BN) "শ্রেণি ${classNumber.formatLocalized(lang)}" else "Class $classNumber"
}

fun streamTitle(streamCode: String, lang: AppLanguage): String = when (streamCode.uppercase()) {
    "SCIENCE" -> if (lang == AppLanguage.BN) "বিজ্ঞান বিভাগ" else "Science"
    "HUMANITIES" -> if (lang == AppLanguage.BN) "মানবিক বিভাগ" else "Humanities"
    "BUSINESS" -> if (lang == AppLanguage.BN) "ব্যবসায় শিক্ষা বিভাগ" else "Business Studies"
    else -> if (lang == AppLanguage.BN) "সাধারণ" else "General"
}

fun streamShortLabel(streamCode: String, lang: AppLanguage): String = when (streamCode.uppercase()) {
    "SCIENCE" -> if (lang == AppLanguage.BN) "বিজ্ঞান" else "Science"
    "HUMANITIES" -> if (lang == AppLanguage.BN) "মানবিক" else "Humanities"
    "BUSINESS" -> if (lang == AppLanguage.BN) "ব্যবসায় শিক্ষা" else "Business Studies"
    "ALL" -> if (lang == AppLanguage.BN) "সকল বিভাগ" else "Common"
    else -> if (lang == AppLanguage.BN) "সাধারণ" else "General"
}

fun com.example.model.Textbook.localizedTitle(lang: AppLanguage): String =
    if (lang == AppLanguage.EN && titleEn.isNotBlank()) titleEn else titleBn

fun com.example.model.Textbook.localizedSubject(lang: AppLanguage): String =
    if (lang == AppLanguage.EN && subjectNameEn.isNotBlank()) subjectNameEn else subjectNameBn

fun com.example.model.Textbook.localizedAuthor(lang: AppLanguage): String =
    author.takeIf { it.isNotBlank() }
        ?: if (lang == AppLanguage.BN) "জাতীয় শিক্ষাক্রম ও পাঠ্যপুস্তক বোর্ড" else "NCTB Curriculum Board"

fun com.example.model.Textbook.localizedPublisher(lang: AppLanguage): String =
    publisher.takeIf { it.isNotBlank() }
        ?: if (lang == AppLanguage.BN) "এনসিটিবি (NCTB)" else "NCTB Bangladesh"

fun com.example.model.Textbook.localizedEdition(lang: AppLanguage): String {
    val ed = edition.ifBlank { "NCTB" }
    val yr = academicYear.ifBlank { "2026" }
    return if (ed.contains(yr)) {
        ed.formatLocalized(lang)
    } else {
        "$ed (${yr.formatLocalized(lang)})"
    }
}

fun com.example.model.Textbook.localizedShortDescription(lang: AppLanguage): String {
    shortDescription.takeIf { it.isNotBlank() }?.let { return it }
    description.takeIf { it.isNotBlank() }?.let { return it }
    val clsText = classTitle(classNumber, lang)
    val streamPart = if (classNumber >= 9 && stream.uppercase() in listOf("SCIENCE", "HUMANITIES", "BUSINESS")) {
        " (${streamShortLabel(stream, lang)})"
    } else {
        ""
    }
    return if (lang == AppLanguage.BN) {
        "$clsText$streamPart-এর ${localizedSubject(lang)} বিষয়ের অনুমোদিত মূল পাঠ্যবই • মোট ${pageCount.formatLocalized(lang)} পৃষ্ঠা।"
    } else {
        "Authorized ${localizedSubject(lang)} textbook for $clsText$streamPart • ${pageCount} pages."
    }
}

fun com.example.model.Textbook.localizedFullDescription(lang: AppLanguage): String {
    description.takeIf { it.isNotBlank() }?.let { return it }
    shortDescription.takeIf { it.isNotBlank() }?.let { return it }
    val clsText = classTitle(classNumber, lang)
    val streamPart = if (classNumber >= 9 && stream.uppercase() in listOf("SCIENCE", "HUMANITIES", "BUSINESS")) {
        if (lang == AppLanguage.BN) " এবং ${streamTitle(stream, lang)}-এর" else " (${streamTitle(stream, lang)} stream)"
    } else {
        ""
    }
    return if (lang == AppLanguage.BN) {
        "এই বইটি $clsText$streamPart শিক্ষার্থীদের জন্য ${localizedSubject(lang)} বিষয়ের অনুমোদিত পাঠ্যক্রম অনুসারে প্রণীত। এতে অধ্যায়ভিত্তিক পাঠ, উদাহরণ ও অনুশীলনীর মাধ্যমে শিক্ষার্থীদের মৌলিক ধারণা স্পষ্ট করতে সহায়তা করা হয়েছে। সংস্করণ: ${localizedEdition(lang)}, মোট পৃষ্ঠা: ${pageCount.formatLocalized(lang)}।"
    } else {
        "This volume is prepared according to the authorized curriculum of ${localizedSubject(lang)} for $clsText$streamPart students. It covers structured chapters, worked examples, and exercises to build strong conceptual foundations. Edition: ${localizedEdition(lang)}, Total Pages: $pageCount."
    }
}

fun com.example.model.GuideNote.localizedTitle(lang: AppLanguage): String =
    if (lang == AppLanguage.EN && titleEn.isNotBlank()) titleEn else titleBn

fun com.example.model.GuideNote.localizedSubject(lang: AppLanguage): String =
    if (lang == AppLanguage.EN && subjectNameEn.isNotBlank()) subjectNameEn else subjectNameBn

fun com.example.model.GuideNote.localizedAuthor(lang: AppLanguage): String =
    author.takeIf { it.isNotBlank() }
        ?: if (lang == AppLanguage.BN) "স্মার্ট স্কুল সম্পাদনা পর্ষদ" else "Smart School Editorial Panel"

fun com.example.model.GuideNote.localizedPublisher(lang: AppLanguage): String =
    publisher.takeIf { it.isNotBlank() } ?: "Smart School Library BD"

fun com.example.model.GuideNote.localizedEdition(lang: AppLanguage): String =
    edition.takeIf { it.isNotBlank() }?.formatLocalized(lang)
        ?: if (lang == AppLanguage.BN) "সহায়ক সংস্করণ ২০২৬" else "Study Edition 2026"

fun com.example.model.GuideNote.localizedShortDescription(lang: AppLanguage): String {
    shortDescription.takeIf { it.isNotBlank() }?.let { return it }
    val preview = summaryContent.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
    if (!preview.isNullOrBlank()) return preview
    val clsText = classTitle(classNumber, lang)
    return if (lang == AppLanguage.BN) {
        "$clsText • ${localizedSubject(lang)} (${chapter}) বিষয়ের অধ্যায়ভিত্তিক গাইড, নোট ও অনুশীলনী।"
    } else {
        "Structured study guide, notes, and practice materials for $clsText • ${localizedSubject(lang)} ($chapter)."
    }
}

fun com.example.model.GuideNote.derivedChapters(): List<String> {
    if (tableOfContents.isNotEmpty()) return tableOfContents
    val headings = summaryContent.lines()
        .map { it.trim() }
        .filter { line ->
            line.length in 3..120 && (
                line.startsWith("#") ||
                    line.startsWith("অধ্যায়") ||
                    line.startsWith("পাঠ") ||
                    line.startsWith("Chapter", ignoreCase = true) ||
                    line.matches(Regex("^[০-৯0-9]+[.)\\-: ].+"))
                )
        }
        .map { it.trimStart('#', ' ').trim() }
        .distinct()
        .take(25)
    if (headings.isNotEmpty()) return headings
    return listOfNotNull(chapter.takeIf { it.isNotBlank() })
}

