package com.creativem.fulltv.tv

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.*
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.bumptech.glide.Glide
import com.creativem.fulltv.R
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.databinding.PlayerBinding
import com.google.firebase.database.FirebaseDatabase

class PlayerTv : AppCompatActivity() {

    private var reintentosContador = 0
    private val MAX_REINTENTOS = 3
    private var isOffline = false
    private var masterTvList: List<Modelo> = emptyList()
    private var currentChannelIndex = -1

    private var player: ExoPlayer? = null
    private var streamUrl: String = ""
    private var movieImageUrl: String = ""
    private lateinit var movieTitle: String
    private lateinit var binding: PlayerBinding
    private lateinit var adapter: TvMenuAdapter

    private val handler = Handler(Looper.getMainLooper())

    private val bufferUpdater = object : Runnable {
        override fun run() {
            if (player != null && binding.loadingIndicator.visibility == View.VISIBLE) {
                val percentage = player?.bufferedPercentage ?: 0
                val estimatedKb = (player?.bufferedPosition ?: 0) / 1024
                binding.loadingBufferText.text = "Búfer: $percentage% (${estimatedKb} KB)"
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = PlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        // Ocultar los controles directamente
        val controles = binding.reproductor.findViewById<View>(R.id.controles_reproductor)
        controles?.visibility = View.GONE
        binding.loadingIndicator.visibility = View.VISIBLE
        binding.loadingBufferText.text = "Iniciando señal..."

        binding.reproductor.keepScreenOn = true
        initializeRecyclerView()

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        intent?.let {
            streamUrl = it.getStringExtra("EXTRA_STREAM_URL") ?: ""
            movieTitle = it.getStringExtra("EXTRA_MOVIE_TITLE") ?: "TV en vivo"
            movieImageUrl = it.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""

            binding.loadingMovieTitle.text = movieTitle
            findViewById<TextView>(R.id.nombrePelicula).text = movieTitle
            Glide.with(this).load(movieImageUrl).placeholder(R.drawable.icono).into(findViewById<ImageView>(R.id.imagenPelicula))
        }

        // NUEVO: Tocar la pantalla abre de una vez el menú de canales
        binding.reproductor.setOnTouchListener { _, _ ->
            if (binding.recyclerViewTv.visibility != View.VISIBLE) {
                mostarpélis()
            }
            true
        }

        loadTvCollection()
        initializePlayer()

        // Configurar acción táctil del botón X para cerrar el menú en móviles
        binding.btnCerrarMenuTv.setOnClickListener {
            ocultarMenuCompleto()
        }
    }
    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        if (streamUrl.isEmpty()) return

        // 1. ORIGEN DE DATOS (Más robusto)
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)") // User-agent más realista para evitar bloqueos
            .setConnectTimeoutMs(10000) // Reducido a 10s para detectar caídas más rápido
            .setReadTimeoutMs(10000)

        // 2. CONTROL DE BUFFER (Optimizado para TV y memoria)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBuffer: 15 seg (suficiente para evitar cortes cortos)
                30000, // maxBuffer: 30 seg (Evita saturar la RAM del TV después de horas)
                1500,  // bufferForPlayback: 1.5 seg (Arranca rápido al cambiar de canal)
                3000   // bufferAfterRebuffer: 3 seg (Arranca rápido tras una caída de internet)
            )
            .setPrioritizeTimeOverSizeThresholds(true) // ¡CLAVE! Prioriza el tiempo en vivo sobre el tamaño en memoria
            .build()

        // 3. SELECTOR DE PISTAS (Adaptativo para internet lento)
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSizeSd() // Opcional: Si el TV es muy lento, fuerza a calidad SD en redes malas
                    .setForceHighestSupportedBitrate(false)
            )
        }

        // 4. RENDERERS (Tolerancia a fallos de hardware del TV)
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true) // Si falla el decodificador del TV, intenta con otro por software

        // CONSTRUIR EXOPLAYER
        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().also { exoPlayer ->
                binding.reproductor.player = exoPlayer
                binding.reproductor.useController = false

                // 5. CONFIGURAR MEDIA ITEM PARA CANALES EN VIVO
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(streamUrl))
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setMaxPlaybackSpeed(1.02f) // Si se atrasa un poco, acelera el video imperceptiblemente para alcanzar el "En vivo"
                            .build()
                    )
                    .build()

                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.prepare()
                exoPlayer.addListener(playerListener)
                exoPlayer.playWhenReady = true
            }
    }
    private val playerListener = @UnstableApi object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    if (!isOffline) {
                        binding.loadingIndicator.visibility = View.VISIBLE
                        handler.post(bufferUpdater)
                    }
                }
                Player.STATE_READY -> {
                    binding.loadingIndicator.visibility = View.GONE
                    isOffline = false
                    reintentosContador = 0
                    handler.removeCallbacks(bufferUpdater)
                }
                Player.STATE_ENDED -> playNextChannel()
                Player.STATE_IDLE -> { }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (isOffline) return
            reintentosContador++

            Log.e("PlayerTv", "Error al cargar: Intento $reintentosContador de $MAX_REINTENTOS")

            if (reintentosContador <= MAX_REINTENTOS) {
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.loadingBufferText.text = "Señal débil, reintentando ($reintentosContador/$MAX_REINTENTOS)..."
                handler.postDelayed({ reiniciarReproductor() }, 3000)
            } else {
                isOffline = true
                binding.loadingMovieTitle.text = "CANAL FUERA DE LÍNEA"
                binding.loadingBufferText.text = "Vuelve pronto, estamos trabajando en ello."
                binding.loadingBufferText.setTextColor(ContextCompat.getColor(this@PlayerTv, R.color.redpersonalisado))

                handler.postDelayed({
                    playNextChannel()
                }, 4000)
            }
        }
    }

    private fun playNextChannel() {
        if (masterTvList.isNotEmpty()) {
            if (currentChannelIndex == -1) {
                currentChannelIndex = masterTvList.indexOfFirst { it.streamUrl == streamUrl }
            }

            currentChannelIndex = (currentChannelIndex + 1) % masterTvList.size
            val nextChannel = masterTvList[currentChannelIndex]

            streamUrl = nextChannel.streamUrl
            movieTitle = nextChannel.title
            movieImageUrl = nextChannel.imageUrl

            reintentosContador = 0
            isOffline = false

            binding.loadingIndicator.visibility = View.VISIBLE
            binding.loadingMovieTitle.text = movieTitle
            binding.loadingBufferText.text = "Buscando señal..."
            binding.loadingBufferText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            findViewById<TextView>(R.id.nombrePelicula).text = movieTitle

            reiniciarReproductor()
        } else {
            Toast.makeText(this, "No hay más canales disponibles", Toast.LENGTH_SHORT).show()
            finishPlayer()
        }
    }

    private fun loadTvCollection() {
        FirebaseDatabase.getInstance().getReference("tv").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                masterTvList = snapshot.children.mapNotNull { it.getValue(Modelo::class.java)?.copy(id = it.key ?: "") }
                currentChannelIndex = masterTvList.indexOfFirst { it.streamUrl == streamUrl }
                adapter.updateData(masterTvList)
            }
        }
    }

    private fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    private fun reiniciarReproductor() {
        player?.release()
        player = null
        initializePlayer()
    }



    private fun initializeRecyclerView() {
        adapter = TvMenuAdapter(this, mutableListOf()) { movie ->
            val intent = Intent(this, PlayerTv::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("EXTRA_STREAM_URL", movie.streamUrl)
                putExtra("EXTRA_MOVIE_TITLE", movie.title)
                putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
            }
            startActivity(intent)
        }

        binding.recyclerViewTv.adapter = adapter
        binding.recyclerViewTv.layoutManager = LinearLayoutManager(this)

        binding.recyclerViewTv.isFocusable = true
        binding.recyclerViewTv.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun mostarpélis() {
        if (binding.recyclerViewTv.visibility == View.VISIBLE) {
            ocultarMenuCompleto()
        } else {
            binding.recyclerViewTv.visibility = View.VISIBLE
            binding.btnCerrarMenuTv.visibility = View.VISIBLE // Muestra la "X" para móvil

            adapter.setCurrentPlayingChannel(streamUrl)
            val posicionActual = masterTvList.indexOfFirst { it.streamUrl == streamUrl }

            if (posicionActual != -1) {
                (binding.recyclerViewTv.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(posicionActual, 200)

                handler.postDelayed({
                    val viewHolder = binding.recyclerViewTv.findViewHolderForAdapterPosition(posicionActual)
                    viewHolder?.itemView?.requestFocus()
                }, 100)
            } else {
                binding.recyclerViewTv.requestFocus()
            }
        }
    }

    private fun ocultarMenuCompleto() {
        binding.recyclerViewTv.visibility = View.GONE
        binding.btnCerrarMenuTv.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            // --- NUEVA LÓGICA: CAMBIO DE CANAL ---
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                cambiarCanal(siguiente = true)
                true
            }

            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                cambiarCanal(siguiente = false)
                true
            }

            // --- BOTÓN MENÚ ---
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_INFO -> {
                mostarpélis()
                true
            }

            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                mostarpélis()
                true
            }

            // --- LÓGICA EXISTENTE ---
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (binding.recyclerViewTv.visibility == View.VISIBLE) {
                    super.onKeyDown(keyCode, event)
                } else {
                    mostarpélis()
                    true
                }
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                true
            }

            KeyEvent.KEYCODE_BACK -> {
                if (binding.recyclerViewTv.visibility == View.VISIBLE) {
                    ocultarMenuCompleto()
                    true
                } else {
                    finishPlayer()
                    true
                }
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (binding.recyclerViewTv.visibility != View.VISIBLE) {
                    mostarpélis()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                super.onKeyDown(keyCode, event)
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }
    private fun cambiarCanal(siguiente: Boolean) {
        if (masterTvList.isEmpty()) return

        // Cálculo del nuevo índice
        currentChannelIndex = if (siguiente) {
            (currentChannelIndex + 1) % masterTvList.size
        } else {
            if (currentChannelIndex <= 0) masterTvList.size - 1 else currentChannelIndex - 1
        }

        val canalElegido = masterTvList[currentChannelIndex]

        // Actualizar datos
        streamUrl = canalElegido.streamUrl
        movieTitle = canalElegido.title
        movieImageUrl = canalElegido.imageUrl

        // Actualizar UI
        binding.loadingMovieTitle.text = movieTitle
        findViewById<TextView>(R.id.nombrePelicula).text = movieTitle
        Glide.with(this).load(movieImageUrl).placeholder(R.drawable.icono).into(findViewById(R.id.imagenPelicula))

        // Si el menú está abierto, actualizar su selección
        adapter.setCurrentPlayingChannel(streamUrl)
        binding.recyclerViewTv.scrollToPosition(currentChannelIndex)

        // Reiniciar player
        reiniciarReproductor()
    }

    private fun finishPlayer() {
        player?.release()
        player = null
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        handler.removeCallbacksAndMessages(null)
    }

}