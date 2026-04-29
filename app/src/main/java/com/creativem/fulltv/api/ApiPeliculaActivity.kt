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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.peliculasvalidas.PelisCarteleraAdapter
import com.creativem.fulltv.principal.Movie
import com.creativem.fulltv.principal.ViewUtils
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
        val pelisMostradas = mutableListOf<Movie>()

        carteleraAdapter = PelisCarteleraAdapter(pelisMostradas) { movieSeleccionado ->
            actualizarPeliculaSeleccionada(movieSeleccionado)
        }
        recyclerCartelera.adapter = carteleraAdapter

        CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                val listaActualDelObjeto = Validacioneslista.obtenerPeliculasValidas()

                if (listaActualDelObjeto.size > pelisMostradas.size) {
                    pelisMostradas.clear()
                    pelisMostradas.addAll(listaActualDelObjeto)
                    carteleraAdapter.notifyDataSetChanged()
                }

                if (Validacioneslista.yaCargado()) break
                delay(500)
            }
        }
    }
    private fun actualizarPeliculaSeleccionada(movieSeleccionado: Movie) {
        // 🟢 PASO 1: CAMBIO VISUAL INMEDIATO
        tvTitulo.text = movieSeleccionado.title
        tvSinopsis.text = "Cargando información detallada..."
        tvInfoAdicional.text = "Obteniendo géneros y duración..."
        recyclerActores.adapter = null

        // 🟢 PASO 1: CARGA DEL PÓSTER (ivPoster)
        Glide.with(this)
            .load(movieSeleccionado.imageUrl)
            // Usamos lo que ya tenga el ImageView como placeholder para evitar el parpadeo
            .placeholder(ivPoster.drawable)
            // Forzamos a que use el caché que ya generó el adaptador
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .into(ivPoster)

// 🟢 PASO 2: CARGA DEL FONDO (backgroundImageView)
        Glide.with(this)
            .load(movieSeleccionado.imageUrl)
            .centerCrop()
            // Añadimos un fundido suave para que el cambio de fondo no sea brusco
            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .into(backgroundImageView)

        // 🟢 PASO 2: ACTUALIZAR DATOS PARA EL PLAYER
        streamUrlGuardado = movieSeleccionado.streamUrl
        movieTitle = movieSeleccionado.title
        movieImageUrl = movieSeleccionado.imageUrl

        // Asignamos directamente el objeto seleccionado
        movieActual = movieSeleccionado

        // 🟢 PASO 3: CONSULTA API EN SEGUNDO PLANO
        // Usamos el título original de la clase Movie
        val consulta = movieSeleccionado.originalTitle ?: movieSeleccionado.title
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
        // 2. Carga del Póster (ivPoster)
        Glide.with(this)
            .load(posterUrl)
            // CLAVE: En lugar de R.drawable.icono, usamos el drawable actual
            // Esto evita que la pantalla se ponga en blanco/icono entre cambios
            .placeholder(ivPoster.drawable)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .into(ivPoster)

        // 3. Carga del Fondo
        Glide.with(this)
            .load(posterUrl)
            .centerCrop()
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            // Añadimos un pequeño fundido para que el cambio de fondo no sea brusco
            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
            .into(backgroundImageView)

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
        val movie = movieActual ?: return

        val url = movie.imageUrl

        // Configuramos los textos (Sin los "50")
        tvTitulo.text = movie.title
        tvFecha.text = "Verificada ✅"
        tvCalificacion.text = ""
        tvSinopsis.text = "Cargando información..."

        // CARGA DE IMAGEN SIN PARPADEO
        Glide.with(this)
            .load(url)
            // ELIMINAMOS el placeholder(R.drawable.icono)
            // Al no poner placeholder, Glide NO limpia el ImageView con un icono
            .dontAnimate() // Esto hace que la carga sea inmediata sin efectos de transición
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .into(ivPoster)

        // Fondo con fundido suave
        Glide.with(this)
            .load(url)
            .centerCrop()
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .transition(com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade())
            .into(backgroundImageView)
    }

}