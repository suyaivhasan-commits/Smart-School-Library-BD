package com.example.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.components.DestructiveConfirmDialog
import com.example.components.EmptyOrErrorStateCard
import com.example.components.LoadingStateBox
import com.example.components.NavyScreenTopBar
import com.example.model.UiState
import com.example.model.UserNote
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.formatLocalized
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldDark
import com.example.ui.theme.RoyalBlue
import java.text.SimpleDateFormat
import java.util.Locale

private val noteAccentColors = listOf(
    RoyalBlue,
    Color(0xFFDC2626),
    GoldDark,
    EmeraldDark,
    Color(0xFF7C3AED),
    Color(0xFF0D9488)
)

@Composable
fun NotebookScreen(
    notesState: UiState<List<UserNote>>,
    onBack: () -> Unit,
    onSaveNote: (
        existingNoteId: String?,
        title: String,
        content: String,
        subjectTag: String,
        colorIndex: Int,
        isPinned: Boolean
    ) -> Unit,
    onDeleteNote: (String) -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<UserNote?>(null) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var notePendingDelete by remember { mutableStateOf<UserNote?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    Scaffold(
        topBar = {
            NavyScreenTopBar(
                title = if (isBn) "আমার নোটবুক" else "My Notebook",
                subtitle = if (isBn) "ব্যক্তিগত ও ক্লাউড সুরক্ষিত অধ্যয়ন নোট" else "Private Cloud-Synced Study Notes",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = {
                            editingNote = null
                            showEditorDialog = true
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("notebook_top_add_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Note",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingNote = null
                    showEditorDialog = true
                },
                containerColor = EmeraldDark,
                contentColor = Color.White,
                modifier = Modifier.testTag("notebook_fab_add")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Create Note")
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.testTag("notebook_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(if (isBn) "নোটের শিরোনাম বা বিষয় খুঁজুন..." else "Search note title or content...")
                },
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
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("notebook_search_input")
            )

            when (notesState) {
                is UiState.Loading -> LoadingStateBox()
                is UiState.Error -> EmptyOrErrorStateCard(
                    title = strings.offlineError,
                    body = notesState.message,
                    isError = true,
                    onRetry = onRetry
                )
                is UiState.Success -> {
                    val filteredNotes = remember(notesState.data, searchQuery) {
                        if (searchQuery.isBlank()) notesState.data
                        else {
                            val q = searchQuery.trim()
                            notesState.data.filter {
                                it.title.contains(q, ignoreCase = true) ||
                                    it.content.contains(q, ignoreCase = true) ||
                                    it.subjectTag.contains(q, ignoreCase = true)
                            }
                        }
                    }

                    if (filteredNotes.isEmpty()) {
                        EmptyOrErrorStateCard(
                            title = if (isBn) "কোনো ব্যক্তিগত নোট নেই" else "No Personal Notes Yet",
                            body = if (isBn) {
                                "নিচের (+) বাটনে ট্যাপ করে গণিতের সূত্র, বাংলা রচনা বা গুরুত্বপূর্ণ অধ্যায় নোট লিখে রাখুন। আপনার নোট সম্পূর্ণ ব্যক্তিগত।"
                            } else {
                                "Tap the (+) button to create private study notes, formulas, or chapter summaries."
                            }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredNotes, key = { it.id }) { note ->
                                val accent = noteAccentColors[note.colorIndex.coerceIn(0, noteAccentColors.lastIndex)]
                                val dateStr = (note.updatedAt ?: note.createdAt)?.toDate()?.let {
                                    dateFormatter.format(it).formatLocalized(lang)
                                } ?: ""

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            editingNote = note
                                            showEditorDialog = true
                                        }
                                        .testTag("note_card_${note.id}"),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(accent.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Description,
                                                contentDescription = null,
                                                tint = accent,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = note.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (note.isPinned) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PushPin,
                                                        contentDescription = "Pinned",
                                                        tint = GoldDark,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            if (note.subjectTag.isNotBlank() || dateStr.isNotBlank()) {
                                                Spacer(Modifier.height(2.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (note.subjectTag.isNotBlank()) {
                                                        Surface(
                                                            color = accent.copy(alpha = 0.12f),
                                                            shape = RoundedCornerShape(50)
                                                        ) {
                                                            Text(
                                                                text = note.subjectTag,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = accent,
                                                                fontWeight = FontWeight.SemiBold,
                                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
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
                                            }

                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = note.content,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Column {
                                            IconButton(
                                                onClick = {
                                                    editingNote = note
                                                    showEditorDialog = true
                                                },
                                                modifier = Modifier.size(44.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = "Edit Note",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(
                                                onClick = { notePendingDelete = note },
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .testTag("delete_note_btn_${note.id}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.DeleteOutline,
                                                    contentDescription = "Delete Note",
                                                    tint = MaterialTheme.colorScheme.error
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
    }

    if (showEditorDialog) {
        NoteEditorDialog(
            initialNote = editingNote,
            isBn = isBn,
            onDismiss = { showEditorDialog = false },
            onSave = { title, content, subjectTag, colorIdx, pinned ->
                onSaveNote(editingNote?.id, title, content, subjectTag, colorIdx, pinned)
                showEditorDialog = false
            }
        )
    }

    notePendingDelete?.let { target ->
        DestructiveConfirmDialog(
            title = if (isBn) "নোট মুছে ফেলবেন?" else "Delete Note?",
            message = if (isBn) {
                "\"${target.title}\" নোটটি স্থায়ীভাবে মুছে ফেলা হবে। এটি আর ফিরিয়ে আনা যাবে না।"
            } else {
                "\"${target.title}\" will be permanently deleted from your private notebook."
            },
            onConfirm = { onDeleteNote(target.id) },
            onDismiss = { notePendingDelete = null }
        )
    }
}

@Composable
private fun NoteEditorDialog(
    initialNote: UserNote?,
    isBn: Boolean,
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, subjectTag: String, colorIndex: Int, isPinned: Boolean) -> Unit
) {
    val strings = LocalAppStrings.current
    var title by remember(initialNote) { mutableStateOf(initialNote?.title.orEmpty()) }
    var content by remember(initialNote) { mutableStateOf(initialNote?.content.orEmpty()) }
    var subjectTag by remember(initialNote) { mutableStateOf(initialNote?.subjectTag.orEmpty()) }
    var colorIndex by remember(initialNote) { mutableIntStateOf(initialNote?.colorIndex ?: 0) }
    var isPinned by remember(initialNote) { mutableStateOf(initialNote?.isPinned ?: false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialNote == null) {
                    if (isBn) "নতুন নোট তৈরি করুন" else "Create New Note"
                } else {
                    if (isBn) "নোট সম্পাদনা করুন" else "Edit Note"
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isBn) "নোটের শিরোনাম *" else "Note Title *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                OutlinedTextField(
                    value = subjectTag,
                    onValueChange = { subjectTag = it },
                    label = { Text(if (isBn) "বিষয় / অধ্যায় (ঐচ্ছিক)" else "Subject / Tag (Optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_subject_input")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(if (isBn) "নোটের বিস্তারিত লিখুন *" else "Note Content *") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        noteAccentColors.forEachIndexed { idx, color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (colorIndex == idx) {
                                            Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { colorIndex = idx }
                            )
                        }
                    }

                    FilterChip(
                        selected = isPinned,
                        onClick = { isPinned = !isPinned },
                        label = { Text(if (isBn) "পিন করুন" else "Pin") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank() || content.isBlank()) {
                        validationError = if (isBn) {
                            "নোটের শিরোনাম এবং বিস্তারিত অংশ পূরণ করুন।"
                        } else {
                            "Please enter both a note title and content."
                        }
                        return@Button
                    }
                    onSave(title, content, subjectTag, colorIndex, isPinned)
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary, contentColor = Color(0xFF042F1E)),
                modifier = Modifier.testTag("note_save_button")
            ) {
                Text(strings.save, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}
