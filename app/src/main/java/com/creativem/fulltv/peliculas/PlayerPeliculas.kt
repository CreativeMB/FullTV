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
import android.os.CountDownTimer

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.widget.ImageView
import androidx.activity.addCallback
import com.bumptech.glide.Glide

import com.creativem.fulltv.databinding.PlayerBinding
import com.creativem.fulltv.principal.Nosotros


import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope

import androidx.media3.common.util.UnstableApi
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
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnableActualizar: Runnable
    private lateinit var runnableOcultar: Runnable
    private val hideControlsDelay: Long = 10000 // 10 segundos
    private val updateInterval: Long = 1000 // 1 segundo
    private var isProcessingOrder = false
    private var reconnectionAttempts = 0
    private val maxReconnectionAttempts = 10
    private var isReconnecting = false
    private var isPlaybackActive = false // Indica si la reproducción ha sido activa
    private val playbackStartTime = AtomicLong(0) // Tiempo en que inicia la reproducción
    private var lastKnownPosition: Long = 0 // Para guardar la última posición conocida



    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)


        initializeRecyclerView() // Configura el RecyclerView con un adaptador vacío
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
        actualizarTiempo()


        onBackPressedDispatcher.addCallback(this) {
            val menuVisible = binding.recyclerMoviesMenu.visibility == View.VISIBLE
            val controlesVisibles = binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility == View.VISIBLE

            when {
                menuVisible -> {
                    binding.recyclerMoviesMenu.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            binding.recyclerMoviesMenu.visibility = View.GONE
                            binding.recyclerMoviesMenu.alpha = 1f
                        }
                        .start()
                }

                controlesVisibles -> {
                    binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.GONE
                }

                else -> {
                    finish()
                }
            }
        }


    }

    private fun mostarpelis() {
        val isVisible = binding.recyclerMoviesMenu.visibility == View.VISIBLE

        if (isVisible) {
            binding.recyclerMoviesMenu.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    binding.recyclerMoviesMenu.visibility = View.GONE
                    binding.recyclerMoviesMenu.alpha = 1f
                }
                .start()
        } else {
            binding.recyclerMoviesMenu.visibility = View.VISIBLE
            binding.recyclerMoviesMenu.alpha = 1f
        }
    }


    private fun initializeRecyclerView() {
        // Crear el adaptador inicialmente con una lista vacía
        adapter = PeliculasMenuAdapter(mutableListOf()) { movie ->
            startMoviePlayback(movie.streamUrl, movie.title, movie.year, movie.imageUrl)
        }

        // Establecer el LayoutManager horizontal
        binding.recyclerMoviesMenu.layoutManager =
            LinearLayoutManager(this@PlayerPeliculas, LinearLayoutManager.HORIZONTAL, false)

        binding.recyclerMoviesMenu.adapter = adapter

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

    // Método para inicializar el reproductor de video
    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        // Verifica si la URL ya ha sido establecida
        if (streamUrl.isEmpty()) {
            showErrorDialog(movieTitle, movieYear)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            // Validar la URL en Firestore de manera asíncrona
            val isUrlValid = withContext(Dispatchers.IO) {
                isUrlValidInFirestore(streamUrl)
            }

            // Si la URL no es válida, muestra un diálogo de error
            if (!isUrlValid) {
                showErrorDialog(movieTitle, movieYear)
                return@launch
            }

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(mapOf("User-Agent" to "Mozilla/5.0"))
                .setConnectTimeoutMs(30_000) // Tiempo de espera de conexión (30 segundos)
                .setReadTimeoutMs(30_000) // Tiempo de espera de lectura (30 segundos)

            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            // Configura el LoadControl
            val loadControl = DefaultLoadControl.Builder()
                .setTargetBufferBytes(8 * 1024 * 1024) // 8 MB
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()

            // Crea el reproductor
            player = ExoPlayer.Builder(this@PlayerPeliculas)
                .setLoadControl(loadControl)
                .setRenderersFactory(
                    DefaultRenderersFactory(this@PlayerPeliculas)
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

                    // Añade el listener del reproductor para detectar si es en vivo
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                            // Verifica si hay ventanas en la línea de tiempo
                            if (timeline.windowCount > 0) {
                                val window = Timeline.Window()
                                timeline.getWindow(0, window)
                                // Determina si la ventana es en vivo
                                when {
                                    !window.isLive -> {

                                    }
                                }
                            }
                        }
                    })

                    // Añade el listener del reproductor
                    exoPlayer.addListener(playerListener)
                    // Inicia la reproducción automáticamente
                    exoPlayer.playWhenReady = true
                }
        }
    }
    // Listener para el reproductor
    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    mostrarBuffer() // Muestra el estado del búfer
                }

                Player.STATE_READY -> {

                    isPlaybackActive = true // Indica que la reproducción está activa
                    playbackStartTime.set(System.currentTimeMillis()) // Guarda el tiempo de inicio
                    reconnectionAttempts = 0 // Reinicia los intentos de reconexión
                    isReconnecting = false // Indica que no se está reconectando

                    // Reanudar desde la última posición conocida
                    if (lastKnownPosition > 0) {
                        player?.seekTo(lastKnownPosition) // Busca a la última posición
                        lastKnownPosition = 0 // Reinicia la posición
                    }

                    actualizarTiempo() // Actualiza el tiempo de reproducción
                }

                Player.STATE_ENDED -> {

                    isPlaybackActive = false // La reproducción ya no está activa
                    showErrorDialog(movieTitle, movieYear)
                    // Si es un stream en vivo, intentar reconectar
                    if (isLiveStream) {

                        intentarReconexion() // Llama al método de reconexión
                    } else {

                        handler.removeCallbacks(runnable) // Detiene el runnable
                    }
                }

                Player.STATE_IDLE -> {

                    isPlaybackActive = false // La reproducción ya no está activa

                    // Si es un stream en vivo, intentar reconectar
                    if (isLiveStream) {

                        intentarReconexion() // Llama al método de reconexión
                    } else {

                        handler.removeCallbacks(runnable) // Detiene el runnable
                    }
                }

                else -> {

                    // En caso de un estado desconocido, intenta reconectar si no hay actividad de reproducción
                    if (!isPlaybackActive && reconnectionAttempts < maxReconnectionAttempts) {

                        intentarReconexion() // Llama al método de reconexión
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

        val spannable = SpannableStringBuilder()

        // Primera línea: "Película: <Título>"
        val movieInfo = "Película: $movieTitle\n"
        spannable.append(movieInfo)

        // Resaltar "Película:" en rojo y más grande
        val peliculaTexto = "Película:"
        val peliculaIndex = spannable.indexOf(peliculaTexto)
        spannable.setSpan(ForegroundColorSpan(Color.RED), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), peliculaIndex, peliculaIndex + peliculaTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Resaltar el título de la película en rojo y más grande
        val tituloIndex = peliculaIndex + peliculaTexto.length + 1
        spannable.setSpan(ForegroundColorSpan(Color.BLUE), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.4f), tituloIndex, tituloIndex + movieTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Segunda línea: "Precio CasTV: $<Año>"
        val precioInfo = "Precio CasTV: $$movieYear\n"  // Aquí el $ está dentro del String
        spannable.append(precioInfo)

        // Resaltar "Precio CasTV:" en azul y más grande
        val precioTexto = "Precio CasTV:"
        val precioIndex = spannable.indexOf(precioTexto)
        spannable.setSpan(ForegroundColorSpan(Color.RED), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.3f), precioIndex, precioIndex + precioTexto.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Ubicamos el índice del número sin incluir el $
        val precioValorIndex = precioIndex + precioTexto.length + 2 // +2 para saltar "$ "
        spannable.setSpan(ForegroundColorSpan(Color.BLUE), precioValorIndex, precioValorIndex + movieYear.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(RelativeSizeSpan(1.4f), precioValorIndex, precioValorIndex + movieYear.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Agregar las demás líneas sin perder formato
        spannable.append("\nEstará en línea en breve. Estamos disponibles 24/7")
        spannable.append("\nSi no tienes saldo recuerda recargar en COP")

        // Aplicar el texto formateado al TextView
        messageText.text = spannable

        // Configurar el enlace a la actividad "Nosotros"
        linkNosotros.text = "Más información aquí"
        linkNosotros.setTextColor(Color.GRAY)
        linkNosotros.paintFlags = linkNosotros.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        linkNosotros.setOnClickListener {
            val intent = Intent(this, Nosotros::class.java)
            startActivity(intent)
        }

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("¡Alquila Tu Pelicula!")
            .setView(dialogView)
            .setPositiveButton("Volver al contenido") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNeutralButton("Alquilar Pelicula") { _, _ ->
                verificarYProcesarPedido()
            }
            .create() // Asegurar que se crea antes de modificar el fondo

        alertDialog.setOnShowListener {
            alertDialog.window?.setBackgroundDrawableResource(R.color.textColorPrimary) // Reemplaza con tu color
        }

        alertDialog.show() // Mostrar después de aplicar el fondo
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
        showControlsAndResetTimer()
        Log.d("KeyCodeTest", "Tecla presionada: $keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                mostarpelis()
                true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                Log.d("KeyCodeTest", "Página Arriba presionada")
                mostarpelis()
                true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                Log.d("KeyCodeTest", "Página Abajo presionada")
                mostarpelis()
                true
            }

            174 -> { // Código del botón del control remoto
                Log.d("KeyCodeTest", "Botón del control remoto (174) presionado")
                mostarpelis()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

}
