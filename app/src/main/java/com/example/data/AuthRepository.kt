package com.example.data

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.firebase.FirebaseProvider
import com.example.firebase.OperationType
import com.example.firebase.SecurityConstants
import com.example.firebase.handleFirestoreError
import com.example.firebase.toSafeModel
import com.example.model.UserProfile
import com.example.ui.i18n.AppLanguage
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AuthRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseProvider.getFirestore(context),
        auth = FirebaseProvider.getAuth(context)
    )

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private data class PendingRegistrationProfile(
        val name: String,
        val selectedClass: Int,
        val selectedStream: String
    )

    private val profileMutex = Mutex()
    private val pendingRegistrationsByEmail = ConcurrentHashMap<String, PendingRegistrationProfile>()

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toSafeModel(UserProfile::class.java)
            }
            .catch { error ->
                if (error is CancellationException) throw error
                if (error is Exception && auth.currentUser != null) {
                    handleFirestoreError(error, OperationType.GET, path)
                }
                throw error
            }
    }

    suspend fun ensureUserProfile(
        firebaseUser: FirebaseUser,
        fallbackName: String = "",
        initialClass: Int = 5,
        initialStream: String = "NONE"
    ): Result<UserProfile> {
        val uid = firebaseUser.uid
        val docRef = db.collection("users").document(uid)
        val normalizedEmail = firebaseUser.email?.trim()?.lowercase().orEmpty()
        return try {
            profileMutex.withLock {
                val pending = pendingRegistrationsByEmail[normalizedEmail]
                val effectiveFallbackName = fallbackName.trim().ifBlank { pending?.name.orEmpty() }
                val effectiveClass = (if (fallbackName.isNotBlank() || initialClass != 5 || initialStream != "NONE") initialClass else (pending?.selectedClass ?: initialClass)).coerceIn(1, 30)
                val effectiveStreamRaw = if (initialStream != "NONE") initialStream else (pending?.selectedStream ?: initialStream)
                val effectiveStream = if (effectiveClass in 9..10 && effectiveStreamRaw in listOf("SCIENCE", "HUMANITIES", "BUSINESS")) effectiveStreamRaw else "NONE"

                val existingSnap = docRef.get().await()
                if (existingSnap.exists()) {
                    val profile = existingSnap.toSafeModel(UserProfile::class.java)
                        ?: UserProfile(id = uid, userId = uid)
                    Result.success(profile)
                } else {
                    val resolvedRole = if (SecurityConstants.isTrustedSuperAdmin(uid)) "super_admin" else "student"
                    val displayName = effectiveFallbackName.ifBlank {
                        firebaseUser.displayName?.trim().takeUnless { it.isNullOrBlank() }
                            ?: firebaseUser.email?.substringBefore("@")
                            ?: "শিক্ষার্থী"
                    }
                    val newProfile = UserProfile(
                        id = uid,
                        userId = uid,
                        name = displayName.take(120),
                        email = (firebaseUser.email ?: "").take(200),
                        role = resolvedRole,
                        selectedClass = effectiveClass,
                        selectedStream = effectiveStream,
                        photoUrl = firebaseUser.photoUrl?.toString()?.take(500),
                        isActive = true,
                        accountDeletionRequested = false
                    )
                    docRef.set(newProfile.toCreateMap()).await()
                    Result.success(newProfile)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (auth.currentUser != null) {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
            }
            Result.failure(e)
        }
    }

    suspend fun updateOwnProfile(
        name: String,
        selectedClass: Int,
        selectedStream: String,
        accountDeletionRequested: Boolean? = null
    ): Result<Unit> {
        val currentUser = auth.currentUser
            ?: return Result.failure(IllegalStateException("লগইন করা নেই"))
        val uid = currentUser.uid
        val docRef = db.collection("users").document(uid)
        return try {
            val cleanName = name.trim().take(120).ifBlank {
                currentUser.displayName?.trim().takeUnless { it.isNullOrBlank() } ?: "শিক্ষার্থী"
            }
            val cleanStream = if (selectedClass in 9..10 && selectedStream in listOf("SCIENCE", "HUMANITIES", "BUSINESS")) {
                selectedStream
            } else {
                "NONE"
            }

            // Sync FirebaseAuth displayName
            runCatching {
                val profileChange = UserProfileChangeRequest.Builder()
                    .setDisplayName(cleanName)
                    .build()
                currentUser.updateProfile(profileChange).await()
            }

            val snap = docRef.get().await()
            if (!snap.exists()) {
                ensureUserProfile(
                    firebaseUser = currentUser,
                    fallbackName = cleanName,
                    initialClass = selectedClass.coerceIn(1, 30),
                    initialStream = cleanStream
                ).getOrThrow()
                if (accountDeletionRequested != null && accountDeletionRequested) {
                    docRef.update(
                        mapOf(
                            "accountDeletionRequested" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                return Result.success(Unit)
            }
            val updates = mutableMapOf<String, Any>(
                "name" to cleanName,
                "selectedClass" to selectedClass.coerceIn(1, 30),
                "selectedStream" to cleanStream,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (accountDeletionRequested != null) {
                updates["accountDeletionRequested"] = accountDeletionRequested
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(
        name: String,
        email: String,
        password: String,
        selectedClass: Int,
        selectedStream: String
    ): Result<FirebaseUser> {
        val cleanEmail = email.trim()
        val emailKey = cleanEmail.lowercase()
        val cleanName = name.trim().take(120)
        val cleanClass = selectedClass.coerceIn(1, 30)
        val cleanStream = if (cleanClass in 9..10 && selectedStream in listOf("SCIENCE", "HUMANITIES", "BUSINESS")) {
            selectedStream
        } else {
            "NONE"
        }
        pendingRegistrationsByEmail[emailKey] = PendingRegistrationProfile(
            name = cleanName,
            selectedClass = cleanClass,
            selectedStream = cleanStream
        )
        return try {
            val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
            val user = result.user ?: error("User creation returned null")
            try {
                val req = UserProfileChangeRequest.Builder()
                    .setDisplayName(cleanName)
                    .build()
                user.updateProfile(req).await()
            } catch (_: Exception) {
                // Non-fatal if Auth display name update fails; Firestore profile is authoritative
            }
            val ensured = ensureUserProfile(
                firebaseUser = user,
                fallbackName = cleanName,
                initialClass = cleanClass,
                initialStream = cleanStream
            ).getOrThrow()
            if (ensured.name != cleanName ||
                ensured.selectedClass != cleanClass ||
                ensured.selectedStream != cleanStream
            ) {
                db.collection("users").document(user.uid).update(
                    mapOf(
                        "name" to cleanName.ifBlank { ensured.name },
                        "selectedClass" to cleanClass,
                        "selectedStream" to cleanStream,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            pendingRegistrationsByEmail.remove(emailKey)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val credential = EmailAuthProvider.getCredential(email.trim(), password)
            val result = auth.signInWithCredential(credential).await()
            val user = result.user ?: error("Sign-in returned null user")
            ensureUserProfile(firebaseUser = user).getOrThrow()
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Silent auto sign-in using GetGoogleIdOption (single option in GetCredentialRequest).
     */
    suspend fun attemptSilentGoogleSignIn(context: Context): Result<FirebaseUser?> {
        if (auth.currentUser != null) return Result.success(auth.currentUser)
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user
                if (user != null) {
                    ensureUserProfile(user)
                }
                Result.success(user)
            } else {
                Result.success(null)
            }
        } catch (_: NoCredentialException) {
            Result.success(null)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.success(null)
        }
    }

    /**
     * Interactive Google Sign-In using GetSignInWithGoogleOption (single option in GetCredentialRequest).
     */
    suspend fun signInWithGoogleInteractive(
        activityContext: Activity,
        onCancelled: () -> Unit = {}
    ): Result<FirebaseUser?> {
        val clientId = try {
            activityContext.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: error("Google sign-in returned null user")
                ensureUserProfile(user).getOrThrow()
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Unexpected credential type"))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            onCancelled()
            Result.success(null)
        } catch (e: NoCredentialException) {
            Log.w("Auth", "No Google credentials available on device: ${e.message}", e)
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            try {
                val credentialManager = CredentialManager.create(context)
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear CredentialManager state", e)
            } finally {
                auth.signOut()
            }
        }
    }

    companion object {
        fun translateAuthError(throwable: Throwable, lang: AppLanguage): String {
            val code = (throwable as? FirebaseAuthException)?.errorCode.orEmpty()
            val msg = throwable.message.orEmpty()
            return if (lang == AppLanguage.BN) {
                when {
                    throwable is NoCredentialException ->
                        "ডিভাইসে কোনো Google অ্যাকাউন্ট পাওয়া যায়নি। ডিভাইসে Google অ্যাকাউন্ট যোগ করুন অথবা ইমেইল ও পাসওয়ার্ড দিয়ে লগইন করুন।"
                    code == "ERROR_INVALID_EMAIL" || msg.contains("badly formatted", true) ->
                        "সঠিক ইমেইল ঠিকানা প্রদান করুন।"
                    code == "ERROR_WRONG_PASSWORD" || code == "ERROR_INVALID_CREDENTIAL" || msg.contains("INVALID_LOGIN_CREDENTIALS", true) ->
                        "ইমেইল অথবা পাসওয়ার্ড সঠিক নয়। অনুগ্রহ করে যাচাই করুন।"
                    code == "ERROR_USER_NOT_FOUND" ->
                        "এই ইমেইলে কোনো অ্যাকাউন্ট পাওয়া যায়নি। প্রথমে নিবন্ধন করুন।"
                    code == "ERROR_EMAIL_ALREADY_IN_USE" ->
                        "এই ইমেইল দিয়ে ইতিমধ্যে একটি অ্যাকাউন্ট খোলা হয়েছে।"
                    code == "ERROR_WEAK_PASSWORD" ->
                        "পাসওয়ার্ড অন্তত ৬ অক্ষরের হতে হবে।"
                    code == "ERROR_USER_DISABLED" ->
                        "আপনার অ্যাকাউন্টটি সাময়িকভাবে নিষ্ক্রিয় করা হয়েছে।"
                    code == "ERROR_OPERATION_NOT_ALLOWED" ->
                        "Firebase Console-এ এই সাইন-ইন পদ্ধতিটি এখনো সক্রিয় করা হয়নি।"
                    msg.contains("network", true) ->
                        "ইন্টারনেট সংযোগ বিচ্ছিন্ন। সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।"
                    else -> "প্রমাণীকরণ সম্পন্ন হয়নি: ${throwable.localizedMessage ?: "অজানা ত্রুটি"}"
                }
            } else {
                when {
                    throwable is NoCredentialException ->
                        "No Google account found on this device. Add a Google account in Settings or sign in with email."
                    code == "ERROR_INVALID_EMAIL" || msg.contains("badly formatted", true) ->
                        "Please enter a valid email address."
                    code == "ERROR_WRONG_PASSWORD" || code == "ERROR_INVALID_CREDENTIAL" || msg.contains("INVALID_LOGIN_CREDENTIALS", true) ->
                        "Incorrect email or password."
                    code == "ERROR_USER_NOT_FOUND" ->
                        "No account found with this email. Please register first."
                    code == "ERROR_EMAIL_ALREADY_IN_USE" ->
                        "An account already exists with this email address."
                    code == "ERROR_WEAK_PASSWORD" ->
                        "Password must be at least 6 characters long."
                    code == "ERROR_USER_DISABLED" ->
                        "Your account has been deactivated by the administrator."
                    code == "ERROR_OPERATION_NOT_ALLOWED" ->
                        "This sign-in method is not yet enabled in Firebase Console."
                    else -> throwable.localizedMessage ?: "Authentication failed."
                }
            }
        }
    }
}
