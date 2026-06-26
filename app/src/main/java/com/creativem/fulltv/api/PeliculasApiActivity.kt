package com.creativem.fulltv.api

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.creativem.fulltv.R
import com.creativem.fulltv.menu.MenuSuperiorAdapter
import com.creativem.fulltv.peliculas.PeliculasActivity
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.principal.ViewUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.jvm.java

class PeliculasApiActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PeliculasApiAdapter
    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        supportActionBar?.hide()
        setContentView(R.layout.fragment_peliculasapi)

        setupApiService()
        setupMenu()
        setupRecyclerView()

        cargarCategoria("Populares")
        onBackPressedDispatcher.addCallback(this) {
            CastvHelper.regresarAPeliculas(this@PeliculasApiActivity)
        }
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

    private fun setupMenu() {
        val menuOpciones = listOf(
            "Populares", "Mejor valoradas", "En cartelera", "Acción", "Aventura", "Animación",
            "Comedia", "Crimen", "Documental", "Drama", "Familia", "Fantasía",
            "Historia", "Terror", "Música", "Misterio", "Romance", "Ciencia ficción",
            "Película de TV", "Suspenso", "Bélica", "Western"
        )

        val menuRecycler = findViewById<RecyclerView>(R.id.menu_horizontal)
        menuRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        menuRecycler.adapter = MenuSuperiorAdapter(menuOpciones) { seleccion ->
            cargarCategoria(seleccion)
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recycler_populares)

        val columnas = ViewUtils.calcularColumnas(this)
        recyclerView.layoutManager = GridLayoutManager(this, columnas)

        recyclerView.setHasFixedSize(true)
        recyclerView.itemAnimator = null

        adapter = PeliculasApiAdapter(mutableListOf()) { movie ->
            val intent = Intent(this, ApiPeliculaActivity::class.java).apply {
                putExtra("EXTRA_STREAM_URL", movie.streamUrl)
                putExtra("EXTRA_MOVIE_TITLE", movie.title)
                putExtra("EXTRA_MOVIE_CASTV", movie.castv)
                putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
                putExtra("EXTRA_ORIGINAL_TITLE", movie.originalTitle)
                putExtra("EXTRA_COUNTDOWN", movie.countdownMinutes)
                // 🟢 SOLUCIÓN: Pasamos también la fecha de creación y el modelo completo serializado
                putExtra("EXTRA_CREATED_AT", movie.createdAt)
                putExtra("EXTRA_MOVIE_DATA", movie)
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        recyclerView.adapter = adapter
    }

    private fun cargarCategoria(categoria: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val allModelos = mutableListOf<Modelo>()

            try {
                for (page in 1..5) {
                    val response = obtenerLlamadaApi(categoria, page).awaitResponse()

                    if (response.isSuccessful) {
                        val mapped = response.body()?.results?.map { movie ->
                            Modelo(
                                id = movie.id.toString(),
                                title = movie.title,
                                originalTitle = movie.original_title,
                                imageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}",
                                streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                castv = 10,
                                countdownMinutes = 0,
                                createdAt = 0L,
                                releaseDate = movie.release_date ?: ""
                            )
                        } ?: emptyList()
                        allModelos.addAll(mapped)
                    }
                }

                withContext(Dispatchers.Main) {
                    adapter.updateMovies(allModelos)
                    recyclerView.scrollToPosition(0)
                }

            } catch (e: Exception) {
                Log.e("API_ERROR", "Error cargando $categoria", e)
            }
        }
    }

    private fun obtenerLlamadaApi(categoria: String, page: Int): Call<MovieResponse> {
        return when (categoria) {
            "Populares" -> apiService.getPopularMovies(apiKey, "es-MX", page)
            "Mejor valoradas" -> apiService.getTopRatedMovies(apiKey, "es-MX", page)
            "En cartelera" -> apiService.getNowPlayingMovies(apiKey, "es-MX", page)
            else -> {
                val genreId = obtenerIdGenero(categoria)
                apiService.getMoviesByGenre(apiKey, "es-MX", genreId, page)
            }
        }
    }

    private fun obtenerIdGenero(genero: String): Int {
        return when (genero) {
            "Acción" -> 28; "Aventura" -> 12; "Animación" -> 16
            "Comedia" -> 35; "Crimen" -> 80; "Documental" -> 99
            "Drama" -> 18; "Familia" -> 10751; "Fantasía" -> 14
            "Historia" -> 36; "Terror" -> 27; "Música" -> 10402
            "Misterio" -> 9648; "Romance" -> 10749; "Ciencia ficción" -> 878
            "Película de TV" -> 10770; "Suspenso" -> 53; "Bélica" -> 10752
            "Western" -> 37; else -> 28
        }
    }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            CastvHelper.regresarAPeliculas(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}