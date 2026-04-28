package com.creativem.fulltv.api

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
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.peliculasvalidas.PelisCarteleraAdapter
import com.creativem.fulltv.principal.Movie
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class ApiPeliculaActivity : AppCompatActivity() {
    // Añade esto debajo de las otras variables
    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }
    private lateinit var ivPoster: ImageView
    private lateinit var tvTitulo: TextView
    private lateinit var tvFecha: TextView
    private lateinit var tvCalificacion: TextView
    private lateinit var tvSinopsis: TextView
    private lateinit var tvReproducir: TextView
    private lateinit var backgroundImageView: ImageView
    private lateinit var tvInfoAdicional: TextView
    private lateinit var recyclerActores: RecyclerView
    private lateinit var recyclerCartelera: RecyclerView
    private lateinit var carteleraAdapter: PelisCarteleraAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: View
    private lateinit var loadingContainer: FrameLayout

    private var progreso = 0
    private var cargandoMostrado = false
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

    private var streamUrlGuardado = ""
    private var movieTitle = ""
    private var movieCastv: Int = 0
    private var movieImageUrl = ""
    private var movieCountdown = 0
    private var movieActual: Movie? = null
    private var movieReleaseDate: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_pelicula)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()


        ivPoster = findViewById(R.id.ivPoster)
        tvTitulo = findViewById(R.id.tvTitulo)
        tvFecha = findViewById(R.id.tvFecha)
        tvCalificacion = findViewById(R.id.tvCalificacion)
        tvSinopsis = findViewById(R.id.tvSinopsis)
        tvReproducir = findViewById(R.id.tvReproducir)
        tvInfoAdicional = findViewById(R.id.tvInfoAdicional)
        recyclerActores = findViewById(R.id.recyclerActores)
        backgroundImageView = findViewById(R.id.backgroundImageView)

        val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        apiService = retrofit.create(TMDbApiService::class.java)

        val movieOriginalTitle = intent.getStringExtra("EXTRA_ORIGINAL_TITLE") ?: ""
        streamUrlGuardado = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""
        movieTitle = intent.getStringExtra("EXTRA_MOVIE_TITLE") ?: ""
        movieCastv = intent.getIntExtra("EXTRA_MOVIE_CASTV", 0)
        movieImageUrl = intent.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""
        movieCountdown = intent.getIntExtra("EXTRA_COUNTDOWN", 0)



        recyclerCartelera = findViewById(R.id.peliscartelera)
        recyclerCartelera.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        tvReproducir.setOnClickListener {
            if (streamUrlGuardado.isBlank()) {
                Toast.makeText(this, "URL de reproducción no disponible", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val tituloConFecha = if (movieActual != null) {
                val fecha = movieActual?.releaseDate ?: movieReleaseDate
                "${movieActual?.title} $fecha"
            } else {
                "$movieTitle $movieReleaseDate"
            }

            val intent = Intent(this, PlayerPeliculas::class.java).apply {
                putExtra("EXTRA_STREAM_URL", streamUrlGuardado)
                putExtra("EXTRA_MOVIE_TITLE", tituloConFecha)
                putExtra("EXTRA_MOVIE_CASTV", movieActual?.castv ?: movieCastv)
                putExtra("EXTRA_MOVIE_IMAGE_URL", movieActual?.imageUrl ?: movieImageUrl)
                putExtra("EXTRA_COUNTDOWN", movieActual?.countdownMinutes ?: movieCountdown)
            }
            startActivity(intent)

        }

        tvReproducir.isFocusableInTouchMode = true
        tvReproducir.requestFocus()
        tvReproducir.setOnFocusChangeListener { v, hasFocus ->
            v.scaleX = if (hasFocus) 1.05f else 1f
            v.scaleY = if (hasFocus) 1.05f else 1f
        }

        cargarCartelera()
        buscarPelicula(movieOriginalTitle.ifBlank { movieTitle })
    }

    private fun cargarCartelera() {
        val pelisMostradas = mutableListOf<TmdbMovie>()

        // 1. Configuramos el adaptador de una vez para que la lista no esté nula
        carteleraAdapter = PelisCarteleraAdapter(pelisMostradas) { movieSeleccionado ->
            actualizarPeliculaSeleccionada(movieSeleccionado)
        }
        recyclerCartelera.adapter = carteleraAdapter

        // 2. Iniciamos un "observador" en tiempo real
        CoroutineScope(Dispatchers.Main).launch {
            // Ejecutamos mientras la actividad esté viva
            while (isActive) {
                val listaActualDelObjeto = Validacioneslista.obtenerPeliculasValidas()

                // Si el objeto Singleton ha encontrado nuevas películas...
                if (listaActualDelObjeto.size > pelisMostradas.size) {

                    // Mapeamos solo las que no tenemos
                    val mapeoActualizado = listaActualDelObjeto.map { movie ->
                        TmdbMovie(
                            id = 0,
                            title = movie.title,
                            poster_path = movie.imageUrl.replace("https://image.tmdb.org/t/p/w500", ""),
                            release_date = "Verificada ✅",
                            vote_average = 10.0,
                            overview = "Cargando datos...",
                            original_title = movie.originalTitle ?: movie.title,
                            streamUrl = movie.streamUrl,
                            imageUrl = movie.imageUrl,
                            castv = movie.castv
                        )
                    }

                    // Actualizamos la lista del adaptador
                    pelisMostradas.clear()
                    pelisMostradas.addAll(mapeoActualizado)
                    carteleraAdapter.notifyDataSetChanged()
                }

                // Si el proceso global ya terminó todas las pelis del servidor, dejamos de vigilar
                if (Validacioneslista.yaCargado()) break

                // Revisa cada medio segundo para que parezca instantáneo
                delay(500)
            }
        }
    }
    private fun actualizarPeliculaSeleccionada(movieSeleccionado: TmdbMovie) {
        // 🟢 PASO 1: CAMBIO VISUAL INMEDIATO
        // No esperamos a la API, pintamos ya lo que tenemos en la mano
        tvTitulo.text = movieSeleccionado.title
        tvSinopsis.text = "Cargando información detallada..."
        tvInfoAdicional.text = "Obteniendo géneros y duración..."
        recyclerActores.adapter = null // Limpia actores de la peli anterior

        // Carga las imágenes de inmediato (ya las tenemos en la lista local)
        Glide.with(this).load(movieSeleccionado.imageUrl).placeholder(R.drawable.icono).into(ivPoster)
        Glide.with(this).load(movieSeleccionado.imageUrl).centerCrop().into(backgroundImageView)

        // 🟢 PASO 2: ACTUALIZAR DATOS PARA EL PLAYER
        streamUrlGuardado = movieSeleccionado.streamUrl
        movieTitle = movieSeleccionado.title
        movieImageUrl = movieSeleccionado.imageUrl

        movieActual = Movie(
            id = movieSeleccionado.id.toString(),
            title = movieSeleccionado.title,
            originalTitle = movieSeleccionado.original_title ?: movieSeleccionado.title,
            imageUrl = movieSeleccionado.imageUrl,
            streamUrl = movieSeleccionado.streamUrl,
            castv = 50,
            countdownMinutes = 60
        )

        // 🟢 PASO 3: CONSULTA API EN SEGUNDO PLANO
        // Esto rellenará la sinopsis, director y actores en unos milisegundos
        val consulta = movieSeleccionado.original_title ?: movieSeleccionado.title
        buscarPelicula(consulta)

        // Foco para control remoto
        tvReproducir.requestFocus()
    }
    private fun buscarPelicula(query: String) {
        apiService.searchMovie(apiKey, "es-MX", query)
            .enqueue(object : Callback<MovieResponse> {
                override fun onResponse(call: Call<MovieResponse>, response: Response<MovieResponse>) {
                    if (response.isSuccessful) {
                        response.body()?.results?.firstOrNull()?.let {
                            mostrarPelicula(it)
                        } ?: mostrarContenidoLocal()
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
        tvFecha.text = "\uD83D\uDDD3 ${movie.release_date ?: "N/A"}"
        tvCalificacion.text = "⭐ ${movie.vote_average ?: "N/A"} "
        tvSinopsis.text = movie.overview ?: "Sin sinopsis disponible"

        val posterUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
        Glide.with(this).load(posterUrl).placeholder(R.drawable.icono).into(ivPoster)
        Glide.with(this).load(posterUrl).centerCrop().into(backgroundImageView)

        tvInfoAdicional.text = ""
        recyclerActores.adapter = null

        apiService.getMovieDetails(movie.id, apiKey, "es-MX").enqueue(object : Callback<MovieDetailResponse> {
            override fun onResponse(call: Call<MovieDetailResponse>, response: Response<MovieDetailResponse>) {
                if (response.isSuccessful) {
                    val detalles = response.body()
                    val generos = detalles?.genres?.joinToString(", ") { it.name } ?: "Desconocidos"
                    val duracion = detalles?.runtime ?: 0
                    tvInfoAdicional.text = "🎭 $generos ⏱ ${duracion} Min "
                }
            }

            override fun onFailure(call: Call<MovieDetailResponse>, t: Throwable) {
                tvInfoAdicional.text = "No se pudieron obtener detalles"
            }
        })

        apiService.getCredits(movie.id, apiKey).enqueue(object : Callback<CreditsResponse> {
            override fun onResponse(call: Call<CreditsResponse>, response: Response<CreditsResponse>) {
                if (response.isSuccessful) {
                    val creditos = response.body()
                    val director = creditos?.crew?.find { it.job == "Director" }?.name ?: "N/D"
                    tvInfoAdicional.append("Director: $director")

                    val actores = creditos?.cast?.take(6)
                    if (!actores.isNullOrEmpty()) {
                        recyclerActores.layoutManager = LinearLayoutManager(this@ApiPeliculaActivity, LinearLayoutManager.HORIZONTAL, false)
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
            tvFecha.text = "Estreno: ${movie.castv}"
            tvCalificacion.text = "⭐ ${movie.castv}" // CasTV fijo
            tvSinopsis.text = "Tiempo válido: ${movie.countdownMinutes} min"
            Glide.with(this).load(movie.imageUrl).placeholder(R.drawable.icono).into(ivPoster)
        } else {
            tvTitulo.text = movieTitle
            tvFecha.text = "Estreno: $movieCastv"
            tvCalificacion.text = "⭐ 50"
            tvSinopsis.text = "Tiempo válido: $movieCountdown min"
            Glide.with(this).load(movieImageUrl).placeholder(R.drawable.icono).into(ivPoster)
        }
    }

}