package com.example.library

import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.BookCoverArt
import com.example.components.NavyScreenTopBar
import com.example.components.StatusPillBadge
import com.example.data.PremiumAccessDecision
import com.example.model.AppSettings
import com.example.model.DefaultCatalogSeed
import com.example.model.ExamTest
import com.example.model.GuideNote
import com.example.model.ReadingProgress
import com.example.model.Textbook
import com.example.model.UserNote
import com.example.reader.fetchAndValidatePdf
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.derivedChapters
import com.example.ui.i18n.formatLocalized
import com.example.ui.i18n.localizedAuthor
import com.example.ui.i18n.localizedEdition
import com.example.ui.i18n.localizedFullDescription
import com.example.ui.i18n.localizedPublisher
import com.example.ui.i18n.localizedShortDescription
import com.example.ui.i18n.localizedSubject
import com.example.ui.i18n.localizedTitle
import com.example.ui.i18n.streamTitle
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldPremium
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BookDetailScreen(
    book: Textbook,
    isBookmarked: Boolean,
    readingProgress: ReadingProgress?,
    accessDecision: PremiumAccessDecision,
    appSettings: AppSettings,
    relatedGuides: List<GuideNote>,
    relatedPersonalNotes: List<UserNote>,
    availableExercises: List<ExamTest>,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenPdfReader: (initialPage: Int) -> Unit,
    onOpenSubscriptionGate: () -> Unit,
    onOpenRelatedGuide: (GuideNote) -> Unit,
    onOpenNotebook: () -> Unit,
    onQuickSaveNote: (title: String, content: String, subjectTag: String) -> Unit,
    onStartExercise: (ExamTest) -> Unit,
    onOpenExamHub: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isEntitled = accessDecision in listOf(
        PremiumAccessDecision.ALLOWED_FREE,
        PremiumAccessDecision.ALLOWED_ADMIN,
        PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION
    )
    val canDownload = isEntitled &&
        book.effectivePdfRef.isNotBlank() &&
        appSettings.canDownloadContent("BOOK", book.allowDownload)

    var isDownloading by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    var showAddNoteDialog by remember { mutableStateOf(false) }

    var isSpeaking by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        runCatching {
            instance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching {
                        val res = instance?.setLanguage(Locale.forLanguageTag("bn-BD"))
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            instance?.setLanguage(Locale.US)
                        }
                    }
                }
            }
            ttsEngine = instance
        }
        onDispose {
            runCatching { instance?.stop() }
            runCatching { instance?.shutdown() }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        val src = pendingSaveFile
        if (targetUri != null && src != null && canDownload) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        src.inputStream().use { it.copyTo(out) }
                    }
                    withContext(Dispatchers.Main) {
                        onShowMessage(if (isBn) "পিডিএফ ডিভাইসে সংরক্ষিত হয়েছে।" else "PDF saved to device.")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onShowMessage(if (isBn) "ডাউনলোড ব্যর্থ: ${e.localizedMessage}" else "Download failed: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    val displayTitle = book.localizedTitle(lang)
    val displaySubject = book.localizedSubject(lang)
    val displayClass = classTitle(book.classNumber, lang)
    val displayStream = if (book.classNumber >= 9) streamTitle(book.stream, lang) else if (isBn) "সাধারণ" else "General"
    val fullDescription = book.localizedFullDescription(lang)

    val chapters = remember(book.tableOfContents, relatedGuides) {
        if (book.tableOfContents.isNotEmpty()) {
            book.tableOfContents
        } else {
            relatedGuides.map { it.chapter }.filter { it.isNotBlank() }.distinct()
        }
    }

    val practiceGuides = remember(relatedGuides) {
        relatedGuides.filter {
            it.guideType in setOf("PRACTICE", "MCQ", "CREATIVE_QUESTION", "SHORT_QUESTION", "SUGGESTION")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("book_detail_screen")
    ) {
        NavyScreenTopBar(
            title = displayTitle,
            subtitle = buildString {
                append(displayClass)
                if (book.classNumber >= 9 && book.stream != "ALL") {
                    append(" • ")
                    append(displayStream)
                }
                append(" • ")
                append(displaySubject)
            },
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = {
                        val engine = ttsEngine
                        if (engine != null) {
                            if (isSpeaking) {
                                engine.stop()
                                isSpeaking = false
                            } else {
                                engine.speak(
                                    "$displayTitle. $fullDescription",
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    "book_detail_tts"
                                )
                                isSpeaking = true
                            }
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Filled.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isBn) "বর্ণনা শুনুন" else "Read Aloud",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("book_detail_bookmark_btn")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isBn) "বুকমার্ক" else "Bookmark",
                        tint = if (isBookmarked) EmeraldPrimary else Color.White
                    )
                }
            }
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Hero Book Overview & PDF / Download Actions Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(114.dp)
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                BookCoverArt(
                                    itemId = book.id,
                                    rawCoverUrl = book.effectiveCoverRef,
                                    title = displayTitle,
                                    subject = displaySubject,
                                    badgeLabel = book.localizedEdition(lang),
                                    classNumber = book.classNumber,
                                    isGuide = false,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatusPillBadge(
                                    isPremium = book.isPremium,
                                    isUnlocked = book.isPremium && isEntitled
                                )

                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = if (isBn) {
                                        "লেখক: ${book.localizedAuthor(lang)}"
                                    } else {
                                        "Author: ${book.localizedAuthor(lang)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = if (isBn) {
                                        "প্রকাশক: ${book.localizedPublisher(lang)} • ${book.localizedEdition(lang)}"
                                    } else {
                                        "Publisher: ${book.localizedPublisher(lang)} • ${book.localizedEdition(lang)}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (readingProgress != null && readingProgress.lastPage > 0) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = if (isBn) {
                                            "পড়ার অগ্রগতি: পৃষ্ঠা ${readingProgress.lastPage.formatLocalized(lang)} / ${readingProgress.totalPages.formatLocalized(lang)} (${readingProgress.progressPercent.formatLocalized(lang)}%)"
                                        } else {
                                            "Reading Progress: Page ${readingProgress.lastPage} / ${readingProgress.totalPages} (${readingProgress.progressPercent}%)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                    LinearProgressIndicator(
                                        progress = { (readingProgress.progressPercent / 100f).coerceIn(0f, 1f) },
                                        color = EmeraldPrimary,
                                        trackColor = EmeraldPrimary.copy(alpha = 0.18f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .clip(RoundedCornerShape(50))
                                    )
                                }
                            }
                        }

                        // Primary PDF Reader Action + Secondary Resume/Download Actions
                        if (isEntitled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val resumePage = readingProgress?.lastPage?.takeIf { it > 1 } ?: 0
                                Button(
                                    onClick = { onOpenPdfReader(resumePage) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = RoyalBlue,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("book_detail_read_pdf_btn")
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (resumePage > 1) {
                                            if (isBn) "পড়া চালিয়ে যান (পৃষ্ঠা ${resumePage.formatLocalized(lang)})"
                                            else "Continue PDF (Page $resumePage)"
                                        } else {
                                            if (isBn) "বইটি পড়ুন (PDF রিডার)" else "Read Book (PDF Reader)"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (canDownload) {
                                    OutlinedButton(
                                        onClick = {
                                            if (!isDownloading) {
                                                isDownloading = true
                                                scope.launch {
                                                    val fetched = fetchAndValidatePdf(context, book.effectivePdfRef) {}
                                                    isDownloading = false
                                                    fetched.onSuccess { file ->
                                                        pendingSaveFile = file
                                                        savePdfLauncher.launch("${book.titleBn.take(40)}.pdf")
                                                    }.onFailure { err ->
                                                        onShowMessage(
                                                            if (isBn) "পিডিএফ প্রস্তুত করা যায়নি: ${err.localizedMessage}"
                                                            else "Could not prepare PDF: ${err.localizedMessage}"
                                                        )
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                        modifier = Modifier.testTag("book_detail_download_btn")
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                        } else {
                                            Icon(Icons.Filled.Download, contentDescription = if (isBn) "ডাউনলোড" else "Download", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            // Locked Premium Subscription Action
                            Surface(
                                color = GoldPremium.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = GoldDark, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (accessDecision == PremiumAccessDecision.DENIED_SUBSCRIPTION_EXPIRED) {
                                                if (isBn) "আপনার এই শ্রেণির সাবস্ক্রিপশনের মেয়াদ শেষ হয়েছে।" else "Your subscription for this class has expired."
                                            } else {
                                                if (isBn) "এই প্রিমিয়াম বইটির পিডিএফ পড়তে সক্রিয় সাবস্ক্রিপশন প্রয়োজন।" else "An active class subscription is required to read this premium book."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = GoldDark
                                        )
                                    }
                                    Button(
                                        onClick = onOpenSubscriptionGate,
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldDark, contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("book_detail_read_pdf_btn")
                                    ) {
                                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(strings.subscribeNow, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Full Description Card
                DetailSectionCard(
                    title = if (isBn) "বিস্তারিত বিবরণ" else "Full Description",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    accent = RoyalBlue,
                    testTag = "book_detail_description_section"
                ) {
                    Text(
                        text = fullDescription,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 3. Complete Book Information Grid
                DetailSectionCard(
                    title = if (isBn) "বইয়ের তথ্য" else "Book Information",
                    icon = Icons.Filled.Info,
                    accent = NavyPrimary,
                    testTag = "book_detail_info_section"
                ) {
                    val infoPairs = listOf(
                        (if (isBn) "শ্রেণি" else "Class") to displayClass,
                        (if (isBn) "বিভাগ" else "Stream") to displayStream,
                        (if (isBn) "বিষয়" else "Subject") to displaySubject,
                        (if (isBn) "লেখক" else "Author") to book.localizedAuthor(lang),
                        (if (isBn) "প্রকাশক" else "Publisher") to book.localizedPublisher(lang),
                        (if (isBn) "সংস্করণ" else "Edition") to book.localizedEdition(lang),
                        (if (isBn) "পৃষ্ঠা সংখ্যা" else "Pages") to "${book.pageCount.formatLocalized(lang)} ${strings.pagesLabel}",
                        (if (isBn) "অফলাইন ডাউনলোড" else "Offline Download") to when {
                            canDownload -> if (isBn) "অনুমোদিত" else "Allowed"
                            else -> if (isBn) "অনলাইন রিডিং মোড" else "Online Reading Only"
                        }
                    )
                    BookMetadataGrid(items = infoPairs)
                }

                // 4. Chapter List / Table of Contents
                DetailSectionCard(
                    title = if (isBn) "অধ্যায় তালিকা (${chapters.size.formatLocalized(lang)})" else "Chapter List (${chapters.size})",
                    icon = Icons.AutoMirrored.Filled.List,
                    accent = EmeraldDark,
                    testTag = "book_detail_chapters_section"
                ) {
                    if (chapters.isEmpty()) {
                        Text(
                            text = if (isBn) {
                                "পূর্ণাঙ্গ সূচিপত্র পিডিএফ রিডারের ভেতরে পৃষ্ঠাভিত্তিক দেখা যাবে।"
                            } else {
                                "Full chapter navigation is available directly inside the PDF Reader."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chapters.forEachIndexed { idx, chapterTitle ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isEntitled) onOpenPdfReader(0) else onOpenSubscriptionGate()
                                        }
                                        .testTag("book_chapter_item_$idx")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(EmeraldPrimary.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = (idx + 1).formatLocalized(lang),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldDark
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = chapterTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = null,
                                            tint = EmeraldDark,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Related Notes & Guides Section
                DetailSectionCard(
                    title = if (isBn) "সংশ্লিষ্ট নোট ও গাইড" else "Related Notes & Study Guides",
                    icon = Icons.Filled.EditNote,
                    accent = RoyalBlue,
                    testTag = "book_detail_related_notes_section",
                    headerAction = {
                        TextButton(
                            onClick = { showAddNoteDialog = true },
                            modifier = Modifier.testTag("book_detail_add_note_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isBn) "নোট লিখুন" else "Add Note", fontWeight = FontWeight.Bold)
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (relatedGuides.isNotEmpty()) {
                            Text(
                                text = if (isBn) "প্রকাশিত অধ্যায়ভিত্তিক গাইড ও নোট" else "Published Subject Guides & Notes",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldDark
                            )
                            relatedGuides.take(6).forEach { guide ->
                                val typeMeta = DefaultCatalogSeed.guideTypes.firstOrNull { it.code == guide.guideType }
                                val typeLabel = if (isBn) typeMeta?.nameBn.orEmpty() else typeMeta?.nameEn.orEmpty()
                                Surface(
                                    color = EmeraldPrimary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenRelatedGuide(guide) }
                                        .testTag("related_guide_row_${guide.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Description, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(guide.localizedTitle(lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = listOf(guide.chapter, typeLabel).filter { it.isNotBlank() }.joinToString(" • "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        StatusPillBadge(isPremium = guide.isPremium)
                                    }
                                }
                            }
                        }

                        if (relatedPersonalNotes.isNotEmpty()) {
                            if (relatedGuides.isNotEmpty()) HorizontalDivider()
                            Text(
                                text = if (isBn) "আমার ব্যক্তিগত নোট (${relatedPersonalNotes.size.formatLocalized(lang)})" else "My Personal Notes (${relatedPersonalNotes.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = RoyalBlue
                            )
                            relatedPersonalNotes.take(4).forEach { note ->
                                Surface(
                                    color = RoyalBlue.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClick = onOpenNotebook)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = note.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (relatedGuides.isEmpty() && relatedPersonalNotes.isEmpty()) {
                            Text(
                                text = if (isBn) {
                                    "এই বিষয়ের জন্য এখনো কোনো নোট নেই। উপরের 'নোট লিখুন' বাটনে ট্যাপ করে আপনার নিজস্ব নোট সংরক্ষণ করুন।"
                                } else {
                                    "No related notes yet. Tap 'Add Note' above to create your own personal study note for this book."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenNotebook,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBn) "ব্যক্তিগত নোটবুক খুলুন" else "Open Personal Notebook")
                        }
                    }
                }

                // 6. Available Exercises & MCQ Model Tests Section
                DetailSectionCard(
                    title = if (isBn) "অনুশীলনী ও মডেল টেস্ট" else "Available Exercises",
                    icon = Icons.Filled.Quiz,
                    accent = GoldDark,
                    testTag = "book_detail_exercises_section"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (availableExercises.isNotEmpty()) {
                            availableExercises.take(5).forEach { exam ->
                                Surface(
                                    color = GoldPremium.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onStartExercise(exam) }
                                        .testTag("book_exercise_item_${exam.id}")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (lang == AppLanguage.EN && exam.titleEn.isNotBlank()) exam.titleEn else exam.titleBn,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = if (isBn) {
                                                    "${exam.questionCount.formatLocalized(lang)}টি প্রশ্ন • ${exam.durationMinutes.formatLocalized(lang)} মিনিট • পূর্ণমান ${exam.totalMarks.formatLocalized(lang)}"
                                                } else {
                                                    "${exam.questionCount} MCQs • ${exam.durationMinutes} min • ${exam.totalMarks} marks"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = { onStartExercise(exam) },
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(if (isBn) "শুরু করুন" else "Start", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        if (practiceGuides.isNotEmpty()) {
                            practiceGuides.take(4).forEach { pg ->
                                val gt = DefaultCatalogSeed.guideTypes.firstOrNull { it.code == pg.guideType }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenRelatedGuide(pg) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Quiz, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(pg.localizedTitle(lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (isBn) gt?.nameBn ?: pg.chapter else gt?.nameEn ?: pg.chapter,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        if (availableExercises.isEmpty() && practiceGuides.isEmpty()) {
                            Text(
                                text = if (isBn) {
                                    "এই বিষয়ের অধ্যায়ভিত্তিক নৈর্ব্যক্তিক ও মডেল টেস্ট অনুশীলন করতে নিচের পরীক্ষার প্রস্তুতি মডিউল খুলুন।"
                                } else {
                                    "Open the Exam Preparation hub below to practice class-level MCQ tests and exercises."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedButton(
                            onClick = onOpenExamHub,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBn) "সকল অনুশীলনী ও মডেল টেস্ট দেখুন" else "Browse All Class Exercises & Tests")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddNoteDialog) {
        QuickSubjectNoteDialog(
            defaultSubjectTag = book.subjectNameBn,
            bookTitle = displayTitle,
            isBn = isBn,
            onDismiss = { showAddNoteDialog = false },
            onSave = { title, body, tag ->
                onQuickSaveNote(title, body, tag)
                showAddNoteDialog = false
            }
        )
    }
}

@Composable
fun GuideDetailScreen(
    guide: GuideNote,
    isBookmarked: Boolean,
    accessDecision: PremiumAccessDecision = PremiumAccessDecision.ALLOWED_FREE,
    appSettings: AppSettings = AppSettings(),
    relatedBooks: List<Textbook> = emptyList(),
    relatedPersonalNotes: List<UserNote> = emptyList(),
    availableExercises: List<ExamTest> = emptyList(),
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenPdfAttachment: () -> Unit,
    onOpenSubscriptionGate: () -> Unit = {},
    onOpenRelatedBook: (Textbook) -> Unit = {},
    onOpenNotebook: () -> Unit = {},
    onQuickSaveNote: (title: String, content: String, subjectTag: String) -> Unit = { _, _, _ -> },
    onStartExercise: (ExamTest) -> Unit = {},
    onOpenExamHub: () -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onRecordRead: () -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isEntitled = accessDecision in listOf(
        PremiumAccessDecision.ALLOWED_FREE,
        PremiumAccessDecision.ALLOWED_ADMIN,
        PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION
    )
    val contentTypeForDownload = if (guide.guideType == "CHAPTER_NOTE") "NOTE" else "GUIDE"
    val canDownload = isEntitled &&
        guide.effectivePdfRef.isNotBlank() &&
        appSettings.canDownloadContent(contentTypeForDownload, guide.allowDownload)

    var isDownloading by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    var showAddNoteDialog by remember { mutableStateOf(false) }

    var isSpeaking by remember { mutableStateOf(false) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }

    LaunchedEffect(guide.id, isEntitled) {
        if (isEntitled) {
            onRecordRead()
        }
    }

    DisposableEffect(context) {
        var instance: TextToSpeech? = null
        runCatching {
            instance = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    runCatching {
                        val res = instance?.setLanguage(Locale.forLanguageTag("bn-BD"))
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            instance?.setLanguage(Locale.US)
                        }
                    }
                }
            }
            ttsEngine = instance
        }
        onDispose {
            runCatching { instance?.stop() }
            runCatching { instance?.shutdown() }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        val src = pendingSaveFile
        if (targetUri != null && src != null && canDownload) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(targetUri)?.use { out ->
                        src.inputStream().use { it.copyTo(out) }
                    }
                    withContext(Dispatchers.Main) {
                        onShowMessage(if (isBn) "গাইড পিডিএফ সংরক্ষিত হয়েছে।" else "Guide PDF saved to device.")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onShowMessage(if (isBn) "সংরক্ষণ ব্যর্থ: ${e.localizedMessage}" else "Save failed: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    val typeLabel = DefaultCatalogSeed.guideTypes.firstOrNull { it.code == guide.guideType }
        ?.let { if (isBn) it.nameBn else it.nameEn } ?: guide.guideType
    val displayTitle = guide.localizedTitle(lang)
    val displaySubject = guide.localizedSubject(lang)
    val displayClass = classTitle(guide.classNumber, lang)
    val displayStream = if (guide.classNumber >= 9) streamTitle(guide.stream, lang) else if (isBn) "সাধারণ" else "General"
    val chapters = remember(guide) { guide.derivedChapters() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("guide_detail_screen")
    ) {
        NavyScreenTopBar(
            title = displayTitle,
            subtitle = "$displaySubject • ${guide.chapter}",
            onBack = onBack,
            actions = {
                if (isEntitled && guide.summaryContent.isNotBlank()) {
                    IconButton(
                        onClick = {
                            val engine = ttsEngine
                            if (engine != null) {
                                if (isSpeaking) {
                                    engine.stop()
                                    isSpeaking = false
                                } else {
                                    engine.speak(
                                        "$displayTitle. ${guide.summaryContent}",
                                        TextToSpeech.QUEUE_FLUSH,
                                        null,
                                        "guide_tts"
                                    )
                                    isSpeaking = true
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isSpeaking) Icons.Filled.StopCircle else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Read Aloud",
                            tint = Color.White
                        )
                    }
                }
                IconButton(
                    onClick = onToggleBookmark,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("guide_detail_bookmark_btn")
                ) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (isBookmarked) EmeraldPrimary else Color.White
                    )
                }
            }
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Guide Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(110.dp)
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                BookCoverArt(
                                    itemId = guide.id,
                                    rawCoverUrl = guide.effectiveCoverRef,
                                    title = displayTitle,
                                    subject = displaySubject,
                                    badgeLabel = typeLabel,
                                    classNumber = guide.classNumber,
                                    isGuide = true,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatusPillBadge(
                                    isPremium = guide.isPremium,
                                    isUnlocked = guide.isPremium && isEntitled
                                )
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$displaySubject • ${guide.chapter}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isBn) {
                                        "লেখক: ${guide.localizedAuthor(lang)}\nপ্রকাশক: ${guide.localizedPublisher(lang)}"
                                    } else {
                                        "Author: ${guide.localizedAuthor(lang)}\nPublisher: ${guide.localizedPublisher(lang)}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (isEntitled && guide.effectivePdfRef.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onOpenPdfAttachment,
                                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("guide_detail_read_pdf_btn")
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isBn) "সম্পূর্ণ পিডিএফ গাইড খুলুন" else "Open Full PDF Guide",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (canDownload) {
                                    OutlinedButton(
                                        onClick = {
                                            if (!isDownloading) {
                                                isDownloading = true
                                                scope.launch {
                                                    val fetched = fetchAndValidatePdf(context, guide.effectivePdfRef) {}
                                                    isDownloading = false
                                                    fetched.onSuccess { file ->
                                                        pendingSaveFile = file
                                                        savePdfLauncher.launch("${guide.titleBn.take(40)}.pdf")
                                                    }.onFailure { err ->
                                                        onShowMessage(err.localizedMessage ?: "Download error")
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                        } else {
                                            Icon(Icons.Filled.Download, contentDescription = "Download", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        } else if (!isEntitled) {
                            Surface(
                                color = GoldPremium.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (isBn) {
                                            "এই প্রিমিয়াম গাইড ও নোট সম্পূর্ণ পড়তে সক্রিয় শ্রেণি সাবস্ক্রিপশন প্রয়োজন।"
                                        } else {
                                            "An active class subscription is required to read the full premium guide & notes."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = GoldDark
                                    )
                                    Button(
                                        onClick = onOpenSubscriptionGate,
                                        colors = ButtonDefaults.buttonColors(containerColor = GoldDark, contentColor = Color.White),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(strings.subscribeNow, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Full Description / Study Note Body
                DetailSectionCard(
                    title = if (isBn) "বিস্তারিত পাঠ ও নোট" else "Full Guide & Study Notes",
                    icon = Icons.Filled.Description,
                    accent = EmeraldDark
                ) {
                    if (isEntitled) {
                        Text(
                            text = guide.summaryContent.ifBlank { guide.localizedShortDescription(lang) },
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 25.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = guide.localizedShortDescription(lang),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Guide Information Metadata Grid
                DetailSectionCard(
                    title = if (isBn) "গাইড ও নোটের তথ্য" else "Guide Information",
                    icon = Icons.Filled.Info,
                    accent = NavyPrimary
                ) {
                    val infoPairs = listOf(
                        (if (isBn) "শ্রেণি" else "Class") to displayClass,
                        (if (isBn) "বিভাগ" else "Stream") to displayStream,
                        (if (isBn) "বিষয়" else "Subject") to displaySubject,
                        (if (isBn) "অধ্যায়" else "Chapter") to guide.chapter,
                        (if (isBn) "ধরন" else "Type") to typeLabel,
                        (if (isBn) "লেখক" else "Author") to guide.localizedAuthor(lang),
                        (if (isBn) "প্রকাশক" else "Publisher") to guide.localizedPublisher(lang),
                        (if (isBn) "সংস্করণ" else "Edition") to guide.localizedEdition(lang)
                    )
                    BookMetadataGrid(items = infoPairs)
                }

                // Chapter / Topic List
                if (chapters.isNotEmpty()) {
                    DetailSectionCard(
                        title = if (isBn) "অধ্যায় ও পাঠ্যসূচি" else "Chapter & Topic Outline",
                        icon = Icons.AutoMirrored.Filled.List,
                        accent = EmeraldDark
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chapters.forEachIndexed { idx, ch ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldPrimary.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = (idx + 1).formatLocalized(lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldDark
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(ch, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }

                // Related Textbooks & Personal Notes
                DetailSectionCard(
                    title = if (isBn) "মূল পাঠ্যবই ও ব্যক্তিগত নোট" else "Related Textbook & Personal Notes",
                    icon = Icons.Filled.EditNote,
                    accent = RoyalBlue,
                    headerAction = {
                        TextButton(onClick = { showAddNoteDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isBn) "নোট লিখুন" else "Add Note", fontWeight = FontWeight.Bold)
                        }
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        relatedBooks.take(2).forEach { tb ->
                            Surface(
                                color = RoyalBlue.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenRelatedBook(tb) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = RoyalBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(tb.localizedTitle(lang), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(tb.localizedEdition(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        relatedPersonalNotes.take(3).forEach { note ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenNotebook)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(note.content, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = onOpenNotebook,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isBn) "ব্যক্তিগত নোটবুক খুলুন" else "Open Personal Notebook")
                        }
                    }
                }

                // Available Exercises
                DetailSectionCard(
                    title = if (isBn) "অনুশীলনী ও মডেল টেস্ট" else "Available Exercises",
                    icon = Icons.Filled.Quiz,
                    accent = GoldDark
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        availableExercises.take(4).forEach { exam ->
                            Surface(
                                color = GoldPremium.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStartExercise(exam) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(exam.titleBn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${exam.questionCount.formatLocalized(lang)} MCQ • ${exam.durationMinutes.formatLocalized(lang)} min",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = GoldDark, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenExamHub,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Quiz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isBn) "অনুশীলনী ও পরীক্ষা শুরু করুন" else "Open Practice & Exams")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showAddNoteDialog) {
        QuickSubjectNoteDialog(
            defaultSubjectTag = guide.subjectNameBn,
            bookTitle = displayTitle,
            isBn = isBn,
            onDismiss = { showAddNoteDialog = false },
            onSave = { title, body, tag ->
                onQuickSaveNote(title, body, tag)
                showAddNoteDialog = false
            }
        )
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    testTag: String? = null,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                headerAction?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun BookMetadataGrid(
    items: List<Pair<String, String>>
) {
    val rows = items.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowPairs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowPairs.forEach { (label, value) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowPairs.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickSubjectNoteDialog(
    defaultSubjectTag: String,
    bookTitle: String,
    isBn: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, subjectTag: String) -> Unit
) {
    var noteTitle by remember { mutableStateOf("$bookTitle — ") }
    var noteContent by remember { mutableStateOf("") }
    var subjectTag by remember { mutableStateOf(defaultSubjectTag) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isBn) "বই সম্পর্কিত নোট সংরক্ষণ করুন" else "Add Study Note",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it },
                    label = { Text(if (isBn) "নোটের শিরোনাম *" else "Note Title *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = subjectTag,
                    onValueChange = { subjectTag = it },
                    label = { Text(if (isBn) "বিষয় ট্যাগ" else "Subject Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteContent,
                    onValueChange = { noteContent = it },
                    label = { Text(if (isBn) "বিস্তারিত নোট লিখুন *" else "Write your note *") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (noteTitle.isNotBlank() && noteContent.isNotBlank()) {
                        onSave(noteTitle.trim(), noteContent.trim(), subjectTag.trim())
                    }
                }
            ) {
                Text(if (isBn) "সংরক্ষণ করুন" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBn) "বাতিল" else "Cancel")
            }
        }
    )
}
