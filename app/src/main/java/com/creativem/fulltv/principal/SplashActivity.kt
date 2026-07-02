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
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    }

    private fun checkConexionYProcesar() {
        // 1. Iniciamos una verificación en segundo plano para no congelar la UI
        Thread {
            var conectado = false

            // Hacemos hasta 3 intentos rápidos "silenciosos" antes de asustar al usuario
            // Esto soluciona el fallo en la primera apertura
            for (i in 1..3) {
                if (isNetworkAvailable() || probarConexionReal()) {
                    conectado = true
                    break
                }
                Thread.sleep(800) // Esperamos casi un segundo entre intentos silenciosos
            }

            runOnUiThread {
                if (conectado) {
                    reintentosConexion = 0
                    findViewById<View>(R.id.layoutNoInternet).visibility = View.GONE
                    iniciarCargaDeDatos()
                } else {
                    // Si tras los intentos silenciosos sigue fallando, entramos en el bucle de reintento visible
                    reintentarConexion()
                }
            }
        }.start()
    }
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

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

        // Intentamos primero con la red activa (es lo más rápido)
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))) {
                return true
            }
        }

        // Si falla lo anterior, revisamos todas las interfaces (útil en algunas TV Boxes)
        return connectivityManager.allNetworks.any { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        }
    }


    private fun probarConexionReal(): Boolean {
        return try {
            // Usamos una dirección IP directa para evitar perder tiempo con el DNS
            // 1.1.1.1 es el DNS de Cloudflare, muy rápido.
            val timeoutMs = 2000
            val socket = Socket()
            socket.connect(InetSocketAddress("1.1.1.1", 53), timeoutMs)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }
    private fun mostrarErrorConexion() {
        val layoutError = findViewById<View>(R.id.layoutNoInternet)
        val btnReintentar = findViewById<Button>(R.id.btnReintentar)

        layoutError.visibility = View.VISIBLE
        layoutError.alpha = 0f
        layoutError.animate().alpha(1f).setDuration(500).start()

        btnReintentar.requestFocus()
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
            findViewById<View>(R.id.layoutNoInternet).visibility = View.GONE

            // --- PASO 1: EL ESCUDO SILENCIOSO DE CONEXIÓN (12 Intentos) ---
            // Damos un margen silencioso de hasta 12 segundos (ideal para arranque en frío de Ethernet/DHCP)
            // Mientras tanto, el usuario solo ve las frases de cine animadas de forma elegante.
            var redLista = false
            for (i in 1..12) {
                val tieneConexion = withContext(Dispatchers.IO) {
                    isNetworkAvailable()
                }
                if (tieneConexion) {
                    redLista = true
                    break
                }
                delay(1000) // Esperamos 1 segundo antes de reintentar
                Log.d("SPLASH_NET", "Esperando hardware de red/cable... Intento $i/12")
            }

            if (!redLista) {
                // Solo si después de 12 segundos de intentos reales no hay conexión, mostramos el error
                mostrarErrorConexion()
                return@launch
            }

            // 🟢 SOLUCIÓN: Lanzamos la validación del catálogo completo de forma silenciosa
            // al iniciar la Splash. Al estar en su propia CoroutineScope, NO bloquea el arranque
            // ni causa el error "Job was cancelled". Trabaja de fondo para PeliculasValidasActivity.
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Validacioneslista.cargarPeliculas()
                } catch (e: Exception) {
                    Log.e("SPLASH_BACKGROUND", "Error no crítico cargando cache global: ${e.message}")
                }
            }

            // --- PASO 2: CARGA RÁPIDA DE PÁGINA 1 EN LA SPLASH ---
            val cargaExitosa = withTimeoutOrNull(15000) { // 15 segundos es más que suficiente
                try {
                    withContext(Dispatchers.IO) {
                        // A. Descargamos la lista completa de metadatos de Firebase (Operación muy ligera de pocos KB)
                        val snapshot = FirebaseDatabase.getInstance().reference.child("movies").get().await()
                        val todasLasPeliculas = snapshot.children.mapNotNull { doc ->
                            val m = doc.getValue(Modelo::class.java)
                            m?.copy(id = doc.key ?: "")
                        }.sortedByDescending { it.createdAt }

                        // B. Tomamos exactamente las primeras 24 películas (Página 1 completa: válidas e inválidas)
                        val total = todasLasPeliculas.size
                        val limite = minOf(24, total)
                        val primeras24 = todasLasPeliculas.subList(0, limite)

                        // C. Las validamos todas EN PARALELO por red aquí mismo de forma asíncrona
                        val validador = com.creativem.fulltv.peliculasvalidas.Validaciones()
                        val primeras24Validadas = primeras24.map { movie ->
                            async {
                                val esValida = validador.isUrlValid(movie.streamUrl)
                                movie.copy(isValid = esValida)
                            }
                        }.awaitAll()

                        // D. Las guardamos en el caché global de PeliculasActivity
                        PeliculasActivity.primeraPaginaPrecalculada.clear()
                        PeliculasActivity.primeraPaginaPrecalculada.addAll(primeras24Validadas)

                        // E. Pre-carga de imágenes de portada en disco (evita destellos blancos en la TV)
                        primeras24Validadas.forEach { movie ->
                            try {
                                Glide.with(applicationContext)
                                    .asBitmap()
                                    .load(movie.imageUrl)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .submit()
                                    .get()
                            } catch (e: Exception) {
                                Log.w("SPLASH", "No se pudo pre-cargar imagen: ${movie.imageUrl}")
                            }
                        }
                    }
                    true
                } catch (e: Exception) {
                    Log.e("SPLASH", "Error fatal en carga: ${e.message}")
                    null
                }
            }

            // --- PASO 3: DECISIÓN FINAL ---
            if (cargaExitosa == true) {
                navegarSiguientePantalla()
            } else {
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