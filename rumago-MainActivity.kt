
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import org.json.JSONObject
import java.util.HashMap

class MainActivity : Activity() {

    private lateinit var webView: WebView

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
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

    private fun sendAuthResult(
        success: Boolean,
        message: String,
        uid: String = "",
        email: String = "",
        profileSaved: Boolean = false
    ) {
        val result = JSONObject()
        result.put("ok", success)
        result.put("uid", uid)
        result.put("email", email)
        result.put("profileSaved", profileSaved)
        result.put("message", message)

        sendToPage("onRumaGoAuthResult", result)
    }

    inner class AuthBridge {

        @JavascriptInterface
        fun registerCustomer(
            name: String,
            email: String,
            phone: String,
            password: String
        ) {
            val cleanName = name.trim()
            val cleanEmail = email.trim().lowercase()
            val cleanPhone = phone.trim()

            if (cleanName.isEmpty() || cleanEmail.isEmpty() ||
                cleanPhone.isEmpty() || password.length < 6
            ) {
                sendAuthResult(
                    false,
                    "Lengkapi semua data. Password minimal 6 karakter."
                )
                return
            }

            auth.createUserWithEmailAndPassword(cleanEmail, password)
                .addOnCompleteListener { task ->

                    if (!task.isSuccessful) {
                        sendAuthResult(
                            false,
                            task.exception?.localizedMessage
                                ?: "Pendaftaran gagal."
                        )
                        return@addOnCompleteListener
                    }

                    val user = auth.currentUser

                    if (user == null) {
                        sendAuthResult(false, "Akun berhasil dibuat, tetapi UID tidak ditemukan.")
                        return@addOnCompleteListener
                    }

                    val profile = HashMap<String, Any>()
                    profile["uid"] = user.uid
                    profile["name"] = cleanName
                    profile["email"] = cleanEmail
                    profile["phone"] = cleanPhone
                    profile["role"] = "customer"
                    profile["createdAt"] = FieldValue.serverTimestamp()

                    db.collection("users")
                        .document(user.uid)
                        .set(profile)
                        .addOnSuccessListener {
                            sendAuthResult(
                                true,
                                "Pendaftaran berhasil. Selamat datang di RumaGo!",
                                user.uid,
                                cleanEmail,
                                true
                            )
                        }
                        .addOnFailureListener { error ->
                            sendAuthResult(
                                false,
                                "Akun berhasil dibuat, tetapi profil gagal disimpan: " +
                                    (error.localizedMessage ?: "Kesalahan Firestore"),
                                user.uid,
                                cleanEmail,
                                false
                            )
                        }
                }
        }

        // Kompatibilitas sementara dengan formulir lama
        @JavascriptInterface
        fun register(email: String, password: String) {
            val defaultName = email.substringBefore("@").ifBlank { "Customer" }

            registerCustomer(
                defaultName,
                email,
                "Belum diisi",
                password
            )
        }

        @JavascriptInterface
        fun login(email: String, password: String) {
            auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {
                        val user = auth.currentUser

                        sendAuthResult(
                            true,
                            "Login berhasil.",
                            user?.uid ?: "",
                            user?.email ?: "",
                            true
                        )
                    } else {
                        sendAuthResult(
                            false,
                            task.exception?.localizedMessage ?: "Login gagal."
                        )
                    }
                }
        }

        @JavascriptInterface
        fun logout() {
            auth.signOut()

            val result = JSONObject()
            result.put("ok", true)
            result.put("message", "Berhasil keluar.")

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

    private val authBridge = AuthBridge()

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
        webView.settings.allowContentAccess = true

        webView.addJavascriptInterface(authBridge, "RumaGoAuth")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                authBridge.getCurrentUser()
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
