package com.example.model

import com.example.firebase.FirestoreIdentifiable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "student",
    val selectedClass: Int = 5,
    val selectedStream: String = "NONE",
    val photoUrl: String? = null,
    val isActive: Boolean = true,
    val accountDeletionRequested: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<UserProfile> {
    override fun withDocumentId(docId: String): UserProfile = copy(
        id = docId,
        userId = userId.ifBlank { docId }
    )

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "name" to name,
        "email" to email,
        "role" to role,
        "selectedClass" to selectedClass,
        "selectedStream" to selectedStream,
        "photoUrl" to photoUrl,
        "isActive" to isActive,
        "accountDeletionRequested" to accountDeletionRequested,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }
}

@IgnoreExtraProperties
data class EducationLevel(
    val id: String = "",
    val code: String = "",
    val nameBn: String = "",
    val nameEn: String = "",
    val subtitleBn: String = "",
    val subtitleEn: String = "",
    val descriptionBn: String = "",
    val minClassNumber: Int = 0,
    val maxClassNumber: Int = 0,
    val sortOrder: Int = 1,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<EducationLevel> {
    override fun withDocumentId(docId: String): EducationLevel = copy(id = docId)

    val displayBannerBn: String
        get() = if (subtitleBn.isNotBlank()) "$nameBn — $subtitleBn" else nameBn.ifBlank { nameEn }

    val displayBannerEn: String
        get() = if (subtitleEn.isNotBlank()) "$nameEn — $subtitleEn" else nameEn.ifBlank { nameBn }

    fun toWriteMap(isCreate: Boolean): Map<String, Any> = mapOf(
        "code" to code.trim().uppercase().ifBlank { nameEn.trim().uppercase().replace(Regex("[^A-Z0-9_]+"), "_") }.take(40),
        "nameBn" to nameBn.trim().take(100),
        "nameEn" to nameEn.trim().ifBlank { nameBn.trim() }.take(100),
        "subtitleBn" to subtitleBn.trim().take(140),
        "subtitleEn" to subtitleEn.trim().take(140),
        "descriptionBn" to descriptionBn.trim().take(500),
        "minClassNumber" to minClassNumber.coerceIn(0, 30),
        "maxClassNumber" to maxClassNumber.coerceIn(0, 30),
        "sortOrder" to sortOrder.coerceIn(0, 1000),
        "isActive" to isActive,
        "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class SchoolClass(
    val id: String = "",
    val classNumber: Int = 5,
    val nameBn: String = "",
    val nameEn: String = "",
    val hasStreams: Boolean = false,
    val isActive: Boolean = true,
    val sortOrder: Int = 1,
    val educationLevelId: String = "",
    val educationLevelNameBn: String = "",
    val educationLevelNameEn: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<SchoolClass> {
    override fun withDocumentId(docId: String): SchoolClass = copy(id = docId)

    val displayNameBn: String
        get() = nameBn.trim().ifBlank {
            when (classNumber) {
                1 -> "প্রথম শ্রেণি"
                2 -> "দ্বিতীয় শ্রেণি"
                3 -> "তৃতীয় শ্রেণি"
                4 -> "চতুর্থ শ্রেণি"
                5 -> "পঞ্চম শ্রেণি"
                6 -> "ষষ্ঠ শ্রেণি"
                7 -> "সপ্তম শ্রেণি"
                8 -> "অষ্টম শ্রেণি"
                9 -> "নবম শ্রেণি"
                10 -> "দশম শ্রেণি"
                11 -> "একাদশ শ্রেণি"
                12 -> "দ্বাদশ শ্রেণি"
                else -> "শ্রেণি $classNumber"
            }
        }

    val displayNameEn: String
        get() = nameEn.trim().ifBlank { "Class $classNumber" }

    fun toWriteMap(isCreate: Boolean): Map<String, Any> = mapOf(
        "classNumber" to classNumber.coerceIn(1, 30),
        "nameBn" to displayNameBn,
        "nameEn" to displayNameEn,
        "hasStreams" to hasStreams,
        "isActive" to isActive,
        "sortOrder" to sortOrder.coerceIn(0, 1000),
        "educationLevelId" to educationLevelId.trim().take(128),
        "educationLevelNameBn" to educationLevelNameBn.trim().take(100),
        "educationLevelNameEn" to educationLevelNameEn.trim().take(100),
        "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

fun List<SchoolClass>.sortedBySerialOrder(): List<SchoolClass> = sortedWith(
    compareBy<SchoolClass> { it.classNumber }
        .thenBy { it.sortOrder }
        .thenBy { it.displayNameEn }
)

@IgnoreExtraProperties
data class AcademicStream(
    val id: String = "",
    val code: String = "SCIENCE",
    val nameBn: String = "",
    val nameEn: String = "",
    val isActive: Boolean = true,
    val sortOrder: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<AcademicStream> {
    override fun withDocumentId(docId: String): AcademicStream = copy(id = docId)

    val displayNameBn: String
        get() = nameBn.trim().ifBlank {
            when (code.uppercase()) {
                "SCIENCE" -> "বিজ্ঞান বিভাগ"
                "HUMANITIES" -> "মানবিক বিভাগ"
                "BUSINESS" -> "ব্যবসায় শিক্ষা বিভাগ"
                else -> code
            }
        }

    val displayNameEn: String
        get() = nameEn.trim().ifBlank {
            when (code.uppercase()) {
                "SCIENCE" -> "Science"
                "HUMANITIES" -> "Humanities"
                "BUSINESS" -> "Business Studies"
                else -> code
            }
        }

    fun toWriteMap(isCreate: Boolean): Map<String, Any> = mapOf(
        "code" to code,
        "nameBn" to displayNameBn,
        "nameEn" to displayNameEn,
        "isActive" to isActive,
        "sortOrder" to sortOrder,
        "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class Subject(
    val id: String = "",
    val nameBn: String = "",
    val nameEn: String = "",
    val classNumber: Int = 5,
    val stream: String = "ALL",
    val isActive: Boolean = true,
    val sortOrder: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<Subject> {
    override fun withDocumentId(docId: String): Subject = copy(id = docId)

    fun toWriteMap(isCreate: Boolean): Map<String, Any> = mapOf(
        "nameBn" to nameBn,
        "nameEn" to nameEn,
        "classNumber" to classNumber,
        "stream" to stream,
        "isActive" to isActive,
        "sortOrder" to sortOrder,
        "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class Textbook(
    val id: String = "",
    val titleBn: String = "",
    val titleEn: String = "",
    val subjectId: String = "",
    val subjectNameBn: String = "",
    val subjectNameEn: String = "",
    val classNumber: Int = 5,
    val stream: String = "ALL",
    val academicYear: String = "2026",
    val edition: String = "NCTB",
    val author: String = "",
    val publisher: String = "",
    val shortDescription: String = "",
    val description: String = "",
    val language: String = "BN",
    val pageCount: Int = 100,
    val coverUrl: String = "",
    val pdfUrl: String = "",
    val coverStoragePath: String = "",
    val pdfStoragePath: String = "",
    val coverFileName: String = "",
    val coverFileSizeBytes: Long = 0L,
    val pdfFileName: String = "",
    val pdfFileSizeBytes: Long = 0L,
    val isPremium: Boolean = false,
    val isPublished: Boolean = true,
    val allowDownload: Boolean = false,
    val isPopular: Boolean = false,
    val tableOfContents: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<Textbook> {
    override fun withDocumentId(docId: String): Textbook = copy(id = docId)

    val effectiveCoverRef: String
        get() = coverStoragePath.ifBlank { coverUrl }

    val effectivePdfRef: String
        get() = pdfStoragePath.ifBlank { pdfUrl }

    fun toWriteMap(isCreate: Boolean): Map<String, Any> {
        val resolvedCover = coverStoragePath.ifBlank { coverUrl }.take(1000)
        val resolvedPdf = pdfStoragePath.ifBlank { pdfUrl }.take(1000)
        return mapOf(
            "titleBn" to titleBn.take(200),
            "titleEn" to titleEn.take(200),
            "subjectId" to subjectId.take(128),
            "subjectNameBn" to subjectNameBn.take(120),
            "subjectNameEn" to subjectNameEn.take(120),
            "classNumber" to classNumber,
            "stream" to stream,
            "academicYear" to academicYear.take(20),
            "edition" to edition.take(80),
            "author" to author.take(200),
            "publisher" to publisher.take(200),
            "shortDescription" to shortDescription.take(600),
            "description" to description.take(15000),
            "language" to language,
            "pageCount" to pageCount.coerceIn(1, 3000),
            "coverUrl" to resolvedCover,
            "pdfUrl" to resolvedPdf,
            "coverStoragePath" to coverStoragePath.take(500),
            "pdfStoragePath" to pdfStoragePath.take(500),
            "coverFileName" to coverFileName.take(255),
            "coverFileSizeBytes" to coverFileSizeBytes.coerceIn(0L, 20_000_000L),
            "pdfFileName" to pdfFileName.take(255),
            "pdfFileSizeBytes" to pdfFileSizeBytes.coerceIn(0L, 300_000_000L),
            "isPremium" to isPremium,
            "isPublished" to isPublished,
            "allowDownload" to allowDownload,
            "isPopular" to isPopular,
            "tableOfContents" to tableOfContents.take(60).map { it.take(200) },
            "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@IgnoreExtraProperties
data class GuideNote(
    val id: String = "",
    val titleBn: String = "",
    val titleEn: String = "",
    val classNumber: Int = 5,
    val stream: String = "ALL",
    val subjectId: String = "",
    val subjectNameBn: String = "",
    val subjectNameEn: String = "",
    val chapter: String = "",
    val guideType: String = "CHAPTER_NOTE",
    val academicYear: String = "2026",
    val language: String = "BN",
    val author: String = "",
    val publisher: String = "",
    val edition: String = "",
    val shortDescription: String = "",
    val summaryContent: String = "",
    val tableOfContents: List<String> = emptyList(),
    val pdfUrl: String = "",
    val coverUrl: String = "",
    val coverStoragePath: String = "",
    val pdfStoragePath: String = "",
    val coverFileName: String = "",
    val coverFileSizeBytes: Long = 0L,
    val pdfFileName: String = "",
    val pdfFileSizeBytes: Long = 0L,
    val pageCount: Int = 0,
    val isPremium: Boolean = false,
    val isPublished: Boolean = true,
    val allowDownload: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<GuideNote> {
    override fun withDocumentId(docId: String): GuideNote = copy(id = docId)

    val effectiveCoverRef: String
        get() = coverStoragePath.ifBlank { coverUrl }

    val effectivePdfRef: String
        get() = pdfStoragePath.ifBlank { pdfUrl }

    fun toWriteMap(isCreate: Boolean): Map<String, Any> {
        val resolvedCover = coverStoragePath.ifBlank { coverUrl }.take(1000)
        val resolvedPdf = pdfStoragePath.ifBlank { pdfUrl }.take(1000)
        return mapOf(
            "titleBn" to titleBn.take(200),
            "titleEn" to titleEn.take(200),
            "classNumber" to classNumber,
            "stream" to stream,
            "subjectId" to subjectId.take(128),
            "subjectNameBn" to subjectNameBn.take(120),
            "subjectNameEn" to subjectNameEn.take(120),
            "chapter" to chapter.take(150),
            "guideType" to guideType,
            "academicYear" to academicYear.take(20),
            "language" to language,
            "author" to author.take(200),
            "publisher" to publisher.take(200),
            "edition" to edition.take(80),
            "shortDescription" to shortDescription.take(600),
            "summaryContent" to summaryContent.take(20000),
            "tableOfContents" to tableOfContents.take(60).map { it.take(200) },
            "pdfUrl" to resolvedPdf,
            "coverUrl" to resolvedCover,
            "coverStoragePath" to coverStoragePath.take(500),
            "pdfStoragePath" to pdfStoragePath.take(500),
            "coverFileName" to coverFileName.take(255),
            "coverFileSizeBytes" to coverFileSizeBytes.coerceIn(0L, 20_000_000L),
            "pdfFileName" to pdfFileName.take(255),
            "pdfFileSizeBytes" to pdfFileSizeBytes.coerceIn(0L, 300_000_000L),
            "pageCount" to pageCount.coerceIn(0, 3000),
            "isPremium" to isPremium,
            "isPublished" to isPublished,
            "allowDownload" to allowDownload,
            "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@IgnoreExtraProperties
data class GeneralKnowledgeItem(
    val id: String = "",
    val category: String = "BANGLADESH",
    val questionBn: String = "",
    val questionEn: String = "",
    val answerBn: String = "",
    val answerEn: String = "",
    val explanationBn: String = "",
    val isPublished: Boolean = true,
    val isDemo: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<GeneralKnowledgeItem> {
    override fun withDocumentId(docId: String): GeneralKnowledgeItem = copy(id = docId)

    fun toWriteMap(isCreate: Boolean): Map<String, Any> = mapOf(
        "category" to category,
        "questionBn" to questionBn,
        "questionEn" to questionEn,
        "answerBn" to answerBn,
        "answerEn" to answerEn,
        "explanationBn" to explanationBn,
        "isPublished" to isPublished,
        "isDemo" to isDemo,
        "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class ExamQuestionItem(
    val questionText: String = "",
    val optionA: String = "",
    val optionB: String = "",
    val optionC: String = "",
    val optionD: String = ""
) {
    fun toMap(): Map<String, String> = mapOf(
        "questionText" to questionText,
        "optionA" to optionA,
        "optionB" to optionB,
        "optionC" to optionC,
        "optionD" to optionD
    )
}

@IgnoreExtraProperties
data class ExamTest(
    val id: String = "",
    val titleBn: String = "",
    val titleEn: String = "",
    val examType: String = "MODEL",
    val classNumber: Int = 5,
    val stream: String = "ALL",
    val subjectNameBn: String = "",
    val chapter: String = "",
    val durationMinutes: Int = 20,
    val totalMarks: Int = 20,
    val questionCount: Int = 1,
    val questions: List<Map<String, Any?>> = emptyList(),
    val isPublished: Boolean = true,
    val isDemo: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<ExamTest> {
    override fun withDocumentId(docId: String): ExamTest = copy(id = docId)

    fun parsedQuestions(): List<ExamQuestionItem> = questions.map { map ->
        ExamQuestionItem(
            questionText = map["questionText"]?.toString().orEmpty(),
            optionA = map["optionA"]?.toString().orEmpty(),
            optionB = map["optionB"]?.toString().orEmpty(),
            optionC = map["optionC"]?.toString().orEmpty(),
            optionD = map["optionD"]?.toString().orEmpty()
        )
    }

    fun toWriteMap(isCreate: Boolean): Map<String, Any> = mapOf(
        "titleBn" to titleBn,
        "titleEn" to titleEn,
        "examType" to examType,
        "classNumber" to classNumber,
        "stream" to stream,
        "subjectNameBn" to subjectNameBn,
        "chapter" to chapter,
        "durationMinutes" to durationMinutes,
        "totalMarks" to totalMarks,
        "questionCount" to questionCount,
        "questions" to questions.map { q -> q.mapValues { it.value?.toString().orEmpty() } },
        "isPublished" to isPublished,
        "isDemo" to isDemo,
        "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ExamAnswerKey(
    val id: String = "",
    val examId: String = "",
    val correctIndices: List<Long> = emptyList(),
    val explanations: List<String> = emptyList(),
    val isPublished: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<ExamAnswerKey> {
    override fun withDocumentId(docId: String): ExamAnswerKey = copy(id = docId)

    fun toWriteMap(): Map<String, Any> = mapOf(
        "examId" to examId,
        "correctIndices" to correctIndices,
        "explanations" to explanations,
        "isPublished" to isPublished,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ExamAttempt(
    val id: String = "",
    val userId: String = "",
    val examId: String = "",
    val examTitleBn: String = "",
    val selectedAnswers: List<Long> = emptyList(),
    val score: Int = 0,
    val totalMarks: Int = 1,
    val correctCount: Int = 0,
    val totalQuestions: Int = 1,
    val status: String = "SUBMITTED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<ExamAttempt> {
    override fun withDocumentId(docId: String): ExamAttempt = copy(id = docId)
}

object PaymentMethodConstants {
    const val TYPE_BKASH = "BKASH"
    const val TYPE_NAGAD = "NAGAD"
    const val TYPE_ROCKET = "ROCKET"
    const val TYPE_BANK_TRANSFER = "BANK_TRANSFER"

    const val TRX_SEND_MONEY = "Send Money"
    const val TRX_CASH_OUT = "Cash Out"
    const val TRX_PAYMENT = "Payment"
    const val TRX_BANK_TRANSFER = "Bank Transfer"

    val supportedMethodTypes = listOf(TYPE_BKASH, TYPE_NAGAD, TYPE_ROCKET, TYPE_BANK_TRANSFER)

    fun defaultMethodName(methodType: String): String = when (methodType) {
        TYPE_BKASH -> "bKash"
        TYPE_NAGAD -> "Nagad"
        TYPE_ROCKET -> "Rocket"
        TYPE_BANK_TRANSFER -> "Bank Transfer"
        else -> "bKash"
    }

    fun methodTitleBn(methodType: String): String = when (methodType) {
        TYPE_BKASH -> "বিকাশ (bKash)"
        TYPE_NAGAD -> "নগদ (Nagad)"
        TYPE_ROCKET -> "রকেট (Rocket)"
        TYPE_BANK_TRANSFER -> "ব্যাংক ট্রান্সফার (Bank Transfer)"
        else -> methodType
    }

    fun allowedTransactionTypes(methodType: String): List<String> = when (methodType) {
        TYPE_BKASH -> listOf(TRX_SEND_MONEY, TRX_PAYMENT)
        TYPE_NAGAD -> listOf(TRX_SEND_MONEY, TRX_CASH_OUT, TRX_PAYMENT)
        TYPE_ROCKET -> listOf(TRX_SEND_MONEY, TRX_CASH_OUT, TRX_PAYMENT)
        TYPE_BANK_TRANSFER -> listOf(TRX_BANK_TRANSFER)
        else -> listOf(TRX_SEND_MONEY, TRX_PAYMENT)
    }

    fun allowedAccountTypes(methodType: String): List<String> = when (methodType) {
        TYPE_BANK_TRANSFER -> listOf("Bank Account")
        else -> listOf("Personal", "Agent", "Merchant")
    }

    fun accountTypeBn(accountType: String): String = when (accountType) {
        "Personal" -> "পার্সোনাল (Personal)"
        "Agent" -> "এজেন্ট (Agent)"
        "Merchant" -> "মার্চেন্ট (Merchant)"
        "Bank Account" -> "ব্যাংক হিসাব (Bank Account)"
        else -> accountType
    }

    fun normalizeTransactionId(raw: String): String {
        val cleaned = raw.trim()
            .uppercase()
            .replace(Regex("[^A-Z0-9_\\-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        return "trx_${cleaned.take(96)}"
    }

    fun hasMinimumAlphanumericChars(raw: String, minCount: Int = 4): Boolean =
        raw.count { it.isLetterOrDigit() } >= minCount

    fun canonicalRefPlanPrice(classNumber: Int, durationMonths: Int = 12): Int {
        val predefined = findPredefinedAnnualPlan(classNumber, "ALL")
        if (predefined != null) return predefined.priceBdt
        return if (classNumber >= 9) 350 else 250
    }

    fun findPredefinedAnnualPlan(classNumber: Int, stream: String): SubscriptionPlan? {
        val normStream = if (classNumber >= 9) {
            val s = stream.uppercase().trim()
            if (s in listOf("SCIENCE", "HUMANITIES", "BUSINESS")) s else "SCIENCE"
        } else "ALL"
        return DefaultCatalogSeed.defaultAnnualPackages.firstOrNull {
            it.classNumber == classNumber && it.stream.equals(normStream, ignoreCase = true)
        }
    }

    fun isPredefinedAnnualPlanId(planId: String): Boolean =
        DefaultCatalogSeed.defaultAnnualPackages.any { it.id == planId }
}

@IgnoreExtraProperties
data class PaymentMethodConfig(
    val id: String = "",
    val methodType: String = PaymentMethodConstants.TYPE_BKASH,
    val methodName: String = "bKash",
    val accountNumber: String = "",
    val accountHolderName: String = "",
    val accountType: String = "Personal",
    val transactionType: String = PaymentMethodConstants.TRX_SEND_MONEY,
    val bankName: String = "",
    val branchName: String = "",
    val routingNumber: String = "",
    val paymentInstructions: String = "",
    val isActive: Boolean = true,
    val displayOrder: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<PaymentMethodConfig> {
    override fun withDocumentId(docId: String): PaymentMethodConfig = copy(id = docId)

    val isBankTransfer: Boolean
        get() = methodType == PaymentMethodConstants.TYPE_BANK_TRANSFER

    fun formattedInstruction(amountBdt: Int, isBn: Boolean): String {
        if (paymentInstructions.isNotBlank()) return paymentInstructions
        return if (isBankTransfer) {
            if (isBn) {
                "প্রদত্ত ব্যাংক হিসাবে ($bankName) নির্ধারিত ৳$amountBdt Bank Transfer সম্পন্ন করে নিচে রেফারেন্স নম্বর জমা দিন।"
            } else {
                "Transfer BDT $amountBdt to the bank account above ($bankName) and submit the reference ID below."
            }
        } else {
            if (isBn) {
                "উপরের ${methodName} (${accountType}) নম্বরে ৳$amountBdt $transactionType করুন।"
            } else {
                "Complete $transactionType of BDT $amountBdt to the $methodName ($accountType) number above."
            }
        }
    }

    fun toWriteMap(isCreate: Boolean): Map<String, Any> {
        val validTrxTypes = PaymentMethodConstants.allowedTransactionTypes(methodType)
        val safeTrxType = if (transactionType in validTrxTypes) transactionType else validTrxTypes.first()
        val validAccTypes = PaymentMethodConstants.allowedAccountTypes(methodType)
        val safeAccType = if (accountType in validAccTypes) accountType else validAccTypes.first()

        return mapOf(
            "methodType" to methodType,
            "methodName" to methodName.trim().ifBlank { PaymentMethodConstants.defaultMethodName(methodType) }.take(60),
            "accountNumber" to accountNumber.trim().take(60),
            "accountHolderName" to accountHolderName.trim().take(120),
            "accountType" to safeAccType,
            "transactionType" to safeTrxType,
            "bankName" to (if (isBankTransfer) bankName.trim().take(150) else ""),
            "branchName" to (if (isBankTransfer) branchName.trim().take(150) else ""),
            "routingNumber" to (if (isBankTransfer) routingNumber.trim().take(50) else ""),
            "paymentInstructions" to paymentInstructions.trim().take(1000),
            "isActive" to isActive,
            "displayOrder" to displayOrder.coerceIn(0, 1000),
            "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }
}

@IgnoreExtraProperties
data class UsedTransactionLock(
    val id: String = "",
    val normalizedTrxId: String = "",
    val transactionReference: String = "",
    val requestId: String = "",
    val userId: String = "",
    val status: String = "RESERVED",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<UsedTransactionLock> {
    override fun withDocumentId(docId: String): UsedTransactionLock = copy(id = docId)
}

@IgnoreExtraProperties
data class AppNotification(
    val id: String = "",
    val recipientId: String = "",
    val senderUid: String = "",
    val title: String = "",
    val message: String = "",
    val notificationType: String = "SYSTEM",
    val relatedPaymentRequestId: String = "",
    val studentName: String = "",
    val planName: String = "",
    val amountBdt: Int = 0,
    val paymentMethod: String = "",
    val transactionId: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<AppNotification> {
    override fun withDocumentId(docId: String): AppNotification = copy(id = docId)
}

@IgnoreExtraProperties
data class SubscriptionPlan(
    val id: String = "",
    val nameBn: String = "",
    val nameEn: String = "",
    val descriptionBn: String = "",
    val benefits: List<String> = emptyList(),
    val classNumber: Int = 5,
    val stream: String = "ALL",
    val durationMonths: Int = 6,
    val priceBdt: Int = 60,
    val isActive: Boolean = true,
    val educationLevelId: String = "",
    val educationLevelNameBn: String = "",
    val educationLevelNameEn: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<SubscriptionPlan> {
    override fun withDocumentId(docId: String): SubscriptionPlan = copy(id = docId)

    fun effectiveBenefits(isBn: Boolean): List<String> {
        if (benefits.isNotEmpty()) return benefits
        return if (isBn) {
            listOf(
                "নির্বাচিত শ্রেণির সকল মূল পাঠ্যবই সম্পূর্ণ আনলক",
                "অধ্যায়ভিত্তিক গাইড, সৃজনশীল ও নোট আনলক",
                "মডেল টেস্ট ও পরীক্ষার প্রস্তুতির পূর্ণ সুবিধা"
            )
        } else {
            listOf(
                "Full access to all Original Textbooks for the selected class",
                "Complete Guides, Creative Q&A, and Chapter Notes unlocked",
                "Full MCQ Model Test & Exam Preparation support"
            )
        }
    }

    fun toWriteMap(isCreate: Boolean): Map<String, Any> = mapOf(
        "nameBn" to nameBn.trim().take(120),
        "nameEn" to nameEn.trim().take(120),
        "descriptionBn" to descriptionBn.trim().take(500),
        "benefits" to benefits.map { it.trim().take(200) }.filter { it.isNotBlank() }.take(15),
        "classNumber" to classNumber.coerceIn(0, 30),
        "stream" to stream.ifBlank { "ALL" },
        "durationMonths" to durationMonths,
        "priceBdt" to priceBdt.coerceAtLeast(1),
        "isActive" to isActive,
        "educationLevelId" to educationLevelId.trim().take(128),
        "educationLevelNameBn" to educationLevelNameBn.trim().take(100),
        "educationLevelNameEn" to educationLevelNameEn.trim().take(100),
        "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class SubscriptionRequest(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userEmail: String = "",
    val planId: String = "",
    val planNameBn: String = "",
    val classNumber: Int = 5,
    val stream: String = "ALL",
    val durationMonths: Int = 6,
    val amountBdt: Int = 60,
    val paymentMethodId: String = "",
    val paymentMethod: String = "bKash",
    val transactionType: String = PaymentMethodConstants.TRX_SEND_MONEY,
    val receiverNumber: String = "",
    val accountHolderName: String = "",
    val transactionReference: String = "",
    val normalizedTrxId: String = "",
    val senderNumber: String = "",
    val screenshotUrl: String = "",
    val status: String = "PENDING",
    val adminNote: String = "",
    val rejectionReason: String = "",
    val verifiedBy: String = "",
    val verifiedAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<SubscriptionRequest> {
    override fun withDocumentId(docId: String): SubscriptionRequest = copy(id = docId)

    val effectiveRejectionReason: String
        get() = rejectionReason.ifBlank { adminNote }
}

@IgnoreExtraProperties
data class Subscription(
    val id: String = "",
    val userId: String = "",
    val planId: String = "",
    val planNameBn: String = "",
    val classNumber: Int = 5,
    val stream: String = "ALL",
    val durationMonths: Int = 6,
    val amountBdt: Int = 0,
    val paymentMethod: String = "",
    val transactionReference: String = "",
    val status: String = "ACTIVE",
    val startDate: Timestamp? = null,
    val expiryDate: Timestamp? = null,
    val approvedByUid: String = "",
    val requestId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<Subscription> {
    override fun withDocumentId(docId: String): Subscription = copy(id = docId)

    fun isCurrentlyActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (status != "ACTIVE") return false
        val expMillis = expiryDate?.toDate()?.time ?: return false
        return expMillis > nowMillis
    }

    fun remainingDays(nowMillis: Long = System.currentTimeMillis()): Long {
        val expMillis = expiryDate?.toDate()?.time ?: return 0L
        val diff = expMillis - nowMillis
        return if (diff <= 0L) 0L else (diff / (24L * 60L * 60L * 1000L))
    }
}

@IgnoreExtraProperties
data class Bookmark(
    val id: String = "",
    val userId: String = "",
    val bookmarkType: String = "BOOK",
    val targetId: String = "",
    val titleBn: String = "",
    val subtitleBn: String = "",
    val classNumber: Int = 5,
    val pageNumber: Int = 0,
    val coverUrl: String = "",
    val pdfUrl: String = "",
    val isPremium: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<Bookmark> {
    override fun withDocumentId(docId: String): Bookmark = copy(id = docId)
}

@IgnoreExtraProperties
data class ReadingProgress(
    val id: String = "",
    val userId: String = "",
    val contentId: String = "",
    val contentType: String = "BOOK",
    val titleBn: String = "",
    val subjectNameBn: String = "",
    val classNumber: Int = 5,
    val stream: String = "ALL",
    val coverUrl: String = "",
    val pdfUrl: String = "",
    val isPremium: Boolean = false,
    val lastPage: Int = 1,
    val totalPages: Int = 1,
    val progressPercent: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<ReadingProgress> {
    override fun withDocumentId(docId: String): ReadingProgress = copy(id = docId)
}

@IgnoreExtraProperties
data class UserNote(
    val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val subjectTag: String = "",
    val colorIndex: Int = 0,
    val isPinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<UserNote> {
    override fun withDocumentId(docId: String): UserNote = copy(id = docId)
}

@IgnoreExtraProperties
data class Notice(
    val id: String = "",
    val titleBn: String = "",
    val bodyBn: String = "",
    val category: String = "GENERAL",
    val isPublished: Boolean = true,
    val isImportant: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<Notice> {
    override fun withDocumentId(docId: String): Notice = copy(id = docId)

    fun toWriteMap(isCreate: Boolean): Map<String, Any> = mapOf(
        "titleBn" to titleBn,
        "bodyBn" to bodyBn,
        "category" to category,
        "isPublished" to isPublished,
        "isImportant" to isImportant,
        "createdAt" to (if (isCreate) FieldValue.serverTimestamp() else (createdAt ?: FieldValue.serverTimestamp())),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class AppSettings(
    val id: String = "global",
    val downloadsEnabled: Boolean = false,
    val allowTextbookDownload: Boolean = false,
    val allowGuideDownload: Boolean = false,
    val allowNoteDownload: Boolean = false,
    val defaultTheme: String = "SYSTEM",
    val defaultLanguage: String = "BN",
    val announcementBannerBn: String = "",
    val contactEmail: String = "support@smartschoollibrarybd.org",
    val contactPhone: String = "+880 1700-000000",
    val websiteUrl: String = "https://nctb.gov.bd",
    val privacyPolicyBn: String = "শিক্ষার্থীদের ব্যক্তিগত তথ্য, বুকমার্ক, নোটবুক এবং পড়ার অগ্রগতি সম্পূর্ণ নিরাপদ ও ব্যক্তিগত রাখা হয়। তৃতীয় পক্ষের সাথে কোনো ব্যক্তিগত তথ্য শেয়ার করা হয় না।",
    val termsAndConditionsBn: String = "Smart School Library BD শুধুমাত্র অনুমোদিত ও বৈধ শিক্ষামূলক উপকরণ অধ্যয়নের জন্য তৈরি। সাবস্ক্রিপশন শুধুমাত্র নির্বাচিত শ্রেণি (এবং ৯ম-১০ম শ্রেণির ক্ষেত্রে নির্বাচিত বিভাগ) এর জন্য প্রযোজ্য।",
    val deletedDefaultClassIds: List<String> = emptyList(),
    val deletedDefaultLevelIds: List<String> = emptyList(),
    val deletedDefaultPlanIds: List<String> = emptyList(),
    @ServerTimestamp val updatedAt: Timestamp? = null
) : FirestoreIdentifiable<AppSettings> {
    override fun withDocumentId(docId: String): AppSettings = copy(id = docId)

    fun canDownloadContent(contentType: String, itemAllowsDownload: Boolean): Boolean {
        if (!downloadsEnabled || !itemAllowsDownload) return false
        return when (contentType) {
            "BOOK" -> allowTextbookDownload
            "GUIDE" -> allowGuideDownload
            "NOTE" -> allowNoteDownload
            else -> false
        }
    }

    fun toWriteMap(): Map<String, Any> = mapOf(
        "downloadsEnabled" to downloadsEnabled,
        "allowTextbookDownload" to allowTextbookDownload,
        "allowGuideDownload" to allowGuideDownload,
        "allowNoteDownload" to allowNoteDownload,
        "defaultTheme" to defaultTheme,
        "defaultLanguage" to defaultLanguage,
        "announcementBannerBn" to announcementBannerBn,
        "contactEmail" to contactEmail,
        "contactPhone" to contactPhone,
        "websiteUrl" to websiteUrl,
        "privacyPolicyBn" to privacyPolicyBn,
        "termsAndConditionsBn" to termsAndConditionsBn,
        "deletedDefaultClassIds" to deletedDefaultClassIds.distinct().take(100),
        "deletedDefaultLevelIds" to deletedDefaultLevelIds.distinct().take(100),
        "deletedDefaultPlanIds" to deletedDefaultPlanIds.distinct().take(100),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

data class GroupedBookEntry(
    val groupKey: String,
    val primary: Textbook,
    val siblings: List<Textbook>,
    val classNumbers: List<Int>
) {
    val siblingIds: List<String> get() = siblings.map { it.id }.filter { it.isNotBlank() }
    val classSummaryLabel: String get() = classNumbers.joinToString(", ")
}

data class GroupedGuideEntry(
    val groupKey: String,
    val primary: GuideNote,
    val siblings: List<GuideNote>,
    val classNumbers: List<Int>
) {
    val siblingIds: List<String> get() = siblings.map { it.id }.filter { it.isNotBlank() }
    val classSummaryLabel: String get() = classNumbers.joinToString(", ")
}

object ContentClassGroupHelper {
    const val MAX_MULTI_CLASS_SELECTION = 2

    private val groupDocRegex = Regex("^(book|guide)_grp_([a-zA-Z0-9]+)_c\\d+$")
    private val linkDocRegex = Regex("^(book|guide)_link_(.+)_c\\d+$")

    fun groupKeyForId(docId: String): String {
        val clean = docId.trim()
        if (clean.isEmpty()) return ""
        groupDocRegex.matchEntire(clean)?.let { match ->
            val prefix = match.groupValues[1]
            val token = match.groupValues[2]
            return "${prefix}_grp_$token"
        }
        linkDocRegex.matchEntire(clean)?.let { match ->
            return match.groupValues[2]
        }
        return clean
    }

    fun isGroupedRootKey(groupKey: String): Boolean =
        groupKey.startsWith("book_grp_") || groupKey.startsWith("guide_grp_")

    fun buildGroupDocId(prefix: String, groupToken: String, classNumber: Int): String =
        "${prefix}_grp_${groupToken}_c${classNumber.coerceIn(1, 30)}"

    fun buildLinkedDocIdForGroup(prefix: String, groupKey: String, classNumber: Int): String {
        val cleanClass = classNumber.coerceIn(1, 30)
        return if (groupKey.startsWith("${prefix}_grp_")) {
            "${groupKey}_c$cleanClass"
        } else {
            val safeBase = groupKey.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(96)
            "${prefix}_link_${safeBase}_c$cleanClass"
        }
    }

    fun groupBooksForAdmin(books: List<Textbook>): List<GroupedBookEntry> {
        if (books.isEmpty()) return emptyList()
        val grouped = linkedMapOf<String, MutableList<Textbook>>()
        books.forEachIndexed { index, book ->
            val key = if (book.id.isBlank()) "blank_book_$index" else groupKeyForId(book.id)
            grouped.getOrPut(key) { mutableListOf() }.add(book)
        }
        return grouped.entries.map { (key, list) ->
            val sorted = list.sortedBy { it.classNumber }
            val classes = sorted.map { it.classNumber }.distinct().sorted()
            GroupedBookEntry(
                groupKey = key,
                primary = sorted.first(),
                siblings = sorted,
                classNumbers = classes
            )
        }
    }

    fun groupGuidesForAdmin(guides: List<GuideNote>): List<GroupedGuideEntry> {
        if (guides.isEmpty()) return emptyList()
        val grouped = linkedMapOf<String, MutableList<GuideNote>>()
        guides.forEachIndexed { index, guide ->
            val key = if (guide.id.isBlank()) "blank_guide_$index" else groupKeyForId(guide.id)
            grouped.getOrPut(key) { mutableListOf() }.add(guide)
        }
        return grouped.entries.map { (key, list) ->
            val sorted = list.sortedBy { it.classNumber }
            val classes = sorted.map { it.classNumber }.distinct().sorted()
            GroupedGuideEntry(
                groupKey = key,
                primary = sorted.first(),
                siblings = sorted,
                classNumbers = classes
            )
        }
    }

    fun deduplicateBooksForAllClassesView(books: List<Textbook>): List<Textbook> {
        if (books.size <= 1) return books
        val seenGroups = HashSet<String>(books.size)
        return buildList(books.size) {
            for (book in books) {
                if (book.id.isBlank()) {
                    add(book)
                } else {
                    val key = groupKeyForId(book.id)
                    if (seenGroups.add(key)) {
                        add(book)
                    }
                }
            }
        }
    }

    fun deduplicateGuidesForAllClassesView(guides: List<GuideNote>): List<GuideNote> {
        if (guides.size <= 1) return guides
        val seenGroups = HashSet<String>(guides.size)
        return buildList(guides.size) {
            for (guide in guides) {
                if (guide.id.isBlank()) {
                    add(guide)
                } else {
                    val key = groupKeyForId(guide.id)
                    if (seenGroups.add(key)) {
                        add(guide)
                    }
                }
            }
        }
    }
}

