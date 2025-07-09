package com.creativem.fulltv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
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
    private lateinit var tvReproducir: TextView
    private lateinit var backgroundImageView: ImageView
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
    private lateinit var tvInfoAdicional: TextView
    private lateinit var recyclerActores: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_pelicula)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()

        recyclerMenu = findViewById(R.id.recycler_movies_menu)
        ivPoster = findViewById(R.id.ivPoster)
        tvTitulo = findViewById(R.id.tvTitulo)
        tvFecha = findViewById(R.id.tvFecha)
        tvCalificacion = findViewById(R.id.tvCalificacion)
        tvSinopsis = findViewById(R.id.tvSinopsis)
        tvReproducir = findViewById(R.id.tvReproducir)
        tvInfoAdicional = findViewById(R.id.tvInfoAdicional)
        recyclerActores = findViewById(R.id.recyclerActores)
        backgroundImageView = findViewById(R.id.backgroundImageView)


        tvReproducir.isFocusableInTouchMode = true
        tvReproducir.requestFocus()
        tvReproducir.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.scaleX = 1.05f
                view.scaleY = 1.05f
            } else {
                view.scaleX = 1f
                view.scaleY = 1f
            }
        }


        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        loadingContainer = findViewById(R.id.layoutCargando)

        val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        apiService = retrofit.create(TMDbApiService::class.java)

        // Recibir datos desde intent
        val movieOriginalTitle = intent.getStringExtra("EXTRA_ORIGINAL_TITLE") ?: ""
        streamUrlGuardado = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""
        movieTitle = intent.getStringExtra("EXTRA_MOVIE_TITLE") ?: ""
        movieYear = intent.getStringExtra("EXTRA_MOVIE_YEAR") ?: ""
        movieImageUrl = intent.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""
        movieCountdown = intent.getIntExtra("EXTRA_COUNTDOWN", 0)

        buscarPelicula(movieOriginalTitle.ifBlank { movieTitle })

        adapter = PeliculasMenuAdapter(mutableListOf()) { movie ->
            movieTitle = movie.title
            streamUrlGuardado = movie.streamUrl
            movieActual = movie

            tvTitulo.text = movie.title
            tvFecha.text = "Estreno: ${movie.year}"
            tvCalificacion.text = "⭐ ${movie.casTV}"
            tvSinopsis.text = "Tiempo válido: ${movie.countdownMinutes} min"

            Glide.with(this)
                .load(movie.imageUrl)
                .placeholder(R.drawable.icono)
                .into(ivPoster)

            buscarPelicula(movie.originalTitle)
        }

        recyclerMenu.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerMenu.adapter = adapter

        loadPeliculasValidas()

        tvReproducir.setOnClickListener {
            if (streamUrlGuardado.isBlank()) {
                Toast.makeText(this, "URL de reproducción no disponible", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, PlayerPeliculas::class.java)
            intent.putExtra("EXTRA_STREAM_URL", streamUrlGuardado)
            intent.putExtra("EXTRA_MOVIE_TITLE", movieActual?.title ?: movieTitle)
            intent.putExtra("EXTRA_MOVIE_YEAR", movieActual?.year ?: movieYear)
            intent.putExtra("EXTRA_MOVIE_IMAGE_URL", movieActual?.imageUrl ?: movieImageUrl)
            intent.putExtra("EXTRA_COUNTDOWN", movieActual?.countdownMinutes ?: movieCountdown)
            startActivity(intent)
        }

    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }


    private fun loadPeliculasValidas() {
        CoroutineScope(Dispatchers.Main).launch {
            val peliculas = Validacioneslista.obtenerPeliculasValidas()

            if (peliculas.isNotEmpty()) {
                adapter.updateMovies(peliculas)
            } else {
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
        tvCalificacion.text = "⭐${movie.vote_average ?: "N/A"}"
        tvSinopsis.text = movie.overview ?: "Sin sinopsis disponible"

        val posterUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
        Glide.with(this)
            .load(posterUrl)
            .placeholder(R.drawable.icono)
            .into(ivPoster)

        Glide.with(this)
            .load(posterUrl)
            .placeholder(R.drawable.icono)
            .into(ivPoster)

// Fondo opaco
        Glide.with(this)
            .load(posterUrl)
            .centerCrop()
            .into(backgroundImageView)

        // Limpia datos adicionales
        tvInfoAdicional.text = ""
        recyclerActores.adapter = null

        // 🔽 Obtener detalles (duración, géneros)
        apiService.getMovieDetails(movie.id, apiKey, "es-MX")
            .enqueue(object : Callback<MovieDetailResponse> {
                override fun onResponse(
                    call: Call<MovieDetailResponse>,
                    response: Response<MovieDetailResponse>
                ) {
                    if (response.isSuccessful) {
                        val detalles = response.body()
                        val generos = detalles?.genres?.joinToString(", ") { it.name } ?: "Desconocidos"
                        val duracion = detalles?.runtime ?: 0

                        val texto = "🎭 Géneros: $generos\n⏱   Duración: ${duracion} min "
                        tvInfoAdicional.text = texto
                    }
                }

                override fun onFailure(call: Call<MovieDetailResponse>, t: Throwable) {
                    tvInfoAdicional.text = "No se pudieron obtener detalles"
                }
            })

        // 🔽 Obtener reparto y director
        apiService.getCredits(movie.id, apiKey)
            .enqueue(object : Callback<CreditsResponse> {
                override fun onResponse(
                    call: Call<CreditsResponse>,
                    response: Response<CreditsResponse>
                ) {
                    if (response.isSuccessful) {
                        val creditos = response.body()
                        val director = creditos?.crew?.find { it.job == "Director" }?.name ?: "N/D"
                        tvInfoAdicional.append("🎬 Director: $director")

                        // Reparto limitado a 6 actores
                        val actores = creditos?.cast?.take(6)
                        if (!actores.isNullOrEmpty()) {
                            recyclerActores.layoutManager =
                                LinearLayoutManager(this@ApiPeliculaActivity, LinearLayoutManager.HORIZONTAL, false)
                            recyclerActores.adapter = ActoresAdapter(actores)
                        }
                    }
                }

                override fun onFailure(call: Call<CreditsResponse>, t: Throwable) {}
            })
    }




    private fun mostrarContenidoLocal() {
        val movie = movieActual
        if (movie != null) {
            tvTitulo.text = movie.title
            tvFecha.text = "Estreno: ${movie.year}"
            tvCalificacion.text = "⭐${movie.casTV}"
            tvSinopsis.text = "Tiempo válido: ${movie.countdownMinutes} min"
            Glide.with(this)
                .load(movie.imageUrl)
                .placeholder(R.drawable.icono)
                .into(ivPoster)
        } else {
            tvTitulo.text = movieTitle
            tvFecha.text = "Estreno: $movieYear"
            tvCalificacion.text = "⭐ N/A"
            tvSinopsis.text = "Tiempo válido: $movieCountdown min"
            Glide.with(this)
                .load(movieImageUrl)
                .placeholder(R.drawable.icono)
                .into(ivPoster)
        }
        Toast.makeText(this, "Mostrando datos locales", Toast.LENGTH_SHORT).show()
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
