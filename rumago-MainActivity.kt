
package id.rumago.app

import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.GeolocationPermissions
import android.content.pm.PackageManager
import android.graphics.Color
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import androidx.webkit.WebViewAssetLoader
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val LOCATION_REQUEST_CODE = 1001
    private val FILE_CHOOSER_REQUEST_CODE = 1002

    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null

    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null

    private fun hasLocationPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 23) return true

        return checkSelfPermission(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(13, 104, 69)
        window.navigationBarColor = Color.rgb(8, 75, 50)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setGeolocationEnabled(true)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {

                val url = request.url.toString()

                if (
                    url.startsWith("https://www.google.com/maps") ||
                    url.startsWith("https://maps.google.com") ||
                    url.startsWith("geo:") ||
                    url.startsWith("https://wa.me/") ||
                    url.startsWith("https://api.whatsapp.com/") ||
                    url.startsWith("https://www.whatsapp.com/") ||
                    url.startsWith("whatsapp://")
                ) {
                    return try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                        )

                        startActivity(intent)
                        true

                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            this@MainActivity,
                            "Tidak dapat membuka aplikasi tujuan.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()

                        true
                    }
                }

                return false
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback

                    if (android.os.Build.VERSION.SDK_INT >= 23) {
                        requestPermissions(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            ),
                            LOCATION_REQUEST_CODE
                        )
                    } else {
                        callback.invoke(origin, true, false)
                    }
                }
            }

            // PEMILIHAN FOTO POD: KAMERA ATAU GALERI
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {

                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback
                pendingCameraUri = null

                return try {

                    // Folder sementara foto POD
                    val podFolder = File(
                        this@MainActivity.cacheDir,
                        "pod"
                    )

                    if (!podFolder.exists()) {
                        podFolder.mkdirs()
                    }

                    // File foto dari kamera
                    val photoFile = File.createTempFile(
                        "POD_",
                        ".jpg",
                        podFolder
                    )

                    // URI kamera melalui FileProvider
                    pendingCameraUri = FileProvider.getUriForFile(
                        this@MainActivity,
                        "${this@MainActivity.packageName}.fileprovider",
                        photoFile
                    )

                    // Intent kamera
                    val cameraIntent = Intent(
                        android.provider.MediaStore.ACTION_IMAGE_CAPTURE
                    ).apply {
                        putExtra(
                            android.provider.MediaStore.EXTRA_OUTPUT,
                            pendingCameraUri
                        )

                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }

                    // Intent galeri
                    val galleryIntent = Intent(
                        Intent.ACTION_GET_CONTENT
                    ).apply {
                        type = "image/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }

                    // Gabungkan pilihan kamera dan galeri
                    val chooser = Intent.createChooser(
                        galleryIntent,
                        "Pilih Foto Bukti POD"
                    ).apply {
                        putExtra(
                            Intent.EXTRA_INITIAL_INTENTS,
                            arrayOf(cameraIntent)
                        )
                    }

                    this@MainActivity.startActivityForResult(
                        chooser,
                        FILE_CHOOSER_REQUEST_CODE
                    )

                    true

                } catch (e: Exception) {

                    pendingFileCallback?.onReceiveValue(null)
                    pendingFileCallback = null
                    pendingCameraUri = null

                    android.widget.Toast.makeText(
                        this@MainActivity,
                        "Gagal membuka kamera atau galeri: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()

                    true
                }
            }
        }

        // Hubungkan JavaScript dengan Android
        webView.addJavascriptInterface(
            RumaGoBridge(),
            "Android"
        )

        // Halaman pelanggan dan kurir
        webView.loadUrl(
            "https://irsanputra28-design.github.io/RumaGo-Android./pelanggan.html"
        )
    }

    // HASIL FOTO DARI KAMERA ATAU GALERI
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {

            val selectedUri: Uri? = if (resultCode == RESULT_OK) {
                data?.data ?: pendingCameraUri
            } else {
                null
            }

            val results = selectedUri?.let {
                arrayOf(it)
            }

            pendingFileCallback?.onReceiveValue(results)

            pendingFileCallback = null
            pendingCameraUri = null
        }
    }

    // HASIL IZIN LOKASI
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == LOCATION_REQUEST_CODE) {

            val granted = grantResults.any {
                it == PackageManager.PERMISSION_GRANTED
            }

            pendingGeoCallback?.invoke(
                pendingGeoOrigin ?: "",
                granted,
                false
            )

            pendingGeoCallback = null
            pendingGeoOrigin = null
        }
    }

    // KIRIM HASIL KE JAVASCRIPT
    private fun sendCallback(
        functionName: String,
        payload: JSONObject
    ) {
        val safeJson = JSONObject.quote(payload.toString())

        runOnUiThread {
            webView.evaluateJavascript(
                "window.$functionName && window.$functionName($safeJson);",
                null
            )
        }
    }

    private fun userJson(
        uid: String,
        email: String?,
        profile: Map<String, Any?>
    ): JSONObject {
        return JSONObject().apply {
            put("uid", uid)
            put("email", email ?: "")
            put("name", profile["name"] ?: "")
            put("phone", profile["phone"] ?: "")
            put("role", profile["role"] ?: "customer")
        }
    }

    private inner class RumaGoBridge {

        @JavascriptInterface
        fun registerCustomer(
            name: String,
            email: String,
            phone: String,
            password: String
        ) {
            auth.createUserWithEmailAndPassword(
                email.trim(),
                password
            ).addOnSuccessListener { result ->

                val user = result.user

                if (user == null) {
                    sendCallback(
                        "onRumaGoAuthResult",
                        JSONObject()
                            .put("success", false)
                            .put("message", "Akun tidak ditemukan setelah registrasi.")
                    )
                    return@addOnSuccessListener
                }

                val profile = hashMapOf<String, Any>(
                    "uid" to user.uid,
                    "name" to name.trim(),
                    "email" to email.trim(),
                    "phone" to phone.trim(),
                    "role" to "customer",
                    "createdAt" to FieldValue.serverTimestamp()
                )

                db.collection("users")
                    .document(user.uid)
                    .set(profile)
                    .addOnSuccessListener {

                        val safeProfile = mapOf(
                            "name" to name.trim(),
                            "phone" to phone.trim(),
                            "role" to "customer"
                        )

                        sendCallback(
                            "onRumaGoAuthResult",
                            JSONObject()
                                .put("success", true)
                                .put("message", "Registrasi berhasil. Selamat datang di RumaGo.")
                                .put(
                                    "user",
                                    userJson(user.uid, user.email, safeProfile)
                                )
                        )
                    }
                    .addOnFailureListener { e ->
                        sendCallback(
                            "onRumaGoAuthResult",
                            JSONObject()
                                .put("success", false)
                                .put(
                                    "message",
                                    "Akun dibuat, tetapi profil gagal disimpan: ${e.localizedMessage ?: "Periksa koneksi dan Firestore Rules"}"
                                )
                        )
                    }
            }.addOnFailureListener { e ->
                sendCallback(
                    "onRumaGoAuthResult",
                    JSONObject()
                        .put("success", false)
                        .put("message", e.localizedMessage ?: "Registrasi gagal.")
                )
            }
        }

        @JavascriptInterface
        fun register(
            email: String,
            password: String
        ) {
            auth.createUserWithEmailAndPassword(
                email.trim(),
                password
            ).addOnSuccessListener { result ->

                sendCallback(
                    "onRumaGoAuthResult",
                    JSONObject()
                        .put("success", true)
                        .put("message", "Akun berhasil dibuat.")
                        .put(
                            "user",
                            JSONObject()
                                .put("uid", result.user?.uid ?: "")
                                .put("email", email)
                                .put("role", "customer")
                        )
                )
            }.addOnFailureListener { e ->
                sendCallback(
                    "onRumaGoAuthResult",
                    JSONObject()
                        .put("success", false)
                        .put("message", e.localizedMessage ?: "Registrasi gagal.")
                )
            }
        }

        @JavascriptInterface
        fun login(
            email: String,
            password: String
        ) {
            auth.signInWithEmailAndPassword(
                email.trim(),
                password
            ).addOnSuccessListener { result ->

                val user = result.user

                if (user == null) {
                    sendCallback(
                        "onRumaGoAuthResult",
                        JSONObject()
                            .put("success", false)
                            .put("message", "Akun tidak ditemukan.")
                    )
                    return@addOnSuccessListener
                }

                db.collection("users")
                    .document(user.uid)
                    .get()
                    .addOnSuccessListener { doc ->

                        val data = doc.data ?: emptyMap<String, Any>()

                        sendCallback(
                            "onRumaGoAuthResult",
                            JSONObject()
                                .put("success", true)
                                .put("message", "Login berhasil.")
                                .put(
                                    "user",
                                    userJson(user.uid, user.email, data)
                                )
                        )
                    }
                    .addOnFailureListener { e ->
                        sendCallback(
                            "onRumaGoAuthResult",
                            JSONObject()
                                .put("success", false)
                                .put("message", e.localizedMessage ?: "Gagal membaca profil.")
                        )
                    }
            }.addOnFailureListener { e ->
                sendCallback(
                    "onRumaGoAuthResult",
                    JSONObject()
                        .put("success", false)
                        .put("message", e.localizedMessage ?: "Login gagal.")
                )
            }
        }

        @JavascriptInterface
        fun getCurrentUser() {
            val user = auth.currentUser

            if (user == null) {
                sendCallback(
                    "onRumaGoCurrentUser",
                    JSONObject().put("user", JSONObject.NULL)
                )
                return
            }

            db.collection("users")
                .document(user.uid)
                .get()
                .addOnSuccessListener { doc ->

                    val data = doc.data ?: emptyMap<String, Any>()

                    sendCallback(
                        "onRumaGoCurrentUser",
                        JSONObject().put(
                            "user",
                            userJson(user.uid, user.email, data)
                        )
                    )
                }
                .addOnFailureListener {
                    sendCallback(
                        "onRumaGoCurrentUser",
                        JSONObject().put("user", JSONObject.NULL)
                    )
                }
        }

        @JavascriptInterface
        fun logout() {
            auth.signOut()

            sendCallback(
                "onRumaGoAuthResult",
                JSONObject()
                    .put("success", true)
                    .put("message", "Anda sudah keluar.")
                    .put("user", JSONObject.NULL)
            )
        }

        @JavascriptInterface
        fun createOrder(orderJson: String) {
            val user = auth.currentUser

            if (user == null) {
                sendCallback(
                    "onRumaGoOrderResult",
                    JSONObject()
                        .put("success", false)
                        .put("message", "Silakan login kembali.")
                )
                return
            }

            try {
                val input = JSONObject(orderJson)
                val itemsJson = input.optJSONArray("items") ?: JSONArray()
                val items = mutableListOf<Map<String, Any>>()

                for (i in 0 until itemsJson.length()) {
                    val item = itemsJson.getJSONObject(i)

                    items.add(
                        mapOf(
                            "id" to item.optInt("id"),
                            "name" to item.optString("name"),
                            "price" to item.optDouble("price"),
                            "qty" to item.optInt("qty")
                        )
                    )
                }

                if (items.isEmpty()) {
                    sendCallback(
                        "onRumaGoOrderResult",
                        JSONObject()
                            .put("success", false)
                            .put("message", "Keran
