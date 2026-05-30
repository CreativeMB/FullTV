package com.creativem.tvfullurl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// --- OBJETO: AGENTE CONSTRUCTOR ---
object LocalAgentBuilder {
    fun getSecureHeaders(link: CapturedLink): HashMap<String, String> {
        val headers = HashMap<String, String>()

        link.userAgent?.let { headers["User-Agent"] = it }
        link.referer?.let { headers["Referer"] = it }
        link.cookie?.let { headers["Cookie"] = it }

        headers["Accept"] = "*/*"
        headers["Connection"] = "keep-alive"

        link.referer?.let { ref ->
            try {
                val uri = Uri.parse(ref)
                headers["Origin"] = "${uri.scheme}://${uri.host}"
            } catch (e: Exception) { }
        }

        return headers
    }
}

data class FavoriteItem(val title: String, val url: String)

data class CapturedLink(
    val url: String,
    val userAgent: String?,
    val referer: String?,
    val cookie: String?,
    var ipLockStatus: String = "🔄 Analizando compatibilidad..."
) {
    fun getFormattedUrlForClipboard(): String {
        return url.trim()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CapturedLink) return false
        return this.url == other.url
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }
}


class BrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var btnGo: Button
    private lateinit var btnBack: Button
    private lateinit var btnForward: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnSaveFav: Button
    private lateinit var btnListFav: Button
    private lateinit var btnHome: Button
    private var lastCapturedUrl: String = ""
    private var mainDomain: String = ""
    private lateinit var sharedPreferences: SharedPreferences
    private var btnTogglePopup: Button? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var exoPlayer: androidx.media3.exoplayer.ExoPlayer? = null
    private val capturedLinksList = LinkedHashSet<CapturedLink>()
    private var captureDialog: AlertDialog? = null

    private var btnFloatingCapture: View? = null

    private var popupContainer: FrameLayout? = null
    private var popupWebView: WebView? = null
    private var isPopupMinimized = false

    private val blacklistedDomains = arrayOf(
        "adsterra", "exoclick", "onclickads", "popcash", "popads", "propellerads",
        "doubleclick", "googlesyndication", "google-analytics", "telemetry", "tracker",
        "adserver", "adservice", "histats", "statcounter", "beacon"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_browser)

        sharedPreferences = getSharedPreferences("MisFavoritos", Context.MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        etUrl = findViewById(R.id.etUrl)
        btnGo = findViewById(R.id.btnGo)
        btnBack = findViewById(R.id.btnBack)
        btnForward = findViewById(R.id.btnForward)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSaveFav = findViewById(R.id.btnSaveFav)
        btnListFav = findViewById(R.id.btnListFav)
        btnHome = findViewById(R.id.btnHome)

        setupWebView()
        setupButtons()
        setupFloatingCaptureButton()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun goHomeWithoutFinishing() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            startActivity(intent)
        } catch (e: Exception) {
            moveTaskToBack(true)
        }
    }

    private fun setupButtons() {
        btnGo.setOnClickListener {
            val input = etUrl.text.toString().trim()
            if (input.isNotEmpty()) {
                val finalUrl = if (input.startsWith("http://") || input.startsWith("https://")) {
                    input
                } else if (!input.contains(" ") && input.contains(".")) {
                    "https://$input"
                } else {
                    val encodedQuery = URLEncoder.encode(input, "UTF-8")
                    "https://www.google.com/search?q=$encodedQuery"
                }

                capturedLinksList.clear()
                updateFloatingButtonVisibility()
                lastCapturedUrl = ""
                captureDialog?.dismiss()
                destroyPopup()

                webView.loadUrl(finalUrl)
            }
        }
        btnBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        btnRefresh.setOnClickListener { webView.reload() }

        btnHome.setOnClickListener {
            goHomeWithoutFinishing()
        }
        btnSaveFav.setOnClickListener {
            webView.url?.let { saveFavorite(it) }
        }
        btnListFav.setOnClickListener {
            showFavoritesDialog()
        }
    }

    private fun setupFloatingCaptureButton() {
        val rootView = findViewById<FrameLayout>(android.R.id.content)

        val floatingButton = FrameLayout(this).apply {
            val shape = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#2979FF"))
            }
            background = shape
            elevation = dpToPx(8).toFloat()
            visibility = View.GONE

            layoutParams = FrameLayout.LayoutParams(
                dpToPx(56),
                dpToPx(56)
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(dpToPx(16), 0, 0, dpToPx(80))
            }
        }

        val icon = TextView(this).apply {
            text = "📡"
            textSize = 20f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        floatingButton.addView(icon)

        floatingButton.setOnClickListener {
            if (capturedLinksList.isNotEmpty()) {
                showCapturedLinksDialog()
            }
        }

        btnFloatingCapture = floatingButton
        rootView.addView(floatingButton)
    }

    private fun updateFloatingButtonVisibility() {
        runOnUiThread {
            btnFloatingCapture?.visibility = if (capturedLinksList.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun cleanOverlays() {
        val script = """
        (function() {
            try {
                var elems = Array.from(document.body.querySelectorAll('*'));
                elems.forEach(function(el) {
                    var style = window.getComputedStyle(el);
                    if (style.position === 'fixed' || style.position === 'absolute') {
                        var zIndex = parseInt(style.zIndex);
                        if (zIndex > 90 || (style.width === '100%' && style.height === '100%')) {
                            var html = el.innerHTML || '';
                            var isImportant = html.includes('video') || 
                                              html.includes('Download') || 
                                              el.tagName === 'VIDEO';
                            if (!isImportant) {
                                el.style.display = 'none';
                                el.style.pointerEvents = 'none';
                                el.remove();
                            }
                        }
                    }
                });

                document.querySelectorAll('iframe').forEach(function(iframe) {
                    var src = iframe.src || '';
                    if (!src.includes('video') && !src.includes('player')) {
                        iframe.remove();
                    }
                });

            } catch (e) {
                console.log('Error limpiando overlays: ' + e);
            }
        })();
    """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun isPotentialVideoUrl(url: String): Boolean {
        val urlLower = url.lowercase()
        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
        val path = uri?.path?.lowercase() ?: ""
        val host = uri?.host?.lowercase() ?: ""

        for (domain in blacklistedDomains) {
            if (host.contains(domain)) return false
        }

        if (path.contains("subtitle") || path.contains(".vtt") || path.contains("audio-only")) {
            return false
        }

        if (urlLower.contains(".mp4") || urlLower.contains(".m3u8") ||
            urlLower.contains(".m3u") || urlLower.contains(".mkv") ||
            urlLower.contains(".webm") || urlLower.contains(".mov") ||
            urlLower.contains(".bin")) {
            return true
        }

        if (host.contains("streamtape.com") || host.contains("dood") ||
            host.contains("mixdrop") || host.contains("voe.sx") ||
            host.contains("fembed") || host.contains("googlevideo.com") ||
            host.contains("acek-cdn.com") ||
            host.contains("mediafire.com")) {
            return true
        }

        if (path.contains("get_video") || path.contains("videoplayback") || path.contains("stream")) {
            return true
        }

        return false
    }

    private fun createPopupContainer(): FrameLayout {
        val context = this@BrowserActivity
        isPopupMinimized = true

        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(160),
                dpToPx(40)
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dpToPx(16), dpToPx(80))
            }
        }

        val cardView = androidx.cardview.widget.CardView(context).apply {
            radius = dpToPx(12).toFloat()
            cardElevation = dpToPx(8).toFloat()
            setCardBackgroundColor(Color.parseColor("#222230"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#2D2D3F"))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
        }

        val titleTv = TextView(context).apply {
            text = "Anuncio"
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(dpToPx(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
        }
        header.addView(titleTv)

        btnTogglePopup = Button(context).apply {
            text = "➕"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = null
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
            setOnClickListener {
                toggleMinimizePopup()
            }
        }
        header.addView(btnTogglePopup)

        val btnClose = Button(context).apply {
            text = "❌"
            textSize = 10f
            setTextColor(Color.WHITE)
            background = null
            layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(40))
            setOnClickListener {
                destroyPopup()
            }
        }
        header.addView(btnClose)

        mainLayout.addView(header)

        makeHeaderDraggable(header, container)

        val secWebView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.supportMultipleWindows()
            settings.userAgentString = webView.settings.userAgentString
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
        }

        popupWebView = secWebView
        mainLayout.addView(secWebView)
        cardView.addView(mainLayout)
        container.addView(cardView)

        return container
    }

    private fun toggleMinimizePopup() {
        val container = popupContainer ?: return
        val params = container.layoutParams as FrameLayout.LayoutParams
        if (isPopupMinimized) {
            params.width = dpToPx(280)
            params.height = dpToPx(380)
            popupWebView?.visibility = View.VISIBLE
            btnTogglePopup?.text = "➖"
            isPopupMinimized = false
        } else {
            params.width = dpToPx(160)
            params.height = dpToPx(40)
            popupWebView?.visibility = View.GONE
            btnTogglePopup?.text = "➕"
            isPopupMinimized = true
        }
        container.layoutParams = params
    }

    private fun destroyPopup() {
        val decor = window.decorView as FrameLayout
        popupContainer?.let {
            popupWebView?.stopLoading()
            popupWebView?.destroy()
            popupWebView = null
            decor.removeView(it)
        }
        popupContainer = null
        btnTogglePopup = null
        isPopupMinimized = false
    }

    private fun makeHeaderDraggable(header: View, container: View) {
        var dX = 0f
        var dY = 0f
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = container.x - event.rawX
                    dY = container.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    container.animate()
                        .x(event.rawX + dX)
                        .y(event.rawY + dY)
                        .setDuration(0)
                        .start()
                }
            }
            true
        }
    }

    // --- FUNCIÓN CENTRALIZADA DE EXTRACCIÓN ---
    private fun injectVideoExtractor(view: WebView?) {
        val extractVideoJs = """
            javascript:(function() {
                if (window.videoExtractorLoaded) return;
                window.videoExtractorLoaded = true;

                var isVideo = function(u) {
                    if (typeof u !== 'string') return false;
                    var ul = u.toLowerCase();
                    return ul.includes('.m3u8') || ul.includes('.mp4') || ul.includes('.m3u') || 
                           ul.includes('.bin') || ul.includes('.webm') || ul.includes('.mkv') ||
                           ul.includes('get_video') || ul.includes('videoplayback') || 
                           ul.includes('streamtape') || ul.includes('mixdrop') || 
                           ul.includes('voe.sx') || ul.includes('dood');
                };

                var findVideos = function() {
                    var videos = document.getElementsByTagName('video');
                    for(var i = 0; i < videos.length; i++) {
                        if(videos[i].src && !videos[i].src.startsWith('blob:')) {
                            console.log('VIDEO_ENCONTRADO: ' + videos[i].src);
                        }
                        var sources = videos[i].getElementsByTagName('source');
                        for(var j = 0; j < sources.length; j++) {
                            if(sources[j].src) console.log('VIDEO_ENCONTRADO: ' + sources[j].src);
                        }
                    }
                };
                findVideos();
                setInterval(findVideos, 1500); 

                var originalFetch = window.fetch;
                window.fetch = function() {
                    var fetchUrl = arguments[0];
                    if (typeof fetchUrl === 'string' && isVideo(fetchUrl)) {
                        console.log('VIDEO_ENCONTRADO: ' + fetchUrl);
                    }
                    return originalFetch.apply(this, arguments);
                };

                var originalOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, xhrUrl) {
                    if (typeof xhrUrl === 'string' && isVideo(xhrUrl)) {
                        console.log('VIDEO_ENCONTRADO: ' + xhrUrl);
                    }
                    originalOpen.apply(this, arguments);
                };
            })();
        """.trimIndent()
        view?.evaluateJavascript(extractVideoJs, null)
    }

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.userAgentString = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Safari/605.1.15"

        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.setDownloadListener { downloadUrl, userAgentHeader, _, _, _ ->
            val referer = webView.url ?: ""
            val cookie = CookieManager.getInstance().getCookie(downloadUrl)

            val isVideoDownload = downloadUrl.lowercase().contains(".mp4") ||
                    downloadUrl.lowercase().contains(".bin") ||
                    downloadUrl.lowercase().contains(".m3u8") ||
                    downloadUrl.lowercase().contains("mediafire.com")

            if (isVideoDownload) {
                val capturedLink = CapturedLink(downloadUrl, userAgentHeader, referer, cookie)
                runOnUiThread {
                    if (capturedLinksList.add(capturedLink)) {
                        lastCapturedUrl = downloadUrl
                        Toast.makeText(this@BrowserActivity, "📥 ENLACE DIRECTO CAPTURADO", Toast.LENGTH_LONG).show()
                        updateFloatingButtonVisibility()
                        showCapturedLinksDialog()
                    }
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                lastCapturedUrl = ""
                captureDialog?.dismiss()
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                val host = request?.url?.host?.lowercase() ?: ""
                val method = request?.method ?: ""

                for (domain in blacklistedDomains) {
                    if (host.contains(domain)) {
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }
                }

                if (method.equals("GET", ignoreCase = true)) {
                    if (isPotentialVideoUrl(url)) {
                        val headers = request?.requestHeaders ?: emptyMap()
                        processDetectedLink(url, headers)
                    }
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                val host = request?.url?.host?.lowercase() ?: ""

                for (domain in blacklistedDomains) {
                    if (host.contains(domain)) return true
                }

                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                etUrl.setText(url)
                url?.let {
                    val uri = Uri.parse(it)
                    uri.host?.let { host -> mainDomain = host }
                }
                cleanOverlays()
                injectVideoExtractor(view)
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                handler?.proceed()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            // Se integra la inyección del extractor durante el proceso de carga de la web
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress > 40) {
                    injectVideoExtractor(view)
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = consoleMessage?.message() ?: ""
                if (message.startsWith("VIDEO_ENCONTRADO: ")) {
                    val videoUrl = message.removePrefix("VIDEO_ENCONTRADO: ")
                    val fakeHeaders = mapOf(
                        "User-Agent" to webView.settings.userAgentString,
                        "Referer" to (webView.url ?: "")
                    )
                    processDetectedLink(videoUrl, fakeHeaders)
                }
                return super.onConsoleMessage(consoleMessage)
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                destroyPopup()

                val decor = window.decorView as FrameLayout
                val container = createPopupContainer()
                popupContainer = container

                decor.addView(container)

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = popupWebView
                resultMsg?.sendToTarget()

                Toast.makeText(this@BrowserActivity, "Ad encapsulado en segundo plano", Toast.LENGTH_SHORT).show()
                return true
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback

                val decor = window.decorView as FrameLayout
                decor.addView(view, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                if (customView == null) return

                val decor = window.decorView as FrameLayout
                decor.removeView(customView)
                customView = null
                customViewCallback?.onCustomViewHidden()
                webView.visibility = View.VISIBLE
            }
        }
    }

    private fun isValidVideoUrl(url: String): Boolean {
        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
        val host = uri?.host?.lowercase() ?: ""
        val path = uri?.path?.lowercase() ?: ""

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return false
        }

        for (domain in blacklistedDomains) {
            if (host.contains(domain)) return false
        }

        if (path.contains("subtitle") || path.contains(".vtt") || path.contains("audio-only")) {
            return false
        }

        // Se eliminan 'index-v' y 'chunklist' para permitir la captura de resoluciones alternativas/secundarias
        if (path.contains("seg-") ||
            path.contains("fragment") ||
            (path.endsWith(".ts"))) {
            return false
        }

        return true
    }

    private fun reconstructMasterUrl(url: String): String? {
        val uri = try { Uri.parse(url) } catch (e: Exception) { return null }
        val lastSegment = uri.lastPathSegment ?: ""

        if (lastSegment.equals("master.m3u8", ignoreCase = true)) {
            return null
        }

        if (lastSegment.endsWith(".m3u8", ignoreCase = true)) {
            if (lastSegment.startsWith("index", ignoreCase = true) ||
                lastSegment.contains("chunklist", ignoreCase = true) ||
                lastSegment.contains("variant", ignoreCase = true) ||
                lastSegment.contains("mono", ignoreCase = true)) {

                val path = uri.path ?: ""
                if (path.contains("/")) {
                    val newPath = path.substringBeforeLast("/") + "/master.m3u8"
                    return uri.buildUpon().path(newPath).build().toString()
                }
            }
        }
        return null
    }

    private fun processDetectedLink(url: String, headers: Map<String, String>) {
        val finalUrl = reconstructMasterUrl(url) ?: url

        if (finalUrl == lastCapturedUrl) return
        if (capturedLinksList.any { it.url == finalUrl }) return
        if (!isValidVideoUrl(finalUrl)) return

        val userAgent = headers.entries.firstOrNull { it.key.equals("user-agent", ignoreCase = true) }?.value
            ?: webView.settings.userAgentString
        val referer = headers.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value
            ?: webView.url ?: ""
        val cookie = headers.entries.firstOrNull { it.key.equals("cookie", ignoreCase = true) }?.value
            ?: CookieManager.getInstance().getCookie(url)

        val capturedLink = CapturedLink(finalUrl, userAgent, referer, cookie)

        if (finalUrl.contains("acek-cdn.com", ignoreCase = true)) {
            confirmAndCapture(capturedLink, "🎬 PELÍCULA CAPTURADA (ACEK)")
            return
        }

        if (finalUrl.contains(".mp4", ignoreCase = true)) {
            thread {
                try {
                    val retriever = MediaMetadataRetriever()
                    val headersMap = HashMap<String, String>()
                    headersMap["User-Agent"] = userAgent
                    if (referer.isNotEmpty()) headersMap["Referer"] = referer

                    retriever.setDataSource(finalUrl, headersMap)
                    val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durationMs = time?.toLong() ?: 0
                    retriever.release()

                    val cuarentaMinutos = 2400000
                    if (durationMs >= cuarentaMinutos) {
                        confirmAndCapture(capturedLink, "🎬 VIDEO LARGO DETECTADO (>40 min)")
                    }
                } catch (e: Exception) {
                    confirmAndCapture(capturedLink, "✅ VIDEO MP4 CAPTURADO (Directo / Protegido)")
                }
            }
            return
        }

        confirmAndCapture(capturedLink, "📡 ENLACE MULTIMEDIA CAPTURADO")
    }

    private fun confirmAndCapture(link: CapturedLink, mensaje: String) {
        lastCapturedUrl = link.url
        runOnUiThread {
            if (capturedLinksList.add(link)) {
                Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                updateFloatingButtonVisibility()
                showCapturedLinksDialog()
            }
        }
    }

    private fun releaseExoPlayer() {
        exoPlayer?.let { player ->
            player.stop()
            player.release()
        }
        exoPlayer = null
    }

    fun obtenerCaducidadDeUrl(url: String): String {
        try {
            val uri = Uri.parse(url)
            var expirationTimestamp: Long? = null

            val sParam = uri.getQueryParameter("s")
            val eParam = uri.getQueryParameter("e")

            if (sParam != null && sParam.length == 10 && sParam.all { it.isDigit() }) {
                val start = sParam.toLong()
                if (eParam != null && eParam.all { it.isDigit() }) {
                    val duration = eParam.toLong()
                    if (duration < 2592000) {
                        expirationTimestamp = start + duration
                    } else {
                        expirationTimestamp = start
                    }
                } else {
                    expirationTimestamp = start
                }
            }

            if (expirationTimestamp == null) {
                val pathSegments = uri.pathSegments
                val routeTimestamp = pathSegments.firstOrNull { segment ->
                    segment.length == 10 && segment.all { it.isDigit() }
                }

                if (routeTimestamp != null) {
                    expirationTimestamp = routeTimestamp.toLong()
                } else {
                    val queryNames = uri.queryParameterNames
                    for (name in queryNames) {
                        val value = uri.getQueryParameter(name) ?: ""
                        if (value.length == 10 && value.all { it.isDigit() }) {
                            expirationTimestamp = value.toLong()
                            break
                        }
                    }
                }
            }

            if (expirationTimestamp != null) {
                val timestampMilisegundos = expirationTimestamp * 1000

                val tiempoActual = System.currentTimeMillis()
                if (timestampMilisegundos < tiempoActual) {
                    return "⚠️ Enlace ya caducado"
                }

                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                sdf.timeZone = TimeZone.getDefault()
                val fechaLegible = sdf.format(Date(timestampMilisegundos))

                val diferenciaHoras = (timestampMilisegundos - tiempoActual) / (1000 * 60 * 60)

                return "Vence el: $fechaLegible (Quedan aprox. $diferenciaHoras horas)"
            }
        } catch (e: Exception) { }
        return "Caducidad desconocida / Enlace sin token de tiempo"
    }

    private fun analizarCompatibilidadExterna(context: Context, link: CapturedLink, onComplete: () -> Unit) {
        if (link.ipLockStatus != "🔄 Analizando compatibilidad...") {
            return
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val usandoWiFi = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        val redParaPrueba = if (usandoWiFi) {
            NetworkCapabilities.TRANSPORT_CELLULAR
        } else {
            NetworkCapabilities.TRANSPORT_WIFI
        }

        val nombreRedContraria = if (usandoWiFi) "Datos Móviles" else "Wi-Fi"

        val builder = NetworkRequest.Builder()
        builder.addTransportType(redParaPrueba)
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                thread {
                    ejecutarPruebaPorRedAlterna(network, link, connectivityManager, this, onComplete)
                }
            }

            override fun onUnavailable() {
                runOnUiThread {
                    link.ipLockStatus = "⚠️ Enciende tu $nombreRedContraria para la prueba externa"
                    onComplete()
                }
            }
        }

        try {
            connectivityManager.requestNetwork(builder.build(), networkCallback, 5000)
        } catch (e: SecurityException) {
            link.ipLockStatus = "❌ Faltan permisos de red en la app"
            onComplete()
        }
    }

    private fun ejecutarPruebaPorRedAlterna(
        alternateNetwork: Network,
        link: CapturedLink,
        connectivityManager: ConnectivityManager,
        callback: ConnectivityManager.NetworkCallback,
        onComplete: () -> Unit
    ) {
        try {
            val SAFARI_OSX_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Safari/605.1.15"

            var urlReal = link.url
            val isPlaylist = link.url.lowercase().contains(".m3u8") || link.url.lowercase().contains(".m3u")

            if (isPlaylist) {
                val localConn = URL(link.url).openConnection() as HttpURLConnection
                localConn.connectTimeout = 5000
                localConn.readTimeout = 5000
                localConn.setRequestProperty("User-Agent", SAFARI_OSX_AGENT)
                if (!link.referer.isNullOrEmpty()) localConn.setRequestProperty("Referer", link.referer)

                if (localConn.responseCode == 200) {
                    val content = localConn.inputStream.bufferedReader().use { it.readText() }
                    val innerLine = content.lines().firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }?.trim()
                    if (!innerLine.isNullOrEmpty()) {
                        urlReal = if (innerLine.startsWith("http", ignoreCase = true)) {
                            innerLine
                        } else {
                            URL(URL(link.url), innerLine).toString()
                        }
                    }
                }
                localConn.disconnect()
            }

            val testConn = alternateNetwork.openConnection(URL(urlReal)) as HttpURLConnection
            testConn.requestMethod = "GET"
            testConn.connectTimeout = 8000
            testConn.readTimeout = 8000
            testConn.setRequestProperty("User-Agent", SAFARI_OSX_AGENT)
            testConn.setRequestProperty("Accept", "*/*")
            if (!link.referer.isNullOrEmpty()) {
                testConn.setRequestProperty("Referer", link.referer)
            }

            val code = testConn.responseCode
            val contentType = testConn.contentType?.lowercase() ?: ""

            val esVideo = contentType.contains("video") ||
                    contentType.contains("mpegurl") ||
                    contentType.contains("application/octet-stream") ||
                    contentType.contains("application/vnd.apple.mpegurl")

            runOnUiThread {
                if (code == 200 && esVideo) {
                    link.ipLockStatus = "✅ Enlace 100% Libre (Reproducible en cualquier red)"
                } else if (code == 403 || code == 401) {
                    link.ipLockStatus = "🚫 Candado Confirmado: Atado a tu red actual"
                } else {
                    link.ipLockStatus = "❓ Error externo ($code) - Posible geobloqueo o caída"
                }
                onComplete()
            }
            testConn.disconnect()

        } catch (e: Exception) {
            runOnUiThread {
                link.ipLockStatus = "❌ Error en prueba externa: Timeout o red inestable"
                onComplete()
            }
        } finally {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    @OptIn(UnstableApi::class)
    private fun showCapturedLinksDialog() {
        if (isFinishing || isDestroyed) return

        captureDialog?.dismiss()
        releaseExoPlayer()

        val linksArray = capturedLinksList.toList()
        if (linksArray.isEmpty()) return

        val builder = AlertDialog.Builder(this)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setBackgroundColor(Color.parseColor("#1A1A24"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        val titleTextView = TextView(this).apply {
            text = "📡 Enlaces de Video Capturados"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpToPx(12))
        }
        container.addView(titleTextView)

        val videoCard = androidx.cardview.widget.CardView(this).apply {
            radius = dpToPx(12).toFloat()
            cardElevation = dpToPx(6).toFloat()
            setCardBackgroundColor(Color.BLACK)
            preventCornerOverlap = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(240)
            ).apply {
                setMargins(0, 0, 0, dpToPx(12))
            }
        }

        val playerView = androidx.media3.ui.PlayerView(this).apply {
            useController = false
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
        videoCard.addView(playerView)
        container.addView(videoCard)

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val playerInstance = androidx.media3.exoplayer.ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .build()
        exoPlayer = playerInstance
        playerView.player = playerInstance

        val controlLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dpToPx(4), 0, dpToPx(8))
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnPlayPause = Button(this).apply {
            text = "⏸"
            setTextColor(Color.WHITE)
            textSize = 16f
            val btnShape = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF"))
                cornerRadius = dpToPx(12).toFloat()
            }
            background = btnShape
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(44),
                dpToPx(44)
            ).apply {
                setMargins(0, 0, dpToPx(12), 0)
            }
        }
        controlLayout.addView(btnPlayPause)

        val seekBar = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2979FF"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2979FF"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        controlLayout.addView(seekBar)
        container.addView(controlLayout)

        val listView = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.parseColor("#2C2C3C"))
            dividerHeight = dpToPx(1)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f)
        }
        container.addView(listView)

        val adapter = object : ArrayAdapter<CapturedLink>(this, android.R.layout.simple_list_item_2, android.R.id.text1, linksArray) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView1 = view.findViewById<TextView>(android.R.id.text1)
                val textView2 = view.findViewById<TextView>(android.R.id.text2)

                val item = linksArray[position]

                textView1.text = "${position + 1}. ${item.url}"
                textView1.setTextColor(Color.parseColor("#E0E0E0"))
                textView1.textSize = 13f

                val caducidadInfo = obtenerCaducidadDeUrl(item.url)
                val compatibilidadIp = item.ipLockStatus

                textView2.text = "$caducidadInfo\n$compatibilidadIp"
                textView2.textSize = 11f
                textView2.setPadding(0, dpToPx(2), 0, 0)

                if (caducidadInfo.contains("⚠️") || compatibilidadIp.contains("⚠️") || compatibilidadIp.contains("❌")) {
                    textView2.setTextColor(Color.parseColor("#FF5252"))
                } else if (compatibilidadIp.contains("✅")) {
                    textView2.setTextColor(Color.parseColor("#4CAF50"))
                } else {
                    textView2.setTextColor(Color.parseColor("#90A4AE"))
                }

                return view
            }
        }
        listView.adapter = adapter

        linksArray.forEach { item ->
            analizarCompatibilidadExterna(this, item) {
                adapter.notifyDataSetChanged()
            }
        }

        val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateProgressTask = object : Runnable {
            override fun run() {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        seekBar.progress = player.currentPosition.toInt()
                    }
                }
                progressHandler.postDelayed(this, 1000)
            }
        }

        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(12), 0, 0)
        }

        val btnLimpiar = Button(this).apply {
            text = "Limpiar"
            setTextColor(Color.parseColor("#FF5252"))
            background = null
            setOnClickListener {
                progressHandler.removeCallbacksAndMessages(null)
                releaseExoPlayer()
                capturedLinksList.clear()
                updateFloatingButtonVisibility()
                Toast.makeText(this@BrowserActivity, "Lista de capturas vaciada", Toast.LENGTH_SHORT).show()
                captureDialog?.dismiss()
            }
        }

        val btnCasa = Button(this).apply {
            text = "Ir a Casa"
            setTextColor(Color.parseColor("#90A4AE"))
            background = null
            setOnClickListener {
                exoPlayer?.pause()
                btnPlayPause.text = "▶"
                progressHandler.removeCallbacksAndMessages(null)
                goHomeWithoutFinishing()
            }
        }

        val btnCerrar = Button(this).apply {
            text = "Cerrar"
            setTextColor(Color.parseColor("#2979FF"))
            background = null
            setOnClickListener {
                progressHandler.removeCallbacksAndMessages(null)
                releaseExoPlayer()
                captureDialog?.dismiss()
            }
        }

        actionsLayout.addView(btnLimpiar)
        actionsLayout.addView(btnCasa)
        actionsLayout.addView(btnCerrar)
        container.addView(actionsLayout)

        builder.setView(container)
        captureDialog = builder.create()

        captureDialog?.setCancelable(false)
        captureDialog?.setCanceledOnTouchOutside(false)
        captureDialog?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        captureDialog?.show()

        captureDialog?.window?.let { window ->
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    exoPlayer?.seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnPlayPause.setOnClickListener {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    btnPlayPause.text = "▶"
                } else {
                    player.play()
                    btnPlayPause.text = "⏸"
                }
            }
        }

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val capturedItem = linksArray[position]

            val clipboardFormat = capturedItem.getFormattedUrlForClipboard()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("CapturedLink", clipboardFormat)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this@BrowserActivity, "📋 Copiado y probando en reproductor local", Toast.LENGTH_SHORT).show()
            val progressToast = Toast.makeText(this@BrowserActivity, "Cargando flujo con ExoPlayer...", Toast.LENGTH_SHORT)
            progressToast.show()

            val secureHeaders = LocalAgentBuilder.getSecureHeaders(capturedItem)

            try {
                progressHandler.removeCallbacks(updateProgressTask)
                exoPlayer?.stop()
                btnPlayPause.text = "⏸"
                seekBar.progress = 0

                val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Safari/605.1.15")
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)

                val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(httpDataSourceFactory)
                    .createMediaSource(androidx.media3.common.MediaItem.fromUri(Uri.parse(capturedItem.url)))

                exoPlayer?.setMediaSource(mediaSource)
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true

                exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            androidx.media3.common.Player.STATE_READY -> {
                                progressToast.cancel()
                                seekBar.max = exoPlayer?.duration?.toInt() ?: 0
                                progressHandler.post(updateProgressTask)
                            }
                            androidx.media3.common.Player.STATE_ENDED -> {
                                btnPlayPause.text = "▶"
                            }
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        progressToast.cancel()
                        progressHandler.removeCallbacks(updateProgressTask)
                        Toast.makeText(this@BrowserActivity, "⚠️ Error al reproducir el flujo en ExoPlayer", Toast.LENGTH_SHORT).show()
                    }
                })

            } catch (e: Exception) {
                progressToast.cancel()
                progressHandler.removeCallbacks(updateProgressTask)
            }
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val capturedItem = linksArray[position]
            AlertDialog.Builder(this)
                .setTitle("¿Eliminar enlace de la lista?")
                .setMessage(capturedItem.url)
                .setPositiveButton("Eliminar") { _, _ ->
                    exoPlayer?.stop()
                    progressHandler.removeCallbacksAndMessages(null)
                    seekBar.progress = 0
                    btnPlayPause.text = "⏸"

                    capturedLinksList.remove(capturedItem)
                    updateFloatingButtonVisibility()

                    captureDialog?.dismiss()
                    if (capturedLinksList.isNotEmpty()) {
                        showCapturedLinksDialog()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }
    }


    private fun saveFavorite(url: String) {
        val list = getFavoritesListJSON()
        if (list.any { it.url == url }) {
            Toast.makeText(this, "Esta página ya es favorita", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
        val defaultName = uri?.host ?: "Favorito"

        val input = EditText(this).apply {
            setText(defaultName)
            setSelection(defaultName.length)
            setTextColor(Color.BLACK)
        }

        AlertDialog.Builder(this)
            .setTitle("Guardar Favorito")
            .setMessage("Asigna un nombre para identificarlo:")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val customName = input.text.toString().trim().ifEmpty { defaultName }
                list.add(FavoriteItem(customName, url))
                saveFavoritesListJSON(list)
                Toast.makeText(this, "⭐ Guardado en favoritos", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun removeFavorite(url: String) {
        val list = getFavoritesListJSON()
        val removed = list.removeAll { it.url == url }
        if (removed) {
            saveFavoritesListJSON(list)
            Toast.makeText(this, "🗑️ Eliminado de favoritos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFavoritesDialog() {
        val favs = getFavoritesListJSON()
        if (favs.isEmpty()) {
            Toast.makeText(this, "No tienes favoritos guardados", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            val dialogBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A24"))
                cornerRadius = 24f
            }
            background = dialogBg
        }

        val headerTv = TextView(this).apply {
            text = "⭐ Mis Favoritos"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        container.addView(headerTv)

        val listView = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.parseColor("#2C2C3C"))
            dividerHeight = dpToPx(1)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(300)
            ).apply { weight = 1f }
        }
        container.addView(listView)

        val adapter = object : ArrayAdapter<FavoriteItem>(this, android.R.layout.simple_list_item_2, android.R.id.text1, favs) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView1 = view.findViewById<TextView>(android.R.id.text1)
                val textView2 = view.findViewById<TextView>(android.R.id.text2)

                val item = favs[position]
                textView1.text = "⭐ " + item.title
                textView1.setTextColor(Color.WHITE)
                textView1.textSize = 14f

                textView2.text = item.url
                textView2.setTextColor(Color.parseColor("#90A4AE"))
                textView2.textSize = 10f
                textView2.setPadding(0, dpToPx(2), 0, 0)

                return view
            }
        }
        listView.adapter = adapter

        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dpToPx(12), 0, 0)
        }

        var dialog: AlertDialog? = null
        val btnCerrar = Button(this).apply {
            text = "Cerrar"
            setTextColor(Color.parseColor("#2979FF"))
            background = null
            setOnClickListener { dialog?.dismiss() }
        }
        actionsLayout.addView(btnCerrar)
        container.addView(actionsLayout)

        builder.setView(container)
        dialog = builder.create()
        dialog?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog?.show()

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedItem = favs[position]
            webView.loadUrl(selectedItem.url)
            dialog?.dismiss()
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val selectedItem = favs[position]

            val opciones = arrayOf(
                "✏️ Editar Nombre",
                "⬆️ Mover Arriba",
                "⬇️ Mover Abajo",
                "🗑️ Eliminar"
            )

            AlertDialog.Builder(this@BrowserActivity)
                .setTitle(selectedItem.title)
                .setItems(opciones) { _, which ->
                    when (which) {
                        0 -> {
                            dialog?.dismiss()
                            showEditFavoriteNameDialog(position, favs)
                        }
                        1 -> {
                            if (position > 0) {
                                val temp = favs[position]
                                favs[position] = favs[position - 1]
                                favs[position - 1] = temp
                                saveFavoritesListJSON(favs)
                                dialog?.dismiss()
                                showFavoritesDialog()
                            } else {
                                Toast.makeText(this@BrowserActivity, "Ya está en la cima", Toast.LENGTH_SHORT).show()
                            }
                        }
                        2 -> {
                            if (position < favs.size - 1) {
                                val temp = favs[position]
                                favs[position] = favs[position + 1]
                                favs[position + 1] = temp
                                saveFavoritesListJSON(favs)
                                dialog?.dismiss()
                                showFavoritesDialog()
                            } else {
                                Toast.makeText(this@BrowserActivity, "Ya está al final", Toast.LENGTH_SHORT).show()
                            }
                        }
                        3 -> {
                            removeFavorite(selectedItem.url)
                            favs.removeAt(position)
                            saveFavoritesListJSON(favs)
                            dialog?.dismiss()
                            showFavoritesDialog()
                        }
                    }
                }
                .show()
            true
        }
    }

    private fun showEditFavoriteNameDialog(position: Int, favs: MutableList<FavoriteItem>) {
        val item = favs[position]
        val input = EditText(this).apply {
            setText(item.title)
            setSelection(item.title.length)
            setTextColor(Color.BLACK)
        }

        AlertDialog.Builder(this)
            .setTitle("Editar Nombre")
            .setMessage("Escribe el nuevo nombre de identificación:")
            .setView(input)
            .setPositiveButton("Actualizar") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    favs[position] = FavoriteItem(newName, item.url)
                    saveFavoritesListJSON(favs)
                    showFavoritesDialog()
                }
            }
            .setNegativeButton("Cancelar") { _, _ ->
                showFavoritesDialog()
            }
            .show()
    }

    private fun getFavoritesListJSON(): MutableList<FavoriteItem> {
        val jsonString = sharedPreferences.getString("fav_list_json", null) ?: return mutableListOf()
        val list = mutableListOf<FavoriteItem>()
        try {
            val jsonArray = org.json.JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(FavoriteItem(obj.getString("title"), obj.getString("url")))
            }
        } catch (e: Exception) { }
        return list
    }

    private fun saveFavoritesListJSON(list: List<FavoriteItem>) {
        val jsonArray = org.json.JSONArray()
        for (item in list) {
            val obj = org.json.JSONObject()
            obj.put("title", item.title)
            obj.put("url", item.url)
            jsonArray.put(obj)
        }
        sharedPreferences.edit().putString("fav_list_json", jsonArray.toString()).apply()
    }

    override fun onBackPressed() {
        if (popupContainer != null) {
            destroyPopup()
            return
        }
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        destroyPopup()
        releaseExoPlayer()
        super.onDestroy()
    }
}