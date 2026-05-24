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


import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.util.Log
import android.widget.Button
import kotlinx.coroutines.withContext

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
    private var frasesJob: kotlinx.coroutines.Job? = null

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

        // Configurar el botón de reintentar
        findViewById<Button>(R.id.btnReintentar).setOnClickListener {
            checkConexionYProcesar()
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

    private fun checkConexionYProcesar() {
        if (isNetworkAvailable()) {
            // Ocultar error si estaba visible
            findViewById<View>(R.id.layoutNoInternet).visibility = View.GONE
            // Reiniciar animaciones y carga
            iniciarCargaDeDatos()
        } else {
            mostrarErrorConexion()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            // VPN y Bluetooth también pueden dar red en algunos dispositivos
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> true
            else -> false
        }
    }

    private fun mostrarErrorConexion() {
        val layoutError = findViewById<View>(R.id.layoutNoInternet)
        val btnReintentar = findViewById<Button>(R.id.btnReintentar)

        layoutError.visibility = View.VISIBLE
        layoutError.alpha = 0f
        layoutError.animate().alpha(1f).setDuration(500).start()

        // Dar foco al botón para que el control remoto pueda usarlo inmediatamente
        btnReintentar.requestFocus()
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
            // --- PASO 1: EL ESCUDO PARA EL PRIMER ARRANQUE ---
            // Esperamos hasta 5 segundos a que el sistema Android active la red
            var redLista = false
            for (i in 1..5) {
                if (isNetworkAvailable()) {
                    redLista = true
                    break
                }
                delay(1000) // Espera 1 segundo y vuelve a preguntar
                Log.d("SPLASH", "Esperando hardware de red... Intento $i")
            }

            if (!redLista) {
                // Si después de 5 segundos de verdad no hay red, recién ahí mostramos el error
                mostrarErrorConexion()
                return@launch
            }

            // --- PASO 2: CARGA DE DATOS CON MANEJO DE ERRORES MEJORADO ---
            val cargaExitosa = withTimeoutOrNull(20000) { // Aumentamos a 20 seg para el primer inicio
                try {
                    // Usamos withContext(Dispatchers.IO) para no congelar la pantalla
                    withContext(Dispatchers.IO) {
                        // Cargar lista de películas del servidor
                        Validacioneslista.cargarPeliculas()

                        val lasPrimeras = Validacioneslista.obtenerPeliculasValidas().take(15)
                        lasPrimeras.forEach { movie ->
                            try {
                                // Pre-carga de imágenes (esto evita destellos de imágenes blancas luego)
                                Glide.with(applicationContext)
                                    .asBitmap()
                                    .load(movie.imageUrl)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .submit()
                                    .get() // Espera a que la imagen baje
                            } catch (e: Exception) {
                                // Si una imagen falla (como el error 403 que vimos), que no detenga la app
                                Log.w("SPLASH", "No se pudo pre-cargar imagen: ${movie.imageUrl}")
                            }
                        }
                    }
                    true // Retornamos true si terminó el bloque Dispatchers.IO
                } catch (e: Exception) {
                    Log.e("SPLASH", "Error fatal en carga: ${e.message}")
                    null
                }
            }

            // --- PASO 3: DECISIÓN FINAL ---
            if (cargaExitosa == true) {
                navegarSiguientePantalla()
            } else {
                // Si hubo Timeout (servidor lento) o error de servidor
                mostrarErrorConexion()
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

        // CAMBIO AQUÍ: 0, 0 elimina el parpadeo y la transición del sistema
        overridePendingTransition(0, 0)

        finish()
    }

    companion object {
        var instance: SplashActivity? = null
    }
}