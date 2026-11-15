package com.example.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.BookmarkRemove
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.components.EmptyOrErrorStateCard
import com.example.components.LoadingStateBox
import com.example.components.NavyScreenTopBar
import com.example.model.Bookmark
import com.example.model.ReadingProgress
import com.example.model.UiState
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.formatLocalized
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.NavyPrimary

@Composable
fun BookmarksScreen(
    bookmarksState: UiState<List<Bookmark>>,
    onBack: (() -> Unit)? = null,
    onOpenBookmark: (Bookmark) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("bookmarks_screen")
    ) {
        NavyScreenTopBar(
            title = strings.featureBookmarks,
            subtitle = if (isBn) "সংরক্ষিত বই, নির্দিষ্ট পৃষ্ঠা ও সাধারণ জ্ঞান" else "Saved Books, Pages & GK",
            onBack = onBack
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(if (isBn) "সংরক্ষিত বই" else "Saved Books", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(if (isBn) "সংরক্ষিত পৃষ্ঠা" else "Saved Pages", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text(if (isBn) "সাধারণ জ্ঞান" else "GK", fontWeight = FontWeight.Bold) }
            )
        }

        when (bookmarksState) {
            is UiState.Loading -> LoadingStateBox()
            is UiState.Error -> EmptyOrErrorStateCard(strings.offlineError, bookmarksState.message, isError = true, onRetry = onRetry)
            is UiState.Success -> {
                val filtered = when (selectedTab) {
                    0 -> bookmarksState.data.filter { it.bookmarkType == "BOOK" || it.bookmarkType == "GUIDE" }
                    1 -> bookmarksState.data.filter { it.bookmarkType == "PAGE" }
                    else -> bookmarksState.data.filter { it.bookmarkType == "GK" }
                }

                if (filtered.isEmpty()) {
                    EmptyOrErrorStateCard(
                        title = if (isBn) "কোনো বুকমার্ক নেই" else "No Bookmarks Saved",
                        body = if (isBn) {
                            "বই বা পিডিএফ পড়ার সময় বুকমার্ক আইকনে ট্যাপ করলে এখানে ব্যক্তিগতভাবে সংরক্ষিত থাকবে।"
                        } else {
                            "Tap the bookmark icon while reading any textbook, page, or GK question to save it privately."
                        }
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { bm ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenBookmark(bm) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (bm.bookmarkType) {
                                            "PAGE" -> Icons.Filled.Description
                                            "GK" -> Icons.Filled.Lightbulb
                                            else -> Icons.AutoMirrored.Filled.MenuBook
                                        },
                                        contentDescription = null,
                                        tint = EmeraldDark
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bm.titleBn,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val sub = when (bm.bookmarkType) {
                                            "PAGE" -> "${classTitle(bm.classNumber, lang)} • ${strings.pagesLabel} ${bm.pageNumber.formatLocalized(lang)}"
                                            "GK" -> bm.subtitleBn
                                            else -> "${classTitle(bm.classNumber, lang)} • ${bm.subtitleBn}"
                                        }
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (bm.bookmarkType != "GK") {
                                        Button(
                                            onClick = { onOpenBookmark(bm) },
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
                                        ) {
                                            Text(strings.readNow, color = Color.White)
                                        }
                                    }
                                    IconButton(onClick = { onRemoveBookmark(bm.id) }) {
                                        Icon(
                                            imageVector = Icons.Filled.BookmarkRemove,
                                            contentDescription = "Remove Bookmark",
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

@Composable
fun ReadingHistoryScreen(
    progressState: UiState<List<ReadingProgress>>,
    onBack: () -> Unit,
    onResumeItem: (ReadingProgress) -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("reading_history_screen")
    ) {
        NavyScreenTopBar(
            title = strings.featureHistory,
            subtitle = if (isBn) "সর্বশেষ পঠিত বই ও অগ্রগতি" else "Recently Read Books & Progress",
            onBack = onBack
        )

        when (progressState) {
            is UiState.Loading -> LoadingStateBox()
            is UiState.Error -> EmptyOrErrorStateCard(strings.offlineError, progressState.message, isError = true, onRetry = onRetry)
            is UiState.Success -> {
                if (progressState.data.isEmpty()) {
                    EmptyOrErrorStateCard(
                        title = if (isBn) "পড়ার ইতিহাস খালি" else "No Reading History Yet",
                        body = if (isBn) "যেকোনো পাঠ্যবই বা গাইড খুললে আপনার সর্বশেষ পঠিত পৃষ্ঠা এখানে সংরক্ষিত থাকবে।" else "Open any textbook or guide to automatically track your reading progress."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(progressState.data, key = { it.id }) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onResumeItem(item) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.titleBn,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${classTitle(item.classNumber, lang)} • ${item.subjectNameBn}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = { onResumeItem(item) },
                                            shape = RoundedCornerShape(50),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldDark)
                                        ) {
                                            Text(
                                                text = if (isBn) "পৃষ্ঠা ${item.lastPage.formatLocalized(lang)} খুলুন" else "Page ${item.lastPage}",
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    LinearProgressIndicator(
                                        progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                                        color = EmeraldPrimary,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(7.dp)
                                            .clip(RoundedCornerShape(50))
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
