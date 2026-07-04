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
import com.creativem.fulltv.principal.CastvHelper
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

        binding.reproductor.setOnTouchListener { _, _ ->
            if (binding.recyclerViewTv.visibility != View.VISIBLE) {
                mostarpélis()
            }
            true
        }

        // Cargamos la lista en 2do plano de inmediato. Así cuando llames al menú ya estará lista.
        loadTvCollection()
        initializePlayer()

        binding.btnCerrarMenuTv.setOnClickListener {
            ocultarMenuCompleto()
        }
    }
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(CastvHelper.ajustarContexto(newBase))
    }

    override fun getResources(): android.content.res.Resources {
        val res = super.getResources()
        // 🟢 Pasamos 'this' (el contexto de la actividad) para autodetectar la pantalla
        CastvHelper.ajustarRecursos(res, this)
        return res
    }
    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        if (streamUrl.isEmpty()) return

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .setConnectTimeoutMs(10000)
            .setReadTimeoutMs(10000)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 30000, 1500, 3000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd().setForceHighestSupportedBitrate(false))
        }

        val renderersFactory = DefaultRenderersFactory(this).setEnableDecoderFallback(true)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().also { exoPlayer ->
                binding.reproductor.player = exoPlayer
                binding.reproductor.useController = false

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(streamUrl))
                    .setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.02f).build()
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

            if (reintentosContador <= MAX_REINTENTOS) {
                binding.loadingIndicator.visibility = View.VISIBLE
                binding.loadingBufferText.text = "Señal débil, reintentando ($reintentosContador/$MAX_REINTENTOS)..."
                handler.postDelayed({ reiniciarReproductor() }, 3000)
            } else {
                isOffline = true
                binding.loadingMovieTitle.text = "CANAL FUERA DE LÍNEA"
                binding.loadingBufferText.text = "Vuelve pronto, estamos trabajando en ello."
                binding.loadingBufferText.setTextColor(ContextCompat.getColor(this@PlayerTv, R.color.redpersonalisado))

                handler.postDelayed({ playNextChannel() }, 4000)
            }
        }
    }

    private fun playNextChannel() {
        if (masterTvList.isNotEmpty()) {
            cambiarCanal(siguiente = true)
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
                // ¡La lista ya queda cargada e invisible en memoria, lista para salir al instante!
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
        adapter = TvMenuAdapter(this, mutableListOf()) { canalElegido ->
            // 🔥 SOLUCIÓN CRÍTICA: Ya no abrimos una nueva ventana. Cambiamos el canal en directo.
            cambiarCanalDirecto(canalElegido)
        }

        binding.recyclerViewTv.adapter = adapter
        binding.recyclerViewTv.layoutManager = LinearLayoutManager(this)

        binding.recyclerViewTv.isFocusable = true
        binding.recyclerViewTv.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    // Nueva función para cambiar canales al hacer clic en el menú sin recargar toda la Activity
    private fun cambiarCanalDirecto(canal: Modelo) {
        currentChannelIndex = masterTvList.indexOfFirst { it.streamUrl == canal.streamUrl }
        streamUrl = canal.streamUrl
        movieTitle = canal.title
        movieImageUrl = canal.imageUrl

        binding.loadingIndicator.visibility = View.VISIBLE
        binding.loadingMovieTitle.text = movieTitle
        binding.loadingBufferText.text = "Buscando señal..."
        binding.loadingBufferText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        findViewById<TextView>(R.id.nombrePelicula).text = movieTitle
        Glide.with(this).load(movieImageUrl).placeholder(R.drawable.icono).into(findViewById<ImageView>(R.id.imagenPelicula))

        ocultarMenuCompleto()
        reiniciarReproductor()
    }

    private fun mostarpélis() {
        if (binding.recyclerViewTv.visibility == View.VISIBLE) {
            ocultarMenuCompleto()
        } else {
            binding.recyclerViewTv.visibility = View.VISIBLE
            binding.btnCerrarMenuTv.visibility = View.VISIBLE

            adapter.setCurrentPlayingChannel(streamUrl)
            val posicionActual = masterTvList.indexOfFirst { it.streamUrl == streamUrl }

            if (posicionActual != -1) {
                // 1. Movemos el scroll internamente a la posición
                binding.recyclerViewTv.scrollToPosition(posicionActual)

                // 🔥 SOLUCIÓN: Usar .post{} asegura que Android haya terminado de hacer "VISIBLE"
                // el RecyclerView antes de intentar encontrar la vista para darle foco.
                // Esto elimina el error de la "primera vez".
                binding.recyclerViewTv.post {
                    val viewHolder = binding.recyclerViewTv.findViewHolderForAdapterPosition(posicionActual)
                    if (viewHolder != null) {
                        viewHolder.itemView.requestFocus()
                    } else {
                        binding.recyclerViewTv.requestFocus()
                    }
                }
            } else {
                binding.recyclerViewTv.post { binding.recyclerViewTv.requestFocus() }
            }
        }
    }

    private fun ocultarMenuCompleto() {
        binding.recyclerViewTv.visibility = View.GONE
        binding.btnCerrarMenuTv.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                cambiarCanal(siguiente = true)
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                cambiarCanal(siguiente = false)
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                mostarpélis()
                true
            }
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
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun cambiarCanal(siguiente: Boolean) {
        if (masterTvList.isEmpty()) return

        currentChannelIndex = if (siguiente) {
            (currentChannelIndex + 1) % masterTvList.size
        } else {
            if (currentChannelIndex <= 0) masterTvList.size - 1 else currentChannelIndex - 1
        }

        val canalElegido = masterTvList[currentChannelIndex]

        streamUrl = canalElegido.streamUrl
        movieTitle = canalElegido.title
        movieImageUrl = canalElegido.imageUrl

        binding.loadingIndicator.visibility = View.VISIBLE
        binding.loadingMovieTitle.text = movieTitle
        binding.loadingBufferText.text = "Buscando señal..."
        binding.loadingBufferText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        findViewById<TextView>(R.id.nombrePelicula).text = movieTitle
        Glide.with(this).load(movieImageUrl).placeholder(R.drawable.icono).into(findViewById(R.id.imagenPelicula))

        if (binding.recyclerViewTv.visibility == View.VISIBLE) {
            adapter.setCurrentPlayingChannel(streamUrl)
            binding.recyclerViewTv.scrollToPosition(currentChannelIndex)
        }

        reiniciarReproductor()
    }
    private fun liberarReproductor() {
        player?.let {
            it.release()
            player = null
        }
    }
    private fun finishPlayer() {
        liberarReproductor()
        finish()
    }
    override fun onStop() {
        super.onStop()
        // Si el usuario presiona "Home" en el mando, liberamos los recursos aquí también
        liberarReproductor()
    }
    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        liberarReproductor()
        handler.removeCallbacksAndMessages(null)
    }

}