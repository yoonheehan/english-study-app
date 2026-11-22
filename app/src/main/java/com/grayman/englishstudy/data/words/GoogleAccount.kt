package com.grayman.englishstudy.data.words

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/**
 * Connects this device to the learner's Google account, so the sync UID stays the same after the
 * app is reinstalled, and is the same on the phone and the tablet.
 *
 * An anonymous account is linked rather than replaced, so the first device keeps its UID (already
 * allowed in the Firestore rules). A device whose Google account is already linked elsewhere
 * signs in to that account instead and takes over its UID.
 */
class GoogleAccount(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {
    sealed interface Result {
        /** This device's anonymous account now belongs to the Google account; the UID did not change. */
        data object Linked : Result

        /** Signed in to the Google account another device linked first; the UID changed to that one. */
        data object Switched : Result

        data object Cancelled : Result

        data class Failed(val message: String) : Result
    }

    private val _email = MutableStateFlow(currentEmail())

    /** The connected Google account, or null while signed in anonymously. */
    val email: StateFlow<String?> = _email

    fun refresh() {
        _email.value = currentEmail()
    }

    private fun currentEmail(): String? = auth.currentUser
        ?.providerData
        ?.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        ?.email

    /** Shows the account picker. [activity] must be an Activity context, for the picker's UI. */
    suspend fun connect(activity: Context): Result = connect(activity, silent = false)

    /**
     * Signs in without asking, when this Google account already allowed this app (for example
     * before the app was reinstalled). [Result.Cancelled] when that is not possible; then the
     * learner is asked with [connect].
     */
    suspend fun connectSilently(activity: Context): Result = connect(activity, silent = true)

    private suspend fun connect(activity: Context, silent: Boolean): Result {
        val clientId = webClientId(activity) ?: return Result.Failed(
            "Firebase에서 Google 로그인이 아직 꺼져 있어요. Authentication에서 Google을 켜고, 새 google-services.json을 넣어 다시 빌드해 주세요.",
        )
        val idToken = try {
            val option = if (silent) {
                GetGoogleIdOption.Builder()
                    .setServerClientId(clientId)
                    .setFilterByAuthorizedAccounts(true)
                    .setAutoSelectEnabled(true)
                    .build()
            } else {
                GetSignInWithGoogleOption.Builder(clientId).build()
            }
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return Result.Failed("구글 계정 정보를 받지 못했어요.")
            }
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GetCredentialCancellationException) {
            return Result.Cancelled
        } catch (e: NoCredentialException) {
            return if (silent) Result.Cancelled else Result.Failed("이 기기에 구글 계정이 없어요. 설정에서 구글 계정을 추가해 주세요.")
        } catch (e: GetCredentialException) {
            if (silent) return Result.Cancelled
            Log.w(TAG, "account picker failed", e)
            return Result.Failed(e.message ?: "구글 계정을 고르지 못했어요.")
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        return try {
            val current = auth.currentUser
            val result = if (current != null && current.isAnonymous) {
                try {
                    current.linkWithCredential(credential).await()
                    Result.Linked
                } catch (e: FirebaseAuthUserCollisionException) {
                    // Another device linked this Google account first: use that account's UID.
                    auth.signInWithCredential(e.updatedCredential ?: credential).await()
                    Result.Switched
                }
            } else {
                auth.signInWithCredential(credential).await()
                Result.Switched
            }
            // A fresh token carries the account's email, which the Firestore rules check.
            auth.currentUser?.getIdToken(true)?.await()
            refresh()
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "firebase sign-in failed", e)
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * The OAuth client the Google ID token is issued for. The google-services plugin generates
     * this string only once Google sign-in is turned on in Firebase, so it is looked up by name
     * and the app still builds without it.
     */
    private fun webClientId(context: Context): String? {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id == 0) null else context.getString(id).takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "GoogleAccount"
    }
}
