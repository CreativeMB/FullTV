package com.creativem.fulltv.peliculasvalidas

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.creativem.fulltv.peliculas.MoviesAdapter
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.databinding.ActivityPeliculasValidasBinding
import com.creativem.fulltv.principal.Movie
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PeliculasValidasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPeliculasValidasBinding
    private lateinit var movieAdapter: MoviesAdapter
    private val movieList = mutableListOf<Movie>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPeliculasValidasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuración de pantalla para TV
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setupRecyclerView()
        loadValidatedMovies()
    }

    private fun setupRecyclerView() {
        // Calculamos columnas dinámicas para que se adapte a cualquier TV
        val columnas = calcularColumnas(this)
        binding.rvPelisValidas.layoutManager = GridLayoutManager(this, columnas)

        movieAdapter = MoviesAdapter(
            movieList,
            onItemClick = { movie -> irAlDetalle(movie) },
            onFocusChange = { movie -> actualizarFondo(movie.imageUrl) }
        )
        binding.rvPelisValidas.adapter = movieAdapter
    }

    private fun loadValidatedMovies() {
        CoroutineScope(Dispatchers.Main).launch {
            // 1. Esperamos a que el proceso de validación global termine
            if (!Validacioneslista.yaCargado()) {
                Toast.makeText(this@PeliculasValidasActivity, "Verificando enlaces...", Toast.LENGTH_SHORT).show()
                Validacioneslista.esperarCarga()
            }

            // 2. Obtenemos SOLO las películas marcadas como válidas
            val validadas = Validacioneslista.obtenerPeliculasValidas()

            withContext(Dispatchers.Main) {
                if (validadas.isNotEmpty()) {
                    movieList.clear()
                    // Aseguramos que todas tengan el flag isValid en true para que el adapter pinte el check
                    validadas.forEach { it.isValid = true }
                    movieList.addAll(validadas.sortedByDescending { it.createdAt })
                    movieAdapter.notifyDataSetChanged()
                } else {
                    Toast.makeText(
                        this@PeliculasValidasActivity,
                        "No hay películas disponibles actualmente",
                        Toast.LENGTH_LONG
                    ).show()
                    finish() // Cerramos si no hay nada que mostrar
                }
            }
        }
    }

    private fun actualizarFondo(url: String?) {
        if (!url.isNullOrEmpty()) {
            Glide.with(this)
                .load(url)
                .centerCrop()
                .into(binding.imgFondo)
        }
    }

    private fun irAlDetalle(movie: Movie) {
        val intent = Intent(this, ApiPeliculaActivity::class.java).apply {
            putExtra("EXTRA_STREAM_URL", movie.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", movie.title)
            putExtra("EXTRA_MOVIE_CASTV", movie.castv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
            putExtra("EXTRA_ORIGINAL_TITLE", movie.originalTitle)
            putExtra("EXTRA_COUNTDOWN", movie.countdownMinutes)
            putExtra("EXTRA_CREATED_AT", movie.createdAt / 1000)
            putExtra("EXTRA_IS_VALID", true)
        }
        startActivity(intent)
    }

    private fun calcularColumnas(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        return (dpWidth / 180).toInt().coerceAtLeast(2)
    }
}