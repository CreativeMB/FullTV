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
        "Reuniendo al parche para la gran función",
        "Sincronizando lo mejor del cine en nuestro idioma",
        "Preparando la sala para compartir en comunidad",
        "Alistando los estrenos en español latino",
        "Haciendo posible el cine para todos",
        "Tu parche, tu cine, tu comunidad",
        "Conectando con la mejor señal latina",
        "Organizando la cartelera para el grupo"
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

                            // Evaluación de películas promocionales activas
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

                            // 🟢 DESCARGA EN LÍNEA GARANTIZADA: Esperamos de forma síncrona la descarga del archivo de imagen
                            if (bannerSeleccionado != null && !bannerSeleccionado.imageUrl.isNullOrBlank()) {
                                try {
                                    withContext(Dispatchers.IO) {
                                        withTimeoutOrNull(3000) { // Timeout de seguridad de 3 segundos
                                            Glide.with(applicationContext)
                                                .asBitmap()
                                                .load(bannerSeleccionado.imageUrl)
                                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                                .submit()
                                                .get() // Detiene la corrutina hasta que la imagen se descarga por completo
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("SPLASH_GLIDE", "La descarga en línea de la imagen excedió el tiempo límite: ${e.message}")
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