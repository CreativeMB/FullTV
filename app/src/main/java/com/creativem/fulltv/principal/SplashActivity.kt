package com.creativem.fulltv.principal // Ajusta a tu paquete real

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.creativem.fulltv.BuildConfig // Importante para sacar la versión automática
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PeliculasActivity
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// 🖼️ IMPORTANTE: Para la precarga optimizada de imágenes (Glide)
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
// ... tus imports ...

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
        // 1. Configuración de Pantalla Completa Inmersiva para TV
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

        // 2. Referencias de las Vistas
        val logo = findViewById<ImageView>(R.id.imgLogoSplash)
        val txtBienvenido = findViewById<TextView>(R.id.txtBienvenido) // Título CINE PARCHE
        val txtCargando = findViewById<TextView>(R.id.txtCargandoAnim) // Frases de comunidad
        val txtVersion = findViewById<TextView>(R.id.txtVersion)
        val viewGlow = findViewById<View>(R.id.viewGlow)

        // 3. EFECTO ORO METÁLICO (Gradiente) al Título Principal
        // Usamos .post para que el gradiente se aplique una vez el texto ya tenga dimensiones
        txtBienvenido.post {
            val paint = txtBienvenido.paint
            val width = paint.measureText(txtBienvenido.text.toString())
            val shader = android.graphics.LinearGradient(
                0f, 0f, width, txtBienvenido.textSize,
                intArrayOf(
                    Color.parseColor("#F5E6AD"), // Oro Brillante
                    Color.parseColor("#C5A059"), // Dorado Medio
                    Color.parseColor("#8A6E2F"), // Bronce Oscuro
                    Color.parseColor("#C5A059")  // Dorado Medio
                ),
                null, android.graphics.Shader.TileMode.CLAMP
            )
            txtBienvenido.paint.shader = shader
            txtBienvenido.invalidate() // Forzar redibujado con el nuevo color
        }

        // 4. Mostrar Versión actualizada de la App
        txtVersion.text = "VERSIÓN ${BuildConfig.VERSION_NAME}"

        // 5. Animación de "Glow" (Efecto palpitante de luz de cine)
        val animGlow = android.animation.ObjectAnimator.ofFloat(viewGlow, "alpha", 0.4f, 0.9f)
        animGlow.duration = 1500
        animGlow.repeatMode = android.animation.ObjectAnimator.REVERSE
        animGlow.repeatCount = android.animation.ObjectAnimator.INFINITE
        animGlow.start()

        // 6. Animación de Entrada (Logo y Título aparecen con suavidad)
        logo.alpha = 0f
        txtBienvenido.alpha = 0f
        logo.animate().alpha(1f).setDuration(1000).start()
        txtBienvenido.animate().alpha(1f).setDuration(1200).setStartDelay(300).start()

        // 7. Iniciar Ciclo de Frases Cinematográficas y Carga de Datos
        iniciarCicloFrases(txtCargando)
        iniciarCargaDeDatos()
    }
    private fun iniciarCicloFrases(textView: TextView) {
        lifecycleScope.launch {
            var index = 0
            while (true) {
                textView.text = frasesCine[index]
                textView.animate().alpha(1f).setDuration(400).start()
                delay(2000)
                textView.animate().alpha(0f).setDuration(400).start()
                delay(450)
                index = (index + 1) % frasesCine.size
            }
        }
    }
    private fun iniciarCargaDeDatos() {
        lifecycleScope.launch {
            // 1. Carga de datos de fondo
            val cargaTrabajo = launch(Dispatchers.IO) {
                Validacioneslista.cargarPeliculas()
                val lasPrimeras = Validacioneslista.obtenerPeliculasValidas().take(15)
                lasPrimeras.forEach { movie ->
                    try {
                        Glide.with(applicationContext).asBitmap().load(movie.imageUrl)
                            .diskCacheStrategy(DiskCacheStrategy.ALL).submit().get()
                    } catch (e: Exception) {}
                }
            }

            cargaTrabajo.join()

            // --- 🛡️ LOGICA DE REDIRECCIÓN (AQUÍ ESTÁ EL CAMBIO) ---
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser

            val intentDestino: Intent
            if (currentUser == null || currentUser.email == "invitado@fulltv.com") {
                // Si no hay nadie o es el invitado, lo deslogueamos por seguridad
                // y lo mandamos al LOGIN para que elija.
                auth.signOut()
                intentDestino = Intent(this@SplashActivity, Login::class.java)
            } else {
                // Si es un usuario real (Google), va directo a las Películas.
                intentDestino = Intent(this@SplashActivity, PeliculasActivity::class.java)
            }

            startActivity(intentDestino)
            overridePendingTransition(0, 0)
            // No hacemos finish() aquí para mantener el instance vivo si PeliculasActivity lo necesita
        }
    }

    companion object {
        var instance: SplashActivity? = null
    }
}