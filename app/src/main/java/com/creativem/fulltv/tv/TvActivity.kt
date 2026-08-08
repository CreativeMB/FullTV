package com.creativem.fulltv.tv

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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
import com.google.firebase.database.FirebaseDatabase
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

// 🎨 VALORES DE COLOR UNIFICADOS CON PELICULASACTIVITY
val GoldAccent = Color(0xFFC5A059)
val DeepDarkBg = Color(0xFF0A122A)
val CardDarkBg = Color(0xFF161622)
val RedLive = Color(0xFFFF2A2A)

class TvActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        configurarModoTv()

        prefs = getSharedPreferences("TV_PREFS", Context.MODE_PRIVATE)

        onBackPressedDispatcher.addCallback(this) {
            CastvHelper.regresarAPeliculas(this@TvActivity)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DeepDarkBg
                ) {
                    TvInteractiveScreen(
                        prefs = prefs,
                        onOpenFullScreenPlayer = { canal -> abrirReproductor(canal) }
                    )
                }
            }
        }
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
@Composable
fun TvInteractiveScreen(
    prefs: SharedPreferences,
    onOpenFullScreenPlayer: (Modelo) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var masterChannels by remember { mutableStateOf<List<Modelo>>(emptyList()) }
    var favoriteIds by remember { mutableStateOf(prefs.getStringSet("fav_ids", emptySet()) ?: emptySet()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf<Modelo?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Sincronización al regresar de PlayerTv
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TvRepository.lastPlayedChannel?.let { canalActualizado ->
                    selectedChannel = canalActualizado
                    prefs.edit().putString("last_selected_channel_id", canalActualizado.id).apply()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Carga desde Firebase
    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val savedChannelId = prefs.getString("last_selected_channel_id", null)

            if (TvRepository.channelListMaster.isNotEmpty()) {
                masterChannels = TvRepository.channelListMaster

                val canalASelec = TvRepository.lastPlayedChannel
                    ?: masterChannels.firstOrNull { it.id == savedChannelId }
                    ?: masterChannels.firstOrNull { favoriteIds.contains(it.id) }
                    ?: masterChannels.firstOrNull()

                selectedChannel = canalASelec
                TvRepository.lastPlayedChannel = canalASelec
                isLoading = false
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
                    withContext(Dispatchers.Main) { isLoading = false }
                }
            } catch (e: Exception) {
                Log.e("TV_ACTIVITY", "Error Firebase", e)
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    val matchesSearch = remember<(String, String) -> Boolean> {
        { title, query ->
            if (query.isBlank()) true
            else {
                val normalizedTitle = title.normalizeSearch()
                val queryWords = query.normalizeSearch().split("\\s+".toRegex())
                queryWords.all { word -> normalizedTitle.contains(word) }
            }
        }
    }

    val favoriteChannels = remember(favoriteIds, masterChannels, searchQuery) {
        masterChannels
            .filter { favoriteIds.contains(it.id) }
            .filter { matchesSearch(it.title, searchQuery) }
    }

    val allChannelsFiltered = remember(masterChannels, searchQuery) {
        masterChannels.filter { matchesSearch(it.title, searchQuery) }
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GoldAccent)
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // =========================================================================
            // LADO IZQUIERDO (50%): MIS FAVORITOS
            // =========================================================================
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TvSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = RedLive)
                    Text(
                        text = "MIS FAVORITOS (${favoriteChannels.size})",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldAccent
                    )
                }

                if (favoriteChannels.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardDarkBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Sin coincidencias en Favoritos" else "Aún no tienes favoritos.\nMantén presionado OK en un canal a la derecha para agregar.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(favoriteChannels, key = { "fav_grid_${it.id}" }) { canal ->
                            ChannelCard(
                                canal = canal,
                                isFavorite = true,
                                isSelected = selectedChannel?.id == canal.id,
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

            // =========================================================================
            // LADO DERECHO (50%): REPRODUCTOR + CANALES + INFORMACIÓN
            // =========================================================================
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

                // 1. REPRODUCTOR MINI
                TvPlayerCard(
                    selectedChannel = selectedChannel,
                    onOpenFullScreen = { canal -> onOpenFullScreenPlayer(canal) }
                )

                // 2. LISTA DE TODOS LOS CANALES
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 2.dp)
                ) {
                    Icon(Icons.Default.Tv, contentDescription = null, tint = GoldAccent)
                    Text(
                        text = "Todos los Canales (${allChannelsFiltered.size})",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                }

                if (allChannelsFiltered.isEmpty()) {
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
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allChannelsFiltered, key = { "all_${it.id}" }) { canal ->
                            HorizontalChannelCard(
                                canal = canal,
                                isFavorite = favoriteIds.contains(canal.id),
                                isSelected = selectedChannel?.id == canal.id,
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

                // 3. INFORMACIÓN DEL CANAL EN REPRODUCCIÓN
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

// -------------------------------------------------------------
// COMPOSABLE: FICHA DE INFORMACIÓN DEL CANAL ACTIVO (ESTILO CINEPARCHE)
// -------------------------------------------------------------
@Composable
fun SelectedChannelDetailCard(
    canal: Modelo,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDarkBg),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = canal.imageUrl,
                contentDescription = canal.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(75.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
                    .padding(4.dp)
            )

            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = canal.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = if (isFavorite) RedLive else Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "🔴 TRANSMISIÓN EN VIVO",
                    color = RedLive,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Presiona OK en el reproductor para Pantalla Completa.",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// COMPOSABLE: BUSCADOR TV (DORADO CINEPARCHE)
// -------------------------------------------------------------
@Composable
fun TvSearchBar(query: String, onQueryChange: (String) -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = 2.dp,
                color = if (isFocused) GoldAccent else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            ),
        placeholder = { Text("Buscar canal por nombre...", fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldAccent) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GoldAccent,
            unfocusedBorderColor = Color(0xFF2A2A38),
            focusedContainerColor = Color(0xFF1E1E28),
            unfocusedContainerColor = CardDarkBg
        )
    )
}

// -------------------------------------------------------------
// COMPOSABLE: TARJETA GRILLA (DORADO EN FOCO / ROJO EN SELECCIÓN)
// -------------------------------------------------------------

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelCard(
    canal: Modelo,
    isFavorite: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "scale")

    val borderColor = when {
        isFocused -> GoldAccent
        isSelected -> RedLive
        else -> Color.Transparent
    }

    val backgroundColor = if (isFocused) Color(0xFF282836) else Color(0xFF1B1B24)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(2.5.dp, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp)
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
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = RedLive,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = canal.title,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// -------------------------------------------------------------
// COMPOSABLE: TARJETA HORIZONTAL
// -------------------------------------------------------------

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalChannelCard(
    canal: Modelo,
    isFavorite: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(targetValue = if (isFocused) 1.08f else 1.0f, label = "scale")

    val borderColor = when {
        isFocused -> GoldAccent
        isSelected -> RedLive
        else -> Color(0xFF2A2A38)
    }

    val backgroundColor = if (isFocused) Color(0xFF282836) else Color(0xFF1B1B24)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(105.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(2.5.dp, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = canal.imageUrl,
                contentDescription = canal.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            if (isFavorite) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = RedLive,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = canal.title,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// -------------------------------------------------------------
// COMPOSABLE: REPRODUCTOR MINI CON FOCO
// -------------------------------------------------------------
@Composable
fun TvPlayerCard(
    selectedChannel: Modelo?,
    onOpenFullScreen: (Modelo) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor = if (isFocused) GoldAccent else RedLive.copy(alpha = 0.8f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(3.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable {
                selectedChannel?.let { onOpenFullScreen(it) }
            }
    ) {
        selectedChannel?.let { canal ->
            EmbeddedPlayerView(
                streamUrl = canal.streamUrl,
                onPlayerClick = { onOpenFullScreen(canal) }
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Sin señal seleccionada", color = Color.Gray)
        }
    }
}

// -------------------------------------------------------------
// REPRODUCTOR EMBEBIDO MEDIA3 EXOPLAYER (SIN TEXTO SOBREPUESTO)
// -------------------------------------------------------------
@OptIn(UnstableApi::class)
@Composable
fun EmbeddedPlayerView(
    streamUrl: String,
    onPlayerClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(context) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
            .setConnectTimeoutMs(20000)
            .setReadTimeoutMs(20000)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val tsFlags = DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setTsExtractorFlags(tsFlags)
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10000, 40000, 1500, 3000)
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

        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    exoPlayer.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (exoPlayer.playbackState == Player.STATE_READY || exoPlayer.playbackState == Player.STATE_BUFFERING) {
                        exoPlayer.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    LaunchedEffect(streamUrl) {
        if (streamUrl.isNotEmpty()) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(20000)
            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val tsFlags = DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
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
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize()
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