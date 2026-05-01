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
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Configurar flags ANTES de que se cree la vista para evitar parpadeos de reajuste
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        super.onCreate(savedInstanceState)
        instance = this
        // 🔧 Configuración Visual TV (Pantalla Completa)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
// 2. Forzar pantalla completa total
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.imgLogoSplash)
        val txtVersion = findViewById<TextView>(R.id.txtVersion)

        // 📌 Poner la versión instalada automáticamente
        txtVersion.text = "Versión ${BuildConfig.VERSION_NAME}"

        // 🎬 Animación elegante (El logo crece ligeramente y aparece)
        logo.scaleX = 0.8f
        logo.scaleY = 0.8f
        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(1500) // 1.5 segundos de animación
            .setInterpolator(DecelerateInterpolator())
            .start()

        // 🎬 El texto de la versión aparece un poquito después para darle estilo
        txtVersion.animate().alpha(1f).setDuration(1500).setStartDelay(500).start()

        // 🚀 Iniciar Carga de datos de fondo
        iniciarCargaDeDatos()
    }

    private fun iniciarCargaDeDatos() {
        lifecycleScope.launch {
            // 1. Carga de datos e imágenes en segundo plano
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

            // 2. Esperamos a que los datos estén listos
            cargaTrabajo.join()

            // 3. Abrimos la actividad pero NO llamamos a finish() todavía
            val intent = Intent(this@SplashActivity, PeliculasActivity::class.java)
            startActivity(intent)

            // Quitamos animaciones para que parezca la misma pantalla
            overridePendingTransition(0, 0)

            // El Splash se queda vivo en el fondo hasta que la lista se dibuje
        }
    }

    // 4. Creamos un método estático para cerrar el splash desde la otra actividad
    companion object {
        var instance: SplashActivity? = null
    }

}