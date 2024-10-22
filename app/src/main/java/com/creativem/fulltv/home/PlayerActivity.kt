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
import androidx.media3.exoplayer.DefaultRenderersFactory
import kotlinx.coroutines.MainScope
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.creativem.fulltv.R
import com.creativem.fulltv.adapter.FirestoreRepository
import com.creativem.fulltv.menu.MoviesMenuAdapter
import com.google.firebase.auth.FirebaseAuth
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
    private val initialReconnectionDelayMs = 5000L
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
            startMoviePlayback(movie.streamUrl, movie.title)
        }
        binding.recyclerViewMovies.adapter = adapter
        binding.recyclerViewMovies.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewMovies.visibility = View.GONE
    }

    private fun startMoviePlayback(streamUrl: String, movieTitle: String) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("EXTRA_STREAM_URL", streamUrl)
        intent.putExtra("EXTRA_MOVIE_TITLE", movieTitle)
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

                Player.STATE_ENDED -> {
                    Log.d("PlayerActivity", "Reproducción finalizada.")
                    isPlaybackActive = false

                    // Si es un stream en vivo, intentar reconectar
                    if (isLiveStream) {
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

                Player.STATE_IDLE -> {
                    Log.d("PlayerActivity", "Reproductor en estado IDLE.")
                    isPlaybackActive = false

                    // Si es un stream en vivo, intentar reconectar
                    if (isLiveStream) {
                        Log.d("PlayerActivity", "Intentando reconectar transmisión en vivo...")
                        intentarReconexion()
                    } else {
                        Log.d("PlayerActivity", "No hay reproducción activa.")
                        handler.removeCallbacks(runnable)
                    }
                }

                else -> {
                    Log.w("PlayerActivity", "Estado desconocido del reproductor: $playbackState")
                    // En caso de un estado desconocido, intentar reconectar si no hay actividad de reproducción
                    if (!isPlaybackActive && reconnectionAttempts < maxReconnectionAttempts) {
                        intentarReconexion()
                    }
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
                showErrorDialog(streamUrl, movieTitle, movieYear)
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
                showErrorDialog(
                    "No se pudo conectar al stream después de varios intentos.",
                    movieTitle,
                    movieYear
                )
            }
            return
        }

        if (!isNetworkConnected()) {
            Log.d("Reconexión", "No hay conexión a Internet. Esperando...")
            return
        }

        reconnectionAttempts++
        isReconnecting = true

        val tiempoEspera =
            initialReconnectionDelayMs * (2.0.pow((reconnectionAttempts - 1).toDouble())).toLong()

        Log.d(
            "Reconexión",
            "Intentando reconectar (intento $reconnectionAttempts, espera de ${tiempoEspera}ms)..."
        )

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
        val playPauseButton: ImageButton = findViewById(R.id.play_pause)
        playPauseButton.setOnClickListener {
            if (isNetworkConnected()) {
                Log.d("Reconexión", "Clic en Play detectado. Intentando reanudar...")
                player?.playWhenReady = true
            } else {
                Log.d("Reconexión", "No hay conexión, no se puede reproducir.")
            }
        }
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
            player?.let {
                it.stop() // Asegúrate de detener antes de preparar
                it.seekTo(0) // Reiniciar el stream desde el principio si es en vivo
                it.prepare() // Prepara de nuevo el reproductor
                it.playWhenReady = true // Reanuda automáticamente
            }
        } catch (e: Exception) {
            Log.e("Reconexión", "Error al reiniciar la reproducción: ${e.message}")
            intentarReconexion()
        }
    }

    // Método auxiliar para verificar la conexión a internet
    private fun isNetworkConnected(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork?.isConnectedOrConnecting == true
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

