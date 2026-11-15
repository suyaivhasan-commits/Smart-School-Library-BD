package com.example.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.SecurityConstants
import com.example.model.AppNotification
import com.example.model.AppSettings
import com.example.model.EducationLevel
import com.example.model.GuideNote
import com.example.model.PaymentMethodConfig
import com.example.model.SchoolClass
import com.example.model.Subscription
import com.example.model.SubscriptionRequest
import com.example.model.Textbook
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.formatLocalized
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldPremium
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyHeader
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue

enum class AdminModule(val labelBn: String, val labelEn: String, val icon: ImageVector) {
    DASHBOARD("ড্যাশবোর্ড", "Dashboard", Icons.Filled.Dashboard),
    PAYMENT_METHODS("পেমেন্ট মাধ্যম", "Payment Method", Icons.Filled.AccountBalanceWallet),
    PAYMENT_NOTIFICATIONS("পেমেন্ট নোটিফিকেশন", "Payment Alerts", Icons.Filled.NotificationsActive),
    SUBSCRIPTIONS("সাবস্ক্রিপশন ও যাচাই", "Subscriptions", Icons.Filled.WorkspacePremium),
    CLASSES("শ্রেণি ব্যবস্থাপনা", "Classes", Icons.Filled.Class),
    EDUCATION_LEVELS("শিক্ষা স্তর", "Education Levels", Icons.Filled.School),
    STREAMS("বিভাগ ব্যবস্থাপনা", "Streams", Icons.Filled.AccountTree),
    SUBJECTS("বিষয় ব্যবস্থাপনা", "Subjects", Icons.Filled.Category),
    BOOKS("বই ব্যবস্থাপনা", "Textbooks", Icons.AutoMirrored.Filled.MenuBook),
    GUIDES("গাইড ও নোট", "Guides & Notes", Icons.Filled.Description),
    GK("সাধারণ জ্ঞান", "GK Bank", Icons.Filled.Lightbulb),
    EXAMS("এমসিকিউ পরীক্ষা", "MCQ Exams", Icons.Filled.Quiz),
    USERS("ব্যবহারকারী", "Students", Icons.Filled.Group),
    NOTICES("নোটিশ প্রকাশ", "Notices", Icons.Filled.Campaign),
    SETTINGS("ডাউনলোড ও সেটিংস", "App Settings", Icons.Filled.Settings),
    QUICK_CONTENT("কনটেন্ট কুইক-ম্যানেজার", "Content Manager", Icons.Filled.Tune)
}

@Composable
fun SuperAdminPanelScreen(
    currentUserUid: String,
    adminViewModel: AdminViewModel,
    onExitToStudentApp: () -> Unit,
    onLogout: () -> Unit
) {
    // Hard client-side gate (backed by server-side Firestore Security Rules checking exact UID)
    if (!SecurityConstants.isTrustedSuperAdmin(currentUserUid)) {
        LaunchedEffect(currentUserUid) {
            onExitToStudentApp()
        }
        return
    }

    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    var activeModule by rememberSaveable { mutableStateOf(AdminModule.DASHBOARD) }

    BackHandler(enabled = activeModule != AdminModule.DASHBOARD) {
        activeModule = AdminModule.DASHBOARD
    }

    val usersState by adminViewModel.usersState.collectAsStateWithLifecycle()
    val classesState by adminViewModel.classesState.collectAsStateWithLifecycle()
    val educationLevelsState by adminViewModel.educationLevelsState.collectAsStateWithLifecycle()
    val streamsState by adminViewModel.streamsState.collectAsStateWithLifecycle()
    val subjectsState by adminViewModel.subjectsState.collectAsStateWithLifecycle()
    val booksState by adminViewModel.booksState.collectAsStateWithLifecycle()
    val guidesState by adminViewModel.guidesState.collectAsStateWithLifecycle()
    val gkState by adminViewModel.gkState.collectAsStateWithLifecycle()
    val examsState by adminViewModel.examsState.collectAsStateWithLifecycle()
    val plansState by adminViewModel.plansState.collectAsStateWithLifecycle()
    val paymentMethodsState by adminViewModel.paymentMethodsState.collectAsStateWithLifecycle()
    val requestsState by adminViewModel.requestsState.collectAsStateWithLifecycle()
    val subscriptionsState by adminViewModel.subscriptionsState.collectAsStateWithLifecycle()
    val adminNotificationsState by adminViewModel.adminNotificationsState.collectAsStateWithLifecycle()
    val focusedPaymentRequestId by adminViewModel.focusedPaymentRequestId.collectAsStateWithLifecycle()
    val noticesState by adminViewModel.noticesState.collectAsStateWithLifecycle()
    val appSettingsState by adminViewModel.appSettingsState.collectAsStateWithLifecycle()
    val feedback by adminViewModel.feedbackMessage.collectAsStateWithLifecycle()
    val isErrorFeedback by adminViewModel.isErrorFeedback.collectAsStateWithLifecycle()
    val isBusy by adminViewModel.isBusy.collectAsStateWithLifecycle()

    val streamErrorMessage = listOf(
        booksState,
        guidesState,
        subjectsState,
        classesState,
        educationLevelsState,
        noticesState
    ).firstNotNullOfOrNull { (it as? UiState.Error)?.message }

    val users = (usersState as? UiState.Success)?.data.orEmpty()
    val classes = (classesState as? UiState.Success)?.data.orEmpty()
    val educationLevels = (educationLevelsState as? UiState.Success)?.data.orEmpty()
    val streams = (streamsState as? UiState.Success)?.data.orEmpty()
    val subjects = (subjectsState as? UiState.Success)?.data.orEmpty()
    val books = (booksState as? UiState.Success)?.data.orEmpty()
    val guides = (guidesState as? UiState.Success)?.data.orEmpty()
    val gkList = (gkState as? UiState.Success)?.data.orEmpty()
    val exams = (examsState as? UiState.Success)?.data.orEmpty()
    val plans = (plansState as? UiState.Success)?.data.orEmpty()
    val paymentMethods = (paymentMethodsState as? UiState.Success)?.data.orEmpty()
    val requests = (requestsState as? UiState.Success)?.data.orEmpty()
    val subscriptions = (subscriptionsState as? UiState.Success)?.data.orEmpty()
    val adminNotifications = (adminNotificationsState as? UiState.Success)?.data.orEmpty()
    val notices = (noticesState as? UiState.Success)?.data.orEmpty()
    val settings = (appSettingsState as? UiState.Success)?.data ?: AppSettings()
    val pendingPaymentCount = requests.count { it.status == "PENDING" }
    val unreadNotifCount = adminNotifications.count { !it.isRead }

    SuperAdminPanelScaffold(
        activeModule = activeModule,
        onSelectModule = { activeModule = it },
        pendingPaymentCount = pendingPaymentCount,
        unreadNotifCount = unreadNotifCount,
        isBusy = isBusy,
        activeBannerText = feedback ?: streamErrorMessage,
        activeBannerIsError = if (feedback != null) isErrorFeedback else (streamErrorMessage != null),
        onClearFeedback = if (feedback != null) ({ adminViewModel.clearFeedback() }) else null,
        onExitToStudentApp = onExitToStudentApp,
        onLogout = onLogout
    ) { mod, onModSelected ->
        AdminModuleBody(
            activeModule = mod,
            onSelectModule = onModSelected,
            adminViewModel = adminViewModel,
            users = users,
            classes = classes,
            educationLevels = educationLevels,
            streams = streams,
            subjects = subjects,
            books = books,
            guides = guides,
            gkList = gkList,
            exams = exams,
            plans = plans,
            paymentMethods = paymentMethods,
            requests = requests,
            subscriptions = subscriptions,
            adminNotifications = adminNotifications,
            focusedPaymentRequestId = focusedPaymentRequestId,
            notices = notices,
            settings = settings,
            lang = lang
        )
    }
}

@Composable
internal fun SuperAdminPanelScaffold(
    activeModule: AdminModule,
    onSelectModule: (AdminModule) -> Unit,
    pendingPaymentCount: Int = 0,
    unreadNotifCount: Int = 0,
    isBusy: Boolean = false,
    activeBannerText: String? = null,
    activeBannerIsError: Boolean = false,
    onClearFeedback: (() -> Unit)? = null,
    onExitToStudentApp: () -> Unit,
    onLogout: () -> Unit,
    moduleContent: @Composable (activeModule: AdminModule, onSelectModule: (AdminModule) -> Unit) -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    val badgeAlertCount = maxOf(pendingPaymentCount, unreadNotifCount)

    fun badgeForModule(mod: AdminModule): Int = when (mod) {
        AdminModule.PAYMENT_NOTIFICATIONS -> unreadNotifCount
        AdminModule.SUBSCRIPTIONS -> pendingPaymentCount
        else -> 0
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("super_admin_panel_screen")
    ) {
        val isWideLayout = maxWidth >= 700.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Dedicated Super Admin Top Header with Live Pending Payment Notification Bell & Badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(NavyDeep, NavyHeader, NavyPrimary)))
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (activeModule != AdminModule.DASHBOARD) {
                                    onSelectModule(AdminModule.DASHBOARD)
                                } else {
                                    onExitToStudentApp()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("admin_header_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (activeModule != AdminModule.DASHBOARD) {
                                    if (isBn) "ড্যাশবোর্ডে ফিরুন" else "Back to Dashboard"
                                } else {
                                    "Back to App"
                                },
                                tint = Color.White
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = GoldPremium,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Admin Panel",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = if (isBn) activeModule.labelBn else activeModule.labelEn,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPrimary
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Live Pending Payment Notification Badge Button
                        Surface(
                            color = if (badgeAlertCount > 0) ErrorRed else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .clickable { onSelectModule(AdminModule.PAYMENT_NOTIFICATIONS) }
                                .testTag("admin_header_payment_alerts_btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = "Payment Notifications",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                if (badgeAlertCount > 0) {
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = badgeAlertCount.formatLocalized(lang),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        Surface(
                            color = GoldPremium.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Super Admin",
                                color = GoldPremium,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (isBusy) {
                LinearProgressIndicator(
                    color = EmeraldPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (activeBannerText != null) {
                Surface(
                    color = if (activeBannerIsError) ErrorRed.copy(alpha = 0.14f) else EmeraldPrimary.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeBannerText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeBannerIsError) ErrorRed else EmeraldDark,
                            modifier = Modifier.weight(1f)
                        )
                        if (onClearFeedback != null) {
                            TextButton(onClick = onClearFeedback) {
                                Text(strings.close)
                            }
                        }
                    }
                }
            }

            if (isWideLayout) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Tablet / Wide Sidebar
                    Column(
                        modifier = Modifier
                            .width(245.dp)
                            .fillMaxHeight()
                            .background(NavyDeep)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AdminModule.entries.forEach { mod ->
                            val selected = activeModule == mod
                            val badge = badgeForModule(mod)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) RoyalBlue else Color.Transparent)
                                    .clickable { onSelectModule(mod) }
                                    .padding(horizontal = 12.dp, vertical = 11.dp)
                                    .testTag("admin_nav_${mod.name}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = mod.icon,
                                        contentDescription = null,
                                        tint = if (selected) Color.White else Color.White.copy(alpha = 0.75f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = if (isBn) mod.labelBn else mod.labelEn,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (badge > 0) {
                                    Surface(
                                        color = ErrorRed,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = badge.formatLocalized(lang),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(onClick = onLogout)
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFF87171))
                            Spacer(Modifier.width(10.dp))
                            Text(strings.logoutButton, color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        moduleContent(activeModule, onSelectModule)
                    }
                }
            } else {
                // Compact Phone: No top horizontal chip strip; modules live on the Dashboard as 2-column cards.
                // When inside any sub-module, show a sleek return bar to jump back to the Dashboard cleanly.
                if (activeModule != AdminModule.DASHBOARD) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelectModule(AdminModule.DASHBOARD) }
                            .testTag("admin_subscreen_back_to_dashboard_bar")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = if (isBn) "ড্যাশবোর্ডে ফিরে যান" else "Back to Dashboard",
                                    tint = RoyalBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "ড্যাশবোর্ডে ফিরে যান" else "Back to Dashboard",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = RoyalBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (isBn) activeModule.labelBn else activeModule.labelEn,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    moduleContent(activeModule, onSelectModule)
                }
            }
        }
    }
}

@Composable
private fun AdminModuleBody(
    activeModule: AdminModule,
    onSelectModule: (AdminModule) -> Unit,
    adminViewModel: AdminViewModel,
    users: List<UserProfile>,
    classes: List<SchoolClass>,
    educationLevels: List<EducationLevel>,
    streams: List<com.example.model.AcademicStream>,
    subjects: List<com.example.model.Subject>,
    books: List<Textbook>,
    guides: List<GuideNote>,
    gkList: List<com.example.model.GeneralKnowledgeItem>,
    exams: List<com.example.model.ExamTest>,
    plans: List<com.example.model.SubscriptionPlan>,
    paymentMethods: List<PaymentMethodConfig>,
    requests: List<SubscriptionRequest>,
    subscriptions: List<Subscription>,
    adminNotifications: List<AppNotification>,
    focusedPaymentRequestId: String?,
    notices: List<com.example.model.Notice>,
    settings: AppSettings,
    lang: AppLanguage
) {
    when (activeModule) {
        AdminModule.DASHBOARD -> AdminDashboardOverview(
            users = users,
            classes = classes,
            educationLevels = educationLevels,
            streams = streams,
            subjects = subjects,
            books = books,
            guides = guides,
            gkList = gkList,
            exams = exams,
            plans = plans,
            paymentMethods = paymentMethods,
            subscriptions = subscriptions,
            requests = requests,
            adminNotifications = adminNotifications,
            notices = notices,
            lang = lang,
            onBootstrapClasses = { adminViewModel.bootstrapMissingClassesAndStreams() },
            onNavigateModule = onSelectModule
        )
        AdminModule.PAYMENT_METHODS -> AdminPaymentMethodsModule(
            paymentMethods = paymentMethods,
            onSave = adminViewModel::savePaymentMethod,
            onToggleActive = adminViewModel::togglePaymentMethod,
            onDelete = adminViewModel::deletePaymentMethod
        )
        AdminModule.PAYMENT_NOTIFICATIONS -> AdminPaymentNotificationsModule(
            notifications = adminNotifications,
            requests = requests,
            onMarkRead = adminViewModel::markNotificationRead,
            onMarkAllRead = adminViewModel::markAllNotificationsRead,
            onInspectPaymentRequest = { reqId ->
                adminViewModel.focusPaymentRequest(reqId)
                onSelectModule(AdminModule.SUBSCRIPTIONS)
            }
        )
        AdminModule.SUBSCRIPTIONS -> AdminSubscriptionManagementModule(
            plans = plans,
            classes = classes,
            educationLevels = educationLevels,
            requests = requests,
            subscriptions = subscriptions,
            focusedRequestId = focusedPaymentRequestId,
            onClearFocusedRequest = { adminViewModel.focusPaymentRequest(null) },
            onSavePlan = adminViewModel::savePlan,
            onDeletePlan = adminViewModel::deletePlan,
            onApproveRequest = adminViewModel::approveRequest,
            onRejectRequest = adminViewModel::rejectRequest
        )
        AdminModule.CLASSES -> AdminClassesModule(
            classes = classes,
            educationLevels = educationLevels,
            onSave = adminViewModel::saveClass,
            onDelete = adminViewModel::deleteClass
        )
        AdminModule.EDUCATION_LEVELS -> AdminEducationLevelsModule(
            levels = educationLevels,
            onSave = adminViewModel::saveEducationLevel,
            onDelete = adminViewModel::deleteEducationLevel
        )
        AdminModule.STREAMS -> AdminStreamsModule(streams = streams, onSave = adminViewModel::saveStream)
        AdminModule.SUBJECTS -> AdminSubjectsModule(
            subjects = subjects,
            classes = classes,
            onSave = adminViewModel::saveSubject,
            onDelete = adminViewModel::deleteSubject
        )
        AdminModule.BOOKS -> {
            val uploadState by adminViewModel.directUploadState.collectAsStateWithLifecycle()
            AdminBooksModule(
                books = books,
                subjects = subjects,
                classes = classes,
                onSave = adminViewModel::saveBook,
                onDelete = adminViewModel::deleteBook,
                uploadState = uploadState,
                onResetUploadState = adminViewModel::resetDirectUploadState,
                onDirectUpload = adminViewModel::uploadBookOrGuide,
                onGoAdminHome = { onSelectModule(AdminModule.DASHBOARD) }
            )
        }
        AdminModule.GUIDES -> {
            val uploadState by adminViewModel.directUploadState.collectAsStateWithLifecycle()
            AdminGuidesModule(
                guides = guides,
                subjects = subjects,
                classes = classes,
                onSave = adminViewModel::saveGuide,
                onDelete = adminViewModel::deleteGuide,
                uploadState = uploadState,
                onResetUploadState = adminViewModel::resetDirectUploadState,
                onDirectUpload = adminViewModel::uploadBookOrGuide,
                onGoAdminHome = { onSelectModule(AdminModule.DASHBOARD) }
            )
        }
        AdminModule.GK -> AdminGkModule(
            gkItems = gkList,
            onSave = adminViewModel::saveGk,
            onDelete = adminViewModel::deleteGk
        )
        AdminModule.EXAMS -> AdminExamsModule(
            exams = exams,
            classes = classes,
            onSave = adminViewModel::saveExam,
            onDelete = adminViewModel::deleteExam
        )
        AdminModule.USERS -> AdminUsersModule(
            users = users,
            subscriptions = subscriptions,
            onToggleActive = adminViewModel::toggleUserActive
        )
        AdminModule.NOTICES -> AdminNoticesModule(
            notices = notices,
            onSave = adminViewModel::saveNotice,
            onDelete = adminViewModel::deleteNotice
        )
        AdminModule.SETTINGS -> AdminSettingsModule(
            settings = settings,
            onSave = adminViewModel::saveSettings
        )
        AdminModule.QUICK_CONTENT -> AdminQuickContentManager(
            books = books,
            guides = guides,
            onSaveBook = adminViewModel::saveBook,
            onSaveGuide = adminViewModel::saveGuide
        )
    }
}

@Composable
internal fun AdminDashboardOverview(
    users: List<UserProfile>,
    classes: List<SchoolClass>,
    educationLevels: List<EducationLevel> = emptyList(),
    streams: List<com.example.model.AcademicStream> = emptyList(),
    subjects: List<com.example.model.Subject> = emptyList(),
    books: List<Textbook>,
    guides: List<GuideNote>,
    gkList: List<com.example.model.GeneralKnowledgeItem> = emptyList(),
    exams: List<com.example.model.ExamTest> = emptyList(),
    plans: List<com.example.model.SubscriptionPlan> = emptyList(),
    paymentMethods: List<PaymentMethodConfig>,
    subscriptions: List<Subscription>,
    requests: List<SubscriptionRequest>,
    adminNotifications: List<AppNotification> = emptyList(),
    notices: List<com.example.model.Notice> = emptyList(),
    lang: AppLanguage,
    onBootstrapClasses: () -> Unit,
    onNavigateModule: (AdminModule) -> Unit
) {
    val isBn = lang == AppLanguage.BN
    val totalStudents = users.count { it.role == "student" }
    val activeStudents = users.count { it.role == "student" && it.isActive }
    val activeSubs = subscriptions.count { it.isCurrentlyActive() }
    val expiredSubs = subscriptions.count { !it.isCurrentlyActive() }
    val pendingReqs = requests.count { it.status == "PENDING" }
    val activeMethods = paymentMethods.count { it.isActive }
    val unreadNotifications = adminNotifications.count { !it.isRead }
    val dashboardModules = AdminModule.entries.filter { it != AdminModule.DASHBOARD }
    val navBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 16.dp + navBottomPadding
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_dashboard_overview")
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "ড্যাশবোর্ড সারসংক্ষেপ" else "Dashboard Overview",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Button(
                    onClick = onBootstrapClasses,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.testTag("admin_bootstrap_classes_btn")
                ) {
                    Icon(Icons.Filled.AutoAwesomeMotion, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isBn) "ডিফল্ট শ্রেণি ও বিভাগ সিঙ্ক" else "Sync Classes & Streams")
                }
            }
        }

        // Actionable Live Pending Payment Alert Card
        if (pendingReqs > 0) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateModule(AdminModule.PAYMENT_NOTIFICATIONS) }
                        .testTag("admin_pending_payment_alert_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isBn) {
                                        "নতুন অপেক্ষমাণ পেমেন্ট রিকোয়েস্ট (${pendingReqs.formatLocalized(lang)}টি)"
                                    } else {
                                        "$pendingReqs Pending Payment Request(s)"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = ErrorRed
                                )
                                Text(
                                    text = if (isBn) {
                                        "শিক্ষার্থীদের Transaction ID যাচাই করে অনুমোদন বা বাতিল করতে এখানে ট্যাপ করুন।"
                                    } else {
                                        "Tap to inspect Transaction IDs and approve or reject student payments."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Surface(color = ErrorRed, shape = RoundedCornerShape(50)) {
                            Text(
                                text = if (isBn) "যাচাই করুন" else "Verify",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // KPI Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    AdminMetricCard(
                        title = if (isBn) "মোট শিক্ষার্থী" else "Total Students",
                        value = totalStudents.formatLocalized(lang),
                        subtitle = if (isBn) "সক্রিয়: ${activeStudents.formatLocalized(lang)}" else "Active: $activeStudents",
                        tint = RoyalBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateModule(AdminModule.USERS) }
                    )
                    AdminMetricCard(
                        title = if (isBn) "মোট বই ও গাইড" else "Books & Guides",
                        value = books.size.formatLocalized(lang),
                        subtitle = if (isBn) "গাইড: ${guides.size.formatLocalized(lang)}" else "Guides: ${guides.size}",
                        tint = EmeraldDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateModule(AdminModule.BOOKS) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    AdminMetricCard(
                        title = if (isBn) "সক্রিয় সাবস্ক্রিপশন" else "Active Subs",
                        value = activeSubs.formatLocalized(lang),
                        subtitle = if (isBn) "মেয়াদোত্তীর্ণ: ${expiredSubs.formatLocalized(lang)}" else "Expired: $expiredSubs",
                        tint = GoldDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateModule(AdminModule.SUBSCRIPTIONS) }
                    )
                    AdminMetricCard(
                        title = if (isBn) "অপেক্ষমাণ পেমেন্ট" else "Pending Payments",
                        value = pendingReqs.formatLocalized(lang),
                        subtitle = if (isBn) "সক্রিয় মাধ্যম: ${activeMethods.formatLocalized(lang)}" else "Active Methods: $activeMethods",
                        tint = if (pendingReqs > 0) ErrorRed else Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateModule(AdminModule.SUBSCRIPTIONS) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    AdminMetricCard(
                        title = if (isBn) "পেমেন্ট মাধ্যমসমূহ" else "Payment Methods",
                        value = paymentMethods.size.formatLocalized(lang),
                        subtitle = if (isBn) "সক্রিয়: ${activeMethods.formatLocalized(lang)} (bKash/Nagad/Rocket/Bank)" else "Active: $activeMethods",
                        tint = Color(0xFFE2136E),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateModule(AdminModule.PAYMENT_METHODS) }
                    )
                    AdminMetricCard(
                        title = if (isBn) "মোট শ্রেণি" else "Academic Classes",
                        value = (classes.size.takeIf { it > 0 } ?: 6).formatLocalized(lang),
                        subtitle = if (isBn) "৫ম – ১০ম শ্রেণি" else "Classes 5–10",
                        tint = Color(0xFF0D9488),
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateModule(AdminModule.CLASSES) }
                    )
                }
            }
        }

        // 2-Column Admin Management Module Cards directly on the Dashboard screen
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_dashboard_module_cards_section"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isBn) "ম্যানেজমেন্ট মডিউলসমূহ" else "Management Modules",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Surface(
                    color = RoyalBlue.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isBn) "${dashboardModules.size.formatLocalized(lang)}টি সেকশন" else "${dashboardModules.size} Sections",
                        color = RoyalBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        items(
            items = dashboardModules.chunked(2),
            key = { row -> row.joinToString("_") { it.name } }
        ) { rowModules ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowModules.forEach { mod ->
                    val badgeCount = when (mod) {
                        AdminModule.PAYMENT_NOTIFICATIONS -> unreadNotifications
                        AdminModule.SUBSCRIPTIONS -> pendingReqs
                        else -> 0
                    }
                    val subtitle = when (mod) {
                        AdminModule.PAYMENT_METHODS ->
                            if (isBn) "সক্রিয় ${activeMethods.formatLocalized(lang)}/${paymentMethods.size.formatLocalized(lang)}টি" else "Active $activeMethods/${paymentMethods.size}"
                        AdminModule.PAYMENT_NOTIFICATIONS ->
                            if (isBn) {
                                if (unreadNotifications > 0) "${unreadNotifications.formatLocalized(lang)}টি অপঠিত বার্তা" else "মোট ${adminNotifications.size.formatLocalized(lang)}টি সতর্কতা"
                            } else {
                                if (unreadNotifications > 0) "$unreadNotifications unread alert(s)" else "${adminNotifications.size} total alerts"
                            }
                        AdminModule.SUBSCRIPTIONS ->
                            if (isBn) "অপেক্ষমাণ ${pendingReqs.formatLocalized(lang)} • প্যাকেজ ${plans.size.formatLocalized(lang)}" else "Pending $pendingReqs • Plans ${plans.size}"
                        AdminModule.CLASSES ->
                            if (isBn) "মোট ${(classes.size.takeIf { it > 0 } ?: 6).formatLocalized(lang)}টি শ্রেণি" else "${classes.size.takeIf { it > 0 } ?: 6} classes"
                        AdminModule.EDUCATION_LEVELS ->
                            if (isBn) "মোট ${educationLevels.size.formatLocalized(lang)}টি স্তর" else "${educationLevels.size} levels"
                        AdminModule.STREAMS ->
                            if (isBn) "মোট ${streams.size.formatLocalized(lang)}টি বিভাগ" else "${streams.size} streams"
                        AdminModule.SUBJECTS ->
                            if (isBn) "মোট ${subjects.size.formatLocalized(lang)}টি বিষয়" else "${subjects.size} subjects"
                        AdminModule.BOOKS ->
                            if (isBn) "মোট ${books.size.formatLocalized(lang)}টি বই" else "${books.size} textbooks"
                        AdminModule.GUIDES ->
                            if (isBn) "মোট ${guides.size.formatLocalized(lang)}টি গাইড" else "${guides.size} guides"
                        AdminModule.GK ->
                            if (isBn) "মোট ${gkList.size.formatLocalized(lang)}টি প্রশ্ন" else "${gkList.size} GK items"
                        AdminModule.EXAMS ->
                            if (isBn) "মোট ${exams.size.formatLocalized(lang)}টি পরীক্ষা" else "${exams.size} MCQ exams"
                        AdminModule.USERS ->
                            if (isBn) "শিক্ষার্থী ${totalStudents.formatLocalized(lang)} জন" else "$totalStudents students"
                        AdminModule.NOTICES ->
                            if (isBn) "প্রকাশিত ${notices.size.formatLocalized(lang)}টি" else "${notices.size} notices"
                        AdminModule.SETTINGS ->
                            if (isBn) "অ্যাপ ও ডাউনলোড নিয়ন্ত্রণ" else "App & download controls"
                        AdminModule.QUICK_CONTENT ->
                            if (isBn) "প্রিমিয়াম ও দ্রুত এডিট" else "Quick premium toggle"
                        AdminModule.DASHBOARD -> ""
                    }

                    AdminModuleGridCard(
                        module = mod,
                        title = if (isBn) mod.labelBn else mod.labelEn,
                        subtitle = subtitle,
                        badgeCount = badgeCount,
                        tint = adminModuleAccentColor(mod),
                        lang = lang,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = { onNavigateModule(mod) }
                    )
                }
                if (rowModules.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // Recent Registrations
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "সাম্প্রতিক নিবন্ধিত শিক্ষার্থী" else "Recent Student Registrations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (users.isEmpty()) {
                        Text(
                            text = if (isBn) "এখনো কোনো শিক্ষার্থী নিবন্ধিত হয়নি।" else "No students registered yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        users.take(5).forEach { u ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(RoyalBlue.copy(alpha = 0.14f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(u.name.take(1).uppercase(), color = RoyalBlue, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(u.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(u.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Text(
                                    text = "Class ${u.selectedClass}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recently Added Content
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "সাম্প্রতিক যুক্ত পাঠ্যবই ও গাইড" else "Recently Added Content",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (books.isEmpty() && guides.isEmpty()) {
                        Text(
                            text = if (isBn) "বই বা গাইড ব্যবস্থাপনা থেকে নতুন কনটেন্ট যুক্ত করুন।" else "Add books or guides from the management tabs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        books.take(4).forEach { b ->
                            Text(
                                text = "📘 ${b.titleBn} (শ্রেণি ${b.classNumber})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                        guides.take(4).forEach { g ->
                            Text(
                                text = "📗 ${g.titleBn} — ${g.chapter} (শ্রেণি ${g.classNumber})",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminMetricCard(
    title: String,
    value: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.11f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = tint, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = tint)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun adminModuleAccentColor(module: AdminModule): Color = when (module) {
    AdminModule.DASHBOARD -> RoyalBlue
    AdminModule.PAYMENT_METHODS -> Color(0xFFE2136E)
    AdminModule.PAYMENT_NOTIFICATIONS -> ErrorRed
    AdminModule.SUBSCRIPTIONS -> GoldDark
    AdminModule.CLASSES -> Color(0xFF0D9488)
    AdminModule.EDUCATION_LEVELS -> Color(0xFF4F46E5)
    AdminModule.STREAMS -> Color(0xFF0284C7)
    AdminModule.SUBJECTS -> Color(0xFF9333EA)
    AdminModule.BOOKS -> RoyalBlue
    AdminModule.GUIDES -> EmeraldDark
    AdminModule.GK -> Color(0xFFD97706)
    AdminModule.EXAMS -> Color(0xFF7C3AED)
    AdminModule.USERS -> Color(0xFF2563EB)
    AdminModule.NOTICES -> Color(0xFFEA580C)
    AdminModule.SETTINGS -> NavyHeader
    AdminModule.QUICK_CONTENT -> Color(0xFF059669)
}

@Composable
private fun AdminModuleGridCard(
    module: AdminModule,
    title: String,
    subtitle: String,
    badgeCount: Int,
    tint: Color,
    lang: AppLanguage,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.testTag("admin_card_${module.name}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = module.icon,
                        contentDescription = title,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (badgeCount > 0) {
                    Surface(
                        color = ErrorRed,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = badgeCount.formatLocalized(lang),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = tint.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
