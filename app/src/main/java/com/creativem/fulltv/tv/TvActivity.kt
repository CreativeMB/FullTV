package com.creativem.fulltv.tv

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.creativem.fulltv.peliculas.MoviesAdapter
import com.creativem.fulltv.databinding.ActivityTvBinding
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.principal.AudioFocusHelper
import com.creativem.fulltv.principal.Movie
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTvBinding
    private lateinit var adapter: MoviesAdapter
    private val channelList = mutableListOf<Movie>()
    private val databaseRef = FirebaseDatabase.getInstance().getReference("tv")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTvBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configuración para TV
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setupRecyclerView()
        loadTvChannels()
    }

    private fun setupRecyclerView() {
        val columnas = calcularColumnas(this)
        binding.rvCanales.layoutManager = GridLayoutManager(this, columnas)

        // Usamos el mismo MoviesAdapter para mantener la estética
        adapter = MoviesAdapter(
            channelList,
            onItemClick = { canal -> abrirReproductor(canal) },
            onFocusChange = { canal -> actualizarFondo(canal.imageUrl) }
        )
        binding.rvCanales.adapter = adapter
    }

    private fun loadTvChannels() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = databaseRef.get().await()
                val canales = mutableListOf<Movie>()

                for (child in snapshot.children) {
                    val canal = child.getValue(Movie::class.java)
                    canal?.let {
                        // 🟢 FORZAMOS LA ETIQUETA GRATIS AQUÍ
                        // Al ser canales de TV, marcamos que siempre son válidos/gratis
                        it.isValid = true

                        canales.add(it.copy(id = child.key ?: ""))
                    }
                }

                withContext(Dispatchers.Main) {
                    channelList.clear()
                    // Ordenar por nombre o fecha si lo prefieres
                    channelList.addAll(canales.sortedBy { it.title })
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e("TV_ACTIVITY", "Error cargando canales: ${e.message}")
            }
        }
    }

    private fun actualizarFondo(url: String?) {
        if (!url.isNullOrEmpty()) {
            Glide.with(this)
                .load(url)
                .centerCrop()
                .into(binding.imgFondoTv)
        }
    }

    private fun abrirReproductor(movie: Movie) {
        val intent = Intent(this, PlayerTv::class.java).apply {
            putExtra("EXTRA_STREAM_URL", movie.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", movie.title)
            putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
            putExtra("EXTRA_IS_LIVE", true) // Indica que es un canal de TV
        }
        startActivity(intent)
    }

    private fun calcularColumnas(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val dpWidth = displayMetrics.widthPixels / displayMetrics.density
        return (dpWidth / 180).toInt().coerceAtLeast(2)
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusHelper.requestAudioFocus(this)
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusHelper.abandonAudioFocus()
        }
    }
}