package com.example.security

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class FirebaseAuthUiState(
    val isSignedIn: Boolean = false,
    val uid: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val photoUrl: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class FirebaseAuthRequiredException(
    message: String = "Sign in with Google to use cloud Gemini AI Auto Captions, or tap Offline AI for 100% local captions."
) : IllegalStateException(message)

/**
 * Manages Firebase Authentication using Google Sign-In via Jetpack Credential Manager.
 *
 * Strictly enforces:
 * - Exclusive Google Sign-In (`GetSignInWithGoogleOption` for interactive sign-in,
 *   `GetGoogleIdOption` for silent startup auto sign-in; exactly one credential option per request).
 * - Web Client ID resolved exclusively via `R.string.default_web_client_id`.
 * - Non-blocking offline-first design: local editing, offline auto-captions, TTS, cutout, and 4K/60fps exports
 *   never require an account; Firebase Auth gates only the cloud built-in Gemini AI auto-captioning services.
 */
class FirebaseAuthManager private constructor(private val appContext: Context) {

    private val _authState = MutableStateFlow(currentSnapshot())
    val authState: StateFlow<FirebaseAuthUiState> = _authState.asStateFlow()

    init {
        runCatching {
            Firebase.auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                _authState.update { prev ->
                    prev.copy(
                        isSignedIn = user != null,
                        uid = user?.uid,
                        displayName = user?.displayName,
                        email = user?.email,
                        photoUrl = user?.photoUrl?.toString()
                    )
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "FirebaseAuth listener registration deferred: ${err.message}")
        }
    }

    private fun currentSnapshot(): FirebaseAuthUiState {
        val user = runCatching { Firebase.auth.currentUser }.getOrNull()
        return FirebaseAuthUiState(
            isSignedIn = user != null,
            uid = user?.uid,
            displayName = user?.displayName,
            email = user?.email,
            photoUrl = user?.photoUrl?.toString()
        )
    }

    fun isUserSignedIn(): Boolean = runCatching { Firebase.auth.currentUser != null }.getOrDefault(false)

    /**
     * Attempts a silent Google Sign-In on startup using `GetGoogleIdOption` as the sole option
     * in the `GetCredentialRequest`. Never surfaces UI errors when no authorized account exists.
     */
    fun attemptSilentAutoSignIn(activity: Activity, scope: CoroutineScope) {
        if (isUserSignedIn()) return
        scope.launch {
            try {
                val webClientId = activity.getString(R.string.default_web_client_id)
                if (webClientId.isBlank()) return@launch

                val credentialManager = CredentialManager.create(activity)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(true)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val response = credentialManager.getCredential(activity, request)
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                    val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
                    updateSignedInUser(authResult.user, "Signed in automatically")
                }
            } catch (_: NoCredentialException) {
                // Normal when no previously authorized Google account is cached
            } catch (_: GetCredentialCancellationException) {
                // Silent check cancelled; ignore quietly
            } catch (e: Exception) {
                Log.w(TAG, "Silent Google auto sign-in skipped: ${e.message}")
            }
        }
    }

    /**
     * Interactive Google Sign-In button flow using `GetSignInWithGoogleOption` as the sole
     * credential option per API 36 Credential Manager requirements.
     */
    fun signInWithGoogle(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: ((FirebaseUser) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        scope.launch {
            _authState.update { it.copy(isLoading = true, errorMessage = null, statusMessage = null) }
            try {
                val webClientId = activity.getString(R.string.default_web_client_id).trim()
                if (webClientId.isBlank()) {
                    val msg = "Google Sign-In is not configured in this APK (missing google-services.json). Use Offline AI or AI Login with an API key."
                    _authState.update { it.copy(isLoading = false, errorMessage = msg) }
                    onError?.invoke(msg)
                    return@launch
                }
                val credentialManager = CredentialManager.create(activity)
                val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = webClientId)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()

                val response = credentialManager.getCredential(activity, request)
                val credential = response.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
                    val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
                    val authResult = Firebase.auth.signInWithCredential(firebaseCred).await()
                    val user = authResult.user
                    if (user != null) {
                        updateSignedInUser(user, "Signed in as ${user.email ?: user.displayName ?: "Google User"}")
                        onSuccess?.invoke(user)
                    } else {
                        val msg = "Google Sign-In completed without a Firebase user session."
                        _authState.update { it.copy(isLoading = false, errorMessage = msg) }
                        onError?.invoke(msg)
                    }
                } else {
                    val msg = "Unsupported credential type returned by Google Play services."
                    _authState.update { it.copy(isLoading = false, errorMessage = msg) }
                    onError?.invoke(msg)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-In was cancelled by user or credential provider: ${e.message}")
                val msg = "Sign-in was cancelled. Select a Google account to enable Cloud Gemini AI."
                _authState.update { it.copy(isLoading = false, errorMessage = msg) }
                onError?.invoke(msg)
            } catch (e: GetCredentialException) {
                Log.w(TAG, "Google Credential Manager error: ${e.type} - ${e.message}")
                val msg = e.localizedMessage ?: "Unable to launch Google Sign-In on this device."
                _authState.update { it.copy(isLoading = false, errorMessage = msg) }
                onError?.invoke(msg)
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Google Sign-In failed: ${e.message}")
                val msg = e.localizedMessage ?: "Google Sign-In failed. Check your internet connection."
                _authState.update { it.copy(isLoading = false, errorMessage = msg) }
                onError?.invoke(msg)
            }
        }
    }

    /**
     * Signs out cleanly from both Jetpack Credential Manager and Firebase Auth inside
     * `withContext(NonCancellable)` so account switching always succeeds cleanly.
     */
    fun signOut(context: Context, scope: CoroutineScope, onComplete: (() -> Unit)? = null) {
        scope.launch {
            _authState.update { it.copy(isLoading = true, errorMessage = null) }
            withContext(NonCancellable) {
                runCatching {
                    CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
                }.onFailure { err ->
                    Log.w(TAG, "CredentialManager clearCredentialState note: ${err.message}")
                }
                runCatching {
                    Firebase.auth.signOut()
                }.onFailure { err ->
                    Log.w(TAG, "Firebase signOut note: ${err.message}")
                }
            }
            _authState.value = FirebaseAuthUiState(
                isSignedIn = false,
                isLoading = false,
                statusMessage = "Signed out of Google Cloud AI"
            )
            onComplete?.invoke()
        }
    }

    fun clearFeedback() {
        _authState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    private fun updateSignedInUser(user: FirebaseUser?, status: String) {
        _authState.value = FirebaseAuthUiState(
            isSignedIn = user != null,
            uid = user?.uid,
            displayName = user?.displayName,
            email = user?.email,
            photoUrl = user?.photoUrl?.toString(),
            isLoading = false,
            statusMessage = status,
            errorMessage = null
        )
    }

    companion object {
        private const val TAG = "Auth"

        @Volatile
        private var instance: FirebaseAuthManager? = null

        fun getInstance(context: Context): FirebaseAuthManager =
            instance ?: synchronized(this) {
                instance ?: FirebaseAuthManager(context.applicationContext).also { instance = it }
            }

        fun isSignedIn(): Boolean =
            runCatching { Firebase.auth.currentUser != null }.getOrDefault(false)
    }
}
