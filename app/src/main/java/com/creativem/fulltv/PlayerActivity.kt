package com.creativem.fulltv

import android.annotation.SuppressLint
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.fulltv.databinding.ActivityPlayerBinding
import com.creativem.fulltv.ui.data.Movie
import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException


class PlayerActivity : AppCompatActivity() {
    //    private lateinit var playerView: PlayerView
    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var isLiveStream = false

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var adapter: MoviesMenuAdapter
    private lateinit var moviesCollection: CollectionReference
    private lateinit var firestore: FirebaseFirestore


    private var reconnectionAttempts = 0 // Contador de intentos de reconexión
    private val maxReconnectionAttempts = 5 // Máximo de intentos permitidos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        streamUrl = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""

        if (streamUrl.isEmpty()) {
            Log.e("PlayerActivity", "No se recibió la URL de streaming.")
            showErrorDialog("No se recibió la URL de streaming.")
            return
        }
        firestore = Firebase.firestore
        // Inicializa el RecyclerView
        initializeRecyclerView()
        player = ExoPlayer.Builder(this).build()
        binding.reproductor.player = player
        initializePlayer()
        val menupelis = binding.reproductor.findViewById<ImageButton>(R.id.exo_lista_pelis)
        menupelis.setOnClickListener {
            mostarpélis()

        }
        // Cargar películas de Firestore
        loadMoviesFromFirestore()

    }

    private fun mostarpélis() {
        Log.e("PlayerActivity", "clki menupelis")
        binding.recyclerViewMovies.visibility = if (binding.recyclerViewMovies.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun loadMoviesFromFirestore() {
        firestore.collection("movies")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val movies = mutableListOf<Movie>() // Crear una lista temporal para almacenar las películas

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
        intent.putExtra("EXTRA_STREAM_URL", streamUrl) // Asegúrate de usar el mismo nombre clave que usas en VideoPlayerActivity
        startActivity(intent)
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        // Configura el LoadControl
        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(2 * 1024 * 1024)  // 2MB
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // Crea el ExoPlayer
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
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

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("KeyCodeTest", "Tecla liberada: $keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                Log.d("KeyCodeTest", "Menu presionado")
                mostarpélis()
                true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                Log.d("KeyCodeTest", "Página Arriba liberada")
                mostarpélis()
                true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                Log.d("KeyCodeTest", "Página Abajo liberada")
                mostarpélis()
                true
            }

            174 -> { // Código del botón del control remoto
                Log.d("KeyCodeTest", "Botón del control remoto (174) liberado")
                mostarpélis()
                true
            }

            else -> super.onKeyUp(keyCode, event)
        }
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
        // Si el GridView es visible, simplemente ocultarlo
        if (binding.recyclerViewMovies.visibility == View.VISIBLE) {
            binding.recyclerViewMovies.visibility = View.GONE
        } else {
            super.onBackPressed() // Llama al comportamiento predeterminado
        }
    }
   /* override fun onBackPressed() {
        super.onBackPressed()
        // Redirige a MoviesPrincipal
        val intent = Intent(this, MoviesPrincipal::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish() // Opcional: Cierra la actividad actual
    }*/

}
