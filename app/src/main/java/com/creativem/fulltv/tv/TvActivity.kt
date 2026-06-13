package com.creativem.fulltv.tv

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import java.text.Normalizer
import android.content.SharedPreferences
import android.widget.Toast
class TvActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var binding: ActivityTvBinding
    private lateinit var adapter: ChannelsAdapter

    // SOLUCIÓN: Separamos los datos puros de Firebase de los datos que se muestran filtrados
    private val channelListMaster = mutableListOf<Modelo>() // Lista original/maestra
    private val channelList = mutableListOf<Modelo>()       // Lista que usa el Adapter

    private val databaseRef = FirebaseDatabase.getInstance().getReference("tv")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("TV_PREFS", Context.MODE_PRIVATE)
        // Configuración para TV
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setupRecyclerView()
        setupBuscador() // <-- Inicializamos el buscador robusto
        loadTvChannels()
    }

    private fun setupRecyclerView() {
        val columnas = ViewUtils.calcularColumnas(this)
        binding.recyclerViewTV.layoutManager = GridLayoutManager(this, columnas)

        adapter = ChannelsAdapter(
            mutableListOf(),
            onItemClick = { canal -> abrirReproductor(canal) },
            onFocusChange = { canal -> actualizarFondo(canal.imageUrl) },
            onLongClick = { canal -> toggleFavorite(canal) } // NUEVO
        )

        binding.recyclerViewTV.adapter = adapter
    }

    // --- LÓGICA DE FAVORITOS ---
    private fun toggleFavorite(canal: Modelo) {
        val currentFavs = getFavoriteIds().toMutableSet()

        if (currentFavs.contains(canal.id)) {
            currentFavs.remove(canal.id)
            Toast.makeText(this, "${canal.title} quitado de favoritos", Toast.LENGTH_SHORT).show()
        } else {
            currentFavs.add(canal.id)
            Toast.makeText(this, "${canal.title} añadido a favoritos", Toast.LENGTH_SHORT).show()
        }

        // Guardar en cache persistente
        prefs.edit().putStringSet("fav_ids", currentFavs).apply()

        // Re-filtrar y Re-ordenar la lista inmediatamente
        filterChannels(binding.searchEditText.text.toString())
    }

    private fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
    }

    private fun filterChannels(query: String) {
        val normalizedQuery = query.flatten()
        val favIds = getFavoriteIds()

        // 1. Filtrar por búsqueda
        val filtered = if (normalizedQuery.isEmpty()) {
            channelListMaster
        } else {
            channelListMaster.filter { it.title.flatten().contains(normalizedQuery) }
        }

        // 2. ORDENAR: Favoritos primero, luego alfabético
        val sortedList = filtered.sortedWith(
            compareByDescending<Modelo> { favIds.contains(it.id) }
                .thenBy { it.title }
        )

        if (::adapter.isInitialized) {
            adapter.updateList(sortedList, favIds)
        }
    }

    // --- CONFIGURACIÓN DEL BUSCADOR ROBUSTO ---
    private fun setupBuscador() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
        // Función de extensión para "limpiar" el texto
    private fun String.flatten(): String {
        val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
        return temp.replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "").lowercase().trim()
    }

    private fun loadTvChannels() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = databaseRef.get().await()
                Log.d("TV_DEBUG", "Hijos encontrados en Firebase: ${snapshot.childrenCount}")

                val canales = mutableListOf<Modelo>()
                for (child in snapshot.children) {
                    val canal = child.getValue(Modelo::class.java)
                    canal?.let {
                        it.isValid = true
                        val canalConId = it.copy(id = child.key ?: "")
                        canales.add(canalConId)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (canales.isEmpty()) {
                        Log.d("TV_DEBUG", "La lista de canales está vacía en Firebase.")
                    } else {
                        // Guardamos todo ordenado en nuestra lista maestra estática
                        channelListMaster.clear()
                        channelListMaster.addAll(canales.sortedBy { it.title })

                        // Ejecutamos el filtro inicial por si el usuario ya escribió algo antes de cargar
                        filterChannels(binding.searchEditText.text.toString())

                        Log.d("TV_DEBUG", "Canales cargados en lista maestra: ${channelListMaster.size}")
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
            putExtra("EXTRA_IS_LIVE", true)
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