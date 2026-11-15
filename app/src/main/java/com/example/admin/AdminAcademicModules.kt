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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.components.BookCoverArt
import com.example.components.DestructiveConfirmDialog
import com.example.components.FormEditorDialog
import com.example.data.DirectUploadDraft
import com.example.data.DirectUploadState
import com.example.data.SelectedCoverInfo
import com.example.data.SelectedPdfInfo
import com.example.data.UploadCategory
import com.example.model.AcademicStream
import com.example.model.ContentClassGroupHelper
import com.example.model.DefaultCatalogSeed
import com.example.model.EducationLevel
import com.example.model.GroupedBookEntry
import com.example.model.GroupedGuideEntry
import com.example.model.GuideNote
import com.example.model.SchoolClass
import com.example.model.Subject
import com.example.model.Textbook
import com.example.model.sortedBySerialOrder
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.RoyalBlue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag

// --- A. Class Management ---
@Composable
fun AdminClassesModule(
    classes: List<SchoolClass>,
    educationLevels: List<EducationLevel> = emptyList(),
    onSave: (SchoolClass) -> Unit,
    onDelete: (String) -> Unit = {}
) {
    var editing by remember { mutableStateOf<SchoolClass?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SchoolClass?>(null) }
    val displayList = remember(classes) { classes.sortedBySerialOrder() }
    val sortedLevels = remember(educationLevels) {
        educationLevels.sortedWith(compareBy<EducationLevel> { it.sortOrder }.thenBy { it.minClassNumber })
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("শ্রেণি ব্যবস্থাপনা (${displayList.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        val nextNum = ((displayList.maxOfOrNull { it.classNumber } ?: 10) + 1).coerceIn(1, 30)
                        val matchingLevel = sortedLevels.firstOrNull {
                            it.minClassNumber > 0 && nextNum in it.minClassNumber..it.maxClassNumber
                        }
                        editing = SchoolClass(
                            id = "",
                            classNumber = nextNum,
                            nameBn = "",
                            nameEn = "",
                            hasStreams = nextNum in 9..10,
                            sortOrder = nextNum,
                            educationLevelId = matchingLevel?.id.orEmpty(),
                            educationLevelNameBn = matchingLevel?.nameBn.orEmpty(),
                            educationLevelNameEn = matchingLevel?.nameEn.orEmpty()
                        )
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                    modifier = Modifier.testTag("admin_add_class_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("নতুন শ্রেণি")
                }
            }
        }
        itemsIndexed(displayList, key = { idx, it -> it.id.ifBlank { "c_${it.classNumber}_$idx" } }) { _, cls ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${cls.displayNameBn} (${cls.displayNameEn})", fontWeight = FontWeight.Bold)
                        val levelTag = if (cls.educationLevelNameBn.isNotBlank()) " • স্তর: ${cls.educationLevelNameBn}" else ""
                        Text(
                            "নম্বর: ${cls.classNumber} • ক্রম: ${cls.sortOrder}$levelTag • বিভাগ: ${if (cls.hasStreams) "হ্যাঁ" else "না"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cls.isActive,
                        onCheckedChange = { onSave(cls.copy(nameBn = cls.displayNameBn, nameEn = cls.displayNameEn, isActive = it)) }
                    )
                    IconButton(onClick = { editing = cls; showDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit class")
                    }
                    IconButton(
                        onClick = { deleteTarget = cls },
                        modifier = Modifier.testTag("admin_delete_class_${cls.id}")
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete class", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDialog && editing != null) {
        val currentEditing = editing!!
        var classNumber by remember(currentEditing) { mutableStateOf(currentEditing.classNumber.toString()) }
        var nameBn by remember(currentEditing) { mutableStateOf(currentEditing.nameBn.ifBlank { currentEditing.displayNameBn }) }
        var nameEn by remember(currentEditing) { mutableStateOf(currentEditing.nameEn.ifBlank { currentEditing.displayNameEn }) }
        var sortOrder by remember(currentEditing) { mutableStateOf(currentEditing.sortOrder.toString()) }
        var hasStreams by remember(currentEditing) { mutableStateOf(currentEditing.hasStreams) }
        var selectedLevelId by remember(currentEditing) { mutableStateOf(currentEditing.educationLevelId) }
        var validationError by remember(currentEditing) { mutableStateOf<String?>(null) }

        FormEditorDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("শ্রেণির তথ্য সংরক্ষণ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            content = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!validationError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = validationError!!,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = classNumber,
                        onValueChange = {
                            classNumber = it.filter { ch -> ch.isDigit() }
                            validationError = null
                        },
                        label = { Text("শ্রেণি নম্বর (১-৩০) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameBn,
                        onValueChange = { nameBn = it; validationError = null },
                        label = { Text("শ্রেণির বাংলা নাম *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = nameEn,
                        onValueChange = { nameEn = it; validationError = null },
                        label = { Text("English Name *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = sortOrder,
                        onValueChange = { sortOrder = it.filter { ch -> ch.isDigit() } },
                        label = { Text("প্রদর্শন ক্রম (Sort Order)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (sortedLevels.isNotEmpty()) {
                        Text("শিক্ষা স্তর (Education Level)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedLevelId.isBlank(),
                                onClick = { selectedLevelId = "" },
                                label = { Text("সাধারণ / কোনোটি নয়") }
                            )
                            sortedLevels.forEach { lvl ->
                                FilterChip(
                                    selected = selectedLevelId == lvl.id,
                                    onClick = { selectedLevelId = lvl.id },
                                    label = { Text(lvl.nameBn.ifBlank { lvl.code }) }
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("বিভাগ রয়েছে (Science/Humanities/Business)", modifier = Modifier.weight(1f))
                        Switch(checked = hasStreams, onCheckedChange = { hasStreams = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsedNum = classNumber.trim().toIntOrNull()
                    if (parsedNum == null || parsedNum !in 1..30) {
                        validationError = "শ্রেণি নম্বর ১ থেকে ৩০-এর মধ্যে হতে হবে।"
                        return@Button
                    }
                    val duplicate = displayList.firstOrNull {
                        it.classNumber == parsedNum && it.id != currentEditing.id
                    }
                    if (duplicate != null) {
                        validationError = "শ্রেণি $parsedNum (${duplicate.displayNameBn}) ইতিমধ্যে বিদ্যমান রয়েছে!"
                        return@Button
                    }
                    val chosenLevel = sortedLevels.firstOrNull { it.id == selectedLevelId }
                    val fallbackClass = currentEditing.copy(classNumber = parsedNum)
                    val finalBn = nameBn.trim().ifBlank { fallbackClass.displayNameBn }
                    val finalEn = nameEn.trim().ifBlank { fallbackClass.displayNameEn }
                    onSave(
                        currentEditing.copy(
                            id = currentEditing.id.ifBlank { "class_$parsedNum" },
                            classNumber = parsedNum,
                            nameBn = finalBn,
                            nameEn = finalEn,
                            hasStreams = hasStreams,
                            sortOrder = sortOrder.toIntOrNull()?.coerceIn(0, 1000) ?: parsedNum,
                            educationLevelId = chosenLevel?.id.orEmpty(),
                            educationLevelNameBn = chosenLevel?.nameBn.orEmpty(),
                            educationLevelNameEn = chosenLevel?.nameEn.orEmpty()
                        )
                    )
                    showDialog = false
                }) { Text("সংরক্ষণ করুন") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("বাতিল") } }
        )
    }

    deleteTarget?.let { target ->
        DestructiveConfirmDialog(
            title = "শ্রেণি মুছে ফেলবেন?",
            message = "\"${target.displayNameBn} (${target.displayNameEn})\" শ্রেণিটি স্থায়ীভাবে মুছে ফেলা হবে। সংরক্ষিত বই, গাইড, সাবস্ক্রিপশন ও ব্যবহারকারীর অ্যাকাউন্ট অক্ষত থাকবে।",
            onConfirm = {
                val targetId = target.id.ifBlank { "class_${target.classNumber}" }
                deleteTarget = null
                onDelete(targetId)
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

// --- A2. Education Level Management (SSC / HSC / Graduate) ---
@Composable
fun AdminEducationLevelsModule(
    levels: List<EducationLevel>,
    onSave: (EducationLevel) -> Unit,
    onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<EducationLevel?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<EducationLevel?>(null) }
    val sortedLevels = remember(levels) {
        levels.sortedWith(compareBy<EducationLevel> { it.sortOrder }.thenBy { it.minClassNumber }.thenBy { it.nameEn })
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("শিক্ষা স্তর ব্যবস্থাপনা (${sortedLevels.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        val nextOrder = (sortedLevels.maxOfOrNull { it.sortOrder } ?: 0) + 1
                        editing = EducationLevel(sortOrder = nextOrder, isActive = true)
                        showDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoyalBlue, contentColor = Color.White),
                    modifier = Modifier.testTag("admin_add_level_btn")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("নতুন স্তর")
                }
            }
        }
        itemsIndexed(sortedLevels, key = { idx, it -> it.id.ifBlank { "lvl_$idx" } }) { _, lvl ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lvl.displayBannerBn, fontWeight = FontWeight.Bold)
                        val rangeLabel = if (lvl.minClassNumber > 0 && lvl.maxClassNumber >= lvl.minClassNumber) {
                            "শ্রেণি ${lvl.minClassNumber}–${lvl.maxClassNumber}"
                        } else {
                            "কাস্টম শ্রেণি সংযোগ"
                        }
                        Text(
                            "কোড: ${lvl.code} • ক্রম: ${lvl.sortOrder} • $rangeLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (lvl.descriptionBn.isNotBlank()) {
                            Text(
                                lvl.descriptionBn,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = lvl.isActive,
                        onCheckedChange = { onSave(lvl.copy(isActive = it)) }
                    )
                    IconButton(onClick = { editing = lvl; showDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit level")
                    }
                    IconButton(onClick = { deleteTarget = lvl }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete level", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if (showDialog && editing != null) {
        val initial = editing!!
        var code by remember(initial) { mutableStateOf(initial.code) }
        var nameBn by remember(initial) { mutableStateOf(initial.nameBn) }
        var nameEn by remember(initial) { mutableStateOf(initial.nameEn) }
        var subtitleBn by remember(initial) { mutableStateOf(initial.subtitleBn) }
        var subtitleEn by remember(initial) { mutableStateOf(initial.subtitleEn) }
        var descriptionBn by remember(initial) { mutableStateOf(initial.descriptionBn) }
        var minCls by remember(initial) { mutableStateOf(if (initial.minClassNumber > 0) initial.minClassNumber.toString() else "") }
        var maxCls by remember(initial) { mutableStateOf(if (initial.maxClassNumber > 0) initial.maxClassNumber.toString() else "") }
        var sortOrder by remember(initial) { mutableStateOf(initial.sortOrder.toString()) }
        var isActive by remember(initial) { mutableStateOf(initial.isActive) }
        var err by remember(initial) { mutableStateOf<String?>(null) }

        FormEditorDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("শিক্ষা স্তরের তথ্য সংরক্ষণ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            content = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!err.isNullOrBlank()) {
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(err!!, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                        }
                    }
                    OutlinedTextField(code, { code = it; err = null }, label = { Text("কোড (যেমন SSC / HSC / GRADUATE) *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameBn, { nameBn = it; err = null }, label = { Text("বাংলা নাম *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameEn, { nameEn = it; err = null }, label = { Text("English Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(subtitleBn, { subtitleBn = it }, label = { Text("ব্যানার উপশিরোনাম (বাংলা)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(subtitleEn, { subtitleEn = it }, label = { Text("Banner Subtitle (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(descriptionBn, { descriptionBn = it }, label = { Text("বিস্তারিত বিবরণ (বাংলা)") }, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minCls,
                            onValueChange = { minCls = it.filter { ch -> ch.isDigit() } },
                            label = { Text("শুরুর শ্রেণি (০-৩০)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxCls,
                            onValueChange = { maxCls = it.filter { ch -> ch.isDigit() } },
                            label = { Text("শেষ শ্রেণি (০-৩০)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = sortOrder,
                        onValueChange = { sortOrder = it.filter { ch -> ch.isDigit() } },
                        label = { Text("প্রদর্শন ক্রম (Sort Order)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("সক্রিয় (হোমপেজে প্রদর্শিত হবে)", modifier = Modifier.weight(1f))
                        Switch(checked = isActive, onCheckedChange = { isActive = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val cleanNameBn = nameBn.trim()
                    val cleanNameEn = nameEn.trim()
                    val cleanCode = code.trim().uppercase()
                    val parsedMin = (minCls.toIntOrNull() ?: 0).coerceIn(0, 30)
                    val parsedMax = (maxCls.toIntOrNull() ?: 0).coerceIn(0, 30)
                    if (cleanNameBn.isBlank() && cleanNameEn.isBlank()) {
                        err = "শিক্ষা স্তরের নাম লিখুন।"
                        return@Button
                    }
                    if (parsedMax in 1 until parsedMin) {
                        err = "শেষ শ্রেণি শুরুর শ্রেণির চেয়ে ছোট হতে পারবে না।"
                        return@Button
                    }
                    onSave(
                        initial.copy(
                            code = cleanCode.ifBlank { cleanNameEn.uppercase().replace(Regex("[^A-Z0-9_]+"), "_").ifBlank { "LEVEL" } },
                            nameBn = cleanNameBn.ifBlank { cleanNameEn },
                            nameEn = cleanNameEn.ifBlank { cleanNameBn },
                            subtitleBn = subtitleBn.trim(),
                            subtitleEn = subtitleEn.trim(),
                            descriptionBn = descriptionBn.trim(),
                            minClassNumber = parsedMin,
                            maxClassNumber = parsedMax,
                            sortOrder = sortOrder.toIntOrNull()?.coerceIn(0, 1000) ?: 1,
                            isActive = isActive
                        )
                    )
                    showDialog = false
                }) { Text("সংরক্ষণ করুন") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("বাতিল") } }
        )
    }

    deleteTarget?.let { target ->
        DestructiveConfirmDialog(
            title = "শিক্ষা স্তর মুছে ফেলবেন?",
            message = "\"${target.displayBannerBn}\" স্তরটি স্থায়ীভাবে মুছে ফেলা হবে। কোনো শ্রেণি বা বই মুছে যাবে না।",
            onConfirm = {
                val id = target.id
                deleteTarget = null
                if (id.isNotBlank()) onDelete(id)
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

// --- B. Stream Management ---
@Composable
fun AdminStreamsModule(
    streams: List<AcademicStream>,
    onSave: (AcademicStream) -> Unit
) {
    val display = streams.ifEmpty { DefaultCatalogSeed.defaultStreams }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text("বিভাগ ব্যবস্থাপনা (৯ম ও ১০ম শ্রেণি)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        itemsIndexed(display, key = { idx, it -> "${it.code}_$idx" }) { _, st ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${st.displayNameBn} (${st.displayNameEn})", fontWeight = FontWeight.Bold)
                        Text("Code: ${st.code}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = st.isActive,
                        onCheckedChange = { onSave(st.copy(nameBn = st.displayNameBn, nameEn = st.displayNameEn, isActive = it)) }
                    )
                }
            }
        }
    }
}

// --- C. Subject Management ---
@Composable
fun AdminSubjectsModule(
    subjects: List<Subject>,
    classes: List<SchoolClass> = DefaultCatalogSeed.defaultClasses,
    onSave: (Subject) -> Unit,
    onDelete: (String) -> Unit
) {
    var editing by remember { mutableStateOf<Subject?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Subject?>(null) }
    val sortedClasses = remember(classes) {
        classes.ifEmpty { DefaultCatalogSeed.defaultClasses }.sortedBySerialOrder()
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("বিষয় ব্যবস্থাপনা (Subjects)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(onClick = { editing = Subject(classNumber = sortedClasses.firstOrNull()?.classNumber ?: 5); showDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("নতুন বিষয়")
                }
            }
        }
        itemsIndexed(subjects, key = { idx, it -> it.id.ifBlank { "subj_$idx" } }) { _, subj ->
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${subj.nameBn} (${subj.nameEn})", fontWeight = FontWeight.Bold)
                        Text("শ্রেণি ${subj.classNumber} • বিভাগ: ${subj.stream}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = subj.isActive, onCheckedChange = { onSave(subj.copy(isActive = it)) })
                    IconButton(onClick = { editing = subj; showDialog = true }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { deleteTarget = subj }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (showDialog && editing != null) {
        var nameBn by remember(editing) { mutableStateOf(editing!!.nameBn) }
        var nameEn by remember(editing) { mutableStateOf(editing!!.nameEn) }
        var cls by remember(editing) { mutableIntStateOf(editing!!.classNumber) }
        var stream by remember(editing) { mutableStateOf(editing!!.stream) }
        val selectedClassHasStreams = cls in 9..10 || (sortedClasses.firstOrNull { it.classNumber == cls }?.hasStreams == true)

        FormEditorDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("বিষয় সংরক্ষণ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            content = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(nameBn, { nameBn = it }, label = { Text("বিষয়ের বাংলা নাম *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(nameEn, { nameEn = it }, label = { Text("English Name *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val chipNumbers = (sortedClasses.map { it.classNumber } + cls).distinct().sorted()
                        chipNumbers.forEach { c ->
                            FilterChip(selected = cls == c, onClick = { cls = c }, label = { Text("Class $c") })
                        }
                    }
                    if (selectedClassHasStreams) {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("ALL", "SCIENCE", "HUMANITIES", "BUSINESS").forEach { st ->
                                FilterChip(selected = stream == st, onClick = { stream = st }, label = { Text(st) })
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (nameBn.isNotBlank()) {
                        onSave(
                            editing!!.copy(
                                nameBn = nameBn.trim(),
                                nameEn = nameEn.trim().ifBlank { nameBn.trim() },
                                classNumber = cls,
                                stream = if (selectedClassHasStreams) stream else "ALL"
                            )
                        )
                        showDialog = false
                    }
                }) { Text("সংরক্ষণ") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("বাতিল") } }
        )
    }

    deleteTarget?.let { t ->
        DestructiveConfirmDialog(
            title = "বিষয় মুছে ফেলবেন?",
            message = "\"${t.nameBn}\" স্থায়ীভাবে মুছে ফেলা হবে।",
            onConfirm = { onDelete(t.id) },
            onDismiss = { deleteTarget = null }
        )
    }
}

// --- D. Original Textbooks Management (Direct File Upload — Zero URL Inputs) ---
@Composable
fun AdminBooksModule(
    books: List<Textbook>,
    subjects: List<Subject>,
    classes: List<SchoolClass> = DefaultCatalogSeed.defaultClasses,
    onSave: (Textbook) -> Unit,
    onDelete: (String) -> Unit,
    uploadState: DirectUploadState = DirectUploadState.Idle,
    onResetUploadState: () -> Unit = {},
    onDirectUpload: (DirectUploadDraft, SelectedCoverInfo?, SelectedPdfInfo?) -> Unit = { _, _, _ -> },
    onGoAdminHome: (() -> Unit)? = null
) {
    var editingBookId by rememberSaveable { mutableStateOf<String?>(null) }
    var showUploadScreen by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GroupedBookEntry?>(null) }
    val groupedBooks = remember(books) { ContentClassGroupHelper.groupBooksForAdmin(books) }
    val editingEntry = remember(groupedBooks, editingBookId) {
        editingBookId?.let { id ->
            groupedBooks.firstOrNull { entry ->
                entry.primary.id == id || id in entry.siblingIds
            }
        }
    }
    val editing = editingEntry?.primary

    if (showUploadScreen) {
        AdminBookUploadScreen(
            initialCategory = UploadCategory.ORIGINAL_TEXTBOOKS,
            editingBook = editing,
            editingGuide = null,
            initialSelectedClasses = editingEntry?.classNumbers.orEmpty(),
            editingSiblingIds = editingEntry?.siblingIds.orEmpty(),
            subjects = subjects,
            availableClasses = classes,
            uploadState = uploadState,
            onResetUploadState = onResetUploadState,
            onSubmitUpload = onDirectUpload,
            onBack = {
                onResetUploadState()
                showUploadScreen = false
                editingBookId = null
            },
            onGoAdminHome = {
                onResetUploadState()
                showUploadScreen = false
                editingBookId = null
                onGoAdminHome?.invoke()
            }
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("মূল পাঠ্যবই ব্যবস্থাপনা (${groupedBooks.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        editingBookId = null
                        onResetUploadState()
                        showUploadScreen = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("নতুন বই যোগ করুন")
                }
            }
        }
        itemsIndexed(groupedBooks, key = { idx, it -> it.primary.id.ifBlank { "book_$idx" } }) { _, entry ->
            val book = entry.primary
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        BookCoverArt(
                            itemId = book.id.ifBlank { "adm_b" },
                            rawCoverUrl = book.effectiveCoverRef,
                            title = book.titleBn,
                            subject = book.subjectNameBn,
                            badgeLabel = book.edition,
                            classNumber = book.classNumber,
                            isGuide = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(book.titleBn, fontWeight = FontWeight.Bold)
                        Text(
                            "শ্রেণি ${entry.classSummaryLabel} (${book.stream}) • ${book.subjectNameBn} • ${if (book.isPremium) "প্রিমিয়াম" else "ফ্রি"} • ${if (book.isPublished) "প্রকাশিত" else "অপ্রকাশিত"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (book.pdfFileName.isNotBlank()) {
                            Text(
                                "PDF: ${book.pdfFileName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldDark
                            )
                        }
                    }
                    IconButton(onClick = {
                        editingBookId = book.id
                        onResetUploadState()
                        showUploadScreen = true
                    }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { deleteTarget = entry }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    deleteTarget?.let { entry ->
        val t = entry.primary
        val classNote = if (entry.classNumbers.size > 1) " (শ্রেণি ${entry.classSummaryLabel})" else ""
        DestructiveConfirmDialog(
            title = "পাঠ্যবই মুছে ফেলবেন?",
            message = "\"${t.titleBn}\"$classNote পাঠ্যবইটি ডাটাবেজ থেকে স্থায়ীভাবে মুছে যাবে।",
            onConfirm = {
                val delId = t.id
                deleteTarget = null
                onDelete(delId)
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

// --- E. Guides & Notes Management (Direct File Upload — Zero URL Inputs) ---
@Composable
fun AdminGuidesModule(
    guides: List<GuideNote>,
    subjects: List<Subject>,
    classes: List<SchoolClass> = DefaultCatalogSeed.defaultClasses,
    onSave: (GuideNote) -> Unit,
    onDelete: (String) -> Unit,
    uploadState: DirectUploadState = DirectUploadState.Idle,
    onResetUploadState: () -> Unit = {},
    onDirectUpload: (DirectUploadDraft, SelectedCoverInfo?, SelectedPdfInfo?) -> Unit = { _, _, _ -> },
    onGoAdminHome: (() -> Unit)? = null
) {
    var editingGuideId by rememberSaveable { mutableStateOf<String?>(null) }
    var showUploadScreen by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GroupedGuideEntry?>(null) }
    val groupedGuides = remember(guides) { ContentClassGroupHelper.groupGuidesForAdmin(guides) }
    val editingEntry = remember(groupedGuides, editingGuideId) {
        editingGuideId?.let { id ->
            groupedGuides.firstOrNull { entry ->
                entry.primary.id == id || id in entry.siblingIds
            }
        }
    }
    val editing = editingEntry?.primary

    if (showUploadScreen) {
        AdminBookUploadScreen(
            initialCategory = UploadCategory.GUIDES_AND_NOTES,
            editingBook = null,
            editingGuide = editing,
            initialSelectedClasses = editingEntry?.classNumbers.orEmpty(),
            editingSiblingIds = editingEntry?.siblingIds.orEmpty(),
            subjects = subjects,
            availableClasses = classes,
            uploadState = uploadState,
            onResetUploadState = onResetUploadState,
            onSubmitUpload = onDirectUpload,
            onBack = {
                onResetUploadState()
                showUploadScreen = false
                editingGuideId = null
            },
            onGoAdminHome = {
                onResetUploadState()
                showUploadScreen = false
                editingGuideId = null
                onGoAdminHome?.invoke()
            }
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("গাইড ও নোট ব্যবস্থাপনা (${groupedGuides.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Button(
                    onClick = {
                        editingGuideId = null
                        onResetUploadState()
                        showUploadScreen = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("নতুন গাইড/নোট")
                }
            }
        }
        itemsIndexed(groupedGuides, key = { idx, it -> it.primary.id.ifBlank { "guide_$idx" } }) { _, entry ->
            val g = entry.primary
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        BookCoverArt(
                            itemId = g.id.ifBlank { "adm_g" },
                            rawCoverUrl = g.effectiveCoverRef,
                            title = g.titleBn,
                            subject = g.subjectNameBn,
                            badgeLabel = g.guideType,
                            classNumber = g.classNumber,
                            isGuide = true,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(g.titleBn, fontWeight = FontWeight.Bold)
                        Text("শ্রেণি ${entry.classSummaryLabel} • ${g.subjectNameBn} • ${g.chapter} • ${g.guideType}", style = MaterialTheme.typography.bodySmall)
                        if (g.pdfFileName.isNotBlank()) {
                            Text(
                                "PDF: ${g.pdfFileName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldDark
                            )
                        }
                    }
                    IconButton(onClick = {
                        editingGuideId = g.id
                        onResetUploadState()
                        showUploadScreen = true
                    }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { deleteTarget = entry }) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    deleteTarget?.let { entry ->
        val t = entry.primary
        val classNote = if (entry.classNumbers.size > 1) " (শ্রেণি ${entry.classSummaryLabel})" else ""
        DestructiveConfirmDialog(
            title = "গাইড/নোট মুছে ফেলবেন?",
            message = "\"${t.titleBn}\"$classNote মুছে ফেলা হবে।",
            onConfirm = {
                val delId = t.id
                deleteTarget = null
                onDelete(delId)
            },
            onDismiss = { deleteTarget = null }
        )
    }
}
