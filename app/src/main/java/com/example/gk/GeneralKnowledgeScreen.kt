package com.example.gk

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.example.components.EmptyOrErrorStateCard
import com.example.components.LoadingStateBox
import com.example.components.NavyScreenTopBar
import com.example.model.Bookmark
import com.example.model.DefaultCatalogSeed
import com.example.model.GeneralKnowledgeItem
import com.example.model.UiState
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.formatLocalized
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.GoldDark
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue

@Composable
fun GeneralKnowledgeScreen(
    gkState: UiState<List<GeneralKnowledgeItem>>,
    bookmarks: List<Bookmark>,
    onBack: () -> Unit,
    onToggleGkBookmark: (GeneralKnowledgeItem) -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    var selectedCategory by rememberSaveable { mutableStateOf("ALL") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var showAnswer by rememberSaveable { mutableStateOf(false) }

    val bookmarkedGkIds = remember(bookmarks) {
        bookmarks.filter { it.bookmarkType == "GK" }.map { it.targetId }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("general_knowledge_screen")
    ) {
        NavyScreenTopBar(
            title = strings.featureGk,
            subtitle = if (isBn) "৭টি ক্যাটাগরিতে নির্বাচিত সাধারণ জ্ঞান ও ব্যাখ্যা" else "7 Curated Categories with Answers & Explanations",
            onBack = onBack
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                currentIndex = 0
                showAnswer = false
            },
            placeholder = { Text(if (isBn) "সাধারণ জ্ঞান প্রশ্ন খুঁজুন..." else "Search general knowledge...") },
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
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("gk_search_input")
        )

        // 7 Category Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedCategory == "ALL",
                    onClick = {
                        selectedCategory = "ALL"
                        currentIndex = 0
                        showAnswer = false
                    },
                    label = { Text(strings.allFilter) }
                )
            }
            items(DefaultCatalogSeed.gkCategories, key = { it.code }) { cat ->
                FilterChip(
                    selected = selectedCategory == cat.code,
                    onClick = {
                        selectedCategory = cat.code
                        currentIndex = 0
                        showAnswer = false
                    },
                    label = { Text(if (isBn) cat.nameBn else cat.nameEn) },
                    modifier = Modifier.testTag("gk_cat_${cat.code}")
                )
            }
        }

        when (gkState) {
            is UiState.Loading -> LoadingStateBox()
            is UiState.Error -> EmptyOrErrorStateCard(
                title = strings.offlineError,
                body = gkState.message,
                isError = true,
                onRetry = onRetry
            )
            is UiState.Success -> {
                val filtered = remember(gkState.data, selectedCategory, searchQuery) {
                    gkState.data.filter { item ->
                        val catMatch = selectedCategory == "ALL" || item.category == selectedCategory
                        val queryMatch = searchQuery.isBlank() ||
                            item.questionBn.contains(searchQuery, ignoreCase = true) ||
                            item.questionEn.contains(searchQuery, ignoreCase = true) ||
                            item.answerBn.contains(searchQuery, ignoreCase = true)
                        catMatch && queryMatch
                    }
                }

                LaunchedEffect(filtered.size) {
                    if (currentIndex >= filtered.size) {
                        currentIndex = 0
                    }
                }

                if (filtered.isEmpty()) {
                    EmptyOrErrorStateCard(
                        title = strings.emptyStateTitle,
                        body = if (isBn) {
                            "এই ক্যাটাগরিতে কোনো প্রশ্ন পাওয়া যায়নি।"
                        } else {
                            "No general knowledge questions matched your filter."
                        }
                    )
                } else {
                    val safeIndex = currentIndex.coerceIn(0, filtered.lastIndex)
                    val currentItem = filtered[safeIndex]
                    val isBookmarked = currentItem.id in bookmarkedGkIds
                    val categoryMeta = DefaultCatalogSeed.gkCategories.firstOrNull { it.code == currentItem.category }
                    val categoryLabel = if (isBn) {
                        categoryMeta?.nameBn ?: currentItem.category
                    } else {
                        categoryMeta?.nameEn ?: currentItem.category
                    }

                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 640.dp)
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Demo Notice Banner when displaying built-in DEMO questions
                            if (currentItem.isDemo) {
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isBn) {
                                                "নমুনা সাধারণ জ্ঞান (DEMO) — অ্যাডমিন প্যানেল থেকে নতুন প্রশ্ন যোগ করা যাবে।"
                                            } else {
                                                "DEMO GK Set — Super Admin can publish live GK items anytime."
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = Color(0xFF92400E),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("gk_question_card"),
                                shape = RoundedCornerShape(22.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                            ) {
                                Column(modifier = Modifier.padding(20.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                color = RoyalBlue.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(50)
                                            ) {
                                                Text(
                                                    text = categoryLabel,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = RoyalBlue,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                )
                                            }
                                            if (currentItem.isDemo) {
                                                Surface(
                                                    color = Color(0xFFFEF3C7),
                                                    shape = RoundedCornerShape(50)
                                                ) {
                                                    Text(
                                                        text = "DEMO",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF92400E),
                                                        fontWeight = FontWeight.ExtraBold,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${(safeIndex + 1).formatLocalized(lang)} / ${filtered.size.formatLocalized(lang)}",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            IconButton(
                                                onClick = { onToggleGkBookmark(currentItem) },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .testTag("gk_bookmark_button")
                                            ) {
                                                Icon(
                                                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                                    contentDescription = "Bookmark GK",
                                                    tint = if (isBookmarked) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(14.dp))

                                    Row(verticalAlignment = Alignment.Top) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(GoldDark.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Lightbulb,
                                                contentDescription = null,
                                                tint = GoldDark,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Text(
                                            text = if (!isBn && currentItem.questionEn.isNotBlank()) {
                                                currentItem.questionEn
                                            } else {
                                                currentItem.questionBn
                                            },
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(Modifier.height(18.dp))

                                    OutlinedButton(
                                        onClick = { showAnswer = !showAnswer },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("gk_reveal_answer_button")
                                    ) {
                                        Icon(Icons.Filled.Visibility, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = if (showAnswer) {
                                                if (isBn) "উত্তর লুকান" else "Hide Answer"
                                            } else {
                                                if (isBn) "সঠিক উত্তর ও ব্যাখ্যা দেখুন" else "Reveal Answer & Explanation"
                                            },
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    AnimatedVisibility(visible = showAnswer) {
                                        Column {
                                            Spacer(Modifier.height(14.dp))
                                            Surface(
                                                color = EmeraldPrimary.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(modifier = Modifier.padding(16.dp)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Filled.CheckCircle,
                                                            contentDescription = null,
                                                            tint = EmeraldDark,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            text = if (isBn) "সঠিক উত্তর:" else "Correct Answer:",
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = EmeraldDark,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Spacer(Modifier.height(6.dp))
                                                    Text(
                                                        text = if (!isBn && currentItem.answerEn.isNotBlank()) {
                                                            currentItem.answerEn
                                                        } else {
                                                            currentItem.answerBn
                                                        },
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = EmeraldDark
                                                    )
                                                    if (currentItem.explanationBn.isNotBlank()) {
                                                        Spacer(Modifier.height(8.dp))
                                                        Text(
                                                            text = currentItem.explanationBn,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Previous / Next Navigation Bar matching Reference Sheet
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (safeIndex > 0) {
                                            currentIndex = safeIndex - 1
                                            showAnswer = false
                                        }
                                    },
                                    enabled = safeIndex > 0,
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("gk_prev_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isBn) "পূর্ববর্তী" else "Previous", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        if (safeIndex < filtered.lastIndex) {
                                            currentIndex = safeIndex + 1
                                            showAnswer = false
                                        }
                                    },
                                    enabled = safeIndex < filtered.lastIndex,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NavyPrimary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("gk_next_button")
                                ) {
                                    Text(if (isBn) "পরবর্তী প্রশ্ন" else "Next Question", fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
