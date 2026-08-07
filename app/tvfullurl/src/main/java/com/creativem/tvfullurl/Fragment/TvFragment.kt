package com.creativem.tvfullurl.Fragment

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.recyclerview.widget.LinearLayoutManager
import com.creativem.cineflexurl.modelo.Movie
import com.creativem.cineflexurl.modelo.tv
import com.creativem.tvfullurl.ChannelAdapter
import com.creativem.tvfullurl.databinding.FragmentTvBinding
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class TvFragment : Fragment() {

    private lateinit var binding: FragmentTvBinding
    private lateinit var database: DatabaseReference
    private var player: ExoPlayer? = null

    private var channelList: MutableList<tv> = mutableListOf()
    private lateinit var channelAdapter: ChannelAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentTvBinding.inflate(inflater, container, false)

        database = FirebaseDatabase.getInstance(TvRepository.FIREBASE_DB_URL)
            .getReference(TvRepository.FIREBASE_PATH)

        // 🟢 1. INICIALIZAR EL ADAPTADOR AL INICIO (Sin opción de borrar canales)
        channelAdapter = ChannelAdapter(
            channelList,
            onDeleteClick = { /* Desactivado: Lista M3U remota */ },
            onItemClick = { channel -> playChannel(channel.streamUrl) }
        )

        binding.recyclerViewTV.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewTV.adapter = channelAdapter

        // 2. Configuración avanzada de ExoPlayer
        setupExoPlayer()

        // 3. Buscador en tiempo real
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().lowercase().trim()
                filterChannels(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. Botón: Guardar URL remota en Firebase y descargar canales
        binding.btnImportM3u.setOnClickListener {
            val urlIngresada = binding.urlEditText.text.toString().trim()
            if (urlIngresada.isNotEmpty() && urlIngresada.startsWith("http", ignoreCase = true)) {
                guardarUrlEnFirebase(urlIngresada)
            } else {
                Toast.makeText(context, "Por favor ingresa una URL válida (http/https)", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Botón: Limpiar URL de Firebase
        binding.btnClearAll.setOnClickListener {
            val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            builder.setTitle("¡Atención!")
            builder.setMessage("¿Estás seguro de que quieres borrar la URL remota de Firebase?")

            builder.setPositiveButton("Sí, borrar") { dialog, _ ->
                database.removeValue().addOnSuccessListener {
                    binding.urlEditText.text?.clear()
                    channelList.clear()
                    TvRepository.channelListMaster = emptyList()
                    if (::channelAdapter.isInitialized) {
                        channelAdapter.updateList(channelList)
                    }
                    player?.stop()
                    mostrarCargando(false)
                    Toast.makeText(context, "URL remota eliminada", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }

            builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
            builder.create().show()
        }

        // 6. Cargar la URL guardada en Firebase al abrir
        cargarUrlDesdeFirebase()

        return binding.root
    }

    @OptIn(UnstableApi::class)
    @SuppressLint("UnsafeOptInUsageError")
    private fun setupExoPlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val dataSourceFactory = DefaultDataSource.Factory(requireContext(), httpDataSourceFactory)

        val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setTsExtractorFlags(tsFlags)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        player = ExoPlayer.Builder(requireContext())
            .setMediaSourceFactory(mediaSourceFactory)
            .build().also { exoPlayer ->
                binding.playerView.player = exoPlayer
                binding.playerView.useController = false

                exoPlayer.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> mostrarCargando(true)
                            Player.STATE_READY -> mostrarCargando(false)
                            Player.STATE_ENDED, Player.STATE_IDLE -> mostrarCargando(false)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        mostrarCargando(false)
                        Toast.makeText(context, "Error al reproducir: ${error.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                })
            }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progressBar.visibility = if (cargando) View.VISIBLE else View.GONE
    }

    private fun playChannel(url: String) {
        if (url.isEmpty()) return

        mostrarCargando(true)

        val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(url))

        val isHls = url.contains(".m3u8", ignoreCase = true) ||
                url.contains("hls", ignoreCase = true) ||
                url.contains("/live/", ignoreCase = true)

        if (isHls) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        } else if (url.contains(".ts", ignoreCase = true)) {
            mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
        }

        player?.apply {
            setMediaItem(mediaItemBuilder.build())
            prepare()
            playWhenReady = true
        }
    }

    private fun cargarUrlDesdeFirebase() {
        database.get().addOnSuccessListener { snapshot ->
            val urlGuardada = snapshot.value?.toString()?.trim() ?: ""
            if (urlGuardada.isNotEmpty()) {
                binding.urlEditText.setText(urlGuardada)
                descargarYVerificarM3U(urlGuardada)
            } else {
                Toast.makeText(context, "No hay ninguna URL M3U guardada en Firebase", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Error al conectar con Firebase", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarUrlEnFirebase(nuevaUrl: String) {
        binding.btnImportM3u.isEnabled = false
        Toast.makeText(context, "Guardando URL en Firebase...", Toast.LENGTH_SHORT).show()

        database.setValue(nuevaUrl).addOnSuccessListener {
            Toast.makeText(context, "URL guardada exitosamente", Toast.LENGTH_SHORT).show()
            descargarYVerificarM3U(nuevaUrl)
        }.addOnFailureListener { error ->
            binding.btnImportM3u.isEnabled = true
            Toast.makeText(context, "Error al guardar en Firebase: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun descargarYVerificarM3U(urlString: String) {
        binding.btnImportM3u.isEnabled = false
        Toast.makeText(context, "Verificando canales e imágenes...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val canalesObtenidos = descargarM3uStream(urlString)

            withContext(Dispatchers.Main) {
                binding.btnImportM3u.isEnabled = true
                if (canalesObtenidos.isNotEmpty()) {
                    channelList.clear()
                    channelList.addAll(canalesObtenidos)

                    TvRepository.channelListMaster = canalesObtenidos.map { canal ->
                        Movie(
                            id = canal.id,
                            title = canal.title,
                            streamUrl = canal.streamUrl,
                            imageUrl = canal.imageUrl
                        )
                    }

                    val queryActual = binding.searchEditText.text.toString().lowercase().trim()
                    filterChannels(queryActual)

                    Toast.makeText(context, "Se cargaron ${canalesObtenidos.size} canales con sus logos", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "La URL no devolvió canales válidos", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun descargarM3uStream(urlString: String): List<tv> {
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
                    return parsearM3uBuffer(reader)
                } else {
                    return emptyList()
                }
            } catch (e: Exception) {
                return emptyList()
            } finally {
                connection?.disconnect()
            }
        }
        return emptyList()
    }

    private fun parsearM3uBuffer(reader: BufferedReader): List<tv> {
        val channels = mutableListOf<tv>()
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
                            tv(
                                id = "iptv_$idContador",
                                title = finalName,
                                imageUrl = currentLogo,
                                streamUrl = trimmed
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

    private fun filterChannels(query: String) {
        val filtered = if (query.isEmpty()) {
            channelList
        } else {
            channelList.filter { it.title.lowercase().contains(query) }
        }
        if (::channelAdapter.isInitialized) {
            channelAdapter.updateList(filtered)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}