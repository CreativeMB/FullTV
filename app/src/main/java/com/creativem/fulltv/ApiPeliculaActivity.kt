package com.creativem.fulltv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.peliculas.Validacioneslista
import com.creativem.fulltv.peliculasvalidas.PeliculasMenuAdapter
import com.creativem.fulltv.principal.Movie

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class ApiPeliculaActivity : AppCompatActivity() {

    private lateinit var recyclerMenu: RecyclerView
    private lateinit var adapter: PeliculasMenuAdapter
      private lateinit var ivPoster: ImageView
    private lateinit var tvTitulo: TextView
    private lateinit var tvFecha: TextView
    private lateinit var tvCalificacion: TextView
    private lateinit var tvSinopsis: TextView
    private lateinit var videoView: VideoView
    private var lastPosition: Int = 0

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: View
    private lateinit var loadingContainer: FrameLayout

    private var progreso = 0
    private val progresoHandler = Handler(Looper.getMainLooper())
    private val progresoRunnable = object : Runnable {
        override fun run() {
            if (progreso < 95) {
                progreso += 1
                progressBar.progress = progreso
                progresoHandler.postDelayed(this, 100)
            }
        }
    }

    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"

    private var streamUrlGuardado: String = ""
    private var movieTitle: String = ""
    private var movieYear: String = ""
    private var movieImageUrl: String = ""
    private var movieCountdown: Int = 0
    private var movieActual: Movie? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_pelicula)

        // Pantalla completa
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()

        // Inicializar vistas
        recyclerMenu = findViewById(R.id.recycler_movies_menu)
        ivPoster = findViewById(R.id.ivPoster)
        tvTitulo = findViewById(R.id.tvTitulo)
        tvFecha = findViewById(R.id.tvFecha)
        tvCalificacion = findViewById(R.id.tvCalificacion)
        tvSinopsis = findViewById(R.id.tvSinopsis)
        videoView = findViewById(R.id.videoView)

        val videoLayout = findViewById<LinearLayout>(R.id.videoLayout)
        val focusBorder = findViewById<View>(R.id.focusBorder)

        videoLayout.isFocusableInTouchMode = true
        videoLayout.requestFocus()

        videoLayout.setOnFocusChangeListener { _, hasFocus ->
            focusBorder.visibility = if (hasFocus) View.VISIBLE else View.GONE
        }



        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        loadingContainer = findViewById(R.id.layoutCargando)

        // Retrofit
        val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        apiService = retrofit.create(TMDbApiService::class.java)
               // Datos desde intent
        val movieOriginalTitle = intent.getStringExtra("EXTRA_ORIGINAL_TITLE") ?: ""
        streamUrlGuardado = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""
        movieTitle = intent.getStringExtra("EXTRA_MOVIE_TITLE") ?: ""
        movieYear = intent.getStringExtra("EXTRA_MOVIE_YEAR") ?: ""
        movieImageUrl = intent.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""
        movieCountdown = intent.getIntExtra("EXTRA_COUNTDOWN", 0)

        buscarPelicula(movieOriginalTitle.ifBlank { movieTitle })

        if (streamUrlGuardado.isNotEmpty()) {
            reproducirVideo(streamUrlGuardado)
        }

        adapter = PeliculasMenuAdapter(mutableListOf()) { movie ->
            movieTitle = movie.title
            streamUrlGuardado = movie.streamUrl
            movieActual = movie

            tvTitulo.text = movie.title
            tvFecha.text = "Estreno: ${movie.year}"
            tvCalificacion.text = "Calificación: ${movie.casTV}"
            tvSinopsis.text = "Tiempo válido: ${movie.countdownMinutes} min"

            Glide.with(this)
                .load(movie.imageUrl)
                .placeholder(R.drawable.icono)
                .into(ivPoster)

            reproducirVideo(movie.streamUrl)
            buscarPelicula(movie.originalTitle)
        }

        recyclerMenu.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerMenu.adapter = adapter

        loadPeliculasValidas()

        videoLayout.setOnClickListener {
            val currentPosition = videoView.currentPosition
            val intent = Intent(this, PlayerPeliculas::class.java)
            intent.putExtra("EXTRA_STREAM_URL", streamUrlGuardado)
            intent.putExtra("EXTRA_MOVIE_TITLE", movieActual?.title ?: movieTitle)
            intent.putExtra("EXTRA_MOVIE_YEAR", movieActual?.year ?: movieYear)
            intent.putExtra("EXTRA_MOVIE_IMAGE_URL", movieActual?.imageUrl ?: movieImageUrl)
            intent.putExtra("EXTRA_COUNTDOWN", movieActual?.countdownMinutes ?: movieCountdown)
            intent.putExtra("EXTRA_POSITION", currentPosition)
            startActivityForResult(intent, 100) // <- cambia a startActivityForResult
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            val nuevaPosicion = data.getLongExtra("EXTRA_RESULT_POSITION", 0L)
            if (nuevaPosicion > 0) {
                lastPosition = nuevaPosicion.toInt()
                videoView.seekTo(lastPosition)
                videoView.start()
            }
        }
    }


    override fun onBackPressed() {
        super.onBackPressed()
        val intent = Intent()
        intent.putExtra("EXTRA_RESULT_POSITION", videoView.currentPosition)
        setResult(RESULT_OK, intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        val position = videoView.currentPosition.toLong()
        if (position > 10_000 && movieTitle.isNotBlank()) {
            val clave = generarClaveProgreso()
            val prefs = getSharedPreferences("progreso_peliculas", MODE_PRIVATE)
            prefs.edit().putLong(clave, position).apply()
        }
        videoView.pause()
    }



    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("videoPrefs", MODE_PRIVATE)
        lastPosition = prefs.getInt("lastPosition", 0)
        if (lastPosition > 0) {
            videoView.seekTo(lastPosition)
            videoView.start()
        }
    }
    private fun generarClaveProgreso(): String {
        val titulo = movieTitle.trim().ifBlank { "pelicula_sin_titulo" }
        val año = movieYear.trim().ifBlank { "sin_año" }
        return "$titulo-$año".replace(Regex("[^A-Za-z0-9_-]"), "_")
    }



    private fun loadPeliculasValidas() {
        CoroutineScope(Dispatchers.Main).launch {
            val peliculas = Validacioneslista.obtenerPeliculasValidas()

            if (peliculas.isNotEmpty()) {
                adapter.updateMovies(peliculas)
                // No mostrar barra, ya estaban cargadas
            } else {
                // Solo si no hay, mostrar barra y esperar
                mostrarCargando()

                var nuevasPeliculas: List<Movie>
                do {
                    delay(300)
                    nuevasPeliculas = Validacioneslista.obtenerPeliculasValidas()
                } while (nuevasPeliculas.isEmpty())

                adapter.updateMovies(nuevasPeliculas)
                ocultarCargando()
            }
        }
    }


    private fun buscarPelicula(query: String) {
        apiService.searchMovie(apiKey, "es-ES", query)
            .enqueue(object : Callback<MovieResponse> {
                override fun onResponse(call: Call<MovieResponse>, response: Response<MovieResponse>) {
                    if (response.isSuccessful) {
                        val movie = response.body()?.results?.firstOrNull()
                        if (movie != null) {
                            mostrarPelicula(movie)
                        } else {
                            mostrarContenidoLocal()
                        }
                    } else {
                        mostrarContenidoLocal()
                    }
                }

                override fun onFailure(call: Call<MovieResponse>, t: Throwable) {
                    mostrarContenidoLocal()
                }
            })
    }

    private fun mostrarPelicula(movie: TmdbMovie) {
        tvTitulo.text = movie.title
        tvFecha.text = "Estreno: ${movie.release_date ?: "N/A"}"
        tvCalificacion.text = "Calificación: ${movie.vote_average ?: "N/A"}"
        tvSinopsis.text = movie.overview ?: "Sin sinopsis disponible"

        val posterUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
        Glide.with(this)
            .load(posterUrl)
            .placeholder(R.drawable.icono)
            .into(ivPoster)
    }

    private fun mostrarContenidoLocal() {
        val movie = movieActual
        if (movie != null) {
            tvTitulo.text = movie.title
            tvFecha.text = "Estreno: ${movie.year}"
            tvCalificacion.text = "Calificación: ${movie.casTV}"
            tvSinopsis.text = "Tiempo válido: ${movie.countdownMinutes} min"

            Glide.with(this)
                .load(movie.imageUrl)
                .placeholder(R.drawable.icono)
                .into(ivPoster)
        } else {
            tvTitulo.text = movieTitle
            tvFecha.text = "Estreno: $movieYear"
            tvCalificacion.text = "Calificación: N/A"
            tvSinopsis.text = "Tiempo válido: $movieCountdown min"

            Glide.with(this)
                .load(movieImageUrl)
                .placeholder(R.drawable.icono)
                .into(ivPoster)
        }

        Toast.makeText(this, "Mostrando datos locales", Toast.LENGTH_SHORT).show()
    }

    private fun reproducirVideo(url: String) {
        val uri = Uri.parse(url)
        videoView.setVideoURI(uri)

        videoView.setOnPreparedListener { mp ->
            if (lastPosition > 0) {
                videoView.seekTo(lastPosition)
            }
            videoView.start()

            mp.setOnVideoSizeChangedListener { _, videoWidth, videoHeight ->
                val viewWidth = videoView.width.toFloat()
                val viewHeight = videoView.height.toFloat()

                val videoProportion = videoWidth.toFloat() / videoHeight.toFloat()
                val screenProportion = viewWidth / viewHeight

                val lp = videoView.layoutParams

                if (videoProportion > screenProportion) {
                    lp.width = (viewHeight * videoProportion).toInt()
                    lp.height = viewHeight.toInt()
                } else {
                    lp.width = viewWidth.toInt()
                    lp.height = (viewWidth / videoProportion).toInt()
                }

                videoView.layoutParams = lp
                videoView.requestLayout()
            }
        }

        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "Error al reproducir video", Toast.LENGTH_SHORT).show()
            true
        }
    }


    private var cargandoMostrado = false

    private fun mostrarCargando() {
        if (cargandoMostrado) return
        cargandoMostrado = true

        progreso = 0
        progressBar.progress = 0
        loadingContainer.visibility = View.VISIBLE
        loadingText.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        progresoHandler.post(progresoRunnable)
    }

    private fun ocultarCargando() {
        cargandoMostrado = false
        progresoHandler.removeCallbacks(progresoRunnable)

        CoroutineScope(Dispatchers.Main).launch {
            while (progreso < 100) {
                progreso += 5
                if (progreso > 100) progreso = 100
                progressBar.progress = progreso
                delay(10)
            }
            delay(100)
            loadingContainer.visibility = View.GONE
        }
    }

}