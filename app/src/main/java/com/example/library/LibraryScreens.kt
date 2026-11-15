package com.example.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.components.EmptyOrErrorStateCard
import com.example.components.GuideItemCard
import com.example.components.LoadingStateBox
import com.example.components.NavyScreenTopBar
import com.example.components.TextbookItemCard
import com.example.model.Bookmark
import com.example.model.ContentClassGroupHelper
import com.example.model.DefaultCatalogSeed
import com.example.model.GuideNote
import com.example.model.SchoolClass
import com.example.model.Subject
import com.example.model.Textbook
import com.example.model.UiState
import com.example.model.sortedBySerialOrder
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.formatLocalized
import com.example.ui.i18n.localizedAuthor
import com.example.ui.i18n.localizedPublisher
import com.example.ui.i18n.localizedShortDescription
import com.example.ui.i18n.localizedSubject
import com.example.ui.i18n.localizedTitle
import com.example.ui.i18n.streamShortLabel
import com.example.ui.i18n.streamTitle

enum class LibrarySortOption {
    RECOMMENDED,
    TITLE_ASC,
    NEWEST,
    POPULAR,
    FREE_FIRST;

    fun label(lang: AppLanguage): String = when (this) {
        RECOMMENDED -> if (lang == AppLanguage.BN) "ডিফল্ট ক্রম" else "Recommended"
        TITLE_ASC -> if (lang == AppLanguage.BN) "নাম (ক-হ / A-Z)" else "Title (A-Z)"
        NEWEST -> if (lang == AppLanguage.BN) "নতুন সংযোজন" else "Newest First"
        POPULAR -> if (lang == AppLanguage.BN) "জনপ্রিয় আগে" else "Popular First"
        FREE_FIRST -> if (lang == AppLanguage.BN) "ফ্রি আগে" else "Free First"
    }
}

private fun matchesStreamFilter(itemClassNumber: Int, itemStream: String, activeStreamFilter: String): Boolean {
    if (itemClassNumber !in 9..10 || activeStreamFilter.isBlank() || activeStreamFilter.equals("ALL", ignoreCase = true)) return true
    val cleanItem = itemStream.trim().uppercase()
    val cleanFilter = activeStreamFilter.trim().uppercase()
    return cleanItem == "ALL" || cleanItem.isBlank() || cleanItem == cleanFilter
}

private fun sortBooks(books: List<Textbook>, sort: LibrarySortOption, lang: AppLanguage): List<Textbook> = when (sort) {
    LibrarySortOption.RECOMMENDED,
    LibrarySortOption.NEWEST -> books.sortedWith(
        compareByDescending<Textbook> { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            .thenByDescending { it.isPopular }
            .thenBy { it.localizedTitle(lang) }
    )
    LibrarySortOption.TITLE_ASC -> books.sortedBy { it.localizedTitle(lang).lowercase() }
    LibrarySortOption.POPULAR -> books.sortedWith(
        compareByDescending<Textbook> { it.isPopular }
            .thenByDescending { it.pageCount }
            .thenBy { it.localizedTitle(lang) }
    )
    LibrarySortOption.FREE_FIRST -> books.sortedWith(
        compareBy<Textbook> { it.isPremium }
            .thenBy { it.localizedTitle(lang) }
    )
}

private fun sortGuides(guides: List<GuideNote>, sort: LibrarySortOption, lang: AppLanguage): List<GuideNote> = when (sort) {
    LibrarySortOption.RECOMMENDED,
    LibrarySortOption.NEWEST -> guides.sortedWith(
        compareByDescending<GuideNote> { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
            .thenBy { it.chapter }
            .thenBy { it.localizedTitle(lang) }
    )
    LibrarySortOption.TITLE_ASC -> guides.sortedBy { it.localizedTitle(lang).lowercase() }
    LibrarySortOption.POPULAR -> guides.sortedWith(
        compareByDescending<GuideNote> { it.pageCount }
            .thenBy { it.localizedTitle(lang) }
    )
    LibrarySortOption.FREE_FIRST -> guides.sortedWith(
        compareBy<GuideNote> { it.isPremium }
            .thenBy { it.localizedTitle(lang) }
    )
}

@Composable
fun TextbookListScreen(
    classNumber: Int,
    stream: String,
    subjects: List<Subject>,
    booksState: UiState<List<Textbook>>,
    bookmarks: List<Bookmark>,
    onBack: () -> Unit,
    onOpenBook: (Textbook) -> Unit,
    onToggleBookBookmark: (Textbook) -> Unit,
    isBookUnlocked: (Textbook) -> Boolean = { !it.isPremium },
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeStreamFilter by rememberSaveable(classNumber, stream) {
        mutableStateOf(if (classNumber in 9..10) stream.uppercase().ifBlank { "ALL" } else "ALL")
    }
    var selectedSubjectFilter by rememberSaveable { mutableStateOf("ALL") }
    var sortOptionName by rememberSaveable { mutableStateOf(LibrarySortOption.NEWEST.name) }
    val sortOption = LibrarySortOption.entries.firstOrNull { it.name == sortOptionName } ?: LibrarySortOption.NEWEST

    val bookmarkedBookIds = remember(bookmarks) {
        bookmarks.filter { it.bookmarkType == "BOOK" }.map { it.targetId }.toSet()
    }

    val subtitle = buildString {
        append(classTitle(classNumber, lang))
        if (classNumber in 9..10 && activeStreamFilter != "ALL") {
            append(" • ")
            append(streamTitle(activeStreamFilter, lang))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("textbook_list_screen")
    ) {
        NavyScreenTopBar(
            title = strings.originalTextbooks,
            subtitle = subtitle,
            onBack = onBack
        )

        when (booksState) {
            is UiState.Loading -> LoadingStateBox()
            is UiState.Error -> EmptyOrErrorStateCard(
                title = strings.offlineError,
                body = booksState.message,
                isError = true,
                onRetry = onRetry
            )
            is UiState.Success -> {
                val classBooks = remember(booksState.data, classNumber, activeStreamFilter) {
                    booksState.data.filter { book ->
                        book.classNumber == classNumber &&
                            matchesStreamFilter(book.classNumber, book.stream, activeStreamFilter)
                    }
                }

                val dynamicSubjects = remember(subjects, classBooks, activeStreamFilter) {
                    val fromCollection = subjects.filter { s ->
                        s.classNumber == classNumber &&
                            matchesStreamFilter(s.classNumber, s.stream, activeStreamFilter)
                    }.map { s -> if (lang == AppLanguage.EN && s.nameEn.isNotBlank()) s.nameEn else s.nameBn }
                    val fromBooks = classBooks.map { it.localizedSubject(lang) }.filter { it.isNotBlank() }
                    (fromCollection + fromBooks).distinct()
                }

                val filteredAndSortedBooks = remember(classBooks, searchQuery, selectedSubjectFilter, sortOption, lang) {
                    val filtered = classBooks.filter { book ->
                        val matchesSubject = selectedSubjectFilter == "ALL" ||
                            book.subjectNameBn.equals(selectedSubjectFilter, ignoreCase = true) ||
                            book.localizedSubject(lang).equals(selectedSubjectFilter, ignoreCase = true)
                        val q = searchQuery.trim()
                        val matchesQuery = q.isBlank() ||
                            book.titleBn.contains(q, ignoreCase = true) ||
                            book.titleEn.contains(q, ignoreCase = true) ||
                            book.subjectNameBn.contains(q, ignoreCase = true) ||
                            book.subjectNameEn.contains(q, ignoreCase = true) ||
                            book.localizedAuthor(lang).contains(q, ignoreCase = true) ||
                            book.localizedPublisher(lang).contains(q, ignoreCase = true) ||
                            book.edition.contains(q, ignoreCase = true) ||
                            book.localizedShortDescription(lang).contains(q, ignoreCase = true)
                        matchesSubject && matchesQuery
                    }
                    sortBooks(filtered, sortOption, lang)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                if (isBn) "বই, লেখক, প্রকাশক বা বিষয় খুঁজুন..."
                                else "Search book, author, publisher or subject..."
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
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
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("textbooks_search_input")
                    )

                    // Stream Filter Row for Classes 9 & 10 (Science, Humanities, Business Studies)
                    if (classNumber in 9..10) {
                        ClassStreamFilterRow(
                            selectedStream = activeStreamFilter,
                            lang = lang,
                            onSelectStream = { nextStream ->
                                activeStreamFilter = nextStream
                                selectedSubjectFilter = "ALL"
                            }
                        )
                    }

                    // Subject & Free/Premium Filter Chips
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("textbooks_subject_filter_row")
                    ) {
                        item {
                            FilterChip(
                                selected = selectedSubjectFilter == "ALL",
                                onClick = { selectedSubjectFilter = "ALL" },
                                label = { Text(strings.allFilter) }
                            )
                        }
                        items(dynamicSubjects) { subjName ->
                            FilterChip(
                                selected = selectedSubjectFilter == subjName,
                                onClick = { selectedSubjectFilter = subjName },
                                label = { Text(subjName) },
                                modifier = Modifier.testTag("subject_chip_$subjName")
                            )
                        }
                    }

                    // Count & Sort Toolbar
                    LibraryCountAndSortBar(
                        count = filteredAndSortedBooks.size,
                        itemWordBn = "টি বই",
                        itemWordEn = "books",
                        currentSort = sortOption,
                        lang = lang,
                        onSelectSort = { sortOptionName = it.name }
                    )

                    if (filteredAndSortedBooks.isEmpty()) {
                        EmptyOrErrorStateCard(
                            title = strings.emptyStateTitle,
                            body = if (isBn) {
                                "এই শ্রেণির অনুমোদিত মূল পাঠ্যবই সুপার অ্যাডমিন প্যানেল থেকে যুক্ত করলে এখানে দুই-কলাম গ্রিডে প্রদর্শিত হবে।"
                            } else {
                                "Authorized NCTB textbooks added from the Super Admin Panel for this class will appear here."
                            }
                        )
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            // Strictly 2 columns side-by-side on mobile phones (< 700dp); 3 on wide tablets
                            val columnCount = if (maxWidth >= 700.dp) 3 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columnCount),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("textbooks_two_column_grid")
                            ) {
                                itemsIndexed(
                                    items = filteredAndSortedBooks,
                                    key = { index, book -> book.id.takeIf { it.isNotBlank() }?.let { "${it}_$index" } ?: "book_idx_$index" }
                                ) { _, book ->
                                    TextbookItemCard(
                                        book = book,
                                        isBookmarked = book.id in bookmarkedBookIds,
                                        onReadClick = { onOpenBook(book) },
                                        onBookmarkClick = { onToggleBookBookmark(book) },
                                        isUnlockedPremium = book.isPremium && isBookUnlocked(book)
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

@Composable
fun GuideListScreen(
    classNumber: Int,
    stream: String,
    subjects: List<Subject>,
    guidesState: UiState<List<GuideNote>>,
    bookmarks: List<Bookmark>,
    onBack: () -> Unit,
    onOpenGuide: (GuideNote) -> Unit,
    onToggleGuideBookmark: (GuideNote) -> Unit,
    isGuideUnlocked: (GuideNote) -> Boolean = { !it.isPremium },
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var activeStreamFilter by rememberSaveable(classNumber, stream) {
        mutableStateOf(if (classNumber in 9..10) stream.uppercase().ifBlank { "ALL" } else "ALL")
    }
    var selectedSubject by rememberSaveable { mutableStateOf("ALL") }
    var selectedGuideType by rememberSaveable { mutableStateOf("ALL") }
    var sortOptionName by rememberSaveable { mutableStateOf(LibrarySortOption.NEWEST.name) }
    val sortOption = LibrarySortOption.entries.firstOrNull { it.name == sortOptionName } ?: LibrarySortOption.NEWEST

    val bookmarkedGuideIds = remember(bookmarks) {
        bookmarks.filter { it.bookmarkType == "GUIDE" }.map { it.targetId }.toSet()
    }

    val subtitle = buildString {
        append(classTitle(classNumber, lang))
        if (classNumber in 9..10 && activeStreamFilter != "ALL") {
            append(" • ")
            append(streamTitle(activeStreamFilter, lang))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("guide_list_screen")
    ) {
        NavyScreenTopBar(
            title = strings.guidesAndNotes,
            subtitle = subtitle,
            onBack = onBack
        )

        when (guidesState) {
            is UiState.Loading -> LoadingStateBox()
            is UiState.Error -> EmptyOrErrorStateCard(
                title = strings.offlineError,
                body = guidesState.message,
                isError = true,
                onRetry = onRetry
            )
            is UiState.Success -> {
                val classGuides = remember(guidesState.data, classNumber, activeStreamFilter) {
                    guidesState.data.filter { g ->
                        g.classNumber == classNumber &&
                            matchesStreamFilter(g.classNumber, g.stream, activeStreamFilter)
                    }
                }

                val dynamicSubjects = remember(subjects, classGuides, activeStreamFilter) {
                    val fromCol = subjects.filter {
                        it.classNumber == classNumber &&
                            matchesStreamFilter(it.classNumber, it.stream, activeStreamFilter)
                    }.map { s -> if (lang == AppLanguage.EN && s.nameEn.isNotBlank()) s.nameEn else s.nameBn }
                    val fromGuides = classGuides.map { it.localizedSubject(lang) }.filter { it.isNotBlank() }
                    (fromCol + fromGuides).distinct()
                }

                val filteredAndSorted = remember(classGuides, searchQuery, selectedSubject, selectedGuideType, sortOption, lang) {
                    val filtered = classGuides.filter { g ->
                        val okSubj = selectedSubject == "ALL" ||
                            g.subjectNameBn == selectedSubject ||
                            g.localizedSubject(lang) == selectedSubject
                        val okType = selectedGuideType == "ALL" || g.guideType == selectedGuideType
                        val q = searchQuery.trim()
                        val okQuery = q.isBlank() ||
                            g.titleBn.contains(q, ignoreCase = true) ||
                            g.titleEn.contains(q, ignoreCase = true) ||
                            g.chapter.contains(q, ignoreCase = true) ||
                            g.subjectNameBn.contains(q, ignoreCase = true) ||
                            g.localizedAuthor(lang).contains(q, ignoreCase = true) ||
                            g.localizedPublisher(lang).contains(q, ignoreCase = true) ||
                            g.localizedShortDescription(lang).contains(q, ignoreCase = true)
                        okSubj && okType && okQuery
                    }
                    sortGuides(filtered, sortOption, lang)
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                if (isBn) "অধ্যায়, গাইড, লেখক বা নোট খুঁজুন..."
                                else "Search chapter, guide, author or note..."
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Clear, contentDescription = "Clear")
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
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("guides_search_input")
                    )

                    // Stream Filter Row for Classes 9 & 10
                    if (classNumber in 9..10) {
                        ClassStreamFilterRow(
                            selectedStream = activeStreamFilter,
                            lang = lang,
                            onSelectStream = { nextStream ->
                                activeStreamFilter = nextStream
                                selectedSubject = "ALL"
                            }
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedSubject == "ALL",
                                onClick = { selectedSubject = "ALL" },
                                label = { Text(strings.allFilter) }
                            )
                        }
                        items(dynamicSubjects) { sName ->
                            FilterChip(
                                selected = selectedSubject == sName,
                                onClick = { selectedSubject = sName },
                                label = { Text(sName) }
                            )
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedGuideType == "ALL",
                                onClick = { selectedGuideType = "ALL" },
                                label = { Text(if (isBn) "সকল ধরন" else "All Types") }
                            )
                        }
                        items(DefaultCatalogSeed.guideTypes) { gt ->
                            FilterChip(
                                selected = selectedGuideType == gt.code,
                                onClick = { selectedGuideType = gt.code },
                                label = { Text(if (isBn) gt.nameBn else gt.nameEn) }
                            )
                        }
                    }

                    LibraryCountAndSortBar(
                        count = filteredAndSorted.size,
                        itemWordBn = "টি গাইড ও নোট",
                        itemWordEn = "guides & notes",
                        currentSort = sortOption,
                        lang = lang,
                        onSelectSort = { sortOptionName = it.name }
                    )

                    if (filteredAndSorted.isEmpty()) {
                        EmptyOrErrorStateCard(
                            title = strings.emptyStateTitle,
                            body = if (isBn) {
                                "এই শ্রেণি ও বিষয়ের অনুমোদিত গাইড বা অধ্যায়ভিত্তিক নোট প্রকাশিত হলে এখানে প্রদর্শিত হবে।"
                            } else {
                                "Authorized guides and chapter notes for this class will appear here once published."
                            }
                        )
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columnCount = if (maxWidth >= 700.dp) 3 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columnCount),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("guides_two_column_grid")
                            ) {
                                itemsIndexed(
                                    items = filteredAndSorted,
                                    key = { index, guide -> guide.id.takeIf { it.isNotBlank() }?.let { "${it}_$index" } ?: "guide_idx_$index" }
                                ) { _, guide ->
                                    val gtMeta = DefaultCatalogSeed.guideTypes.firstOrNull { it.code == guide.guideType }
                                    GuideItemCard(
                                        guide = guide,
                                        guideTypeLabel = if (isBn) gtMeta?.nameBn.orEmpty() else gtMeta?.nameEn.orEmpty(),
                                        isBookmarked = guide.id in bookmarkedGuideIds,
                                        onOpenClick = { onOpenGuide(guide) },
                                        onBookmarkClick = { onToggleGuideBookmark(guide) },
                                        isUnlockedPremium = guide.isPremium && isGuideUnlocked(guide)
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

@Composable
fun LibraryHubScreen(
    classes: List<SchoolClass> = DefaultCatalogSeed.defaultClasses,
    booksState: UiState<List<Textbook>>,
    guidesState: UiState<List<GuideNote>>,
    subjects: List<Subject> = emptyList(),
    bookmarks: List<Bookmark>,
    onOpenBook: (Textbook) -> Unit,
    onOpenGuide: (GuideNote) -> Unit,
    onToggleBookBookmark: (Textbook) -> Unit,
    onToggleGuideBookmark: (GuideNote) -> Unit,
    isBookUnlocked: (Textbook) -> Boolean = { !it.isPremium },
    isGuideUnlocked: (GuideNote) -> Boolean = { !it.isPremium },
    onRetry: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var classFilter by rememberSaveable { mutableIntStateOf(0) } // 0 = All classes
    var streamFilter by rememberSaveable { mutableStateOf("ALL") }
    var subjectFilter by rememberSaveable { mutableStateOf("ALL") }
    var query by rememberSaveable { mutableStateOf("") }
    var sortOptionName by rememberSaveable { mutableStateOf(LibrarySortOption.RECOMMENDED.name) }
    val sortOption = LibrarySortOption.entries.firstOrNull { it.name == sortOptionName } ?: LibrarySortOption.RECOMMENDED
    val filterClassNumbers = remember(classes, booksState, guidesState) {
        val fromClasses = classes.ifEmpty { DefaultCatalogSeed.defaultClasses }.sortedBySerialOrder().map { it.classNumber }
        val fromBooks = (booksState as? UiState.Success)?.data.orEmpty().map { it.classNumber }.filter { it > 0 }
        val fromGuides = (guidesState as? UiState.Success)?.data.orEmpty().map { it.classNumber }.filter { it > 0 }
        (fromClasses + fromBooks + fromGuides).distinct().sorted()
    }

    val bookmarkedBookIds = remember(bookmarks) {
        bookmarks.filter { it.bookmarkType == "BOOK" }.map { it.targetId }.toSet()
    }
    val bookmarkedGuideIds = remember(bookmarks) {
        bookmarks.filter { it.bookmarkType == "GUIDE" }.map { it.targetId }.toSet()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("library_hub_screen")
    ) {
        NavyScreenTopBar(
            title = strings.navLibrary,
            subtitle = if (isBn) "মূল পাঠ্যবই এবং গাইড ও নোট সংগ্রহশালা" else "Original Textbooks & Study Guides"
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    selectedTab = 0
                    subjectFilter = "ALL"
                },
                text = { Text(strings.originalTextbooks, fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    selectedTab = 1
                    subjectFilter = "ALL"
                },
                text = { Text(strings.guidesAndNotes, fontWeight = FontWeight.Bold) }
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(strings.searchHint) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
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
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("library_hub_search_input")
        )

        // Class 5–10 Filter Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = classFilter == 0,
                    onClick = {
                        classFilter = 0
                        streamFilter = "ALL"
                        subjectFilter = "ALL"
                    },
                    label = { Text(strings.allFilter) }
                )
            }
            items(filterClassNumbers) { cls ->
                FilterChip(
                    selected = classFilter == cls,
                    onClick = {
                        classFilter = cls
                        if (cls !in 9..10) streamFilter = "ALL"
                        subjectFilter = "ALL"
                    },
                    label = { Text(classTitle(cls, lang)) },
                    modifier = Modifier.testTag("hub_class_chip_$cls")
                )
            }
        }

        // Stream Filter Row for Classes 9 & 10
        if (classFilter == 9 || classFilter == 10) {
            ClassStreamFilterRow(
                selectedStream = streamFilter,
                lang = lang,
                onSelectStream = {
                    streamFilter = it
                    subjectFilter = "ALL"
                }
            )
        }

        if (selectedTab == 0) {
            when (booksState) {
                is UiState.Loading -> LoadingStateBox()
                is UiState.Error -> EmptyOrErrorStateCard(strings.offlineError, booksState.message, isError = true, onRetry = onRetry)
                is UiState.Success -> {
                    val baseBooks = remember(booksState.data, classFilter, streamFilter) {
                        val filtered = booksState.data.filter { b ->
                            (classFilter == 0 || b.classNumber == classFilter) &&
                                matchesStreamFilter(b.classNumber, b.stream, streamFilter)
                        }
                        if (classFilter == 0) {
                            ContentClassGroupHelper.deduplicateBooksForAllClassesView(filtered)
                        } else {
                            filtered
                        }
                    }

                    val availableSubjects = remember(subjects, baseBooks, classFilter, streamFilter, lang) {
                        val fromSubj = subjects.filter { s ->
                            (classFilter == 0 || s.classNumber == classFilter) &&
                                matchesStreamFilter(s.classNumber, s.stream, streamFilter)
                        }.map { if (lang == AppLanguage.EN && it.nameEn.isNotBlank()) it.nameEn else it.nameBn }
                        val fromBooks = baseBooks.map { it.localizedSubject(lang) }.filter { it.isNotBlank() }
                        (fromSubj + fromBooks).distinct()
                    }

                    if (availableSubjects.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = subjectFilter == "ALL",
                                    onClick = { subjectFilter = "ALL" },
                                    label = { Text(if (isBn) "সকল বিষয়" else "All Subjects") }
                                )
                            }
                            items(availableSubjects) { sName ->
                                FilterChip(
                                    selected = subjectFilter == sName,
                                    onClick = { subjectFilter = sName },
                                    label = { Text(sName) }
                                )
                            }
                        }
                    }

                    val items = remember(baseBooks, subjectFilter, query, sortOption, lang) {
                        val q = query.trim()
                        val filtered = baseBooks.filter { b ->
                            val okSubj = subjectFilter == "ALL" ||
                                b.subjectNameBn == subjectFilter ||
                                b.localizedSubject(lang) == subjectFilter
                            val okQuery = q.isBlank() ||
                                b.titleBn.contains(q, true) ||
                                b.titleEn.contains(q, true) ||
                                b.subjectNameBn.contains(q, true) ||
                                b.localizedAuthor(lang).contains(q, true) ||
                                b.localizedPublisher(lang).contains(q, true) ||
                                b.edition.contains(q, true)
                            okSubj && okQuery
                        }
                        sortBooks(filtered, sortOption, lang)
                    }

                    LibraryCountAndSortBar(
                        count = items.size,
                        itemWordBn = "টি বই",
                        itemWordEn = "books",
                        currentSort = sortOption,
                        lang = lang,
                        onSelectSort = { sortOptionName = it.name }
                    )

                    if (items.isEmpty()) {
                        EmptyOrErrorStateCard(strings.emptyStateTitle, strings.emptyStateBody)
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columnCount = if (maxWidth >= 700.dp) 3 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columnCount),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("library_hub_two_column_grid")
                            ) {
                                itemsIndexed(
                                    items = items,
                                    key = { index, book -> book.id.takeIf { it.isNotBlank() }?.let { "${it}_$index" } ?: "hub_book_$index" }
                                ) { _, book ->
                                    TextbookItemCard(
                                        book = book,
                                        isBookmarked = book.id in bookmarkedBookIds,
                                        onReadClick = { onOpenBook(book) },
                                        onBookmarkClick = { onToggleBookBookmark(book) },
                                        isUnlockedPremium = book.isPremium && isBookUnlocked(book)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            when (guidesState) {
                is UiState.Loading -> LoadingStateBox()
                is UiState.Error -> EmptyOrErrorStateCard(strings.offlineError, guidesState.message, isError = true, onRetry = onRetry)
                is UiState.Success -> {
                    val baseGuides = remember(guidesState.data, classFilter, streamFilter) {
                        val filtered = guidesState.data.filter { g ->
                            (classFilter == 0 || g.classNumber == classFilter) &&
                                matchesStreamFilter(g.classNumber, g.stream, streamFilter)
                        }
                        if (classFilter == 0) {
                            ContentClassGroupHelper.deduplicateGuidesForAllClassesView(filtered)
                        } else {
                            filtered
                        }
                    }

                    val availableSubjects = remember(subjects, baseGuides, classFilter, streamFilter, lang) {
                        val fromSubj = subjects.filter { s ->
                            (classFilter == 0 || s.classNumber == classFilter) &&
                                matchesStreamFilter(s.classNumber, s.stream, streamFilter)
                        }.map { if (lang == AppLanguage.EN && it.nameEn.isNotBlank()) it.nameEn else it.nameBn }
                        val fromGuides = baseGuides.map { it.localizedSubject(lang) }.filter { it.isNotBlank() }
                        (fromSubj + fromGuides).distinct()
                    }

                    if (availableSubjects.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = subjectFilter == "ALL",
                                    onClick = { subjectFilter = "ALL" },
                                    label = { Text(if (isBn) "সকল বিষয়" else "All Subjects") }
                                )
                            }
                            items(availableSubjects) { sName ->
                                FilterChip(
                                    selected = subjectFilter == sName,
                                    onClick = { subjectFilter = sName },
                                    label = { Text(sName) }
                                )
                            }
                        }
                    }

                    val items = remember(baseGuides, subjectFilter, query, sortOption, lang) {
                        val q = query.trim()
                        val filtered = baseGuides.filter { g ->
                            val okSubj = subjectFilter == "ALL" ||
                                g.subjectNameBn == subjectFilter ||
                                g.localizedSubject(lang) == subjectFilter
                            val okQuery = q.isBlank() ||
                                g.titleBn.contains(q, true) ||
                                g.titleEn.contains(q, true) ||
                                g.subjectNameBn.contains(q, true) ||
                                g.chapter.contains(q, true) ||
                                g.localizedAuthor(lang).contains(q, true)
                            okSubj && okQuery
                        }
                        sortGuides(filtered, sortOption, lang)
                    }

                    LibraryCountAndSortBar(
                        count = items.size,
                        itemWordBn = "টি গাইড ও নোট",
                        itemWordEn = "guides & notes",
                        currentSort = sortOption,
                        lang = lang,
                        onSelectSort = { sortOptionName = it.name }
                    )

                    if (items.isEmpty()) {
                        EmptyOrErrorStateCard(strings.emptyStateTitle, strings.emptyStateBody)
                    } else {
                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val columnCount = if (maxWidth >= 700.dp) 3 else 2
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(columnCount),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("library_hub_two_column_grid")
                            ) {
                                itemsIndexed(
                                    items = items,
                                    key = { index, guide -> guide.id.takeIf { it.isNotBlank() }?.let { "${it}_$index" } ?: "hub_guide_$index" }
                                ) { _, guide ->
                                    val gt = DefaultCatalogSeed.guideTypes.firstOrNull { it.code == guide.guideType }
                                    GuideItemCard(
                                        guide = guide,
                                        guideTypeLabel = if (isBn) gt?.nameBn.orEmpty() else gt?.nameEn.orEmpty(),
                                        isBookmarked = guide.id in bookmarkedGuideIds,
                                        onOpenClick = { onOpenGuide(guide) },
                                        onBookmarkClick = { onToggleGuideBookmark(guide) },
                                        isUnlockedPremium = guide.isPremium && isGuideUnlocked(guide)
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

@Composable
private fun ClassStreamFilterRow(
    selectedStream: String,
    lang: AppLanguage,
    onSelectStream: (String) -> Unit
) {
    val streamCodes = listOf("ALL", "SCIENCE", "HUMANITIES", "BUSINESS")
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("library_stream_filter_row")
    ) {
        items(streamCodes) { code ->
            val label = if (code == "ALL") {
                if (lang == AppLanguage.BN) "সকল বিভাগ" else "All Streams"
            } else {
                streamShortLabel(code, lang)
            }
            FilterChip(
                selected = selectedStream.equals(code, ignoreCase = true),
                onClick = { onSelectStream(code) },
                label = { Text(label, fontWeight = FontWeight.SemiBold) },
                modifier = Modifier.testTag("stream_filter_chip_$code")
            )
        }
    }
}

@Composable
private fun LibraryCountAndSortBar(
    count: Int,
    itemWordBn: String,
    itemWordEn: String,
    currentSort: LibrarySortOption,
    lang: AppLanguage,
    onSelectSort: (LibrarySortOption) -> Unit
) {
    val isBn = lang == AppLanguage.BN
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (isBn) {
                "${count.formatLocalized(lang)}$itemWordBn"
            } else {
                "$count $itemWordEn"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            AssistChip(
                onClick = { expanded = true },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        text = currentSort.label(lang),
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                modifier = Modifier.testTag("library_sort_button")
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                LibrarySortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label(lang)) },
                        trailingIcon = {
                            if (option == currentSort) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        },
                        onClick = {
                            onSelectSort(option)
                            expanded = false
                        },
                        modifier = Modifier.testTag("sort_option_${option.name}")
                    )
                }
            }
        }
    }
}
