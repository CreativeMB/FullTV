package com.creativem.fulltv.principal

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
            SplashScreenUltraFast(
                fraseInicial = frasesCine.random(),
                versionName = BuildConfig.VERSION_NAME
            )
        }

        iniciarCargaDeDatos()
    }

    private fun iniciarCargaDeDatos() {
        lifecycleScope.launch {
            CoroutineScope(Dispatchers.IO).launch {
                try { Validacioneslista.cargarPeliculas() } catch (_: Exception) {}
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
                                                Log.e("SPLASH_TMDB", "Error cargando detalles: ${e.message}")
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
                                                Log.e("SPLASH_GLIDE", "Error imagen: ${e.message}")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("SPLASH_TMDB", "Fallo TMDb: ${e.message}")
                                }
                            }

                            true
                        } else false
                    }
                    cargado = exito
                } catch (_: Exception) {
                    Log.e("SPLASH", "Reintentando...")
                }

                if (!cargado) delay(400)
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

// =========================================================================
// 🟢 PANTALLA ULTRA LIGERA: 0% LAG, COLOR PLANO Y 100% RESPONSIVA PARA TV
// =========================================================================
@Composable
fun SplashScreenUltraFast(
    fraseInicial: String,
    versionName: String
) {
    // Fondo plano oscuro cinematográfico (#0A0C11). Cero uso de GPU.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C11))
    ) {
        // Contenedor principal centrado
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 1. Logo optimizado y estático (sin rotaciones pesadas)
            Image(
                painter = painterResource(id = R.drawable.icono),
                contentDescription = "Logo CineParche",
                modifier = Modifier
                    .size(130.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Título nítido con color dorado sólido para no consumir shaders
            Text(
                text = "BIENVENIDO",
                color = Color(0xFFFFD56B),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 3. Indicador de carga ligero + Frase fija
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.80f)
                    .wrapContentHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFFFFD56B),
                    strokeWidth = 2.5.dp,
                    trackColor = Color(0xFF22252E)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = fraseInicial,
                    color = Color(0xFFE2E4E9),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 4. Versión al fondo (Nunca se superpone con el centro)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp)
                .background(
                    color = Color(0x33FFFFFF),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            Text(
                text = "VERSIÓN $versionName",
                color = Color(0xFFB0B4BE),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }
    }
}