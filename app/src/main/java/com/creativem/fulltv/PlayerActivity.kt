package com.creativem.fulltv

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
import com.creativem.fulltv.ui.data.Movie
import com.creativem.fulltv.ui.data.RelojCuston
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import android.text.format.DateUtils
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import kotlinx.coroutines.MainScope
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.Protocol


class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var isLiveStream = false
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var adapter: MoviesMenuAdapter
    private lateinit var moviesCollection: CollectionReference
    private lateinit var firestore: FirebaseFirestore
    private lateinit var relojhora: RelojCuston
    private var reconnectionAttempts = 0 // Contador de intentos de reconexión
    private val maxReconnectionAttempts = 5 // Máximo de intentos permitidos

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnableActualizar: Runnable
    private lateinit var runnableOcultar: Runnable
    private val hideControlsDelay: Long = 10000 // 10 segundos
    private val updateInterval: Long = 1000 // 1 segundo

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
            binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.GONE
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
        firestore.collection("movies")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val movies =
                    mutableListOf<Movie>() // Crear una lista temporal para almacenar las películas

                // Cargar las películas
                val jobs = snapshot.documents.map { document ->
                    CoroutineScope(Dispatchers.IO).async {
                        val title = document.getString("title") ?: "Sin título"
                        val synopsis = document.getString("synopsis") ?: "Sin sinopsis"
                        val imageUrl = document.getString("imageUrl") ?: ""
                        val streamUrl = document.getString("streamUrl") ?: ""
                        val createdAt = document.getTimestamp("createdAt") ?: return@async null

                        // Llama a isUrlValid de forma asíncrona
                        val isValid = isUrlValid(streamUrl)

                        // Crea una película y retorna
                        Movie(title, synopsis, imageUrl, streamUrl, createdAt, isValid)
                    }
                }

                // Esperar a que todos los trabajos se completen
                GlobalScope.launch(Dispatchers.Main) {
                    jobs.forEach { job ->
                        job.await()?.let { movie ->
                            movies.add(movie) // Añadir la película a la lista temporal
                        }
                    }

                    // Actualizar el adaptador con la lista de películas
                    adapter.updateMovies(movies)
                }
            }
    }

    // Cambia la función isUrlValid para que sea suspend
    private suspend fun isUrlValid(url: String): Boolean {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).head().build()

        return try {
            val response: Response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            false
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
        // Configura el LoadControl
        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(2 * 1024 * 1024)  // 2MB
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val dataSourceFactory = createInsecureDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        // Crea el ExoPlayer utilizando createRenderersFactory()
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().also { exoPlayer ->

                // Asocia el ExoPlayer con el PlayerView usando binding
                binding.reproductor.player = exoPlayer
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
        showControlsAndResetTimer()
        Log.d("KeyCodeTest", "Tecla presionada: $keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                mostarpélis()
                true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                Log.d("KeyCodeTest", "Página Arriba presionada")
                mostarpélis()
                true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                Log.d("KeyCodeTest", "Página Abajo presionada")
                mostarpélis()
                true
            }

            174 -> { // Código del botón del control remoto
                Log.d("KeyCodeTest", "Botón del control remoto (174) presionado")
                mostarpélis()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            Log.d("PlayerActivity", "onPlaybackStateChanged - playbackState: $playbackState")
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    Log.d("PlayerActivity", "Reproductor almacenando en búfer...")
                    // Puedes mostrar un indicador de carga si lo necesitas
                }
                Player.STATE_READY -> {
                    Log.d("PlayerActivity", "Reproductor listo. Reproduciendo...Tobias")
                    reconnectionAttempts = 0
                    actualizarTiempo() // Iniciar la actualización del tiempo
                }
                Player.STATE_ENDED -> {
                    Log.d("PlayerActivity", "Reproducción finalizada.")
                    if (isLiveStream) {
                        attemptReconnection()
                    } else {
                        // Detener la actualización del tiempo al finalizar la reproducción
                        handler.removeCallbacks(runnable)
                        Log.d("PlayerActivity", "Se canceló la próxima actualización (STATE_ENDED)")
                    }
                }
                Player.STATE_IDLE -> {
                    Log.d("PlayerActivity", "Reproductor inactivo.")
                    if (isLiveStream) {
                        attemptReconnection()
                    } else {
                        // Detener la actualización del tiempo cuando el reproductor está inactivo
                        handler.removeCallbacks(runnable)
                        Log.d("PlayerActivity", "Se canceló la próxima actualización (STATE_IDLE)")
                    }
                }
                else -> {
                    Log.w("PlayerActivity", "Estado de reproducción desconocido: $playbackState")
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d("PlayerActivity", "onIsPlayingChanged - isPlaying: $isPlaying")
            super.onIsPlayingChanged(isPlaying)
            if (isPlaying) {
                handler.postDelayed(runnableOcultar, hideControlsDelay) // Programa la ocultación de los controles
                Log.d("PlayerActivity", "Se programó la próxima actualización")
            } else {
                handler.removeCallbacks(runnableOcultar) // Solo cancela la ocultación
                Log.d("PlayerActivity", "Se canceló la próxima actualización")
            }
        }
        // Método para manejar errores del reproductor
        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerActivity", "Error en el reproductor: ${error.message}")
            // Intento de reconexión
            if (reconnectionAttempts < maxReconnectionAttempts) {
                Log.d("PlayerActivity", "Intento de reconexión: $reconnectionAttempts")
                reconnectionAttempts++

                Handler(Looper.getMainLooper()).postDelayed({
                    // Reconexión al stream
                    val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                    player?.setMediaItem(mediaItem)
                    player?.prepare()
                    player?.playWhenReady = true
                }, 2000) // 2 segundos de retraso para el siguiente intento
            } else {
                Log.d(
                    "PlayerActivity",
                    "Máximo número de intentos alcanzado. Mostrando diálogo de error."
                )
                showErrorDialog("No se pudo reconectar al stream.")
            }
        }
    }

    private fun attemptReconnection() {
        if (reconnectionAttempts < maxReconnectionAttempts) {
            Log.d("PlayerActivity", "Intento de reconexión: $reconnectionAttempts")
            reconnectionAttempts++
            reconnectLiveStream()
        } else {
            Log.d(
                "PlayerActivity",
                "Máximo número de intentos alcanzado. Mostrando diálogo de error."
            )
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
                        "Con tu ayuda, podremos restaurarlo En breve.\n" +
                        "\n" +
                        "Cada contribución cuenta para que sigamos ofreciendo este servicio! ¡Haz tu donación ahora y vuelve a disfrutar de lo que te gusta!\n" +
                        "\nReporte de donacion al WhatsApp(3028667672)"
            )
            .setPositiveButton("Volver al contenido") { dialog, _ ->
                dialog.dismiss() // Cierra el diálogo
                onBackPressed() // Simula el botón de retroceso en lugar de terminar la actividad
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
            val tiemporeproducido = binding.reproductor.findViewById<TextView>(R.id.tiemporeproducido) // TextView del contador
            val tiempototal = binding.reproductor.findViewById<TextView>(R.id.tiempototal)
            val seekBar = binding.reproductor.findViewById<SeekBar>(R.id.progreso)
            Log.d("PlayerActivity", "actualizarTiempo() llamado")

            val posicionActual = player?.currentPosition ?: 0
            val duracionTotal = player?.duration ?: 0
            Log.d("PlayerActivity", "Posición actual: $posicionActual, Duración total: $duracionTotal")

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

    private fun createInsecureDataSourceFactory(): HttpDataSource.Factory {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        // Configurar OkHttpClient
        val client = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .protocols(listOf(Protocol.HTTP_1_1, Protocol.HTTP_2)) // Establecer protocolos soportados
            .build()

        return OkHttpDataSource.Factory(client)
    }

}

