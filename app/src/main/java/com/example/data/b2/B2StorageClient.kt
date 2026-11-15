package com.example.data.b2

import android.content.Context
import android.net.Uri
import com.example.firebase.FirebaseProvider
import com.example.firebase.SecurityConstants
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject

data class UploadTicket(
    val uploadUrl: String,
    val storagePath: String,
    val expiresInSeconds: Long
)

/**
 * Client for communicating with the secure Supabase Edge Function Gateway and
 * streaming binary files directly to Backblaze B2 via S3 Presigned URLs.
 */
object B2StorageClient {

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.MINUTES)
            .readTimeout(15, TimeUnit.MINUTES)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private suspend fun getFreshFirebaseToken(context: Context): String {
        val auth = FirebaseProvider.getAuth(context)
        val user = auth.currentUser
            ?: throw IllegalStateException("প্রবেশাধিকার নিশ্চিত করতে প্রথমে সাইন ইন করুন।")
        val tokenResult = user.getIdToken(false).await()
        val token = tokenResult.token
        if (token.isNullOrBlank()) {
            throw IllegalStateException("Firebase অথেন্টিকেশন টোকেন সংগ্রহ করা সম্ভব হয়নি।")
        }
        return token
    }

    /**
     * Obtains a short-lived S3 Presigned Upload URL from the Cloudflare Worker.
     * Strictly restricted to the trusted Super Admin UID.
     */
    suspend fun requestUploadTicket(
        context: Context,
        category: String,
        classNumber: Int,
        itemId: String,
        fileType: String,
        fileName: String,
        contentType: String
    ): Result<UploadTicket> = withContext(Dispatchers.IO) {
        try {
            val auth = FirebaseProvider.getAuth(context)
            val currentUid = auth.currentUser?.uid
            check(SecurityConstants.isTrustedSuperAdmin(currentUid)) {
                "অননুমোদিত প্রবেশাধিকার: শুধুমাত্র সুপার অ্যাডমিন ফাইল আপলোড করতে পারবেন।"
            }

            val token = getFreshFirebaseToken(context)
            val endpoint = "${B2Config.workerBaseUrl.trimEnd('/')}/api/storage/upload-ticket"

            val jsonBody = JSONObject().apply {
                put("category", category)
                put("classNumber", classNumber)
                put("itemId", itemId)
                put("fileType", fileType)
                put("fileName", fileName)
                put("contentType", contentType)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching { JSONObject(bodyStr).optString("error") }.getOrNull()
                        ?: "সার্ভার এরর (HTTP ${response.code})"
                    return@withContext Result.failure(IOException(errMsg))
                }

                val json = JSONObject(bodyStr)
                val ticket = UploadTicket(
                    uploadUrl = json.getString("uploadUrl"),
                    storagePath = json.getString("storagePath"),
                    expiresInSeconds = json.optLong("expiresInSeconds", 900L)
                )
                Result.success(ticket)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Streams binary file directly from Android ContentResolver to the B2 Presigned URL.
     * Uses buffered streaming to ensure minimal RAM usage even for 200MB+ PDFs.
     */
    suspend fun uploadFileStream(
        context: Context,
        uploadUrl: String,
        uri: Uri,
        contentType: String,
        totalSizeBytes: Long,
        onProgress: (bytesWritten: Long, totalBytes: Long) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val mediaType = contentType.toMediaTypeOrNull()

            val requestBody = object : RequestBody() {
                override fun contentType(): MediaType? = mediaType
                override fun contentLength(): Long = totalSizeBytes

                override fun writeTo(sink: BufferedSink) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val buffer = ByteArray(32 * 1024) // 32 KB chunk
                        var bytesWritten = 0L
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            bytesWritten += read
                            onProgress(bytesWritten, totalSizeBytes)
                        }
                    } ?: throw IOException("স্থানীয় ফাইল পড়া সম্ভব হয়নি: $uri")
                }
            }

            val request = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .apply {
                    if (contentType.isNotBlank()) {
                        addHeader("Content-Type", contentType)
                    }
                }
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty().take(300)
                    return@withContext Result.failure(
                        IOException("Backblaze B2 আপলোড ব্যর্থ (HTTP ${response.code}): $errBody")
                    )
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Obtains a short-lived S3 Presigned GET URL for reading or downloading a private PDF.
     * The Cloudflare Worker validates the user's Firebase token and subscription status server-side.
     */
    suspend fun requestReadTicket(
        context: Context,
        storagePath: String,
        docId: String = "",
        collection: String = "books"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = getFreshFirebaseToken(context)
            val endpoint = "${B2Config.workerBaseUrl.trimEnd('/')}/api/storage/read-ticket"

            val jsonBody = JSONObject().apply {
                put("storagePath", B2Config.extractB2ObjectPath(storagePath))
                if (docId.isNotBlank()) put("docId", docId)
                put("collection", collection)
            }

            val request = Request.Builder()
                .url(endpoint)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val errMsg = runCatching { JSONObject(bodyStr).optString("error") }.getOrNull()
                        ?: "সার্ভার থেকে পড়ার অনুমতি মেলেনি (HTTP ${response.code})"
                    return@withContext Result.failure(IOException(errMsg))
                }

                val json = JSONObject(bodyStr)
                val downloadUrl = json.getString("downloadUrl")
                Result.success(downloadUrl)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolves the secure cover image URL via Cloudflare Worker edge cache.
     */
    fun resolveCoverUrl(storagePath: String): String {
        return B2Config.buildCoverCdnUrl(storagePath)
    }
}
