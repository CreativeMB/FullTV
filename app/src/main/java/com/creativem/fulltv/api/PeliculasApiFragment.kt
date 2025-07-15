package com.creativem.fulltv.api

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R

import com.creativem.fulltv.menu.MenuSuperiorAdapter
import com.creativem.fulltv.peliculasvalidas.PeliculasMenuAdapter
import com.creativem.fulltv.principal.Movie
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory

class PeliculasApiFragment : Fragment() {
            private lateinit var recyclerView: RecyclerView
        private lateinit var adapter: PeliculasMenuAdapter
        private lateinit var apiService: TMDbApiService
        private val apiKey = "678193d2c735c6f37840cee035f4d69a"
    private lateinit var mainBackgroundImage: ImageView

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View = inflater.inflate(R.layout.fragment_peliculasapi, container, false)

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            mainBackgroundImage = view.findViewById(R.id.mainBackgroundImage)
            establecerFondoPorDefecto()

            // 🔹 Menu horizontal
            val menuOpciones = listOf(
                "Populares", "Mejor valoradas", "En cartelera", "Acción", "Aventura", "Animación",
                "Comedia", "Crimen", "Documental", "Drama", "Familia", "Fantasía",
                "Historia", "Terror", "Música", "Misterio", "Romance", "Ciencia ficción",
                "Película de TV", "Suspenso", "Bélica", "Western"
            )

            val menuRecycler = view.findViewById<RecyclerView>(R.id.menu_horizontal)
            menuRecycler.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            menuRecycler.adapter = MenuSuperiorAdapter(menuOpciones) { seleccion ->
                when (seleccion) {
                    "Populares" -> cargarPeliculasPopulares()
                    "Mejor valoradas" -> cargarPeliculasTopRated()
                    "En cartelera" -> cargarPeliculasNowPlaying()
                    else -> cargarPeliculasPorGenero(seleccion)
                }
            }

            // 🔹 Recycler de películas
            recyclerView = view.findViewById(R.id.recycler_populares)

            // Se ajusta dinámicamente al tamaño visible
            recyclerView.viewTreeObserver.addOnGlobalLayoutListener {
                val spanCount = calcularElementosPorFila(recyclerView.width)
                if (recyclerView.layoutManager !is GridLayoutManager ||
                    (recyclerView.layoutManager as GridLayoutManager).spanCount != spanCount) {

                    recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
                }
            }

            adapter = PeliculasMenuAdapter(mutableListOf()) { movie ->
                val intent = Intent(requireContext(), ApiPeliculaActivity::class.java).apply {
                    putExtra("EXTRA_STREAM_URL", movie.streamUrl)
                    putExtra("EXTRA_MOVIE_TITLE", movie.title)
                    putExtra("EXTRA_MOVIE_YEAR", movie.year)
                    putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
                    putExtra("EXTRA_ORIGINAL_TITLE", movie.originalTitle)
                    putExtra("EXTRA_COUNTDOWN", movie.countdownMinutes)
                }
                startActivity(intent)
            }
            recyclerView.adapter = adapter

            setupApiService()
            cargarPeliculasPopulares()
        }

    //fondo animado de colores
    private var fondoActual: GradientDrawable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var fondoAnimando = false
    private var matrizX = 0f
    private var direccion = 1
    private var colorIndex = 0
    private var brilloOverlayId: Int = View.generateViewId()

    private val coloresFluorescentes = listOf(
        intArrayOf(0x88000000.toInt(), 0xFF1a1a1a.toInt()), // negro a gris muy oscuro
        intArrayOf(0xFF1b2735.toInt(), 0xFF090a0f.toInt()), // azul grisáceo oscuro a negro absoluto
        intArrayOf(0xFF2c3e50.toInt(), 0xFF34495e.toInt()), // azul pizarra oscuro
        intArrayOf(0xFF3a3f44.toInt(), 0xFF1e272e.toInt()), // gris acero a gris muy oscuro
        intArrayOf(0xFF0f2027.toInt(), 0xFF203a43.toInt()), // tonos carbón
        intArrayOf(0xFF2c2c2c.toInt(), 0xFF1c1c1c.toInt()), // gris profundo a negro
        intArrayOf(0xFF1f1c2c.toInt(), 0xFF928dab.toInt()), // violeta muy oscuro a lavanda grisácea
        intArrayOf(0xFF232526.toInt(), 0xFF414345.toInt()), // gris carbón a gris acero
        intArrayOf(0xFF373737.toInt(), 0xFF232323.toInt()), // gris oscuro a casi negro
        intArrayOf(0xFF0f0c29.toInt(), 0xFF302b63.toInt())  // azul medianoche a azul profundo
    )


    private fun establecerFondoPorDefecto() {
        if (fondoAnimando) return
        fondoAnimando = true
        handler.removeCallbacksAndMessages(null)

        // 1. Cambios de colores rápidos
        fun cambiarColores() {
            val colores = coloresFluorescentes[colorIndex % coloresFluorescentes.size]
            colorIndex++

            val nuevo = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                colores
            ).apply {
                gradientType = GradientDrawable.LINEAR_GRADIENT
            }

            fondoActual?.let { anterior ->
                val transicion = TransitionDrawable(arrayOf(anterior, nuevo))
                mainBackgroundImage.setImageDrawable(transicion)
                transicion.isCrossFadeEnabled = true
                transicion.startTransition(600)
            } ?: run {
                mainBackgroundImage.setImageDrawable(nuevo)
            }

            mainBackgroundImage.apply {
                alpha = 0.9f
                scaleType = ImageView.ScaleType.MATRIX
            }

            fondoActual = nuevo
        }

        cambiarColores()

        handler.postDelayed(object : Runnable {
            override fun run() {
                cambiarColores()
                handler.postDelayed(this, 1500)
            }
        }, 1500)

        // 2. Movimiento escaneado
        handler.post(object : Runnable {
            override fun run() {
                val matrix = Matrix().apply {
                    matrizX += direccion * 1.5f
                    if (matrizX > 120f || matrizX < -120f) direccion *= -1
                    setTranslate(matrizX, 0f)
                }

                mainBackgroundImage.imageMatrix = matrix
                handler.postDelayed(this, 16)
            }
        })

    }




    private fun setupApiService() {
            val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build()
            apiService = retrofit.create(TMDbApiService::class.java)
        }

        private fun cargarPeliculasPopulares() {
            CoroutineScope(Dispatchers.IO).launch {
                val allMovies = mutableListOf<Movie>()
                for (page in 1..5) {
                    try {
                        val response = apiService.getPopularMovies(apiKey, "es-MX", page).awaitResponse()
                        if (response.isSuccessful) {
                            val peliculas = response.body()?.results ?: emptyList()
                            val mapped = peliculas.map { movie ->
                                Movie(
                                    id = movie.id.toString(),
                                    title = "${movie.title} (${movie.release_date ?: "N/A"})",
                                    originalTitle = movie.original_title,
                                    imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                                    streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                    year = "50",
                                    countdownMinutes = 60,
                                    casTV = "50",
                                    createdAt = Timestamp.now()
                                )
                            }
                            allMovies.addAll(mapped)
                        }
                    } catch (e: Exception) {
                        Log.e("PeliculasApiFragment", "Fallo en página $page", e)
                    }
                }
                withContext(Dispatchers.Main) {
                    adapter.updateMovies(allMovies)
                }
            }
        }

        private fun cargarPeliculasTopRated() {
            CoroutineScope(Dispatchers.IO).launch {
                val allMovies = mutableListOf<Movie>()
                for (page in 1..5) {
                    try {
                        val response = apiService.getTopRatedMovies(apiKey, "es-MX", page).awaitResponse()
                        if (response.isSuccessful) {
                            val peliculas = response.body()?.results ?: emptyList()
                            val mapped = peliculas.map { movie ->
                                Movie(
                                    id = movie.id.toString(),
                                    title = "${movie.title} (${movie.release_date ?: "N/A"})",
                                    originalTitle = movie.original_title,
                                    imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                                    streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                    year = "50",
                                    countdownMinutes = 60,
                                    casTV = "50",
                                    createdAt = Timestamp.now()
                                )
                            }
                            allMovies.addAll(mapped)
                        }
                    } catch (e: Exception) {
                        Log.e("PeliculasApiFragment", "Fallo en TopRated página $page", e)
                    }
                }
                withContext(Dispatchers.Main) {
                    adapter.updateMovies(allMovies)
                }
            }
        }

        private fun cargarPeliculasPorGenero(genero: String) {
            val genreId = when (genero) {
                "Acción" -> 28
                "Aventura" -> 12
                "Animación" -> 16
                "Comedia" -> 35
                "Crimen" -> 80
                "Documental" -> 99
                "Drama" -> 18
                "Familia" -> 10751
                "Fantasía" -> 14
                "Historia" -> 36
                "Terror" -> 27
                "Música" -> 10402
                "Misterio" -> 9648
                "Romance" -> 10749
                "Ciencia ficción" -> 878
                "Película de TV" -> 10770
                "Suspenso" -> 53
                "Bélica" -> 10752
                "Western" -> 37
                else -> 0
            }

            if (genreId == 0) return

            CoroutineScope(Dispatchers.IO).launch {
                val allMovies = mutableListOf<Movie>()
                for (page in 1..5) {
                    try {
                        val response = apiService.getMoviesByGenre(apiKey, "es-MX", genreId, page).awaitResponse()
                        if (response.isSuccessful) {
                            val peliculas = response.body()?.results ?: emptyList()
                            val mapped = peliculas.map { movie ->
                                Movie(
                                    id = movie.id.toString(),
                                    title = "${movie.title} (${movie.release_date ?: "N/A"})",
                                    originalTitle = movie.original_title,
                                    imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                                    streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                    year = "50",
                                    countdownMinutes = 60,
                                    casTV = "50",
                                    createdAt = Timestamp.now()
                                )
                            }
                            allMovies.addAll(mapped)
                        }
                    } catch (e: Exception) {
                        Log.e("PeliculasApiFragment", "Fallo en género $genero página $page", e)
                    }
                }
                withContext(Dispatchers.Main) {
                    adapter.updateMovies(allMovies)
                }
            }
        }
    private fun cargarPeliculasNowPlaying() {
        CoroutineScope(Dispatchers.IO).launch {
            val allMovies = mutableListOf<Movie>()
            for (page in 1..6) {
                try {
                    val response = apiService.getNowPlayingMovies(apiKey, "es-MX", page).awaitResponse()
                    if (response.isSuccessful) {
                        val peliculas = response.body()?.results ?: emptyList()
                        val mapped = peliculas.map { movie ->
                            Movie(
                                id = movie.id.toString(),
                                title = "${movie.title} (${movie.release_date ?: "N/A"})",
                                originalTitle = movie.original_title,
                                imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                                streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                year = "50",
                                countdownMinutes = 60,
                                casTV = "50",
                                createdAt = Timestamp.now()
                            )
                        }
                        allMovies.addAll(mapped)
                    }
                } catch (e: Exception) {
                    Log.e("PeliculasApiFragment", "Fallo en Now Playing: ${e.message}", e)
                }
            }
            withContext(Dispatchers.Main) {
                adapter.updateMovies(allMovies)
            }
        }
    }

    // Función para calcular cuántos elementos caben según el ancho real del RecyclerView
    private fun calcularElementosPorFila(anchoRecyclerPx: Int): Int {
        val anchoTarjetaDp = 120
        val anchoTarjetaPx = (anchoTarjetaDp * resources.displayMetrics.density).toInt()
        return (anchoRecyclerPx / anchoTarjetaPx).coerceAtLeast(1)
    }
    override fun onResume() {
        super.onResume()

        // Espera a que el recyclerView esté ya medido antes de calcular
        recyclerView.post {
            val ancho = recyclerView.width
            if (ancho > 0) {
                val spanCount = calcularElementosPorFila(ancho)
                val currentLayoutManager = recyclerView.layoutManager as? GridLayoutManager
                if (currentLayoutManager == null || currentLayoutManager.spanCount != spanCount) {
                    recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
                }
            }
        }
    }


}
