@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.media3.common.util.UnstableApi::class
)

package com.creativem.fulltv.peliculas

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.util.Log
import android.view.KeyEvent as AndroidKeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlin.OptIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.creativem.fulltv.api.MovieResponse
import com.creativem.fulltv.api.TMDbApiService
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.*
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// 🟢 PALETA DORADO PREMIUM
private val DoradoBrillante = Color(0xFFEAD295)
private val DegradadoDoradoPremiun = Brush.horizontalGradient(
    listOf(Color(0xFFF3E0AA), Color(0xFFC5A059), Color(0xFF9E7B36))
)

@Suppress("DEPRECATION")
class PlayerPeliculas : AppCompatActivity() {

    var onWakeUpControls: (() -> Unit)? = null
    var onToggleMenuCartelera: (() -> Unit)? = null
    var onUserInteraction: (() -> Unit)? = null
    var areControlsVisibleState: () -> Boolean = { false }
    var isMenuCarteleraOpenState: () -> Boolean = { false }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CastvHelper.ajustarContexto(newBase))
    }

    override fun getResources(): android.content.res.Resources {
        val res = super.getResources()
        CastvHelper.ajustarRecursos(res, this)
        return res
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val streamUrl = intent?.getStringExtra("EXTRA_STREAM_URL") ?: ""
        val movieTitle = intent?.getStringExtra("EXTRA_MOVIE_TITLE") ?: "Título desconocido"
        val movieCastv = intent?.getIntExtra("EXTRA_MOVIE_CASTV", 0) ?: 0
        val movieImageUrl = intent?.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""
        val movieSynopsis = intent?.getStringExtra("EXTRA_MOVIE_SYNOPSIS")
            ?: intent?.getStringExtra("EXTRA_OVERVIEW")
            ?: intent?.getStringExtra("EXTRA_SINOPSIS")
            ?: ""
        val startPosition = intent?.getIntExtra("EXTRA_POSITION", 0)?.toLong() ?: 0L

        setContent {
            MaterialTheme {
                PlayerPeliculasScreen(
                    activity = this,
                    initialStreamUrl = streamUrl,
                    initialMovieTitle = movieTitle,
                    initialMovieCastv = movieCastv,
                    initialMovieImageUrl = movieImageUrl,
                    initialMovieSynopsis = movieSynopsis,
                    initialStartPosition = startPosition,
                    onFinish = { finish() }
                )
            }
        }

        enterFullScreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterFullScreen()
    }

    private fun enterFullScreen() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (_: Exception) {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
        }
    }

    override fun dispatchKeyEvent(event: AndroidKeyEvent): Boolean {
        if (event.action == AndroidKeyEvent.ACTION_DOWN) {
            val code = event.keyCode

            onUserInteraction?.invoke()

            if (code in listOf(AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_PAGE_UP, AndroidKeyEvent.KEYCODE_PAGE_DOWN, 174)) {
                onToggleMenuCartelera?.invoke()
                return true
            }

            if (isMenuCarteleraOpenState()) {
                return super.dispatchKeyEvent(event)
            }

            if (!areControlsVisibleState()) {
                val teclasDespertar = listOf(
                    AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                    AndroidKeyEvent.KEYCODE_ENTER,
                    AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                    AndroidKeyEvent.KEYCODE_DPAD_UP,
                    AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                    AndroidKeyEvent.KEYCODE_DPAD_LEFT,
                    AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
                    AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
                    AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                )
                if (code in teclasDespertar) {
                    onWakeUpControls?.invoke()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

// =========================================================================
// 🟢 PANTALLA PRINCIPAL DEL REPRODUCTOR
// =========================================================================
@OptIn(UnstableApi::class)
@Composable
fun PlayerPeliculasScreen(
    activity: PlayerPeliculas,
    initialStreamUrl: String,
    initialMovieTitle: String,
    initialMovieCastv: Int,
    initialMovieImageUrl: String,
    initialMovieSynopsis: String,
    initialStartPosition: Long,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var streamUrl by remember { mutableStateOf(initialStreamUrl) }
    var movieTitle by remember { mutableStateOf(initialMovieTitle) }
    var movieCastv by remember { mutableIntStateOf(initialMovieCastv) }
    var movieImageUrl by remember { mutableStateOf(initialMovieImageUrl) }
    var movieSynopsis by remember { mutableStateOf(initialMovieSynopsis) }

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var totalDuration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }
    var currentAspectRatioMode by remember { mutableIntStateOf(0) }

    var pistasAudioDisponibles by remember { mutableStateOf<List<PistaAudio>>(emptyList()) }
    var pistasSubtitulosDisponibles by remember { mutableStateOf<List<PistaSubtitulo>>(emptyList()) }
    var mostrarDialogoPistas by remember { mutableStateOf(false) }

    var controlesVisibles by remember { mutableStateOf(true) }
    var menuPelisAbierto by remember { mutableStateOf(false) }
    var estaDesplazandoProgreso by remember { mutableStateOf(false) }
    var estaCargando by remember { mutableStateOf(true) }
    var progresoSimulado by remember { mutableIntStateOf(0) }
    var gateoTicks by remember { mutableIntStateOf(0) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // 🟢 LISTAS EN TIEMPO REAL (ALQUILERES PRIMERO + CARTELERA GENERAL)
    val peliculasActivas = remember { mutableStateListOf<Modelo>() }
    val idsAlquileresUsuario = remember { mutableStateListOf<String>() }
    val todasLasPeliculasFirebase = remember { mutableStateListOf<Modelo>() }
    var snapshotAlquileres by remember { mutableStateOf<DataSnapshot?>(null) }

    val auth = remember { com.google.firebase.auth.FirebaseAuth.getInstance() }
    val userEmail = auth.currentUser?.email
    val correoKey = remember(userEmail) { userEmail?.replace(".", "_")?.replace("@", "_") ?: "" }
    val databaseRef = remember { com.google.firebase.database.FirebaseDatabase.getInstance().reference }

    fun normalizarClave(texto: String): String {
        return texto.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
    }

    // 🟢 FUNCIÓN QUE COMBINA ALQUILERES DE PRIMERAS Y LUEGO LAS GRATIS
    fun sincronizarCartelera() {
        val ahora = System.currentTimeMillis()
        val cachePorTitulo = todasLasPeliculasFirebase.associateBy { normalizarClave(it.title) }
        val cachePorId = todasLasPeliculasFirebase.associateBy { it.id }

        val misAlquileres = mutableListOf<Modelo>()
        val misIds = mutableSetOf<String>()

        // 1. Procesar alquileres personales del perfil
        snapshotAlquileres?.children?.forEach { child ->
            val tituloKey = child.key ?: return@forEach
            val createdAtRaw = (child.child("createdAt").value as? Number)?.toLong() ?: 0L
            val countdownMinutes = (child.child("countdownMinutes").value as? Number)?.toInt() ?: 0

            val createdAtMillis = if (createdAtRaw in 1..999999999999L) createdAtRaw * 1000 else createdAtRaw
            val durationMillis = TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())

            if (countdownMinutes > 0 && (ahora - createdAtMillis) in 0 until durationMillis) {
                val movieOriginal = cachePorTitulo[tituloKey] ?: cachePorId[tituloKey]
                if (movieOriginal != null) {
                    val movieAlquilada = movieOriginal.copy(
                        createdAt = createdAtMillis,
                        countdownMinutes = countdownMinutes
                    )
                    misAlquileres.add(movieAlquilada)
                    misIds.add(movieAlquilada.id)
                }
            }
        }

        // 2. Procesar cartelera gratis (excluyendo si ya la tiene alquilada para no duplicar)
        val promosGenerales = todasLasPeliculasFirebase.filter { m ->
            val durationMillis = TimeUnit.MINUTES.toMillis(m.countdownMinutes.toLong())
            val createdAtMillis = if (m.createdAt in 1..999999999999L) m.createdAt * 1000 else m.createdAt
            m.countdownMinutes > 0 && (ahora - createdAtMillis) in 0 until durationMillis && !misIds.contains(m.id)
        }.sortedByDescending { it.createdAt }

        // 3. Unir: ALQUILERES PRIMERO SIEMPRE
        val combinadas = mutableListOf<Modelo>()
        combinadas.addAll(misAlquileres)
        combinadas.addAll(promosGenerales)

        peliculasActivas.clear()
        peliculasActivas.addAll(combinadas)

        idsAlquileresUsuario.clear()
        idsAlquileresUsuario.addAll(misIds)

        if (combinadas.isEmpty() && menuPelisAbierto) {
            menuPelisAbierto = false
        }
    }

    // 🟢 ESCUCHA DE FIREBASE EN TIEMPO REAL: MOVIES
    DisposableEffect(Unit) {
        val moviesRef = databaseRef.child("movies")
        val listenerMovies = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                if (snapshot.exists()) {
                    val raw = snapshot.children.mapNotNull { child ->
                        val m = child.getValue(Modelo::class.java)
                        m?.apply { id = child.key ?: "" }
                    }
                    todasLasPeliculasFirebase.clear()
                    todasLasPeliculasFirebase.addAll(raw)
                    sincronizarCartelera()
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        moviesRef.addValueEventListener(listenerMovies)
        onDispose { moviesRef.removeEventListener(listenerMovies) }
    }

    // 🟢 ESCUCHA DE FIREBASE EN TIEMPO REAL: ALQUILERES DEL PERFIL
    DisposableEffect(correoKey) {
        if (correoKey.isNotEmpty()) {
            val rentalsRef = databaseRef.child("usuarios").child(correoKey).child("alquileres")
            val listenerRentals = object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    snapshotAlquileres = snapshot
                    sincronizarCartelera()
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            }
            rentalsRef.addValueEventListener(listenerRentals)
            onDispose { rentalsRef.removeEventListener(listenerRentals) }
        } else {
            onDispose {}
        }
    }

    // 🟢 RELOJ VIGILANTE CADA SEGUNDO (Actualiza contadores y retira expiradas)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            if (todasLasPeliculasFirebase.isNotEmpty() || snapshotAlquileres != null) {
                sincronizarCartelera()
            }
        }
    }

    var mostrarDialogoContinuar by remember { mutableStateOf(false) }
    var progresoGuardadoEncontrado by remember { mutableLongStateOf(0L) }
    var reconnectionAttempts by remember { mutableIntStateOf(0) }
    val maxReconnectionAttempts = 10

    val apiService = remember {
        val client = okhttp3.OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        retrofit2.Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .client(client)
            .build()
            .create(com.creativem.fulltv.api.TMDbApiService::class.java)
    }
    val apiKey = "678193d2c735c6f37840cee035f4d69a"

    LaunchedEffect(movieTitle) {
        if (movieSynopsis.isBlank() || movieSynopsis.length < 15) {
            val queryLimpia = movieTitle
                .replace(Regex("\\(\\d{4}-\\d{2}-\\d{2}\\)"), "")
                .replace(Regex("\\(\\d{4}\\)"), "")
                .replace(Regex("\\[.*?\\]"), "")
                .trim()

            if (queryLimpia.isNotBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val response = apiService.searchMovie(apiKey, "es-MX", queryLimpia).execute()
                        if (response.isSuccessful) {
                            val primera = response.body()?.results?.firstOrNull()
                            if (primera != null && !primera.overview.isNullOrBlank()) {
                                movieSynopsis = primera.overview!!
                            }
                        }
                    } catch (_: Exception) {}
                    Unit
                }
            }
        }
    }

    DisposableEffect(menuPelisAbierto, controlesVisibles) {
        activity.areControlsVisibleState = { controlesVisibles }
        activity.isMenuCarteleraOpenState = { menuPelisAbierto }
        activity.onUserInteraction = { lastInteractionTime = System.currentTimeMillis() }
        activity.onWakeUpControls = {
            if (!menuPelisAbierto) {
                lastInteractionTime = System.currentTimeMillis()
                controlesVisibles = true
            }
        }
        activity.onToggleMenuCartelera = {
            lastInteractionTime = System.currentTimeMillis()
            menuPelisAbierto = !menuPelisAbierto
            if (menuPelisAbierto) controlesVisibles = false
        }
        onDispose {
            activity.onWakeUpControls = null
            activity.onToggleMenuCartelera = null
            activity.onUserInteraction = null
            activity.isMenuCarteleraOpenState = { false }
        }
    }

    fun generarClaveProgreso(url: String, titulo: String, castv: Int): String {
        val urlLimpia = url.split("?")[0].split("|")[0].trim()
        return if (urlLimpia.isNotEmpty()) {
            "progreso_peli_" + urlLimpia.hashCode().toString()
        } else {
            val t = titulo.trim().ifBlank { "pelicula_sin_titulo" }
            val anio = if (castv <= 0) "0" else castv.toString()
            "progreso_peli_" + "$t-$anio".replace(Regex("[^A-Za-z0-9_-]"), "_")
        }
    }

    fun obtenerProgresoGuardado(url: String, titulo: String, castv: Int): Long {
        val clave = generarClaveProgreso(url, titulo, castv)
        val prefs = context.getSharedPreferences("progreso_peliculas", Context.MODE_PRIVATE)
        return try {
            prefs.getLong(clave, 0L)
        } catch (_: Exception) {
            prefs.getInt(clave, 0).toLong()
        }
    }

    fun guardarProgresoActual() {
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration
        if (pos > 10_000 && (dur <= 0 || (dur - pos) > 15_000)) {
            val clave = generarClaveProgreso(streamUrl, movieTitle, movieCastv)
            val prefs = context.getSharedPreferences("progreso_peliculas", Context.MODE_PRIVATE)
            prefs.edit().putLong(clave, pos).commit()
        }
    }

    fun borrarProgresoGuardado() {
        val clave = generarClaveProgreso(streamUrl, movieTitle, movieCastv)
        val prefs = context.getSharedPreferences("progreso_peliculas", Context.MODE_PRIVATE)
        prefs.edit().remove(clave).commit()
    }

    fun procesarPistasDisponibles(tracks: Tracks) {
        val listaAudios = mutableListOf<PistaAudio>()
        val listaSubtitulos = mutableListOf<PistaSubtitulo>()
        val groups = tracks.groups

        for (groupIndex in groups.indices) {
            val group = groups[groupIndex]
            val tipoPista = group.type

            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val mimeType = format.sampleMimeType ?: ""
                if (mimeType.contains("cea-608") || mimeType.contains("cea-708")) continue

                val idiomaCodigo = format.language ?: "und"
                val nombrePista = format.label ?: try {
                    Locale(idiomaCodigo).getDisplayLanguage(Locale("es")).replaceFirstChar { it.uppercase() }
                } catch (_: Exception) { idiomaCodigo.uppercase() }

                val esForzado = (format.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
                val nombreFinal = if (esForzado) "$nombrePista (Forzado)" else nombrePista
                val estaSeleccionada = group.isTrackSelected(trackIndex)

                if (tipoPista == C.TRACK_TYPE_AUDIO) {
                    listaAudios.add(PistaAudio(groupIndex, trackIndex, nombreFinal, idiomaCodigo, estaSeleccionada, group))
                } else if (tipoPista == C.TRACK_TYPE_TEXT) {
                    listaSubtitulos.add(PistaSubtitulo(groupIndex, trackIndex, nombreFinal, idiomaCodigo, estaSeleccionada, group))
                }
            }
        }
        pistasAudioDisponibles = listaAudios
        pistasSubtitulosDisponibles = listaSubtitulos
    }

    fun prepararReproductor(posicionAIniciar: Long) {
        try {
            estaCargando = true
            progresoSimulado = 0

            player?.stop()
            player?.clearMediaItems()
            player?.release()
            player = null

            var urlLimpia = streamUrl.trim()
            var finalUserAgent: String? = null
            var finalReferer: String? = null
            var finalCookie: String? = null

            if (urlLimpia.contains("|")) {
                val partes = urlLimpia.split("|")
                urlLimpia = partes[0].trim()
                if (partes.size > 1) {
                    partes[1].split("&").forEach { parametro ->
                        val kv = parametro.split("=")
                        if (kv.size == 2) {
                            val clave = kv[0].trim()
                            val valor = Uri.decode(kv[1].trim())
                            when {
                                clave.equals("User-Agent", ignoreCase = true) -> finalUserAgent = valor
                                clave.equals("Referer", ignoreCase = true) -> finalReferer = valor
                                clave.equals("Cookie", ignoreCase = true) -> finalCookie = valor
                            }
                        }
                    }
                }
            }

            val requestProperties = HashMap<String, String>()
            requestProperties["Connection"] = "keep-alive"
            requestProperties["Accept"] = "*/*"
            requestProperties["ngrok-skip-browser-warning"] = "true"

            val ref = finalReferer
            if (!ref.isNullOrEmpty()) {
                requestProperties["Referer"] = ref
                try {
                    val uri = Uri.parse(ref)
                    requestProperties["Origin"] = "${uri.scheme}://${uri.host}"
                } catch (_: Exception) {}
            }

            val coo = finalCookie
            if (!coo.isNullOrEmpty()) {
                requestProperties["Cookie"] = coo
            }

            val userAgentExo = finalUserAgent ?: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Safari/605.1.15"

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgentExo)
                .setDefaultRequestProperties(requestProperties)
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(30_000)
                .setAllowCrossProtocolRedirects(true)

            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)

            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 300_000, 1_000, 4_500)
                .setTargetBufferBytes(C.LENGTH_UNSET)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            val nuevoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .build()

            val estiloSubtitulos = CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                null
            )
            playerViewRef?.subtitleView?.setStyle(estiloSubtitulos)
            playerViewRef?.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20f)

            val mediaItem = MediaItem.Builder().setUri(Uri.parse(urlLimpia)).build()

            nuevoPlayer.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) { procesarPistasDisponibles(tracks) }
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_BUFFERING -> estaCargando = true
                        Player.STATE_READY -> {
                            estaCargando = false
                            reconnectionAttempts = 0
                        }
                        Player.STATE_ENDED -> {
                            borrarProgresoGuardado()
                            estaCargando = false
                        }
                        else -> {}
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                override fun onPlayerError(error: PlaybackException) {
                    estaCargando = false
                    if (reconnectionAttempts < maxReconnectionAttempts) {
                        reconnectionAttempts++
                        val ultimaPos = nuevoPlayer.currentPosition.takeIf { it > 0 } ?: posicionAIniciar
                        coroutineScope.launch {
                            delay(3000)
                            prepararReproductor(ultimaPos)
                        }
                    }
                }
            })

            nuevoPlayer.setMediaItem(mediaItem)
            if (posicionAIniciar > 0) nuevoPlayer.seekTo(posicionAIniciar)
            nuevoPlayer.playWhenReady = true
            nuevoPlayer.prepare()

            player = nuevoPlayer
            playerViewRef?.player = nuevoPlayer

        } catch (e: Exception) {
            estaCargando = false
        }
    }

    LaunchedEffect(streamUrl) {
        if (streamUrl.isNotBlank()) {
            val guardado = obtenerProgresoGuardado(streamUrl, movieTitle, movieCastv)
            if (guardado > 0) {
                progresoGuardadoEncontrado = guardado
                mostrarDialogoContinuar = true
            } else {
                prepararReproductor(initialStartPosition)
            }
        }
    }

    LaunchedEffect(player, isPlaying) {
        while (isActive) {
            player?.let { p ->
                currentPosition = p.currentPosition
                totalDuration = p.duration.coerceAtLeast(0L)
                bufferedPosition = p.bufferedPosition

                if (estaCargando) {
                    val milisCargados = maxOf(0L, p.bufferedPosition - p.currentPosition)
                    val objetivo = if (p.currentPosition <= 1000L) 3000.0 else 4500.0
                    val progresoReal = ((milisCargados / objetivo) * 100).toInt()

                    if (progresoReal > progresoSimulado) {
                        progresoSimulado = minOf(99, progresoSimulado + 2)
                        gateoTicks = 0
                    } else if (progresoSimulado < 99) {
                        gateoTicks++
                        if (gateoTicks >= 3) {
                            progresoSimulado += 1
                            gateoTicks = 0
                        }
                    }
                }
            }
            delay(1000)
        }
    }

    LaunchedEffect(controlesVisibles, lastInteractionTime, menuPelisAbierto, estaDesplazandoProgreso) {
        if (controlesVisibles && !menuPelisAbierto && !estaDesplazandoProgreso) {
            delay(6000)
            controlesVisibles = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            guardarProgresoActual()
            player?.stop()
            player?.release()
            player = null
        }
    }

    BackHandler {
        when {
            menuPelisAbierto -> menuPelisAbierto = false
            controlesVisibles -> controlesVisibles = false
            else -> {
                guardarProgresoActual()
                onFinish()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    lastInteractionTime = System.currentTimeMillis()
                    if (menuPelisAbierto) {
                        menuPelisAbierto = false
                    } else {
                        controlesVisibles = !controlesVisibles
                    }
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    playerViewRef = this
                }
            },
            update = { pView ->
                val aspectRatios = listOf(
                    AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    AspectRatioFrameLayout.RESIZE_MODE_FILL,
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                )
                pView.resizeMode = aspectRatios[currentAspectRatioMode]
            }
        )

        if (estaCargando && !controlesVisibles) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, end = 24.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                IndicadorBufferSuperior(progreso = progresoSimulado)
            }
        }

        AnimatedVisibility(
            visible = controlesVisibles && !menuPelisAbierto,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            OverlayCinematograficoTV(
                movieTitle = movieTitle,
                movieImageUrl = movieImageUrl,
                movieSynopsis = movieSynopsis,
                isPlaying = isPlaying,
                estaCargando = estaCargando,
                progresoSimulado = progresoSimulado,
                currentPosition = currentPosition,
                totalDuration = totalDuration,
                bufferedPosition = bufferedPosition,
                tieneOpcionesPistas = pistasAudioDisponibles.size > 1 || pistasSubtitulosDisponibles.isNotEmpty(),
                onInteraction = { lastInteractionTime = System.currentTimeMillis() },
                onSeekingStateChanged = { estaDesplazandoProgreso = it },
                onTogglePlayPause = {
                    lastInteractionTime = System.currentTimeMillis()
                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                },
                onSeekTo = { nuevaPos ->
                    lastInteractionTime = System.currentTimeMillis()
                    player?.seekTo(nuevaPos.coerceIn(0L, totalDuration))
                },
                onToggleMenuPelis = {
                    lastInteractionTime = System.currentTimeMillis()
                    controlesVisibles = false
                    menuPelisAbierto = true
                },
                onAbrirPistas = {
                    lastInteractionTime = System.currentTimeMillis()
                    mostrarDialogoPistas = true
                },
                onCycleAspectRatio = {
                    lastInteractionTime = System.currentTimeMillis()
                    currentAspectRatioMode = (currentAspectRatioMode + 1) % 3
                },
                onHomeExit = {
                    guardarProgresoActual()
                    player?.stop()
                    player?.release()
                    player = null
                    onFinish()
                },
                onAbrirPerfil = {
                    context.startActivity(Intent(context, Perfil::class.java))
                }
            )
        }

        // 🟢 MENÚ CARTELERA CON ALQUILERES DE PRIMERAS
        AnimatedVisibility(
            visible = menuPelisAbierto,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MenuCarteleraOverlay(
                peliculas = peliculasActivas,
                idsAlquileres = idsAlquileresUsuario,
                peliculaActualId = "",
                onSelectMovie = { seleccionada ->
                    guardarProgresoActual()
                    streamUrl = seleccionada.streamUrl
                    movieTitle = seleccionada.title
                    movieCastv = seleccionada.castv
                    movieImageUrl = seleccionada.imageUrl
                    movieSynopsis = seleccionada.overview ?: ""
                    menuPelisAbierto = false
                }
            )
        }
    }

    if (mostrarDialogoContinuar) {
        DialogoContinuarProgreso(
            progresoGuardado = progresoGuardadoEncontrado,
            onReiniciar = {
                mostrarDialogoContinuar = false
                prepararReproductor(0L)
            },
            onReanudar = {
                mostrarDialogoContinuar = false
                prepararReproductor(progresoGuardadoEncontrado)
            }
        )
    }

    if (mostrarDialogoPistas) {
        DialogoSelectorPistas(
            audios = pistasAudioDisponibles,
            subtitulos = pistasSubtitulosDisponibles,
            onDismiss = { mostrarDialogoPistas = false },
            onSelectAudio = { pista ->
                player?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .addOverride(TrackSelectionOverride(pista.trackGroup.mediaTrackGroup, pista.indexPista))
                        .build()
                }
            },
            onSelectSubtitulo = { pista ->
                player?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .addOverride(TrackSelectionOverride(pista.trackGroup.mediaTrackGroup, pista.indexPista))
                        .build()
                }
            },
            onDesactivarSubtitulos = {
                player?.let { p ->
                    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
            }
        )
    }
}

// =========================================================================
// 🟢 OVERLAY CINEMATOGRÁFICO
// =========================================================================
@Composable
fun OverlayCinematograficoTV(
    movieTitle: String,
    movieImageUrl: String,
    movieSynopsis: String,
    isPlaying: Boolean,
    estaCargando: Boolean,
    progresoSimulado: Int,
    currentPosition: Long,
    totalDuration: Long,
    bufferedPosition: Long,
    tieneOpcionesPistas: Boolean,
    onInteraction: () -> Unit,
    onSeekingStateChanged: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleMenuPelis: () -> Unit,
    onAbrirPistas: () -> Unit,
    onCycleAspectRatio: () -> Unit,
    onHomeExit: () -> Unit,
    onAbrirPerfil: () -> Unit
) {
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    var horaActual by remember { mutableStateOf("") }
    var fechaCompletaActual by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val formatHora = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val formatFecha = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es", "ES"))

        while (isActive) {
            val d = Date()
            horaActual = formatHora.format(d).uppercase(Locale.getDefault())
            fechaCompletaActual = formatFecha.format(d).replaceFirstChar { it.uppercase() }
            delay(1000)
        }
    }

    val tiempoRestante = maxOf(0L, totalDuration - currentPosition)
    val strRestante = if (tiempoRestante > 0) {
        val h = TimeUnit.MILLISECONDS.toHours(tiempoRestante)
        val m = TimeUnit.MILLISECONDS.toMinutes(tiempoRestante) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(tiempoRestante) % 60
        String.format(Locale.getDefault(), "-%02d:%02d:%02d", h, m, s)
    } else ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.55f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.70f)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CINE",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "PARCHE",
                    color = ColorDorado,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = horaActual,
                    color = ColorDorado,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = fechaCompletaActual,
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (estaCargando) {
                    Spacer(modifier = Modifier.height(4.dp))
                    IndicadorBufferSuperior(progreso = progresoSimulado)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33000000))
                    .border(0.8.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 110.dp, height = 160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.5.dp, ColorDorado, RoundedCornerShape(8.dp))
                ) {
                    AsyncImage(
                        model = movieImageUrl,
                        contentDescription = movieTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = movieTitle,
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            delayMillis = 800,
                            velocity = 35.dp
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (movieSynopsis.isNotBlank()) movieSynopsis else "Disfruta de la mejor producción del cine en CineParche.",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            BarraProgresoTVDesplazable(
                posicionActual = currentPosition,
                duracionTotal = totalDuration,
                posicionBuffer = bufferedPosition,
                strTiempoActual = DateUtils.formatElapsedTime(currentPosition / 1000),
                strTiempoRestante = strRestante,
                onInteraction = onInteraction,
                onSeekingStateChanged = onSeekingStateChanged,
                onSeekConfirmed = { nuevaPos ->
                    onInteraction()
                    onSeekTo(nuevaPos)
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonIconoControlTV(
                    icono = Icons.Default.Home,
                    descripcion = "Inicio",
                    onInteraction = onInteraction,
                    onClick = onHomeExit
                )
                Spacer(modifier = Modifier.width(18.dp))

                BotonIconoControlTV(
                    icono = Icons.Default.List,
                    descripcion = "Cartelera",
                    onInteraction = onInteraction,
                    onClick = onToggleMenuPelis
                )
                Spacer(modifier = Modifier.width(18.dp))

                BotonIconoControlTV(
                    icono = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Refresh,
                    descripcion = if (isPlaying) "Pausar" else "Reproducir",
                    tamano = 56.dp,
                    colorFondo = ColorDorado,
                    colorIcono = Color.Black,
                    modifier = Modifier.focusRequester(playPauseFocusRequester),
                    onInteraction = onInteraction,
                    onClick = onTogglePlayPause
                )
                Spacer(modifier = Modifier.width(18.dp))

                BotonIconoControlTV(
                    icono = Icons.Default.Settings,
                    descripcion = "Audios y Subtítulos",
                    habilitado = tieneOpcionesPistas,
                    onInteraction = onInteraction,
                    onClick = onAbrirPistas
                )
                Spacer(modifier = Modifier.width(18.dp))

                BotonIconoControlTV(
                    icono = Icons.Default.Share,
                    descripcion = "Aspect Ratio",
                    onInteraction = onInteraction,
                    onClick = onCycleAspectRatio
                )
                Spacer(modifier = Modifier.width(18.dp))

                BotonIconoControlTV(
                    icono = Icons.Default.Person,
                    descripcion = "Perfil",
                    onInteraction = onInteraction,
                    onClick = onAbrirPerfil
                )
            }
        }
    }
}

// =========================================================================
// 🟢 INDICADOR DE BÚFER SUPERIOR (CHIP DISCRETO)
// =========================================================================
@Composable
fun IndicadorBufferSuperior(progreso: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "bufferSuperior")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x99000000))
            .border(1.dp, ColorDorado.copy(alpha = alphaAnim), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        CircularProgressIndicator(
            color = ColorDorado,
            strokeWidth = 2.dp,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Optimizando... $progreso%",
            color = ColorDorado,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// =========================================================================
// 🟢 BARRA DE PROGRESO DESPLAZABLE
// =========================================================================
@Composable
fun BarraProgresoTVDesplazable(
    posicionActual: Long,
    duracionTotal: Long,
    posicionBuffer: Long,
    strTiempoActual: String,
    strTiempoRestante: String,
    onInteraction: () -> Unit,
    onSeekingStateChanged: (Boolean) -> Unit,
    onSeekConfirmed: (Long) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    var targetSeekPosition by remember { mutableLongStateOf(posicionActual) }
    var estaDesplazando by remember { mutableStateOf(false) }

    LaunchedEffect(posicionActual) {
        if (!estaDesplazando) {
            targetSeekPosition = posicionActual
        }
    }

    val posRender = if (estaDesplazando) targetSeekPosition else posicionActual
    val progresoFrac = if (duracionTotal > 0) (posRender.toFloat() / duracionTotal).coerceIn(0f, 1f) else 0f
    val bufferFrac = if (duracionTotal > 0) (posicionBuffer.toFloat() / duracionTotal).coerceIn(0f, 1f) else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.02f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color(0x33FFC107) else Color.Transparent)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = ColorDorado,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onInteraction()
                }
                if (!state.isFocused && estaDesplazando) {
                    onSeekConfirmed(targetSeekPosition)
                    estaDesplazando = false
                    onSeekingStateChanged(false)
                }
            }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    onInteraction()
                    val salto = 15_000L
                    when (keyEvent.key) {
                        Key.DirectionLeft -> {
                            estaDesplazando = true
                            onSeekingStateChanged(true)
                            targetSeekPosition = maxOf(0L, targetSeekPosition - salto)
                            true
                        }
                        Key.DirectionRight -> {
                            estaDesplazando = true
                            onSeekingStateChanged(true)
                            targetSeekPosition = minOf(duracionTotal, targetSeekPosition + salto)
                            true
                        }
                        Key.Enter, Key.DirectionCenter, Key.NumPadEnter -> {
                            if (estaDesplazando) {
                                onSeekConfirmed(targetSeekPosition)
                                estaDesplazando = false
                                onSeekingStateChanged(false)
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = if (estaDesplazando) DateUtils.formatElapsedTime(targetSeekPosition / 1000) else strTiempoActual,
            color = if (isFocused) ColorDorado else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFocused) 8.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF333333))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferFrac)
                    .height(if (isFocused) 8.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray.copy(alpha = 0.5f))
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(progresoFrac)
                    .height(if (isFocused) 8.dp else 5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (estaDesplazando) Color(0xFFFF9800) else ColorDorado)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(progresoFrac)
                    .wrapContentWidth(Alignment.End)
                    .size(if (isFocused) 16.dp else 11.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, if (estaDesplazando) Color(0xFFFF9800) else ColorDorado, CircleShape)
            )
        }

        Text(
            text = if (estaDesplazando) "PULSA OK" else strTiempoRestante,
            color = if (isFocused) ColorDorado else Color.LightGray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// =========================================================================
// 🟢 BOTÓN CON FOCO REMARCADO PARA TV
// =========================================================================
@Composable
fun BotonIconoControlTV(
    icono: ImageVector,
    descripcion: String,
    tamano: Dp = 44.dp,
    habilitado: Boolean = true,
    colorFondo: Color = Color(0xFF222222),
    colorIcono: Color = Color.White,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {},
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(tamano)
            .scale(if (isFocused) 1.18f else 1.0f)
            .clip(CircleShape)
            .background(
                when {
                    isFocused -> Color.White
                    habilitado -> colorFondo
                    else -> colorFondo.copy(alpha = 0.25f)
                }
            )
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) ColorDorado else Color.Gray.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onInteraction()
            }
            .clickable(
                enabled = habilitado,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    onInteraction()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icono,
            contentDescription = descripcion,
            tint = if (isFocused) Color.Black else if (habilitado) colorIcono else colorIcono.copy(alpha = 0.25f),
            modifier = Modifier.size(tamano * 0.52f)
        )
    }
}

// =========================================================================
// 🟢 MENÚ CARTELERA (ALQUILERES PRIMERO EN DORADO, GRATIS EN VERDE)
// =========================================================================
@Composable
fun MenuCarteleraOverlay(
    peliculas: List<Modelo>,
    idsAlquileres: List<String>,
    peliculaActualId: String,
    onSelectMovie: (Modelo) -> Unit
) {
    val primerCardFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            primerCardFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x77000000))
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🍿 PELÍCULAS EN CARTELERA",
                    color = DoradoBrillante,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (idsAlquileres.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "(${idsAlquileres.size} alquiladas activas de primeras)",
                        color = ColorDorado,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                itemsIndexed(peliculas, key = { _, movie -> movie.id }) { index, movie ->
                    var isCardFocused by remember { mutableStateOf(false) }
                    val esAlquilerPersonal = idsAlquileres.contains(movie.id)

                    val createdAtMillis = remember(movie.createdAt) {
                        if (movie.createdAt in 1..999999999999L) movie.createdAt * 1000 else movie.createdAt
                    }
                    val countdownMillis = remember(movie.id, movie.countdownMinutes) {
                        TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
                    }
                    var millisRestantes by remember(movie.id, createdAtMillis) {
                        mutableLongStateOf(maxOf(0L, countdownMillis - (System.currentTimeMillis() - createdAtMillis)))
                    }

                    LaunchedEffect(movie.id, createdAtMillis) {
                        while (millisRestantes > 0) {
                            delay(1000)
                            millisRestantes = maxOf(0L, countdownMillis - (System.currentTimeMillis() - createdAtMillis))
                        }
                    }

                    val h = TimeUnit.MILLISECONDS.toHours(millisRestantes)
                    val m = TimeUnit.MILLISECONDS.toMinutes(millisRestantes) % 60
                    val s = TimeUnit.MILLISECONDS.toSeconds(millisRestantes) % 60
                    val tiempoFormateado = String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)

                    val strTiempo = if (esAlquilerPersonal) "⭐ ALQUILER $tiempoFormateado" else "▶ GRATIS $tiempoFormateado"
                    val colorBadge = if (esAlquilerPersonal) Color(0xE6C5A059) else Color(0xE6107C41)

                    val cardModifier = if (index == 0) {
                        Modifier.focusRequester(primerCardFocusRequester)
                    } else {
                        Modifier
                    }

                    Column(
                        modifier = cardModifier
                            .width(120.dp)
                            .height(170.dp)
                            .scale(if (isCardFocused) 1.10f else 1.0f)
                            .shadow(
                                elevation = if (isCardFocused) 20.dp else 0.dp,
                                shape = RoundedCornerShape(12.dp),
                                spotColor = if (esAlquilerPersonal) ColorDorado else DoradoBrillante
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .border(
                                width = if (isCardFocused) 3.dp else 1.dp,
                                color = if (isCardFocused) ColorDorado else if (esAlquilerPersonal) ColorDorado.copy(alpha = 0.6f) else Color(0x33C5A059),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .onFocusChanged { isCardFocused = it.isFocused }
                            .focusable()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onSelectMovie(movie)
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            AsyncImage(
                                model = movie.imageUrl,
                                contentDescription = movie.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            // Insignia: Dorada si es del perfil, Verde si es gratis de la comunidad
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(colorBadge)
                                    .padding(vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = strTiempo,
                                    color = Color.White,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        val marqueeMod = if (isCardFocused) {
                            Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                delayMillis = 400,
                                velocity = 35.dp
                            )
                        } else {
                            Modifier
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isCardFocused) {
                                        Modifier.background(DegradadoDoradoPremiun)
                                    } else {
                                        Modifier.background(Color(0xFF0F172A))
                                    }
                                )
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text(
                                text = movie.title,
                                color = if (isCardFocused) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = if (isCardFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(marqueeMod)
                            )
                        }
                    }
                }
            }
        }
    }
}
// =========================================================================
// 🟢 DIÁLOGOS Y SELECTOR DE PISTAS
// =========================================================================
@Composable
fun DialogoContinuarProgreso(
    progresoGuardado: Long,
    onReiniciar: () -> Unit,
    onReanudar: () -> Unit
) {
    var segundos by remember { mutableIntStateOf(10) }
    val botonReanudarRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        botonReanudarRequester.requestFocus()
        while (segundos > 0) {
            delay(1000)
            segundos--
        }
        onReanudar()
    }

    val horas = progresoGuardado / 3600000
    val minutos = (progresoGuardado % 3600000) / 60000
    val segs = (progresoGuardado % 60000) / 1000
    val strTiempo = String.format(Locale.getDefault(), "%02d:%02d:%02d", horas, minutos, segs)

    Dialog(onDismissRequest = onReanudar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A122A)),
            modifier = Modifier
                .width(420.dp)
                .wrapContentHeight()
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "¿Deseas continuar?",
                    color = ColorDorado,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Te quedaste en:",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = strTiempo,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BotonDialogoTV(
                        texto = "REINICIAR",
                        esPrincipal = false,
                        onClick = onReiniciar
                    )
                    BotonDialogoTV(
                        texto = "REANUDAR (${segundos}s)",
                        esPrincipal = true,
                        modifier = Modifier.focusRequester(botonReanudarRequester),
                        onClick = onReanudar
                    )
                }
            }
        }
    }
}

@Composable
fun DialogoSelectorPistas(
    audios: List<PistaAudio>,
    subtitulos: List<PistaSubtitulo>,
    onDismiss: () -> Unit,
    onSelectAudio: (PistaAudio) -> Unit,
    onSelectSubtitulo: (PistaSubtitulo) -> Unit,
    onDesactivarSubtitulos: () -> Unit
) {
    val subtitulosApagados = subtitulos.none { it.estaSeleccionada }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141414)),
            modifier = Modifier
                .width(440.dp)
                .heightIn(max = 380.dp)
                .border(1.5.dp, ColorDorado, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "🎧 AUDIO Y SUBTÍTULOS",
                    color = ColorDorado,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Audios:",
                            color = ColorDorado,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        audios.forEach { audio ->
                            ItemPistaTV(
                                texto = audio.nombre,
                                seleccionada = audio.estaSeleccionada,
                                onClick = { onSelectAudio(audio) }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "Subtítulos:",
                            color = ColorDorado,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        ItemPistaTV(
                            texto = "Desactivar Subtítulos",
                            seleccionada = subtitulosApagados,
                            onClick = onDesactivarSubtitulos
                        )

                        subtitulos.forEach { sub ->
                            ItemPistaTV(
                                texto = sub.nombre,
                                seleccionada = sub.estaSeleccionada && !subtitulosApagados,
                                onClick = { onSelectSubtitulo(sub) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    BotonDialogoTV(
                        texto = "LISTO",
                        esPrincipal = true,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
fun ItemPistaTV(
    texto: String,
    seleccionada: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.04f else 1.0f)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused -> Color(0xFF2C2214)
                    seleccionada -> Color(0xFF1E1E1E)
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (isFocused) 2.dp else if (seleccionada) 1.dp else 0.dp,
                color = if (isFocused) ColorDorado else if (seleccionada) ColorDorado.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = if (seleccionada) "➤  $texto" else "    $texto",
            color = if (seleccionada || isFocused) ColorDorado else Color.White,
            fontSize = 12.sp,
            fontWeight = if (seleccionada || isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}