package com.example.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

    protected lateinit var firestore: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var databaseId: String

    @Before
    open fun setUpFirebase() {
        val emulatorOnline = try {
            java.net.Socket().use { s ->
                s.connect(java.net.InetSocketAddress(EMULATOR_HOST, FIRESTORE_PORT), 200)
                true
            }
        } catch (_: Exception) {
            false
        }
        org.junit.Assume.assumeTrue(
            "Firestore emulator is not running at $EMULATOR_HOST:$FIRESTORE_PORT; skipping emulator tests in headless JVM",
            emulatorOnline
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        databaseId = if (resId != 0) context.getString(resId) else "(default)"

        val existingApp = FirebaseApp.getApps(context).firstOrNull { it.name == EMULATOR_APP_NAME }
        val app = if (existingApp == null) {
            val newApp = FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.SmartSchoolLibraryBD")
                    .setProjectId(PROJECT_ID)
                    .setApiKey("fake-api-key-for-emulator")
                    .build(),
                EMULATOR_APP_NAME
            )
            val newFirestore = if (databaseId.isBlank() || databaseId == "(default)") {
                FirebaseFirestore.getInstance(newApp)
            } else {
                FirebaseFirestore.getInstance(newApp, databaseId)
            }
            newFirestore.useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
            newFirestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()

            val newAuth = FirebaseAuth.getInstance(newApp)
            newAuth.useEmulator(EMULATOR_HOST, AUTH_PORT)
            newApp
        } else {
            existingApp
        }

        firestore = if (databaseId.isBlank() || databaseId == "(default)") {
            FirebaseFirestore.getInstance(app)
        } else {
            FirebaseFirestore.getInstance(app, databaseId)
        }
        auth = FirebaseAuth.getInstance(app)
    }

    @After
    open fun tearDownFirebase() {
        if (::auth.isInitialized) {
            auth.signOut()
        }
    }

    protected suspend fun signInTestUser(email: String): String = withContext(Dispatchers.IO) {
        withTimeout(AUTH_TIMEOUT_MS) {
            val result = try {
                auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
            } catch (_: FirebaseAuthInvalidUserException) {
                auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
            } catch (_: Exception) {
                auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
            }
            checkNotNull(result.user?.uid) { "User auth failed" }
        }
    }

    protected suspend fun signInSuperAdmin(): String = withContext(Dispatchers.IO) {
        withTimeout(AUTH_TIMEOUT_MS) {
            val adminUid = com.example.firebase.SecurityConstants.TRUSTED_SUPER_ADMIN_UID
            val adminEmail = "superadmin_emulator@smartschoolbd.test"
            val endpoint = java.net.URI(
                "http://$EMULATOR_HOST:$AUTH_PORT/identitytoolkit.googleapis.com/v1/projects/$PROJECT_ID/accounts"
            ).toURL()
            val conn = (endpoint.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer owner")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            try {
                val payload = """{"localId":"$adminUid","email":"$adminEmail","password":"$DEFAULT_PASSWORD","emailVerified":true}"""
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    check(err.contains("EMAIL_EXISTS") || err.contains("DUPLICATE_LOCAL_ID")) {
                        "Failed to seed Super Admin in Auth Emulator: HTTP $code $err"
                    }
                }
            } finally {
                conn.disconnect()
            }
            val signInRes = auth.signInWithEmailAndPassword(adminEmail, DEFAULT_PASSWORD).await()
            check(signInRes.user?.uid == adminUid) {
                "Expected Super Admin UID $adminUid, got ${signInRes.user?.uid}"
            }
            adminUid
        }
    }

    protected suspend fun seedEmulatorDocumentAsAdmin(
        collection: String,
        documentId: String,
        fieldsJson: String
    ) = withContext(Dispatchers.IO) {
        val dbSegment = if (databaseId.isBlank()) "(default)" else databaseId
        val endpoint = java.net.URI(
            "http://$EMULATOR_HOST:$FIRESTORE_PORT/v1/projects/$PROJECT_ID/databases/$dbSegment/documents/$collection?documentId=$documentId"
        ).toURL()
        val conn = (endpoint.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer owner")
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            conn.outputStream.use { out ->
                out.write(fieldsJson.toByteArray(Charsets.UTF_8))
            }
            val status = conn.responseCode
            check(status in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                "Failed to seed emulator document ($collection/$documentId): HTTP $status $err"
            }
        } finally {
            conn.disconnect()
        }
    }

    protected fun Throwable.unwrapFirestoreException(): FirebaseFirestoreException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is FirebaseFirestoreException) return current
            current = current.cause
        }
        return null
    }

    private companion object {
        const val EMULATOR_APP_NAME = "emulator-test-app"
        const val EMULATOR_HOST = "127.0.0.1"
        const val FIRESTORE_PORT = 8085
        const val AUTH_PORT = 9099
        const val PROJECT_ID = "demo-no-project"
        const val DEFAULT_PASSWORD = "password123"
        const val AUTH_TIMEOUT_MS = 5000L
    }
}
