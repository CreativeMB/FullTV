package com.creativem.fulltv.api

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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

// Paleta Dorado Premium CasTV
private val DoradoPrincipal = Color(0xFFC5A059)
private val DoradoBrillante = Color(0xFFEAD295)
private val DegradadoDoradoPremiun = Brush.horizontalGradient(
    listOf(Color(0xFFF3E0AA), Color(0xFFC5A059), Color(0xFF9E7B36))
)

// Datos extra de TMDb para actualización instantánea
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

            // Datos dinámicos de la película enfocada
            var peliculaEnfocada by remember { mutableStateOf<Modelo?>(null) }
            var backdropActual by remember { mutableStateOf("") }
            var sinopsisActual by remember { mutableStateOf("Selecciona una película para ver su sinopsis...") }
            var infoAdicionalActual by remember { mutableStateOf("Obteniendo detalles...") }
            var calificacionActual by remember { mutableStateOf("⭐ 8.0") }

            // Clave del tráiler de YouTube
            var currentTrailerKey by remember { mutableStateOf("") }
            var fetchJob by remember { mutableStateOf<Job?>(null) }

            fun buscarTrailerYDetalles(tmdbIdInt: Int) {
                if (tmdbIdInt == 0) return

                // 1. Consultamos géneros y duración en TMDb
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

                // 2. Buscamos el tráiler en español con fallback al global
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

                // Actualización inmediata con los datos en memoria
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
                    delay(300) // Debounce para navegación ágil
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
                        for (page in 1..5) {
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
                            extraInfoMap.putAll(tempExtras)
                            peliculasList = allModelos
                            isLoading = false
                            if (allModelos.isNotEmpty()) {
                                peliculaEnfocada = null
                                onMovieFocused(allModelos.first())
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

            PantallaPeliculasCompletamenteHorizontal(
                categorias = menuOpciones,
                categoriaSeleccionada = categoriaSeleccionada,
                peliculas = peliculasList,
                isLoading = isLoading,
                peliculaEnfocada = peliculaEnfocada,
                backdropUrl = backdropActual,
                sinopsis = sinopsisActual,
                infoAdicional = infoAdicionalActual,
                calificacion = calificacionActual,
                hasTrailer = currentTrailerKey.isNotEmpty(),
                onCategoriaClick = { cargarCategoria(it) },
                onMovieFocused = { onMovieFocused(it) },
                onVerTrailerClick = {
                    if (currentTrailerKey.isNotEmpty()) {
                        val intent = Intent(context, TrailerActivity::class.java).apply {
                            putExtra("EXTRA_TRAILER_KEY", currentTrailerKey)
                        }
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "Tráiler no disponible para este título", Toast.LENGTH_SHORT).show()
                    }
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
fun PantallaPeliculasCompletamenteHorizontal(
    categorias: List<String>,
    categoriaSeleccionada: String,
    peliculas: List<Modelo>,
    isLoading: Boolean,
    peliculaEnfocada: Modelo?,
    backdropUrl: String,
    sinopsis: String,
    infoAdicional: String,
    calificacion: String,
    hasTrailer: Boolean,
    onCategoriaClick: (String) -> Unit,
    onMovieFocused: (Modelo) -> Unit,
    onVerTrailerClick: () -> Unit,
    onMovieClick: (Modelo) -> Unit
) {
    val carouselListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo cinemático
        Crossfade(
            targetState = backdropUrl,
            animationSpec = tween(500),
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

        // Degradado oscuro para lectura óptima
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99050B14),
                            Color(0xDD050B14),
                            Color(0xF8050B14)
                        )
                    )
                )
        )

        // Contenedor general en una sola pantalla sin scroll vertical
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 🟢 FILA 1: MENÚ DE CATEGORÍAS (Horizontal Superior)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categorias) { cat ->
                    BotonCategoriaDorado(
                        texto = cat,
                        isSelected = cat == categoriaSeleccionada,
                        onClick = { onCategoriaClick(cat) }
                    )
                }
            }

            // 🟢 FILA 2: VITRINA SUPERIOR (Póster + Sinopsis + Botón Tráiler a la derecha)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // A) Póster vertical
                if (peliculaEnfocada != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(peliculaEnfocada.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = peliculaEnfocada.title,
                        modifier = Modifier
                            .width(135.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .border(3.dp, DoradoPrincipal, RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(18.dp))
                }

                // B) Información central: Título con marquesina, metadatos y sinopsis
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    if (peliculaEnfocada != null) {
                        Text(
                            text = peliculaEnfocada.title,
                            color = Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    delayMillis = 1000,
                                    velocity = 40.dp
                                )
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 🟢 FILA 1 DE METADATOS: Fecha + Calificación + Etiqueta ALQUILA (Nunca se estripa)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📅 " + (if (peliculaEnfocada.releaseDate.isNullOrBlank()) "Estreno" else peliculaEnfocada.releaseDate!!),
                                color = Color(0xFFE2E8F0),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = calificacion,
                                color = DoradoBrillante,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            // 🏷️ Insignia ALQUILA protegida contra estiramientos
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x3300E676))
                                    .border(1.dp, Color(0xFF00E676), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ALQUILA YA",
                                    color = Color(0xFF00E676),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp,
                                    maxLines = 1,          // 🟢 Evita partirse en dos líneas
                                    softWrap = false       // 🟢 Fuerza a mantenerse en una sola pieza
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 🟢 FILA 2 DE METADATOS: Géneros y duración con todo el ancho disponible
                        Text(
                            text = infoAdicional,
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = sinopsis,
                            color = Color(0xFFCBD5E1),
                            fontSize = 13.5.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 19.sp
                        )
                    } else {
                        Text(
                            text = "Navega horizontalmente con las flechas del control.",
                            color = Color(0xFF94A3B8),
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // C) Botón de Tráiler (Navegable arriba desde el carrusel de películas)
                BotonVerTrailerTv(
                    hasTrailer = hasTrailer,
                    onClick = onVerTrailerClick
                )
            }

            // 🟢 FILA 3: CARRUSEL DE PELÍCULAS 100% HORIZONTAL CON CENTRADO DINÁMICO
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                Text(
                    text = "CATÁLOGO ($categoriaSeleccionada)",
                    color = DoradoBrillante,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DoradoPrincipal)
                    }
                } else {
                    LazyRow(
                        state = carouselListState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        contentPadding = PaddingValues(start = 0.dp, end = 350.dp)
                    ) {
                        itemsIndexed(peliculas, key = { _, movie -> movie.id }) { index, movie ->
                            MovieHorizontalItemDorado(
                                movie = movie,
                                isSelected = movie.id == peliculaEnfocada?.id,
                                onFocused = {
                                    onMovieFocused(movie)
                                    // Centrado suave en pantalla al desplazarse
                                    scope.launch {
                                        delay(40)
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
                                                    animationSpec = tween(durationMillis = 280)
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

// 🟢 BOTÓN COMPACTO DE TRÁILER (RECIBE FOCO AL PULSAR ARRIBA DESDE EL CARRUSEL)
@Composable
fun BotonVerTrailerTv(
    hasTrailer: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1f,
        animationSpec = tween(150),
        label = "btnTrailerScale"
    )

    Box(
        modifier = Modifier
            .width(200.dp)
            .height(140.dp)
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 22.dp else 0.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = DoradoBrillante
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isFocused) DegradadoDoradoPremiun else Brush.verticalGradient(
                    listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                )
            )
            .border(
                width = if (isFocused) 3.5.dp else 1.5.dp,
                color = if (isFocused) Color.White else DoradoPrincipal.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "▶",
                color = if (isFocused) Color.Black else DoradoBrillante,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (hasTrailer) "Ver Tráiler Oficial" else "Buscando Tráiler...",
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Pantalla Completa",
                color = if (isFocused) Color(0xFF222222) else Color(0xFF94A3B8),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// 🟢 TARJETA DE PELÍCULA EN CARRUSEL HORIZONTAL CON MARQUESINA Y CENTRADO
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MovieHorizontalItemDorado(
    movie: Modelo,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1f,
        animationSpec = tween(150),
        label = "itemScale"
    )

    Column(
        modifier = Modifier
            .width(115.dp)
            .height(155.dp)
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 20.dp else 0.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = DoradoBrillante
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .border(
                width = if (isFocused) 3.5.dp else if (isSelected) 2.dp else 1.dp,
                color = if (isFocused) DoradoBrillante else if (isSelected) DoradoPrincipal else Color(0x33C5A059),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocused()
                }
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFocused) {
                        Modifier.background(DegradadoDoradoPremiun)
                    } else {
                        Modifier.background(Color(0xFF0F172A))
                    }
                )
                .padding(vertical = 4.dp, horizontal = 6.dp)
        ) {
            val marqueeMod = if (isFocused) {
                Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    delayMillis = 400,
                    velocity = 35.dp
                )
            } else {
                Modifier
            }

            Text(
                text = movie.title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(marqueeMod)
            )
        }
    }
}

// 🟢 BOTONES DE CATEGORÍA
@Composable
fun BotonCategoriaDorado(
    texto: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1f,
        animationSpec = tween(150),
        label = "catScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 14.dp else 0.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = DoradoBrillante
            )
            .clip(RoundedCornerShape(20.dp))
            .then(
                when {
                    isFocused -> Modifier.background(DegradadoDoradoPremiun)
                    isSelected -> Modifier.background(DoradoPrincipal)
                    else -> Modifier.background(Color(0xFF1E293B))
                }
            )
            .border(
                width = if (isFocused) 3.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = texto,
            color = if (isSelected || isFocused) Color.Black else Color.White,
            fontWeight = if (isSelected || isFocused) FontWeight.ExtraBold else FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}