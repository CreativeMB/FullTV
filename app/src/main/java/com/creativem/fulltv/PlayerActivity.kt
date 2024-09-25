package com.creativem.fulltv

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.Util
import com.google.android.exoplayer2.util.Util.SDK_INT
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout // Asegúrate de importar esto

class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null

    private lateinit var streamUrls: List<String>
    private var currentStreamIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Mantener la pantalla en modo paisaje
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // Mantener la pantalla encendida
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.player_view)

        // Obtener la lista de URLs del stream desde el Intent
        streamUrls = intent.getStringArrayListExtra("EXTRA_STREAM_URLS")?.toList() ?: emptyList()

        // Habilitar modo pantalla completa
        enterFullScreen()

        // Establecer el modo de redimensionamiento a llenar
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

        // Inicializar el reproductor
        initializePlayer()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            playerView.player = exoPlayer
            exoPlayer.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("PlayerActivity", "Error en la transmisión: ${error.message}")
                    // Intentar cargar la siguiente URL si hay más
                    loadNextStream()
                }
            })
            loadStream()
        }
    }

    private fun loadStream() {
        if (currentStreamIndex < streamUrls.size) {
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrls[currentStreamIndex]))
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
        } else {
            Log.e("PlayerActivity", "No hay más URLs disponibles.")
        }
    }

    private fun loadNextStream() {
        currentStreamIndex++
        loadStream()
    }

    private fun releasePlayer() {
        player?.run {
            playWhenReady = false
            release()
        }
        player = null
    }

    override fun onStart() {
        super.onStart()
        if (SDK_INT >= 24) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        if (SDK_INT < 24 || player == null) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (SDK_INT < 24) {
            releasePlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (SDK_INT >= 24) {
            releasePlayer()
        }
    }

    override fun onBackPressed() {
        super.onBackPressed() // Llama a la acción de retroceso por defecto
        releasePlayer() // Asegúrate de liberar el reproductor al salir
    }

    private fun enterFullScreen() {
        // Ocultar la barra de estado y la barra de navegación
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun exitFullScreen() {
        // Muestra las barras de estado y de navegación
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterFullScreen() // Asegurarse de que el modo de pantalla completa esté activo
        }
    }
}