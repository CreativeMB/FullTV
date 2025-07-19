package com.creativem.fulltv.api

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R

import com.creativem.fulltv.menu.MenuSuperiorAdapter
import com.creativem.fulltv.principal.Main
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
    private lateinit var adapter: ApiAdapter
    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_peliculasapi, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuOpciones = listOf(
            "Populares", "Mejor valoradas", "En cartelera", "Acción", "Aventura", "Animación",
            "Comedia", "Crimen", "Documental", "Drama", "Familia", "Fantasía",
            "Historia", "Terror", "Música", "Misterio", "Romance", "Ciencia ficción",
            "Película de TV", "Suspenso", "Bélica", "Western"
        )

        val menuRecycler = view.findViewById<RecyclerView>(R.id.menu_horizontal)
        menuRecycler.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        menuRecycler.adapter = MenuSuperiorAdapter(menuOpciones) { seleccion ->
            when (seleccion) {
                "Populares" -> cargarPeliculasPopulares()
                "Mejor valoradas" -> cargarPeliculasTopRated()
                "En cartelera" -> cargarPeliculasNowPlaying()
                else -> cargarPeliculasPorGenero(seleccion)
            }
        }

        recyclerView = view.findViewById(R.id.recycler_populares)

        layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!isAdded) return@OnGlobalLayoutListener
            val spanCount = calcularElementosPorFila(recyclerView.width)
            val currentLayoutManager = recyclerView.layoutManager as? GridLayoutManager
            if (currentLayoutManager == null || currentLayoutManager.spanCount != spanCount) {
                recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
            }
        }

        recyclerView.viewTreeObserver.addOnGlobalLayoutListener {
            val spanCount = calcularElementosPorFila(recyclerView.width)
            if (recyclerView.layoutManager !is GridLayoutManager ||
                (recyclerView.layoutManager as GridLayoutManager).spanCount != spanCount
            ) {
                recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
            }
        }

        adapter = ApiAdapter(mutableListOf()) { movie ->

            val intent = Intent(requireContext(), ApiPeliculaActivity::class.java).apply {
                putExtra("EXTRA_STREAM_URL", movie.streamUrl)
                putExtra("EXTRA_MOVIE_TITLE", movie.title)
                putExtra("EXTRA_MOVIE_CASTV", movie.castv)
                putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
                putExtra("EXTRA_ORIGINAL_TITLE", movie.originalTitle)
                putExtra("EXTRA_COUNTDOWN", movie.countdownMinutes)
            }
            startActivity(intent)
        }

        recyclerView.adapter = adapter
        setupApiService()
        cargarPeliculasPopulares()

        // 🔄 Restaurar fondo animado al iniciar
        (activity as? Main)?.restaurarFondoAnimado()
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
                    val response =
                        apiService.getPopularMovies(apiKey, "es-MX", page).awaitResponse()
                    if (response.isSuccessful) {
                        val peliculas = response.body()?.results ?: emptyList()
                        val mapped = peliculas.map { movie ->
                            Movie(
                                id = movie.id.toString(),
                                title = "${movie.title} (${movie.release_date ?: "N/A"})",
                                originalTitle = movie.original_title,
                                imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                                streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                castv = 50,
                                countdownMinutes = 60,
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
                    val response =
                        apiService.getTopRatedMovies(apiKey, "es-MX", page).awaitResponse()
                    if (response.isSuccessful) {
                        val peliculas = response.body()?.results ?: emptyList()
                        val mapped = peliculas.map { movie ->
                            Movie(
                                id = movie.id.toString(),
                                title = "${movie.title} (${movie.release_date ?: "N/A"})",
                                originalTitle = movie.original_title,
                                imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                                streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                castv = 50,
                                countdownMinutes = 60,
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
                    val response =
                        apiService.getMoviesByGenre(apiKey, "es-MX", genreId, page).awaitResponse()
                    if (response.isSuccessful) {
                        val peliculas = response.body()?.results ?: emptyList()
                        val mapped = peliculas.map { movie ->
                            Movie(
                                id = movie.id.toString(),
                                title = "${movie.title} (${movie.release_date ?: "N/A"})",
                                originalTitle = movie.original_title,
                                imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                                streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                castv = 50,
                                countdownMinutes = 60,
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
                    val response =
                        apiService.getNowPlayingMovies(apiKey, "es-MX", page).awaitResponse()
                    if (response.isSuccessful) {
                        val peliculas = response.body()?.results ?: emptyList()
                        val mapped = peliculas.map { movie ->
                            Movie(
                                id = movie.id.toString(),
                                title = "${movie.title} (${movie.release_date ?: "N/A"})",
                                originalTitle = movie.original_title,
                                imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                                streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                castv = 50,
                                countdownMinutes = 60,
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
        if (!isAdded || anchoRecyclerPx <= 0) return 1
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
    override fun onDestroyView() {
        super.onDestroyView()
        // Es crucial eliminar el listener para evitar crashes y fugas de memoria.
        if (layoutListener != null) {
            recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        }
        // También es bueno limpiar la referencia.
        layoutListener = null
    }

}
