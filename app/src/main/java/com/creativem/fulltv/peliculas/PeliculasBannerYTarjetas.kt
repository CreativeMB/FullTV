package com.creativem.fulltv.peliculas

import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.creativem.fulltv.R
import com.creativem.fulltv.api.TMDbApiService
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.Modelo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

// ==========================================
// 🟢 CONSTANTES Y MODELOS COMPARTIDOS
// ==========================================
val ColorDorado = Color(0xFFC5A059)
val ColorFondoOscuro = Color(0xFF0A122A)

data class TmdbResultadoItem(
    val id: String,
    val title: String,
    val originalTitle: String,
    val releaseDate: String,
    val posterPath: String,
    val backdropPath: String,
    val overview: String,
    val voteAverage: Double
)

// ==========================================
// 🟢 CARGADOR DE IMÁGENES EFICIENTE (GLIDE)
// ==========================================
@Composable
fun GlideImageCustom(
    url: String?,
    modifier: Modifier = Modifier,
    scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP,
    isBackdrop: Boolean = false
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                this.scaleType = scaleType
                if (isBackdrop) {
                    setBackgroundColor(android.graphics.Color.BLACK)
                } else {
                    setImageResource(R.drawable.pelifondo)
                }
            }
        },
        update = { imageView ->
            if (isBackdrop) {
                imageView.setBackgroundColor(android.graphics.Color.BLACK)
            }

            if (!url.isNullOrBlank()) {
                val placeholderDrawable = if (isBackdrop) {
                    ColorDrawable(android.graphics.Color.BLACK)
                } else {
                    androidx.core.content.ContextCompat.getDrawable(imageView.context, R.drawable.pelifondo)
                }

                Glide.with(imageView.context)
                    .load(url)
                    .placeholder(placeholderDrawable)
                    .error(placeholderDrawable)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .into(imageView)
            } else {
                if (isBackdrop) {
                    imageView.setImageResource(0)
                    imageView.setBackgroundColor(android.graphics.Color.BLACK)
                } else {
                    imageView.setImageResource(R.drawable.pelifondo)
                }
            }
        }
    )
}

// ==========================================
// 🟢 BANNER HERO ESTILO NETFLIX (DISEÑO PRÉMIUM)
// ==========================================
@Composable
fun BannerNetflixHero(
    movie: Modelo,
    promosList: List<Modelo>,
    selectedIndex: Int,
    apiService: TMDbApiService,
    apiKey: String,
    bannerFocusRequester: FocusRequester,
    gridFocusRequester: FocusRequester,
    menuFocusRequester: FocusRequester,
    onBannerFocusState: (Boolean) -> Unit,
    onSelectPromo: (Int) -> Unit,
    onClickPromo: () -> Unit = {},
    onPlayMovie: (Modelo) -> Unit
) {
    var tmdbTitle by remember(movie.id) { mutableStateOf(movie.title) }
    var tmdbOverview by remember(movie.id) { mutableStateOf(movie.overview.ifBlank { "Estreno exclusivo" }) }
    var tmdbRating by remember(movie.id) { mutableStateOf("⭐ 8.5") }
    var tmdbInfoAdicional by remember(movie.id) { mutableStateOf(movie.genres.ifBlank { "Acción • Aventura • Cine" }) }
    var backdropUrl by remember(movie.id) { mutableStateOf(movie.imageUrl) }
    var posterUrl by remember(movie.id) { mutableStateOf(movie.imageUrl) }

    val durationMillis = remember(movie.id, movie.countdownMinutes) {
        TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
    }
    val createdAtMillis = remember(movie.id, movie.createdAt) {
        if (movie.createdAt in 1..999999999999L) movie.createdAt * 1000 else movie.createdAt
    }
    var millisRestantes by remember(movie.id, movie.countdownMinutes, movie.createdAt) {
        val transcurrido = System.currentTimeMillis() - createdAtMillis
        mutableLongStateOf(maxOf(0L, durationMillis - transcurrido))
    }

    LaunchedEffect(movie.id, movie.countdownMinutes, movie.createdAt) {
        while (millisRestantes > 0) {
            delay(1000)
            val transcurrido = System.currentTimeMillis() - createdAtMillis
            millisRestantes = maxOf(0L, durationMillis - transcurrido)
        }
    }

    LaunchedEffect(movie.id, movie.title, movie.originalTitle) {
        val resolved = PeliculasActivity.bannerTMDBResolved
        if (resolved != null && resolved.movieId == movie.id) {
            tmdbTitle = resolved.title
            tmdbOverview = resolved.overview
            tmdbRating = resolved.rating
            tmdbInfoAdicional = resolved.infoAdicional
            backdropUrl = resolved.backdropUrl
            posterUrl = resolved.posterUrl
            return@LaunchedEffect
        }

        val query = movie.originalTitle.ifBlank { movie.title }
        try {
            val resp = withContext(Dispatchers.IO) {
                apiService.searchMovie(apiKey, "es-MX", query).execute()
            }
            if (resp.isSuccessful) {
                val r = resp.body()?.results?.firstOrNull()
                if (r != null) {
                    val fechaCompleta = r.release_date ?: ""
                    tmdbTitle = if (fechaCompleta.isNotEmpty()) "${r.title} ($fechaCompleta)" else r.title
                    tmdbOverview = r.overview ?: movie.overview
                    val anio = r.release_date?.take(4) ?: "2026"
                    val cal = if (r.vote_average > 0) String.format(Locale.US, "%.1f", r.vote_average) else "8.5"
                    tmdbRating = "⭐ $cal   |   $anio"

                    backdropUrl = "https://image.tmdb.org/t/p/w1280${r.backdrop_path ?: r.poster_path}"
                    posterUrl = "https://image.tmdb.org/t/p/w500${r.poster_path ?: r.backdrop_path}"

                    val detResp = withContext(Dispatchers.IO) {
                        apiService.getMovieDetails(r.id, apiKey, "es-MX").execute()
                    }
                    if (detResp.isSuccessful) {
                        val detalles = detResp.body()
                        val generos = detalles?.genres?.joinToString(" • ") { it.name } ?: "Acción"
                        val duracion = detalles?.runtime ?: 0
                        tmdbInfoAdicional = "🎭 $generos  ⏱️ ${duracion} Min"
                    }
                }
            }
        } catch (_: Exception) {}
    }

    val horas = TimeUnit.MILLISECONDS.toHours(millisRestantes)
    val minutos = TimeUnit.MILLISECONDS.toMinutes(millisRestantes) % 60
    val segundos = TimeUnit.MILLISECONDS.toSeconds(millisRestantes) % 60
    val contadorFormato = String.format("▶ %02d:%02d:%02d", horas, minutos, segundos)

    val carouselState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(255.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        GlideImageCustom(
            url = backdropUrl,
            modifier = Modifier.fillMaxSize(),
            isBackdrop = true
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.96f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PÓSTER GRANDE PRINCIPAL
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .border(2.dp, ColorDorado, RoundedCornerShape(8.dp))
            ) {
                GlideImageCustom(url = posterUrl, modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // ZONA SUPERIOR: TÍTULO + ETIQUETA PROMOCIONAL + DATOS
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = tmdbTitle,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // 🟢 ETIQUETA LLAMATIVA Y ELEGANTE (CORTESÍA DE CINEPARCHE)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    // 🟢 Fondo oscuro cinematográfico con tinte esmeralda/dorado para máximo contraste
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xF506150E), // Negro esmeralda profundo
                                            Color(0xF5171306)  // Negro dorado profundo
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF00E676), // Verde Neón
                                            Color(0xFFFFD56B)  // Dorado Premium
                                        )
                                    ),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 9.dp, vertical = 3.5.dp)
                        ) {
                            Text(
                                text = "🎁 DISPONIBLE AL PÚBLICO • CORTESÍA CINEPARCHE",
                                color = Color(0xFF69F0AE), // Verde menta brillante (resalta mucho más que el verde plano)
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp, // 🟢 Detalle clave para que el texto mayúsculo se vea prémium
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = tmdbRating, color = ColorDorado, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = contadorFormato, color = ColorDorado, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = tmdbInfoAdicional,
                        color = Color(0xFFD4AF37),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = tmdbOverview,
                        color = Color.LightGray,
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        lineHeight = 15.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 🟢 CAJUELA CON MINI PÓSTERS LIMPIOS (SIN EL TEXTO "LIBRE" ENCIMA)
                LazyRow(
                    state = carouselState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .focusRequester(bannerFocusRequester)
                        .onFocusChanged { onBannerFocusState(it.hasFocus) }
                        .focusProperties {
                            down = gridFocusRequester
                            left = menuFocusRequester
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(end = 40.dp)
                ) {
                    itemsIndexed(promosList) { idx, promoMovie ->
                        var isItemFocused by remember { mutableStateOf(false) }
                        val isSelected = idx == selectedIndex

                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(74.dp)
                                .scale(if (isItemFocused) 1.08f else 1.0f)
                                .zIndex(if (isItemFocused) 10f else 1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E1E1E))
                                .border(
                                    width = if (isItemFocused) 2.dp else if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isItemFocused) ColorDorado else if (isSelected) Color.White else Color.Gray.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .onFocusChanged {
                                    isItemFocused = it.isFocused
                                    if (it.isFocused) {
                                        onSelectPromo(idx)
                                    }
                                }
                                .focusable()
                                .clickable {
                                    onPlayMovie(promoMovie)
                                }
                        ) {
                            // Póster 100% visible sin ningún cartel tapándolo
                            GlideImageCustom(
                                url = promoMovie.imageUrl,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 🟢 TARJETA DE PELÍCULA (SIN SALTOS DE LÍNEA)
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeliculaCard(
    movie: Modelo,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    val durationMillis = remember(movie.id, movie.countdownMinutes) {
        TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
    }
    val createdAtMillis = remember(movie.id, movie.createdAt) {
        if (movie.createdAt in 1..999999999999L) movie.createdAt * 1000 else movie.createdAt
    }

    var millisRestantes by remember(movie.id, movie.countdownMinutes, movie.createdAt) {
        if (movie.countdownMinutes > 0) {
            val transcurrido = System.currentTimeMillis() - createdAtMillis
            mutableLongStateOf(maxOf(0L, durationMillis - transcurrido))
        } else {
            mutableLongStateOf(0L)
        }
    }

    if (movie.countdownMinutes > 0 && millisRestantes > 0) {
        LaunchedEffect(movie.id, movie.countdownMinutes, movie.createdAt) {
            while (millisRestantes > 0) {
                delay(1000)
                val transcurrido = System.currentTimeMillis() - createdAtMillis
                millisRestantes = maxOf(0L, durationMillis - transcurrido)
            }
        }
    }

    val esPromoActiva = movie.countdownMinutes > 0 && millisRestantes > 0
    val esValida = remember(movie.id, movie.isValid) {
        movie.isValid || Validacioneslista.obtenerPeliculasValidas().any { it.id == movie.id }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .padding(2.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) {
                    coroutineScope.launch {
                        bringIntoViewRequester.bringIntoView()
                    }
                }
            }
            .focusable()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) ColorDorado else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            GlideImageCustom(url = movie.imageUrl, modifier = Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )

            when {
                esPromoActiva -> {
                    val h = TimeUnit.MILLISECONDS.toHours(millisRestantes)
                    val m = TimeUnit.MILLISECONDS.toMinutes(millisRestantes) % 60
                    val s = TimeUnit.MILLISECONDS.toSeconds(millisRestantes) % 60
                    val tiempoStr = String.format("▶ %02d:%02d:%02d", h, m, s)

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(
                                color = Color(0xE6107C41),
                                shape = RoundedCornerShape(topStart = 8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tiempoStr,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                esValida -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(
                                color = Color(0xEE2E7D32),
                                shape = RoundedCornerShape(topStart = 8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ACTIVAR",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(
                                color = Color(0xEEB71C1C),
                                shape = RoundedCornerShape(topStart = 8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ALQUILA",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = movie.title,
            color = if (isFocused) ColorDorado else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .then(
                    if (isFocused) Modifier.basicMarquee(iterations = 1, delayMillis = 600)
                    else Modifier
                )
        )
    }
}