package com.example.data.b2

/**
 * Backblaze B2 & Supabase Edge Function Storage Gateway Configuration.
 * 
 * Strict Security Guarantee:
 * - NO Backblaze Application Key or Secret Key is stored in the Android app.
 * - All presigned upload and download operations are brokered by the secure Supabase Edge Function Gateway.
 */
object B2Config {
    const val B2_BUCKET = "smart-school-library-files"
    const val B2_ENDPOINT = "https://s3.us-east-005.backblazeb2.com"
    const val B2_REGION = "us-east-005"

    /**
     * The public base URL of the deployed Supabase Edge Function Gateway.
     */
    const val SUPABASE_GATEWAY_URL = "https://ydvikkvgqivxsczjcinz.supabase.co/functions/v1/b2-storage-gateway"

    @Volatile
    var workerBaseUrl: String = SUPABASE_GATEWAY_URL

    val gatewayBaseUrl: String
        get() = workerBaseUrl

    fun isB2Path(rawPath: String?): Boolean {
        if (rawPath.isNullOrBlank()) return false
        val trimmed = rawPath.trim()
        return trimmed.startsWith("b2://", ignoreCase = true) ||
            trimmed.startsWith("books/", ignoreCase = true) ||
            trimmed.startsWith("guides/", ignoreCase = true) ||
            trimmed.startsWith("book_covers/", ignoreCase = true) ||
            trimmed.startsWith("guide_covers/", ignoreCase = true) ||
            trimmed.contains("backblazeb2.com", ignoreCase = true)
    }

    fun extractB2ObjectPath(rawPath: String?): String {
        if (rawPath.isNullOrBlank()) return ""
        val trimmed = rawPath.trim()
        return when {
            trimmed.startsWith("b2://smart-school-library-files/", ignoreCase = true) ->
                trimmed.removePrefix("b2://smart-school-library-files/")
            trimmed.startsWith("b2://", ignoreCase = true) ->
                trimmed.removePrefix("b2://").substringAfter("/")
            trimmed.contains("backblazeb2.com/smart-school-library-files/", ignoreCase = true) ->
                trimmed.substringAfter("backblazeb2.com/smart-school-library-files/").substringBefore("?")
            trimmed.startsWith("books/") || trimmed.startsWith("guides/") ||
                trimmed.startsWith("book_covers/") || trimmed.startsWith("guide_covers/") ->
                trimmed
            else -> trimmed
        }
    }

    fun buildCoverCdnUrl(rawPath: String?): String {
        val objectPath = extractB2ObjectPath(rawPath)
        if (objectPath.isBlank()) return ""
        val baseUrl = workerBaseUrl.trimEnd('/')
        return "$baseUrl/covers/$objectPath"
    }
}
