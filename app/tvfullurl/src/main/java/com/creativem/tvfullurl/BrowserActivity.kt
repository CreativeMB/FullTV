package com.creativem.tvfullurl

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder
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
    private var mainDomain: String = ""
    private lateinit var sharedPreferences: SharedPreferences

    // Variables de control para el soporte de pantalla completa en videos web
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Lista para acumular los enlaces capturados sin duplicados
    private val capturedLinksList = LinkedHashSet<String>()
    private var captureDialog: AlertDialog? = null

    // Lista negra para blindaje contra anuncios (solo aplicada a dominios para no romper tokens)
    private val blacklistedDomains = arrayOf(
        "adsystem", "adserver", "pixel", "analytics", "telemetry", "tracker",
        "beacon", "statcounter", "doubleclick", "adsterra", "exoclick",
        "onclickads", "popcash", "popads", "propellerads", "histats",
        "traffic", "prebid", "vast", "vpaid", "googlesyndication", "google-analytics",
        "adservice", "serving", "advert", "banner", "metric"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // FORZAR ACELERACIÓN POR HARDWARE: Vital para que los videos web no se queden congelados
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
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

    private fun goHomeWithoutFinishing() {
        try {
            val intent = android.content.Intent(this, MainActivity::class.java)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
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
                lastCapturedUrl = ""
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
                var elems = document.body.getElementsByTagName('*');
                for (var i = 0; i < elems.length; i++) {
                    var style = window.getComputedStyle(elems[i]);
                    if (parseInt(style.zIndex) > 100 || style.position == 'fixed') {
                        if (!elems[i].innerHTML.contains('video') && !elems[i].innerHTML.contains('Download')) {
                            elems[i].remove();
                        }
                    }
                }
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

        // OPTIMIZACIONES PARA EVITAR VIDEOS PEGADOS / EN PLAY CONGELADO:
        settings.mediaPlaybackRequiresUserGesture = false // Permite que los videos web arranquen sin bloqueos gestuales
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW // Permite cargar videos HTTP en páginas HTTPS
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true

        // Habilitar soporte de cookies de terceros (requerido por muchos servidores de video)
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url.toString()
                val urlLower = url.lowercase()

                // Bloqueo de publicidad basado únicamente en el Host (dominio)
                val host = request?.url?.host?.lowercase() ?: ""
                for (domain in blacklistedDomains) {
                    if (host.contains(domain)) {
                        return WebResourceResponse("text/plain", "UTF-8", null)
                    }
                }

                // Captura en la capa de red (aquí la URL tiene tokens completos e intactos)
                if (urlLower.contains(".m3u8") || urlLower.contains(".m3u")) {
                    processDetectedLink(url)
                } else if (urlLower.contains(".mp4") || urlLower.contains("acek-cdn.com")) {
                    if (url.contains("?")) processDetectedLink(url)
                }

                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                val urlLower = url.lowercase()

                // NO bloqueamos la carga de archivos multimedia para permitir que el navegador solicite el token real
                if (urlLower.contains(".mp4") || urlLower.contains(".m3u8") || urlLower.contains(".m3u") || urlLower.contains("acek-cdn.com")) {
                    processDetectedLink(url)
                    return false // Retornamos false para que la petición de red continúe y se generen los tokens de sesión
                }

                if (request?.hasGesture() == true) return false

                val currentUrl = webView.url
                if (currentUrl != null) {
                    val currentHost = Uri.parse(currentUrl).host
                    val targetHost = Uri.parse(url).host
                    if (currentHost != null && targetHost != null && currentHost != targetHost) {
                        return true
                    }
                }

                return false
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                super.onLoadResource(view, url)
                cleanOverlays()

                url?.let {
                    val uLower = it.lowercase()
                    if (uLower.contains(".m3u8") || uLower.contains(".m3u") || (uLower.contains(".mp4") && it.contains("?"))) {
                        processDetectedLink(it)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                etUrl.setText(url)
                url?.let {
                    val uri = Uri.parse(it)
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
                    // Permitimos que el WebView cargue el destino para que se procesen las redirecciones y queries completas
                    view.loadUrl(url)
                    return true
                }
                return false
            }

            // SOPORTE DE REPRODUCCIÓN EN PANTALLA COMPLETA (Previene que los reproductores web se queden pegados)
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback

                // Agregamos el renderizador de video de pantalla completa al contenedor de la pantalla principal
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

        // 1. Debe usar protocolo web
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return false
        }

        // 2. Comprobación de lista negra limitada ESTRICTAMENTE al host (evita dañar tokens legítimos con palabras clave)
        for (domain in blacklistedDomains) {
            if (host.contains(domain)) return false
        }

        // 3. Descartar subtítulos y fragmentos de audio
        if (path.contains("subtitle") || path.contains(".vtt") || path.contains("audio-only")) {
            return false
        }

        // 4. Excluir URLs incompletas o vacías
        if (url.length < 25) {
            return false
        }

        return true
    }

    // SANITIZADOR DE URL (Remueve parámetros que atan la URL a la IP o proveedor de internet del móvil)
    private fun sanitizeStreamUrl(url: String): String {
        try {
            val uri = Uri.parse(url)
            val queryNames = uri.queryParameterNames
            if (queryNames.isEmpty()) return url

            val builder = uri.buildUpon()
            builder.clearQuery()

            // Lista de parámetros de IP o ISP conocidos por limitar la reproducción a una red específica
            val ipTrackingParams = arrayOf("ip", "client_ip", "user_ip", "userip", "asn", "ip_block")

            for (name in queryNames) {
                // Omitir parámetros vinculados a la IP
                if (ipTrackingParams.contains(name.lowercase())) {
                    continue
                }
                val value = uri.getQueryParameter(name)
                builder.appendQueryParameter(name, value)
            }
            return builder.build().toString()
        } catch (e: Exception) {
            return url
        }
    }

    private fun processDetectedLink(url: String) {
        if (url == lastCapturedUrl) return

        // Validación de estructura y seguridad
        if (!isValidVideoUrl(url)) return

        // Sanitización del enlace para desvincularlo de la IP del móvil
        val sanitizedUrl = sanitizeStreamUrl(url)

        if (sanitizedUrl.contains("acek-cdn.com", ignoreCase = true)) {
            confirmAndCapture(sanitizedUrl, "🎬 PELÍCULA CAPTURADA (ACEK)")
            return
        }

        if (sanitizedUrl.contains(".mp4", ignoreCase = true)) {
            thread {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(sanitizedUrl, HashMap<String, String>())
                    val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durationMs = time?.toLong() ?: 0
                    retriever.release()

                    val cuarentaMinutos = 2400000

                    if (durationMs >= cuarentaMinutos) {
                        confirmAndCapture(sanitizedUrl, "🎬 VIDEO LARGO DETECTADO (>40 min)")
                    } else {
                        android.util.Log.d("CAPTURA", "Video corto descartado: ${durationMs / 1000} seg")
                    }
                } catch (e: Exception) {
                    if (sanitizedUrl.contains("?") && isValidVideoUrl(sanitizedUrl)) {
                        confirmAndCapture(sanitizedUrl, "✅ VIDEO MP4 CAPTURADO")
                    }
                }
            }
            return
        }

        // Captura completa para formato M3U o M3U8 de transmisión continua libre de IP
        if (sanitizedUrl.contains(".m3u8", ignoreCase = true) || sanitizedUrl.contains(".m3u", ignoreCase = true)) {
            confirmAndCapture(sanitizedUrl, "📡 STREAMING M3U8 CAPTURADO")
        }
    }

    private fun confirmAndCapture(url: String, mensaje: String) {
        lastCapturedUrl = url
        runOnUiThread {
            if (capturedLinksList.add(url)) {
                Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                showCapturedLinksDialog()
            }
        }
    }

    // Ventana de capturas integrada: Sin título, video redondeado en CardView negro, controles estilizados y lista en modo oscuro premium (Sin usar XML)
    // Ventana de capturas integrada: Diseño visual unificado sin franjas transparentes
    private fun showCapturedLinksDialog() {
        if (isFinishing || isDestroyed) return

        captureDialog?.dismiss()

        val linksArray = capturedLinksList.toList()
        if (linksArray.isEmpty()) return

        val displayItems = linksArray.mapIndexed { index, link ->
            "${index + 1}. $link"
        }.toTypedArray()

        val builder = AlertDialog.Builder(this)

        // DISEÑO PERSONALIZADO PRINCIPAL (Vertical con fondo pizarra oscuro y esquinas curvas)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 20)
            val dialogBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A24")) // Fondo pizarra oscuro premium
                cornerRadius = 24f // Bordes redondeados de la tarjeta principal
            }
            background = dialogBg
        }

        // 1. REPRODUCTOR DE VIDEO ENVUELTO EN UN CARDVIEW REDONDEADO CON SOMBRA
        val videoCard = androidx.cardview.widget.CardView(this).apply {
            radius = 16f // Bordes curvos del video
            cardElevation = 8f // Efecto de elevación/sombra flotante
            setCardBackgroundColor(Color.BLACK)
            preventCornerOverlap = true
        }
        val cardParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            380
        ).apply {
            setMargins(0, 0, 0, 20)
        }
        videoCard.layoutParams = cardParams

        // Instancia del reproductor de video centrado
        val videoView = VideoView(this)
        val videoParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }
        videoView.layoutParams = videoParams

        videoCard.addView(videoView) // Añadir reproductor dentro de la tarjeta curva
        container.addView(videoCard) // Añadir tarjeta curva al contenedor principal

        // 2. PANEL DE CONTROL FIJO (Estilizado en Azul Premium)
        val controlLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 15, 0, 15)
            gravity = Gravity.CENTER_VERTICAL
        }

        // Botón de Play/Pausa redondo de color Azul Eléctrico
        val btnPlayPause = Button(this).apply {
            text = "⏸"
            setTextColor(Color.WHITE)
            textSize = 16f
            val btnShape = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#2979FF")) // Color azul eléctrico
                cornerRadius = 14f // Botón con curvas elegantes
            }
            background = btnShape
            layoutParams = LinearLayout.LayoutParams(
                100, // Ancho cuadrado
                100  // Alto cuadrado
            ).apply {
                setMargins(0, 0, 16, 0)
            }
        }
        controlLayout.addView(btnPlayPause)

        // Barra de progreso teñida para coincidir con la paleta de colores azul
        val seekBar = SeekBar(this).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2979FF"))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2979FF"))
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
        }
        controlLayout.addView(seekBar)

        container.addView(controlLayout)

        // 3. LISTA DE ENLACES EN MODO OSCURO ADAPTATIVO
        val listView = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.parseColor("#2C2C3C")) // Línea de división pizarra
            dividerHeight = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                350 // Altura fija para que no tape los botones inferiores
            )
        }
        container.addView(listView)

        // Adaptador con sobrescritura programática de color y espaciado de texto
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, displayItems) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.parseColor("#E0E0E0")) // Color blanco grisáceo de lectura suave
                textView.textSize = 14f
                textView.setPadding(12, 16, 12, 16)
                return view
            }
        }
        listView.adapter = adapter

        // Hilo de actualización en tiempo real de la barra de progreso
        val progressHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateProgressTask = object : Runnable {
            override fun run() {
                if (videoView.isPlaying) {
                    seekBar.progress = videoView.currentPosition
                }
                progressHandler.postDelayed(this, 1000)
            }
        }

        // 4. BOTONES DE ACCIÓN INTEGRADOS EN EL CONTENEDOR (Soluciona la franja transparente inferior)
        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 20, 0, 0)
        }

        val btnLimpiar = Button(this).apply {
            text = "Limpiar Lista"
            setTextColor(Color.parseColor("#FF5252")) // Rojo Alerta
            background = null // Botón plano minimalista
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
            setTextColor(Color.parseColor("#90A4AE")) // Gris
            background = null
            setOnClickListener {
                progressHandler.removeCallbacksAndMessages(null)
                videoView.stopPlayback()
                captureDialog?.dismiss()
                goHomeWithoutFinishing()
            }
        }

        val btnCerrar = Button(this).apply {
            text = "Cerrar"
            setTextColor(Color.parseColor("#2979FF")) // Azul Premium
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

        // Habilitar transparencia de ventana para permitir esquinas redondeadas perfectas
        captureDialog?.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        captureDialog?.show()

        // ACCIONES DE LA BARRA DE DESPLAZAMIENTO (Control manual de tiempo)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    videoView.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // ACCIONES DEL BOTÓN PLAY/PAUSA
        btnPlayPause.setOnClickListener {
            if (videoView.isPlaying) {
                videoView.pause()
                btnPlayPause.text = "▶"
            } else {
                videoView.start()
                btnPlayPause.text = "⏸"
            }
        }

        // SELECCIÓN RÁPIDA (Un click): Copia enlace e inicia reproducción de prueba arriba con lógica híbrida
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedUrl = linksArray[position]

            val referer = webView.url ?: ""
            val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"

            val urlLower = selectedUrl.lowercase()
            val isDirectDownload = urlLower.contains(".mp4") || urlLower.contains("acek-cdn.com")

            val castUrl = if (!isDirectDownload && referer.isNotEmpty()) {
                "$selectedUrl|User-Agent=$userAgent&Referer=$referer"
            } else {
                selectedUrl
            }

            // 1. COPIAR ENLACE (Limpio o Autenticado según corresponda)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Captura", castUrl)
            clipboard.setPrimaryClip(clip)

            val mensajeCopia = if (isDirectDownload) "Enlace directo copiado limpio" else "Copiado enlace con autenticación"
            Toast.makeText(this, "$mensajeCopia #${position + 1}", Toast.LENGTH_SHORT).show()

            // 2. REPRODUCIR EN EL VISOR SUPERIOR CON CABECERAS COMPLETAS
            val progressToast = Toast.makeText(this, "Probando enlace...", Toast.LENGTH_SHORT)
            progressToast.show()

            val headers = HashMap<String, String>().apply {
                put("User-Agent", userAgent)
                if (referer.isNotEmpty()) {
                    put("Referer", referer)
                    val originUri = Uri.parse(referer)
                    put("Origin", "${originUri.scheme}://${originUri.host}")
                }
                put("Accept", "*/*")
                put("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                put("Connection", "keep-alive")
            }

            try {
                progressHandler.removeCallbacks(updateProgressTask)
                videoView.stopPlayback()
                btnPlayPause.text = "⏸"
                seekBar.progress = 0

                videoView.setVideoURI(Uri.parse(selectedUrl), headers)

                videoView.setOnPreparedListener { mediaPlayer ->
                    progressToast.cancel()
                    seekBar.max = videoView.duration
                    mediaPlayer.start()
                    progressHandler.post(updateProgressTask)
                    Toast.makeText(this, "▶️ Cargado. Arrastre la barra para adelantar.", Toast.LENGTH_SHORT).show()
                }

                videoView.setOnErrorListener { _, _, _ ->
                    progressToast.cancel()
                    progressHandler.removeCallbacks(updateProgressTask)
                    Toast.makeText(this, "❌ El enlace requiere reproductor externo o ha caducado", Toast.LENGTH_SHORT).show()
                    true
                }
            } catch (e: Exception) {
                progressToast.cancel()
                progressHandler.removeCallbacks(updateProgressTask)
                Toast.makeText(this, "❌ Error al conectar con el enlace", Toast.LENGTH_SHORT).show()
            }
        }

        // ACCIÓN ELIMINAR (Toque sostenido / click largo)
        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val selectedUrl = linksArray[position]

            AlertDialog.Builder(this)
                .setTitle("¿Eliminar enlace de la lista?")
                .setMessage(selectedUrl)
                .setPositiveButton("Eliminar") { _, _ ->
                    videoView.stopPlayback()
                    progressHandler.removeCallbacksAndMessages(null)
                    seekBar.progress = 0
                    btnPlayPause.text = "⏸"

                    capturedLinksList.remove(selectedUrl)
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

    // 4. MOSTRAR DIÁLOGO (Mejorado con estilo Dark Cinema Premium)
    // Diálogo de favoritos con diseño unificado de una sola tarjeta oscura
    private fun showFavoritesDialog() {
        val favs = getFavoritesList().toList()

        if (favs.isEmpty()) {
            Toast.makeText(this, "No tienes favoritos guardados", Toast.LENGTH_SHORT).show()
            return
        }

        val builder = AlertDialog.Builder(this)

        // DISEÑO PERSONALIZADO PRINCIPAL (Vertical con fondo pizarra oscuro)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 30, 30, 20)
            val dialogBg = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A24")) // Fondo pizarra oscuro
                cornerRadius = 24f // Esquinas redondeadas
            }
            background = dialogBg
        }

        // Encabezado personalizado para el diálogo
        val headerTv = TextView(this).apply {
            text = "⭐ Mis Favoritos"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 20)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        container.addView(headerTv)

        // Lista de favoritos adaptativa
        val listView = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Color.parseColor("#2C2C3C")) // Línea de división pizarra
            dividerHeight = 2
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                weight = 1f // Permite flexibilidad si hay muchos favoritos
            }
        }
        container.addView(listView)

        // Adaptador con sobrescritura de color y padding para modo oscuro
        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, favs) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.parseColor("#E0E0E0")) // Texto claro de lectura suave
                textView.textSize = 14f
                textView.setPadding(12, 16, 12, 16)
                return view
            }
        }
        listView.adapter = adapter

        // BOTÓN DE ACCIÓN INTEGRADO (Soluciona la barra de botones transparente)
        val actionsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 20, 0, 0)
        }

        // Declaración previa para poder referenciar el diálogo dentro del listener del botón
        var dialog: AlertDialog? = null

        val btnCerrar = Button(this).apply {
            text = "Cerrar"
            setTextColor(Color.parseColor("#90A4AE")) // Gris sutil
            background = null // Botón plano sin contorno
            setOnClickListener {
                dialog?.dismiss()
            }
        }
        actionsLayout.addView(btnCerrar)
        container.addView(actionsLayout)

        builder.setView(container)
        dialog = builder.create()

        // Habilitar transparencia de ventana para permitir esquinas redondeadas perfectas
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        // ACCIÓN UN TOQUE: Carga el enlace en el navegador y cierra el diálogo
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedUrl = favs[position]
            webView.loadUrl(selectedUrl)
            dialog.dismiss()
        }

        // ACCIÓN TOQUE SOSTENIDO: Menú para eliminar favorito
        listView.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            val selectedUrl = favs[position]

            AlertDialog.Builder(this)
                .setTitle("¿Eliminar favorito?")
                .setMessage(selectedUrl)
                .setPositiveButton("Eliminar") { _, _ ->
                    removeFavorite(selectedUrl)
                    dialog.dismiss()
                    showFavoritesDialog() // Refresca el diálogo con los favoritos restantes
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