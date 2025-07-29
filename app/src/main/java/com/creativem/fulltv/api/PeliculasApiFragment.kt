package com.creativem.fulltv.api

import android.content.Intent
import android.os.Build
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
import com.creativem.fulltv.principal.CastvHelper
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

    private var currentPage = 1
    private var isLoading = false
    private var currentCategory = "Populares"
    private val totalPagesToLoad = 10

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
            currentPage = 1
            currentCategory = seleccion
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
        recyclerView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        adapter = ApiAdapter(mutableListOf()) { movie ->
            val intent = Intent(requireContext(), ApiPeliculaActivity::class.java).apply {
                putExtra("EXTRA_STREAM_URL", movie.streamUrl)
                putExtra("EXTRA_MOVIE_TITLE", movie.title)
                putExtra("EXTRA_MOVIE_CASTV", movie.castv)
                putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
                putExtra("EXTRA_ORIGINAL_TITLE", movie.originalTitle)
            }
            startActivity(intent)
        }

        recyclerView.adapter = adapter
        setupApiService()
        setupScrollListener()
        CastvHelper.limpiarCacheGlide(requireContext()) // en Fragment
        cargarPeliculasPopulares()

        (activity as? Main)?.restaurarFondoAnimado()
    }

    private fun setupScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                if (!isLoading && currentPage < totalPagesToLoad && lastVisibleItemPosition + layoutManager.spanCount * 2 >= totalItemCount && totalItemCount > 0) {
                    currentPage++
                    when (currentCategory) {
                        "Populares" -> cargarPeliculasPopulares()
                        "Mejor valoradas" -> cargarPeliculasTopRated()
                        "En cartelera" -> cargarPeliculasNowPlaying()
                        else -> cargarPeliculasPorGenero(currentCategory)
                    }
                }
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

    private fun handleDataResponse(mapped: List<Movie>) {
        // ✅ Guarda el ítem enfocado antes de cargar nuevos datos
        val focusedView = recyclerView.findFocus()
        val focusedPosition = if (focusedView != null)
            recyclerView.getChildAdapterPosition(focusedView)
        else
            RecyclerView.NO_POSITION

        if (currentPage == 1) {
            adapter.updateMovies(mapped)

            // ✅ Enfocar primer ítem al cambiar de categoría o inicio
            recyclerView.post {
                recyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
            }
        } else {
            adapter.addMovies(mapped)

            // ✅ Restaurar el foco en el ítem que ya tenía foco
            if (focusedPosition != RecyclerView.NO_POSITION) {
                recyclerView.post {
                    recyclerView.findViewHolderForAdapterPosition(focusedPosition)?.itemView?.requestFocus()
                }
            }
        }

        isLoading = false
    }


    private fun cargarPeliculasPopulares() {
        if (isLoading) return; isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getPopularMovies(apiKey, "es-MX", currentPage).awaitResponse()
                if (response.isSuccessful) {
                    val mapped = response.body()?.results?.map { movie ->
                        Movie(id = movie.id.toString(),
                            title = "${movie.title} (${CastvHelper.formatearFecha(movie.release_date)})",
                            originalTitle = movie.original_title,
                            imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                            streamUrl = "streamUrl",
                            castv = 50,
                            createdAt = Timestamp.now()) } ?: emptyList()
                    withContext(Dispatchers.Main) { handleDataResponse(mapped) }
                } else { withContext(Dispatchers.Main) { isLoading = false } }
            } catch (e: Exception) { withContext(Dispatchers.Main) { isLoading = false }; Log.e("PeliculasApiFragment", "Fallo", e) }
        }
    }

    private fun cargarPeliculasTopRated() {
        if (isLoading) return; isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getTopRatedMovies(apiKey, "es-MX", currentPage).awaitResponse()
                if (response.isSuccessful) {
                    val mapped = response.body()?.results?.map { movie ->
                        Movie(id = movie.id.toString(),
                            title = "${movie.title} (${CastvHelper.formatearFecha(movie.release_date)})",
                            originalTitle = movie.original_title,
                            imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                            streamUrl = "streamUrl",
                            castv = 50,
                            createdAt = Timestamp.now()) } ?: emptyList()
                    withContext(Dispatchers.Main) { handleDataResponse(mapped) }
                } else { withContext(Dispatchers.Main) { isLoading = false } }
            } catch (e: Exception) { withContext(Dispatchers.Main) { isLoading = false }; Log.e("PeliculasApiFragment", "Fallo", e) }
        }
    }

    private fun cargarPeliculasPorGenero(genero: String) {
        if (isLoading) return; isLoading = true
        val genreId = when (genero) {
            "Acción" -> 28; "Aventura" -> 12; "Animación" -> 16; "Comedia" -> 35
            "Crimen" -> 80; "Documental" -> 99; "Drama" -> 18; "Familia" -> 10751
            "Fantasía" -> 14; "Historia" -> 36; "Terror" -> 27; "Música" -> 10402
            "Misterio" -> 9648; "Romance" -> 10749; "Ciencia ficción" -> 878
            "Película de TV" -> 10770; "Suspenso" -> 53; "Bélica" -> 10752; "Western" -> 37
            else -> 0
        }
        if (genreId == 0) { isLoading = false; return }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getMoviesByGenre(apiKey, "es-MX", genreId, currentPage).awaitResponse()
                if (response.isSuccessful) {
                    val mapped = response.body()?.results?.map { movie ->
                        Movie(id = movie.id.toString(),
                            title = "${movie.title} (${CastvHelper.formatearFecha(movie.release_date)})",
                            originalTitle = movie.original_title,
                            imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                            streamUrl = "streamUrl",
                            castv = 50,
                            createdAt = Timestamp.now()) } ?: emptyList()
                    withContext(Dispatchers.Main) { handleDataResponse(mapped) }
                } else { withContext(Dispatchers.Main) { isLoading = false } }
            } catch (e: Exception) { withContext(Dispatchers.Main) { isLoading = false }; Log.e("PeliculasApiFragment", "Fallo", e) }
        }
    }

    private fun cargarPeliculasNowPlaying() {
        if (isLoading) return; isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.getNowPlayingMovies(apiKey, "es-MX", currentPage).awaitResponse()
                if (response.isSuccessful) {
                    val mapped = response.body()?.results?.map { movie ->
                        Movie(id = movie.id.toString(),
                            title = "${movie.title} (${CastvHelper.formatearFecha(movie.release_date)})",
                            originalTitle = movie.original_title,
                            imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                            streamUrl = "streamUrl",
                            castv = 50,
                            createdAt = Timestamp.now()) } ?: emptyList()
                    withContext(Dispatchers.Main) { handleDataResponse(mapped) }
                } else { withContext(Dispatchers.Main) { isLoading = false } }
            } catch (e: Exception) { withContext(Dispatchers.Main) { isLoading = false }; Log.e("PeliculasApiFragment", "Fallo", e) }
        }
    }

    private fun calcularElementosPorFila(anchoRecyclerPx: Int): Int {
        if (!isAdded || anchoRecyclerPx <= 0) return 1
        val anchoTarjetaDp = 140
        val anchoTarjetaPx = (anchoTarjetaDp * resources.displayMetrics.density).toInt()
        return (anchoRecyclerPx / anchoTarjetaPx).coerceAtLeast(1)
    }

    override fun onResume() {
        super.onResume()
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
        if (layoutListener != null) {
            recyclerView.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        }
        layoutListener = null
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CastvHelper.solicitarAudioFocus(requireContext()) // más seguro
        }
    }

    override fun onStop() {
        super.onStop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CastvHelper.liberarAudioFocus()
        }
    }

}