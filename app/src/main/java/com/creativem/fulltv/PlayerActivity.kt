package com.creativem.fulltv

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var playbackPosition: Long = 0L // Posición de reproducción guardada
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
        // Listener para el botón de menú
        menuButton.setOnClickListener {
            showMoviesBottomSheet()
        }
    }

    private fun showMoviesBottomSheet() {
        val bottomSheetFragment = MoviesBottomSheetFragment()
        bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
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
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val currentMediaItem = player?.currentMediaItem

            if (currentMediaItem != null) {
                val title = currentMediaItem.mediaMetadata.title
                val artist = currentMediaItem.mediaMetadata.artist
                Log.d("PlayerActivity", "Título: $title, Artista: $artist")

                // Verifica si es un stream en vivo
                isLiveStream = player?.duration == C.TIME_UNSET
                Log.d("PlayerActivity", "isLiveStream: $isLiveStream")
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> Log.d("PlayerActivity", "Reproductor almacenando en búfer...")
                Player.STATE_READY -> {
                    Log.d("PlayerActivity", "Reproductor listo. Reproduciendo...")

                    // Mostrar el diálogo para reanudar si es necesario
                    if (playbackPosition > 0 && !isLiveStream) {
                        showResumeDialog()
                    } else if (!isLiveStream) {
                        player?.playWhenReady = true // Iniciar la reproducción solo si no hay posición
                    }
                }
                Player.STATE_ENDED -> Log.d("PlayerActivity", "Reproducción finalizada.")
                Player.STATE_IDLE -> Log.d("PlayerActivity", "Reproductor inactivo.")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.let {
            if (!isLiveStream) {
                playbackPosition = it.currentPosition // Guarda la posición actual
            }
            it.pause()
        }
    }

    private var isDialogShowing = false
    override fun onResume() {
        super.onResume()
        Log.d("PlayerActivity", "onResume called")

        // Comprueba si hay una posición de reproducción guardada
        if (playbackPosition > 0 && !isLiveStream && !isDialogShowing) {
            Log.d("PlayerActivity", "Showing resume dialog")
            isDialogShowing = true // Marcar como diálogo mostrando
            showResumeDialog() // Mostrar diálogo para reanudar
        } else {
            player?.playWhenReady = true // Reanudar automáticamente si no hay posición guardada
        }
    }

    private fun releasePlayer() {
        player?.run {
            if (!isLiveStream) {
                playbackPosition = currentPosition
            }
            removeListener(playerListener)
            release()
        }
        player = null
    }

    private fun showResumeDialog() {
        // Evitar mostrar el diálogo si es un stream en vivo
        if (isLiveStream) {
            return
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Continuar Reproducción")
            .setMessage("Has reproducido hasta ${playbackPosition / 1000} segundos. ¿Deseas continuar o reiniciar?")
            .setPositiveButton("Continuar") { dialogInterface, _ ->
                dialogInterface.dismiss() // Cierra el diálogo
                resumePlayer(playbackPosition) // Reanudar desde la posición guardada
                isDialogShowing = false // Restablecer el flag
            }
            .setNegativeButton("Reiniciar") { dialogInterface, _ ->
                dialogInterface.dismiss() // Cierra el diálogo
                resumePlayer(0L) // Reiniciar la reproducción
                isDialogShowing = false // Restablecer el flag
            }
            .create()

        dialog.setOnDismissListener {
            isDialogShowing = false // Asegúrate de restablecer el flag al cerrar el diálogo
        }

        dialog.show()
    }

    private fun resumePlayer(position: Long) {
        if (player == null) {
            player = ExoPlayer.Builder(this)
                .setMediaSourceFactory(DefaultMediaSourceFactory(this))
                .build().also { exoPlayer ->
                    playerView.player = exoPlayer
                    exoPlayer.addListener(playerListener)

                    val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                    exoPlayer.setMediaItem(mediaItem)
                    exoPlayer.seekTo(position)
                    exoPlayer.prepare() // Prepara el reproductor aquí
                    exoPlayer.playWhenReady = true // Inicia la reproducción automáticamente
                }
        } else {
            player?.seekTo(position) // Retomar desde la posición guardada
            player?.playWhenReady = true // Asegúrate de que se reproduzca
        }
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
        // Guarda la posición del vídeo antes de liberar el reproductor
        player?.let {
            if (!isLiveStream) {
                playbackPosition = it.currentPosition
            }
        }
        releasePlayer()
    }

    // Sobrescribiendo el método onBackPressed
    override fun onBackPressed() {
        // Aquí puedes implementar cualquier lógica que necesites
        super.onBackPressed() // Esto cerrará la actividad normalmente
    }

    // Captura de teclas
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                // Mostrar el BottomSheet al presionar la tecla de cruzeta hacia arriba
                showBottomSheet()
                true // Indica que la tecla ha sido manejada
            }
            else -> super.onKeyDown(keyCode, event) // Llama a la implementación base
        }
    }
    private var isBottomSheetVisible = false
    private fun showBottomSheet() {
        // Verificar si el BottomSheet ya está visible
        if (!isBottomSheetVisible) {
            val fragment = supportFragmentManager.findFragmentByTag(MoviesBottomSheetFragment::class.java.simpleName)
            if (fragment == null || !fragment.isVisible) {
                val moviesBottomSheetFragment = MoviesBottomSheetFragment()
                moviesBottomSheetFragment.show(supportFragmentManager, MoviesBottomSheetFragment::class.java.simpleName)
                isBottomSheetVisible = true // Actualizar el estado a visible
            }
        }
    }
}