package com.example.admin

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.components.BookCoverArt
import com.example.data.DirectUploadDraft
import com.example.data.DirectUploadState
import com.example.data.LocalUploadValidator
import com.example.data.SelectedCoverInfo
import com.example.data.SelectedPdfInfo
import com.example.data.UploadCategory
import com.example.model.ContentClassGroupHelper
import com.example.model.DefaultCatalogSeed
import com.example.model.GuideNote
import com.example.model.SchoolClass
import com.example.model.Subject
import com.example.model.Textbook
import com.example.model.sortedBySerialOrder
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.RoyalBlue
import kotlinx.coroutines.launch

/**
 * Super Admin Direct Book & Cover Upload Screen.
 *
 * Implements native file pickers (`Upload Book Cover` + `Upload Complete Book PDF`) with zero URL inputs,
 * strict binary validation, preview thumbnail, real-time Firebase Storage progress, retry support,
 * and atomic metadata publication into `books` or `guides`.
 */
@Composable
fun AdminBookUploadScreen(
    initialCategory: UploadCategory,
    editingBook: Textbook? = null,
    editingGuide: GuideNote? = null,
    initialSelectedClasses: List<Int> = emptyList(),
    editingSiblingIds: List<String> = emptyList(),
    subjects: List<Subject>,
    availableClasses: List<SchoolClass> = DefaultCatalogSeed.defaultClasses,
    uploadState: DirectUploadState,
    onResetUploadState: () -> Unit,
    onSubmitUpload: (
        draft: DirectUploadDraft,
        selectedCover: SelectedCoverInfo?,
        selectedPdf: SelectedPdfInfo?
    ) -> Unit,
    onBack: () -> Unit,
    onGoAdminHome: (() -> Unit)? = null
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current
    val isBn = strings.language == AppLanguage.BN

    val isEditing = editingBook != null || editingGuide != null
    val editSessionKey = editingBook?.id ?: editingGuide?.id ?: "new_${initialCategory.name}"

    var categoryName by rememberSaveable(editSessionKey) {
        mutableStateOf(
            when {
                editingBook != null -> UploadCategory.ORIGINAL_TEXTBOOKS.name
                editingGuide != null -> UploadCategory.GUIDES_AND_NOTES.name
                else -> initialCategory.name
            }
        )
    }
    val category = runCatching { UploadCategory.valueOf(categoryName) }.getOrDefault(initialCategory)

    val initialClassesResolved = remember(editSessionKey, initialSelectedClasses, editingBook, editingGuide) {
        when {
            initialSelectedClasses.isNotEmpty() ->
                initialSelectedClasses.distinct().sorted().take(ContentClassGroupHelper.MAX_MULTI_CLASS_SELECTION)
            editingBook != null -> listOf(editingBook.classNumber)
            editingGuide != null -> listOf(editingGuide.classNumber)
            else -> listOf(5)
        }
    }
    var selectedClasses by rememberSaveable(editSessionKey, stateSaver = IntListSaver) {
        mutableStateOf(initialClassesResolved)
    }
    val classNumber = selectedClasses.firstOrNull() ?: (editingBook?.classNumber ?: editingGuide?.classNumber ?: 5)

    var stream by rememberSaveable(editSessionKey) {
        mutableStateOf(editingBook?.stream ?: editingGuide?.stream ?: "ALL")
    }

    val sortedClasses = remember(availableClasses) {
        (availableClasses.ifEmpty { DefaultCatalogSeed.defaultClasses }).sortedBySerialOrder()
    }
    val streamCapableClasses = remember(sortedClasses) {
        sortedClasses.filter { it.hasStreams || it.classNumber in 9..10 }.map { it.classNumber }.toSet() + setOf(9, 10)
    }
    val hasStreamsForClass = remember(selectedClasses, streamCapableClasses) {
        selectedClasses.any { it in streamCapableClasses }
    }

    val matchingSubjects = remember(subjects, selectedClasses, classNumber, stream, streamCapableClasses) {
        val lookupClasses = selectedClasses.ifEmpty { listOf(classNumber) }
        val nctbSubs = lookupClasses.flatMap { c ->
            val effStream = if (c in streamCapableClasses) stream else "ALL"
            com.example.model.NctbCurriculum.getSubjects(c, effStream)
        }
        val repoSubs = subjects.filter { it.classNumber in lookupClasses }.map { it.nameBn }
        (nctbSubs + repoSubs).distinct().filter { it.isNotBlank() }
    }

    var subjectNameBn by rememberSaveable(editSessionKey) {
        mutableStateOf(
            editingBook?.subjectNameBn?.takeIf { it.isNotBlank() }
                ?: editingGuide?.subjectNameBn?.takeIf { it.isNotBlank() }
                ?: matchingSubjects.firstOrNull().orEmpty()
        )
    }

    val availableBookTitles = remember(selectedClasses, classNumber, stream, subjectNameBn, streamCapableClasses) {
        if (subjectNameBn.isNotBlank()) {
            val lookupClasses = selectedClasses.ifEmpty { listOf(classNumber) }
            lookupClasses.flatMap { c ->
                val effStream = if (c in streamCapableClasses) stream else "ALL"
                com.example.model.NctbCurriculum.getBooks(c, effStream, subjectNameBn)
            }.distinct()
        } else {
            emptyList()
        }
    }

    var titleBn by rememberSaveable(editSessionKey) {
        mutableStateOf(
            editingBook?.titleBn?.takeIf { it.isNotBlank() }
                ?: editingGuide?.titleBn?.takeIf { it.isNotBlank() }
                ?: availableBookTitles.firstOrNull().orEmpty()
        )
    }
    var titleEn by rememberSaveable(editSessionKey) {
        mutableStateOf(editingBook?.titleEn ?: editingGuide?.titleEn ?: "")
    }
    var author by rememberSaveable(editSessionKey) {
        mutableStateOf(
            editingBook?.author?.takeIf { it.isNotBlank() }
                ?: editingGuide?.author?.takeIf { it.isNotBlank() }
                ?: "NCTB"
        )
    }
    var publisher by rememberSaveable(editSessionKey) {
        mutableStateOf(
            editingBook?.publisher?.takeIf { it.isNotBlank() }
                ?: editingGuide?.publisher?.takeIf { it.isNotBlank() }
                ?: "জাতীয় শিক্ষাক্রম ও পাঠ্যপুস্তক বোর্ড (NCTB)"
        )
    }
    var edition by rememberSaveable(editSessionKey) {
        mutableStateOf(
            editingBook?.edition?.takeIf { it.isNotBlank() }
                ?: editingGuide?.edition?.takeIf { it.isNotBlank() }
                ?: "NCTB 2026"
        )
    }
    var academicYear by rememberSaveable(editSessionKey) {
        mutableStateOf(
            editingBook?.academicYear?.takeIf { it.isNotBlank() }
                ?: editingGuide?.academicYear?.takeIf { it.isNotBlank() }
                ?: "2026"
        )
    }
    var bookLanguage by rememberSaveable(editSessionKey) {
        mutableStateOf(
            editingBook?.language?.takeIf { it.isNotBlank() }
                ?: editingGuide?.language?.takeIf { it.isNotBlank() }
                ?: "BN"
        )
    }
    var chapter by rememberSaveable(editSessionKey) {
        mutableStateOf(editingGuide?.chapter?.ifBlank { "সম্পূর্ণ বই" } ?: "সম্পূর্ণ বই")
    }
    var guideType by rememberSaveable(editSessionKey) {
        mutableStateOf(
            editingGuide?.guideType?.takeIf { it.isNotBlank() && it != "FULL_GUIDE" }
                ?: "COMPLETE_GUIDE"
        )
    }
    var shortDesc by rememberSaveable(editSessionKey) {
        mutableStateOf(editingBook?.shortDescription ?: editingGuide?.shortDescription ?: "")
    }
    var fullDesc by rememberSaveable(editSessionKey) {
        mutableStateOf(editingBook?.description ?: editingGuide?.summaryContent ?: "")
    }
    var tocRaw by rememberSaveable(editSessionKey) {
        mutableStateOf(
            (editingBook?.tableOfContents ?: editingGuide?.tableOfContents).orEmpty().joinToString("\n")
        )
    }
    var isPremium by rememberSaveable(editSessionKey) {
        mutableStateOf(editingBook?.isPremium ?: editingGuide?.isPremium ?: false)
    }
    var allowDownload by rememberSaveable(editSessionKey) {
        mutableStateOf(editingBook?.allowDownload ?: editingGuide?.allowDownload ?: false)
    }
    var isPublished by rememberSaveable(editSessionKey) {
        mutableStateOf(editingBook?.isPublished ?: editingGuide?.isPublished ?: true)
    }
    var isPopular by rememberSaveable(editSessionKey) {
        mutableStateOf(editingBook?.isPopular ?: false)
    }

    val existingCoverPath = editingBook?.effectiveCoverRef ?: editingGuide?.effectiveCoverRef ?: ""
    val existingPdfPath = editingBook?.effectivePdfRef ?: editingGuide?.effectivePdfRef ?: ""
    val existingCoverName = editingBook?.coverFileName ?: editingGuide?.coverFileName ?: ""
    val existingCoverSize = editingBook?.coverFileSizeBytes ?: editingGuide?.coverFileSizeBytes ?: 0L
    val existingPdfName = editingBook?.pdfFileName ?: editingGuide?.pdfFileName ?: ""
    val existingPdfSize = editingBook?.pdfFileSizeBytes ?: editingGuide?.pdfFileSizeBytes ?: 0L
    val existingPageCount = editingBook?.pageCount ?: editingGuide?.pageCount ?: 100

    var selectedCover by rememberSaveable(editSessionKey, stateSaver = SelectedCoverInfoSaver) {
        mutableStateOf<SelectedCoverInfo?>(null)
    }
    var selectedPdf by rememberSaveable(editSessionKey, stateSaver = SelectedPdfInfoSaver) {
        mutableStateOf<SelectedPdfInfo?>(null)
    }
    var coverValidationError by rememberSaveable(editSessionKey) { mutableStateOf<String?>(null) }
    var pdfValidationError by rememberSaveable(editSessionKey) { mutableStateOf<String?>(null) }
    var formValidationError by rememberSaveable(editSessionKey) { mutableStateOf<String?>(null) }
    var isValidatingCover by remember { mutableStateOf(false) }
    var isValidatingPdf by remember { mutableStateOf(false) }

    // Native Android System File / Gallery Picker for Book Cover (JPG, JPEG, PNG, WEBP)
    val coverPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            isValidatingCover = true
            coverValidationError = null
            formValidationError = null
            scope.launch {
                val res = LocalUploadValidator.validateSelectedCover(context, uri)
                isValidatingCover = false
                res.onSuccess { info ->
                    selectedCover = info
                    coverValidationError = null
                }.onFailure { err ->
                    coverValidationError = err.localizedMessage ?: "বৈধ কভার ছবি নির্বাচন করুন।"
                }
            }
        }
    }

    // Native Android System Document Picker strictly for Original PDF (`application/pdf`)
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            isValidatingPdf = true
            pdfValidationError = null
            formValidationError = null
            scope.launch {
                val res = LocalUploadValidator.validateSelectedPdf(context, uri)
                isValidatingPdf = false
                res.onSuccess { info ->
                    selectedPdf = info
                    pdfValidationError = null
                    if (titleBn.isBlank()) {
                        val inferred = info.displayName
                            .substringBeforeLast('.')
                            .replace('_', ' ')
                            .replace('-', ' ')
                            .trim()
                        if (inferred.isNotBlank()) {
                            titleBn = inferred
                        }
                    }
                }.onFailure { err ->
                    pdfValidationError = err.localizedMessage ?: "বৈধ PDF ফাইল নির্বাচন করুন।"
                }
            }
        }
    }

    val isUploading = uploadState is DirectUploadState.Uploading

    fun buildCurrentDraft(): DirectUploadDraft {
        val cleanClasses = selectedClasses.distinct().sorted().take(ContentClassGroupHelper.MAX_MULTI_CLASS_SELECTION)
        val primaryClass = cleanClasses.firstOrNull() ?: classNumber
        val matchingSubjectObj = subjects.firstOrNull {
            it.classNumber in cleanClasses && it.nameBn.equals(subjectNameBn.trim(), ignoreCase = true)
        }
        val safeTitle = titleBn.trim().ifBlank { subjectNameBn.trim() }
        val resolvedExistingSiblings = editingSiblingIds
            .ifEmpty { listOfNotNull(editingBook?.id ?: editingGuide?.id).filter { it.isNotBlank() } }
            .distinct()
        return DirectUploadDraft(
            existingId = editingBook?.id ?: editingGuide?.id ?: "",
            category = category,
            titleBn = safeTitle,
            titleEn = titleEn.trim().ifBlank { safeTitle },
            classNumber = primaryClass,
            stream = if (hasStreamsForClass) stream else "ALL",
            subjectId = matchingSubjectObj?.id.orEmpty(),
            subjectNameBn = subjectNameBn.trim(),
            subjectNameEn = matchingSubjectObj?.nameEn.orEmpty(),
            chapter = chapter.trim().ifBlank { if (category == UploadCategory.GUIDES_AND_NOTES) "সম্পূর্ণ গাইড" else "সম্পূর্ণ বই" },
            guideType = guideType.ifBlank { "COMPLETE_GUIDE" },
            author = author.trim().ifBlank { "NCTB" },
            publisher = publisher.trim().ifBlank { "জাতীয় শিক্ষাক্রম ও পাঠ্যপুস্তক বোর্ড (NCTB)" },
            edition = edition.trim().ifBlank { "NCTB 2026" },
            academicYear = academicYear.trim().ifBlank { "2026" },
            language = bookLanguage.ifBlank { "BN" },
            shortDescription = shortDesc.trim().ifBlank {
                "$safeTitle • শ্রেণি ${cleanClasses.joinToString(", ")}"
            },
            fullDescription = fullDesc.trim(),
            pageCount = selectedPdf?.detectedPageCount?.takeIf { it > 0 } ?: existingPageCount.coerceAtLeast(1),
            tableOfContents = tocRaw.lines().map { it.trim().take(200) }.filter { it.isNotBlank() }.take(60),
            isPremium = isPremium,
            isPublished = isPublished,
            allowDownload = allowDownload,
            isPopular = isPopular,
            existingCoverStoragePath = existingCoverPath,
            existingPdfStoragePath = existingPdfPath,
            existingCoverFileName = existingCoverName,
            existingCoverFileSizeBytes = existingCoverSize,
            existingPdfFileName = existingPdfName,
            existingPdfFileSizeBytes = existingPdfSize,
            selectedClasses = cleanClasses,
            existingSiblingIds = resolvedExistingSiblings,
            streamCapableClasses = streamCapableClasses
        )
    }

    fun validateAndTriggerUpload() {
        formValidationError = null
        val hasCover = selectedCover != null || existingCoverPath.isNotBlank()
        val hasPdf = selectedPdf != null || existingPdfPath.isNotBlank()

        when {
            selectedClasses.isEmpty() -> {
                formValidationError = if (isBn) {
                    "কমপক্ষে ১টি শ্রেণি নির্বাচন করুন (সর্বোচ্চ ২টি নির্বাচন করা যাবে)।"
                } else {
                    "Please select at least 1 class (maximum 2 classes allowed)."
                }
            }
            selectedClasses.size > ContentClassGroupHelper.MAX_MULTI_CLASS_SELECTION -> {
                formValidationError = if (isBn) {
                    "সর্বোচ্চ ২টি শ্রেণি নির্বাচন করা যাবে।"
                } else {
                    "Maximum 2 classes can be selected."
                }
            }
            !hasCover -> {
                formValidationError = if (isBn) {
                    "অনুগ্রহ করে বইয়ের কভার ছবি (JPG, PNG বা WEBP) নির্বাচন করুন।"
                } else {
                    "Please select a Book Cover image (JPG, PNG, or WEBP)."
                }
            }
            !hasPdf -> {
                formValidationError = if (isBn) {
                    "অনুগ্রহ করে সম্পূর্ণ বইয়ের আসল PDF ফাইল নির্বাচন করুন।"
                } else {
                    "Please select the Complete Book PDF file from your device."
                }
            }
            titleBn.isBlank() -> {
                formValidationError = if (isBn) {
                    "বইয়ের নাম (শিরোনাম) লিখুন।"
                } else {
                    "Please enter the Book Title."
                }
            }
            subjectNameBn.isBlank() -> {
                formValidationError = if (isBn) {
                    "বিষয়ের নাম নির্বাচন করুন বা লিখুন।"
                } else {
                    "Please select or enter the Subject."
                }
            }
            else -> {
                onSubmitUpload(buildCurrentDraft(), selectedCover, selectedPdf)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("admin_book_upload_screen")
    ) {
        // Royal Blue Top Header Bar matching Reference Image ("Add New Book" + Back + Home)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF06327D), Color(0xFF0A47A9), Color(0xFF1256C4))
                    )
                )
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        enabled = !isUploading,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("upload_screen_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (isEditing) "Edit Book / Guide" else "Add New Book",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    )
                }

                IconButton(
                    onClick = { (onGoAdminHome ?: onBack).invoke() },
                    enabled = !isUploading,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Admin Home",
                        tint = Color.White
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Intro Banner Card ("সম্পূর্ণ বই আপলোড করুন")
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = RoyalBlue.copy(alpha = 0.11f),
                    border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        StackedBooksArtwork(modifier = Modifier.size(54.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "সম্পূর্ণ বই আপলোড করুন",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "ফোনের Files থেকে আসল PDF ও কভার নির্বাচন করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // ==========================================================
                // STEP 1: BOOK COVER IMAGE UPLOAD (Gallery & Files)
                // ==========================================================
                StepSectionHeader(number = "1", title = "বইয়ের কভার ছবি")

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = RoyalBlue.copy(alpha = 0.06f)
                    ),
                    border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.18f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Cover Preview Thumbnail (0.72f portrait proportion, distortion-free Crop)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                shadowElevation = 3.dp,
                                border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.28f)),
                                modifier = Modifier
                                    .width(108.dp)
                                    .aspectRatio(0.72f)
                                    .testTag("selected_cover_preview")
                            ) {
                                val localCover = selectedCover
                                when {
                                    localCover != null -> {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(localCover.uri)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Selected Book Cover Preview",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    existingCoverPath.isNotBlank() -> {
                                        BookCoverArt(
                                            itemId = editingBook?.id ?: editingGuide?.id ?: "existing",
                                            rawCoverUrl = existingCoverPath,
                                            title = titleBn.ifBlank { "কভার প্রিভিউ" },
                                            subject = subjectNameBn,
                                            badgeLabel = edition,
                                            classNumber = classNumber,
                                            isGuide = category == UploadCategory.GUIDES_AND_NOTES,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    else -> {
                                        BookCoverArt(
                                            itemId = "draft_cover",
                                            rawCoverUrl = "",
                                            title = titleBn.ifBlank { "বইয়ের কভার প্রিভিউ" },
                                            subject = subjectNameBn.ifBlank { "Class $classNumber" },
                                            badgeLabel = "Cover Preview",
                                            classNumber = classNumber,
                                            isGuide = category == UploadCategory.GUIDES_AND_NOTES,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coverPickerLauncher.launch(arrayOf("image/*"))
                                    },
                                    enabled = !isUploading && !isValidatingCover,
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.5.dp, RoyalBlue.copy(alpha = 0.65f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = RoyalBlue
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 50.dp)
                                        .testTag("upload_book_cover_button")
                                ) {
                                    if (isValidatingCover) {
                                        CircularProgressIndicator(
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.Image,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Upload Book Cover",
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = "JPG, JPEG, PNG, WEBP",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )

                                if (selectedCover != null || existingCoverPath.isNotBlank()) {
                                    TextButton(
                                        onClick = {
                                            coverPickerLauncher.launch(arrayOf("image/*"))
                                        },
                                        enabled = !isUploading,
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Autorenew,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isBn) "কভার পরিবর্তন করুন" else "Replace Cover",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Cover file status / validation row
                        val coverInfo = selectedCover
                        when {
                            coverValidationError != null -> {
                                FileValidationBanner(
                                    isError = true,
                                    text = coverValidationError!!
                                )
                            }
                            coverInfo != null -> {
                                FileValidationBanner(
                                    isError = false,
                                    text = "${coverInfo.displayName} (${coverInfo.formattedSize})"
                                )
                            }
                            existingCoverPath.isNotBlank() -> {
                                val label = if (existingCoverName.isNotBlank()) {
                                    val sz = if (existingCoverSize > 0L) " (${LocalUploadValidator.formatFileSize(existingCoverSize)})" else ""
                                    "সংরক্ষিত কভার: $existingCoverName$sz"
                                } else {
                                    "সংরক্ষিত কভার ছবি যুক্ত রয়েছে"
                                }
                                FileValidationBanner(isError = false, text = label)
                            }
                        }
                    }
                }

                // ==========================================================
                // STEP 2: ORIGINAL COMPLETE BOOK PDF UPLOAD (application/pdf)
                // ==========================================================
                StepSectionHeader(number = "2", title = "সম্পূর্ণ বইয়ের PDF")

                val dashedBorderColor = Color(0xFF16A34A)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF16A34A).copy(alpha = 0.06f))
                        .drawBehind {
                            drawRoundRect(
                                color = dashedBorderColor,
                                style = Stroke(
                                    width = 1.8.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                ),
                                cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx())
                            )
                        }
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Red PDF Badge Icon matching reference design
                        Surface(
                            color = Color(0xFFEF2323),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(width = 56.dp, height = 68.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                                Text(
                                    text = "PDF",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                },
                                enabled = !isUploading && !isValidatingPdf,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFD1F2D9),
                                    contentColor = Color(0xFF0A4D23)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF16A34A).copy(alpha = 0.55f)),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 50.dp)
                                    .testTag("upload_complete_pdf_button")
                            ) {
                                if (isValidatingPdf) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF0A4D23),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    text = "Upload Complete Book PDF",
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Text(
                                text = "আপনার ডিভাইস থেকে PDF নির্বাচন করুন",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )

                            if (selectedPdf != null || existingPdfPath.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                    },
                                    enabled = !isUploading,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Autorenew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isBn) "অন্য PDF নির্বাচন করুন" else "Replace PDF",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Selected PDF validation / filename + size display
                val pdfInfo = selectedPdf
                when {
                    pdfValidationError != null -> {
                        FileValidationBanner(
                            isError = true,
                            text = pdfValidationError!!
                        )
                    }
                    pdfInfo != null -> {
                        val pageTag = if (pdfInfo.detectedPageCount > 0) " • ${pdfInfo.detectedPageCount} পৃষ্ঠা" else ""
                        FileValidationBanner(
                            isError = false,
                            text = "${pdfInfo.displayName} (${pdfInfo.formattedSize})$pageTag"
                        )
                    }
                    existingPdfPath.isNotBlank() -> {
                        val label = if (existingPdfName.isNotBlank()) {
                            val sz = if (existingPdfSize > 0L) " (${LocalUploadValidator.formatFileSize(existingPdfSize)})" else ""
                            "সংরক্ষিত আসল PDF: $existingPdfName$sz"
                        } else {
                            "সংরক্ষিত আসল PDF যুক্ত রয়েছে"
                        }
                        FileValidationBanner(isError = false, text = label)
                    }
                }

                // ==========================================================
                // BOOK INFORMATION FIELDS (NO URL FIELDS!)
                // ==========================================================
                // ==========================================================
                // শ্রেণি, বিভাগ, বিষয় ও বইয়ের নাম নির্বাচন (NCTB তালিকা)
                // ==========================================================
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "বইয়ের শ্রেণি, বিষয় ও নাম নির্বাচন",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // ১. শ্রেণি নির্বাচন (১টি বা সর্বোচ্চ ২টি শ্রেণি Multi-Select)
                        val classOptions = remember(sortedClasses, selectedClasses) {
                            val list = sortedClasses.map { it.displayNameBn to it.classNumber }.toMutableList()
                            selectedClasses.forEach { sel ->
                                if (list.none { it.second == sel }) {
                                    list.add("শ্রেণি $sel" to sel)
                                }
                            }
                            list.sortedBy { it.second }
                        }

                        fun updateSelectedClasses(nextList: List<Int>) {
                            val clean = nextList.distinct().sorted().take(ContentClassGroupHelper.MAX_MULTI_CLASS_SELECTION)
                            selectedClasses = clean
                            formValidationError = null
                            if (clean.isNotEmpty()) {
                                val nextHasStreams = clean.any { it in streamCapableClasses }
                                if (!nextHasStreams) stream = "ALL"
                                val newSubs = clean.flatMap { c ->
                                    val effStream = if (c in streamCapableClasses) stream else "ALL"
                                    com.example.model.NctbCurriculum.getSubjects(c, effStream) +
                                        subjects.filter { it.classNumber == c }.map { it.nameBn }
                                }.distinct().filter { it.isNotBlank() }
                                if (subjectNameBn.isBlank() || (newSubs.isNotEmpty() && subjectNameBn !in newSubs)) {
                                    val nextSub = newSubs.firstOrNull().orEmpty()
                                    subjectNameBn = nextSub
                                    if (!isEditing && titleBn.isBlank()) {
                                        val c0 = clean.first()
                                        val eff0 = if (c0 in streamCapableClasses) stream else "ALL"
                                        titleBn = com.example.model.NctbCurriculum.getBooks(c0, eff0, nextSub).firstOrNull().orEmpty()
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MultiClassDropdownFieldBox(
                                label = if (isBn) "শ্রেণি নির্বাচন (${selectedClasses.size}/২)" else "Select Class (${selectedClasses.size}/2)",
                                required = true,
                                selectedClasses = selectedClasses,
                                options = classOptions,
                                isBn = isBn,
                                onToggleClass = { toggledClass ->
                                    if (toggledClass in selectedClasses) {
                                        updateSelectedClasses(selectedClasses - toggledClass)
                                    } else if (selectedClasses.size < ContentClassGroupHelper.MAX_MULTI_CLASS_SELECTION) {
                                        updateSelectedClasses(selectedClasses + toggledClass)
                                    }
                                },
                                enabled = !isUploading,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("upload_multi_class_selector")
                            )

                            DropdownFieldBox(
                                label = "ধরন",
                                required = true,
                                selectedText = category.labelEn,
                                options = UploadCategory.entries.map { "${it.labelEn} (${it.labelBn})" to it },
                                onSelect = { chosen ->
                                    if (!isEditing) categoryName = chosen.name
                                },
                                enabled = !isEditing,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (selectedClasses.isNotEmpty()) {
                            SelectedClassChipsStrip(
                                selectedClasses = selectedClasses,
                                classOptions = classOptions,
                                enabled = !isUploading,
                                onRemoveClass = { clsToRemove ->
                                    updateSelectedClasses(selectedClasses - clsToRemove)
                                }
                            )
                        }

                        // ২. বিভাগ নির্বাচন (নবম ও দশম শ্রেণি বা বিভাগযুক্ত শ্রেণির জন্য)
                        if (hasStreamsForClass) {
                            val streamOptions = listOf(
                                "বিজ্ঞান বিভাগ (Science)" to "SCIENCE",
                                "মানবিক বিভাগ (Humanities)" to "HUMANITIES",
                                "ব্যবসায় শিক্ষা বিভাগ (Business Studies)" to "BUSINESS",
                                "আবশ্যিক / সকল বিভাগ (Common / All Streams)" to "ALL"
                            )
                            val currentStreamLabel = streamOptions.firstOrNull { it.second == stream }?.first ?: stream
                            DropdownFieldBox(
                                label = "বিভাগ নির্বাচন (Stream) *",
                                required = true,
                                selectedText = currentStreamLabel,
                                options = streamOptions,
                                onSelect = { chosenStream ->
                                    stream = chosenStream
                                    val newSubs = com.example.model.NctbCurriculum.getSubjects(classNumber, chosenStream)
                                    if (subjectNameBn !in newSubs) {
                                        subjectNameBn = newSubs.firstOrNull().orEmpty()
                                        titleBn = com.example.model.NctbCurriculum.getBooks(classNumber, chosenStream, subjectNameBn).firstOrNull().orEmpty()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // ৩. বিষয় নির্বাচন
                        DropdownFieldBox(
                            label = "বিষয় নির্বাচন *",
                            required = true,
                            selectedText = subjectNameBn.ifBlank { "বিষয় নির্বাচন করুন" },
                            options = matchingSubjects.map { it to it },
                            onSelect = { chosenSub ->
                                subjectNameBn = chosenSub
                                val newBooks = com.example.model.NctbCurriculum.getBooks(classNumber, if (hasStreamsForClass) stream else "ALL", chosenSub)
                                titleBn = newBooks.firstOrNull().orEmpty()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_book_subject_input")
                        )

                        // ৪. বইয়ের নাম নির্বাচন (NCTB তালিকা থেকে)
                        val bookOptions = availableBookTitles.map { it to it }
                        if (bookOptions.isNotEmpty()) {
                            DropdownFieldBox(
                                label = "বইয়ের নাম নির্বাচন (NCTB তালিকা) *",
                                required = true,
                                selectedText = titleBn.ifBlank { "বইয়ের নাম সিলেক্ট করুন" },
                                options = bookOptions,
                                onSelect = { chosenTitle ->
                                    titleBn = chosenTitle
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // বইয়ের নাম সম্পাদনা / কাস্টম নাম
                        OutlinedTextField(
                            value = titleBn,
                            onValueChange = { titleBn = it },
                            label = { Text("নির্বাচিত বইয়ের নাম / শিরোনাম *") },
                            placeholder = { Text("বইয়ের নাম লিখুন বা উপরের তালিকা থেকে নির্বাচন করুন") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_book_title_input")
                        )
                    }
                }

                // Inline Validation Alert
                formValidationError?.let { msg ->
                    FileValidationBanner(isError = true, text = msg)
                }

                // Real-Time Upload Progress / Success / Retry Status Card
                when (val st = uploadState) {
                    DirectUploadState.Idle -> Unit

                    is DirectUploadState.Uploading -> {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = RoyalBlue.copy(alpha = 0.10f)
                            ),
                            border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.30f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_progress_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBn) st.stageLabelBn else st.stageLabelEn,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = RoyalBlue,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${st.progressPercent}%",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmeraldDark
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (st.progressPercent / 100f).coerceIn(0f, 1f) },
                                    color = EmeraldDark,
                                    trackColor = RoyalBlue.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                                Text(
                                    text = "ফাইল: ${st.activeFileName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    is DirectUploadState.Succeeded -> {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = EmeraldPrimary.copy(alpha = 0.14f)
                            ),
                            border = BorderStroke(1.dp, EmeraldDark.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_success_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldDark,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isBn) st.messageBn else st.messageEn,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldDark
                                    )
                                }
                                Text(
                                    text = "PDF Path: ${st.pdfStoragePath}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = onBack,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EmeraldDark,
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(if (isBn) "বই তালিকায় ফিরুন" else "Back to Book List")
                                    }
                                }
                            }
                        }
                    }

                    is DirectUploadState.Failed -> {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = ErrorRed.copy(alpha = 0.10f)
                            ),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("upload_error_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = ErrorRed,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isBn) st.errorMessageBn else st.errorMessageEn,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ErrorRed,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (st.canRetry) {
                                    OutlinedButton(
                                        onClick = { validateAndTriggerUpload() },
                                        border = BorderStroke(1.dp, ErrorRed),
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .heightIn(min = 48.dp)
                                            .testTag("retry_book_upload_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Autorenew,
                                            contentDescription = null,
                                            tint = ErrorRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = if (isBn) "পুনরায় চেষ্টা করুন" else "Retry Upload",
                                            color = ErrorRed,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================================
                // PRIMARY GREEN UPLOAD CTA BUTTON ("আপলোড করুন")
                // ==========================================================
                Button(
                    onClick = { validateAndTriggerUpload() },
                    enabled = !isUploading && !isValidatingCover && !isValidatingPdf,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0A6638),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("submit_book_upload_button")
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "আপলোড চলছে...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "আপলোড করুন",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun StepSectionHeader(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = RoyalBlue,
            shape = CircleShape,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FileValidationBanner(
    isError: Boolean,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (isError) ErrorRed else Color(0xFF16A34A),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isError) ErrorRed else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun <T> DropdownFieldBox(
    label: String,
    required: Boolean,
    selectedText: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RoyalBlue.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.16f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    append(label)
                    if (required) {
                        withStyle(SpanStyle(color = ErrorRed, fontWeight = FontWeight.Bold)) {
                            append(" *")
                        }
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = enabled) { expanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { (display, item) ->
                        DropdownMenuItem(
                            text = { Text(display) },
                            onClick = {
                                onSelect(item)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MultiClassDropdownFieldBox(
    label: String,
    required: Boolean,
    selectedClasses: List<Int>,
    options: List<Pair<String, Int>>,
    isBn: Boolean,
    onToggleClass: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    val summaryText = when (selectedClasses.size) {
        0 -> if (isBn) "শ্রেণি নির্বাচন করুন" else "Select class"
        1 -> options.firstOrNull { it.second == selectedClasses[0] }?.first ?: "শ্রেণি ${selectedClasses[0]}"
        else -> {
            val firstLabel = options.firstOrNull { it.second == selectedClasses[0] }?.first ?: "শ্রেণি ${selectedClasses[0]}"
            val secondLabel = options.firstOrNull { it.second == selectedClasses[1] }?.first ?: "শ্রেণি ${selectedClasses[1]}"
            "$firstLabel, $secondLabel"
        }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RoyalBlue.copy(alpha = 0.06f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selectedClasses.isEmpty()) ErrorRed.copy(alpha = 0.55f) else RoyalBlue.copy(alpha = 0.16f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    append(label)
                    if (required) {
                        withStyle(SpanStyle(color = ErrorRed, fontWeight = FontWeight.Bold)) {
                            append(" *")
                        }
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (selectedClasses.isEmpty()) ErrorRed.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = enabled) { expanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedClasses.isEmpty()) ErrorRed else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    Text(
                        text = if (isBn) {
                            "১টি বা সর্বোচ্চ ২টি শ্রেণি নির্বাচন করুন (${selectedClasses.size}/২)"
                        } else {
                            "Select 1 or up to 2 classes (${selectedClasses.size}/2)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedClasses.size >= ContentClassGroupHelper.MAX_MULTI_CLASS_SELECTION) RoyalBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    HorizontalDivider()
                    options.forEach { (display, clsNum) ->
                        val isSelected = clsNum in selectedClasses
                        val canSelect = isSelected || selectedClasses.size < ContentClassGroupHelper.MAX_MULTI_CLASS_SELECTION
                        DropdownMenuItem(
                            enabled = canSelect,
                            leadingIcon = {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = null,
                                    enabled = canSelect
                                )
                            },
                            text = {
                                Text(
                                    text = display,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                if (canSelect) {
                                    onToggleClass(clsNum)
                                }
                            },
                            modifier = Modifier.testTag("multi_class_option_$clsNum")
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (isBn) "সম্পন্ন" else "Done",
                                fontWeight = FontWeight.Bold,
                                color = RoyalBlue
                            )
                        },
                        onClick = { expanded = false },
                        modifier = Modifier.testTag("multi_class_done_btn")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectedClassChipsStrip(
    selectedClasses: List<Int>,
    classOptions: List<Pair<String, Int>>,
    enabled: Boolean,
    onRemoveClass: (Int) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        selectedClasses.forEach { clsNum ->
            val label = classOptions.firstOrNull { it.second == clsNum }?.first ?: "শ্রেণি $clsNum"
            Surface(
                shape = RoundedCornerShape(50),
                color = RoyalBlue.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.28f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = RoyalBlue
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove $label",
                        tint = RoyalBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .clickable(enabled = enabled) { onRemoveClass(clsNum) }
                            .testTag("remove_selected_class_$clsNum")
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactLabeledInputBox(
    label: String,
    required: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = RoyalBlue.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, RoyalBlue.copy(alpha = 0.16f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = buildAnnotatedString {
                    append(label)
                    if (required) {
                        withStyle(SpanStyle(color = ErrorRed, fontWeight = FontWeight.Bold)) {
                            append(" *")
                        }
                    }
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun UploadSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StackedBooksArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Bottom green book
        drawRoundRect(
            color = Color(0xFF16A34A),
            topLeft = Offset(w * 0.08f, h * 0.62f),
            size = Size(w * 0.84f, h * 0.24f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = Color(0xFFFEFCE8),
            topLeft = Offset(w * 0.16f, h * 0.67f),
            size = Size(w * 0.72f, h * 0.14f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Middle amber book
        drawRoundRect(
            color = Color(0xFFF59E0B),
            topLeft = Offset(w * 0.10f, h * 0.37f),
            size = Size(w * 0.82f, h * 0.23f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = Color(0xFFFFFBEB),
            topLeft = Offset(w * 0.18f, h * 0.42f),
            size = Size(w * 0.68f, h * 0.13f),
            cornerRadius = CornerRadius(4f, 4f)
        )
        // Top blue book
        drawRoundRect(
            color = Color(0xFF0EA5E9),
            topLeft = Offset(w * 0.08f, h * 0.12f),
            size = Size(w * 0.84f, h * 0.24f),
            cornerRadius = CornerRadius(8f, 8f)
        )
        drawRoundRect(
            color = Color(0xFFF0F9FF),
            topLeft = Offset(w * 0.16f, h * 0.17f),
            size = Size(w * 0.72f, h * 0.14f),
            cornerRadius = CornerRadius(4f, 4f)
        )
    }
}

private val SelectedCoverInfoSaver: Saver<SelectedCoverInfo?, List<Any>> = Saver(
    save = { info ->
        if (info == null) {
            emptyList()
        } else {
            listOf(
                info.uri.toString(),
                info.displayName,
                info.sizeBytes,
                info.formattedSize,
                info.contentType,
                info.storageFileName,
                info.widthPx,
                info.heightPx
            )
        }
    },
    restore = { list ->
        if (list.size < 8) {
            null
        } else {
            SelectedCoverInfo(
                uri = Uri.parse(list[0] as String),
                displayName = list[1] as String,
                sizeBytes = (list[2] as Number).toLong(),
                formattedSize = list[3] as String,
                contentType = list[4] as String,
                storageFileName = list[5] as String,
                widthPx = (list[6] as Number).toInt(),
                heightPx = (list[7] as Number).toInt()
            )
        }
    }
)

private val SelectedPdfInfoSaver: Saver<SelectedPdfInfo?, List<Any>> = Saver(
    save = { info ->
        if (info == null) {
            emptyList()
        } else {
            listOf(
                info.uri.toString(),
                info.displayName,
                info.sizeBytes,
                info.formattedSize,
                info.detectedPageCount
            )
        }
    },
    restore = { list ->
        if (list.size < 5) {
            null
        } else {
            SelectedPdfInfo(
                uri = Uri.parse(list[0] as String),
                displayName = list[1] as String,
                sizeBytes = (list[2] as Number).toLong(),
                formattedSize = list[3] as String,
                detectedPageCount = (list[4] as Number).toInt()
            )
        }
    }
)

private val IntListSaver: Saver<List<Int>, IntArray> = Saver(
    save = { it.toIntArray() },
    restore = { it.toList() }
)
