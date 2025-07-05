package com.creativem.fulltv.peliculas

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
import com.creativem.fulltv.principal.Reloj
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.text.format.DateUtils
import android.widget.Toast
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultRenderersFactory
import kotlinx.coroutines.MainScope
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculasvalidas.PeliculasMenuAdapter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow
import org.json.JSONObject
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.ViewGroup
import android.widget.ImageView
import androidx.activity.addCallback
import com.bumptech.glide.Glide

import com.creativem.fulltv.databinding.PlayerBinding
import com.creativem.fulltv.principal.Nosotros


import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope

import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit


@Suppress("DEPRECATION")
class PlayerPeliculas : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var movieImageUrl: String = ""
    private var movieYear: String = ""
    private lateinit var movieTitle: String
    private var isLiveStream = false
    private lateinit var binding: PlayerBinding
    private lateinit var adapter: PeliculasMenuAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var isProcessingOrder = false
    private var reconnectionAttempts = 0
    private val maxReconnectionAttempts = 10
    private var isReconnecting = false
    private var isPlaybackActive = false // Indica si la reproducción ha sido activa
    private val playbackStartTime = AtomicLong(0) // Tiempo en que inicia la reproducción
    private var lastKnownPosition: Long = 0 // Para guardar la última posición conocida
    private var menuAbierto = false
    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsDelay = 5000L // 5 segundos
    private val updateInterval = 1000L    // 1 segundo
    private var lastInteractionTime = 0L


    private var runnableOcultar = Runnable {
        binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.GONE
    }

    private val runnableActualizar: Runnable = Runnable {
        actualizarTiempo()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initializeRecyclerView()
        activarListenersEnControles()
        setupControlTimers()
        loadMovies()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.reproductor.useController = false

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val nombrePeliculaTextView: TextView = findViewById(R.id.nombrePelicula)
        val imagenPeliculaImageView: ImageView = findViewById(R.id.imagenPelicula)

        intent?.let {
            streamUrl = it.getStringExtra("EXTRA_STREAM_URL") ?: ""
            movieTitle = it.getStringExtra("EXTRA_MOVIE_TITLE") ?: "Título desconocido"
            movieYear = it.getStringExtra("EXTRA_MOVIE_YEAR") ?: ""
            movieImageUrl = it.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""

            nombrePeliculaTextView.text = movieTitle

            Glide.with(this)
                .load(movieImageUrl)
                .placeholder(R.drawable.icono)
                .error(R.drawable.icono)
                .into(imagenPeliculaImageView)

            if (streamUrl.isEmpty()) {
                showErrorDialog(movieTitle, movieYear)
                return@let
            }

        }

        val textHora = binding.textHora
        val textfecha = binding.textfecha
        val reloj = Reloj(textHora, textfecha)
        reloj.startClock()

        firestore = Firebase.firestore
        // Inicializa el SeekBar desde el binding
        actualizarTiempo()
        player = ExoPlayer.Builder(this).build()
        binding.reproductor.player = player
        initializePlayer()

        lifecycleScope.launch {
            // Esperar a que las validaciones estén listas
            validacioneslista.esperarCarga()

            // Luego cargar las películas al RecyclerView del menú
            loadMovies()
        }

        val menupelis = binding.reproductor.findViewById<ImageButton>(R.id.lista_pelis)
        menupelis.setOnClickListener {
            mostarpelis()

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
            player?.let {
                it.playWhenReady = false  // Detiene la reproducción inmediata
                it.stop() // Asegura que el audio se detenga
                it.clearMediaItems() // Limpia la lista de reproducción
                it.release() // Libera los recursos
            }
            player = null // Elimina la referencia

            finish()
        }
        // Botón pedidos
        val pedidosButton: ImageButton = findViewById(R.id.pedidos)
        pedidosButton.setOnClickListener {
            showErrorDialog(movieTitle, movieYear)
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

        // Referencia a los controles
        val controles = binding.reproductor.findViewById<View>(R.id.controles_reproductor)

// Define el runnable que revisa si debe ocultar los controles
        runnableOcultar = object : Runnable {
            override fun run() {
                val tiempoActual = System.currentTimeMillis()
                val tiempoInactivo = tiempoActual - lastInteractionTime

                // Si ha pasado suficiente tiempo y no hay interacción visible
                if (tiempoInactivo >= hideControlsDelay) {
                    controles.visibility = View.GONE
                } else {
                    handler.postDelayed(this, 1000)
                }
            }
        }

// Listeners para reiniciar el temporizador con cualquier interacción
        binding.reproductor.setOnTouchListener { _, _ ->
            showControlsAndResetTimer()
            true
        }

        binding.reproductor.setOnKeyListener { _, _, _ ->
            showControlsAndResetTimer()
            false
        }

// ✅ Inicia los controles y temporizador
        showControlsAndResetTimer()

        // 🕐 Iniciar temporizador de actualización
        handler.postDelayed(runnableActualizar, updateInterval)

        // 🕒 Primer tiempo inmediato
        actualizarTiempo()

        // ⬅️ Manejar botón "Atrás"
        onBackPressedDispatcher.addCallback(this) {
            val controles = binding.reproductor.findViewById<View>(R.id.controles_reproductor)
            val menuPelis = binding.reproductor.findViewById<RecyclerView>(R.id.recycler_movies_menu)

            when {
                menuPelis.visibility == View.VISIBLE -> {
                    menuPelis.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            menuPelis.visibility = View.GONE
                            menuPelis.alpha = 1f
                        }
                        .start()
                }

                controles.visibility == View.VISIBLE -> {
                    controles.visibility = View.GONE
                }

                else -> {
                    finish()
                }
            }
        }
    }

        private fun mostarpelis() {
        val menuPelis = binding.reproductor.findViewById<RecyclerView>(R.id.recycler_movies_menu)

        if (menuAbierto) {
            menuPelis.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    menuPelis.visibility = View.GONE
                    menuPelis.alpha = 1f
                    menuAbierto = false
                }
                .start()
        } else {
            menuPelis.visibility = View.VISIBLE
            menuPelis.alpha = 1f
            menuPelis.requestFocus()
            menuAbierto = true
        }
    }

    private fun initializeRecyclerView() {
        // Crear el adaptador inicialmente con una lista vacía
        adapter = PeliculasMenuAdapter(mutableListOf()) { movie ->
            startMoviePlayback(movie.streamUrl, movie.title, movie.year, movie.imageUrl)
        }

        val menuPelis = binding.reproductor.findViewById<RecyclerView>(R.id.recycler_movies_menu)

        menuPelis.layoutManager = LinearLayoutManager(
            this@PlayerPeliculas,
            LinearLayoutManager.HORIZONTAL,
            false
        )

        menuPelis.adapter = adapter


        // Cargar las películas desde Firestore
        loadMovies()
    }



    private fun loadMovies() {
        CoroutineScope(Dispatchers.Main).launch {
            // Usamos las que ya fueron cargadas y validadas previamente
            val peliculasOrdenadasValidas = validacioneslista.obtenerPeliculasValidas()
            val peliculasInvalidas = validacioneslista.obtenerPeliculasInvalidas()

            // Log para verificar
            Log.d(
                "MoviesData",
                "Películas válidas ordenadas: ${peliculasOrdenadasValidas.size}, Películas inválidas: ${peliculasInvalidas.size}"
            )

            // Actualizar el adaptador
            adapter.updateMovies(peliculasOrdenadasValidas)
        }
    }
    // Método que llama al repositorio de Firestore para validar la URL
    private suspend fun isUrlValidInFirestore(url: String?): Boolean {
        // Verifica si la URL está vacía o es nula
        return if (url.isNullOrEmpty()) {
            false
        } else {
            // Llama al método en tu Validaciones para validar la URL
            Validaciones().isUrlValid(url) // Ajusta esto según tu implementación
        }
    }


    private fun startMoviePlayback(streamUrl: String, movieTitle: String, movieYear: String, movieImageUrl: String) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Limpia la pila de actividades
        // Envía la URL de transmisión y el título de la película como extras
        intent.putExtra("EXTRA_STREAM_URL", streamUrl)
        intent.putExtra("EXTRA_MOVIE_TITLE", movieTitle)
        intent.putExtra("EXTRA_MOVIE_YEAR", movieYear)
        intent.putExtra("EXTRA_MOVIE_IMAGE_URL", movieImageUrl)
        // Inicia la actividad de reproducción
        startActivity(intent)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        if (streamUrl.isEmpty()) {
            showErrorDialog(movieTitle, movieYear)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val isUrlValid = withContext(Dispatchers.IO) {
                isUrlValidInFirestore(streamUrl)
            }

            if (!isUrlValid) {
                showErrorDialog(movieTitle, movieYear)
                return@launch
            }

            val progresoGuardado = obtenerProgresoGuardado()

            if (progresoGuardado > 0) {

                val minutos = progresoGuardado / 60000
                val segundos = (progresoGuardado % 60000) / 1000
                val tiempoFormateado = String.format("%02d:%02d", minutos, segundos)

                AlertDialog.Builder(this@PlayerPeliculas)
                    .setTitle("¿Continuar viendo?")
                    .setMessage("¿Quieres continuar desde el minuto $tiempoFormateado?")
                    .setPositiveButton("Sí") { _, _ ->
                        prepararReproductor(progresoGuardado)
                    }
                    .setNegativeButton("No") { _, _ ->
                        prepararReproductor(0L)
                    }
                    .setCancelable(false)
                    .show()
            } else {
                prepararReproductor(0L)
            }
        }
    }


    @OptIn(UnstableApi::class)
    private fun prepararReproductor(posicionInicial: Long) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(mapOf("User-Agent" to "Mozilla/5.0"))
            .setConnectTimeoutMs(30_000)
            .setReadTimeoutMs(30_000)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(8 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

        player = ExoPlayer.Builder(this@PlayerPeliculas)
            .setLoadControl(loadControl)
            .setRenderersFactory(
                DefaultRenderersFactory(this@PlayerPeliculas)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            )
            .setMediaSourceFactory(mediaSourceFactory)
            .build().also { exoPlayer ->

                binding.reproductor.player = exoPlayer

                val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()

                exoPlayer.addListener(object : Player.Listener {
                    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                        if (timeline.windowCount > 0) {
                            val window = Timeline.Window()
                            timeline.getWindow(0, window)
                            if (!window.isLive) {
                                // No es stream en vivo
                            }
                        }
                    }
                })

                exoPlayer.addListener(playerListener)

                if (posicionInicial > 0) {
                    exoPlayer.seekTo(posicionInicial)
                }

                exoPlayer.playWhenReady = true

                // 🔁 Handler para actualizar la última posición válida mientras se reproduce
                val handlerPosicion = Handler(Looper.getMainLooper())
                val actualizarPosicionRunnable = object : Runnable {
                    override fun run() {
                        val position = player?.contentPosition ?: 0L
                        if (position > 10_000) {
                            ultimaPosicionValida = position

                        }
                        handlerPosicion.postDelayed(this, 5000)
                    }
                }
                handlerPosicion.postDelayed(actualizarPosicionRunnable, 5000)

                // Limpia el handler cuando se destruya la actividad
                lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        handlerPosicion.removeCallbacks(actualizarPosicionRunnable)
                    }
                })
            }
    }

    private fun obtenerProgresoGuardado(): Long {
        val clave = generarClaveProgreso()
        val prefs = getSharedPreferences("progreso_peliculas", Context.MODE_PRIVATE)
        return prefs.getLong(clave, 0L)
    }

    private fun generarClaveProgreso(): String {
        val titulo = movieTitle.trim().ifBlank { "pelicula_sin_titulo" }
        val año = movieYear.trim().ifBlank { "sin_año" }
        return "$titulo-$año".replace(Regex("[^A-Za-z0-9_-]"), "_")
    }

    private fun borrarProgresoGuardado() {
        val clave = generarClaveProgreso()
        val prefs = getSharedPreferences("progreso_peliculas", Context.MODE_PRIVATE)
        prefs.edit().remove(clave).apply()
    }

    override fun onPause() {
        super.onPause()

        val position = ultimaPosicionValida
        if (position > 10_000) {
            val clave = generarClaveProgreso()
            val prefs = getSharedPreferences("progreso_peliculas", Context.MODE_PRIVATE)
            prefs.edit().putLong(clave, position).apply()
        }

        player?.pause()
        handler.postDelayed(runnableActualizar, 1000)
    }


    private var ultimaPosicionValida: Long = 0L

    // Listener para el reproductor
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val currentPosition = player?.contentPosition ?: 0L

            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    mostrarBuffer()
                    if (currentPosition > 0) {
                        ultimaPosicionValida = currentPosition

                    }
                }

                Player.STATE_READY -> {
                    isPlaybackActive = true
                    playbackStartTime.set(System.currentTimeMillis())
                    reconnectionAttempts = 0
                    isReconnecting = false

                    if (lastKnownPosition > 0) {
                        player?.seekTo(lastKnownPosition)
                        lastKnownPosition = 0
                    }

                    actualizarTiempo()
                    val pos = player?.contentPosition ?: 0L
                    Log.d("PROGRESO", "📍 Posición detectada en estado $playbackState: $pos")

// Guardamos la última posición válida
                    val current = player?.contentPosition ?: 0L
                    if (current > 10_000) {
                        ultimaPosicionValida = current
                        Log.d("PROGRESO", "🎯 Posición válida (READY): $ultimaPosicionValida ms")
                    }
                }

                Player.STATE_ENDED -> {
                    borrarProgresoGuardado()
                    isPlaybackActive = false
                    showErrorDialog(movieTitle, movieYear)

                    if (isLiveStream) {
                        intentarReconexion()
                    } else {
                        handler.postDelayed(runnableActualizar, 1000)
                    }
                }

                Player.STATE_IDLE -> {
                    isPlaybackActive = false

                    if (isLiveStream) {
                        intentarReconexion()
                    } else {
                        handler.postDelayed(runnableActualizar, 1000)
                    }
                }

                else -> {
                    if (!isPlaybackActive && reconnectionAttempts < maxReconnectionAttempts) {
                        intentarReconexion()
                    }
                }
            }
        }


    override fun onIsPlayingChanged(isPlaying: Boolean) {
            val playPauseButton = findViewById<ImageButton>(R.id.play_pause)
            if (isPlaying) {
                handler.postDelayed(runnableOcultar, hideControlsDelay)
                playPauseButton.setImageResource(R.drawable.ic_play)
            } else {
                handler.removeCallbacks(runnableOcultar)
                playPauseButton.setImageResource(R.drawable.ic_stop)
            }
        }


        override fun onPlayerError(error: PlaybackException) {


            // Intenta la reconexión solo si el error es recuperable y la reproducción ha sido activa
            if (isRecoverableError(error) && isPlaybackActive) {
                intentarReconexion() // Llama al método de reconexión
            } else if (!isPlaybackActive && reconnectionAttempts >= maxReconnectionAttempts) {
                showErrorDialog(movieTitle, movieYear) // Muestra un diálogo de error
            }
        }
    }

    // Método para determinar si el error es recuperable
    private fun isRecoverableError(error: PlaybackException): Boolean {
        // Define qué errores son recuperables para tu caso específico
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR // Y otros errores recuperables
    }

    // Método para mostrar el estado del búfer
    private fun mostrarBuffer() {
        val bufferedPercentage = player?.bufferedPercentage ?: 0
        val bufferedData = (bufferedPercentage / 100.0) * (2 * 1024) // 2MB de targetBufferBytes
        Toast.makeText(
            this,
            "Búfer almacenado: ${bufferedData.toInt()} KB", // Muestra el tamaño del búfer
            Toast.LENGTH_SHORT
        ).show()
    }

    // Método para intentar reconectar
    private fun intentarReconexion() {
        if (isReconnecting) {
            return // No intenta reconectar si ya se está reconectando
        }

        // Verifica si se alcanzó el máximo de intentos de reconexión
        if (reconnectionAttempts >= maxReconnectionAttempts) {
            Log.d("Reconexión", "Se alcanzó el máximo de intentos. No se puede reconectar.")
            reconnectionAttempts = 0 // Reinicia los intentos de reconexión
            isReconnecting = false // Indica que no se está reconectando

            // Muestra un diálogo de error si no hay reproducción activa
            if (!isPlaybackActive) {
                showErrorDialog(
                    movieTitle,
                    movieYear
                )
            }
            return
        }

        // Verifica la conexión a internet
        if (!isNetworkConnected()) {
            Log.d("Reconexión", "No hay conexión a Internet. Esperando...")
            return // No intenta reconectar si no hay conexión
        }

        reconnectionAttempts++ // Incrementa los intentos de reconexión
        isReconnecting = true // Indica que se está reconectando

        // Calcula el tiempo de espera para reconectar
        val waitTime = (2.0).pow(reconnectionAttempts).toLong() * 1000 // Espera un tiempo exponencial

        // Inicia una corutina para manejar la reconexión
        CoroutineScope(Dispatchers.Main).launch {
            delay(waitTime) // Espera el tiempo calculado
            // Intenta reiniciar la reproducción solo si streamUrl no es nulo
            streamUrl.let { url ->
                startMoviePlayback(url, movieTitle, movieYear, movieImageUrl) // Llama al método de inicio
                isReconnecting = false // Indica que no se está reconectando
            }
        }
    }

    // Método para verificar si hay conexión a Internet
    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo?.isConnected == true // Devuelve true si hay conexión
    }

    @SuppressLint("SetTextI18n")
    private fun showErrorDialog(movieTitle: String, movieYear: String) {
        val dialogView = layoutInflater.inflate(R.layout.player_alerdialogo, null)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val linkNosotros = dialogView.findViewById<TextView>(R.id.linkNosotros)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogImage)
        imageView.setImageResource(R.drawable.qrcontenido)

        val spannable = SpannableStringBuilder()

        // Texto película
        val movieInfo = "Película: $movieTitle\n"
        spannable.append(movieInfo)

        val peliculaTexto = "Película:"
        val peliculaIndex = spannable.indexOf(peliculaTexto)
        spannable.setSpan(ForegroundColorSpan(Color.RED), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val tituloIndex = peliculaIndex + peliculaTexto.length + 1
        spannable.setSpan(ForegroundColorSpan(Color.GREEN), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.4f), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Precio
        val precioInfo = "Precio CasTV: $$movieYear\n"
        spannable.append(precioInfo)

        val precioTexto = "Precio CasTV:"
        val precioIndex = spannable.indexOf(precioTexto)
        spannable.setSpan(ForegroundColorSpan(Color.RED), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val precioValorIndex = precioIndex + precioTexto.length + 2
        spannable.setSpan(ForegroundColorSpan(Color.GREEN), precioValorIndex, precioValorIndex + movieYear.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.4f), precioValorIndex, precioValorIndex + movieYear.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        spannable.append("\n¡Alquila Tu Película!")
        spannable.append("\nEstará en línea en breve. Estamos disponibles 24/7")
        spannable.append("\nSi no tienes saldo recuerda recargar en COP")

        messageText.text = spannable

        // Enlace
        linkNosotros.text = "Más información aquí"
        linkNosotros.setTextColor(Color.RED)
        linkNosotros.paintFlags = linkNosotros.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        linkNosotros.setOnClickListener {
            val intent = Intent(this, Nosotros::class.java)
            startActivity(intent)
        }

        val alertDialog = AlertDialog.Builder(this)

            .setView(dialogView)
            .setNegativeButton("Volver al contenido") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNeutralButton("Alquilar Película") { _, _ ->
                verificarYProcesarPedido()
            }
            .setPositiveButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
        dialogView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))

        alertDialog.setOnShowListener {
            // Botones
            val btnAlquilar = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val btnVolver = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCerrar = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // Asignar fondo con selector visual de foco
            val focusSelector = R.drawable.focus_selector
            btnAlquilar.setBackgroundResource(focusSelector)
            btnVolver.setBackgroundResource(focusSelector)
            btnCerrar.setBackgroundResource(focusSelector)

            // Fondo para la barra inferior (padres de botones)
            val buttonParent = btnAlquilar.parent as View
            buttonParent.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))

            // Activar foco y navegación
            listOf(btnAlquilar, btnVolver, btnCerrar).forEach {
                it.isFocusable = true
                it.isFocusableInTouchMode = true
            }

            // Foco inicial
            btnAlquilar.requestFocus()
        }



        alertDialog.show()
    }


    private fun verificarYProcesarPedido() {
        val query = firestore.collection("pedidosmovies")
            .whereEqualTo("title", movieTitle) // Solo se filtra por movieTitle

        query.get().addOnSuccessListener { querySnapshot ->
            if (querySnapshot.isEmpty) {
                // La película NO está pedida (por ningún usuario), llamar a enviarPedido
                enviarPedido()
            } else {
                // La película YA está pedida (por algún usuario)
                Log.i("Firestore", "La película '$movieTitle' ya existe en la base de datos.")
                Toast.makeText(this, "La película '$movieTitle' ya fue pedida; puedes alquilar más...", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error al consultar: ${e.message}")
            Toast.makeText(this, "Error al consultar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun enviarPedido() {
        if (isProcessingOrder) {
            return // Salir si ya se está procesando un pedido
        }

        isProcessingOrder = true // Marcar como procesando

        val query = firestore.collection("pedidosmovies")
            .whereEqualTo("title", movieTitle)
            .whereEqualTo("year", movieYear)

        query.get().addOnSuccessListener { querySnapshot ->
            if (querySnapshot.isEmpty) {
                val user = auth.currentUser // Obtener el usuario autenticado
                if (user != null) {
                    val userId = user.uid
                    val userEmail = user.email ?: "Sin correo"
                    val userName = user.displayName ?: "Sin nombre"

                    val costoPedido = movieYear.toIntOrNull() ?: 0

                    verificarPuntos(userId, costoPedido) { tienePuntos ->
                        if (tienePuntos) {
                            val datos: HashMap<String, Any> = hashMapOf(
                                "title" to movieTitle,
                                "year" to movieYear,
                                "email" to userEmail, // Agregar el correo del usuario
                                "nombre" to userName, // Agregar el nombre del usuario
                                "userId" to userId
                            )

                            firestore.collection("pedidosmovies")
                                .add(datos)
                                .addOnSuccessListener {
                                    descontarPuntos(userId, costoPedido.toLong())
                                    enviarCorreoNuevoPedido(movieTitle)
                                    Toast.makeText(
                                        this,
                                        "Pedido realizado con éxito.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("Firestore", "Error al agregar la película: ${e.message}")
                                    Toast.makeText(
                                        this,
                                        "Error al realizar el pedido: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        } else {
                            Toast.makeText(
                                this,
                                "¡Ho! No tienes Saldo de CasTV para poder Alquilar.",
                                Toast.LENGTH_LONG
                            ).show()
                            val intent = Intent(this, Nosotros::class.java)
                            startActivity(intent)
                            finish()
                        }
                    }
                } else {
                    Toast.makeText(this, "No hay usuario autenticado.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(
                    this,
                    "La película '$movieTitle' ya fue pedida; puedes alquilar más...",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error al consultar la película: ${e.message}")
            Toast.makeText(this, "Error al consultar la película: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }.addOnCompleteListener {
            isProcessingOrder = false // Restablecer el flag al finalizar
        }
    }

    private fun verificarPuntos(userId: String, costo: Int, callback: (Boolean) -> Unit) {
        val userRef = firestore.collection("users").document(userId)

        userRef.get().addOnSuccessListener { userDocument ->
            val puntosActuales = userDocument.getLong("puntos") ?: 0
            // Usar el costo del pedido que se pasó
            callback(puntosActuales >= costo) // Llama al callback con true si tiene suficientes puntos
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error al verificar puntos: ${e.message}")
            Toast.makeText(this, "Error al verificar puntos: ${e.message}", Toast.LENGTH_SHORT).show()
            callback(false) // En caso de error, asume que no tiene suficientes puntos
        }
    }
    // Método para enviar un correo
    private fun enviarCorreoNuevoPedido(movieTitle: String) {
        // Crear un objeto JSON para el correo
        val emailData = mapOf(
            "to" to "fulltvurl@gmail.com", // Cambia esto por el correo del destinatario
            "subject" to movieTitle,
            "text" to "PAGADA: $movieTitle"
        )

        // Hacer la solicitud POST al servidor que envía el correo
        val url = "https://fulltvurl.glitch.me/sendEmail" // Cambia esto por la URL de tu servidor

        // Usar Volley para hacer la solicitud
        val requestQueue = Volley.newRequestQueue(this) // Contexto de tu actividad

        val jsonObjectRequest = object : JsonObjectRequest(
            Method.POST, url, JSONObject(emailData),
            Response.Listener { response ->
                Log.d("Email", "Correo enviado exitosamente: $response")
            },
            Response.ErrorListener { error ->
                Log.e("Email", "Error al enviar el correo: ${error.message}")
            }
        ) {}

        requestQueue.add(jsonObjectRequest)
    }

    private fun descontarPuntos(
        userId: String,
        puntosADescontar: Long
    ) {
        val userRef = firestore.collection("users").document(userId)

        userRef.get().addOnSuccessListener { userDocument ->
            val puntosActuales = userDocument.getLong("puntos") ?: 0

            // Comparar puntos
            if (puntosActuales >= puntosADescontar) {
                // Actualizar puntos
                userRef.update("puntos", puntosActuales - puntosADescontar)
                    .addOnSuccessListener {
                        // Solo se ejecuta aquí si se han descontado puntos
                        Toast.makeText(this, "Pedido enviado exitosamente", Toast.LENGTH_SHORT)
                            .show()
                        val intent = Intent(this, Nosotros::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error al descontar puntos: ${e.message}")
                        Toast.makeText(
                            this,
                            "Error al descontar puntos: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                // No tiene suficientes puntos, mostrar mensaje y redirigir
                Toast.makeText(
                    this,
                    "¡Ho! No tienes Saldo de CasTV para poder Alquilar",
                    Toast.LENGTH_LONG
                ).show()
                val intent = Intent(this, Nosotros::class.java)
                startActivity(intent)
                finish()
            }
        }.addOnFailureListener { e ->
            Log.e("Firestore", "Error al obtener el documento del usuario: ${e.message}")
            Toast.makeText(this, "Error al obtener usuario: ${e.message}", Toast.LENGTH_SHORT)
                .show()
        }
    }
//    override fun onPause() {
//        super.onPause()
//        player?.pause()
//        handler.postDelayed(runnableActualizar, 1000)
//
//    }

    override fun onResume() {

        super.onResume()
        player?.playWhenReady = true
        if (player?.isPlaying == true) {
            handler.postDelayed(runnableActualizar, 1000)
            // Reanudar actualizaciones al reproducir
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


    private fun setupControlTimers() {
        runnableOcultar = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()
                val elapsed = now - lastInteractionTime

                val controles = binding.reproductor.findViewById<View>(R.id.controles_reproductor)

                val controlesTienenFoco = tieneFocoEnHijos(controles)

                if (elapsed >= hideControlsDelay && !controlesTienenFoco && !menuAbierto) {
                    // Ocultar controles
                    controles.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction {
                            controles.visibility = View.GONE
                            controles.alpha = 1f
                        }
                        .start()
                }

                // Si el menú sigue abierto y no hay interacción, lo cerramos
                if (elapsed >= hideControlsDelay && menuAbierto) {
                    mostarpelis()
                }

                // Continuar verificando cada segundo
                handler.postDelayed(this, 1000)
            }
        }

        binding.reproductor.setOnTouchListener { _, _ ->
            showControlsAndResetTimer()
            true
        }

        showControlsAndResetTimer()
    }


    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // ⏱️ Mantiene el temporizador de ocultar controles
            lastInteractionTime = System.currentTimeMillis()
            handler.removeCallbacks(runnableOcultar)
            handler.postDelayed(runnableOcultar, hideControlsDelay)

            // 🔙 Detectar teclas "atrás" universales
            val keyCode = event.keyCode
            val scanCode = event.scanCode
            val keyName = KeyEvent.keyCodeToString(keyCode)

            Log.d("KeyBack", "Tecla presionada: $keyCode ($keyName), scanCode: $scanCode")

            val teclasAtras = setOf(
                KeyEvent.KEYCODE_BACK,          // 4
                KeyEvent.KEYCODE_ESCAPE,        // 111
                KeyEvent.KEYCODE_BUTTON_B,      // 97 (Gamepad botón B)
                4, 111, 158, 172                // Otros comunes por scanCode
            )

            if (keyCode in teclasAtras || scanCode in teclasAtras) {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }


    private fun tieneFocoEnHijos(view: View): Boolean {
        if (view.hasFocus()) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (tieneFocoEnHijos(view.getChildAt(i))) return true
            }
        }
        return false
    }


    private fun showControlsAndResetTimer() {
        lastInteractionTime = System.currentTimeMillis()
        binding.reproductor.findViewById<ImageButton>(R.id.lista_pelis).requestFocus()
        val controles = binding.reproductor.findViewById<View>(R.id.controles_reproductor)
        if (controles.visibility != View.VISIBLE) {
            controles.alpha = 0f
            controles.visibility = View.VISIBLE
            controles.animate().alpha(1f).setDuration(300).start()
        }

        handler.removeCallbacks(runnableOcultar)
        handler.postDelayed(runnableOcultar, hideControlsDelay)
    }


    private fun activarListenersEnControles() {
        val controles = binding.reproductor.findViewById<ViewGroup>(R.id.controles_reproductor)
        val menuPelis = binding.reproductor.findViewById<RecyclerView>(R.id.recycler_movies_menu)

        // Escucha interacción en cada botón de los controles
        for (i in 0 until controles.childCount) {
            val child = controles.getChildAt(i)

            child.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showControlsAndResetTimer()
            }

            child.setOnTouchListener { _, _ ->
                showControlsAndResetTimer()
                false
            }

            child.setOnKeyListener { _, _, _ ->
                showControlsAndResetTimer()
                false
            }

            child.setOnHoverListener { _, _ ->
                showControlsAndResetTimer()
                false
            }
        }

        // Escuchar interacción en el RecyclerView del menú de películas
        menuPelis.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showControlsAndResetTimer()
        }

        menuPelis.setOnTouchListener { _, _ ->
            showControlsAndResetTimer()
            false
        }

        menuPelis.setOnKeyListener { _, _, _ ->
            showControlsAndResetTimer()
            false
        }

        menuPelis.setOnHoverListener { _, _ ->
            showControlsAndResetTimer()
            false
        }
    }



    private fun togglePlayPause() {
        val player = binding.reproductor.player
        val playPauseButton = findViewById<ImageButton>(R.id.play_pause)

        if (player != null) {
            if (player.isPlaying) {
                player.pause()
                playPauseButton.setImageResource(R.drawable.ic_stop)
            } else {
                player.play()
                playPauseButton.setImageResource(R.drawable.ic_play)

                // ✅ Reiniciar contador de tiempo manualmente
                handler.removeCallbacks(runnableActualizar)
                handler.post(runnableActualizar) // Esto reactiva actualizarTiempo()
            }
        }
    }


    // Función para alternar entre pantalla completa y vista normal
    private var currentAspectRatioMode = 0

    @OptIn(UnstableApi::class)
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
        if (player != null && player!!.isPlaying) {
            MainScope().launch {
                val tiemporeproducido = binding.reproductor.findViewById<TextView>(R.id.tiemporeproducido)
                val tiempototal = binding.reproductor.findViewById<TextView>(R.id.tiempototal)
                val seekBar = binding.reproductor.findViewById<SeekBar>(R.id.progreso)

                val posicionActual = player?.currentPosition ?: 0
                val duracionTotal = player?.duration ?: 0
                val tiempoRestante = duracionTotal - posicionActual

                // Mostrar tiempo reproducido
                tiemporeproducido.text = tiempoFormateado(posicionActual)

                // Mostrar tiempo restante (hacia atrás)
                if (tiempoRestante > 0) {
                    val h = TimeUnit.MILLISECONDS.toHours(tiempoRestante)
                    val m = TimeUnit.MILLISECONDS.toMinutes(tiempoRestante) % 60
                    val s = TimeUnit.MILLISECONDS.toSeconds(tiempoRestante) % 60
                    tiempototal.text = String.format("⏳ %02d:%02d:%02d", h, m, s)
                } else {
                    tiempototal.text = "⛔ Finalizado"
                }

                // Avance del SeekBar
                if (duracionTotal > 0) {
                    val progress = (posicionActual.toFloat() / duracionTotal * 100).toInt()
                    seekBar.progress = progress
                    handler.postDelayed(runnableActualizar, updateInterval)
                }
            }
        }
    }


    private fun tiempoFormateado(tiempoMs: Long): String {
        return DateUtils.formatElapsedTime(tiempoMs / 1000)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {

        return when (keyCode) {
            // ✅ Teclas que muestran el menú de películas
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
            174 -> {
                mostarpelis()
                true
            }

            // ✅ Todas las teclas "OK" o "Enter"
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                showControlsAndResetTimer()
                true
            }

            // 🔙 Tecla "Atrás"
            KeyEvent.KEYCODE_BACK -> {
                false // deja que el sistema lo maneje
            }

            else -> super.onKeyDown(keyCode, event)
        }

    }


}
