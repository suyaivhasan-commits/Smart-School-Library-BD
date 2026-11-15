package com.example.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import com.example.data.b2.B2Config
import com.example.R
import com.example.model.GuideNote
import com.example.model.Textbook
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.localizedAuthor
import com.example.ui.i18n.localizedEdition
import com.example.ui.i18n.localizedPublisher
import com.example.ui.i18n.localizedShortDescription
import com.example.ui.i18n.localizedSubject
import com.example.ui.i18n.localizedTitle
import com.example.ui.i18n.streamShortLabel
import com.example.ui.theme.Class10SlateEnd
import com.example.ui.theme.Class10SlateStart
import com.example.ui.theme.Class5BlueEnd
import com.example.ui.theme.Class5BlueStart
import com.example.ui.theme.Class6GreenEnd
import com.example.ui.theme.Class6GreenStart
import com.example.ui.theme.Class7AmberEnd
import com.example.ui.theme.Class7AmberStart
import com.example.ui.theme.Class8PurpleEnd
import com.example.ui.theme.Class8PurpleStart
import com.example.ui.theme.Class9TealEnd
import com.example.ui.theme.Class9TealStart
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldPremium
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyHeader
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import com.google.firebase.auth.FirebaseAuth
import java.net.URLEncoder
import kotlinx.coroutines.tasks.await

private const val DEFAULT_STORAGE_BUCKET = "smart-school-library-bd.firebasestorage.app"

/**
 * Resolves HTTPS URLs, Firebase Storage `gs://bucket/path` URIs, or relative storage object paths
 * into valid HTTPS media endpoints so book covers and PDFs load dynamically from Firebase Storage.
 */
fun resolveFirebaseStorageUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ""
    if (
        trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("content://", ignoreCase = true) ||
        trimmed.startsWith("file://", ignoreCase = true)
    ) {
        return trimmed
    }
    if (trimmed.startsWith("gs://", ignoreCase = true)) {
        val withoutScheme = trimmed.substring(5)
        val slashIdx = withoutScheme.indexOf('/')
        if (slashIdx <= 0 || slashIdx >= withoutScheme.length - 1) return ""
        val bucket = withoutScheme.substring(0, slashIdx)
        val objectPath = withoutScheme.substring(slashIdx + 1)
        val encodedObject = URLEncoder.encode(objectPath, "UTF-8").replace("+", "%20")
        return "https://firebasestorage.googleapis.com/v0/b/$bucket/o/$encodedObject?alt=media"
    }
    if (!trimmed.contains("://") && !trimmed.startsWith("/")) {
        val encodedObject = URLEncoder.encode(trimmed, "UTF-8").replace("+", "%20")
        return "https://firebasestorage.googleapis.com/v0/b/$DEFAULT_STORAGE_BUCKET/o/$encodedObject?alt=media"
    }
    return trimmed
}

fun normalizeGoogleDrivePdfUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (!trimmed.contains("drive.google.com", ignoreCase = true)) return trimmed
    val fileIdRegex = "(?:/file/d/|/d/|id=|uc\\?id=)([a-zA-Z0-9_-]{15,})".toRegex()
    val match = fileIdRegex.find(trimmed)
    return if (match != null) {
        val fileId = match.groupValues[1]
        "https://drive.google.com/uc?export=download&id=$fileId"
    } else {
        trimmed
    }
}

fun normalizeGoogleDriveImageUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (!trimmed.contains("drive.google.com", ignoreCase = true)) return trimmed
    val fileIdRegex = "(?:/file/d/|/d/|id=|uc\\?id=)([a-zA-Z0-9_-]{15,})".toRegex()
    val match = fileIdRegex.find(trimmed)
    return if (match != null) {
        val fileId = match.groupValues[1]
        "https://drive.google.com/thumbnail?id=$fileId&sz=w1000"
    } else {
        trimmed
    }
}

fun extractGoogleDriveViewUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) return ""
    if (!trimmed.contains("drive.google.com", ignoreCase = true)) return trimmed
    val fileIdRegex = "(?:/file/d/|/d/|id=|uc\\?id=)([a-zA-Z0-9_-]{15,})".toRegex()
    val match = fileIdRegex.find(trimmed)
    return if (match != null) {
        val fileId = match.groupValues[1]
        "https://drive.google.com/file/d/$fileId/view"
    } else {
        trimmed
    }
}

fun isGoogleDriveOrWebUrl(rawUrl: String): Boolean {
    val trimmed = rawUrl.trim()
    return trimmed.startsWith("http://", ignoreCase = true) ||
           trimmed.startsWith("https://", ignoreCase = true)
}

fun classGradientFor(classNumber: Int): Brush = when (classNumber) {
    5 -> Brush.linearGradient(listOf(Class5BlueStart, Class5BlueEnd))
    6 -> Brush.linearGradient(listOf(Class6GreenStart, Class6GreenEnd))
    7 -> Brush.linearGradient(listOf(Class7AmberStart, Class7AmberEnd))
    8 -> Brush.linearGradient(listOf(Class8PurpleStart, Class8PurpleEnd))
    9 -> Brush.linearGradient(listOf(Class9TealStart, Class9TealEnd))
    10 -> Brush.linearGradient(listOf(Class10SlateStart, Class10SlateEnd))
    else -> Brush.linearGradient(listOf(NavyPrimary, Class5BlueEnd))
}

@Composable
fun AppLogoBadge(size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.ic_app_emblem),
        contentDescription = "Smart School Library BD Logo",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape((size.value * 0.22f).dp))
    )
}

@Composable
fun BrandWordmark(fontSizeSp: Int = 18) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)) {
                append("Smart School\n")
            }
            withStyle(SpanStyle(color = EmeraldPrimary, fontWeight = FontWeight.Bold)) {
                append("Library BD")
            }
        },
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp + 5).sp
    )
}

@Composable
fun NavyScreenTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(NavyDeep, NavyHeader, NavyPrimary)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
    }
}

@Composable
fun StatusPillBadge(
    isPremium: Boolean,
    isDemo: Boolean = false,
    isUnlocked: Boolean = false
) {
    val strings = LocalAppStrings.current
    val isBn = strings.language == AppLanguage.BN
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDemo) {
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = strings.demoBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF92400E),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
        Surface(
            color = when {
                isPremium && isUnlocked -> EmeraldPrimary.copy(alpha = 0.16f)
                isPremium -> GoldPremium.copy(alpha = 0.18f)
                else -> EmeraldPrimary.copy(alpha = 0.16f)
            },
            shape = RoundedCornerShape(50)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                if (isPremium) {
                    Icon(
                        imageVector = if (isUnlocked) Icons.Filled.CheckCircle else Icons.Filled.WorkspacePremium,
                        contentDescription = null,
                        tint = if (isUnlocked) EmeraldDark else GoldDark,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = when {
                        isPremium && isUnlocked -> if (isBn) "প্রিমিয়াম (সক্রিয়)" else "Premium (Unlocked)"
                        isPremium -> strings.premiumBadge
                        else -> strings.freeBadge
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isPremium && isUnlocked -> EmeraldDark
                        isPremium -> GoldDark
                        else -> EmeraldDark
                    },
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Reusable prominent Book / Guide Cover Art component with consistent 0.72f portrait proportion,
 * distortion-free cropping (`ContentScale.Crop`), Firebase Storage URL resolution, and a styled
 * educational book-spine placeholder when `coverUrl` is missing or fails to load.
 */
@Composable
fun BookCoverArt(
    itemId: String,
    rawCoverUrl: String,
    title: String,
    subject: String,
    badgeLabel: String,
    classNumber: Int,
    isGuide: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val trimmedRaw = remember(rawCoverUrl) { rawCoverUrl.trim() }
    val isB2Cover = remember(trimmedRaw) { B2Config.isB2Path(trimmedRaw) }
    val isStorageObjectRef = remember(trimmedRaw, isB2Cover) {
        !isB2Cover && trimmedRaw.isNotBlank() && (
            trimmedRaw.startsWith("gs://", ignoreCase = true) ||
                (!trimmedRaw.contains("://") && !trimmedRaw.startsWith("/"))
            )
    }
    val staticResolvedUrl = remember(trimmedRaw, isB2Cover) {
        when {
            isB2Cover -> B2Config.buildCoverCdnUrl(trimmedRaw)
            trimmedRaw.contains("drive.google.com", ignoreCase = true) -> normalizeGoogleDriveImageUrl(trimmedRaw)
            else -> resolveFirebaseStorageUrl(trimmedRaw)
        }
    }
    val needsStorageAuthHeader = remember(staticResolvedUrl, isB2Cover) {
        isB2Cover || (staticResolvedUrl.contains("firebasestorage.googleapis.com") && !staticResolvedUrl.contains("token="))
    }

    var effectiveImageUrl by remember(trimmedRaw) {
        mutableStateOf(
            if (isStorageObjectRef || needsStorageAuthHeader) "" else staticResolvedUrl
        )
    }
    var authToken by remember(trimmedRaw) { mutableStateOf<String?>(null) }

    LaunchedEffect(trimmedRaw, isStorageObjectRef, needsStorageAuthHeader) {
        if (trimmedRaw.isBlank()) {
            effectiveImageUrl = ""
            return@LaunchedEffect
        }
        if (isStorageObjectRef) {
            val sdkDownloadUrl = try {
                val storage = com.example.firebase.FirebaseProvider.getStorage(context)
                val ref = if (trimmedRaw.startsWith("gs://", ignoreCase = true)) {
                    storage.getReferenceFromUrl(trimmedRaw)
                } else {
                    storage.reference.child(trimmedRaw)
                }
                ref.downloadUrl.await().toString()
            } catch (_: Exception) {
                null
            }
            if (!sdkDownloadUrl.isNullOrBlank()) {
                effectiveImageUrl = sdkDownloadUrl
                return@LaunchedEffect
            }
        }
        if (needsStorageAuthHeader) {
            authToken = try {
                com.example.firebase.FirebaseProvider.getAuth(context).currentUser?.getIdToken(false)?.await()?.token
            } catch (_: Exception) {
                null
            }
        }
        effectiveImageUrl = staticResolvedUrl
    }

    Box(
        modifier = modifier
            .background(classGradientFor(classNumber))
            .testTag("cover_art_$itemId"),
        contentAlignment = Alignment.Center
    ) {
        if (trimmedRaw.isBlank()) {
            StyledBookCoverPlaceholder(
                itemId = itemId,
                title = title,
                subject = subject,
                badgeLabel = badgeLabel,
                isGuide = isGuide
            )
        } else if (effectiveImageUrl.isBlank()) {
            StyledBookCoverPlaceholder(
                itemId = itemId,
                title = title,
                subject = subject,
                badgeLabel = badgeLabel,
                isGuide = isGuide,
                showLoadingSpinner = true
            )
        } else {
            val imageRequest = remember(context, effectiveImageUrl, authToken) {
                ImageRequest.Builder(context)
                    .data(effectiveImageUrl)
                    .crossfade(true)
                    .apply {
                        if (!authToken.isNullOrBlank() && !effectiveImageUrl.contains("token=")) {
                            val headerValue = if (isB2Cover) "Bearer $authToken" else "Firebase $authToken"
                            addHeader("Authorization", headerValue)
                        }
                    }
                    .build()
            }
            SubcomposeAsyncImage(
                model = imageRequest,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    StyledBookCoverPlaceholder(
                        itemId = itemId,
                        title = title,
                        subject = subject,
                        badgeLabel = badgeLabel,
                        isGuide = isGuide,
                        showLoadingSpinner = true
                    )
                },
                error = {
                    StyledBookCoverPlaceholder(
                        itemId = itemId,
                        title = title,
                        subject = subject,
                        badgeLabel = badgeLabel,
                        isGuide = isGuide
                    )
                },
                success = {
                    SubcomposeAsyncImageContent(
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            )
        }
    }
}

@Composable
private fun StyledBookCoverPlaceholder(
    itemId: String,
    title: String,
    subject: String,
    badgeLabel: String,
    isGuide: Boolean,
    showLoadingSpinner: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("cover_placeholder_$itemId")
    ) {
        // Decorative left book-spine shadow crease for realistic textbook look
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(10.dp)
                .background(Color.Black.copy(alpha = 0.22f))
                .align(Alignment.CenterStart)
        )
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 10.dp)
                .width(2.dp)
                .background(Color.White.copy(alpha = 0.22f))
                .align(Alignment.CenterStart)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.20f),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = subject.ifBlank { badgeLabel },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (showLoadingSpinner) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isGuide) Icons.Filled.Description else Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = badgeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.88f),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Premium vertical Book Card designed for the 2-column mobile grid layout.
 * Displays prominent cover art at the top (0.72f aspect ratio, distortion-free cropping),
 * bookmark icon button, and complete academic metadata underneath.
 */
@Composable
fun TextbookItemCard(
    book: Textbook,
    isBookmarked: Boolean,
    onReadClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier,
    isUnlockedPremium: Boolean = false
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    val displayTitle = book.localizedTitle(lang)
    val displaySubject = book.localizedSubject(lang)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onReadClick)
            .testTag("book_card_${book.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Prominent Top Cover Image + Accessible Bookmark Icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                BookCoverArt(
                    itemId = book.id,
                    rawCoverUrl = book.effectiveCoverRef,
                    title = displayTitle,
                    subject = displaySubject,
                    badgeLabel = "",
                    classNumber = book.classNumber,
                    isGuide = false,
                    modifier = Modifier.fillMaxSize()
                )

                // Accessible 48dp touch target Bookmark Icon at top-right of cover
                IconButton(
                    onClick = onBookmarkClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(44.dp)
                        .testTag("bookmark_book_btn_${book.id}")
                ) {
                    Surface(
                        color = NavyDeep.copy(alpha = 0.72f),
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (isBn) "বুকমার্ক" else "Bookmark",
                                tint = if (isBookmarked) EmeraldPrimary else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Exactly and ONLY the Book Title Underneath the Cover Image
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Premium vertical Guide / Study Note Card matching the 2-column book grid layout.
 */
@Composable
fun GuideItemCard(
    guide: GuideNote,
    guideTypeLabel: String,
    isBookmarked: Boolean,
    onOpenClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier,
    isUnlockedPremium: Boolean = false
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    val displayTitle = guide.localizedTitle(lang)
    val displaySubject = guide.localizedSubject(lang)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenClick)
            .testTag("guide_card_${guide.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Prominent Top Cover Image + Accessible Bookmark Icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                BookCoverArt(
                    itemId = guide.id,
                    rawCoverUrl = guide.effectiveCoverRef,
                    title = displayTitle,
                    subject = displaySubject,
                    badgeLabel = "",
                    classNumber = guide.classNumber,
                    isGuide = true,
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = onBookmarkClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(44.dp)
                        .testTag("bookmark_guide_btn_${guide.id}")
                ) {
                    Surface(
                        color = NavyDeep.copy(alpha = 0.72f),
                        shape = CircleShape,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (isBn) "বুকমার্ক" else "Bookmark guide",
                                tint = if (isBookmarked) EmeraldPrimary else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Exactly and ONLY the Guide/Note Title Underneath the Cover Image
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun MetadataCaptionLine(
    label: String,
    value: String
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                append("$label: ")
            }
            append(value)
        },
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp, lineHeight = 13.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun LoadingStateBox(message: String? = null) {
    val strings = LocalAppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            text = message ?: strings.loading,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmptyOrErrorStateCard(
    title: String,
    body: String,
    isError: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isError) Icons.Filled.CloudOff else Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (onRetry != null) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onRetry) {
                    Text(strings.retry)
                }
            }
        }
    }
}

@Composable
fun DestructiveConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(strings.delete, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
fun FormEditorDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 540.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    title()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                ) {
                    content()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                        Spacer(Modifier.width(8.dp))
                    }
                    confirmButton()
                }
            }
        }
    }
}

