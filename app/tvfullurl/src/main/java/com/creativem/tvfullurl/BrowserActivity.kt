package com.creativem.tvfullurl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.webkit.*
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.media.MediaMetadataRetriever
import kotlin.concurrent.thread
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
    private var mainDomain: String = "minochinos.com"
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
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

    private fun setupButtons() {
        btnGo.setOnClickListener {
            val url = etUrl.text.toString()
            if (url.isNotEmpty()) {
                val finalUrl = if (url.startsWith("http")) url else "https://$url"
                lastCapturedUrl = ""
                webView.loadUrl(finalUrl)
            }
        }
        btnBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        btnRefresh.setOnClickListener { webView.reload() }

        // BOTÓN CASA: Cierra esta actividad y vuelve al Fragment anterior
        btnHome.setOnClickListener {
            finish()
        }

        btnSaveFav.setOnClickListener {
            webView.url?.let { saveFavorite(it) }
        }

        btnListFav.setOnClickListener {
            showFavoritesDialog()
        }
    }

//    private fun setupWebView() {
//        val settings = webView.settings
//        settings.javaScriptEnabled = true
//        settings.domStorageEnabled = true
//        settings.databaseEnabled = true
//        settings.setSupportMultipleWindows(true) // Permitir para capturarlas nosotros
//        settings.javaScriptCanOpenWindowsAutomatically = false
//
//        // User Agent de Desktop para que el botón de descarga cargue siempre
//        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
//
//        webView.webViewClient = object : WebViewClient() {
//
//            // 1. BLOQUEADOR DE DOMINIOS DE PUBLICIDAD
//            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
//                val url = request?.url.toString().lowercase()
//                val adDomains = arrayOf("doubleclick", "adsterra", "popads", "exoclick", "onclickads", "popcash", "propellerads", "clktraffic")
//
//                for (domain in adDomains) {
//                    if (url.contains(domain)) return WebResourceResponse("text/plain", "UTF-8", null)
//                }
//
//                // OLFATEO DE VIDEO EN SEGUNDO PLANO
//                if (url.contains("acek-cdn.com") || url.contains(".mp4") || url.contains(".m3u8")) {
//                    if (url.contains("?")) processDetectedLink(url)
//                }
//
//                return super.shouldInterceptRequest(view, request)
//            }
//
//            // 2. CONTROL DE NAVEGACIÓN
//            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
//                val url = request?.url.toString()
//
//                // Si es video, capturar
//                if (url.contains("acek-cdn.com") || url.contains(".mp4") || url.contains(".m3u8")) {
//                    processDetectedLink(url)
//                    return true
//                }
//
//                // Si el usuario hizo clic, dejarlo pasar (esto arregla el botón que no cargaba)
//                if (request?.hasGesture() == true) {
//                    return false
//                }
//
//                // Bloquear redirecciones automáticas de publicidad
//                if (!url.contains(mainDomain) && !url.contains("google")) return true
//
//                return false
//            }
//
//            override fun onLoadResource(view: WebView?, url: String?) {
//                super.onLoadResource(view, url)
//                // Inyectar limpiador de capas cada vez que cargue un recurso
//                cleanOverlays()
//            }
//
//            override fun onPageFinished(view: WebView?, url: String?) {
//                super.onPageFinished(view, url)
//                etUrl.setText(url)
//                url?.let {
//                    val uri = android.net.Uri.parse(it)
//                    uri.host?.let { host -> mainDomain = host }
//                }
//                cleanOverlays()
//            }
//        }
//
//        webView.webChromeClient = object : WebChromeClient() {
//            // 3. MANEJO DE VENTANAS NUEVAS (FORZAR A CARGAR AQUÍ)
//            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
//                val result = view?.hitTestResult
//                val url = result?.extra
//
//                if (isUserGesture && url != null) {
//                    // Si el usuario hizo clic en algo que abre ventana, lo cargamos en el mismo webview
//                    view.loadUrl(url)
//                    return true
//                }
//
//                // Si es un popup automático, lo ignoramos
//                return false
//            }
//        }
//    }

    private fun cleanOverlays() {
        val script = """
            (function() {
                // Eliminar elementos con z-index alto o posición fija (anuncios flotantes)
                var elems = document.body.getElementsByTagName('*');
                for (var i = 0; i < elems.length; i++) {
                    var style = window.getComputedStyle(elems[i]);
                    if (parseInt(style.zIndex) > 100 || style.position == 'fixed') {
                        if (!elems[i].innerHTML.contains('video') && !elems[i].innerHTML.contains('Download')) {
                            elems[i].remove();
                        }
                    }
                }
                // Eliminar capas invisibles que tapan botones
                document.querySelectorAll('div').forEach(el => {
                    if (el.style.position == 'absolute' && el.style.zIndex > 10) {
                        el.remove();
                    }
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                val urlLower = url.lowercase()

                // Bloqueo de publicidad
                val adDomains = arrayOf("doubleclick", "adsterra", "popads", "exoclick", "onclickads", "popcash")
                for (domain in adDomains) {
                    if (urlLower.contains(domain)) return WebResourceResponse("text/plain", "UTF-8", null)
                }

                // CAPTURA: Si es m3u8 no exigimos el "?" porque muchas listas no lo usan
                if (urlLower.contains(".m3u8")) {
                    processDetectedLink(url)
                }
                // Si es MP4 o ACEK, normalmente sí llevan token de seguridad
                else if (urlLower.contains(".mp4") || urlLower.contains("acek-cdn.com")) {
                    if (url.contains("?")) processDetectedLink(url)
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                val urlLower = url.lowercase()

                if (urlLower.contains(".mp4") || urlLower.contains(".m3u8") || urlLower.contains("acek-cdn.com")) {
                    processDetectedLink(url)
                    return true
                }

                if (request?.hasGesture() == true) return false
                if (mainDomain.isNotEmpty() && !url.contains(mainDomain) && !url.contains("google")) return true

                return false
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                cleanOverlays()

                // OLFATEO EN TIEMPO REAL (Muy importante para M3U8 al dar Play)
                url?.let {
                    val uLower = it.lowercase()
                    if (uLower.contains(".m3u8") || (uLower.contains(".mp4") && it.contains("?"))) {
                        processDetectedLink(it)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                etUrl.setText(url)
                url?.let {
                    val uri = android.net.Uri.parse(it)
                    uri.host?.let { host -> mainDomain = host }
                }
                cleanOverlays()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                val result = view?.hitTestResult
                val url = result?.extra
                if (url != null) {
                    if (url.lowercase().contains(".mp4") || url.lowercase().contains(".m3u8")) {
                        processDetectedLink(url)
                        return true
                    }
                    view.loadUrl(url)
                    return true
                }
                return false
            }
        }
    }

    private fun processDetectedLink(url: String) {
        if (url == lastCapturedUrl) return

        // 1. Prioridad Absoluta: Si el link es de acek-cdn, ya sabemos que es la película
        if (url.contains("acek-cdn.com", ignoreCase = true)) {
            confirmAndCapture(url, "🎬 PELÍCULA CAPTURADA (ACEK)")
            return
        }

        // 2. Si es un MP4 genérico, verificamos su duración en segundo plano
        if (url.contains(".mp4", ignoreCase = true)) {
            thread {
                try {
                    val retriever = MediaMetadataRetriever()
                    // Conectamos al link para obtener la duración
                    retriever.setDataSource(url, HashMap<String, String>())
                    val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durationMs = time?.toLong() ?: 0
                    retriever.release()

                    // 40 minutos en milisegundos (40 * 60 * 1000)
                    val cuarentaMinutos = 2400000

                    if (durationMs >= cuarentaMinutos) {
                        confirmAndCapture(url, "🎬 VIDEO LARGO DETECTADO (>40 min)")
                    } else {
                        // Si dura menos, es publicidad. No hacemos nada.
                        android.util.Log.d("CAPTURA", "Video ignorado por corto: ${durationMs / 1000} seg")
                    }
                } catch (e: Exception) {
                    // Si hay error al obtener duración, pero el link parece real (tiene token ?), capturamos por si acaso
                    if (url.contains("?")) {
                        confirmAndCapture(url, "✅ VIDEO MP4 CAPTURADO")
                    }
                }
            }
            return
        }

        // 3. Si es M3U8 (Streaming), lo capturamos siempre porque suelen ser canales en vivo (sin duración fija)
        if (url.contains(".m3u8", ignoreCase = true)) {
            confirmAndCapture(url, "📡 STREAMING M3U8 CAPTURADO")
        }
    }

    // Función auxiliar para copiar al portapapeles y avisar
    private fun confirmAndCapture(url: String, mensaje: String) {
        lastCapturedUrl = url
        runOnUiThread {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Captura", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        }
    }

    // --- FAVORITOS ---
    private fun saveFavorite(url: String) {
        val favorites = sharedPreferences.getStringSet("fav_urls", emptySet())?.toMutableSet() ?: mutableSetOf()
        if (favorites.add(url)) {
            sharedPreferences.edit().putStringSet("fav_urls", favorites).apply()
            Toast.makeText(this, "⭐ Guardado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFavoritesDialog() {
        val favs = sharedPreferences.getStringSet("fav_urls", emptySet())?.toList() ?: emptyList()
        if (favs.isEmpty()) return
        AlertDialog.Builder(this).setTitle("Favoritos")
            .setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, favs)) { _, which ->
                webView.loadUrl(favs[which])
            }.setNegativeButton("Cerrar", null).show()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}