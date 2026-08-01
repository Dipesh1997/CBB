package g.p.cbb.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

class GoogleOAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clientId = intent.getStringExtra("CLIENT_ID") ?: "812006416646-cd28a14enlpg87ktbeim0l02m6f965q9.apps.googleusercontent.com"
        val scopes = "https://www.googleapis.com/auth/spreadsheets https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile"
        val redirectUri = "https://developers.google.com/oauthplayground"
        
        val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                "client_id=$clientId&" +
                "redirect_uri=${Uri.encode(redirectUri)}&" +
                "response_type=token&" +
                "scope=${Uri.encode(scopes)}&" +
                "prompt=select_account"

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                @SuppressLint("SetJavaScriptEnabled")
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        if (url != null && url.contains("access_token=")) {
                                            extractTokenAndFinish(url)
                                        }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: ""
                                        if (url.contains("access_token=")) {
                                            extractTokenAndFinish(url)
                                            return true
                                        }
                                        return false
                                    }
                                }
                                loadUrl(authUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    private fun extractTokenAndFinish(url: String) {
        try {
            val tokenPart = url.substringAfter("access_token=", "").substringBefore("&")
            val accessToken = Uri.decode(tokenPart)
            if (accessToken.isNotEmpty()) {
                val resultIntent = Intent().apply {
                    putExtra("ACCESS_TOKEN", accessToken)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
                return
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
