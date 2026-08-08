package com.creativem.fulltv.tv

import android.content.Context
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
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

class PlayerTv : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private var currentChannelState = mutableStateOf<Modelo?>(null)
    private var isMenuVisibleState = mutableStateOf(false)
    private var favoriteChannelsState = mutableStateOf<List<Modelo>>(emptyList())
    private var isLoadingState = mutableStateOf(true)
    private var bufferTextState = mutableStateOf("Iniciando señal...")
    private var isOfflineState = mutableStateOf(false)

    private var exoPlayerInstance: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configurarPantallaTvFull()
        prefs = getSharedPreferences("TV_PREFS", Context.MODE_PRIVATE)

        val streamUrl = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""
        val movieTitle = intent.getStringExtra("EXTRA_MOVIE_TITLE") ?: "TV en Vivo"
        val movieImageUrl = intent.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""

        val canalInicial = Modelo(
            id = "canal_actual",
            title = movieTitle,
            streamUrl = streamUrl,
            imageUrl = movieImageUrl
        )

        currentChannelState.value = canalInicial
        TvRepository.lastPlayedChannel = canalInicial

        cargarListaFavoritos()

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
                    PlayerTvScreen(
                        currentChannel = currentChannelState.value,
                        favoriteChannels = favoriteChannelsState.value,
                        isMenuVisible = isMenuVisibleState.value,
                        isLoading = isLoadingState.value,
                        bufferText = bufferTextState.value,
                        isOffline = isOfflineState.value,
                        onCloseMenu = { isMenuVisibleState.value = false },
                        onSelectFavoriteChannel = { canal -> cambiarCanalDirecto(canal) },
                        onToggleMenu = { isMenuVisibleState.value = !isMenuVisibleState.value },
                        onPlayerCreated = { player -> exoPlayerInstance = player }
                    )
                }
            }
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
        isLoadingState.value = true
        bufferTextState.value = "Cargando canal..."
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
                    // Permitir que las flechas arriba/abajo naveguen naturalmente dentro del menú de Compose
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

    override fun onResume() {
        super.onResume()
        configurarPantallaTvFull()
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

// -------------------------------------------------------------
// COMPOSABLE PANTALLA
// -------------------------------------------------------------
@OptIn(UnstableApi::class)
@Composable
fun PlayerTvScreen(
    currentChannel: Modelo?,
    favoriteChannels: List<Modelo>,
    isMenuVisible: Boolean,
    isLoading: Boolean,
    bufferText: String,
    isOffline: Boolean,
    onCloseMenu: () -> Unit,
    onSelectFavoriteChannel: (Modelo) -> Unit,
    onToggleMenu: () -> Unit,
    onPlayerCreated: (ExoPlayer) -> Unit
) {
    val context = LocalContext.current

    var currentBufferText by remember { mutableStateOf(bufferText) }
    var isBuffering by remember { mutableStateOf(isLoading) }

    val exoPlayer = remember(context) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setTsExtractorFlags(tsFlags)
        }
        val hlsExtractorFactory = DefaultHlsExtractorFactory(tsFlags, true)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10000, 40000, 1500, 3000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(3840, 2160)
                    .setForceHighestSupportedBitrate(false)
            )
        }

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
                onPlayerCreated(this)
            }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY -> isBuffering = false
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = true
                currentBufferText = "Error de señal. Reintentando..."
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (isBuffering) {
                val percentage = exoPlayer.bufferedPercentage
                val estimatedKb = exoPlayer.bufferedPosition / 1024
                currentBufferText = "Búfer: $percentage% (${estimatedKb} KB)"
            }
            delay(500)
        }
    }

    LaunchedEffect(currentChannel?.streamUrl) {
        val streamUrl = currentChannel?.streamUrl ?: ""
        if (streamUrl.isNotEmpty()) {
            isBuffering = true
            currentBufferText = "Cargando señal..."

            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val tsFlags = DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS

            val hlsExtractorFactory = DefaultHlsExtractorFactory(tsFlags, true)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val uri = Uri.parse(streamUrl)
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)

            val isStrictHls = streamUrl.contains(".m3u8", ignoreCase = true) ||
                    streamUrl.contains("format=m3u8", ignoreCase = true)

            val isTsStream = streamUrl.contains(".ts", ignoreCase = true) ||
                    streamUrl.contains("/live/", ignoreCase = true) ||
                    streamUrl.contains("/stream/", ignoreCase = true)

            val mediaSource = if (isStrictHls) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                HlsMediaSource.Factory(dataSourceFactory)
                    .setExtractorFactory(hlsExtractorFactory)
                    .setAllowChunklessPreparation(false)
                    .createMediaSource(mediaItemBuilder.build())
            } else if (isTsStream) {
                mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
                mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
            } else {
                mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
            }

            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. REPRODUCTOR DE VIDEO
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                    keepScreenOn = true
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clickable { onToggleMenu() }
        )

        // 2. BUFFER DE CARGA OVERLAY
        if (isBuffering || isOffline) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
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
                            .padding(8.dp)
                    )

                    Text(
                        text = currentChannel?.title ?: "",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (!isOffline) {
                        CircularProgressIndicator(color = Color.Red)
                    }

                    Text(
                        text = if (isOffline) "CANAL FUERA DE LÍNEA" else currentBufferText,
                        color = if (isOffline) Color.Red else Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // 3. OVERLAY MENÚ LATERAL DE FAVORITOS
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
// MENÚ OVERLAY FAVORITOS (CON FOCO AUTOMÁTICO D-PAD)
// -------------------------------------------------------------
@Composable
fun FavoritesOverlayMenu(
    favoriteChannels: List<Modelo>,
    currentStreamUrl: String,
    isMenuVisible: Boolean,
    onCloseMenu: () -> Unit,
    onSelectChannel: (Modelo) -> Unit
) {
    // 🎯 Solicitador de Foco para el Control Remoto
    val firstItemFocusRequester = remember { FocusRequester() }

    // Al abrir el menú, asigna automáticamente el foco D-Pad al primer canal
    LaunchedEffect(isMenuVisible) {
        if (isMenuVisible && favoriteChannels.isNotEmpty()) {
            delay(150) // Pequeña espera para que la animación termine
            try {
                firstItemFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Captura por si la vista aún no está lista
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp),
        color = Color(0xFF141414).copy(alpha = 0.96f),
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
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Red)
                    Text(
                        text = "MIS FAVORITOS",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onCloseMenu) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }

            Divider(color = Color(0xFF333333))

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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(favoriteChannels, key = { _, canal -> "menu_fav_${canal.id}" }) { index, canal ->
                        FavoriteMenuItem(
                            canal = canal,
                            isPlaying = canal.streamUrl == currentStreamUrl,
                            onSelect = { onSelectChannel(canal) },
                            modifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// ÍTEM DEL MENÚ DE FAVORITOS (CON CURSOR AMARILLO D-PAD)
// -------------------------------------------------------------
@Composable
fun FavoriteMenuItem(
    canal: Modelo,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "scale")

    val borderColor = when {
        isFocused -> Color(0xFFFFD600) // 🟡 Amarillo Neón brillante para el control de la TV
        isPlaying -> Color.Red
        else -> Color.Transparent
    }

    val backgroundColor = when {
        isFocused -> Color(0xFF2E2E38)
        isPlaying -> Color(0xFF2D1515)
        else -> Color(0xFF1E1E1E)
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
            .onFocusChanged { isFocused = it.isFocused }
            .focusable() // 🎯 Habilita navegación con Control Remoto
            .clickable { onSelect() }
            .padding(10.dp)
    ) {
        AsyncImage(
            model = canal.imageUrl,
            contentDescription = canal.title,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(45.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black)
                .padding(2.dp)
        )

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
                    color = Color.Red,
                    fontSize = 10.sp
                )
            }
        }
    }
}