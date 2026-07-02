package com.creativem.fulltv.principal

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        // 1. Pantalla Completa Inmersiva
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        super.onCreate(savedInstanceState)
        instance = this

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        setContentView(R.layout.activity_splash)

        // 2. Referencias
        val logo = findViewById<ImageView>(R.id.imgLogoSplash)
        val txtBienvenido = findViewById<TextView>(R.id.txtBienvenido)
        val txtCargando = findViewById<TextView>(R.id.txtCargandoAnim)
        val txtVersion = findViewById<TextView>(R.id.txtVersion)
        val viewGlow = findViewById<View>(R.id.viewGlow)

        // 3. Efecto Dorado al Título
        txtBienvenido.post {
            val paint = txtBienvenido.paint
            val width = paint.measureText(txtBienvenido.text.toString())
            val shader = android.graphics.LinearGradient(
                0f, 0f, width, txtBienvenido.textSize,
                intArrayOf(
                    Color.parseColor("#F5E6AD"),
                    Color.parseColor("#C5A059"),
                    Color.parseColor("#8A6E2F"),
                    Color.parseColor("#C5A059")
                ),
                null, android.graphics.Shader.TileMode.CLAMP
            )
            txtBienvenido.paint.shader = shader
            txtBienvenido.invalidate()
        }

        // 4. Versión y Animaciones
        txtVersion.text = "VERSIÓN ${BuildConfig.VERSION_NAME}"

        val animGlow = android.animation.ObjectAnimator.ofFloat(viewGlow, "alpha", 0.4f, 0.9f)
        animGlow.duration = 1500
        animGlow.repeatMode = android.animation.ObjectAnimator.REVERSE
        animGlow.repeatCount = android.animation.ObjectAnimator.INFINITE
        animGlow.start()

        logo.alpha = 0f
        txtBienvenido.alpha = 0f
        logo.animate().alpha(1f).setDuration(1000).start()
        txtBienvenido.animate().alpha(1f).setDuration(1200).setStartDelay(300).start()

        // 5. Ocultar el layout de error por si acaso estaba visible en el XML
        findViewById<View>(R.id.layoutNoInternet).visibility = View.GONE

        // 6. Iniciar procesos
        iniciarCicloFrases(txtCargando)
        iniciarCargaDeDatos()
    }

    private fun iniciarCargaDeDatos() {
        lifecycleScope.launch {
            // Asegurar que el error no se vea nunca
            findViewById<View>(R.id.layoutNoInternet).visibility = View.GONE

            // 🟢 VALIDACIONES EN SEGUNDO PLANO (Silenciosas)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Validacioneslista.cargarPeliculas()
                } catch (e: Exception) {
                    Log.e("SPLASH", "Validación silenciosa falló")
                }
            }

            // 🟢 INTENTO DE CARGA DE CARTELERA (Máximo 10 segundos)
            try {
                withTimeoutOrNull(10000) {
                    withContext(Dispatchers.IO) {
                        // Descargar metadatos
                        val snapshot = FirebaseDatabase.getInstance().reference.child("movies").get().await()
                        val todasLasPeliculas = snapshot.children.mapNotNull { doc ->
                            val m = doc.getValue(Modelo::class.java)
                            m?.copy(id = doc.key ?: "")
                        }.sortedByDescending { it.createdAt }

                        val limite = minOf(24, todasLasPeliculas.size)
                        val primeras24 = todasLasPeliculas.subList(0, limite)

                        // Validar URLS en paralelo
                        val validador = com.creativem.fulltv.peliculasvalidas.Validaciones()
                        val validadas = primeras24.map { movie ->
                            async {
                                movie.copy(isValid = validador.isUrlValid(movie.streamUrl))
                            }
                        }.awaitAll()

                        PeliculasActivity.primeraPaginaPrecalculada.clear()
                        PeliculasActivity.primeraPaginaPrecalculada.addAll(validadas)

                        // Pre-carga de imágenes en caché
                        validadas.forEach { movie ->
                            try {
                                Glide.with(applicationContext)
                                    .asBitmap()
                                    .load(movie.imageUrl)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .submit()
                            } catch (e: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SPLASH", "Carga incompleta por red, pero continuamos...")
            }

            // 🟢 NAVEGAR SIEMPRE
            navegarSiguientePantalla()
        }
    }

    private fun iniciarCicloFrases(textView: TextView) {
        lifecycleScope.launch {
            var ultimoIndex = -1
            while (isActive) {
                var indexAleatorio: Int
                do {
                    indexAleatorio = (0 until frasesCine.size).random()
                } while (indexAleatorio == ultimoIndex && frasesCine.size > 1)

                ultimoIndex = indexAleatorio
                textView.text = frasesCine[indexAleatorio]
                textView.animate().alpha(1f).setDuration(400).start()
                delay(2000)
                textView.animate().alpha(0f).setDuration(400).start()
                delay(450)
            }
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
        overridePendingTransition(0, 0)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar cualquier callback de red sobrante de versiones anteriores
    }

    companion object {
        var instance: SplashActivity? = null
    }
}