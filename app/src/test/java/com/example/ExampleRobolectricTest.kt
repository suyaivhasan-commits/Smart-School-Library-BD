package com.example

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.test.core.app.ApplicationProvider
import com.example.components.resolveFirebaseStorageUrl
import com.example.data.PremiumAccessDecision
import com.example.data.StudyRepository
import com.example.firebase.SecurityConstants
import com.example.home.HomeScreen
import com.example.library.BookDetailScreen
import com.example.library.GuideListScreen
import com.example.library.TextbookListScreen
import com.example.model.AppSettings
import com.example.model.DefaultCatalogSeed
import com.example.model.ExamTest
import com.example.model.GuideNote
import com.example.model.Subject
import com.example.model.Subscription
import com.example.model.Textbook
import com.google.firebase.Timestamp
import java.util.Date
import com.example.model.UiState
import com.example.model.UserNote
import com.example.model.UserProfile
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.theme.SmartSchoolTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appIdentityAndFirestoreConfig_areConfiguredCorrectly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Smart School Library BD", context.getString(R.string.app_name))
        assertEquals("(default)", context.getString(R.string.firestore_database_id))
    }

    @Test
    fun superAdminAuthorization_strictlyMatchesTrustedUidOnly() {
        assertTrue(SecurityConstants.isTrustedSuperAdmin("yyskhmPvvfeNl572FnVhZgYAaQx2"))
        assertFalse(SecurityConstants.isTrustedSuperAdmin("student_uid_12345"))
        assertFalse(SecurityConstants.isTrustedSuperAdmin(""))
        assertFalse(SecurityConstants.isTrustedSuperAdmin(null))
    }

    @Test
    fun centralizedDownloadPolicy_enforcesMasterTypeAndItemControls() {
        val disabledMaster = AppSettings(
            downloadsEnabled = false,
            allowTextbookDownload = true,
            allowGuideDownload = true
        )
        assertFalse(disabledMaster.canDownloadContent("BOOK", itemAllowsDownload = true))

        val enabledBooksOnly = AppSettings(
            downloadsEnabled = true,
            allowTextbookDownload = true,
            allowGuideDownload = false,
            allowNoteDownload = false
        )
        assertTrue(enabledBooksOnly.canDownloadContent("BOOK", itemAllowsDownload = true))
        assertFalse(enabledBooksOnly.canDownloadContent("BOOK", itemAllowsDownload = false))
        assertFalse(enabledBooksOnly.canDownloadContent("GUIDE", itemAllowsDownload = true))
    }

    @Test
    fun firebaseStorageUrlResolver_handlesHttpsGsAndRelativePaths() {
        assertEquals(
            "https://cdn.example.org/covers/bangla.jpg",
            resolveFirebaseStorageUrl("https://cdn.example.org/covers/bangla.jpg")
        )
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/smart-school-library-bd.firebasestorage.app/o/covers%2Fclass9_physics.png?alt=media",
            resolveFirebaseStorageUrl("gs://smart-school-library-bd.firebasestorage.app/covers/class9_physics.png")
        )
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/smart-school-library-bd.firebasestorage.app/o/books%2Fc5_math.pdf?alt=media",
            resolveFirebaseStorageUrl("books/c5_math.pdf")
        )
        assertEquals("", resolveFirebaseStorageUrl("   "))
    }

    @Test
    fun homeScreen_hidesSuperAdminButtonFromNormalStudents_andShowsClassCards() {
        composeRule.setContent {
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.BN)) {
                    HomeScreen(
                        currentUserUid = "normal_student_uid_99",
                        userProfile = UserProfile(userId = "normal_student_uid_99", name = "রাহাতুল ইসলাম", role = "student"),
                        classes = DefaultCatalogSeed.defaultClasses,
                        books = emptyList(),
                        guides = emptyList(),
                        bookmarks = emptyList(),
                        readingProgress = emptyList(),
                        notices = emptyList(),
                        subscriptions = emptyList(),
                        appSettings = AppSettings(),
                        isDarkTheme = false,
                        onToggleTheme = {},
                        onOpenProfile = {},
                        onOpenNotifications = {},
                        onOpenClass = {},
                        onOpenBook = {},
                        onOpenGuide = {},
                        onResumeReading = {},
                        onToggleBookBookmark = {},
                        onToggleGuideBookmark = {},
                        onOpenNotebook = {},
                        onOpenGk = {},
                        onOpenExam = {},
                        onOpenBookmarks = {},
                        onOpenHistory = {},
                        onOpenSubscription = {},
                        onOpenSettings = {},
                        onOpenSuperAdmin = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("class_button_5").assertIsDisplayed()
        composeRule.onNodeWithTag("class_button_6").assertIsDisplayed()
        composeRule.onNodeWithTag("home_super_admin_button").assertDoesNotExist()
    }

    @Test
    fun mobileScreen_rendersTwoBookCardsSideBySide_withCoverPlaceholderBookmarkAndMetadata() {
        var clickedBookId: String? = null
        var bookmarkedId: String? = null

        val sampleBooks = listOf(
            Textbook(
                id = "b1",
                titleBn = "আমার বাংলা বই",
                subjectNameBn = "বাংলা",
                classNumber = 6,
                stream = "ALL",
                author = "জাতীয় শিক্ষাক্রম বোর্ড",
                publisher = "NCTB ঢাকা",
                edition = "২০২৬ সংস্করণ",
                shortDescription = "ষষ্ঠ শ্রেণির বাংলা সাহিত্য ও গদ্য-পদ্য সংকলন।",
                coverUrl = "", // Missing cover -> must render StyledBookCoverPlaceholder
                isPremium = false
            ),
            Textbook(
                id = "b2",
                titleBn = "গণিত",
                subjectNameBn = "গণিত",
                classNumber = 6,
                stream = "ALL",
                author = "ড. মাহবুবুর রহমান",
                publisher = "NCTB ঢাকা",
                edition = "২০২৬ সংস্করণ",
                shortDescription = "পাটিগণিত, বীজগণিত ও জ্যামিতি অনুশীলন।",
                coverUrl = "",
                isPremium = true
            ),
            Textbook(
                id = "b3",
                titleBn = "বিজ্ঞান অনুসন্ধানী পাঠ",
                subjectNameBn = "বিজ্ঞান",
                classNumber = 6,
                stream = "ALL",
                author = "NCTB বিজ্ঞান প্যানেল",
                publisher = "NCTB ঢাকা",
                edition = "২০২৬ সংস্করণ",
                shortDescription = "ষষ্ঠ শ্রেণির বিজ্ঞান অনুসন্ধানী পাঠ্যবই।",
                coverUrl = "",
                isPremium = false
            )
        )

        composeRule.setContent {
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.BN)) {
                    TextbookListScreen(
                        classNumber = 6,
                        stream = "ALL",
                        subjects = listOf(
                            Subject(id = "s1", classNumber = 6, nameBn = "বাংলা"),
                            Subject(id = "s2", classNumber = 6, nameBn = "গণিত"),
                            Subject(id = "s3", classNumber = 6, nameBn = "বিজ্ঞান")
                        ),
                        booksState = UiState.Success(sampleBooks),
                        bookmarks = emptyList(),
                        onBack = {},
                        onOpenBook = { clickedBookId = it.id },
                        onToggleBookBookmark = { bookmarkedId = it.id }
                    )
                }
            }
        }

        composeRule.waitForIdle()

        // Verify two-column grid container & first two cards side-by-side on 390dp mobile viewport
        composeRule.onNodeWithTag("textbooks_two_column_grid").assertIsDisplayed()
        val card1Bounds = composeRule.onNodeWithTag("book_card_b1").getUnclippedBoundsInRoot()
        val card2Bounds = composeRule.onNodeWithTag("book_card_b2").getUnclippedBoundsInRoot()

        assertTrue(
            "Expected b1 and b2 on the exact same top row on mobile screen, got top1=${card1Bounds.top} vs top2=${card2Bounds.top}",
            abs(card1Bounds.top.value - card2Bounds.top.value) < 1.5f
        )
        assertTrue(
            "Expected b1 strictly to the left of b2 without overlap, got right1=${card1Bounds.right}, left2=${card2Bounds.left}",
            card1Bounds.right <= card2Bounds.left
        )
        val width1 = card1Bounds.right - card1Bounds.left
        val width2 = card2Bounds.right - card2Bounds.left
        assertTrue(
            "Expected equal card widths in 2-column grid, got w1=$width1, w2=$width2",
            abs(width1.value - width2.value) < 2f
        )

        // Verify missing-cover placeholder and accessible bookmark button exist
        composeRule.onNodeWithTag("cover_placeholder_b1", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("bookmark_book_btn_b1").performClick()
        assertEquals("b1", bookmarkedId)

        // Tapping a card triggers opening the detailed book screen
        composeRule.onNodeWithTag("book_card_b1").performClick()
        assertEquals("b1", clickedBookId)
    }

    @Test
    fun class9And10Library_filtersByScienceHumanitiesAndBusinessStudies_andAppliesToGuides() {
        val class9Books = listOf(
            Textbook(id = "sci_phy", titleBn = "পদার্থবিজ্ঞান", subjectNameBn = "পদার্থবিজ্ঞান", classNumber = 9, stream = "SCIENCE"),
            Textbook(id = "hum_his", titleBn = "বাংলাদেশের ইতিহাস ও বিশ্বসভ্যতা", subjectNameBn = "ইতিহাস", classNumber = 9, stream = "HUMANITIES"),
            Textbook(id = "bus_acc", titleBn = "হিসাববিজ্ঞান", subjectNameBn = "হিসাববিজ্ঞান", classNumber = 9, stream = "BUSINESS")
        )

        composeRule.setContent {
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.BN)) {
                    TextbookListScreen(
                        classNumber = 9,
                        stream = "ALL",
                        subjects = emptyList(),
                        booksState = UiState.Success(class9Books),
                        bookmarks = emptyList(),
                        onBack = {},
                        onOpenBook = {},
                        onToggleBookBookmark = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_stream_filter_row").assertIsDisplayed()

        // Filter by Science
        composeRule.onNodeWithTag("stream_filter_chip_SCIENCE").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("book_card_sci_phy").assertIsDisplayed()
        composeRule.onNodeWithTag("book_card_hum_his").assertDoesNotExist()
        composeRule.onNodeWithTag("book_card_bus_acc").assertDoesNotExist()

        // Filter by Humanities
        composeRule.onNodeWithTag("stream_filter_chip_HUMANITIES").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("book_card_hum_his").assertIsDisplayed()
        composeRule.onNodeWithTag("book_card_sci_phy").assertDoesNotExist()

        // Filter by Business Studies
        composeRule.onNodeWithTag("stream_filter_chip_BUSINESS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("book_card_bus_acc").assertIsDisplayed()
        composeRule.onNodeWithTag("book_card_hum_his").assertDoesNotExist()
    }

    @Test
    fun guidesTwoColumnGrid_rendersTwoGuideCardsSideBySideOnMobile() {
        val sampleGuides = listOf(
            GuideNote(id = "g1", titleBn = "অধ্যায় ১ গাইড", subjectNameBn = "গণিত", classNumber = 8, chapter = "অধ্যায় ১", guideType = "CHAPTER_NOTE"),
            GuideNote(id = "g2", titleBn = "অধ্যায় ২ সৃজনশীল", subjectNameBn = "গণিত", classNumber = 8, chapter = "অধ্যায় ২", guideType = "CREATIVE_QUESTION")
        )

        composeRule.setContent {
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.EN)) {
                    GuideListScreen(
                        classNumber = 8,
                        stream = "ALL",
                        subjects = emptyList(),
                        guidesState = UiState.Success(sampleGuides),
                        bookmarks = emptyList(),
                        onBack = {},
                        onOpenGuide = {},
                        onToggleGuideBookmark = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        val g1Bounds = composeRule.onNodeWithTag("guide_card_g1").getUnclippedBoundsInRoot()
        val g2Bounds = composeRule.onNodeWithTag("guide_card_g2").getUnclippedBoundsInRoot()
        assertTrue(abs(g1Bounds.top.value - g2Bounds.top.value) < 1.5f)
        assertTrue(g1Bounds.right <= g2Bounds.left)
    }

    @Test
    fun bookDetailScreen_displaysDescriptionInfoChaptersNotesExercises_andEnforcesSubscriptionAndDownloadSettings() {
        var openedPdfReader = false
        var openedSubscriptionGate = false

        val premiumPhysicsBook = Textbook(
            id = "c10_phy",
            titleBn = "পদার্থবিজ্ঞান (দশম শ্রেণি)",
            titleEn = "Physics (Class 10)",
            subjectNameBn = "পদার্থবিজ্ঞান",
            subjectNameEn = "Physics",
            classNumber = 10,
            stream = "SCIENCE",
            author = "ড. মুহম্মদ জাফর ইকবাল ও প্যানেল",
            publisher = "NCTB",
            edition = "২০২৬ সংস্করণ",
            description = "দশম শ্রেণির বিজ্ঞান বিভাগের শিক্ষার্থীদের জন্য গতি, বল, কাজ ও শক্তি অধ্যায়সমূহের বিস্তারিত পাঠ্যবই।",
            pdfUrl = "https://example.org/c10_physics.pdf",
            tableOfContents = listOf("অধ্যায় ১: ভৌত রাশি এবং পরিমাপ", "অধ্যায় ২: গতি"),
            isPremium = true,
            allowDownload = true,
            pageCount = 312
        )

        // Case A: Unsubscribed student -> PDF action routes to Subscription Gate & Download hidden
        composeRule.setContent {
            SmartSchoolTheme(darkTheme = true) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.BN)) {
                    BookDetailScreen(
                        book = premiumPhysicsBook,
                        isBookmarked = false,
                        readingProgress = null,
                        accessDecision = PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
                        appSettings = AppSettings(downloadsEnabled = true, allowTextbookDownload = true),
                        relatedGuides = listOf(
                            GuideNote(
                                id = "rel_g1",
                                titleBn = "গতি অধ্যায়ের গাণিতিক সমাধান",
                                subjectNameBn = "পদার্থবিজ্ঞান",
                                classNumber = 10,
                                stream = "SCIENCE",
                                chapter = "অধ্যায় ২"
                            )
                        ),
                        relatedPersonalNotes = listOf(
                            UserNote(id = "n1", title = "গতির সমীকরণ", content = "v = u + at", subjectTag = "পদার্থবিজ্ঞান")
                        ),
                        availableExercises = listOf(
                            ExamTest(id = "ex1", titleBn = "পদার্থবিজ্ঞান মডেল টেস্ট ১", classNumber = 10, questionCount = 20, durationMinutes = 20, totalMarks = 20)
                        ),
                        onBack = {},
                        onToggleBookmark = {},
                        onOpenPdfReader = { openedPdfReader = true },
                        onOpenSubscriptionGate = { openedSubscriptionGate = true },
                        onOpenRelatedGuide = {},
                        onOpenNotebook = {},
                        onQuickSaveNote = { _, _, _ -> },
                        onStartExercise = {},
                        onOpenExamHub = {},
                        onShowMessage = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("book_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("book_detail_download_btn").assertDoesNotExist()
        composeRule.onNodeWithTag("book_detail_read_pdf_btn").performClick()
        assertFalse(openedPdfReader)
        assertTrue(openedSubscriptionGate)

        // Verify all required detail sections exist when scrolled into view
        composeRule.onNodeWithTag("book_detail_description_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("book_detail_info_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("book_detail_chapters_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("অধ্যায় ১: ভৌত রাশি এবং পরিমাপ").assertExists()
        composeRule.onNodeWithTag("book_detail_related_notes_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("book_detail_exercises_section").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bookDetailScreen_showsDownloadOnlyWhenSubscribedAndAdminAllowsDownloads() {
        var openedPdfPage: Int? = null
        val book = Textbook(
            id = "c5_bn",
            titleBn = "আমার বাংলা বই",
            subjectNameBn = "বাংলা",
            classNumber = 5,
            pdfUrl = "https://example.org/c5_bn.pdf",
            isPremium = false,
            allowDownload = true
        )

        composeRule.setContent {
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.EN)) {
                    BookDetailScreen(
                        book = book,
                        isBookmarked = true,
                        readingProgress = null,
                        accessDecision = PremiumAccessDecision.ALLOWED_FREE,
                        appSettings = AppSettings(downloadsEnabled = true, allowTextbookDownload = true),
                        relatedGuides = emptyList(),
                        relatedPersonalNotes = emptyList(),
                        availableExercises = emptyList(),
                        onBack = {},
                        onToggleBookmark = {},
                        onOpenPdfReader = { openedPdfPage = it },
                        onOpenSubscriptionGate = {},
                        onOpenRelatedGuide = {},
                        onOpenNotebook = {},
                        onQuickSaveNote = { _, _, _ -> },
                        onStartExercise = {},
                        onOpenExamHub = {},
                        onShowMessage = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("book_detail_download_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("book_detail_read_pdf_btn").performClick()
        assertEquals(0, openedPdfPage)
    }

    @Test
    fun guideDetailScreen_rendersStudyNotesMetadataRelatedBooksAndExercises_withoutCrashing() {
        var bookmarkToggled = false
        val sampleGuide = GuideNote(
            id = "g_sci_9",
            titleBn = "বল ও গতি সৃজনশীল গাইড",
            subjectNameBn = "পদার্থবিজ্ঞান",
            classNumber = 9,
            stream = "SCIENCE",
            chapter = "অধ্যায় ৩: বল",
            guideType = "CREATIVE_QUESTION",
            author = "স্মার্ট স্কুল সম্পাদনা পর্ষদ",
            publisher = "Smart School Library BD",
            edition = "২০২৬ সংস্করণ",
            summaryContent = "নিউটনের গতিসূত্র ও ভরবেগের সংরক্ষণশীলতার বিস্তারিত ব্যাখ্যা এবং সৃজনশীল প্রশ্নোত্তর।",
            pdfUrl = "https://example.org/guide_force.pdf",
            isPremium = false,
            allowDownload = true
        )

        composeRule.setContent {
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.BN)) {
                    com.example.library.GuideDetailScreen(
                        guide = sampleGuide,
                        isBookmarked = false,
                        accessDecision = PremiumAccessDecision.ALLOWED_FREE,
                        appSettings = AppSettings(downloadsEnabled = true, allowGuideDownload = true),
                        relatedBooks = listOf(
                            Textbook(id = "b_phy_9", titleBn = "পদার্থবিজ্ঞান", subjectNameBn = "পদার্থবিজ্ঞান", classNumber = 9, stream = "SCIENCE")
                        ),
                        relatedPersonalNotes = emptyList(),
                        availableExercises = listOf(
                            ExamTest(id = "ex_phy_9", titleBn = "বল অধ্যায় কুইজ", classNumber = 9, questionCount = 10, durationMinutes = 10, totalMarks = 10)
                        ),
                        onBack = {},
                        onToggleBookmark = { bookmarkToggled = true },
                        onOpenPdfAttachment = {},
                        onOpenSubscriptionGate = {},
                        onOpenRelatedBook = {},
                        onOpenNotebook = {},
                        onQuickSaveNote = { _, _, _ -> },
                        onStartExercise = {},
                        onOpenExamHub = {},
                        onShowMessage = {},
                        onRecordRead = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("guide_detail_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_detail_read_pdf_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("guide_detail_bookmark_btn").performClick()
        assertTrue(bookmarkToggled)
    }

    @Test
    fun libraryHubAndPdfReaderAndAdminScreens_renderAndInteractWithoutCrashing() {
        var activeView = "HUB"
        val booksWithBlankAndDuplicateIds = listOf(
            Textbook(id = "", titleBn = "বাংলা ব্যাকরণ", subjectNameBn = "বাংলা", classNumber = 9, stream = "ALL"),
            Textbook(id = "", titleBn = "ইংরেজি গ্রামার", subjectNameBn = "English", classNumber = 9, stream = "ALL")
        )
        val guidesSample = listOf(
            GuideNote(id = "g_hub_1", titleBn = "বীজগণিত নোট", subjectNameBn = "গণিত", classNumber = 9, stream = "SCIENCE", chapter = "অধ্যায় ১")
        )

        composeRule.setContent {
            val viewState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(activeView) }
            activeView = viewState.value
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.BN)) {
                    when (viewState.value) {
                        "HUB" -> com.example.library.LibraryHubScreen(
                            booksState = UiState.Success(booksWithBlankAndDuplicateIds),
                            guidesState = UiState.Success(guidesSample),
                            subjects = emptyList(),
                            bookmarks = emptyList(),
                            onOpenBook = { viewState.value = "PDF" },
                            onOpenGuide = {},
                            onToggleBookBookmark = {},
                            onToggleGuideBookmark = {}
                        )
                        "PDF" -> com.example.reader.PdfReaderScreen(
                            contentId = "test_pdf_1",
                            contentType = "BOOK",
                            titleBn = "বাংলা ব্যাকরণ",
                            subjectNameBn = "বাংলা",
                            classNumber = 9,
                            stream = "ALL",
                            coverUrl = "",
                            pdfUrl = "",
                            isPremium = false,
                            itemAllowsDownload = false,
                            tableOfContents = listOf("অধ্যায় ১|1"),
                            initialPageOneBased = 1,
                            savedLastPageOneBased = 1,
                            bookmarks = emptyList(),
                            appSettings = AppSettings(),
                            onBack = { viewState.value = "ADMIN" },
                            onSaveProgress = { _, _ -> },
                            onToggleWholeBookBookmark = {},
                            onTogglePageBookmark = {},
                            onShowMessage = {}
                        )
                        "ADMIN_BOOKS" -> com.example.admin.AdminBooksModule(
                            books = booksWithBlankAndDuplicateIds,
                            subjects = listOf(Subject(id = "s1", nameBn = "বাংলা", classNumber = 9)),
                            onSave = {},
                            onDelete = {}
                        )
                        else -> com.example.admin.AdminGuidesModule(
                            guides = guidesSample,
                            subjects = listOf(Subject(id = "s2", nameBn = "গণিত", classNumber = 9)),
                            onSave = {},
                            onDelete = {}
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        // 1. LibraryHubScreen renders even when books have blank IDs
        composeRule.onNodeWithTag("library_hub_screen").assertIsDisplayed()
        composeRule.onNodeWithText("বাংলা ব্যাকরণ").performClick()
        composeRule.waitForIdle()

        // 2. PdfReaderScreen renders empty/missing URL error state cleanly without crashing
        composeRule.onNodeWithTag("pdf_reader_screen").assertIsDisplayed()
    }

    @Test
    fun adminBooksAndGuidesModules_openDirectUploadScreenWithoutUrlFields() {
        lateinit var switchToGuides: () -> Unit
        composeRule.setContent {
            val showGuides = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            switchToGuides = { showGuides.value = true }
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.BN)) {
                    if (!showGuides.value) {
                        com.example.admin.AdminBooksModule(
                            books = listOf(Textbook(id = "ab1", titleBn = "গণিত", subjectNameBn = "গণিত", classNumber = 8)),
                            subjects = listOf(Subject(id = "s1", nameBn = "গণিত", classNumber = 8)),
                            onSave = {},
                            onDelete = {}
                        )
                    } else {
                        com.example.admin.AdminGuidesModule(
                            guides = listOf(GuideNote(id = "ag1", titleBn = "বীজগণিত গাইড", subjectNameBn = "গণিত", classNumber = 8, chapter = "অধ্যায় ১")),
                            subjects = listOf(Subject(id = "s1", nameBn = "গণিত", classNumber = 8)),
                            onSave = {},
                            onDelete = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("নতুন বই যোগ করুন").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_book_upload_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("upload_book_cover_button").assertIsDisplayed()
        composeRule.onNodeWithTag("upload_complete_pdf_button").assertIsDisplayed()
        // Verify all URL fields are strictly removed
        composeRule.onNodeWithText("PDF URL", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Cover Image URL", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("কভার ছবি URL", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("upload_screen_back_button").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle { switchToGuides() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("নতুন গাইড/নোট").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_book_upload_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("upload_book_cover_button").assertIsDisplayed()
        composeRule.onNodeWithTag("upload_complete_pdf_button").assertIsDisplayed()
        composeRule.onNodeWithTag("upload_screen_back_button").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun composeNavigationRoutes_encodeSpecialCharactersSpacesAndBengaliIds_withoutCrashing() {
        var decodedBookId: String? = null
        var decodedGuideId: String? = null
        var decodedPdfId: String? = null

        val trickyBookId = "বাংলা বই #১?edition=2026&v=2"
        val trickyGuideId = "গাইড নোট ১০০% প্রস্তুত?ch=3#top"
        val trickyPdfId = "পিডিএফ ডকুমেন্ট #৯?page=1"

        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = com.example.navigation.Routes.HOME
            ) {
                composableWithNavigationTest(
                    navController = navController,
                    trickyBookId = trickyBookId,
                    trickyGuideId = trickyGuideId,
                    trickyPdfId = trickyPdfId,
                    onBookDecoded = { decodedBookId = it },
                    onGuideDecoded = { decodedGuideId = it },
                    onPdfDecoded = { decodedPdfId = it }
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(trickyBookId, decodedBookId)
        assertEquals(trickyGuideId, decodedGuideId)
        assertEquals(trickyPdfId, decodedPdfId)
    }

    @Test
    fun welcomeScreen_rendersAllRequiredEducationalContentAndLanguageSwitch() {
        var selectedLanguage = AppLanguage.BN
        var started = false

        composeRule.setContent {
            val langState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(AppLanguage.BN) }
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(langState.value)) {
                    com.example.welcome.WelcomeScreen(
                        currentLanguage = langState.value,
                        onSelectLanguage = {
                            selectedLanguage = it
                            langState.value = it
                        },
                        onStartClick = { started = true }
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("welcome_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Smart School Library BD", substring = false).assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_headline").assertIsDisplayed()
        composeRule.onNodeWithText("আপনার ডিজিটাল শিক্ষাসঙ্গী", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("welcome_highlight_textbooks").assertExists()
        composeRule.onNodeWithTag("welcome_highlight_guides").assertExists()
        composeRule.onNodeWithTag("welcome_highlight_gk").assertExists()
        composeRule.onNodeWithTag("welcome_highlight_mcq").assertExists()
        composeRule.onNodeWithTag("welcome_highlight_pdf").assertExists()
        composeRule.onNodeWithTag("welcome_highlight_bookmarks").assertExists()
        composeRule.onNodeWithText("শিক্ষাই জাতির মেরুদণ্ড।", substring = true).assertExists()

        // Switch language to English and back
        composeRule.onNodeWithTag("welcome_lang_en").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(AppLanguage.EN, selectedLanguage)

        // Tap শুরু করুন CTA
        composeRule.onNodeWithTag("welcome_start_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        org.junit.Assert.assertTrue(started)
    }

    @Test
    fun localUploadValidator_andStoragePaths_enforceGenuinePdfAndSupportedCoverImages() {
        val validPdfBytes = "%PDF-1.7\n1 0 obj\n<<>>\nendobj".toByteArray(Charsets.US_ASCII)
        val fakeHtmlBytes = "<!DOCTYPE html><html></html>".toByteArray(Charsets.US_ASCII)

        org.junit.Assert.assertTrue(
            com.example.data.LocalUploadValidator.isValidPdfBinaryHeader(validPdfBytes, 1024L)
        )
        org.junit.Assert.assertFalse(
            com.example.data.LocalUploadValidator.isValidPdfBinaryHeader(fakeHtmlBytes, 1024L)
        )
        org.junit.Assert.assertFalse(
            com.example.data.LocalUploadValidator.isValidPdfBinaryHeader(validPdfBytes, 0L)
        )

        assertEquals(
            "image/jpeg" to "cover.jpg",
            com.example.data.LocalUploadValidator.resolveSupportedCoverFormat("image/jpeg", "amar-bangla.jpg")
        )
        assertEquals(
            "image/png" to "cover.png",
            com.example.data.LocalUploadValidator.resolveSupportedCoverFormat("image/png", "cover.png")
        )
        assertEquals(
            "image/webp" to "cover.webp",
            com.example.data.LocalUploadValidator.resolveSupportedCoverFormat("image/webp", "cover.webp")
        )
        org.junit.Assert.assertNull(
            com.example.data.LocalUploadValidator.resolveSupportedCoverFormat("application/octet-stream", "script.exe")
        )

        assertEquals(
            "books/class_5/book_abc123/original_book.pdf",
            com.example.data.BookStorageUploader.buildPdfStoragePath(5, "book_abc123")
        )
        assertEquals(
            "book_covers/class_5/book_abc123/cover.jpg",
            com.example.data.BookStorageUploader.buildCoverStoragePath(5, "book_abc123", "cover.jpg")
        )
    }

    @Test
    fun subscriptionAccessControl_enforcesStrictClassAndStreamIsolation_andAnnualExpiry() {
        val now = 1772928000000L // 2026-03-08
        val future = Timestamp(Date(now + 30L * 24 * 60 * 60 * 1000L))
        val past = Timestamp(Date(now - 30L * 24 * 60 * 60 * 1000L))

        val studentProfile = UserProfile(
            userId = "student_1",
            name = "Student Test",
            selectedClass = 5,
            isActive = true
        )

        val class5Sub = Subscription(id = "sub_5", userId = "student_1", classNumber = 5, stream = "ALL", status = "ACTIVE", expiryDate = future)
        val class9SciSub = Subscription(id = "sub_9_sci", userId = "student_1", classNumber = 9, stream = "SCIENCE", status = "ACTIVE", expiryDate = future)
        val class9HumSub = Subscription(id = "sub_9_hum", userId = "student_2", classNumber = 9, stream = "HUMANITIES", status = "ACTIVE", expiryDate = future)
        val class10SciSub = Subscription(id = "sub_10_sci", userId = "student_1", classNumber = 10, stream = "SCIENCE", status = "ACTIVE", expiryDate = future)
        val expiredSub = Subscription(id = "sub_exp", userId = "student_1", classNumber = 9, stream = "SCIENCE", status = "ACTIVE", expiryDate = past)

        // 1. Separate annual subscription for Class 5: allowed for class 5
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 5,
                contentStream = "ALL",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class5Sub),
                nowMillis = now
            )
        )

        // 2. Class 5 subscription DENIES access to Class 6 (Class mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 6,
                contentStream = "ALL",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class5Sub),
                nowMillis = now
            )
        )

        // 3. Class 9 Science allows Class 9 Science
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciSub),
                nowMillis = now
            )
        )

        // 4. Class 9 Science allows Class 9 common subject (stream "ALL")
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "ALL",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciSub),
                nowMillis = now
            )
        )

        // 5. Class 9 Science subscription DENIES Class 9 Humanities (Stream mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "HUMANITIES",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciSub),
                nowMillis = now
            )
        )

        // 6. Class 9 Science subscription DENIES Class 9 Business Studies (Stream mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "BUSINESS",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciSub),
                nowMillis = now
            )
        )

        // 7. Class 9 Humanities subscription DENIES Class 9 Science (Stream mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = "student_2",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9HumSub),
                nowMillis = now
            )
        )

        // 8. Class 9 Science subscription DENIES Class 10 Science (Grade mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 10,
                contentStream = "SCIENCE",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciSub),
                nowMillis = now
            )
        )

        // 9. Class 10 Science subscription DENIES Class 9 Science (Grade mismatch)
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class10SciSub),
                nowMillis = now
            )
        )

        // 10. Expired subscription returns DENIED_SUBSCRIPTION_EXPIRED
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_EXPIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 9,
                contentStream = "SCIENCE",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(expiredSub),
                nowMillis = now
            )
        )
        assertFalse(expiredSub.isCurrentlyActive(now))

        // 11. evaluateClassAndStreamAccess helper
        assertTrue(
            StudyRepository.evaluateClassAndStreamAccess(
                classNumber = 9,
                stream = "SCIENCE",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciSub),
                nowMillis = now
            )
        )
        assertFalse(
            StudyRepository.evaluateClassAndStreamAccess(
                classNumber = 9,
                stream = "HUMANITIES",
                currentUserUid = "student_1",
                userProfile = studentProfile,
                userSubscriptions = listOf(class9SciSub),
                nowMillis = now
            )
        )

        // 12. Trusted Super Admin always allowed
        assertEquals(
            PremiumAccessDecision.ALLOWED_ADMIN,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 10,
                contentStream = "BUSINESS",
                currentUserUid = SecurityConstants.TRUSTED_SUPER_ADMIN_UID,
                userProfile = null,
                userSubscriptions = emptyList(),
                nowMillis = now
            )
        )

        // 13. Annual Expiry Calculation Rule in Bangladesh Time (Asia/Dhaka)
        val dhakaTz = com.example.data.AdminRepository.BANGLADESH_TIMEZONE
        // Subscriptions approved in 2026 expire on Dec 31, 2026, 11:59:59 PM Asia/Dhaka
        val cal2026 = java.util.Calendar.getInstance(dhakaTz).apply {
            set(2026, java.util.Calendar.MARCH, 15, 10, 0, 0)
        }
        val expDate2026 = com.example.data.AdminRepository.calculateAnnualExpiryDate(cal2026.timeInMillis)
        val expCal2026 = java.util.Calendar.getInstance(dhakaTz).apply { time = expDate2026 }
        assertEquals(2026, expCal2026.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.DECEMBER, expCal2026.get(java.util.Calendar.MONTH))
        assertEquals(31, expCal2026.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(23, expCal2026.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(59, expCal2026.get(java.util.Calendar.MINUTE))
        assertEquals(59, expCal2026.get(java.util.Calendar.SECOND))

        // Subscriptions approved in 2027 expire on Dec 31, 2027, 11:59:59 PM Asia/Dhaka
        val cal2027 = java.util.Calendar.getInstance(dhakaTz).apply {
            set(2027, java.util.Calendar.JANUARY, 5, 14, 30, 0)
        }
        val expDate2027 = com.example.data.AdminRepository.calculateAnnualExpiryDate(cal2027.timeInMillis)
        val expCal2027 = java.util.Calendar.getInstance(dhakaTz).apply { time = expDate2027 }
        assertEquals(2027, expCal2027.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.DECEMBER, expCal2027.get(java.util.Calendar.MONTH))
        assertEquals(31, expCal2027.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(23, expCal2027.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(59, expCal2027.get(java.util.Calendar.MINUTE))
        assertEquals(59, expCal2027.get(java.util.Calendar.SECOND))
    }

    @Test
    fun predefined10AnnualPackages_andSingleSubscriptionUnlocksBothBooksAndGuides() {
        val packages = DefaultCatalogSeed.defaultAnnualPackages
        assertEquals(10, packages.size)

        val c5 = packages.firstOrNull { it.id == "plan_annual_c5" }
        val c6 = packages.firstOrNull { it.id == "plan_annual_c6" }
        val c7 = packages.firstOrNull { it.id == "plan_annual_c7" }
        val c8 = packages.firstOrNull { it.id == "plan_annual_c8" }
        val c9Sci = packages.firstOrNull { it.id == "plan_annual_c9_science" }
        val c9Hum = packages.firstOrNull { it.id == "plan_annual_c9_humanities" }
        val c9Bus = packages.firstOrNull { it.id == "plan_annual_c9_business" }
        val c10Sci = packages.firstOrNull { it.id == "plan_annual_c10_science" }
        val c10Hum = packages.firstOrNull { it.id == "plan_annual_c10_humanities" }
        val c10Bus = packages.firstOrNull { it.id == "plan_annual_c10_business" }

        org.junit.Assert.assertNotNull(c5)
        org.junit.Assert.assertNotNull(c6)
        org.junit.Assert.assertNotNull(c7)
        org.junit.Assert.assertNotNull(c8)
        org.junit.Assert.assertNotNull(c9Sci)
        org.junit.Assert.assertNotNull(c9Hum)
        org.junit.Assert.assertNotNull(c9Bus)
        org.junit.Assert.assertNotNull(c10Sci)
        org.junit.Assert.assertNotNull(c10Hum)
        org.junit.Assert.assertNotNull(c10Bus)

        val now = 1772928000000L // 2026-03-08
        val future = Timestamp(Date(now + 30L * 24 * 60 * 60 * 1000L))
        val student = UserProfile(userId = "stu_c7", name = "Test C7", selectedClass = 7, isActive = true)
        val c7Sub = Subscription(id = "sub_7", userId = "stu_c7", classNumber = 7, stream = "ALL", status = "ACTIVE", expiryDate = future)

        // Single subscription for Class 7 unlocks BOTH textbook and guide (even when isContentPremium is false/true)
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = false,
                contentClass = 7,
                contentStream = "ALL",
                currentUserUid = "stu_c7",
                userProfile = student,
                userSubscriptions = listOf(c7Sub),
                nowMillis = now
            )
        )
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 7,
                contentStream = "ALL",
                currentUserUid = "stu_c7",
                userProfile = student,
                userSubscriptions = listOf(c7Sub),
                nowMillis = now
            )
        )

        // Without subscription, both textbook and guide for class 7 are DENIED
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = false,
                contentClass = 7,
                contentStream = "ALL",
                currentUserUid = "stu_c7",
                userProfile = student,
                userSubscriptions = emptyList(),
                nowMillis = now
            )
        )
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 7,
                contentStream = "ALL",
                currentUserUid = "stu_c7",
                userProfile = student,
                userSubscriptions = emptyList(),
                nowMillis = now
            )
        )
    }

    @Test
    fun superAdminDashboard_replacesTopChipStripWithTwoColumnModuleCards_andNavigatesBackToDashboard() {
        var exitedToApp = false

        composeRule.setContent {
            val activeModuleState = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(com.example.admin.AdminModule.DASHBOARD)
            }
            SmartSchoolTheme(darkTheme = false) {
                CompositionLocalProvider(LocalAppStrings provides stringsFor(AppLanguage.BN)) {
                    com.example.admin.SuperAdminPanelScaffold(
                        activeModule = activeModuleState.value,
                        onSelectModule = { activeModuleState.value = it },
                        pendingPaymentCount = 2,
                        unreadNotifCount = 1,
                        onExitToStudentApp = { exitedToApp = true },
                        onLogout = {}
                    ) { mod, onSelect ->
                        if (mod == com.example.admin.AdminModule.DASHBOARD) {
                            com.example.admin.AdminDashboardOverview(
                                users = listOf(
                                    UserProfile(userId = "u1", name = "আরিফ", role = "student", isActive = true)
                                ),
                                classes = DefaultCatalogSeed.defaultClasses,
                                educationLevels = DefaultCatalogSeed.defaultEducationLevels,
                                streams = DefaultCatalogSeed.defaultStreams,
                                subjects = emptyList(),
                                books = listOf(
                                    Textbook(id = "b1", titleBn = "গণিত", classNumber = 8)
                                ),
                                guides = listOf(
                                    GuideNote(id = "g1", titleBn = "বিজ্ঞান গাইড", classNumber = 8)
                                ),
                                paymentMethods = emptyList(),
                                subscriptions = emptyList(),
                                requests = emptyList(),
                                lang = AppLanguage.BN,
                                onBootstrapClasses = {},
                                onNavigateModule = onSelect
                            )
                        } else {
                            androidx.compose.material3.Text(
                                text = "Submodule: ${mod.name}",
                                modifier = Modifier.testTag("active_submodule_screen")
                            )
                        }
                    }
                }
            }
        }

        composeRule.waitForIdle()

        // 1. Old horizontal top chip strip must no longer exist on compact phone screens
        composeRule.onNodeWithTag("admin_chip_DASHBOARD").assertDoesNotExist()
        composeRule.onNodeWithTag("admin_chip_PAYMENT_METHODS").assertDoesNotExist()
        composeRule.onNodeWithTag("admin_chip_BOOKS").assertDoesNotExist()

        // 2. Existing KPI metric cards appear ABOVE the new 2-column module cards section
        composeRule.onNodeWithText("মোট শিক্ষার্থী").assertIsDisplayed()
        composeRule.onNodeWithText("মোট বই ও গাইড").assertIsDisplayed()
        val kpiBounds = composeRule.onNodeWithText("মোট শিক্ষার্থী").getUnclippedBoundsInRoot()
        val moduleSectionBounds = composeRule.onNodeWithTag("admin_dashboard_module_cards_section")
            .getUnclippedBoundsInRoot()
        assertTrue(
            "KPI metrics must remain above the Dashboard module cards",
            kpiBounds.bottom.value <= moduleSectionBounds.top.value
        )

        // 3. First row has 2 cards side-by-side (PAYMENT_METHODS left, PAYMENT_NOTIFICATIONS right)
        val pmBounds = composeRule.onNodeWithTag("admin_card_PAYMENT_METHODS").getUnclippedBoundsInRoot()
        val notifBounds = composeRule.onNodeWithTag("admin_card_PAYMENT_NOTIFICATIONS").getUnclippedBoundsInRoot()
        assertTrue(
            "Expected 2 cards side-by-side per row",
            notifBounds.left.value > pmBounds.right.value
        )
        assertTrue(
            "Cards in the same row must align vertically",
            abs(pmBounds.top.value - notifBounds.top.value) < 4f
        )

        // 4. Scroll to last card (QUICK_CONTENT) to ensure all 15 module cards are rendered
        composeRule.onNodeWithTag("admin_dashboard_overview")
            .performScrollToNode(hasTestTag("admin_card_QUICK_CONTENT"))
        composeRule.onNodeWithTag("admin_card_QUICK_CONTENT").assertIsDisplayed()

        // 5. Scroll to BOOKS card and open it
        composeRule.onNodeWithTag("admin_dashboard_overview")
            .performScrollToNode(hasTestTag("admin_card_BOOKS"))
        composeRule.onNodeWithTag("admin_card_BOOKS").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("active_submodule_screen").assertIsDisplayed()
        composeRule.onNodeWithText("Submodule: BOOKS").assertIsDisplayed()
        composeRule.onNodeWithTag("admin_subscreen_back_to_dashboard_bar").assertIsDisplayed()
        assertFalse(exitedToApp)

        // 6. Tapping header back arrow while inside a sub-module returns to Dashboard (not exiting app)
        composeRule.onNodeWithTag("admin_header_back_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_dashboard_overview").assertIsDisplayed()
        assertFalse(exitedToApp)

        // 7. Open PAYMENT_METHODS card and return via the sub-screen Back to Dashboard bar
        composeRule.onNodeWithTag("admin_dashboard_overview")
            .performScrollToNode(hasTestTag("admin_card_PAYMENT_METHODS"))
        composeRule.onNodeWithTag("admin_card_PAYMENT_METHODS").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Submodule: PAYMENT_METHODS").assertIsDisplayed()

        composeRule.onNodeWithTag("admin_subscreen_back_to_dashboard_bar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("admin_dashboard_overview").assertIsDisplayed()
        assertFalse(exitedToApp)

        // 8. Tapping header back arrow while on Dashboard exits to student app
        composeRule.onNodeWithTag("admin_header_back_btn").performClick()
        composeRule.waitForIdle()
        assertTrue(exitedToApp)
    }
}

private fun androidx.navigation.NavGraphBuilder.composableWithNavigationTest(
    navController: NavHostController,
    trickyBookId: String,
    trickyGuideId: String,
    trickyPdfId: String,
    onBookDecoded: (String) -> Unit,
    onGuideDecoded: (String) -> Unit,
    onPdfDecoded: (String) -> Unit
) {
    composable(com.example.navigation.Routes.HOME) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            navController.navigate(com.example.navigation.Routes.bookDetail(trickyBookId))
        }
    }
    composable(
        route = com.example.navigation.Routes.BOOK_DETAIL,
        arguments = listOf(navArgument("bookId") { type = NavType.StringType })
    ) { entry ->
        val id = entry.arguments?.getString("bookId").orEmpty()
        onBookDecoded(id)
        androidx.compose.runtime.LaunchedEffect(id) {
            navController.navigate(com.example.navigation.Routes.guideDetail(trickyGuideId))
        }
    }
    composable(
        route = com.example.navigation.Routes.GUIDE_DETAIL,
        arguments = listOf(navArgument("guideId") { type = NavType.StringType })
    ) { entry ->
        val id = entry.arguments?.getString("guideId").orEmpty()
        onGuideDecoded(id)
        androidx.compose.runtime.LaunchedEffect(id) {
            navController.navigate(com.example.navigation.Routes.pdfReader("BOOK", trickyPdfId, 3))
        }
    }
    composable(
        route = com.example.navigation.Routes.PDF_READER,
        arguments = listOf(
            navArgument("contentType") { type = NavType.StringType },
            navArgument("contentId") { type = NavType.StringType },
            navArgument("initialPage") { type = NavType.IntType; defaultValue = 0 }
        )
    ) { entry ->
        onPdfDecoded(entry.arguments?.getString("contentId").orEmpty())
    }
}
