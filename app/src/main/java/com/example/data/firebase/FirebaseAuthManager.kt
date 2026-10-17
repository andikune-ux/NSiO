package com.example.data.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

sealed interface AuthState {
    data object Loading : AuthState
    data object Unauthenticated : AuthState
    data class Authenticated(val user: FirebaseUser) : AuthState
    data class Error(val message: String) : AuthState
}

class FirebaseAuthManager(private val context: Context) {

    private val auth: FirebaseAuth = Firebase.auth
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    suspend fun ensureStorageUser(): FirebaseUser {
        auth.currentUser?.let { return it }
        val result = auth.signInAnonymously().await()
        return result.user ?: throw IllegalStateException("Sesi penyimpanan anonim tidak tersedia")
    }

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            _authState.value = if (user != null) {
                AuthState.Authenticated(user)
            } else {
                AuthState.Unauthenticated
            }
        }
    }

    /**
     * Authenticates the user via Google Sign-In using Jetpack CredentialManager.
     */
    suspend fun signInWithGoogle(): Result<FirebaseUser> {
        _authState.value = AuthState.Loading
        return try {
            val webClientId = context.getString(R.string.default_web_client_id)
            val googleIdOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw IllegalStateException("FirebaseUser is null after sign-in")
                _authState.value = AuthState.Authenticated(user)
                Result.success(user)
            } else {
                val err = "Tipe kredensial tidak didukung"
                _authState.value = AuthState.Error(err)
                Result.failure(Exception(err))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Google Sign-In dibatalkan oleh pengguna")
            _authState.value = if (auth.currentUser != null) {
                AuthState.Authenticated(auth.currentUser!!)
            } else {
                AuthState.Unauthenticated
            }
            Result.failure(e)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Gagal mendapatkan kredensial: ${e.message}", e)
            val msg = e.localizedMessage ?: "Gagal terhubung ke Google"
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(TAG, "Autentikasi Firebase gagal: ${e.message}", e)
            val msg = e.localizedMessage ?: "Autentikasi gagal"
            _authState.value = AuthState.Error(msg)
            Result.failure(e)
        }
    }

    fun signOut() {
        try {
            auth.signOut()
            _authState.value = AuthState.Unauthenticated
        } catch (e: Exception) {
            Log.e(TAG, "Gagal sign out: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "FirebaseAuthManager"
    }
}
