package com.creativem.fulltv.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.fulltv.databinding.ActivityPlayerBinding
import com.creativem.fulltv.data.RelojCuston
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.text.format.DateUtils
import android.widget.Toast
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.media3.exoplayer.DefaultRenderersFactory
import kotlinx.coroutines.MainScope
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import com.creativem.fulltv.R
import com.creativem.fulltv.adapter.FirestoreRepository
import com.creativem.fulltv.menu.MoviesMenuAdapter
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow


class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var isLiveStream = false
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var adapter: MoviesMenuAdapter
    private lateinit var moviesCollection: CollectionReference
    private lateinit var firestore: FirebaseFirestore
    private lateinit var relojhora: RelojCuston
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnableActualizar: Runnable
    private lateinit var runnableOcultar: Runnable
    private val hideControlsDelay: Long = 10000 // 10 segundos
    private val updateInterval: Long = 1000 // 1 segundo

    private var reconnectionAttempts = 0
    private val maxReconnectionAttempts = 10
    private val initialReconnectionDelayMs = 5000L
    private var isReconnecting = false
    private var isPlaybackActive = false // Indica si la reproducción ha sido activa
    private val playbackStartTime = AtomicLong(0) // Tiempo en que inicia la reproducción
    private var lastKnownPosition: Long = 0 // Para guardar la última posición conocida


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)



        binding.reproductor.useController = false

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        streamUrl = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""

        if (streamUrl.isEmpty()) {
            Log.e("PlayerActivity", "No se recibió la URL de streaming.")
            showErrorDialog("No se recibió la URL de streaming.")
            return
        }
        val textHora = binding.textHora
        val textfecha = binding.textfecha
        val relojCuston = RelojCuston(textHora, textfecha)
        relojCuston.startClock()

        firestore = Firebase.firestore
        // Inicializa el RecyclerView
        initializeRecyclerView()
        // Inicializa el SeekBar desde el binding

        player = ExoPlayer.Builder(this).build()
        binding.reproductor.player = player
        initializePlayer()

        val menupelis = binding.reproductor.findViewById<ImageButton>(R.id.lista_pelis)
        menupelis.setOnClickListener {
            mostarpélis()

        }
        // Referencias a los botones

        // Botón Play/Pause
        val playPauseButton: ImageButton = findViewById(R.id.play_pause)
        playPauseButton.setOnClickListener {
            togglePlayPause()
        }

        // Botón Shuffle
        val shuffleButton: ImageButton = findViewById(R.id.home)
        shuffleButton.setOnClickListener {
            finish()
        }

        // Botón Pantalla Completa
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
//                    seekBar?.progress = progress // Actualización de la SeekBar
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                player?.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                player?.playWhenReady = true
            }
        })
        // Cargar películas de Firestore
        loadMoviesFromFirestore()

        runnableActualizar = Runnable { actualizarTiempo() }
        runnableOcultar = Runnable {
            binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility =
                View.GONE
        }

        binding.reproductor.setOnTouchListener { _, _ ->
            showControlsAndResetTimer()
            true
        }
        showControlsAndResetTimer() // Mostrar controles al inicio
        handler.postDelayed(runnableActualizar, updateInterval)
    }

    private fun mostarpélis() {
        Log.e("PlayerActivity", "clki menupelis")
        binding.recyclerViewMovies.visibility =
            if (binding.recyclerViewMovies.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun loadMoviesFromFirestore() {
        // Usar un CoroutineScope adecuado (puede ser un lifecycleScope si estás dentro de una Activity o Fragment)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Obtener la instancia de FirestoreRepository
                val repository = FirestoreRepository()

                // Obtener las películas desde Firestore
                val (peliculasValidas) = repository.obtenerPeliculas()

                // Actualizar el adaptador con la lista de películas válidas
                withContext(Dispatchers.Main) {
                    adapter.updateMovies(peliculasValidas)
                }

            } catch (exception: Exception) {
                // Manejo de errores si la obtención de películas falla
                exception.printStackTrace()
            }
        }
    }

    private fun initializeRecyclerView() {
        adapter = MoviesMenuAdapter(mutableListOf()) { movie ->
            startMoviePlayback(movie.streamUrl)
        }

        binding.recyclerViewMovies.adapter = adapter
        binding.recyclerViewMovies.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMovies.visibility = View.GONE
    }

    private fun startMoviePlayback(streamUrl: String) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra(
            "EXTRA_STREAM_URL",
            streamUrl
        ) // Asegúrate de usar el mismo nombre clave que usas en VideoPlayerActivity
        startActivity(intent)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        // Crea el DataSource.Factory
        val dataSourceFactory = DefaultDataSource.Factory(this)

        // Crea la MediaSourceFactory
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Configura el LoadControl
        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(2 * 1024 * 1024) // 2MB
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Crea el reproductor
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setRenderersFactory(
                DefaultRenderersFactory(this)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            ) // Esto habilita FFmpeg
            .setMediaSourceFactory(mediaSourceFactory)
            .build().also { exoPlayer ->
                // Asocia el ExoPlayer con el PlayerView usando binding
                binding.reproductor.player = exoPlayer

                // Configura el MediaItem
                val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))

                // Prepara el ExoPlayer para la reproducción
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()

                // Ajusta el comportamiento de la reproducción
                exoPlayer.playWhenReady = false

                // Añade el listener del reproductor
                exoPlayer.addListener(playerListener)
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    Log.d("PlayerActivity", "Reproductor almacenando en búfer...")
                    mostrarBuffer()
                }

                Player.STATE_READY -> {
                    Log.d("PlayerActivity", "Reproductor en estado READY")
                    isPlaybackActive = true
                    playbackStartTime.set(System.currentTimeMillis())
                    reconnectionAttempts = 0
                    isReconnecting = false

                    // Reanudar desde la última posición conocida
                    if (lastKnownPosition > 0) {
                        player?.seekTo(lastKnownPosition)
                        lastKnownPosition = 0 // Reiniciar la posición
                    }

                    actualizarTiempo()
                }

                Player.STATE_ENDED, Player.STATE_IDLE -> {
                    isPlaybackActive = false
                    Log.d("PlayerActivity", "Reproductor en estado ENDED o IDLE")

                    if (!isPlaybackActive && reconnectionAttempts >= maxReconnectionAttempts) {
                        Log.d("PlayerActivity", "No se pudo conectar al stream. Mostrando diálogo.")
                        showErrorDialog("No se pudo conectar al stream.")
                    } else if (isLiveStream && !isPlaybackActive) {
                        Log.d(
                            "PlayerActivity",
                            "Transmisión en vivo finalizada. Intentando reconectar..."
                        )
                        intentarReconexion()
                    } else {
                        Log.d("PlayerActivity", "Deteniendo actualizaciones de tiempo.")
                        handler.removeCallbacks(runnable)
                    }
                }

                else -> {
                    Log.w("PlayerActivity", "Estado desconocido: $playbackState")
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                handler.postDelayed(runnableOcultar, hideControlsDelay)
            } else {
                handler.removeCallbacks(runnableOcultar)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(
                "PlayerActivity",
                "Error en el reproductor: ${error.message} - Código: ${error.errorCode}"
            )

            // Intenta la reconexión solo si el error es recuperable y la reproducción ha sido activa
            if (isRecoverableError(error) && isPlaybackActive) {
                intentarReconexion()
            } else if (!isPlaybackActive && reconnectionAttempts >= maxReconnectionAttempts) {
                showErrorDialog("Error al cargar el video.")
            }
        }
    }

    private fun isRecoverableError(error: PlaybackException): Boolean {
        // Define qué errores son recuperables para tu caso específico
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR // Y otros errores recuperables
    }

    private fun mostrarBuffer() {
        val bufferedPercentage = player?.bufferedPercentage ?: 0
        val bufferedData = (bufferedPercentage / 100.0) * (2 * 1024) // 2MB de targetBufferBytes
        Toast.makeText(
            this,
            "Búfer almacenado: ${bufferedData.toInt()} KB",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun intentarReconexion() {
        if (isReconnecting) {
            return
        }

        if (reconnectionAttempts >= maxReconnectionAttempts) {
            Log.d("Reconexión", "Se alcanzó el máximo de intentos. No se puede reconectar.")
            reconnectionAttempts = 0
            isReconnecting = false

            if (!isPlaybackActive) {
                showErrorDialog("No se pudo conectar al stream después de varios intentos.")
            }
            return
        }

        if (!isNetworkConnected()) {
            Log.d("Reconexión", "No hay conexión a Internet. Esperando...")
            return
        }

        reconnectionAttempts++
        isReconnecting = true

        val tiempoEspera = initialReconnectionDelayMs * (2.0.pow((reconnectionAttempts - 1).toDouble())).toLong()

        Log.d("Reconexión", "Intentando reconectar (intento $reconnectionAttempts, espera de ${tiempoEspera}ms)...")

        // (Opcional) Mostrar indicador de reconexión

        Handler(Looper.getMainLooper()).postDelayed({
            reconectarStream()

            // Verificar el estado de la reproducción después de intentar reconectar
            Handler(Looper.getMainLooper()).postDelayed({
                if (!isPlaybackActive) {
                    // Si la reproducción no se ha reanudado, simular el clic en "Play"
                    simularClicPlay()
                } else {
                    // La reproducción se reanudó con éxito
                    Log.d("Reconexión", "Reconexión exitosa.")
                    reconnectionAttempts = 0
                }
            }, tiempoEspera) // Usar el mismo tiempo de espera

        }, tiempoEspera)
    }

    private fun simularClicPlay() {
        Log.d("Reconexión", "Simulando clic en Play. Reintentando reconexión...")
        intentarReconexion()
    }

    private fun reconectarStream() {
        Log.d("Reconexión", "Reanudando la reproducción...")
        isReconnecting = false

        if (!isNetworkConnected()) {
            Log.d("Reconexión", "Sin conexión a internet. Volviendo a intentar...")
            intentarReconexion()
            return
        }

        try {
            player?.seekTo(lastKnownPosition)
            player?.prepare()
            player?.playWhenReady = true
        } catch (e: Exception) {
            Log.e("Reconexión", "Error al reiniciar la reproducción: ${e.message}")
            intentarReconexion()
        }
    }

    // Método auxiliar para verificar la conexión a internet
    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
    }

    private fun showErrorDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle("¡Alquila Tu Acceso al Contenido!")
            .setMessage(
                "Este contenido ha sido bloqueado temporalmente, pero con una donación voluntaria, puedes \"alquilar\" el acceso por un tiempo limitado.\n" +
                        "\n" +
                        "Con tu ayuda, podremos restaurarlo En breve.\n" +
                        "\n" +
                        "Cada contribución cuenta para que sigamos ofreciendo este servicio! ¡Haz tu donación ahora y vuelve a disfrutar de lo que te gusta!\n" +
                        "\nReporte de donacion al WhatsApp(3028667672)"
            )
            .setPositiveButton("Volver al contenido") { dialog, _ ->
                dialog.dismiss() // Cierra el diálogo
                onBackPressed() // Simula el botón de retroceso en lugar de terminar la actividad
            }
            .setNeutralButton("Reporte de donacion") { _, _ ->
                val intent = Intent(this, PedidosActivity::class.java)
                startActivity(intent)
                finish() // Cierra el diálogo
                onBackPressed()
            }
            .show()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        handler.removeCallbacks(runnable)
    }

    override fun onResume() {
        super.onResume()
        player?.playWhenReady = true
        if (player?.isPlaying == true) {
            handler.postDelayed(runnable, updateInterval) // Reanudar actualizaciones al reproducir
        }
    }

    private fun releasePlayer() {
        player?.removeListener(playerListener)
        player?.release()
        player = null
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
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

    override fun onBackPressed() {
        // Si el GridView es visible, simplemente ocultarlo
        if (binding.recyclerViewMovies.visibility == View.VISIBLE) {
            binding.recyclerViewMovies.visibility = View.GONE
        } else {
            // Finaliza la actividad al presionar "atrás"
            finish()
        }
    }

    private fun showControlsAndResetTimer() {
        binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.VISIBLE
        // Reinicia el temporizador
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, hideControlsDelay)
    }

    private val runnable = Runnable {
        // Actualiza la UI
        actualizarTiempo()

        // Oculta los controles
        binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.GONE
    }

    private fun togglePlayPause() {
        val player = binding.reproductor.player // Accede al reproductor desde PlayerView

        if (player != null) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    // Función para alternar entre pantalla completa y vista normal
    private var currentAspectRatioMode = 0
    private fun cycleAspectRatio() {
        val playerView = binding.reproductor
        val aspectRatios = listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            // Puedes agregar otros modos de AspectRatioFrameLayout si lo necesitas
        )
        currentAspectRatioMode = (currentAspectRatioMode + 1) % aspectRatios.size
        playerView.resizeMode = aspectRatios[currentAspectRatioMode]
    }

    private fun actualizarTiempo() {
        MainScope().launch {
            val tiemporeproducido =
                binding.reproductor.findViewById<TextView>(R.id.tiemporeproducido) // TextView del contador
            val tiempototal = binding.reproductor.findViewById<TextView>(R.id.tiempototal)
            val seekBar = binding.reproductor.findViewById<SeekBar>(R.id.progreso)
            Log.d("PlayerActivity", "actualizarTiempo() llamado")

            val posicionActual = player?.currentPosition ?: 0
            val duracionTotal = player?.duration ?: 0
            Log.d(
                "PlayerActivity",
                "Posición actual: $posicionActual, Duración total: $duracionTotal"
            )

            tiemporeproducido.text = tiempoFormateado(posicionActual) // Actualiza el TextView
            tiempototal.text = tiempoFormateado(duracionTotal)

            if (duracionTotal > 0) {
                val progress = (posicionActual.toFloat() / duracionTotal * 100).toInt()
                seekBar.progress = progress // Actualiza la SeekBar
                Log.d("PlayerActivity", "SeekBar progress: $progress")

                handler.postDelayed(runnableActualizar, updateInterval)
            }
        }
    }

    private fun tiempoFormateado(tiempoMs: Long): String {
        return DateUtils.formatElapsedTime(tiempoMs / 1000)
    }

}

