package g.p.cbb.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import dagger.hilt.android.qualifiers.ApplicationContext
import g.p.cbb.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) {
    private val credentialManager = CredentialManager.create(context)
    
    private val _userEmail = MutableStateFlow<String?>(settings.getUserEmail())
    val userEmail = _userEmail.asStateFlow()

    private val _userName = MutableStateFlow<String?>(settings.getUserName())
    val userName = _userName.asStateFlow()

    private val _userProfilePic = MutableStateFlow<String?>(settings.getUserProfilePic())
    val userProfilePic = _userProfilePic.asStateFlow()

    private val _accessToken = MutableStateFlow<String?>(settings.getAccessToken())
    val accessToken = _accessToken.asStateFlow()

    fun isUserSignedIn(): Boolean = _userEmail.value != null

    fun saveOAuthAccessToken(token: String, email: String? = null) {
        _accessToken.value = token
        settings.saveAccessToken(token)
        if (!email.isNullOrEmpty()) {
            forceAccountLink(email)
        }
    }

    fun forceAccountLink(email: String, photoUrl: String? = null) {
        _userEmail.value = email
        val name = email.split("@")[0]
        _userName.value = name
        settings.saveUserEmail(email)
        settings.saveUserName(name)
        if (photoUrl != null) {
            _userProfilePic.value = photoUrl
            settings.saveUserProfilePic(photoUrl)
        }
        Log.d("GoogleAuth", "Silent account linked: $email, photo: $photoUrl")
    }

    suspend fun signIn(activityContext: Context): Boolean {
        val activity = activityContext.findActivity() ?: run {
            Log.e("GoogleAuth", "Context is not an Activity")
            return false
        }

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("812006416646-70f535jogrloppco7q8l0c0qoinoq2un.apps.googleusercontent.com")
            .setAutoSelectEnabled(false) // Disable auto-select for testing to force popup
            .build()

        val request: GetCredentialRequest = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        return try {
            val result = credentialManager.getCredential(
                context = activity,
                request = request
            )
            val credential = result.credential
            Log.d("GoogleAuth", "Credential Type: ${credential.type}")
            
            when (credential) {
                is GoogleIdTokenCredential -> {
                    Log.d("GoogleAuth", "Sign-in Success: ${credential.id}")
                    _userEmail.value = credential.id
                    _userName.value = credential.displayName
                    val photoUrl = credential.profilePictureUri?.toString() ?: FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
                    _userProfilePic.value = photoUrl
                    settings.saveUserEmail(credential.id)
                    settings.saveUserName(credential.displayName)
                    settings.saveUserProfilePic(photoUrl)
                    try {
                        val firebaseCred = GoogleAuthProvider.getCredential(credential.idToken, null)
                        FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
                        Log.d("GoogleAuth", "Firebase Auth successful for ${credential.id}")
                    } catch (e: Exception) {
                        Log.w("GoogleAuth", "Firebase auth fallback: ${e.message}")
                    }
                    true
                }
                else -> {
                    // Try to extract from data if it's a custom credential but carries Google ID info
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        Log.d("GoogleAuth", "Sign-in Success (via data): ${googleIdTokenCredential.id}")
                        _userEmail.value = googleIdTokenCredential.id
                        _userName.value = googleIdTokenCredential.displayName
                        val photoUrl = googleIdTokenCredential.profilePictureUri?.toString() ?: FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
                        _userProfilePic.value = photoUrl
                        settings.saveUserEmail(googleIdTokenCredential.id)
                        settings.saveUserName(googleIdTokenCredential.displayName)
                        settings.saveUserProfilePic(photoUrl)
                        try {
                            val firebaseCred = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                            FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
                            Log.d("GoogleAuth", "Firebase Auth successful for ${googleIdTokenCredential.id}")
                        } catch (e: Exception) {
                            Log.w("GoogleAuth", "Firebase auth fallback: ${e.message}")
                        }
                        true
                    } catch (e: Exception) {
                        Log.e("GoogleAuth", "Unexpected credential type: ${credential.type}")
                        android.widget.Toast.makeText(activityContext, "Sign-in failed: Unexpected type", android.widget.Toast.LENGTH_SHORT).show()
                        false
                    }
                }
            }
        } catch (e: androidx.credentials.exceptions.GetCredentialException) {
            Log.e("GoogleAuth", "Sign-in Credential Error: [${e.type}] ${e.message}")
            val errorMsg = when (e) {
                is androidx.credentials.exceptions.GetCredentialCancellationException -> "Sign-in cancelled. Select an account manually to sync."
                is androidx.credentials.exceptions.GetCredentialInterruptedException -> "Sign-in interrupted."
                is androidx.credentials.exceptions.GetCredentialProviderConfigurationException -> "Google One-Tap SHA-1 check skipped. Select your account below to link."
                else -> {
                    if (e.type == "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL") {
                        "No saved credentials. Please select your Google account below."
                    } else {
                        "Opening Google Account Picker..."
                    }
                }
            }
            android.widget.Toast.makeText(activityContext, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            Log.e("GoogleAuth", "Sign-in Unexpected Error: ${e.message}")
            android.widget.Toast.makeText(activityContext, "Sign-in Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
            false
        }
    }

    private fun Context.findActivity(): Activity? {
        var currentContext = this
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) return currentContext
            currentContext = currentContext.baseContext
        }
        return null
    }

    suspend fun signOut() {
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
        _userEmail.value = null
        _userName.value = null
        settings.saveUserEmail(null)
        settings.saveUserName(null)
    }
}
