package com.creativem.fulltv.tv

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy // Importado para centrado dinámico
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed // Modificado para obtener índices
import androidx.compose.foundation.lazy.grid.rememberLazyGridState // Control del scroll de la grilla
import androidx.compose.foundation.lazy.itemsIndexed // Modificado para obtener índices
import androidx.compose.foundation.lazy.rememberLazyListState // Control del scroll de la fila
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.creativem.fulltv.principal.AudioFocusHelper
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Modelo
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    @OptIn(UnstableApi::class)
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

    var filteredChannels by remember { mutableStateOf<List<Modelo>>(emptyList()) }
    var filteredFavorites by remember { mutableStateOf<List<Modelo>>(emptyList()) }

    // Control de foco inicial único
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // Estados de scroll para controlar el centrado al recibir foco
    val favoritesGridState = rememberLazyGridState()
    val channelsRowState = rememberLazyListState()

    LaunchedEffect(searchQuery, masterChannels, favoriteIds) {
        delay(250)
        withContext(Dispatchers.Default) {
            val normalizedQuery = searchQuery.normalizeSearch()

            val filteredAll = if (normalizedQuery.isBlank()) {
                masterChannels
            } else {
                masterChannels.filter { canal ->
                    val normalizedTitle = canal.title.normalizeSearch()
                    val queryWords = normalizedQuery.split("\\s+".toRegex())
                    queryWords.all { word -> normalizedTitle.contains(word) }
                }
            }

            val filteredFavs = if (normalizedQuery.isBlank()) {
                masterChannels.filter { favoriteIds.contains(it.id) }
            } else {
                filteredAll.filter { favoriteIds.contains(it.id) }
            }

            withContext(Dispatchers.Main) {
                filteredChannels = filteredAll
                filteredFavorites = filteredFavs
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                TvRepository.lastPlayedChannel?.let { canalActualizado ->
                    selectedChannel = canalActualizado
                    prefs.edit().putString("last_selected_channel_id", canalActualizado.id).apply()
                    // Permitir que se vuelva a solicitar foco automático al regresar del reproductor
                    hasRequestedInitialFocus = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
            // LADO IZQUIERDO (50%): MIS FAVORITOS (DOS COLUMNAS HORIZONTALES)
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
                        val searchScale by animateFloatAsState(targetValue = if (isSearchButtonFocused) 1.12f else 1.0f, label = "searchScale")

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
                            text = if (searchQuery.isNotBlank()) "Sin coincidencias en Favoritos" else "Aún no tienes favoritos.\nMantén presionado OK en un canal a la derecha para agregar.",
                            color = Color.Gray,
                            fontSize = 13.sp,
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
                        itemsIndexed(filteredFavorites, key = { _, it -> "fav_grid_${it.id}" }) { index, canal ->
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

            // LADO DERECHO (50%): REPRODUCTOR + CANALES + INFORMACIÓN
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

                TvPlayerCard(
                    selectedChannel = selectedChannel,
                    onOpenFullScreen = { canal -> onOpenFullScreenPlayer(canal) }
                )

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
                        itemsIndexed(filteredChannels, key = { _, it -> "all_${it.id}" }) { index, canal ->
                            // Enlazar FocusRequester dinámico para el elemento seleccionado
                            val isCurrentSelected = selectedChannel?.id == canal.id
                            val focusRequester = remember { FocusRequester() }

                            LaunchedEffect(selectedChannel) {
                                if (isCurrentSelected && !hasRequestedInitialFocus) {
                                    delay(400) // Pequeño delay de renderizado
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
                                modifier = Modifier.focusRequester(focusRequester) // Pasar el requester
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

// -------------------------------------------------------------
// COMPOSABLE: NUEVA TARJETA FAVORITA HORIZONTAL ELEGANTE (2 COLUMNAS) CON CONTROL DE FOCO OK
// -------------------------------------------------------------
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteGridCard(
    canal: Modelo,
    isSelected: Boolean,
    onFocused: () -> Unit, // Callback al recibir foco
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
            .height(65.dp) // Altura elegante y fija para la grilla de dos columnas
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
                    onFocused() // Disparar el evento de centrado
                }
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                val isSelectKey = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == AndroidKeyEvent.KEYCODE_ENTER

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
        // Imagen al inicio (izquierda) con indicador de favorito (corazón) encima
        Box(
            modifier = Modifier.size(45.dp),
            contentAlignment = Alignment.Center
        ) {
            // Reproductor de imagen inteligente con reemplazo de TV roja ante fallos o ausencia de logo
            coil.compose.SubcomposeAsyncImage(
                model = canal.imageUrl,
                contentDescription = canal.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            ) {
                val state = painter.state
                if (state is coil.compose.AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = RedLive, // El TV ahora se ve en color rojo y es completamente visible
                        modifier = Modifier.padding(6.dp) // Ajuste de tamaño para el contenedor de 45dp
                    )
                }
            }

            // Indicador de corazón flotante sobre la imagen
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

        // Nombre del canal grande y visible de lejos (derecha)
        Text(
            text = canal.title,
            color = Color.White,
            fontSize = 14.sp, // Tamaño de letra agrandado
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            lineHeight = 16.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

// -------------------------------------------------------------
// COMPOSABLE: DETALLE DEL CANAL
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
// COMPOSABLE: BUSCADOR TV (EXPANDIBLE AUTO-ENFOCABLE)
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

    // Solicita el foco de forma automática al aparecer en pantalla para abrir el teclado
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
// COMPOSABLE: TARJETA HORIZONTAL
// -------------------------------------------------------------
// -------------------------------------------------------------
// COMPOSABLE: TARJETA HORIZONTAL (ACTUALIZADA CON MODIFIER EXTERNO)
// -------------------------------------------------------------
@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalChannelCard(
    canal: Modelo,
    isFavorite: Boolean,
    isSelected: Boolean,
    onFocused: () -> Unit, // Callback al recibir foco
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier // Añadido soporte para modifier externo
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
        modifier = modifier // Se encadena aquí el modifier recibido del LazyRow
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
                    onFocused() // Disparar el evento de centrado
                }
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                val keyCode = keyEvent.nativeKeyEvent.keyCode
                val isSelectKey = keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                        keyCode == AndroidKeyEvent.KEYCODE_ENTER

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
            coil.compose.SubcomposeAsyncImage(
                model = canal.imageUrl,
                contentDescription = canal.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            ) {
                val state = painter.state
                if (state is coil.compose.AsyncImagePainter.State.Success) {
                    SubcomposeAsyncImageContent()
                } else {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = RedLive,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

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
// REPRODUCTOR EMBEBIDO MEDIA3 EXOPLAYER (CON RESOLUCIÓN OPTIMIZADA A 480P)
// -------------------------------------------------------------
@OptIn(UnstableApi::class)
@Composable
fun EmbeddedPlayerView(
    streamUrl: String,
    onPlayerClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Estado reactivo para el reproductor embebido
    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    // Las tres banderas TS requeridas para la estabilidad de IPTV
    val tsFlags = DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
            DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM

    // CONTROL DE CICLO DE VIDA ACTIVO: Destruye el player al salir para liberar el decodificador de hardware
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (exoPlayer == null) {
                        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                            .setAllowCrossProtocolRedirects(true)
                            .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
                            .setConnectTimeoutMs(15000)
                            .setReadTimeoutMs(15000)

                        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

                        val extractorsFactory = DefaultExtractorsFactory().apply {
                            setTsExtractorFlags(tsFlags)
                        }

                        val errorHandlingPolicy = DefaultLoadErrorHandlingPolicy(100)

                        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
                            .setLoadErrorHandlingPolicy(errorHandlingPolicy)

                        val loadControl = DefaultLoadControl.Builder()
                            .setBufferDurationsMs(
                                12000, // minBufferMs: IGUAL al máximo. Activa la descarga constante y evita pausas inactivas.
                                12000, // maxBufferMs: IGUAL al mínimo. Mantiene el puerto con el servidor IPTV abierto todo el tiempo.
                                1500,  // bufferForPlaybackMs
                                2000   // bufferForPlaybackAfterRebufferMs
                            )
                            .setPrioritizeTimeOverSizeThresholds(true)
                            .build()

                        val trackSelector = DefaultTrackSelector(context).apply {
                            setParameters(
                                buildUponParameters()
                                    .setMaxVideoSize(854, 480) // 480p máximo para ahorrar recursos
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
                            .build().apply {
                                playWhenReady = true
                            }

                        player.addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                                    player.seekToDefaultPosition()
                                }
                                player.prepare()
                                player.play()
                            }
                        })

                        exoPlayer = player
                    }
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    // LIBERACIÓN CRÍTICA: Cerramos el reproductor por completo para liberar códecs
                    exoPlayer?.release()
                    exoPlayer = null
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // Watchdog contra el congelamiento de imagen silencioso
    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        var lastRenderedFrames = -1
        var secondsFrozen = 0

        while (true) {
            delay(1000)

            val isPlayingAndReady = player.playbackState == Player.STATE_READY && player.playWhenReady
            if (isPlayingAndReady) {
                val counters = player.videoDecoderCounters
                if (counters != null) {
                    val currentFrames = counters.renderedOutputBufferCount

                    if (currentFrames == lastRenderedFrames) {
                        secondsFrozen++
                        if (secondsFrozen >= 4) {
                            secondsFrozen = 0
                            player.prepare()
                            player.play()
                        }
                    } else {
                        lastRenderedFrames = currentFrames
                        secondsFrozen = 0
                    }
                }
            } else {
                secondsFrozen = 0
            }
        }
    }

    // Carga del stream reactiva al cambiar URL o recrear el reproductor
    LaunchedEffect(streamUrl, exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        if (streamUrl.isNotEmpty()) {
            val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent("VLC/3.0.18 LibVLC/3.0.18")
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(15000)

            val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

            val uri = Uri.parse(streamUrl)

            // --- AQUÍ SE AGREGA EL RETRASO EN VIVO (LIVE CONFIGURATION) ---
            val mediaItemBuilder = MediaItem.Builder().setUri(uri).setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(5000).build())
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        // Define el retraso objetivo en milisegundos (10000 ms = 10 segundos).
                        // Esto hace que el reproductor intente mantenerse 10 segundos por detrás de la señal
                        // en vivo absoluta, creando un colchón de tiempo seguro frente a caídas de internet.
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
        }
    }
    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize().clickable { onPlayerClick() }) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                // Actualiza dinámicamente la instancia del player cuando se destruye/recrea
                view.player = exoPlayer
            },
            modifier = androidx.compose.ui.Modifier.fillMaxSize()
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

// -------------------------------------------------------------
// EXTENSIONES PARA CENTRAR ELEMENTOS EN FOCO (TV LAYOUTS)
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
            // Para LazyVerticalGrid, calculamos sobre el eje Y (vertical)
            val childCenter = itemInfo.offset.y + itemInfo.size.height / 2
            this@animateScrollAndCentralizeItem.animateScrollBy((childCenter - center).toFloat())
        } else {
            this@animateScrollAndCentralizeItem.animateScrollToItem(index)
        }
    }
}