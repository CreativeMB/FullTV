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
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.principal.ViewUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PeliculasValidasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPeliculasValidasBinding
    private lateinit var movieAdapter: MoviesAdapter
    private val modeloList = mutableListOf<Modelo>()

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
        // Usamos la función centralizada
        val columnas = ViewUtils.calcularColumnas(this)

        binding.rvPelisValidas.apply {
            layoutManager = GridLayoutManager(this@PeliculasValidasActivity, columnas)

            // Evita que el RecyclerView haga una animación de "parpadeo" al cargar
            itemAnimator = null

            adapter = MoviesAdapter(
                modeloList,
                onItemClick = { movie -> irAlDetalle(movie) },
                onFocusChange = { movie -> actualizarFondo(movie.imageUrl) }
            )
        }
    }

    private fun loadValidatedMovies() {
        CoroutineScope(Dispatchers.IO).launch { // Cambiamos a IO para no bloquear la UI en la espera
            // 1. Esperamos a que el proceso de validación global termine
            if (!Validacioneslista.yaCargado()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PeliculasValidasActivity, "Verificando enlaces...", Toast.LENGTH_SHORT).show()
                }
                Validacioneslista.esperarCarga()
            }

            // 2. Obtenemos SOLO las películas marcadas como válidas
            val validadas = Validacioneslista.obtenerPeliculasValidas()

            withContext(Dispatchers.Main) {
                if (validadas.isNotEmpty()) {
                    modeloList.clear()
                    validadas.forEach { it.isValid = true }
                    modeloList.addAll(validadas.sortedByDescending { it.createdAt })

                    // 🟢 SOLUCIÓN AL CRASH:
                    // Verificamos si el adapter ya fue creado antes de usarlo
                    if (::movieAdapter.isInitialized) {
                        movieAdapter.notifyDataSetChanged()
                    } else {
                        // Si por alguna razón la corrutina ganó y el adapter no existe, lo creamos
                        setupRecyclerView()
                    }

                } else {
                    Toast.makeText(
                        this@PeliculasValidasActivity,
                        "No hay películas disponibles actualmente",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
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

    private fun irAlDetalle(modelo: Modelo) {
        val intent = Intent(this, ApiPeliculaActivity::class.java).apply {
            putExtra("EXTRA_STREAM_URL", modelo.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", modelo.title)
            putExtra("EXTRA_MOVIE_CASTV", modelo.castv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", modelo.imageUrl)
            putExtra("EXTRA_ORIGINAL_TITLE", modelo.originalTitle)
            putExtra("EXTRA_COUNTDOWN", modelo.countdownMinutes)
            putExtra("EXTRA_CREATED_AT", modelo.createdAt / 1000)
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