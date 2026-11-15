package com.example.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.components.DestructiveConfirmDialog
import com.example.components.FormEditorDialog
import com.example.model.AppSettings
import com.example.model.DefaultCatalogSeed
import com.example.model.ExamQuestionItem
import com.example.model.ExamTest
import com.example.model.GeneralKnowledgeItem
import com.example.model.GuideNote
import com.example.model.Notice
import com.example.model.Subscription
import com.example.model.SubscriptionPlan
import com.example.model.SubscriptionRequest
import com.example.model.Textbook
import com.example.model.UserProfile
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.RoyalBlue

// --- F. General Knowledge Management ---
@Composable
fun AdminGkModule(
    gkItems: List<GeneralKnowledgeItem>,
    onSave: (GeneralKnowledgeItem) -> Unit,
    onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<GeneralKnowledgeItem?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GeneralKnowledgeItem?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("সাধারণ জ্ঞান ব্যবস্থাপনা (${gkItems.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { editing = GeneralKnowledgeItem(); showDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("নতুন প্রশ্ন")
                }
            }
        }
        itemsIndexed(gkItems, key = { idx, it -> it.id.ifBlank { "gk_$idx" } }) { _, item ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.questionBn, fontWeight = FontWeight.Bold)
                        Text("উত্তর: ${item.answerBn} • [${item.category}]", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { editing = item; showDialog = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { deleteTarget = item }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = ErrorRed) }
                }
            }
        }
    }

    if (showDialog && editing != null) {
        val g = editing!!
        var category by remember(g) { mutableStateOf(g.category) }
        var questionBn by remember(g) { mutableStateOf(g.questionBn) }
        var answerBn by remember(g) { mutableStateOf(g.answerBn) }
        var explanationBn by remember(g) { mutableStateOf(g.explanationBn) }
        var isPublished by remember(g) { mutableStateOf(g.isPublished) }

        FormEditorDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("সাধারণ জ্ঞান সংরক্ষণ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            content = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DefaultCatalogSeed.gkCategories.forEach { c ->
                            FilterChip(selected = category == c.code, onClick = { category = c.code }, label = { Text(c.nameBn) })
                        }
                    }
                    OutlinedTextField(questionBn, { questionBn = it }, label = { Text("প্রশ্ন (বাংলা) *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(answerBn, { answerBn = it }, label = { Text("সঠিক উত্তর *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(explanationBn, { explanationBn = it }, label = { Text("ব্যাখ্যা (ঐচ্ছিক)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    FilterChip(selected = isPublished, onClick = { isPublished = !isPublished }, label = { Text("প্রকাশিত") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (questionBn.isNotBlank() && answerBn.isNotBlank()) {
                        onSave(g.copy(category = category, questionBn = questionBn.trim(), answerBn = answerBn.trim(), explanationBn = explanationBn.trim(), isPublished = isPublished, isDemo = false))
                        showDialog = false
                    }
                }) { Text("সংরক্ষণ") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("বাতিল") } }
        )
    }

    deleteTarget?.let { t ->
        DestructiveConfirmDialog("প্রশ্ন মুছে ফেলবেন?", "\"${t.questionBn}\" মুছে ফেলা হবে।", { onDelete(t.id) }, { deleteTarget = null })
    }
}

// --- G. MCQ Examination & Protected Answer Key Management ---
private data class DraftQuestion(
    val q: String = "",
    val a: String = "",
    val b: String = "",
    val c: String = "",
    val d: String = "",
    val ansIdx: Int = 0,
    val exp: String = ""
)

@Composable
fun AdminExamsModule(
    exams: List<ExamTest>,
    classes: List<com.example.model.SchoolClass> = com.example.model.DefaultCatalogSeed.defaultClasses,
    onSave: (ExamTest, List<Int>, List<String>) -> Unit,
    onDelete: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ExamTest?>(null) }
    val classNumbers = remember(classes) {
        classes.ifEmpty { com.example.model.DefaultCatalogSeed.defaultClasses }
            .map { it.classNumber }
            .distinct()
            .sorted()
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("এমসিকিউ পরীক্ষা (${exams.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { showCreateDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White)) {
                    Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("নতুন পরীক্ষা তৈরি")
                }
            }
        }
        itemsIndexed(exams, key = { idx, it -> it.id.ifBlank { "ex_$idx" } }) { _, ex ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ex.titleBn, fontWeight = FontWeight.Bold)
                        Text("শ্রেণি ${ex.classNumber} • ${ex.subjectNameBn} • ${ex.questionCount} প্রশ্ন • ${ex.durationMinutes} মিনিট", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { deleteTarget = ex }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = ErrorRed) }
                }
            }
        }
    }

    if (showCreateDialog) {
        var titleBn by remember { mutableStateOf("") }
        var subjectNameBn by remember { mutableStateOf("") }
        var examType by remember { mutableStateOf("MODEL") }
        var cls by remember { mutableIntStateOf(classNumbers.firstOrNull() ?: 5) }
        var duration by remember { mutableStateOf("20") }
        var totalMarks by remember { mutableStateOf("20") }
        val drafts = remember { mutableStateListOf(DraftQuestion()) }

        FormEditorDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("নতুন MCQ পরীক্ষা ও সুরক্ষিত উত্তরপত্র", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            content = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(titleBn, { titleBn = it }, label = { Text("পরীক্ষার শিরোনাম *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(subjectNameBn, { subjectNameBn = it }, label = { Text("বিষয় *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MODEL", "SUBJECT", "CHAPTER").forEach { t ->
                            FilterChip(selected = examType == t, onClick = { examType = t }, label = { Text(t) })
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        classNumbers.forEach { c -> FilterChip(selected = cls == c, onClick = { cls = c }, label = { Text("Class $c") }) }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(duration, { duration = it }, label = { Text("সময় (মিনিট)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        OutlinedTextField(totalMarks, { totalMarks = it }, label = { Text("মোট নম্বর") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                    drafts.forEachIndexed { idx, dq ->
                        Text("প্রশ্ন ${idx + 1}", fontWeight = FontWeight.Bold, color = RoyalBlue)
                        OutlinedTextField(dq.q, { drafts[idx] = dq.copy(q = it) }, label = { Text("প্রশ্ন *") }, modifier = Modifier.fillMaxWidth())
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(dq.a, { drafts[idx] = dq.copy(a = it) }, label = { Text("A") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(dq.b, { drafts[idx] = dq.copy(b = it) }, label = { Text("B") }, modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(dq.c, { drafts[idx] = dq.copy(c = it) }, label = { Text("C") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(dq.d, { drafts[idx] = dq.copy(d = it) }, label = { Text("D") }, modifier = Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("A" to 0, "B" to 1, "C" to 2, "D" to 3).forEach { (lbl, i) ->
                                FilterChip(selected = dq.ansIdx == i, onClick = { drafts[idx] = dq.copy(ansIdx = i) }, label = { Text("সঠিক: $lbl") })
                            }
                        }
                        OutlinedTextField(dq.exp, { drafts[idx] = dq.copy(exp = it) }, label = { Text("ব্যাখ্যা") }, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(onClick = { drafts.add(DraftQuestion()) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(4.dp)); Text("আরও প্রশ্ন যোগ করুন")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val validQs = drafts.filter { it.q.isNotBlank() && it.a.isNotBlank() && it.b.isNotBlank() }
                    if (titleBn.isNotBlank() && subjectNameBn.isNotBlank() && validQs.isNotEmpty()) {
                        val qMaps = validQs.map {
                            ExamQuestionItem(it.q.trim(), it.a.trim(), it.b.trim(), it.c.trim().ifBlank { "-" }, it.d.trim().ifBlank { "-" })
                                .toMap().mapValues { v -> v.value.toString() }
                        }
                        val exam = ExamTest(
                            titleBn = titleBn.trim(),
                            examType = examType,
                            classNumber = cls,
                            subjectNameBn = subjectNameBn.trim(),
                            durationMinutes = duration.toIntOrNull()?.coerceIn(1, 240) ?: 20,
                            totalMarks = totalMarks.toIntOrNull()?.coerceIn(1, 500) ?: validQs.size,
                            questionCount = validQs.size,
                            questions = qMaps,
                            isPublished = true,
                            isDemo = false
                        )
                        onSave(exam, validQs.map { it.ansIdx }, validQs.map { it.exp.trim() })
                        showCreateDialog = false
                    }
                }) { Text("প্রকাশ করুন") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("বাতিল") } }
        )
    }

    deleteTarget?.let { t ->
        DestructiveConfirmDialog("পরীক্ষা মুছে ফেলবেন?", "\"${t.titleBn}\" এবং এর উত্তরপত্র মুছে যাবে।", { onDelete(t.id) }, { deleteTarget = null })
    }
}

// --- H. User Management (Never exposes passwords) ---
@Composable
fun AdminUsersModule(
    users: List<UserProfile>,
    subscriptions: List<Subscription>,
    onToggleActive: (String, Boolean) -> Unit
) {
    val activeSubUserIds = remember(subscriptions) {
        subscriptions.filter { it.isCurrentlyActive() }.map { it.userId }.toSet()
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("নিবন্ধিত শিক্ষার্থী ও ব্যবহারকারী (${users.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        itemsIndexed(users, key = { idx, it -> it.userId.ifBlank { it.id }.ifBlank { "user_$idx" } }) { _, u ->
            val hasSub = u.userId in activeSubUserIds
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${u.name} (${u.role})", fontWeight = FontWeight.Bold)
                        Text("${u.email} • শ্রেণি ${u.selectedClass} (${u.selectedStream})", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = (if (hasSub) "প্রিমিয়াম সক্রিয়" else "ফ্রি সদস্য") +
                                if (u.accountDeletionRequested) " • [অ্যাকাউন্ট ডিলিট অনুরোধ]" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (u.accountDeletionRequested) ErrorRed else EmeraldDark
                        )
                    }
                    if (u.role != "super_admin") {
                        Switch(checked = u.isActive, onCheckedChange = { onToggleActive(u.userId.ifBlank { u.id }, it) })
                    }
                }
            }
        }
    }
}

// --- J. Notice Management ---
@Composable
fun AdminNoticesModule(
    notices: List<Notice>,
    onSave: (Notice) -> Unit,
    onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<Notice?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("নোটিশ ব্যবস্থাপনা (${notices.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { editing = Notice(); showDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("নতুন নোটিশ")
                }
            }
        }
        itemsIndexed(notices, key = { idx, it -> it.id.ifBlank { "notice_$idx" } }) { _, n ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(n.titleBn, fontWeight = FontWeight.Bold)
                        Text(n.bodyBn, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { editing = n; showDialog = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { onDelete(n.id) }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = ErrorRed) }
                }
            }
        }
    }

    if (showDialog && editing != null) {
        val n = editing!!
        var titleBn by remember(n) { mutableStateOf(n.titleBn) }
        var bodyBn by remember(n) { mutableStateOf(n.bodyBn) }
        var isImportant by remember(n) { mutableStateOf(n.isImportant) }
        var isPublished by remember(n) { mutableStateOf(n.isPublished) }

        FormEditorDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("নোটিশ প্রকাশ / সম্পাদনা", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            content = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(titleBn, { titleBn = it }, label = { Text("শিরোনাম *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(bodyBn, { bodyBn = it }, label = { Text("বিস্তারিত নোটিশ *") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = isImportant, onClick = { isImportant = !isImportant }, label = { Text("জরুরি") })
                        FilterChip(selected = isPublished, onClick = { isPublished = !isPublished }, label = { Text("প্রকাশিত") })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (titleBn.isNotBlank() && bodyBn.isNotBlank()) {
                        onSave(n.copy(titleBn = titleBn.trim(), bodyBn = bodyBn.trim(), isImportant = isImportant, isPublished = isPublished))
                        showDialog = false
                    }
                }) { Text("সংরক্ষণ") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("বাতিল") } }
        )
    }
}

// --- K. Centralized Download & App Settings ---
@Composable
fun AdminSettingsModule(
    settings: AppSettings,
    onSave: (AppSettings) -> Unit
) {
    var downloadsEnabled by remember(settings) { mutableStateOf(settings.downloadsEnabled) }
    var allowTextbook by remember(settings) { mutableStateOf(settings.allowTextbookDownload) }
    var allowGuide by remember(settings) { mutableStateOf(settings.allowGuideDownload) }
    var allowNote by remember(settings) { mutableStateOf(settings.allowNoteDownload) }
    var banner by remember(settings) { mutableStateOf(settings.announcementBannerBn) }
    var email by remember(settings) { mutableStateOf(settings.contactEmail) }
    var phone by remember(settings) { mutableStateOf(settings.contactPhone) }
    var website by remember(settings) { mutableStateOf(settings.websiteUrl) }
    var privacy by remember(settings) { mutableStateOf(settings.privacyPolicyBn) }
    var terms by remember(settings) { mutableStateOf(settings.termsAndConditionsBn) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("কেন্দ্রীয় ডাউনলোড নিয়ন্ত্রণ ও অ্যাপ সেটিংস", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminToggleRow("মাস্টার ডাউনলোড সুইচ (Enable Downloads)", downloadsEnabled) { downloadsEnabled = it }
                AdminToggleRow("মূল পাঠ্যবই ডাউনলোড অনুমোদন", allowTextbook) { allowTextbook = it }
                AdminToggleRow("গাইড ডাউনলোড অনুমোদন", allowGuide) { allowGuide = it }
                AdminToggleRow("নোট ডাউনলোড অনুমোদন", allowNote) { allowNote = it }
            }
        }
        OutlinedTextField(banner, { banner = it }, label = { Text("হোম স্ক্রিন ঘোষণা ব্যানার") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("সাপোর্ট ইমেইল") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("সাপোর্ট ও পেমেন্ট ফোন নম্বর") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(website, { website = it }, label = { Text("ওয়েবসাইট URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(privacy, { privacy = it }, label = { Text("গোপনীয়তা নীতি") }, minLines = 3, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(terms, { terms = it }, label = { Text("শর্তাবলী") }, minLines = 3, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                onSave(
                    settings.copy(
                        downloadsEnabled = downloadsEnabled,
                        allowTextbookDownload = allowTextbook,
                        allowGuideDownload = allowGuide,
                        allowNoteDownload = allowNote,
                        announcementBannerBn = banner.trim(),
                        contactEmail = email.trim(),
                        contactPhone = phone.trim(),
                        websiteUrl = website.trim(),
                        privacyPolicyBn = privacy.trim(),
                        termsAndConditionsBn = terms.trim()
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("সেটিংস সংরক্ষণ করুন", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// --- L. Content Quick-Manager ---
@Composable
fun AdminQuickContentManager(
    books: List<Textbook>,
    guides: List<GuideNote>,
    onSaveBook: (Textbook) -> Unit,
    onSaveGuide: (GuideNote) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Text("কনটেন্ট কুইক-ম্যানেজার (দ্রুত ফ্রি/প্রিমিয়াম ও প্রকাশনা নিয়ন্ত্রণ)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        itemsIndexed(books, key = { idx, it -> "qb_${it.id}_$idx" }) { _, b ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📘 ${b.titleBn}", fontWeight = FontWeight.Bold)
                        Text("শ্রেণি ${b.classNumber}", style = MaterialTheme.typography.labelSmall)
                    }
                    FilterChip(selected = b.isPremium, onClick = { onSaveBook(b.copy(isPremium = !b.isPremium)) }, label = { Text(if (b.isPremium) "প্রিমিয়াম" else "ফ্রি") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = b.isPublished, onClick = { onSaveBook(b.copy(isPublished = !b.isPublished)) }, label = { Text(if (b.isPublished) "প্রকাশিত" else "ড্রাফট") })
                }
            }
        }
        itemsIndexed(guides, key = { idx, it -> "qg_${it.id}_$idx" }) { _, g ->
            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📗 ${g.titleBn}", fontWeight = FontWeight.Bold)
                        Text("শ্রেণি ${g.classNumber} • ${g.chapter}", style = MaterialTheme.typography.labelSmall)
                    }
                    FilterChip(selected = g.isPremium, onClick = { onSaveGuide(g.copy(isPremium = !g.isPremium)) }, label = { Text(if (g.isPremium) "প্রিমিয়াম" else "ফ্রি") })
                    Spacer(Modifier.width(6.dp))
                    FilterChip(selected = g.isPublished, onClick = { onSaveGuide(g.copy(isPublished = !g.isPublished)) }, label = { Text(if (g.isPublished) "প্রকাশিত" else "ড্রাফট") })
                }
            }
        }
    }
}
