package com.creativem.fulltv

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle

import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

import androidx.appcompat.app.AppCompatActivity

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException

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

        streamUrl = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""

        if (streamUrl.isEmpty()) {
            Log.e("PlayerActivity", "No se recibió la URL de streaming.")
            showErrorDialog("No se recibió la URL de streaming.") // Muestra el dialogo de error
            return
        }

        playerView = findViewById(R.id.player_view)
        playerView.useController = true
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

                Player.STATE_ENDED -> {
                    Log.d("PlayerActivity", "Reproducción finalizada.")
                    reconnectLiveStream() // Intenta reconectar si finaliza
                }

                Player.STATE_IDLE -> {
                    Log.d("PlayerActivity", "Reproductor inactivo. Intentando reconectar...")
                    reconnectLiveStream() // Intenta reconectar si está inactivo
                }

                Player.STATE_READY -> {
                    Log.d("PlayerActivity", "Error en la reproducción.")
                    showErrorDialog("Error en la reproducción.") // Muestra el dialogo de error
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerActivity", "Error en el reproductor: ${error.message}")
            showErrorDialog("Error en la reproducción: ${error.message}") // Muestra el dialogo de error
        }
    }

    private fun reconnectLiveStream() {
        if (isLiveStream) {
            Log.d("PlayerActivity", "Intentando reconectar a la transmisión en vivo...")
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true // Asegúrate de reanudar la reproducción
        }
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("¡Alquila Tu Acceso al Contenido!")
            .setMessage(
                "Este contenido ha sido bloqueado temporalmente, pero con una donación voluntaria, puedes \"alquilar\" el acceso por un tiempo limitado.\n" +
                        "\n" +
                        "Con tu ayuda, podremos restaurarlo en menos de 2 horas.\n" +
                        "\n" +
                        "Cada contribución cuenta para que sigamos ofreciendo este servicio! ¡Haz tu donación ahora y vuelve a disfrutar de lo que te gusta!\n" +
                        "\nReporte de donacion al WhatsApp(3028667672)"
            )
            .setPositiveButton("Volver al contenido") { _, _ ->
                val intent = Intent(this, MoviesPrincipal::class.java)
                startActivity(intent)
                finish() // Finaliza la actividad actual
            }
            .setNeutralButton("Donar") { _, _ ->
                // Abre el enlace de donación
                val donationUrl =
                    "https://www.floristerialoslirios.com/" // Cambia esto por tu URL de donación
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(donationUrl))
                startActivity(intent)
            }
            .show() // Quitar el botón negativo
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

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
    }


    private fun showBottomSheet() {
        // Cerrar el BottomSheet si ya está visible
        val fragment = supportFragmentManager.findFragmentByTag(MoviesMenu::class.java.simpleName)
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

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("KeyCodeTest", "Tecla liberada: $keyCode")

        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                showBottomSheet()
                true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                // Acción para la tecla Página Arriba
                Log.d("KeyCodeTest", "Página Arriba presionada")
                showBottomSheet()
                // Agrega aquí la acción que desees para la tecla Página Arriba
                true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                // Acción para la tecla Página Abajo
                Log.d("KeyCodeTest", "Página Abajo presionada")
                showBottomSheet()
                // Agrega aquí la acción que desees para la tecla Página Abajo
                true
            }

            174 -> { // Código del botón del control remoto
                Log.d("KeyCodeTest", "Botón del control remoto (174) presionado")
                // Agrega aquí la acción que desees para el botón del control remoto
                showBottomSheet()
                true
            }

            else -> super.onKeyUp(keyCode, event)
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("KeyCodeTest", "Tecla presionada: $keyCode") // Muestra el código de la tecla

        return super.onKeyDown(keyCode, event) // Llama al método padre
    }
}
