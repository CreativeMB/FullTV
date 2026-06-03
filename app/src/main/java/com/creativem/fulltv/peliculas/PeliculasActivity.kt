package com.creativem.fulltv.peliculas

import android.R.attr.apiKey
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.creativem.fulltv.BannerPromosAdapter
import com.creativem.fulltv.R
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.api.PeliculasApiActivity
import com.creativem.fulltv.api.TMDbApiClient
import com.creativem.fulltv.databinding.ActivityPeliculasBinding
import com.creativem.fulltv.menu.MenuPrincipalAdapter
import com.creativem.fulltv.menu.MenuPrincipalItem
import com.creativem.fulltv.peliculasvalidas.PeliculasValidasActivity
import com.creativem.fulltv.peliculasvalidas.Validaciones
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Login
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.principal.Perfil
import com.creativem.fulltv.tv.TvActivity
import com.google.firebase.auth.FirebaseAuth
import com.creativem.fulltv.BuildConfig
import com.creativem.fulltv.principal.CineAlert
import com.creativem.fulltv.principal.ViewUtils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import com.creativem.fulltv.api.MovieResponse
import com.creativem.fulltv.api.TmdbMovie
import com.creativem.fulltv.api.MovieDetailResponse
import com.creativem.fulltv.api.TMDbApiService
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PeliculasActivity : AppCompatActivity() {
        private var yaTieneListener = false
    private lateinit var binding: ActivityPeliculasBinding
    private lateinit var movieAdapter: MoviesAdapter
    private val modeloList = mutableListOf<Modelo>()
    private var esModoGratis = false // Para saber si estamos filtrando por validación o no
    // --- Firebase & Listeners ---
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val databaseRef: DatabaseReference = FirebaseDatabase.getInstance().reference
    private var peliculasListener: ValueEventListener? = null
    private var userStatusListener: ValueEventListener? = null
    private var downloadId: Long = -1
    // --- Estado y Control ---
    private var haProcesadoEliminacion = false
    private var isRotationRunning = false // Candado para evitar saltos locos al mover el control
    private var publicidadDialog: Dialog? = null
    private var lastFocusedMovie: View? = null
    private val handler = Handler(Looper.getMainLooper())

    private var progressDialog: AlertDialog? = null
    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"
    private var bannerTimer: CountDownTimer? = null

    private val promoRotationHandler = Handler(Looper.getMainLooper())
    private var promoRotationRunnable: Runnable? = null
    private val peliculasPromoList = mutableListOf<Modelo>()
    private var currentPromoIndex = 0

    // Handler y Runnable para detectar inactividad del usuario y retomar la rotación
    private var isUserInteractingWithPromo = false
    private val inactivityHandler = Handler(Looper.getMainLooper())

    // 🟢 ASÍ SE DECLARA PARA EVITAR EL ERROR "Val cannot be reassigned"
    private val inactivityRunnable = Runnable {
        isUserInteractingWithPromo = false
        // Forzamos la reanudación automática
        iniciarRotacionAutomatica()
    }

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                progressDialog?.dismiss()
                val query = DownloadManager.Query().setFilterById(downloadId)
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val cursor = dm.query(query)

                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(statusIndex)) {
                        val file = File(getExternalFilesDir(null), "CineParcheApp-debug.apk")
                        if (file.exists()) {
                            instalarAPK(file)
                        }
                    } else {
                        Toast.makeText(context, "Error en la descarga", Toast.LENGTH_SHORT).show()
                    }
                }
                cursor.close()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_FullTV_tv)
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)

        // 2. CONFIGURACIÓN VISUAL PARA TV (Pantalla Completa e Inmersiva)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding = ActivityPeliculasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 3. CARGA DE DATOS DESDE LA RAM (Rápido y Seguro)
        val peliculasYaCargadas = Validacioneslista.obtenerPeliculasValidas()
        if (peliculasYaCargadas.isNotEmpty()) {
            modeloList.clear()
            modeloList.addAll(peliculasYaCargadas)
        }

        // 4. INICIALIZACIÓN DE COMPONENTES UI
        setupMenuHorizontal()
        setupMovieGrid()

        // --- NOTA: HEMOS ELIMINADO EL BLOQUE SplashActivity.instance?.finish() ---
        // Al usar las FLAGS en el Login, esto ya no es necesario y evita el CRASH.

        // 5. GESTIÓN DE FOCO (Crucial para el control remoto de TV)
        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null && isViewDescendantOf(newFocus, binding.rvPeliculas)) {
                lastFocusedMovie = newFocus
            }
        }

        // 6. LISTENERS Y SEGURIDAD (En segundo plano)
        onBackPressedDispatcher.addCallback(this) {
            mostrarConfirmacionSalida()
        }

        escucharCambiosEnPeliculas()
        iniciarVerificacionDeEstadoDeCuenta()
        obtenerNoticiaYActualizaciones()

        // 7. REGISTRO DE USUARIO (Si aplica)
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrBlank()) {
            // Verificamos si es el invitado para poner el nombre manual
            val nombreAMostrar = if (currentUser.email == "invitado@fulltv.com") {
                "Estas En Invitado"
            } else {
                currentUser.displayName ?: "Usuario"
            }

            CastvHelper.nuevosusuarios(this, nombreAMostrar, currentUser.email!!)
        }
        escucharSaldoUsuario()

        configurarAnimacionDelBanner()
        // 🟢 Ejecutamos la carga del banner de Netflix
        cargarBannerPromocional()

// Configurar cliente Retrofit para TMDB dentro de onCreate de PeliculasActivity
        val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        apiService = retrofit.create(TMDbApiService::class.java)

    }

    private fun configurarAnimacionDelBanner() {
        val layoutBannerNetflix = findViewById<View>(R.id.layoutBannerNetflix)
        val rvPeliculas = findViewById<RecyclerView>(R.id.rvPeliculas)
        val rvBannerPromos = findViewById<RecyclerView>(R.id.rvBannerPromos)

        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { oldFocus, newFocus ->
            val focoEnPeliculas = rvPeliculas?.findContainingItemView(newFocus) != null
            val focoEnGuiones = rvBannerPromos?.findContainingItemView(newFocus) != null || newFocus == rvBannerPromos

            if (focoEnPeliculas) {
                // Caso 1: Bajó a las películas -> Ocultar banner y apagar rotación por completo
                if (layoutBannerNetflix?.visibility == View.VISIBLE) {
                    layoutBannerNetflix.visibility = View.GONE
                    detenerRotacionAutomatica()
                }
            } else {
                // Caso 2: El foco está en la zona superior (Menú lateral/superior o Banner)
                if (layoutBannerNetflix?.visibility == View.GONE && peliculasPromoList.isNotEmpty()) {
                    layoutBannerNetflix.visibility = View.VISIBLE
                }

                if (focoEnGuiones) {
                    // Si está navegando los guiones, el temporizador de inactividad toma el control
                    registrarActividadUsuario()
                } else {
                    // 🟢 REACCIÓN INMEDIATA: Si el foco salió de los guiones hacia el menú,
                    // apagamos los flags de usuario y forzamos el arranque del carrusel.
                    isUserInteractingWithPromo = false
                    iniciarRotacionAutomatica()
                }
            }
        }
    }

    private fun cargarBannerPromocional() {
        val bannerContainer = findViewById<View>(R.id.layoutBannerNetflix)
        val rvBannerPromos = findViewById<RecyclerView>(R.id.rvBannerPromos)

        databaseRef.child("movies").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                peliculasPromoList.clear()

                for (child in snapshot.children) {
                    val movie = child.getValue(Modelo::class.java)
                    if (movie != null) {
                        movie.id = child.key ?: ""
                        val countdownMinutes = movie.countdownMinutes
                        val createdAt = movie.createdAt
                        val createdAtMillis = if (createdAt > 0 && createdAt < 1000000000000L) createdAt * 1000 else createdAt

                        if (countdownMinutes > 0 && createdAtMillis > 0L) {
                            val durationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                            val elapsed = System.currentTimeMillis() - createdAtMillis
                            if (durationMillis - elapsed > 0) {
                                peliculasPromoList.add(movie)
                            }
                        }
                    }
                }

                if (peliculasPromoList.isNotEmpty()) {
                    bannerContainer.visibility = View.VISIBLE
                    currentPromoIndex = 0
                    isUserInteractingWithPromo = false

                    rvBannerPromos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

                    // CONFIGURACIÓN CLAVE: Pasamos los dos eventos interactivos al adaptador
                    val adapter = BannerPromosAdapter(
                        list = peliculasPromoList,
                        onMovieFocused = { movieSeleccionado ->
                            detenerRotacionAutomatica() // Frenamos el auto-giro para que no salte solo
                            registrarActividadUsuario()  // Espera 8 segundos de inactividad antes de reanudar

                            currentPromoIndex = peliculasPromoList.indexOfFirst { it.id == movieSeleccionado.id }.coerceAtLeast(0)
                            mostrarDatosPeliculaEnBanner(movieSeleccionado)
                        },
                        onMovieClicked = { movieSeleccionado ->
                            irAlReproductor(movieSeleccionado) // Acción de la tecla ENTER
                        }
                    )
                    rvBannerPromos.adapter = adapter

                    mostrarDatosPeliculaEnBanner(peliculasPromoList[0])

                    rvBannerPromos.postDelayed({
                        val currentAdapter = rvBannerPromos.adapter as? BannerPromosAdapter
                        currentAdapter?.updateSelectedPosition(0, rvBannerPromos)
                    }, 300)

                    iniciarRotacionAutomatica()
                } else {
                    bannerContainer.visibility = View.GONE
                }
            } else {
                bannerContainer.visibility = View.GONE
            }
        }.addOnFailureListener {
            bannerContainer.visibility = View.GONE
        }
    }

    private fun iniciarRotacionAutomatica() {
        // Si el usuario está interactuando activamente con el mando, esperamos.
        if (isUserInteractingWithPromo) return

        // Si la transición ya está corriendo en segundo plano, no duplicamos el bucle.
        if (isRotationRunning) return

        isRotationRunning = true
        promoRotationRunnable?.let { promoRotationHandler.removeCallbacks(it) }

        promoRotationRunnable = object : Runnable {
            override fun run() {
                if (peliculasPromoList.isNotEmpty()) {
                    val rvBannerPromos = findViewById<RecyclerView>(R.id.rvBannerPromos)

                    // 🟢 LA CLAVE AQUÍ: Si el usuario tiene el foco en los guiones, NO matamos el bucle.
                    // Simplemente lo posponemos otros 6 segundos. El corazón del carrusel sigue latiendo.
                    if (isUserInteractingWithPromo || rvBannerPromos?.hasFocus() == true) {
                        promoRotationHandler.postDelayed(this, 6000)
                        return
                    }

                    // Avanzamos al siguiente índice de película de forma segura
                    currentPromoIndex = (currentPromoIndex + 1) % peliculasPromoList.size

                    val peliActual = peliculasPromoList[currentPromoIndex]
                    mostrarDatosPeliculaEnBanner(peliActual)

                    val adapter = rvBannerPromos?.adapter as? BannerPromosAdapter
                    adapter?.updateSelectedPosition(currentPromoIndex, rvBannerPromos)

                    // Programar el siguiente cambio en 6 segundos
                    promoRotationHandler.postDelayed(this, 6000)
                }
            }
        }

        // Inicia el ciclo con una espera inicial de 6 segundos completos
        promoRotationHandler.postDelayed(promoRotationRunnable!!, 6000)
    }

    private fun detenerRotacionAutomatica() {
        isRotationRunning = false // Liberamos el candado
        promoRotationRunnable?.let {
            promoRotationHandler.removeCallbacks(it)
        }
    }

    private fun registrarActividadUsuario() {
        isUserInteractingWithPromo = true
        inactivityHandler.removeCallbacks(inactivityRunnable)
        // Si el usuario deja de presionar botones por 6 segundos, el carrusel vuelve a girar solo
        inactivityHandler.postDelayed(inactivityRunnable, 6000)
    }

    private fun mostrarDatosPeliculaEnBanner(movie: Modelo) {
        val ivBackdrop = findViewById<ImageView>(R.id.ivBannerBackdrop)
        val ivPoster = findViewById<ImageView>(R.id.ivBannerPoster)
        val layoutInfo = findViewById<LinearLayout>(R.id.layoutInfo)
        val tvTitulo = findViewById<TextView>(R.id.tvBannerTitulo)
        val tvSinopsis = findViewById<TextView>(R.id.tvBannerSinopsis)
        val tvContador = findViewById<TextView>(R.id.tvBannerContador)
        val tvCalificacion = findViewById<TextView>(R.id.tvBannerCalificacion)
        val tvBannerInfoAdicional = findViewById<TextView>(R.id.tvBannerInfoAdicional)

        // Si la Activity se está cerrando antes de iniciar la animación, cancelamos
        if (isFinishing || isDestroyed) return

        layoutInfo.animate().alpha(0f).setDuration(400).withEndAction {
            // Doble verificación al terminar la animación de ocultado
            if (isFinishing || isDestroyed) return@withEndAction

            tvTitulo.text = movie.title
            tvSinopsis.text = movie.overview.ifBlank { "Estreno exclusivo." }
            tvCalificacion.text = "⭐ 8.5"
            tvBannerInfoAdicional.text = movie.genres.ifBlank { "Acción • Aventura • Cine" }

            val queryBusqueda = movie.originalTitle.ifBlank { movie.title }
            apiService.searchMovie(apiKey, "es-MX", queryBusqueda).enqueue(object : retrofit2.Callback<MovieResponse> {
                override fun onResponse(call: retrofit2.Call<MovieResponse>, response: retrofit2.Response<MovieResponse>) {
                    // 🟢 CANDADO 1: Detiene el proceso si la Activity murió durante la búsqueda de la película
                    if (isFinishing || isDestroyed) return

                    if (response.isSuccessful) {
                        val result = response.body()?.results?.firstOrNull()
                        if (result != null) {
                            tvTitulo.text = result.title
                            tvSinopsis.text = result.overview ?: movie.overview
                            val anio = result.release_date?.take(4) ?: "2026"
                            val cal = if (result.vote_average > 0.0) "${result.vote_average}" else "8.5"
                            tvCalificacion.text = "⭐ $cal   |   $anio"

                            // Petición anidada para los detalles del género y duración
                            apiService.getMovieDetails(result.id, apiKey, "es-MX").enqueue(object : retrofit2.Callback<MovieDetailResponse> {
                                override fun onResponse(call: retrofit2.Call<MovieDetailResponse>, response: retrofit2.Response<MovieDetailResponse>) {
                                    // 🟢 CANDADO 2: Detiene si murió durante la descarga de detalles extra
                                    if (isFinishing || isDestroyed) return

                                    if (response.isSuccessful) {
                                        val detalles = response.body()
                                        val generos = detalles?.genres?.joinToString(" • ") { it.name } ?: "Desconocidos"
                                        val duracion = detalles?.runtime ?: 0
                                        tvBannerInfoAdicional.text = "🎭 $generos  ⏱️ ${duracion} Min"
                                    }
                                }
                                override fun onFailure(call: retrofit2.Call<MovieDetailResponse>, t: Throwable) {
                                    // 🟢 CANDADO 3: Evita fugas si falla la petición de detalles
                                    if (isFinishing || isDestroyed) return
                                }
                            })

                            layoutInfo.animate().alpha(1f).setDuration(500).start()
                            val backdropUrl = "https://image.tmdb.org/t/p/w1280${result.backdrop_path ?: result.poster_path}"
                            val posterUrl = "https://image.tmdb.org/t/p/w500${result.poster_path ?: result.backdrop_path}"
                            cargarImagenSuave(backdropUrl, ivBackdrop)
                            cargarImagenSuave(posterUrl, ivPoster)
                        } else {
                            layoutInfo.animate().alpha(1f).setDuration(500).start()
                            cargarImagenSuave(movie.imageUrl, ivBackdrop)
                            cargarImagenSuave(movie.imageUrl, ivPoster)
                        }
                    } else {
                        layoutInfo.animate().alpha(1f).setDuration(500).start()
                        cargarImagenSuave(movie.imageUrl, ivBackdrop)
                        cargarImagenSuave(movie.imageUrl, ivPoster)
                    }
                }

                override fun onFailure(call: retrofit2.Call<MovieResponse>, t: Throwable) {
                    // 🟢 CANDADO 4: Detiene el fallo si el usuario ya cerró la pantalla
                    if (isFinishing || isDestroyed) return

                    layoutInfo.animate().alpha(1f).setDuration(500).start()
                    cargarImagenSuave(movie.imageUrl, ivBackdrop)
                    cargarImagenSuave(movie.imageUrl, ivPoster)
                }
            })

            // Lógica del contador del Banner
            val durationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
            val createdAtMillis = if (movie.createdAt > 0 && movie.createdAt < 1000000000000L) movie.createdAt * 1000 else movie.createdAt
            val elapsed = System.currentTimeMillis() - createdAtMillis
            iniciarContadorBanner(tvContador, durationMillis - elapsed)
        }.start()
    }

    private fun cargarImagenSuave(url: String, imageView: ImageView) {
        // 🟢 EL CANDADO: Si el usuario cerró la Activity mientras la red respondía, salimos de inmediato
        if (isFinishing || isDestroyed) return

        // Tu código actual de Glide aquí abajo
        Glide.with(this) // O Glide.with(this@PeliculasActivity)
            .load(url)
            // .placeholder(...) si tienes uno
            .into(imageView)
    }

    private fun iniciarContadorBanner(textView: TextView, remainingTime: Long) {
        bannerTimer?.cancel()
        bannerTimer = object : CountDownTimer(remainingTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val h = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val m = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val s = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                textView.text = String.format("▶\uFE0F %02d:%02d:%02d", h, m, s)
            }

            override fun onFinish() {
                cargarBannerPromocional()
            }
        }.start()
    }

    private fun escucharSaldoUsuario() {
        val email = auth.currentUser?.email ?: return
        val correoKey = email.replace(".", "_").replace("@", "_")
        val userRef = databaseRef.child("usuarios").child(correoKey)

        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // 1. Obtenemos el valor del saldo
                    val saldo = snapshot.child("castv").value?.toString() ?: "0"

                    // 2. CONSTRUIR TEXTO: 🪙 CasTV: [VALOR]
                    // Usamos el emoji directamente en el string
                    val emoji = "🪙 "
                    val etiqueta = "CasTV: "
                    val textoCompleto = "$emoji$etiqueta$saldo"

                    val spannable = SpannableStringBuilder(textoCompleto)

                    // Color Blanco para la palabra "CasTV: "
                    spannable.setSpan(
                        ForegroundColorSpan(Color.WHITE),
                        emoji.length,
                        emoji.length + etiqueta.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    // Color Dorado (#C5A059) para el número del saldo
                    spannable.setSpan(
                        ForegroundColorSpan(Color.parseColor("#C5A059")),
                        emoji.length + etiqueta.length,
                        textoCompleto.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    // Aplicamos todo al TextView
                    binding.tvSaldoValue.text = spannable

                    // 3. ANIMACIÓN DE "LATIDO" (Feedback visual al cambiar saldo)
                    binding.layoutSaldo.animate()
                        .scaleX(1.1f)
                        .scaleY(1.1f)
                        .setDuration(200)
                        .withEndAction {
                            binding.layoutSaldo.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(200)
                                .start()
                        }.start()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error al obtener saldo: ${error.message}")
            }
        })
    }
    // Función auxiliar para saber si una vista está dentro del RecyclerView
    private fun isViewDescendantOf(view: View, parent: ViewGroup): Boolean {
        var current = view.parent
        while (current != null) {
            if (current == parent) return true
            current = current.parent
        }
        return false
    }

    // ==========================================
    // 1. CONFIGURACIÓN DE UI
    // ==========================================
    private fun setupMenuHorizontal() {
        val menuItems = listOf(
            "Activar", "Alquila", "Buscar",
            "Pedir", "Paquete", "Perfil", "TV", "Cerrar"
        )
        val menuIcons = listOf(
            R.drawable.cartelera,
            R.drawable.cine, R.drawable.buscar, R.drawable.pedido,
            R.drawable.activacion, R.drawable.home, R.drawable.tv, R.drawable.cerrrarp
        )


        // Asegúrate de usar los R.drawable correspondientes (aquí puse IDs de ejemplo)
        val menuList = menuItems.mapIndexed { i, name ->
            MenuPrincipalItem(name, menuIcons[i])
        }

        val adapter = MenuPrincipalAdapter(menuList) { item ->
            when (item.name) {
                "Activar" -> navegarGratis() // En esta pantalla, Inicio y Gratis suelen ser lo mismo
                "Buscar" -> buscarPeliculaDialogo()
                "Pedir" -> mostrarDialogoPedido()
                "Paquete" -> activarpaquete()
                "Alquila" -> navegarAPeliculasApi()
                "Perfil" -> {
                    val intent = Intent(this, Perfil::class.java)
                    startActivity(intent)
                }

                "TV" -> navegarATv()
                "Cerrar" -> cerrarSesion()
                else -> Toast.makeText(this, "${item.name} seleccionado", Toast.LENGTH_SHORT).show()
            }
        }

        binding.menuPrincipal.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.menuPrincipal.adapter = adapter
        binding.menuPrincipal.isFocusable = true
    }
      // 2. Funciones de Navegación corregidas para Actividades
    fun navegarGratis() {
        // Aquí abres la actividad de peliculas validas/gratis
        val intent = Intent(this, PeliculasValidasActivity::class.java)
        startActivity(intent)
    }

    fun navegarATv() {

        val intent = Intent(this, TvActivity::class.java)
        startActivity(intent)
    }

    fun navegarAPeliculasApi() {

        val intent = Intent(this, PeliculasApiActivity::class.java)
        startActivity(intent)
    }

    // 3. Configuración de la Grilla Adaptable
    private fun setupMovieGrid() {
        val columnas = ViewUtils.calcularColumnas(this)

        val layoutManager = object : GridLayoutManager(this, columnas) {
            override fun isAutoMeasureEnabled(): Boolean = false
        }
        layoutManager.initialPrefetchItemCount = 25

        binding.rvPeliculas.layoutManager = layoutManager
        binding.rvPeliculas.setHasFixedSize(true)
        binding.rvPeliculas.itemAnimator = null

        // 🟢 OBLIGATORIO: Desactivar scroll interno para funcionar en sincronía con el NestedScrollView
        binding.rvPeliculas.isNestedScrollingEnabled = false

        movieAdapter = MoviesAdapter(
            modeloList,
            onItemClick = { movie -> irAlReproductor(movie) },
            onFocusChange = { movie -> actualizarImagenDeFondo(movie.imageUrl) }
        )

        binding.rvPeliculas.adapter = movieAdapter
    }
    private fun actualizarImagenDeFondo(url: String?) {
        if (!url.isNullOrEmpty()) {
            Glide.with(this)
                .load(url)
                .centerCrop() // Asegura que llene toda la pantalla
                .error(R.drawable.pelifondo) // Imagen por defecto si falla
                .into(binding.imgFondo)

            // Opcional: ajustar la transparencia si se ve muy fuerte
            binding.imgFondo.alpha = 0.3f
        }
    }

    // ==========================================
    // 2. SEGURIDAD: CUENTA ELIMINADA
    // ==========================================

    private fun iniciarVerificacionDeEstadoDeCuenta() {
        val email = auth.currentUser?.email ?: return
        if (email == "invitado@fulltv.com") return

        val correoKey = email.replace(".", "_").replace("@", "_")
        val userRef = databaseRef.child("usuarios").child(correoKey)

        userStatusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (haProcesadoEliminacion) return


            }
            override fun onCancelled(error: DatabaseError) {}
        }
        userRef.addValueEventListener(userStatusListener!!)
    }


    // ==========================================
    // 3. ACTUALIZACIONES
    // ==========================================

    private fun obtenerNoticiaYActualizaciones() {
        val versionLocal = BuildConfig.VERSION_NAME
        // Apuntamos al nodo noticia en Realtime Database
        val ref = FirebaseDatabase.getInstance().getReference("noticia").child("us4vaaf0VPezu9vuc4ns")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val versionRemota = snapshot.child("versionapk").getValue(String::class.java) ?: ""

                    if (versionRemota.isNotEmpty() && esNuevaVersion(versionRemota, versionLocal)) {
                        mostrarAlertaActualizacion(versionRemota)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Error: ${error.message}")
            }
        })
    }

    private fun esNuevaVersion(remota: String, local: String): Boolean {
        val r = remota.split("."); val l = local.split(".")
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrNull(i)?.toIntOrNull() ?: 0
            val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    private fun mostrarAlertaActualizacion(version: String) {

        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")
        val fondoBoton = Color.parseColor("#1A1A1A")

        // Crear diálogo base
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        // Contenedor principal
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
            background = GradientDrawable().apply {
                setColor(colorFondo)
                cornerRadius = 20f
            }
        }

        // TÍTULO
        val titulo = TextView(this).apply {
            text = "🚀 Nueva Versión $version"
            setTextColor(colorDorado)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        // MENSAJE
        val mensaje = TextView(this).apply {
            text = "Hemos mejorado CineParche para ti. Actualiza ahora para disfrutar de la mejor experiencia."
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 20, 0, 30)
            gravity = Gravity.CENTER
        }

        // Contenedor de botones
        val botones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        // Función para crear fondo
        fun fondo(color: Int): GradientDrawable {
            return GradientDrawable().apply {
                setColor(color)
                cornerRadius = 12f
            }
        }

        // BOTÓN ACTUALIZAR
        val btnActualizar = TextView(this).apply {
            text = "ACTUALIZAR"
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(40, 20, 40, 20)
            background = fondo(fondoBoton)

            isFocusable = true
            isFocusableInTouchMode = true

            setOnClickListener {
                dialog.dismiss()
                descargarAPK(version)
            }
        }

        // BOTÓN LUEGO
        val btnLuego = TextView(this).apply {
            text = "LUEGO"
            setTextColor(Color.LTGRAY)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(40, 20, 40, 20)
            background = fondo(fondoBoton)

            isFocusable = true
            isFocusableInTouchMode = true

            setOnClickListener {
                dialog.dismiss()
            }
        }

        // Layout params para separar botones
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(20, 0, 20, 0)
        }

        btnActualizar.layoutParams = params
        btnLuego.layoutParams = params

        // --- EFECTO FOCO (clave para TV) ---
        fun aplicarFoco(view: TextView, colorTextoNormal: Int) {
            view.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    v.background = fondo(colorDorado)
                    v.scaleX = 1.1f
                    v.scaleY = 1.1f
                    v.elevation = 20f
                    (v as TextView).setTextColor(Color.BLACK)
                } else {
                    v.background = fondo(fondoBoton)
                    v.scaleX = 1f
                    v.scaleY = 1f
                    v.elevation = 4f
                    (v as TextView).setTextColor(colorTextoNormal)
                }
            }
        }

        aplicarFoco(btnActualizar, Color.WHITE)
        aplicarFoco(btnLuego, Color.LTGRAY)

        // Agregar vistas
        botones.addView(btnActualizar)
        botones.addView(btnLuego)

        container.addView(titulo)
        container.addView(mensaje)
        container.addView(botones)

        dialog.setContentView(container)

        // Fondo transparente del diálogo (para bordes redondeados)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.show()

        // Foco inicial (muy importante en TV)
        btnActualizar.requestFocus()
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun descargarAPK(version: String) {
        // Colores de identidad CineParche
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val url = "https://github.com/CreativeMB/center/releases/download/apk/CineParcheApp-debug.apk"
        val file = File(getExternalFilesDir(null), "CineParcheApp-debug.apk")
        if (file.exists()) file.delete()

        // --- DISEÑO PROFESIONAL CINEPARCHE ---
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            // Cambiamos el tinte de la barra de Rojo a Dorado
            progressTintList = ColorStateList.valueOf(colorDorado)
            progressBackgroundTintList = ColorStateList.valueOf(Color.GRAY)
        }

        val textoProgreso = TextView(this).apply {
            text = "Iniciando descarga segura..."
            setTextColor(Color.WHITE) // Blanco para legibilidad sobre azul
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 30, 0, 0)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            setBackgroundColor(colorFondo) // Fondo azul noche
            addView(progressBar)
            addView(textoProgreso)
        }

        // Título con estilo dorado
        val title = SpannableString("📥 Actualizando CineParche v$version")
        title.setSpan(ForegroundColorSpan(colorDorado), 0, title.length, 0)
        title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)

        progressDialog = AlertDialog.Builder(this)
            .setCustomTitle(TextView(this).apply {
                text = title
                textSize = 20f
                setPadding(60, 40, 60, 0)
                setTextColor(colorDorado)
                setBackgroundColor(colorFondo)
            })
            .setView(layout)
            .setCancelable(false)
            .create()

        progressDialog?.show()

        // Eliminamos bordes del sistema
        progressDialog?.window?.setBackgroundDrawable(ColorDrawable(colorFondo))

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("CineParche v$version")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))

        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        downloadId = dm.enqueue(request)

        val handler = Handler(Looper.getMainLooper())
        val monitor = object : Runnable {
            @SuppressLint("Range")
            override fun run() {
                val q = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(q)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    val downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                    if (total > 0) {
                        val progress = ((downloaded * 100) / total).toInt()
                        progressBar.progress = progress
                        textoProgreso.text = "Descargando... $progress%"
                        // El texto de porcentaje también puede resaltar en dorado
                        if (progress > 0) textoProgreso.setTextColor(colorDorado)
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        cursor.close()
                        textoProgreso.text = "Descarga completada ✅"
                        textoProgreso.setTextColor(Color.GREEN)
                        handler.postDelayed({
                            progressDialog?.dismiss()
                            instalarAPK(file)
                        }, 800)
                        return
                    }
                }
                cursor.close()
                handler.postDelayed(this, 500)
            }
        }
        handler.post(monitor)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), RECEIVER_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun instalarAPK(file: File) {
        val authority = "${packageName}.provider"
        val uri = FileProvider.getUriForFile(this, authority, file)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                // No tiene permiso, lo enviamos a configuración
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Autoriza la instalación y vuelve a intentarlo", Toast.LENGTH_LONG).show()
                return
            }
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("Instalador", "Error al abrir: ${e.message}")
        }
    }

    // ==========================================
    // 5. BUSCADOR TMDB
    // ==========================================
    private fun buscarPeliculaDialogo() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.buscador, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.search_edit_text)
        val searchResultsView = dialogView.findViewById<ListView>(R.id.list_view)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        progressBar.visibility = View.GONE

        val filteredModeloList = mutableListOf<Modelo>()
        val allFirebaseModelos = mutableListOf<Modelo>() // Cache local de Firebase

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        searchResultsView.adapter = adapter

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.show()

        // --- FUNCIÓN PARA QUITAR TILDES Y MAYÚSCULAS ---
        fun String.normalizar(): String {
            val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
            val temp = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            return diacritics.replace(temp, "").lowercase()
        }

        // 1. CARGAMOS TODOS LOS DATOS DE FIREBASE UNA SOLA VEZ AL ABRIR EL DIÁLOGO
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = FirebaseDatabase.getInstance().getReference("movies").get().await()
                val loadedMovies = snapshot.children.mapNotNull { doc ->
                    val m = doc.getValue(Modelo::class.java)
                    m?.copy(id = doc.key ?: "")
                }
                allFirebaseModelos.addAll(loadedMovies)
            } catch (e: Exception) {
                Log.e("FIREBASE", "Error cargando base de datos local: ${e.message}")
            }
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            private var searchJob: Job? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val queryRaw = s.toString().trim()
                val queryNormalizada = queryRaw.normalizar()

                if (queryRaw.length < 2) {
                    adapter.clear()
                    return
                }

                searchJob?.cancel()
                searchJob = CoroutineScope(Dispatchers.IO).launch {
                    // --- A. FILTRADO EN FIREBASE (LOCAL) ---
                    // Buscamos cualquier coincidencia en el título (infalible)
                    val firebaseMatches = allFirebaseModelos.filter {
                        it.title.normalizar().contains(queryNormalizada)
                    }.map { it.copy(title = "💿 ${it.title}") }

                    // --- B. BÚSQUEDA EN API ---
                    val apiResults = try {
                        val response = TMDbApiClient.service.searchMovies(
                            apiKey = "678193d2c735c6f37840cee035f4d69a",
                            language = "es-MX",
                            query = queryRaw
                        ).execute()

                        if (response.isSuccessful) {
                            response.body()?.results?.map {
                                Modelo(
                                    id = it.id.toString(),
                                    title = "🌐 ${it.title} (${it.release_date?.take(4) ?: "N/A"})",
                                    originalTitle = it.original_title,
                                    imageUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}",
                                    streamUrl = "https://tuservidor.com/stream/${it.id}",
                                    castv = 10,
                                    countdownMinutes = 0,
                                    createdAt = 0L
                                )
                            } ?: emptyList()
                        } else emptyList()
                    } catch (e: Exception) { emptyList() }

                    // --- C. COMBINAR ---
                    val combined = firebaseMatches + apiResults

                    withContext(Dispatchers.Main) {
                        filteredModeloList.clear()
                        filteredModeloList.addAll(combined)
                        adapter.clear()
                        adapter.addAll(filteredModeloList.map { it.title })
                        adapter.notifyDataSetChanged()
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        searchResultsView.setOnItemClickListener { _, _, position, _ ->
            val selectedMovie = filteredModeloList[position]
            irAlReproductor(selectedMovie)
            dialog.dismiss()
        }
    }

    // ==========================================
    // 6. PEDIDOS Y CasTV
    // ==========================================

    private fun mostrarDialogoPedido() {
        // 🎨 Tus colores exactos
        val colorTextoLogo = Color.parseColor("#C5A059") // El dorado
        val colorFondoPrincipal = Color.parseColor("#2A2A2A") // El azul con transparencia

        // 1. Contenedor Principal
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 50, 60, 50)
            // Aplicamos tu color de fondo principal
            setBackgroundColor(colorFondoPrincipal)
        }

        // 2. Título con el dorado del logo
        val titulo = TextView(this).apply {
            text = "SOLICITAR PELÍCULA"
            textSize = 22f
            setTextColor(colorTextoLogo)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 40)
        }

        // 3. Campo de entrada (EditText)
        val input = EditText(this).apply {
            hint = "Ej: Moana 2 (2024)"
            setHintTextColor(Color.parseColor("#BDBDBD")) // Gris claro para que sea legible
            setTextColor(Color.WHITE)
            textSize = 18f
            // La línea inferior del EditText en dorado
            background.setColorFilter(colorTextoLogo, PorterDuff.Mode.SRC_ATOP)
            setPadding(10, 25, 10, 25)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        layout.addView(titulo)
        layout.addView(input)

        // 4. Crear el diálogo
        val dialog = AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("SIGUIENTE", null)
            .setNegativeButton("CANCELAR", null)
            .create()

        dialog.show()

        // 5. Estilizar los botones

        // Botón SIGUIENTE en Dorado
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setTextColor(colorTextoLogo)
            textSize = 17f
            setTypeface(null, Typeface.BOLD)

            setOnClickListener {
                val nombrePeli = input.text.toString().trim()
                if (nombrePeli.isNotEmpty()) {
                    comprobantepago(nombrePeli)
                    dialog.dismiss()
                } else {
                    input.error = "Escribe el nombre"
                }
            }
        }

        // Botón CANCELAR en Blanco
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
        }
    }
    private fun comprobantepago(pedido: String) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val correoKey = email.replace(".", "_").replace("@", "_")

        // Colores de tu identidad
        val colorTextoLogo = Color.parseColor("#C5A059") // Dorado
        val colorFondoPrincipal = Color.parseColor("#2A2A2A") // Tu fondo oscuro

        databaseRef.child("usuarios").child(correoKey).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val nombreUsuario = snapshot.child("nombre").value?.toString() ?: "Usuario"
                val saldoActual = (snapshot.child("castv").value as? Number)?.toInt() ?: 0
                val costo = 20
                val saldoFinal = saldoActual - costo

                // 1. Contenedor Principal
                val layout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(60, 50, 60, 50)
                    setBackgroundColor(colorFondoPrincipal)
                }

                // 2. Título "Resumen de Pedido"
                val titulo = TextView(this).apply {
                    text = "CONFIRMAR PEDIDO"
                    textSize = 20f
                    setTextColor(colorTextoLogo)
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, 40)
                }

                // 3. Bloque de Datos (Información del pedido)
                val infoLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(30, 30, 30, 30)
                    // Le damos un borde sutil para que parezca una ficha
                    val shape = GradientDrawable().apply {
                        cornerRadius = 10f
                        setStroke(2, Color.parseColor("#444444"))
                    }
                    background = shape
                }

                val crearFila = { label: String, valor: String, resaltado: Boolean ->
                    TextView(this).apply {
                        text = "$label $valor"
                        textSize = if (resaltado) 17f else 15f
                        setTextColor(if (resaltado) colorTextoLogo else Color.WHITE)
                        setPadding(0, 8, 0, 8)
                    }
                }

                infoLayout.addView(crearFila("🎬 Película:", pedido, true))
                infoLayout.addView(crearFila("👤 Usuario:", nombreUsuario, false))
                infoLayout.addView(crearFila("💰 Saldo:", "$saldoActual CasTV", false))
                infoLayout.addView(crearFila("📉 Costo:", "$costo CasTV", false))

                // Separador sutil
                val linea = View(this).apply {
                    // Usamos ViewGroup.LayoutParams para acceder a MATCH_PARENT
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        2
                    ).apply {
                        setMargins(0, 20, 0, 20)
                    }
                    setBackgroundColor(Color.parseColor("#444444"))
                }
                infoLayout.addView(linea)
                infoLayout.addView(crearFila("✅ Saldo Final:", "$saldoFinal CasTV", true))

                layout.addView(titulo)
                layout.addView(infoLayout)

                // 4. Crear el Alert Dialog
                val dialog = AlertDialog.Builder(this)
                    .setView(layout)
                    .setCancelable(false)
                    .setPositiveButton("CONFIRMAR Y ENVIAR", null)
                    .setNegativeButton("CORREGIR", null)
                    .create()

                dialog.show()

                // 5. Estilo de Botones
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                    setTextColor(colorTextoLogo)
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setOnClickListener {
                        if (saldoActual >= costo) {
                            ejecutarProcesoFinal(correoKey, pedido, costo, nombreUsuario, email, dialog)

                        } else {
                            // 🟢 REEMPLAZO DEL TOAST POR CINEALERT PREMIUM
                            CineAlert.show(
                                this@PeliculasActivity, // O la actividad donde estés
                                "Saldo insuficiente en CasTV ❌",
                                CineAlert.Tipo.ERROR,
                                dialog.window?.decorView as? ViewGroup
                            )
                        }
                    }
                }

                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setOnClickListener {
                        dialog.dismiss()
                        mostrarDialogoPedido() // Regresa al anterior
                    }
                }
            }
        }
    }
    private fun ejecutarProcesoFinal(correoKey: String, titulo: String, costo: Int, nombre: String, email: String, dialog: AlertDialog) {
        val data = hashMapOf(
            "title" to titulo,
            "castv" to costo,
            "nombre" to nombre,
            "email" to email,
            "timestamp" to ServerValue.TIMESTAMP
        )

        // 1. Guardamos el pedido
        databaseRef.child("pedidosmovies").push().setValue(data).addOnSuccessListener {

            // 2. Descontamos los puntos y pasamos el dialog para que se cierre después
            descontarPuntos(correoKey, costo, titulo, dialog)

        }.addOnFailureListener {
            // 🔴 ERROR: Si falla el guardado, avisamos al usuario sin cerrar el diálogo
            CineAlert.show(
                this@PeliculasActivity,
                "Error al registrar pedido ❌",
                CineAlert.Tipo.ERROR,
                dialog.window?.decorView as? ViewGroup
            )
        }
    }

    private fun descontarPuntos(correoKey: String, puntosADescontar: Int, tituloPelicula: String, dialog: AlertDialog) {
        val userRef = FirebaseDatabase.getInstance().getReference("usuarios").child(correoKey)

        userRef.child("castv").get().addOnSuccessListener { snapshot ->
            val castvActual = (snapshot.value as? Number)?.toInt() ?: 0

            if (castvActual >= puntosADescontar) {
                val nuevoCastv = castvActual - puntosADescontar

                userRef.child("castv").setValue(nuevoCastv).addOnSuccessListener {
                    val email = correoKey.replace("_", ".")
                    CastvHelper.registrarConsumo(email, "PEDIDO: $tituloPelicula", puntosADescontar)

                    // 🟢 AVISO FINAL DE ÉXITO (Único y claro)
                    CineAlert.show(
                        this@PeliculasActivity,
                        "¡Pedido realizado con éxito! 🎬",
                        CineAlert.Tipo.EXITO,
                        dialog.window?.decorView as? ViewGroup
                    ) {
                        // 🕒 TRAS 2.5 SEGUNDOS, CERRAMOS EL DIÁLOGO
                        dialog.dismiss()
                    }
                }
            } else {
                // Error de saldo
                CineAlert.show(this@PeliculasActivity, "Saldo insuficiente 💰", CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
            }
        }
    }
    private fun activarpaquete() {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val uid = user.uid
        val correoKey = email.replace(".", "_").replace("@", "_")

        val colorTextoLogo = Color.parseColor("#C5A059")
        val colorFondoPrincipal = Color.parseColor("#2A2A2A")

        // 1. Contenedor principal sin ScrollView para forzar el ajuste
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 20) // Reducido de 60/50 a 40/30
            setBackgroundColor(colorFondoPrincipal)
        }

        // 2. Título y Descripción más compactos
        val titulo = TextView(this).apply {
            text = "💎 ACTIVAR PAQUETE"
            textSize = 18f // Reducido de 22f a 18f
            setTextColor(colorTextoLogo)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15) // Espacio inferior reducido a la mitad
        }

        val descripcion = TextView(this).apply {
            text = "Selecciona el paquete que pagaste:"
            textSize = 14f // Reducido de 16f a 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 10)
        }

        // 3. RadioGroup con menos padding
        val radioGroup = android.widget.RadioGroup(this).apply {
            setPadding(10, 0, 10, 10)
        }

        fun crearRadioButton(texto: String): android.widget.RadioButton {
            return android.widget.RadioButton(this).apply {
                text = texto
                setTextColor(Color.WHITE)
                textSize = 14f // Reducido de 16f a 14f
                buttonTintList = ColorStateList.valueOf(colorTextoLogo)
                id = View.generateViewId()
                setPadding(15, 10, 15, 10) // Padding interno mucho más pequeño
            }
        }

        val rbPlata = crearRadioButton("Bronce: $10.000 (50 Castv)")
        val rbBronce = crearRadioButton("Plata: $22.000 (120 Castv)")
        val rbOro = crearRadioButton("Oro: $45.000 (250 Castv)")

        radioGroup.addView(rbPlata)
        radioGroup.addView(rbBronce)
        radioGroup.addView(rbOro)
        rbPlata.isChecked = true

        // 4. EL CUADRO DE TEXTO ajustado
        val inputReferencia = EditText(this).apply {
            id = View.generateViewId()
            hint = "Banco y Nombre de quien envía"
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 15f
            isFocusable = true
            isFocusableInTouchMode = true

            val gd = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 8f // Bordes más discretos
                setStroke(2, colorTextoLogo) // Borde más delgado
            }
            background = gd

            setPadding(25, 25, 25, 25) // Altura del cuadro reducida

            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 10) // Márgenes externos reducidos
            }

            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

        layout.addView(titulo)
        layout.addView(descripcion)
        layout.addView(radioGroup)
        layout.addView(inputReferencia)

        // 5. Mostrar Diálogo
        val dialog = AlertDialog.Builder(this)
            .setView(layout) // Usamos el layout directamente
            .setPositiveButton("ENVIAR REPORTE", null)
            .setNegativeButton("CANCELAR", null)
            .create()

        dialog.show()

        // Lógica de botones (Sin cambios en funcionalidad)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setTextColor(colorTextoLogo)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                val detalle = inputReferencia.text.toString().trim()
                if (detalle.isEmpty()) {
                    inputReferencia.error = "Faltan detalles"
                    return@setOnClickListener
                }

                val planSeleccionado = when (radioGroup.checkedRadioButtonId) {
                    rbPlata.id -> "PLATA"
                    rbBronce.id -> "BRONCE"
                    rbOro.id -> "ORO"
                    else -> "DESCONOCIDO"
                }
                val puntosPlan = when (radioGroup.checkedRadioButtonId) {
                    rbPlata.id -> 50
                    rbBronce.id -> 120
                    rbOro.id -> 250
                    else -> 0
                }

                lifecycleScope.launch {
                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            databaseRef.child("usuarios").child(correoKey).get().await()
                        }
                        val nombreReal = snapshot.child("nombre").value?.toString() ?: "Usuario"
                        val data = hashMapOf(
                            "title" to "$planSeleccionado - $detalle",
                            "castv" to puntosPlan,
                            "email" to email,
                            "nombre" to nombreReal,
                            "timestamp" to ServerValue.TIMESTAMP,
                            "userId" to uid
                        )
                        withContext(Dispatchers.IO) {
                            databaseRef.child("pedidosmovies").push().setValue(data).await()
                        }
                        CineAlert.show(this@PeliculasActivity, "✅ Enviado", CineAlert.Tipo.EXITO,
                            dialog.window?.decorView as? ViewGroup)
                        {
                        dialog.dismiss()
                        }
                    } catch (e: Exception) {
                        CineAlert.show(this@PeliculasActivity, "❌ Error",  CineAlert.Tipo.ERROR,
                            dialog.window?.decorView as? ViewGroup)
                    }
                }
            }
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }
    }
    // ==========================================
    // 7. LISTA Y REPRODUCTOR
    // ==========================================

    private fun escucharCambiosEnPeliculas() {
        if (yaTieneListener) return // Evitamos duplicar el listener

        val moviesRef = databaseRef.child("movies")
        peliculasListener = moviesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val nuevasPeliculasRaw = mutableListOf<Modelo>()

                    // 1. Mapeamos los IDs de las películas ya validadas para compararlas rápido
                    val yaValidadas = Validacioneslista.obtenerPeliculasValidas().map { it.id }.toSet()

                    // 2. Extraemos los datos de Firebase
                    for (child in snapshot.children) {
                        val modelo = child.getValue(Modelo::class.java)
                        if (modelo != null) {
                            // Copiamos el objeto incluyendo el ID del nodo de Firebase
                            val movieConId = modelo.copy(id = child.key ?: "")

                            // Si ya está validada, marcamos el flag para que el adapter lo sepa
                            if (yaValidadas.contains(movieConId.id)) {
                                movieConId.isValid = true
                            }
                            nuevasPeliculasRaw.add(movieConId)
                        }
                    }

                    // 3. Ordenamos por fecha de creación (de más nueva a más vieja)
                    val listaNuevaOrdenada = nuevasPeliculasRaw.sortedByDescending { it.createdAt }

                    // 4. Lógica de actualización Inteligente (Premium)
                    if (modeloList.isEmpty()) {
                        // Primera carga: Llenamos y notificamos todo de golpe para rapidez
                        modeloList.addAll(listaNuevaOrdenada)
                        movieAdapter.notifyDataSetChanged()
                    } else {
                        // Cargas posteriores o cambios en vivo: Usamos DiffUtil para evitar parpadeos
                        val listaVieja = ArrayList(modeloList) // Copia de seguridad de la lista actual

                        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                            override fun getOldListSize(): Int = listaVieja.size
                            override fun getNewListSize(): Int = listaNuevaOrdenada.size

                            override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                                return listaVieja[oldPos].id == listaNuevaOrdenada[newPos].id
                            }

                            override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                                // Esto compara todos los campos de la data class Modelo
                                return listaVieja[oldPos] == listaNuevaOrdenada[newPos]
                            }
                        })

                        // Actualizamos la lista principal y aplicamos los cambios quirúrgicos
                        modeloList.clear()
                        modeloList.addAll(listaNuevaOrdenada)
                        diffResult.dispatchUpdatesTo(movieAdapter)
                    }

                    // 5. Verificamos si hay que validar contenido nuevo
                    if (!Validacioneslista.yaCargado()) {
                        validarYActualizarVistasEnVivo()
                    }

                    yaTieneListener = true
                } else {
                    // Si el nodo "movies" está vacío
                    modeloList.clear()
                    movieAdapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                yaTieneListener = false
                // Aquí podrías agregar un Log para debuggear fallos de conexión
            }
        })
    }
    private fun validarYActualizarVistasEnVivo() {
        // No borramos nombres, usamos la lógica masiva local
        CoroutineScope(Dispatchers.Main).launch {
            val validador = Validaciones()

            // Hacemos una copia para no tener errores de concurrencia
            val listaActual = ArrayList(modeloList)

            // Lanzamos la validación de cada película INDEPENDIENTEMENTE
            listaActual.forEach { movie ->
                launch(Dispatchers.Main) {
                    // Cada película se valida en su propio "hilo" de corrutina
                    val esValida = withContext(Dispatchers.IO) {
                        validador.isUrlValid(movie.streamUrl)
                    }

                    // Buscamos la posición por si la lista se movió (scroll)
                    val posicionActual = modeloList.indexOfFirst { it.id == movie.id }
                    if (posicionActual != -1) {
                        modeloList[posicionActual].isValid = esValida
                        // 🔄 ACTUALIZA LA VISTA AL INSTANTE (una por una)
                        movieAdapter.notifyItemChanged(posicionActual)
                    }
                }
            }

            // Al final, guardamos en el objeto para que otras actividades lo usen
            // Pero el usuario ya vio los resultados uno por uno antes de que esto termine
            Validacioneslista.cargarPeliculas()
        }
    }

    // Nueva función de apoyo para refrescar etiquetas rápido
    private fun sincronizarConCacheLocal() {
        val validadas = Validacioneslista.obtenerPeliculasValidas().map { it.id }.toSet()

        for (index in modeloList.indices) {
            val movie = modeloList[index]
            if (validadas.contains(movie.id) && !movie.isValid) {
                movie.isValid = true
                movieAdapter.notifyItemChanged(index)
            }
        }
    }
    private fun irAlReproductor(modelo: Modelo) {
        // 1. Verificación de seguridad: No iniciar si el link es nulo
        if (modelo.streamUrl.isNullOrBlank()) {
            Toast.makeText(this, "El enlace de reproducción no es válido", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ApiPeliculaActivity::class.java).apply {
            // 2. Flags de optimización:
            // CLEAR_TOP: Si la actividad ya existe, cierra las que están encima y la trae al frente.
            // SINGLE_TOP: Evita crear una copia nueva si ya estás en ella (usa onNewIntent).
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

            // 3. Empaquetado limpio de datos
            putExtra("EXTRA_MOVIE_DATA", modelo)
            putExtra("EXTRA_STREAM_URL", modelo.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", modelo.title)
            putExtra("EXTRA_MOVIE_CASTV", modelo.castv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", modelo.imageUrl)
            putExtra("EXTRA_ORIGINAL_TITLE", modelo.originalTitle)
            putExtra("EXTRA_COUNTDOWN", modelo.countdownMinutes)

            // Evitamos errores de precisión enviando el Long directamente si es necesario
            putExtra("EXTRA_CREATED_AT", modelo.createdAt / 1000)
        }

        // 4. Ejecución
        startActivity(intent)

        // Opcional: Quitar animación para que el cambio de link sea instantáneo
        overridePendingTransition(0, 0)
    }

    // --- En PeliculasActivity.kt ---

    private fun cerrarSesion() {
        // 1. Cerrar sesión en Firebase (Fundamental)
        auth.signOut()

        // 2. Configurar y cerrar Google
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInClient.signOut().addOnCompleteListener {
            // Opcional: Revocar acceso limpia el rastro de la cuenta de Google en el selector
            googleSignInClient.revokeAccess().addOnCompleteListener {
                // 3. Navegar al Login limpiando el historial de actividades
                val intent = Intent(this, Login::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
    }


    private fun mostrarConfirmacionSalida() {
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val title = SpannableString("¿Desea cerrar CineParche?")
        title.setSpan(ForegroundColorSpan(colorDorado), 0, title.length, 0)
        title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, 0)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Si sales ahora, te perderás lo mejor del parche.")
            .setPositiveButton("SÍ, SALIR") { _, _ ->
                finishAffinity()
                System.exit(0)
            }
            .setNegativeButton("VOLVER (5s)", null) // Texto inicial
            .create()

        dialog.show()

        // Estética del fondo y mensaje
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorFondo))
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.WHITE)

        val btnNegativo = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val btnPositivo = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        btnNegativo.setTextColor(colorDorado)
        btnPositivo.setTextColor(Color.WHITE)

        // --- Lógica del Contador ---
        val timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val segundosRestantes = millisUntilFinished / 1000
                btnNegativo.text = "VOLVER (${segundosRestantes}s)"
            }

            override fun onFinish() {
                if (dialog.isShowing) {
                    dialog.dismiss() // Se cierra sin hacer nada
                }
            }
        }

        timer.start()

        // Si el usuario presiona un botón manualmente, detenemos el timer
        dialog.setOnDismissListener { timer.cancel() }
    }

    override fun onStart() {
        super.onStart()

        // Solo iniciamos el listener si la lista está vacía
        if (modeloList.isEmpty()) {
            escucharCambiosEnPeliculas()
        } else {
            // Si ya hay películas, solo asegúrate de que las etiquetas estén al día
            sincronizarConCacheLocal()
        }

        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        am.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerTimer?.cancel()
        promoRotationRunnable?.let { promoRotationHandler.removeCallbacks(it) }

        // 1. Limpiar el Receptor de Descargas (APK)
        try {
            unregisterReceiver(onDownloadComplete)
        } catch (e: Exception) {
            // Ya estaba desregistrado o nunca se activó
        }

        // 2. Limpiar Listener de Películas (Realtime Database)
        peliculasListener?.let {
            databaseRef.child("movies").removeEventListener(it)
        }

        // 3. Limpiar Listener de Seguridad (Estado de cuenta)
        // Es vital quitarlo de la ruta exacta del usuario
        val email = auth.currentUser?.email
        if (email != null && userStatusListener != null) {
            val correoKey = email.replace(".", "_").replace("@", "_")
            databaseRef.child("usuarios").child(correoKey).removeEventListener(userStatusListener!!)
        }

        // 4. Cerrar todos los diálogos abiertos para evitar error "WindowLeaked"
        publicidadDialog?.let { if (it.isShowing) it.dismiss() }
        progressDialog?.let { if (it.isShowing) it.dismiss() } // El de la descarga roja

        // 5. Limpiar el Handler (Detiene el monitor de progreso de descarga)
        handler.removeCallbacksAndMessages(null)

        Log.d("PeliculasActivity", "Limpieza de onDestroy completada")
    }
}