package com.example.admin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.components.DestructiveConfirmDialog
import com.example.components.FormEditorDialog
import com.example.components.resolveFirebaseStorageUrl
import com.example.model.AppNotification
import com.example.model.DefaultCatalogSeed
import com.example.model.EducationLevel
import com.example.model.PaymentMethodConfig
import com.example.model.PaymentMethodConstants
import com.example.model.SchoolClass
import com.example.model.Subscription
import com.example.model.SubscriptionPlan
import com.example.model.SubscriptionRequest
import com.example.model.sortedBySerialOrder
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.classTitle
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldDark
import com.example.ui.theme.RoyalBlue
import java.text.SimpleDateFormat
import java.util.Locale

fun paymentBrandColor(methodType: String): Color = when (methodType) {
    PaymentMethodConstants.TYPE_BKASH -> Color(0xFFE2136E)
    PaymentMethodConstants.TYPE_NAGAD -> Color(0xFFF7941D)
    PaymentMethodConstants.TYPE_ROCKET -> Color(0xFF8C3494)
    PaymentMethodConstants.TYPE_BANK_TRANSFER -> RoyalBlue
    else -> RoyalBlue
}

// =========================================================================
// 1. ADMIN PAYMENT METHOD MANAGEMENT (bKash, Nagad, Rocket, Bank Transfer)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminPaymentMethodsModule(
    paymentMethods: List<PaymentMethodConfig>,
    onSave: (PaymentMethodConfig) -> Unit,
    onToggleActive: (PaymentMethodConfig, Boolean) -> Unit,
    onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<PaymentMethodConfig?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PaymentMethodConfig?>(null) }

    val activeCount = remember(paymentMethods) { paymentMethods.count { it.isActive } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_payment_methods_module")
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "পেমেন্ট মাধ্যম ব্যবস্থাপনা",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "মোট মাধ্যম: ${paymentMethods.size} • সক্রিয়: $activeCount (শিক্ষার্থীদের পেমেন্ট স্ক্রিনে শুধু সক্রিয় মাধ্যম দেখা যাবে)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                editing = PaymentMethodConfig(
                                    methodType = PaymentMethodConstants.TYPE_BKASH,
                                    methodName = "bKash",
                                    accountType = "Personal",
                                    transactionType = PaymentMethodConstants.TRX_SEND_MONEY,
                                    displayOrder = paymentMethods.size + 1,
                                    isActive = true
                                )
                                showEditor = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White),
                            modifier = Modifier.testTag("admin_add_payment_method_btn")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("নতুন পেমেন্ট মাধ্যম যোগ করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (paymentMethods.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = GoldDark.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "এখনো কোনো পেমেন্ট মাধ্যম সংরক্ষিত হয়নি",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldDark
                        )
                        Text(
                            text = "উপরের 'নতুন পেমেন্ট মাধ্যম যোগ করুন' বাটনে ট্যাপ করে bKash, Nagad, Rocket অথবা Bank Transfer নম্বর, Transaction Type ও পেমেন্ট নির্দেশনা যুক্ত করুন। সংরক্ষণের সাথে সাথেই শিক্ষার্থীদের পেমেন্ট স্ক্রিনে রিয়েল-টাইমে তা প্রদর্শিত হবে।",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        itemsIndexed(paymentMethods, key = { idx, m -> m.id.ifBlank { "pm_$idx" } }) { _, method ->
            val brandColor = paymentBrandColor(method.methodType)
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(brandColor.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (method.isBankTransfer) Icons.Filled.AccountBalance else Icons.Filled.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = brandColor
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = method.methodName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = brandColor
                                    )
                                    Surface(
                                        color = brandColor.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = method.transactionType,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = brandColor,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${PaymentMethodConstants.accountTypeBn(method.accountType)} • ক্রম: #${method.displayOrder}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = method.isActive,
                                onCheckedChange = { onToggleActive(method, it) }
                            )
                            IconButton(onClick = { editing = method; showEditor = true }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit Payment Method")
                            }
                            IconButton(onClick = { deleteTarget = method }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Payment Method", tint = ErrorRed)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                    Text(
                        text = if (method.isBankTransfer) {
                            "ব্যাংক: ${method.bankName} • হিসাব নম্বর: ${method.accountNumber}"
                        } else {
                            "পেমেন্ট নম্বর: ${method.accountNumber}" +
                                if (method.accountHolderName.isNotBlank()) " (${method.accountHolderName})" else ""
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (method.isBankTransfer) {
                        Text(
                            text = buildString {
                                append("হিসাবধারীর নাম: ${method.accountHolderName}")
                                if (method.branchName.isNotBlank()) append(" • শাখা: ${method.branchName}")
                                if (method.routingNumber.isNotBlank()) append(" • Routing: ${method.routingNumber}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (method.paymentInstructions.isNotBlank()) {
                        Text(
                            text = "নির্দেশনা: ${method.paymentInstructions}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showEditor && editing != null) {
        PaymentMethodEditorDialog(
            initial = editing!!,
            onDismiss = { showEditor = false },
            onSave = { updated ->
                onSave(updated)
                showEditor = false
            }
        )
    }

    deleteTarget?.let { target ->
        DestructiveConfirmDialog(
            title = "পেমেন্ট মাধ্যম মুছে ফেলবেন?",
            message = "\"${target.methodName} (${target.accountNumber})\" স্থায়ীভাবে মুছে ফেলা হবে। সাময়িকভাবে লুকাতে চাইলে সুইচ অফ (Inactive) করতে পারেন।",
            onConfirm = {
                onDelete(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentMethodEditorDialog(
    initial: PaymentMethodConfig,
    onDismiss: () -> Unit,
    onSave: (PaymentMethodConfig) -> Unit
) {
    var methodType by remember(initial) { mutableStateOf(initial.methodType) }
    var methodName by remember(initial) { mutableStateOf(initial.methodName) }
    var accountNumber by remember(initial) { mutableStateOf(initial.accountNumber) }
    var accountHolderName by remember(initial) { mutableStateOf(initial.accountHolderName) }
    var accountType by remember(initial) { mutableStateOf(initial.accountType) }
    var transactionType by remember(initial) { mutableStateOf(initial.transactionType) }
    var bankName by remember(initial) { mutableStateOf(initial.bankName) }
    var branchName by remember(initial) { mutableStateOf(initial.branchName) }
    var routingNumber by remember(initial) { mutableStateOf(initial.routingNumber) }
    var instructions by remember(initial) { mutableStateOf(initial.paymentInstructions) }
    var displayOrderStr by remember(initial) { mutableStateOf(initial.displayOrder.toString()) }
    var isActive by remember(initial) { mutableStateOf(initial.isActive) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val allowedTrxTypes = PaymentMethodConstants.allowedTransactionTypes(methodType)
    val allowedAccountTypes = PaymentMethodConstants.allowedAccountTypes(methodType)
    val isBank = methodType == PaymentMethodConstants.TYPE_BANK_TRANSFER

    LaunchedEffect(methodType) {
        if (transactionType !in allowedTrxTypes) {
            transactionType = allowedTrxTypes.first()
        }
        if (accountType !in allowedAccountTypes) {
            accountType = allowedAccountTypes.first()
        }
    }

    FormEditorDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initial.id.isBlank()) "নতুন পেমেন্ট মাধ্যম যোগ করুন" else "পেমেন্ট মাধ্যম সম্পাদনা করুন",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (validationError != null) {
                    Surface(
                        color = ErrorRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError!!,
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Text("১. পেমেন্ট মাধ্যম নির্বাচন করুন *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMethodConstants.supportedMethodTypes.forEach { t ->
                        FilterChip(
                            selected = methodType == t,
                            onClick = {
                                methodType = t
                                methodName = PaymentMethodConstants.defaultMethodName(t)
                                accountType = PaymentMethodConstants.allowedAccountTypes(t).first()
                                transactionType = PaymentMethodConstants.allowedTransactionTypes(t).first()
                            },
                            label = { Text(PaymentMethodConstants.methodTitleBn(t)) }
                        )
                    }
                }

                OutlinedTextField(
                    value = methodName,
                    onValueChange = { methodName = it },
                    label = { Text("প্রদর্শিত মাধ্যমের নাম (যেমন: bKash / Nagad / Bank Transfer) *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("২. Transaction Type নির্বাচন করুন *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    allowedTrxTypes.forEach { trx ->
                        FilterChip(
                            selected = transactionType == trx,
                            onClick = { transactionType = trx },
                            label = { Text(trx) }
                        )
                    }
                }

                if (!isBank) {
                    Text("৩. অ্যাকাউন্টের ধরন (Account Type) *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        allowedAccountTypes.forEach { acc ->
                            FilterChip(
                                selected = accountType == acc,
                                onClick = { accountType = acc },
                                label = { Text(PaymentMethodConstants.accountTypeBn(acc)) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("রিসিভার মোবাইল নম্বর (Account Number) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = accountHolderName,
                        onValueChange = { accountHolderName = it },
                        label = { Text("অ্যাকাউন্ট হোল্ডারের নাম (ঐচ্ছিক)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("ব্যাংকের নাম (Bank Name) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = accountHolderName,
                        onValueChange = { accountHolderName = it },
                        label = { Text("হিসাবধারীর নাম (Account Holder Name) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { accountNumber = it },
                        label = { Text("ব্যাংক হিসাব নম্বর (Bank Account Number) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = branchName,
                            onValueChange = { branchName = it },
                            label = { Text("শাখার নাম (Branch)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = routingNumber,
                            onValueChange = { routingNumber = it },
                            label = { Text("Routing Number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("পেমেন্ট নির্দেশনা (Payment Instructions)") },
                    placeholder = {
                        Text(
                            if (isBank) "প্রদত্ত ব্যাংক হিসাবে নির্ধারিত ফি ট্রান্সফার করে রেফারেন্স নম্বর জমা দিন।"
                            else "উপরের নম্বরে নির্ধারিত ফি $transactionType করে প্রাপ্ত Transaction ID জমা দিন।"
                        )
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = displayOrderStr,
                        onValueChange = { displayOrderStr = it },
                        label = { Text("প্রদর্শন ক্রম (Order)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isActive) "সক্রিয়" else "নিষ্ক্রিয়", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (accountNumber.trim().length < 4) {
                        validationError = "সঠিক অ্যাকাউন্ট / মোবাইল নম্বর প্রদান করুন।"
                        return@Button
                    }
                    if (isBank && bankName.trim().length < 2) {
                        validationError = "ব্যাংক ট্রান্সফারের জন্য ব্যাংকের নাম আবশ্যক।"
                        return@Button
                    }
                    if (isBank && accountHolderName.trim().length < 2) {
                        validationError = "ব্যাংক ট্রান্সফারের জন্য হিসাবধারীর নাম আবশ্যক।"
                        return@Button
                    }
                    onSave(
                        initial.copy(
                            methodType = methodType,
                            methodName = methodName.trim().ifBlank { PaymentMethodConstants.defaultMethodName(methodType) },
                            accountNumber = accountNumber.trim(),
                            accountHolderName = accountHolderName.trim(),
                            accountType = accountType,
                            transactionType = transactionType,
                            bankName = if (isBank) bankName.trim() else "",
                            branchName = if (isBank) branchName.trim() else "",
                            routingNumber = if (isBank) routingNumber.trim() else "",
                            paymentInstructions = instructions.trim(),
                            isActive = isActive,
                            displayOrder = displayOrderStr.toIntOrNull()?.coerceIn(0, 1000) ?: 1
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White)
            ) {
                Text("সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("বাতিল") }
        }
    )
}

// =========================================================================
// 2. ADMIN PAYMENT NOTIFICATION CENTER
// =========================================================================

@Composable
fun AdminPaymentNotificationsModule(
    notifications: List<AppNotification>,
    requests: List<SubscriptionRequest>,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: (List<AppNotification>) -> Unit,
    onInspectPaymentRequest: (String) -> Unit
) {
    val dateTimeFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    val pendingCount = remember(requests) { requests.count { it.status == "PENDING" } }
    val unreadCount = remember(notifications) { notifications.count { !it.isRead } }
    val requestById = remember(requests) { requests.associateBy { it.id } }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_payment_notifications_module")
    ) {
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "পেমেন্ট নোটিফিকেশন কেন্দ্র",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "অপেক্ষমাণ পেমেন্ট (Pending): $pendingCount • অপঠিত নোটিফিকেশন: $unreadCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pendingCount > 0) ErrorRed else EmeraldDark,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (unreadCount > 0) {
                            OutlinedButton(onClick = { onMarkAllRead(notifications) }) {
                                Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("সব পঠিত")
                            }
                        }
                    }
                }
            }
        }

        if (notifications.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "এখনো কোনো পেমেন্ট নোটিফিকেশন আসেনি",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "শিক্ষার্থীরা পেমেন্ট রিকোয়েস্ট জমা দিলেই সাথে সাথে এখানে নাম, প্ল্যান, টাকার পরিমাণ ও Transaction ID সহ নোটিফিকেশন চলে আসবে।",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        itemsIndexed(notifications, key = { idx, n -> n.id.ifBlank { "an_$idx" } }) { _, notif ->
            val linkedReq = requestById[notif.relatedPaymentRequestId]
            val reqStatus = linkedReq?.status ?: "PENDING"
            val statusColor = when (reqStatus) {
                "APPROVED" -> EmeraldDark
                "REJECTED" -> ErrorRed
                else -> GoldDark
            }
            val timeStr = notif.createdAt?.toDate()?.let { dateTimeFormatter.format(it) } ?: "এইমাত্র"

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!notif.isRead) onMarkRead(notif.id)
                        if (notif.relatedPaymentRequestId.isNotBlank()) {
                            onInspectPaymentRequest(notif.relatedPaymentRequestId)
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!notif.isRead) {
                        RoyalBlue.copy(alpha = 0.08f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = if (!notif.isRead) ErrorRed else RoyalBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = notif.studentName.ifBlank { linkedReq?.userName ?: notif.title },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Surface(
                            color = statusColor.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = when (reqStatus) {
                                    "APPROVED" -> "অনুমোদিত"
                                    "REJECTED" -> "বাতিল"
                                    else -> "যাচাই বাকি (PENDING)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = statusColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = "প্ল্যান: ${notif.planName.ifBlank { linkedReq?.planNameBn ?: "-" }} • পরিমাণ: ৳${if (notif.amountBdt > 0) notif.amountBdt else (linkedReq?.amountBdt ?: 0)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "মাধ্যম: ${notif.paymentMethod.ifBlank { linkedReq?.paymentMethod ?: "-" }} • TrxID: ${notif.transactionId.ifBlank { linkedReq?.transactionReference ?: "-" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoyalBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "জমার সময়: $timeStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (notif.relatedPaymentRequestId.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, tint = EmeraldDark, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "বিস্তারিত যাচাই করুন",
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
    }
}

// =========================================================================
// 3. ADMIN SUBSCRIPTION APPROVAL, PAYMENT HISTORY & PLAN MANAGEMENT
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminSubscriptionManagementModule(
    plans: List<SubscriptionPlan>,
    classes: List<SchoolClass> = emptyList(),
    educationLevels: List<EducationLevel> = emptyList(),
    requests: List<SubscriptionRequest>,
    subscriptions: List<Subscription>,
    focusedRequestId: String?,
    onClearFocusedRequest: () -> Unit,
    onSavePlan: (SubscriptionPlan) -> Unit,
    onDeletePlan: (String) -> Unit,
    onApproveRequest: (SubscriptionRequest, String) -> Unit,
    onRejectRequest: (SubscriptionRequest, String) -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var editingPlan by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var showPlanDialog by remember { mutableStateOf(false) }
    var deletePlanTarget by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var inspectingRequest by remember { mutableStateOf<SubscriptionRequest?>(null) }
    var subFilterClass by remember { mutableIntStateOf(0) }
    var subFilterStream by remember { mutableStateOf("ALL") }
    var subFilterStatus by remember { mutableStateOf("ALL") }
    val sortedClasses = remember(classes) {
        (classes.ifEmpty { DefaultCatalogSeed.defaultClasses }).sortedBySerialOrder()
    }
    val sortedLevels = remember(educationLevels) {
        educationLevels.sortedWith(compareBy<EducationLevel> { it.sortOrder }.thenBy { it.minClassNumber })
    }

    LaunchedEffect(focusedRequestId, requests) {
        if (!focusedRequestId.isNullOrBlank()) {
            val found = requests.firstOrNull { it.id == focusedRequestId }
            if (found != null) {
                inspectingRequest = found
                onClearFocusedRequest()
            }
        }
    }

    val pendingRequests = remember(requests) { requests.filter { it.status == "PENDING" } }
    val approvedRequests = remember(requests) { requests.filter { it.status == "APPROVED" } }
    val rejectedRequests = remember(requests) { requests.filter { it.status == "REJECTED" } }
    val trxCountMap = remember(requests) {
        requests.groupingBy {
            it.normalizedTrxId.ifBlank { PaymentMethodConstants.normalizeTransactionId(it.transactionReference) }
        }.eachCount()
    }
    val dateTimeFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("অপেক্ষমাণ (${pendingRequests.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("অনুমোদিত (${approvedRequests.size})") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("বাতিল (${rejectedRequests.size})") })
            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("সকল ইতিহাস (${requests.size})") })
            Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("প্ল্যানসমূহ (${plans.size})") })
            Tab(selected = tab == 5, onClick = { tab = 5 }, text = { Text("সক্রিয় সদস্য (${subscriptions.count { it.isCurrentlyActive() }})") })
        }

        when (tab) {
            0, 1, 2, 3 -> {
                val filtered = when (tab) {
                    0 -> pendingRequests
                    1 -> approvedRequests
                    2 -> rejectedRequests
                    else -> requests
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Text(
                                    text = "এই তালিকায় বর্তমানে কোনো পেমেন্ট রিকোয়েস্ট নেই।",
                                    modifier = Modifier.padding(18.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    itemsIndexed(filtered, key = { idx, it -> it.id.ifBlank { "req_$idx" } }) { _, req ->
                        val normKey = req.normalizedTrxId.ifBlank {
                            PaymentMethodConstants.normalizeTransactionId(req.transactionReference)
                        }
                        val isDuplicateTrx = (trxCountMap[normKey] ?: 0) > 1
                        val statusColor = when (req.status) {
                            "APPROVED" -> EmeraldDark
                            "REJECTED" -> ErrorRed
                            else -> GoldDark
                        }
                        val submittedStr = req.createdAt?.toDate()?.let { dateTimeFormatter.format(it) } ?: "-"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { inspectingRequest = req },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${req.userName}${if (req.userEmail.isNotBlank()) " (${req.userEmail})" else ""}", fontWeight = FontWeight.ExtraBold)
                                        Text("Student UID: ${req.userId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${req.planNameBn} • শ্রেণি ${req.classNumber} (${req.stream}) • ${req.durationMonths} মাস", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
                                        Text(
                                            text = when (req.status) {
                                                "APPROVED" -> "APPROVED"
                                                "REJECTED" -> "REJECTED"
                                                else -> "PENDING"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "${req.paymentMethod} (${req.transactionType}) • ৳${req.amountBdt} • TrxID: ${req.transactionReference}",
                                    fontWeight = FontWeight.Bold,
                                    color = RoyalBlue
                                )
                                Text(
                                    text = buildString {
                                        if (req.senderNumber.isNotBlank()) append("প্রেরক: ${req.senderNumber} • ")
                                        if (req.receiverNumber.isNotBlank()) append("রিসিভার: ${req.receiverNumber} • ")
                                        append("জমা: $submittedStr")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (req.screenshotUrl.isNotBlank()) {
                                    Surface(color = RoyalBlue.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Image, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(13.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "পেমেন্ট স্ক্রিনশট সংযুক্ত",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = RoyalBlue,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (isDuplicateTrx) {
                                    Surface(color = ErrorRed.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "সতর্কতা: একই Transaction ID একাধিক রিকোয়েস্টে পাওয়া গেছে!",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ErrorRed,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (req.status == "REJECTED" && req.effectiveRejectionReason.isNotBlank()) {
                                    Text(
                                        text = "বাতিলের কারণ: ${req.effectiveRejectionReason}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ErrorRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = { inspectingRequest = req }) {
                                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("বিস্তারিত ও যাচাই")
                                    }

                                    if (req.status == "PENDING") {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { inspectingRequest = req },
                                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White)
                                            ) {
                                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("যাচাই / অনুমোদন")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            4 -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "সাবস্ক্রিপশন প্যাকেজ (${plans.size})",
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = {
                                            val defaultCls = sortedClasses.firstOrNull()?.classNumber ?: 11
                                            editingPlan = SubscriptionPlan(
                                                id = "",
                                                nameBn = "",
                                                nameEn = "",
                                                descriptionBn = "নির্বাচিত শ্রেণির সকল মূল পাঠ্যবই ও গাইড আনলক",
                                                classNumber = defaultCls,
                                                stream = "ALL",
                                                durationMonths = 12,
                                                priceBdt = 300,
                                                isActive = true
                                            )
                                            showPlanDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                                        modifier = Modifier.testTag("admin_add_plan_btn")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("নতুন প্যাকেজ")
                                    }
                                }
                                Text(
                                    text = "শ্রেণি ১–৩০ এবং শিক্ষা স্তর (SSC, HSC, Graduate) ভিত্তিক যেকোনো মেয়াদের প্যাকেজ তৈরি, সম্পাদনা, সক্রিয়/নিষ্ক্রিয় বা মুছে ফেলতে পারেন।",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    itemsIndexed(plans, key = { idx, it -> it.id.ifBlank { "plan_$idx" } }) { _, p ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(p.nameBn.ifBlank { p.nameEn }, fontWeight = FontWeight.Bold)
                                    val targetLabel = if (p.classNumber == 0) {
                                        p.educationLevelNameBn.ifBlank { "সকল / স্তর ভিত্তিক" }
                                    } else {
                                        val clsName = classTitle(p.classNumber, AppLanguage.BN)
                                        val levelSuffix = if (p.educationLevelNameBn.isNotBlank()) " (${p.educationLevelNameBn})" else ""
                                        "$clsName$levelSuffix"
                                    }
                                    val streamLabel = if (p.classNumber in 9..10 && p.stream != "ALL") " • বিভাগ: ${p.stream}" else ""
                                    val durationLabel = if (p.durationMonths == 12) "১২ মাস / বার্ষিক" else "${p.durationMonths} মাস"
                                    Text(
                                        text = "$targetLabel$streamLabel • $durationLabel • ৳${p.priceBdt}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (p.isActive) EmeraldDark else ErrorRed,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (p.descriptionBn.isNotBlank()) {
                                        Text(p.descriptionBn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Switch(
                                    checked = p.isActive,
                                    onCheckedChange = { onSavePlan(p.copy(isActive = it)) }
                                )
                                IconButton(onClick = { editingPlan = p; showPlanDialog = true }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit Plan")
                                }
                                IconButton(
                                    onClick = { deletePlanTarget = p },
                                    modifier = Modifier.testTag("admin_delete_plan_${p.id}")
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Plan", tint = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                val filteredSubscriptions = remember(subscriptions, subFilterClass, subFilterStream, subFilterStatus) {
                    subscriptions.filter { s ->
                        val matchesClass = subFilterClass == 0 || s.classNumber == subFilterClass
                        val matchesStream = subFilterStream == "ALL" || s.stream.equals(subFilterStream, ignoreCase = true)
                        val matchesStatus = when (subFilterStatus) {
                            "ACTIVE" -> s.isCurrentlyActive()
                            "EXPIRED" -> !s.isCurrentlyActive()
                            else -> true
                        }
                        matchesClass && matchesStream && matchesStatus
                    }
                }
                val memberFilterClasses = remember(sortedClasses, subscriptions) {
                    (sortedClasses.map { it.classNumber } + subscriptions.map { it.classNumber }.filter { it > 0 })
                        .distinct()
                        .sorted()
                }
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("শ্রেণি ও বিভাগ অনুযায়ী সাবস্ক্রিপশন ফিল্টার", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(selected = subFilterClass == 0, onClick = { subFilterClass = 0 }, label = { Text("সব শ্রেণি") })
                                    memberFilterClasses.forEach { c ->
                                        FilterChip(selected = subFilterClass == c, onClick = { subFilterClass = c }, label = { Text("Class $c") })
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("ALL" to "সব বিভাগ", "SCIENCE" to "বিজ্ঞান", "HUMANITIES" to "মানবিক", "BUSINESS" to "ব্যবসায় শিক্ষা").forEach { (code, title) ->
                                        FilterChip(selected = subFilterStream == code, onClick = { subFilterStream = code }, label = { Text(title) })
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(
                                        "ALL" to "সব স্ট্যাটাস",
                                        "ACTIVE" to "সক্রিয় (${subscriptions.count { it.isCurrentlyActive() }})",
                                        "EXPIRED" to "মেয়াদোত্তীর্ণ (${subscriptions.count { !it.isCurrentlyActive() }})"
                                    ).forEach { (code, title) ->
                                        FilterChip(selected = subFilterStatus == code, onClick = { subFilterStatus = code }, label = { Text(title) })
                                    }
                                }
                                Text(
                                    text = "মোট প্রদর্শিত সদস্য: ${filteredSubscriptions.size} জন",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldDark,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (filteredSubscriptions.isEmpty()) {
                        item {
                            Text(
                                text = "নির্বাচিত ফিল্টারে কোনো সাবস্ক্রিপশন পাওয়া যায়নি।",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        itemsIndexed(filteredSubscriptions, key = { idx, it -> it.id.ifBlank { "sub_$idx" } }) { _, s ->
                            val active = s.isCurrentlyActive()
                            val startStr = s.startDate?.toDate()?.let { dateTimeFormatter.format(it) } ?: "-"
                            val expStr = s.expiryDate?.toDate()?.let { dateTimeFormatter.format(it) } ?: "-"
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(s.planNameBn.ifBlank { "শ্রেণি ${s.classNumber} সাবস্ক্রিপশন" }, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (active) "ACTIVE (${s.remainingDays()} দিন বাকি)" else "EXPIRED",
                                            color = if (active) EmeraldDark else ErrorRed,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                    Text("শিক্ষার্থী UID: ${s.userId}", style = MaterialTheme.typography.labelSmall)
                                    Text("শ্রেণি ${s.classNumber} (${s.stream}) • শুরু: $startStr • শেষ: $expStr", style = MaterialTheme.typography.bodySmall)
                                    if (s.transactionReference.isNotBlank()) {
                                        Text("TrxID: ${s.transactionReference} • ফি: ৳${s.amountBdt}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inspectingRequest?.let { req ->
        val normKey = req.normalizedTrxId.ifBlank {
            PaymentMethodConstants.normalizeTransactionId(req.transactionReference)
        }
        val duplicateMatches = requests.filter {
            it.id != req.id && (
                it.normalizedTrxId.ifBlank { PaymentMethodConstants.normalizeTransactionId(it.transactionReference) } == normKey
                )
        }
        val matchingPlan = plans.firstOrNull { it.id == req.planId }

        PaymentRequestVerificationDialog(
            request = req,
            matchingPlan = matchingPlan,
            duplicateRequests = duplicateMatches,
            onDismiss = { inspectingRequest = null },
            onApprove = { note ->
                onApproveRequest(req, note)
                inspectingRequest = null
            },
            onReject = { reason ->
                onRejectRequest(req, reason)
                inspectingRequest = null
            }
        )
    }

    deletePlanTarget?.let { target ->
        DestructiveConfirmDialog(
            title = "প্যাকেজ মুছে ফেলবেন?",
            message = "\"${target.nameBn.ifBlank { target.nameEn }}\" প্যাকেজটি তালিকা থেকে মুছে ফেলা হবে। পূর্ববর্তী শিক্ষার্থীদের সাবস্ক্রিপশন ও পেমেন্ট রেকর্ড অক্ষত থাকবে।",
            onConfirm = {
                val id = target.id
                deletePlanTarget = null
                if (id.isNotBlank()) onDeletePlan(id)
            },
            onDismiss = { deletePlanTarget = null }
        )
    }

    if (showPlanDialog && editingPlan != null) {
        val initial = editingPlan!!
        val isCreating = initial.id.isBlank()
        var nameBn by remember(initial) { mutableStateOf(initial.nameBn) }
        var nameEn by remember(initial) { mutableStateOf(initial.nameEn) }
        var descriptionBn by remember(initial) { mutableStateOf(initial.descriptionBn.ifBlank { "মূল পাঠ্যবই + অধ্যায়ভিত্তিক গাইড ও নোট আনলক" }) }
        var benefitsRaw by remember(initial) {
            mutableStateOf(
                initial.benefits.joinToString("\n").ifBlank {
                    "নির্বাচিত শ্রেণির সকল মূল পাঠ্যবই আনলক\nঅধ্যায়ভিত্তিক গাইড ও নোট আনলক\nমডেল টেস্ট ও পরীক্ষার প্রস্তুতি"
                }
            )
        }
        var cls by remember(initial) { mutableIntStateOf(initial.classNumber) }
        var selectedLevelId by remember(initial) { mutableStateOf(initial.educationLevelId) }
        var stream by remember(initial) { mutableStateOf(initial.stream.ifBlank { "ALL" }) }
        var months by remember(initial) { mutableIntStateOf(initial.durationMonths.takeIf { it in listOf(1, 3, 6, 12) } ?: 12) }
        var price by remember(initial) { mutableStateOf(initial.priceBdt.toString()) }
        var isActive by remember(initial) { mutableStateOf(initial.isActive) }
        var planError by remember(initial) { mutableStateOf<String?>(null) }

        val classHasStreams = cls in 9..10 || (sortedClasses.firstOrNull { it.classNumber == cls }?.hasStreams == true)
        val selectableClasses = remember(sortedClasses, cls) {
            val base = sortedClasses.map { it.classNumber }.toMutableList()
            if (cls > 0 && cls !in base) base.add(cls)
            base.distinct().sorted()
        }

        FormEditorDialog(
            onDismissRequest = { showPlanDialog = false },
            title = {
                Text(
                    text = if (isCreating) "নতুন সাবস্ক্রিপশন প্যাকেজ তৈরি" else "সাবস্ক্রিপশন প্যাকেজ সম্পাদনা",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            content = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!planError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = planError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Text("লক্ষ্য শ্রেণি নির্বাচন করুন *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = cls == 0,
                            onClick = {
                                cls = 0
                                stream = "ALL"
                                planError = null
                            },
                            label = { Text("স্তর ভিত্তিক (Level-Wide)") }
                        )
                        selectableClasses.forEach { c ->
                            FilterChip(
                                selected = cls == c,
                                onClick = {
                                    cls = c
                                    if (c !in 9..10 && sortedClasses.firstOrNull { it.classNumber == c }?.hasStreams != true) {
                                        stream = "ALL"
                                    }
                                    planError = null
                                },
                                label = { Text("Class $c") }
                            )
                        }
                    }

                    if (sortedLevels.isNotEmpty()) {
                        Text(
                            text = if (cls == 0) "শিক্ষা স্তর (বাধ্যতামূলক) *" else "শিক্ষা স্তর (ঐচ্ছিক)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (cls > 0) {
                                FilterChip(
                                    selected = selectedLevelId.isBlank(),
                                    onClick = { selectedLevelId = "" },
                                    label = { Text("শ্রেণি ভিত্তিক") }
                                )
                            }
                            sortedLevels.forEach { lvl ->
                                FilterChip(
                                    selected = selectedLevelId == lvl.id,
                                    onClick = {
                                        selectedLevelId = lvl.id
                                        planError = null
                                    },
                                    label = { Text(lvl.nameBn.ifBlank { lvl.code }) }
                                )
                            }
                        }
                    }

                    if (classHasStreams) {
                        Text("বিভাগ (Stream)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("ALL" to "সকল বিভাগ", "SCIENCE" to "বিজ্ঞান", "HUMANITIES" to "মানবিক", "BUSINESS" to "ব্যবসায়").forEach { (code, lbl) ->
                                FilterChip(selected = stream == code, onClick = { stream = code }, label = { Text(lbl) })
                            }
                        }
                    }

                    Text("মেয়াদ (Duration) *", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1 to "১ মাস", 3 to "৩ মাস", 6 to "৬ মাস", 12 to "১২ মাস (বার্ষিক)").forEach { (m, lbl) ->
                            FilterChip(selected = months == m, onClick = { months = m }, label = { Text(lbl) })
                        }
                    }

                    OutlinedTextField(
                        value = price,
                        onValueChange = {
                            price = it.filter { ch -> ch.isDigit() }
                            planError = null
                        },
                        label = { Text("প্যাকেজ মূল্য (BDT) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameBn,
                        onValueChange = {
                            nameBn = it
                            planError = null
                        },
                        label = { Text("প্যাকেজের প্রদর্শিত নাম (বাংলা) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameEn,
                        onValueChange = { nameEn = it },
                        label = { Text("Package Name (English)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = descriptionBn,
                        onValueChange = { descriptionBn = it },
                        label = { Text("সংক্ষিপ্ত বিবরণ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = benefitsRaw,
                        onValueChange = { benefitsRaw = it },
                        label = { Text("সুবিধাসমূহ (প্রতি লাইনে একটি)") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilterChip(
                        selected = isActive,
                        onClick = { isActive = !isActive },
                        label = { Text(if (isActive) "প্যাকেজটি সক্রিয় (Active)" else "প্যাকেজটি নিষ্ক্রিয় (Inactive)") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsedPrice = price.trim().toIntOrNull()
                    val chosenLevel = sortedLevels.firstOrNull { it.id == selectedLevelId }
                    val effectiveNameBn = nameBn.trim().ifBlank {
                        when {
                            cls == 0 && chosenLevel != null -> "${chosenLevel.nameBn} সাবস্ক্রিপশন"
                            cls > 0 -> "${classTitle(cls, AppLanguage.BN)} সাবস্ক্রিপশন"
                            else -> ""
                        }
                    }
                    when {
                        cls == 0 && chosenLevel == null ->
                            planError = "স্তর ভিত্তিক প্যাকেজের জন্য একটি শিক্ষা স্তর নির্বাচন করুন।"
                        effectiveNameBn.isBlank() ->
                            planError = "প্যাকেজের নাম লিখুন।"
                        parsedPrice == null || parsedPrice !in 1..100_000 ->
                            planError = "প্যাকেজের মূল্য ১ থেকে ১,০০,০০০ টাকার মধ্যে হতে হবে।"
                        else -> {
                            val parsedBenefits = benefitsRaw.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val effStream = if (classHasStreams) stream.ifBlank { "ALL" } else "ALL"
                            onSavePlan(
                                initial.copy(
                                    nameBn = effectiveNameBn,
                                    nameEn = nameEn.trim().ifBlank {
                                        if (cls == 0 && chosenLevel != null) "${chosenLevel.nameEn} Plan" else "Class $cls Plan"
                                    },
                                    descriptionBn = descriptionBn.trim(),
                                    benefits = parsedBenefits,
                                    classNumber = cls,
                                    stream = effStream,
                                    durationMonths = months,
                                    priceBdt = parsedPrice,
                                    isActive = isActive,
                                    educationLevelId = chosenLevel?.id.orEmpty(),
                                    educationLevelNameBn = chosenLevel?.nameBn.orEmpty(),
                                    educationLevelNameEn = chosenLevel?.nameEn.orEmpty()
                                )
                            )
                            showPlanDialog = false
                        }
                    }
                }) { Text("সংরক্ষণ করুন") }
            },
            dismissButton = { TextButton(onClick = { showPlanDialog = false }) { Text("বাতিল") } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentRequestVerificationDialog(
    request: SubscriptionRequest,
    matchingPlan: SubscriptionPlan?,
    duplicateRequests: List<SubscriptionRequest>,
    onDismiss: () -> Unit,
    onApprove: (note: String) -> Unit,
    onReject: (reason: String) -> Unit
) {
    val dateTimeFormatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US) }
    var approvalNote by remember(request) { mutableStateOf("পেমেন্ট যাচাই সম্পন্ন ও অনুমোদিত হয়েছে।") }
    var rejectionReason by remember(request) { mutableStateOf("") }
    var rejectMode by remember(request) { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val submittedStr = request.createdAt?.toDate()?.let { dateTimeFormatter.format(it) } ?: "-"
    val verifiedStr = request.verifiedAt?.toDate()?.let { dateTimeFormatter.format(it) } ?: "-"
    val expectedPrice = matchingPlan?.priceBdt ?: PaymentMethodConstants.canonicalRefPlanPrice(request.classNumber, request.durationMonths)
    val priceMatches = matchingPlan == null || request.amountBdt == expectedPrice

    FormEditorDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "পেমেন্ট যাচাই ও অনুমোদন প্যানেল",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (duplicateRequests.isNotEmpty()) {
                    Surface(
                        color = ErrorRed.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "ডুপ্লিকেট Transaction ID সতর্কতা!",
                                color = ErrorRed,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "এই Transaction ID (${request.transactionReference}) অন্য ${duplicateRequests.size}টি রিকোয়েস্টেও পাওয়া গেছে। ভালোভাবে যাচাই না করে অনুমোদন করবেন না।",
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        VerificationInfoRow("শিক্ষার্থীর নাম", request.userName)
                        VerificationInfoRow("শিক্ষার্থীর ইমেইল", request.userEmail.ifBlank { "-" })
                        VerificationInfoRow("Student UID", request.userId)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        VerificationInfoRow("সাবস্ক্রিপশন প্ল্যান", request.planNameBn)
                        VerificationInfoRow("প্যাকেজ ID", request.planId.ifBlank { "-" })
                        VerificationInfoRow("শ্রেণি ও মেয়াদ", "শ্রেণি ${request.classNumber} (${request.stream}) • ${request.durationMonths} মাস")
                        VerificationInfoRow(
                            "প্রদেয় টাকা (Payable)",
                            "৳${request.amountBdt}" + if (!priceMatches) " (প্ল্যান মূল্য: ৳$expectedPrice!)" else " (প্ল্যান মূল্যের সাথে মিল রয়েছে ✓)"
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        VerificationInfoRow("পেমেন্ট মাধ্যম", request.paymentMethod)
                        VerificationInfoRow("Transaction Type", request.transactionType.ifBlank { "-" })
                        VerificationInfoRow("রিসিভার নম্বর (Admin)", request.receiverNumber.ifBlank { "-" })
                        if (request.accountHolderName.isNotBlank()) {
                            VerificationInfoRow("হিসাবের তথ্য", request.accountHolderName)
                        }
                        VerificationInfoRow("প্রেরক নম্বর (Sender)", request.senderNumber.ifBlank { "-" })
                        VerificationInfoRow("Transaction ID", request.transactionReference)
                        VerificationInfoRow("জমার সময়", submittedStr)
                        VerificationInfoRow("বর্তমান স্ট্যাটাস", request.status)
                        if (request.verifiedBy.isNotBlank()) {
                            VerificationInfoRow("যাচাইকারী অ্যাডমিন", request.verifiedBy)
                            VerificationInfoRow("যাচাইয়ের সময়", verifiedStr)
                        }
                        if (request.status == "REJECTED" && request.effectiveRejectionReason.isNotBlank()) {
                            VerificationInfoRow("বাতিলের কারণ", request.effectiveRejectionReason)
                        }
                    }
                }

                PaymentScreenshotViewer(screenshotUrl = request.screenshotUrl)

                if (localError != null) {
                    Text(localError!!, color = ErrorRed, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }

                if (request.status == "PENDING") {
                    if (!rejectMode) {
                        OutlinedTextField(
                            value = approvalNote,
                            onValueChange = { approvalNote = it },
                            label = { Text("অনুমোদন নোট (ঐচ্ছিক)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text("বাতিল করার কারণ নির্বাচন বা লিখুন *", style = MaterialTheme.typography.labelLarge, color = ErrorRed, fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "ভুল Transaction ID প্রদান করা হয়েছে।",
                                "প্রদত্ত নম্বরে কোনো পেমেন্ট পাওয়া যায়নি।",
                                "নির্ধারিত সাবস্ক্রিপশন ফি সম্পূর্ণ পাঠানো হয়নি।",
                                "ডুপ্লিকেট বা পূর্বে ব্যবহৃত Transaction ID।"
                            ).forEach { preset ->
                                FilterChip(
                                    selected = rejectionReason == preset,
                                    onClick = { rejectionReason = preset },
                                    label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = rejectionReason,
                            onValueChange = { rejectionReason = it },
                            label = { Text("Rejection Reason (বাতিলের কারণ) *") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (request.status == "PENDING") {
                if (!rejectMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { rejectMode = true; localError = null }
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("REJECT", color = ErrorRed, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { onApprove(approvalNote) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White),
                            modifier = Modifier.testTag("admin_confirm_approve_btn")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("APPROVE PAYMENT", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { rejectMode = false; localError = null }) {
                            Text("ফিরে যান")
                        }
                        Button(
                            onClick = {
                                if (rejectionReason.trim().length < 4) {
                                    localError = "অনুগ্রহ করে বাতিল করার সুস্পষ্ট কারণ লিখুন।"
                                    return@Button
                                }
                                onReject(rejectionReason.trim())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                            modifier = Modifier.testTag("admin_confirm_reject_btn")
                        ) {
                            Text("বাতিল নিশ্চিত করুন", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Button(onClick = onDismiss) { Text("বন্ধ করুন") }
            }
        },
        dismissButton = {
            if (request.status == "PENDING") {
                TextButton(onClick = onDismiss) { Text("বন্ধ করুন") }
            }
        }
    )
}

@Composable
private fun VerificationInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
fun PaymentScreenshotViewer(
    screenshotUrl: String,
    modifier: Modifier = Modifier
) {
    if (screenshotUrl.isBlank()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = RoundedCornerShape(12.dp),
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "কোনো পেমেন্ট স্ক্রিনশট সংযুক্ত করা হয়নি (ঐচ্ছিক)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var showFullScreenDialog by remember { mutableStateOf(false) }

    val bitmap = remember(screenshotUrl) {
        if (screenshotUrl.startsWith("data:image/", ignoreCase = true)) {
            try {
                val base64Data = screenshotUrl.substringAfter("base64,")
                val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Image, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "পেমেন্ট স্ক্রিনশট (Payment Proof)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = { showFullScreenDialog = true }) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("বড় করে দেখুন")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.05f))
                    .clickable { showFullScreenDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Payment Screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    val staticUrl = resolveFirebaseStorageUrl(screenshotUrl)
                    AsyncImage(
                        model = staticUrl,
                        contentDescription = "Payment Screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
    }

    if (showFullScreenDialog) {
        Dialog(onDismissRequest = { showFullScreenDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("স্ক্রিনশট পূর্ণাঙ্গ প্রিভিউ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { showFullScreenDialog = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Payment Screenshot",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        } else {
                            val staticUrl = resolveFirebaseStorageUrl(screenshotUrl)
                            AsyncImage(
                                model = staticUrl,
                                contentDescription = "Payment Screenshot",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.FillWidth
                            )
                        }
                    }
                }
            }
        }
    }
}
