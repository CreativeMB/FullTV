package com.creativem.tvfullurl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder
import kotlin.concurrent.thread

// --- NUEVO OBJETO: AGENTE CONSTRUCTOR ---
// Su única función es preparar y blindar las cabeceras para engañar al servidor
// y alimentar a tu VideoView local.
object LocalAgentBuilder {
    fun getSecureHeaders(link: CapturedLink): HashMap<String, String> {
        val headers = HashMap<String, String>()

        link.userAgent?.let { headers["User-Agent"] = it }
        link.referer?.let { headers["Referer"] = it }
        link.cookie?.let { headers["Cookie"] = it }

        // Cabeceras adicionales para simular que la petición sigue en el navegador
        headers["Accept"] = "*/*"
        headers["Connection"] = "keep-alive"

        // Extraemos y forzamos el Origin (Vital para servidores con seguridad estricta CORS)
        link.referer?.let { ref ->
            try {
                val uri = Uri.parse(ref)
                headers["Origin"] = "${uri.scheme}://${uri.host}"
            } catch (e: Exception) { }
        }

        return headers
    }
}
// ----------------------------------------

// Clase modelo para estructurar el enlace y sus parámetros de sesión
data class CapturedLink(
    val url: String,
    val userAgent: String?,
    val referer: String?,
    val cookie: String?
) {
    fun getFormattedUrlForClipboard(): String {
        val uri = try { Uri.parse(url) } catch (e: Exception) { null }
        val host = uri?.host?.lowercase() ?: ""

        val protectedHosts = arrayOf("minochinos.com", "acek-cdn.com")
        val isProtected = protectedHosts.any { host.contains(it) }

        if (isProtected) {
            val sb = StringBuilder(url)
            val params = mutableListOf<String>()
            try {
                if (!userAgent.isNullOrEmpty()) {
                    val encodedUA = URLEncoder.encode(userAgent, "UTF-8").replace("+", "%20")
                    params.add("User-Agent=$encodedUA")
                }
                if (!referer.isNullOrEmpty()) {
                    val encodedRef = URLEncoder.encode(referer, "UTF-8").replace("+", "%20")
                    params.add("Referer=$encodedRef")
                }
                if (!cookie.isNullOrEmpty()) {
                    val encodedCookie = URLEncoder.encode(cookie, "UTF-8").replace("+", "%20")
                    params.add("Cookie=$encodedCookie")
                }
            } catch (e: Exception) {
                if (!userAgent.isNullOrEmpty()) params.add("User-Agent=$userAgent")
                if (!referer.isNullOrEmpty()) params.add("Referer=$referer")
            }
            if (params.isNotEmpty()) {
                sb.append("|").append(params.joinToString("&"))
            }
            return sb.toString()
        }

        return url
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

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val capturedLinksList = LinkedHashSet<CapturedLink>()
    private var captureDialog: AlertDialog? = null

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

                // Limpiamos todo antes de ir a la nueva URL
                capturedLinksList.clear()
                lastCapturedUrl = ""
                captureDialog?.dismiss()

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

    private fun cleanOverlays() {
        val script = """
        (function() {
            try {
                // 1. Convertimos los elementos a un Array real para evitar saltos al eliminar
                var elems = Array.from(document.body.querySelectorAll('*'));
                
                elems.forEach(function(el) {
                    var style = window.getComputedStyle(el);
                    
                    // Buscamos elementos flotantes (típico de overlays invisibles y popups)
                    if (style.position === 'fixed' || style.position === 'absolute') {
                        var zIndex = parseInt(style.zIndex);
                        
                        // Si el elemento está muy al frente o cubre toda la pantalla (trampa de clic)
                        if (zIndex > 90 || (style.width === '100%' && style.height === '100%')) {
                            var html = el.innerHTML || '';
                            
                            // CORRECCIÓN: En JavaScript se usa .includes(), NO .contains()
                            var isImportant = html.includes('video') || 
                                              html.includes('Download') || 
                                              el.tagName === 'VIDEO';
                            
                            // Si no contiene el video o el botón, lo aniquilamos
                            if (!isImportant) {
                                // En vez de solo remove(), lo ocultamos también por si falla
                                el.style.display = 'none';
                                el.style.pointerEvents = 'none';
                                el.remove();
                            }
                        }
                    }
                });

                // 2. Limpieza de iframes basura que meten anuncios de apuestas
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

    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = false
        // Simulador de Desktop/Chrome fuerte para evitar capados de servidores móviles
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"

        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        // 1. CAZADOR DE DESCARGAS DIRECTAS
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
                        showCapturedLinksDialog()
                    }
                }
            }
        }

        // 2. INTERCEPTOR DE RED Y SNIFFER DE DOM
        webView.webViewClient = object : WebViewClient() {

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)

                // Vaciamos la lista y la memoria del último enlace
                capturedLinksList.clear()
                lastCapturedUrl = ""

                // Si el diálogo estaba abierto de la página anterior, lo cerramos
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

            // REEMPLAZAR ESTE MÉTODO COMPLETO:
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                val host = request?.url?.host?.lowercase() ?: ""

                for (domain in blacklistedDomains) {
                    if (host.contains(domain)) return true
                }

                return false // Permite la carga fluida de cualquier reproductor o servidor incrustado sin bloquear por gestos
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                etUrl.setText(url)
                url?.let {
                    val uri = Uri.parse(it)
                    uri.host?.let { host -> mainDomain = host }
                }
                cleanOverlays()

                val extractVideoJs = """
                javascript:(function() {
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
                    setInterval(findVideos, 2000); 

                    var originalFetch = window.fetch;
                    window.fetch = function() {
                        var fetchUrl = arguments[0];
                        if (typeof fetchUrl === 'string' && (fetchUrl.includes('.m3u8') || fetchUrl.includes('.mp4'))) {
                            console.log('VIDEO_ENCONTRADO: ' + fetchUrl);
                        }
                        return originalFetch.apply(this, arguments);
                    };

                    var originalOpen = XMLHttpRequest.prototype.open;
                    XMLHttpRequest.prototype.open = function(method, xhrUrl) {
                        if (typeof xhrUrl === 'string' && (xhrUrl.includes('.m3u8') || xhrUrl.includes('.mp4'))) {
                            console.log('VIDEO_ENCONTRADO: ' + xhrUrl);
                        }
                        originalOpen.apply(this, arguments);
                    };
                })();
            """.trimIndent()
                view?.evaluateJavascript(extractVideoJs, null)
            }
        }

        // 3. LECTOR DE CONSOLA (Atrapa los mensajes del Sniffer JS)
        webView.webChromeClient = object : WebChromeClient() {

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

            // REEMPLAZAR ESTE MÉTODO COMPLETO DENTRO DE webChromeClient:
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                // Creamos un WebView temporal en memoria para atrapar el enlace de la publicidad
                val tempWebView = WebView(this@BrowserActivity)
                tempWebView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url.toString()
                        try {
                            // Desviamos el anuncio al navegador predeterminado del dispositivo (Chrome, Samsung Internet, etc.)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        } catch (e: Exception) { }
                        return true // Retornamos true para cancelar la carga interna y no perder el progreso de la película
                    }
                }

                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = tempWebView
                resultMsg?.sendToTarget()
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

        // Descartar subtítulos y fragmentos de audio
        if (path.contains("subtitle") || path.contains(".vtt") || path.contains("audio-only")) {
            return false
        }

        // NUEVO FILTRO: Descartar sub-listas y fragmentos secundarios de m3u8
        if (path.contains("index-v") ||
            path.contains("chunklist") ||
            path.contains("seg-") ||
            path.contains("fragment") ||
            (path.endsWith(".ts"))) {
            return false // Lo ignoramos porque es basura secundaria, el master ya debió pasar o está por pasar.
        }

        return true
    }

    private fun processDetectedLink(url: String, headers: Map<String, String>) {
        val finalUrl = url

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
                    if (finalUrl.contains("?") && isValidVideoUrl(finalUrl)) {
                        confirmAndCapture(capturedLink, "✅ VIDEO MP4 CAPTURADO (Metadata protegida)")
                    }
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
                showCapturedLinksDialog()
            }
        }
    }

    private fun showCapturedLinksDialog() {
        if (isFinishing || isDestroyed) return

        captureDialog?.dismiss()

        val linksArray = capturedLinksList.toList()
        if (linksArray.isEmpty()) return

        val displayItems = linksArray.mapIndexed { index, item ->
            "${index + 1}. ${item.url}"
        }.toTypedArray()

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

        val videoView = VideoView(this)
        val videoParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        videoView.layoutParams = videoParams
        videoCard.addView(videoView)
        container.addView(videoCard)

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

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayItems) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.parseColor("#E0E0E0"))
                textView.textSize = 14f
                textView.setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
                return view
            }
        }
        listView.adapter = adapter

        val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateProgressTask = object : Runnable {
            override fun run() {
                if (videoView.isPlaying) {
                    seekBar.progress = videoView.currentPosition
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
                videoView.stopPlayback()
                capturedLinksList.clear()
                Toast.makeText(this@BrowserActivity, "Lista de capturas vaciada", Toast.LENGTH_SHORT).show()
                captureDialog?.dismiss()
            }
        }

        val btnCasa = Button(this).apply {
            text = "Ir a Casa"
            setTextColor(Color.parseColor("#90A4AE"))
            background = null
            setOnClickListener {
                if (videoView.isPlaying) {
                    videoView.pause()
                    btnPlayPause.text = "▶"
                }
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
                videoView.stopPlayback()
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
                if (fromUser) videoView.seekTo(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        btnPlayPause.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                btnPlayPause.text = "▶"
            } else {
                videoView.start()
                btnPlayPause.text = "⏸"
            }
        }

        // --- EL CORAZÓN DEL REPRODUCTOR LOCAL USANDO EL AGENTE CONSTRUCTOR ---
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val capturedItem = linksArray[position]

            // 1. Copiamos al portapapeles
            val clipboardFormat = capturedItem.getFormattedUrlForClipboard()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Captura", clipboardFormat)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this@BrowserActivity, "📋 Copiado y probando en reproductor local", Toast.LENGTH_SHORT).show()
            val progressToast = Toast.makeText(this@BrowserActivity, "Cargando flujo...", Toast.LENGTH_SHORT)
            progressToast.show()

            // 2. INVOCAMOS AL AGENTE PARA OBTENER LAS CABECERAS SEGURAS
            val secureHeaders = LocalAgentBuilder.getSecureHeaders(capturedItem)

            try {
                // Limpiamos la interfaz antes de cargar
                progressHandler.removeCallbacks(updateProgressTask)
                if (videoView.isPlaying) {
                    videoView.stopPlayback()
                }
                btnPlayPause.text = "⏸"
                seekBar.progress = 0

                // 3. Reproducimos internamente en tu VideoView alimentándolo con el Agente
                videoView.setVideoURI(Uri.parse(capturedItem.url), secureHeaders)

                videoView.setOnPreparedListener { mediaPlayer ->
                    progressToast.cancel()
                    seekBar.max = videoView.duration
                    mediaPlayer.start()
                    progressHandler.post(updateProgressTask)
                }

                videoView.setOnErrorListener { _, _, _ ->
                    progressToast.cancel()
                    progressHandler.removeCallbacks(updateProgressTask)
                    Toast.makeText(this@BrowserActivity, "⚠️ El flujo superó las capacidades del reproductor nativo", Toast.LENGTH_SHORT).show()
                    true
                }
            } catch (e: Exception) {
                progressToast.cancel()
                progressHandler.removeCallbacks(updateProgressTask)
            }
        }
        // ---------------------------------------------------------------------

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val capturedItem = linksArray[position]
            AlertDialog.Builder(this)
                .setTitle("¿Eliminar enlace de la lista?")
                .setMessage(capturedItem.url)
                .setPositiveButton("Eliminar") { _, _ ->
                    videoView.stopPlayback()
                    progressHandler.removeCallbacksAndMessages(null)
                    seekBar.progress = 0
                    btnPlayPause.text = "⏸"

                    capturedLinksList.remove(capturedItem)
                    Toast.makeText(this, "🗑️ Enlace eliminado", Toast.LENGTH_SHORT).show()

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

    private fun getFavoritesList(): Set<String> {
        return sharedPreferences.getStringSet("fav_urls", emptySet()) ?: emptySet()
    }

    private fun saveFavorite(url: String) {
        val favorites = getFavoritesList().toMutableSet()
        if (favorites.add(url)) {
            sharedPreferences.edit().putStringSet("fav_urls", favorites).apply()
            Toast.makeText(this, "⭐ Guardado en favoritos", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Esta página ya es favorita", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeFavorite(url: String) {
        val favorites = getFavoritesList().toMutableSet()
        if (favorites.remove(url)) {
            sharedPreferences.edit().putStringSet("fav_urls", favorites).apply()
            Toast.makeText(this, "🗑️ Eliminado de favoritos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFavoritesDialog() {
        val favs = getFavoritesList().toList()
        if (favs.isEmpty()) {
            Toast.makeText(this, "No tienes favoritos guardados", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 20)
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
            dividerHeight = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f }
        }
        container.addView(listView)

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, favs) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.parseColor("#E0E0E0"))
                textView.textSize = 14f
                textView.setPadding(12, 16, 12, 16)
                return view
            }
        }
        listView.adapter = adapter

        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 20, 0, 0)
        }

        var dialog: AlertDialog? = null
        val btnCerrar = Button(this).apply {
            text = "Cerrar"
            setTextColor(Color.parseColor("#90A4AE"))
            background = null
            setOnClickListener { dialog?.dismiss() }
        }
        actionsLayout.addView(btnCerrar)
        container.addView(actionsLayout)

        builder.setView(container)
        dialog = builder.create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedUrl = favs[position]
            webView.loadUrl(selectedUrl)
            dialog.dismiss()
        }

        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val selectedUrl = favs[position]
            AlertDialog.Builder(this)
                .setTitle("¿Eliminar favorito?")
                .setMessage(selectedUrl)
                .setPositiveButton("Eliminar") { _, _ ->
                    removeFavorite(selectedUrl)
                    dialog.dismiss()
                    showFavoritesDialog()
                }
                .setNegativeButton("Cancelar", null)
                .show()
            true
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}