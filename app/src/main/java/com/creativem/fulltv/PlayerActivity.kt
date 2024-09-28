package com.creativem.fulltv

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle

import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton

import androidx.appcompat.app.AppCompatActivity

import androidx.media3.common.MediaItem

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

import androidx.media3.ui.PlayerView
import com.google.android.material.snackbar.Snackbar



class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var isLiveStream = false // Flag para indicar si el stream es en vivo
    private lateinit var menuButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        menuButton = findViewById(R.id.menu_button)
        menuButton.visibility = View.GONE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.player_view)
        playerView.useController = true

        streamUrl = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""

        if (streamUrl.isEmpty()) {
            Log.e("PlayerActivity", "No se recibió la URL de streaming.")
            showErrorSnackbar("No se recibió la URL de streaming.")
            finish()
            return
        }
        initializePlayer()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this))
            .build().also { exoPlayer ->
                playerView.player = exoPlayer
                exoPlayer.addListener(playerListener)

                // Establece el MediaItem antes de preparar el reproductor
                val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                exoPlayer.setMediaItem(mediaItem)

                // Prepara el reproductor
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true // Iniciar automáticamente
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            val currentMediaItem = player?.currentMediaItem

            if (currentMediaItem != null) {
                val title = currentMediaItem.mediaMetadata.title
                val artist = currentMediaItem.mediaMetadata.artist
                Log.d("PlayerActivity", "Título: $title, Artista: $artist")

                // Verifica si es un stream en vivo
                isLiveStream = player?.duration == androidx.media3.common.C.TIME_UNSET
                Log.d("PlayerActivity", "isLiveStream: $isLiveStream")
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> Log.d(
                    "PlayerActivity",
                    "Reproductor almacenando en búfer..."
                )

                Player.STATE_READY -> {
                    Log.d("PlayerActivity", "Reproductor listo. Reproduciendo...")
                }

                Player.STATE_ENDED -> Log.d("PlayerActivity", "Reproducción finalizada.")
                Player.STATE_IDLE -> Log.d("PlayerActivity", "Reproductor inactivo.")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
    }

    private fun releasePlayer() {
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    private fun reconnectLiveStream() {
        if (isLiveStream) {
            Log.d("PlayerActivity", "Intentando reconectar a la transmisión en vivo...")
            player?.prepare() // Preparar nuevamente el reproductor para reconectar
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterFullScreen()
        }
    }

    private fun enterFullScreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    private fun showErrorSnackbar(message: String) {
        Snackbar.make(playerView, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            174 -> { // Reemplaza 200 con el keyCode del botón que quieras manejar
                showBottomSheet()
                true // Indica que el botón ha sido manejado
            }

            else -> super.onKeyDown(keyCode, event) // Llama a la implementación base
        }
    }

    private fun showBottomSheet() {
        // Cerrar el BottomSheet si ya está visible
        val fragment =
            supportFragmentManager.findFragmentByTag(MoviesMenu::class.java.simpleName)
        if (fragment != null && fragment.isVisible) {
            (fragment as MoviesMenu).dismiss() // Cerrar el fragmento si está visible
        }

        // Mostrar el BottomSheet nuevamente
        val moviesMenu = MoviesMenu()
        moviesMenu.show(
            supportFragmentManager,
            MoviesMenu::class.java.simpleName
        )
    }
}