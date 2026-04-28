package com.creativem.fulltv.api

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R

import com.creativem.fulltv.menu.MenuSuperiorAdapter
import com.creativem.fulltv.principal.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory


class PeliculasApiActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PeliculasApiAdapter
    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()
        // 1. Cargamos el layout directamente
        setContentView(R.layout.fragment_peliculasapi)

        // --- TODA TU LÓGICA DE onViewCreated COMIENZA AQUÍ ---

        val menuOpciones = listOf(
            "Populares", "Mejor valoradas", "En cartelera", "Acción", "Aventura", "Animación",
            "Comedia", "Crimen", "Documental", "Drama", "Familia", "Fantasía",
            "Historia", "Terror", "Música", "Misterio", "Romance", "Ciencia ficción",
            "Película de TV", "Suspenso", "Bélica", "Western"
        )

        // En Activity usamos findViewById directamente
        val menuRecycler = findViewById<RecyclerView>(R.id.menu_horizontal)

        // Cambiamos requireContext() por 'this'
        menuRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        menuRecycler.adapter = MenuSuperiorAdapter(menuOpciones) { seleccion ->
            when (seleccion) {
                "Populares" -> cargarPeliculasPopulares()
                "Mejor valoradas" -> cargarPeliculasTopRated()
                "En cartelera" -> cargarPeliculasNowPlaying()
                else -> cargarPeliculasPorGenero(seleccion)
            }
        }

        recyclerView = findViewById(R.id.recycler_populares)

        // Mantenemos tu layoutListener tal cual
        layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            // En Activity no hace falta 'isAdded', siempre está añadida si está abierta
            val spanCount = calcularElementosPorFila(recyclerView.width)
            val currentLayoutManager = recyclerView.layoutManager as? GridLayoutManager
            if (currentLayoutManager == null || currentLayoutManager.spanCount != spanCount) {
                recyclerView.layoutManager = GridLayoutManager(this, spanCount)
            }
        }

        recyclerView.viewTreeObserver.addOnGlobalLayoutListener {
            val spanCount = calcularElementosPorFila(recyclerView.width)
            if (recyclerView.layoutManager !is GridLayoutManager ||
                (recyclerView.layoutManager as GridLayoutManager).spanCount != spanCount
            ) {
                recyclerView.layoutManager = GridLayoutManager(this, spanCount)
            }
        }

        // Mantenemos el Intent con todos tus Extras
        adapter = PeliculasApiAdapter(mutableListOf()) { movie ->
            val intent = Intent(this, ApiPeliculaActivity::class.java).apply {
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

        // Llamamos a tus funciones de API
        setupApiService()
        cargarPeliculasPopulares()

        // 🔄 Como ya no estás en Main, si quieres fondo animado aquí,
        // deberías llamar a una función de animación propia de esta Activity.
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
                                createdAt = System.currentTimeMillis()
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
                                createdAt = System.currentTimeMillis()
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
                                createdAt = System.currentTimeMillis()
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
                                createdAt = System.currentTimeMillis()
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
        // 1. Quitamos 'isAdded'. Solo verificamos que el ancho sea mayor a 0 para evitar errores.
        if (anchoRecyclerPx <= 0) return 2 // Retornamos 2 como mínimo por defecto

        // 2. Definimos el ancho que queremos para cada póster en DP
        val anchoTarjetaDp = 120

        // 3. Convertimos DP a Píxeles según la densidad de la pantalla actual
        val anchoTarjetaPx = (anchoTarjetaDp * resources.displayMetrics.density).toInt()

        // 4. Calculamos cuántas tarjetas caben en el ancho total del RecyclerView
        // Usamos .coerceAtLeast(1) para asegurar que al menos se vea 1 columna
        val columnas = anchoRecyclerPx / anchoTarjetaPx

        return columnas.coerceAtLeast(1)
    }

    override fun onResume() {
        super.onResume()

        // Usamos post para asegurar que el RecyclerView ya tenga dimensiones reales en pantalla
        recyclerView.post {
            val ancho = recyclerView.width
            if (ancho > 0) {
                val spanCount = calcularElementosPorFila(ancho)
                val currentLayoutManager = recyclerView.layoutManager as? GridLayoutManager

                // Verificamos si necesitamos cambiar el número de columnas
                if (currentLayoutManager == null || currentLayoutManager.spanCount != spanCount) {
                    // CAMBIO: Usamos 'this' porque estamos en una Activity
                    recyclerView.layoutManager = GridLayoutManager(this, spanCount)
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy() // En Activity llamamos a super.onDestroy()

        // Es crucial eliminar el listener para evitar crashes y fugas de memoria.
        if (layoutListener != null) {
            // Verificamos que el recyclerView no sea nulo antes de acceder a su observer
            recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        }

        // También es bueno limpiar la referencia.
        layoutListener = null
    }

}
