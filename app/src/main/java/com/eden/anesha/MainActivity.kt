package com.eden.anesha

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message // Import Message for onCreateViewWindow
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient // IMPORTANT: Add this import
import android.webkit.WebResourceRequest // IMPORTANT: Add this import for shouldOverrideUrlLoading
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.MobileAds // Import for AdMob

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var uploadMessage: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val results = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            uploadMessage?.onReceiveValue(results)
        } else {
            uploadMessage?.onReceiveValue(null)
        }
        uploadMessage = null
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (!allGranted) {
                Toast.makeText(this, "Permissions are required for some features to work.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)

        // Initialize MobileAds for AdMob
        MobileAds.initialize(this) {}

        checkAndRequestPermissions()
        createNotificationChannel()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            // IMPORTANT for handling links that open in new windows (e.g., target="_blank")
            setSupportMultipleWindows(true)
        }

        // --- WebViewClient for handling URL loading within the WebView ---
        webView.webViewClient = object : WebViewClient() {

            // For API 21 and above
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString()
                if (url.isNullOrEmpty()) {
                    return false
                }

                // Check if it's a URL that needs to open an external app (like tel:, mailto:, WhatsApp)
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(url)
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, "No application found to handle this link.", Toast.LENGTH_SHORT).show()
                    }
                    return true // Handled by us (opening external app)
                }

                // If it's a regular http/https URL, load it in our WebView
                view?.loadUrl(url)
                return true // Indicate that we've handled the URL internally
            }

            // For older API levels (deprecated in API 24)
            @Deprecated("Deprecated in API 24")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url.isNullOrEmpty()) {
                    return false
                }

                // Check if it's a URL that needs to open an external app (like tel:, mailto:, WhatsApp)
                if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:")) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(url)
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(this@MainActivity, "No application found to handle this link.", Toast.LENGTH_SHORT).show()
                    }
                    return true // Handled by us (opening external app)
                }

                // If it's a regular http/https URL, load it in our WebView
                view?.loadUrl(url)
                return true // Indicate that we've handled the URL internally
            }

            // Optional: You can show a loading indicator
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Show loading indicator
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Hide loading indicator
            }
        }

        webView.addJavascriptInterface(WebappInterface(this), "Android")

        // --- WebChromeClient to handle new window requests and file choosers ---
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadMessage?.onReceiveValue(null)
                uploadMessage = filePathCallback
                val intent = fileChooserParams?.createIntent()
                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    uploadMessage = null
                    Toast.makeText(this@MainActivity, "Cannot open file chooser", Toast.LENGTH_LONG).show()
                    return false
                }
                return true
            }

            // IMPORTANT: This method is called when JavaScript tries to open a new window (e.g., window.open() or target="_blank")
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                // Create a temporary WebView instance to capture the URL of the new window
                val newWebView = WebView(this@MainActivity)
                newWebView.settings.javaScriptEnabled = true // Enable JavaScript for the new window content
                newWebView.settings.domStorageEnabled = true
                newWebView.settings.setSupportZoom(true) // Optional: add zoom support

                // IMPORTANT: Set a WebViewClient for this new (temporary) WebView
                // The sole purpose of this client is to intercept the URL the new window tries to load
                // and then load it into our *main* WebView.
                newWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString()
                        if (url != null && url != "about:blank") { // Prevent loading blank pages
                            this@MainActivity.webView.loadUrl(url) // Load the new window's URL in our *main* WebView
                        }
                        return true // Indicate that we've handled the URL
                    }

                    @Deprecated("Deprecated in API 24")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (url != null && url != "about:blank") {
                            this@MainActivity.webView.loadUrl(url)
                        }
                        return true
                    }
                }

                // Get the message transport and assign our temporary WebView
                val transport = resultMsg?.obj as WebView.WebViewTransport
                transport.webView = newWebView // Pass the new WebView to the system
                resultMsg.sendToTarget() // Send the message back to the system

                return true // Indicate that we have handled the new window creation
            }
        }
        // --- FIX ENDS HERE ---

        //----start of download_listener_setup----
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimetype)
                request.setDescription("Downloading file...")
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype))
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(applicationContext, "Downloading File", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                // Log the exception for debugging in a real app, e.g.:
                // Log.e("DownloadManager", "Download failed: ${e.message}")
                Toast.makeText(applicationContext, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        //----end of download_listener_setup----

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    if (isEnabled) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        webView.loadUrl("https://xbvercel.vercel.app")
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // WRITE_EXTERNAL_STORAGE is deprecated for API 29+ for direct app access to Downloads,
        // but still relevant for older versions if you target them without scoped storage.
        // For downloads on modern Android, DownloadManager handles permissions internally.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // Q is API 29
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "WebApp Notifications"
            val descriptionText = "Notifications from the web app"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("WEBAPP_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    class WebappInterface(private val context: Context) {
        @JavascriptInterface
        fun showNotification(title: String, message: String) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val builder = NotificationCompat.Builder(context, "WEBAPP_CHANNEL")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)

            val notificationId = System.currentTimeMillis().toInt()
            notificationManager.notify(notificationId, builder.build())
        }
    }
}