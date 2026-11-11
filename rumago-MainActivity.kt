
package id.rumago.app

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.JavascriptInterface
import android.view.ViewGroup
import android.graphics.Color
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private fun sendToPage(function: String, data: JSONObject) {
        val json = data.toString()
        runOnUiThread {
            webView.evaluateJavascript(
                "window.$function && window.$function($json)",
                null
            )
        }
    }

    inner class AuthBridge {
        @JavascriptInterface
        fun register(email: String, password: String) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    val result = JSONObject()
                    result.put("ok", task.isSuccessful)
                    result.put("uid", auth.currentUser?.uid ?: "")
                    result.put("email", auth.currentUser?.email ?: "")
                    result.put(
                        "message",
                        if (task.isSuccessful) "Pendaftaran berhasil"
                        else task.exception?.localizedMessage ?: "Pendaftaran gagal"
                    )
                    sendToPage("onRumaGoAuthResult", result)
                }
        }

        @JavascriptInterface
        fun login(email: String, password: String) {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    val result = JSONObject()
                    result.put("ok", task.isSuccessful)
                    result.put("uid", auth.currentUser?.uid ?: "")
                    result.put("email", auth.currentUser?.email ?: "")
                    result.put(
                        "message",
                        if (task.isSuccessful) "Login berhasil"
                        else task.exception?.localizedMessage ?: "Login gagal"
                    )
                    sendToPage("onRumaGoAuthResult", result)
                }
        }

        @JavascriptInterface
        fun logout() {
            auth.signOut()
            val result = JSONObject()
            result.put("ok", true)
            result.put("message", "Berhasil keluar")
            sendToPage("onRumaGoAuthResult", result)
        }

        @JavascriptInterface
        fun getCurrentUser() {
            val user = auth.currentUser
            val result = JSONObject()
            result.put("loggedIn", user != null)
            result.put("uid", user?.uid ?: "")
            result.put("email", user?.email ?: "")
            sendToPage("onRumaGoAuthState", result)
        }
    }

    @Suppress("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(13, 104, 69)
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility =
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        webView = WebView(this)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true

        webView.addJavascriptInterface(AuthBridge(), "RumaGoAuth")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                AuthBridge().getCurrentUser()
            }
        }

        webView.webChromeClient = WebChromeClient()
        setContentView(webView)

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/index.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
