package com.creativem.fulltv.principal // Ajusta a tu paquete real

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
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

        val logo = findViewById<ImageView>(R.id.imgLogoSplash)
        val txtVersion = findViewById<TextView>(R.id.txtVersion)

        txtVersion.text = "Versión ${BuildConfig.VERSION_NAME}"

        logo.scaleX = 0.8f
        logo.scaleY = 0.8f
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        txtVersion.animate().alpha(1f).setDuration(1500).setStartDelay(500).start()

        iniciarCargaDeDatos()
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