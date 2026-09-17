package com.creativem.fulltv.tv

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.TvOff
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
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import coil.compose.AsyncImage
import com.creativem.fulltv.principal.AudioFocusHelper
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Modelo
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.Normalizer

val GoldAccent = Color(0xFFC5A059)
val DeepDarkBg = Color(0xFF000000)
val CardDarkBg = Color(0xFF161622)
val RedLive = Color(0xFFFF2A2A)

@UnstableApi
class TvActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    var isFullScreenState = mutableStateOf(false)
    var isSideMenuVisibleState = mutableStateOf(false)
    var onNextPreviousChannel: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configurarModoTv()
        prefs = getSharedPreferences("TV_PREFS", Context.MODE_PRIVATE)

        onBackPressedDispatcher.addCallback(this) {
            if (isSideMenuVisibleState.value) {
                isSideMenuVisibleState.value = false
            } else if (isFullScreenState.value) {
                isFullScreenState.value = false
            } else {
                CastvHelper.regresarAPeliculas(this@TvActivity)
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepDarkBg
                ) {
                    TvInteractiveScreen(
                        prefs = prefs,
                        isFullScreen = isFullScreenState.value,
                        isSideMenuVisible = isSideMenuVisibleState.value,
                        onToggleFullScreen = { full -> isFullScreenState.value = full },
                        onToggleSideMenu = { visible -> isSideMenuVisibleState.value = visible },
                        setNextPreviousChannelHandler = { handler -> onNextPreviousChannel = handler }
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (isFullScreenState.value) {
            when (keyCode) {
                AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    onNextPreviousChannel?.invoke(true)
                    return true
                }
                AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
                AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                    onNextPreviousChannel?.invoke(false)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun configurarModoTv() {
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
        configurarModoTv()
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
// COMPOSABLE PRINCIPAL
// -------------------------------------------------------------
@OptIn(UnstableApi::class)
@Composable
fun TvInteractiveScreen(
    prefs: SharedPreferences,
    isFullScreen: Boolean,
    isSideMenuVisible: Boolean,
    onToggleFullScreen: (Boolean) -> Unit,
    onToggleSideMenu: (Boolean) -> Unit,
    setNextPreviousChannelHandler: (((Boolean) -> Unit)?) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var masterChannels by remember { mutableStateOf<List<Modelo>>(emptyList()) }
    var favoriteIds by remember { mutableStateOf(prefs.getStringSet("fav_ids", emptySet()) ?: emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf<Modelo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var isChannelLoading by remember { mutableStateOf(true) }
    var isChannelOffline by remember { mutableStateOf(false) }

    val filteredChannels = remember(searchQuery, masterChannels) {
        if (searchQuery.isBlank()) {
            masterChannels
        } else {
            val normalizedQuery = searchQuery.normalizeSearch()
            val queryWords = normalizedQuery.split("\\s+".toRegex())
            masterChannels.filter { canal ->
                val normalizedTitle = canal.title.normalizeSearch()
                queryWords.all { word -> normalizedTitle.contains(word) }
            }
        }
    }

    val filteredFavorites = remember(searchQuery, masterChannels, favoriteIds) {
        if (searchQuery.isBlank()) {
            masterChannels.filter { favoriteIds.contains(it.id) }
        } else {
            filteredChannels.filter { favoriteIds.contains(it.id) }
        }
    }

    val activeChannelsList = remember(filteredFavorites, masterChannels) {
        if (filteredFavorites.isNotEmpty()) filteredFavorites else masterChannels
    }

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    val tsFlags = DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM

    DisposableEffect(lifecycleOwner) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        val extractorsFactory = DefaultExtractorsFactory().apply { setTsExtractorFlags(tsFlags) }
        val errorHandlingPolicy = DefaultLoadErrorHandlingPolicy(100)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(12000, 12000, 1500, 2000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setMaxVideoSize(1920, 1080)
                    .setForceHighestSupportedBitrate(false)
            )
        }

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
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

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    isChannelLoading = false
                    isChannelOffline = false
                }
            }

            override fun onRenderedFirstFrame() {
                isChannelLoading = false
                isChannelOffline = false
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("TV_PLAYER", "Playback Error: ${error.errorCodeName}", error)
                isChannelLoading = false
                isChannelOffline = true

                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                }
            }
        })

        exoPlayer = player

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    player.playWhenReady = true
                    player.play()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    player.pause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
            exoPlayer = null
        }
    }

    LaunchedEffect(selectedChannel, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        val canal = selectedChannel ?: return@LaunchedEffect

        player.stop()
        player.clearMediaItems()
        isChannelLoading = true
        isChannelOffline = false

        if (canal.streamUrl.isNotEmpty()) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
                .setConnectTimeoutMs(10000)
                .setReadTimeoutMs(10000)

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
            val uri = Uri.parse(canal.streamUrl)

            val mediaItemBuilder = MediaItem.Builder()
                .setUri(uri)
                .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(10000).build())

            val isStrictHls = canal.streamUrl.contains(".m3u8", ignoreCase = true) ||
                    canal.streamUrl.contains("format=m3u8", ignoreCase = true)

            val isTsStream = canal.streamUrl.contains(".ts", ignoreCase = true) ||
                    canal.streamUrl.contains("/live/", ignoreCase = true) ||
                    canal.streamUrl.contains("/stream/", ignoreCase = true)

            val errorHandlingPolicy = DefaultLoadErrorHandlingPolicy(100)

            val mediaSource = if (isStrictHls) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                val hlsExtractorFactory = DefaultHlsExtractorFactory(tsFlags, true)
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

            launch {
                delay(8000)
                if (isChannelLoading && !player.isPlaying && player.playbackState != Player.STATE_READY) {
                    isChannelLoading = false
                    isChannelOffline = true
                }
            }
        } else {
            isChannelLoading = false
            isChannelOffline = true
        }
    }

    DisposableEffect(activeChannelsList, selectedChannel) {
        val handler: (Boolean) -> Unit = { siguiente ->
            if (activeChannelsList.isNotEmpty()) {
                val indexActual = activeChannelsList.indexOfFirst { it.streamUrl == selectedChannel?.streamUrl }
                val nuevoIndex = if (siguiente) {
                    if (indexActual == -1 || indexActual >= activeChannelsList.size - 1) 0 else indexActual + 1
                } else {
                    if (indexActual <= 0) activeChannelsList.size - 1 else indexActual - 1
                }
                val nuevoCanal = activeChannelsList[nuevoIndex]
                selectedChannel = nuevoCanal
                TvRepository.lastPlayedChannel = nuevoCanal
                prefs.edit().putString("last_selected_channel_id", nuevoCanal.id).apply()
            }
        }
        setNextPreviousChannelHandler(handler)
        onDispose { setNextPreviousChannelHandler(null) }
    }

    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    val favoritesGridState = rememberLazyGridState()
    val channelsRowState = rememberLazyListState()

    val cargarCanales: () -> Unit = {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val savedChannelId = prefs.getString("last_selected_channel_id", null)

            if (TvRepository.channelListMaster.isNotEmpty()) {
                val list = TvRepository.channelListMaster
                val canalASelec = TvRepository.lastPlayedChannel
                    ?: list.firstOrNull { it.id == savedChannelId }
                    ?: list.firstOrNull { favoriteIds.contains(it.id) }
                    ?: list.firstOrNull()

                withContext(Dispatchers.Main) {
                    masterChannels = list
                    selectedChannel = canalASelec
                    TvRepository.lastPlayedChannel = canalASelec
                    isLoading = false
                }
                return@launch
            }

            try {
                val snapshot = FirebaseDatabase.getInstance(TvRepository.FIREBASE_DB_URL)
                    .getReference(TvRepository.FIREBASE_PATH)
                    .get().await()

                val m3uUrl = snapshot.value?.toString()?.trim()
                if (!m3uUrl.isNullOrEmpty()) {
                    val downloaded = descargarM3uStream(m3uUrl)
                    withContext(Dispatchers.Main) {
                        masterChannels = downloaded
                        TvRepository.channelListMaster = downloaded

                        val canalASelec = TvRepository.lastPlayedChannel
                            ?: downloaded.firstOrNull { it.id == savedChannelId }
                            ?: downloaded.firstOrNull { favoriteIds.contains(it.id) }
                            ?: downloaded.firstOrNull()

                        selectedChannel = canalASelec
                        TvRepository.lastPlayedChannel = canalASelec
                        isLoading = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        masterChannels = emptyList()
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                Log.e("TV_ACTIVITY", "Error Firebase", e)
                withContext(Dispatchers.Main) {
                    masterChannels = emptyList()
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        cargarCanales()
    }

    val toggleFavorite = { canal: Modelo ->
        val newFavs = favoriteIds.toMutableSet()
        if (newFavs.contains(canal.id)) {
            newFavs.remove(canal.id)
            Toast.makeText(context, "${canal.title} quitado de Favoritos", Toast.LENGTH_SHORT).show()
        } else {
            newFavs.add(canal.id)
            Toast.makeText(context, "${canal.title} añadido a Favoritos", Toast.LENGTH_SHORT).show()
        }
        favoriteIds = newFavs
        prefs.edit().putStringSet("fav_ids", newFavs).apply()
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DeepDarkBg),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = GoldAccent,
                    modifier = Modifier.size(50.dp),
                    strokeWidth = 4.dp
                )
                Text(
                    text = "Cargando canales...",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else if (masterChannels.isEmpty()) {
        TvMaintenanceScreen(
            onRetry = {
                TvRepository.channelListMaster = emptyList()
                cargarCanales()
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {

            // 1. PANTALLA COMPLETA
            if (isFullScreen) {
                val fullScreenFocusRequester = remember { FocusRequester() }

                // Garantiza foco siempre que se entre o se cierre el menú lateral
                LaunchedEffect(isSideMenuVisible) {
                    if (!isSideMenuVisible) {
                        delay(100)
                        try {
                            fullScreenFocusRequester.requestFocus()
                        } catch (e: Exception) {}
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .zIndex(20f)
                ) {
                    RobustTexturePlayer(
                        exoPlayer = exoPlayer,
                        modifier = Modifier.fillMaxSize()
                    )

                    // CAPA TÁCTIL Y DE CONTROL REMOTO
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                            .focusRequester(fullScreenFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                val keyCode = keyEvent.nativeKeyEvent.keyCode
                                val isOkKey = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                                        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                                        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                                        keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A ||
                                        keyCode == AndroidKeyEvent.KEYCODE_MENU

                                if (isOkKey && keyEvent.type == KeyEventType.KeyUp) {
                                    if (!isSideMenuVisible) {
                                        onToggleSideMenu(true)
                                    }
                                    true
                                } else if (keyCode == AndroidKeyEvent.KEYCODE_DPAD_LEFT && keyEvent.type == KeyEventType.KeyUp) {
                                    if (isSideMenuVisible) {
                                        onToggleSideMenu(false)
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            .clickable {
                                onToggleSideMenu(!isSideMenuVisible)
                            }
                    )

                    ChannelStatusOverlay(
                        channel = selectedChannel,
                        isOffline = isChannelOffline,
                        isLoading = isChannelLoading,
                        isMini = false
                    )

                    AnimatedVisibility(
                        visible = isSideMenuVisible,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .zIndex(100f)
                    ) {
                        FavoritesOverlayMenu(
                            favoriteChannels = if (filteredFavorites.isNotEmpty()) filteredFavorites else masterChannels,
                            currentStreamUrl = selectedChannel?.streamUrl ?: "",
                            isMenuVisible = isSideMenuVisible,
                            onCloseMenu = { onToggleSideMenu(false) },
                            onSelectChannel = { canal ->
                                selectedChannel = canal
                                TvRepository.lastPlayedChannel = canal
                                prefs.edit().putString("last_selected_channel_id", canal.id).apply()
                                onToggleSideMenu(false)
                            }
                        )
                    }
                }
            }

            // 2. MODO VISTA DIVIDIDA (UI PRINCIPAL)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // LADO IZQUIERDO: FAVORITOS
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    var isSearching by remember { mutableStateOf(false) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp)
                    ) {
                        if (isSearching) {
                            TvSearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onCloseSearch = {
                                    isSearching = false
                                    searchQuery = ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = RedLive)
                                Text(
                                    text = "MIS FAVORITOS (${filteredFavorites.size})",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldAccent
                                )
                            }

                            var isSearchButtonFocused by remember { mutableStateOf(false) }
                            val searchScale by animateFloatAsState(
                                targetValue = if (isSearchButtonFocused) 1.12f else 1.0f,
                                label = "searchScale"
                            )

                            IconButton(
                                onClick = { isSearching = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer {
                                        scaleX = searchScale
                                        scaleY = searchScale
                                    }
                                    .onFocusChanged { isSearchButtonFocused = it.isFocused }
                                    .focusable()
                                    .border(
                                        width = 2.dp,
                                        color = if (isSearchButtonFocused) GoldAccent else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .background(
                                        color = if (isSearchButtonFocused) Color(0xFF282836) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Buscar",
                                    tint = GoldAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (filteredFavorites.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CardDarkBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "Sin coincidencias en Favoritos"
                                else "Aún no tienes favoritos.\nMantén presionado OK en un canal a la derecha para agregar.",
                                color = Color.Gray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = favoritesGridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(filteredFavorites, key = { index, it -> "fav_grid_${it.id}_$index" }) { index, canal ->
                                FavoriteGridCard(
                                    canal = canal,
                                    isSelected = selectedChannel?.id == canal.id,
                                    onFocused = {
                                        favoritesGridState.animateScrollAndCentralizeItem(index, coroutineScope)
                                    },
                                    onClick = {
                                        selectedChannel = canal
                                        TvRepository.lastPlayedChannel = canal
                                        prefs.edit().putString("last_selected_channel_id", canal.id).apply()
                                    },
                                    onLongClick = { toggleFavorite(canal) }
                                )
                            }
                        }
                    }
                }

                // LADO DERECHO: MINI REPRODUCTOR Y LISTA DE CANALES
                Column(
                    modifier = Modifier
                        .weight(0.5f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = selectedChannel?.title ?: "Selecciona un canal",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 2.dp)
                    )

                    var isMiniFocused by remember { mutableStateOf(false) }
                    val borderColor = if (isMiniFocused) GoldAccent else if (isChannelOffline) RedLive else RedLive.copy(alpha = 0.8f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .border(3.dp, borderColor, RoundedCornerShape(12.dp))
                            .onFocusChanged { isMiniFocused = it.isFocused }
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                val keyCode = keyEvent.nativeKeyEvent.keyCode
                                val isOkKey = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                                        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                                        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                                        keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A

                                if (isOkKey && keyEvent.type == KeyEventType.KeyUp) {
                                    onToggleFullScreen(true)
                                    true
                                } else {
                                    false
                                }
                            }
                            .clickable { onToggleFullScreen(true) }
                    ) {
                        if (!isFullScreen) {
                            RobustTexturePlayer(
                                exoPlayer = exoPlayer,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        ChannelStatusOverlay(
                            channel = selectedChannel,
                            isOffline = isChannelOffline,
                            isLoading = isChannelLoading,
                            isMini = true
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 2.dp)
                    ) {
                        Icon(Icons.Default.Tv, contentDescription = null, tint = GoldAccent)
                        Text(
                            text = "Todos los Canales (${filteredChannels.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                    }

                    if (filteredChannels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No se encontraron canales", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyRow(
                            state = channelsRowState,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(filteredChannels, key = { index, it -> "all_${it.id}_$index" }) { index, canal ->
                                val isCurrentSelected = selectedChannel?.id == canal.id
                                val focusRequester = remember { FocusRequester() }

                                LaunchedEffect(selectedChannel) {
                                    if (isCurrentSelected && !hasRequestedInitialFocus) {
                                        delay(400)
                                        try {
                                            focusRequester.requestFocus()
                                            hasRequestedInitialFocus = true
                                        } catch (e: Exception) {}
                                    }
                                }

                                HorizontalChannelCard(
                                    canal = canal,
                                    isFavorite = favoriteIds.contains(canal.id),
                                    isSelected = isCurrentSelected,
                                    onFocused = {
                                        channelsRowState.animateScrollAndCentralizeItem(index, coroutineScope)
                                    },
                                    onClick = {
                                        selectedChannel = canal
                                        TvRepository.lastPlayedChannel = canal
                                        prefs.edit().putString("last_selected_channel_id", canal.id).apply()
                                    },
                                    onLongClick = { toggleFavorite(canal) },
                                    modifier = Modifier.focusRequester(focusRequester)
                                )
                            }
                        }
                    }

                    selectedChannel?.let { canal ->
                        SelectedChannelDetailCard(
                            canal = canal,
                            isFavorite = favoriteIds.contains(canal.id),
                            onToggleFavorite = { toggleFavorite(canal) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// REPRODUCTOR FLUIDO BASADO EN TEXTURE_VIEW (SIN PANTALLA NEGRA)
// -------------------------------------------------------------
@Composable
fun RobustTexturePlayer(
    exoPlayer: ExoPlayer?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                val textureView = TextureView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }
                addView(textureView)
                exoPlayer?.setVideoTextureView(textureView)
            }
        },
        update = { frameLayout ->
            val textureView = frameLayout.getChildAt(0) as? TextureView
            if (textureView != null && exoPlayer != null) {
                exoPlayer.setVideoTextureView(textureView)
            }
        },
        modifier = modifier
    )
}

// -------------------------------------------------------------
// PANTALLA DE MANTENIMIENTO
// -------------------------------------------------------------
@Composable
fun TvMaintenanceScreen(
    onRetry: () -> Unit
) {
    var isRetryFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isRetryFocused) 1.1f else 1.0f, label = "retryScale")
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(200)
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepDarkBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = "ESTAMOS EN MANTENIMIENTO",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Estamos actualizando y mejorando nuestra lista de canales para brindarte una mejor experiencia.\nPor favor, vuelve a intentar en unos momentos.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.widthIn(max = 550.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .onFocusChanged { isRetryFocused = it.isFocused }
                    .border(
                        width = 2.5.dp,
                        color = if (isRetryFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "REINTENTAR CONEXIÓN",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// CONTENEDOR DE ESTADO DEL CANAL (CONECTANDO / FUERA DE LÍNEA)
// -------------------------------------------------------------
@Composable
fun ChannelStatusOverlay(
    channel: Modelo?,
    isOffline: Boolean,
    isLoading: Boolean,
    isMini: Boolean = false
) {
    if (!isOffline && !isLoading) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0C14).copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isMini) 6.dp else 12.dp),
            modifier = Modifier.padding(8.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = channel?.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(if (isMini) 45.dp else 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .padding(4.dp)
                )
            }

            if (isOffline) {
                Text(
                    text = "SEÑAL NO DISPONIBLE",
                    color = RedLive,
                    fontSize = if (isMini) 12.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Canal temporalmente fuera de línea",
                    color = Color.LightGray,
                    fontSize = if (isMini) 10.sp else 13.sp,
                    textAlign = TextAlign.Center
                )
            } else if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = GoldAccent,
                        modifier = Modifier.size(if (isMini) 16.dp else 24.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Conectando señal...",
                        color = Color.White,
                        fontSize = if (isMini) 11.sp else 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// MENÚ LATERAL OVERLAY DE FAVORITOS
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
// ITEM DEL MENÚ DE FAVORITOS
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
                    color = RedLive,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// TARJETA FAVORITA HORIZONTAL ELEGANTE
// -------------------------------------------------------------
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteGridCard(
    canal: Modelo,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }
    var longPressTriggered by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    val borderColor = when {
        isFocused -> GoldAccent
        isSelected -> RedLive
        else -> Color.Transparent
    }

    val backgroundColor = if (isFocused) Color(0xFF282836) else Color(0xFF1B1B24)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(65.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 3.dp else 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                val isSelectKey = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                        keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A

                if (isSelectKey) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            if (pressStartTime == 0L) {
                                pressStartTime = System.currentTimeMillis()
                                longPressTriggered = false
                            } else {
                                val elapsed = System.currentTimeMillis() - pressStartTime
                                if (elapsed > 800L && !longPressTriggered) {
                                    longPressTriggered = true
                                    onLongClick()
                                }
                            }
                        }
                        KeyEventType.KeyUp -> {
                            val elapsed = System.currentTimeMillis() - pressStartTime
                            pressStartTime = 0L
                            if (!longPressTriggered) {
                                if (elapsed < 800L) {
                                    onClick()
                                }
                            }
                            longPressTriggered = false
                        }
                    }
                    true
                } else {
                    false
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier.size(45.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = canal.imageUrl,
                contentDescription = canal.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(1.dp)
                    .size(18.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = RedLive,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Text(
            text = canal.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            lineHeight = 16.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// -------------------------------------------------------------
// DETALLE DEL CANAL
// -------------------------------------------------------------
@Composable
fun SelectedChannelDetailCard(
    canal: Modelo,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFavFocused by remember { mutableStateOf(false) }
    val favScale by animateFloatAsState(targetValue = if (isFavFocused) 1.1f else 1.0f, label = "favScale")

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = canal.imageUrl,
                contentDescription = canal.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black)
                    .padding(2.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = canal.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(1.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔴 EN VIVO",
                        color = RedLive,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• OK: Pantalla Completa",
                        color = Color.LightGray,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer {
                        scaleX = favScale
                        scaleY = favScale
                    }
                    .onFocusChanged { isFavFocused = it.isFocused }
                    .focusable()
                    .border(
                        width = 1.dp,
                        color = if (isFavFocused) GoldAccent else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .background(
                        color = if (isFavFocused) Color(0xFF282836) else Color.Transparent,
                        shape = RoundedCornerShape(4.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) RedLive else Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// BUSCADOR TV
// -------------------------------------------------------------
@Composable
fun TvSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) GoldAccent else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ),
        placeholder = { Text("Buscar canal...", fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldAccent) },
        trailingIcon = {
            IconButton(onClick = onCloseSearch) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar búsqueda",
                    tint = Color.Gray
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search,
            autoCorrect = false
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GoldAccent,
            unfocusedBorderColor = Color(0xFF2A2A38),
            focusedContainerColor = Color(0xFF1E1E28),
            unfocusedContainerColor = CardDarkBg
        )
    )
}

// -------------------------------------------------------------
// TARJETA DE CANAL HORIZONTAL
// -------------------------------------------------------------
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalChannelCard(
    canal: Modelo,
    isFavorite: Boolean,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    var pressStartTime by remember { mutableStateOf(0L) }
    var longPressTriggered by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "scale"
    )

    val borderColor = when {
        isFocused -> GoldAccent
        isSelected -> RedLive
        else -> Color(0xFF2A2A38)
    }

    val backgroundColor = if (isFocused) Color(0xFF282836) else Color(0xFF1B1B24)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(130.dp)
            .height(115.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(
                width = if (isFocused) 3.dp else 2.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                val isSelectKey = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == AndroidKeyEvent.KEYCODE_ENTER ||
                        keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER ||
                        keyCode == AndroidKeyEvent.KEYCODE_BUTTON_A

                if (isSelectKey) {
                    when (keyEvent.type) {
                        KeyEventType.KeyDown -> {
                            if (pressStartTime == 0L) {
                                pressStartTime = System.currentTimeMillis()
                                longPressTriggered = false
                            } else {
                                val elapsed = System.currentTimeMillis() - pressStartTime
                                if (elapsed > 800L && !longPressTriggered) {
                                    longPressTriggered = true
                                    onLongClick()
                                }
                            }
                        }
                        KeyEventType.KeyUp -> {
                            val elapsed = System.currentTimeMillis() - pressStartTime
                            pressStartTime = 0L
                            if (!longPressTriggered) {
                                if (elapsed < 800L) {
                                    onClick()
                                }
                            }
                            longPressTriggered = false
                        }
                    }
                    true
                } else {
                    false
                }
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = canal.imageUrl,
                contentDescription = canal.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = RedLive,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = canal.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 2,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

// -------------------------------------------------------------
// NORMALIZADOR DE BÚSQUEDA
// -------------------------------------------------------------
private fun String.normalizeSearch(): String {
    val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
    return temp.replace("[\\p{InCombiningDiacriticalMarks}]".toRegex(), "")
        .lowercase()
        .replace("-", " ")
        .replace("_", " ")
        .trim()
}

// -------------------------------------------------------------
// DESCARGA Y PARSEO M3U
// -------------------------------------------------------------
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
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
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

private fun parseM3uBuffer(reader: BufferedReader): List<Modelo> {
    val channels = mutableListOf<Modelo>()
    var currentName = ""
    var currentLogo = ""

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
                            id = finalName,
                            title = finalName,
                            streamUrl = trimmed,
                            imageUrl = currentLogo
                        )
                    )
                }
                currentName = ""
                currentLogo = ""
            }
        }
    }
    return channels
}

// -------------------------------------------------------------
// EXTENSIONES PARA CENTRAR ELEMENTOS EN FOCO
// -------------------------------------------------------------
fun androidx.compose.foundation.lazy.LazyListState.animateScrollAndCentralizeItem(
    index: Int,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val itemInfo = this.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    scope.launch {
        if (itemInfo != null) {
            val center = (this@animateScrollAndCentralizeItem.layoutInfo.viewportEndOffset -
                    this@animateScrollAndCentralizeItem.layoutInfo.viewportStartOffset) / 2
            val childCenter = itemInfo.offset + itemInfo.size / 2
            this@animateScrollAndCentralizeItem.animateScrollBy((childCenter - center).toFloat())
        } else {
            this@animateScrollAndCentralizeItem.animateScrollToItem(index)
        }
    }
}

fun androidx.compose.foundation.lazy.grid.LazyGridState.animateScrollAndCentralizeItem(
    index: Int,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val itemInfo = this.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    scope.launch {
        if (itemInfo != null) {
            val center = (this@animateScrollAndCentralizeItem.layoutInfo.viewportEndOffset -
                    this@animateScrollAndCentralizeItem.layoutInfo.viewportStartOffset) / 2
            val childCenter = itemInfo.offset.y + itemInfo.size.height / 2
            this@animateScrollAndCentralizeItem.animateScrollBy((childCenter - center).toFloat())
        } else {
            this@animateScrollAndCentralizeItem.animateScrollToItem(index)
        }
    }
}

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