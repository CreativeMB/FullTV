package com.creativem.fulltv

import android.graphics.Color
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
                    mostrarImagenEsperaYSalir()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error crítico durante la extracción con YoutubeDL: ${e.message}", e)
                // 👉 SOLUCIÓN: Si el canal está offline o da error por no transmitir, mostramos la imagen
                mostrarImagenEsperaYSalir()
            }
        }
    }

    private fun mostrarImagenEsperaYSalir() {
        runOnUiThread {
            progressBar.visibility = View.GONE
            exoPlayer?.stop() // Detenemos ExoPlayer por completo

            // Creamos un diálogo nativo a pantalla completa sobre la actividad actual
            val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

            val imageView = android.widget.ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = android.widget.ImageView.ScaleType.FIT_XY

                // 👉 Usa el nombre exacto de tu imagen guardada en res/drawable
                setImageResource(R.drawable.fondomundial)

                isFocusable = true
                isFocusableInTouchMode = true

                // Si el usuario presiona el botón central (OK) del control, cierra el aviso y sale al menú
                setOnClickListener {
                    dialog.dismiss()
                    finish()
                }
            }

            // Si el usuario presiona el botón "Atrás" del control remoto, también cierra todo de vuelta al menú
            dialog.setOnCancelListener { finish() }

            dialog.setContentView(imageView)
            dialog.show()

            imageView.requestFocus() // Forzamos el foco en la imagen para capturar las pulsaciones del control
        }
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
                        androidx.media3.common.Player.STATE_READY -> {
                            Log.i(TAG, "ExoPlayer: ¡Reproduciendo el Stream!")
                            // 👉 RECONEXIÓN: Si conectó con éxito, reseteamos el contador de fallos a 0
                            intentosReconexion = 0
                        }
                        androidx.media3.common.Player.STATE_ENDED -> Log.i(TAG, "ExoPlayer: Stream finalizado.")
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e(TAG, "Error nativo de ExoPlayer: ${error.message}", error)

                    // 👉 RECONEXIÓN LÓGICA PRINCIPAL: Si hay un error, intentamos reconectar en lugar de salir
                    if (intentosReconexion < MAX_INTENTOS) {
                        intentosReconexion++
                        Log.w(TAG, "El token caducó o falló la red. Reconectando... (Intento $intentosReconexion de $MAX_INTENTOS)")

                        runOnUiThread {
                            progressBar.visibility = View.VISIBLE
                            Toast.makeText(this@LivePlayerActivity, "Reconectando señal en vivo...", Toast.LENGTH_SHORT).show()
                        }

                        // Volvemos a llamar a yt-dlp para obtener una URL fresca
                        extraerYReproducir(originalYoutubeUrl)
                    } else {
                        lanzarErrorYSalir("La señal en vivo se ha interrumpido de forma permanente.")
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
        exoPlayer?.playWhenReady = true
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