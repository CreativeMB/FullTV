package com.creativem.fulltv.peliculas

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.creativem.fulltv.BuildConfig
import com.creativem.fulltv.R
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.api.MovieDetailResponse
import com.creativem.fulltv.api.PeliculasApiActivity
import com.creativem.fulltv.api.TMDbApiService
import com.creativem.fulltv.menu.MenuPrincipalItem
import com.creativem.fulltv.peliculasvalidas.PeliculasValidasActivity
import com.creativem.fulltv.peliculasvalidas.Validaciones
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.*
import com.creativem.fulltv.tv.TvActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

val ColorDorado = Color(0xFFC5A059)
val ColorFondoOscuro = Color(0xFF0A122A)
val ColorSuperficieDialogo = Color(0xFF2A2A2A)

data class TmdbResultadoItem(
    val id: String,
    val title: String,
    val originalTitle: String,
    val releaseDate: String,
    val posterPath: String,
    val backdropPath: String,
    val overview: String,
    val voteAverage: Double
)

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

@Composable
fun GlideImageCustom(
    url: String?,
    modifier: Modifier = Modifier,
    scaleType: ImageView.ScaleType = ImageView.ScaleType.CENTER_CROP,
    isBackdrop: Boolean = false
) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                this.scaleType = scaleType
                if (isBackdrop) {
                    setBackgroundColor(android.graphics.Color.BLACK)
                } else {
                    setImageResource(R.drawable.pelifondo)
                }
            }
        },
        update = { imageView ->
            if (isBackdrop) {
                imageView.setBackgroundColor(android.graphics.Color.BLACK)
            }

            if (!url.isNullOrBlank()) {
                val placeholderDrawable = if (isBackdrop) {
                    ColorDrawable(android.graphics.Color.BLACK)
                } else {
                    androidx.core.content.ContextCompat.getDrawable(imageView.context, R.drawable.pelifondo)
                }

                Glide.with(imageView.context)
                    .load(url)
                    .placeholder(placeholderDrawable)
                    .error(placeholderDrawable)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate()
                    .into(imageView)
            } else {
                if (isBackdrop) {
                    imageView.setImageResource(0)
                    imageView.setBackgroundColor(android.graphics.Color.BLACK)
                } else {
                    imageView.setImageResource(R.drawable.pelifondo)
                }
            }
        }
    )
}

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

    // 🟢 ESTADOS DE ALQUILERES ACTIVOS
    val alquileresActivos = remember { mutableStateListOf<Modelo.AlquilerItem>() }
    var mostrarDialogoAlquileres by remember { mutableStateOf(false) }

    var mostrarDialogoSalir by remember { mutableStateOf(false) }
    var mostrarBuscador by remember { mutableStateOf(false) }
    var mostrarPedido by remember { mutableStateOf(false) }
    var mostrarPaquete by remember { mutableStateOf(false) }

    // 👇 ESTADOS PARA LA ACTUALIZACIÓN DE APK
    var mostrarDialogoActualizacion by remember { mutableStateOf(false) }
    var versionDisponible by remember { mutableStateOf("") }
    var progresoDescarga by remember { mutableIntStateOf(-1) }
    var mostrarDialogoDescarga by remember { mutableStateOf(false) }

    val menuFocusRequester = remember { FocusRequester() }
    val bannerFocusRequester = remember { FocusRequester() }
    val gridFocusRequester = remember { FocusRequester() }

    BackHandler { mostrarDialogoSalir = true }

    // Saldo en tiempo real
    DisposableEffect(Unit) {
        val email = auth.currentUser?.email
        if (email != null) {
            val correoKey = email.replace(".", "_").replace("@", "_")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    saldoCasTV = snapshot.child("castv").value?.toString() ?: "0"
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            val ref = databaseRef.child("usuarios").child(correoKey)
            ref.addValueEventListener(listener)
            onDispose { ref.removeEventListener(listener) }
        } else onDispose { }
    }

    // 🟢 1. VERIFICACIÓN AUTOMÁTICA DE ACTUALIZACIONES (NUEVO CÓDIGO RESTAURADO)
    LaunchedEffect(Unit) {
        delay(2500) // Pequeño retraso para no saturar el inicio de la app
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

    // 🟢 2. VERIFICACIÓN AUTOMÁTICA DE ALQUILERES ACTIVOS AL INICIAR
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

    // 🟢 3. ESCUCHA DE FIREBASE EN TIEMPO REAL
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

    // 🟢 4. VIGILANTE DE EXPIRACIÓN EN SEGUNDO PLANO
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
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

    val menuItems = remember(saldoCasTV) {
        listOf(
            MenuPrincipalItem("TV", R.drawable.tv),
            MenuPrincipalItem("Alquila", R.drawable.cine),
            MenuPrincipalItem("Activar", R.drawable.cartelera),
            MenuPrincipalItem("Pedir", R.drawable.pedido),
            MenuPrincipalItem("Buscar", R.drawable.buscar),
            MenuPrincipalItem("Paquete", R.drawable.activacion),
            MenuPrincipalItem("Perfil", R.drawable.home),
            MenuPrincipalItem("$$saldoCasTV CasTV", R.drawable.pagos),
            MenuPrincipalItem("Cerrar", R.drawable.cerrrarp)
        )
    }

    val menuWidth by animateDpAsState(
        targetValue = if (isMenuExpanded) 170.dp else 90.dp,
        animationSpec = tween(200),
        label = "menuWidth"
    )

    val columnas = remember(activity) { ViewUtils.calcularColumnas(activity).coerceAtLeast(3) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(Color.Black)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {

            // MENÚ LATERAL VERTICAL
            Box(
                modifier = Modifier
                    .width(menuWidth)
                    .fillMaxHeight()
                    .zIndex(30f)
                    .background(Color.Black.copy(alpha = 0.95f))
                    .onFocusChanged { isMenuExpanded = it.hasFocus }
                    .focusRequester(menuFocusRequester)
                    .focusProperties {
                        right = if (isBannerVisible && promoList.isNotEmpty()) bannerFocusRequester else gridFocusRequester
                    }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(menuItems) { item ->
                        MenuComposeItemRow(
                            item = item,
                            isExpanded = isMenuExpanded,
                            onClick = {
                                when (item.name) {
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
                    }
                }
            }

            // CONTENIDO PRINCIPAL
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // BANNER HERO
                AnimatedVisibility(
                    visible = isBannerVisible && promoList.isNotEmpty(),
                    enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                    exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
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

                // GRILLA DE PELÍCULAS
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columnas),
                        contentPadding = PaddingValues(
                            top = if (isBannerVisible) 8.dp else 24.dp,
                            bottom = 90.dp,
                            start = 6.dp,
                            end = 6.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        // 🟢 DIÁLOGOS DE ACTUALIZACIÓN
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

        // 🟢 DIÁLOGO DE ALQUILERES ACTIVOS
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

        if (mostrarDialogoSalir) {
            DialogoConfirmacionSalida(
                onConfirmar = {
                    mostrarDialogoSalir = false
                    onSalirApp()
                },
                onCancelar = { mostrarDialogoSalir = false }
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
    }
}

// ==========================================
// 🟢 DIÁLOGO DE ALQUILERES ACTIVOS
// ==========================================
@Composable
fun DialogoAlquileresActivos(
    alquileres: List<Modelo.AlquilerItem>,
    onDismiss: () -> Unit,
    onSelectMovie: (Modelo) -> Unit
) {
    Dialog(onDismissRequest = onDismiss,properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
            modifier = Modifier
                .widthIn(max = 850.dp)
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🍿 TUS ALQUILERES ACTIVOS",
                    color = ColorDorado,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Tienes películas en alquiler listas para reproducir:",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(alquileres) { item ->
                        val durationMillis = remember(item.movie.id, item.countdownMinutes) {
                            TimeUnit.MINUTES.toMillis(item.countdownMinutes.toLong())
                        }
                        var millisRestantes by remember(item.movie.id, item.createdAt, item.countdownMinutes) {
                            val transcurrido = System.currentTimeMillis() - item.createdAt
                            mutableLongStateOf(maxOf(0L, durationMillis - transcurrido))
                        }

                        LaunchedEffect(item.movie.id) {
                            while (millisRestantes > 0) {
                                delay(1000)
                                val transcurrido = System.currentTimeMillis() - item.createdAt
                                millisRestantes = maxOf(0L, durationMillis - transcurrido)
                            }
                        }

                        val h = TimeUnit.MILLISECONDS.toHours(millisRestantes)
                        val m = TimeUnit.MILLISECONDS.toMinutes(millisRestantes) % 60
                        val s = TimeUnit.MILLISECONDS.toSeconds(millisRestantes) % 60
                        val tiempoFormato = String.format("▶ %02d:%02d:%02d", h, m, s)

                        var isCardFocused by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .width(135.dp)
                                .scale(if (isCardFocused) 1.06f else 1.0f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCardFocused) Color(0xFF2C2C2C) else Color(0xFF141414))
                                .border(
                                    width = if (isCardFocused) 2.5.dp else 1.dp,
                                    color = if (isCardFocused) ColorDorado else Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { isCardFocused = it.isFocused }
                                .focusable()
                                .clickable { onSelectMovie(item.movie) }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                GlideImageCustom(
                                    url = item.movie.imageUrl,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color(0xE6107C41))
                                        .padding(vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tiempoFormato,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = item.movie.title,
                                color = if (isCardFocused) ColorDorado else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isCardFocused) ColorDorado else Color(0xFF333333))
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "VER AHORA",
                                    color = if (isCardFocused) Color.Black else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = ColorDorado),
                    onClick = onDismiss
                ) {
                    Text(
                        text = "IR AL CATÁLOGO",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MenuComposeItemRow(
    item: MenuPrincipalItem,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val esCasTV = remember(item.name) { item.name.contains("CasTV") }
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused && !esCasTV) 1.1f else 1.0f,
        animationSpec = tween(150),
        label = "scaleRow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .zIndex(if (isFocused) 5f else 0f)
            .focusProperties { canFocus = !esCasTV }
            .onFocusChanged { if (!esCasTV) isFocused = it.isFocused }
            .then(
                if (!esCasTV) {
                    Modifier
                        .focusable()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        )
                } else Modifier
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .fillMaxHeight()
                .scale(scale)
                .then(
                    if (isFocused && !esCasTV) {
                        Modifier
                            .background(
                                brush = Brush.horizontalGradient(listOf(Color(0xFF2A2A2A), Color(0xFF1A1A1A))),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(1.5.dp, ColorDorado, RoundedCornerShape(8.dp))
                    } else Modifier
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = item.iconResId),
                contentDescription = item.name,
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(100))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.name,
                        color = when {
                            esCasTV -> ColorDorado
                            isFocused -> ColorDorado
                            else -> Color.White
                        },
                        fontSize = 14.sp,
                        fontWeight = if (isFocused || esCasTV) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ==========================================
// 🟢 BANNER HERO
// ==========================================
@Composable
fun BannerNetflixHero(
    movie: Modelo,
    promosList: List<Modelo>,
    selectedIndex: Int,
    apiService: TMDbApiService,
    apiKey: String,
    bannerFocusRequester: FocusRequester,
    gridFocusRequester: FocusRequester,
    menuFocusRequester: FocusRequester,
    onBannerFocusState: (Boolean) -> Unit,
    onSelectPromo: (Int) -> Unit,
    onClickPromo: () -> Unit,
    onPlayMovie: (Modelo) -> Unit
) {
    var tmdbTitle by remember(movie.id) { mutableStateOf(movie.title) }
    var tmdbOverview by remember(movie.id) { mutableStateOf(movie.overview.ifBlank { "Estreno exclusivo" }) }
    var tmdbRating by remember(movie.id) { mutableStateOf("⭐ 8.5") }
    var tmdbInfoAdicional by remember(movie.id) { mutableStateOf(movie.genres.ifBlank { "Acción • Aventura • Cine" }) }
    var backdropUrl by remember(movie.id) { mutableStateOf(movie.imageUrl) }
    var posterUrl by remember(movie.id) { mutableStateOf(movie.imageUrl) }

    var isPlayButtonFocused by remember { mutableStateOf(false) }

    val durationMillis = remember(movie.id, movie.countdownMinutes) {
        TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
    }
    val createdAtMillis = remember(movie.id, movie.createdAt) {
        if (movie.createdAt in 1..999999999999L) movie.createdAt * 1000 else movie.createdAt
    }
    var millisRestantes by remember(movie.id, movie.countdownMinutes, movie.createdAt) {
        val transcurrido = System.currentTimeMillis() - createdAtMillis
        mutableLongStateOf(maxOf(0L, durationMillis - transcurrido))
    }

    LaunchedEffect(movie.id, movie.countdownMinutes, movie.createdAt) {
        while (millisRestantes > 0) {
            delay(1000)
            val transcurrido = System.currentTimeMillis() - createdAtMillis
            millisRestantes = maxOf(0L, durationMillis - transcurrido)
        }
    }

    LaunchedEffect(movie.id, movie.title, movie.originalTitle) {
        val resolved = PeliculasActivity.bannerTMDBResolved
        if (resolved != null && resolved.movieId == movie.id) {
            tmdbTitle = resolved.title
            tmdbOverview = resolved.overview
            tmdbRating = resolved.rating
            tmdbInfoAdicional = resolved.infoAdicional
            backdropUrl = resolved.backdropUrl
            posterUrl = resolved.posterUrl
            return@LaunchedEffect
        }

        val query = movie.originalTitle.ifBlank { movie.title }
        try {
            val resp = withContext(Dispatchers.IO) {
                apiService.searchMovie(apiKey, "es-MX", query).execute()
            }
            if (resp.isSuccessful) {
                val r = resp.body()?.results?.firstOrNull()
                if (r != null) {
                    val fechaCompleta = r.release_date ?: ""
                    tmdbTitle = if (fechaCompleta.isNotEmpty()) "${r.title} ($fechaCompleta)" else r.title
                    tmdbOverview = r.overview ?: movie.overview
                    val anio = r.release_date?.take(4) ?: "2026"
                    val cal = if (r.vote_average > 0) String.format(Locale.US, "%.1f", r.vote_average) else "8.5"
                    tmdbRating = "⭐ $cal   |   $anio"

                    backdropUrl = "https://image.tmdb.org/t/p/w1280${r.backdrop_path ?: r.poster_path}"
                    posterUrl = "https://image.tmdb.org/t/p/w500${r.poster_path ?: r.backdrop_path}"

                    val detResp = withContext(Dispatchers.IO) {
                        apiService.getMovieDetails(r.id, apiKey, "es-MX").execute()
                    }
                    if (detResp.isSuccessful) {
                        val detalles = detResp.body()
                        val generos = detalles?.genres?.joinToString(" • ") { it.name } ?: "Acción"
                        val duracion = detalles?.runtime ?: 0
                        tmdbInfoAdicional = "🎭 $generos  ⏱️ ${duracion} Min"
                    }
                }
            }
        } catch (_: Exception) {}
    }

    val horas = TimeUnit.MILLISECONDS.toHours(millisRestantes)
    val minutos = TimeUnit.MILLISECONDS.toMinutes(millisRestantes) % 60
    val segundos = TimeUnit.MILLISECONDS.toSeconds(millisRestantes) % 60
    val contadorFormato = String.format("▶ %02d:%02d:%02d", horas, minutos, segundos)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        Crossfade(
            targetState = backdropUrl,
            animationSpec = tween(400),
            label = "backdropCrossfade"
        ) { url ->
            GlideImageCustom(
                url = url,
                modifier = Modifier.fillMaxSize(),
                isBackdrop = true
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.96f),
                            Color.Black.copy(alpha = 0.75f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Crossfade(
                targetState = posterUrl,
                animationSpec = tween(400),
                label = "posterCrossfade"
            ) { pUrl ->
                Box(
                    modifier = Modifier
                        .height(210.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.5.dp, ColorDorado, RoundedCornerShape(8.dp))
                ) {
                    GlideImageCustom(url = pUrl, modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = tmdbTitle,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = tmdbRating, color = ColorDorado, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = contadorFormato, color = ColorDorado, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = tmdbInfoAdicional,
                    color = Color(0xFFD4AF37),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tmdbOverview,
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    maxLines = 2,
                    lineHeight = 16.sp,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .scale(if (isPlayButtonFocused) 1.08f else 1.0f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPlayButtonFocused) Color.White else ColorDorado)
                        .border(
                            width = if (isPlayButtonFocused) 2.dp else 0.dp,
                            color = ColorDorado,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .onFocusChanged {
                            isPlayButtonFocused = it.isFocused
                            onBannerFocusState(it.hasFocus)
                        }
                        .focusProperties {
                            down = gridFocusRequester
                            left = menuFocusRequester
                        }
                        .focusable()
                        .clickable { onClickPromo() }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "▶ REPRODUCIR",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 12.dp)
                .focusRequester(bannerFocusRequester)
                .onFocusChanged { onBannerFocusState(it.hasFocus) }
                .focusProperties {
                    down = gridFocusRequester
                    left = menuFocusRequester
                },
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(promosList) { idx, promoMovie ->
                var isItemFocused by remember { mutableStateOf(false) }
                val isSelected = idx == selectedIndex

                Box(
                    modifier = Modifier
                        .size(width = 50.dp, height = 72.dp)
                        .scale(if (isItemFocused) 1.15f else 1.0f)
                        .zIndex(if (isItemFocused) 10f else 1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E1E1E))
                        .border(
                            width = if (isItemFocused) 2.5.dp else if (isSelected) 1.5.dp else 0.5.dp,
                            color = if (isItemFocused) ColorDorado else if (isSelected) Color.White else Color.Gray.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .onFocusChanged {
                            isItemFocused = it.isFocused
                            if (it.isFocused) {
                                onSelectPromo(idx)
                            }
                        }
                        .focusable()
                        .clickable {
                            onPlayMovie(promoMovie)
                        }
                ) {
                    GlideImageCustom(
                        url = promoMovie.imageUrl,
                        modifier = Modifier.fillMaxSize()
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LIBRE",
                            color = ColorDorado,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 🟢 TARJETA DE PELÍCULA
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeliculaCard(
    movie: Modelo,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    var itemHeightPx by remember { mutableFloatStateOf(0f) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = tween(150),
        label = "scaleCard"
    )

    val durationMillis = remember(movie.id, movie.countdownMinutes) {
        TimeUnit.MINUTES.toMillis(movie.countdownMinutes.toLong())
    }
    val createdAtMillis = remember(movie.id, movie.createdAt) {
        if (movie.createdAt in 1..999999999999L) movie.createdAt * 1000 else movie.createdAt
    }
    var millisRestantes by remember(movie.id, movie.countdownMinutes, movie.createdAt) {
        val transcurrido = System.currentTimeMillis() - createdAtMillis
        mutableLongStateOf(maxOf(0L, durationMillis - transcurrido))
    }

    LaunchedEffect(movie.id, movie.countdownMinutes, movie.createdAt) {
        while (millisRestantes > 0) {
            delay(1000)
            val transcurrido = System.currentTimeMillis() - createdAtMillis
            millisRestantes = maxOf(0L, durationMillis - transcurrido)
        }
    }

    val esPromoActiva = movie.countdownMinutes > 0 && millisRestantes > 0

    val esValida = remember(movie.id, movie.isValid) {
        movie.isValid || Validacioneslista.obtenerPeliculasValidas().any { it.id == movie.id }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                itemHeightPx = coordinates.size.height.toFloat()
            }
            .bringIntoViewRequester(bringIntoViewRequester)
            .scale(scale)
            .zIndex(if (isFocused) 20f else 1f)
            .padding(4.dp)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused && itemHeightPx > 0f) {
                    coroutineScope.launch {
                        val halfScreen = screenHeightPx / 2f
                        val halfItem = itemHeightPx / 2f
                        val topOffset = -(halfScreen - halfItem)
                        val bottomOffset = itemHeightPx + (halfScreen - halfItem)

                        bringIntoViewRequester.bringIntoView(
                            Rect(
                                left = 0f,
                                top = topOffset,
                                right = 0f,
                                bottom = bottomOffset
                            )
                        )
                    }
                }
            }
            .focusable()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 3.dp else 1.dp,
                    color = if (isFocused) ColorDorado else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            GlideImageCustom(url = movie.imageUrl, modifier = Modifier.fillMaxSize())

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )

            when {
                esPromoActiva -> {
                    val h = TimeUnit.MILLISECONDS.toHours(millisRestantes)
                    val m = TimeUnit.MILLISECONDS.toMinutes(millisRestantes) % 60
                    val s = TimeUnit.MILLISECONDS.toSeconds(millisRestantes) % 60
                    val tiempoStr = String.format("▶ %02d:%02d:%02d", h, m, s)

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(
                                color = Color(0xE6107C41),
                                shape = RoundedCornerShape(topStart = 8.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tiempoStr,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                esValida -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(
                                color = Color(0xEE2E7D32),
                                shape = RoundedCornerShape(topStart = 8.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "ACTIVAR",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(
                                color = Color(0xEEB71C1C),
                                shape = RoundedCornerShape(topStart = 8.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "ALQUILA",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = movie.title,
            color = if (isFocused) ColorDorado else Color.White,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
                .then(
                    if (isFocused) Modifier.basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 600)
                    else Modifier
                )
        )
    }
}

// ==========================================
// 🟢 FUNCIONES AUXILIARES GLOBALES
// ==========================================
private fun normalizarClave(texto: String): String {
    return texto.replace(".", "_")
        .replace("$", "_")
        .replace("#", "_")
        .replace("[", "_")
        .replace("]", "_")
}

private fun String.normalizarTexto(): String {
    val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    val temp = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
    return diacritics.replace(temp, "").lowercase()
}

private fun generarBitmapQR(texto: String): Bitmap? {
    return try {
        val size = 500
        val bitMatrix = MultiFormatWriter().encode(texto, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

// ==========================================
// 1. DIÁLOGO BUSCADOR (OPTIMIZADO PARA TV)
// ==========================================
@Composable
fun DialogoBuscador(
    catalogoLocal: List<Modelo>,
    apiService: TMDbApiService,
    apiKey: String,
    onDismiss: () -> Unit,
    onSelectMovie: (Modelo) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val resultados = remember { mutableStateListOf<Modelo>() }
    var estaBuscando by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        val queryRaw = query.trim()
        val queryNorm = queryRaw.normalizarTexto()

        if (queryRaw.length >= 2) {
            estaBuscando = true
            delay(350)

            val matchesLocales = catalogoLocal.filter {
                it.title.normalizarTexto().contains(queryNorm)
            }

            val apiMatches = try {
                val resp = withContext(Dispatchers.IO) {
                    apiService.searchMovie(apiKey, "es-MX", queryRaw).execute()
                }
                if (resp.isSuccessful) {
                    resp.body()?.results?.map {
                        val fechaCompleta = it.release_date ?: "2026-01-01"
                        Modelo(
                            id = it.id.toString(),
                            title = "${it.title} ($fechaCompleta)",
                            originalTitle = it.original_title ?: it.title ?: "",
                            imageUrl = "https://image.tmdb.org/t/p/w500${it.poster_path}",
                            streamUrl = "https://tuservidor.com/stream/${it.id}",
                            castv = 10,
                            countdownMinutes = 0,
                            createdAt = 0L
                        )
                    } ?: emptyList()
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            resultados.clear()
            resultados.addAll(matchesLocales + apiMatches)
            estaBuscando = false
        } else {
            resultados.clear()
            estaBuscando = false
        }
    }

    Dialog(onDismissRequest = onDismiss,properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .widthIn(max = 800.dp)
                .wrapContentHeight()
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "🔍 BUSCAR EN CATÁLOGO Y ESTRENOS",
                    color = ColorDorado,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Escribe el nombre de la película...", color = Color.Gray, fontSize = 16.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ColorDorado,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = ColorDorado
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (estaBuscando) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = ColorDorado,
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(resultados) { item ->
                        var isItemFocused by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(if (isItemFocused) 1.02f else 1.0f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isItemFocused) Color(0xFF2E2E2E) else Color(0xFF141414))
                                .border(
                                    width = if (isItemFocused) 2.dp else 0.5.dp,
                                    color = if (isItemFocused) ColorDorado else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .focusable()
                                .clickable { onSelectMovie(item) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlideImageCustom(
                                url = item.imageUrl,
                                modifier = Modifier
                                    .size(width = 50.dp, height = 75.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = item.title,
                                color = if (isItemFocused) ColorDorado else Color.White,
                                fontSize = 16.sp,
                                fontWeight = if (isItemFocused) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("CERRAR", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}

// ==========================================
// 2. DIÁLOGO PEDIDO
// ==========================================
@Composable
fun DialogoPedido(
    activity: AppCompatActivity,
    apiService: TMDbApiService,
    apiKey: String,
    saldoActual: Int,
    correoKey: String,
    databaseRef: DatabaseReference,
    userEmail: String,
    onDismiss: () -> Unit
) {
    var pasoConfirmacion by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<TmdbResultadoItem?>(null) }
    var mensajeError by remember { mutableStateOf("") }
    val sugerencias = remember { mutableStateListOf<TmdbResultadoItem>() }
    var nombreUsuario by remember { mutableStateOf("Usuario") }

    val costoPedido = 20

    LaunchedEffect(correoKey) {
        if (correoKey.isNotBlank()) {
            databaseRef.child("usuarios").child(correoKey).child("nombre").get().addOnSuccessListener {
                nombreUsuario = it.value?.toString() ?: "Usuario"
            }
        }
    }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length >= 2 && selectedItem == null) {
            delay(350)
            try {
                val resp = withContext(Dispatchers.IO) {
                    apiService.searchMovie(apiKey, "es-MX", q).execute()
                }
                if (resp.isSuccessful) {
                    val items = resp.body()?.results?.map {
                        TmdbResultadoItem(
                            id = it.id.toString(),
                            title = it.title ?: "",
                            originalTitle = it.original_title ?: it.title ?: "",
                            releaseDate = it.release_date ?: "2026-01-01",
                            posterPath = if (!it.poster_path.isNullOrBlank()) "https://image.tmdb.org/t/p/w500${it.poster_path}" else "",
                            backdropPath = if (!it.backdrop_path.isNullOrBlank()) "https://image.tmdb.org/t/p/w1280${it.backdrop_path}" else "",
                            overview = it.overview ?: "Sin sinopsis disponible.",
                            voteAverage = it.vote_average
                        )
                    } ?: emptyList()

                    sugerencias.clear()
                    sugerencias.addAll(items)
                }
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismiss,  properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .widthIn(max = 850.dp)
                .wrapContentHeight()
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                if (!pasoConfirmacion) {
                    Text(
                        text = "🎬 SOLICITAR PELÍCULA",
                        color = ColorDorado,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            selectedItem = null
                            mensajeError = ""
                        },
                        placeholder = {
                            Text(
                                "Escribe para buscar (Ej: Moana 2)",
                                color = Color.Gray,
                                fontSize = 15.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ColorDorado,
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = ColorDorado
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (mensajeError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = mensajeError,
                            color = Color(0xFFFF5252),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (selectedItem == null) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(sugerencias) { item ->
                                var isFocusRow by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(if (isFocusRow) 1.02f else 1.0f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isFocusRow) Color(0xFF2C2C2C) else Color(
                                                0xFF121212
                                            )
                                        )
                                        .border(
                                            width = if (isFocusRow) 2.dp else 0.dp,
                                            color = ColorDorado,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .onFocusChanged { isFocusRow = it.isFocused }
                                        .focusable()
                                        .clickable {
                                            selectedItem = item
                                            query = "${item.title} (${item.releaseDate})"
                                            sugerencias.clear()
                                        }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    GlideImageCustom(
                                        url = item.posterPath,
                                        modifier = Modifier
                                            .size(width = 45.dp, height = 65.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = "${item.title} (${item.releaseDate})",
                                            color = if (isFocusRow) ColorDorado else Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "⭐ ${
                                                if (item.voteAverage > 0) String.format(
                                                    Locale.US,
                                                    "%.1f",
                                                    item.voteAverage
                                                ) else "N/A"
                                            }",
                                            color = ColorDorado,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            if (query.trim().length >= 2) {
                                item {
                                    var isCustomFocus by remember { mutableStateOf(false) }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .scale(if (isCustomFocus) 1.02f else 1.0f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isCustomFocus) Color(0xFF2C2C2C) else Color(
                                                    0xFF161616
                                                )
                                            )
                                            .border(
                                                width = if (isCustomFocus) 2.dp else 0.dp,
                                                color = ColorDorado,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .onFocusChanged { isCustomFocus = it.isFocused }
                                            .focusable()
                                            .clickable {
                                                selectedItem = TmdbResultadoItem(
                                                    id = System.currentTimeMillis().toString(),
                                                    title = query.trim(),
                                                    originalTitle = query.trim(),
                                                    releaseDate = "Personalizado",
                                                    posterPath = "",
                                                    backdropPath = "",
                                                    overview = "Película solicitada de forma personalizada.",
                                                    voteAverage = 0.0
                                                )
                                                sugerencias.clear()
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "➕ Pedir: \"${query.trim()}\" (Pedido Personalizado)",
                                            color = ColorDorado,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF121212))
                                .border(1.5.dp, ColorDorado, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlideImageCustom(
                                url = selectedItem!!.posterPath,
                                modifier = Modifier
                                    .size(width = 110.dp, height = 165.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${selectedItem!!.title} (${selectedItem!!.releaseDate})",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "⭐ ${
                                        if (selectedItem!!.voteAverage > 0) String.format(
                                            Locale.US,
                                            "%.1f",
                                            selectedItem!!.voteAverage
                                        ) else "N/A"
                                    }  |  ${selectedItem!!.releaseDate}",
                                    color = ColorDorado,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = selectedItem!!.overview,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("CANCELAR", color = Color.White, fontSize = 15.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDorado),
                            onClick = {
                                if (selectedItem == null) {
                                    mensajeError =
                                        "Por favor selecciona una película de la lista sugerida."
                                    return@Button
                                }
                                if (saldoActual < costoPedido) {
                                    mensajeError =
                                        "Saldo insuficiente en CasTV. Tienes $saldoActual y se requieren $costoPedido."
                                    return@Button
                                }

                                if (selectedItem!!.releaseDate.isNotBlank() && selectedItem!!.releaseDate != "Personalizado") {
                                    try {
                                        val sdf =
                                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        val dateEstreno = sdf.parse(selectedItem!!.releaseDate)
                                        if (dateEstreno != null) {
                                            val diffMillis =
                                                System.currentTimeMillis() - dateEstreno.time
                                            val diffDias = TimeUnit.MILLISECONDS.toDays(diffMillis)
                                            if (diffDias < 40) {
                                                val faltan =
                                                    if (diffDias < 0) 40 + abs(diffDias) else 40 - diffDias
                                                mensajeError =
                                                    "Película muy reciente (${selectedItem!!.releaseDate}). Deben pasar 40 días. Faltan aprox. $faltan días."
                                                return@Button
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                }

                                pasoConfirmacion = true
                            }
                        ) {
                            Text(
                                "SIGUIENTE",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                } else {
                    Text(
                        text = "📋 CONFIRMAR PEDIDO",
                        color = ColorDorado,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF141414))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlideImageCustom(
                                url = selectedItem?.posterPath,
                                modifier = Modifier
                                    .size(width = 70.dp, height = 105.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    selectedItem?.title ?: "",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "⭐ ${selectedItem?.voteAverage ?: 0.0}  |  📅 ${selectedItem?.releaseDate}",
                                    color = ColorDorado,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    selectedItem?.overview ?: "",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 3
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF444444), RoundedCornerShape(8.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "👤 Solicitante: $nombreUsuario",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                "💰 Saldo Actual: $saldoActual CasTV",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                "📉 Costo Pedido: $costoPedido CasTV",
                                color = Color(0xFFFF5252),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(color = Color(0xFF444444))
                            Text(
                                "✅ Saldo Final: ${saldoActual - costoPedido} CasTV",
                                color = ColorDorado,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { pasoConfirmacion = false },
                            border = BorderStroke(1.dp, Color.Gray)
                        ) {
                            Text("CORREGIR", color = Color.White, fontSize = 14.sp)
                        }

                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDorado),
                            onClick = {
                                CastvHelper.mostrarSelectorFechaHora(activity) { fecha, hora ->
                                    val item = selectedItem ?: return@mostrarSelectorFechaHora
                                    val moviesRef = databaseRef.child("movies")
                                    val correoUsuario =
                                        userEmail.ifBlank { correoKey.replace("_", ".") }

                                    moviesRef.orderByChild("originalTitle")
                                        .equalTo(item.originalTitle)
                                        .addListenerForSingleValueEvent(object :
                                            ValueEventListener {
                                            override fun onDataChange(snapshot: DataSnapshot) {
                                                val tiempoActual = System.currentTimeMillis()
                                                if (snapshot.exists()) {
                                                    val existingId =
                                                        snapshot.children.firstOrNull()?.key ?: ""
                                                    if (existingId.isNotEmpty()) {
                                                        moviesRef.child(existingId)
                                                            .child("createdAt")
                                                            .setValue(tiempoActual)
                                                        val solRef = moviesRef.child(existingId)
                                                            .child("solicitudes").push()
                                                        solRef.setValue(
                                                            mapOf(
                                                                "id" to (solRef.key ?: ""),
                                                                "email" to correoUsuario,
                                                                "userId" to nombreUsuario,
                                                                "fechaActivacion" to fecha,
                                                                "horaActivacion" to hora,
                                                                "timestamp" to ServerValue.TIMESTAMP
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    val newId = moviesRef.push().key ?: return
                                                    val mapaNueva = hashMapOf(
                                                        "id" to newId,
                                                        "title" to item.title,
                                                        "originalTitle" to item.originalTitle,
                                                        "nombre" to "${item.title} (${item.releaseDate})".trim(),
                                                        "imageUrl" to item.posterPath,
                                                        "streamUrl" to "https://tuservidor.com/stream/",
                                                        "castv" to 10,
                                                        "countdownMinutes" to 0,
                                                        "createdAt" to tiempoActual,
                                                        "email" to correoUsuario,
                                                        "trailerUrl" to "",
                                                        "userId" to nombreUsuario
                                                    )
                                                    moviesRef.child(newId).setValue(mapaNueva)
                                                        .addOnSuccessListener {
                                                            val solRef = moviesRef.child(newId)
                                                                .child("solicitudes").push()
                                                            solRef.setValue(
                                                                mapOf(
                                                                    "id" to (solRef.key ?: ""),
                                                                    "email" to correoUsuario,
                                                                    "userId" to nombreUsuario,
                                                                    "fechaActivacion" to fecha,
                                                                    "horaActivacion" to hora,
                                                                    "timestamp" to ServerValue.TIMESTAMP
                                                                )
                                                            )
                                                        }
                                                }

                                                val userRef =
                                                    databaseRef.child("usuarios").child(correoKey)
                                                userRef.child("castv")
                                                    .setValue(saldoActual - costoPedido)
                                                    .addOnSuccessListener {
                                                        CastvHelper.registrarConsumo(
                                                            correoUsuario,
                                                            "PEDIDO: ${item.title}",
                                                            costoPedido
                                                        )
                                                        Toast.makeText(
                                                            activity,
                                                            "¡Pedido realizado con éxito! 🎬",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        onDismiss()
                                                    }
                                            }

                                            override fun onCancelled(error: DatabaseError) {
                                                onDismiss()
                                            }
                                        })
                                }
                            }
                        ) {
                            Text(
                                "CONFIRMAR Y PROGRAMAR",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. DIÁLOGO PAQUETES
// ==========================================
@Composable
fun DialogoPaquetes(
    correoKey: String,
    databaseRef: DatabaseReference,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var pasoQR by remember { mutableStateOf(false) }

    var paqueteElegido by remember { mutableStateOf("Bronce") }
    var montoElegido by remember { mutableStateOf("10000") }
    var pedidoId by remember { mutableStateOf("") }

    var estadoPedido by remember { mutableStateOf("pendiente") }
    var urlPagosComprobante by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var urlPasarela by remember { mutableStateOf("") }

    LaunchedEffect(correoKey) {
        if (correoKey.isNotBlank()) {
            databaseRef.child("usuarios").child(correoKey).get().addOnSuccessListener { snapshot ->
                val estado = snapshot.child("estado").value?.toString() ?: ""
                val paqueteGuardado = snapshot.child("paquete").value?.toString() ?: ""
                val pedidoIdGuardado = snapshot.child("pedidoId").value?.toString() ?: ""

                if (estado.equals("pendiente", ignoreCase = true) && paqueteGuardado.isNotEmpty() && pedidoIdGuardado.isNotEmpty()) {
                    paqueteElegido = paqueteGuardado
                    pedidoId = pedidoIdGuardado
                    montoElegido = when (paqueteGuardado) {
                        "Bronce" -> "10000"
                        "Plata" -> "22000"
                        "Oro" -> "45000"
                        else -> "10000"
                    }
                    val url = "https://onnline.web.app/CineParche/public/pagos/index.html?user=$correoKey&pedido=$pedidoId&paquete=$paqueteElegido&monto=$montoElegido"
                    urlPasarela = url
                    qrBitmap = generarBitmapQR(url)
                    pasoQR = true
                }
            }
        }
    }

    DisposableEffect(correoKey, pasoQR) {
        if (pasoQR && correoKey.isNotBlank()) {
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    estadoPedido = snapshot.child("estado").value?.toString() ?: "pendiente"
                    urlPagosComprobante = snapshot.child("urlpagos").value?.toString() ?: ""
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            val ref = databaseRef.child("usuarios").child(correoKey)
            ref.addValueEventListener(listener)
            onDispose { ref.removeEventListener(listener) }
        } else {
            onDispose {}
        }
    }

    Dialog(onDismissRequest = onDismiss,properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            modifier = Modifier
                .widthIn(max = 750.dp)
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!pasoQR) {
                    Text(
                        text = "💎 SELECCIONAR PAQUETE",
                        color = ColorDorado,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Selecciona el paquete de créditos CasTV a recargar:",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    val paquetes = listOf(
                        Triple("Bronce", "10000", "Bronce: $10.000 (50 CasTV)"),
                        Triple("Plata", "22000", "Plata: $22.000 (120 CasTV)"),
                        Triple("Oro", "45000", "Oro: $45.000 (250 CasTV)")
                    )

                    paquetes.forEach { (nombre, precio, etiqueta) ->
                        var isItemFocused by remember { mutableStateOf(false) }
                        val isSelected = paqueteElegido == nombre

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(if (isItemFocused) 1.02f else 1.0f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isItemFocused) Color(0xFF2E2E2E) else if (isSelected) Color(0xFF262626) else Color(0xFF141414))
                                .border(
                                    width = if (isItemFocused || isSelected) 2.dp else 0.5.dp,
                                    color = if (isItemFocused || isSelected) ColorDorado else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .focusable()
                                .clickable {
                                    paqueteElegido = nombre
                                    montoElegido = precio
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    paqueteElegido = nombre
                                    montoElegido = precio
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = ColorDorado, unselectedColor = Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = etiqueta,
                                color = if (isSelected) ColorDorado else Color.White,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("CANCELAR", color = Color.White, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = ColorDorado),
                            onClick = {
                                val timestamp = System.currentTimeMillis().toString().takeLast(4)
                                val numeroAleatorio = (100..999).random()
                                pedidoId = "ORD$timestamp$numeroAleatorio"

                                val url = "https://onnline.web.app/CineParche/public/pagos/index.html?user=$correoKey&pedido=$pedidoId&paquete=$paqueteElegido&monto=$montoElegido"
                                urlPasarela = url

                                databaseRef.child("usuarios").child(correoKey).updateChildren(
                                    mapOf(
                                        "pedidoId" to pedidoId,
                                        "paquete" to paqueteElegido,
                                        "estado" to "pendiente"
                                    )
                                )

                                qrBitmap = generarBitmapQR(url)
                                pasoQR = true
                            }
                        ) {
                            Text("SIGUIENTE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                } else {
                    Text(
                        text = "💎 PEDIDO REGISTRADO",
                        color = ColorDorado,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val esRevision = urlPagosComprobante.isNotEmpty() && estadoPedido.equals("pendiente", ignoreCase = true)
                    val esActivo = estadoPedido.equals("activo", ignoreCase = true)

                    val estadoVisual = when {
                        esActivo -> "PAQUETE ACTIVADO"
                        esRevision -> "EN REVISIÓN"
                        else -> "PENDIENTE DE PAGO"
                    }

                    val colorEstado = when {
                        esActivo -> Color(0xFF34D399)
                        esRevision -> Color(0xFF34D399)
                        else -> Color(0xFFF87171)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ID Pedido: $pedidoId", color = Color.LightGray, fontSize = 14.sp)
                        Text("Paquete: $paqueteElegido ($$montoElegido)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Estado: $estadoVisual",
                            color = colorEstado,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    when {
                        esActivo -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF132B1A))
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✅ ¡Tu pago fue aprobado con éxito!\nTus créditos CasTV ya se encuentran disponibles en tu cuenta.",
                                    color = Color(0xFF34D399),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        esRevision -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1B2A22))
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🔍 Tu comprobante de pago está en revisión.\nUna vez validado por nuestro equipo, tus créditos se sumarán automáticamente.",
                                    color = Color(0xFF34D399),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            qrBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Código QR de Pago",
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            if (urlPasarela.isNotEmpty()) {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlPasarela)))
                                            }
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Bre-Be: Código @TMB833\n✔ Soporte activo 24/7\nEscanea o pulsa sobre el QR para subir tu comprobante",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDorado),
                        onClick = onDismiss
                    ) {
                        Text("CERRAR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DialogoConfirmacionSalida(
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    var segundos by remember { mutableIntStateOf(5) }

    LaunchedEffect(Unit) {
        while (segundos > 0) {
            delay(1000)
            segundos--
        }
        onCancelar()
    }

    Dialog(onDismissRequest = onCancelar,properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = ColorFondoOscuro),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("¿Desea cerrar CineParche?", color = ColorDorado, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("Si sales ahora, te perderás lo mejor del parche.", color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = ColorDorado),
                        onClick = onConfirmar
                    ) {
                        Text("SÍ, SALIR", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = onCancelar) {
                        Text("VOLVER (${segundos}s)", color = ColorDorado)
                    }
                }
            }
        }
    }
}

// ==========================================
// 🟢 DIÁLOGOS DE ACTUALIZACIÓN (COMPOSE)
// ==========================================
@Composable
fun DialogoActualizacion(
    version: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit
) {
    Dialog(onDismissRequest = onCancelar,properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ColorFondoOscuro),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🚀 Nueva Versión $version",
                    color = ColorDorado,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Hemos mejorado CineParche para ti. Actualiza ahora para disfrutar de la mejor experiencia.",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
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
                            .scale(if (isBtnActualizarFocused) 1.1f else 1.0f)
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
                            .scale(if (isBtnLuegoFocused) 1.1f else 1.0f)
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
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "📥 Actualizando CineParche v$version",
                    color = ColorDorado,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                LinearProgressIndicator(
                    progress = { if (progreso >= 0) progreso / 100f else 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = ColorDorado,
                    trackColor = Color.Gray,
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (progreso >= 0 && progreso < 100) {
                    Text("Descargando... $progreso%", color = ColorDorado, fontSize = 16.sp)
                } else if (progreso == 100) {
                    Text("Descarga completada ✅", color = Color.Green, fontSize = 16.sp)
                } else {
                    Text("Iniciando descarga segura...", color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

// ==========================================
// 🟢 LÓGICA CORE DE INSTALACIÓN Y VERIFICACIÓN
// ==========================================
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