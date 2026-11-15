package com.example.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.components.EmptyOrErrorStateCard
import com.example.components.LoadingStateBox
import com.example.components.NavyScreenTopBar
import com.example.model.Notice
import com.example.model.Subscription
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.formatLocalized
import com.example.ui.i18n.streamTitle
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldDark
import com.example.ui.theme.RoyalBlue
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(
    userProfile: UserProfile?,
    classes: List<com.example.model.SchoolClass> = com.example.model.DefaultCatalogSeed.defaultClasses,
    subscriptions: List<Subscription>,
    booksReadCount: Int,
    bookmarksCount: Int,
    notesCount: Int,
    examsCount: Int,
    onOpenReadingHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenNotebook: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenSettings: () -> Unit,
    onUpdateProfile: (name: String, selectedClass: Int, selectedStream: String) -> Unit,
    onLogout: () -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    var showEditDialog by remember { mutableStateOf(false) }
    val activeSubs = remember(subscriptions) { subscriptions.filter { it.isCurrentlyActive() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("profile_screen")
    ) {
        NavyScreenTopBar(
            title = if (isBn) "আমার প্রোফাইল" else "My Profile",
            subtitle = if (isBn) "শিক্ষার্থী পরিচিতি ও অধ্যয়ন পরিসংখ্যান" else "Student Account & Learning Summary",
            actions = {
                IconButton(
                    onClick = { showEditDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("profile_edit_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", tint = Color.White)
                }
            }
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Profile Identity Card matching Reference Sheet
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(82.dp)
                                .clip(CircleShape)
                                .background(RoyalBlue.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                tint = RoyalBlue,
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = userProfile?.name?.ifBlank { if (isBn) "শিক্ষার্থী" else "Student" }
                                ?: if (isBn) "শিক্ষার্থী" else "Student",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (!userProfile?.email.isNullOrBlank()) {
                            Text(
                                text = userProfile?.email.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        val cls = userProfile?.selectedClass ?: 5
                        val st = userProfile?.selectedStream ?: "NONE"
                        Text(
                            text = buildString {
                                append(if (isBn) "শ্রেণি: " else "Class: ")
                                append(classTitle(cls, lang))
                                if (cls in 9..10 && st != "NONE") {
                                    append(" • ")
                                    append(if (isBn) "বিভাগ: " else "Stream: ")
                                    append(streamTitle(st, lang))
                                } else {
                                    append(" • ")
                                    append(if (isBn) "বিভাগ: সাধারণ" else "General")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = if (activeSubs.isNotEmpty()) EmeraldPrimary.copy(alpha = 0.16f) else GoldDark.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (activeSubs.isNotEmpty()) Icons.Filled.CheckCircle else Icons.Filled.WorkspacePremium,
                                    contentDescription = null,
                                    tint = if (activeSubs.isNotEmpty()) EmeraldDark else GoldDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (activeSubs.isNotEmpty()) {
                                        if (isBn) "সক্রিয় প্রিমিয়াম সদস্য" else "Active Premium Member"
                                    } else {
                                        if (isBn) "ফ্রি সদস্য" else "Standard Free Member"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeSubs.isNotEmpty()) EmeraldDark else GoldDark
                                )
                            }
                        }
                    }
                }

                // 4 Study Statistics Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileStatCard(
                        value = booksReadCount.formatLocalized(lang),
                        label = if (isBn) "পঠিত বই" else "Books Read",
                        tint = RoyalBlue,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatCard(
                        value = bookmarksCount.formatLocalized(lang),
                        label = strings.navBookmarks,
                        tint = EmeraldDark,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatCard(
                        value = notesCount.formatLocalized(lang),
                        label = strings.featureNotebook,
                        tint = GoldDark,
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatCard(
                        value = examsCount.formatLocalized(lang),
                        label = if (isBn) "পরীক্ষা" else "Exams",
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Menu Card matching Uploaded Profile Reference
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.Filled.BarChart,
                            title = if (isBn) "আমার পড়ার পরিসংখ্যান ও ইতিহাস" else "Reading Statistics & History",
                            onClick = onOpenReadingHistory
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ProfileMenuRow(
                            icon = Icons.Filled.Bookmark,
                            title = if (isBn) "বুকমার্ক সংগ্রহ" else "Saved Bookmarks",
                            onClick = onOpenBookmarks
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ProfileMenuRow(
                            icon = Icons.Filled.EditNote,
                            title = if (isBn) "ব্যক্তিগত নোটবুক" else "Personal Notebook",
                            onClick = onOpenNotebook
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ProfileMenuRow(
                            icon = Icons.Filled.WorkspacePremium,
                            title = strings.subscriptionTitle,
                            onClick = onOpenSubscription
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ProfileMenuRow(
                            icon = Icons.Filled.Settings,
                            title = strings.featureSettings,
                            onClick = onOpenSettings
                        )
                    }
                }

                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("profile_logout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        tint = ErrorRed
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = strings.logoutButton,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showEditDialog) {
        var editName by remember(userProfile) { mutableStateOf(userProfile?.name.orEmpty()) }
        var editClass by remember(userProfile) { mutableIntStateOf(userProfile?.selectedClass ?: 5) }
        var editStream by remember(userProfile) {
            mutableStateOf(
                userProfile?.selectedStream?.takeIf { it in listOf("SCIENCE", "HUMANITIES", "BUSINESS") }
                    ?: "SCIENCE"
            )
        }

        com.example.components.FormEditorDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Text(
                    text = if (isBn) "প্রোফাইল সম্পাদনা করুন" else "Edit Student Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            content = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(strings.fullNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(strings.classesHeader, style = MaterialTheme.typography.labelLarge)
                    val selectableClassNums = remember(classes, editClass) {
                        val base = classes.ifEmpty { com.example.model.DefaultCatalogSeed.defaultClasses }
                            .map { it.classNumber }
                            .toMutableList()
                        if (editClass > 0 && editClass !in base) base.add(editClass)
                        base.distinct().sorted()
                    }
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectableClassNums.forEach { c ->
                            FilterChip(
                                selected = editClass == c,
                                onClick = { editClass = c },
                                label = { Text(classTitle(c, lang)) }
                            )
                        }
                    }

                    if (editClass in 9..10) {
                        Text(strings.selectStreamTitle, style = MaterialTheme.typography.labelLarge)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("SCIENCE", "HUMANITIES", "BUSINESS").forEach { st ->
                                FilterChip(
                                    selected = editStream == st,
                                    onClick = { editStream = st },
                                    label = { Text(streamTitle(st, lang)) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            onUpdateProfile(
                                editName,
                                editClass,
                                if (editClass in 9..10) editStream else "NONE"
                            )
                            showEditDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White)
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
private fun ProfileStatCard(
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = tint
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = RoyalBlue,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun NoticesScreen(
    noticesState: UiState<List<Notice>>,
    userNotificationsState: UiState<List<com.example.model.AppNotification>> = UiState.Success(emptyList()),
    onMarkNotificationRead: (String) -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    onBack: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val dateTimeFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val personalAlerts = (userNotificationsState as? UiState.Success)?.data.orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("notices_screen")
    ) {
        NavyScreenTopBar(
            title = strings.latestNotices,
            subtitle = if (isBn) "পেমেন্ট আপডেট ও শিক্ষামূলক বিজ্ঞপ্তি" else "Payment Alerts & School Library Announcements",
            onBack = onBack
        )

        when (noticesState) {
            is UiState.Loading -> LoadingStateBox()
            is UiState.Error -> EmptyOrErrorStateCard(strings.offlineError, noticesState.message, isError = true, onRetry = onRetry)
            is UiState.Success -> {
                if (noticesState.data.isEmpty() && personalAlerts.isEmpty()) {
                    EmptyOrErrorStateCard(
                        title = if (isBn) "কোনো নোটিশ বা নোটিফিকেশন নেই" else "No Notices or Alerts Yet",
                        body = if (isBn) "পেমেন্ট যাচাইয়ের আপডেট অথবা নতুন কোনো শিক্ষামূলক বিজ্ঞপ্তি প্রকাশিত হলে এখানে দেখা যাবে।" else "Payment status updates and official library announcements will appear here."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (personalAlerts.isNotEmpty()) {
                            item {
                                Text(
                                    text = if (isBn) "আপনার ব্যক্তিগত পেমেন্ট ও সাবস্ক্রিপশন বার্তা" else "Your Payment & Subscription Alerts",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            items(personalAlerts, key = { "p_${it.id}" }) { alert ->
                                val isApproved = alert.notificationType == "PAYMENT_APPROVED"
                                val isRejected = alert.notificationType == "PAYMENT_REJECTED"
                                val accent = when {
                                    isApproved -> EmeraldDark
                                    isRejected -> ErrorRed
                                    else -> RoyalBlue
                                }
                                val alertTime = alert.createdAt?.toDate()?.let {
                                    dateTimeFormatter.format(it).formatLocalized(lang)
                                } ?: ""

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!alert.isRead) onMarkNotificationRead(alert.id)
                                            onOpenSubscription()
                                        }
                                        .testTag("student_notification_${alert.id}"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (!alert.isRead) accent.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surface
                                    ),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                Icon(
                                                    imageVector = if (isApproved) Icons.Filled.CheckCircle else Icons.Filled.NotificationsActive,
                                                    contentDescription = null,
                                                    tint = accent,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = alert.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = accent
                                                )
                                            }
                                            if (!alert.isRead) {
                                                Surface(color = accent, shape = RoundedCornerShape(50)) {
                                                    Text(
                                                        text = if (isBn) "নতুন" else "NEW",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = alert.message,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (alertTime.isNotBlank()) {
                                            Text(
                                                text = alertTime,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (noticesState.data.isNotEmpty()) {
                            item {
                                Text(
                                    text = if (isBn) "সাধারণ নোটিশ ও ঘোষণা" else "Official Library Notices",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            items(noticesState.data, key = { it.id }) { notice ->
                                val dateStr = notice.createdAt?.toDate()?.let {
                                    dateFormatter.format(it).formatLocalized(lang)
                                } ?: ""
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (notice.isImportant) Icons.Filled.NotificationsActive else Icons.Filled.Campaign,
                                                    contentDescription = null,
                                                    tint = if (notice.isImportant) ErrorRed else RoyalBlue,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                    color = (if (notice.isImportant) ErrorRed else RoyalBlue).copy(alpha = 0.12f),
                                                    shape = RoundedCornerShape(50)
                                                ) {
                                                    Text(
                                                        text = if (notice.isImportant) {
                                                            if (isBn) "জরুরি নোটিশ" else "IMPORTANT"
                                                        } else {
                                                            notice.category
                                                        },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (notice.isImportant) ErrorRed else RoyalBlue,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                            if (dateStr.isNotBlank()) {
                                                Text(
                                                    text = dateStr,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            text = notice.titleBn,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = notice.bodyBn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
