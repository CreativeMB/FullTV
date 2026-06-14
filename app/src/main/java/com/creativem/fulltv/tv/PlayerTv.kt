package com.creativem.fulltv.tv

import android.annotation.SuppressLint
import android.content.Context
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
import com.creativem.fulltv.principal.Reloj
import android.text.format.DateUtils
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultHttpDataSource
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
    private val updateInterval: Long = 1000
    private val hideControlsDelay: Long = 10000

    private lateinit var runnableActualizar: Runnable
    private val runnableOcultarControles = Runnable {
        binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.GONE
    }

    // ELIMINADOS: handlerMenu y ocultarMenuRunnable ya no existen para que no se cierre solo

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

        initializePlayerControls()
        loadTvCollection()
        initializePlayer()

        showControlsAndResetTimer()

        // NUEVO: Configurar acción táctil del botón X para cerrar el menú en móviles
        binding.btnCerrarMenuTv.setOnClickListener {
            ocultarMenuCompleto()
        }
    }

    private fun initializePlayerControls() {
        val textHora = binding.reproductor.findViewById<TextView>(R.id.textHora)
        val textfecha = binding.reproductor.findViewById<TextView>(R.id.textfecha)
        if (textHora != null && textfecha != null) {
            Reloj(textHora, textfecha).startClock()
        }

        binding.reproductor.findViewById<ImageButton>(R.id.lista_pelis).setOnClickListener { mostarpélis() }
        binding.reproductor.findViewById<ImageButton>(R.id.play_pause).setOnClickListener { togglePlayPause() }
        binding.reproductor.findViewById<ImageButton>(R.id.home).setOnClickListener { finishPlayer() }
        binding.reproductor.findViewById<ImageButton>(R.id.render).setOnClickListener { cycleAspectRatio() }

        runnableActualizar = Runnable { actualizarTiempo() }

        binding.reproductor.setOnTouchListener { _, _ ->
            showControlsAndResetTimer()
            true
        }
    }

    private fun showControlsAndResetTimer() {
        if (binding.recyclerViewTv.visibility == View.VISIBLE) {
            return
        }

        binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.VISIBLE
        actualizarTiempo()
        handler.removeCallbacks(runnableOcultarControles)
        handler.postDelayed(runnableOcultarControles, hideControlsDelay)
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
                    actualizarTiempo()
                    showControlsAndResetTimer()
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

    private fun actualizarTiempo() {
        if (player != null && player!!.isPlaying) {
            val pos = player?.currentPosition ?: 0
            val dur = player?.duration ?: 0
            val tvProgreso = binding.reproductor.findViewById<TextView>(R.id.tiemporeproducido)
            val tvTotal = binding.reproductor.findViewById<TextView>(R.id.tiempototal)
            val sbProgreso = binding.reproductor.findViewById<SeekBar>(R.id.progreso)

            tvProgreso?.text = DateUtils.formatElapsedTime(pos / 1000)
            tvTotal?.text = DateUtils.formatElapsedTime(dur / 1000)
            if (dur > 0) {
                sbProgreso?.progress = (pos.toFloat() / dur * 100).toInt()
            }
            handler.removeCallbacks(runnableActualizar)
            handler.postDelayed(runnableActualizar, updateInterval)
        }
    }

    private fun togglePlayPause() {
        player?.let { if (it.isPlaying) it.pause() else it.play() }
        showControlsAndResetTimer()
    }

    private fun reiniciarReproductor() {
        player?.release()
        player = null
        initializePlayer()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun initializePlayer() {
        if (streamUrl.isEmpty()) return
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("Mozilla/5.0")
            .setConnectTimeoutMs(15000)

        player = ExoPlayer.Builder(this)
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(30000, 60000, 2500, 5000).build())
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().also { exoPlayer ->
                binding.reproductor.player = exoPlayer
                binding.reproductor.useController = false
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(streamUrl)))
                exoPlayer.prepare()
                exoPlayer.addListener(playerListener)
                exoPlayer.playWhenReady = true
            }
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

    // --- MÉTODOS DEL MENÚ LATERAL CORREGIDOS ---
    private fun mostarpélis() {
        val controlesLayout = binding.reproductor.findViewById<View>(R.id.controles_reproductor)

        if (binding.recyclerViewTv.visibility == View.VISIBLE) {
            ocultarMenuCompleto()
        } else {
            if (controlesLayout != null) {
                controlesLayout.visibility = View.GONE
                handler.removeCallbacks(runnableOcultarControles)
            }

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

    // Método centralizado para ocultar el menú de canales y su botón "X"
    private fun ocultarMenuCompleto() {
        binding.recyclerViewTv.visibility = View.GONE
        binding.btnCerrarMenuTv.visibility = View.GONE // Esconde la "X"
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // ELIMINADO: Ya no llamamos a reiniciarTemporizadorMenu() aquí adentro.

        return when (keyCode) {
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                mostarpélis()
                true
            }

            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (binding.recyclerViewTv.visibility == View.VISIBLE) {
                    super.onKeyDown(keyCode, event)
                } else {
                    showControlsAndResetTimer()
                    false
                }
            }

            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                togglePlayPause()
                true
            }

            KeyEvent.KEYCODE_BACK -> {
                if (binding.recyclerViewTv.visibility == View.VISIBLE) {
                    ocultarMenuCompleto() // Cierra el menú de forma fija
                    true
                } else if (binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility == View.VISIBLE) {
                    binding.reproductor.findViewById<View>(R.id.controles_reproductor).visibility = View.GONE
                    true
                } else {
                    finishPlayer()
                    true
                }
            }

            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE -> {
                if (binding.recyclerViewTv.visibility != View.VISIBLE) {
                    showControlsAndResetTimer()
                }
                super.onKeyDown(keyCode, event)
            }

            else -> super.onKeyDown(keyCode, event)
        }
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

    @OptIn(UnstableApi::class)
    private fun cycleAspectRatio() {
        val aspectRatios = listOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
        binding.reproductor.resizeMode = aspectRatios[(aspectRatios.indexOf(binding.reproductor.resizeMode) + 1) % aspectRatios.size]
    }
}