package com.creativem.fulltv
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.util.Util
import com.google.android.material.snackbar.Snackbar

class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var streamUrl: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Orientación y pantalla encendida
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.player_view)
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        playerView.useController = true

        // Obtener la URL del Intent
        streamUrl = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""
        Log.d("PlayerActivity", "Stream URL recibida: $streamUrl")

        if (streamUrl.isEmpty()) {
            Log.e("PlayerActivity", "No se recibió la URL de streaming.")
            showErrorSnackbar("No se recibió la URL de streaming.")
            finish()
            return
        }

        // Inicializar ExoPlayer
        initializePlayer()
    }

    private fun initializePlayer() {
        if (player == null) {
            player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(this))
                .build()
                .also { exoPlayer ->
                    playerView.player = exoPlayer
                    exoPlayer.addListener(playerListener)

                    // Cargar el stream
                    val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    Log.d("PlayerActivity", "Cargando stream: $streamUrl")
                }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerActivity", "Error en la reproducción: ${error.message}")
            showErrorSnackbar("Error al cargar la transmisión. Por favor, inténtalo de nuevo.")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> Log.d("PlayerActivity", "Reproductor almacenando en búfer...")
                Player.STATE_READY -> Log.d("PlayerActivity", "Reproductor listo. Reproduciendo stream...")
                Player.STATE_ENDED -> Log.d("PlayerActivity", "Reproducción finalizada.")
                Player.STATE_IDLE -> Log.d("PlayerActivity", "Reproductor inactivo.")
                else -> Log.d("PlayerActivity", "Estado de reproducción desconocido: $playbackState")
            }
        }
    }

    private fun releasePlayer() {
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    override fun onStart() {
        super.onStart()
        initializePlayer()
    }

    override fun onResume() {
        super.onResume()
        if (player == null) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        releasePlayer()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    override fun onBackPressed() {
        releasePlayer()
        super.onBackPressed()
    }

    private fun enterFullScreen() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun exitFullScreen() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterFullScreen()
        }
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(playerView, message, Snackbar.LENGTH_LONG).show()
    }
}