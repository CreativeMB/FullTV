package com.creativem.fulltv.tv

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.creativem.fulltv.databinding.ActivityTvBinding
import com.creativem.fulltv.peliculas.PeliculasActivity
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

class TvActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var binding: ActivityTvBinding
    private lateinit var adapter: ChannelsAdapter
    private var lastFocusedChannelId: String? = null

    private var channelsShowing: List<Modelo> = mutableListOf()
    private val channelListMaster = mutableListOf<Modelo>()
    private val databaseRef = FirebaseDatabase.getInstance().getReference("tv")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("TV_PREFS", Context.MODE_PRIVATE)

        // 🔥 SOLUCIÓN 1: Evitar que el teclado se abra automáticamente al entrar
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        // 🔥 SOLUCIÓN 2: Quitarle el foco inicial al buscador para que no se quede ahí pegado
        binding.searchEditText.clearFocus()


        // Configuración para TV
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setupRecyclerView()
        setupBuscador()
        loadTvChannels()

    }

    private fun setupRecyclerView() {
        val columnas = ViewUtils.calcularColumnas(this)
        binding.recyclerViewTV.layoutManager = GridLayoutManager(this, columnas)

        adapter = ChannelsAdapter(
            mutableListOf(),
            onItemClick = { canal -> abrirReproductor(canal) },
            onFocusChange = { canal ->
                // GUARDAMOS EL ID DEL CANAL ENFOCADO
                lastFocusedChannelId = canal.id
                actualizarFondo(canal.imageUrl)
            },
            onLongClick = { canal -> toggleFavorite(canal) }
        )
        binding.recyclerViewTV.adapter = adapter

        // Evita que el foco se pierda al limpiar el buscador
        binding.recyclerViewTV.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding.searchEditText.text.isNotEmpty()) {
                // No limpiamos aquí para no perder la navegación
            }
        }
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

        prefs.edit().putStringSet("fav_ids", currentFavs).apply()

        // Al marcar favorito, refrescamos respetando lo que esté escrito en el buscador
        filterChannels(binding.searchEditText.text.toString())
    }

    private fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
    }

    private fun filterChannels(query: String) {
        val normalizedQuery = query.flatten()
        val favIds = getFavoriteIds()

        val baseList = if (normalizedQuery.isEmpty()) {
            channelListMaster
        } else {
            channelListMaster.filter { it.title.flatten().contains(normalizedQuery) }
        }.distinctBy { it.id }

        val sortedList = baseList.sortedWith(
            compareByDescending<Modelo> { favIds.contains(it.id) }
                .thenBy { it.title }
        )

        if (::adapter.isInitialized) {
            val buscadorTeníaFoco = binding.searchEditText.hasFocus()

            adapter.updateList(sortedList, favIds)

            binding.recyclerViewTV.post {
                if (adapter.itemCount > 0) {
                    if (buscadorTeníaFoco) {
                        binding.searchEditText.requestFocus()
                    } else {
                        // --- LÓGICA DE RESTAURACIÓN DE FOCO ---
                        // Buscamos la posición del último ID que tuvo foco en la nueva lista
                        val positionToFocus = sortedList.indexOfFirst { it.id == lastFocusedChannelId }

                        if (positionToFocus != -1) {
                            // Si el elemento existe en la nueva lista, vamos a él
                            val view = binding.recyclerViewTV.layoutManager?.findViewByPosition(positionToFocus)
                            if (view != null) {
                                view.requestFocus()
                            } else {
                                // Si la vista no está creada (fuera de pantalla), hacemos scroll y enfocamos
                                binding.recyclerViewTV.scrollToPosition(positionToFocus)
                                binding.recyclerViewTV.postDelayed({
                                    binding.recyclerViewTV.layoutManager?.findViewByPosition(positionToFocus)?.requestFocus()
                                }, 50)
                            }
                        } else {
                            // Si el canal que tenía foco ya no está en la lista (por el filtro), enfocamos el primero
                            binding.recyclerViewTV.layoutManager?.findViewByPosition(0)?.requestFocus()
                        }
                    }
                }
            }
        }
    }


    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val layoutManager = binding.recyclerViewTV.layoutManager as? GridLayoutManager

        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (binding.searchEditText.hasFocus()) {
                // USAMOS channelsShowing EN LUGAR DE currentList
                val positionToFocus = channelsShowing.indexOfFirst { it.id == lastFocusedChannelId }

                if (positionToFocus != -1) {
                    binding.recyclerViewTV.requestFocus()
                    // Hacemos scroll y enfocamos
                    layoutManager?.scrollToPositionWithOffset(positionToFocus, 100)
                    binding.recyclerViewTV.postDelayed({
                        layoutManager?.findViewByPosition(positionToFocus)?.requestFocus()
                    }, 100)
                    return true
                }
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Tu lógica de regreso
            val intent = Intent(this, PeliculasActivity::class.java)
            startActivity(intent)
            finish()
            return true
        }

        return super.onKeyDown(keyCode, event)
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

        // NUEVO: Al presionar buscar en el teclado de la TV
        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Ocultar teclado
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)

                // Forzar el foco a la lista
                binding.recyclerViewTV.requestFocus()
                true
            } else {
                false
            }
        }
    }

    private fun String.flatten(): String {
        val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
        return temp.replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "").lowercase().trim()
    }

    private fun loadTvChannels() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = databaseRef.get().await()
                val canalesTemp = mutableListOf<Modelo>()

                for (child in snapshot.children) {
                    val canal = child.getValue(Modelo::class.java)
                    canal?.let {
                        val canalConId = it.copy(id = child.key ?: "")
                        canalesTemp.add(canalConId)
                    }
                }

                withContext(Dispatchers.Main) {
                    channelListMaster.clear()
                    channelListMaster.addAll(canalesTemp.distinctBy { it.id })

                    // Carga inicial respetando el texto que tenga el buscador
                    filterChannels(binding.searchEditText.text.toString())
                }
            } catch (e: Exception) {
                Log.e("TV_ACTIVITY", "Error: ${e.message}")
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