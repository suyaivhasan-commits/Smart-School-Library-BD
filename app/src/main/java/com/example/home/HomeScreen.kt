package com.example.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.AppLogoBadge
import com.example.components.BrandWordmark
import com.example.components.GuideItemCard
import com.example.components.TextbookItemCard
import com.example.components.classGradientFor
import com.example.firebase.SecurityConstants
import com.example.model.AppSettings
import com.example.model.Bookmark
import com.example.model.ContentClassGroupHelper
import com.example.model.DefaultCatalogSeed
import com.example.model.EducationLevel
import com.example.model.GuideNote
import com.example.model.Notice
import com.example.model.ReadingProgress
import com.example.model.SchoolClass
import com.example.model.Subscription
import com.example.model.Textbook
import com.example.model.UserProfile
import com.example.model.sortedBySerialOrder
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.formatLocalized
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldPremium
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyHeader
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue

private data class QuickShortcut(
    val tag: String,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    currentUserUid: String,
    userProfile: UserProfile?,
    classes: List<SchoolClass>,
    educationLevels: List<EducationLevel> = emptyList(),
    books: List<Textbook>,
    guides: List<GuideNote>,
    bookmarks: List<Bookmark>,
    readingProgress: List<ReadingProgress>,
    notices: List<Notice>,
    subscriptions: List<Subscription>,
    appSettings: AppSettings,
    unreadNotificationCount: Int = 0,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenClass: (Int) -> Unit,
    onOpenBook: (Textbook) -> Unit,
    onOpenGuide: (GuideNote) -> Unit,
    onResumeReading: (ReadingProgress) -> Unit,
    onToggleBookBookmark: (Textbook) -> Unit,
    onToggleGuideBookmark: (GuideNote) -> Unit,
    onOpenNotebook: () -> Unit,
    onOpenGk: () -> Unit,
    onOpenExam: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSuperAdmin: () -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    var searchQuery by remember { mutableStateOf("") }
    var inspectedLevel by remember { mutableStateOf<Pair<EducationLevel, List<SchoolClass>>?>(null) }

    val activeSubs = remember(subscriptions) {
        subscriptions.filter { it.isCurrentlyActive() }
    }
    val bookmarkedIds = remember(bookmarks) {
        bookmarks.filter { it.bookmarkType == "BOOK" || it.bookmarkType == "GUIDE" }.map { it.targetId }.toSet()
    }
    val popularBooks = remember(books) {
        val uniqueBooks = ContentClassGroupHelper.deduplicateBooksForAllClassesView(books)
        val marked = uniqueBooks.filter { it.isPopular }
        if (marked.isNotEmpty()) marked.take(8) else uniqueBooks.take(6)
    }

    val searchMatchingBooks = remember(searchQuery, books) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim()
            val matched = books.filter {
                it.titleBn.contains(q, ignoreCase = true) ||
                    it.titleEn.contains(q, ignoreCase = true) ||
                    it.subjectNameBn.contains(q, ignoreCase = true) ||
                    it.subjectNameEn.contains(q, ignoreCase = true)
            }
            ContentClassGroupHelper.deduplicateBooksForAllClassesView(matched)
        }
    }
    val searchMatchingGuides = remember(searchQuery, guides) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val q = searchQuery.trim()
            val matched = guides.filter {
                it.titleBn.contains(q, ignoreCase = true) ||
                    it.subjectNameBn.contains(q, ignoreCase = true) ||
                    it.chapter.contains(q, ignoreCase = true)
            }
            ContentClassGroupHelper.deduplicateGuidesForAllClassesView(matched)
        }
    }

    val shortcuts = listOf(
        QuickShortcut("shortcut_notebook", strings.featureNotebook, Icons.Filled.EditNote, RoyalBlue, onOpenNotebook),
        QuickShortcut("shortcut_gk", strings.featureGk, Icons.Filled.Lightbulb, GoldDark, onOpenGk),
        QuickShortcut("shortcut_exam", strings.featureExam, Icons.Filled.Quiz, EmeraldDark, onOpenExam),
        QuickShortcut("shortcut_bookmarks", strings.featureBookmarks, Icons.Filled.Bookmark, Color(0xFF7C3AED), onOpenBookmarks),
        QuickShortcut("shortcut_history", strings.featureHistory, Icons.Filled.History, Color(0xFF0D9488), onOpenHistory),
        QuickShortcut("shortcut_premium", strings.featurePremium, Icons.Filled.WorkspacePremium, GoldDark, onOpenSubscription),
        QuickShortcut("shortcut_notices", strings.featureNotifications, Icons.Filled.Notifications, Color(0xFFE11D48), onOpenNotifications),
        QuickShortcut("shortcut_settings", strings.featureSettings, Icons.Filled.Settings, NavyPrimary, onOpenSettings)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_screen")
    ) {
        // Header matching Reference Sheet
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(NavyDeep, NavyHeader, NavyPrimary)))
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppLogoBadge(size = 42.dp)
                    Spacer(Modifier.width(10.dp))
                    BrandWordmark(fontSizeSp = 17)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Strictly show Super Admin entry button ONLY for exact trusted UID
                    if (SecurityConstants.isTrustedSuperAdmin(currentUserUid)) {
                        IconButton(
                            onClick = onOpenSuperAdmin,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("home_super_admin_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = "Super Admin Panel",
                                tint = GoldPremium
                            )
                        }
                    }

                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = onOpenNotifications,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_notifications_button")
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Filled.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.padding(4.dp)
                            )
                            if (unreadNotificationCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = unreadNotificationCount.coerceAtMost(9).toString(),
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onOpenProfile,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_profile_shortcut")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(EmeraldPrimary.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Optional Announcement Banner from AppSettings
            if (appSettings.announcementBannerBn.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.14f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Campaign, contentDescription = null, tint = EmeraldDark)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = appSettings.announcementBannerBn,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Personalized Bengali Welcome Card + Subscription & Learning Progress
            item {
                val studentName = userProfile?.name?.ifBlank { null }
                    ?: if (isBn) "প্রিয় শিক্ষার্থী" else "Student"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(RoyalBlue.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = studentName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = RoyalBlue,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${strings.welcomePrefix}, $studentName",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = strings.welcomeSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Subscription Status Pill
                            Surface(
                                color = if (activeSubs.isNotEmpty()) EmeraldPrimary.copy(alpha = 0.16f) else GoldPremium.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.clickable(onClick = onOpenSubscription)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WorkspacePremium,
                                        contentDescription = null,
                                        tint = if (activeSubs.isNotEmpty()) EmeraldDark else GoldDark,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (activeSubs.isNotEmpty()) {
                                            if (isBn) "প্রিমিয়াম সক্রিয়" else "Premium Active"
                                        } else {
                                            if (isBn) "ফ্রি প্ল্যান" else "Free Plan"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (activeSubs.isNotEmpty()) EmeraldDark else GoldDark
                                    )
                                }
                            }
                        }

                        if (readingProgress.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            val avgProgress = readingProgress.map { it.progressPercent }.average().toFloat() / 100f
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = strings.learningProgress,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${(avgProgress * 100).toInt().formatLocalized(lang)}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldDark
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { avgProgress.coerceIn(0f, 1f) },
                                color = EmeraldPrimary,
                                trackColor = EmeraldPrimary.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(strings.searchHint) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_search_bar")
                )
            }

            // Live Search Results (when user types in search bar)
            if (searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = if (isBn) {
                            "অনুসন্ধানের ফলাফল (${(searchMatchingBooks.size + searchMatchingGuides.size).formatLocalized(lang)})"
                        } else {
                            "Search Results (${searchMatchingBooks.size + searchMatchingGuides.size})"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (searchMatchingBooks.isNotEmpty()) {
                    item {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth >= 700.dp) 3 else 2
                            val rows = searchMatchingBooks.chunked(columns)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                rows.forEach { rowBooks ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowBooks.forEach { book ->
                                            TextbookItemCard(
                                                book = book,
                                                isBookmarked = book.id in bookmarkedIds,
                                                onReadClick = { onOpenBook(book) },
                                                onBookmarkClick = { onToggleBookBookmark(book) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowBooks.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (searchMatchingGuides.isNotEmpty()) {
                    item {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth >= 700.dp) 3 else 2
                            val rows = searchMatchingGuides.chunked(columns)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                rows.forEach { rowGuides ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowGuides.forEach { guide ->
                                            val typeMeta = DefaultCatalogSeed.guideTypes.firstOrNull { it.code == guide.guideType }
                                            val typeName = if (isBn) typeMeta?.nameBn.orEmpty() else typeMeta?.nameEn.orEmpty()
                                            GuideItemCard(
                                                guide = guide,
                                                guideTypeLabel = typeName,
                                                isBookmarked = guide.id in bookmarkedIds,
                                                onOpenClick = { onOpenGuide(guide) },
                                                onBookmarkClick = { onToggleGuideBookmark(guide) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowGuides.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Class Selection Cards (Ascending Serial Order + Slim Educational-Level Banners)
                item {
                    Text(
                        text = strings.classesHeader,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    val displayClasses = remember(classes) {
                        classes.sortedBySerialOrder()
                    }
                    val activeLevels = remember(educationLevels) {
                        educationLevels
                            .filter { it.isActive }
                            .sortedWith(
                                compareBy<EducationLevel> { it.sortOrder }
                                    .thenBy { it.minClassNumber }
                                    .thenBy { it.nameEn }
                            )
                    }
                    val classGrouping = remember(displayClasses, activeLevels) {
                        val assignedIds = mutableSetOf<String>()
                        val levelGroups = activeLevels.map { level ->
                            val matching = displayClasses.filter { cls ->
                                val explicitMatch = cls.educationLevelId == level.id
                                val rangeMatch = cls.educationLevelId.isBlank() &&
                                    level.minClassNumber > 0 &&
                                    level.maxClassNumber >= level.minClassNumber &&
                                    cls.classNumber in level.minClassNumber..level.maxClassNumber
                                (explicitMatch || rangeMatch) && cls.classNumber >= 9
                            }.sortedBySerialOrder()
                            matching.forEach { assignedIds.add(it.id) }
                            level to matching
                        }
                        // Classes 5–8 (or < 9) ALWAYS stay above with NO level card above them
                        val topClasses = displayClasses.filter { it.classNumber < 9 || it.id !in assignedIds }
                            .let { candidates ->
                                val lower = candidates.filter { it.classNumber < 9 }.sortedBySerialOrder()
                                lower.forEach { assignedIds.add(it.id) }
                                lower
                            }
                        val trailingUnassigned = displayClasses.filter { it.id !in assignedIds }.sortedBySerialOrder()
                        Triple(topClasses, levelGroups, trailingUnassigned)
                    }
                    val (topClasses, levelGroups, trailingClasses) = classGrouping

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columns = if (maxWidth >= 600.dp) 3 else 2
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (topClasses.isNotEmpty()) {
                                ClassCardGridRows(
                                    items = topClasses,
                                    columns = columns,
                                    lang = lang,
                                    onOpenClass = onOpenClass
                                )
                            }

                            levelGroups.forEachIndexed { idx, (level, groupClasses) ->
                                EducationLevelBannerCard(
                                    level = level,
                                    classCount = groupClasses.size,
                                    accentIndex = idx,
                                    lang = lang,
                                    onClick = { inspectedLevel = level to groupClasses }
                                )
                                if (groupClasses.isNotEmpty()) {
                                    ClassCardGridRows(
                                        items = groupClasses,
                                        columns = columns,
                                        lang = lang,
                                        onOpenClass = onOpenClass
                                    )
                                }
                            }

                            if (trailingClasses.isNotEmpty()) {
                                ClassCardGridRows(
                                    items = trailingClasses,
                                    columns = columns,
                                    lang = lang,
                                    onOpenClass = onOpenClass
                                )
                            }
                        }
                    }
                }

                // Continue Reading & Recently Opened Books
                if (readingProgress.isNotEmpty()) {
                    item {
                        val latest = readingProgress.first()
                        Text(
                            text = strings.continueReading,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResumeReading(latest) }
                                .testTag("continue_reading_card"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = NavyPrimary)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = latest.titleBn,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isBn) {
                                            "পৃষ্ঠা ${latest.lastPage.formatLocalized(lang)} / ${latest.totalPages.formatLocalized(lang)} (${latest.progressPercent.formatLocalized(lang)}%)"
                                        } else {
                                            "Page ${latest.lastPage} / ${latest.totalPages} (${latest.progressPercent}%)"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                                Surface(
                                    color = EmeraldPrimary,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = strings.readNow,
                                        color = Color(0xFF042F1E),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (readingProgress.size > 1) {
                        item {
                            Text(
                                text = strings.recentBooks,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(readingProgress.take(6), key = { it.id }) { item ->
                                    Card(
                                        modifier = Modifier
                                            .width(175.dp)
                                            .clickable { onResumeReading(item) },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = item.titleBn,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = classTitle(item.classNumber, lang),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            LinearProgressIndicator(
                                                progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                                                color = EmeraldPrimary,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(50))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Additional Home Buttons Grid ("অন্যান্য সুবিধাসমূহ")
                item {
                    Text(
                        text = strings.otherFeatures,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(10.dp))
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val cols = if (maxWidth >= 600.dp) 4 else 4
                        val rows = shortcuts.chunked(cols)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            rows.forEach { rowShortcuts ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowShortcuts.forEach { item ->
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(onClick = item.onClick)
                                                .testTag(item.tag),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp, horizontal = 6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(item.tint.copy(alpha = 0.14f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = item.icon,
                                                        contentDescription = item.label,
                                                        tint = item.tint,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Text(
                                                    text = item.label,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Popular Books Section (2-column grid on mobile)
                if (popularBooks.isNotEmpty()) {
                    item {
                        Text(
                            text = strings.popularBooks,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val columns = if (maxWidth >= 700.dp) 3 else 2
                            val rows = popularBooks.take(4).chunked(columns)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                rows.forEach { rowBooks ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowBooks.forEach { book ->
                                            TextbookItemCard(
                                                book = book,
                                                isBookmarked = book.id in bookmarkedIds,
                                                onReadClick = { onOpenBook(book) },
                                                onBookmarkClick = { onToggleBookBookmark(book) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowBooks.size) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Latest Notices Section
                if (notices.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.latestNotices,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isBn) "সব দেখুন →" else "See all →",
                                style = MaterialTheme.typography.labelMedium,
                                color = RoyalBlue,
                                modifier = Modifier.clickable(onClick = onOpenNotifications)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        notices.take(2).forEach { notice ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clickable(onClick = onOpenNotifications),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = notice.titleBn,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = notice.bodyBn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    inspectedLevel?.let { (level, groupClasses) ->
        AlertDialog(
            onDismissRequest = { inspectedLevel = null },
            title = {
                Text(
                    text = if (isBn) level.displayBannerBn else level.displayBannerEn,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val desc = if (isBn) level.descriptionBn else level.subtitleEn.ifBlank { level.descriptionBn }
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (groupClasses.isEmpty()) {
                        Text(
                            text = if (isBn) {
                                "এই শিক্ষা স্তরের অন্তর্ভুক্ত শ্রেণিগুলো শীঘ্রই যুক্ত করা হবে।"
                            } else {
                                "Classes under this educational level will be added soon."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text(
                            text = if (isBn) "নিচের যেকোনো শ্রেণিতে প্রবেশ করুন:" else "Select a class below:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        groupClasses.forEach { cls ->
                            OutlinedButton(
                                onClick = {
                                    inspectedLevel = null
                                    onOpenClass(cls.classNumber)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBn) cls.displayNameBn else cls.displayNameEn,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedLevel = null }) {
                    Text(strings.close)
                }
            }
        )
    }
}

@Composable
private fun ClassCardGridRows(
    items: List<SchoolClass>,
    columns: Int,
    lang: AppLanguage,
    onOpenClass: (Int) -> Unit
) {
    val rows = items.chunked(columns.coerceAtLeast(1))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { rowClasses ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowClasses.forEach { schoolClass ->
                    ClassHeroTile(
                        schoolClass = schoolClass,
                        lang = lang,
                        onClick = { onOpenClass(schoolClass.classNumber) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowClasses.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun EducationLevelBannerCard(
    level: EducationLevel,
    classCount: Int,
    accentIndex: Int,
    lang: AppLanguage,
    onClick: () -> Unit
) {
    val isBn = lang == AppLanguage.BN
    val accentColor = when (accentIndex % 3) {
        0 -> NavyPrimary
        1 -> EmeraldDark
        else -> Color(0xFF6D28D9)
    }
    val containerColor = accentColor.copy(alpha = 0.08f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .testTag("education_level_banner_${level.id}"),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )
            Surface(
                color = accentColor,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = level.code.ifBlank { level.nameEn.take(8) },
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isBn) level.displayBannerBn else level.displayBannerEn,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = if (isBn) level.subtitleBn else level.subtitleEn
                if (sub.isNotBlank()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (classCount > 0) {
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isBn) "${classCount.formatLocalized(lang)}টি শ্রেণি" else "$classCount cls",
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isBn) level.displayBannerBn else level.displayBannerEn,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ClassHeroTile(
    schoolClass: SchoolClass,
    lang: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val numDisplay = schoolClass.classNumber.toString().padStart(2, '0').let {
        if (lang == AppLanguage.BN) schoolClass.classNumber.formatLocalized(lang) else it
    }
    Card(
        modifier = modifier
            .height(94.dp)
            .clickable(onClick = onClick)
            .testTag("class_button_${schoolClass.classNumber}"),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(classGradientFor(schoolClass.classNumber))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = numDisplay,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (lang == AppLanguage.BN) schoolClass.displayNameBn else schoolClass.displayNameEn,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
