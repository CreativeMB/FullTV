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
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.creativem.fulltv.databinding.ActivityTvBinding
import com.creativem.fulltv.principal.AudioFocusHelper
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.principal.ViewUtils
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.Normalizer

class TvActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var binding: ActivityTvBinding
    private lateinit var adapter: ChannelsAdapter
    private var lastFocusedChannelId: String? = null

    private var channelsShowing: List<Modelo> = mutableListOf()
    private val channelListMaster = mutableListOf<Modelo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTvBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("TV_PREFS", Context.MODE_PRIVATE)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        binding.searchEditText.clearFocus()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setupRecyclerView()
        setupBuscador()

        // 🟢 Carga la lista de canales desde Firebase
        loadTvChannels()

        onBackPressedDispatcher.addCallback(this) {
            CastvHelper.regresarAPeliculas(this@TvActivity)
        }
    }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(CastvHelper.ajustarContexto(newBase))
    }

    override fun getResources(): android.content.res.Resources {
        val res = super.getResources()
        CastvHelper.ajustarRecursos(res, this)
        return res
    }

    private fun setupRecyclerView() {
        val columnas = ViewUtils.calcularColumnas(this)
        binding.recyclerViewTV.layoutManager = GridLayoutManager(this, columnas)

        adapter = ChannelsAdapter(
            mutableListOf(),
            onItemClick = { canal -> abrirReproductor(canal) },
            onFocusChange = { canal ->
                lastFocusedChannelId = canal.id
                actualizarFondo(canal.imageUrl)
            },
            onLongClick = { canal -> toggleFavorite(canal) }
        )
        binding.recyclerViewTV.adapter = adapter

        binding.recyclerViewTV.setOnFocusChangeListener { _, _ -> }
    }

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

        channelsShowing = sortedList

        if (::adapter.isInitialized) {
            val buscadorTeníaFoco = binding.searchEditText.hasFocus()

            adapter.updateList(sortedList, favIds)

            binding.recyclerViewTV.post {
                if (adapter.itemCount > 0) {
                    if (buscadorTeníaFoco) {
                        binding.searchEditText.requestFocus()
                    } else {
                        val positionToFocus = sortedList.indexOfFirst { it.id == lastFocusedChannelId }

                        if (positionToFocus != -1) {
                            val view = binding.recyclerViewTV.layoutManager?.findViewByPosition(positionToFocus)
                            if (view != null) {
                                view.requestFocus()
                            } else {
                                binding.recyclerViewTV.scrollToPosition(positionToFocus)
                                binding.recyclerViewTV.postDelayed({
                                    binding.recyclerViewTV.layoutManager?.findViewByPosition(positionToFocus)?.requestFocus()
                                }, 50)
                            }
                        } else {
                            binding.recyclerViewTV.requestFocus()
                            binding.recyclerViewTV.postDelayed({
                                binding.recyclerViewTV.layoutManager?.findViewByPosition(0)?.requestFocus()
                            }, 100)
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
                val positionToFocus = channelsShowing.indexOfFirst { it.id == lastFocusedChannelId }

                if (positionToFocus != -1) {
                    binding.recyclerViewTV.requestFocus()
                    layoutManager?.scrollToPositionWithOffset(positionToFocus, 100)
                    binding.recyclerViewTV.postDelayed({
                        layoutManager?.findViewByPosition(positionToFocus)?.requestFocus()
                    }, 100)
                    return true
                }
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            CastvHelper.regresarAPeliculas(this)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    private fun setupBuscador() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.searchEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(binding.searchEditText.windowToken, 0)

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

    // 🟢 Carga desde Firebase (Ruta exacta: tv/urliptv)
    private fun loadTvChannels() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Si ya están cargados en el repositorio compartido, los usamos directamente
                if (TvRepository.channelListMaster.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        channelListMaster.clear()
                        channelListMaster.addAll(TvRepository.channelListMaster)
                        filterChannels(binding.searchEditText.text.toString())
                    }
                    return@launch
                }

                // 2. Consulta exacta a Firebase usando la constante del repositorio ("tv/urliptv")
                val snapshot = FirebaseDatabase.getInstance(TvRepository.FIREBASE_DB_URL)
                    .getReference(TvRepository.FIREBASE_PATH)
                    .get().await()

                val m3uUrl = snapshot.value?.toString()?.trim()

                if (!m3uUrl.isNullOrEmpty()) {
                    Log.d("TV_ACTIVITY", "URL obtenida de Firebase (${TvRepository.FIREBASE_PATH}): $m3uUrl")

                    val canalesTemp = descargarM3uStream(m3uUrl)

                    withContext(Dispatchers.Main) {
                        channelListMaster.clear()
                        channelListMaster.addAll(canalesTemp)

                        // 🟢 GUARDAMOS EN LA VARIABLE GLOBAL DEL REPOSITORIO PARA PLAYERTV
                        TvRepository.channelListMaster = canalesTemp

                        if (channelListMaster.isEmpty()) {
                            Toast.makeText(this@TvActivity, "La lista IPTV en Firebase no contiene canales válidos", Toast.LENGTH_LONG).show()
                        } else {
                            Log.d("TV_ACTIVITY", "Canales cargados exitosamente: ${channelListMaster.size}")
                        }

                        filterChannels(binding.searchEditText.text.toString())
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Log.e("TV_ACTIVITY", "El nodo '${TvRepository.FIREBASE_PATH}' en Firebase está vacío")
                        Toast.makeText(this@TvActivity, "No se encontró la URL IPTV en Firebase", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("TV_ACTIVITY", "Error de conexión con Firebase: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TvActivity, "Error al conectar con Firebase", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun descargarM3uStream(urlString: String): List<Modelo> {
        var currentUrl = urlString.trim()
        var redirects = 0
        val maxRedirects = 5

        while (redirects < maxRedirects) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.requestMethod = "GET"
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connection.instanceFollowRedirects = true

                val status = connection.responseCode

                if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                    status == HttpURLConnection.HTTP_MOVED_PERM ||
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308) {

                    val newUrl = connection.getHeaderField("Location")
                    if (newUrl.isNullOrEmpty()) break
                    currentUrl = newUrl
                    redirects++
                    connection.disconnect()
                    continue
                }

                if (status == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8))
                    return parseM3uBuffer(reader)
                } else {
                    Log.e("TV_ACTIVITY", "Respuesta HTTP $status en la URL: $currentUrl")
                    return emptyList()
                }
            } catch (e: Exception) {
                Log.e("TV_ACTIVITY", "Error de red al conectar con: $currentUrl", e)
                return emptyList()
            } finally {
                connection?.disconnect()
            }
        }
        return emptyList()
    }

    private fun parseM3uBuffer(reader: BufferedReader): List<Modelo> {
        val channels = mutableListOf<Modelo>()
        var currentName = ""
        var currentLogo = ""
        var idContador = 0

        reader.useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@forEach

                if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                    val logoMatch = Regex("""tvg-logo="([^"]*)"""", RegexOption.IGNORE_CASE).find(trimmed)
                    currentLogo = logoMatch?.groupValues?.get(1)?.trim() ?: ""

                    val tvgNameMatch = Regex("""tvg-name="([^"]*)"""", RegexOption.IGNORE_CASE).find(trimmed)
                    val tvgName = tvgNameMatch?.groupValues?.get(1)?.trim()

                    val nameAfterComma = trimmed.substringAfterLast(",", "").trim()

                    currentName = when {
                        nameAfterComma.isNotEmpty() -> nameAfterComma
                        !tvgName.isNullOrEmpty() -> tvgName
                        else -> ""
                    }
                } else if (!trimmed.startsWith("#")) {
                    if (trimmed.contains("://") || trimmed.startsWith("rtmp", ignoreCase = true) || trimmed.startsWith("udp", ignoreCase = true)) {
                        val finalName = if (currentName.isNotEmpty()) currentName else "Canal ${channels.size + 1}"
                        channels.add(
                            Modelo(
                                id = "iptv_$idContador",
                                title = finalName,
                                streamUrl = trimmed,
                                imageUrl = currentLogo
                            )
                        )
                        idContador++
                    }
                    currentName = ""
                    currentLogo = ""
                }
            }
        }
        return channels
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

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val currentFocus = currentFocus
            val lm = binding.recyclerViewTV.layoutManager as? GridLayoutManager

            if (currentFocus != null && lm != null) {
                val esHijoDeGrid = isViewDescendantOf(currentFocus, binding.recyclerViewTV) || currentFocus == binding.recyclerViewTV
                val position = if (esHijoDeGrid) lm.getPosition(currentFocus) else androidx.recyclerview.widget.RecyclerView.NO_POSITION
                val columns = lm.spanCount
                val totalItems = lm.itemCount

                when (event.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        CastvHelper.regresarAPeliculas(this)
                        return true
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (binding.searchEditText.hasFocus()) {
                            val positionToFocus = channelListMaster.indexOfFirst { it.id == lastFocusedChannelId }
                            val finalPos = if (positionToFocus != -1) positionToFocus else 0

                            binding.recyclerViewTV.requestFocus()
                            lm.scrollToPositionWithOffset(finalPos, 100)
                            binding.recyclerViewTV.postDelayed({
                                lm.findViewByPosition(finalPos)?.requestFocus()
                            }, 100)
                            return true
                        }

                        if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION && totalItems > 0) {
                            val bottomRowStart = (totalItems - 1) / columns * columns
                            if (position >= bottomRowStart) {
                                return true
                            }
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION && position in 0 until columns) {
                            binding.searchEditText.requestFocus()
                            return true
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                            if (position % columns == 0) {
                                binding.searchEditText.requestFocus()
                                return true
                            }
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION && totalItems > 0) {
                            val isLastColumn = (position % columns == columns - 1) || (position == totalItems - 1)
                            if (isLastColumn) {
                                binding.searchEditText.requestFocus()
                                return true
                            }
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isViewDescendantOf(view: View, parent: ViewGroup): Boolean {
        var current = view.parent
        while (current != null) {
            if (current == parent) return true
            current = current.parent
        }
        return false
    }
}