package com.creativem.fulltv.peliculas

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.creativem.fulltv.BuildConfig
import com.creativem.fulltv.R
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.api.PeliculasApiActivity
import com.creativem.fulltv.api.TMDbApiService
import com.creativem.fulltv.peliculasvalidas.PeliculasValidasActivity
import com.creativem.fulltv.peliculasvalidas.Validaciones
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.*
import com.creativem.fulltv.tv.TvActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

class PeliculasActivity : AppCompatActivity() {

    companion object {
        var haVerificadoAlquileresEnEstaSesion = false
        val primeraPaginaPrecalculada = mutableListOf<Modelo>()
        var bannerPeliculaInicial: Modelo? = null

        data class TMDBResolvedData(
            val movieId: String,
            val title: String,
            val overview: String,
            val rating: String,
            val infoAdicional: String,
            val backdropUrl: String,
            val posterUrl: String
        )
        var bannerTMDBResolved: TMDBResolvedData? = null

        fun restablecerEstadoSesion() {
            haVerificadoAlquileresEnEstaSesion = false
            primeraPaginaPrecalculada.clear()
            bannerTMDBResolved = null
            bannerPeliculaInicial = null
        }
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val databaseRef: DatabaseReference = FirebaseDatabase.getInstance().reference
    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        apiService = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(TMDbApiService::class.java)

        setContent {
            MaterialTheme {
                PeliculasScreen(
                    activity = this,
                    auth = auth,
                    databaseRef = databaseRef,
                    apiService = apiService,
                    apiKey = apiKey,
                    onReproducir = { movie -> irAlReproductor(movie) },
                    onCerrarSesion = { cerrarSesion() },
                    onSalirApp = { finishAffinity() }
                )
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CastvHelper.ajustarContexto(newBase))
    }

    private fun irAlReproductor(modelo: Modelo) {
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
    }

    private fun cerrarSesion() {
        PeliculasActivity.restablecerEstadoSesion()
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()
        val client = GoogleSignIn.getClient(this, gso)
        client.signOut().addOnCompleteListener {
            val intent = Intent(this, Login::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}

// ==========================================
// 🟢 PANTALLA PRINCIPAL ENSAMBLADA
// ==========================================
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PeliculasScreen(
    activity: AppCompatActivity,
    auth: FirebaseAuth,
    databaseRef: DatabaseReference,
    apiService: TMDbApiService,
    apiKey: String,
    onReproducir: (Modelo) -> Unit,
    onCerrarSesion: () -> Unit,
    onSalirApp: () -> Unit
) {
    val context = LocalContext.current
    var saldoCasTV by remember { mutableStateOf("0") }
    var isMenuExpanded by remember { mutableStateOf(false) }
    var isBannerVisible by remember { mutableStateOf(true) }

    val catalogoCompleto = remember { mutableStateListOf<Modelo>() }
    val peliculasVisibles = remember {
        mutableStateListOf<Modelo>().apply {
            if (PeliculasActivity.primeraPaginaPrecalculada.isNotEmpty()) {
                addAll(PeliculasActivity.primeraPaginaPrecalculada)
            }
        }
    }
    var paginasCargadas by remember { mutableIntStateOf(1) }
    val itemsPorPagina = 24
    var versionCatalogo by remember { mutableIntStateOf(0) }

    val promoList = remember { mutableStateListOf<Modelo>() }
    var currentPromoIndex by remember { mutableIntStateOf(0) }
    var isUserInteractingWithBanner by remember { mutableStateOf(false) }

    val alquileresActivos = remember { mutableStateListOf<Modelo.AlquilerItem>() }
    var mostrarDialogoAlquileres by remember { mutableStateOf(false) }

    var mostrarDialogoSalir by remember { mutableStateOf(false) }
    var mostrarBuscador by remember { mutableStateOf(false) }
    var mostrarPedido by remember { mutableStateOf(false) }
    var mostrarPaquete by remember { mutableStateOf(false) }

    var mostrarDialogoActualizacion by remember { mutableStateOf(false) }
    var versionDisponible by remember { mutableStateOf("") }
    var progresoDescarga by remember { mutableIntStateOf(-1) }
    var mostrarDialogoDescarga by remember { mutableStateOf(false) }

    val menuFocusRequester = remember { FocusRequester() }
    val bannerFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }

    // 🟢 CONTROL INTELIGENTE DE BOTÓN ATRÁS (ANTI-REBOTE TV)
    var ultimaVezReanudado by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                ultimaVezReanudado = System.currentTimeMillis()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    BackHandler {
        // 1. Ignorar pulsaciones accidentales que vengan arrastradas de la pantalla anterior (< 800 ms)
        val tiempoDesdeReanudado = System.currentTimeMillis() - ultimaVezReanudado
        if (tiempoDesdeReanudado < 800L) {
            return@BackHandler
        }

        // 2. Si hay algún diálogo en pantalla, cerrar ese diálogo y NO salir de la aplicación
        when {
            mostrarBuscador -> { mostrarBuscador = false; return@BackHandler }
            mostrarPedido -> { mostrarPedido = false; return@BackHandler }
            mostrarPaquete -> { mostrarPaquete = false; return@BackHandler }
            mostrarDialogoAlquileres -> { mostrarDialogoAlquileres = false; return@BackHandler }
            mostrarDialogoActualizacion -> { mostrarDialogoActualizacion = false; return@BackHandler }
        }

        // 3. Si estás en la pantalla principal sin diálogos abiertos, pedir confirmación de salida
        mostrarDialogoSalir = true
    }

    // 🟢 Escucha de Saldo en tiempo real
    DisposableEffect(Unit) {
        val email = auth.currentUser?.email
        if (email != null) {
            val correoKey = email.replace(".", "_").replace("@", "_")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    saldoCasTV = snapshot.child("TV").value?.toString() ?: "0"
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            val ref = databaseRef.child("usuarios").child(correoKey)
            ref.addValueEventListener(listener)
            onDispose { ref.removeEventListener(listener) }
        } else onDispose { }
    }

    // 🟢 1. Verificación automática de actualizaciones
    LaunchedEffect(Unit) {
        delay(2500)
        val versionLocal = BuildConfig.VERSION_NAME
        val ref = databaseRef.child("noticia").child("us4vaaf0VPezu9vuc4ns")

        ref.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val versionRemota = snapshot.child("versionapk").getValue(String::class.java) ?: ""
                    if (versionRemota.isNotEmpty() && esNuevaVersion(versionRemota, versionLocal)) {
                        versionDisponible = versionRemota
                        mostrarDialogoActualizacion = true
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // 🟢 2. Verificación de alquileres activos al iniciar sesión
    LaunchedEffect(Unit) {
        val user = auth.currentUser
        val email = user?.email
        if (user != null && !email.isNullOrBlank() && email != "invitado@fulltv.com") {
            if (!PeliculasActivity.haVerificadoAlquileresEnEstaSesion) {
                PeliculasActivity.haVerificadoAlquileresEnEstaSesion = true
                delay(2000)

                val correoKey = email.replace(".", "_").replace("@", "_")
                val rentalsRef = databaseRef.child("usuarios").child(correoKey).child("alquileres")

                rentalsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(rentalsSnapshot: DataSnapshot) {
                        if (!rentalsSnapshot.exists()) return

                        databaseRef.child("movies").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(moviesSnapshot: DataSnapshot) {
                                if (!moviesSnapshot.exists()) return

                                val freshMoviesCache = mutableMapOf<String, Modelo>()
                                for (child in moviesSnapshot.children) {
                                    val movie = child.getValue(Modelo::class.java) ?: continue
                                    movie.id = child.key ?: ""
                                    freshMoviesCache[normalizarClave(movie.title)] = movie
                                }

                                val ahora = System.currentTimeMillis()
                                val vigentes = mutableListOf<Modelo.AlquilerItem>()

                                for (child in rentalsSnapshot.children) {
                                    val tituloKey = child.key ?: continue
                                    val createdAt = (child.child("createdAt").value as? Number)?.toLong() ?: 0L
                                    val countdownMinutes = (child.child("countdownMinutes").value as? Number)?.toInt() ?: 0

                                    val tiempoTranscurrido = ahora - createdAt

                                    if (tiempoTranscurrido >= 0) {
                                        val durationMillis = TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                                        val tiempoRestante = durationMillis - tiempoTranscurrido

                                        if (tiempoRestante > 0) {
                                            val movie = freshMoviesCache[tituloKey]
                                            if (movie != null) {
                                                vigentes.add(
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
                                }

                                if (vigentes.isNotEmpty()) {
                                    alquileresActivos.clear()
                                    alquileresActivos.addAll(vigentes)
                                    mostrarDialogoAlquileres = true
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
        }
    }

    // 🟢 3. Catálogo en tiempo real desde Firebase
    LaunchedEffect(Unit) {
        databaseRef.child("movies").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val validadasCache = Validacioneslista.obtenerPeliculasValidas().map { it.id }.toSet()

                    val moviesRaw = snapshot.children.mapNotNull { child ->
                        val m = child.getValue(Modelo::class.java)
                        m?.apply {
                            id = child.key ?: ""
                            val yaEraValida = peliculasVisibles.find { it.id == id }?.isValid == true
                            if (validadasCache.contains(id) || yaEraValida) {
                                isValid = true
                            }
                        }
                    }

                    val ahora = System.currentTimeMillis()

                    fun esPromoActiva(m: Modelo): Boolean {
                        val durationMillis = TimeUnit.MINUTES.toMillis(m.countdownMinutes.toLong())
                        val createdAtMillis = if (m.createdAt in 1..999999999999L) m.createdAt * 1000 else m.createdAt
                        return m.countdownMinutes > 0 && (ahora - createdAtMillis) in 0 until durationMillis
                    }

                    val ordenadas = moviesRaw.sortedWith(
                        compareByDescending<Modelo> { esPromoActiva(it) }
                            .thenByDescending { it.createdAt }
                    )

                    catalogoCompleto.clear()
                    catalogoCompleto.addAll(ordenadas)

                    val cantidadAMostrar = maxOf(itemsPorPagina, paginasCargadas * itemsPorPagina).coerceAtMost(ordenadas.size)
                    val nuevosVisibles = ordenadas.take(cantidadAMostrar)

                    peliculasVisibles.clear()
                    peliculasVisibles.addAll(nuevosVisibles)
                    versionCatalogo++

                    val promos = ordenadas.filter { esPromoActiva(it) }
                    val nuevaPromoDetectada = promos.isNotEmpty() && (promoList.isEmpty() || promos.first().id != promoList.firstOrNull()?.id)

                    promoList.clear()
                    promoList.addAll(promos)

                    if (promos.isNotEmpty()) {
                        isBannerVisible = true
                        if (nuevaPromoDetectada || currentPromoIndex >= promos.size) {
                            currentPromoIndex = 0
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // 🟢 4. Vigilante de expiración sin recargar memoria
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            if (promoList.isNotEmpty()) {
                val ahora = System.currentTimeMillis()
                val hayExpiradas = promoList.any { m ->
                    val durationMillis = TimeUnit.MINUTES.toMillis(m.countdownMinutes.toLong())
                    val createdAtMillis = if (m.createdAt in 1..999999999999L) m.createdAt * 1000 else m.createdAt
                    (ahora - createdAtMillis) >= durationMillis
                }
                if (hayExpiradas) {
                    val promosVigentes = catalogoCompleto.filter { m ->
                        val durationMillis = TimeUnit.MINUTES.toMillis(m.countdownMinutes.toLong())
                        val createdAtMillis = if (m.createdAt in 1..999999999999L) m.createdAt * 1000 else m.createdAt
                        m.countdownMinutes > 0 && (ahora - createdAtMillis) in 0 until durationMillis
                    }
                    promoList.clear()
                    promoList.addAll(promosVigentes)
                    if (promoList.isEmpty()) {
                        isBannerVisible = false
                    } else if (currentPromoIndex >= promoList.size) {
                        currentPromoIndex = 0
                    }
                }
            }
        }
    }

    // Validación asíncrona de URLs en segundo plano
    LaunchedEffect(versionCatalogo) {
        val copiaLista = peliculasVisibles.toList()
        val validador = Validaciones()

        withContext(Dispatchers.IO) {
            val validadasCache = Validacioneslista.obtenerPeliculasValidas().map { it.id }.toSet()

            copiaLista.forEachIndexed { index, m ->
                if (!m.isValid) {
                    val esValida = validadasCache.contains(m.id) || validador.isUrlValid(m.streamUrl)
                    if (esValida) {
                        withContext(Dispatchers.Main) {
                            if (index < peliculasVisibles.size && peliculasVisibles[index].id == m.id) {
                                peliculasVisibles[index] = m.copy(isValid = true)
                            }
                        }
                    }
                }
            }
        }
    }

    // Rotación del banner
    LaunchedEffect(promoList.size, isUserInteractingWithBanner) {
        if (!isUserInteractingWithBanner && promoList.isNotEmpty()) {
            while (isActive) {
                delay(6000)
                currentPromoIndex = (currentPromoIndex + 1) % promoList.size
            }
        }
    }

    fun cargarMasPeliculas() {
        val inicio = paginasCargadas * itemsPorPagina
        if (inicio < catalogoCompleto.size) {
            val fin = minOf(inicio + itemsPorPagina, catalogoCompleto.size)
            val nuevas = catalogoCompleto.subList(inicio, fin)
            peliculasVisibles.addAll(nuevas)
            paginasCargadas++
        }
    }

    val columnas = remember(activity) { ViewUtils.calcularColumnas(activity).coerceAtLeast(3) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color.Black)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // 🟢 MENÚ LATERAL COMPACTO (ARCHIVO 1)
            MenuLateralVertical(
                saldoCasTV = saldoCasTV,
                isBannerVisible = isBannerVisible,
                hasPromos = promoList.isNotEmpty(),
                menuFocusRequester = menuFocusRequester,
                bannerFocusRequester = bannerFocusRequester,
                gridFocusRequester = gridFocusRequester,
                onMenuExpandChange = { isMenuExpanded = it },
                onItemClick = { nombre ->
                    when (nombre) {
                        "TV" -> context.startActivity(Intent(context, TvActivity::class.java))
                        "Alquila" -> context.startActivity(Intent(context, PeliculasApiActivity::class.java))
                        "Activar" -> context.startActivity(Intent(context, PeliculasValidasActivity::class.java))
                        "Pedir" -> mostrarPedido = true
                        "Buscar" -> mostrarBuscador = true
                        "Paquete" -> mostrarPaquete = true
                        "Perfil" -> context.startActivity(Intent(context, Perfil::class.java))
                        "Cerrar" -> onCerrarSesion()
                    }
                }
            )

            // 🟢 CONTENIDO PRINCIPAL (BANNER + GRILLA)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // BANNER HERO (ARCHIVO 2)
                AnimatedVisibility(
                    visible = isBannerVisible && promoList.isNotEmpty(),
                    enter = expandVertically(tween(250)) + fadeIn(tween(250)),
                    exit = shrinkVertically(tween(250)) + fadeOut(tween(250))
                ) {
                    val promoActual = promoList.getOrNull(currentPromoIndex)
                    if (promoActual != null) {
                        BannerNetflixHero(
                            movie = promoActual,
                            promosList = promoList,
                            selectedIndex = currentPromoIndex,
                            apiService = apiService,
                            apiKey = apiKey,
                            bannerFocusRequester = bannerFocusRequester,
                            gridFocusRequester = gridFocusRequester,
                            menuFocusRequester = menuFocusRequester,
                            onBannerFocusState = { isUserInteractingWithBanner = it },
                            onSelectPromo = { idx ->
                                currentPromoIndex = idx
                                isUserInteractingWithBanner = true
                            },
                            onClickPromo = { onReproducir(promoActual) },
                            onPlayMovie = { movie -> onReproducir(movie) }
                        )
                    }
                }

                // GRILLA DE PELÍCULAS CON TARJETAS OPTIMIZADAS (ARCHIVO 2)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnas),
                        contentPadding = PaddingValues(
                            top = if (isBannerVisible) 8.dp else 16.dp,
                            bottom = 80.dp,
                            start = 4.dp,
                            end = 4.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(gridFocusRequester)
                            .onFocusChanged { state ->
                                isBannerVisible = !state.hasFocus && promoList.isNotEmpty()
                            }
                            .focusProperties {
                                left = menuFocusRequester
                                up = if (promoList.isNotEmpty() && isBannerVisible) bannerFocusRequester else FocusRequester.Default
                            }
                    ) {
                        itemsIndexed(peliculasVisibles, key = { _, m -> m.id }) { index, movie ->
                            if (index >= peliculasVisibles.size - 6) {
                                LaunchedEffect(index) { cargarMasPeliculas() }
                            }
                            PeliculaCard(
                                movie = movie,
                                onClick = { onReproducir(movie) }
                            )
                        }
                    }
                }
            }
        }

        // 🟢 DIÁLOGOS RESPONSIVOS (ARCHIVO 3)
        if (mostrarDialogoAlquileres && alquileresActivos.isNotEmpty()) {
            DialogoAlquileresActivos(
                alquileres = alquileresActivos,
                onDismiss = { mostrarDialogoAlquileres = false },
                onSelectMovie = { movie ->
                    mostrarDialogoAlquileres = false
                    onReproducir(movie)
                }
            )
        }

        if (mostrarBuscador) {
            DialogoBuscador(
                catalogoLocal = catalogoCompleto,
                apiService = apiService,
                apiKey = apiKey,
                onDismiss = { mostrarBuscador = false },
                onSelectMovie = { movie ->
                    mostrarBuscador = false
                    onReproducir(movie)
                }
            )
        }

        if (mostrarPedido) {
            DialogoPedido(
                activity = activity,
                apiService = apiService,
                apiKey = apiKey,
                saldoActual = saldoCasTV.toIntOrNull() ?: 0,
                correoKey = auth.currentUser?.email?.replace(".", "_")?.replace("@", "_") ?: "",
                databaseRef = databaseRef,
                userEmail = auth.currentUser?.email ?: "",
                onDismiss = { mostrarPedido = false }
            )
        }

        if (mostrarPaquete) {
            DialogoPaquetes(
                correoKey = auth.currentUser?.email?.replace(".", "_")?.replace("@", "_") ?: "",
                databaseRef = databaseRef,
                onDismiss = { mostrarPaquete = false }
            )
        }

        if (mostrarDialogoSalir) {
            DialogoConfirmacionSalida(
                onConfirmar = {
                    mostrarDialogoSalir = false
                    onSalirApp()
                },
                onCancelar = { mostrarDialogoSalir = false }
            )
        }

        // 🟢 DIÁLOGOS DE ACTUALIZACIÓN DE APK
        if (mostrarDialogoActualizacion) {
            DialogoActualizacion(
                version = versionDisponible,
                onConfirmar = {
                    mostrarDialogoActualizacion = false
                    mostrarDialogoDescarga = true
                    descargarAPK(
                        context = context,
                        version = versionDisponible,
                        onProgreso = { progreso -> progresoDescarga = progreso },
                        onCompletado = { file ->
                            progresoDescarga = 100
                            instalarAPK(context, file)
                        }
                    )
                },
                onCancelar = { mostrarDialogoActualizacion = false }
            )
        }

        if (mostrarDialogoDescarga) {
            DialogoDescarga(
                progreso = progresoDescarga,
                version = versionDisponible
            )
        }
    }
}

// ==========================================
// 🟢 DIÁLOGOS Y LÓGICA DE ACTUALIZACIÓN APK
// ==========================================
@Composable
fun DialogoActualizacion(
    version: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ColorFondoOscuro),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🚀 Nueva Versión $version",
                    color = ColorDorado,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Hemos mejorado CineParche para ti. Actualiza ahora para disfrutar de la mejor experiencia.",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    var isBtnActualizarFocused by remember { mutableStateOf(false) }
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBtnActualizarFocused) ColorDorado else Color(0xFF1A1A1A)
                        ),
                        onClick = onConfirmar,
                        modifier = Modifier
                            .onFocusChanged { isBtnActualizarFocused = it.isFocused }
                            .focusable()
                    ) {
                        Text(
                            text = "ACTUALIZAR",
                            color = if (isBtnActualizarFocused) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    var isBtnLuegoFocused by remember { mutableStateOf(false) }
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBtnLuegoFocused) ColorDorado else Color(0xFF1A1A1A)
                        ),
                        onClick = onCancelar,
                        modifier = Modifier
                            .onFocusChanged { isBtnLuegoFocused = it.isFocused }
                            .focusable()
                    ) {
                        Text(
                            text = "LUEGO",
                            color = if (isBtnLuegoFocused) Color.Black else Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DialogoDescarga(progreso: Int, version: String) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ColorFondoOscuro),
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📥 Actualizando CineParche v$version",
                    color = ColorDorado,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                LinearProgressIndicator(
                    progress = { if (progreso >= 0) progreso / 100f else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = ColorDorado,
                    trackColor = Color.Gray,
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (progreso in 0..99) {
                    Text("Descargando... $progreso%", color = ColorDorado, fontSize = 14.sp)
                } else if (progreso == 100) {
                    Text("Descarga completada ✅", color = Color.Green, fontSize = 14.sp)
                } else {
                    Text("Iniciando descarga segura...", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

// 🟢 Helpers de APK y claves
private fun normalizarClave(texto: String): String {
    return texto.replace(".", "_")
        .replace("$", "_")
        .replace("#", "_")
        .replace("[", "_")
        .replace("]", "_")
}

fun esNuevaVersion(remota: String, local: String): Boolean {
    val r = remota.split(".")
    val l = local.split(".")
    for (i in 0 until maxOf(r.size, l.size)) {
        val rv = r.getOrNull(i)?.toIntOrNull() ?: 0
        val lv = l.getOrNull(i)?.toIntOrNull() ?: 0
        if (rv > lv) return true
        if (rv < lv) return false
    }
    return false
}

fun descargarAPK(
    context: Context,
    version: String,
    onProgreso: (Int) -> Unit,
    onCompletado: (File) -> Unit
) {
    val url = "https://github.com/CreativeMB/center/releases/download/apk/CineParcheApp-debug.apk"
    val file = File(context.getExternalFilesDir(null), "CineParcheApp-debug.apk")
    if (file.exists()) file.delete()

    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle("CineParche v$version")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        .setDestinationUri(Uri.fromFile(file))

    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val downloadId = dm.enqueue(request)

    CoroutineScope(Dispatchers.IO).launch {
        var isDownloading = true
        while (isDownloading) {
            val q = DownloadManager.Query().setFilterById(downloadId)
            val cursor = dm.query(q)
            if (cursor != null && cursor.moveToFirst()) {
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val downloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                if (statusIndex >= 0 && downloadedIndex >= 0 && totalIndex >= 0) {
                    val status = cursor.getInt(statusIndex)
                    val downloaded = cursor.getLong(downloadedIndex)
                    val total = cursor.getLong(totalIndex)

                    if (total > 0) {
                        val progress = ((downloaded * 100) / total).toInt()
                        withContext(Dispatchers.Main) { onProgreso(progress) }
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        isDownloading = false
                        withContext(Dispatchers.Main) {
                            onProgreso(100)
                            delay(800)
                            onCompletado(file)
                        }
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        isDownloading = false
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Error en la descarga", Toast.LENGTH_SHORT).show()
                            onProgreso(-1)
                        }
                    }
                }
            }
            cursor?.close()
            if (isDownloading) delay(500)
        }
    }
}

fun instalarAPK(context: Context, file: File) {
    val authority = "${context.packageName}.provider"
    val uri = FileProvider.getUriForFile(context, authority, file)

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            Toast.makeText(context, "Autoriza la instalación y vuelve a intentarlo", Toast.LENGTH_LONG).show()
            return
        }
    }

    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("Instalador", "Error al abrir: ${e.message}")
    }
}