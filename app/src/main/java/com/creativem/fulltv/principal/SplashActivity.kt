package com.creativem.fulltv.principal // Ajusta a tu paquete real

// 🖼️ IMPORTANTE: Para la precarga optimizada de imágenes (Glide)


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var reintentosConexion = 0
    private val MAX_REINTENTOS = 5
    private val DELAY_REINTENTO_MS = 2000L // 2 segundos entre reintentos
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
        // ⭐ Registrar listener para detectar cambios de red automáticamente
        registrarListenerDeRed()

        // Verificar conexión inicial
        checkConexionYProcesar()
    }

    private fun checkConexionYProcesar() {
        if (isNetworkAvailable()) {
            reintentosConexion = 0
            findViewById<View>(R.id.layoutNoInternet).visibility = View.GONE
            iniciarCargaDeDatos()
        } else {
            // Antes de mostrar error, intentar reintentar varias veces
            // (porque en TV recién encendida la red tarda en validarse)
            reintentarConexion()
        }
    }

    /**
     * Reintenta verificar la conexión antes de mostrar el error
     */
    private fun reintentarConexion() {
        if (reintentosConexion < MAX_REINTENTOS) {
            reintentosConexion++
            // Mostrar un pequeño indicador de "buscando conexión..." (opcional)
            handler.postDelayed({
                if (isNetworkAvailable()) {
                    reintentosConexion = 0
                    findViewById<View>(R.id.layoutNoInternet).visibility = View.GONE
                    iniciarCargaDeDatos()
                } else {
                    reintentarConexion()
                }
            }, DELAY_REINTENTO_MS)
        } else {
            // Ya se agotaron los reintentos, mostrar error
            reintentosConexion = 0
            mostrarErrorConexion()
        }
    }

    /**
     * Verificación robusta de conexión para Android TV
     * Valida: WiFi, Ethernet (cable/fibra), Cellular, VPN
     * Y confirma que la red tenga INTERNET REAL (no solo red local)
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Método 1: Verificación rápida con activeNetwork
        if (verificarRedActiva(connectivityManager)) {
            return true
        }

        // Método 2: Verificación exhaustiva de todas las redes registradas
        // (útil cuando activeNetwork es null en el arranque)
        return verificarTodasLasRedes(connectivityManager)
    }

    /**
     * Verifica la red activa con validación de internet real
     */
    private fun verificarRedActiva(cm: ConnectivityManager): Boolean {
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return validarCapacidades(capabilities)
    }

    /**
     * Recorre todas las redes registradas (fallback cuando activeNetwork falla)
     */
    private fun verificarTodasLasRedes(cm: ConnectivityManager): Boolean {
        val networks = cm.allNetworks ?: return false
        for (network in networks) {
            val capabilities = cm.getNetworkCapabilities(network) ?: continue
            if (validarCapacidades(capabilities)) {
                return true
            }
        }
        return false
    }

    /**
     * Valida que la red tenga:
     * 1. Un transporte válido (WiFi, Ethernet, Cellular, VPN)
     * 2. Capacidad de internet
     * 3. Internet VALIDADO (confirma que realmente hay salida a internet)
     */
    private fun validarCapacidades(capabilities: NetworkCapabilities): Boolean {
        // Primero verificar que tenga algún transporte válido
        val tieneTransporteValido = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) || // ⭐ CABLE/FIBRA
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)

        if (!tieneTransporteValido) return false

        // Verificar que tenga capacidad de internet
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false
        }

        // ⭐ CLAVE: Verificar que el internet esté VALIDADO
        // Esto confirma que la red realmente puede salir a internet
        // (no es solo una red local sin salida)
        if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return true
        }

        // Si no está validado, hacer una prueba rápida de conectividad real
        return probarConexionReal()
    }

    /**
     * Prueba real de conexión haciendo un socket a un servidor público
     * (Último recurso cuando el sistema no valida la red)
     */
    private fun probarConexionReal(): Boolean {
        return try {
            val socket = Socket()
            // Conectar a DNS de Google (8.8.8.8) puerto 53
            socket.connect(InetSocketAddress("8.8.8.8", 53), 1500)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Muestra el error de conexión con animación
     */
    private fun mostrarErrorConexion() {
        val layoutError = findViewById<View>(R.id.layoutNoInternet)
        val btnReintentar = findViewById<Button>(R.id.btnReintentar)

        layoutError.visibility = View.VISIBLE
        layoutError.alpha = 0f
        layoutError.animate().alpha(1f).setDuration(500).start()

        btnReintentar.requestFocus()
    }

    /**
     * Registra un listener para detectar cambios de red en tiempo real
     * (Opcional pero recomendado para Android TV)
     */
    private fun registrarListenerDeRed() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Red disponible con internet
                handler.post {
                    findViewById<View>(R.id.layoutNoInternet).visibility = View.GONE
                    iniciarCargaDeDatos()
                }
            }

            override fun onLost(network: Network) {
                // Se perdió la conexión
                handler.post {
                    checkConexionYProcesar()
                }
            }
        }

        cm.registerNetworkCallback(request, networkCallback!!)
    }

    /**
     * Llamar en onDestroy() para liberar recursos
     */
    private fun desregistrarListenerDeRed() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        networkCallback?.let {
            try {
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignorar si ya estaba desregistrado
            }
        }
        handler.removeCallbacksAndMessages(null)
    }
    private fun iniciarCicloFrases(textView: TextView) {
        lifecycleScope.launch {
            var ultimoIndex = -1

            while (true) {
                // Seleccionar un índice aleatorio diferente al anterior
                var indexAleatorio: Int
                do {
                    indexAleatorio = (0 until frasesCine.size).random()
                } while (indexAleatorio == ultimoIndex && frasesCine.size > 1)

                ultimoIndex = indexAleatorio

                // Animación de aparición
                textView.text = frasesCine[indexAleatorio]
                textView.animate().alpha(1f).setDuration(400).start()
                delay(2000)

                // Animación de desaparición
                textView.animate().alpha(0f).setDuration(400).start()
                delay(450)
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
    override fun onDestroy() {
        super.onDestroy()
        desregistrarListenerDeRed()
    }
    companion object {
        var instance: SplashActivity? = null
    }
}