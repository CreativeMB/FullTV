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
import com.creativem.fulltv.databinding.ActivityTvBinding
import com.creativem.fulltv.principal.AudioFocusHelper
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.principal.ViewUtils
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class TvActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTvBinding
    private lateinit var adapter: ChannelsAdapter
    private val channelList = mutableListOf<Modelo>()
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
        val columnas = ViewUtils.calcularColumnas(this)
        binding.rvCanales.layoutManager = GridLayoutManager(this, columnas)

        // QUITA el "val" antes de adapter. Así usas la variable de clase.
        adapter = ChannelsAdapter(
            channelList,
            onItemClick = { canal -> abrirReproductor(canal) },
            onFocusChange = { canal -> actualizarFondo(canal.imageUrl) }
        )

        binding.rvCanales.itemAnimator = null
        binding.rvCanales.adapter = adapter
    }

    private fun loadTvChannels() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = databaseRef.get().await()
                Log.d("TV_DEBUG", "Hijos encontrados en Firebase: ${snapshot.childrenCount}")

                val canales = mutableListOf<Modelo>()
                for (child in snapshot.children) {
                    // Firebase a veces necesita que la clase tenga constructor vacío
                    val canal = child.getValue(Modelo::class.java)
                    canal?.let {
                        it.isValid = true
                        // Asignamos el ID directamente
                        val canalConId = it.copy(id = child.key ?: "")
                        canales.add(canalConId)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (canales.isEmpty()) {
                        Log.d("TV_DEBUG", "La lista de canales está vacía en Firebase.")
                    } else {
                        // Limpiamos y recargamos la lista ORIGINAL que le pasamos al adaptador
                        channelList.clear()
                        channelList.addAll(canales.sortedBy { it.title })

                        // Si el adaptador ya fue creado, avisarle que los datos cambiaron
                        if (::adapter.isInitialized) {
                            adapter.notifyDataSetChanged()
                        } else {
                            // Si por alguna razón setupRecyclerView no se llamó aún, llamarlo aquí
                            setupRecyclerView()
                        }
                        Log.d("TV_DEBUG", "Canales cargados: ${channelList.size}")
                    }
                }
            } catch (e: Exception) {
                Log.e("TV_ACTIVITY", "Error crítico al cargar Firebase: ${e.message}")
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

    private fun abrirReproductor(modelo: Modelo) {
        val intent = Intent(this, PlayerTv::class.java).apply {
            putExtra("EXTRA_STREAM_URL", modelo.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", modelo.title)
            putExtra("EXTRA_MOVIE_IMAGE_URL", modelo.imageUrl)
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