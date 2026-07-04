package com.creativem.fulltv.principal

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
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

        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.imgLogoSplash)
        val txtBienvenido = findViewById<TextView>(R.id.txtBienvenido)
        val txtCargando = findViewById<TextView>(R.id.txtCargandoAnim)
        val txtVersion = findViewById<TextView>(R.id.txtVersion)
        val viewGlow = findViewById<View>(R.id.viewGlow)

        // Efecto Dorado
        txtBienvenido.post {
            val paint = txtBienvenido.paint
            val width = paint.measureText(txtBienvenido.text.toString())
            val shader = android.graphics.LinearGradient(
                0f, 0f, width, txtBienvenido.textSize,
                intArrayOf(Color.parseColor("#F5E6AD"), Color.parseColor("#C5A059"), Color.parseColor("#8A6E2F"), Color.parseColor("#C5A059")),
                null, android.graphics.Shader.TileMode.CLAMP
            )
            txtBienvenido.paint.shader = shader
            txtBienvenido.invalidate()
        }

        txtVersion.text = "VERSIÓN ${BuildConfig.VERSION_NAME}"

        // Ocultar layout de error para que nunca moleste
        findViewById<View>(R.id.layoutNoInternet).visibility = View.GONE

        // Iniciar procesos
        iniciarCicloFrases(txtCargando)
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

                            // 🟢 Resolución anticipada de la API de TMDb para el banner inicial
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

                                            // Guardar de forma estática los datos procesados en la caché
                                            PeliculasActivity.bannerTMDBResolved = PeliculasActivity.Companion.TMDBResolvedData(
                                                movieId = bannerSeleccionado.id,
                                                title = titleText,
                                                overview = overviewText,
                                                rating = ratingText,
                                                infoAdicional = infoAdicionalText,
                                                backdropUrl = backdropUrl,
                                                posterUrl = posterUrl
                                            )

                                            // Descargar previamente las imágenes finales utilizando Glide
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
        // Animación de transición nativa (fade in/out) que previene destellos y pantallas negras
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun iniciarCicloFrases(textView: TextView) {
        lifecycleScope.launch {
            var ultimoIndex = -1
            while (isActive) {
                var index: Int
                do { index = (0 until frasesCine.size).random() } while (index == ultimoIndex)
                ultimoIndex = index
                textView.text = frasesCine[index]
                textView.animate().alpha(1f).setDuration(400).start()
                delay(2500)
                textView.animate().alpha(0f).setDuration(400).start()
                delay(500)
            }
        }
    }

    companion object { var instance: SplashActivity? = null }
}