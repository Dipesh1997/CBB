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

    fun forceAccountLink(email: String) {
        _userEmail.value = email
        _userName.value = email.split("@")[0]
        settings.saveUserEmail(email)
        settings.saveUserName(_userName.value)
        Log.d("GoogleAuth", "Silent account linked: $email")
    }

    suspend fun signIn(activityContext: Context): Boolean {
        val activity = activityContext.findActivity() ?: run {
            Log.e("GoogleAuth", "Context is not an Activity")
            return false
        }

        val googleIdOption: GetGoogleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("812006416646-cd28a14enlpg87ktbeim0l02m6f965q9.apps.googleusercontent.com")
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
                    settings.saveUserEmail(credential.id)
                    settings.saveUserName(credential.displayName)
                    true
                }
                else -> {
                    // Try to extract from data if it's a custom credential but carries Google ID info
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        Log.d("GoogleAuth", "Sign-in Success (via data): ${googleIdTokenCredential.id}")
                        _userEmail.value = googleIdTokenCredential.id
                        _userName.value = googleIdTokenCredential.displayName
                        settings.saveUserEmail(googleIdTokenCredential.id)
                        settings.saveUserName(googleIdTokenCredential.displayName)
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
                is androidx.credentials.exceptions.GetCredentialCancellationException -> "Sign-in cancelled"
                is androidx.credentials.exceptions.GetCredentialInterruptedException -> "Sign-in interrupted"
                is androidx.credentials.exceptions.GetCredentialProviderConfigurationException -> "Configuration error (Check Client ID/SHA-1)"
                else -> {
                    if (e.type == "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL") {
                        "No Google accounts found. Please sign in to Google in your phone settings."
                    } else {
                        "Sign-in failed: ${e.message}"
                    }
                }
            }
            android.widget.Toast.makeText(activityContext, errorMsg, android.widget.Toast.LENGTH_LONG).show()
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
