package com.example.firebase

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage

/**
 * Centralized, crash-safe Firebase initialization and Firestore snapshot deserialization utilities.
 */
object FirebaseProvider {

    private const val DEFAULT_STORAGE_BUCKET = "smart-school-library-bd.firebasestorage.app"

    fun ensureInitialized(context: Context): FirebaseApp {
        val appContext = context.applicationContext ?: context
        val existing = FirebaseApp.getApps(appContext)
        if (existing.isNotEmpty()) {
            return FirebaseApp.getInstance()
        }
        val initialized = FirebaseApp.initializeApp(appContext)
        if (initialized != null) {
            return initialized
        }
        // Fallback only for headless JVM / Robolectric environments lacking google-services resources
        return FirebaseApp.initializeApp(
            appContext,
            FirebaseOptions.Builder()
                .setApplicationId("1:747711256943:android:743e39aefbce707f98309a")
                .setProjectId("smart-school-library-bd")
                .setStorageBucket(DEFAULT_STORAGE_BUCKET)
                .setApiKey("AIzaSyDummyFallbackForLocalUnitTestsOnly")
                .build()
        )
    }

    fun getFirestore(context: Context): FirebaseFirestore {
        val appContext = context.applicationContext ?: context
        val app = ensureInitialized(appContext)
        val dbId = runCatching {
            appContext.getString(R.string.firestore_database_id).trim()
        }.getOrDefault("(default)")
        return if (dbId.isBlank() || dbId == "(default)") {
            FirebaseFirestore.getInstance(app)
        } else {
            FirebaseFirestore.getInstance(app, dbId)
        }
    }

    fun getAuth(context: Context): FirebaseAuth {
        val appContext = context.applicationContext ?: context
        val app = ensureInitialized(appContext)
        return FirebaseAuth.getInstance(app)
    }

    fun getStorage(context: Context): FirebaseStorage {
        val appContext = context.applicationContext ?: context
        val app = ensureInitialized(appContext)
        val bucket = app.options.storageBucket?.trim()?.removePrefix("gs://").orEmpty()
            .ifBlank { DEFAULT_STORAGE_BUCKET }
        return FirebaseStorage.getInstance(app, "gs://$bucket")
    }
}

interface FirestoreIdentifiable<T> {
    fun withDocumentId(docId: String): T
}

fun <T : FirestoreIdentifiable<T>> DocumentSnapshot.toSafeModel(clazz: Class<T>): T? {
    if (!exists()) return null
    return try {
        toObject(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.withDocumentId(id)
    } catch (e: Exception) {
        Log.e("FirestoreMapper", "Failed to map document ${reference.path} to ${clazz.simpleName}: ${e.message}", e)
        null
    }
}

fun <T : FirestoreIdentifiable<T>> QuerySnapshot.toSafeModels(clazz: Class<T>): List<T> {
    return documents.mapNotNull { doc ->
        doc.toSafeModel(clazz)
    }
}
