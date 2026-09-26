package com.cryptoeze.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.provider.MediaStore
import android.view.ContextMenu
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.cryptoeze.app.push.PushService
import com.cryptoeze.app.security.AppLock
import com.cryptoeze.app.ui.BrandSpinner
import com.cryptoeze.app.ui.IntroView
import com.cryptoeze.app.web.AppBridge
import com.cryptoeze.app.web.AppWebView
import com.cryptoeze.app.web.Downloads
import com.cryptoeze.app.web.PageScripts
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import java.io.File

class MainActivity : AppCompatActivity(), AppBridge.Host {

    private lateinit var root: View
    private lateinit var web: AppWebView
    private lateinit var swipe: SwipeRefreshLayout
    private lateinit var progress: LinearProgressIndicator
    private lateinit var intro: IntroView
    private lateinit var offline: View
    private lateinit var lockView: View
    private lateinit var exitPill: TextView
    private lateinit var retry: MaterialButton
    private lateinit var retrySpinner: BrandSpinner
    private lateinit var appLock: AppLock

    private val homeUrl by lazy { getString(R.string.home_url) }
    private var firstPageShown = false
    private var mainFrameFailed = false
    private var failedUrl: String? = null
    private var lastBackPress = 0L
    private var coldStart = true

    // ---- File upload / camera --------------------------------------------------------------
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingChooser: WebChromeClient.FileChooserParams? = null
    private var cameraUri: Uri? = null
    private var pendingWebPermission: PermissionRequest? = null
    private var pendingDownload: (() -> Unit)? = null

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileCallback ?: return@registerForActivityResult
            fileCallback = null
            val data = result.data
            val uris = mutableListOf<Uri>()
            if (result.resultCode == RESULT_OK) {
                data?.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri }
                data?.data?.let { if (it !in uris) uris += it }
                val shot = cameraUri
                if (uris.isEmpty() && shot != null && cameraFileHasPhoto()) uris += shot
            }
            callback.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
            cameraUri = null
        }

    private val cameraPermissionForChooser =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            pendingChooser?.let { launchChooser(it) }
            pendingChooser = null
        }

    private val cameraPermissionForPage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val request = pendingWebPermission ?: return@registerForActivityResult
            pendingWebPermission = null
            if (granted) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else request.deny()
        }

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pendingDownload?.invoke() else showPill(getString(R.string.download_failed))
            pendingDownload = null
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val updateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    // ---- Lifecycle ---------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        web = findViewById(R.id.web)
        swipe = findViewById(R.id.swipe)
        progress = findViewById(R.id.progress)
        intro = findViewById(R.id.intro)
        offline = findViewById(R.id.offline)
        lockView = findViewById(R.id.lock)
        exitPill = findViewById(R.id.exitPill)
        retry = findViewById(R.id.retry)
        retrySpinner = findViewById(R.id.retrySpinner)
        appLock = AppLock(this)

        // Hand over from the system splash to our animated intro with a quick cross-fade.
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate().alpha(0f).setDuration(200L).withEndAction { provider.remove() }.start()
        }

        applyInsets()
        setupSwipeRefresh()
        setupWebView()
        setupBackHandling()
        setupOfflineScreen()
        watchConnectivity()

        findViewById<View>(R.id.unlock).setOnClickListener { promptUnlock() }

        if (savedInstanceState != null) {
            // Re-created (e.g. dark/light mode switched): no intro, restore the page as it was.
            coldStart = false
            intro.dismissImmediately()
            firstPageShown = true
            web.alpha = 1f
            if (web.restoreState(savedInstanceState) == null) web.loadUrl(homeUrl)
        } else {
            intro.start()
            web.loadUrl(urlFromIntent(intent) ?: homeUrl)
        }
        if (appLock.shouldLock(coldStart = savedInstanceState == null)) showLock()

        checkForAppUpdate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        urlFromIntent(intent)?.let { web.loadUrl(it) }
    }

    override fun onStart() {
        super.onStart()
        if (!coldStart && appLock.shouldLock(coldStart = false)) {
            showLock()
            promptUnlock()
        }
        coldStart = false
    }

    override fun onStop() {
        super.onStop()
        appLock.onBackground()
        android.webkit.CookieManager.getInstance().flush()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onDestroy() {
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
        super.onDestroy()
    }

    private fun urlFromIntent(intent: Intent?): String? {
        val url = intent?.getStringExtra(PushService.EXTRA_URL) ?: intent?.data?.toString() ?: return null
        return url.takeIf { isInternal(Uri.parse(it)) }
    }

    // ---- Layout --------------------------------------------------------------------------------

    private fun applyInsets() {
        val content = findViewById<View>(R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
    }

    /** Colours the status/navigation bar area to match the page so it feels like one surface. */
    private fun applyPageColor(color: Int) {
        root.setBackgroundColor(color)
        val light = ColorUtils.calculateLuminance(color) > 0.5
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun readPageColor() {
        web.evaluateJavascript(PageScripts.THEME_COLOR) { raw ->
            parseCssColor(raw?.trim('"').orEmpty())?.let { applyPageColor(it) }
        }
    }

    private fun parseCssColor(css: String): Int? = runCatching {
        when {
            css.startsWith("#") -> Color.parseColor(if (css.length == 4) "#" + css.drop(1).map { "$it$it" }.joinToString("") else css)
            css.startsWith("rgb") -> {
                val parts = css.substringAfter("(").substringBefore(")").split(",").map { it.trim().toFloat() }
                Color.rgb(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
            }
            else -> null
        }
    }.getOrNull()

    // ---- WebView -------------------------------------------------------------------------------

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            allowFileAccess = false
            allowContentAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString CryptoEzeApp/${BuildConfig.VERSION_NAME}"
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(web.settings, false)
        }
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.setBackgroundColor(ContextCompat.getColor(this, R.color.app_bg))
        web.addJavascriptInterface(AppBridge(this), AppBridge.NAME)

        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(web, PageScripts.DOCUMENT_START, setOf("https://*.cryptoeze.com", "https://cryptoeze.com"))
        }

        web.webViewClient = Client()
        web.webChromeClient = Chrome()
        web.setDownloadListener { url, userAgent, disposition, mime, _ -> download(url, userAgent, disposition, mime) }
        registerForContextMenu(web)
    }

    private inner class Client : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            return when (uri.scheme) {
                "http", "https" -> {
                    // Our site stays in the app. Links the user taps to other sites open in a
                    // polished in-app browser tab; redirects (payments, KYC) stay in place.
                    if (isInternal(uri) || !request.isForMainFrame || !request.hasGesture()) false
                    else { openCustomTab(uri); true }
                }
                "intent" -> { openIntentUri(uri.toString()); true }
                "about", "data", "blob", "javascript" -> false
                else -> { openExternalApp(uri); true }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            mainFrameFailed = false
            progress.show()
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                view.evaluateJavascript(PageScripts.DOCUMENT_START, null)
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            swipe.isRefreshing = false
            progress.hide()
            if (mainFrameFailed) return
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                view.evaluateJavascript(PageScripts.DOCUMENT_START, null)
            }
            hideOffline()
            readPageColor()
            trackSession(Uri.parse(url))
            PushService.token(this@MainActivity).takeIf { it.isNotEmpty() }?.let {
                view.evaluateJavascript(PageScripts.fcmTokenEvent(it), null)
            }
            revealApp()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            mainFrameFailed = true
            failedUrl = request.url.toString()
            showOffline(networkProblem = !isOnline() || error.errorCode in NETWORK_ERRORS)
            revealApp()
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            // Never continue on a bad certificate: this is a financial app.
            handler.cancel()
            mainFrameFailed = true
            failedUrl = error.url
            showOffline(networkProblem = false)
            revealApp()
        }

        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            // The page crashed or was killed to free memory: rebuild cleanly instead of crashing.
            recreate()
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress.setProgressCompat(newProgress, true)
            if (newProgress >= 100) progress.hide()
        }

        override fun onShowFileChooser(
            webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, params: FileChooserParams,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            val wantsImages = params.acceptTypes.none { it.isNotBlank() } ||
                params.acceptTypes.any { it.startsWith("image") || it == "*/*" }
            if (wantsImages && !hasPermission(Manifest.permission.CAMERA)) {
                pendingChooser = params
                cameraPermissionForChooser.launch(Manifest.permission.CAMERA)
            } else {
                launchChooser(params)
            }
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            runOnUiThread {
                if (!isInternal(request.origin) ||
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE !in request.resources
                ) {
                    request.deny(); return@runOnUiThread
                }
                if (hasPermission(Manifest.permission.CAMERA)) {
                    request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                } else {
                    pendingWebPermission = request
                    cameraPermissionForPage.launch(Manifest.permission.CAMERA)
                }
            }
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            // target="_blank" / window.open: capture the URL and route it like a normal link.
            val catcher = WebView(this@MainActivity)
            catcher.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (isInternal(uri)) web.loadUrl(uri.toString())
                    else if (uri.scheme == "http" || uri.scheme == "https") openCustomTab(uri)
                    else openExternalApp(uri)
                    v.destroy()
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = catcher
            resultMsg.sendToTarget()
            return true
        }
    }

    private fun isInternal(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host == SITE_HOST || host.endsWith(".$SITE_HOST")
    }

    private fun trackSession(uri: Uri) {
        if (!isInternal(uri)) return
        val path = uri.path.orEmpty().lowercase().trimEnd('/')
        val publicPage = path.isEmpty() || PUBLIC_PATHS.any { path.startsWith(it) }
        appLock.signedIn = !publicPage
    }

    private fun revealApp() {
        if (firstPageShown) return
        firstPageShown = true
        intro.finish {
            web.animate().alpha(1f).setDuration(380L).start()
            if (appLock.locked) promptUnlock() else askNotificationPermission()
        }
    }

    // ---- Links ---------------------------------------------------------------------------------

    private fun openCustomTab(uri: Uri) {
        val bar = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(ContextCompat.getColor(this, R.color.app_bg))
            .build()
        runCatching {
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(bar)
                .setShowTitle(true)
                .setUrlBarHidingEnabled(true)
                .build()
                .launchUrl(this, uri)
        }.onFailure { openExternalApp(uri) }
    }

    private fun openExternalApp(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            showPill(getString(R.string.no_app_found))
        }
    }

    private fun openIntentUri(url: String) {
        val intent = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        intent.component = null
        intent.selector = null
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            intent.getStringExtra("browser_fallback_url")?.let { openCustomTab(Uri.parse(it)) }
                ?: intent.`package`?.let { openExternalApp(Uri.parse("market://details?id=$it")) }
        }
    }

    // Long-press on a link or image: copy / share / open / save.
    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val hit = web.hitTestResult
        val extra = hit.extra ?: return
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE, WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val link = if (hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) anchorHref() ?: extra else extra
                menu.setHeaderTitle(link)
                menu.add(R.string.copy_link).setOnMenuItemClickListener { copy(link, getString(R.string.link_copied)); true }
                menu.add(R.string.share_link).setOnMenuItemClickListener { share(link); true }
                menu.add(R.string.open_in_browser).setOnMenuItemClickListener { openExternalApp(Uri.parse(link)); true }
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                menu.add(R.string.download_image).setOnMenuItemClickListener {
                    download(extra, web.settings.userAgentString, null, null); true
                }
                if (!extra.startsWith("data:")) {
                    menu.add(R.string.copy_image_link).setOnMenuItemClickListener { copy(extra, getString(R.string.link_copied)); true }
                }
            }
        }
    }

    private fun anchorHref(): String? {
        val msg = android.os.Handler(mainLooper).obtainMessage()
        web.requestFocusNodeHref(msg)
        return msg.data?.getString("url")
    }

    private fun share(link: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
        startActivity(Intent.createChooser(send, null))
    }

    private fun copy(text: String, confirmation: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("CryptoEze", text))
        // Android 13+ shows its own copy confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) showPill(confirmation)
    }

    // ---- Uploads -------------------------------------------------------------------------------

    private fun launchChooser(params: WebChromeClient.FileChooserParams) {
        val types = params.acceptTypes.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
            .map { if (it.startsWith(".")) android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(it.drop(1)) ?: "*/*" else it }
        val wantsImages = types.isEmpty() || types.any { it.startsWith("image") || it == "*/*" }
        val camera = if (wantsImages && hasPermission(Manifest.permission.CAMERA)) cameraIntent() else null

        if (params.isCaptureEnabled && camera != null) {
            fileChooserLauncher.launch(camera)
            return
        }
        val pick = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (types.size == 1) types[0] else "*/*"
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)
        }
        val chooser = Intent.createChooser(pick, getString(R.string.choose_file))
        if (camera != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
        try {
            fileChooserLauncher.launch(chooser)
        } catch (e: ActivityNotFoundException) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
        }
    }

    private fun cameraIntent(): Intent? {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        cameraUri = uri
        return Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, uri)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun cameraFileHasPhoto(): Boolean =
        File(cacheDir, "camera").listFiles()?.any { it.length() > 0 } == true

    // ---- Downloads -----------------------------------------------------------------------------

    private fun download(url: String, userAgent: String, disposition: String?, mime: String?) {
        when {
            url.startsWith("blob:") -> web.evaluateJavascript(
                "window.__cryptoEzeSaveBlob && window.__cryptoEzeSaveBlob('${url.replace("'", "")}', '');", null,
            )
            url.startsWith("data:") -> saveBase64File(url, "", mime.orEmpty())
            else -> withStoragePermission {
                runCatching { Downloads.enqueue(this, url, userAgent, disposition, mime) }
                    .onSuccess { showPill(getString(R.string.downloading, it)) }
                    .onFailure { showPill(getString(R.string.download_failed)) }
            }
        }
    }

    private fun withStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P || hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            action()
        } else {
            pendingDownload = action
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // ---- AppBridge.Host ------------------------------------------------------------------------

    override fun fcmToken(): String = PushService.token(this)

    override fun copyText(text: String) {
        runOnUiThread { copy(text, getString(R.string.copied)) }
    }

    override fun saveBase64File(dataUrl: String, fileName: String, mimeType: String) {
        runOnUiThread {
            withStoragePermission {
                Thread {
                    val result = runCatching { Downloads.saveDataUrl(this, dataUrl, fileName, mimeType) }
                    runOnUiThread {
                        showPill(getString(if (result.isSuccess) R.string.download_saved else R.string.download_failed))
                    }
                }.start()
            }
        }
    }

    override fun setInnerScrolled(scrolled: Boolean) {
        web.innerScrolled = scrolled
    }

    // ---- Pull to refresh -----------------------------------------------------------------------

    private fun setupSwipeRefresh() {
        swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.brand_teal))
        swipe.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface))
        swipe.setOnChildScrollUpCallback { _, _ -> !web.canPullToRefresh }
        swipe.setOnRefreshListener { reload() }
    }

    private fun reload() {
        val target = failedUrl
        if (mainFrameFailed && target != null) web.loadUrl(target) else web.reload()
    }

    // ---- Offline -------------------------------------------------------------------------------

    private fun setupOfflineScreen() {
        retry.setOnClickListener {
            retry.text = ""
            retrySpinner.visibility = View.VISIBLE
            retry.isEnabled = false
            reload()
            retry.postDelayed({ resetRetryButton() }, 1600L)
        }
    }

    private fun resetRetryButton() {
        retry.setText(R.string.try_again)
        retry.isEnabled = true
        retrySpinner.visibility = View.GONE
    }

    private fun showOffline(networkProblem: Boolean) {
        findViewById<TextView>(R.id.offlineTitle).setText(if (networkProblem) R.string.offline_title else R.string.page_error_title)
        findViewById<TextView>(R.id.offlineBody).setText(if (networkProblem) R.string.offline_body else R.string.page_error_body)
        swipe.isRefreshing = false
        progress.hide()
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(web.windowToken, 0)
        applyPageColor(ContextCompat.getColor(this, R.color.app_bg))
        if (offline.visibility != View.VISIBLE) {
            offline.alpha = 0f
            offline.visibility = View.VISIBLE
            offline.animate().alpha(1f).setDuration(250L).start()
            val art = findViewById<View>(R.id.offlineArt)
            art.scaleX = 0.85f; art.scaleY = 0.85f
            art.animate().scaleX(1f).scaleY(1f).setDuration(500L)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
        }
    }

    private fun hideOffline() {
        failedUrl = null
        resetRetryButton()
        if (offline.visibility == View.VISIBLE) {
            offline.animate().alpha(0f).setDuration(250L).withEndAction { offline.visibility = View.GONE }.start()
        }
    }

    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnUiThread { if (offline.visibility == View.VISIBLE) reload() }
        }
    }

    private fun watchConnectivity() {
        runCatching { connectivity.registerDefaultNetworkCallback(networkCallback) }
    }

    private fun isOnline(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ---- Back: "Click exit again" -----------------------------------------------------------

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (lockView.visibility != View.VISIBLE && offline.visibility != View.VISIBLE && web.canGoBack()) {
                    web.goBack()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackPress < EXIT_WINDOW_MS) {
                    finish()
                } else {
                    lastBackPress = now
                    showPill(getString(R.string.exit_again))
                }
            }
        })
    }

    private fun showPill(text: String) {
        exitPill.text = text
        exitPill.animate().cancel()
        exitPill.removeCallbacks(hidePill)
        if (exitPill.visibility != View.VISIBLE) {
            exitPill.visibility = View.VISIBLE
            exitPill.alpha = 0f
            exitPill.translationY = 16f * resources.displayMetrics.density
            exitPill.scaleX = 0.94f; exitPill.scaleY = 0.94f
        }
        exitPill.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(220L).start()
        exitPill.postDelayed(hidePill, EXIT_WINDOW_MS)
    }

    private val hidePill = Runnable {
        exitPill.animate().alpha(0f).translationY(12f * resources.displayMetrics.density).setDuration(200L)
            .withEndAction { exitPill.visibility = View.GONE }.start()
    }

    // ---- Lock ----------------------------------------------------------------------------------

    private fun showLock() {
        appLock.markLocked()
        lockView.alpha = 1f
        lockView.visibility = View.VISIBLE
    }

    private fun promptUnlock() {
        appLock.prompt {
            lockView.animate().alpha(0f).setDuration(250L).withEndAction { lockView.visibility = View.GONE }.start()
            askNotificationPermission()
        }
    }

    // ---- Notifications & updates -------------------------------------------------------------

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasPermission(Manifest.permission.POST_NOTIFICATIONS)) return
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun checkForAppUpdate() {
        val manager = AppUpdateManagerFactory.create(this)
        val listener = object : InstallStateUpdatedListener {
            override fun onStateUpdate(state: com.google.android.play.core.install.InstallState) {
                if (state.installStatus() == InstallStatus.DOWNLOADED) {
                    manager.unregisterListener(this)
                    Snackbar.make(root, R.string.update_ready, Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.restart) { manager.completeUpdate() }
                        .setActionTextColor(ContextCompat.getColor(this@MainActivity, R.color.brand_teal))
                        .show()
                }
            }
        }
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                manager.registerListener(listener)
                runCatching {
                    manager.startUpdateFlowForResult(info, updateLauncher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
                }
            }
        }
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val SITE_HOST = "cryptoeze.com"
        private const val EXIT_WINDOW_MS = 2000L
        private val PUBLIC_PATHS = listOf("/login", "/register", "/signup", "/sign-up", "/forgot", "/reset", "/verify", "/logout")
        private val NETWORK_ERRORS = setOf(
            WebViewClient.ERROR_HOST_LOOKUP, WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT, WebViewClient.ERROR_IO,
        )
    }
}
