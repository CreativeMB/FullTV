package com.creativem.fulltv.principal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.creativem.fulltv.BuildConfig
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PeliculasActivity
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlin.math.sin

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    companion object {
        var instance: SplashActivity? = null
    }

    private val frasesCine = listOf(
        "Reuniendo al parche para una gran función",
        "Preparando el mejor cine en español latino",
        "Organizando la cartelera de la comunidad",
        "Conectando historias que unen al parche",
        "Cada película es mejor cuando la compartes",
        "Disfruta el mejor cine junto a tu comunidad",
        "Historias inolvidables habladas en español",
        "La mejor experiencia de cine comienza aquí",
        "El mejor contenido reunido para el parche",
        "Creando momentos que merecen compartirse",
        "Cada estreno nos reúne como una comunidad",
        "Las mejores películas llegan para todos",
        "El cine une personas, historias y emociones",
        "Descubre grandes historias sin interrupciones",
        "Una comunidad construida por amantes del cine",
        "El siguiente gran estreno te está esperando",
        "Comparte emociones a través del mejor cine",
        "Las mejores historias hablan nuestro idioma",
        "Donde cada película encuentra su audiencia",
        "Vive el cine con quienes comparten tu pasión",
        "Cada función comienza con una gran historia",
        "El entretenimiento que reúne a la comunidad",
        "Porque el buen cine siempre se disfruta juntos",
        "El mejor cine latino pensado para compartir",
        "Aquí cada película crea un nuevo recuerdo",
        "Preparando una experiencia digna de disfrutar",
        "Más que películas, compartimos emociones",
        "Donde el cine cobra vida junto al parche",
        "El idioma nos une, el cine nos inspira",
        "Siempre hay una historia esperando por ti",
        "La magia del cine comienza en comunidad",
        "Cada película abre la puerta a otro mundo",
        "Historias que emocionan, inspiran y acompañan",
        "Compartiendo la pasión por el cine latino",
        "Tu próxima gran historia comienza ahora",
        "Una experiencia creada para disfrutar juntos",
        "El cine que conecta generaciones y amigos",
        "Aquí las historias nunca dejan de emocionar",
        "Cada estreno fortalece nuestra comunidad",
        "Bienvenido al lugar donde vive el mejor cine"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        super.onCreate(savedInstanceState)
        instance = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContent {
            SplashScreen(
                frasesCine = frasesCine,
                versionName = BuildConfig.VERSION_NAME
            )
        }

        iniciarCargaDeDatos()
    }

    private fun iniciarCargaDeDatos() {
        lifecycleScope.launch {
            CoroutineScope(Dispatchers.IO).launch {
                try { Validacioneslista.cargarPeliculas() } catch (e: Exception) {}
            }

            var cargado = false
            val startTime = System.currentTimeMillis()

            while (!cargado && (System.currentTimeMillis() - startTime) < 3500) {
                try {
                    val exito = withContext(Dispatchers.IO) {
                        val snapshot = FirebaseDatabase.getInstance().reference.child("movies").get().await()
                        val todas = snapshot.children.mapNotNull { doc ->
                            val m = doc.getValue(Modelo::class.java)
                            m?.copy(id = doc.key ?: "")
                        }.sortedByDescending { it.createdAt }

                        if (todas.isNotEmpty()) {
                            val limite = minOf(24, todas.size)
                            val primeras24 = todas.subList(0, limite)

                            val validador = com.creativem.fulltv.peliculasvalidas.Validaciones()
                            val validadas = primeras24.map { movie ->
                                async { movie.copy(isValid = validador.isUrlValid(movie.streamUrl)) }
                            }.awaitAll()

                            PeliculasActivity.primeraPaginaPrecalculada.clear()
                            PeliculasActivity.primeraPaginaPrecalculada.addAll(validadas)

                            val peliculasPromoValidas = todas.filter { movie ->
                                val countdownMinutes = movie.countdownMinutes
                                val createdAt = movie.createdAt
                                val createdAtMillis = if (createdAt > 0 && createdAt < 1000000000000L) createdAt * 1000 else createdAt

                                if (countdownMinutes > 0 && createdAtMillis > 0L) {
                                    val durationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                                    val elapsed = System.currentTimeMillis() - createdAtMillis
                                    (durationMillis - elapsed) > 0
                                } else false
                            }

                            val bannerSeleccionado = peliculasPromoValidas.randomOrNull() ?: validadas.firstOrNull()
                            PeliculasActivity.bannerPeliculaInicial = bannerSeleccionado

                            if (bannerSeleccionado != null) {
                                val queryBusqueda = bannerSeleccionado.originalTitle.ifBlank { bannerSeleccionado.title }
                                val apiKey = "678193d2c735c6f37840cee035f4d69a"
                                try {
                                    val searchResponse = com.creativem.fulltv.api.TMDbApiClient.service.searchMovies(apiKey, "es-MX", queryBusqueda).execute()
                                    if (searchResponse.isSuccessful) {
                                        val result = searchResponse.body()?.results?.firstOrNull()
                                        if (result != null) {
                                            val fechaCompleta = result.release_date ?: ""
                                            val titleText = if (fechaCompleta.isNotEmpty()) "${result.title} ($fechaCompleta)" else result.title
                                            val overviewText = result.overview ?: bannerSeleccionado.overview
                                            val anio = result.release_date?.take(4) ?: "2026"
                                            val cal = if (result.vote_average > 0.0) "${result.vote_average}" else "8.5"
                                            val ratingText = "⭐ $cal   |   $anio"

                                            var infoAdicionalText = bannerSeleccionado.genres.ifBlank { "Acción • Aventura • Cine" }
                                            try {
                                                val detailResponse = com.creativem.fulltv.api.TMDbApiClient.service.getMovieDetails(result.id, apiKey, "es-MX").execute()
                                                if (detailResponse.isSuccessful) {
                                                    val detalles = detailResponse.body()
                                                    val generos = detalles?.genres?.joinToString(" • ") { it.name } ?: "Desconocidos"
                                                    val duracion = detalles?.runtime ?: 0
                                                    infoAdicionalText = "🎭 $generos  ⏱️ ${duracion} Min"
                                                }
                                            } catch (e: Exception) {
                                                Log.e("SPLASH_TMDB", "Error cargando detalles del banner: ${e.message}")
                                            }

                                            val backdropUrl = "https://image.tmdb.org/t/p/w1280${result.backdrop_path ?: result.poster_path}"
                                            val posterUrl = "https://image.tmdb.org/t/p/w500${result.poster_path ?: result.backdrop_path}"

                                            PeliculasActivity.bannerTMDBResolved = PeliculasActivity.Companion.TMDBResolvedData(
                                                movieId = bannerSeleccionado.id,
                                                title = titleText,
                                                overview = overviewText,
                                                rating = ratingText,
                                                infoAdicional = infoAdicionalText,
                                                backdropUrl = backdropUrl,
                                                posterUrl = posterUrl
                                            )

                                            try {
                                                withTimeoutOrNull(2500) {
                                                    Glide.with(applicationContext)
                                                        .asBitmap()
                                                        .load(backdropUrl)
                                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                                        .submit()
                                                        .get()
                                                }
                                                withTimeoutOrNull(1500) {
                                                    Glide.with(applicationContext)
                                                        .asBitmap()
                                                        .load(posterUrl)
                                                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                                                        .submit()
                                                        .get()
                                                }
                                            } catch (e: Exception) {
                                                Log.e("SPLASH_GLIDE", "Error en precarga de imágenes: ${e.message}")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("SPLASH_TMDB", "Fallo de conexión a TMDb: ${e.message}")
                                }
                            }

                            true
                        } else false
                    }
                    cargado = exito
                } catch (e: Exception) {
                    Log.e("SPLASH", "Reintentando carga...")
                }

                if (!cargado) delay(500)
            }

            navegarSiguientePantalla()
        }
    }

    private fun navegarSiguientePantalla() {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        val intentDestino = if (currentUser == null || currentUser.email == "invitado@fulltv.com") {
            auth.signOut()
            Intent(this, Login::class.java)
        } else {
            Intent(this, PeliculasActivity::class.java)
        }

        startActivity(intentDestino)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

// ==========================================
// 🟢 FONDO CINEMATOGRÁFICO PREMIUM (ATMÓSFERA Y DESTELLOS EN TODA LA PANTALLA)
// ==========================================
private data class ParticulaCine(
    val xRatio: Float,
    val baseYRatio: Float,
    val radioDp: Float,
    val velocidad: Float,
    val fase: Float,
    val color: Color
)

@Composable
fun FondoCinePremiumDestellos(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "fondoAtmosphere")

    // Pulsación suave del haz del proyector en la parte superior
    val proyectorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "proyectorAlpha"
    )

    // Expansión ambiental de la luz de sala de cine
    val ambientScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(4800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientScale"
    )

    // Tiempo continuo para las partículas doradas flotantes
    val particleTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleTime"
    )

    // Lista fija de partículas doradas (polvo de sala de cine iluminado)
    val particulas = remember {
        val random = java.util.Random(101)
        List(36) {
            val isGold = random.nextBoolean()
            val color = if (isGold) Color(0xFFFFD56B) else Color(0xFFFFF7DD)
            ParticulaCine(
                xRatio = random.nextFloat(),
                baseYRatio = random.nextFloat(),
                radioDp = 1.3f + random.nextFloat() * 2.2f,
                velocidad = 0.12f + random.nextFloat() * 0.32f,
                fase = random.nextFloat() * 2f * Math.PI.toFloat(),
                color = color
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF030509)) // Negro azulado muy profundo de sala
    ) {
        // 1. Haz de luz del proyector cenital (viene desde arriba hacia el centro)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.70f)
                .align(Alignment.TopCenter)
                .alpha(proyectorAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x40C5A059),
                            Color(0x1C8A6E2F),
                            Color(0x082B1E0C),
                            Color.Transparent
                        ),
                        radius = 850f
                    )
                )
        )

        // 2. Halo ambiental expansivo en el centro (tono azul medianoche de cine)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .scale(ambientScale)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x35192238),
                            Color(0x160D1424),
                            Color.Transparent
                        )
                    )
                )
        )

        // 3. Resplandor cálido inferior (Alfombra roja / iluminación de suelo)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.BottomCenter)
                .alpha(proyectorAlpha * 0.7f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x143A2C11),
                            Color(0x30614B1F)
                        )
                    )
                )
        )

        // 4. Lienzo de partículas doradas titilantes y flotantes
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ancho = size.width
            val alto = size.height

            particulas.forEach { p ->
                val progreso = (particleTime * p.velocidad + p.baseYRatio) % 1.0f
                val y = alto * (1.0f - progreso)
                val x = ancho * p.xRatio + (sin((particleTime * 2 * Math.PI + p.fase).toDouble()).toFloat() * 16f)

                val brillo = (sin((particleTime * 4 * Math.PI + p.fase).toDouble()).toFloat() * 0.4f + 0.6f)
                    .coerceIn(0.1f, 1f)

                // Halo de la partícula
                drawCircle(
                    color = p.color.copy(alpha = brillo * 0.22f),
                    radius = (p.radioDp * 2.6f).dp.toPx(),
                    center = Offset(x, y)
                )

                // Núcleo brillante
                drawCircle(
                    color = p.color.copy(alpha = brillo * 0.85f),
                    radius = p.radioDp.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

// ==========================================
// 🟢 DESTELLO CINEMATOGRÁFICO DETRÁS DEL ICONO
// ==========================================
@Composable
fun DestelloCinemaDetras(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cinemaFlareAnim")

    val flareScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flareScale"
    )

    val flareAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flareAlpha"
    )

    val rotacionHaz by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotacionHaz"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Halo difuso dorado
        Box(
            modifier = Modifier
                .size(330.dp)
                .scale(flareScale)
                .alpha(flareAlpha)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x88C5A059),
                            Color(0x448A6E2F),
                            Color(0x184A3B18),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Destello horizontal anamórfico de cámara
        Box(
            modifier = Modifier
                .width(440.dp)
                .height(42.dp)
                .scale(flareScale)
                .alpha(flareAlpha * 0.75f)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x22FFF1C5),
                            Color(0xAAFFF3B0),
                            Color(0xEEFFFFFF),
                            Color(0xAAFFF3B0),
                            Color(0x22FFF1C5),
                            Color.Transparent
                        )
                    )
                )
        )

        // Haces de luz suaves en rotación
        Box(
            modifier = Modifier
                .size(260.dp)
                .rotate(rotacionHaz)
                .alpha(flareAlpha * 0.35f)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x66FFD700),
                            Color.Transparent,
                            Color(0x33FFD700),
                            Color.Transparent,
                            Color(0x66FFD700),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
    }
}

// ==========================================
// 🟢 PANTALLA COMPOSABLE DE SPLASH PARA ANDROID TV
// ==========================================
@Composable
fun SplashScreen(
    frasesCine: List<String>,
    versionName: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var fraseActual by remember { mutableStateOf("") }
    val alphaAnim = remember { Animatable(0f) }

    // Pulso suave continuo en el logo (efecto respiración cinemática)
    val infiniteTransition = rememberInfiniteTransition(label = "logoPulse")
    val logoScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scaleAnim"
    )

    // Degradado Dorado Premium
    val goldGradient = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFFFFF1C5),
                Color(0xFFF5E6AD),
                Color(0xFFC5A059),
                Color(0xFF8A6E2F),
                Color(0xFFC5A059)
            )
        )
    }

    // Ciclo de frases aleatorias no repetidas con SharedPreferences
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("SplashPrefs", Context.MODE_PRIVATE)
        val indicesString = prefs.getString("unshown_indices", "") ?: ""
        var listaIndices = if (indicesString.isNotEmpty()) {
            indicesString.split(",").mapNotNull { it.toIntOrNull() }.toMutableList()
        } else {
            mutableListOf()
        }

        if (listaIndices.isEmpty()) {
            listaIndices = (0 until frasesCine.size).shuffled().toMutableList()
            prefs.edit().putString("unshown_indices", listaIndices.joinToString(",")).apply()
        }

        while (isActive) {
            if (listaIndices.isEmpty()) {
                listaIndices = (0 until frasesCine.size).shuffled().toMutableList()
            }

            val index = listaIndices.removeAt(0)
            prefs.edit().putString("unshown_indices", listaIndices.joinToString(",")).apply()
            fraseActual = frasesCine[index]

            // Entrada suave
            alphaAnim.animateTo(1f, animationSpec = tween(350))
            delay(2200)
            // Salida suave
            alphaAnim.animateTo(0f, animationSpec = tween(300))
            delay(200)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 🟢 1. FONDO CON ATMÓSFERA Y DESTELLOS PREMIUM EN TODA LA PANTALLA
        FondoCinePremiumDestellos()

        // 🟢 2. CONTENIDO CENTRAL OPTIMIZADO PARA ANDROID TV
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // LOGO EN GRANDE CON DESTELLO CINEMATOGRÁFICO DETRÁS
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                // Destello detrás del logo
                DestelloCinemaDetras()

                // Logo principal con respiración
                Image(
                    painter = painterResource(id = R.drawable.icono),
                    contentDescription = "Logo CineParche",
                    modifier = Modifier
                        .size(180.dp)
                        .scale(logoScale),
                    contentScale = ContentScale.Fit
                )
            }

            // TÍTULO BIENVENIDO GRANDE PARA TV (42.sp)
            Text(
                text = "BIENVENIDO",
                style = TextStyle(
                    brush = goldGradient,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(36.dp))

            // CONTENEDOR CON ÍCONO DE CARGA Y FRASE DINÁMICA GRANDE (22.sp)
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(68.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 12.dp),
                    color = Color(0xFFC5A059),
                    strokeWidth = 3.dp,
                    trackColor = Color(0xFF332A15)
                )

                Text(
                    text = fraseActual,
                    color = Color.White.copy(alpha = alphaAnim.value),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Start,
                    lineHeight = 28.sp,
                    maxLines = 2
                )
            }
        }

        // 🟢 3. VERSIÓN INFERIOR VISIBLE
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "VERSIÓN $versionName",
                color = Color(0xFFC5A059),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}