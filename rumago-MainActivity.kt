
package id.rumago.app

import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.GeolocationPermissions
import android.content.pm.PackageManager
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val LOCATION_REQUEST_CODE = 1001
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null

    private fun hasLocationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT < 23) {
            true
        } else {
            checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(13, 104, 69)
        window.navigationBarColor = Color.rgb(8, 75, 50)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setGeolocationEnabled(true)

        webView.webViewClient = WebViewClient()

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
        }

        webView.addJavascriptInterface(RumaGoBridge(), "Android")
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

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

    private fun sendCallback(functionName: String, payload: JSONObject) {
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
                                .put("user", userJson(user.uid, user.email, safeProfile))
                        )
                    }
                    .addOnFailureListener { e ->
                        sendCallback(
                            "onRumaGoAuthResult",
                            JSONObject()
                                .put("success", false)
                                .put("message", "Akun dibuat, tetapi profil gagal disimpan: ${e.localizedMessage ?: "Periksa koneksi dan Firestore Rules"}")
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
        fun register(email: String, password: String) {
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
        fun login(email: String, password: String) {
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
                                .put("user", userJson(user.uid, user.email, data))
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
                            .put("message", "Keranjang masih kosong.")
                    )
                    return
                }

                val orderNumber = "RG" + System.currentTimeMillis()

                val order = hashMapOf<String, Any>(
                    "orderNumber" to orderNumber,
                    "customerId" to user.uid,
                    "customerEmail" to (user.email ?: ""),
                    "customerName" to input.optString("customerName"),
                    "phone" to input.optString("phone"),
                    "address" to input.optString("address"),
                    "city" to input.optString("city"),
                    "delivery" to input.optString("delivery"),
                    "note" to input.optString("note"),
                    "items" to items,
                    "total" to input.optDouble("total"),
                    "status" to "Menunggu Konfirmasi",
                    "createdAt" to FieldValue.serverTimestamp()
                )

                // Simpan koordinat GPS jika dikirim dari halaman checkout.
                if (input.has("latitude") && !input.isNull("latitude")) {
                    val latitude = input.optDouble("latitude")
                    if (!latitude.isNaN() && !latitude.isInfinite()) {
                        order["latitude"] = latitude
                    }
                }

                if (input.has("longitude") && !input.isNull("longitude")) {
                    val longitude = input.optDouble("longitude")
                    if (!longitude.isNaN() && !longitude.isInfinite()) {
                        order["longitude"] = longitude
                    }
                }

                db.collection("orders")
                    .add(order)
                    .addOnSuccessListener {
                        sendCallback(
                            "onRumaGoOrderResult",
                            JSONObject()
                                .put("success", true)
                                .put("orderNumber", orderNumber)
                        )
                    }
                    .addOnFailureListener { e ->
                        sendCallback(
                            "onRumaGoOrderResult",
                            JSONObject()
                                .put("success", false)
                                .put("message", e.localizedMessage ?: "Gagal menyimpan pesanan.")
                        )
                    }

            } catch (e: Exception) {
                sendCallback(
                    "onRumaGoOrderResult",
                    JSONObject()
                        .put("success", false)
                        .put("message", e.localizedMessage ?: "Data pesanan tidak valid.")
                )
            }
        }

        @JavascriptInterface
        fun getMyOrders() {
            val user = auth.currentUser

            if (user == null) {
                sendCallback(
                    "onRumaGoOrdersResult",
                    JSONObject()
                        .put("success", false)
                        .put("message", "Silakan login.")
                )
                return
            }

            db.collection("orders")
                .whereEqualTo("customerId", user.uid)
                .get()
                .addOnSuccessListener { snapshot ->

                    val result = JSONArray()

                    for (doc in snapshot.documents) {
                        val data = doc.data ?: continue
                        val created = data["createdAt"]

                        val dateText = when (created) {
                            is com.google.firebase.Timestamp ->
                                SimpleDateFormat(
                                    "dd MMM yyyy, HH:mm",
                                    Locale("id", "ID")
                                ).format(created.toDate())

                            is Date ->
                                SimpleDateFormat(
                                    "dd MMM yyyy, HH:mm",
                                    Locale("id", "ID")
                                ).format(created)

                            else -> ""
                        }

                        val obj = JSONObject().apply {
                            put("id", doc.id)
                            put("orderNumber", data["orderNumber"] ?: doc.id)
                            put("status", data["status"] ?: "Diproses")
                            put("createdAt", dateText)
                            put("customerName", data["customerName"] ?: "")
                            put("phone", data["phone"] ?: "")
                            put("address", data["address"] ?: "")
                            put("city", data["city"] ?: "")
                            put("delivery", data["delivery"] ?: "")
                            put("total", data["total"] ?: 0)
                            put("latitude", data["latitude"] ?: JSONObject.NULL)
                            put("longitude", data["longitude"] ?: JSONObject.NULL)
                            put(
                                "items",
                                JSONArray(
                                    data["items"] as? Collection<*> ?: emptyList<Any>()
                                )
                            )
                        }

                        result.put(obj)
                    }

                    sendCallback(
                        "onRumaGoOrdersResult",
                        JSONObject()
                            .put("success", true)
                            .put("orders", result)
                    )
                }
                .addOnFailureListener { e ->
                    sendCallback(
                        "onRumaGoOrdersResult",
                        JSONObject()
                            .put("success", false)
                            .put("message", e.localizedMessage ?: "Gagal memuat pesanan.")
                    )
                }
        }
    }
}
