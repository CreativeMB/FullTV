package com.creativem.fulltv.tv

import android.annotation.SuppressLint
import android.content.Intent
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.fulltv.data.RelojCuston
import com.creativem.fulltv.adapter.MoviesMenuAdapter
import kotlinx.coroutines.*
import android.text.format.DateUtils
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import com.creativem.fulltv.R
import com.creativem.fulltv.databinding.ActivityPlayerBinding
import com.creativem.fulltv.home.Nosotros

class PlayertvActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private lateinit var movieTitle: String
    private var movieYear: String = ""
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var adapter: MoviesMenuAdapter
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnableActualizar: Runnable
    private lateinit var runnableOcultar: Runnable
    private val hideControlsDelay: Long = 10000 // 10 segundos
    private val updateInterval: Long = 1000 // 1 segundo
    private var playerReleased = false
    private val playerHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.reproductor.keepScreenOn = true
        initializeRecyclerView()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val nombrePeliculaTextView: TextView = findViewById(R.id.nombrePelicula)
        intent?.let {
            streamUrl = it.getStringExtra("EXTRA_STREAM_URL") ?: ""
            movieTitle = it.getStringExtra("EXTRA_MOVIE_TITLE") ?: "Título desconocido"
            movieYear = it.getStringExtra("EXTRA_MOVIE_YEAR") ?: ""
            Log.d("PlayertvActivity", "Cargando stream desde URL: $streamUrl")
            nombrePeliculaTextView.text = movieTitle
        }

        if (streamUrl.isEmpty()) {
            Log.e("PlayertvActivity", "No se recibió la URL de streaming.")
            Toast.makeText(this, "No se recibió la URL de streaming.", Toast.LENGTH_SHORT).show()
            return
        }
        val textHora = binding.textHora
        val textfecha = binding.textfecha
        val relojCuston = RelojCuston(textHora, textfecha)
        relojCuston.startClock()

        actualizarTiempo()
        player = ExoPlayer.Builder(this).build()
        binding.reproductor.player = player
        initializePlayer()

        val menupelis = binding.reproductor.findViewById<ImageButton>(R.id.lista_pelis)
        menupelis.setOnClickListener {
            mostarpélis()
        }

        val playPauseButton: ImageButton = findViewById(R.id.play_pause)
        playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        val shuffleButton: ImageButton = findViewById(R.id.home)
        shuffleButton.setOnClickListener {
            player?.let {
                it.playWhenReady = false  // Detiene la reproducción inmediata
                it.stop() // Asegura que el audio se detenga
                it.clearMediaItems() // Limpia la lista de reproducción
                it.release() // Libera los recursos
            }
            player = null // Elimina la referencia

            finish()
        }
        val pedidosButton: ImageButton = findViewById(R.id.pedidos)
        pedidosButton.setOnClickListener {
            val intent = Intent(this, Nosotros::class.java)
            startActivity(intent)
        }

        val renderButton: ImageButton = findViewById(R.id.render)
        renderButton.setOnClickListener {
            cycleAspectRatio()
        }

        val seekBar = binding.reproductor.findViewById<SeekBar>(R.id.progreso)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = player?.duration ?: 1
                    val newPosition = (progress / 100.0 * duration).toLong()
                    player?.seekTo(newPosition)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                player?.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                player?.playWhenReady = true
            }
        })


        runnableActualizar = Runnable { actualizarTiempo() }
        runnableOcultar = Runnable {
            binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility =
                View.GONE
        }

        binding.reproductor.setOnTouchListener { _, _ ->
            showControlsAndResetTimer()
            true
        }
        showControlsAndResetTimer()
        handler.postDelayed(runnableActualizar, updateInterval)
        actualizarTiempo()
    }

    private fun mostarpélis() {
        binding.recyclerMoviesMenu.visibility =
            if (binding.recyclerMoviesMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun initializeRecyclerView() {
        adapter = MoviesMenuAdapter(mutableListOf()) { movie ->
            startMoviePlayback(movie.streamUrl, movie.title, movie.year)
        }
        binding.recyclerMoviesMenu.adapter = adapter
        binding.recyclerMoviesMenu.layoutManager = LinearLayoutManager(this)
        //loadMovies() //Comentado porque no se necesita cargar películas en este caso.
    }

    //Función loadMovies() se ha omitido ya que no se está utilizando en este reproductor simplificado.

    private fun startMoviePlayback(streamUrl: String, movieTitle: String, movieYear: String) {
        val intent = Intent(this, PlayertvActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("EXTRA_STREAM_URL", streamUrl)
        intent.putExtra("EXTRA_MOVIE_TITLE", movieTitle)
        intent.putExtra("EXTRA_MOVIE_YEAR", movieYear)
        startActivity(intent)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        if (streamUrl.isEmpty()) {
            Log.e("PlayertvActivity", "No se recibió la URL de streaming.")
            Toast.makeText(this, "No se recibió la URL de streaming.", Toast.LENGTH_SHORT).show()
            return
        }

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("User-Agent" to "Mozilla/5.0"))
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                40_000,  // Min Buffer
                100_000, // Max Buffer
                10_000,  // Min Playback Buffer
                10_000   // Min Rebuffer
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        player = ExoPlayer.Builder(this)

            .setLoadControl(loadControl)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            )
            .setMediaSourceFactory(mediaSourceFactory)
            .build().also { exoPlayer ->
                binding.reproductor.player = exoPlayer
                binding.reproductor.useController = false
                Log.d("PlayertvActivity", "URL asignada al reproductor: $streamUrl")
                val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.addListener(playerListener)
                exoPlayer.playWhenReady = true
            }
    }

    private var bufferingStartTime: Long = 0
    private val maxBufferingTimeMillis = 3000
    private var consecutiveBufferingAttempts = 0
    private val maxBufferingAttempts = 3
    private var isBuffering = false
    private val playerListener = @UnstableApi
    object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            playerHandler.post {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                        consecutiveBufferingAttempts++
                        if (consecutiveBufferingAttempts >= maxBufferingAttempts) {
                            Log.w(
                                "PlayertvActivity",
                                "Muchos intentos de buffering consecutivos, reiniciando..."
                            )
                            reiniciarReproductor()
                            consecutiveBufferingAttempts = 0
                        }
                    }

                    Player.STATE_READY -> {
                        consecutiveBufferingAttempts = 0
                        isBuffering = false

                        // Eliminamos la llamada a actualizarTiempo() inmediata
                        Handler(Looper.getMainLooper()).postDelayed({
                            actualizarTiempo()  //Llamada única y optimizada
                            mostrarBuffer()     //Llamada única y optimizada
                        }, 250) //Experimenta con diferentes valores de retraso (250ms o menos)

                    }

                    Player.STATE_ENDED -> {
                        Log.d("PlayertvActivity", "Reproducción finalizada, reiniciando...")
                        reiniciarReproductor()
                    }

                    Player.STATE_IDLE -> {
                        Log.d(
                            "PlayertvActivity",
                            "Reproductor en estado IDLE, intentando recuperar..."
                        )
                        reiniciarReproductor()
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(
                "PlayertvActivity",
                "Error de reproducción: ${error.message}, código: ${error.errorCode}, tipo de error: ${error.cause?.javaClass?.simpleName}",
                error
            )
            reiniciarReproductor()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                handler.postDelayed(runnableOcultar, hideControlsDelay)
            } else {
                handler.removeCallbacks(runnableOcultar)
            }
        }

        @SuppressLint("UnsafeOptInUsageError")
        override fun onPositionDiscontinuity(reason: Int) {
            Log.w("PlayertvActivity", "Discontinuidad de posición: $reason")
        }


        override fun onIsLoadingChanged(isLoading: Boolean) {
            Log.d("PlayertvActivity", "Está cargando: $isLoading")
        }

    }

    private fun reiniciarReproductor() {
        Log.d("PlayertvActivity", "Reiniciando el reproductor...")
        player?.release()  // Libera el reproductor actual
        player = null  // Elimina referencia
        handler.removeCallbacksAndMessages(null) // Detiene cualquier proceso en espera
        initializePlayer()  // Vuelve a iniciar ExoPlayer
    }

    private fun mostrarBuffer() {
        val bufferedPercentage = player?.bufferedPercentage ?: 0
        val bufferedData = (bufferedPercentage / 100.0) * (2 * 1024)
        Toast.makeText(
            this,
            "Búfer almacenado: ${bufferedData.toInt()} KB",
            Toast.LENGTH_SHORT
        ).show()
    }


    override fun onPause() {
        super.onPause()
        releasePlayer()
        playerHandler.removeCallbacksAndMessages(null) // Limpiar todos los mensajes del Handler
    }

    override fun onResume() {
        super.onResume()
        if (player == null && !playerReleased) {
            player = ExoPlayer.Builder(this).build()
            binding.reproductor.player = player
            initializePlayer()
        }
    }


    private fun releasePlayer() {
        if (!playerReleased) {
            player?.removeListener(playerListener)
            player?.release()
            player = null
            playerReleased = true
            Log.d("PlayertvActivity", "Reproductor liberado")
        }
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
        if (binding.recyclerMoviesMenu.visibility == View.VISIBLE) {
            binding.recyclerMoviesMenu.visibility = View.GONE
        } else {
            finish()
        }
    }

    private fun showControlsAndResetTimer() {
        binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.VISIBLE
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, hideControlsDelay)
    }

    private val runnable = Runnable {
        actualizarTiempo()
        binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.GONE
    }

    private fun togglePlayPause() {
        val player = binding.reproductor.player
        if (player != null) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    private var currentAspectRatioMode = 0

    @OptIn(UnstableApi::class)
    private fun cycleAspectRatio() {
        val playerView = binding.reproductor
        val aspectRatios = listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        )
        currentAspectRatioMode = (currentAspectRatioMode + 1) % aspectRatios.size
        playerView.resizeMode = aspectRatios[currentAspectRatioMode]
    }

    private fun actualizarTiempo() {
        if (player != null && player!!.isPlaying) {
            MainScope().launch {
                val tiemporeproducido =
                    binding.reproductor.findViewById<TextView>(R.id.tiemporeproducido)
                val tiempototal = binding.reproductor.findViewById<TextView>(R.id.tiempototal)
                val seekBar = binding.reproductor.findViewById<SeekBar>(R.id.progreso)

                val posicionActual = player?.currentPosition ?: 0
                val duracionTotal = player?.duration ?: 0

                tiemporeproducido.text = tiempoFormateado(posicionActual)
                tiempototal.text = tiempoFormateado(duracionTotal)

                if (duracionTotal > 0) {
                    val progress = (posicionActual.toFloat() / duracionTotal * 100).toInt()
                    seekBar.progress = progress
                    handler.postDelayed(runnableActualizar, updateInterval)
                    handleBuffering()
                }
            }
        }
    }

    private fun handleBuffering() {
        if (bufferingStartTime != 0L) {
            val bufferingDuration = System.currentTimeMillis() - bufferingStartTime
            if (bufferingDuration > maxBufferingTimeMillis) {
                Log.w(
                    "PlayertvActivity",
                    "Buffering prolongado ($bufferingDuration ms), intentando reiniciar..."
                )
                reiniciarReproductor()
                bufferingStartTime = 0L
            }
        }
    }

    private fun tiempoFormateado(tiempoMs: Long): String {
        return DateUtils.formatElapsedTime(tiempoMs / 1000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        showControlsAndResetTimer()
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                mostarpélis()
                true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                mostarpélis()
                true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                mostarpélis()
                true
            }

            174 -> {
                mostarpélis()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }
}