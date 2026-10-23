package com.creativem.fulltv.api

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.peliculasvalidas.Validaciones
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.CineAlert
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.principal.Perfil
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

// Paleta Dorado Premium CasTV
private val DoradoPrincipal = Color(0xFFC5A059)
private val DoradoBrillante = Color(0xFFEAD295)
private val DoradoOscuro = Color(0xFF8C6E2D)
private val DegradadoDoradoPremiun = Brush.horizontalGradient(
    listOf(Color(0xFFF3E0AA), Color(0xFFC5A059), Color(0xFF9E7B36))
)

class ApiPeliculaActivity : ComponentActivity() {

    data class VideoResponse(val results: List<VideoResult>)
    data class VideoResult(val key: String, val site: String, val type: String)

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val validaciones = Validaciones()
    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }

    private lateinit var apiService: TMDbApiService
    private val apiKey = "678193d2c735c6f37840cee035f4d69a"

    private var realTmdbId = 0
    private var isProcessingOrder = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val client = OkHttpClient.Builder().hostnameVerifier { _, _ -> true }.build()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
        apiService = retrofit.create(TMDbApiService::class.java)

        val movieOriginalTitleExtra = intent.getStringExtra("EXTRA_ORIGINAL_TITLE") ?: ""
        val initialModelo: Modelo? = intent.getParcelableExtra("EXTRA_MOVIE_DATA")
        val streamUrlExtra = intent.getStringExtra("EXTRA_STREAM_URL") ?: ""
        val movieTitleExtra = intent.getStringExtra("EXTRA_MOVIE_TITLE") ?: ""
        val movieCastvExtra = intent.getIntExtra("EXTRA_MOVIE_CASTV", 0)
        val movieImageUrlExtra = intent.getStringExtra("EXTRA_MOVIE_IMAGE_URL") ?: ""
        val movieCountdownExtra = intent.getIntExtra("EXTRA_COUNTDOWN", 0)
        val movieCreatedAtExtra = intent.getLongExtra("EXTRA_CREATED_AT", 0L)

        setContent {
            val scope = rememberCoroutineScope()

            var modeloActual by remember {
                mutableStateOf(
                    initialModelo ?: Modelo(
                        id = "0",
                        title = movieTitleExtra,
                        originalTitle = movieOriginalTitleExtra,
                        imageUrl = movieImageUrlExtra,
                        streamUrl = streamUrlExtra,
                        castv = movieCastvExtra,
                        countdownMinutes = movieCountdownExtra,
                        createdAt = movieCreatedAtExtra
                    )
                )
            }

            var tituloDisplay by remember { mutableStateOf(modeloActual.title) }
            var fechaDisplay by remember { mutableStateOf("Cargando...") }
            var calificacionDisplay by remember { mutableStateOf("") }
            var sinopsisDisplay by remember { mutableStateOf("Cargando detalles...") }
            var infoAdicionalDisplay by remember { mutableStateOf("Obteniendo información...") }
            var posterDisplay by remember { mutableStateOf(modeloActual.imageUrl) }
            var backdropDisplay by remember { mutableStateOf(modeloActual.imageUrl) }
            var carteleraList by remember { mutableStateOf<List<Modelo>>(emptyList()) }

            var showPurchaseDialog by remember { mutableStateOf(false) }
            var showErrorBrokenDialog by remember { mutableStateOf(false) }
            var btnReproducirText by remember { mutableStateOf("Reproducir") }
            var isVerificandoReproducir by remember { mutableStateOf(false) }

            var nombreUsuarioDialog by remember { mutableStateOf("Consultando...") }
            var saldoUsuarioDialog by remember { mutableStateOf("Consultando...") }
            var isVerificandoEstreno by remember { mutableStateOf(false) }

            var fetchTmdbJob by remember { mutableStateOf<Job?>(null) }

            fun mostrarContenidoLocal(modelo: Modelo) {
                realTmdbId = modelo.id.toIntOrNull() ?: 0
                tituloDisplay = modelo.title.ifEmpty { "Gran Estreno CineParche" }
                fechaDisplay = if (modelo.releaseDate.isNullOrBlank()) "Estreno Reciente" else "${modelo.releaseDate} ✅"
                calificacionDisplay = if (modelo.voteAverage == null || modelo.voteAverage == 0.0) "⭐ 8.5" else "⭐ ${modelo.voteAverage}"
                infoAdicionalDisplay = if (modelo.genres.isNullOrBlank()) "Acción • Aventura • Cine" else modelo.genres!!
                sinopsisDisplay = if (modelo.overview.isNullOrBlank()) "Disfruta de esta increíble producción ahora en CineParche." else modelo.overview!!
                posterDisplay = modelo.imageUrl
                backdropDisplay = modelo.imageUrl
            }

            fun buscarPeliculaTmdb(queryLimpia: String, modeloFallback: Modelo) {
                apiService.searchMovie(apiKey, "es-MX", queryLimpia).enqueue(object : Callback<MovieResponse> {
                    override fun onResponse(call: Call<MovieResponse>, response: Response<MovieResponse>) {
                        val primera = response.body()?.results?.firstOrNull()
                        if (response.isSuccessful && primera != null) {
                            realTmdbId = primera.id
                            tituloDisplay = primera.title ?: modeloFallback.title
                            fechaDisplay = "\uD83D\uDDD3 ${primera.release_date ?: "N/A"}"
                            calificacionDisplay = "⭐ ${primera.vote_average ?: "N/A"}"
                            sinopsisDisplay = primera.overview ?: "Sin sinopsis disponible"
                            posterDisplay = "https://image.tmdb.org/t/p/w500${primera.poster_path}"
                            backdropDisplay = "https://image.tmdb.org/t/p/w780${primera.backdrop_path}"

                            apiService.getMovieDetails(primera.id, apiKey, "es-MX").enqueue(object : Callback<MovieDetailResponse> {
                                override fun onResponse(call: Call<MovieDetailResponse>, res: Response<MovieDetailResponse>) {
                                    if (res.isSuccessful) {
                                        val detalles = res.body()
                                        val generos = detalles?.genres?.joinToString(", ") { it.name } ?: "General"
                                        val duracion = detalles?.runtime ?: 0
                                        infoAdicionalDisplay = "🎭 $generos   ⏱ ${duracion} Min"
                                    }
                                }
                                override fun onFailure(call: Call<MovieDetailResponse>, t: Throwable) {
                                    infoAdicionalDisplay = "No se pudieron obtener detalles"
                                }
                            })

                            apiService.getCredits(primera.id, apiKey).enqueue(object : Callback<CreditsResponse> {
                                override fun onResponse(call: Call<CreditsResponse>, res: Response<CreditsResponse>) {
                                    if (res.isSuccessful) {
                                        val director = res.body()?.crew?.find { it.job == "Director" }?.name ?: ""
                                        if (director.isNotBlank()) {
                                            infoAdicionalDisplay += "   |   Director: $director"
                                        }
                                    }
                                }
                                override fun onFailure(call: Call<CreditsResponse>, t: Throwable) {}
                            })
                        } else {
                            mostrarContenidoLocal(modeloFallback)
                        }
                    }

                    override fun onFailure(call: Call<MovieResponse>, t: Throwable) {
                        mostrarContenidoLocal(modeloFallback)
                    }
                })
            }

            fun onPeliculaCarteleraEnfocada(nuevoModelo: Modelo) {
                if (modeloActual.id == nuevoModelo.id) return

                modeloActual = nuevoModelo
                tituloDisplay = nuevoModelo.title
                posterDisplay = nuevoModelo.imageUrl
                backdropDisplay = nuevoModelo.imageUrl
                sinopsisDisplay = "Cargando información..."
                infoAdicionalDisplay = "Obteniendo detalles..."

                fetchTmdbJob?.cancel()
                fetchTmdbJob = scope.launch {
                    delay(200)
                    val consulta = nuevoModelo.originalTitle?.ifBlank { nuevoModelo.title } ?: nuevoModelo.title
                    val consultaLimpia = consulta
                        .replace(Regex("\\(\\d{4}-\\d{2}-\\d{2}\\)"), "")
                        .replace(Regex("\\(\\d{4}\\)"), "")
                        .replace(Regex("\\[.*?\\]"), "")
                        .trim()
                    buscarPeliculaTmdb(consultaLimpia, nuevoModelo)
                }
            }

            fun consultarDatosUsuario(correo: String) {
                if (correo.isNotEmpty()) {
                    CastvHelper.obtenerDatosUsuario(
                        correo,
                        onSuccess = { nombre, _, castv, _ ->
                            nombreUsuarioDialog = nombre
                            saldoUsuarioDialog = "$castv CasTV"
                        },
                        onFailure = {
                            nombreUsuarioDialog = "No disponible"
                            saldoUsuarioDialog = "No disponible"
                        }
                    )
                }
            }

            LaunchedEffect(Unit) {
                val consultaInicial = (modeloActual.originalTitle ?: modeloActual.title)
                    .replace(Regex("\\(\\d{4}-\\d{2}-\\d{2}\\)"), "")
                    .replace(Regex("\\(\\d{4}\\)"), "")
                    .replace(Regex("\\[.*?\\]"), "")
                    .trim()
                buscarPeliculaTmdb(consultaInicial, modeloActual)

                while (isActive) {
                    val listaCompleta = Validacioneslista.obtenerPeliculasValidas()
                    val listaFiltrada = listaCompleta.filter { movie ->
                        val esUrlValida = movie.streamUrl.isNotBlank() && !movie.streamUrl.contains("tuservidor.com")
                        val isCountdownActive = calcularSiContadorEstaActivo(movie)
                        esUrlValida && !isCountdownActive
                    }
                    if (listaFiltrada.size != carteleraList.size) {
                        carteleraList = listaFiltrada
                    }
                    if (Validacioneslista.yaCargado()) break
                    delay(1000)
                }
            }

            BackHandler {
                finish()
            }

            PantallaDetalleTv(
                titulo = tituloDisplay,
                fecha = fechaDisplay,
                calificacion = calificacionDisplay,
                sinopsis = sinopsisDisplay,
                infoAdicional = infoAdicionalDisplay,
                posterUrl = posterDisplay,
                backdropUrl = backdropDisplay,
                cartelera = carteleraList,
                peliculaActualId = modeloActual.id,
                btnReproducirText = btnReproducirText,
                isVerificando = isVerificandoReproducir,
                onTrailerClick = { reproducirTrailer() },
                onCarteleraItemFocused = { onPeliculaCarteleraEnfocada(it) },
                onReproducirClick = {
                    val urlActual = modeloActual.streamUrl
                    val costoActual = modeloActual.castv
                    val user = auth.currentUser

                    if (urlActual.contains("tuservidor.com") || urlActual.isBlank()) {
                        consultarDatosUsuario(user?.email ?: "")
                        showErrorBrokenDialog = true
                        return@PantallaDetalleTv
                    }

                    val countdownGlobal = modeloActual.countdownMinutes
                    val createdAtGlobal = modeloActual.createdAt
                    val createdAtMillis = if (createdAtGlobal in 1..999999999999L) createdAtGlobal * 1000 else createdAtGlobal

                    var isPromoGlobalActiva = false
                    if (countdownGlobal > 0) {
                        if (createdAtMillis == 0L) {
                            isPromoGlobalActiva = true
                        } else {
                            val countdownDurationMillis = TimeUnit.MINUTES.toMillis(countdownGlobal.toLong())
                            val timeElapsed = System.currentTimeMillis() - createdAtMillis
                            if (countdownDurationMillis - timeElapsed > 0) isPromoGlobalActiva = true
                        }
                    }

                    if (isPromoGlobalActiva) {
                        irAlReproductorDirecto(modeloActual)
                        return@PantallaDetalleTv
                    }

                    if (user == null || user.email == null) {
                        CineAlert.show(this@ApiPeliculaActivity, "Debes iniciar sesión para reproducir", CineAlert.Tipo.ERROR)
                        return@PantallaDetalleTv
                    }

                    val correoKey = user.email!!.replace(".", "_").replace("@", "_")
                    val tituloMovie = modeloActual.title

                    isVerificandoReproducir = true
                    btnReproducirText = "Verificando..."

                    scope.launch {
                        val isAlquilerActivo = withContext(Dispatchers.IO) {
                            verificarAlquilerVigenteSincrono(correoKey, tituloMovie)
                        }

                        if (isAlquilerActivo) {
                            isVerificandoReproducir = false
                            btnReproducirText = "Reproducir"
                            irAlReproductorDirecto(modeloActual)
                        } else {
                            btnReproducirText = "Procesando..."
                            val enlaceValido = withContext(Dispatchers.IO) {
                                validaciones.isUrlValid(urlActual)
                            }
                            isVerificandoReproducir = false
                            btnReproducirText = "Reproducir"

                            if (enlaceValido) {
                                showPurchaseDialog = true
                            } else {
                                consultarDatosUsuario(user.email ?: "")
                                showErrorBrokenDialog = true
                            }
                        }
                    }
                }
            )

            if (showPurchaseDialog) {
                DialogConfirmarAlquiler(
                    modelo = modeloActual,
                    onDismiss = { showPurchaseDialog = false },
                    onConfirm = {
                        // Cerramos el diálogo de inmediato para no solapar ventanas
                        showPurchaseDialog = false

                        val user = auth.currentUser
                        if (user?.email != null) {
                            val correoKey = user.email!!.replace(".", "_").replace("@", "_")
                            verificarPuntos(correoKey, modeloActual.castv) { tienePuntos ->
                                if (tienePuntos) {
                                    descontarPuntos(correoKey, modeloActual.castv, modeloActual.title) { exito ->
                                        if (exito) {
                                            activarAlquilerUsuario(correoKey, modeloActual.title)
                                            CineAlert.show(this@ApiPeliculaActivity, "¡Alquiler exitoso! Disponible por 5 horas.", CineAlert.Tipo.EXITO) {
                                                irAlReproductorDirecto(modeloActual)
                                            }
                                        } else {
                                            CineAlert.show(this@ApiPeliculaActivity, "Error procesando el pago", CineAlert.Tipo.ERROR)
                                        }
                                    }
                                } else {
                                    CineAlert.show(this@ApiPeliculaActivity, "Saldo CasTV insuficiente.", CineAlert.Tipo.ERROR)
                                }
                            }
                        }
                    }
                )
            }

            if (showErrorBrokenDialog) {
                DialogEnlaceRoto(
                    modelo = modeloActual,
                    nombreUsuario = nombreUsuarioDialog,
                    saldoUsuario = saldoUsuarioDialog,
                    isVerificandoEstreno = isVerificandoEstreno,
                    onDismiss = {
                        // SOLO cerramos el diálogo y te quedas en la misma pantalla
                        showErrorBrokenDialog = false
                    },
                    onMasInfoClick = {
                        startActivity(Intent(this@ApiPeliculaActivity, Perfil::class.java))
                    },
                    onAlquilarClick = {
                        isVerificandoEstreno = true
                        validarEstrenoYProcesar(
                            modelo = modeloActual,
                            onSuccess = {
                                isVerificandoEstreno = false
                                showErrorBrokenDialog = false
                                CastvHelper.mostrarSelectorFechaHora(this@ApiPeliculaActivity) { fechaSel, horaSel ->
                                    enviarPedido(modeloActual, fechaSel, horaSel) {
                                        volverAlContenido() // Aquí sí tiene sentido volver tras completar el pedido
                                    }
                                }
                            },
                            onRechazado = { msg ->
                                isVerificandoEstreno = false
                                showErrorBrokenDialog = false
                                CineAlert.show(this@ApiPeliculaActivity, msg, CineAlert.Tipo.ERROR)
                            }
                        )
                    }
                )
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(CastvHelper.ajustarContexto(newBase))
    }

    override fun getResources(): android.content.res.Resources {
        val res = super.getResources()
        CastvHelper.ajustarRecursos(res, this)
        return res
    }

    private fun reproducirTrailer() {
        if (realTmdbId == 0) {
            Toast.makeText(this, "El tráiler no está disponible para esta película.", Toast.LENGTH_SHORT).show()
            return
        }

        apiService.getMovieVideos(realTmdbId, apiKey, "es-MX").enqueue(object : Callback<VideoResponse> {
            override fun onResponse(call: Call<VideoResponse>, response: Response<VideoResponse>) {
                var keyTrailer = ""
                if (response.isSuccessful) {
                    val resultados = response.body()?.results ?: emptyList()
                    val trailer = resultados.find { it.site == "YouTube" && it.type == "Trailer" }
                        ?: resultados.find { it.site == "YouTube" }
                    if (trailer != null) keyTrailer = trailer.key
                }

                if (keyTrailer.isNotEmpty()) {
                    abrirTrailer(keyTrailer)
                } else {
                    apiService.getMovieVideos(realTmdbId, apiKey, "").enqueue(object : Callback<VideoResponse> {
                        override fun onResponse(call: Call<VideoResponse>, response: Response<VideoResponse>) {
                            if (response.isSuccessful) {
                                val resultados = response.body()?.results ?: emptyList()
                                val trailer = resultados.find { it.site == "YouTube" && it.type == "Trailer" }
                                    ?: resultados.find { it.site == "YouTube" }
                                if (trailer != null) {
                                    abrirTrailer(trailer.key)
                                } else {
                                    Toast.makeText(this@ApiPeliculaActivity, "Tráiler no disponible en YouTube.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        override fun onFailure(call: Call<VideoResponse>, t: Throwable) {
                            Toast.makeText(this@ApiPeliculaActivity, "Tráiler no disponible.", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }

            override fun onFailure(call: Call<VideoResponse>, t: Throwable) {
                Toast.makeText(this@ApiPeliculaActivity, "Error de red al buscar el tráiler.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun abrirTrailer(key: String) {
        val intent = Intent(this@ApiPeliculaActivity, TrailerActivity::class.java).apply {
            putExtra("EXTRA_TRAILER_KEY", key)
        }
        startActivity(intent)
    }

    private fun validarEstrenoYProcesar(modelo: Modelo, onSuccess: () -> Unit, onRechazado: (String) -> Unit) {
        val queryTitle = modelo.originalTitle?.ifBlank { modelo.title } ?: modelo.title

        apiService.searchMovie(apiKey, "es-MX", queryTitle).enqueue(object : Callback<MovieResponse> {
            override fun onResponse(call: Call<MovieResponse>, response: Response<MovieResponse>) {
                var fechaAValidar = ""
                if (response.isSuccessful) {
                    val primeraPeli = response.body()?.results?.firstOrNull()
                    if (primeraPeli != null && !primeraPeli.release_date.isNullOrBlank()) {
                        fechaAValidar = primeraPeli.release_date
                    }
                }

                if (fechaAValidar.isBlank()) {
                    fechaAValidar = modelo.releaseDate ?: ""
                }

                if (fechaAValidar.isBlank()) {
                    onRechazado("No se pudo verificar la fecha de estreno en la base de datos. Operación denegada por seguridad.")
                    return
                }

                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val dateEstreno = sdf.parse(fechaAValidar)
                    if (dateEstreno != null) {
                        val diffMillis = System.currentTimeMillis() - dateEstreno.time
                        val diffDias = TimeUnit.MILLISECONDS.toDays(diffMillis)

                        if (diffDias < 40) {
                            val diasFaltantes = if (diffDias < 0) 40 + abs(diffDias) else 40 - diffDias
                            onRechazado("Película en cines Estreno ($fechaAValidar).\nDeben pasar 40 días. Faltan aprox. $diasFaltantes días.")
                        } else {
                            onSuccess()
                        }
                    } else {
                        onRechazado("Error leyendo la fecha de estreno.")
                    }
                } catch (e: Exception) {
                    onRechazado("Error de formato en la fecha.")
                }
            }

            override fun onFailure(call: Call<MovieResponse>, t: Throwable) {
                onRechazado("Error de red. No se pudo conectar con la base de datos para validar el estreno.")
            }
        })
    }

    private fun enviarPedido(
        modelo: Modelo,
        fechaActivacion: String,
        horaActivacion: Int,
        onTerminado: () -> Unit
    ) {
        if (isProcessingOrder) return
        isProcessingOrder = true

        val user = auth.currentUser
        if (user != null && user.email != null) {
            val correoKey = user.email!!.replace(".", "_").replace("@", "_")

            databaseRef.child("usuarios").child(correoKey).get().addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val userName = snapshot.child("nombre").value?.toString() ?: "Sin nombre"
                    val userEmail = snapshot.child("correo").value?.toString() ?: user.email!!
                    val costoPedido = modelo.castv

                    verificarPuntos(correoKey, costoPedido) { tienePuntos ->
                        if (tienePuntos) {
                            descontarPuntos(correoKey, costoPedido, modelo.title) { exitoDescuento ->
                                if (exitoDescuento) {
                                    val tituloOriginal = modelo.originalTitle?.ifBlank { modelo.title } ?: modelo.title
                                    val urlImagen = modelo.imageUrl
                                    val tituloBase = modelo.title
                                    val fecha = modelo.releaseDate ?: ""
                                    val tituloUsado = if (fecha.isNotEmpty()) "$tituloBase ($fecha)" else tituloBase
                                    val anioEstreno = if (fecha.length >= 4) fecha.substring(0, 4) else "2024"

                                    verificarYCrearPeliculaRota(
                                        tituloMovie = tituloUsado,
                                        originalTitleMovie = tituloOriginal,
                                        imageUrlMovie = urlImagen,
                                        urlRota = modelo.streamUrl,
                                        anio = anioEstreno,
                                        userEmail = userEmail,
                                        userName = userName,
                                        fechaActivacion = fechaActivacion,
                                        horaActivacion = horaActivacion
                                    )

                                    CineAlert.show(this, "Pedido enviado. Puntos descontados.", CineAlert.Tipo.EXITO) {
                                        isProcessingOrder = false
                                        onTerminado()
                                    }
                                } else {
                                    isProcessingOrder = false
                                    CineAlert.show(this, "Error al procesar el descuento de puntos.", CineAlert.Tipo.ERROR)
                                }
                            }
                        } else {
                            isProcessingOrder = false
                            CineAlert.show(this, "Saldo CasTV insuficiente.", CineAlert.Tipo.ERROR)
                        }
                    }
                } else {
                    isProcessingOrder = false
                }
            }.addOnFailureListener { isProcessingOrder = false }
        }
    }

    private fun verificarYCrearPeliculaRota(
        tituloMovie: String,
        originalTitleMovie: String,
        imageUrlMovie: String,
        urlRota: String,
        anio: String,
        userEmail: String,
        userName: String,
        fechaActivacion: String,
        horaActivacion: Int
    ) {
        if (isFinishing || isDestroyed) return

        val customDbUrl = "https://corario-16991-default-rtdb.firebaseio.com/"
        val dbMoviesRef = FirebaseDatabase.getInstance(customDbUrl).getReference("movies")

        if (originalTitleMovie.isBlank()) return

        dbMoviesRef.orderByChild("originalTitle").equalTo(originalTitleMovie)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    if (isFinishing || isDestroyed) return

                    if (snapshot.exists()) {
                        val existingId = snapshot.children.firstOrNull()?.key ?: ""
                        if (existingId.isNotEmpty()) {
                            dbMoviesRef.child(existingId).child("createdAt").setValue(System.currentTimeMillis())
                            val solicitudesRef = dbMoviesRef.child(existingId).child("solicitudes").push()
                            val idSolicitud = solicitudesRef.key ?: ""

                            val datosSolicitud = mapOf<String, Any>(
                                "id" to idSolicitud,
                                "email" to userEmail,
                                "userId" to userName,
                                "fechaActivacion" to fechaActivacion,
                                "horaActivacion" to horaActivacion,
                                "timestamp" to ServerValue.TIMESTAMP
                            )
                            solicitudesRef.setValue(datosSolicitud)
                        }
                        return
                    }

                    val newId = dbMoviesRef.push().key ?: return
                    val nombreFormateado = if (tituloMovie.contains("($anio)")) tituloMovie.trim() else "$tituloMovie ($anio)".trim()

                    val nuevaPeliculaMap = hashMapOf(
                        "id" to newId,
                        "title" to tituloMovie,
                        "originalTitle" to originalTitleMovie,
                        "nombre" to nombreFormateado,
                        "imageUrl" to imageUrlMovie,
                        "streamUrl" to urlRota,
                        "castv" to 10,
                        "countdownMinutes" to 0,
                        "createdAt" to System.currentTimeMillis(),
                        "email" to "",
                        "trailerUrl" to "",
                        "userId" to ""
                    )

                    dbMoviesRef.child(newId).setValue(nuevaPeliculaMap).addOnSuccessListener {
                        if (isFinishing || isDestroyed) return@addOnSuccessListener
                        val solicitudesRef = dbMoviesRef.child(newId).child("solicitudes").push()
                        val idSolicitud = solicitudesRef.key ?: ""

                        val datosSolicitud = mapOf<String, Any>(
                            "id" to idSolicitud,
                            "email" to userEmail,
                            "userId" to userName,
                            "fechaActivacion" to fechaActivacion,
                            "horaActivacion" to horaActivacion,
                            "timestamp" to ServerValue.TIMESTAMP
                        )
                        solicitudesRef.setValue(datosSolicitud)
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
    }

    private suspend fun verificarAlquilerVigenteSincrono(correoKey: String, tituloPelicula: String): Boolean =
        suspendCoroutine { cont ->
            val peliculaKey = tituloPelicula.replace(".", "_")
                .replace("$", "_")
                .replace("#", "_")
                .replace("[", "_")
                .replace("]", "_")

            databaseRef.child("usuarios")
                .child(correoKey)
                .child("alquileres")
                .child(peliculaKey)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val createdAt = snapshot.child("createdAt").value as? Long ?: 0L
                        val countdownMinutes = (snapshot.child("countdownMinutes").value as? Number)?.toInt() ?: 0

                        if (countdownMinutes > 0 && createdAt > 0) {
                            val durationMillis = TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                            val timeElapsed = System.currentTimeMillis() - createdAt
                            val remainingTime = durationMillis - timeElapsed
                            cont.resume(remainingTime > 0)
                            return@addOnSuccessListener
                        }
                    }
                    cont.resume(false)
                }
                .addOnFailureListener { cont.resume(false) }
        }

    private fun activarAlquilerUsuario(correoKey: String, tituloPelicula: String) {
        val peliculaKey = tituloPelicula.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")

        val alquilerRef = databaseRef.child("usuarios").child(correoKey).child("alquileres").child(peliculaKey)
        val datosAlquiler = mapOf<String, Any>(
            "countdownMinutes" to 300,
            "createdAt" to ServerValue.TIMESTAMP
        )
        alquilerRef.setValue(datosAlquiler)
    }

    private fun verificarPuntos(correoKey: String, costo: Int, callback: (Boolean) -> Unit) {
        databaseRef.child("usuarios").child(correoKey).child("castv").get().addOnSuccessListener { snapshot ->
            val puntosActuales = (snapshot.value as? Number)?.toInt() ?: 0
            callback(puntosActuales >= costo)
        }.addOnFailureListener { callback(false) }
    }

    private fun descontarPuntos(correoKey: String, costo: Int, tituloPelicula: String, onComplete: (Boolean) -> Unit) {
        val userRef = databaseRef.child("usuarios").child(correoKey)
        userRef.child("castv").get().addOnSuccessListener { snapshot ->
            val puntosActuales = (snapshot.value as? Number)?.toInt() ?: 0
            if (puntosActuales >= costo) {
                val nuevosPuntos = puntosActuales - costo
                userRef.child("castv").setValue(nuevosPuntos).addOnSuccessListener {
                    val emailOriginal = correoKey.replace("_", ".")
                    CastvHelper.registrarConsumo(emailOriginal, tituloPelicula, costo)
                    onComplete(true)
                }.addOnFailureListener { onComplete(false) }
            } else {
                onComplete(false)
            }
        }.addOnFailureListener { onComplete(false) }
    }

    private fun irAlReproductorDirecto(modelo: Modelo) {
        val tituloBase = modelo.title
        val fecha = modelo.releaseDate ?: ""
        val tituloConFecha = if (fecha.isNotEmpty()) "$tituloBase ($fecha)" else tituloBase

        val intent = Intent(this, PlayerPeliculas::class.java).apply {
            putExtra("EXTRA_STREAM_URL", modelo.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", tituloConFecha)
            putExtra("EXTRA_MOVIE_CASTV", modelo.castv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", modelo.imageUrl)
            putExtra("EXTRA_COUNTDOWN", modelo.countdownMinutes)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    private fun volverAlContenido() {
        CastvHelper.regresarAPeliculas(this)
    }

    private fun calcularSiContadorEstaActivo(modelo: Modelo): Boolean {
        if (modelo.countdownMinutes <= 0) return false
        val createdAtMillis = if (modelo.createdAt in 1..999999999999L) modelo.createdAt * 1000 else modelo.createdAt
        if (createdAtMillis == 0L) return false

        val countdownDurationMillis = TimeUnit.MINUTES.toMillis(modelo.countdownMinutes.toLong())
        val timeElapsed = System.currentTimeMillis() - createdAtMillis
        return (countdownDurationMillis - timeElapsed) > 0
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

// ======================== PANTALLA PRINCIPAL TV SIN SCROLL ========================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaDetalleTv(
    titulo: String,
    fecha: String,
    calificacion: String,
    sinopsis: String,
    infoAdicional: String,
    posterUrl: String,
    backdropUrl: String,
    cartelera: List<Modelo>,
    peliculaActualId: String,
    btnReproducirText: String,
    isVerificando: Boolean,
    onTrailerClick: () -> Unit,
    onReproducirClick: () -> Unit,
    onCarteleraItemFocused: (Modelo) -> Unit
) {
    val focusRequesterInicial = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(260)
        try {
            focusRequesterInicial.requestFocus()
        } catch (_: Exception) {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = backdropUrl,
            animationSpec = tween(500),
            label = "backdropCrossfade"
        ) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99000000),
                            Color(0xCC050B14),
                            Color(0xF5050B14)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 42.dp, vertical = 26.dp)
        ) {
            // SECCIÓN SUPERIOR: PÓSTER FIJO + DATOS + BOTONES (Carga estable de una)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 🟢 PÓSTER CON MEDIDA FIJA: Evita que parpadee o cambie de tamaño al cargar
                Crossfade(
                    targetState = posterUrl,
                    animationSpec = tween(350),
                    label = "posterCrossfade",
                    modifier = Modifier
                        .width(210.dp)
                        .height(305.dp)
                ) { url ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = titulo,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .border(3.dp, DoradoPrincipal, RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(36.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = titulo,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                delayMillis = 1000,
                                velocity = 40.dp
                            )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fecha,
                            color = Color(0xFFE2E8F0),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            text = calificacion,
                            color = DoradoBrillante,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(20.dp))
                        Text(
                            text = infoAdicional,
                            color = Color(0xFF94A3B8),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = sinopsis,
                        color = Color(0xFFCBD5E1),
                        fontSize = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 23.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        BotonTvDoradoPremium(
                            texto = if (isVerificando) "Verificando..." else btnReproducirText,
                            icono = "▶",
                            modifier = Modifier.focusRequester(focusRequesterInicial),
                            enabled = !isVerificando,
                            onClick = onReproducirClick
                        )

                        BotonTvDoradoPremium(
                            texto = "Ver Tráiler",
                            icono = "🎬",
                            onClick = onTrailerClick
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 🟢 SECCIÓN INFERIOR: CARTELERA QUE ARRANCA ALINEADA Y SE CENTRA AL NAVEGAR
            if (cartelera.isNotEmpty()) {
                val carteleraListState = rememberLazyListState()
                val scope = rememberCoroutineScope()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    Text(
                        text = "MÁS EN CARTELERA (Navega con las flechas para previsualizar)",
                        color = DoradoBrillante,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        state = carteleraListState,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        // 🟢 Padding normal: Arranca a la izquierda limpia (42.dp) y deja espacio para centrar al final (350.dp)
                        contentPadding = PaddingValues(start = 0.dp, end = 350.dp)
                    ) {
                        itemsIndexed(cartelera, key = { _, movie -> movie.id }) { index, movie ->
                            CarteleraItemTvDorado(
                                movie = movie,
                                isMovieSelected = movie.id == peliculaActualId,
                                onFocused = {
                                    onCarteleraItemFocused(movie)

                                    // 🟢 CENTRADO INTELIGENTE: Desplaza el ítem suavemente hacia el centro de la pantalla
                                    scope.launch {
                                        delay(40)
                                        val visibleItems = carteleraListState.layoutInfo.visibleItemsInfo
                                        val currentItem = visibleItems.find { it.index == index }
                                        if (currentItem != null) {
                                            val viewportWidth = carteleraListState.layoutInfo.viewportSize.width
                                            val itemCenter = currentItem.offset + (currentItem.size / 2)
                                            val targetCenter = viewportWidth / 2
                                            val scrollDelta = itemCenter - targetCenter
                                            if (abs(scrollDelta) > 10) {
                                                carteleraListState.animateScrollBy(
                                                    value = scrollDelta.toFloat(),
                                                    animationSpec = tween(durationMillis = 280)
                                                )
                                            }
                                        }
                                    }
                                },
                                onEnterClick = {
                                    onCarteleraItemFocused(movie)
                                    focusRequesterInicial.requestFocus()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// 🟢 BOTÓN PREMIUM CON RESPLANDOR DORADO Y ENFOQUE MARCADO
@Composable
fun BotonTvDoradoPremium(
    texto: String,
    icono: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1f,
        animationSpec = tween(150),
        label = "btnScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 24.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                spotColor = DoradoBrillante
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isFocused) DegradadoDoradoPremiun else Brush.horizontalGradient(
                    listOf(Color(0xFF1E293B), Color(0xFF131C2E))
                )
            )
            .border(
                width = if (isFocused) 3.5.dp else 1.5.dp,
                color = if (isFocused) Color.White else DoradoPrincipal.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
            }
            .focusable(enabled = enabled, interactionSource = interactionSource)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                onClick()
            }
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icono.isNotEmpty()) {
                Text(
                    text = icono,
                    color = if (isFocused) Color.Black else DoradoBrillante,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Text(
                text = texto,
                color = if (isFocused) Color.Black else Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp
            )
        }
    }
}

// 🟢 CARTELERA ITEM: TAMAÑO FIJO Y ESTABLE DESDE EL INICIO (SIN DEFORMACIONES)
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CarteleraItemTvDorado(
    movie: Modelo,
    isMovieSelected: Boolean,
    onFocused: () -> Unit,
    onEnterClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    // 🟢 Solo escala cuando el control remoto lo enfoca (evita que la primera nazca agrandada)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.12f else 1f,
        animationSpec = tween(150),
        label = "carteleraScale"
    )

    Column(
        modifier = Modifier
            .width(115.dp)
            .height(160.dp) // 🟢 Medida fija para que todas carguen idénticas y ajustadas de una
            .scale(scale)
            .shadow(
                elevation = if (isFocused) 20.dp else 0.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = DoradoBrillante
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            // El borde dorado marca la película activa o enfocada sin alterar su tamaño
            .border(
                width = if (isFocused) 3.5.dp else if (isMovieSelected) 2.dp else 1.dp,
                color = if (isFocused) DoradoBrillante else if (isMovieSelected) DoradoPrincipal else Color(0x33C5A059),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocused()
                }
            }
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                onEnterClick()
            }
    ) {
        AsyncImage(
            model = movie.imageUrl,
            contentDescription = movie.title,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentScale = ContentScale.Crop
        )

        // Franja inferior con Marquee
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFocused) {
                        Modifier.background(DegradadoDoradoPremiun)
                    } else {
                        Modifier.background(Color(0xFF0F172A))
                    }
                )
                .padding(vertical = 4.dp, horizontal = 6.dp)
        ) {
            val marqueeMod = if (isFocused) {
                Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    delayMillis = 400,
                    velocity = 35.dp
                )
            } else {
                Modifier
            }

            Text(
                text = movie.title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(marqueeMod)
            )
        }
    }
}

// ======================== DIÁLOGOS CON DORADO PREMIUM ========================

@Composable
fun DialogConfirmarAlquiler(
    modelo: Modelo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val tituloBase = modelo.title
    val fecha = modelo.releaseDate ?: ""
    val tituloUsado = if (fecha.isNotEmpty()) "$tituloBase ($fecha)" else tituloBase

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A122A)),
            border = BorderStroke(3.dp, DoradoPrincipal),
            modifier = Modifier
                .width(580.dp)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎬 Confirmar Alquiler",
                    color = DoradoBrillante,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Película: $tituloUsado",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Costo: ${modelo.castv} CasTV",
                    color = Color(0xFF00E676),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Contenido disponible.\n¿Deseas alquilar y ver la película ahora?",
                    color = Color(0xFFE0E0E0),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BotonTvDoradoPremium(
                        texto = "Cancelar",
                        onClick = onDismiss
                    )

                    BotonTvDoradoPremium(
                        texto = "Ver Ahora",
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}

@Composable
fun DialogEnlaceRoto(
    modelo: Modelo,
    nombreUsuario: String,
    saldoUsuario: String,
    isVerificandoEstreno: Boolean,
    onDismiss: () -> Unit,
    onMasInfoClick: () -> Unit,
    onAlquilarClick: () -> Unit
) {
    val tituloBase = modelo.title
    val fecha = modelo.releaseDate ?: ""
    val tituloUsado = if (fecha.isNotEmpty()) "$tituloBase ($fecha)" else tituloBase

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            border = BorderStroke(3.dp, DoradoPrincipal),
            modifier = Modifier
                .width(620.dp)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(26.dp)
            ) {
                Text(
                    text = "🎬 Película: $tituloUsado",
                    color = DoradoBrillante,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "🪙 Costo: ${modelo.castv} CasTV",
                    color = Color(0xFF4ADE80),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "👤 Usuario: $nombreUsuario   |   💎 Saldo: $saldoUsuario",
                    color = Color(0xFFCBD5E1),
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color(0xFF444444), thickness = 1.5.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "⚠️ RESTRICCIONES IMPORTANTES\n• Recuerda mantener saldo en tu cuenta para tus próximos alquileres.",
                    color = Color(0xFFF87171),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Más información aquí",
                    color = Color(0xFFF87171),
                    fontSize = 15.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { onMasInfoClick() }
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    BotonTvDoradoPremium(
                        texto = "Volver",
                        onClick = onDismiss
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    BotonTvDoradoPremium(
                        texto = if (isVerificandoEstreno) "Verificando..." else "Alquilar Película",
                        enabled = !isVerificandoEstreno,
                        onClick = onAlquilarClick
                    )
                }
            }
        }
    }
}