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
import android.view.ViewGroup
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
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit
import com.android.volley.Request
import com.creativem.fulltv.peliculasvalidas.PelisCarteleraAdapter
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.CastvHelper
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
                showErrorDialog(movieTitle, movieCastv, userId)


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
            showErrorDialog(movieTitle, movieCastv, userId)
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
        // 1. Obtenemos la lista inicial
        val peliculasValidas = Validacioneslista.obtenerPeliculasValidas()

        if (peliculasValidas.isNotEmpty()) {
            // Ordenamos y enviamos al adaptador de cartelera
            val listaOrdenada = peliculasValidas.sortedByDescending { it.createdAt }

            // Si el adaptador espera TmdbMovie y recibes Movie, asegúrate de que sea la misma clase
            carteleraAdapter.updateMovies(listaOrdenada)
        }

        // 2. Vigilamos actualizaciones en segundo plano
        lifecycleScope.launch {
            var ultimaCantidad = peliculasValidas.size

            while (isActive) {
                val listaActualizada = Validacioneslista.obtenerPeliculasValidas()

                // Si hay pelis nuevas, actualizamos el adaptador
                if (listaActualizada.size > ultimaCantidad) {
                    ultimaCantidad = listaActualizada.size
                    carteleraAdapter.updateMovies(listaActualizada.sortedByDescending { it.createdAt })
                }

                // Si el Singleton dice que ya no hay más por cargar, salimos del bucle
                if (Validacioneslista.yaCargado()) break

                delay(2000) // Espera 2 segundos antes de la siguiente revisión
            }
        }
    }


    private fun startMoviePlayback(streamUrl: String, movieTitle: String, movieCastv: Int, movieImageUrl: String) {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Limpia la pila de actividades
        // Envía la URL de transmisión y el título de la película como extras
        intent.putExtra("EXTRA_STREAM_URL", streamUrl)
        intent.putExtra("EXTRA_MOVIE_TITLE", movieTitle)
        intent.putExtra("EXTRA_MOVIE_CASTV", movieCastv)
        intent.putExtra("EXTRA_MOVIE_IMAGE_URL", movieImageUrl)
        // Inicia la actividad de reproducción
        startActivity(intent)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        if (streamUrl.isEmpty()) {
            showErrorDialog(movieTitle, movieCastv, userId)
            return
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
                showErrorDialog(movieTitle, movieCastv, userId)
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

    private fun mostrarDialogoContinuar(progresoGuardado: Long) {
        val horas = progresoGuardado / 3600000
        val minutos = (progresoGuardado % 3600000) / 60000
        val segundos = (progresoGuardado % 60000) / 1000
        val tiempoFormateado = String.format("%02d:%02d:%02d", horas, minutos, segundos)

        val contadorTextView = TextView(this@PlayerPeliculas).apply {
            textSize = 22f
            setTextColor(Color.GREEN)
            setPadding(30, 10, 20, 10)
        }

        val customTitle = TextView(this@PlayerPeliculas).apply {
            text = "¿Deseas continuar?"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 25f)
            setTextColor(Color.GREEN)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(30, 20, 20, 20)
        }

        val customMessage = LinearLayout(this@PlayerPeliculas).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 20, 20, 20)

            val texto = TextView(this@PlayerPeliculas).apply {
                text = "Te Quedaste en $tiempoFormateado"
                setTextSize(30f)
                setTextColor(Color.RED)
            }

            addView(texto)
            addView(contadorTextView)
        }

        val dialog = AlertDialog.Builder(this@PlayerPeliculas)
            .setCustomTitle(customTitle)
            .setView(customMessage)
            .setNegativeButton("Reanudar", null)
            .setPositiveButton("Reiniciar", null) // se configura luego para evitar cierre automático
            .setCancelable(false)
            .create()

        var contador: CountDownTimer? = null

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(
                ColorDrawable(ContextCompat.getColor(this@PlayerPeliculas, R.color.colorPrimary))
            )

            val focusSelector = R.drawable.focus_selector
            val btnReiniciar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnReanudar = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            listOf(btnReiniciar, btnReanudar).forEach {
                it.setTextColor(Color.LTGRAY)
                it.textSize = 16f
                it.setBackgroundResource(focusSelector)
                it.isFocusable = true
                it.isFocusableInTouchMode = true
            }

            // Botón por defecto con foco
            btnReiniciar?.requestFocus()

            // Acciones de los botones
            btnReiniciar.setOnClickListener {
                contador?.cancel()
                prepararReproductor(0L)
                dialog.dismiss()
            }

            btnReanudar.setOnClickListener {
                contador?.cancel()
                prepararReproductor(progresoGuardado)
                dialog.dismiss()
            }

            // Iniciar contador regresivo
            contador = object : CountDownTimer(10000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val segundosRestantes = millisUntilFinished / 1000
                    contadorTextView.text = "Reanudar en $segundosRestantes"
                }

                override fun onFinish() {
                    prepararReproductor(progresoGuardado)
                    dialog.dismiss()
                }
            }.start()
        }

        dialog.show()
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

        return try {
            prefs.getLong(clave, 0L)
        } catch (e: ClassCastException) {
            prefs.getInt(clave, 0).toLong() // 🛠️ Conversión segura
        }
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
                    showErrorDialog(movieTitle, movieCastv, userId)

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
                playPauseButton.setImageResource(R.drawable.ic_stop)
            } else {
                handler.removeCallbacks(runnableOcultar)
                // Cambias a icono de PLAY si está detenido
                playPauseButton.setImageResource(R.drawable.ic_play)
            }
        }

        override fun onPlayerError(error: PlaybackException) {


            // Intenta la reconexión solo si el error es recuperable y la reproducción ha sido activa
            if (isRecoverableError(error) && isPlaybackActive) {
                intentarReconexion() // Llama al método de reconexión
            } else if (!isPlaybackActive && reconnectionAttempts >= maxReconnectionAttempts) {
                showErrorDialog(movieTitle, movieCastv, userId) // Muestra un diálogo de error
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
                    movieCastv,
                    userId
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
                startMoviePlayback(url, movieTitle, movieCastv, movieImageUrl) // Llama al método de inicio
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
    private fun showErrorDialog(movieTitle: String, movieCastv: Int, correoUsuario: String) {
        val dialogView = layoutInflater.inflate(R.layout.player_alerdialogo, null)
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val linkNosotros = dialogView.findViewById<TextView>(R.id.linkNosotros)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogImage)
        imageView.setImageResource(R.drawable.qrcontenido)

        val spannable = SpannableStringBuilder()

        // --- Título película
        val movieInfo = "Película: $movieTitle\n"
        spannable.append(movieInfo)
        val peliculaTexto = "Película:"
        val peliculaIndex = spannable.indexOf(peliculaTexto)
        spannable.setSpan(ForegroundColorSpan(Color.RED), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val tituloIndex = peliculaIndex + peliculaTexto.length + 1
        spannable.setSpan(ForegroundColorSpan(Color.GREEN), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.4f), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // --- Precio CasTV
        val precioInfo = "Precio CasTV: $$movieCastv\n"
        spannable.append(precioInfo)
        val precioTexto = "Precio CasTV:"
        val precioIndex = spannable.indexOf(precioTexto)
        spannable.setSpan(ForegroundColorSpan(Color.RED), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val precioValorIndex = precioIndex + precioTexto.length + 2
        spannable.setSpan(ForegroundColorSpan(Color.GREEN), precioValorIndex, precioValorIndex + movieCastv.toString().length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.4f), precioValorIndex, precioValorIndex + movieCastv.toString().length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Línea temporal mientras se obtiene usuario y saldo
        spannable.append("\nUsuario: Consultando...\n")
        spannable.append("Saldo actual: Consultando...\n")

        // Texto final
        spannable.append("\n¡Gracias por tu pedido!")
        spannable.append("\nLa película estará disponible pronto. Estamos disponibles 24/7.")
        spannable.append("\nSi la película se estrenó hace menos de 1 mes, no será puesta en línea.")
        spannable.append("\nEn ese caso, el valor será reembolsado como crédito (CasTV).")
        spannable.append("\nRecuerda tener saldo en CasTV para futuros Alquileres.")

        messageText.text = spannable

        val correoUsuario = FirebaseAuth.getInstance().currentUser?.email ?: ""

        if (correoUsuario.isNotEmpty()) {
            CastvHelper.obtenerDatosUsuario(
                correoUsuario,
                onSuccess = { nombre, correo, castv, _ ->
                    val usuarioIndex = spannable.indexOf("Usuario: Consultando...")
                    if (usuarioIndex != -1) {
                        spannable.replace(
                            usuarioIndex,
                            usuarioIndex + "Usuario: Consultando...".length,
                            "Usuario: $nombre"
                        )
                    }

                    val saldoIndex = spannable.indexOf("Saldo actual: Consultando...")
                    if (saldoIndex != -1) {
                        spannable.replace(
                            saldoIndex,
                            saldoIndex + "Saldo actual: Consultando...".length,
                            "Saldo actual: $castv CasTV"
                        )
                    }

                    messageText.text = spannable
                },
                onFailure = { e ->
                    Log.e("CastvHelper", "❌ Error obteniendo datos del usuario: ${e.message}")
                }
            )
        } else {
            Log.e("CastvHelper", "⚠️ Correo del usuario es nulo o vacío")
        }


        // Botones
        linkNosotros.text = "Más información aquí"
        linkNosotros.setTextColor(Color.RED)
        linkNosotros.paintFlags = linkNosotros.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        linkNosotros.setOnClickListener {
            val intent = Intent(this, Nosotros::class.java)
            startActivity(intent)
        }

        // ... (todo tu código anterior de Spannable y CastvHelper)

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Volver al contenido") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNeutralButton("Alquilar Película", null) // Se deja en null aquí
            .setPositiveButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialogView.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))

        alertDialog.setOnShowListener {
            val btnAlquilar = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            val btnVolver = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val btnCerrar = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            // --- AQUÍ ES DONDE SE AGREGA LA LLAMADA ---
            btnAlquilar.setOnClickListener {
                Log.d("ALQUILER_LOG", "1. Botón Alquilar presionado")
                verificarYProcesarPedido(alertDialog)
            }
            // ------------------------------------------

            val focusSelector = R.drawable.focus_selector
            btnAlquilar.setBackgroundResource(focusSelector)
            btnVolver.setBackgroundResource(focusSelector)
            btnCerrar.setBackgroundResource(focusSelector)

            val buttonParent = btnAlquilar.parent as View
            buttonParent.setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))

            listOf(btnAlquilar, btnVolver, btnCerrar).forEach {
                it.isFocusable = true
                it.isFocusableInTouchMode = true
            }

            btnAlquilar.requestFocus()
        }

        alertDialog.show()
    }

    // Agregamos (dialog: AlertDialog) aquí
    private fun verificarYProcesarPedido(dialog: AlertDialog) {
        Log.d("ALQUILER_LOG", "2. Entrando a verificarYProcesarPedido para: $movieTitle")

        val query = databaseRef.child("pedidosmovies")
            .orderByChild("title")
            .equalTo(movieTitle)

        query.get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                Log.d("ALQUILER_LOG", "3. La película no ha sido pedida aún. Procediendo...")
                enviarPedido(dialog)
            } else {
                Log.d("ALQUILER_LOG", "3. La película YA existe en pedidos.")
                Toast.makeText(this, "Esta película ya fue pedida.", Toast.LENGTH_LONG).show()
            }
        }.addOnFailureListener { e ->
            Log.e("ALQUILER_LOG", "ERROR en consulta de pedidos: ${e.message}")
        }
    }
    // Agregamos (dialog: AlertDialog) aquí
    private fun enviarPedido(dialog: AlertDialog) {
        if (isProcessingOrder) return
        isProcessingOrder = true

        val user = auth.currentUser
        if (user != null && user.email != null) {
            val correoKey = user.email!!.replace(".", "_").replace("@", "_")

            databaseRef.child("usuarios").child(correoKey).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val userName = snapshot.child("nombre").value?.toString() ?: "Sin nombre"
                    val userEmail = snapshot.child("correo").value?.toString() ?: user.email!!
                    val costoPedido = (movieCastv as? Number)?.toInt() ?: 0

                    // ✅ Ahora enviamos los dos parámetros correctamente
                    verificarPuntos(correoKey, costoPedido) { tienePuntos ->
                        if (tienePuntos) {
                            val datos = hashMapOf(
                                "title" to movieTitle,
                                "castv" to costoPedido,
                                "email" to userEmail,
                                "nombre" to userName,
                                "userId" to snapshot.child("userId").value?.toString(),
                                "timestamp" to ServerValue.TIMESTAMP
                            )

                            databaseRef.child("pedidosmovies").push().setValue(datos)
                                .addOnSuccessListener {
                                    dialog.dismiss()
                                    descontarPuntos(correoKey, costoPedido)
                                    enviarCorreoNuevoPedido(movieTitle)
                                    isProcessingOrder = false
                                }
                                .addOnFailureListener { e ->
                                    isProcessingOrder = false
                                    Toast.makeText(this, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            isProcessingOrder = false
                            Toast.makeText(this, "Saldo CasTV insuficiente.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    isProcessingOrder = false
                }
            }.addOnFailureListener { isProcessingOrder = false }
        }
    }
    private fun verificarPuntos(correoKey: String, costo: Int, callback: (Boolean) -> Unit) {
        Log.d("ALQUILER_LOG", "Buscando en la ruta correcta: usuarios/$correoKey")

        val userRef = databaseRef.child("usuarios").child(correoKey)

        userRef.child("castv").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val puntosActuales = (snapshot.value as? Number)?.toInt() ?: 0
                Log.d("ALQUILER_LOG", "✅ Puntos encontrados para $correoKey: $puntosActuales")
                callback(puntosActuales >= costo)
            } else {
                Log.e("ALQUILER_LOG", "❌ No se encontró la carpeta: usuarios/$correoKey")
                callback(false)
            }
        }.addOnFailureListener { e ->
            Log.e("ALQUILER_LOG", "Error de Firebase: ${e.message}")
            callback(false)
        }
    }
    private fun enviarCorreoNuevoPedido(movieTitle: String) {
        val url = "https://server-csks8w.fly.dev/correo"

        // No codificamos el título, lo enviamos tal cual
        val jsonBody = JSONObject()
        jsonBody.put("titulo", movieTitle)

        val requestQueue = Volley.newRequestQueue(this)
        val jsonRequest = object : JsonObjectRequest(
            Request.Method.POST, url, jsonBody,
            Response.Listener { response ->
                Log.d("Email", "✅ Correo enviado exitosamente: $response")
            },
            Response.ErrorListener { error ->
                Log.e("Email", "❌ Error al enviar el correo: ${error.message}")
            }
        ) {
            override fun getBodyContentType(): String = "application/json; charset=utf-8"
        }

        requestQueue.add(jsonRequest)
    }

    private fun descontarPuntos(correoKey: String, puntosADescontar: Int) {
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)

        userRef.child("castv").get().addOnSuccessListener { snapshot ->
            val castvActual = (snapshot.value as? Number)?.toInt() ?: 0

            if (castvActual >= puntosADescontar) {
                val nuevoCastv = castvActual - puntosADescontar

                userRef.child("castv").setValue(nuevoCastv)
                    .addOnSuccessListener {
                        Log.d("ALQUILER_LOG", "✅ Descuento aplicado. Nuevo saldo: $nuevoCastv")
                        Toast.makeText(this, "Pedido enviado exitosamente", Toast.LENGTH_SHORT).show()

                        val intent = Intent(this, Nosotros::class.java)
                        startActivity(intent)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Log.e("ALQUILER_LOG", "❌ Error al actualizar saldo: ${e.message}")
                    }
            }
        }.addOnFailureListener { e ->
            Log.e("ALQUILER_LOG", "Error de conexión: ${e.message}")
        }
    }
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
                    tiempototal.text = String.format("%02d:%02d:%02d", h, m, s)
                } else {
                    tiempototal.text = "Finalizado"
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
