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
import android.widget.ImageView
import android.widget.Toast
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultRenderersFactory
import kotlinx.coroutines.MainScope
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.creativem.fulltv.R
import com.creativem.fulltv.adapter.FirestoreRepository
import com.creativem.fulltv.data.Movie
import com.creativem.fulltv.menu.MoviesMenuAdapter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.pow
import org.json.JSONObject

class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private lateinit var movieTitle: String
    private var movieYear: String = ""
    private var isLiveStream = false
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var adapter: MoviesMenuAdapter
    private lateinit var moviesCollection: CollectionReference
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var relojhora: RelojCuston
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.reproductor.useController = false

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
// Referencia al TextView para el nombre de la película
        val nombrePeliculaTextView: TextView = findViewById(R.id.nombrePelicula)
        // Recuperar los datos del Intent
        intent?.let {
            streamUrl = it.getStringExtra("EXTRA_STREAM_URL") ?: ""
            movieTitle = it.getStringExtra("EXTRA_MOVIE_TITLE") ?: "Título desconocido"
            movieYear = it.getStringExtra("EXTRA_MOVIE_YEAR") ?: ""
            // Actualiza el TextView con el título
            nombrePeliculaTextView.text = movieTitle
        }

        if (streamUrl.isEmpty()) {
            Log.e("PlayerActivity", "No se recibió la URL de streaming.")
            showErrorDialog("No se recibió la URL de streaming.", movieTitle, movieYear)
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
        binding.recyclerMoviesMenu.visibility =
            if (binding.recyclerMoviesMenu.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun initializeRecyclerView() {
        // Crear el adaptador inicialmente con una lista vacía
        adapter = MoviesMenuAdapter(mutableListOf()) { movie ->
            startMoviePlayback(movie.streamUrl, movie.title)
        }
        binding.recyclerMoviesMenu.adapter = adapter
        binding.recyclerMoviesMenu.layoutManager = LinearLayoutManager(this@PlayerActivity)

        // Cargar las películas desde Firestore
        loadMovies() // Llama al método que carga las películas
    }

    private fun loadMovies() {
        CoroutineScope(Dispatchers.Main).launch {
            val firestoreRepository = FirestoreRepository()
            val (validMovies, invalidMovies) = firestoreRepository.obtenerPeliculas()

            // Log para verificar la cantidad de películas cargadas
            Log.d("MoviesData", "Películas válidas: ${validMovies.size}, Películas inválidas: ${invalidMovies.size}")

            // Actualizar el adaptador
            withContext(Dispatchers.Main) {
                adapter.updateMovies(validMovies) // Esto ahora valida las URLs de nuevo
                binding.recyclerMoviesMenu.visibility = if (validMovies.isNotEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
    // Método que llama al repositorio de Firestore para validar la URL
    private suspend fun isUrlValidInFirestore(url: String?): Boolean {
        // Verifica si la URL está vacía o es nula
        return if (url.isNullOrEmpty()) {
            false
        } else {
            // Llama al método en tu FirestoreRepository para validar la URL
            FirestoreRepository().isUrlValid(url) // Ajusta esto según tu implementación
        }
    }

    // Método para iniciar la reproducción de la película
    private fun startMoviePlayback(streamUrl: String, movieTitle: String) {
        // Crea un Intent para abrir PlayerActivity
        val intent = Intent(this, PlayerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) // Limpia la pila de actividades
        // Envía la URL de transmisión y el título de la película como extras
        intent.putExtra("EXTRA_STREAM_URL", streamUrl)
        intent.putExtra("EXTRA_MOVIE_TITLE", movieTitle)
        // Inicia la actividad de reproducción
        startActivity(intent)
    }

    // Método para inicializar el reproductor de video
    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        // Ejecuta la validación de la URL de forma asíncrona
        CoroutineScope(Dispatchers.Main).launch {
            // Validar la URL en Firestore antes de reproducir
            if (!isUrlValidInFirestore(streamUrl)) {
                // Si la URL no es válida, muestra un diálogo de error
                showErrorDialog(ulsvideo = streamUrl, movieTitle, movieYear)
                return@launch
            }

            // Crea el DataSource.Factory
            val dataSourceFactory = DefaultDataSource.Factory(this@PlayerActivity)

            // Crea la MediaSourceFactory
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            // Configura el LoadControl
            val loadControl = DefaultLoadControl.Builder()
                // Define el tamaño objetivo del búfer (tamaño máximo)
                .setTargetBufferBytes(8 * 1024 * 1024) // 8 MB
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()

            // Crea el reproductor
            player = ExoPlayer.Builder(this@PlayerActivity)
                .setLoadControl(loadControl)
                .setRenderersFactory(
                    DefaultRenderersFactory(this@PlayerActivity)
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
                                if (window.isLive) {
                                    Log.d("PlayerActivity", "Es una transmisión en vivo")
                                } else {
                                    Log.d("PlayerActivity", "Es un video pregrabado")
                                }
                            }
                        }
                    })

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
                    Log.d("PlayerActivity", "Reproductor almacenando en búfer...")
                    mostrarBuffer() // Muestra el estado del búfer
                }

                Player.STATE_READY -> {
                    Log.d("PlayerActivity", "Reproductor en estado READY")
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
                    Log.d("PlayerActivity", "Reproducción finalizada.")
                    isPlaybackActive = false // La reproducción ya no está activa

                    // Si es un stream en vivo, intentar reconectar
                    if (isLiveStream) {
                        Log.d("PlayerActivity", "Transmisión en vivo finalizada. Intentando reconectar...")
                        intentarReconexion() // Llama al método de reconexión
                    } else {
                        Log.d("PlayerActivity", "Deteniendo actualizaciones de tiempo.")
                        handler.removeCallbacks(runnable) // Detiene el runnable
                    }
                }

                Player.STATE_IDLE -> {
                    Log.d("PlayerActivity", "Reproductor en estado IDLE.")
                    isPlaybackActive = false // La reproducción ya no está activa

                    // Si es un stream en vivo, intentar reconectar
                    if (isLiveStream) {
                        Log.d("PlayerActivity", "Intentando reconectar transmisión en vivo...")
                        intentarReconexion() // Llama al método de reconexión
                    } else {
                        Log.d("PlayerActivity", "No hay reproducción activa.")
                        handler.removeCallbacks(runnable) // Detiene el runnable
                    }
                }

                else -> {
                    Log.w("PlayerActivity", "Estado desconocido del reproductor: $playbackState")
                    // En caso de un estado desconocido, intenta reconectar si no hay actividad de reproducción
                    if (!isPlaybackActive && reconnectionAttempts < maxReconnectionAttempts) {
                        intentarReconexion() // Llama al método de reconexión
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Cambia el comportamiento dependiendo si se está reproduciendo
            if (isPlaying) {
                handler.postDelayed(runnableOcultar, hideControlsDelay) // Oculta controles después de un tiempo
            } else {
                handler.removeCallbacks(runnableOcultar) // Remueve el runnable si no se está reproduciendo
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerActivity", "Error en el reproductor: ${error.message} - Código: ${error.errorCode}")

            // Intenta la reconexión solo si el error es recuperable y la reproducción ha sido activa
            if (isRecoverableError(error) && isPlaybackActive) {
                intentarReconexion() // Llama al método de reconexión
            } else if (!isPlaybackActive && reconnectionAttempts >= maxReconnectionAttempts) {
                showErrorDialog(streamUrl, movieTitle, movieYear) // Muestra un diálogo de error
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
                    "No se pudo conectar al stream después de varios intentos.",
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
            // Intenta reiniciar la reproducción
            if (streamUrl != null) {
                startMoviePlayback(streamUrl, movieTitle) // Llama al método de inicio
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

    private fun showErrorDialog(ulsvideo: String, movieTitle: String, movieYear: String) {
        // Infla el layout personalizado para el diálogo
        val dialogView = layoutInflater.inflate(R.layout.alert_reproductor, null)

        // Encuentra los componentes dentro del layout
        val messageText = dialogView.findViewById<TextView>(R.id.messageText)
        val firstImage = dialogView.findViewById<ImageView>(R.id.firstImage)
        val secondImage = dialogView.findViewById<ImageView>(R.id.secondImage)
        val thirdImage = dialogView.findViewById<ImageView>(R.id.thirdImage)
        val qrneqiText = dialogView.findViewById<TextView>(R.id.qrneqiText)
        val qepseText = dialogView.findViewById<TextView>(R.id.qepseText)
        val qrtjText = dialogView.findViewById<TextView>(R.id.qrtjText)

        qrneqiText.text = "QR Nequi(3014416502)"
        qepseText.text = "QR PSE(3014416502)"
        qrtjText.text = "QR TJ Credito"
        // Configura el mensaje
        messageText.text = "Pelicula: $movieTitle\nPrecio CasTV: $$movieYear\n" +
                "Estara en linea en Breve estamos disponibles 24/7\n" +
                "\nSi no tienes saldo recuerda recargar en COP" +
                "\nPaquete Plata $5.000(Castv: 50)\n" +
                "Paquete Bronce $10.000(Castv: 120)\n" +
                "Paquete Oro $20.000(Castv: 250\n" +
                "Comprobante de pago WhatsApp(+573028667672)"

        // Opcional: Cambia las imágenes si es necesario
        firstImage.setImageResource(R.drawable.qrnequi)
        secondImage.setImageResource(R.drawable.qrpse)
        thirdImage.setImageResource(R.drawable.qrtarjeta)

        // Crea el AlertDialog
        AlertDialog.Builder(this)
            .setTitle("¡Alquila Tu Peli!")
            .setView(dialogView) // Aplica el layout personalizado
            .setPositiveButton("Volver al contenido") { dialog, _ ->
                dialog.dismiss() // Cierra el diálogo
                onBackPressed() // Simula el botón de retroceso
            }
            .setNeutralButton("Alquilar Pelicula") { _, _ ->
                enviarPedido()
            }
            .show()
    }

    private fun enviarPedido() {
        if (isProcessingOrder) {
            return // Salir si ya se está procesando un pedido
        }

        isProcessingOrder = true // Marcar como procesando

        // Crear una consulta para buscar la película por título y año
        val query = firestore.collection("pedidosmovies")
            .whereEqualTo("title", movieTitle)
            .whereEqualTo("year", movieYear)

        query.get().addOnSuccessListener { querySnapshot ->
            if (querySnapshot.isEmpty) {
                // La película no existe, agregarla y descontar puntos
                val userId = auth.currentUser?.uid // Obtener el ID del usuario autenticado
                if (userId != null) {
                    val datos: HashMap<String, Any> = hashMapOf(
                        "title" to movieTitle,
                        "year" to movieYear,
                        "userId" to userId // Agregar el userId a los datos de la película
                    )

                    // Agregar la película a la colección
                    firestore.collection("pedidosmovies")
                        .add(datos)
                        .addOnSuccessListener { documentReference ->
                            // Descontar puntos del usuario
                            descontarPuntos(userId, movieYear.toLong(), datos)

                            // *** Enviar notificación ***
                            enviarCorreoNuevoPedido(movieTitle)
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
                    Toast.makeText(this, "No hay usuario autenticado.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // La película ya existe, mostrar mensaje y redirigir
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
    // Método para enviar un correo
    private fun enviarCorreoNuevoPedido(movieTitle: String) {
        // Crear un objeto JSON para el correo
        val emailData = mapOf(
            "to" to "fulltvurl@gmail.com", // Cambia esto por el correo del destinatario
            "subject" to "$movieTitle",
            "text" to "PAGADA: $movieTitle"
        )

        // Hacer la solicitud POST al servidor que envía el correo
        val url = "https://fulltvurl.glitch.me/sendEmail" // Cambia esto por la URL de tu servidor

        // Usar Volley para hacer la solicitud
        val requestQueue = Volley.newRequestQueue(this) // Contexto de tu actividad

        val jsonObjectRequest = object : JsonObjectRequest(
            Method.POST, url, JSONObject(emailData),
            Response.Listener { response ->
                Log.d("Email", "Correo enviado exitosamente: ${response.toString()}")
            },
            Response.ErrorListener { error ->
                Log.e("Email", "Error al enviar el correo: ${error.message}")
            }
        ) {}

        requestQueue.add(jsonObjectRequest)
    }

    private fun descontarPuntos(
        userId: String,
        puntosADescontar: Long,
        datos: HashMap<String, Any>
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
        if (binding.recyclerMoviesMenu.visibility == View.VISIBLE) {
            binding.recyclerMoviesMenu.visibility = View.GONE
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
        if (player != null && player!!.isPlaying) {
            MainScope().launch {
                val tiemporeproducido =
                    binding.reproductor.findViewById<TextView>(R.id.tiemporeproducido)
                val tiempototal = binding.reproductor.findViewById<TextView>(R.id.tiempototal)
                val seekBar = binding.reproductor.findViewById<SeekBar>(R.id.progreso)
                Log.d("PlayerActivity", "actualizarTiempo() llamado")

                val posicionActual = player?.currentPosition ?: 0
                val duracionTotal = player?.duration ?: 0
                Log.d(
                    "PlayerActivity",
                    "Posición actual: $posicionActual, Duración total: $duracionTotal"
                )

                tiemporeproducido.text = tiempoFormateado(posicionActual)
                tiempototal.text = tiempoFormateado(duracionTotal)

                if (duracionTotal > 0) {
                    val progress = (posicionActual.toFloat() / duracionTotal * 100).toInt()
                    seekBar.progress = progress
                    Log.d("PlayerActivity", "SeekBar progress: $progress")

                    handler.postDelayed(runnableActualizar, updateInterval)
                }
            }
        } else {
            Log.d("PlayerActivity", "El reproductor no está listo o no está reproduciendo")
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

}

