package com.creativem.fulltv.tv

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.creativem.fulltv.principal.AudioFocusHelper
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Modelo
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.lifecycleScope
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@UnstableApi
class PlayerTv : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private var currentChannelState = mutableStateOf<Modelo?>(null)
    private var isMenuVisibleState = mutableStateOf(false)
    private var favoriteChannelsState = mutableStateOf<List<Modelo>>(emptyList())
    private var isLoadingState = mutableStateOf(true)
    private var bufferTextState = mutableStateOf("Iniciando señal...")
    private var isOfflineState = mutableStateOf(false)

    private var exoPlayerInstance = mutableStateOf<ExoPlayer?>(null)

    @OptIn(UnstableApi::class)
    private val tsFlags = DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM

    private lateinit var httpDataSourceFactory: DefaultHttpDataSource.Factory
    private lateinit var dataSourceFactory: DefaultDataSource.Factory
    private lateinit var hlsExtractorFactory: DefaultHlsExtractorFactory
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configurarTransicionInstantanea()
        configurarPantallaTvFull()

        prefs = getSharedPreferences("TV_PREFS", Context.MODE_PRIVATE)

        inicializarComponentesDeRed()

        val streamUrl = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""
        val movieTitle = intent.getStringExtra("EXTRA_MOVIE_TITLE") ?: "TV en Vivo"
        val movieImageUrl = intent.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""

        // Modificado: El id ahora utiliza 'movieTitle' para alinearse con los IDs por nombre de TvActivity
        val canalInicial = Modelo(
            id = movieTitle,
            title = movieTitle,
            streamUrl = streamUrl,
            imageUrl = movieImageUrl
        )

        currentChannelState.value = canalInicial
        TvRepository.lastPlayedChannel = canalInicial

        cargarListaFavoritos()

        inicializarExoPlayerAnticipado()
        prepararYReproducirCanal(canalInicial.streamUrl)

        onBackPressedDispatcher.addCallback(this) {
            if (isMenuVisibleState.value) {
                isMenuVisibleState.value = false
            } else {
                finish()
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val player = exoPlayerInstance.value
                    if (player != null) {
                        PlayerTvScreen(
                            exoPlayer = player,
                            currentChannel = currentChannelState.value,
                            favoriteChannels = favoriteChannelsState.value,
                            isMenuVisible = isMenuVisibleState.value,
                            isLoading = isLoadingState.value,
                            bufferText = bufferTextState.value,
                            isOffline = isOfflineState.value,
                            onCloseMenu = { isMenuVisibleState.value = false },
                            onSelectFavoriteChannel = { canal -> cambiarCanalDirecto(canal) },
                            onToggleMenu = { isMenuVisibleState.value = !isMenuVisibleState.value }
                        )
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun inicializarComponentesDeRed() {
        httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        hlsExtractorFactory = DefaultHlsExtractorFactory(tsFlags, true)

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setTsExtractorFlags(tsFlags)
        }

        mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(DefaultLoadErrorHandlingPolicy(100))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val streamUrl = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""
        val movieTitle = intent.getStringExtra("EXTRA_MOVIE_TITLE") ?: "TV en Vivo"
        val movieImageUrl = intent.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""

        // Modificado: Al igual que en onCreate, usamos el título como id estable
        val nuevoCanal = Modelo(
            id = movieTitle,
            title = movieTitle,
            streamUrl = streamUrl,
            imageUrl = movieImageUrl
        )

        currentChannelState.value = nuevoCanal
        TvRepository.lastPlayedChannel = nuevoCanal
        isOfflineState.value = false

        if (exoPlayerInstance.value == null) {
            inicializarExoPlayerAnticipado()
        }
        prepararYReproducirCanal(streamUrl)
    }

    private fun configurarTransicionInstantanea() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    @OptIn(UnstableApi::class)
    private fun inicializarExoPlayerAnticipado() {
        if (exoPlayerInstance.value != null) return

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                12000,
                12000,
                1500,
                2000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .setForceHighestSupportedBitrate(false)
            )
        }

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        exoPlayerInstance.value = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .build().apply {
                playWhenReady = true
            }
    }

    @OptIn(UnstableApi::class)
    private fun prepararYReproducirCanal(streamUrl: String) {
        val player = exoPlayerInstance.value ?: return
        if (streamUrl.isEmpty()) return

        isLoadingState.value = true
        bufferTextState.value = "Cargando señal..."

        lifecycleScope.launch(Dispatchers.Main) {
            delay(300)

            val uri = Uri.parse(streamUrl)

            val mediaItemBuilder = MediaItem.Builder().setUri(uri).setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(5000).build())
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(10000)
                        .build()
                )

            val isStrictHls = streamUrl.contains(".m3u8", ignoreCase = true) ||
                    streamUrl.contains("format=m3u8", ignoreCase = true)

            val isTsStream = streamUrl.contains(".ts", ignoreCase = true) ||
                    streamUrl.contains("/live/", ignoreCase = true) ||
                    streamUrl.contains("/stream/", ignoreCase = true)

            val errorHandlingPolicy = DefaultLoadErrorHandlingPolicy(100)

            val mediaSource = if (isStrictHls) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                HlsMediaSource.Factory(dataSourceFactory)
                    .setExtractorFactory(hlsExtractorFactory)
                    .setAllowChunklessPreparation(false)
                    .setLoadErrorHandlingPolicy(errorHandlingPolicy)
                    .createMediaSource(mediaItemBuilder.build())
            } else {
                val extractorsFactory = DefaultExtractorsFactory().apply {
                    setTsExtractorFlags(tsFlags)
                }
                if (isTsStream) {
                    mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
                }
                DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                    .setLoadErrorHandlingPolicy(errorHandlingPolicy)
                    .createMediaSource(mediaItemBuilder.build())
            }

            player.setMediaSource(mediaSource)
            player.prepare()
            player.play()
        }
    }
    private fun cargarListaFavoritos() {
        val favIds = prefs.getStringSet("fav_ids", emptySet()) ?: emptySet()
        val masterList = TvRepository.channelListMaster

        val favsList = masterList.filter { favIds.contains(it.id) }
        favoriteChannelsState.value = if (favsList.isNotEmpty()) favsList else masterList
    }

    private fun cambiarCanalDirecto(canal: Modelo) {
        currentChannelState.value = canal
        TvRepository.lastPlayedChannel = canal
        isMenuVisibleState.value = false
        isOfflineState.value = false
        prepararYReproducirCanal(canal.streamUrl)
    }

    private fun cambiarCanalSiguienteAnterior(siguiente: Boolean) {
        val lista = favoriteChannelsState.value
        if (lista.isEmpty()) return

        val canalActual = currentChannelState.value
        val indexActual = lista.indexOfFirst { it.streamUrl == canalActual?.streamUrl }

        val nuevoIndex = if (siguiente) {
            if (indexActual == -1 || indexActual >= lista.size - 1) 0 else indexActual + 1
        } else {
            if (indexActual <= 0) lista.size - 1 else indexActual - 1
        }

        cambiarCanalDirecto(lista[nuevoIndex])
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                cambiarCanalSiguienteAnterior(siguiente = true)
                true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                cambiarCanalSiguienteAnterior(siguiente = false)
                true
            }
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_INFO,
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                isMenuVisibleState.value = !isMenuVisibleState.value
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (!isMenuVisibleState.value) {
                    isMenuVisibleState.value = true
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isMenuVisibleState.value) {
                    isMenuVisibleState.value = false
                    true
                } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                    finish()
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isMenuVisibleState.value) {
                    isMenuVisibleState.value = true
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun configurarPantallaTvFull() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CastvHelper.ajustarContexto(newBase))
    }

    override fun getResources(): android.content.res.Resources {
        val res = super.getResources()
        CastvHelper.ajustarRecursos(res, this)
        return res
    }

    override fun onStart() {
        super.onStart()
        if (exoPlayerInstance.value == null) {
            inicializarExoPlayerAnticipado()
            currentChannelState.value?.let { canal ->
                prepararYReproducirCanal(canal.streamUrl)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        configurarPantallaTvFull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusHelper.requestAudioFocus(this)
        }
        exoPlayerInstance.value?.playWhenReady = true
        exoPlayerInstance.value?.play()
    }

    override fun onPause() {
        super.onPause()
        if (isFinishing) {
            exoPlayerInstance.value?.release()
            exoPlayerInstance.value = null
        } else {
            exoPlayerInstance.value?.pause()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusHelper.abandonAudioFocus()
        }
    }

    override fun onStop() {
        super.onStop()
        exoPlayerInstance.value?.release()
        exoPlayerInstance.value = null
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayerInstance.value?.release()
        exoPlayerInstance.value = null
    }

    override fun finish() {
        super.finish()
        configurarTransicionInstantanea()
    }
}

// -------------------------------------------------------------
// COMPOSABLE PANTALLA REPRODUCTOR MEDIA3 EXOPLAYER
// -------------------------------------------------------------
@OptIn(UnstableApi::class)
@Composable
fun PlayerTvScreen(
    exoPlayer: ExoPlayer,
    currentChannel: Modelo?,
    favoriteChannels: List<Modelo>,
    isMenuVisible: Boolean,
    isLoading: Boolean,
    bufferText: String,
    isOffline: Boolean,
    onCloseMenu: () -> Unit,
    onSelectFavoriteChannel: (Modelo) -> Unit,
    onToggleMenu: () -> Unit
) {
    var currentBufferText by remember { mutableStateOf(bufferText) }
    var isBuffering by remember { mutableStateOf(isLoading) }

    LaunchedEffect(isLoading) {
        isBuffering = isLoading
    }

    LaunchedEffect(bufferText) {
        currentBufferText = bufferText
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = true
                currentBufferText = "Error de señal. Reintentando..."

                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                }

                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
        exoPlayer.addListener(listener)
        isBuffering = exoPlayer.playbackState == Player.STATE_BUFFERING

        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    LaunchedEffect(isBuffering, exoPlayer) {
        if (isBuffering) {
            var secondsBuffering = 0
            while (isActive) {
                val percentage = exoPlayer.bufferedPercentage
                val estimatedKb = exoPlayer.bufferedPosition / 1024
                currentBufferText = "Búfer: $percentage% (${estimatedKb} KB)"

                delay(1000)
                secondsBuffering++

                if (secondsBuffering >= 12) {
                    secondsBuffering = 0
                    currentBufferText = "Señal lenta. Restableciendo conexión..."
                    exoPlayer.prepare()
                    exoPlayer.play()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable { onToggleMenu() }
        )

        if (isBuffering || isOffline) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A122A).copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AsyncImage(
                        model = currentChannel?.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(1.dp, GoldAccent, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    )

                    Text(
                        text = currentChannel?.title ?: "",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (!isOffline) {
                        CircularProgressIndicator(color = GoldAccent)
                    }

                    Text(
                        text = if (isOffline) "CANAL FUERA DE LÍNEA" else currentBufferText,
                        color = if (isOffline) RedLive else Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isMenuVisible,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            FavoritesOverlayMenu(
                favoriteChannels = favoriteChannels,
                currentStreamUrl = currentChannel?.streamUrl ?: "",
                isMenuVisible = isMenuVisible,
                onCloseMenu = onCloseMenu,
                onSelectChannel = onSelectFavoriteChannel
            )
        }
    }
}

// -------------------------------------------------------------
// MENÚ OVERLAY FAVORITOS (CON FOCO DINÁMICO EN CANAL ACTUAL)
// -------------------------------------------------------------
@Composable
fun FavoritesOverlayMenu(
    favoriteChannels: List<Modelo>,
    currentStreamUrl: String,
    isMenuVisible: Boolean,
    onCloseMenu: () -> Unit,
    onSelectChannel: (Modelo) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val focusRequesters = remember(favoriteChannels) {
        List(favoriteChannels.size) { FocusRequester() }
    }

    val playingIndex = remember(favoriteChannels, currentStreamUrl) {
        val index = favoriteChannels.indexOfFirst { it.streamUrl == currentStreamUrl }
        if (index != -1) index else 0
    }

    LaunchedEffect(isMenuVisible, playingIndex) {
        if (isMenuVisible && favoriteChannels.isNotEmpty() && playingIndex in focusRequesters.indices) {
            delay(150)
            try {
                lazyListState.scrollToItem(playingIndex)
                focusRequesters[playingIndex].requestFocus()
            } catch (e: Exception) {}
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp),
        color = Color(0xFF0A122A).copy(alpha = 0.96f),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = RedLive)
                    Text(
                        text = "MIS FAVORITOS",
                        color = GoldAccent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onCloseMenu) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }

            HorizontalDivider(color = Color(0xFF2A2A38))

            if (favoriteChannels.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tienes canales en favoritos",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Modificado: Agregado el índice a la clave para evitar cierres ante duplicados
                    itemsIndexed(favoriteChannels, key = { index, canal -> "menu_fav_${canal.id}_$index" }) { index, canal ->
                        FavoriteMenuItem(
                            canal = canal,
                            isPlaying = canal.streamUrl == currentStreamUrl,
                            onFocused = {
                                lazyListState.animateScrollAndCentralizeMenuItem(index, coroutineScope)
                            },
                            onSelect = { onSelectChannel(canal) },
                            modifier = Modifier.focusRequester(focusRequesters[index])
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ÍTEM DEL MENÚ DE FAVORITOS
// -------------------------------------------------------------
@Composable
fun FavoriteMenuItem(
    canal: Modelo,
    isPlaying: Boolean,
    onFocused: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "scale")

    val borderColor = when {
        isFocused -> GoldAccent
        isPlaying -> RedLive
        else -> Color.Transparent
    }

    val backgroundColor = when {
        isFocused -> Color(0xFF282836)
        isPlaying -> Color(0xFF2D1515)
        else -> Color(0xFF161622)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(2.5.dp, borderColor, RoundedCornerShape(8.dp))
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable { onSelect() }
            .padding(10.dp)
    ) {
        coil.compose.SubcomposeAsyncImage(
            model = canal.imageUrl,
            contentDescription = canal.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(45.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
                .padding(2.dp)
        ) {
            val state = painter.state
            if (state is coil.compose.AsyncImagePainter.State.Success) {
                SubcomposeAsyncImageContent()
            } else {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = RedLive,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = canal.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isPlaying || isFocused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (isPlaying) {
                Text(
                    text = "🔴 Reproduciendo ahora",
                    color = RedLive,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// EXTENSIONES PARA CENTRAR ELEMENTOS EN FOCO
// -------------------------------------------------------------
private fun androidx.compose.foundation.lazy.LazyListState.animateScrollAndCentralizeMenuItem(
    index: Int,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val itemInfo = this.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    scope.launch {
        if (itemInfo != null) {
            val center = (this@animateScrollAndCentralizeMenuItem.layoutInfo.viewportEndOffset -
                    this@animateScrollAndCentralizeMenuItem.layoutInfo.viewportStartOffset) / 2
            val childCenter = itemInfo.offset + itemInfo.size / 2
            this@animateScrollAndCentralizeMenuItem.animateScrollBy((childCenter - center).toFloat())
        } else {
            this@animateScrollAndCentralizeMenuItem.animateScrollToItem(index)
        }
    }
}