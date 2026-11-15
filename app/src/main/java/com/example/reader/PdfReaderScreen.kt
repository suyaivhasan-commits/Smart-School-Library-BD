package com.example.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.EmptyOrErrorStateCard
import com.example.components.NavyScreenTopBar
import com.example.components.extractGoogleDriveViewUrl
import com.example.components.isGoogleDriveOrWebUrl
import com.example.components.normalizeGoogleDrivePdfUrl
import com.example.components.resolveFirebaseStorageUrl
import com.example.data.b2.B2Config
import com.example.data.b2.B2StorageClient
import com.example.model.AppSettings
import com.example.model.Bookmark
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.formatLocalized
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.google.firebase.auth.FirebaseAuth
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private sealed interface PdfLoadState {
    data class Loading(val progress: Float) : PdfLoadState
    data class Ready(val file: File, val pageCount: Int) : PdfLoadState
    data class Failed(val errorMessage: String) : PdfLoadState
}

private val sharedPdfHttpClient by lazy {
    OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

internal suspend fun fetchAndValidatePdf(
    context: Context,
    pdfUrl: String,
    onProgress: (Float) -> Unit
): Result<File> = withContext(Dispatchers.IO) {
    val rawRef = pdfUrl.trim()
    if (rawRef.isBlank()) {
        return@withContext Result.failure(IllegalArgumentException("পিডিএফ লিংক পাওয়া যায়নি।"))
    }
    try {
        val cacheFolder = File(context.cacheDir, "smart_school_pdfs").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(rawRef.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(24)
        val targetFile = File(cacheFolder, "doc_$hash.pdf")

        if (targetFile.exists() && targetFile.length() > 8L && hasValidPdfHeader(targetFile)) {
            onProgress(1f)
            return@withContext Result.success(targetFile)
        }

        val tempFile = File(cacheFolder, "tmp_$hash.pdf")
        if (tempFile.exists()) tempFile.delete()

        val isLocalUri = rawRef.startsWith("file://", ignoreCase = true) ||
            rawRef.startsWith("content://", ignoreCase = true)
        val isB2Path = B2Config.isB2Path(rawRef)
        val isGsUri = rawRef.startsWith("gs://", ignoreCase = true)
        val isLegacyFirebaseStoragePath = !isB2Path && !rawRef.contains("://") && !rawRef.startsWith("/")

        when {
            isLocalUri -> {
                val parsedUri = android.net.Uri.parse(rawRef)
                context.contentResolver.openInputStream(parsedUri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                } ?: return@withContext Result.failure(
                    IllegalStateException("স্থানীয় পিডিএফ ফাইল পড়া যায়নি।")
                )
            }

            isB2Path -> {
                // Request short-lived presigned download ticket from Cloudflare Worker
                val ticketResult = B2StorageClient.requestReadTicket(
                    context = context,
                    storagePath = rawRef
                )
                val presignedDownloadUrl = ticketResult.getOrElse { err ->
                    return@withContext Result.failure(
                        IllegalStateException("বইটি খোলার অনুমতি মেলেনি: ${err.localizedMessage}")
                    )
                }

                val req = Request.Builder().url(presignedDownloadUrl).build()
                sharedPdfHttpClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("Backblaze B2 থেকে বই লোড করা যায়নি (HTTP ${response.code})।")
                        )
                    }
                    val body = response.body ?: return@withContext Result.failure(
                        IllegalStateException("পিডিএফ ফাইল খালি পাওয়া গেছে।")
                    )
                    val totalBytes = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalBytes > 0L) {
                                    onProgress((downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                            output.flush()
                        }
                    }
                }
            }

            isGsUri || isLegacyFirebaseStoragePath -> {
                // Legacy Authenticated Firebase Storage binary download
                val storage = com.example.firebase.FirebaseProvider.getStorage(context)
                val storageRef = if (isGsUri) {
                    storage.getReferenceFromUrl(rawRef)
                } else {
                    storage.reference.child(rawRef)
                }
                val downloadTask = storageRef.getFile(tempFile)
                downloadTask.addOnProgressListener { snap ->
                    val total = snap.totalByteCount
                    if (total > 0L) {
                        onProgress((snap.bytesTransferred.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
                downloadTask.await()
            }

            else -> {
                val resolvedUrl = if (rawRef.contains("drive.google.com", ignoreCase = true)) {
                    normalizeGoogleDrivePdfUrl(rawRef)
                } else {
                    resolveFirebaseStorageUrl(rawRef)
                }
                val reqBuilder = Request.Builder().url(resolvedUrl)
                if (resolvedUrl.contains("firebasestorage.googleapis.com") && !resolvedUrl.contains("token=")) {
                    val token = try {
                        com.example.firebase.FirebaseProvider.getAuth(context).currentUser?.getIdToken(false)?.await()?.token
                    } catch (_: Exception) {
                        null
                    }
                    if (!token.isNullOrBlank()) {
                        reqBuilder.addHeader("Authorization", "Firebase $token")
                    }
                }
                val req = reqBuilder.build()
                sharedPdfHttpClient.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            IllegalStateException("সার্ভার থেকে পিডিএফ লোড করা যায়নি (HTTP ${response.code})।")
                        )
                    }
                    val body = response.body ?: return@withContext Result.failure(
                        IllegalStateException("পিডিএফ ফাইল খালি পাওয়া গেছে।")
                    )
                    val totalBytes = body.contentLength()
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var downloaded = 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloaded += read
                                if (totalBytes > 0L) {
                                    onProgress((downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                            output.flush()
                        }
                    }
                }
            }
        }

        if (!hasValidPdfHeader(tempFile)) {
            tempFile.delete()
            return@withContext Result.failure(
                IllegalStateException("ফাইলটি বৈধ আসল PDF নয় (%PDF- স্বাক্ষর অনুপস্থিত)।")
            )
        }
        tempFile.renameTo(targetFile)
        onProgress(1f)
        Result.success(targetFile)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun hasValidPdfHeader(file: File): Boolean {
    if (!file.exists() || file.length() < 5L) return false
    return try {
        val header = ByteArray(5)
        file.inputStream().use { it.read(header) }
        String(header, Charsets.US_ASCII).startsWith("%PDF-")
    } catch (_: Exception) {
        false
    }
}

@Composable
fun PdfReaderScreen(
    contentId: String,
    contentType: String,
    titleBn: String,
    subjectNameBn: String,
    classNumber: Int,
    stream: String,
    coverUrl: String,
    pdfUrl: String,
    isPremium: Boolean,
    itemAllowsDownload: Boolean,
    tableOfContents: List<String>,
    initialPageOneBased: Int,
    savedLastPageOneBased: Int,
    bookmarks: List<Bookmark>,
    appSettings: AppSettings,
    onBack: () -> Unit,
    onSaveProgress: (lastPage: Int, totalPages: Int) -> Unit,
    onToggleWholeBookBookmark: () -> Unit,
    onTogglePageBookmark: (pageOneBased: Int) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reloadTrigger by remember { mutableIntStateOf(0) }
    var loadState by remember { mutableStateOf<PdfLoadState>(PdfLoadState.Loading(0f)) }

    var currentPageIndex by remember { mutableIntStateOf(0) } // 0-based
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isRenderingPage by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showTocDialog by remember { mutableStateOf(false) }
    var showJumpPageDialog by remember { mutableStateOf(false) }
    var jumpInput by remember { mutableStateOf("") }
    var tocSearchQuery by remember { mutableStateOf("") }
    val audioController = remember(context) { PdfPageAudioController(context) }
    val audioState by audioController.state.collectAsStateWithLifecycle()

    val rendererHolder = remember { mutableStateOf<Pair<ParcelFileDescriptor, PdfRenderer>?>(null) }
    val renderMutex = remember { Mutex() }

    val canDownload = appSettings.canDownloadContent(contentType, itemAllowsDownload)
    var cachedPdfFile by remember { mutableStateOf<File?>(null) }

    // Authorized Save-to-Device Launcher (strictly gated by Super Admin Download Control)
    val exportPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri ->
        val src = cachedPdfFile
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
                        onShowMessage(if (isBn) "সংরক্ষণ ব্যর্থ: ${e.localizedMessage}" else "Download failed: ${e.localizedMessage}")
                    }
                }
            }
        }
    }

    // Stop audio immediately whenever the user switches to a different page
    LaunchedEffect(currentPageIndex) {
        audioController.onPageChanged()
    }

    DisposableEffect(context, audioController) {
        onDispose {
            audioController.release()
            try {
                rendererHolder.value?.second?.close()
                rendererHolder.value?.first?.close()
            } catch (_: Exception) {
            }
            rendererHolder.value = null
        }
    }

    // Download & open native PdfRenderer — NEVER displays a fake page if loading fails!
    LaunchedEffect(pdfUrl, reloadTrigger) {
        loadState = PdfLoadState.Loading(0f)
        pageBitmap = null
        try {
            rendererHolder.value?.second?.close()
            rendererHolder.value?.first?.close()
        } catch (_: Exception) {
        }
        rendererHolder.value = null

        val fetched = fetchAndValidatePdf(context, pdfUrl) { prog ->
            loadState = PdfLoadState.Loading(prog)
        }
        fetched.onSuccess { file ->
            try {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                if (renderer.pageCount <= 0) {
                    renderer.close()
                    pfd.close()
                    loadState = PdfLoadState.Failed(
                        if (isBn) "পিডিএফ ফাইলটিতে কোনো পৃষ্ঠা পাওয়া যায়নি।" else "PDF contains zero pages."
                    )
                } else {
                    rendererHolder.value = pfd to renderer
                    cachedPdfFile = file
                    val startOneBased = when {
                        initialPageOneBased > 0 -> initialPageOneBased
                        savedLastPageOneBased > 0 -> savedLastPageOneBased
                        else -> 1
                    }.coerceIn(1, renderer.pageCount)
                    currentPageIndex = startOneBased - 1
                    loadState = PdfLoadState.Ready(file, renderer.pageCount)
                }
            } catch (e: Exception) {
                loadState = PdfLoadState.Failed(
                    if (isBn) "অবৈধ বা ক্ষতিগ্রস্ত পিডিএফ ফাইল: ${e.localizedMessage}"
                    else "Invalid or corrupted PDF file: ${e.localizedMessage}"
                )
            }
        }.onFailure { err ->
            loadState = PdfLoadState.Failed(
                err.localizedMessage ?: if (isBn) "নেটওয়ার্ক ত্রুটির কারণে পিডিএফ লোড হয়নি।" else "Failed to load PDF."
            )
        }
    }

    // Render current page bitmap & auto-save reading progress ONLY when PDF is truly loaded
    LaunchedEffect(loadState, currentPageIndex) {
        val ready = loadState as? PdfLoadState.Ready ?: return@LaunchedEffect
        val pair = rendererHolder.value ?: return@LaunchedEffect
        isRenderingPage = true
        val bmp = withContext(Dispatchers.IO) {
            renderMutex.withLock {
                try {
                    pair.second.openPage(currentPageIndex).use { page ->
                        val targetWidth = (page.width * 2).coerceIn(720, 2048)
                        val scaleFactor = targetWidth.toFloat() / page.width.coerceAtLeast(1).toFloat()
                        val targetHeight = (page.height * scaleFactor).toInt().coerceIn(720, 2800)
                        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(AndroidColor.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
        pageBitmap = bmp
        isRenderingPage = false
        if (bmp != null) {
            onSaveProgress(currentPageIndex + 1, ready.pageCount)
        }
    }

    val isWholeBookmarked = remember(bookmarks, contentId, contentType) {
        bookmarks.any { it.bookmarkType == contentType && it.targetId == contentId }
    }
    val currentOneBased = currentPageIndex + 1
    val isCurrentPageBookmarked = remember(bookmarks, contentId, currentOneBased) {
        bookmarks.any {
            it.bookmarkType == "PAGE" && it.targetId == contentId && it.pageNumber == currentOneBased
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color(0xFF0A121E) else MaterialTheme.colorScheme.background)
            .testTag("pdf_reader_screen")
    ) {
        if (!isFullscreen) {
            NavyScreenTopBar(
                title = titleBn,
                subtitle = subjectNameBn,
                onBack = onBack,
                actions = {
                    if (tableOfContents.isNotEmpty()) {
                        IconButton(onClick = { showTocDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of Contents", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showJumpPageDialog = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "Go to Page", tint = Color.White)
                    }
                    IconButton(
                        onClick = {
                            audioController.playOrPauseToggle(
                                pdfFile = cachedPdfFile,
                                pageIndex = currentPageIndex,
                                pageBitmap = pageBitmap,
                                isBn = isBn,
                                scope = scope
                            )
                        },
                        modifier = Modifier.testTag("pdf_top_audio_button")
                    ) {
                        val isAudioPlaying = audioState.phase == PageAudioPhase.PLAYING
                        val isAudioPaused = audioState.phase == PageAudioPhase.PAUSED
                        Icon(
                            imageVector = when {
                                isAudioPlaying -> Icons.Filled.Pause
                                isAudioPaused -> Icons.Filled.PlayArrow
                                else -> Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = if (isBn) "বর্তমান পৃষ্ঠা অডিও শুনুন" else "Read current page aloud",
                            tint = if (isAudioPlaying || isAudioPaused) EmeraldPrimary else Color.White
                        )
                    }
                    IconButton(onClick = onToggleWholeBookBookmark) {
                        Icon(
                            imageVector = if (isWholeBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Bookmark Whole Book",
                            tint = if (isWholeBookmarked) EmeraldPrimary else Color.White
                        )
                    }
                    // Strictly hide student download control when downloads are disabled in Super Admin Panel
                    if (canDownload && loadState is PdfLoadState.Ready) {
                        IconButton(
                            onClick = {
                                val cleanTitle = titleBn.replace(Regex("[^\\p{L}\\p{N}_\\- ]"), "").trim().ifBlank { "book" }
                                exportPdfLauncher.launch("$cleanTitle.pdf")
                            },
                            modifier = Modifier.testTag("pdf_download_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Download PDF", tint = EmeraldPrimary)
                        }
                    }
                }
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            when (val st = loadState) {
                is PdfLoadState.Loading -> {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = if (isBn) "আসল পাঠ্যবইয়ের পিডিএফ লোড হচ্ছে..." else "Loading authorized PDF...",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))
                            if (st.progress > 0f) {
                                LinearProgressIndicator(
                                    progress = { st.progress },
                                    modifier = Modifier.width(200.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "${(st.progress * 100).toInt().formatLocalized(lang)}%",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                is PdfLoadState.Failed -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        EmptyOrErrorStateCard(
                            title = if (isBn) "পিডিএফ খোলা সম্ভব হয়নি" else "Unable to Open PDF",
                            body = st.errorMessage,
                            isError = true,
                            onRetry = { reloadTrigger++ }
                        )
                        if (isGoogleDriveOrWebUrl(pdfUrl)) {
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    runCatching {
                                        val targetUrl = extractGoogleDriveViewUrl(pdfUrl).ifBlank { pdfUrl }
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                        context.startActivity(intent)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (isBn) "গুগল ড্রাইভ / ব্রাউজারে সরাসরি খুলুন" else "Open in Google Drive / Browser")
                            }
                        }
                    }
                }

                is PdfLoadState.Ready -> {
                    val bmp = pageBitmap
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentPageIndex) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (zoomScale * zoom).coerceIn(1f, 4f)
                                    zoomScale = newScale
                                    panOffset = if (newScale <= 1.02f) {
                                        Offset.Zero
                                    } else {
                                        Offset(
                                            x = (panOffset.x + pan.x).coerceIn(-900f, 900f),
                                            y = (panOffset.y + pan.y).coerceIn(-1200f, 1200f)
                                        )
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "PDF Page $currentOneBased",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = if (isFullscreen) 2.dp else 8.dp, vertical = 6.dp)
                                    .graphicsLayer(
                                        scaleX = zoomScale,
                                        scaleY = zoomScale,
                                        translationX = panOffset.x,
                                        translationY = panOffset.y
                                    )
                            )
                        }
                        if (isRenderingPage) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }

                        if (isFullscreen) {
                            Surface(
                                color = NavyDeep.copy(alpha = 0.78f),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(16.dp)
                            ) {
                                IconButton(onClick = { isFullscreen = false }) {
                                    Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit Fullscreen", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Reader Control Bar (Shown when PDF is Ready)
        val readyState = loadState as? PdfLoadState.Ready
        if (readyState != null) {
            Surface(
                color = NavyPrimary,
                contentColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                    // Compact docked Page-Wise Audio Reader bar
                    if (!isFullscreen || audioState.phase != PageAudioPhase.IDLE) {
                        Surface(
                            color = if (audioState.phase == PageAudioPhase.ERROR) {
                                Color(0xFF4C1D24)
                            } else {
                                Color.White.copy(alpha = 0.10f)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .testTag("pdf_page_audio_bar")
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (audioState.phase == PageAudioPhase.EXTRACTING) {
                                            CircularProgressIndicator(
                                                color = EmeraldPrimary,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                                contentDescription = null,
                                                tint = if (audioState.phase == PageAudioPhase.PLAYING) EmeraldPrimary else Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            val statusTitle = when (audioState.phase) {
                                                PageAudioPhase.IDLE ->
                                                    if (isBn) "পৃষ্ঠা অডিও (পৃষ্ঠা ${currentOneBased.formatLocalized(lang)})" else "Page Audio (Page $currentOneBased)"
                                                PageAudioPhase.EXTRACTING ->
                                                    if (isBn) "পৃষ্ঠার লেখা পড়া হচ্ছে..." else "Extracting page text..."
                                                PageAudioPhase.PLAYING ->
                                                    if (isBn) "পৃষ্ঠা পড়া চলছে" else "Reading page aloud"
                                                PageAudioPhase.PAUSED ->
                                                    if (isBn) "বিরতি দেওয়া হয়েছে" else "Paused"
                                                PageAudioPhase.ERROR ->
                                                    if (isBn) "অডিও পাঠ করা যায়নি" else "Audio unavailable"
                                            }
                                            Text(
                                                text = statusTitle,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                maxLines = 1
                                            )
                                            val langLabel = when (audioState.activeLanguage) {
                                                SpeechLanguage.BENGALI -> if (isBn) "বাংলা" else "Bengali"
                                                SpeechLanguage.ENGLISH -> if (isBn) "ইংরেজি" else "English"
                                                null -> null
                                            }
                                            if (langLabel != null && audioState.totalChunks > 0 &&
                                                (audioState.phase == PageAudioPhase.PLAYING || audioState.phase == PageAudioPhase.PAUSED)
                                            ) {
                                                Text(
                                                    text = if (isBn) {
                                                        "$langLabel • অংশ ${(audioState.currentChunkIndex + 1).formatLocalized(lang)}/${audioState.totalChunks.formatLocalized(lang)}"
                                                    } else {
                                                        "$langLabel • Part ${audioState.currentChunkIndex + 1}/${audioState.totalChunks}"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.White.copy(alpha = 0.78f),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                audioController.playOrPauseToggle(
                                                    pdfFile = cachedPdfFile,
                                                    pageIndex = currentPageIndex,
                                                    pageBitmap = pageBitmap,
                                                    isBn = isBn,
                                                    scope = scope
                                                )
                                            },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("pdf_audio_play_pause_btn")
                                        ) {
                                            val isPlaying = audioState.phase == PageAudioPhase.PLAYING
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                                contentDescription = when (audioState.phase) {
                                                    PageAudioPhase.PLAYING -> if (isBn) "বিরতি দিন" else "Pause"
                                                    PageAudioPhase.PAUSED -> if (isBn) "আবার শুনুন" else "Resume"
                                                    else -> if (isBn) "পৃষ্ঠাটি শুনুন" else "Play current page"
                                                },
                                                tint = EmeraldPrimary
                                            )
                                        }
                                        if (audioState.phase == PageAudioPhase.PLAYING ||
                                            audioState.phase == PageAudioPhase.PAUSED ||
                                            audioState.phase == PageAudioPhase.EXTRACTING
                                        ) {
                                            IconButton(
                                                onClick = { audioController.stop() },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("pdf_audio_stop_btn")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.StopCircle,
                                                    contentDescription = if (isBn) "অডিও বন্ধ করুন" else "Stop audio",
                                                    tint = Color.White
                                                )
                                            }
                                        } else if (audioState.phase == PageAudioPhase.ERROR) {
                                            IconButton(
                                                onClick = { audioController.dismissError() },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = if (isBn) "বন্ধ করুন" else "Dismiss",
                                                    tint = Color.White
                                                )
                                            }
                                        }
                                    }
                                }

                                val audioErr = audioState.errorMessage
                                if (audioState.phase == PageAudioPhase.ERROR && !audioErr.isNullOrBlank()) {
                                    Text(
                                        text = audioErr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFFD6D9),
                                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!isFullscreen && readyState.pageCount > 1) {
                        Slider(
                            value = currentPageIndex.toFloat(),
                            onValueChange = {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                                currentPageIndex = it.toInt().coerceIn(0, readyState.pageCount - 1)
                            },
                            valueRange = 0f..(readyState.pageCount - 1).coerceAtLeast(1).toFloat(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                        )
                    }
                    // Secondary utility tools row (Zoom, Fit, Bookmark, Fullscreen)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f)
                                    if (zoomScale == 1f) panOffset = Offset.Zero
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out", tint = Color.White.copy(alpha = 0.85f))
                            }
                            IconButton(
                                onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(4f) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In", tint = Color.White.copy(alpha = 0.85f))
                            }
                            IconButton(
                                onClick = {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.FitScreen, contentDescription = "Fit to Width", tint = Color.White.copy(alpha = 0.85f))
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onTogglePageBookmark(currentOneBased) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("pdf_bookmark_page_btn")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentPageBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                    contentDescription = "Bookmark This Page",
                                    tint = if (isCurrentPageBookmarked) EmeraldPrimary else Color.White
                                )
                            }
                            IconButton(
                                onClick = { isFullscreen = !isFullscreen },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Prominent Bottom Navigation Row: [Previous] on Left, Page count Center, [Next] on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Previous Page Button
                        Button(
                            onClick = {
                                if (currentPageIndex > 0) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                    currentPageIndex--
                                }
                            },
                            enabled = currentPageIndex > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0F3A75),
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("pdf_prev_page_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Previous",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Center Page Count (Clickable to jump)
                        Surface(
                            color = Color.White.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.clickable { showJumpPageDialog = true }
                        ) {
                            Text(
                                text = "${currentOneBased.formatLocalized(lang)} / ${readyState.pageCount.formatLocalized(lang)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }

                        // Next Page Button
                        Button(
                            onClick = {
                                if (currentPageIndex < readyState.pageCount - 1) {
                                    zoomScale = 1f
                                    panOffset = Offset.Zero
                                    currentPageIndex++
                                }
                            },
                            enabled = currentPageIndex < readyState.pageCount - 1,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldDark,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                contentColor = Color.White,
                                disabledContentColor = Color.White.copy(alpha = 0.35f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .testTag("pdf_next_page_btn")
                        ) {
                            Text(
                                text = "Next",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Jump to Page / Search Dialog
    if (showJumpPageDialog) {
        val total = (loadState as? PdfLoadState.Ready)?.pageCount ?: 1
        AlertDialog(
            onDismissRequest = { showJumpPageDialog = false },
            title = { Text(if (isBn) "পৃষ্ঠা বা অধ্যায় খুঁজুন" else "Go to Page / Search", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = jumpInput,
                        onValueChange = { jumpInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text(if (isBn) "পৃষ্ঠা নম্বর (১ - ${total.formatLocalized(lang)})" else "Page Number (1 - $total)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tableOfContents.isNotEmpty()) {
                        OutlinedTextField(
                            value = tocSearchQuery,
                            onValueChange = { tocSearchQuery = it },
                            label = { Text(if (isBn) "অধ্যায়ের নাম খুঁজুন" else "Search Chapter") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val matchingToc = tableOfContents.filter {
                            tocSearchQuery.isBlank() || it.contains(tocSearchQuery, ignoreCase = true)
                        }
                        LazyColumn(modifier = Modifier.height(140.dp)) {
                            items(matchingToc) { entry ->
                                val chName = entry.substringBefore("|")
                                val pg = entry.substringAfter("|", "1").toIntOrNull() ?: 1
                                Text(
                                    text = "$chName — ${pg.formatLocalized(lang)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentPageIndex = (pg - 1).coerceIn(0, total - 1)
                                            showJumpPageDialog = false
                                        }
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = jumpInput.toIntOrNull()
                        if (parsed != null) {
                            currentPageIndex = (parsed - 1).coerceIn(0, total - 1)
                        }
                        showJumpPageDialog = false
                    }
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpPageDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Table of Contents Modal
    if (showTocDialog) {
        val total = (loadState as? PdfLoadState.Ready)?.pageCount ?: 1
        AlertDialog(
            onDismissRequest = { showTocDialog = false },
            title = { Text(if (isBn) "সূচিপত্র" else "Table of Contents", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(tableOfContents) { entry ->
                        val title = entry.substringBefore("|")
                        val pg = entry.substringAfter("|", "1").toIntOrNull() ?: 1
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    currentPageIndex = (pg - 1).coerceIn(0, total - 1)
                                    showTocDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                                Text(
                                    "${strings.pagesLabel} ${pg.formatLocalized(lang)}",
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTocDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }
}
