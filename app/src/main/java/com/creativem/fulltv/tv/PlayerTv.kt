package com.creativem.fulltv.tv

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.databinding.PlayerBinding
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Modelo
import com.google.firebase.database.FirebaseDatabase
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class PlayerTv : AppCompatActivity() {

    private var reintentosContador = 0
    private val MAX_REINTENTOS = 3
    private var isOffline = false
    private var masterTvList: List<Modelo> = emptyList()
    private var currentChannelIndex = -1

    private var player: ExoPlayer? = null

    private var streamUrl: String = ""
    private var movieImageUrl: String = ""
    private lateinit var movieTitle: String
    private lateinit var binding: PlayerBinding
    private lateinit var adapter: TvMenuAdapter

    private val handler = Handler(Looper.getMainLooper())

    private val bufferUpdater = object : Runnable {
        override fun run() {
            if (player != null && binding.loadingIndicator.visibility == View.VISIBLE) {
                val percentage = player?.bufferedPercentage ?: 0
                val estimatedKb = (player?.bufferedPosition ?: 0) / 1024
                binding.loadingBufferText.text = "Búfer: $percentage% (${estimatedKb} KB)"
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        val controles = binding.reproductor.findViewById<View>(R.id.controles_reproductor)
        controles?.visibility = View.GONE
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.loadingBufferText.text = "Iniciando señal..."

        binding.reproductor.keepScreenOn = true
        initializeRecyclerView()

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        intent?.let {
            streamUrl = it.getStringExtra("EXTRA_STREAM_URL") ?: ""
            movieTitle = it.getStringExtra("EXTRA_MOVIE_TITLE") ?: "TV en vivo"
            movieImageUrl = it.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""

            binding.loadingMovieTitle.text = movieTitle
            findViewById<TextView>(R.id.nombrePelicula)?.text = movieTitle
            findViewById<ImageView>(R.id.imagenPelicula)?.let { img ->
                Glide.with(this).load(movieImageUrl).placeholder(R.drawable.icono).into(img)
            }
        }

        binding.reproductor.setOnTouchListener { _, _ ->
            if (binding.recyclerViewTv.visibility != View.VISIBLE) {
                mostarpélis()
            }
            true
        }

        loadTvCollection()
        initializePlayer()

        binding.btnCerrarMenuTv.setOnClickListener {
            ocultarMenuCompleto()
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(CastvHelper.ajustarContexto(newBase))
    }

    override fun getResources(): android.content.res.Resources {
        val res = super.getResources()
        CastvHelper.ajustarRecursos(res, this)
        return res
    }

    @OptIn(UnstableApi::class)
    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        if (streamUrl.isEmpty()) return

        liberarReproductor()

        Log.d("PlayerTv_DEBUG", "--------------------------------------------------")
        Log.d("PlayerTv_DEBUG", "Iniciando reproduccion de URL: $streamUrl")

        // 1. Configuración de Pantalla
        binding.reproductor.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        binding.reproductor.useController = false

        // 2. HTTP DataSource con User-Agent de reproductor estándar
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18") // Cambiado a VLC para mayor compatibilidad IPTV
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // 3. Extracción permisiva para canales MPEG-TS / IPTV
        val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setTsExtractorFlags(tsFlags)
        }

        val hlsExtractorFactory = DefaultHlsExtractorFactory(tsFlags, true)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        // 4. Búfer optimizado
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10000, 40000, 1500, 3000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(3840, 2160)
                    .setForceHighestSupportedBitrate(false)
            )
        }

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().also { exoPlayer ->

                // 🟢 LOGS AVANZADOS DE EXOPLAYER (MUESTRA TODO EN LOGCAT FILTRANDO POR "EventLogger")
                exoPlayer.addAnalyticsListener(androidx.media3.exoplayer.util.EventLogger("PlayerTv_EventLogger"))

                binding.reproductor.player = exoPlayer

                val uri = Uri.parse(streamUrl)
                val mediaItemBuilder = MediaItem.Builder().setUri(uri)

                // 🟢 CORRECCIÓN DE DETECCIÓN HLS VS MPEG-TS
                // Solo tratamos como HLS si la URL termina en .m3u8 o contiene la extensión explícita.
                // NO usar "/live/" o "/stream/" porque usualmente son streams TS directos.
                val isStrictHls = streamUrl.contains(".m3u8", ignoreCase = true) ||
                        streamUrl.contains("format=m3u8", ignoreCase = true)

                val isTsStream = streamUrl.contains(".ts", ignoreCase = true) ||
                        streamUrl.contains("/live/", ignoreCase = true) ||
                        streamUrl.contains("/stream/", ignoreCase = true)

                Log.d("PlayerTv_DEBUG", "Es HLS Estricto: $isStrictHls | Es TS Stream: $isTsStream")

                val mediaSource = if (isStrictHls) {
                    Log.d("PlayerTv_DEBUG", "Creando HlsMediaSource...")
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    HlsMediaSource.Factory(dataSourceFactory)
                        .setExtractorFactory(hlsExtractorFactory)
                        .setAllowChunklessPreparation(false)
                        .createMediaSource(mediaItemBuilder.build())
                } else if (isTsStream) {
                    Log.d("PlayerTv_DEBUG", "Creando MediaSource para MPEG-TS...")
                    mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
                    mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
                } else {
                    Log.d("PlayerTv_DEBUG", "Creando MediaSource Generico (Auto-detect)...")
                    mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
                }

                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.addListener(playerListener)
                exoPlayer.playWhenReady = true
            }
    }

    private val playerListener = @UnstableApi object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (!isOffline) {
                        binding.loadingIndicator.visibility = View.VISIBLE
                        handler.post(bufferUpdater)
                    }
                }
                Player.STATE_READY -> {
                    Log.d("PlayerTv_DEBUG", " Reproducción iniciada correctamente para: $streamUrl")
                    binding.loadingIndicator.visibility = View.GONE
                    isOffline = false
                    reintentosContador = 0
                    handler.removeCallbacks(bufferUpdater)
                }
                Player.STATE_ENDED -> playNextChannel()
                Player.STATE_IDLE -> { }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val errorMsg = error.localizedMessage ?: error.errorCodeName

            // 🟢 LOGS DETALLADOS DEL ERROR DE FUENTE (SOURCE ERROR)
            Log.e("PlayerTv_DEBUG", "================ ERROR DE REPRODUCCION ================")
            Log.e("PlayerTv_DEBUG", "Codigo Error Nombre: ${error.errorCodeName}")
            Log.e("PlayerTv_DEBUG", "Codigo Error Int: ${error.errorCode}")
            Log.e("PlayerTv_DEBUG", "Mensaje: $errorMsg")

            val cause = error.cause
            if (cause != null) {
                Log.e("PlayerTv_DEBUG", "Causa raiz (Class): ${cause.javaClass.name}")
                Log.e("PlayerTv_DEBUG", "Causa raiz (Mensaje): ${cause.message}")

                // Verificar si fue un error HTTP (ej. 403 Forbidden, 404 Not Found)
                if (cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException) {
                    Log.e("PlayerTv_DEBUG", "Error de red HTTP detectado")
                    if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        Log.e("PlayerTv_DEBUG", "Codigo HTTP devuelto por servidor: ${cause.responseCode}")
                    }
                }
            }
            Log.e("PlayerTv_DEBUG", "=======================================================")

            if (isOffline) return
            reintentosContador++

            if (reintentosContador <= MAX_REINTENTOS) {
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.loadingBufferText.text = "Reintentando canal ($reintentosContador/$MAX_REINTENTOS)..."
                handler.postDelayed({ reiniciarReproductor() }, 3000)
            } else {
                isOffline = true
                binding.loadingMovieTitle.text = "CANAL FUERA DE LÍNEA"
                binding.loadingBufferText.text = "Error: $errorMsg"
                binding.loadingBufferText.setTextColor(ContextCompat.getColor(this@PlayerTv, R.color.redpersonalisado))

                handler.postDelayed({ playNextChannel() }, 4000)
            }
        }
    }
    private fun playNextChannel() {
        if (masterTvList.isNotEmpty()) {
            cambiarCanal(siguiente = true)
        } else {
            Toast.makeText(this, "No hay más canales disponibles", Toast.LENGTH_SHORT).show()
            finishPlayer()
        }
    }

    private fun loadTvCollection() {
        if (TvRepository.channelListMaster.isNotEmpty()) {
            masterTvList = TvRepository.channelListMaster
            currentChannelIndex = masterTvList.indexOfFirst { it.streamUrl == streamUrl }
            adapter.updateData(masterTvList)
            Log.d("PlayerTv", "Menú cargado desde TvRepository (${masterTvList.size} canales)")
            return
        }

        FirebaseDatabase.getInstance(TvRepository.FIREBASE_DB_URL)
            .getReference(TvRepository.FIREBASE_PATH)
            .get().addOnSuccessListener { snapshot ->
                val m3uUrl = snapshot.value?.toString()?.trim()
                if (!m3uUrl.isNullOrEmpty()) {
                    descargarYParsearM3u(m3uUrl)
                } else {
                    Log.e("PlayerTv", "No se encontró la URL IPTV en Firebase (${TvRepository.FIREBASE_PATH})")
                }
            }.addOnFailureListener { exception ->
                Log.e("PlayerTv", "Error al conectar con Firebase", exception)
            }
    }

    private fun descargarYParsearM3u(m3uUrl: String) {
        val executor = Executors.newSingleThreadExecutor()
        executor.execute {
            val canales = descargarM3uStream(m3uUrl)
            if (canales.isNotEmpty()) {
                handler.post {
                    masterTvList = canales
                    TvRepository.channelListMaster = canales
                    currentChannelIndex = masterTvList.indexOfFirst { it.streamUrl == streamUrl }
                    adapter.updateData(masterTvList)
                    Log.d("PlayerTv", "Lista cargada desde Firebase con ${canales.size} canales")
                }
            } else {
                Log.e("PlayerTv", "La lista M3U descargada desde Firebase no trajo canales válidos")
            }
        }
    }

    private fun descargarM3uStream(urlString: String): List<Modelo> {
        var currentUrl = urlString.trim()
        var redirects = 0
        val maxRedirects = 5

        while (redirects < maxRedirects) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connection.instanceFollowRedirects = true

                val status = connection.responseCode

                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308) {

                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl.isNullOrEmpty()) break
                    currentUrl = newUrl
                    redirects++
                    connection.disconnect()
                    continue
                }

                if (status == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    return parseM3uBuffer(reader)
                } else {
                    Log.e("PlayerTv", "Error HTTP $status en la URL remota: $currentUrl")
                    return emptyList()
                }
            } catch (e: Exception) {
                Log.e("PlayerTv", "Excepción al conectar con la lista de Firebase: $currentUrl", e)
                return emptyList()
            } finally {
                connection?.disconnect()
            }
        }
        return emptyList()
    }

    private fun parseM3uBuffer(reader: BufferedReader): List<Modelo> {
        val channels = mutableListOf<Modelo>()
        var currentName = ""
        var currentLogo = ""
        var idContador = 0

        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach

                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    val logoMatch = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE).find(trimmed)
                    currentLogo = logoMatch?.groupValues?.get(1)?.trim() ?: ""

                    val tvgNameMatch = Regex("""tvg-name="([^"]*)"""", RegexOption.IGNORE_CASE).find(trimmed)
                    val tvgName = tvgNameMatch?.groupValues?.get(1)?.trim()

                    val nameAfterComma = trimmed.substringAfterLast(",", "").trim()

                    currentName = when {
                        nameAfterComma.isNotEmpty() -> nameAfterComma
                        !tvgName.isNullOrEmpty() -> tvgName
                        else -> ""
                    }
                } else if (!trimmed.startsWith("#")) {
                    if (trimmed.contains("://") || trimmed.startsWith("rtmp", ignoreCase = true) || trimmed.startsWith("udp", ignoreCase = true)) {
                        val finalName = if (currentName.isNotEmpty()) currentName else "Canal ${channels.size + 1}"
                        channels.add(
                            Modelo(
                                id = "iptv_$idContador",
                                title = finalName,
                                streamUrl = trimmed,
                                imageUrl = currentLogo
                            )
                        )
                        idContador++
                    }
                    currentName = ""
                    currentLogo = ""
                }
            }
        }
        return channels
    }

    private fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun reiniciarReproductor() {
        liberarReproductor()
        initializePlayer()
    }

    private fun initializeRecyclerView() {
        adapter = TvMenuAdapter(this, mutableListOf()) { canalElegido ->
            cambiarCanalDirecto(canalElegido)
        }

        binding.recyclerViewTv.adapter = adapter
        binding.recyclerViewTv.layoutManager = LinearLayoutManager(this)

        binding.recyclerViewTv.isFocusable = true
        binding.recyclerViewTv.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun cambiarCanalDirecto(canal: Modelo) {
        currentChannelIndex = masterTvList.indexOfFirst { it.streamUrl == canal.streamUrl }
        streamUrl = canal.streamUrl
        movieTitle = canal.title
        movieImageUrl = canal.imageUrl

        binding.loadingIndicator.visibility = View.VISIBLE
        binding.loadingMovieTitle.text = movieTitle
        binding.loadingBufferText.text = "Buscando señal..."
        binding.loadingBufferText.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        findViewById<TextView>(R.id.nombrePelicula)?.text = movieTitle
        findViewById<ImageView>(R.id.imagenPelicula)?.let { img ->
            Glide.with(this).load(movieImageUrl).placeholder(R.drawable.icono).into(img)
        }

        ocultarMenuCompleto()
        reiniciarReproductor()
    }

    private fun mostarpélis() {
        if (binding.recyclerViewTv.visibility == View.VISIBLE) {
            ocultarMenuCompleto()
        } else {
            binding.recyclerViewTv.visibility = View.VISIBLE
            binding.btnCerrarMenuTv.visibility = View.VISIBLE

            adapter.setCurrentPlayingChannel(streamUrl)
            val posicionActual = masterTvList.indexOfFirst { it.streamUrl == streamUrl }

            if (posicionActual != -1) {
                binding.recyclerViewTv.scrollToPosition(posicionActual)

                binding.recyclerViewTv.post {
                    val viewHolder = binding.recyclerViewTv.findViewHolderForAdapterPosition(posicionActual)
                    if (viewHolder != null) {
                        viewHolder.itemView.requestFocus()
                    } else {
                        binding.recyclerViewTv.requestFocus()
                    }
                }
            } else {
                binding.recyclerViewTv.post { binding.recyclerViewTv.requestFocus() }
            }
        }
    }

    private fun ocultarMenuCompleto() {
        binding.recyclerViewTv.visibility = View.GONE
        binding.btnCerrarMenuTv.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                cambiarCanal(siguiente = true)
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                cambiarCanal(siguiente = false)
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                mostarpélis()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (binding.recyclerViewTv.visibility == View.VISIBLE) {
                    super.onKeyDown(keyCode, event)
                } else {
                    mostarpélis()
                    true
                }
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (binding.recyclerViewTv.visibility == View.VISIBLE) {
                    ocultarMenuCompleto()
                    true
                } else {
                    finishPlayer()
                    true
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (binding.recyclerViewTv.visibility != View.VISIBLE) {
                    mostarpélis()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun cambiarCanal(siguiente: Boolean) {
        if (masterTvList.isEmpty()) return

        currentChannelIndex = if (siguiente) {
            (currentChannelIndex + 1) % masterTvList.size
        } else {
            if (currentChannelIndex <= 0) masterTvList.size - 1 else currentChannelIndex - 1
        }

        val canalElegido = masterTvList[currentChannelIndex]

        streamUrl = canalElegido.streamUrl
        movieTitle = canalElegido.title
        movieImageUrl = canalElegido.imageUrl

        binding.loadingIndicator.visibility = View.VISIBLE
        binding.loadingMovieTitle.text = movieTitle
        binding.loadingBufferText.text = "Buscando señal..."
        binding.loadingBufferText.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        findViewById<TextView>(R.id.nombrePelicula)?.text = movieTitle
        findViewById<ImageView>(R.id.imagenPelicula)?.let { img ->
            Glide.with(this).load(movieImageUrl).placeholder(R.drawable.icono).into(img)
        }

        if (binding.recyclerViewTv.visibility == View.VISIBLE) {
            adapter.setCurrentPlayingChannel(streamUrl)
            binding.recyclerViewTv.scrollToPosition(currentChannelIndex)
        }

        reiniciarReproductor()
    }

    private fun liberarReproductor() {
        player?.let {
            it.release()
            player = null
        }
    }

    private fun finishPlayer() {
        liberarReproductor()
        finish()
    }

    override fun onStop() {
        super.onStop()
        liberarReproductor()
    }

    override fun onDestroy() {
        super.onDestroy()
        liberarReproductor()
        handler.removeCallbacksAndMessages(null)
    }
}