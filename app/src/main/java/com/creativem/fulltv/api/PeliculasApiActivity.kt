package com.creativem.fulltv.api

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Modelo
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.awaitResponse
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.abs

private val DoradoPrincipal = Color(0xFFC5A059)
private val DoradoBrillante = Color(0xFFFFD56B)
private val FondoNegroSala = Color(0xFF07090E)

private data class ExtraMovieInfo(
    val backdropUrl: String,
    val overview: String,
    val voteAverage: Double
)

class PeliculasApiActivity : ComponentActivity() {

    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setupApiService()

        setContent {
            val context = LocalContext.current
            val scope = rememberCoroutineScope()

            val menuOpciones = remember {
                listOf(
                    "Populares", "Mejor valoradas", "En cartelera", "Acción", "Aventura", "Animación",
                    "Comedia", "Crimen", "Documental", "Drama", "Familia", "Fantasía",
                    "Historia", "Terror", "Música", "Misterio", "Romance", "Ciencia ficción",
                    "Película de TV", "Suspenso", "Bélica", "Western"
                )
            }

            var categoriaSeleccionada by remember { mutableStateOf("Populares") }
            var peliculasList by remember { mutableStateOf<List<Modelo>>(emptyList()) }
            val extraInfoMap = remember { mutableStateMapOf<String, ExtraMovieInfo>() }
            var isLoading by remember { mutableStateOf(false) }

            var peliculaEnfocada by remember { mutableStateOf<Modelo?>(null) }
            var backdropActual by remember { mutableStateOf("") }
            var sinopsisActual by remember { mutableStateOf("Selecciona una película para ver su sinopsis...") }
            var infoAdicionalActual by remember { mutableStateOf("Cargando detalles...") }
            var calificacionActual by remember { mutableStateOf("⭐ 8.0") }

            var currentTrailerKey by remember { mutableStateOf("") }
            var fetchJob by remember { mutableStateOf<Job?>(null) }

            fun buscarTrailerYDetalles(tmdbIdInt: Int) {
                if (tmdbIdInt == 0) return

                apiService.getMovieDetails(tmdbIdInt, apiKey, "es-MX").enqueue(object : Callback<MovieDetailResponse> {
                    override fun onResponse(call: Call<MovieDetailResponse>, res: Response<MovieDetailResponse>) {
                        if (res.isSuccessful) {
                            val detalles = res.body()
                            val generos = detalles?.genres?.take(3)?.joinToString(" • ") { it.name } ?: "Cine"
                            val duracion = detalles?.runtime ?: 0
                            infoAdicionalActual = "🎭 $generos" + if (duracion > 0) "   ⏱ ${duracion} min" else ""
                        }
                    }
                    override fun onFailure(call: Call<MovieDetailResponse>, t: Throwable) {
                        infoAdicionalActual = "🎭 Cine General"
                    }
                })

                apiService.getMovieVideos(tmdbIdInt, apiKey, "es-MX")
                    .enqueue(object : Callback<ApiPeliculaActivity.VideoResponse> {
                        override fun onResponse(
                            call: Call<ApiPeliculaActivity.VideoResponse>,
                            response: Response<ApiPeliculaActivity.VideoResponse>
                        ) {
                            var keyEncontrada = ""
                            if (response.isSuccessful) {
                                val resultados = response.body()?.results ?: emptyList()
                                val trailer = resultados.find { it.site == "YouTube" && it.type == "Trailer" }
                                    ?: resultados.find { it.site == "YouTube" }
                                if (trailer != null) keyEncontrada = trailer.key
                            }

                            if (keyEncontrada.isNotEmpty()) {
                                currentTrailerKey = keyEncontrada
                            } else {
                                apiService.getMovieVideos(tmdbIdInt, apiKey, "")
                                    .enqueue(object : Callback<ApiPeliculaActivity.VideoResponse> {
                                        override fun onResponse(
                                            call: Call<ApiPeliculaActivity.VideoResponse>,
                                            res: Response<ApiPeliculaActivity.VideoResponse>
                                        ) {
                                            if (res.isSuccessful) {
                                                val resultados = res.body()?.results ?: emptyList()
                                                val trailer = resultados.find { it.site == "YouTube" && it.type == "Trailer" }
                                                    ?: resultados.find { it.site == "YouTube" }
                                                if (trailer != null) {
                                                    currentTrailerKey = trailer.key
                                                }
                                            }
                                        }
                                        override fun onFailure(call: Call<ApiPeliculaActivity.VideoResponse>, t: Throwable) {}
                                    })
                            }
                        }
                        override fun onFailure(call: Call<ApiPeliculaActivity.VideoResponse>, t: Throwable) {}
                    })
            }

            fun onMovieFocused(movie: Modelo) {
                if (peliculaEnfocada?.id == movie.id) return

                peliculaEnfocada = movie
                val extra = extraInfoMap[movie.id]

                backdropActual = extra?.backdropUrl?.ifBlank { movie.imageUrl } ?: movie.imageUrl
                calificacionActual = "⭐ ${extra?.voteAverage ?: "8.0"}"
                sinopsisActual = if (!extra?.overview.isNullOrBlank()) {
                    extra!!.overview
                } else {
                    "Disfruta de esta increíble producción ahora en CineParche."
                }
                infoAdicionalActual = "Cargando detalles..."
                currentTrailerKey = ""

                fetchJob?.cancel()
                fetchJob = scope.launch {
                    delay(250)
                    val tmdbId = movie.id.toIntOrNull() ?: 0
                    buscarTrailerYDetalles(tmdbId)
                }
            }

            fun cargarCategoria(categoria: String) {
                categoriaSeleccionada = categoria
                isLoading = true
                scope.launch(Dispatchers.IO) {
                    val allModelos = mutableListOf<Modelo>()
                    val tempExtras = mutableMapOf<String, ExtraMovieInfo>()

                    try {
                        for (page in 1..4) {
                            val response = obtenerLlamadaApi(categoria, page).awaitResponse()
                            if (response.isSuccessful) {
                                val mapped = response.body()?.results?.map { movie ->
                                    val idStr = movie.id.toString()
                                    val posterUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
                                    val backdropUrl = if (!movie.backdrop_path.isNullOrBlank()) {
                                        "https://image.tmdb.org/t/p/w780${movie.backdrop_path}"
                                    } else {
                                        posterUrl
                                    }

                                    tempExtras[idStr] = ExtraMovieInfo(
                                        backdropUrl = backdropUrl,
                                        overview = movie.overview ?: "",
                                        voteAverage = movie.vote_average ?: 8.0
                                    )

                                    Modelo(
                                        id = idStr,
                                        title = movie.title,
                                        originalTitle = movie.original_title,
                                        imageUrl = posterUrl,
                                        streamUrl = "https://tuservidor.com/stream/${movie.id}",
                                        castv = 10,
                                        countdownMinutes = 0,
                                        createdAt = 0L,
                                        releaseDate = movie.release_date ?: ""
                                    )
                                } ?: emptyList()
                                allModelos.addAll(mapped)
                            }
                        }
                        withContext(Dispatchers.Main) {
                            val peliculasSinDuplicados = allModelos.distinctBy { it.id }
                            extraInfoMap.putAll(tempExtras)
                            peliculasList = peliculasSinDuplicados
                            isLoading = false
                            if (peliculasSinDuplicados.isNotEmpty()) {
                                peliculaEnfocada = null
                                onMovieFocused(peliculasSinDuplicados.first())
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("API_ERROR", "Error cargando $categoria", e)
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                }
            }

            LaunchedEffect(Unit) {
                cargarCategoria("Populares")
            }

            BackHandler {
                finish()
            }

            PantallaPeliculasHorizontalConMenuIzquierdo(
                categorias = menuOpciones,
                categoriaSeleccionada = categoriaSeleccionada,
                peliculas = peliculasList,
                isLoading = isLoading,
                peliculaEnfocada = peliculaEnfocada,
                backdropUrl = backdropActual,
                sinopsis = sinopsisActual,
                infoAdicional = infoAdicionalActual,
                calificacion = calificacionActual,
                onCategoriaClick = { cargarCategoria(it) },
                onMovieFocused = { onMovieFocused(it) },
                onVerTrailerClick = {
                    val intent = Intent(context, TrailerActivity::class.java).apply {
                        putExtra("EXTRA_TRAILER_KEY", currentTrailerKey)
                        putExtra("EXTRA_MOVIE_TITLE", peliculaEnfocada?.title ?: "")
                    }
                    context.startActivity(intent)
                },
                onMovieClick = { movie ->
                    val intent = Intent(context, ApiPeliculaActivity::class.java).apply {
                        putExtra("EXTRA_STREAM_URL", movie.streamUrl)
                        putExtra("EXTRA_MOVIE_TITLE", movie.title)
                        putExtra("EXTRA_MOVIE_CASTV", movie.castv)
                        putExtra("EXTRA_MOVIE_IMAGE_URL", movie.imageUrl)
                        putExtra("EXTRA_ORIGINAL_TITLE", movie.originalTitle)
                        putExtra("EXTRA_COUNTDOWN", movie.countdownMinutes)
                        putExtra("EXTRA_CREATED_AT", movie.createdAt)
                        putExtra("EXTRA_MOVIE_DATA", movie)
                    }
                    context.startActivity(intent)
                }
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

    private fun setupApiService() {
        val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        apiService = retrofit.create(TMDbApiService::class.java)
    }

    private fun obtenerLlamadaApi(categoria: String, page: Int): Call<MovieResponse> {
        return when (categoria) {
            "Populares" -> apiService.getPopularMovies(apiKey, "es-MX", page)
            "Mejor valoradas" -> apiService.getTopRatedMovies(apiKey, "es-MX", page)
            "En cartelera" -> apiService.getNowPlayingMovies(apiKey, "es-MX", page)
            else -> {
                val genreId = obtenerIdGenero(categoria)
                apiService.getMoviesByGenre(apiKey, "es-MX", genreId, page)
            }
        }
    }

    private fun obtenerIdGenero(genero: String): Int {
        return when (genero) {
            "Acción" -> 28; "Aventura" -> 12; "Animación" -> 16
            "Comedia" -> 35; "Crimen" -> 80; "Documental" -> 99
            "Drama" -> 18; "Familia" -> 10751; "Fantasía" -> 14
            "Historia" -> 36; "Terror" -> 27; "Música" -> 10402
            "Misterio" -> 9648; "Romance" -> 10749; "Ciencia ficción" -> 878
            "Película de TV" -> 10770; "Suspenso" -> 53; "Bélica" -> 10752
            "Western" -> 37; else -> 28
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

// ======================== PANTALLA PRINCIPAL HORIZONTAL ========================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaPeliculasHorizontalConMenuIzquierdo(
    categorias: List<String>,
    categoriaSeleccionada: String,
    peliculas: List<Modelo>,
    isLoading: Boolean,
    peliculaEnfocada: Modelo?,
    backdropUrl: String,
    sinopsis: String,
    infoAdicional: String,
    calificacion: String,
    onCategoriaClick: (String) -> Unit,
    onMovieFocused: (Modelo) -> Unit,
    onVerTrailerClick: () -> Unit,
    onMovieClick: (Modelo) -> Unit
) {
    val carouselListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val config = LocalConfiguration.current
    val esTv = config.screenWidthDp > 600

    val menuFocusRequester = remember { FocusRequester() }
    val carouselFocusRequester = remember { FocusRequester() }
    val trailerFocusRequester = remember { FocusRequester() }
    val firstMovieFocusRequester = remember { FocusRequester() }

    // Salto automático al primer ítem de la categoría
    LaunchedEffect(peliculas) {
        if (peliculas.isNotEmpty()) {
            carouselListState.scrollToItem(0)
            delay(120)
            try {
                firstMovieFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(FondoNegroSala)) {
        // 🟢 FONDO CINEMÁTICO (BACKDROP) MUCHO MÁS VISIBLE
        Crossfade(
            targetState = backdropUrl,
            animationSpec = tween(400),
            label = "bgCrossfade"
        ) { url ->
            if (url.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // 🟢 DEGRADADO CINEMÁTICO LATERAL: Oscuro a la izquierda para leer, transparente al centro/derecha para ver la imagen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xF607090E), // Fondo firme en el texto
                            Color(0xD907090E),
                            Color(0x7507090E), // Muy translúcido para apreciar el póster de fondo
                            Color(0x3507090E)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 12.dp)
        ) {
            // MENÚ VERTICAL ULTRA COMPACTO (Izquierda)
            MenuCategoriasVertical(
                categorias = categorias,
                categoriaSeleccionada = categoriaSeleccionada,
                esTv = esTv,
                menuFocusRequester = menuFocusRequester,
                carouselFocusRequester = firstMovieFocusRequester,
                onCategoriaClick = onCategoriaClick
            )

            Spacer(modifier = Modifier.width(14.dp))

            // CONTENIDO PRINCIPAL APROVECHANDO TODA LA PANTALLA
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 🟢 SECCIÓN SUPERIOR: PÓSTER EXTRA GRANDE (200.dp x 280.dp) + TEXTOS GRANDES
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.38f)
                        .padding(top = 2.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Póster grande destacado sin vacíos
                    if (peliculaEnfocada != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(peliculaEnfocada.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = peliculaEnfocada.title,
                            modifier = Modifier
                                .width(if (esTv) 200.dp else 140.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .border(2.5.dp, DoradoPrincipal, RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(20.dp))
                    }

                    // Información central completa y de tamaño grande para TV
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Top
                    ) {
                        if (peliculaEnfocada != null) {
                            Text(
                                text = peliculaEnfocada.title,
                                color = Color.White,
                                fontSize = if (esTv) 32.sp else 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 1000)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Metadatos (Año, Calificación, Insignia Alquila aquí en CineParche)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "📅 " + (peliculaEnfocada.releaseDate?.take(4) ?: "Estreno"),
                                    color = Color(0xFFE2E8F0),
                                    fontSize = if (esTv) 15.sp else 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                Text(
                                    text = calificacion,
                                    color = DoradoBrillante,
                                    fontSize = if (esTv) 16.sp else 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )

                                Spacer(modifier = Modifier.width(14.dp))

                                // 🟢 INSIGNIA LIMPIA: Alquila aquí en CineParche (sin mayúsculas agresivas)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x3300E676))
                                        .border(1.dp, Color(0xFF00E676), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 9.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = "Alquila aquí en CineParche",
                                        color = Color(0xFF00E676),
                                        fontSize = if (esTv) 12.sp else 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Géneros y duración
                            Text(
                                text = infoAdicional,
                                color = Color(0xFFCBD5E1),
                                fontSize = if (esTv) 14.sp else 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // 🟢 SINOPSIS COMPLETA Y EXTENSA (HASTA 8 RENGLONES PARA NO DEJAR VACÍOS)
                            Text(
                                text = sinopsis,
                                color = Color(0xFFE2E8F0),
                                fontSize = if (esTv) 15.5.sp else 12.5.sp,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = if (esTv) 22.sp else 17.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = "Navega con las flechas del control para ver detalles.",
                                color = Color(0xFF94A3B8),
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // 🟢 FILA UNIFICADA: SOLO CATÁLOGO Y CATEGORÍA (SIN TEXTO EXTRA) + BOTÓN TRÁILER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Catálogo: $categoriaSeleccionada",
                        color = DoradoBrillante,
                        fontSize = if (esTv) 16.sp else 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )

                    // Botón en formato pregunta alineado a la derecha de la barra
                    BotonTrailerCentradoPregunta(
                        esTv = esTv,
                        trailerFocusRequester = trailerFocusRequester,
                        carouselFocusRequester = firstMovieFocusRequester,
                        menuFocusRequester = menuFocusRequester,
                        onClick = onVerTrailerClick
                    )
                }

                // SECCIÓN INFERIOR: CARRUSEL HORIZONTAL CON PÓSTERS AMPLIOS
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.02f)
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DoradoPrincipal, strokeWidth = 3.dp)
                        }
                    } else {
                        LazyRow(
                            state = carouselListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .focusRequester(carouselFocusRequester)
                                .focusProperties {
                                    left = menuFocusRequester
                                    up = trailerFocusRequester
                                },
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(end = 220.dp)
                        ) {
                            itemsIndexed(peliculas, key = { index, movie -> "${movie.id}_$index" }) { index, movie ->
                                val itemModifier = if (index == 0) {
                                    Modifier.focusRequester(firstMovieFocusRequester)
                                } else {
                                    Modifier
                                }

                                MovieHorizontalItemDorado(
                                    movie = movie,
                                    esTv = esTv,
                                    isSelected = movie.id == peliculaEnfocada?.id,
                                    modifier = itemModifier,
                                    onFocused = {
                                        onMovieFocused(movie)
                                        scope.launch {
                                            delay(30)
                                            val visibleItems = carouselListState.layoutInfo.visibleItemsInfo
                                            val currentItem = visibleItems.find { it.index == index }
                                            if (currentItem != null) {
                                                val viewportWidth = carouselListState.layoutInfo.viewportSize.width
                                                val itemCenter = currentItem.offset + (currentItem.size / 2)
                                                val targetCenter = viewportWidth / 2
                                                val scrollDelta = itemCenter - targetCenter
                                                if (abs(scrollDelta) > 10) {
                                                    carouselListState.animateScrollBy(
                                                        value = scrollDelta.toFloat(),
                                                        animationSpec = tween(durationMillis = 240)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = { onMovieClick(movie) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// 🟢 MENÚ LATERAL IZQUIERDO VERTICAL ULTRA ANGOSTO (ICONO + TEXTO)
// ====================================================================
@Composable
fun MenuCategoriasVertical(
    categorias: List<String>,
    categoriaSeleccionada: String,
    esTv: Boolean,
    menuFocusRequester: FocusRequester,
    carouselFocusRequester: FocusRequester,
    onCategoriaClick: (String) -> Unit
) {
    val anchoMenu = if (esTv) 72.dp else 58.dp

    Box(
        modifier = Modifier
            .width(anchoMenu)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F121A))
            .border(1.dp, Color(0xFF1E2433), RoundedCornerShape(10.dp))
            .focusRequester(menuFocusRequester)
            .focusProperties {
                right = carouselFocusRequester
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(categorias) { _, cat ->
                val isSelected = cat == categoriaSeleccionada
                var isFocused by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (esTv) 52.dp else 44.dp)
                        .padding(horizontal = 4.dp)
                        .scale(if (isFocused) 1.05f else 1.0f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                isFocused -> Color(0xFF262626)
                                isSelected -> Color(0x33C5A059)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            width = if (isFocused) 1.5.dp else if (isSelected) 1.dp else 0.dp,
                            color = if (isFocused) DoradoPrincipal else if (isSelected) DoradoPrincipal.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onCategoriaClick(cat)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = obtenerIconoGenero(cat),
                            fontSize = if (esTv) 16.sp else 13.sp
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = cat,
                            color = if (isFocused || isSelected) DoradoBrillante else Color(0xFFCBD5E1),
                            fontSize = if (esTv) 9.5.sp else 8.sp,
                            fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ====================================================================
// 🟢 BOTÓN EN FORMATO PREGUNTA (AMPLIO Y VISIBLE)
// ====================================================================
@Composable
fun BotonTrailerCentradoPregunta(
    esTv: Boolean,
    trailerFocusRequester: FocusRequester,
    carouselFocusRequester: FocusRequester,
    menuFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .wrapContentWidth()
            .height(if (esTv) 38.dp else 32.dp)
            .scale(if (isFocused) 1.05f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) Color.White else Color(0xFF1E293B))
            .border(
                width = if (isFocused) 2.5.dp else 1.2.dp,
                color = if (isFocused) DoradoPrincipal else Color(0xFF334155),
                shape = RoundedCornerShape(8.dp)
            )
            .focusRequester(trailerFocusRequester)
            .focusProperties {
                down = carouselFocusRequester
                up = menuFocusRequester
                left = menuFocusRequester
            }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
            .padding(horizontal = if (esTv) 20.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "▶",
                color = if (isFocused) Color.Black else DoradoPrincipal,
                fontSize = if (esTv) 13.sp else 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "¿Quieres ver el tráiler?",
                color = if (isFocused) Color.Black else Color.White,
                fontSize = if (esTv) 13.sp else 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ====================================================================
// 🟢 TARJETA DE PELÍCULA EN CARRUSEL (CON MARQUESINA EN ENFOQUE O SELECCIÓN)
// ====================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieHorizontalItemDorado(
    movie: Modelo,
    esTv: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(if (esTv) 130.dp else 108.dp)
            .fillMaxHeight()
            .scale(if (isFocused) 1.06f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141923))
            .border(
                width = if (isFocused) 2.5.dp else if (isSelected) 1.5.dp else 0.5.dp,
                color = if (isFocused) DoradoBrillante else if (isSelected) DoradoPrincipal else Color(0xFF262E3D),
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocused()
                }
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onClick()
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(movie.imageUrl)
                .crossfade(true)
                .build(),
            contentDescription = movie.title,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentScale = ContentScale.Crop
        )

        // 🟢 Marquesina activa si está enfocada O si es la película actualmente seleccionada
        val marqueeMod = if (isFocused || isSelected) {
            Modifier.basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 600, velocity = 35.dp)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) DoradoPrincipal else Color(0xFF0C1017))
                .padding(vertical = 4.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = movie.title,
                color = if (isFocused) Color.Black else if (isSelected) DoradoBrillante else Color.White,
                fontSize = if (esTv) 11.5.sp else 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = if (isFocused || isSelected) TextOverflow.Clip else TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(marqueeMod)
            )
        }
    }
}

// Helper de iconos alusivos ultraligeros
private fun obtenerIconoGenero(categoria: String): String {
    return when (categoria) {
        "Populares" -> "🔥"
        "Mejor valoradas" -> "🏆"
        "En cartelera" -> "🎟️"
        "Acción" -> "💥"
        "Aventura" -> "🗺️"
        "Animación" -> "🎨"
        "Comedia" -> "😂"
        "Crimen" -> "🕵️"
        "Documental" -> "📽️"
        "Drama" -> "🎭"
        "Familia" -> "👨‍👩‍👧"
        "Fantasía" -> "🧙"
        "Historia" -> "📜"
        "Terror" -> "👻"
        "Música" -> "🎵"
        "Misterio" -> "🔍"
        "Romance" -> "❤️"
        "Ciencia ficción" -> "🚀"
        "Película de TV" -> "📺"
        "Suspenso" -> "⚡"
        "Bélica" -> "🪖"
        "Western" -> "🤠"
        else -> "🎬"
    }
}