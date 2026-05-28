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
import com.creativem.fulltv.api.PeliculasApiAdapter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow
import org.json.JSONObject
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.CountDownTimer
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.addCallback
import com.bumptech.glide.Glide

import com.creativem.fulltv.databinding.PlayerBinding


import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit
import com.android.volley.Request
import com.creativem.fulltv.peliculasvalidas.PelisCarteleraAdapter
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.principal.Nosotros


import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.isActive


@Suppress("DEPRECATION")
class PlayerPeliculas : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var movieImageUrl: String = ""
    private var movieCastv: Int = 0
    private lateinit var movieTitle: String
    private var isLiveStream = false
    private lateinit var binding: PlayerBinding
    private lateinit var carteleraAdapter: PelisCarteleraAdapter
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
    private var progresoSimulado = 0
    private var gateoTicks = 0
    private var lastInteractionTime = 0L
    private var startPosition: Long = 0L
    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
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
            movieCastv = intent.getIntExtra("EXTRA_MOVIE_CASTV", 0)
            movieImageUrl = it.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""
            startPosition = it.getIntExtra("EXTRA_POSITION", 0).toLong()



            nombrePeliculaTextView.text = movieTitle

            Glide.with(this)
                .load(movieImageUrl)
                .placeholder(R.drawable.icono)
                .error(R.drawable.icono)
                .into(imagenPeliculaImageView)

            if (streamUrl.isEmpty()) {
//                showErrorDialog(movieTitle, movieCastv, userId)


                return@let
            }

        }

        firestore = Firebase.firestore
        // Inicializa el SeekBar desde el binding
        actualizarTiempo()
        player = ExoPlayer.Builder(this).build()
        binding.reproductor.player = player
        initializePlayer()

        lifecycleScope.launch {
            // Esperar a que las validaciones estén listas
            Validacioneslista.esperarCarga()

            // Luego cargar las películas al RecyclerView del menú
            loadMovies()
        }

        // --- 1. LLAMAR AL RELOJ (Buscando dentro del reproductor) ---
        val textHora = binding.reproductor.findViewById<TextView>(R.id.textHora)
        val textfecha = binding.reproductor.findViewById<TextView>(R.id.textfecha)

        if (textHora != null && textfecha != null) {
            val reloj = Reloj(textHora, textfecha)
            reloj.startClock()
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
            // Creamos el Intent para ir a la Activity "Nosotros"
            val intent = Intent(this, Nosotros::class.java)

            // Si necesitas pasarle datos a esa nueva pantalla (como el userId), puedes hacerlo así:
            // intent.putExtra("USER_ID", userId)

            startActivity(intent)
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
            val menuPelis = binding.reproductor.findViewById<RecyclerView>(R.id.peliscartelera)

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
        val menuPelis = binding.reproductor.findViewById<RecyclerView>(R.id.peliscartelera)

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
        // 1. Usamos PelisCarteleraAdapter en lugar de PeliculasApiAdapter
        // Pasamos una lista vacía inicialmente y la lógica de clic
        carteleraAdapter = PelisCarteleraAdapter(mutableListOf()) { movie ->
            // Al tocar una peli en el reproductor, la reproducimos
            startMoviePlayback(movie.streamUrl, movie.title, movie.castv, movie.imageUrl)
        }

        val menuPelis = binding.reproductor.findViewById<RecyclerView>(R.id.peliscartelera)

        // 2. Configuración del LayoutManager (Horizontal para TV)
        menuPelis.layoutManager = LinearLayoutManager(
            this@PlayerPeliculas,
            LinearLayoutManager.HORIZONTAL,
            false

        )

        // 3. Asignamos el nuevo adaptador al RecyclerView del reproductor
        menuPelis.adapter = carteleraAdapter

        // 4. Cargamos los datos (asegúrate de que loadMovies actualice ahora carteleraAdapter)
        loadMovies()
    }

    private fun loadMovies() {
        val menuPelis = binding.reproductor.findViewById<RecyclerView>(R.id.peliscartelera)

        // Función interna para filtrar las películas activas
        fun filtrarActivas(lista: List<Movie>): List<Movie> {
            return lista.filter { movie ->
                val countdownDurationMillis =
                    java.util.concurrent.TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
                val timeElapsed = System.currentTimeMillis() - movie.createdAt
                val remainingTimeMillis = countdownDurationMillis - timeElapsed

                // Filtro: Debe tener contador mayor a 0 y aún no haber finalizado
                movie.countdownMinutes > 0 && remainingTimeMillis > 0
            }.sortedByDescending { it.createdAt }
        }

        lifecycleScope.launch {
            while (isActive) {
                val listaActualizada = Validacioneslista.obtenerPeliculasValidas()
                val nuevasActivas = filtrarActivas(listaActualizada)

                withContext(Dispatchers.Main) {
                    if (nuevasActivas.isNotEmpty()) {
                        // Si hay películas, actualizamos
                        carteleraAdapter.updateMovies(nuevasActivas)

                        // Si el menú estaba oculto por estar vacío, puedes decidir mostrarlo
                        // menuPelis.visibility = View.VISIBLE
                    } else {
                        // 🟢 SI NO HAY PELÍCULAS, OCULTAMOS EL MENÚ CON ANIMACIÓN
                        if (menuPelis.visibility == View.VISIBLE) {
                            menuPelis.animate()
                                .alpha(0f)
                                .setDuration(300)
                                .withEndAction {
                                    menuPelis.visibility = View.GONE
                                    menuPelis.alpha = 1f // Restauramos alpha para la próxima vez
                                }
                                .start()
                            menuAbierto = false
                        }
                    }
                }

                // Si ya se cargó todo de la BD, terminamos el bucle
                if (Validacioneslista.yaCargado()) break

                delay(3000) // Revisión cada 3 segundos
            }
        }
    }


    private fun startMoviePlayback(
        streamUrl: String,
        movieTitle: String,
        movieCastv: Int,
        movieImageUrl: String
    ) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Limpia la pila de actividades
        // Envía la URL de transmisión y el título de la película como extras
        intent.putExtra("EXTRA_STREAM_URL", streamUrl)
        intent.putExtra("EXTRA_MOVIE_TITLE", movieTitle)
        intent.putExtra("EXTRA_MOVIE_CASTV", movieCastv)
        intent.putExtra("EXTRA_MOVIE_IMAGE_URL", movieImageUrl)
        // Inicia la actividad de reproducción
        startActivity(intent)
        finish()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        Log.d("Player", "Intentando preparar URL: $streamUrl")
        if (streamUrl.isEmpty())
            return
        lifecycleScope.launch {
            // Asegurar que la validación ocurra antes de preparar
            withContext(Dispatchers.IO) {
                if (!Validacioneslista.yaCargado()) {
                    Validacioneslista.cargarPeliculas()
                }
            }

            CoroutineScope(Dispatchers.Main).launch {
                // 1. Verificamos localmente contra el objeto Validacioneslista
                val isUrlValid = withContext(Dispatchers.IO) {
                    // Si por alguna razón el objeto no ha cargado nada, le pedimos que lo haga
                    if (!Validacioneslista.yaCargado()) {
                        Validacioneslista.cargarPeliculas()
                    }

                    // Comprobamos si la URL de esta película está entre las que pasaron el ping
                    Validacioneslista.obtenerPeliculasValidas().any { it.streamUrl == streamUrl }
                }

                // 2. Si no es válida, mostramos el diálogo de error (pedido)
                if (!isUrlValid) {
//                showErrorDialog(movieTitle, movieCastv, userId)
                    return@launch
                }

                val progresoGuardado = obtenerProgresoGuardado()

                if (progresoGuardado > 0) {
                    mostrarDialogoContinuar(progresoGuardado)
                } else {
                    prepararReproductor(startPosition)

                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun prepararReproductor(posicionInicial: Long) {
        try {
            // 1. LIMPIEZA DE HARDWARE
            binding.reproductor.player = null
            player?.stop()
            player?.clearMediaItems()
            player?.release()
            player = null

            // 2. CONFIGURACIÓN DE RED PARA TV
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 10; BRAVIA 4K Build/QTG3.200305.006.A1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setDefaultRequestProperties(mapOf(
                    "Connection" to "close",
                    "ngrok-skip-browser-warning" to "true"
                ))
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(30_000)
                .setAllowCrossProtocolRedirects(true)

            // 3. RENDERIZADORES PARA TV
            val renderersFactory = DefaultRenderersFactory(this@PlayerPeliculas)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)

            // 4. FACTORÍA DE MEDIA
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            // 5. LOAD CONTROL ESTILO "YOUTUBE" (Búfer ultra-agresivo de precarga)
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    30_000,   // Mínimo buffer antes de evaluar pausar la descarga (7.5s)
                    300_000, // Máximo buffer: Almacena hasta 2 MINUTOS de video por adelantado (Estilo YouTube)
                    3_000,   // Buffer necesario para el arranque inicial rápido (3s)
                    4_500    // Buffer necesario para reanudar tras una pausa (4.5s)
                )
                .setTargetBufferBytes(128 * 1024 * 1024) // Aumentamos la memoria a 128MB para aguantar los 2 min de búfer en HD/4K
                .setPrioritizeTimeOverSizeThresholds(true) // Prioriza siempre acumular tiempo de reproducción (segundos) sobre bytes
                .build()

            // 6. CREAR EL REPRODUCTOR CON BACK-BUFFER (Corregido para Media3)
            player = ExoPlayer.Builder(this@PlayerPeliculas, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build()

            binding.reproductor.player = player

            // 7. PROCESAMIENTO SEGURO DEL MEDIA ITEM
            val uriLimpia = streamUrl.trim()
            if (uriLimpia.isEmpty()) {
                mostrarCargando(false)
                return
            }
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(uriLimpia))
                .build()

            // 8. ASIGNACIÓN DE LISTENERS
            player?.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("TV_ERROR", "Error detectado en reproducción: ${error.errorCodeName}")
                    mostrarCargando(false)

                    if (reconnectionAttempts < maxReconnectionAttempts) {
                        reconnectionAttempts++
                        val ultimaPosicion = player?.currentPosition ?: posicionInicial

                        when (error.errorCode) {
                            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
                                prepararReproductor(ultimaPosicion)
                            }
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                                handler.postDelayed({
                                    prepararReproductor(ultimaPosicion)
                                }, 3000)
                            }
                            else -> {
                                handler.postDelayed({
                                    prepararReproductor(ultimaPosicion)
                                }, 3000)
                            }
                        }
                    } else {
                        reconnectionAttempts = 0
                    }
                }
            })

            player?.addListener(playerListener)

            // 9. INICIALIZACIÓN
            player?.setMediaItem(mediaItem)

            if (posicionInicial > 0) {
                player?.seekTo(posicionInicial)
            }

            player?.playWhenReady = true
            player?.prepare()

        } catch (e: Exception) {
            Log.e("TV_ERROR", "Error crítico en la inicialización: ${e.message}")
            mostrarCargando(false)
        }
    }

    // Función para activar/desactivar la animación de pulso_premium
    @SuppressLint("ResourceType")
    private fun mostrarCargando(mostrar: Boolean) {
        val loadingIndicator = binding.loadingIndicator

        if (mostrar) {
            if (loadingIndicator.visibility != View.VISIBLE) {
                // Reiniciamos el progreso a 0 cada vez que empieza a cargar
                progresoSimulado = 0

                val txtTitulo = loadingIndicator.findViewById<TextView>(R.id.loadingMovieTitle)
                if (::movieTitle.isInitialized) {
                    txtTitulo?.text = "Cargando: $movieTitle"
                } else {
                    txtTitulo?.text = "Cargando película..."
                }

                loadingIndicator.visibility = View.VISIBLE
                val animacion = AnimationUtils.loadAnimation(this, R.anim.pulso_premium)
                loadingIndicator.startAnimation(animacion)

                // Iniciamos la simulación del progreso
                handler.removeCallbacks(runnableBuffer)
                handler.post(runnableBuffer)
            }
        } else {
            handler.removeCallbacks(runnableBuffer)
            loadingIndicator.clearAnimation()
            loadingIndicator.visibility = View.GONE
        }
    }

    private val runnableBuffer = object : Runnable {
        override fun run() {
            val loadingIndicator = binding.loadingIndicator

            if (player != null && loadingIndicator.visibility == View.VISIBLE) {

                // 1. Calculamos los milisegundos reales cargados en memoria
                val posicionDescargada = player?.bufferedPosition ?: 0L
                val posicionActual = player?.currentPosition ?: 0L
                val milisegundosCargados = maxOf(0L, posicionDescargada - posicionActual)

                // 2. DETECCIÓN DINÁMICA DEL OBJETIVO:
                // Si la posición actual es muy cercana a 0 (está iniciando la película por primera vez),
                // el LoadControl exige 3,000 ms para arrancar.
                // Si ya pasó el inicio (posición > 1 segundo), exige 4,500 ms para reanudar.
                val objetivoDespegueMs = if (posicionActual <= 1000L) {
                    3000.0 // Objetivo inicial de tu LoadControl (3s)
                } else {
                    4500.0 // Objetivo de re-búfer de tu LoadControl (4.5s)
                }

                val progresoReal = ((milisegundosCargados / objetivoDespegueMs) * 100).toInt()

                // 3. Lógica de movimiento continuo (gateo)
                if (progresoReal > progresoSimulado) {
                    // Si el búfer real avanza, el contador sube moderadamente para alcanzarlo
                    progresoSimulado += 2
                    gateoTicks = 0
                } else {
                    // Si el búfer se estanca esperando la red, gatea lentamente
                    if (progresoSimulado < 99) {
                        gateoTicks++
                        if (gateoTicks >= 3) {
                            progresoSimulado += 1
                            gateoTicks = 0
                        }
                    }
                }

                // Límites de seguridad
                if (progresoSimulado > 99) progresoSimulado = 99
                if (progresoSimulado < 0) progresoSimulado = 0

                val txtBuffer = loadingIndicator.findViewById<TextView>(R.id.loadingBufferText)
                if (txtBuffer != null) {
                    txtBuffer.text = "Optimizando conexión... $progresoSimulado%"
                }

                handler.postDelayed(this, 150)
            }
        }
    }
//    @OptIn(UnstableApi::class)
//    private fun prepararReproductor(posicionInicial: Long) {
//        // 1. Limpieza
//        player?.let {
//            it.stop()
//            it.clearMediaItems()
//            it.release()
//        }
//        player = null
//
//        // 2. Factory de red: Añadimos "Connection: close" para evitar el bloqueo del CDN
//        val dataSourceFactory = DefaultHttpDataSource.Factory()
//            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
//            .setDefaultRequestProperties(mapOf("Connection" to "close")) // Obliga a refrescar el socket
//            .setConnectTimeoutMs(15_000)
//            .setReadTimeoutMs(15_000)
//            .setAllowCrossProtocolRedirects(true)
//
//        // 3. Load Control para arranque rápido
//        val loadControl = DefaultLoadControl.Builder()
//            .setBufferDurationsMs(1_000, 30_000, 500, 1_000)
//            .setPrioritizeTimeOverSizeThresholds(true)
//            .build()
//
//        // 4. Instancia del reproductor
//        player = ExoPlayer.Builder(this@PlayerPeliculas)
//            .setLoadControl(loadControl)
//            .build()
//
//        binding.reproductor.player = player
//
//        // 5. Configuración específica por tipo de archivo
//        val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
//
//        if (streamUrl.contains(".m3u8")) {
//            // Para HLS: Usamos la factoría explícita y desactivamos el Chunkless
//            // si falla (a veces es más estable sin ello)
//            val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
//                .setAllowChunklessPreparation(false) // <--- Cambiado a FALSE para mayor compatibilidad
//                .createMediaSource(mediaItem)
//            player?.setMediaSource(hlsSource)
//        } else {
//            // Para MP4: Usamos ProgressiveMediaSource
//            val progressiveSource = ProgressiveMediaSource.Factory(dataSourceFactory)
//                .createMediaSource(mediaItem)
//            player?.setMediaSource(progressiveSource)
//        }
//
//        // 6. Preparar
//        player?.prepare()
//        player?.addListener(playerListener)
//
//        if (posicionInicial > 0) player?.seekTo(posicionInicial)
//        player?.playWhenReady = true
//    }


    private fun obtenerProgresoGuardado(): Long {
        val clave = generarClaveProgreso()
        val prefs = getSharedPreferences("progreso_peliculas", Context.MODE_PRIVATE)

        return try {
            prefs.getLong(clave, 0L)
        } catch (e: ClassCastException) {
            prefs.getInt(clave, 0).toLong() // 🛠️ Conversión segura
        }
    }

    private fun mostrarDialogoContinuar(progresoGuardado: Long) {
        // Colores de identidad CineParche
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val horas = progresoGuardado / 3600000
        val minutos = (progresoGuardado % 3600000) / 60000
        val segundos = (progresoGuardado % 60000) / 1000
        val tiempoFormateado = String.format("%02d:%02d:%02d", horas, minutos, segundos)

        // Título elegante
        val customTitle = TextView(this).apply {
            text = "¿Deseas continuar?"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(colorDorado)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(40, 30, 40, 10)
        }

        // Mensaje centralizado
        val customMessage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 30)
            gravity = Gravity.CENTER_HORIZONTAL

            val textoInfo = TextView(this@PlayerPeliculas).apply {
                text = "Te quedaste en:"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            }

            val textoTiempo = TextView(this@PlayerPeliculas).apply {
                text = tiempoFormateado
                textSize = 32f
                setTextColor(Color.WHITE)
                typeface = Typeface.MONOSPACE
                setPadding(0, 10, 0, 10)
                gravity = Gravity.CENTER
            }

            addView(textoInfo)
            addView(textoTiempo)
        }

        val dialog = AlertDialog.Builder(this)
            .setCustomTitle(customTitle)
            .setView(customMessage)
            .setNegativeButton("Reanudar (10s)", null)
            .setPositiveButton("Reiniciar", null)
            .setCancelable(false)
            .create()

        var contadorTimer: CountDownTimer? = null

        dialog.setOnShowListener {
            // Fondo inmersivo azul noche
            dialog.window?.setBackgroundDrawable(ColorDrawable(colorFondo))

            val btnReiniciar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnReanudar = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            val focusSelector = R.drawable.focus_selector

            listOf(btnReiniciar, btnReanudar).forEach { button ->
                button.setTextColor(Color.RED)
                button.textSize = 18f
                button.setBackgroundResource(focusSelector)
                button.isFocusable = true
                button.isFocusableInTouchMode = true
                button.setPadding(30, 15, 30, 15)
            }

            // Foco inicial en Reanudar para mayor comodidad
            btnReanudar?.requestFocus()

            btnReiniciar.setOnClickListener {
                contadorTimer?.cancel()
                prepararReproductor(0L)
                dialog.dismiss()
            }

            btnReanudar.setOnClickListener {
                contadorTimer?.cancel()
                prepararReproductor(progresoGuardado)
                dialog.dismiss()
            }

            // Lógica del contador integrada en el botón
            contadorTimer = object : CountDownTimer(10000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val seg = millisUntilFinished / 1000
                    btnReanudar.text = "Reanudar (${seg}s)"
                    // Efecto visual: el botón con el contador resalta en dorado
                    btnReanudar.setTextColor(colorDorado)
                }

                override fun onFinish() {
                    if (dialog.isShowing) {
                        prepararReproductor(progresoGuardado)
                        dialog.dismiss()
                    }
                }
            }.start()
        }

        dialog.show()
    }

    private fun generarClaveProgreso(): String {
        val titulo = movieTitle.trim().ifBlank { "pelicula_sin_titulo" }
        val año = if (movieCastv <= 0) "sin_año" else movieCastv.toString()
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
                    mostrarCargando(true)
//                    mostrarBuffer()
                    if (currentPosition > 0) {
                        ultimaPosicionValida = currentPosition

                    }
                }

                Player.STATE_READY -> {
                    mostrarCargando(false)
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
                    mostrarCargando(false)
                    isPlaybackActive = false
//                    showErrorDialog(movieTitle, movieCastv, userId)

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
                // Cambias a icono de PAUSA si está reproduciendo
                playPauseButton.setImageResource(R.drawable.play)
            } else {
                handler.removeCallbacks(runnableOcultar)
                // Cambias a icono de PLAY si está detenido
                playPauseButton.setImageResource(R.drawable.stop)
            }
        }

        override fun onPlayerError(error: PlaybackException) {


            // Intenta la reconexión solo si el error es recuperable y la reproducción ha sido activa
            if (isRecoverableError(error) && isPlaybackActive) {
                intentarReconexion() // Llama al método de reconexión
            } else if (!isPlaybackActive && reconnectionAttempts >= maxReconnectionAttempts) {
//                showErrorDialog(movieTitle, movieCastv, userId) // Muestra un diálogo de error
            }
        }
    }

    // Método para determinar si el error es recuperable
    private fun isRecoverableError(error: PlaybackException): Boolean {
        // Define qué errores son recuperables para tu caso específico
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR // Y otros errores recuperables
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
//            if (!isPlaybackActive) {
//                showErrorDialog(
//                    movieTitle,
//                    movieCastv,
//                    userId
//                )
//            }
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
        val waitTime =
            (2.0).pow(reconnectionAttempts).toLong() * 1000 // Espera un tiempo exponencial

        // Inicia una corutina para manejar la reconexión
        CoroutineScope(Dispatchers.Main).launch {
            delay(waitTime) // Espera el tiempo calculado
            // Intenta reiniciar la reproducción solo si streamUrl no es nulo
            streamUrl.let { url ->
                startMoviePlayback(
                    url,
                    movieTitle,
                    movieCastv,
                    movieImageUrl
                ) // Llama al método de inicio
                isReconnecting = false // Indica que no se está reconectando
            }
        }
    }

    // Método para verificar si hay conexión a Internet
    private fun isNetworkConnected(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo?.isConnected == true // Devuelve true si hay conexión
    }

    //
//    @SuppressLint("SetTextI18n")
//    private fun showErrorDialog(movieTitle: String, movieCastv: Int, correoUsuario: String) {
//        val dialogView = layoutInflater.inflate(R.layout.player_alerdialogo, null)
//        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
//        val linkNosotros = dialogView.findViewById<TextView>(R.id.linkNosotros)
//        val imageView = dialogView.findViewById<ImageView>(R.id.dialogImage)
//        imageView.setImageResource(R.drawable.canal)
//
//        val spannable = SpannableStringBuilder()
//
//        // --- Título película
//        val movieInfo = "Película: $movieTitle\n"
//        spannable.append(movieInfo)
//        val peliculaTexto = "Película:"
//        val peliculaIndex = spannable.indexOf(peliculaTexto)
//        spannable.setSpan(ForegroundColorSpan(Color.RED), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//        spannable.setSpan(RelativeSizeSpan(1.3f), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//        val tituloIndex = peliculaIndex + peliculaTexto.length + 1
//        spannable.setSpan(ForegroundColorSpan(Color.GREEN), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//        spannable.setSpan(RelativeSizeSpan(1.4f), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//
//        // --- Precio CasTV
//        val precioInfo = "Precio CasTV: $$movieCastv\n"
//        spannable.append(precioInfo)
//        val precioTexto = "Precio CasTV:"
//        val precioIndex = spannable.indexOf(precioTexto)
//        spannable.setSpan(ForegroundColorSpan(Color.RED), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//        spannable.setSpan(RelativeSizeSpan(1.3f), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//        val precioValorIndex = precioIndex + precioTexto.length + 2
//        spannable.setSpan(ForegroundColorSpan(Color.GREEN), precioValorIndex, precioValorIndex + movieCastv.toString().length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//        spannable.setSpan(RelativeSizeSpan(1.4f), precioValorIndex, precioValorIndex + movieCastv.toString().length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
//
//        // Línea temporal mientras se obtiene usuario y saldo
//        spannable.append("\nUsuario: Consultando...\n")
//        spannable.append("Saldo actual: Consultando...\n")
//
//        // Texto final
//        spannable.append("\n¡Gracias por tu pedido!")
//        spannable.append("\nLa película estará disponible pronto. Estamos disponibles 24/7.")
//        spannable.append("\nSi la película se estrenó hace menos de 1 mes, no será puesta en línea.")
//        spannable.append("\nEn ese caso, el valor será reembolsado como crédito (CasTV).")
//        spannable.append("\nRecuerda tener saldo en CasTV para futuros Alquileres.")
//
//        messageText.text = spannable
//
//        val correoUsuario = FirebaseAuth.getInstance().currentUser?.email ?: ""
//
//        if (correoUsuario.isNotEmpty()) {
//            CastvHelper.obtenerDatosUsuario(
//                correoUsuario,
//                onSuccess = { nombre, correo, castv, _ ->
//                    val usuarioIndex = spannable.indexOf("Usuario: Consultando...")
//                    if (usuarioIndex != -1) {
//                        spannable.replace(
//                            usuarioIndex,
//                            usuarioIndex + "Usuario: Consultando...".length,
//                            "Usuario: $nombre"
//                        )
//                    }
//
//                    val saldoIndex = spannable.indexOf("Saldo actual: Consultando...")
//                    if (saldoIndex != -1) {
//                        spannable.replace(
//                            saldoIndex,
//                            saldoIndex + "Saldo actual: Consultando...".length,
//                            "Saldo actual: $castv CasTV"
//                        )
//                    }
//
//                    messageText.text = spannable
//                },
//                onFailure = { e ->
//                    Log.e("CastvHelper", "❌ Error obteniendo datos del usuario: ${e.message}")
//                }
//            )
//        } else {
//            Log.e("CastvHelper", "⚠️ Correo del usuario es nulo o vacío")
//        }
//
//
//        // Botones
//        linkNosotros.text = "Más información aquí"
//        linkNosotros.setTextColor(Color.RED)
//        linkNosotros.paintFlags = linkNosotros.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
//        linkNosotros.setOnClickListener {
//            val intent = Intent(this, Nosotros::class.java)
//            startActivity(intent)
//        }
//
//// Colores de identidad CineParche
//        val colorDorado = Color.parseColor("#C5A059")
//        val colorFondo = Color.parseColor("#0A122A")
//
//        val alertDialog = AlertDialog.Builder(this)
//            .setView(dialogView)
//            // 1. Evita que se cierre con el botón atrás o tocando fuera
//            .setCancelable(false)
//            .setNegativeButton("Volver al contenido") { dialog, _ ->
//                dialog.dismiss()
//                finish()
//            }
//            .setNeutralButton("Alquilar Película", null)
//            // Se eliminó el setPositiveButton ("Cerrar")
//            .create()
//
//// 2. Refuerzo para que no se cierre al tocar fuera (opcional pero recomendado)
//        alertDialog.setCanceledOnTouchOutside(false)
//
//// Aplicamos el fondo azul oscuro al View personalizado
//        dialogView.setBackgroundColor(colorFondo)
//
//        alertDialog.setOnShowListener {
//            val btnAlquilar = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
//            val btnVolver = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
//
//            // --- ESTILO DE TEXTO ---
//            btnAlquilar.setTextColor(colorDorado)
//            btnAlquilar.setTypeface(Typeface.DEFAULT_BOLD)
//            btnVolver.setTextColor(colorDorado)
//
//            // --- CONFIGURACIÓN DE ENFOQUE Y SELECTOR ---
//            val focusSelector = R.drawable.focus_selector
//            // Solo aplicamos a los dos botones existentes
//            listOf(btnAlquilar, btnVolver).forEach { button ->
//                button.setBackgroundResource(focusSelector)
//                button.isFocusable = true
//                button.isFocusableInTouchMode = true
//                button.setPadding(24, 12, 24, 12)
//            }
//
//            // Asegurar que el contenedor de los botones no tenga bordes de otro color
//            (btnAlquilar.parent as? View)?.setBackgroundColor(colorFondo)
//
//            // --- LÓGICA DE ALQUILER ---
//            btnAlquilar.setOnClickListener {
//                Log.d("ALQUILER_LOG", "1. Botón Alquilar presionado")
//                verificarYProcesarPedido(alertDialog)
//            }
//
//            // El foco inicia en Alquilar para facilitar la compra
//            btnAlquilar.requestFocus()
//        }
//
//        alertDialog.show()
//
//// Ventana totalmente inmersiva
//        alertDialog.window?.setBackgroundDrawable(ColorDrawable(colorFondo))
//    }
//
//    // Agregamos (dialog: AlertDialog) aquí
//    private fun verificarYProcesarPedido(dialog: AlertDialog) {
//        Log.d("ALQUILER_LOG", "2. Entrando a verificarYProcesarPedido para: $movieTitle")
//
//        val query = databaseRef.child("pedidosmovies")
//            .orderByChild("title")
//            .equalTo(movieTitle)
//
//        query.get().addOnSuccessListener { snapshot ->
//            if (!snapshot.exists()) {
//                Log.d("ALQUILER_LOG", "3. La película no ha sido pedida aún. Procediendo...")
//                enviarPedido(dialog)
//            } else {
//                Log.d("ALQUILER_LOG", "3. La película YA existe en pedidos.")
//                Toast.makeText(this, "Esta película ya fue pedida.", Toast.LENGTH_LONG).show()
//            }
//        }.addOnFailureListener { e ->
//            Log.e("ALQUILER_LOG", "ERROR en consulta de pedidos: ${e.message}")
//        }
//    }
    // Agregamos (dialog: AlertDialog) aquí
//    private fun enviarPedido(dialog: AlertDialog) {
//        if (isProcessingOrder) return
//        isProcessingOrder = true
//
//        val user = auth.currentUser
//        if (user != null && user.email != null) {
//            val correoKey = user.email!!.replace(".", "_").replace("@", "_")
//
//            databaseRef.child("usuarios").child(correoKey).get().addOnSuccessListener { snapshot ->
//                if (snapshot.exists()) {
//                    val userName = snapshot.child("nombre").value?.toString() ?: "Sin nombre"
//                    val userEmail = snapshot.child("correo").value?.toString() ?: user.email!!
//                    val costoPedido = (movieCastv as? Number)?.toInt() ?: 0
//
//                    // ✅ Ahora enviamos los dos parámetros correctamente
//                    verificarPuntos(correoKey, costoPedido) { tienePuntos ->
//                        if (tienePuntos) {
//                            val datos = hashMapOf(
//                                "title" to movieTitle,
//                                "castv" to costoPedido,
//                                "email" to userEmail,
//                                "nombre" to userName,
//                                "userId" to snapshot.child("userId").value?.toString(),
//                                "timestamp" to ServerValue.TIMESTAMP
//                            )
//
//                            databaseRef.child("pedidosmovies").push().setValue(datos)
//                                .addOnSuccessListener {
//                                    dialog.dismiss()
//                                    descontarPuntos(correoKey, costoPedido)
//                                    enviarCorreoNuevoPedido(movieTitle)
//                                    isProcessingOrder = false
//                                }
//                                .addOnFailureListener { e ->
//                                    isProcessingOrder = false
//                                    Toast.makeText(this, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
//                                }
//                        } else {
//                            isProcessingOrder = false
//                            Toast.makeText(this, "Saldo CasTV insuficiente.", Toast.LENGTH_SHORT).show()
//                        }
//                    }
//                } else {
//                    isProcessingOrder = false
//                }
//            }.addOnFailureListener { isProcessingOrder = false }
//        }
//    }
//    private fun verificarPuntos(correoKey: String, costo: Int, callback: (Boolean) -> Unit) {
//        Log.d("ALQUILER_LOG", "Buscando en la ruta correcta: usuarios/$correoKey")
//
//        val userRef = databaseRef.child("usuarios").child(correoKey)
//
//        userRef.child("castv").get().addOnSuccessListener { snapshot ->
//            if (snapshot.exists()) {
//                val puntosActuales = (snapshot.value as? Number)?.toInt() ?: 0
//                Log.d("ALQUILER_LOG", "✅ Puntos encontrados para $correoKey: $puntosActuales")
//                callback(puntosActuales >= costo)
//            } else {
//                Log.e("ALQUILER_LOG", "❌ No se encontró la carpeta: usuarios/$correoKey")
//                callback(false)
//            }
//        }.addOnFailureListener { e ->
//            Log.e("ALQUILER_LOG", "Error de Firebase: ${e.message}")
//            callback(false)
//        }
//    }
//    private fun enviarCorreoNuevoPedido(movieTitle: String) {
//        val url = "https://server-csks8w.fly.dev/correo"
//
//        // No codificamos el título, lo enviamos tal cual
//        val jsonBody = JSONObject()
//        jsonBody.put("titulo", movieTitle)
//
//        val requestQueue = Volley.newRequestQueue(this)
//        val jsonRequest = object : JsonObjectRequest(
//            Request.Method.POST, url, jsonBody,
//            Response.Listener { response ->
//                Log.d("Email", "✅ Correo enviado exitosamente: $response")
//            },
//            Response.ErrorListener { error ->
//                Log.e("Email", "❌ Error al enviar el correo: ${error.message}")
//            }
//        ) {
//            override fun getBodyContentType(): String = "application/json; charset=utf-8"
//        }
//
//        requestQueue.add(jsonRequest)
//    }
//
//    private fun descontarPuntos(correoKey: String, puntosADescontar: Int) {
//        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)
//
//        userRef.child("castv").get().addOnSuccessListener { snapshot ->
//            val castvActual = (snapshot.value as? Number)?.toInt() ?: 0
//
//            if (castvActual >= puntosADescontar) {
//                val nuevoCastv = castvActual - puntosADescontar
//
//                userRef.child("castv").setValue(nuevoCastv)
//                    .addOnSuccessListener {
//                        Log.d("ALQUILER_LOG", "✅ Descuento aplicado. Nuevo saldo: $nuevoCastv")
//                        Toast.makeText(this, "Pedido enviado exitosamente", Toast.LENGTH_SHORT).show()
//
//                        val intent = Intent(this, Nosotros::class.java)
//                        startActivity(intent)
//                        finish()
//                    }
//                    .addOnFailureListener { e ->
//                        Log.e("ALQUILER_LOG", "❌ Error al actualizar saldo: ${e.message}")
//                    }
//            }
//        }.addOnFailureListener { e ->
//            Log.e("ALQUILER_LOG", "Error de conexión: ${e.message}")
//        }
//    }
    override fun onResume() {
        super.onResume()
        // Verificar si el player está en reproducción para actualizar el UI
        if (player?.isPlaying == true) {
            handler.postDelayed(runnableActualizar, 1000)
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
        binding.reproductor.findViewById<ImageButton>(R.id.lista_pelis)
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
        val menuPelis = binding.reproductor.findViewById<RecyclerView>(R.id.peliscartelera)

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
//                playPauseButton.setImageResource(R.drawable.stop)
            } else {
                player.play()
//                playPauseButton.setImageResource(R.drawable.play)

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
        val activePlayer = player
        if (activePlayer != null) {
            val estado = activePlayer.playbackState

            // El ciclo continuará ejecutándose siempre y cuando el reproductor esté activo (reproduciendo o pausado)
            if (estado != Player.STATE_IDLE && estado != Player.STATE_ENDED) {
                MainScope().launch {
                    val tiemporeproducido = binding.reproductor.findViewById<TextView>(R.id.tiemporeproducido)
                    val tiempototal = binding.reproductor.findViewById<TextView>(R.id.tiempototal)
                    val seekBar = binding.reproductor.findViewById<SeekBar>(R.id.progreso)

                    val posicionActual = activePlayer.currentPosition
                    val duracionTotal = activePlayer.duration
                    val tiempoRestante = duracionTotal - posicionActual

                    // Mostrar tiempo reproducido
                    tiemporeproducido.text = tiempoFormateado(posicionActual)

                    // Mostrar tiempo restante (hacia atrás)
                    if (tiempoRestante > 0) {
                        val h = TimeUnit.MILLISECONDS.toHours(tiempoRestante)
                        val m = TimeUnit.MILLISECONDS.toMinutes(tiempoRestante) % 60
                        val s = TimeUnit.MILLISECONDS.toSeconds(tiempoRestante) % 60
                        tiempototal.text = String.format("%02d:%02d:%02d", h, m, s)
                    } else {
                        tiempototal.text = "Finalizado"
                    }

                    // Avance del SeekBar y del Búfer
                    if (duracionTotal > 0) {
                        val progress = (posicionActual.toFloat() / duracionTotal * 100).toInt()
                        seekBar.progress = progress

                        // ========================================================
                        // ACTUALIZACIÓN EN TIEMPO REAL:
                        // Crecerá en pantalla de forma fluida incluso estando en PAUSA
                        // ========================================================
                        val porcentajeBuffer = activePlayer.bufferedPercentage
                        seekBar.secondaryProgress = porcentajeBuffer
                        // ========================================================
                    }

                    // Volvemos a programar la actualización en 1 segundo (esté o no en pausa)
                    handler.removeCallbacks(runnableActualizar)
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
