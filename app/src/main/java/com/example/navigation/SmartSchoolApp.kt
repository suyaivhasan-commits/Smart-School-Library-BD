package com.example.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.AuthScreen
import com.example.bookmarks.BookmarksScreen
import com.example.bookmarks.ReadingHistoryScreen
import com.example.classes.ClassSelectionScreen
import com.example.components.EmptyOrErrorStateCard
import com.example.data.AuthRepository
import com.example.data.LibraryRepository
import com.example.data.PreferencesRepository
import com.example.data.PremiumAccessDecision
import com.example.data.StudyRepository
import com.example.data.ThemeMode
import com.example.data.UserPreferencesState
import com.example.exam.ExamPrepHubScreen
import com.example.exam.ExamSessionScreen
import com.example.firebase.SecurityConstants
import com.example.gk.GeneralKnowledgeScreen
import com.example.home.HomeScreen
import com.example.library.BookDetailScreen
import com.example.library.GuideDetailScreen
import com.example.library.GuideListScreen
import com.example.library.LibraryHubScreen
import com.example.library.TextbookListScreen
import com.example.model.AppSettings
import com.example.model.Bookmark
import com.example.model.DefaultCatalogSeed
import com.example.model.GuideNote
import com.example.model.ReadingProgress
import com.example.model.Textbook
import com.example.model.UiState
import com.example.notebook.NotebookScreen
import com.example.profile.NoticesScreen
import com.example.profile.ProfileScreen
import com.example.reader.PdfReaderScreen
import com.example.settings.LegalInfoScreen
import com.example.settings.SettingsScreen
import com.example.subscription.PremiumAccessGateScreen
import com.example.subscription.SubscriptionScreen
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.theme.SmartSchoolTheme
import com.example.welcome.WelcomeScreen
import kotlinx.coroutines.launch

private data class StudentNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun SmartSchoolApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    val prefsRepository = remember(appContext) { PreferencesRepository(appContext) }
    val authRepository = remember(appContext) { AuthRepository(appContext) }

    val prefs by prefsRepository.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = UserPreferencesState()
    )
    val firebaseUser by authRepository.observeAuthState().collectAsStateWithLifecycle(
        initialValue = authRepository.currentUser
    )

    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = when (prefs.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val appStrings = remember(prefs.language) { stringsFor(prefs.language) }

    SmartSchoolTheme(darkTheme = isDarkTheme) {
        CompositionLocalProvider(LocalAppStrings provides appStrings) {
            when {
                !prefs.isLoaded -> {
                    Box(modifier = Modifier.fillMaxSize().testTag("preferences_loading_box"))
                }
                firebaseUser == null -> {
                    // Unauthenticated state: NEVER instantiate Firestore-backed ViewModels here
                    AuthScreen(
                        authRepository = authRepository,
                        onAuthSuccess = { /* FirebaseAuth listener transitions automatically */ },
                        onToggleLanguage = {
                            scope.launch {
                                val next = if (prefs.language == AppLanguage.BN) AppLanguage.EN else AppLanguage.BN
                                prefsRepository.setLanguage(next)
                            }
                        }
                    )
                }
                else -> {
                    val activeUser = firebaseUser!!
                    AuthenticatedStudentWorkspace(
                        currentUserUid = activeUser.uid,
                        authRepository = authRepository,
                        prefsRepository = prefsRepository,
                        preferences = prefs,
                        isDarkTheme = isDarkTheme
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedStudentWorkspace(
    currentUserUid: String,
    authRepository: AuthRepository,
    prefsRepository: PreferencesRepository,
    preferences: UserPreferencesState,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current
    val lang = strings.language
    val snackbarHostState = remember { SnackbarHostState() }

    val sessionViewModel: StudentSessionViewModel = viewModel(
        key = "student_session_$currentUserUid",
        factory = viewModelFactory {
            initializer {
                StudentSessionViewModel(
                    currentUserId = currentUserUid,
                    authRepository = authRepository,
                    libraryRepository = LibraryRepository(appContext),
                    studyRepository = StudyRepository(appContext)
                )
            }
        }
    )

    val userProfileState by sessionViewModel.userProfileState.collectAsStateWithLifecycle()
    val classesState by sessionViewModel.classesState.collectAsStateWithLifecycle()
    val educationLevelsState by sessionViewModel.educationLevelsState.collectAsStateWithLifecycle()
    val streamsState by sessionViewModel.streamsState.collectAsStateWithLifecycle()
    val subjectsState by sessionViewModel.subjectsState.collectAsStateWithLifecycle()
    val booksState by sessionViewModel.booksState.collectAsStateWithLifecycle()
    val guidesState by sessionViewModel.guidesState.collectAsStateWithLifecycle()
    val noticesState by sessionViewModel.noticesState.collectAsStateWithLifecycle()
    val appSettingsState by sessionViewModel.appSettingsState.collectAsStateWithLifecycle()
    val bookmarksState by sessionViewModel.bookmarksState.collectAsStateWithLifecycle()
    val readingProgressState by sessionViewModel.readingProgressState.collectAsStateWithLifecycle()
    val notesState by sessionViewModel.notesState.collectAsStateWithLifecycle()
    val gkState by sessionViewModel.gkState.collectAsStateWithLifecycle()
    val examsState by sessionViewModel.examsState.collectAsStateWithLifecycle()
    val examHistoryState by sessionViewModel.examHistoryState.collectAsStateWithLifecycle()
    val plansState by sessionViewModel.plansState.collectAsStateWithLifecycle()
    val paymentMethodsState by sessionViewModel.paymentMethodsState.collectAsStateWithLifecycle()
    val subscriptionsState by sessionViewModel.subscriptionsState.collectAsStateWithLifecycle()
    val subRequestsState by sessionViewModel.subscriptionRequestsState.collectAsStateWithLifecycle()
    val userNotificationsState by sessionViewModel.userNotificationsState.collectAsStateWithLifecycle()
    val latestExamResult by sessionViewModel.latestExamResult.collectAsStateWithLifecycle()
    val statusMessage by sessionViewModel.statusMessage.collectAsStateWithLifecycle()

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            sessionViewModel.clearStatusMessage()
        }
    }

    val userProfile = (userProfileState as? UiState.Success)?.data
    val classes = (classesState as? UiState.Success)?.data.orEmpty()
    val educationLevels = (educationLevelsState as? UiState.Success)?.data.orEmpty()
    val streams = (streamsState as? UiState.Success)?.data.orEmpty()
    val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
    val books = (booksState as? UiState.Success)?.data.orEmpty()
    val guides = (guidesState as? UiState.Success)?.data.orEmpty()
    val notices = (noticesState as? UiState.Success)?.data.orEmpty()
    val appSettings = (appSettingsState as? UiState.Success)?.data ?: AppSettings()
    val bookmarks = (bookmarksState as? UiState.Success)?.data.orEmpty()
    val readingProgress = (readingProgressState as? UiState.Success)?.data.orEmpty()
    val notes = (notesState as? UiState.Success)?.data.orEmpty()
    val plans = (plansState as? UiState.Success)?.data.orEmpty()
    val subscriptions = (subscriptionsState as? UiState.Success)?.data.orEmpty()
    val userNotifications = (userNotificationsState as? UiState.Success)?.data.orEmpty()
    val unreadUserNotifCount = remember(userNotifications) { userNotifications.count { !it.isRead } }
    val exams = (examsState as? UiState.Success)?.data.orEmpty()
    val examAttempts = (examHistoryState as? UiState.Success)?.data.orEmpty()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route.orEmpty()

    // Student Bottom Navigation / Rail — STRICTLY 4 items (NEVER includes Admin!)
    val studentNavItems = listOf(
        StudentNavItem(Routes.HOME, strings.navHome, Icons.Filled.Home, "nav_tab_home"),
        StudentNavItem(Routes.LIBRARY_HUB, strings.navLibrary, Icons.AutoMirrored.Filled.MenuBook, "nav_tab_library"),
        StudentNavItem(Routes.BOOKMARKS, strings.navBookmarks, Icons.Filled.Bookmark, "nav_tab_bookmarks"),
        StudentNavItem(Routes.PROFILE, strings.navProfile, Icons.Filled.Person, "nav_tab_profile")
    )

    val hideStudentNavChrome = currentRoute.startsWith("pdf_reader") ||
        currentRoute.startsWith("exam_session") ||
        currentRoute.startsWith("premium_gate") ||
        currentRoute == Routes.WELCOME_INTRO

    fun navigateTopLevel(route: String) {
        if (route == currentRoute) return
        if (route == Routes.HOME) {
            val popped = navController.popBackStack(Routes.HOME, inclusive = false)
            if (!popped) {
                navController.navigate(Routes.HOME) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            return
        }
        navController.navigate(route) {
            popUpTo(Routes.HOME) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openBookWithEligibility(book: Textbook, initialPage: Int = 0) {
        val decision = sessionViewModel.evaluatePremiumAccess(
            isPremium = book.isPremium,
            classNumber = book.classNumber,
            stream = book.stream
        )
        when (decision) {
            PremiumAccessDecision.ALLOWED_FREE,
            PremiumAccessDecision.ALLOWED_ADMIN,
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION -> {
                navController.navigate(Routes.pdfReader("BOOK", book.id, initialPage))
            }
            PremiumAccessDecision.DENIED_SUBSCRIPTION_EXPIRED -> {
                navController.navigate(Routes.premiumGate(book.classNumber, book.stream, expired = true))
            }
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED -> {
                navController.navigate(Routes.premiumGate(book.classNumber, book.stream, expired = false))
            }
            PremiumAccessDecision.DENIED_ACCOUNT_INACTIVE -> {
                scope.launch { snackbarHostState.showSnackbar("আপনার অ্যাকাউন্টটি নিষ্ক্রিয় রয়েছে।") }
            }
            PremiumAccessDecision.DENIED_UNAUTHENTICATED -> Unit
        }
    }

    fun isContentUnlocked(isPremium: Boolean, classNumber: Int, stream: String): Boolean {
        val effectivePremium = if (classNumber in 5..10) true else isPremium
        val decision = sessionViewModel.evaluatePremiumAccess(
            isPremium = effectivePremium,
            classNumber = classNumber,
            stream = stream
        )
        return decision in listOf(
            PremiumAccessDecision.ALLOWED_FREE,
            PremiumAccessDecision.ALLOWED_ADMIN,
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION
        )
    }

    fun openGuidePdfWithEligibility(guide: GuideNote) {
        val decision = sessionViewModel.evaluatePremiumAccess(
            isPremium = guide.isPremium,
            classNumber = guide.classNumber,
            stream = guide.stream
        )
        when (decision) {
            PremiumAccessDecision.ALLOWED_FREE,
            PremiumAccessDecision.ALLOWED_ADMIN,
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION -> {
                navController.navigate(Routes.pdfReader("GUIDE", guide.id, 0))
            }
            PremiumAccessDecision.DENIED_SUBSCRIPTION_EXPIRED -> {
                navController.navigate(Routes.premiumGate(guide.classNumber, guide.stream, expired = true))
            }
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED -> {
                navController.navigate(Routes.premiumGate(guide.classNumber, guide.stream, expired = false))
            }
            PremiumAccessDecision.DENIED_ACCOUNT_INACTIVE -> {
                scope.launch { snackbarHostState.showSnackbar("আপনার অ্যাকাউন্টটি নিষ্ক্রিয় রয়েছে।") }
            }
            PremiumAccessDecision.DENIED_UNAUTHENTICATED -> Unit
        }
    }

    fun resumeProgressWithEligibility(item: ReadingProgress) {
        val decision = sessionViewModel.evaluatePremiumAccess(
            isPremium = item.isPremium,
            classNumber = item.classNumber,
            stream = item.stream
        )
        when (decision) {
            PremiumAccessDecision.ALLOWED_FREE,
            PremiumAccessDecision.ALLOWED_ADMIN,
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION -> {
                if (item.contentType == "GUIDE" && item.pdfUrl.isBlank()) {
                    navController.navigate(Routes.guideDetail(item.contentId))
                } else {
                    navController.navigate(Routes.pdfReader(item.contentType, item.contentId, item.lastPage))
                }
            }
            PremiumAccessDecision.DENIED_SUBSCRIPTION_EXPIRED -> {
                navController.navigate(Routes.premiumGate(item.classNumber, item.stream, expired = true))
            }
            else -> {
                navController.navigate(Routes.premiumGate(item.classNumber, item.stream, expired = false))
            }
        }
    }

    fun openBookmarkTarget(bm: Bookmark) {
        when (bm.bookmarkType) {
            "GK" -> navController.navigate(Routes.GENERAL_KNOWLEDGE)
            "GUIDE" -> {
                val guide = guides.firstOrNull { it.id == bm.targetId }
                if (guide != null) {
                    openGuidePdfWithEligibility(guide)
                } else if (bm.pdfUrl.isNotBlank()) {
                    if (isContentUnlocked(bm.isPremium, bm.classNumber, "ALL")) {
                        navController.navigate(Routes.pdfReader("GUIDE", bm.targetId, 0))
                    } else {
                        navController.navigate(Routes.premiumGate(bm.classNumber, "ALL"))
                    }
                }
            }
            "PAGE" -> {
                val book = books.firstOrNull { it.id == bm.targetId }
                if (book != null) {
                    openBookWithEligibility(book, initialPage = bm.pageNumber.coerceAtLeast(1))
                } else if (isContentUnlocked(bm.isPremium, bm.classNumber, "ALL")) {
                    navController.navigate(Routes.pdfReader("BOOK", bm.targetId, bm.pageNumber.coerceAtLeast(1)))
                } else {
                    navController.navigate(Routes.premiumGate(bm.classNumber, "ALL"))
                }
            }
            else -> {
                val book = books.firstOrNull { it.id == bm.targetId }
                if (book != null) {
                    openBookWithEligibility(book)
                } else if (isContentUnlocked(bm.isPremium, bm.classNumber, "ALL")) {
                    navController.navigate(Routes.pdfReader("BOOK", bm.targetId, 0))
                } else {
                    navController.navigate(Routes.premiumGate(bm.classNumber, "ALL"))
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!isWideScreen && !hideStudentNavChrome) {
                    NavigationBar(modifier = Modifier.testTag("student_bottom_nav")) {
                        studentNavItems.forEach { navItem ->
                            val isTabSelected = when (navItem.route) {
                                Routes.HOME -> currentRoute == Routes.HOME || currentRoute.startsWith("class_detail") || currentRoute.startsWith("textbooks") || currentRoute.startsWith("guides")
                                Routes.LIBRARY_HUB -> currentRoute == Routes.LIBRARY_HUB
                                Routes.BOOKMARKS -> currentRoute == Routes.BOOKMARKS
                                Routes.PROFILE -> currentRoute == Routes.PROFILE
                                else -> currentRoute == navItem.route
                            }
                            NavigationBarItem(
                                selected = isTabSelected,
                                onClick = { navigateTopLevel(navItem.route) },
                                icon = { Icon(navItem.icon, contentDescription = navItem.label) },
                                label = { Text(navItem.label) },
                                modifier = Modifier.testTag(navItem.tag)
                            )
                        }
                    }
                }
            }
        ) { scaffoldPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
            ) {
                if (isWideScreen && !hideStudentNavChrome) {
                    NavigationRail(modifier = Modifier.testTag("student_nav_rail")) {
                        studentNavItems.forEach { navItem ->
                            val isTabSelected = when (navItem.route) {
                                Routes.HOME -> currentRoute == Routes.HOME || currentRoute.startsWith("class_detail") || currentRoute.startsWith("textbooks") || currentRoute.startsWith("guides")
                                Routes.LIBRARY_HUB -> currentRoute == Routes.LIBRARY_HUB
                                Routes.BOOKMARKS -> currentRoute == Routes.BOOKMARKS
                                Routes.PROFILE -> currentRoute == Routes.PROFILE
                                else -> currentRoute == navItem.route
                            }
                            NavigationRailItem(
                                selected = isTabSelected,
                                onClick = { navigateTopLevel(navItem.route) },
                                icon = { Icon(navItem.icon, contentDescription = navItem.label) },
                                label = { Text(navItem.label) },
                                modifier = Modifier.testTag(navItem.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME
                    ) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                userProfile = userProfile,
                                classes = classes,
                                educationLevels = educationLevels,
                                books = books,
                                guides = guides,
                                bookmarks = bookmarks,
                                readingProgress = readingProgress,
                                notices = notices,
                                subscriptions = subscriptions,
                                appSettings = appSettings,
                                unreadNotificationCount = unreadUserNotifCount,
                                isDarkTheme = isDarkTheme,
                                onToggleTheme = {
                                    scope.launch {
                                        val next = if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                                        prefsRepository.setThemeMode(next)
                                    }
                                },
                                onOpenProfile = { navigateTopLevel(Routes.PROFILE) },
                                onOpenNotifications = { navController.navigate(Routes.NOTICES) },
                                onOpenClass = { classNum ->
                                    val now = System.currentTimeMillis()
                                    val isAdmin = SecurityConstants.isTrustedSuperAdmin(currentUserUid)
                                    val hasClassAccess = sessionViewModel.evaluateClassAccess(classNum, "ALL", now)
                                    when {
                                        isAdmin -> navController.navigate(Routes.classDetail(classNum))
                                        classNum in 9..10 -> navController.navigate(Routes.classDetail(classNum))
                                        hasClassAccess -> navController.navigate(Routes.classDetail(classNum))
                                        classNum !in 5..10 -> {
                                            val hasFreeContent = books.any { it.classNumber == classNum && !it.isPremium } ||
                                                guides.any { it.classNumber == classNum && !it.isPremium }
                                            val clsObj = classes.firstOrNull { it.classNumber == classNum }
                                            val matchingLevelIds = educationLevels.filter { lvl ->
                                                (clsObj?.educationLevelId?.isNotBlank() == true && clsObj.educationLevelId == lvl.id) ||
                                                    (lvl.minClassNumber > 0 && classNum in lvl.minClassNumber..lvl.maxClassNumber)
                                            }.map { it.id }.toSet()
                                            val hasPlansForClass = plans.any { p ->
                                                p.classNumber == classNum || (p.classNumber == 0 && p.educationLevelId in matchingLevelIds)
                                            }
                                            if (hasFreeContent || !hasPlansForClass) {
                                                navController.navigate(Routes.classDetail(classNum))
                                            } else {
                                                val msg = "শ্রেণি $classNum-এ প্রবেশ করতে সক্রিয় সাবস্ক্রিপশন প্রয়োজন।"
                                                scope.launch { snackbarHostState.showSnackbar(msg) }
                                                navController.navigate(Routes.subscription(classNum))
                                            }
                                        }
                                        else -> {
                                            val classSubs = subscriptions.filter { it.classNumber == classNum }
                                            val hadExpiredSub = classSubs.isNotEmpty()
                                            val msg = if (hadExpiredSub) {
                                                "শ্রেণি $classNum-এর সাবস্ক্রিপশনের মেয়াদ শেষ হয়েছে। নবায়ন করতে অনুগ্রহ করে সাবস্ক্রিপশন নিন।"
                                            } else {
                                                "শ্রেণি $classNum-এ প্রবেশ করতে সক্রিয় সাবস্ক্রিপশন প্রয়োজন।"
                                            }
                                            scope.launch { snackbarHostState.showSnackbar(msg) }
                                            navController.navigate(Routes.subscription(classNum))
                                        }
                                    }
                                },
                                onOpenBook = { book -> openBookWithEligibility(book) },
                                onOpenGuide = { guide -> openGuidePdfWithEligibility(guide) },
                                onResumeReading = { prog -> resumeProgressWithEligibility(prog) },
                                onToggleBookBookmark = { b ->
                                    sessionViewModel.toggleBookmark(
                                        bookmarkType = "BOOK",
                                        targetId = b.id,
                                        titleBn = b.titleBn,
                                        subtitleBn = b.subjectNameBn,
                                        classNumber = b.classNumber,
                                        coverUrl = b.coverUrl,
                                        pdfUrl = b.pdfUrl,
                                        isPremium = b.isPremium
                                    )
                                },
                                onToggleGuideBookmark = { g ->
                                    sessionViewModel.toggleBookmark(
                                        bookmarkType = "GUIDE",
                                        targetId = g.id,
                                        titleBn = g.titleBn,
                                        subtitleBn = "${g.subjectNameBn} • ${g.chapter}",
                                        classNumber = g.classNumber,
                                        coverUrl = g.coverUrl,
                                        pdfUrl = g.pdfUrl,
                                        isPremium = g.isPremium
                                    )
                                },
                                onOpenNotebook = { navController.navigate(Routes.NOTEBOOK) },
                                onOpenGk = { navController.navigate(Routes.GENERAL_KNOWLEDGE) },
                                onOpenExam = { navController.navigate(Routes.examPrep(0)) },
                                onOpenBookmarks = { navigateTopLevel(Routes.BOOKMARKS) },
                                onOpenHistory = { navController.navigate(Routes.READING_HISTORY) },
                                onOpenSubscription = {
                                    navController.navigate(
                                        Routes.subscription(
                                            userProfile?.selectedClass ?: 5,
                                            userProfile?.selectedStream ?: "ALL"
                                        )
                                    )
                                },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                            )
                        }

                        composable(Routes.LIBRARY_HUB) {
                            val nowEpochMs = System.currentTimeMillis()
                            val isAdmin = SecurityConstants.isTrustedSuperAdmin(currentUserUid)
                            val libraryBooksState = remember(booksState, subscriptions, educationLevels, plans, isAdmin, nowEpochMs) {
                                if (isAdmin) {
                                    booksState
                                } else when (val state = booksState) {
                                    is UiState.Success -> {
                                        val filtered = state.data.filter { b ->
                                            if (b.classNumber in 5..10) {
                                                sessionViewModel.evaluateClassAccess(b.classNumber, b.stream, nowEpochMs)
                                            } else {
                                                !b.isPremium || sessionViewModel.evaluateClassAccess(b.classNumber, b.stream, nowEpochMs)
                                            }
                                        }
                                        UiState.Success(filtered)
                                    }
                                    else -> state
                                }
                            }
                            val libraryGuidesState = remember(guidesState, subscriptions, educationLevels, plans, isAdmin, nowEpochMs) {
                                if (isAdmin) {
                                    guidesState
                                } else when (val state = guidesState) {
                                    is UiState.Success -> {
                                        val filtered = state.data.filter { g ->
                                            if (g.classNumber in 5..10) {
                                                sessionViewModel.evaluateClassAccess(g.classNumber, g.stream, nowEpochMs)
                                            } else {
                                                !g.isPremium || sessionViewModel.evaluateClassAccess(g.classNumber, g.stream, nowEpochMs)
                                            }
                                        }
                                        UiState.Success(filtered)
                                    }
                                    else -> state
                                }
                            }
                            LibraryHubScreen(
                                classes = classes,
                                booksState = libraryBooksState,
                                guidesState = libraryGuidesState,
                                subjects = subjects,
                                bookmarks = bookmarks,
                                onOpenBook = { b -> openBookWithEligibility(b) },
                                onOpenGuide = { g -> openGuidePdfWithEligibility(g) },
                                onToggleBookBookmark = { b ->
                                    sessionViewModel.toggleBookmark(
                                        bookmarkType = "BOOK",
                                        targetId = b.id,
                                        titleBn = b.titleBn,
                                        subtitleBn = b.subjectNameBn,
                                        classNumber = b.classNumber,
                                        coverUrl = b.coverUrl,
                                        pdfUrl = b.pdfUrl,
                                        isPremium = b.isPremium
                                    )
                                },
                                onToggleGuideBookmark = { g ->
                                    sessionViewModel.toggleBookmark(
                                        bookmarkType = "GUIDE",
                                        targetId = g.id,
                                        titleBn = g.titleBn,
                                        subtitleBn = "${g.subjectNameBn} • ${g.chapter}",
                                        classNumber = g.classNumber,
                                        coverUrl = g.coverUrl,
                                        pdfUrl = g.pdfUrl,
                                        isPremium = g.isPremium
                                    )
                                },
                                isBookUnlocked = { b -> isContentUnlocked(b.isPremium, b.classNumber, b.stream) },
                                isGuideUnlocked = { g -> isContentUnlocked(g.isPremium, g.classNumber, g.stream) },
                                onRetry = sessionViewModel::retryAllStreams
                            )
                        }

                        composable(Routes.BOOKMARKS) {
                            BookmarksScreen(
                                bookmarksState = bookmarksState,
                                onBack = null,
                                onOpenBookmark = { openBookmarkTarget(it) },
                                onRemoveBookmark = { sessionViewModel.removeBookmark(it) },
                                onRetry = sessionViewModel::retryAllStreams
                            )
                        }

                        composable(Routes.PROFILE) {
                            ProfileScreen(
                                userProfile = userProfile,
                                classes = classes,
                                subscriptions = subscriptions,
                                booksReadCount = readingProgress.size,
                                bookmarksCount = bookmarks.size,
                                notesCount = notes.size,
                                examsCount = examAttempts.size,
                                onOpenReadingHistory = { navController.navigate(Routes.READING_HISTORY) },
                                onOpenBookmarks = { navigateTopLevel(Routes.BOOKMARKS) },
                                onOpenNotebook = { navController.navigate(Routes.NOTEBOOK) },
                                onOpenSubscription = {
                                    navController.navigate(
                                        Routes.subscription(
                                            userProfile?.selectedClass ?: 5,
                                            userProfile?.selectedStream ?: "ALL"
                                        )
                                    )
                                },
                                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                                onUpdateProfile = { name, cls, stream ->
                                    sessionViewModel.updateProfile(name, cls, stream)
                                },
                                onLogout = {
                                    scope.launch { authRepository.signOut(context) }
                                }
                            )
                        }

                        composable(
                            route = Routes.CLASS_DETAIL,
                            arguments = listOf(navArgument("classNumber") { type = NavType.IntType })
                        ) { entry ->
                            val classNumber = entry.arguments?.getInt("classNumber") ?: 5
                            val activeStreams = remember(subscriptions, classNumber) {
                                sessionViewModel.getActiveStreamsForClass(classNumber)
                            }
                            val isSuperAdmin = SecurityConstants.isTrustedSuperAdmin(currentUserUid)
                            val selectedClassMeta = classes.firstOrNull { it.classNumber == classNumber }
                            val hasStreamsFlag = selectedClassMeta?.hasStreams ?: (classNumber in 9..10)
                            ClassSelectionScreen(
                                classNumber = classNumber,
                                streams = streams,
                                initialStream = userProfile?.selectedStream,
                                hasStreamsOverride = hasStreamsFlag,
                                activeSubscribedStreams = activeStreams,
                                isSuperAdmin = isSuperAdmin,
                                onBack = { navController.popBackStack() },
                                onOpenTextbooks = { cls, st ->
                                    val now = System.currentTimeMillis()
                                    val hasFreeBook = cls !in 5..10 && books.any { it.classNumber == cls && !it.isPremium }
                                    val hasAccess = isSuperAdmin ||
                                        hasFreeBook ||
                                        sessionViewModel.evaluateClassAccess(cls, st, now)
                                    if (hasAccess) {
                                        navController.navigate(Routes.textbookList(cls, st))
                                    } else {
                                        scope.launch {
                                            val streamTitleStr = if (st == "ALL") "" else " (${com.example.ui.i18n.streamTitle(st, lang)})"
                                            snackbarHostState.showSnackbar("শ্রেণি $cls$streamTitleStr-এর পাঠ্যবই দেখতে সাবস্ক্রিপশন প্রয়োজন।")
                                        }
                                        navController.navigate(Routes.subscription(cls, st))
                                    }
                                },
                                onOpenGuides = { cls, st ->
                                    val now = System.currentTimeMillis()
                                    val hasFreeGuide = cls !in 5..10 && guides.any { it.classNumber == cls && !it.isPremium }
                                    val hasAccess = isSuperAdmin ||
                                        hasFreeGuide ||
                                        sessionViewModel.evaluateClassAccess(cls, st, now)
                                    if (hasAccess) {
                                        navController.navigate(Routes.guideList(cls, st))
                                    } else {
                                        scope.launch {
                                            val streamTitleStr = if (st == "ALL") "" else " (${com.example.ui.i18n.streamTitle(st, lang)})"
                                            snackbarHostState.showSnackbar("শ্রেণি $cls$streamTitleStr-এর গাইড দেখতে সাবস্ক্রিপশন প্রয়োজন।")
                                        }
                                        navController.navigate(Routes.subscription(cls, st))
                                    }
                                },
                                onOpenSubscription = { cls, st ->
                                    navController.navigate(Routes.subscription(cls, st))
                                },
                                onOpenGk = { navController.navigate(Routes.GENERAL_KNOWLEDGE) },
                                onOpenExamPrep = { cls -> navController.navigate(Routes.examPrep(cls)) }
                            )
                        }

                        composable(
                            route = Routes.TEXTBOOK_LIST,
                            arguments = listOf(
                                navArgument("classNumber") { type = NavType.IntType },
                                navArgument("stream") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val classNumber = entry.arguments?.getInt("classNumber") ?: 5
                            val stream = entry.arguments?.getString("stream") ?: "ALL"
                            val now = System.currentTimeMillis()
                            val hasFreeBook = classNumber !in 5..10 && books.any { it.classNumber == classNumber && !it.isPremium }
                            val isAuthorized = SecurityConstants.isTrustedSuperAdmin(currentUserUid) ||
                                hasFreeBook ||
                                sessionViewModel.evaluateClassAccess(classNumber, stream, now)

                            if (!isAuthorized) {
                                LaunchedEffect(classNumber, stream) {
                                    val streamTitleStr = if (stream == "ALL") "" else " (${com.example.ui.i18n.streamTitle(stream, lang)})"
                                    snackbarHostState.showSnackbar("শ্রেণি $classNumber$streamTitleStr-এর পাঠ্যবই দেখতে সক্রিয় সাবস্ক্রিপশন প্রয়োজন।")
                                    navController.navigate(Routes.subscription(classNumber, stream)) {
                                        popUpTo(Routes.HOME)
                                    }
                                }
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                            } else {
                                TextbookListScreen(
                                    classNumber = classNumber,
                                    stream = stream,
                                    subjects = subjects,
                                    booksState = booksState,
                                    bookmarks = bookmarks,
                                    onBack = { navController.popBackStack() },
                                    onOpenBook = { b -> openBookWithEligibility(b) },
                                    onToggleBookBookmark = { b ->
                                        sessionViewModel.toggleBookmark(
                                            bookmarkType = "BOOK",
                                            targetId = b.id,
                                            titleBn = b.titleBn,
                                            subtitleBn = b.subjectNameBn,
                                            classNumber = b.classNumber,
                                            coverUrl = b.coverUrl,
                                            pdfUrl = b.pdfUrl,
                                            isPremium = b.isPremium
                                        )
                                    },
                                    isBookUnlocked = { b -> isContentUnlocked(b.isPremium, b.classNumber, b.stream) },
                                    onRetry = sessionViewModel::retryAllStreams
                                )
                            }
                        }

                        composable(
                            route = Routes.GUIDE_LIST,
                            arguments = listOf(
                                navArgument("classNumber") { type = NavType.IntType },
                                navArgument("stream") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val classNumber = entry.arguments?.getInt("classNumber") ?: 5
                            val stream = entry.arguments?.getString("stream") ?: "ALL"
                            val now = System.currentTimeMillis()
                            val hasFreeGuide = classNumber !in 5..10 && guides.any { it.classNumber == classNumber && !it.isPremium }
                            val isAuthorized = SecurityConstants.isTrustedSuperAdmin(currentUserUid) ||
                                hasFreeGuide ||
                                sessionViewModel.evaluateClassAccess(classNumber, stream, now)

                            if (!isAuthorized) {
                                LaunchedEffect(classNumber, stream) {
                                    val streamTitleStr = if (stream == "ALL") "" else " (${com.example.ui.i18n.streamTitle(stream, lang)})"
                                    snackbarHostState.showSnackbar("শ্রেণি $classNumber$streamTitleStr-এর গাইড দেখতে সক্রিয় সাবস্ক্রিপশন প্রয়োজন।")
                                    navController.navigate(Routes.subscription(classNumber, stream)) {
                                        popUpTo(Routes.HOME)
                                    }
                                }
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                            } else {
                                GuideListScreen(
                                    classNumber = classNumber,
                                    stream = stream,
                                    subjects = subjects,
                                    guidesState = guidesState,
                                    bookmarks = bookmarks,
                                    onBack = { navController.popBackStack() },
                                    onOpenGuide = { g -> openGuidePdfWithEligibility(g) },
                                    onToggleGuideBookmark = { g ->
                                        sessionViewModel.toggleBookmark(
                                            bookmarkType = "GUIDE",
                                            targetId = g.id,
                                            titleBn = g.titleBn,
                                            subtitleBn = "${g.subjectNameBn} • ${g.chapter}",
                                            classNumber = g.classNumber,
                                            coverUrl = g.coverUrl,
                                            pdfUrl = g.pdfUrl,
                                            isPremium = g.isPremium
                                        )
                                    },
                                    isGuideUnlocked = { g -> isContentUnlocked(g.isPremium, g.classNumber, g.stream) },
                                    onRetry = sessionViewModel::retryAllStreams
                                )
                            }
                        }

                        composable(
                            route = Routes.BOOK_DETAIL,
                            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
                        ) { entry ->
                            val bookId = entry.arguments?.getString("bookId").orEmpty()
                            val book = books.firstOrNull { it.id == bookId }
                            if (book == null) {
                                EmptyOrErrorStateCard(
                                    title = strings.emptyStateTitle,
                                    body = strings.emptyStateBody,
                                    onRetry = { navController.popBackStack() }
                                )
                            } else {
                                val isBm = bookmarks.any { it.bookmarkType == "BOOK" && it.targetId == book.id }
                                val bookProg = readingProgress.firstOrNull {
                                    it.contentType == "BOOK" && it.contentId == book.id
                                }
                                val accessDecision = sessionViewModel.evaluatePremiumAccess(
                                    isPremium = book.isPremium,
                                    classNumber = book.classNumber,
                                    stream = book.stream
                                )
                                val relatedGuides = guides.filter { g ->
                                    g.classNumber == book.classNumber &&
                                        (g.subjectNameBn.equals(book.subjectNameBn, ignoreCase = true) ||
                                            (book.subjectId.isNotBlank() && g.subjectId == book.subjectId))
                                }
                                val relatedPersonalNotes = notes.filter { n ->
                                    n.subjectTag.equals(book.subjectNameBn, ignoreCase = true) ||
                                        n.title.contains(book.titleBn, ignoreCase = true) ||
                                        n.title.contains(book.subjectNameBn, ignoreCase = true)
                                }
                                val availableExercises = exams.filter { ex ->
                                    ex.classNumber == book.classNumber &&
                                        (ex.subjectNameBn.equals(book.subjectNameBn, ignoreCase = true) ||
                                            ex.examType == "MODEL")
                                }

                                BookDetailScreen(
                                    book = book,
                                    isBookmarked = isBm,
                                    readingProgress = bookProg,
                                    accessDecision = accessDecision,
                                    appSettings = appSettings,
                                    relatedGuides = relatedGuides,
                                    relatedPersonalNotes = relatedPersonalNotes,
                                    availableExercises = availableExercises,
                                    onBack = { navController.popBackStack() },
                                    onToggleBookmark = {
                                        sessionViewModel.toggleBookmark(
                                            bookmarkType = "BOOK",
                                            targetId = book.id,
                                            titleBn = book.titleBn,
                                            subtitleBn = book.subjectNameBn,
                                            classNumber = book.classNumber,
                                            coverUrl = book.coverUrl,
                                            pdfUrl = book.pdfUrl,
                                            isPremium = book.isPremium
                                        )
                                    },
                                    onOpenPdfReader = { initialPage ->
                                        openBookWithEligibility(book, initialPage = initialPage)
                                    },
                                    onOpenSubscriptionGate = {
                                        if (accessDecision == PremiumAccessDecision.DENIED_ACCOUNT_INACTIVE) {
                                            scope.launch { snackbarHostState.showSnackbar("আপনার অ্যাকাউন্টটি নিষ্ক্রিয় রয়েছে।") }
                                        } else {
                                            val expired = accessDecision == PremiumAccessDecision.DENIED_SUBSCRIPTION_EXPIRED
                                            navController.navigate(Routes.premiumGate(book.classNumber, book.stream, expired = expired))
                                        }
                                    },
                                    onOpenRelatedGuide = { g -> openGuidePdfWithEligibility(g) },
                                    onOpenNotebook = { navController.navigate(Routes.NOTEBOOK) },
                                    onQuickSaveNote = { title, content, tag ->
                                        sessionViewModel.saveNote(null, title, content, tag, 0, false)
                                    },
                                    onStartExercise = { exam -> navController.navigate(Routes.examSession(exam.id)) },
                                    onOpenExamHub = { navController.navigate(Routes.examPrep(book.classNumber)) },
                                    onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                                )
                            }
                        }

                        composable(
                            route = Routes.GUIDE_DETAIL,
                            arguments = listOf(navArgument("guideId") { type = NavType.StringType })
                        ) { entry ->
                            val guideId = entry.arguments?.getString("guideId").orEmpty()
                            val guide = guides.firstOrNull { it.id == guideId }
                            if (guide == null) {
                                EmptyOrErrorStateCard(
                                    title = strings.emptyStateTitle,
                                    body = strings.emptyStateBody,
                                    onRetry = { navController.popBackStack() }
                                )
                            } else {
                                val isBm = bookmarks.any { it.bookmarkType == "GUIDE" && it.targetId == guide.id }
                                val accessDecision = sessionViewModel.evaluatePremiumAccess(
                                    isPremium = guide.isPremium,
                                    classNumber = guide.classNumber,
                                    stream = guide.stream
                                )
                                val relatedBooks = books.filter { b ->
                                    b.classNumber == guide.classNumber &&
                                        (b.subjectNameBn.equals(guide.subjectNameBn, ignoreCase = true) ||
                                            (guide.subjectId.isNotBlank() && b.subjectId == guide.subjectId))
                                }
                                val relatedPersonalNotes = notes.filter { n ->
                                    n.subjectTag.equals(guide.subjectNameBn, ignoreCase = true) ||
                                        n.title.contains(guide.titleBn, ignoreCase = true) ||
                                        n.title.contains(guide.subjectNameBn, ignoreCase = true)
                                }
                                val availableExercises = exams.filter { ex ->
                                    ex.classNumber == guide.classNumber &&
                                        (ex.subjectNameBn.equals(guide.subjectNameBn, ignoreCase = true) ||
                                            ex.examType == "MODEL")
                                }

                                GuideDetailScreen(
                                    guide = guide,
                                    isBookmarked = isBm,
                                    accessDecision = accessDecision,
                                    appSettings = appSettings,
                                    relatedBooks = relatedBooks,
                                    relatedPersonalNotes = relatedPersonalNotes,
                                    availableExercises = availableExercises,
                                    onBack = { navController.popBackStack() },
                                    onToggleBookmark = {
                                        sessionViewModel.toggleBookmark(
                                            bookmarkType = "GUIDE",
                                            targetId = guide.id,
                                            titleBn = guide.titleBn,
                                            subtitleBn = "${guide.subjectNameBn} • ${guide.chapter}",
                                            classNumber = guide.classNumber,
                                            coverUrl = guide.coverUrl,
                                            pdfUrl = guide.pdfUrl,
                                            isPremium = guide.isPremium
                                        )
                                    },
                                    onOpenPdfAttachment = { openGuidePdfWithEligibility(guide) },
                                    onOpenSubscriptionGate = {
                                        if (accessDecision == PremiumAccessDecision.DENIED_ACCOUNT_INACTIVE) {
                                            scope.launch { snackbarHostState.showSnackbar("আপনার অ্যাকাউন্টটি নিষ্ক্রিয় রয়েছে।") }
                                        } else {
                                            val expired = accessDecision == PremiumAccessDecision.DENIED_SUBSCRIPTION_EXPIRED
                                            navController.navigate(Routes.premiumGate(guide.classNumber, guide.stream, expired = expired))
                                        }
                                    },
                                    onOpenRelatedBook = { b -> openBookWithEligibility(b) },
                                    onOpenNotebook = { navController.navigate(Routes.NOTEBOOK) },
                                    onQuickSaveNote = { title, content, tag ->
                                        sessionViewModel.saveNote(null, title, content, tag, 0, false)
                                    },
                                    onStartExercise = { exam -> navController.navigate(Routes.examSession(exam.id)) },
                                    onOpenExamHub = { navController.navigate(Routes.examPrep(guide.classNumber)) },
                                    onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                                    onRecordRead = {
                                        sessionViewModel.recordReadingProgress(
                                            contentId = guide.id,
                                            contentType = "GUIDE",
                                            titleBn = guide.titleBn,
                                            subjectNameBn = guide.subjectNameBn,
                                            classNumber = guide.classNumber,
                                            stream = guide.stream,
                                            coverUrl = guide.coverUrl,
                                            pdfUrl = guide.pdfUrl,
                                            isPremium = guide.isPremium,
                                            lastPage = 1,
                                            totalPages = guide.pageCount.coerceAtLeast(1)
                                        )
                                    }
                                )
                            }
                        }

                        composable(
                            route = Routes.PDF_READER,
                            arguments = listOf(
                                navArgument("contentType") { type = NavType.StringType },
                                navArgument("contentId") { type = NavType.StringType },
                                navArgument("initialPage") { type = NavType.IntType; defaultValue = 0 }
                            )
                        ) { entry ->
                            val contentType = entry.arguments?.getString("contentType") ?: "BOOK"
                            val contentId = entry.arguments?.getString("contentId").orEmpty()
                            val initialPage = entry.arguments?.getInt("initialPage") ?: 0

                            val book = if (contentType == "BOOK") books.firstOrNull { it.id == contentId } else null
                            val guide = if (contentType == "GUIDE") guides.firstOrNull { it.id == contentId } else null
                            val savedProg = readingProgress.firstOrNull { it.contentId == contentId }
                            val fallbackBm = bookmarks.firstOrNull { it.targetId == contentId }

                            val resolvedTitle = book?.titleBn ?: guide?.titleBn ?: savedProg?.titleBn ?: fallbackBm?.titleBn ?: strings.originalTextbooks
                            val resolvedSubject = book?.subjectNameBn ?: guide?.subjectNameBn ?: savedProg?.subjectNameBn ?: ""
                            val resolvedClass = book?.classNumber ?: guide?.classNumber ?: savedProg?.classNumber ?: fallbackBm?.classNumber ?: 5
                            val resolvedStream = book?.stream ?: guide?.stream ?: savedProg?.stream ?: "ALL"
                            val resolvedCover = book?.effectiveCoverRef ?: guide?.effectiveCoverRef ?: savedProg?.coverUrl ?: fallbackBm?.coverUrl ?: ""
                            val resolvedPdf = book?.effectivePdfRef ?: guide?.effectivePdfRef ?: savedProg?.pdfUrl ?: fallbackBm?.pdfUrl ?: ""
                            val resolvedPremium = book?.isPremium ?: guide?.isPremium ?: savedProg?.isPremium ?: fallbackBm?.isPremium ?: false
                            val resolvedAllowDownload = book?.allowDownload ?: guide?.allowDownload ?: false
                            val resolvedToc = book?.tableOfContents ?: guide?.tableOfContents ?: emptyList()

                            PdfReaderScreen(
                                contentId = contentId,
                                contentType = contentType,
                                titleBn = resolvedTitle,
                                subjectNameBn = resolvedSubject,
                                classNumber = resolvedClass,
                                stream = resolvedStream,
                                coverUrl = resolvedCover,
                                pdfUrl = resolvedPdf,
                                isPremium = resolvedPremium,
                                itemAllowsDownload = resolvedAllowDownload,
                                tableOfContents = resolvedToc,
                                initialPageOneBased = initialPage,
                                savedLastPageOneBased = savedProg?.lastPage ?: 1,
                                bookmarks = bookmarks,
                                appSettings = appSettings,
                                onBack = { navController.popBackStack() },
                                onSaveProgress = { lastPage, totalPages ->
                                    sessionViewModel.recordReadingProgress(
                                        contentId = contentId,
                                        contentType = contentType,
                                        titleBn = resolvedTitle,
                                        subjectNameBn = resolvedSubject,
                                        classNumber = resolvedClass,
                                        stream = resolvedStream,
                                        coverUrl = resolvedCover,
                                        pdfUrl = resolvedPdf,
                                        isPremium = resolvedPremium,
                                        lastPage = lastPage,
                                        totalPages = totalPages
                                    )
                                },
                                onToggleWholeBookBookmark = {
                                    sessionViewModel.toggleBookmark(
                                        bookmarkType = if (contentType == "GUIDE") "GUIDE" else "BOOK",
                                        targetId = contentId,
                                        titleBn = resolvedTitle,
                                        subtitleBn = resolvedSubject,
                                        classNumber = resolvedClass,
                                        pageNumber = 0,
                                        coverUrl = resolvedCover,
                                        pdfUrl = resolvedPdf,
                                        isPremium = resolvedPremium
                                    )
                                },
                                onTogglePageBookmark = { pageOneBased ->
                                    sessionViewModel.toggleBookmark(
                                        bookmarkType = "PAGE",
                                        targetId = contentId,
                                        titleBn = resolvedTitle,
                                        subtitleBn = resolvedSubject,
                                        classNumber = resolvedClass,
                                        pageNumber = pageOneBased,
                                        coverUrl = resolvedCover,
                                        pdfUrl = resolvedPdf,
                                        isPremium = resolvedPremium
                                    )
                                },
                                onShowMessage = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }

                        composable(Routes.NOTEBOOK) {
                            NotebookScreen(
                                notesState = notesState,
                                onBack = { navController.popBackStack() },
                                onSaveNote = { existingId, title, content, tag, colorIdx, pinned ->
                                    sessionViewModel.saveNote(existingId, title, content, tag, colorIdx, pinned)
                                },
                                onDeleteNote = { sessionViewModel.deleteNote(it) },
                                onRetry = sessionViewModel::retryAllStreams
                            )
                        }

                        composable(Routes.GENERAL_KNOWLEDGE) {
                            GeneralKnowledgeScreen(
                                gkState = gkState,
                                bookmarks = bookmarks,
                                onBack = { navController.popBackStack() },
                                onToggleGkBookmark = { gk ->
                                    val catMeta = DefaultCatalogSeed.gkCategories.firstOrNull { it.code == gk.category }
                                    sessionViewModel.toggleBookmark(
                                        bookmarkType = "GK",
                                        targetId = gk.id,
                                        titleBn = gk.questionBn,
                                        subtitleBn = "${catMeta?.nameBn ?: gk.category} • উত্তর: ${gk.answerBn}",
                                        classNumber = 0
                                    )
                                },
                                onRetry = sessionViewModel::retryAllStreams
                            )
                        }

                        composable(
                            route = Routes.EXAM_PREP,
                            arguments = listOf(navArgument("classFilter") { type = NavType.IntType; defaultValue = 0 })
                        ) { entry ->
                            val classFilter = entry.arguments?.getInt("classFilter") ?: 0
                            ExamPrepHubScreen(
                                initialClassFilter = classFilter,
                                classes = classes,
                                examsState = examsState,
                                examHistoryState = examHistoryState,
                                onBack = { navController.popBackStack() },
                                onStartExam = { exam -> navController.navigate(Routes.examSession(exam.id)) },
                                onRetry = sessionViewModel::retryAllStreams
                            )
                        }

                        composable(
                            route = Routes.EXAM_SESSION,
                            arguments = listOf(navArgument("examId") { type = NavType.StringType })
                        ) { entry ->
                            val examId = entry.arguments?.getString("examId").orEmpty()
                            val examList = (examsState as? UiState.Success)?.data.orEmpty()
                            val exam = examList.firstOrNull { it.id == examId }
                                ?: DefaultCatalogSeed.demoExamBundles.firstOrNull { it.exam.id == examId }?.exam
                            if (exam == null) {
                                EmptyOrErrorStateCard(
                                    title = strings.emptyStateTitle,
                                    body = strings.emptyStateBody,
                                    onRetry = { navController.popBackStack() }
                                )
                            } else {
                                ExamSessionScreen(
                                    exam = exam,
                                    gradedResult = latestExamResult,
                                    onBack = { navController.popBackStack() },
                                    onSubmitAnswers = { answers, onDone ->
                                        sessionViewModel.submitExam(exam, answers, onDone)
                                    }
                                )
                            }
                        }

                        composable(Routes.READING_HISTORY) {
                            ReadingHistoryScreen(
                                progressState = readingProgressState,
                                onBack = { navController.popBackStack() },
                                onResumeItem = { resumeProgressWithEligibility(it) },
                                onRetry = sessionViewModel::retryAllStreams
                            )
                        }

                        composable(
                            route = Routes.SUBSCRIPTION,
                            arguments = listOf(
                                navArgument("preselectClass") { type = NavType.IntType; defaultValue = 0 },
                                navArgument("preselectStream") { type = NavType.StringType; defaultValue = "ALL" }
                            )
                        ) { entry ->
                            val preselect = entry.arguments?.getInt("preselectClass") ?: 0
                            val preselectStream = entry.arguments?.getString("preselectStream") ?: "ALL"
                            SubscriptionScreen(
                                currentUserUid = currentUserUid,
                                userProfile = userProfile,
                                preselectClass = preselect,
                                preselectStream = preselectStream,
                                classes = classes,
                                educationLevels = educationLevels,
                                plansState = plansState,
                                paymentMethodsState = paymentMethodsState,
                                subscriptionsState = subscriptionsState,
                                requestsState = subRequestsState,
                                contactPhone = appSettings.contactPhone,
                                onBack = { navController.popBackStack() },
                                onSubmitPaymentRequest = { studentName, plan, method, trx, sender, screenshotUrl, methodCfg, fallbackReceiver, onResult ->
                                    sessionViewModel.submitPaymentRequest(
                                        studentNameOverride = studentName,
                                        plan = plan,
                                        paymentMethod = method,
                                        transactionReference = trx,
                                        senderNumber = sender,
                                        screenshotUrl = screenshotUrl,
                                        selectedMethodConfig = methodCfg,
                                        fallbackReceiverNumber = fallbackReceiver,
                                        onResult = onResult
                                    )
                                },
                                onRetry = sessionViewModel::retryAllStreams
                            )
                        }

                        composable(
                            route = Routes.PREMIUM_GATE,
                            arguments = listOf(
                                navArgument("classNumber") { type = NavType.IntType },
                                navArgument("stream") { type = NavType.StringType },
                                navArgument("expired") { type = NavType.BoolType; defaultValue = false }
                            )
                        ) { entry ->
                            val cls = entry.arguments?.getInt("classNumber") ?: 5
                            val st = entry.arguments?.getString("stream") ?: "ALL"
                            val exp = entry.arguments?.getBoolean("expired") ?: false
                            PremiumAccessGateScreen(
                                classNumber = cls,
                                stream = st,
                                isExpired = exp,
                                onBack = { navController.popBackStack() },
                                onOpenSubscriptionPlans = {
                                    navController.popBackStack()
                                    navController.navigate(Routes.subscription(cls, st))
                                }
                            )
                        }

                        composable(Routes.NOTICES) {
                            NoticesScreen(
                                noticesState = noticesState,
                                userNotificationsState = userNotificationsState,
                                onMarkNotificationRead = sessionViewModel::markUserNotificationRead,
                                onOpenSubscription = {
                                    navController.navigate(
                                        Routes.subscription(
                                            userProfile?.selectedClass ?: 5,
                                            userProfile?.selectedStream ?: "ALL"
                                        )
                                    )
                                },
                                onBack = { navController.popBackStack() },
                                onRetry = sessionViewModel::retryAllStreams
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                preferences = preferences,
                                appSettings = appSettings,
                                userProfile = userProfile,
                                onBack = { navController.popBackStack() },
                                onSetThemeMode = { mode ->
                                    scope.launch { prefsRepository.setThemeMode(mode) }
                                },
                                onSetLanguage = { lang ->
                                    scope.launch { prefsRepository.setLanguage(lang) }
                                },
                                onSetNotifications = { enabled ->
                                    scope.launch { prefsRepository.setNotificationsEnabled(enabled) }
                                },
                                onOpenLegalDoc = { docType ->
                                    navController.navigate(Routes.legalInfo(docType))
                                },
                                onOpenWelcomeIntro = {
                                    navController.navigate(Routes.WELCOME_INTRO)
                                },
                                onRequestAccountDeletion = {
                                    sessionViewModel.updateProfile(
                                        name = userProfile?.name ?: "শিক্ষার্থী",
                                        selectedClass = userProfile?.selectedClass ?: 5,
                                        selectedStream = userProfile?.selectedStream ?: "NONE",
                                        accountDeletionRequested = true
                                    )
                                },
                                onLogout = {
                                    scope.launch { authRepository.signOut(context) }
                                }
                            )
                        }

                        composable(Routes.WELCOME_INTRO) {
                            WelcomeScreen(
                                currentLanguage = preferences.language,
                                onSelectLanguage = { selectedLang ->
                                    scope.launch { prefsRepository.setLanguage(selectedLang) }
                                },
                                onStartClick = { navController.popBackStack() },
                                isRevisitFromSettings = true,
                                onBackFromSettings = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = Routes.LEGAL_INFO,
                            arguments = listOf(navArgument("docType") { type = NavType.StringType })
                        ) { entry ->
                            val docType = entry.arguments?.getString("docType") ?: "ABOUT"
                            LegalInfoScreen(
                                docType = docType,
                                appSettings = appSettings,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
