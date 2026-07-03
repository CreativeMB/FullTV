package com.creativem.fulltv.peliculas

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Bitmap
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
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
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
import com.creativem.fulltv.peliculas.BannerPromosAdapter
import com.creativem.fulltv.R
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.api.MovieDetailResponse
import com.creativem.fulltv.api.MovieResponse
import com.creativem.fulltv.api.PeliculasApiActivity
import com.creativem.fulltv.api.TMDbApiClient
import com.creativem.fulltv.api.TMDbApiService
import com.creativem.fulltv.databinding.ActivityPeliculasBinding
import com.creativem.fulltv.menu.MenuPrincipalAdapter
import com.creativem.fulltv.menu.MenuPrincipalItem
import com.creativem.fulltv.peliculasvalidas.PeliculasValidasActivity
import com.creativem.fulltv.peliculasvalidas.Validaciones
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.CineAlert
import com.creativem.fulltv.principal.Login
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.principal.Perfil
import com.creativem.fulltv.principal.ViewUtils
import com.creativem.fulltv.tv.TvActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.creativem.fulltv.BuildConfig
import com.creativem.fulltv.menu.MenuPrincipalVerticalAdapter
import com.creativem.fulltv.mundial.Mundial
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import androidx.transition.TransitionManager

class PeliculasActivity : AppCompatActivity() {
    companion object {
        private var haVerificadoAlquileresEnEstaSesion = false
        // 🟢 CACHÉ GLOBAL: Recibe las primeras 24 películas pre-validadas de la SplashActivity
        val primeraPaginaPrecalculada = mutableListOf<Modelo>()
        var bannerPeliculaInicial: Modelo? = null
        fun restablecerEstadoSesion() {
            haVerificadoAlquileresEnEstaSesion = false
            primeraPaginaPrecalculada.clear()
        }
        private var bannerHeightCached = -1
        private var currentDisplayedMovieId: String? = null
    }

//    import com.creativem.fulltv.BuildConfig
private var usuarioEsperandoMas = false
    private val modeloList = mutableListOf<Modelo>() // Lista visible en pantalla
    private val peliculasCompletas = mutableListOf<Modelo>() // Caché del catálogo completo de Firebase
    private val siguientePaginaCache = mutableListOf<Modelo>() // Caché de la página pre-validada lista para insertar

    private val ITEMS_POR_PAGINA = 24 // Subimos el tamaño a 24 como propuso
    private var paginasCargadas = 1
    private var cargandoSiguientePagina = false

    private var haVerificadoAlquileresEnEstaSesion = false

    private var primeraCargaBanner = true

    private var jobRotacion: Job? = null
    private var yaTieneListener = false
    private lateinit var binding: ActivityPeliculasBinding
    private lateinit var movieAdapter: MoviesAdapter
     private val auth by lazy { FirebaseAuth.getInstance() }
    private val databaseRef: DatabaseReference = FirebaseDatabase.getInstance().reference
    private var peliculasListener: ValueEventListener? = null
    private var userStatusListener: ValueEventListener? = null
    private var downloadId: Long = -1

    private var haProcesadoEliminacion = false
    private var isRotationRunning = false
    private var publicidadDialog: Dialog? = null
    private var lastFocusedMovie: View? = null
    private val handler = Handler(Looper.getMainLooper())
    // Variable global para respaldar el diseño original del menú en móviles
    private var originalMenuLayoutParams: ViewGroup.LayoutParams? = null
    private var isMenuExpanded = false
    private var progressDialog: AlertDialog? = null
    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"
    private var bannerTimer: CountDownTimer? = null

    private val promoRotationHandler = Handler(Looper.getMainLooper())
    private var promoRotationRunnable: Runnable? = null
    private val peliculasPromoList = mutableListOf<Modelo>()
    private var currentPromoIndex = 0

    private var isUserInteractingWithPromo = false
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        isUserInteractingWithPromo = false
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
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.black)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        binding = ActivityPeliculasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 🟢 1. INICIALIZACIÓN INMEDIATA DE RETROFIT / API SERVICE:
        // Debe ocurrir antes de cualquier intento de dibujo del banner para evitar crashes de lateinit
        val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        apiService = retrofit.create(TMDbApiService::class.java)

        // 2. Inicialización de la primera página desde memoria caché rápida
        if (primeraPaginaPrecalculada.isNotEmpty()) {
            modeloList.clear()
            modeloList.addAll(primeraPaginaPrecalculada)
        } else {
            val peliculasYaCargadas = Validacioneslista.obtenerPeliculasValidas()
            if (peliculasYaCargadas.isNotEmpty()) {
                modeloList.clear()
                modeloList.addAll(peliculasYaCargadas.take(ITEMS_POR_PAGINA))
            }
        }

        // 🟢 3. RESPALDO SÍNCRONO DEL BANNER:
        if (bannerPeliculaInicial == null && primeraPaginaPrecalculada.isNotEmpty()) {
            bannerPeliculaInicial = primeraPaginaPrecalculada.firstOrNull()
        }

        // Pintado instantáneo seguro del banner ahora que apiService está inicializado
        val bannerContainer = findViewById<View>(R.id.layoutBannerNetflix)
        val bannerPeli = bannerPeliculaInicial
        if (bannerPeli != null) {
            mostrarDatosPeliculaEnBanner(bannerPeli)
            bannerContainer.visibility = View.VISIBLE
        } else {
            bannerContainer.visibility = View.GONE
        }

        setupMenuHorizontal()
        setupMovieGrid()

        binding.root.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus != null) {
                if (isViewDescendantOf(newFocus, binding.rvPeliculas)) {
                    lastFocusedMovie = newFocus
                }
                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                if (isLandscape) {
                    val focusInMenu = isViewDescendantOf(newFocus, binding.menuPrincipal) || newFocus == binding.menuPrincipal
                    setMenuExpandedState(focusInMenu)
                }
            }
        }

        onBackPressedDispatcher.addCallback(this) {
            mostrarConfirmacionSalida()
        }

        // Tareas diferidas de red
        handler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                escucharCambiosEnPeliculas()
                iniciarVerificacionDeEstadoDeCuenta()
                obtenerNoticiaYActualizaciones()
                configurarAnimacionDelBanner()
                cargarBannerPromocional()
                intentarVerificacionAlquileres()
            }
        }, 150)

        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrBlank()) {
            val nombreAMostrar = if (currentUser.email == "invitado@fulltv.com") {
                "Estas En Invitado"
            } else {
                currentUser.displayName ?: "Usuario"
            }
            CastvHelper.nuevosusuarios(this, nombreAMostrar, currentUser.email!!)
        }
        escucharSaldoUsuario()
    }
    // Agregue esta función dentro de la clase PeliculasActivity
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // El estado de la vista, scroll y adaptadores se mantiene intacto de forma nativa.
    }

    private fun useryoutube() {
        val intent = Intent(this, Mundial::class.java)
        startActivity(intent)
    }
    private fun intentarVerificacionAlquileres() {
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.email.isNullOrBlank()) {
            if (!haVerificadoAlquileresEnEstaSesion) {
                haVerificadoAlquileresEnEstaSesion = true
                // Retraso de seguridad para que no interfiera visualmente con la transición
                handler.postDelayed({
                    if (!isFinishing && !isDestroyed) {
                        verificarAlquileresActivos()
                    }
                }, 2000)
            }
        } else {
            handler.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    intentarVerificacionAlquileres()
                }
            }, 1500)
        }
    }

    private fun configurarAnimacionDelBanner() {
        val rvPeliculas = findViewById<RecyclerView>(R.id.rvPeliculas)
        val rvBannerPromos = findViewById<RecyclerView>(R.id.rvBannerPromos)

        window.decorView.viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus == null) return@addOnGlobalFocusChangeListener

            val focoEnPeliculas = rvPeliculas?.findContainingItemView(newFocus) != null
            val focoEnGuiones = rvBannerPromos?.findContainingItemView(newFocus) != null || newFocus == rvBannerPromos

            if (focoEnPeliculas) {
                alternarVisibilidadBanner(false)
                detenerRotacionAutomatica()
            } else {
                if (peliculasPromoList.isNotEmpty()) {
                    alternarVisibilidadBanner(true)
                }

                if (focoEnGuiones) {
                    detenerRotacionAutomatica()
                    isUserInteractingWithPromo = true
                } else {
                    isUserInteractingWithPromo = false
                    iniciarRotacionAutomaticaDesde()
                }
            }
        }
    }
    private var bannerAnimationRunnable: Runnable? = null

    private fun alternarVisibilidadBanner(mostrar: Boolean) {
        val banner = findViewById<View>(R.id.layoutBannerNetflix) ?: return

        // Evitamos reprogramar la animación si ya se solicitó el mismo estado de visibilidad
        if (banner.tag == mostrar) return
        banner.tag = mostrar

        bannerAnimationRunnable?.let { handler.removeCallbacks(it) }

        bannerAnimationRunnable = Runnable {
            if (bannerHeightCached == -1) {
                bannerHeightCached = banner.height
                if (bannerHeightCached <= 0) {
                    banner.post {
                        bannerHeightCached = banner.height
                        ejecutarAnimacionDeCortina(mostrar)
                    }
                    return@Runnable
                }
            }
            ejecutarAnimacionDeCortina(mostrar)
        }

        // 180ms es el tiempo óptimo para absorber los rebotes de pérdida de foco en Android TV
        handler.postDelayed(bannerAnimationRunnable!!, 180)
    }

    private fun ejecutarAnimacionDeCortina(mostrar: Boolean) {
        val banner = findViewById<View>(R.id.layoutBannerNetflix) ?: return
        val parentView = binding.root as? ViewGroup ?: return

        val isCurrentlyVisible = banner.visibility == View.VISIBLE
        if (mostrar == isCurrentlyVisible) {
            return
        }

        // 🟢 TRANSICIÓN SIMULTÁNEA UNIFICADA:
        // Creamos un set de transición que ejecuta el rediseño de límites (ChangeBounds)
        // y el desvanecimiento (Fade) AL MISMO TIEMPO, eliminando el parpadeo secuencial.
        val transition = androidx.transition.TransitionSet().apply {
            ordering = androidx.transition.TransitionSet.ORDERING_TOGETHER
            addTransition(androidx.transition.ChangeBounds())
            addTransition(androidx.transition.Fade())
            duration = 300
            interpolator = android.view.animation.DecelerateInterpolator()
        }

        androidx.transition.TransitionManager.beginDelayedTransition(parentView, transition)

        if (mostrar) {
            banner.visibility = View.VISIBLE
            banner.alpha = 1f
        } else {
            banner.visibility = View.GONE
            banner.alpha = 0f
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
                    val indexInicial = if (bannerPeliculaInicial != null) {
                        peliculasPromoList.indexOfFirst { it.id == bannerPeliculaInicial?.id }.coerceAtLeast(0)
                    } else {
                        (0 until peliculasPromoList.size).random()
                    }

                    currentPromoIndex = indexInicial
                    isUserInteractingWithPromo = false

                    if (bannerContainer.visibility != View.VISIBLE) {
                        mostrarDatosPeliculaEnBanner(peliculasPromoList[indexInicial])
                        alternarVisibilidadBanner(true)
                    }

                    rvBannerPromos.layoutManager = LinearLayoutManager(
                        this,
                        LinearLayoutManager.HORIZONTAL,
                        false
                    )

                    val adapter = BannerPromosAdapter(
                        list = peliculasPromoList,
                        onMovieFocused = { movieSeleccionado ->
                            detenerRotacionAutomatica()
                            registrarActividadUsuario()

                            currentPromoIndex = peliculasPromoList.indexOfFirst {
                                it.id == movieSeleccionado.id
                            }.coerceAtLeast(0)
                            mostrarDatosPeliculaEnBanner(movieSeleccionado)

                            val rv = findViewById<RecyclerView>(R.id.rvBannerPromos)
                            val layoutManager = rv.layoutManager as? LinearLayoutManager
                            layoutManager?.scrollToPositionWithOffset(currentPromoIndex, 0)
                        },
                        onMovieClicked = { movieSeleccionado ->
                            irAlReproductor(movieSeleccionado)
                        }
                    )
                    rvBannerPromos.adapter = adapter

                    rvBannerPromos.post {
                        val currentAdapter = rvBannerPromos.adapter as? BannerPromosAdapter
                        currentAdapter?.updateSelectedPosition(indexInicial, rvBannerPromos)

                        val layoutManager = rvBannerPromos.layoutManager as? LinearLayoutManager
                        layoutManager?.scrollToPositionWithOffset(indexInicial, 0)
                    }

                    iniciarRotacionAutomaticaDesde(indexInicial)
                } else {
                    alternarVisibilidadBanner(false)
                }
            } else {
                alternarVisibilidadBanner(false)
            }
        }.addOnFailureListener {
            if (bannerPeliculaInicial == null) {
                alternarVisibilidadBanner(false)
            }
        }
    }

    private fun iniciarRotacionAutomaticaDesde(inicio: Int = currentPromoIndex) {
        detenerRotacionAutomatica()
        if (peliculasPromoList.isEmpty()) return

        // 👇 CORRECCIÓN: Comprobamos de manera segura si el DialogFragment está activo en la jerarquía
        val isDialogShowing = supportFragmentManager.findFragmentByTag("AlquileresDialog") != null
        if (isDialogShowing) return

        jobRotacion = lifecycleScope.launch {
            var indiceActual = inicio

            while (isActive) {
                delay(6000)

                if (!isUserInteractingWithPromo && peliculasPromoList.isNotEmpty()) {
                    indiceActual = (indiceActual + 1) % peliculasPromoList.size
                    currentPromoIndex = indiceActual

                    val pelicula = peliculasPromoList[indiceActual]

                    withContext(Dispatchers.Main) {
                        mostrarDatosPeliculaEnBanner(pelicula)

                        val rvBannerPromos = findViewById<RecyclerView>(R.id.rvBannerPromos)
                        val adapter = rvBannerPromos.adapter as? BannerPromosAdapter
                        adapter?.updateSelectedPosition(indiceActual, rvBannerPromos)

                        val layoutManager = rvBannerPromos.layoutManager as? LinearLayoutManager
                        layoutManager?.scrollToPositionWithOffset(indiceActual, 0)
                    }
                }
            }
        }
    }

    private fun detenerRotacionAutomatica() {
        isRotationRunning = false
        jobRotacion?.cancel()
        jobRotacion = null

        promoRotationRunnable?.let {
            promoRotationHandler.removeCallbacks(it)
        }
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }

    private fun registrarActividadUsuario() {
        isUserInteractingWithPromo = true
        detenerRotacionAutomatica()
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

        if (isFinishing || isDestroyed) return

        // 🟢 OPTIMIZACIÓN CRÍTICA: Si ya estamos mostrando esta película, no reiniciamos el banner a negro
        if (currentPromoIndex != -1 && bannerPeliculaInicial?.id == movie.id && !primeraCargaBanner) {
            return
        }
        if (lastFocusedMovie == null && !primeraCargaBanner && movie.id == peliculasPromoList.getOrNull(currentPromoIndex)?.id) {
            return
        }

        if (primeraCargaBanner) {
            primeraCargaBanner = false
            cargarDatosEnBanner(
                movie, tvTitulo, tvSinopsis, tvCalificacion,
                tvBannerInfoAdicional, tvContador, ivBackdrop, ivPoster, layoutInfo
            )
        } else {
            // Reducimos el tiempo de desvanecimiento para que la transición entre promos sea más rápida
            layoutInfo.animate().alpha(0f).setDuration(250).withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                cargarDatosEnBanner(
                    movie, tvTitulo, tvSinopsis, tvCalificacion,
                    tvBannerInfoAdicional, tvContador, ivBackdrop, ivPoster, layoutInfo
                )
            }.start()
        }
    }

    private fun cargarDatosEnBanner(
        movie: Modelo,
        tvTitulo: TextView,
        tvSinopsis: TextView,
        tvCalificacion: TextView,
        tvBannerInfoAdicional: TextView,
        tvContador: TextView,
        ivBackdrop: ImageView,
        ivPoster: ImageView,
        layoutInfo: LinearLayout
    ) {
        // 1. CARGA INMEDIATA: Pintamos el fondo local y los textos antes de llamar a cualquier API
        cargarImagenSuave(movie.imageUrl, ivBackdrop)
        cargarImagenSuave(movie.imageUrl, ivPoster)

        tvTitulo.text = movie.title
        tvSinopsis.text = movie.overview.ifBlank { "Estreno exclusivo." }
        tvCalificacion.text = "⭐ 8.5"
        tvBannerInfoAdicional.text = movie.genres.ifBlank { "Acción • Aventura • Cine" }

        // Aseguramos visibilidad inmediata de los textos locales
        layoutInfo.animate().cancel()
        layoutInfo.alpha = 1f

        val queryBusqueda = movie.originalTitle.ifBlank { movie.title }
        apiService.searchMovie(apiKey, "es-MX", queryBusqueda).enqueue(object : retrofit2.Callback<MovieResponse> {
            override fun onResponse(call: retrofit2.Call<MovieResponse>, response: retrofit2.Response<MovieResponse>) {
                if (isFinishing || isDestroyed) return

                if (response.isSuccessful) {
                    val result = response.body()?.results?.firstOrNull()
                    if (result != null) {
                        val fechaCompleta = result.release_date ?: ""
                        tvTitulo.text = if (fechaCompleta.isNotEmpty()) {
                            "${result.title} ($fechaCompleta)"
                        } else {
                            result.title
                        }

                        tvSinopsis.text = result.overview ?: movie.overview
                        val anio = result.release_date?.take(4) ?: "2026"
                        val cal = if (result.vote_average > 0.0) "${result.vote_average}" else "8.5"
                        tvCalificacion.text = "⭐ $cal   |   $anio"

                        apiService.getMovieDetails(result.id, apiKey, "es-MX").enqueue(object : retrofit2.Callback<MovieDetailResponse> {
                            override fun onResponse(call: retrofit2.Call<MovieDetailResponse>, response: retrofit2.Response<MovieDetailResponse>) {
                                if (isFinishing || isDestroyed) return
                                if (response.isSuccessful) {
                                    val detalles = response.body()
                                    val generos = detalles?.genres?.joinToString(" • ") { it.name } ?: "Desconocidos"
                                    val duracion = detalles?.runtime ?: 0
                                    tvBannerInfoAdicional.text = "🎭 $generos  ⏱️ ${duracion} Min"
                                }
                            }
                            override fun onFailure(call: retrofit2.Call<MovieDetailResponse>, t: Throwable) {
                                if (isFinishing || isDestroyed) return
                            }
                        })

                        val backdropUrl = "https://image.tmdb.org/t/p/w1280${result.backdrop_path ?: result.poster_path}"
                        val posterUrl = "https://image.tmdb.org/t/p/w500${result.poster_path ?: result.backdrop_path}"
                        cargarImagenSuave(backdropUrl, ivBackdrop)
                        cargarImagenSuave(posterUrl, ivPoster)
                    }
                }
            }

            override fun onFailure(call: retrofit2.Call<MovieResponse>, t: Throwable) {
                if (isFinishing || isDestroyed) return
            }
        })

        val durationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
        val createdAtMillis = if (movie.createdAt > 0 && movie.createdAt < 1000000000000L) movie.createdAt * 1000 else movie.createdAt
        val elapsed = System.currentTimeMillis() - createdAtMillis
        iniciarContadorBanner(tvContador, durationMillis - elapsed)
    }

    private fun cargarImagenSuave(url: String, imageView: ImageView) {
        if (isFinishing || isDestroyed || url.isBlank()) return
        Glide.with(this)
            .load(url)
            // Si no hay imagen previa (primer inicio), usa el recurso local R.drawable.cine instantáneamente
            .placeholder(imageView.drawable ?: androidx.core.content.ContextCompat.getDrawable(this, R.drawable.cine))
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .dontAnimate()
            .centerCrop()
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
                    val saldo = snapshot.child("castv").value?.toString() ?: "0"
                    val emoji = "🪙 "
                    val etiqueta = "CasTV: "
                    val textoCompleto = "$emoji$etiqueta$saldo"

                    val spannable = SpannableStringBuilder(textoCompleto)

                    spannable.setSpan(
                        ForegroundColorSpan(Color.WHITE),
                        emoji.length,
                        emoji.length + etiqueta.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    spannable.setSpan(
                        ForegroundColorSpan(Color.parseColor("#C5A059")),
                        emoji.length + etiqueta.length,
                        textoCompleto.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    binding.tvSaldoValue.text = spannable

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

    private fun isViewDescendantOf(view: View, parent: ViewGroup): Boolean {
        var current = view.parent
        while (current != null) {
            if (current == parent) return true
            current = current.parent
        }
        return false
    }

    private fun setupMenuHorizontal() {
        val menuItems = listOf(
            "Mundial", "Perfil", "Activar", "Alquila", "Buscar",
            "Pedir", "Paquete",  "TV", "Cerrar"
        )
        val menuIcons = listOf(
            R.drawable.youtube, R.drawable.home, R.drawable.cartelera,
            R.drawable.cine, R.drawable.buscar, R.drawable.pedido,
            R.drawable.activacion, R.drawable.tv, R.drawable.cerrrarp
        )

        val menuList = menuItems.mapIndexed { i, name ->
            MenuPrincipalItem(name, menuIcons[i])
        }

        // Respaldar LayoutParams originales del XML para la vista móvil vertical
        if (originalMenuLayoutParams == null) {
            val lp = binding.menuPrincipal.layoutParams
            originalMenuLayoutParams = when (lp) {
                is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams -> androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(lp)
                is ViewGroup.MarginLayoutParams -> ViewGroup.MarginLayoutParams(lp)
                else -> ViewGroup.LayoutParams(lp)
            }
        }

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // --- CONFIGURACIÓN PARA MODO TV/HORIZONTAL ---
            aplicarDisenoEstructuralTv(true)

            // 🟢 CORRECCIÓN: Se cambia el color oscuro por transparente para evitar la franja de inicio
            binding.menuPrincipal.setBackgroundColor(Color.TRANSPARENT)
            binding.menuPrincipal.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

            // El adaptador se inicializa colapsado (isExpanded = false)
            val adapter = MenuPrincipalVerticalAdapter(menuList, isExpanded = false) { item ->
                ejecutarAccionMenu(item)
            }
            binding.menuPrincipal.adapter = adapter

            // Solicitar enfoque inicial en el catálogo de películas para evitar que el menú se auto-expanda al iniciar
            binding.rvPeliculas.post {
                binding.rvPeliculas.requestFocus()
            }
        } else {
            // --- CONFIGURACIÓN ORIGINAL PARA MÓVIL/VERTICAL ---
            aplicarDisenoEstructuralTv(false)

            binding.menuPrincipal.background = null
            binding.menuPrincipal.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

            val adapter = MenuPrincipalAdapter(menuList) { item ->
                ejecutarAccionMenu(item)
            }
            binding.menuPrincipal.adapter = adapter
        }

        binding.menuPrincipal.isFocusable = true
    }
    private fun aplicarDisenoEstructuralTv(isLandscape: Boolean) {
        val menuView = binding.menuPrincipal
        val density = resources.displayMetrics.density
        val menuParams = menuView.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams ?: return

        val banner = findViewById<View>(R.id.layoutBannerNetflix)
        val rvPeliculas = binding.rvPeliculas

        if (isLandscape) {
            // --- MODO HORIZONTAL (BARRA LATERAL IZQUIERDA VERTICAL) ---
            menuView.setPadding(0, 0, 0, 0)

            menuParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            menuParams.leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            menuParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            menuParams.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID

            // Desvincular restricciones derechas
            menuParams.endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            menuParams.endToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            menuParams.rightToRight = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
            menuParams.rightToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET

            menuParams.width = (90 * density).toInt() // Ancho colapsado inicial
            menuParams.height = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
            menuView.layoutParams = menuParams

            // Forzar fondo totalmente transparente en el contenedor del menú
            menuView.setBackgroundColor(Color.TRANSPARENT)
            menuView.background = null

            // 🟢 SOLUCCIÓN: El contenido (Banner y Películas) ocupará TODA la pantalla.
            // Solo dejamos un margen de 80dp para que la carátula no quede tapada por los iconos fijos.
            if (banner != null) {
                val bannerParams = banner.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                if (bannerParams != null) {
                    bannerParams.topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                    bannerParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                    bannerParams.leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID

                    bannerParams.leftMargin = (90 * density).toInt()
                    bannerParams.marginStart = (90 * density).toInt()
                    banner.layoutParams = bannerParams
                }
            }

            val rvParams = rvPeliculas.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (rvParams != null) {
                rvParams.topToBottom = banner?.id ?: androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                rvParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                rvParams.leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID

                // Dejar espacio fijo equivalente al ancho del menú colapsado
                rvParams.leftMargin = (60 * density).toInt()
                rvParams.marginStart = (60 * density).toInt()
                rvPeliculas.layoutParams = rvParams
            }

            // 🟢 Asegurar que el menú esté al frente estructuralmente en Android TV
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                menuView.elevation = 10 * density
                rvPeliculas.elevation = 0f
                banner?.elevation = 0f
            }

        } else {
            // --- RESTAURAR ESTADO VERTICAL ORIGINAL (MÓVIL) ---
            val paddingVal = (20 * density).toInt()
            menuView.setPadding(paddingVal, 0, paddingVal, 0)

            originalMenuLayoutParams?.let {
                menuView.layoutParams = it
            }

            if (banner != null) {
                val bannerParams = banner.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                if (bannerParams != null) {
                    bannerParams.topToBottom = menuView.id
                    bannerParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                    bannerParams.leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                    bannerParams.leftMargin = 0
                    bannerParams.marginStart = 0
                    banner.layoutParams = bannerParams
                }
            }

            val rvParams = rvPeliculas.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            if (rvParams != null) {
                rvParams.topToBottom = banner?.id ?: androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.UNSET
                rvParams.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                rvParams.leftToLeft = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                rvParams.leftMargin = 0
                rvParams.marginStart = 0
                rvPeliculas.layoutParams = rvParams
            }
        }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        setupMenuHorizontal()
    }

    private fun setMenuExpandedState(expand: Boolean) {
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (!isLandscape || isMenuExpanded == expand) return
        isMenuExpanded = expand

        val menuView = binding.menuPrincipal
        val rvPeliculas = binding.rvPeliculas
        val density = resources.displayMetrics.density
        val targetWidth = if (expand) (160 * density).toInt() else (90 * density).toInt()

        menuView.background = null

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (expand) {
                menuView.elevation = 15 * density
                rvPeliculas.elevation = 5 * density
            } else {
                menuView.elevation = 0f
                rvPeliculas.elevation = 5 * density
            }
        }

        // 🟢 CORRECCIÓN: Le pasamos la vista "menuView" al adaptador para que actualice los textos sin alterar el foco
        menuView.post {
            if (!isFinishing && !isDestroyed) {
                (menuView.adapter as? MenuPrincipalVerticalAdapter)?.setExpanded(expand, menuView)
            }
        }

        val anim = android.animation.ValueAnimator.ofInt(menuView.width, targetWidth)
        anim.addUpdateListener { valueAnimator ->
            val lp = menuView.layoutParams
            lp.width = valueAnimator.animatedValue as Int
            menuView.layoutParams = lp
        }
        anim.duration = 200
        anim.start()
    }
    private fun ejecutarAccionMenu(item: MenuPrincipalItem) {
        when (item.name) {
            "Mundial" -> useryoutube()
            "Activar" -> navegarGratis()
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
    fun navegarGratis() {
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

    private fun setupMovieGrid() {
        val columnas = ViewUtils.calcularColumnas(this)

        // 🟢 GRID_LAYOUT_MANAGER SEGURO:
        // Captura y previene por completo los cierres por desincronización nativa (GapWorker) de Android
        val layoutManager = object : GridLayoutManager(this, columnas) {
            override fun onLayoutChildren(recycler: RecyclerView.Recycler?, state: RecyclerView.State?) {
                try {
                    super.onLayoutChildren(recycler, state)
                } catch (e: IndexOutOfBoundsException) {
                    Log.e("RECYCLER_SAFE", "Inconsistencia de red prevenida en el RecyclerView.")
                }
            }
            override fun isAutoMeasureEnabled(): Boolean = false
        }
        layoutManager.initialPrefetchItemCount = ITEMS_POR_PAGINA

        binding.rvPeliculas.layoutManager = layoutManager
        binding.rvPeliculas.setHasFixedSize(true)
        binding.rvPeliculas.itemAnimator = null
        binding.rvPeliculas.isNestedScrollingEnabled = false

        movieAdapter = MoviesAdapter(
            modeloList,
            onItemClick = { movie -> irAlReproductor(movie) },
            onFocusChange = { movie -> (movie.imageUrl) }
        )
        binding.rvPeliculas.adapter = movieAdapter

        binding.rvPeliculas.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                val totalItemCount = lm.itemCount
                val lastVisibleItemPosition = lm.findLastVisibleItemPosition()

                if (lastVisibleItemPosition + 8 >= totalItemCount) {
                    insertarSiguientePagina()
                }
            }
        })
    }
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

    private fun obtenerNoticiaYActualizaciones() {
        val versionLocal = BuildConfig.VERSION_NAME
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

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(false)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 40)
            background = GradientDrawable().apply {
                setColor(colorFondo)
                cornerRadius = 20f
            }
        }

        val titulo = TextView(this).apply {
            text = "🚀 Nueva Versión $version"
            setTextColor(colorDorado)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val mensaje = TextView(this).apply {
            text = "Hemos mejorado CineParche para ti. Actualiza ahora para disfrutar de la mejor experiencia."
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(0, 20, 0, 30)
            gravity = Gravity.CENTER
        }

        val botones = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        fun fondo(color: Int): GradientDrawable {
            return GradientDrawable().apply {
                setColor(color)
                cornerRadius = 12f
            }
        }

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

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(20, 0, 20, 0)
        }

        btnActualizar.layoutParams = params
        btnLuego.layoutParams = params

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

        botones.addView(btnActualizar)
        botones.addView(btnLuego)

        container.addView(titulo)
        container.addView(mensaje)
        container.addView(botones)

        dialog.setContentView(container)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        btnActualizar.requestFocus()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun descargarAPK(version: String) {
        val colorDorado = Color.parseColor("#C5A059")
        val colorFondo = Color.parseColor("#0A122A")

        val url = "https://github.com/CreativeMB/center/releases/download/apk/CineParcheApp-debug.apk"
        val file = File(getExternalFilesDir(null), "CineParcheApp-debug.apk")
        if (file.exists()) file.delete()

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            progressTintList = ColorStateList.valueOf(colorDorado)
            progressBackgroundTintList = ColorStateList.valueOf(Color.GRAY)
        }

        val textoProgreso = TextView(this).apply {
            text = "Iniciando descarga segura..."
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 30, 0, 0)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 60, 60, 60)
            setBackgroundColor(colorFondo)
            addView(progressBar)
            addView(textoProgreso)
        }

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

    private fun buscarPeliculaDialogo() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.buscador, null)
        val searchEditText = dialogView.findViewById<EditText>(R.id.search_edit_text)
        val searchResultsView = dialogView.findViewById<ListView>(R.id.list_view)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        progressBar.visibility = View.GONE

        val filteredModeloList = mutableListOf<Modelo>()
        val allFirebaseModelos = mutableListOf<Modelo>()

        // 🟢 ADAPTADOR PERSONALIZADO: Crea filas horizontales con [Póster | Nombre] para la TV
        // Casteamos la expresión completa a LinearLayout para que Kotlin reconozca sus métodos internos
        val adapter = object : ArrayAdapter<Modelo>(this, 0, filteredModeloList) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val density = context.resources.displayMetrics.density
                val dpToPx = { dp: Int -> (dp * density).toInt() }

                val rowView = (convertView ?: LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
                    layoutParams = android.widget.AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    isFocusable = false
                    isFocusableInTouchMode = false

                    // Vista de la portada
                    val iv = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(dpToPx(45), dpToPx(68)).apply {
                            rightMargin = dpToPx(12)
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }

                    // Vista del título de la película
                    val tv = TextView(context).apply {
                        setTextColor(Color.WHITE)
                        textSize = 15f
                        gravity = Gravity.CENTER_VERTICAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    addView(iv)
                    addView(tv)
                }) as LinearLayout

                // Asignamos la información de la película en la posición correspondiente
                val item = getItem(position)
                if (item != null) {
                    val ivPoster = rowView.getChildAt(0) as ImageView
                    val tvTitle = rowView.getChildAt(1) as TextView

                    tvTitle.text = item.title

                    if (!item.imageUrl.isNullOrBlank()) {
                        Glide.with(context)
                            .load(item.imageUrl)
                            .placeholder(R.drawable.cine)
                            .into(ivPoster)
                    } else {
                        ivPoster.setImageResource(R.drawable.cine)
                    }
                }

                return rowView
            }
        }

        searchResultsView.adapter = adapter

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.show()

        fun String.normalizar(): String {
            val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
            val temp = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
            return diacritics.replace(temp, "").lowercase()
        }

        // Cargamos los datos de Firebase una única vez en segundo plano
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
                    val firebaseMatches = allFirebaseModelos.filter {
                        it.title.normalizar().contains(queryNormalizada)
                    }.map { it.copy(title = "${it.title}") }

                    val apiResults = try {
                        val response = TMDbApiClient.service.searchMovies(
                            apiKey = "678193d2c735c6f37840cee035f4d69a",
                            language = "es-MX",
                            query = queryRaw
                        ).execute()

                        if (response.isSuccessful) {
                            response.body()?.results?.map {
                                // 🟢 CAMBIO: Extraemos la fecha completa (YYYY-MM-DD) sin recortar
                                val fechaCompleta = it.release_date ?: "2026-01-01"

                                Modelo(
                                    id = it.id.toString(),
                                    title = "${it.title} ($fechaCompleta)", // 📅 Concatena la fecha completa
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

                    val combined = firebaseMatches + apiResults

                    withContext(Dispatchers.Main) {
                        filteredModeloList.clear()
                        filteredModeloList.addAll(combined)

                        // Notificamos los cambios al adaptador para actualizar la lista de forma fluida
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
    // 6. MEJORADO: PEDIDOS INTERACTIVOS (TMDB INTEGRADO)
    // ==========================================
    private fun mostrarDialogoPedido(
        prefilledTitle: String = "",
        prefilledOriginalTitle: String = "",
        prefilledImageUrl: String = "",
        prefilledAnio: String = "",
        prefilledSipnosis: String = "",
        prefilledCalificacion: String = ""
    ) {
        val colorTextoLogo = Color.parseColor("#C5A059") // Dorado
        val colorFondoPrincipal = Color.parseColor("#2A2A2A") // Fondo oscuro
        val density = resources.displayMetrics.density

        val dpToPx = { dp: Int -> (dp * density).toInt() }

        var dialog: AlertDialog? = null

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(20), dpToPx(24), dpToPx(20))
            setBackgroundColor(colorFondoPrincipal)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            addView(container)
        }

        val titulo = TextView(this).apply {
            text = "SOLICITAR PELÍCULA"
            textSize = 20f
            setTextColor(colorTextoLogo)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(15))
        }

        val input = EditText(this).apply {
            hint = "Escribe para buscar (Ej: Moana 2)"
            setHintTextColor(Color.parseColor("#BDBDBD"))
            setTextColor(Color.WHITE)
            textSize = 16f
            background.setColorFilter(colorTextoLogo, PorterDuff.Mode.SRC_ATOP)
            setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }

        val resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }

        val previewCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(15)
            }
            background = GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(Color.parseColor("#1F1F1F"))
                setStroke(2, colorTextoLogo)
            }
            visibility = View.GONE
        }

        val ivPoster = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(90), dpToPx(135)).apply {
                rightMargin = dpToPx(12)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        val infoPeliLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitlePreview = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }

        val tvRatingPreview = TextView(this).apply {
            setTextColor(colorTextoLogo)
            textSize = 13f
            setPadding(0, dpToPx(4), 0, dpToPx(4))
        }

        val tvSipnosisPreview = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            textSize = 12f
            maxLines = 4
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        infoPeliLayout.addView(tvTitlePreview)
        infoPeliLayout.addView(tvRatingPreview)
        infoPeliLayout.addView(tvSipnosisPreview)

        previewCard.addView(ivPoster)
        previewCard.addView(infoPeliLayout)

        container.addView(titulo)
        container.addView(input)
        container.addView(resultsContainer)
        container.addView(previewCard)

        var selectedTitle = prefilledTitle
        var selectedOriginalTitle = prefilledOriginalTitle
        var selectedImageUrl = prefilledImageUrl
        var selectedAnio = prefilledAnio
        var selectedSipnosis = prefilledSipnosis
        var selectedCalificacion = prefilledCalificacion

        if (prefilledTitle.isNotEmpty()) {
            input.setText(prefilledTitle)

            if (prefilledSipnosis.isNotEmpty()) {
                tvTitlePreview.text = prefilledTitle
                tvRatingPreview.text = if (prefilledCalificacion == "N/A") "⭐ N/A   |   Personalizado" else "⭐ $prefilledCalificacion   |   $prefilledAnio"
                tvSipnosisPreview.text = prefilledSipnosis

                if (prefilledImageUrl.isNotEmpty()) {
                    Glide.with(this)
                        .load(prefilledImageUrl)
                        .placeholder(R.drawable.cine)
                        .into(ivPoster)
                } else {
                    ivPoster.setImageResource(R.drawable.cine)
                }
                previewCard.visibility = View.VISIBLE
            }
        }

        input.setOnKeyListener { _, keyCode, event ->
            if (event.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_DOWN) {
                if (resultsContainer.childCount > 0) {
                    resultsContainer.getChildAt(0).requestFocus()
                    return@setOnKeyListener true
                }
            }
            false
        }

        input.addTextChangedListener(object : TextWatcher {
            private var searchJob: Job? = null
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()

                if (query == selectedTitle) {
                    return
                }

                if (selectedTitle.isNotEmpty() && query != selectedTitle) {
                    selectedTitle = ""
                    previewCard.visibility = View.GONE
                }

                if (query.length < 2) {
                    resultsContainer.removeAllViews()
                    return
                }

                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(400)
                    try {
                        val response = withContext(Dispatchers.IO) {
                            TMDbApiClient.service.searchMovies(apiKey, "es-MX", query).execute()
                        }

                        withContext(Dispatchers.Main) {
                            resultsContainer.removeAllViews()

                            if (response.isSuccessful) {
                                val results = response.body()?.results ?: emptyList()
                                for (movie in results) {

                                    // 🟢 DISEÑO MEJORADO: Fila horizontal para [Miniatura Póster | Nombre]
                                    val itemRow = LinearLayout(this@PeliculasActivity).apply {
                                        orientation = LinearLayout.HORIZONTAL
                                        setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                                        layoutParams = LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                        ).apply {
                                            bottomMargin = dpToPx(4)
                                        }
                                        isFocusable = true
                                        isFocusableInTouchMode = true

                                        val normalBg = ColorDrawable(Color.TRANSPARENT)
                                        val focusBg = GradientDrawable().apply {
                                            setColor(Color.parseColor("#3A3A3A"))
                                            cornerRadius = dpToPx(6).toFloat()
                                            setStroke(dpToPx(2), colorTextoLogo)
                                        }

                                        // Controles del textview para aplicar negrita al enfocar
                                        setOnFocusChangeListener { _, hasFocus ->
                                            background = if (hasFocus) focusBg else normalBg
                                            val tvChild = getChildAt(1) as? TextView
                                            tvChild?.setTypeface(null, if (hasFocus) Typeface.BOLD else Typeface.NORMAL)
                                        }

                                        setOnKeyListener { _, keyCode, keyEvent ->
                                            if (keyEvent.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                                                val index = resultsContainer.indexOfChild(this)
                                                if (index == 0) {
                                                    input.requestFocus()
                                                    return@setOnKeyListener true
                                                }
                                            }
                                            false
                                        }

                                        setOnClickListener {
                                            val rawTitle = movie.title ?: ""
                                            selectedAnio = movie.release_date ?: "2026-01-01"

                                            selectedTitle = if (rawTitle.endsWith(")") && rawTitle.contains("(")) {
                                                rawTitle
                                            } else {
                                                "$rawTitle ($selectedAnio)"
                                            }

                                            selectedOriginalTitle = movie.original_title ?: rawTitle
                                            selectedImageUrl = "https://image.tmdb.org/t/p/w500${movie.poster_path}"
                                            selectedSipnosis = movie.overview ?: "Sin sinopsis disponible."
                                            selectedCalificacion = if (movie.vote_average > 0.0) "${movie.vote_average}" else "N/A"

                                            tvTitlePreview.text = selectedTitle
                                            tvRatingPreview.text = "⭐ $selectedCalificacion   |   $selectedAnio"
                                            tvSipnosisPreview.text = selectedSipnosis

                                            Glide.with(this@PeliculasActivity)
                                                .load(selectedImageUrl)
                                                .placeholder(R.drawable.cine)
                                                .into(ivPoster)

                                            previewCard.visibility = View.VISIBLE
                                            resultsContainer.removeAllViews()
                                            input.setText(selectedTitle)
                                            input.clearFocus()

                                            dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                                        }
                                    }

                                    // Imagen de portada dentro de la sugerencia
                                    val ivSugerenciaPoster = ImageView(this@PeliculasActivity).apply {
                                        layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(60)).apply {
                                            rightMargin = dpToPx(12)
                                        }
                                        scaleType = ImageView.ScaleType.CENTER_CROP
                                    }

                                    // Título dentro de la sugerencia
                                    val tvSugerenciaTitulo = TextView(this@PeliculasActivity).apply {
                                        val fechaCompleta = movie.release_date ?: "2026-01-01"
                                        text = "${movie.title} ($fechaCompleta)"
                                        setTextColor(Color.WHITE)
                                        textSize = 14f
                                        gravity = Gravity.CENTER_VERTICAL
                                        layoutParams = LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }

                                    // Carga asíncrona de la miniatura de póster sugerida (w154 para optimizar consumo)
                                    if (!movie.poster_path.isNullOrBlank()) {
                                        Glide.with(this@PeliculasActivity)
                                            .load("https://image.tmdb.org/t/p/w154${movie.poster_path}")
                                            .placeholder(R.drawable.cine)
                                            .into(ivSugerenciaPoster)
                                    } else {
                                        ivSugerenciaPoster.setImageResource(R.drawable.cine)
                                    }

                                    itemRow.addView(ivSugerenciaPoster)
                                    itemRow.addView(tvSugerenciaTitulo)

                                    resultsContainer.addView(itemRow)
                                }
                            }

                            // 🟢 DISEÑO MEJORADO PARA PEDIDO PERSONALIZADO: Conserva la misma alineación de filas
                            val customItemRow = LinearLayout(this@PeliculasActivity).apply {
                                orientation = LinearLayout.HORIZONTAL
                                setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    bottomMargin = dpToPx(4)
                                }
                                isFocusable = true
                                isFocusableInTouchMode = true

                                val normalBg = ColorDrawable(Color.TRANSPARENT)
                                val focusBg = GradientDrawable().apply {
                                    setColor(Color.parseColor("#3A3A3A"))
                                    cornerRadius = dpToPx(6).toFloat()
                                    setStroke(dpToPx(2), colorTextoLogo)
                                }

                                setOnFocusChangeListener { _, hasFocus ->
                                    background = if (hasFocus) focusBg else normalBg
                                }

                                setOnKeyListener { _, keyCode, keyEvent ->
                                    if (keyEvent.action == android.view.KeyEvent.ACTION_DOWN && keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                                        val index = resultsContainer.indexOfChild(this)
                                        if (index == 0) {
                                            input.requestFocus()
                                            return@setOnKeyListener true
                                        }
                                    }
                                    false
                                }

                                setOnClickListener {
                                    selectedTitle = query
                                    selectedOriginalTitle = query
                                    selectedImageUrl = ""
                                    selectedAnio = "Personalizado"
                                    selectedSipnosis = "Película personalizada no encontrada en el catálogo de TMDB."
                                    selectedCalificacion = "N/A"

                                    tvTitlePreview.text = selectedTitle
                                    tvRatingPreview.text = "⭐ N/A   |   Personalizado"
                                    tvSipnosisPreview.text = selectedSipnosis
                                    ivPoster.setImageResource(R.drawable.cine)

                                    previewCard.visibility = View.VISIBLE
                                    resultsContainer.removeAllViews()
                                    input.setText(selectedTitle)
                                    input.clearFocus()

                                    dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
                                }
                            }

                            // Ícono por defecto para pedido personalizado
                            val ivCustomIcon = ImageView(this@PeliculasActivity).apply {
                                layoutParams = LinearLayout.LayoutParams(dpToPx(40), dpToPx(60)).apply {
                                    rightMargin = dpToPx(12)
                                }
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                setImageResource(R.drawable.cine)
                            }

                            val tvCustomRow = TextView(this@PeliculasActivity).apply {
                                text = "➕ Pedir: \"$query\" (Pedido Personalizado)"
                                setTextColor(colorTextoLogo)
                                textSize = 14f
                                setTypeface(null, Typeface.BOLD)
                                gravity = Gravity.CENTER_VERTICAL
                                layoutParams = LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }

                            customItemRow.addView(ivCustomIcon)
                            customItemRow.addView(tvCustomRow)

                            resultsContainer.addView(customItemRow)
                        }
                    } catch (e: Exception) {
                        Log.e("TMDB_Pedido", "Error al buscar: ${e.message}")
                    }
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dialog = AlertDialog.Builder(this)
            .setView(scrollView)
            .setPositiveButton("SIGUIENTE", null)
            .setNegativeButton("CANCELAR", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setTextColor(colorTextoLogo)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)

            setOnClickListener {
                val typedText = input.text.toString().trim()

                if (typedText.isEmpty()) {
                    input.error = "Escribe el nombre de la película"
                    return@setOnClickListener
                }

                if (selectedTitle.isEmpty() || typedText != selectedTitle) {
                    input.error = "Selecciona una opción de la lista de abajo"

                    CineAlert.show(
                        this@PeliculasActivity,
                        "Por favor, selecciona una opción de la lista sugerida de abajo antes de continuar 🎬",
                        CineAlert.Tipo.ERROR,
                        dialog?.window?.decorView as? ViewGroup
                    )
                    return@setOnClickListener
                }

                // 🟢 AQUÍ LLAMAMOS A LA VALIDACIÓN ANTES DE PASAR A COMPROBANTEPAGO
                validarPeliculaRecienteLocal(
                    fechaAValidar = selectedAnio, // Usamos la fecha que ya extrajiste de la API
                    onSuccess = {
                        // Si la película ya tiene más de 40 días, la manda a procesar
                        comprobantepago(
                            selectedTitle,
                            selectedOriginalTitle,
                            selectedImageUrl,
                            selectedAnio,
                            selectedSipnosis,
                            selectedCalificacion
                        )
                        dialog?.dismiss()
                    },
                    onRechazado = { mensajeError ->
                        // Si es muy reciente, la detenemos y mostramos el cuadro rojo
                        CineAlert.show(
                            this@PeliculasActivity,
                            mensajeError,
                            CineAlert.Tipo.ERROR,
                            dialog?.window?.decorView as? ViewGroup
                        )
                    }
                )
            }
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }
    }
    private fun validarPeliculaRecienteLocal(fechaAValidar: String, onSuccess: () -> Unit, onRechazado: (String) -> Unit) {
        // Si el usuario eligió "Pedido Personalizado", no podemos validar fecha, así que lo dejamos pasar
        if (fechaAValidar.isBlank() || fechaAValidar == "Personalizado") {
            onSuccess()
            return
        }

        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val dateEstreno = sdf.parse(fechaAValidar)

            if (dateEstreno != null) {
                val diffMillis = System.currentTimeMillis() - dateEstreno.time
                val diffDias = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(diffMillis)

                // Verificamos si tiene menos de 40 días (o si es un estreno futuro)
                if (diffDias < 40) {
                    val diasFaltantes = if (diffDias < 0) 40 + kotlin.math.abs(diffDias) else 40 - diffDias
                    onRechazado("Película muy reciente Estreno ($fechaAValidar).\nDeben pasar 40 días. Faltan aprox. $diasFaltantes días.")
                } else {
                    onSuccess() // Pasó la prueba de los 40 días
                }
            } else {
                onSuccess() // Si la fecha venía rota por defecto de la API, evitamos bloquear al usuario
            }
        } catch (e: Exception) {
            onSuccess() // Error de formato, lo dejamos pasar por precaución
        }
    }

    private fun comprobantepago(
        titulo: String,
        originalTitle: String,
        imageUrl: String,
        anio: String,
        sipnosis: String,
        calificacion: String
    ) {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val correoKey = email.replace(".", "_").replace("@", "_")

        val colorTextoLogo = Color.parseColor("#C5A059") // Dorado
        val colorFondoPrincipal = Color.parseColor("#2A2A2A") // Fondo oscuro
        val density = resources.displayMetrics.density

        val dpToPx = { dp: Int -> (dp * density).toInt() }

        // 🔄 Mantenemos la consulta a "usuarios" para calcular el saldo de CasTV
        databaseRef.child("usuarios").child(correoKey).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val nombreUsuario = snapshot.child("nombre").value?.toString() ?: "Usuario"
                val saldoActual = (snapshot.child("castv").value as? Number)?.toInt() ?: 0
                val costo = 20
                val saldoFinal = saldoActual - costo

                val container = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpToPx(24), dpToPx(20), dpToPx(24), dpToPx(20))
                    setBackgroundColor(colorFondoPrincipal)
                }

                val scrollView = ScrollView(this).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    addView(container)
                }

                val tituloLabel = TextView(this).apply {
                    text = "CONFIRMAR PEDIDO"
                    textSize = 18f
                    setTextColor(colorTextoLogo)
                    gravity = Gravity.CENTER
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, 0, 0, dpToPx(15))
                }

                val fichaPeliculaLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setColor(Color.parseColor("#1F1F1F"))
                        setStroke(1, Color.parseColor("#444444"))
                    }
                }

                val ivPoster = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dpToPx(70), dpToPx(105)).apply {
                        rightMargin = dpToPx(12)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }

                if (imageUrl.isNotEmpty()) {
                    Glide.with(this@PeliculasActivity)
                        .load(imageUrl)
                        .placeholder(R.drawable.cine)
                        .into(ivPoster)
                } else {
                    ivPoster.setImageResource(R.drawable.cine)
                }

                val infoMetaLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val tvTitleMeta = TextView(this).apply {
                    text = titulo
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                }

                val tvRatingMeta = TextView(this).apply {
                    text = "⭐ $calificacion   |   📅 $anio"
                    setTextColor(colorTextoLogo)
                    textSize = 12f
                    setPadding(0, dpToPx(4), 0, dpToPx(4))
                }

                val tvOverviewMeta = TextView(this).apply {
                    text = sipnosis
                    setTextColor(Color.LTGRAY)
                    textSize = 11f
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }

                infoMetaLayout.addView(tvTitleMeta)
                infoMetaLayout.addView(tvRatingMeta)
                infoMetaLayout.addView(tvOverviewMeta)

                fichaPeliculaLayout.addView(ivPoster)
                fichaPeliculaLayout.addView(infoMetaLayout)

                val infoLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpToPx(15), dpToPx(15), dpToPx(15), dpToPx(15))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dpToPx(15)
                    }
                    background = GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        setStroke(2, Color.parseColor("#444444"))
                    }
                }

                val crearFila = { label: String, valor: String, resaltado: Boolean ->
                    TextView(this).apply {
                        text = "$label $valor"
                        textSize = if (resaltado) 15f else 13f
                        setTextColor(if (resaltado) colorTextoLogo else Color.WHITE)
                        setPadding(0, dpToPx(4), 0, dpToPx(4))
                    }
                }

                infoLayout.addView(crearFila("👤 Solicitante:", nombreUsuario, false))
                infoLayout.addView(crearFila("💰 Saldo Actual:", "$saldoActual CasTV", false))
                infoLayout.addView(crearFila("📉 Costo Pedido:", "$costo CasTV", false))

                val linea = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        2
                    ).apply {
                        setMargins(0, dpToPx(10), 0, dpToPx(10))
                    }
                    setBackgroundColor(Color.parseColor("#444444"))
                }
                infoLayout.addView(linea)
                infoLayout.addView(crearFila("✅ Saldo Final:", "$saldoFinal CasTV", true))

                container.addView(tituloLabel)
                container.addView(fichaPeliculaLayout)
                container.addView(infoLayout)

                val dialog = AlertDialog.Builder(this)
                    .setView(scrollView)
                    .setCancelable(false)
                    .setPositiveButton("CONFIRMAR Y ENVIAR", null)
                    .setNegativeButton("CORREGIR", null)
                    .create()

                dialog.show()

                // Cambiar dentro de comprobantepago:
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).apply {
                    setTextColor(colorTextoLogo)
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setOnClickListener {
                        if (saldoActual >= costo) {
                            // 🟢 Llama al helper para que el usuario seleccione la fecha y hora
                            CastvHelper.mostrarSelectorFechaHora(this@PeliculasActivity) { fechaSeleccionada, horaSeleccionada ->
                                // 🟢 Envía los parámetros de programación de la activación al proceso final
                                ejecutarProcesoFinal(
                                    correoKey = correoKey,
                                    titulo = titulo,
                                    originalTitle = originalTitle,
                                    imageUrl = imageUrl,
                                    anio = anio,
                                    costo = costo,
                                    nombre = nombreUsuario,
                                    email = email,
                                    dialog = dialog,
                                    fechaActivacion = fechaSeleccionada, // Pasamos la fecha elegida
                                    horaActivacion = horaSeleccionada   // Pasamos la hora elegida
                                )
                            }
                        } else {
                            CineAlert.show(
                                this@PeliculasActivity,
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
                        // Al corregir, se devuelve toda la información al buscador
                        mostrarDialogoPedido(
                            titulo,
                            originalTitle,
                            imageUrl,
                            anio,
                            sipnosis,
                            calificacion
                        )
                    }
                }
            }
        }
    }
    private fun ejecutarProcesoFinal(
        correoKey: String,
        titulo: String,
        originalTitle: String,
        imageUrl: String,
        anio: String,
        costo: Int,
        nombre: String,
        email: String,
        dialog: AlertDialog,
        fechaActivacion: String, // 📅 Nuevo parámetro
        horaActivacion: Int      // ⏱ Nuevo parámetro
    ) {
        // 🟢 1. Sincroniza y crea/actualiza la película directamente en la grilla principal "movies"
        verificarYCrearPeliculaDesdePedido(titulo, originalTitle, imageUrl, anio, email, nombre, fechaActivacion, horaActivacion)

        // 🟢 2. Ejecuta el descuento de puntos del saldo de CasTV de forma directa y cierra el diálogo
        descontarPuntos(correoKey, costo, titulo, dialog)
    }

    private fun verificarYCrearPeliculaDesdePedido(
        tituloMovie: String,
        originalTitleMovie: String,
        imageUrlMovie: String,
        anio: String,
        userEmail: String,
        userName: String,
        fechaActivacion: String, // 📅 Nuevo parámetro
        horaActivacion: Int      // ⏱ Nuevo parámetro
    ) {
        val moviesRef = databaseRef.child("movies")

        moviesRef.orderByChild("originalTitle").equalTo(originalTitleMovie)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // ESCENARIO A: La película YA existe -> Actualizamos su fecha de creación (createdAt)
                        val existingId = snapshot.children.firstOrNull()?.key ?: ""
                        if (existingId.isNotEmpty()) {
                            val tiempoActual = System.currentTimeMillis()
                            moviesRef.child(existingId).child("createdAt").setValue(tiempoActual)
                                .addOnSuccessListener {
                                    Log.d("FirebaseTV", "📅 Fecha 'createdAt' actualizada para la película existente: $existingId")
                                }

                            // Añadimos la solicitud del usuario a la lista de espera de la película existente
                            val solicitudesRef = moviesRef.child(existingId).child("solicitudes").push()
                            val idSolicitud = solicitudesRef.key ?: ""
                            val datosSolicitud = mapOf(
                                "id" to idSolicitud,
                                "email" to userEmail,
                                "userId" to userName,
                                "fechaActivacion" to fechaActivacion, // 🟢 Se guarda la fecha
                                "horaActivacion" to horaActivacion,   // 🟢 Se guarda la hora
                                "timestamp" to ServerValue.TIMESTAMP
                            )
                            solicitudesRef.setValue(datosSolicitud)
                        }
                    } else {
                        // ESCENARIO B: La película NO existe -> Procedemos a crearla desde cero
                        val newId = moviesRef.push().key ?: return
                        val nombreFormateado = "$tituloMovie ($anio)".trim()

                        val nuevaPeliculaMap = hashMapOf(
                            "id" to newId,
                            "title" to tituloMovie,
                            "originalTitle" to originalTitleMovie,
                            "nombre" to nombreFormateado,
                            "imageUrl" to imageUrlMovie,
                            "streamUrl" to "https://tuservidor.com/stream/", // Queda sin enlace de reproducción (rota) para que el admin la asigne
                            "castv" to 10,
                            "countdownMinutes" to 0,
                            "createdAt" to System.currentTimeMillis(),
                            "email" to "",
                            "trailerUrl" to "",
                            "userId" to ""
                        )

                        moviesRef.child(newId).setValue(nuevaPeliculaMap)
                            .addOnSuccessListener {
                                Log.d("FirebaseTV", "🟢 Nueva película de pedido registrada: $newId")

                                // Registramos la solicitud inicial dentro de la película creada
                                val solicitudesRef = moviesRef.child(newId).child("solicitudes").push()
                                val idSolicitud = solicitudesRef.key ?: ""
                                val datosSolicitud = mapOf(
                                    "id" to idSolicitud,
                                    "email" to userEmail,
                                    "userId" to userName,
                                    "fechaActivacion" to fechaActivacion, // 🟢 Se guarda la fecha
                                    "horaActivacion" to horaActivacion,   // 🟢 Se guarda la hora
                                    "timestamp" to ServerValue.TIMESTAMP
                                )
                                solicitudesRef.setValue(datosSolicitud)
                            }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseTV", "Error al verificar duplicado de pedido: ${error.message}")
                }
            })
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

                    CineAlert.show(
                        this@PeliculasActivity,
                        "¡Pedido realizado con éxito! 🎬",
                        CineAlert.Tipo.EXITO,
                        dialog.window?.decorView as? ViewGroup
                    ) {
                        dialog.dismiss()
                    }
                }
            } else {
                CineAlert.show(this@PeliculasActivity, "Saldo insuficiente 💰", CineAlert.Tipo.ERROR, dialog.window?.decorView as? ViewGroup)
            }
        }
    }

    private fun activarpaquete() {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        val correoKey = email.replace(".", "_").replace("@", "_")

        lifecycleScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    databaseRef.child("usuarios").child(correoKey).get().await()
                }

                val estado = snapshot.child("estado").value?.toString() ?: ""
                val paqueteGuardado = snapshot.child("paquete").value?.toString() ?: ""
                val pedidoIdGuardado = snapshot.child("pedidoId").value?.toString() ?: ""

                if (estado.equals("pendiente", ignoreCase = true) && paqueteGuardado.isNotEmpty() && pedidoIdGuardado.isNotEmpty()) {
                    val monto = when (paqueteGuardado) {
                        "Bronce" -> "10000"
                        "Plata" -> "22000"
                        "Oro" -> "45000"
                        else -> "0"
                    }
                    mostrarDialogoPagoInformativo(correoKey, paqueteGuardado, monto, pedidoIdGuardado)
                } else {
                    mostrarDialogoSeleccion(correoKey)
                }
            } catch (e: Exception) {
                mostrarDialogoSeleccion(correoKey)
            }
        }
    }

    private fun mostrarDialogoSeleccion(correoKey: String) {
        val colorTextoLogo = Color.parseColor("#C5A059")
        val colorFondoPrincipal = Color.parseColor("#2A2A2A")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 20)
            setBackgroundColor(colorFondoPrincipal)
        }

        val titulo = TextView(this).apply {
            text = "💎 SELECCIONAR PAQUETE"
            textSize = 18f
            setTextColor(colorTextoLogo)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        }

        val descripcion = TextView(this).apply {
            text = "Selecciona el paquete que deseas activar:"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 10)
        }

        val radioGroup = android.widget.RadioGroup(this).apply {
            setPadding(10, 0, 10, 10)
        }

        fun crearRadioButton(texto: String): android.widget.RadioButton {
            return android.widget.RadioButton(this).apply {
                text = texto
                setTextColor(Color.WHITE)
                textSize = 14f
                buttonTintList = ColorStateList.valueOf(colorTextoLogo)
                id = View.generateViewId()
                setPadding(15, 10, 15, 10)
            }
        }

        val rbBronce = crearRadioButton("Bronce: $10.000 (50 Castv)")
        val rbPlata = crearRadioButton("Plata: $22.000 (120 Castv)")
        val rbOro = crearRadioButton("Oro: $45.000 (250 Castv)")

        radioGroup.addView(rbBronce)
        radioGroup.addView(rbPlata)
        radioGroup.addView(rbOro)
        rbBronce.isChecked = true

        layout.addView(titulo)
        layout.addView(descripcion)
        layout.addView(radioGroup)

        val dialog1 = AlertDialog.Builder(this)
            .setView(layout)
            .setPositiveButton("SIGUIENTE", null)
            .setNegativeButton("CANCELAR", null)
            .create()

        dialog1.show()

        dialog1.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setTextColor(colorTextoLogo)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                val paqueteNombre = when (radioGroup.checkedRadioButtonId) {
                    rbBronce.id -> "Bronce"
                    rbPlata.id -> "Plata"
                    rbOro.id -> "Oro"
                    else -> "Desconocido"
                }
                val monto = when (radioGroup.checkedRadioButtonId) {
                    rbBronce.id -> "10000"
                    rbPlata.id -> "22000"
                    rbOro.id -> "45000"
                    else -> "0"
                }

                isEnabled = false

                lifecycleScope.launch {
                    try {
                        val timestamp = System.currentTimeMillis().toString().takeLast(4)
                        val numeroAleatorio = (100..999).random()
                        val pedidoIdGenerico = "ORD$timestamp$numeroAleatorio"

                        val datosUsuarioActualizados = hashMapOf<String, Any>(
                            "pedidoId" to pedidoIdGenerico,
                            "paquete" to paqueteNombre,
                            "estado" to "pendiente"
                        )

                        withContext(Dispatchers.IO) {
                            databaseRef.child("usuarios").child(correoKey)
                                .updateChildren(datosUsuarioActualizados)
                                .await()
                        }

                        dialog1.dismiss()
                        mostrarDialogoPagoInformativo(correoKey, paqueteNombre, monto, pedidoIdGenerico)
                    } catch (e: Exception) {
                        isEnabled = true
                        CineAlert.show(this@PeliculasActivity, "❌ Error al registrar", CineAlert.Tipo.ERROR,
                            dialog1.window?.decorView as? ViewGroup)
                    }
                }
            }
        }

        dialog1.getButton(AlertDialog.BUTTON_NEGATIVE).apply {
            setTextColor(Color.WHITE)
            textSize = 14f
        }
    }

    private fun mostrarDialogoPagoInformativo(correoKey: String, paquete: String, monto: String, pedidoId: String) {
        val colorTextoLogo = Color.parseColor("#C5A059")
        val colorFondoPrincipal = Color.parseColor("#2A2A2A")

        val urlSubidaImagen = "https://onnline.web.app/CineParche/public/pagos/index.html?user=$correoKey&pedido=$pedidoId&paquete=$paquete&monto=$monto"

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 20)
            setBackgroundColor(colorFondoPrincipal)
        }

        val scrollView = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(colorFondoPrincipal)
            addView(mainLayout)
        }

        val titulo = TextView(this).apply {
            text = "💎 PEDIDO REGISTRADO"
            textSize = 18f
            setTextColor(colorTextoLogo)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 15)
        }

        val infoPaquete = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 15)
        }

        val scale = resources.displayMetrics.density
        val qrSizePx = (180 * scale + 0.5f).toInt()

        val qrImageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(qrSizePx, qrSizePx).apply {
                gravity = Gravity.CENTER
                setMargins(0, 10, 0, 10)
            }
            val qrBitmap = generarCodigoQR(urlSubidaImagen)
            if (qrBitmap != null) {
                setImageBitmap(qrBitmap)
            }

            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlSubidaImagen))
                context.startActivity(intent)
            }
        }

        val indicaciones = TextView(this).apply {
            val textoHtml = """
                <b>Bre-Be:</b> Código <b>@TMB833</b><br>
                ✔ Soporte activo 24/7<br><br>
                <i>Toca o escanea el QR para subir tu comprobante</i>
            """.trimIndent()

            text = androidx.core.text.HtmlCompat.fromHtml(textoHtml, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(40, 15, 40, 15)
        }

        val mensajeInformativo = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(40, 25, 40, 25)
            visibility = View.GONE
        }

        val estadoListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val estado = snapshot.child("estado").value?.toString() ?: "pendiente"
                val urlpagos = snapshot.child("urlpagos").value?.toString() ?: ""

                val (estadoVisual, colorHex) = if (urlpagos.isNotEmpty() && estado.equals("pendiente", ignoreCase = true)) {
                    Pair("EN REVISIÓN", "#34D399")
                } else if (estado.equals("pendiente", ignoreCase = true)) {
                    Pair("PENDIENTE DE PAGO", "#F87171")
                } else {
                    Pair(estado.uppercase(), "#C5A059")
                }

                val htmlTexto = """
                    ID Pedido: $pedidoId<br>
                    Paquete: $paquete ($$monto)<br>
                    Estado actual: <b><font color='$colorHex'>$estadoVisual</font></b>
                """.trimIndent()

                infoPaquete.text = androidx.core.text.HtmlCompat.fromHtml(
                    htmlTexto,
                    androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
                )

                val esRevision = urlpagos.isNotEmpty() && estado.equals("pendiente", ignoreCase = true)
                val esActivo = estado.equals("activo", ignoreCase = true)

                when {
                    esActivo -> {
                        qrImageView.visibility = View.GONE
                        indicaciones.visibility = View.GONE
                        mensajeInformativo.visibility = View.VISIBLE

                        val textoActivo = """
                            <font color='#34D399'><b>✅ ¡PAQUETE ACTIVADO!</b></font><br><br>
                            Tu pago fue aprobado con éxito y tu saldo de créditos <b>CasTV</b> ya ha sido abonado a tu cuenta.<br><br>
                            <i>¡Gracias por preferir CineParche!</i>
                        """.trimIndent()
                        mensajeInformativo.text = androidx.core.text.HtmlCompat.fromHtml(textoActivo, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                    }
                    esRevision -> {
                        qrImageView.visibility = View.GONE
                        indicaciones.visibility = View.GONE
                        mensajeInformativo.visibility = View.VISIBLE

                        val textoRevision = """
                            <font color='#34D399'><b>🔍 COMPROBANTE EN REVISIÓN</b></font><br><br>
                            Nuestro equipo está verificando tu comprobante de pago.<br>
                            Una vez finalizada la revisión, tu saldo de <b>CasTV</b> se abonará de inmediato a tu cuenta.<br><br>
                            <i>¡Gracias por tu paciencia!</i>
                        """.trimIndent()
                        mensajeInformativo.text = androidx.core.text.HtmlCompat.fromHtml(textoRevision, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY)
                    }
                    else -> {
                        qrImageView.visibility = View.VISIBLE
                        indicaciones.visibility = View.VISIBLE
                        mensajeInformativo.visibility = View.GONE
                    }
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }

        databaseRef.child("usuarios").child(correoKey).addValueEventListener(estadoListener)

        mainLayout.addView(titulo)
        mainLayout.addView(infoPaquete)
        mainLayout.addView(qrImageView)
        mainLayout.addView(indicaciones)
        mainLayout.addView(mensajeInformativo)

        val dialog2 = AlertDialog.Builder(this)
            .setView(scrollView)
            .setPositiveButton("CERRAR", null)
            .create()

        dialog2.setOnDismissListener {
            databaseRef.child("usuarios").child(correoKey).removeEventListener(estadoListener)
        }
        dialog2.show()

        dialog2.getButton(AlertDialog.BUTTON_POSITIVE).apply {
            setTextColor(colorTextoLogo)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
        }
    }

    private fun generarCodigoQR(texto: String): Bitmap? {
        return try {
            val size = 500
            val bitMatrix: BitMatrix = MultiFormatWriter().encode(texto, BarcodeFormat.QR_CODE, size, size)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    private fun normalizarClave(texto: String): String {
        return texto.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
    }

    private fun verificarAlquileresActivos() {
        val user = auth.currentUser ?: return
        val email = user.email ?: return
        if (email == "invitado@fulltv.com") return

        // 1. Evitar ejecuciones si la actividad se está cerrando antes de la consulta
        if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return

        val correoKey = email.replace(".", "_").replace("@", "_")

        databaseRef.child("usuarios").child(correoKey).child("alquileres")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(rentalsSnapshot: DataSnapshot) {
                    // 2. Control de seguridad post-primer callback
                    if (isFinishing || isDestroyed || !rentalsSnapshot.exists()) return

                    databaseRef.child("movies").addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(moviesSnapshot: DataSnapshot) {
                            // 3. Control de seguridad crítico pre-renderizado del fragmento
                            if (isFinishing || isDestroyed || !moviesSnapshot.exists()) return

                            val freshMoviesCache = mutableMapOf<String, Modelo>()
                            for (child in moviesSnapshot.children) {
                                val movie = child.getValue(Modelo::class.java) ?: continue
                                movie.id = child.key ?: ""
                                freshMoviesCache[normalizarClave(movie.title)] = movie
                            }

                            val alquileresVigentes = mutableListOf<Modelo.AlquilerItem>()
                            val ahora = System.currentTimeMillis()

                            for (child in rentalsSnapshot.children) {
                                val tituloKey = child.key ?: continue
                                val createdAt = (child.child("createdAt").value as? Number)?.toLong() ?: 0L
                                val countdownMinutes = (child.child("countdownMinutes").value as? Number)?.toInt() ?: 0

                                val durationMillis = java.util.concurrent.TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                                val tiempoRestante = durationMillis - (ahora - createdAt)

                                if (tiempoRestante > 0) {
                                    val movie = freshMoviesCache[tituloKey]
                                    if (movie != null) {
                                        alquileresVigentes.add(
                                            Modelo.AlquilerItem(
                                                movie = movie,
                                                createdAt = createdAt,
                                                countdownMinutes = countdownMinutes
                                            )
                                        )
                                    }
                                } else {
                                    child.ref.removeValue()
                                }
                            }

                            // 4. Mostrar el diálogo de forma 100% segura
                            if (alquileresVigentes.isNotEmpty() && !supportFragmentManager.isStateSaved) {
                                val yaExiste = supportFragmentManager.findFragmentByTag("AlquileresDialog")
                                // Solo se muestra si el fragment manager sigue activo y no está duplicado
                                if (yaExiste == null && !isFinishing && !isDestroyed) {
                                    val dialogFragment = AlquileresDialogFragment.newInstance(alquileresVigentes, correoKey)
                                    dialogFragment.show(supportFragmentManager, "AlquileresDialog")
                                }
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("ALQUILERES_DB", "Error al leer películas: ${error.message}")
                        }
                    })
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ALQUILERES_DB", "Error al leer alquileres: ${error.message}")
                }
            })
    }

    private fun escucharCambiosEnPeliculas() {
        if (yaTieneListener) return

        val moviesRef = databaseRef.child("movies")
        peliculasListener = moviesRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val nuevasPeliculasRaw = mutableListOf<Modelo>()

                    for (child in snapshot.children) {
                        val modelo = child.getValue(Modelo::class.java)
                        if (modelo != null) {
                            val movieConId = modelo.copy(id = child.key ?: "")
                            nuevasPeliculasRaw.add(movieConId)
                        }
                    }

                    lifecycleScope.launch(Dispatchers.Default) {
                        val listaNuevaOrdenada = nuevasPeliculasRaw.sortedByDescending { it.createdAt }

                        withContext(Dispatchers.Main) {
                            peliculasCompletas.clear()
                            peliculasCompletas.addAll(listaNuevaOrdenada)

                            // 🟢 ELIMINAMOS "modeloList.clear()" de aquí para mantener sincronizado al adaptador
                            // mientras se ejecuta la validación asíncrona de cargarPrimeraPagina()
                            paginasCargadas = 1
                            siguientePaginaCache.clear()

                            cargarPrimeraPagina()
                        }
                    }
                    yaTieneListener = true
                } else {
                    peliculasCompletas.clear()
                    modeloList.clear()
                    movieAdapter.notifyDataSetChanged()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                yaTieneListener = false
            }
        })
    }

    private fun cargarPrimeraPagina() {
        val total = peliculasCompletas.size
        val fin = minOf(ITEMS_POR_PAGINA, total)
        val subLista = ArrayList(peliculasCompletas.subList(0, fin))

        lifecycleScope.launch {
            val validador = Validaciones()

            val subListaValidada = withContext(Dispatchers.Default) {
                subLista.map { movie ->
                    async(Dispatchers.IO) {
                        val esValida = validador.isUrlValid(movie.streamUrl)
                        movie.copy(isValid = esValida)
                    }
                }.awaitAll()
            }

            val listaVieja = ArrayList(modeloList)
            val diffResult = withContext(Dispatchers.Default) {
                DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = listaVieja.size
                    override fun getNewListSize(): Int = subListaValidada.size

                    override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
                        return listaVieja[oldPos].id == subListaValidada[newPos].id
                    }

                    override fun areContentsTheSame(oldPos: Int, newPos: Int): Boolean {
                        return listaVieja[oldPos] == subListaValidada[newPos]
                    }
                })
            }

            modeloList.clear()
            modeloList.addAll(subListaValidada)

            // 🟢 SÓLO DESPACHAMOS LAS ACTUALIZACIONES DEL DIFFUTIL:
            // Esto elimina por completo el parpadeo o salto visual al iniciar.
            diffResult.dispatchUpdatesTo(movieAdapter)

            val tieneMas = total > ITEMS_POR_PAGINA
            if (tieneMas) {
                precargarSiguientePagina()
            }
        }
    }

    private fun insertarSiguientePagina() {
        if (siguientePaginaCache.isEmpty()) {
            usuarioEsperandoMas = true
            return
        }

        usuarioEsperandoMas = false

        val indiceInsertar = modeloList.size
        modeloList.addAll(siguientePaginaCache)

        // 🟢 SOLUCIÓN: Usamos notifyItemRangeInserted para que la inserción sea animada y limpia.
        // No llamamos a updateMovieList para evitar un refresco completo innecesario.
        movieAdapter.notifyItemRangeInserted(indiceInsertar, siguientePaginaCache.size)

        siguientePaginaCache.clear()
        paginasCargadas++

        val tieneMas = peliculasCompletas.size > (paginasCargadas * ITEMS_POR_PAGINA)
        if (tieneMas) {
            precargarSiguientePagina()
        }
    }
    private fun precargarSiguientePagina() {
        if (cargandoSiguientePagina) return
        val inicio = paginasCargadas * ITEMS_POR_PAGINA
        val total = peliculasCompletas.size
        if (inicio >= total) return

        cargandoSiguientePagina = true

        val fin = minOf(inicio + ITEMS_POR_PAGINA, total)
        val subLista = ArrayList(peliculasCompletas.subList(inicio, fin))

        lifecycleScope.launch {
            val validador = Validaciones()

            val subListaValidada = withContext(Dispatchers.Default) {
                subLista.map { movie ->
                    async(Dispatchers.IO) {
                        val esValida = validador.isUrlValid(movie.streamUrl)
                        movie.copy(isValid = esValida)
                    }
                }.awaitAll()
            }

            siguientePaginaCache.clear()
            siguientePaginaCache.addAll(subListaValidada)
            cargandoSiguientePagina = false

            // Si el usuario ya estaba esperando al final de la pantalla,
            // insertamos el lote inmediatamente ahora que terminó de procesarse.
            if (usuarioEsperandoMas) {
                insertarSiguientePagina()
            }
        }
    }


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

    fun irAlReproductor(modelo: Modelo) {
        if (modelo.streamUrl.isNullOrBlank()) {
            Toast.makeText(this, "El enlace de reproducción no es válido", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ApiPeliculaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_MOVIE_DATA", modelo)
            putExtra("EXTRA_STREAM_URL", modelo.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", modelo.title)
            putExtra("EXTRA_MOVIE_CASTV", modelo.castv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", modelo.imageUrl)
            putExtra("EXTRA_ORIGINAL_TITLE", modelo.originalTitle)
            putExtra("EXTRA_COUNTDOWN", modelo.countdownMinutes)
            putExtra("EXTRA_CREATED_AT", modelo.createdAt / 1000)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun cerrarSesion() {
        // 👇 Restablecemos la sesión de alquileres para el próximo inicio
        restablecerEstadoSesion()

        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInClient.revokeAccess().addOnCompleteListener {
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
            .setNegativeButton("VOLVER (5s)", null)
            .create()

        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorFondo))
        dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.WHITE)

        val btnNegativo = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val btnPositivo = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

        btnNegativo.setTextColor(colorDorado)
        btnPositivo.setTextColor(Color.WHITE)

        val timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val segundosRestantes = millisUntilFinished / 1000
                btnNegativo.text = "VOLVER (${segundosRestantes}s)"
            }

            override fun onFinish() {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }
        }
        timer.start()
        dialog.setOnDismissListener { timer.cancel() }
    }

    override fun onStart() {
        super.onStart()
        if (!yaTieneListener) {
            escucharCambiosEnPeliculas()
        } else {
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

        try {
            unregisterReceiver(onDownloadComplete)
        } catch (e: Exception) {}

        peliculasListener?.let {
            databaseRef.child("movies").removeEventListener(it)
        }

        val email = auth.currentUser?.email
        if (email != null && userStatusListener != null) {
            val correoKey = email.replace(".", "_").replace("@", "_")
            databaseRef.child("usuarios").child(correoKey).removeEventListener(userStatusListener!!)
        }

        publicidadDialog?.let { if (it.isShowing) it.dismiss() }
        progressDialog?.let { if (it.isShowing) it.dismiss() }
        handler.removeCallbacksAndMessages(null)

        Log.d("PeliculasActivity", "Limpieza de onDestroy completada")
    }
}

