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
import androidx.media3.exoplayer.DefaultLoadControl


class PlayerActivity : AppCompatActivity() {
    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var isLiveStream = false
    private lateinit var menuButton: ImageButton

    private var reconnectionAttempts = 0 // Contador de intentos de reconexión
    private val maxReconnectionAttempts = 5 // Máximo de intentos permitidos

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
            showErrorDialog("No se recibió la URL de streaming.")
            return
        }

        playerView = findViewById(R.id.player_view)
        playerView.useController = true
        initializePlayer()
    }

    private fun initializePlayer() {
        // Configura el LoadControl
        val loadControl = DefaultLoadControl.Builder()
            // Ajusta el tamaño del búfer según tus necesidades
            .setTargetBufferBytes(2 * 1024 * 1024)  // 2MB
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Crea el ExoPlayer
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build().also { exoPlayer ->
                // Asocia el ExoPlayer con el PlayerView
                playerView.player = exoPlayer
                exoPlayer.addListener(playerListener)

                // Configura el MediaItem
                val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))

                // Prepara el ExoPlayer para la reproducción
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()

                // Ajusta el comportamiento de la reproducción según sea necesario
                exoPlayer.playWhenReady = false // Inicia la reproducción al tocar un botón o un evento específico
            }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("KeyCodeTest", "Tecla presionada: $keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                showBottomSheet()
                true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                Log.d("KeyCodeTest", "Página Arriba presionada")
                showBottomSheet()
                true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                Log.d("KeyCodeTest", "Página Abajo presionada")
                showBottomSheet()
                true
            }

            174 -> { // Código del botón del control remoto
                Log.d("KeyCodeTest", "Botón del control remoto (174) presionado")
                showBottomSheet()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("KeyCodeTest", "Tecla liberada: $keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                Log.d("KeyCodeTest", "Menu presionado")
                showBottomSheet()
                true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                Log.d("KeyCodeTest", "Página Arriba liberada")
                showBottomSheet()
                true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                Log.d("KeyCodeTest", "Página Abajo liberada")
                showBottomSheet()
                true
            }

            174 -> { // Código del botón del control remoto
                Log.d("KeyCodeTest", "Botón del control remoto (174) liberado")
                showBottomSheet()
                true
            }

            else -> super.onKeyUp(keyCode, event)
        }
    }

    private var isBottomSheetShowing = false

    private fun isShowing(): Boolean {
        val fragmentManager = supportFragmentManager
        val fragment = fragmentManager.findFragmentByTag(MoviesMenu::class.java.simpleName)
        return fragment != null && fragment.isVisible
    }

    private fun dismissIfShowing() {
        val fragmentManager = supportFragmentManager
        val fragment = fragmentManager.findFragmentByTag(MoviesMenu::class.java.simpleName)
        if (fragment != null && fragment.isVisible) {
            (fragment as MoviesMenu).dismiss()
        }
    }

    private fun showBottomSheet() {
        if (isBottomSheetShowing) {
            return  // Evita abrir si ya está mostrándose
        }

        // Usa el método isShowing para verificar si ya está visible antes de cerrarlo
        if (isShowing()) {
            dismissIfShowing() // Cierra si ya está mostrando
        }

        // Ahora muestra el nuevo bottom sheet
        isBottomSheetShowing = true  // Indica que está mostrándose
        val moviesMenu = MoviesMenu()

        // Agrega un listener para saber cuándo se ha cerrado el fragmento
        moviesMenu.addOnDismissListener {
            isBottomSheetShowing = false  // Resetea cuando el diálogo se cierra
        }

        moviesMenu.show(supportFragmentManager, MoviesMenu::class.java.simpleName)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> Log.d("PlayerActivity", "Reproductor almacenando en búfer...")

                Player.STATE_READY -> {
                    Log.d("PlayerActivity", "Reproductor listo. Reproduciendo...")
                    reconnectionAttempts = 0 // Reiniciar contador si está reproduciendo correctamente
                }

                Player.STATE_ENDED -> {
                    Log.d("PlayerActivity", "Reproducción finalizada. Intentando reconectar...")
                    attemptReconnection()
                }

                Player.STATE_IDLE -> {
                    Log.d("PlayerActivity", "Reproductor inactivo. Intentando reconectar...")
                    attemptReconnection()
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerActivity", "Error en el reproductor: ${error.message}")
            attemptReconnection()
        }
    }

    private fun attemptReconnection() {
        if (reconnectionAttempts < maxReconnectionAttempts) {
            Log.d("PlayerActivity", "Intento de reconexión: $reconnectionAttempts")
            reconnectionAttempts++
            reconnectLiveStream()
        } else {
            Log.d("PlayerActivity", "Máximo número de intentos alcanzado. Mostrando diálogo de error.")
            showErrorDialog("No se pudo reconectar al stream.")
        }
    }

    private fun reconnectLiveStream() {
        if (isLiveStream || player?.playbackState == Player.STATE_ENDED || player?.playbackState == Player.STATE_IDLE) {
            Log.d("PlayerActivity", "Intentando reconectar a la transmisión en vivo...")
            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            player?.setMediaItem(mediaItem)
            player?.prepare()
            player?.playWhenReady = true
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
                finish()
            }
            .setNeutralButton("Donar") { _, _ ->
                val donationUrl = "https://www.floristerialoslirios.com/"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(donationUrl))
                startActivity(intent)
            }
            .show()
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

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
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
    override fun onBackPressed() {
        super.onBackPressed()
        // Redirige a MoviesPrincipal
        val intent = Intent(this, MoviesPrincipal::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish() // Opcional: Cierra la actividad actual
    }

}
