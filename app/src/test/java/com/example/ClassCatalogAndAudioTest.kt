package com.example

import com.example.data.PremiumAccessDecision
import com.example.data.StudyRepository
import com.example.model.ContentClassGroupHelper
import com.example.model.DefaultCatalogSeed
import com.example.model.EducationLevel
import com.example.model.GuideNote
import com.example.model.SchoolClass
import com.example.model.Subscription
import com.example.model.SubscriptionPlan
import com.example.model.Textbook
import com.example.model.UserProfile
import com.example.model.sortedBySerialOrder
import com.example.reader.PdfPageTextProcessor
import com.example.reader.SpeechLanguage
import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassCatalogAndAudioTest {

    @Test
    fun schoolClasses_sortInAscendingSerialOrder_evenWithOutOfOrderSortOrder() {
        val shuffled = listOf(
            SchoolClass(id = "c12", classNumber = 12, nameBn = "দ্বাদশ শ্রেণি", nameEn = "Class 12", sortOrder = 1),
            SchoolClass(id = "c9", classNumber = 9, nameBn = "নবম শ্রেণি", nameEn = "Class 9", sortOrder = 20),
            SchoolClass(id = "c11", classNumber = 11, nameBn = "একাদশ শ্রেণি", nameEn = "Class 11", sortOrder = 2),
            SchoolClass(id = "c5", classNumber = 5, nameBn = "পঞ্চম শ্রেণি", nameEn = "Class 5", sortOrder = 99),
            SchoolClass(id = "c14", classNumber = 14, nameBn = "স্নাতক ২য় বর্ষ", nameEn = "Class 14", sortOrder = 0),
            SchoolClass(id = "c10", classNumber = 10, nameBn = "দশম শ্রেণি", nameEn = "Class 10", sortOrder = 5),
            SchoolClass(id = "c13", classNumber = 13, nameBn = "স্নাতক ১ম বর্ষ", nameEn = "Class 13", sortOrder = 3),
            SchoolClass(id = "c6", classNumber = 6, nameBn = "ষষ্ঠ শ্রেণি", nameEn = "Class 6", sortOrder = 8),
            SchoolClass(id = "c8", classNumber = 8, nameBn = "অষ্টম শ্রেণি", nameEn = "Class 8", sortOrder = 7),
            SchoolClass(id = "c7", classNumber = 7, nameBn = "সপ্তম শ্রেণি", nameEn = "Class 7", sortOrder = 6)
        )

        val ordered = shuffled.sortedBySerialOrder().map { it.classNumber }
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14), ordered)
    }

    @Test
    fun levelWideSubscription_unlocksClassesWithinEducationLevel_andExtendedFreeContentOpensCleanly() {
        val now = 1_800_000_000_000L
        val future = Timestamp(Date(now + 30L * 24 * 3600 * 1000))
        val student = UserProfile(userId = "stu_hsc_1", isActive = true, role = "student")

        val hscLevel = EducationLevel(
            id = "level_hsc",
            code = "HSC",
            nameBn = "এইচএসসি স্তর",
            nameEn = "HSC Level",
            minClassNumber = 11,
            maxClassNumber = 12,
            isActive = true
        )
        val hscPlan = SubscriptionPlan(
            id = "plan_hsc_12m",
            nameBn = "এইচএসসি পূর্ণাঙ্গ প্যাকেজ",
            classNumber = 0,
            stream = "ALL",
            durationMonths = 12,
            priceBdt = 450,
            educationLevelId = "level_hsc",
            isActive = true
        )
        val hscSub = Subscription(
            id = "sub_hsc",
            userId = "stu_hsc_1",
            planId = "plan_hsc_12m",
            planNameBn = "এইচএসসি পূর্ণাঙ্গ প্যাকেজ",
            classNumber = 0,
            stream = "ALL",
            durationMonths = 12,
            status = "ACTIVE",
            expiryDate = future
        )

        // Class 11 & Class 12 premium content unlocked by HSC level-wide subscription
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 11,
                contentStream = "ALL",
                currentUserUid = "stu_hsc_1",
                userProfile = student,
                userSubscriptions = listOf(hscSub),
                nowMillis = now,
                plans = listOf(hscPlan),
                educationLevels = listOf(hscLevel)
            )
        )
        assertEquals(
            PremiumAccessDecision.ALLOWED_ACTIVE_SUBSCRIPTION,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 12,
                contentStream = "ALL",
                currentUserUid = "stu_hsc_1",
                userProfile = student,
                userSubscriptions = listOf(hscSub),
                nowMillis = now,
                plans = listOf(hscPlan),
                educationLevels = listOf(hscLevel)
            )
        )

        // Class 13 is outside minClassNumber..maxClassNumber (11..12), so HSC level-wide subscription must NOT unlock Class 13 premium
        assertEquals(
            PremiumAccessDecision.DENIED_SUBSCRIPTION_REQUIRED,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = true,
                contentClass = 13,
                contentStream = "ALL",
                currentUserUid = "stu_hsc_1",
                userProfile = student,
                userSubscriptions = listOf(hscSub),
                nowMillis = now,
                plans = listOf(hscPlan),
                educationLevels = listOf(hscLevel)
            )
        )

        // Extended class free content (e.g., Class 11 free textbook) is ALLOWED_FREE without requiring a plan
        assertEquals(
            PremiumAccessDecision.ALLOWED_FREE,
            StudyRepository.checkPremiumEligibility(
                isContentPremium = false,
                contentClass = 11,
                contentStream = "ALL",
                currentUserUid = "stu_hsc_1",
                userProfile = student,
                userSubscriptions = emptyList(),
                nowMillis = now,
                plans = emptyList(),
                educationLevels = DefaultCatalogSeed.defaultEducationLevels
            )
        )
    }

    @Test
    fun pdfPageTextProcessor_detectsBijoyMojibake_withoutFalsePositivesOnEnglishOrUnicodeBengali() {
        val unicodeBengali = "আমাদের ছোট নদী চলে বাঁকে বাঁকে।\nবৈশাখ মাসে তার হাঁটু জল থাকে।"
        val cleanEnglish = "Photosynthesis converts light energy into chemical energy inside chloroplasts."
        val bijoyGarbage = "Avgvi †mvbvi evsjv Avwg †Zvgvq fv‡jvevwm। wKš‘ Zvn‡j GKwU cÖkœ Av‡Q।"

        assertFalse(PdfPageTextProcessor.looksLikeLegacyBijoyMojibake(unicodeBengali))
        assertFalse(PdfPageTextProcessor.looksLikeLegacyBijoyMojibake(cleanEnglish))
        assertTrue(PdfPageTextProcessor.looksLikeLegacyBijoyMojibake(bijoyGarbage))
    }

    @Test
    fun pdfPageTextProcessor_normalizesAndChunksMixedBengaliAndEnglishAccurately() {
        val rawPage = """
            14
            পৃষ্ঠা-১৪
            সালোকসংশ্লেষণ প্রক্রিয়ায় উদ্ভিদ খাদ্য তৈরি করে।
            Photosynthesis requires sunlight, water, and carbon di-
            oxide.
            ২৪
        """.trimIndent()

        val normalized = PdfPageTextProcessor.normalizeText(rawPage)
        assertFalse("Isolated numeric page lines must be filtered out", normalized.lines().any { it.trim() == "14" || it.trim() == "২৪" })
        assertTrue("Hyphenated line wrap should be rejoined", normalized.contains("dioxide"))

        val chunks = PdfPageTextProcessor.splitIntoLanguageChunks(normalized, maxChunkChars = 200)
        assertTrue("Expected at least 2 language chunks", chunks.size >= 2)
        assertEquals(SpeechLanguage.BENGALI, chunks.first { it.text.contains("সালোকসংশ্লেষণ") }.language)
        assertEquals(SpeechLanguage.ENGLISH, chunks.first { it.text.contains("Photosynthesis") }.language)
    }

    @Test
    fun pdfPageTextProcessor_splitsLongParagraphUnderMaxChunkChars() {
        val longEnglish = (1..25).joinToString(" ") { "ScienceTopicNumber$it" } + "."
        val chunks = PdfPageTextProcessor.splitIntoLanguageChunks(longEnglish, maxChunkChars = 120)
        assertTrue(chunks.size >= 3)
        assertTrue(chunks.all { it.language == SpeechLanguage.ENGLISH && it.text.length <= 120 })
    }

    @Test
    fun pdfPageTextProcessor_separatesInlineScriptBoundariesAndShortAcronymsCleanly() {
        val inlineMixed = "সালোকসংশ্লেষণ(Photosynthesis) এবং PDF ও AI প্রযুক্তি CPU-এর মাধ্যমে কাজ করে।"
        val chunks = PdfPageTextProcessor.splitIntoLanguageChunks(inlineMixed, maxChunkChars = 300)

        val bnChunks = chunks.filter { it.language == SpeechLanguage.BENGALI }
        val enChunks = chunks.filter { it.language == SpeechLanguage.ENGLISH }

        assertTrue("Expected Bengali segments", bnChunks.isNotEmpty())
        assertTrue("Expected English segments for Photosynthesis/PDF/AI/CPU", enChunks.isNotEmpty())
        assertTrue(
            "Photosynthesis must be assigned to ENGLISH voice",
            enChunks.any { it.text.contains("Photosynthesis") }
        )
        assertTrue(
            "Short acronyms PDF/AI/CPU must be assigned to ENGLISH voice",
            enChunks.any { it.text.contains("PDF") } &&
                enChunks.any { it.text.contains("AI") } &&
                enChunks.any { it.text.contains("CPU") }
        )
        assertFalse(
            "Bengali chunk must never absorb Photosynthesis",
            bnChunks.any { it.text.contains("Photosynthesis") }
        )
    }

    @Test
    fun pdfPageTextProcessor_pureBengaliAndPureEnglish_neverMisclassify() {
        val pureBn = "• বাংলাদেশের ইতিহাস ও ঐতিহ্য অত্যন্ত সমৃদ্ধ।"
        val bnChunks = PdfPageTextProcessor.splitIntoLanguageChunks(pureBn)
        assertEquals(1, bnChunks.size)
        assertEquals(SpeechLanguage.BENGALI, bnChunks.first().language)
        assertFalse("Decorative bullets should be stripped", bnChunks.first().text.contains("•"))

        val pureEn = "Newton's second law states that force equals mass times acceleration."
        val enChunks = PdfPageTextProcessor.splitIntoLanguageChunks(pureEn)
        assertEquals(1, enChunks.size)
        assertEquals(SpeechLanguage.ENGLISH, enChunks.first().language)
    }

    @Test
    fun contentClassGroupHelper_groupsLinkedBooksAndGuides_andDeduplicatesAllClassesView() {
        val singleBook = Textbook(id = "book_single123", titleBn = "আমার বাংলা বই", classNumber = 5)
        val groupBookC9 = Textbook(id = "book_grp_abc123_c9", titleBn = "বাংলা ব্যাকরণ", classNumber = 9)
        val groupBookC10 = Textbook(id = "book_grp_abc123_c10", titleBn = "বাংলা ব্যাকরণ", classNumber = 10)
        val legacyBaseBook = Textbook(id = "book_legacy99", titleBn = "ইংরেজি গ্রামার", classNumber = 7)
        val legacyLinkedBook = Textbook(id = "book_link_book_legacy99_c8", titleBn = "ইংরেজি গ্রামার", classNumber = 8)

        val adminGrouped = ContentClassGroupHelper.groupBooksForAdmin(
            listOf(groupBookC10, singleBook, legacyLinkedBook, groupBookC9, legacyBaseBook)
        )
        assertEquals(3, adminGrouped.size)

        val grpEntry = adminGrouped.first { it.groupKey == "book_grp_abc123" }
        assertEquals(listOf(9, 10), grpEntry.classNumbers)
        assertEquals("9, 10", grpEntry.classSummaryLabel)

        val legacyEntry = adminGrouped.first { it.groupKey == "book_legacy99" }
        assertEquals(listOf(7, 8), legacyEntry.classNumbers)

        val allClassesDeduped = ContentClassGroupHelper.deduplicateBooksForAllClassesView(
            listOf(singleBook, groupBookC9, groupBookC10, legacyBaseBook, legacyLinkedBook)
        )
        assertEquals(3, allClassesDeduped.size)
        assertEquals(listOf("book_single123", "book_grp_abc123_c9", "book_legacy99"), allClassesDeduped.map { it.id })

        val guide1 = GuideNote(id = "guide_grp_xyz789_c6", titleBn = "গণিত গাইড", classNumber = 6)
        val guide2 = GuideNote(id = "guide_grp_xyz789_c7", titleBn = "গণিত গাইড", classNumber = 7)
        val adminGuides = ContentClassGroupHelper.groupGuidesForAdmin(listOf(guide2, guide1))
        assertEquals(1, adminGuides.size)
        assertEquals(listOf(6, 7), adminGuides.first().classNumbers)
        assertEquals(1, ContentClassGroupHelper.deduplicateGuidesForAllClassesView(listOf(guide1, guide2)).size)
    }
}
