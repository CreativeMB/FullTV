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

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔧 Configuración Visual TV (Pantalla Completa)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

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
            // 1. Cargamos las validaciones en un hilo secundario para no trabar la pantalla
            val cargaTrabajo = launch(Dispatchers.IO) {
                Validacioneslista.cargarPeliculas()
            }

            // 2. Tiempo mínimo en pantalla para que se lea el mensaje y se vea la animación (3 Segundos)
            delay(3000)

            // 3. Garantizamos que la base de datos ya está cacheada antes de avanzar
            cargaTrabajo.join()

            // 4. Verificamos a dónde debe ir el usuario
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                startActivity(Intent(this@SplashActivity, PeliculasActivity::class.java))
            } else {
                startActivity(Intent(this@SplashActivity, Login::class.java))
            }

            // 5. Transición suave de fundido cruzado (Fade in / Fade out)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }
}