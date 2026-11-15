package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.data.b2.B2Config
import com.example.data.b2.B2StorageClient
import com.example.firebase.FirebaseProvider
import com.example.firebase.OperationType
import com.example.firebase.SecurityConstants
import com.example.firebase.handleFirestoreError
import com.example.model.ContentClassGroupHelper
import com.example.model.GuideNote
import com.example.model.Textbook
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

enum class UploadCategory(val labelBn: String, val labelEn: String) {
    ORIGINAL_TEXTBOOKS("মূল পাঠ্যবই", "Original Textbooks"),
    GUIDES_AND_NOTES("গাইড ও নোট", "Guides & Notes")
}

data class SelectedCoverInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val contentType: String,
    val storageFileName: String,
    val widthPx: Int,
    val heightPx: Int
)

data class SelectedPdfInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val formattedSize: String,
    val detectedPageCount: Int
)

sealed interface DirectUploadState {
    data object Idle : DirectUploadState
    data class Uploading(
        val progressPercent: Int,
        val activeFileName: String,
        val stageLabelBn: String,
        val stageLabelEn: String
    ) : DirectUploadState

    data class Succeeded(
        val itemId: String,
        val category: UploadCategory,
        val coverStoragePath: String,
        val pdfStoragePath: String,
        val messageBn: String,
        val messageEn: String
    ) : DirectUploadState

    data class Failed(
        val errorMessageBn: String,
        val errorMessageEn: String,
        val canRetry: Boolean = true
    ) : DirectUploadState
}

data class DirectUploadDraft(
    val existingId: String = "",
    val category: UploadCategory = UploadCategory.ORIGINAL_TEXTBOOKS,
    val titleBn: String = "",
    val titleEn: String = "",
    val classNumber: Int = 5,
    val stream: String = "ALL",
    val subjectId: String = "",
    val subjectNameBn: String = "",
    val subjectNameEn: String = "",
    val chapter: String = "সম্পূর্ণ বই",
    val guideType: String = "COMPLETE_GUIDE",
    val author: String = "NCTB",
    val publisher: String = "জাতীয় শিক্ষাক্রম ও পাঠ্যপুস্তক বোর্ড (NCTB)",
    val edition: String = "NCTB 2026",
    val academicYear: String = "2026",
    val language: String = "BN",
    val shortDescription: String = "",
    val fullDescription: String = "",
    val pageCount: Int = 100,
    val tableOfContents: List<String> = emptyList(),
    val isPremium: Boolean = false,
    val isPublished: Boolean = true,
    val allowDownload: Boolean = false,
    val isPopular: Boolean = false,
    val existingCoverStoragePath: String = "",
    val existingPdfStoragePath: String = "",
    val existingCoverFileName: String = "",
    val existingCoverFileSizeBytes: Long = 0L,
    val existingPdfFileName: String = "",
    val existingPdfFileSizeBytes: Long = 0L,
    val selectedClasses: List<Int> = emptyList(),
    val existingSiblingIds: List<String> = emptyList(),
    val streamCapableClasses: Set<Int> = setOf(9, 10)
)

object LocalUploadValidator {
    const val MAX_PDF_BYTES: Long = 250L * 1024L * 1024L
    const val MAX_COVER_BYTES: Long = 15L * 1024L * 1024L
    private const val STAGING_DIR_NAME = "admin_staged_uploads"

    private fun ensureStagingDir(context: Context): File {
        val dir = File(context.cacheDir, STAGING_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) {
            return String.format(Locale.US, "%.1f KB", kb)
        }
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    /**
     * Pure binary header check verifying genuine `%PDF-` magic signature and valid size bounds.
     */
    fun isValidPdfBinaryHeader(headerBytes: ByteArray, totalSizeBytes: Long): Boolean {
        if (totalSizeBytes < 8L || totalSizeBytes > MAX_PDF_BYTES) return false
        if (headerBytes.size < 5) return false
        val magic = String(headerBytes.copyOfRange(0, 5), Charsets.US_ASCII)
        return magic == "%PDF-"
    }

    /**
     * Maps MIME type and/or file extension to a canonical Firebase Storage cover filename and MIME.
     * Supports strictly JPG, JPEG, PNG, and WEBP.
     */
    fun resolveSupportedCoverFormat(mimeType: String?, fileName: String): Pair<String, String>? {
        val cleanMime = mimeType?.lowercase(Locale.US)?.trim().orEmpty()
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.US).trim()
        return when {
            cleanMime == "image/png" || ext == "png" -> "image/png" to "cover.png"
            cleanMime == "image/webp" || ext == "webp" -> "image/webp" to "cover.webp"
            cleanMime == "image/jpeg" || cleanMime == "image/jpg" || ext == "jpg" || ext == "jpeg" ->
                "image/jpeg" to "cover.jpg"
            else -> null
        }
    }

    suspend fun validateSelectedPdf(
        context: Context,
        uri: Uri,
        preservedDisplayName: String? = null
    ): Result<SelectedPdfInfo> = withContext(Dispatchers.IO) {
        try {
            // Fast path when re-validating an already staged local PDF file
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val localFile = uri.path?.let(::File)
                if (localFile == null || !localFile.exists() || !localFile.canRead()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("নির্বাচিত PDF ফাইলটি পাওয়া যায়নি। অনুগ্রহ করে পুনরায় নির্বাচন করুন।")
                    )
                }
                val header = ByteArray(5)
                val read = FileInputStream(localFile).use { it.read(header) }
                val length = localFile.length()
                if (read < 5 || !isValidPdfBinaryHeader(header, length)) {
                    val reason = if (length > MAX_PDF_BYTES) {
                        "PDF ফাইলের আকার সর্বোচ্চ ২৫০ MB হতে পারবে।"
                    } else {
                        "নির্বাচিত ফাইলটি বৈধ আসল PDF নয় (%PDF- হেডার পাওয়া যায়নি)।"
                    }
                    return@withContext Result.failure(IllegalArgumentException(reason))
                }
                var pages = 0
                runCatching {
                    ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { renderer ->
                            pages = renderer.pageCount
                        }
                    }
                }
                val displayName = preservedDisplayName?.takeIf { it.isNotBlank() } ?: localFile.name
                return@withContext Result.success(
                    SelectedPdfInfo(
                        uri = Uri.fromFile(localFile),
                        displayName = displayName,
                        sizeBytes = length,
                        formattedSize = formatFileSize(length),
                        detectedPageCount = pages
                    )
                )
            }

            val resolver = context.contentResolver
            val meta = queryDisplayNameAndSize(context, uri, fallbackName = "selected_book.pdf")
            val mime = resolver.getType(uri)?.lowercase(Locale.US).orEmpty()
            if (mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/")) {
                return@withContext Result.failure(
                    IllegalArgumentException("শুধুমাত্র আসল PDF ফাইল (.pdf) নির্বাচন করুন।")
                )
            }

            val stagingDir = ensureStagingDir(context)
            // Clean up older staged PDFs to save device cache space
            stagingDir.listFiles()?.filter { it.name.startsWith("staged_pdf_") }?.forEach {
                runCatching { it.delete() }
            }
            val stagedFile = File(stagingDir, "staged_pdf_${System.currentTimeMillis()}.pdf")

            val header = ByteArray(5)
            var headerFilled = 0
            var totalCopied = 0L

            val opened = resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(stagedFile).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var chunk: Int
                    while (input.read(buf).also { chunk = it } != -1) {
                        if (headerFilled < 5) {
                            val needed = minOf(5 - headerFilled, chunk)
                            System.arraycopy(buf, 0, header, headerFilled, needed)
                            headerFilled += needed
                            if (headerFilled == 5) {
                                val magic = String(header, Charsets.US_ASCII)
                                if (magic != "%PDF-") {
                                    break
                                }
                            }
                        }
                        totalCopied += chunk
                        if (totalCopied > MAX_PDF_BYTES) {
                            break
                        }
                        output.write(buf, 0, chunk)
                    }
                    output.flush()
                }
                true
            } ?: false

            if (!opened) {
                runCatching { stagedFile.delete() }
                return@withContext Result.failure(
                    IllegalArgumentException("ফাইল ম্যানেজার থেকে PDF ফাইলটি খোলা যায়নি। অনুগ্রহ করে অভ্যন্তরীণ মেমোরি বা Downloads থেকে ফাইল নির্বাচন করুন।")
                )
            }

            if (headerFilled < 5 || !isValidPdfBinaryHeader(header, totalCopied)) {
                runCatching { stagedFile.delete() }
                val reason = if (totalCopied > MAX_PDF_BYTES) {
                    "PDF ফাইলের আকার সর্বোচ্চ ২৫০ MB হতে পারবে।"
                } else {
                    "নির্বাচিত ফাইলটি বৈধ আসল PDF নয় (%PDF- হেডার পাওয়া যায়নি)।"
                }
                return@withContext Result.failure(IllegalArgumentException(reason))
            }

            var detectedPages = 0
            runCatching {
                ParcelFileDescriptor.open(stagedFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        detectedPages = renderer.pageCount
                    }
                }
            }

            val cleanDisplayName = if (meta.first.lowercase(Locale.US).endsWith(".pdf")) {
                meta.first
            } else {
                "${meta.first}.pdf"
            }

            Result.success(
                SelectedPdfInfo(
                    uri = Uri.fromFile(stagedFile),
                    displayName = cleanDisplayName,
                    sizeBytes = stagedFile.length(),
                    formattedSize = formatFileSize(stagedFile.length()),
                    detectedPageCount = detectedPages
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException("PDF ফাইল যাচাই করা যায়নি: ${e.localizedMessage ?: "অজানা ত্রুটি"}")
            )
        }
    }

    suspend fun validateSelectedCover(
        context: Context,
        uri: Uri,
        preservedDisplayName: String? = null
    ): Result<SelectedCoverInfo> = withContext(Dispatchers.IO) {
        try {
            if (uri.scheme.equals("file", ignoreCase = true)) {
                val localFile = uri.path?.let(::File)
                if (localFile == null || !localFile.exists() || !localFile.canRead()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("নির্বাচিত কভার ছবিটি পাওয়া যায়নি। অনুগ্রহ করে পুনরায় নির্বাচন করুন।")
                    )
                }
                val resolved = resolveSupportedCoverFormat(null, localFile.name)
                    ?: ("image/jpeg" to "cover.jpg")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(localFile.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || localFile.length() !in 1L..MAX_COVER_BYTES) {
                    return@withContext Result.failure(
                        IllegalArgumentException("নির্বাচিত ফাইলটি বৈধ ছবি নয় বা ক্ষতিগ্রস্ত।")
                    )
                }
                return@withContext Result.success(
                    SelectedCoverInfo(
                        uri = Uri.fromFile(localFile),
                        displayName = preservedDisplayName?.takeIf { it.isNotBlank() } ?: localFile.name,
                        sizeBytes = localFile.length(),
                        formattedSize = formatFileSize(localFile.length()),
                        contentType = resolved.first,
                        storageFileName = resolved.second,
                        widthPx = bounds.outWidth,
                        heightPx = bounds.outHeight
                    )
                )
            }

            val resolver = context.contentResolver
            val meta = queryDisplayNameAndSize(context, uri, fallbackName = "book_cover.jpg")
            val mime = resolver.getType(uri)
            val directFormat = resolveSupportedCoverFormat(mime, meta.first)

            val stagingDir = ensureStagingDir(context)
            stagingDir.listFiles()?.filter { it.name.startsWith("staged_cover_") }?.forEach {
                runCatching { it.delete() }
            }

            if (directFormat != null) {
                val stagedFile = File(stagingDir, "staged_cover_${System.currentTimeMillis()}_${directFormat.second}")
                var totalCopied = 0L
                val opened = resolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(stagedFile).use { output ->
                        val buf = ByteArray(32 * 1024)
                        var chunk: Int
                        while (input.read(buf).also { chunk = it } != -1) {
                            totalCopied += chunk
                            if (totalCopied > MAX_COVER_BYTES) break
                            output.write(buf, 0, chunk)
                        }
                        output.flush()
                    }
                    true
                } ?: false

                if (!opened || totalCopied <= 0L || totalCopied > MAX_COVER_BYTES) {
                    runCatching { stagedFile.delete() }
                    return@withContext Result.failure(
                        IllegalArgumentException("কভার ছবির আকার ০ বাইটের বেশি এবং সর্বোচ্চ ১৫ MB হতে হবে।")
                    )
                }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(stagedFile.absolutePath, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    runCatching { stagedFile.delete() }
                    return@withContext Result.failure(
                        IllegalArgumentException("নির্বাচিত ফাইলটি বৈধ ছবি নয় বা ক্ষতিগ্রস্ত।")
                    )
                }

                return@withContext Result.success(
                    SelectedCoverInfo(
                        uri = Uri.fromFile(stagedFile),
                        displayName = meta.first,
                        sizeBytes = stagedFile.length(),
                        formattedSize = formatFileSize(stagedFile.length()),
                        contentType = directFormat.first,
                        storageFileName = directFormat.second,
                        widthPx = bounds.outWidth,
                        heightPx = bounds.outHeight
                    )
                )
            } else {
                // Fallback for gallery HEIC/HEIF/BMP images: decode Bitmap and stage as standard JPEG
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, bounds)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    return@withContext Result.failure(
                        IllegalArgumentException("শুধুমাত্র JPG, JPEG, PNG অথবা WEBP কভার ছবি নির্বাচন করুন।")
                    )
                }
                var sampleSize = 1
                while (bounds.outWidth / sampleSize > 2048 || bounds.outHeight / sampleSize > 2048) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decodedBitmap = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext Result.failure(
                    IllegalArgumentException("শুধুমাত্র JPG, JPEG, PNG অথবা WEBP কভার ছবি নির্বাচন করুন।")
                )

                val stagedFile = File(stagingDir, "staged_cover_${System.currentTimeMillis()}_cover.jpg")
                val outWidth = decodedBitmap.width
                val outHeight = decodedBitmap.height
                FileOutputStream(stagedFile).use { out ->
                    decodedBitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                    out.flush()
                }
                decodedBitmap.recycle()

                if (stagedFile.length() !in 1L..MAX_COVER_BYTES) {
                    runCatching { stagedFile.delete() }
                    return@withContext Result.failure(
                        IllegalArgumentException("কভার ছবির আকার ০ বাইটের বেশি এবং সর্বোচ্চ ১৫ MB হতে হবে।")
                    )
                }

                return@withContext Result.success(
                    SelectedCoverInfo(
                        uri = Uri.fromFile(stagedFile),
                        displayName = meta.first,
                        sizeBytes = stagedFile.length(),
                        formattedSize = formatFileSize(stagedFile.length()),
                        contentType = "image/jpeg",
                        storageFileName = "cover.jpg",
                        widthPx = outWidth,
                        heightPx = outHeight
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException("কভার ছবি যাচাই করা যায়নি: ${e.localizedMessage ?: "অজানা ত্রুটি"}")
            )
        }
    }

    private fun queryDisplayNameAndSize(
        context: Context,
        uri: Uri,
        fallbackName: String
    ): Pair<String, Long> {
        var name = fallbackName
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) {
                        val candidate = cursor.getString(nameIdx)?.trim()
                        if (!candidate.isNullOrBlank()) name = candidate
                    }
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        size = cursor.getLong(sizeIdx)
                    }
                }
            }
        }
        if (name == fallbackName) {
            uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let {
                name = it
            }
        }
        return name to size
    }
}

/**
 * Super Admin Direct File Uploader for Original Complete Book PDFs and Book Covers.
 *
 * Guarantees:
 * - Strictly checks trusted Super Admin UID (`yyskhmPvvfeNl572FnVhZgYAaQx2`).
 * - Uploads raw PDF binary unchanged (`application/pdf`, zero compression or conversion) to
 *   `books/{classId}/{bookId}/original_book.pdf`.
 * - Uploads cover image to `book_covers/{classId}/{bookId}/cover.{jpg|png|webp}`.
 * - Never publishes metadata to Firestore until BOTH required files exist in Firebase Storage.
 * - Never overwrites existing book/guide documents when creating a new record.
 */
class BookStorageUploader(
    private val context: Context,
    private val db: FirebaseFirestore = FirebaseProvider.getFirestore(context),
    private val auth: FirebaseAuth = FirebaseProvider.getAuth(context),
    private val storage: FirebaseStorage = FirebaseProvider.getStorage(context)
) {
    private fun requireTrustedSuperAdmin(): String {
        val uid = auth.currentUser?.uid
        check(SecurityConstants.isTrustedSuperAdmin(uid)) {
            "অননুমোদিত প্রবেশাধিকার: শুধুমাত্র অনুমোদিত সুপার অ্যাডমিন বই ও PDF আপলোড করতে পারবেন।"
        }
        return uid!!
    }

    companion object {
        fun buildClassStorageSegment(classNumber: Int): String =
            "class_${classNumber.coerceIn(1, 12)}"

        fun buildPdfStoragePath(classNumber: Int, itemId: String): String =
            "books/${buildClassStorageSegment(classNumber)}/$itemId/original_book.pdf"

        fun buildCoverStoragePath(classNumber: Int, itemId: String, coverFileName: String): String =
            "book_covers/${buildClassStorageSegment(classNumber)}/$itemId/$coverFileName"
    }

    suspend fun uploadAndPublish(
        draft: DirectUploadDraft,
        selectedCover: SelectedCoverInfo?,
        selectedPdf: SelectedPdfInfo?,
        onProgress: (DirectUploadState.Uploading) -> Unit
    ): Result<DirectUploadState.Succeeded> = withContext(Dispatchers.IO) {
        try {
            requireTrustedSuperAdmin()

            val isEditMode = draft.existingId.isNotBlank()
            val hasFinalCover = selectedCover != null || draft.existingCoverStoragePath.isNotBlank()
            val hasFinalPdf = selectedPdf != null || draft.existingPdfStoragePath.isNotBlank()

            if (!hasFinalCover) {
                return@withContext Result.failure(
                    IllegalArgumentException("প্রকাশ করার আগে বইয়ের কভার ছবি নির্বাচন করুন।")
                )
            }
            if (!hasFinalPdf) {
                return@withContext Result.failure(
                    IllegalArgumentException("প্রকাশ করার আগে সম্পূর্ণ বইয়ের আসল PDF নির্বাচন করুন।")
                )
            }
            if (draft.titleBn.isBlank() || draft.subjectNameBn.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("বইয়ের নাম এবং বিষয় পূরণ করা আবশ্যক।")
                )
            }

            val targetClasses = (if (draft.selectedClasses.isNotEmpty()) {
                draft.selectedClasses
            } else if (draft.classNumber > 0) {
                listOf(draft.classNumber)
            } else {
                emptyList()
            }).map { it.coerceIn(1, 30) }.distinct().sorted()

            if (targetClasses.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("কমপক্ষে ১টি শ্রেণি নির্বাচন করুন (সর্বোচ্চ ২টি নির্বাচন করা যাবে)।")
                )
            }
            if (targetClasses.size > ContentClassGroupHelper.MAX_MULTI_CLASS_SELECTION) {
                return@withContext Result.failure(
                    IllegalArgumentException("সর্বোচ্চ ২টি শ্রেণি নির্বাচন করা যাবে।")
                )
            }

            val collectionName = when (draft.category) {
                UploadCategory.ORIGINAL_TEXTBOOKS -> "books"
                UploadCategory.GUIDES_AND_NOTES -> "guides"
            }
            val idPrefix = when (draft.category) {
                UploadCategory.ORIGINAL_TEXTBOOKS -> "book"
                UploadCategory.GUIDES_AND_NOTES -> "guide"
            }

            val primaryClass = targetClasses.first()
            val existingSnapshots = linkedMapOf<String, DocumentSnapshot>()
            val targetDocs: List<Pair<String, Int>>
            val storageOwnerId: String

            if (isEditMode) {
                val baseEditId = draft.existingId.trim()
                val groupKey = ContentClassGroupHelper.groupKeyForId(baseEditId)
                val seedIds = (draft.existingSiblingIds + baseEditId)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                for (sid in seedIds) {
                    val snap = db.collection(collectionName).document(sid).get().await()
                    if (snap.exists()) {
                        existingSnapshots[sid] = snap
                    }
                }
                val allInCollection = runCatching {
                    db.collection(collectionName).get().await().documents
                }.getOrElse { emptyList() }
                for (doc in allInCollection) {
                    if (doc.exists() && ContentClassGroupHelper.groupKeyForId(doc.id) == groupKey) {
                        existingSnapshots.putIfAbsent(doc.id, doc)
                    }
                }

                val usedExistingIds = mutableSetOf<String>()
                targetDocs = targetClasses.map { cls ->
                    val matchingExisting = existingSnapshots.values.firstOrNull { snap ->
                        snap.id !in usedExistingIds && snap.getLong("classNumber")?.toInt() == cls
                    }
                    val chosenId = when {
                        matchingExisting != null -> matchingExisting.id
                        targetClasses.size == 1 && existingSnapshots.size == 1 ->
                            existingSnapshots.keys.first()
                        else ->
                            ContentClassGroupHelper.buildLinkedDocIdForGroup(idPrefix, groupKey, cls)
                    }
                    usedExistingIds += chosenId
                    if (chosenId !in existingSnapshots) {
                        val extraSnap = db.collection(collectionName).document(chosenId).get().await()
                        if (extraSnap.exists()) {
                            existingSnapshots[chosenId] = extraSnap
                        }
                    }
                    chosenId to cls
                }
                storageOwnerId = targetDocs.first().first
            } else {
                if (targetClasses.size == 1) {
                    val singleId = allocateNonOverwritingId(collectionName, idPrefix)
                    storageOwnerId = singleId
                    targetDocs = listOf(singleId to primaryClass)
                } else {
                    val groupToken = allocateNonOverwritingGroupToken(collectionName, idPrefix, targetClasses)
                    storageOwnerId = "${idPrefix}_grp_$groupToken"
                    targetDocs = targetClasses.map { cls ->
                        ContentClassGroupHelper.buildGroupDocId(idPrefix, groupToken, cls) to cls
                    }
                }
            }

            val idsToDelete = if (isEditMode) {
                val keepIds = targetDocs.map { it.first }.toSet()
                existingSnapshots.keys.filter { it !in keepIds }
            } else {
                emptyList()
            }

            val totalBytesToUpload = (selectedCover?.sizeBytes ?: 0L) + (selectedPdf?.sizeBytes ?: 0L)

            // 1. Upload Cover Image ONCE if newly selected
            val finalCoverStoragePath: String
            val finalCoverUrl: String
            val finalCoverFileName: String
            val finalCoverSizeBytes: Long

            if (selectedCover != null) {
                val revalidatedCover = LocalUploadValidator.validateSelectedCover(
                    context = context,
                    uri = selectedCover.uri,
                    preservedDisplayName = selectedCover.displayName
                ).getOrThrow()

                onProgress(
                    DirectUploadState.Uploading(
                        progressPercent = 2,
                        activeFileName = revalidatedCover.displayName,
                        stageLabelBn = "কভার আপলোড টিকিট সংগ্রহ হচ্ছে...",
                        stageLabelEn = "Requesting cover upload ticket..."
                    )
                )

                val coverTicket = B2StorageClient.requestUploadTicket(
                    context = context,
                    category = draft.category.name,
                    classNumber = primaryClass,
                    itemId = storageOwnerId,
                    fileType = "COVER",
                    fileName = revalidatedCover.displayName,
                    contentType = revalidatedCover.contentType
                ).getOrThrow()

                onProgress(
                    DirectUploadState.Uploading(
                        progressPercent = 5,
                        activeFileName = revalidatedCover.displayName,
                        stageLabelBn = "কভার ছবি আপলোড হচ্ছে...",
                        stageLabelEn = "Uploading book cover..."
                    )
                )

                B2StorageClient.uploadFileStream(
                    context = context,
                    uploadUrl = coverTicket.uploadUrl,
                    uri = revalidatedCover.uri,
                    contentType = revalidatedCover.contentType,
                    totalSizeBytes = revalidatedCover.sizeBytes
                ) { transferred, _ ->
                    val pct = if (totalBytesToUpload > 0L) {
                        ((transferred * 45L) / totalBytesToUpload).toInt().coerceIn(5, 45)
                    } else 25
                    onProgress(
                        DirectUploadState.Uploading(
                            progressPercent = pct,
                            activeFileName = revalidatedCover.displayName,
                            stageLabelBn = "কভার ছবি আপলোড হচ্ছে...",
                            stageLabelEn = "Uploading book cover..."
                        )
                    )
                }.getOrThrow()

                finalCoverStoragePath = coverTicket.storagePath
                finalCoverUrl = "b2://${B2Config.B2_BUCKET}/${coverTicket.storagePath}"
                finalCoverFileName = revalidatedCover.displayName
                finalCoverSizeBytes = revalidatedCover.sizeBytes
            } else {
                val normalized = normalizeExistingStorageRef(draft.existingCoverStoragePath)
                finalCoverStoragePath = normalized.first
                finalCoverUrl = normalized.second
                finalCoverFileName = draft.existingCoverFileName
                finalCoverSizeBytes = draft.existingCoverFileSizeBytes
            }

            // 2. Upload Original Complete PDF ONCE Unchanged (Raw binary stream, zero conversion/compression)
            val finalPdfStoragePath: String
            val finalPdfUrl: String
            val finalPdfFileName: String
            val finalPdfSizeBytes: Long
            val finalPageCount: Int

            if (selectedPdf != null) {
                val revalidated = LocalUploadValidator.validateSelectedPdf(
                    context = context,
                    uri = selectedPdf.uri,
                    preservedDisplayName = selectedPdf.displayName
                ).getOrThrow()

                onProgress(
                    DirectUploadState.Uploading(
                        progressPercent = 48,
                        activeFileName = revalidated.displayName,
                        stageLabelBn = "PDF আপলোড টিকিট সংগ্রহ হচ্ছে...",
                        stageLabelEn = "Requesting PDF upload ticket..."
                    )
                )

                val pdfTicket = B2StorageClient.requestUploadTicket(
                    context = context,
                    category = draft.category.name,
                    classNumber = primaryClass,
                    itemId = storageOwnerId,
                    fileType = "PDF",
                    fileName = revalidated.displayName,
                    contentType = "application/pdf"
                ).getOrThrow()

                onProgress(
                    DirectUploadState.Uploading(
                        progressPercent = 50,
                        activeFileName = revalidated.displayName,
                        stageLabelBn = "আসল সম্পূর্ণ বইয়ের PDF আপলোড হচ্ছে...",
                        stageLabelEn = "Uploading original complete PDF..."
                    )
                )

                B2StorageClient.uploadFileStream(
                    context = context,
                    uploadUrl = pdfTicket.uploadUrl,
                    uri = revalidated.uri,
                    contentType = "application/pdf",
                    totalSizeBytes = revalidated.sizeBytes
                ) { transferred, _ ->
                    val pct = if (totalBytesToUpload > 0L) {
                        48 + ((transferred * 46L) / totalBytesToUpload).toInt().coerceIn(0, 46)
                    } else 75
                    onProgress(
                        DirectUploadState.Uploading(
                            progressPercent = pct.coerceIn(5, 95),
                            activeFileName = revalidated.displayName,
                            stageLabelBn = "আসল সম্পূর্ণ বইয়ের PDF আপলোড হচ্ছে...",
                            stageLabelEn = "Uploading original complete PDF..."
                        )
                    )
                }.getOrThrow()

                finalPdfStoragePath = pdfTicket.storagePath
                finalPdfUrl = "b2://${B2Config.B2_BUCKET}/${pdfTicket.storagePath}"
                finalPdfFileName = revalidated.displayName
                finalPdfSizeBytes = revalidated.sizeBytes
                finalPageCount = if (revalidated.detectedPageCount > 0) {
                    revalidated.detectedPageCount.coerceIn(1, 3000)
                } else {
                    draft.pageCount.coerceIn(1, 3000)
                }
            } else {
                val normalized = normalizeExistingStorageRef(draft.existingPdfStoragePath)
                finalPdfStoragePath = normalized.first
                finalPdfUrl = normalized.second
                finalPdfFileName = draft.existingPdfFileName
                finalPdfSizeBytes = draft.existingPdfFileSizeBytes
                finalPageCount = draft.pageCount.coerceIn(1, 3000)
            }

            // Strict Gate: Never publish to Firestore unless BOTH cover and PDF refs are non-empty
            check((finalCoverStoragePath.isNotBlank() || finalCoverUrl.isNotBlank()) &&
                (finalPdfStoragePath.isNotBlank() || finalPdfUrl.isNotBlank())) {
                "কভার ছবি এবং সম্পূর্ণ PDF—উভয় ফাইল সফলভাবে আপলোড না হওয়া পর্যন্ত বই প্রকাশ করা যাবে না।"
            }

            // 3. Persist Metadata & Storage Paths atomically across all selected classes (1 or 2)
            onProgress(
                DirectUploadState.Uploading(
                    progressPercent = 97,
                    activeFileName = finalPdfFileName.ifBlank { draft.titleBn },
                    stageLabelBn = "ডাটাবেজে বইয়ের তথ্য সংরক্ষণ করা হচ্ছে...",
                    stageLabelEn = "Saving book metadata to Firestore..."
                )
            )

            val rawStreamNormalized = draft.stream.uppercase(Locale.US).takeIf {
                it in setOf("ALL", "SCIENCE", "HUMANITIES", "BUSINESS")
            } ?: "ALL"
            val normalizedLang = draft.language.uppercase(Locale.US).takeIf { it in setOf("BN", "EN") } ?: "BN"
            val batch = db.batch()

            for ((docId, cls) in targetDocs) {
                val docRef = db.collection(collectionName).document(docId)
                val existingSnap = existingSnapshots[docId]
                if (!isEditMode && existingSnap?.exists() == true) {
                    return@withContext Result.failure(
                        IllegalStateException("একই আইডির একটি বই ইতিমধ্যে বিদ্যমান; পুরনো বই প্রতিস্থাপন প্রতিরোধ করা হয়েছে।")
                    )
                }
                val isExistingDoc = existingSnap?.exists() == true
                val classHasStreams = cls in 9..10 || cls in draft.streamCapableClasses
                val classStream = if (classHasStreams) rawStreamNormalized else "ALL"
                val resolvedSubjectId = draft.subjectId.ifBlank {
                    "subj_${cls}_${draft.subjectNameBn.trim().hashCode().toUInt()}"
                }

                when (draft.category) {
                    UploadCategory.ORIGINAL_TEXTBOOKS -> {
                        val bookModel = Textbook(
                            id = docId,
                            titleBn = draft.titleBn.trim(),
                            titleEn = draft.titleEn.trim().ifBlank { draft.titleBn.trim() },
                            subjectId = resolvedSubjectId,
                            subjectNameBn = draft.subjectNameBn.trim(),
                            subjectNameEn = draft.subjectNameEn.trim(),
                            classNumber = cls,
                            stream = classStream,
                            academicYear = draft.academicYear.trim().ifBlank { "2026" },
                            edition = draft.edition.trim().ifBlank { "NCTB" },
                            author = draft.author.trim().ifBlank { "NCTB" },
                            publisher = draft.publisher.trim().ifBlank { "NCTB" },
                            shortDescription = draft.shortDescription.trim(),
                            description = draft.fullDescription.trim().ifBlank { draft.shortDescription.trim() },
                            language = normalizedLang,
                            pageCount = finalPageCount,
                            coverUrl = finalCoverUrl,
                            pdfUrl = finalPdfUrl,
                            coverStoragePath = finalCoverStoragePath,
                            pdfStoragePath = finalPdfStoragePath,
                            coverFileName = finalCoverFileName,
                            coverFileSizeBytes = finalCoverSizeBytes,
                            pdfFileName = finalPdfFileName,
                            pdfFileSizeBytes = finalPdfSizeBytes,
                            isPremium = draft.isPremium,
                            isPublished = draft.isPublished,
                            allowDownload = draft.allowDownload,
                            isPopular = draft.isPopular,
                            tableOfContents = draft.tableOfContents.take(60).map { it.take(200) },
                            createdAt = if (isExistingDoc) existingSnap?.getTimestamp("createdAt") else null
                        )
                        batch.set(docRef, bookModel.toWriteMap(isCreate = !isExistingDoc))
                    }

                    UploadCategory.GUIDES_AND_NOTES -> {
                        val normalizedGuideType = draft.guideType.trim().let { gt ->
                            if (gt.isBlank() || gt == "FULL_GUIDE") "COMPLETE_GUIDE" else gt
                        }
                        val guideModel = GuideNote(
                            id = docId,
                            titleBn = draft.titleBn.trim(),
                            titleEn = draft.titleEn.trim().ifBlank { draft.titleBn.trim() },
                            classNumber = cls,
                            stream = classStream,
                            subjectId = resolvedSubjectId,
                            subjectNameBn = draft.subjectNameBn.trim(),
                            subjectNameEn = draft.subjectNameEn.trim(),
                            chapter = draft.chapter.trim().ifBlank { "সম্পূর্ণ গাইড" },
                            guideType = normalizedGuideType,
                            academicYear = draft.academicYear.trim().ifBlank { "2026" },
                            language = normalizedLang,
                            author = draft.author.trim(),
                            publisher = draft.publisher.trim(),
                            edition = draft.edition.trim().ifBlank { draft.academicYear.trim().ifBlank { "2026" } },
                            shortDescription = draft.shortDescription.trim(),
                            summaryContent = draft.fullDescription.trim().ifBlank { draft.shortDescription.trim() },
                            tableOfContents = draft.tableOfContents.take(60).map { it.take(200) },
                            pdfUrl = finalPdfUrl,
                            coverUrl = finalCoverUrl,
                            coverStoragePath = finalCoverStoragePath,
                            pdfStoragePath = finalPdfStoragePath,
                            coverFileName = finalCoverFileName,
                            coverFileSizeBytes = finalCoverSizeBytes,
                            pdfFileName = finalPdfFileName,
                            pdfFileSizeBytes = finalPdfSizeBytes,
                            pageCount = finalPageCount,
                            isPremium = draft.isPremium,
                            isPublished = draft.isPublished,
                            allowDownload = draft.allowDownload,
                            createdAt = if (isExistingDoc) existingSnap?.getTimestamp("createdAt") else null
                        )
                        batch.set(docRef, guideModel.toWriteMap(isCreate = !isExistingDoc))
                    }
                }
            }

            for (delId in idsToDelete) {
                batch.delete(db.collection(collectionName).document(delId))
            }

            batch.commit().await()

            val classSummaryBn = targetClasses.joinToString(", ")
            val successBn = if (targetClasses.size > 1) {
                "সফলভাবে আসল PDF ও কভার ছবি একবার আপলোড করে শ্রেণি $classSummaryBn-এ প্রকাশিত হয়েছে!"
            } else {
                "সফলভাবে আসল PDF ও কভার ছবি আপলোড এবং লাইব্রেরিতে প্রকাশিত হয়েছে!"
            }
            val successEn = if (targetClasses.size > 1) {
                "Original PDF and cover uploaded once and published to classes $classSummaryBn!"
            } else {
                "Original PDF and cover uploaded and published to library!"
            }

            Result.success(
                DirectUploadState.Succeeded(
                    itemId = targetDocs.first().first,
                    category = draft.category,
                    coverStoragePath = finalCoverStoragePath.ifBlank { finalCoverUrl },
                    pdfStoragePath = finalPdfStoragePath.ifBlank { finalPdfUrl },
                    messageBn = successBn,
                    messageEn = successEn
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "direct_book_upload")
            Result.failure(e)
        }
    }

    private fun normalizeExistingStorageRef(rawRef: String): Pair<String, String> {
        val trimmed = rawRef.trim()
        if (trimmed.isBlank()) return "" to ""
        return if (B2Config.isB2Path(trimmed)) {
            val objectPath = B2Config.extractB2ObjectPath(trimmed)
            if (objectPath.isNotBlank()) {
                objectPath to "b2://${B2Config.B2_BUCKET}/$objectPath"
            } else {
                "" to trimmed
            }
        } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            "" to trimmed
        } else {
            trimmed to trimmed
        }
    }

    private suspend fun allocateNonOverwritingId(collectionName: String, prefix: String): String {
        repeat(5) {
            val candidate = "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val snap = db.collection(collectionName).document(candidate).get().await()
            if (!snap.exists()) {
                return candidate
            }
        }
        return "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(8)}"
    }

    private suspend fun allocateNonOverwritingGroupToken(
        collectionName: String,
        prefix: String,
        classes: List<Int>
    ): String {
        repeat(5) {
            val token = UUID.randomUUID().toString().replace("-", "").take(14)
            val allFree = classes.all { cls ->
                val docId = ContentClassGroupHelper.buildGroupDocId(prefix, token, cls)
                !db.collection(collectionName).document(docId).get().await().exists()
            }
            if (allFree) return token
        }
        return "${System.currentTimeMillis()}_${UUID.randomUUID().toString().replace("-", "").take(6)}"
    }
}
