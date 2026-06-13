package com.creativem.fulltv

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Insets.add
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlin.concurrent.thread
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.TextView
import android.widget.ImageView
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope // Debe estar presente
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import coil.load
class LivePlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnPlayPause: Button
    private lateinit var btnBack: Button

    private val TAG = "FullTV_Player"

    // 👉 RECONEXIÓN: Variables para controlar la url original y los intentos
    private var originalYoutubeUrl: String = ""
    private var intentosReconexion = 0
    private val MAX_INTENTOS = 3

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = PlayerView(this).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true
        }
        rootLayout.addView(playerView)

        progressBar = ProgressBar(this).apply {
            visibility = View.VISIBLE
            layoutParams = FrameLayout.LayoutParams(dpToPx(56), dpToPx(56)).apply {
                gravity = Gravity.CENTER
            }
        }
        rootLayout.addView(progressBar)

        btnPlayPause = Button(this).apply {
            text = "⏸"
            textSize = 18f
            setTextColor(Color.WHITE)
            visibility = View.GONE

            val bgDrawablePlay = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(Color.parseColor("#802979FF"))
            }
            background = bgDrawablePlay

            layoutParams = FrameLayout.LayoutParams(dpToPx(56), dpToPx(56)).apply {
                gravity = Gravity.CENTER
            }

            isFocusable = true
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    bgDrawablePlay.setStroke(dpToPx(3), Color.WHITE)
                    view.animate().scaleX(1.2f).scaleY(1.2f).setDuration(150).start()
                } else {
                    bgDrawablePlay.setStroke(0, Color.TRANSPARENT)
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
            }
            setOnClickListener { togglePlayPause() }
        }
        rootLayout.addView(btnPlayPause)

        btnBack = Button(this).apply {
            text = "◀ CineParche"
            textSize = 12f
            setTextColor(Color.WHITE)

            val defaultColor = Color.parseColor("#991A1A24")
            val focusedColor = Color.parseColor("#E62C2C3D")

            val bgDrawableBack = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(defaultColor)
            }
            background = bgDrawableBack

            setPadding(dpToPx(12), 0, dpToPx(12), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(40)
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(dpToPx(16), dpToPx(16), 0, 0)
            }

            isFocusable = true
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    bgDrawableBack.setColor(focusedColor)
                    bgDrawableBack.setStroke(dpToPx(2), Color.WHITE)
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(150).start()
                } else {
                    bgDrawableBack.setColor(defaultColor)
                    bgDrawableBack.setStroke(0, Color.TRANSPARENT)
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                }
                view.invalidate()
            }
            setOnClickListener { finish() }
        }
        rootLayout.addView(btnBack)

        setContentView(rootLayout)

        playerView.setOnClickListener { togglePlayPause() }
        playerView.requestFocus()

        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al inicializar el motor yt-dlp", e)
        }

        // 👉 RECONEXIÓN: Guardamos la URL original para poder usarla si se cae
        originalYoutubeUrl = intent.getStringExtra("YOUTUBE_URL_OR_ID") ?: ""
        Log.i(TAG, "Procesando URL/ID con YoutubeDL: $originalYoutubeUrl")

        inicializarExoPlayer()
        extraerYReproducir(originalYoutubeUrl)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (playerView.hasFocus() || btnPlayPause.hasFocus()) {
                btnBack.requestFocus()
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (btnBack.hasFocus()) {
                if (btnPlayPause.visibility == View.VISIBLE) {
                    btnPlayPause.requestFocus()
                } else {
                    playerView.requestFocus()
                }
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            keyCode == KeyEvent.KEYCODE_ENTER) {

            if (btnBack.hasFocus()) {
                return super.onKeyDown(keyCode, event)
            }
            togglePlayPause()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun inicializarExoPlayer() {
        // 👉 Volvemos al constructor por defecto, que es más rápido y ligero
        exoPlayer = ExoPlayer.Builder(this).build()
        playerView.player = exoPlayer
    }

    private fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                btnPlayPause.text = "▶"
                btnPlayPause.visibility = View.VISIBLE
                btnPlayPause.requestFocus()
            } else {
                player.play()
                btnPlayPause.text = "⏸"
                btnPlayPause.postDelayed({
                    if (player.isPlaying) {
                        btnPlayPause.visibility = View.GONE
                        playerView.requestFocus()
                    }
                }, 500)
            }
        }
    }

    private fun extraerYReproducir(input: String) {
        val youtubeUrl = if (input.startsWith("http")) input else "https://www.youtube.com/watch?v=$input"

        thread {
            try {
                val request = YoutubeDLRequest(youtubeUrl)
                request.addOption("-f", "best")

                Log.d(TAG, "Iniciando extracción con yt-dlp...")
                val streamInfo = YoutubeDL.getInstance().getInfo(request)
                val downloadUrl = streamInfo.url

                if (!downloadUrl.isNullOrEmpty()) {
                    Log.i(TAG, "¡Extracción Exitosa! URL resuelta.")
                    runOnUiThread {
                        prepararVideo(downloadUrl)
                    }
                } else {
                    Log.e(TAG, "La URL devuelta por YoutubeDL es nula o vacía.")
                    // 👉 SOLUCIÓN: Si no devuelve enlace de stream, pasamos a la imagen de espera
                    mostrarPanelResultados()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error crítico durante la extracción con YoutubeDL: ${e.message}", e)
                // 👉 SOLUCIÓN: Si el canal está offline o da error por no transmitir, mostramos la imagen
                mostrarPanelResultados()
            }
        }
    }

    private fun mostrarPanelResultados() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            exoPlayer?.stop()

            val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

            val container = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setBackgroundColor(Color.BLACK)
            }

            // 👉 BANNER DE CORTESÍA
            val banner = android.widget.TextView(this).apply {
                text = "CineParche: Vive el partido en directo aquí"
                setTextColor(Color.parseColor("#C5A059"))
                textSize = 22f // Un poco más pequeño para que quepa bien en TV
                gravity = Gravity.CENTER
                setPadding(0, 40, 0, 20)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }

// Subtítulo pequeño para guiar al usuario
            val subtitulo = android.widget.TextView(this).apply {
                text = "Programación oficial - Conéctate a la hora del Transmicion"
                setTextColor(Color.LTGRAY)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 30)
            }
            container.addView(banner)
            container.addView(subtitulo)

            val scrollView = android.widget.ScrollView(this)
            val listaPartidos = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.VERTICAL // ¡Fundamental!
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            scrollView.addView(listaPartidos)
            container.addView(scrollView)

            dialog.setContentView(container)
            dialog.setOnKeyListener { _, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_BACK) { dialog.dismiss(); finish(); true } else false
            }
            dialog.show()

            cargarResultadosMundial(listaPartidos)
        }
    }
    @SuppressLint("SetTextI18n")
    private fun cargarResultadosMundial(contenedor: android.widget.LinearLayout) {
        val service = retrofit2.Retrofit.Builder()
            .baseUrl("https://api.football-data.org/v4/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build().create(FootballApiService::class.java)

        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            try {
                val response = service.getWorldCupMatches()

                withContext(Dispatchers.Main) {
                    contenedor.removeAllViews()

                    if (response.isSuccessful && response.body() != null) {
                        // Ordenamos TODOS los partidos por fecha (del más antiguo al más nuevo)
                        val matches = response.body()!!.matches.sortedBy { it.utcDate }
                        val inflater = android.view.LayoutInflater.from(this@LivePlayerActivity)
                        val imageLoader = coil.ImageLoader.Builder(this@LivePlayerActivity)
                            .components { add(coil.decode.SvgDecoder.Factory()) }
                            .build()

                        // Quitamos el .take(8) si quieres ver más, o déjalo para limitar
                        matches.forEach { match ->
                            val view = inflater.inflate(R.layout.item_partido, contenedor, false)

                            val txtHome = view.findViewById<android.widget.TextView>(R.id.txtHome)
                            val txtAway = view.findViewById<android.widget.TextView>(R.id.txtAway)
                            val txtScore = view.findViewById<android.widget.TextView>(R.id.txtScore)
                            val imgHome = view.findViewById<android.widget.ImageView>(R.id.imgHome)
                            val imgAway = view.findViewById<android.widget.ImageView>(R.id.imgAway)

                            txtHome.text = match.homeTeam.name
                            txtAway.text = match.awayTeam.name

                            val homeScore = match.score.fullTime?.home ?: 0
                            val awayScore = match.score.fullTime?.away ?: 0

                            when (match.status) {
                                "FINISHED" -> {
                                    txtScore.text = "FINAL: $homeScore - $awayScore"
                                    txtScore.setTextColor(Color.parseColor("#C5A059"))
                                }
                                "IN_PLAY", "PAUSED" -> {
                                    txtScore.text = "🔴 TRANSMITIENDO AHORA: VER PARTIDO: $homeScore - $awayScore"
                                    txtScore.setTextColor(Color.RED)
                                }
                                "TIMED", "SCHEDULED" -> {
                                    // Aseguramos que la fecha tenga el largo mínimo para evitar crash en substring
                                    if (match.utcDate.length >= 16) {
                                        val horaUTC = match.utcDate.substring(11, 13).toIntOrNull() ?: 0
                                        val min = match.utcDate.substring(14, 16).toIntOrNull() ?: 0
                                        val horaLocal = (horaUTC - 5 + 24) % 24
                                        val horaAmPm = formatoAmPm(horaLocal, min)

                                        val dia = match.utcDate.substring(8, 10)
                                        val mes = match.utcDate.substring(5, 7)

                                        txtScore.text = "🔜 $dia/$mes  📍 $horaAmPm Colombia"
                                        txtScore.setTextColor(Color.parseColor("#C5A059"))
                                    } else {
                                        txtScore.text = "📅 Fecha por confirmar"
                                        txtScore.setTextColor(Color.LTGRAY)
                                    }
                                }
                            }
                            // Carga de banderas (SVG)
                            match.homeTeam.crest?.let { url ->
                                imageLoader.enqueue(coil.request.ImageRequest.Builder(this@LivePlayerActivity)
                                    .data(url).target(imgHome as android.widget.ImageView).build())
                            }
                            match.awayTeam.crest?.let { url ->
                                imageLoader.enqueue(coil.request.ImageRequest.Builder(this@LivePlayerActivity)
                                    .data(url).target(imgAway as android.widget.ImageView).build())
                            }

                            contenedor.addView(view)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("API_DEBUG", "Error al cargar partidos: ${e.message}")
            }
        }
    }

    private fun formatoAmPm(hora24: Int, minuto: Int): String {
        val amPm = if (hora24 >= 12) "PM" else "AM"
        val hora12 = if (hora24 % 12 == 0) 12 else hora24 % 12
        return String.format("%d:%02d %s", hora12, minuto, amPm)
    }
    @OptIn(UnstableApi::class)
    private fun prepararVideo(urlReal: String) {
        try {
            progressBar.visibility = View.GONE

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true)

            val isHls = urlReal.contains(".m3u8") || urlReal.contains("manifest")
            val mimeType = if (isHls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(urlReal))
                .setMimeType(mimeType)
                // 👉 LÓGICA ESTABLE PARA EN VIVO:
                // Le decimos a ExoPlayer cómo comportarse sin congelarse
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(18000) // 👉 18 segundos. Le da margen a YouTube para renderizar el video sin forzar tu reproductor
                        .setMaxPlaybackSpeed(1.02f)
                        .setMinPlaybackSpeed(0.98f)
                        .build()
                )
                .build()

            val mediaSource = DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory)
                .createMediaSource(mediaItem)

            exoPlayer?.setMediaSource(mediaSource)
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true

            exoPlayer?.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        androidx.media3.common.Player.STATE_BUFFERING -> {
                            // 👉 MEJORA: Si el internet se pone lento en pleno partido, muestra la ruedita
                            progressBar.visibility = View.VISIBLE
                        }
                        androidx.media3.common.Player.STATE_READY -> {
                            Log.i(TAG, "ExoPlayer: ¡Reproduciendo el Stream!")
                            progressBar.visibility = View.GONE
                            intentosReconexion = 0 // El stream es estable, reiniciamos el contador
                        }
                        androidx.media3.common.Player.STATE_ENDED -> {
                            Log.i(TAG, "ExoPlayer: Stream finalizado.")
                            progressBar.visibility = View.GONE
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "Error nativo de ExoPlayer: ${error.errorCodeName} - ${error.message}")

                    // 1. Si simplemente se desfasó por un micro-corte, lo reenganchamos sin volver a extraer
                    if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                        Log.w(TAG, "Re-sincronizando stream en vivo...")
                        exoPlayer?.seekToDefaultPosition()
                        exoPlayer?.prepare()
                        return
                    }

                    // 2. Si el enlace caducó (Error de red grave), intentamos yt-dlp otra vez
                    if (intentosReconexion < MAX_INTENTOS) {
                        intentosReconexion++
                        runOnUiThread {
                            progressBar.visibility = View.VISIBLE
                            Toast.makeText(this@LivePlayerActivity, "Recuperando señal...", Toast.LENGTH_SHORT).show()
                        }
                        // Lanzamos la reconexión de inmediato
                        extraerYReproducir(originalYoutubeUrl)
                    } else {
                        mostrarPanelResultados()
                    }
                }
            })

        } catch (e: Exception) {
            Log.e(TAG, "Excepción al preparar ExoPlayer: ${e.message}")
            if (intentosReconexion == 0) lanzarErrorYSalir("Fallo al iniciar el reproductor multimedia.")
        }
    }

    private fun lanzarErrorYSalir(mensaje: String) {
        runOnUiThread {
            progressBar.visibility = View.GONE
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.let { player ->
            // 👉 Si el usuario minimizó la app y volvió, lo forzamos a saltar al "Vivo" actual
            if (player.isCurrentMediaItemLive) {
                player.seekToDefaultPosition()
            }
            player.playWhenReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.stop()
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}