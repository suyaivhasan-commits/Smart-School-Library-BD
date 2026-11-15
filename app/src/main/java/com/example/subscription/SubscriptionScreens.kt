package com.example.subscription

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.firebase.FirebaseProvider
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.admin.paymentBrandColor
import com.example.components.EmptyOrErrorStateCard
import com.example.components.FormEditorDialog
import com.example.components.LoadingStateBox
import com.example.components.NavyScreenTopBar
import com.example.model.DefaultCatalogSeed
import com.example.model.EducationLevel
import com.example.model.PaymentMethodConfig
import com.example.model.PaymentMethodConstants
import com.example.model.SchoolClass
import com.example.model.Subscription
import com.example.model.SubscriptionPlan
import com.example.model.SubscriptionRequest
import com.example.model.UiState
import com.example.model.UserProfile
import com.example.model.sortedBySerialOrder
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.formatLocalized
import com.example.ui.i18n.streamTitle
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldDark
import com.example.ui.theme.GoldPremium
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private fun copyToSystemClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * Masks the middle characters of an account/phone number for UI privacy (e.g. 01906••••47).
 * Full number remains intact in memory for clipboard copy.
 */
private fun maskAccountNumber(number: String): String {
    val clean = number.trim()
    if (clean.length < 7) {
        return clean.take(2) + "•••" + clean.takeLast(1)
    }
    val prefixLen = if (clean.length >= 11) 5 else (clean.length / 3).coerceAtLeast(2)
    val suffixLen = 2
    val prefix = clean.take(prefixLen)
    val suffix = clean.takeLast(suffixLen)
    val maskLen = (clean.length - prefixLen - suffixLen).coerceAtLeast(3)
    val mask = "•".repeat(maskLen)
    return "$prefix$mask$suffix"
}

private fun buildReferencePlansForClass(classNumber: Int, stream: String): List<SubscriptionPlan> {
    if (classNumber !in 5..10) return emptyList()
    val effectiveStream = if (classNumber in 9..10) stream.ifBlank { "SCIENCE" }.uppercase() else "ALL"
    val target = DefaultCatalogSeed.defaultAnnualPackages.firstOrNull {
        it.classNumber == classNumber && it.stream.equals(effectiveStream, ignoreCase = true)
    }
    return if (target != null) listOf(target) else emptyList()
}

/**
 * Validates image size (max 5MB) and compresses to high-quality lightweight JPEG.
 */
private fun processAndCompressImage(context: Context, uri: Uri): Pair<ByteArray, Bitmap>? {
    return try {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri) ?: return null
        val rawBytes = inputStream.readBytes()
        inputStream.close()

        if (rawBytes.size > 5 * 1024 * 1024) {
            throw IllegalArgumentException("ছবির সাইজ ৫MB এর বেশি হতে পারবে না।")
        }

        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

        var sampleSize = 1
        val maxDim = 1200
        while (boundsOptions.outWidth / sampleSize > maxDim || boundsOptions.outHeight / sampleSize > maxDim) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decodedBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions) ?: return null

        var workingBitmap = decodedBitmap
        var compressedBytes: ByteArray
        var quality = 75
        do {
            val outputStream = ByteArrayOutputStream()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
            compressedBytes = outputStream.toByteArray()
            quality -= 15
        } while (compressedBytes.size > 220_000 && quality >= 30)

        var attempts = 0
        while (compressedBytes.size > 220_000 && attempts < 5 && (workingBitmap.width > 160 || workingBitmap.height > 160)) {
            val scaledW = (workingBitmap.width * 0.7f).toInt().coerceAtLeast(160)
            val scaledH = (workingBitmap.height * 0.7f).toInt().coerceAtLeast(160)
            val nextBitmap = Bitmap.createScaledBitmap(workingBitmap, scaledW, scaledH, true)
            if (workingBitmap !== decodedBitmap && workingBitmap !== nextBitmap && !workingBitmap.isRecycled) {
                workingBitmap.recycle()
            }
            workingBitmap = nextBitmap
            val outputStream = ByteArrayOutputStream()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, 40, outputStream)
            compressedBytes = outputStream.toByteArray()
            attempts++
        }

        Pair(compressedBytes, workingBitmap)
    } catch (e: Exception) {
        if (e is IllegalArgumentException) throw e
        null
    }
}

/**
 * Uploads screenshot to Firebase Storage if available, or falls back to Base64 JPEG data URL.
 */
private suspend fun uploadOrEncodeScreenshot(
    context: Context,
    userId: String,
    imageBytes: ByteArray
): String {
    val reqId = "pay_${System.currentTimeMillis()}_${(1000..9999).random()}"
    return try {
        val storage = FirebaseProvider.getStorage(context)
        val ref = storage.reference.child("payment_screenshots/$userId/$reqId.jpg")
        val metadata = com.google.firebase.storage.StorageMetadata.Builder()
            .setContentType("image/jpeg")
            .build()
        ref.putBytes(imageBytes, metadata).await()
        ref.downloadUrl.await().toString()
    } catch (_: Exception) {
        val base64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubscriptionScreen(
    currentUserUid: String = "",
    userProfile: UserProfile? = null,
    preselectClass: Int,
    preselectStream: String = "ALL",
    classes: List<SchoolClass> = DefaultCatalogSeed.defaultClasses,
    educationLevels: List<EducationLevel> = emptyList(),
    plansState: UiState<List<SubscriptionPlan>>,
    paymentMethodsState: UiState<List<PaymentMethodConfig>> = UiState.Success(emptyList()),
    subscriptionsState: UiState<List<Subscription>>,
    requestsState: UiState<List<SubscriptionRequest>>,
    contactPhone: String,
    onBack: () -> Unit,
    onSubmitPaymentRequest: (
        studentName: String,
        plan: SubscriptionPlan,
        paymentMethod: String,
        transactionReference: String,
        senderNumber: String,
        screenshotUrl: String,
        selectedMethodConfig: PaymentMethodConfig?,
        fallbackReceiverNumber: String,
        onResult: (Result<String>) -> Unit
    ) -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    val allPlansList = (plansState as? UiState.Success)?.data.orEmpty()
    val selectableClassNumbers = remember(classes, allPlansList) {
        val fromClasses = classes.ifEmpty { DefaultCatalogSeed.defaultClasses }.sortedBySerialOrder().map { it.classNumber }
        val fromPlans = allPlansList.map { it.classNumber }.filter { it > 0 }
        (fromClasses + fromPlans).distinct().sorted()
    }
    val hasLevelWidePlans = remember(allPlansList) { allPlansList.any { it.classNumber == 0 } }

    var selectedClass by rememberSaveable(preselectClass) {
        mutableIntStateOf(
            when {
                preselectClass in 1..30 -> preselectClass
                preselectClass == 0 && hasLevelWidePlans -> 0
                else -> selectableClassNumbers.firstOrNull() ?: 5
            }
        )
    }
    var selectedStream by rememberSaveable(preselectStream) {
        mutableStateOf(if (preselectStream.isNotBlank() && preselectStream != "ALL") preselectStream.uppercase() else "SCIENCE")
    }
    var selectedPlanForPayment by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var copiedMethodId by remember { mutableStateOf<String?>(null) }
    var lastSubmissionBanner by rememberSaveable { mutableStateOf<String?>(null) }
    var paymentSuccessInfo by remember { mutableStateOf<PaymentSuccessInfo?>(null) }

    LaunchedEffect(preselectClass, preselectStream, plansState) {
        if ((preselectClass in 1..30 || (preselectClass == 0 && hasLevelWidePlans)) && selectedPlanForPayment == null) {
            val effStream = if (preselectClass in 9..10) {
                if (preselectStream.isNotBlank() && preselectStream != "ALL") preselectStream.uppercase() else "SCIENCE"
            } else "ALL"
            val plans = (plansState as? UiState.Success)?.data.orEmpty()
            val matched = plans.firstOrNull {
                it.classNumber == preselectClass && (preselectClass !in 9..10 || it.stream == "ALL" || it.stream.equals(effStream, ignoreCase = true))
            } ?: buildReferencePlansForClass(preselectClass, effStream).firstOrNull()
            if (matched != null) {
                selectedPlanForPayment = matched
            }
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }
    val dateTimeFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val userSubscriptions = (subscriptionsState as? UiState.Success)?.data.orEmpty()
    val userRequests = (requestsState as? UiState.Success)?.data.orEmpty()
    val activePaymentMethods = remember(paymentMethodsState) {
        (paymentMethodsState as? UiState.Success)?.data.orEmpty().filter { it.isActive }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("subscription_screen")
    ) {
        NavyScreenTopBar(
            title = if (isBn) "সাবস্ক্রিপশন ও পেমেন্ট" else "Subscription & Payment",
            subtitle = if (isBn) "শ্রেণিভিত্তিক মূল পাঠ্যবই ও গাইড আনলক" else "Class-Based Textbook & Guide Access",
            onBack = onBack,
            actions = {
                IconButton(
                    onClick = { showInstructionsDialog = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Payment Instructions",
                        tint = Color.White
                    )
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Prominent Confirmation Banner after Payment Submission
            if (!lastSubmissionBanner.isNullOrBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = EmeraldPrimary.copy(alpha = 0.16f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EmeraldDark.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                            .testTag("payment_submitted_confirmation_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldDark)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isBn) "পেমেন্ট রিকোয়েস্ট জমা হয়েছে (Pending)" else "Payment Request Submitted (Pending)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmeraldDark
                                    )
                                }
                                TextButton(onClick = { lastSubmissionBanner = null }) {
                                    Text(strings.close)
                                }
                            }
                            Text(
                                text = lastSubmissionBanner!!,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Golden Premium Header Banner
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A), Color(0xFFFCD34D))
                                )
                            )
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(GoldDark.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WorkspacePremium,
                                contentDescription = null,
                                tint = GoldDark,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBn) "প্রিমিয়াম অ্যাক্সেস" else "Premium Class Access",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF78350F)
                            )
                            Text(
                                text = if (isBn) {
                                    "একটি শ্রেণির সাবস্ক্রিপশনেই সেই শ্রেণির সকল মূল পাঠ্যবই ও গাইড একসাথে আনলক হবে!"
                                } else {
                                    "One class subscription unlocks BOTH Original Textbooks and Guides for that class!"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }
            }

            // Current Active / Expired Subscriptions Summary ("My Subscription")
            if (userSubscriptions.isNotEmpty()) {
                item {
                    Text(
                        text = if (isBn) "আমার সাবস্ক্রিপশন (My Subscription)" else "My Subscriptions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        userSubscriptions.forEach { sub ->
                            val active = sub.isCurrentlyActive()
                            val startStr = sub.startDate?.toDate()?.let { dateFormatter.format(it).formatLocalized(lang) } ?: "-"
                            val expStr = sub.expiryDate?.toDate()?.let { dateFormatter.format(it).formatLocalized(lang) } ?: "-"
                            val remDays = sub.remainingDays()
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = sub.planNameBn.ifBlank {
                                                    "${classTitle(sub.classNumber, lang)}" +
                                                        if (sub.classNumber in 9..10 && sub.stream != "ALL") " (${streamTitle(sub.stream, lang)})" else ""
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${classTitle(sub.classNumber, lang)}" +
                                                    (if (sub.classNumber in 9..10 && sub.stream != "ALL") " • ${streamTitle(sub.stream, lang)}" else "") +
                                                    " • ${sub.durationMonths.formatLocalized(lang)} ${if (isBn) "মাস" else "Months"}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = RoyalBlue,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Surface(
                                            color = if (active) EmeraldPrimary.copy(alpha = 0.16f) else ErrorRed.copy(alpha = 0.14f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text(
                                                text = if (active) {
                                                    if (isBn) "সক্রিয় (${remDays.formatLocalized(lang)} দিন বাকি)" else "Active ($remDays days left)"
                                                } else {
                                                    if (isBn) "মেয়াদোত্তীর্ণ" else "Expired"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (active) EmeraldDark else ErrorRed,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = if (isBn) "শুরু: $startStr • মেয়াদ শেষ: $expStr" else "Start: $startStr • Expires: $expStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (sub.transactionReference.isNotBlank()) {
                                        Text(
                                            text = "${sub.paymentMethod} • TrxID: ${sub.transactionReference}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Class & Stream Selector
            item {
                Text(
                    text = if (isBn) "সাবস্ক্রিপশন প্ল্যান নির্বাচন করুন" else "Select Subscription Plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                val selectedClassObj = remember(classes, selectedClass) {
                    classes.firstOrNull { it.classNumber == selectedClass }
                }
                val selectedHasStreams = selectedClass in 9..10 || selectedClassObj?.hasStreams == true
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasLevelWidePlans) {
                        item {
                            FilterChip(
                                selected = selectedClass == 0,
                                onClick = { selectedClass = 0 },
                                label = { Text(if (isBn) "স্তর ভিত্তিক প্যাক" else "Level Plans") },
                                modifier = Modifier.testTag("sub_class_chip_0")
                            )
                        }
                    }
                    items(selectableClassNumbers) { cls ->
                        FilterChip(
                            selected = selectedClass == cls,
                            onClick = { selectedClass = cls },
                            label = { Text(classTitle(cls, lang)) },
                            modifier = Modifier.testTag("sub_class_chip_$cls")
                        )
                    }
                }
                if (selectedHasStreams) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SCIENCE", "HUMANITIES", "BUSINESS").forEach { st ->
                            FilterChip(
                                selected = selectedStream == st,
                                onClick = { selectedStream = st },
                                label = { Text(streamTitle(st, lang)) }
                            )
                        }
                    }
                }
            }

            // Plans List with Locked Price, Duration, Benefits & Premium Access Info
            when (plansState) {
                is UiState.Loading -> item { LoadingStateBox() }
                is UiState.Error -> item {
                    EmptyOrErrorStateCard(
                        title = strings.offlineError,
                        body = plansState.message,
                        isError = true,
                        onRetry = onRetry
                    )
                }
                is UiState.Success -> {
                    val selectedClassObj = classes.firstOrNull { it.classNumber == selectedClass }
                    val selectedHasStreams = selectedClass in 9..10 || selectedClassObj?.hasStreams == true
                    val matchingLevelIds = if (selectedClass <= 0) {
                        emptySet()
                    } else {
                        educationLevels.filter { lvl ->
                            (selectedClassObj?.educationLevelId?.isNotBlank() == true && lvl.id == selectedClassObj.educationLevelId) ||
                                (lvl.minClassNumber > 0 && selectedClass in lvl.minClassNumber..lvl.maxClassNumber)
                        }.map { it.id }.toSet()
                    }
                    val remoteForClass = plansState.data.filter { p ->
                        val matchesClassOrLevel = p.classNumber == selectedClass ||
                            (selectedClass > 0 && p.classNumber == 0 && p.educationLevelId in matchingLevelIds)
                        val matchesStream = !selectedHasStreams ||
                            p.stream == "ALL" ||
                            p.stream.equals(selectedStream, ignoreCase = true)
                        matchesClassOrLevel && matchesStream
                    }
                    val displayPlans = remoteForClass

                    if (displayPlans.isEmpty()) {
                        item {
                            EmptyOrErrorStateCard(
                                title = if (isBn) "কোনো প্যাকেজ পাওয়া যায়নি" else "No Packages Available",
                                body = if (isBn) {
                                    "এই শ্রেণি বা শিক্ষা স্তরের জন্য এখনো কোনো সক্রিয় সাবস্ক্রিপশন প্যাকেজ নির্ধারণ করা হয়নি।"
                                } else {
                                    "No active subscription packages are currently configured for this class or level."
                                }
                            )
                        }
                    }

                    items(displayPlans, key = { it.id }) { plan ->
                        val benefits = plan.effectiveBenefits(isBn)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("plan_card_${plan.id}"),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
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
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(RoyalBlue.copy(alpha = 0.14f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                                contentDescription = null,
                                                tint = RoyalBlue
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = if (!isBn && plan.nameEn.isNotBlank()) plan.nameEn else plan.nameBn,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = plan.descriptionBn.ifBlank {
                                                    if (isBn) "মূল পাঠ্যবই + গাইড ও নোট প্রিমিয়াম অ্যাক্সেস" else "Original Textbooks + Guides Premium Access"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(10.dp))
                                benefits.forEach { benefit ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldDark,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = benefit,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isPurchasable = plan.isActive && plan.priceBdt > 0
                                    Column {
                                        val durationText = when {
                                            plan.durationMonths == 12 && isBn -> "বার্ষিক ফি (১২ মাস / ৩১ ডিসেম্বর পর্যন্ত)"
                                            plan.durationMonths == 12 -> "Annual Fee (12 Months / Until Dec 31)"
                                            isBn -> "প্যাকেজ ফি (${plan.durationMonths.formatLocalized(lang)} মাস)"
                                            else -> "Package Fee (${plan.durationMonths} Months)"
                                        }
                                        Text(
                                            text = durationText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (plan.priceBdt > 0) {
                                                val cycleLabel = if (plan.durationMonths == 12) {
                                                    if (isBn) "শিক্ষাবর্ষ" else "Year"
                                                } else {
                                                    if (isBn) "${plan.durationMonths.formatLocalized(lang)} মাস" else "${plan.durationMonths} Months"
                                                }
                                                "৳ ${plan.priceBdt.formatLocalized(lang)} / $cycleLabel"
                                            } else {
                                                if (isBn) "মূল্য নির্ধারিত হয়নি" else "Price not set"
                                            },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isPurchasable) EmeraldDark else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    Button(
                                        onClick = { selectedPlanForPayment = plan },
                                        enabled = isPurchasable,
                                        shape = RoundedCornerShape(50),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EmeraldDark,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("subscribe_plan_btn_${plan.durationMonths}")
                                    ) {
                                        Text(
                                            text = if (!plan.isActive) {
                                                if (isBn) "বর্তমানে বন্ধ" else "Inactive"
                                            } else if (plan.priceBdt <= 0) {
                                                if (isBn) "মূল্য নির্ধারণ বাকি" else "Price Pending"
                                            } else {
                                                if (isBn) "সাবস্ক্রাইব করুন" else "Subscribe"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Payment Instructions Button
            item {
                OutlinedButton(
                    onClick = { showInstructionsDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("payment_instructions_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isBn) "পেমেন্ট ও যাচাই নির্দেশনা দেখুন" else "View Payment & Verification Guide",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Student's Payment History ("পেমেন্ট ইতিহাস ও যাচাই অবস্থা")
            item {
                Text(
                    text = if (isBn) "পেমেন্ট ইতিহাস ও যাচাই অবস্থা (Payment History)" else "Payment History & Verification Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (userRequests.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = if (isBn) {
                                "আপনি এখনো কোনো পেমেন্ট রিকোয়েস্ট জমা দেননি। উপরের যেকোনো প্ল্যানে 'সাবস্ক্রাইব করুন' বোতামে চাপ দিয়ে পেমেন্ট তথ্য জমা দিন।"
                            } else {
                                "You have not submitted any payment requests yet."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(userRequests, key = { it.id }) { req ->
                    val statusColor = when (req.status) {
                        "APPROVED" -> EmeraldDark
                        "REJECTED" -> ErrorRed
                        else -> GoldDark
                    }
                    val statusIcon = when (req.status) {
                        "APPROVED" -> Icons.Filled.CheckCircle
                        "REJECTED" -> Icons.Filled.Cancel
                        else -> Icons.Filled.HourglassTop
                    }
                    val statusLabel = when (req.status) {
                        "APPROVED" -> if (isBn) "Approved: পেমেন্ট অনুমোদিত।" else "Approved: Payment verified."
                        "REJECTED" -> if (isBn) "Rejected: পেমেন্ট বাতিল হয়েছে।" else "Rejected: Payment declined."
                        else -> if (isBn) "Pending: পেমেন্ট যাচাই চলছে।" else "Pending: Under admin verification."
                    }
                    val submittedDateStr = req.createdAt?.toDate()?.let {
                        dateTimeFormatter.format(it).formatLocalized(lang)
                    } ?: "-"

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_payment_history_${req.id}")
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = req.planNameBn,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "৳${req.amountBdt.formatLocalized(lang)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldDark
                                )
                            }

                            Surface(
                                color = statusColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = statusLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = buildString {
                                    append(if (isBn) "মাধ্যম: " else "Method: ")
                                    append(req.paymentMethod)
                                    if (req.transactionType.isNotBlank()) append(" (${req.transactionType})")
                                    append(" • TrxID: ")
                                    append(req.transactionReference)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = RoyalBlue
                            )

                            Text(
                                text = buildString {
                                    if (req.receiverNumber.isNotBlank()) {
                                        append(if (isBn) "রিসিভার: ${maskAccountNumber(req.receiverNumber)} • " else "Receiver: ${maskAccountNumber(req.receiverNumber)} • ")
                                    }
                                    if (req.senderNumber.isNotBlank()) {
                                        append(if (isBn) "প্রেরক: ${req.senderNumber} • " else "Sender: ${req.senderNumber} • ")
                                    }
                                    append(if (isBn) "জমার তারিখ: $submittedDateStr" else "Submitted: $submittedDateStr")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (req.status == "REJECTED" && req.effectiveRejectionReason.isNotBlank()) {
                                Surface(
                                    color = ErrorRed.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = (if (isBn) "বাতিলের কারণ: " else "Rejection Reason: ") + req.effectiveRejectionReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ErrorRed,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            } else if (req.status == "APPROVED" && req.adminNote.isNotBlank()) {
                                Text(
                                    text = (if (isBn) "অ্যাডমিন নোট: " else "Admin Note: ") + req.adminNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPlanForPayment?.let { plan ->
        StudentPaymentSubmissionDialog(
            plan = plan,
            currentUserUid = currentUserUid,
            defaultStudentName = userProfile?.name.orEmpty(),
            activePaymentMethods = activePaymentMethods,
            fallbackContactPhone = contactPhone,
            isBn = isBn,
            lang = lang,
            onDismiss = { selectedPlanForPayment = null },
            onSubmit = { studentName, methodLabel, trxId, sender, screenshotUrl, methodConfig, receiverSnapshot, onDone ->
                onSubmitPaymentRequest(
                    studentName,
                    plan,
                    methodLabel,
                    trxId,
                    sender,
                    screenshotUrl,
                    methodConfig,
                    receiverSnapshot
                ) { result ->
                    onDone(result)
                    if (result.isSuccess) {
                        val reqId = result.getOrNull().orEmpty()
                        paymentSuccessInfo = PaymentSuccessInfo(
                            planName = plan.nameBn,
                            amountBdt = plan.priceBdt,
                            paymentMethod = methodLabel,
                            trxId = trxId,
                            senderNumber = sender,
                            requestId = reqId
                        )
                        lastSubmissionBanner =
                            "আপনার পেমেন্ট রিকোয়েস্ট সফলভাবে জমা হয়েছে (স্ট্যাটাস: Pending)। অ্যাডমিন পেমেন্ট যাচাই করার পর আপনার সাবস্ক্রিপশন সক্রিয় হবে।"
                        selectedPlanForPayment = null
                    }
                }
            }
        )
    }

    paymentSuccessInfo?.let { info ->
        PaymentSuccessConfirmationDialog(
            info = info,
            isBn = isBn,
            lang = lang,
            onDismiss = { paymentSuccessInfo = null }
        )
    }

    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            title = {
                Text(
                    text = if (isBn) "সাবস্ক্রিপশন পেমেন্ট ও যাচাই নির্দেশনা" else "Subscription Payment & Verification Guide",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isBn) {
                            "১. আপনার কাঙ্ক্ষিত শ্রেণি (৫ম–১০ম শ্রেণি) ও মেয়াদের প্ল্যান নির্বাচন করুন।\n" +
                                "২. সক্রিয় পেমেন্ট মাধ্যম (bKash / Nagad / Rocket / Bank Transfer) নির্বাচন করে অ্যাডমিনের নম্বরে নির্ধারিত ফি পাঠান (নম্বরটি 'কপি নম্বর' বোতামে চাপে কপি করতে পারবেন)।\n" +
                                "৩. পেমেন্ট সম্পন্ন হলে আপনার প্রেরক মোবাইল নম্বর, প্রাপ্ত সঠিক Transaction ID (TrxID) এবং পেমেন্টের স্ক্রিনশট/রসিদের ছবি ফর্মে সংযুক্ত করে জমা দিন।\n" +
                                "৪. আপনার রিকোয়েস্টটি 'Pending: পেমেন্ট যাচাই চলছে' অবস্থায় জমা হবে এবং সাথে সাথে অ্যাডমিন প্যানেলে নোটিফিকেশন যাবে।\n" +
                                "৫. সুপার অ্যাডমিন পেমেন্ট যাচাই করে Approve করলেই সাথে সাথে আপনার সাবস্ক্রিপশন চালু হবে এবং ঐ শ্রেণির সকল মূল পাঠ্যবই ও গাইড আনলক হয়ে যাবে।"
                        } else {
                            "1. Choose your desired Class plan (Classes 5–10).\n" +
                                "2. Select an active payment method (bKash / Nagad / Rocket / Bank Transfer), copy the official number, and send the exact locked fee.\n" +
                                "3. Enter your sender number, Transaction ID (TrxID) and attach a payment screenshot/receipt photo.\n" +
                                "4. Your request is saved as PENDING and notifies the Super Admin immediately.\n" +
                                "5. Once approved by the Admin, your class Textbooks and Guides unlock automatically."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showInstructionsDialog = false }) {
                    Text(strings.close)
                }
            }
        )
    }
}


data class PaymentSuccessInfo(
    val planName: String,
    val amountBdt: Int,
    val paymentMethod: String,
    val trxId: String,
    val senderNumber: String,
    val requestId: String
)

private data class DisplayPaymentOption(
    val id: String,
    val methodType: String,
    val nameBn: String,
    val nameEn: String,
    val brandColor: Color,
    val accountNumber: String,
    val accountType: String,
    val transactionType: String,
    val instructionBn: String,
    val instructionEn: String,
    val config: PaymentMethodConfig?,
    val isBankTransfer: Boolean = false
)

@Composable
private fun PaymentSuccessConfirmationDialog(
    info: PaymentSuccessInfo,
    isBn: Boolean,
    lang: AppLanguage,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(EmeraldDark.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldDark,
                    modifier = Modifier.size(38.dp)
                )
            }
        },
        title = {
            Text(
                text = if (isBn) "পেমেন্ট রিকোয়েস্ট সফলভাবে জমা হয়েছে!" else "Payment Request Submitted Successfully!",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = EmeraldDark.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, EmeraldDark.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBn) "স্ট্যাটাস:" else "Status:",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = GoldDark.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (isBn) "অপেক্ষমাণ (Pending)" else "Pending Review",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = EmeraldDark.copy(alpha = 0.15f))
                        LockedSummaryRow(
                            label = if (isBn) "প্ল্যান:" else "Plan:",
                            value = info.planName
                        )
                        LockedSummaryRow(
                            label = if (isBn) "প্রদেয় ফি:" else "Amount:",
                            value = "৳ ${info.amountBdt.formatLocalized(lang)} BDT",
                            highlight = EmeraldDark
                        )
                        LockedSummaryRow(
                            label = if (isBn) "পেমেন্ট মাধ্যম:" else "Method:",
                            value = info.paymentMethod
                        )
                        LockedSummaryRow(
                            label = if (isBn) "Transaction ID:" else "TrxID:",
                            value = info.trxId,
                            highlight = RoyalBlue
                        )
                        LockedSummaryRow(
                            label = if (isBn) "প্রেরক নম্বর:" else "Sender:",
                            value = info.senderNumber
                        )
                    }
                }

                Text(
                    text = if (isBn) {
                        "আপনার পেমেন্টের তথ্য Firebase ডাটাবেজে সংরক্ষিত হয়েছে এবং অ্যাডমিন প্যানেলে রিয়েল-টাইম নোটিফিকেশন পৌঁছেছে।\n\nসুপার অ্যাডমিন আপনার পেমেন্ট যাচাই করে অনুমোদন (Approve) করলেই আপনার সাবস্ক্রিপশন সক্রিয় হবে এবং আপনি ইন-অ্যাপ নোটিফিকেশন পাবেন।"
                    } else {
                        "Your payment details have been saved to Firebase and real-time notification has been sent to the Admin Panel.\n\nOnce the Super Admin verifies and approves the request, your subscription will be activated automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldDark,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("payment_success_dialog_confirm_btn")
            ) {
                Text(
                    text = if (isBn) "ঠিক আছে (বুঝেছি)" else "OK, Got it",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudentPaymentSubmissionDialog(
    plan: SubscriptionPlan,
    currentUserUid: String,
    defaultStudentName: String,
    activePaymentMethods: List<PaymentMethodConfig>,
    fallbackContactPhone: String,
    isBn: Boolean,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSubmit: (
        studentName: String,
        paymentMethod: String,
        transactionReference: String,
        senderNumber: String,
        screenshotUrl: String,
        selectedMethodConfig: PaymentMethodConfig?,
        receiverNumberSnapshot: String,
        onDone: (Result<String>) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()
    val todayFormatted = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date())
    }

    var studentName by remember(defaultStudentName) {
        mutableStateOf(defaultStudentName.ifBlank { if (isBn) "শিক্ষার্থী" else "Student" })
    }
    var senderNumber by remember { mutableStateOf("") }
    var trxId by remember { mutableStateOf("") }

    // Screenshot state (Optional)
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var imageSizeText by remember { mutableStateOf<String?>(null) }

    var errorMsg by remember { mutableStateOf<String?>(null) }
    var copiedFeedback by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var submissionStatusText by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val processed = processAndCompressImage(context, uri)
                if (processed != null) {
                    selectedImageUri = uri
                    selectedImageBytes = processed.first
                    selectedImageBitmap = processed.second
                    val sizeKb = processed.first.size / 1024
                    imageSizeText = "$sizeKb KB"
                    errorMsg = null
                } else {
                    errorMsg = if (isBn) "ছবিটি লোড করা যায়নি। অন্য ছবি নির্বাচন করুন।" else "Failed to load image."
                }
            } catch (e: IllegalArgumentException) {
                errorMsg = e.message
            } catch (e: Exception) {
                errorMsg = if (isBn) "ছবি প্রক্রিয়াকরণে সমস্যা হয়েছে।" else "Error processing image."
            }
        }
    }

    // Modern card-based payment options: bKash, Nagad, Rocket + any custom configured methods
    val paymentOptions = remember(activePaymentMethods, fallbackContactPhone, plan.priceBdt) {
        val options = mutableListOf<DisplayPaymentOption>()

        // 1. bKash (বিকাশ)
        val bkashConfig = activePaymentMethods.firstOrNull {
            it.methodType == PaymentMethodConstants.TYPE_BKASH || it.methodName.contains("bkash", true) || it.methodName.contains("বিকাশ")
        }
        val bkashNum = bkashConfig?.accountNumber?.ifBlank { fallbackContactPhone } ?: fallbackContactPhone
        options.add(
            DisplayPaymentOption(
                id = bkashConfig?.id.takeUnless { it.isNullOrBlank() } ?: "opt_bkash",
                methodType = PaymentMethodConstants.TYPE_BKASH,
                nameBn = "বিকাশ",
                nameEn = "bKash",
                brandColor = Color(0xFFD12053),
                accountNumber = bkashNum,
                accountType = bkashConfig?.accountType ?: "Personal",
                transactionType = bkashConfig?.transactionType ?: PaymentMethodConstants.TRX_SEND_MONEY,
                instructionBn = bkashConfig?.paymentInstructions?.ifBlank { null }
                    ?: "বিকাশ অ্যাপ অথবা *247# ডায়াল করে Send Money অপশনে যান এবং উপরের নম্বরে নির্ধারিত ৳${plan.priceBdt} পাঠান। সফল হলে প্রাপ্ত TrxID ও আপনার প্রেরক মোবাইল নম্বর নিচে লিখুন।",
                instructionEn = bkashConfig?.paymentInstructions?.ifBlank { null }
                    ?: "Use bKash App or dial *247# to Send Money ৳${plan.priceBdt} to the official number above.",
                config = bkashConfig
            )
        )

        // 2. Nagad (নগদ)
        val nagadConfig = activePaymentMethods.firstOrNull {
            it.methodType == PaymentMethodConstants.TYPE_NAGAD || it.methodName.contains("nagad", true) || it.methodName.contains("নগদ")
        }
        val nagadNum = nagadConfig?.accountNumber?.ifBlank { fallbackContactPhone } ?: fallbackContactPhone
        options.add(
            DisplayPaymentOption(
                id = nagadConfig?.id.takeUnless { it.isNullOrBlank() } ?: "opt_nagad",
                methodType = PaymentMethodConstants.TYPE_NAGAD,
                nameBn = "নগদ",
                nameEn = "Nagad",
                brandColor = Color(0xFFF7941D),
                accountNumber = nagadNum,
                accountType = nagadConfig?.accountType ?: "Personal",
                transactionType = nagadConfig?.transactionType ?: PaymentMethodConstants.TRX_SEND_MONEY,
                instructionBn = nagadConfig?.paymentInstructions?.ifBlank { null }
                    ?: "নগদ অ্যাপ অথবা *167# ডায়াল করে Send Money অপশনে যান এবং উপরের নম্বরে নির্ধারিত ৳${plan.priceBdt} পাঠান। সফল হলে প্রাপ্ত TrxID ও আপনার প্রেরক মোবাইল নম্বর নিচে লিখুন।",
                instructionEn = nagadConfig?.paymentInstructions?.ifBlank { null }
                    ?: "Use Nagad App or dial *167# to Send Money ৳${plan.priceBdt} to the official number above.",
                config = nagadConfig
            )
        )

        // 3. Rocket (রকেট)
        val rocketConfig = activePaymentMethods.firstOrNull {
            it.methodType == PaymentMethodConstants.TYPE_ROCKET || it.methodName.contains("rocket", true) || it.methodName.contains("রকেট")
        }
        val rocketNum = rocketConfig?.accountNumber?.ifBlank { fallbackContactPhone } ?: fallbackContactPhone
        options.add(
            DisplayPaymentOption(
                id = rocketConfig?.id.takeUnless { it.isNullOrBlank() } ?: "opt_rocket",
                methodType = PaymentMethodConstants.TYPE_ROCKET,
                nameBn = "রকেট",
                nameEn = "Rocket",
                brandColor = Color(0xFF8C3494),
                accountNumber = rocketNum,
                accountType = rocketConfig?.accountType ?: "Personal",
                transactionType = rocketConfig?.transactionType ?: PaymentMethodConstants.TRX_SEND_MONEY,
                instructionBn = rocketConfig?.paymentInstructions?.ifBlank { null }
                    ?: "রকেট অ্যাপ অথবা *322# ডায়াল করে Send Money অপশনে যান এবং উপরের নম্বরে নির্ধারিত ৳${plan.priceBdt} পাঠান। সফল হলে প্রাপ্ত TrxID ও আপনার প্রেরক মোবাইল নম্বর নিচে লিখুন।",
                instructionEn = rocketConfig?.paymentInstructions?.ifBlank { null }
                    ?: "Use Rocket App or dial *322# to Send Money ৳${plan.priceBdt} to the official number above.",
                config = rocketConfig
            )
        )

        // 4. Any other custom configured methods in Firestore (e.g. Bank Transfer)
        activePaymentMethods.filter {
            it.methodType !in listOf(PaymentMethodConstants.TYPE_BKASH, PaymentMethodConstants.TYPE_NAGAD, PaymentMethodConstants.TYPE_ROCKET) &&
            !it.methodName.contains("bkash", true) && !it.methodName.contains("nagad", true) && !it.methodName.contains("rocket", true)
        }.forEach { cfg ->
            options.add(
                DisplayPaymentOption(
                    id = cfg.id,
                    methodType = cfg.methodType,
                    nameBn = cfg.methodName,
                    nameEn = cfg.methodName,
                    brandColor = if (cfg.isBankTransfer) RoyalBlue else EmeraldDark,
                    accountNumber = cfg.accountNumber,
                    accountType = cfg.accountType,
                    transactionType = cfg.transactionType,
                    instructionBn = cfg.formattedInstruction(plan.priceBdt, true),
                    instructionEn = cfg.formattedInstruction(plan.priceBdt, false),
                    config = cfg,
                    isBankTransfer = cfg.isBankTransfer
                )
            )
        }

        options
    }

    var selectedOptionId by remember(paymentOptions) {
        mutableStateOf(paymentOptions.first().id)
    }
    val selectedOption = remember(paymentOptions, selectedOptionId) {
        paymentOptions.firstOrNull { it.id == selectedOptionId } ?: paymentOptions.first()
    }

    val effectiveMethodName = selectedOption.nameEn
    val effectiveTrxType = selectedOption.transactionType
    val effectiveReceiverNumber = selectedOption.accountNumber
    val isBankMethod = selectedOption.isBankTransfer

    // Validation rules:
    // Sender Mobile Number: MANDATORY
    // Transaction ID: MANDATORY
    // Screenshot: OPTIONAL
    val hasValidTrxId = PaymentMethodConstants.hasMinimumAlphanumericChars(trxId.trim(), 4)
    val hasValidSender = if (isBankMethod) senderNumber.trim().length >= 4 else senderNumber.trim().length >= 8
    val hasValidStudentName = studentName.trim().length >= 2
    val hasScreenshot = selectedImageBytes != null
    val canSubmit = !isSubmitting && hasValidStudentName && hasValidSender && hasValidTrxId

    FormEditorDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = {
            Text(
                text = if (isBn) "পেমেন্ট তথ্য প্রদান করুন" else "Submit Payment Details",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Step 1: Modern Card-Based Payment Selection (bKash, Nagad, Rocket)
                Text(
                    text = if (isBn) "১. পেমেন্ট মাধ্যম নির্বাচন করুন (ট্যাপ করুন)" else "1. Select Payment Method (Tap Card)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    paymentOptions.forEach { opt ->
                        val isSelected = opt.id == selectedOption.id
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedOptionId = opt.id
                                    copiedFeedback = false
                                    errorMsg = null
                                }
                                .testTag("payment_card_${opt.methodType.lowercase()}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) opt.brandColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) opt.brandColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) opt.brandColor else opt.brandColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (opt.isBankTransfer) Icons.Filled.AccountBalance else Icons.Filled.AccountBalanceWallet,
                                        contentDescription = opt.nameBn,
                                        tint = if (isSelected) Color.White else opt.brandColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (isBn) opt.nameBn else opt.nameEn,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isSelected) opt.brandColor else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = opt.transactionType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) opt.brandColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                                if (isSelected) {
                                    Spacer(Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = opt.brandColor,
                                        contentColor = Color.White
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(10.dp))
                                            Spacer(Modifier.width(2.dp))
                                            Text(
                                                text = if (isBn) "নির্বাচিত" else "Selected",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 2: Shows ONLY the selected provider's information
                Text(
                    text = if (isBn) "২. ${selectedOption.nameBn} পেমেন্টের তথ্য ও নির্দেশনা" else "2. ${selectedOption.nameEn} Payment Details & Instructions",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = selectedOption.brandColor.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, selectedOption.brandColor.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = selectedOption.brandColor,
                                    contentColor = Color.White
                                ) {
                                    Text(
                                        text = if (isBn) selectedOption.nameBn else selectedOption.nameEn,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${selectedOption.accountType} • ${selectedOption.transactionType}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = selectedOption.brandColor
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(50),
                                color = EmeraldDark.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "৳ ${plan.priceBdt} BDT",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        // Official Receiver Number with 1-Click Copy
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, selectedOption.brandColor.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isBn) "অফিসিয়াল ${selectedOption.nameBn} নম্বর:" else "Official ${selectedOption.nameEn} Number:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = effectiveReceiverNumber.ifBlank { "01700000000" },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                }

                                Button(
                                    onClick = {
                                        val numToCopy = effectiveReceiverNumber.ifBlank { fallbackContactPhone }
                                        copyToSystemClipboard(context, selectedOption.nameEn, numToCopy)
                                        copiedFeedback = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = selectedOption.brandColor,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("dialog_copy_receiver_number_btn")
                                ) {
                                    Icon(
                                        imageVector = if (copiedFeedback) Icons.Filled.Check else Icons.Filled.ContentCopy,
                                        contentDescription = "Copy Receiver Number",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (copiedFeedback) {
                                            if (isBn) "কপি হয়েছে!" else "Copied!"
                                        } else {
                                            if (isBn) "কপি করুন" else "Copy"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Bank transfer details if applicable
                        if (isBankMethod && selectedOption.config != null) {
                            val cfg = selectedOption.config
                            Text(
                                text = buildString {
                                    append("ব্যাংক: ${cfg.bankName}")
                                    if (cfg.accountHolderName.isNotBlank()) append(" • হিসাবধারী: ${cfg.accountHolderName}")
                                    if (cfg.branchName.isNotBlank()) append("\nশাখা: ${cfg.branchName}")
                                    if (cfg.routingNumber.isNotBlank()) append(" • Routing: ${cfg.routingNumber}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Clear step instructions
                        Text(
                            text = if (isBn) selectedOption.instructionBn else selectedOption.instructionEn,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Locked Read-Only Summary Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LockedSummaryRow(
                            label = if (isBn) "প্যাকেজ ID:" else "Package ID:",
                            value = plan.id
                        )
                        LockedSummaryRow(
                            label = if (isBn) "নির্বাচিত প্ল্যান:" else "Subscription Plan:",
                            value = plan.nameBn
                        )
                        LockedSummaryRow(
                            label = if (isBn) "প্রদেয় ফি (লকড):" else "Payable Amount (Locked):",
                            value = "৳ ${plan.priceBdt.formatLocalized(lang)} BDT (৩১ ডিসেম্বর পর্যন্ত)",
                            highlight = EmeraldDark
                        )
                        LockedSummaryRow(
                            label = if (isBn) "পেমেন্ট মাধ্যম:" else "Payment Method:",
                            value = "${selectedOption.nameBn} (${selectedOption.transactionType})"
                        )
                        if (effectiveReceiverNumber.isNotBlank()) {
                            LockedSummaryRow(
                                label = if (isBn) "রিসিভার নম্বর:" else "Receiver Number:",
                                value = maskAccountNumber(effectiveReceiverNumber)
                            )
                        }
                        if (currentUserUid.isNotBlank()) {
                            LockedSummaryRow(
                                label = if (isBn) "শিক্ষার্থী UID:" else "Student UID:",
                                value = currentUserUid.take(18) + if (currentUserUid.length > 18) "..." else ""
                            )
                        }
                        LockedSummaryRow(
                            label = if (isBn) "জমার তারিখ:" else "Submission Date:",
                            value = todayFormatted.formatLocalized(lang)
                        )
                    }
                }

                // Step 3: Transaction Details (Mandatory) & Screenshot (Optional)
                Text(
                    text = if (isBn) "৩. ট্রানজেকশন তথ্য দিন (স্ক্রিনশট ঐচ্ছিক)" else "3. Transaction Details (Screenshot Optional)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                if (errorMsg != null) {
                    Surface(
                        color = ErrorRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMsg!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = studentName,
                    onValueChange = {
                        studentName = it
                        errorMsg = null
                    },
                    label = { Text(if (isBn) "শিক্ষার্থীর নাম (Student Name) *" else "Student Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_student_name_input")
                )

                OutlinedTextField(
                    value = senderNumber,
                    onValueChange = {
                        senderNumber = it
                        errorMsg = null
                    },
                    label = {
                        Text(
                            if (isBankMethod) {
                                if (isBn) "প্রেরক ব্যাংক হিসাব / মোবাইল নম্বর *" else "Sender Account / Mobile Number *"
                            } else {
                                if (isBn) "প্রেরক মোবাইল নম্বর (Sender Mobile Number) *" else "Sender Mobile Number *"
                            }
                        )
                    },
                    placeholder = {
                        Text(if (isBankMethod) "01XXXXXXXXX / Bank Acc" else "01XXXXXXXXX")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = if (isBn) "যে নম্বর থেকে ফি পাঠানো হয়েছে (বাধ্যতামূলক)" else "Number used to send money (Mandatory)",
                            color = if (hasValidSender) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_sender_input")
                )

                OutlinedTextField(
                    value = trxId,
                    onValueChange = {
                        trxId = it
                        errorMsg = null
                    },
                    label = {
                        Text(
                            if (isBankMethod) {
                                if (isBn) "ব্যাংক রেফারেন্স / Transaction ID *" else "Bank Reference / Transaction ID *"
                            } else {
                                if (isBn) "Transaction ID (TrxID) *" else "Transaction ID (TrxID) *"
                            }
                        )
                    },
                    placeholder = { Text(if (isBankMethod) "e.g. BRN98451230" else "e.g. BKS74A9X2P") },
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = if (isBn) "পেমেন্ট মেসেজে পাওয়া ট্রানজেকশন আইডি (বাধ্যতামূলক)" else "Transaction ID from SMS (Mandatory)",
                            color = if (hasValidTrxId) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("payment_trx_input")
                )

                // Optional Payment Screenshot Upload Box
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasScreenshot) EmeraldPrimary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = if (hasScreenshot) EmeraldDark else RoyalBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isBn) "পেমেন্ট স্ক্রিনশট (ঐচ্ছিক / Optional)" else "Payment Screenshot (Optional)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (hasScreenshot) EmeraldDark else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (hasScreenshot) {
                                Surface(
                                    color = EmeraldDark.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = if (isBn) "সংযুক্ত হয়েছে (${imageSizeText ?: ""})" else "Attached (${imageSizeText ?: ""})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldDark,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (selectedImageBitmap != null) {
                            // Preview thumbnail
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.05f))
                                    .border(1.dp, EmeraldDark.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = selectedImageBitmap!!.asImageBitmap(),
                                    contentDescription = "Payment Screenshot Preview",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { photoPickerLauncher.launch("image/*") },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(if (isBn) "পরিবর্তন করুন" else "Change", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        selectedImageUri = null
                                        selectedImageBitmap = null
                                        selectedImageBytes = null
                                        imageSizeText = null
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isBn) "মুছে ফেলুন" else "Remove", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        } else {
                            Text(
                                text = if (isBn) {
                                    "পেমেন্ট রসিদ বা স্ক্রিনশট থাকলে সংযুক্ত করতে পারেন (ঐচ্ছিক, সর্বোচ্চ ৫ MB)।"
                                } else {
                                    "Attach receipt photo or screenshot if available (optional, max 5 MB)."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("pick_payment_screenshot_btn")
                            ) {
                                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isBn) "গ্যালারি থেকে স্ক্রিনশট নির্বাচন করুন (ঐচ্ছিক)" else "Select Screenshot (Optional)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (!hasValidTrxId || !hasValidSender) {
                    Text(
                        text = if (isBn) "* প্রেরক মোবাইল নম্বর ও Transaction ID উভয়ই বাধ্যতামূলক।" else "* Both Sender Mobile Number and Transaction ID are required.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSubmit,
                onClick = {
                    if (studentName.trim().length < 2) {
                        errorMsg = if (isBn) "শিক্ষার্থীর সঠিক নাম লিখুন।" else "Please enter student name."
                        return@Button
                    }
                    if (!isBankMethod && senderNumber.trim().length < 8) {
                        errorMsg = if (isBn) "সঠিক প্রেরক মোবাইল নম্বর প্রদান করুন (কমপক্ষে ৮ সংখ্যা)।" else "Enter a valid sender mobile number."
                        return@Button
                    }
                    if (isBankMethod && senderNumber.trim().length < 4) {
                        errorMsg = if (isBn) "প্রেরক হিসাব বা মোবাইল নম্বর প্রদান করুন।" else "Enter sender account or phone number."
                        return@Button
                    }
                    if (!PaymentMethodConstants.hasMinimumAlphanumericChars(trxId.trim(), 4)) {
                        errorMsg = if (isBn) "সঠিক Transaction ID প্রদান করুন (কমপক্ষে ৪ অক্ষর/সংখ্যা)।" else "Enter a valid Transaction ID (min 4 alphanumeric chars)."
                        return@Button
                    }

                    isSubmitting = true
                    errorMsg = null
                    submissionStatusText = if (selectedImageBytes != null) {
                        if (isBn) "স্ক্রিনশট সংরক্ষণ করা হচ্ছে..." else "Processing screenshot..."
                    } else {
                        if (isBn) "রিকোয়েস্ট জমা হচ্ছে..." else "Submitting request..."
                    }

                    coroutineScope.launch {
                        try {
                            val screenshotUrl = if (selectedImageBytes != null) {
                                uploadOrEncodeScreenshot(
                                    context = context,
                                    userId = currentUserUid.ifBlank { "anonymous" },
                                    imageBytes = selectedImageBytes!!
                                )
                            } else ""

                            submissionStatusText = if (isBn) "পেমেন্ট তথ্য সংরক্ষণ হচ্ছে..." else "Saving payment details..."
                            onSubmit(
                                studentName.trim(),
                                selectedOption.nameEn,
                                trxId.trim(),
                                senderNumber.trim(),
                                screenshotUrl,
                                selectedOption.config,
                                effectiveReceiverNumber
                            ) { res ->
                                isSubmitting = false
                                res.onFailure { err ->
                                    errorMsg = err.localizedMessage ?: if (isBn) "রিকোয়েস্ট জমা দেওয়া যায়নি।" else "Submission failed."
                                }
                            }
                        } catch (e: Exception) {
                            isSubmitting = false
                            errorMsg = e.localizedMessage ?: if (isBn) "পেমেন্ট রিকোয়েস্ট প্রক্রিয়াকরণ ব্যর্থ হয়েছে।" else "Submission processing failed."
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldDark,
                    contentColor = Color.White,
                    disabledContainerColor = EmeraldDark.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f)
                ),
                modifier = Modifier.testTag("submit_payment_request_btn")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = submissionStatusText.ifBlank { if (isBn) "জমা হচ্ছে..." else "Submitting..." },
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = if (isBn) "পেমেন্ট রিকোয়েস্ট নিশ্চিত ও জমা দিন" else "Confirm & Submit Payment",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSubmitting,
                onClick = onDismiss
            ) {
                Text(strings.cancel)
            }
        }
    )
}


@Composable
private fun LockedSummaryRow(
    label: String,
    value: String,
    highlight: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = highlight ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PremiumAccessGateScreen(
    classNumber: Int,
    stream: String,
    isExpired: Boolean = false,
    onBack: () -> Unit,
    onOpenSubscriptionPlans: () -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    var showLearnMore by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF040F22), NavyDeep, NavyPrimary)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("premium_gate_screen")
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .align(Alignment.Center)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(GoldPremium.copy(alpha = 0.16f))
                    .border(2.dp, GoldPremium.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.WorkspacePremium,
                    contentDescription = null,
                    tint = GoldPremium,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(Modifier.height(22.dp))

            Text(
                text = strings.premiumRequiredTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = GoldPremium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            val classLabel = buildString {
                append(classTitle(classNumber, lang))
                if (classNumber in 9..10 && stream != "ALL") {
                    append(" • ")
                    append(streamTitle(stream, lang))
                }
            }

            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(50)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = GoldPremium,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = classLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (isExpired) {
                    if (isBn) {
                        "আপনার এই শ্রেণির সাবস্ক্রিপশনের মেয়াদ শেষ হয়েছে। পুনরায় পড়তে সাবস্ক্রিপশন নবায়ন করুন!"
                    } else {
                        "Your subscription for this class has expired. Renew your plan to continue reading!"
                    }
                } else {
                    if (isBn) {
                        "এই বই বা গাইডটি পড়তে হলে $classLabel-এর সক্রিয় সাবস্ক্রিপশন প্রয়োজন!"
                    } else {
                        "An active subscription for $classLabel is required to read this premium book or guide!"
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onOpenSubscriptionPlans,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPremium,
                    contentColor = Color(0xFF1F1300)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("premium_gate_subscribe_btn")
            ) {
                Text(
                    text = strings.subscribeNow,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = if (isBn) "আরও জানুন" else "Learn More",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { showLearnMore = true }
                    .padding(8.dp)
            )
        }
    }

    if (showLearnMore) {
        AlertDialog(
            onDismissRequest = { showLearnMore = false },
            title = {
                Text(
                    text = if (isBn) "শ্রেণিভিত্তিক সাবস্ক্রিপশন নীতি" else "Class Subscription Policy",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isBn) {
                        "• একটি শ্রেণির সাবস্ক্রিপশন নিলে সেই শ্রেণির সকল মূল পাঠ্যবই এবং গাইড ও নোট একসাথে আনলক হবে।\n" +
                            "• উদাহরণস্বরূপ: ৫ম শ্রেণির সাবস্ক্রিপশনে ৫ম শ্রেণির মূল বই + গাইড আনলক থাকবে, তবে অন্য শ্রেণির প্রিমিয়াম কনটেন্ট লক থাকবে।\n" +
                            "• ৯ম ও ১০ম শ্রেণির ক্ষেত্রে নির্বাচিত বিভাগ (বিজ্ঞান/মানবিক/ব্যবসায় শিক্ষা) যাচাই করা হয়।"
                    } else {
                        "• Subscribing to a class unlocks BOTH Original Textbooks and Guides/Notes for that class.\n" +
                            "• Other classes remain locked unless subscribed separately.\n" +
                            "• For Classes 9 and 10, stream eligibility (Science/Humanities/Business) is also verified."
                    }
                )
            },
            confirmButton = {
                Button(onClick = { showLearnMore = false }) {
                    Text(strings.close)
                }
            }
        )
    }
}
