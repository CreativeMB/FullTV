package com.creativem.fulltv.principal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.text.HtmlCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.creativem.fulltv.BuildConfig
import com.creativem.fulltv.R
import com.creativem.fulltv.peliculas.PlayerPeliculas
import com.creativem.fulltv.peliculasvalidas.Validacioneslista
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

val ColorDoradoPerfil = Color(0xFFC5A059)
val ColorDoradoBrillante = Color(0xFFFFD56B)
val ColorFondoOscuroPerfil = Color(0xFF07090E)
val ColorCardPerfil = Color(0xFF10141F)

class Perfil : AppCompatActivity() {

    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }
    private val auth by lazy { FirebaseAuth.getInstance() }

    var isGuiaAbierta: () -> Boolean = { false }
    var cerrarGuiaAction: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                PerfilScreenDashboard(
                    activity = this,
                    auth = auth,
                    databaseRef = databaseRef,
                    versionApp = BuildConfig.VERSION_NAME,
                    onRegresar = { CastvHelper.regresarAPeliculas(this) },
                    onReproducir = { item -> abrirReproductor(item) }
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

    private fun abrirReproductor(item: Modelo.AlquilerItem) {
        val intent = Intent(this, PlayerPeliculas::class.java).apply {
            putExtra("EXTRA_STREAM_URL", item.movie.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", item.movie.title)
            putExtra("EXTRA_MOVIE_IMAGE_URL", item.movie.imageUrl)
            putExtra("EXTRA_COUNTDOWN", item.countdownMinutes)
            putExtra("EXTRA_CREATED_AT", item.createdAt)
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        CastvHelper.limpiarListeners()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isGuiaAbierta()) {
                cerrarGuiaAction?.invoke()
                return true
            }
            CastvHelper.regresarAPeliculas(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

// =========================================================================
// 🟢 PANTALLA DASHBOARD PRINCIPAL
// =========================================================================
@Composable
fun PerfilScreenDashboard(
    activity: Perfil,
    auth: FirebaseAuth,
    databaseRef: DatabaseReference,
    versionApp: String,
    onRegresar: () -> Unit,
    onReproducir: (Modelo.AlquilerItem) -> Unit
) {
    val config = LocalConfiguration.current
    val esTv = config.screenWidthDp > 600

    var nombreUsuario by remember { mutableStateOf("Usuario") }
    var fotoUsuarioUrl by remember { mutableStateOf<String?>(null) }
    var saldoCastv by remember { mutableStateOf("0") }
    var estaCargando by remember { mutableStateOf(true) }

    var totalPeliculasValidadas by remember { mutableIntStateOf(Validacioneslista.obtenerPeliculasValidas().size) }
    var usuariosOnline by remember { mutableIntStateOf(0) }
    var usuariosOffline by remember { mutableIntStateOf(0) }

    var horaEnVivo by remember { mutableStateOf("") }
    var fechaCompletaEnVivo by remember { mutableStateOf("") }

    var mostrarGuiaPantallaCompleta by remember { mutableStateOf(false) }

    DisposableEffect(mostrarGuiaPantallaCompleta) {
        activity.isGuiaAbierta = { mostrarGuiaPantallaCompleta }
        activity.cerrarGuiaAction = { mostrarGuiaPantallaCompleta = false }
        onDispose {
            activity.isGuiaAbierta = { false }
            activity.cerrarGuiaAction = null
        }
    }

    val alquileresVigentes = remember { mutableStateListOf<Modelo.AlquilerItem>() }
    val cachePeliculas = remember { mutableMapOf<String, Modelo>() }

    val user = auth.currentUser
    val email = user?.email
    val correoKey = remember(email) { email?.replace(".", "_")?.replace("@", "_") ?: "" }

    val backButtonFocusRequester = remember { FocusRequester() }

    BackHandler {
        if (mostrarGuiaPantallaCompleta) {
            mostrarGuiaPantallaCompleta = false
        } else {
            onRegresar()
        }
    }

    fun normalizarClave(texto: String): String {
        return texto.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
    }

    LaunchedEffect(Unit) {
        delay(200)
        try {
            backButtonFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                if (!Validacioneslista.yaCargado()) {
                    Validacioneslista.cargarPeliculas()
                }
            } catch (_: Exception) {}
        }
        totalPeliculasValidadas = Validacioneslista.obtenerPeliculasValidas().size
    }

    LaunchedEffect(Unit) {
        val formatoHora = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val formatoFechaCompleta = SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", Locale("es", "ES"))

        while (isActive) {
            val ahora = Date()
            horaEnVivo = formatoHora.format(ahora).uppercase(Locale.getDefault())
            fechaCompletaEnVivo = formatoFechaCompleta.format(ahora).replaceFirstChar { it.uppercase() }
            val cant = Validacioneslista.obtenerPeliculasValidas().size
            if (cant > 0 && cant != totalPeliculasValidadas) {
                totalPeliculasValidadas = cant
            }
            delay(1000)
        }
    }

    // Variable auxiliar para sumar los usuarios que realmente estén conectados en Firebase
    var baseConectados by remember { mutableIntStateOf(1) }

    // =========================================================================
    // 🧠 SIMULADOR PRECISO CON TOPE ESTRICTO (ONLINE NUNCA SUPERA A LA COMUNIDAD)
    // =========================================================================
    LaunchedEffect(usuariosOffline) {
        fun calcularRangoSegunComunidad(): Pair<Int, Int> {
            val cal = java.util.Calendar.getInstance()
            val hora = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val diaSemana = cal.get(java.util.Calendar.DAY_OF_WEEK)

            val esFinDeSemana = diaSemana == java.util.Calendar.FRIDAY ||
                    diaSemana == java.util.Calendar.SATURDAY ||
                    diaSemana == java.util.Calendar.SUNDAY

            // Tope de la comunidad (usa el valor real o un mínimo si Firebase aún no responde)
            val tope = if (usuariosOffline > 0) usuariosOffline else 100

            // Porcentajes reales de concurrencia sobre la comunidad
            val (pctMin, pctMax) = when (hora) {
                in 1..6 -> 0.08f to 0.16f     // 🌙 Madrugada (8% a 16%)
                in 7..11 -> 0.18f to 0.28f    // ☕ Mañana (18% a 28%)
                in 12..17 -> 0.30f to 0.44f   // ⛅ Tarde (30% a 44%)
                in 18..23 -> 0.48f to 0.65f   // 🎬 Prime Time Noche (48% a 65%)
                else -> 0.20f to 0.30f        // 🕛 Medianoche (20% a 30%)
            }

            val boost = if (esFinDeSemana) 1.10f else 1.0f

            // Límites calculados respetando que el máximo nunca supere el 75% de la comunidad
            val minCalculado = (tope * pctMin * boost).toInt().coerceAtLeast(1)
            val techoMaximo = (tope * 0.75f).toInt().coerceAtLeast(minCalculado)
            val maxCalculado = (tope * pctMax * boost).toInt().coerceIn(minCalculado, techoMaximo)

            return Pair(minCalculado, maxCalculado)
        }

        val (minInicial, maxInicial) = calcularRangoSegunComunidad()
        var simulados = kotlin.random.Random.nextInt(minInicial, maxInicial + 1)
        val topeBase = if (usuariosOffline > 0) usuariosOffline else 100

        // Asignación inicial: tope estricto de seguridad
        usuariosOnline = (simulados + baseConectados).coerceIn(1, (topeBase - 2).coerceAtLeast(1))

        while (true) {
            delay(kotlin.random.Random.nextLong(60_000L, 130_000L))

            val (rMin, rMax) = calcularRangoSegunComunidad()
            val topeActual = if (usuariosOffline > 0) usuariosOffline else 100

            if (simulados < rMin) {
                simulados += kotlin.random.Random.nextInt(1, 3)
            } else if (simulados > rMax) {
                simulados -= kotlin.random.Random.nextInt(1, 3)
            } else {
                simulados += kotlin.random.Random.nextInt(-1, 2)
            }

            simulados = simulados.coerceIn(rMin, rMax)

            // 🟢 GARANTÍA MATEMÁTICA: usuariosOnline JAMÁS superará a usuariosOffline
            usuariosOnline = (simulados + baseConectados).coerceIn(1, (topeActual - 2).coerceAtLeast(1))
        }
    }

    // =========================================================================
    // 2. LISTENER DE FIREBASE (PERFIL Y COMUNIDAD DE ~100)
    // =========================================================================
    DisposableEffect(correoKey) {
        fotoUsuarioUrl = user?.photoUrl?.toString()
        val refUsuarios = databaseRef.child("usuarios")

        val listenerUsuarios = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var conectadosReales = 0
                var desconectadosReales = 0

                for (child in snapshot.children) {
                    val isOnline = child.child("enlinea").value as? Boolean ?: false
                    if (isOnline) conectadosReales++ else desconectadosReales++
                }

                // Guardamos los reales para sumarlos a la fluctuación
                baseConectados = conectadosReales

                // Base fija de 1,000 usuarios falsos + los que existan en Firebase
                val baseComunidad = 100
                usuariosOffline = baseComunidad + desconectadosReales

                // Carga de datos de perfil del usuario logueado
                if (correoKey.isNotEmpty()) {
                    val miUser = snapshot.child(correoKey)
                    val nombreDb = miUser.child("nombre").value?.toString()
                    val fotoDb = miUser.child("foto").value?.toString() ?: miUser.child("photoUrl").value?.toString()

                    nombreUsuario = when {
                        !nombreDb.isNullOrBlank() -> nombreDb
                        !user?.displayName.isNullOrBlank() -> user?.displayName!!
                        email == "invitado@fulltv.com" -> "Invitado"
                        else -> "Usuario"
                    }

                    if (!fotoDb.isNullOrBlank()) {
                        fotoUsuarioUrl = fotoDb
                    }
                    saldoCastv = miUser.child("castv").value?.toString() ?: "0"
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        refUsuarios.addValueEventListener(listenerUsuarios)
        onDispose { refUsuarios.removeEventListener(listenerUsuarios) }
    }
    LaunchedEffect(correoKey) {
        databaseRef.child("movies").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(moviesSnapshot: DataSnapshot) {
                cachePeliculas.clear()
                for (child in moviesSnapshot.children) {
                    val movie = child.getValue(Modelo::class.java) ?: continue
                    movie.id = child.key ?: ""
                    cachePeliculas[normalizarClave(movie.title)] = movie
                }

                if (correoKey.isNotEmpty()) {
                    databaseRef.child("usuarios").child(correoKey).child("alquileres")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(rentalsSnapshot: DataSnapshot) {
                                alquileresVigentes.clear()
                                val ahora = System.currentTimeMillis()

                                if (rentalsSnapshot.exists()) {
                                    for (child in rentalsSnapshot.children) {
                                        val tituloKey = child.key ?: continue
                                        val createdAtRaw = (child.child("createdAt").value as? Number)?.toLong() ?: 0L
                                        val countdownMinutes = (child.child("countdownMinutes").value as? Number)?.toInt() ?: 0

                                        val createdAtMillis = if (createdAtRaw in 1..999999999999L) createdAtRaw * 1000 else createdAtRaw
                                        val tiempoTranscurrido = ahora - createdAtMillis

                                        if (tiempoTranscurrido >= 0) {
                                            val durationMillis = TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                                            val tiempoRestante = durationMillis - tiempoTranscurrido

                                            if (tiempoRestante > 0) {
                                                val movie = cachePeliculas[tituloKey]
                                                if (movie != null) {
                                                    alquileresVigentes.add(
                                                        Modelo.AlquilerItem(
                                                            movie = movie,
                                                            createdAt = createdAtMillis,
                                                            countdownMinutes = countdownMinutes
                                                        )
                                                    )
                                                }
                                            } else {
                                                child.ref.removeValue()
                                            }
                                        }
                                    }
                                }
                                estaCargando = false
                            }

                            override fun onCancelled(error: DatabaseError) { estaCargando = false }
                        })
                } else {
                    estaCargando = false
                }
            }
            override fun onCancelled(error: DatabaseError) { estaCargando = false }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorFondoOscuroPerfil)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (esTv) 20.dp else 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // =================================================================
            // 🟢 1. BARRA SUPERIOR DESPEJADA Y ELEGANTE
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (esTv) 58.dp else 52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorCardPerfil)
                    .border(1.dp, Color(0xFF1E2638), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonCircularCompacto(
                    modifier = Modifier.focusRequester(backButtonFocusRequester),
                    onClick = onRegresar
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar",
                        tint = ColorDoradoPerfil,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(if (esTv) 40.dp else 36.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, ColorDoradoPerfil, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!fotoUsuarioUrl.isNullOrBlank()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx -> ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                            update = { imageView ->
                                Glide.with(imageView.context)
                                    .load(fotoUsuarioUrl)
                                    .placeholder(R.drawable.cine)
                                    .error(R.drawable.cine)
                                    .circleCrop()
                                    .into(imageView)
                            }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Foto Usuario",
                            tint = Color.LightGray,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.widthIn(max = 240.dp)) {
                    Text(
                        text = nombreUsuario,
                        color = Color.White,
                        fontSize = if (esTv) 25.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v$versionApp • $email",
                        color = Color(0xFF94A3B8),
                        fontSize = if (esTv) 11.5.sp else 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = horaEnVivo,
                        color = ColorDoradoBrillante,
                        fontSize = if (esTv) 25.5.sp else 13.5.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = fechaCompletaEnVivo,
                        color = Color.White,
                        fontSize = if (esTv) 16.sp else 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF261D09))
                        .border(1.2.dp, ColorDoradoPerfil, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💰 $saldoCastv CasTV",
                        color = ColorDoradoBrillante,
                        fontSize = if (esTv) 20.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // =================================================================
            // 🟢 2. CENTRO: QRs GIGANTES + NÚMEROS DESTACADOS + BOTÓN GUÍA
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // QRs GIGANTES LIMPIOS (128dp)
                TarjetaQRsGigantesLimpios(
                    esTv = esTv,
                    modifier = Modifier
                        .weight(1.22f)
                        .fillMaxHeight()
                )

                // Números Grandes + Botón Guía Compacto
                Column(
                    modifier = Modifier
                        .weight(0.78f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    TarjetaEstadisticasNumerosGrandes(
                        esTv = esTv,
                        totalPeliculasValidadas = totalPeliculasValidadas,
                        usuariosOnline = usuariosOnline,
                        usuariosOffline = usuariosOffline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    BotonAccesoGuiaCompacto(
                        esTv = esTv,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (esTv) 48.dp else 44.dp),
                        onClick = { mostrarGuiaPantallaCompleta = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // =================================================================
            // 🟢 3. ABAJO: ALQUILERES ACTIVOS ESPACIOSOS (225.dp)
            // =================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (esTv) 225.dp else 190.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorCardPerfil)
                    .border(1.dp, Color(0xFF1E2638), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                if (estaCargando) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ColorDoradoPerfil, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    }
                } else if (alquileresVigentes.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp, start = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🍿 TUS ALQUILERES ACTIVOS",
                                color = ColorDoradoBrillante,
                                fontSize = if (esTv) 14.sp else 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${alquileresVigentes.size} disponibles para reproducir ahora",
                                color = Color(0xFF94A3B8),
                                fontSize = if (esTv) 12.sp else 10.5.sp
                            )
                        }

                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(alquileresVigentes, key = { it.movie.id }) { item ->
                                TarjetaAlquilerPosterVertical(
                                    item = item,
                                    esTv = esTv,
                                    onClick = { onReproducir(item) },
                                    onExpirado = {
                                        val clave = normalizarClave(item.movie.title)
                                        databaseRef.child("usuarios").child(correoKey)
                                            .child("alquileres").child(clave).removeValue()
                                        alquileresVigentes.remove(item)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "🍿", fontSize = 38.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Aquí saldrán tus películas alquiladas activas",
                                color = Color.White,
                                fontSize = if (esTv) 15.5.sp else 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Canjea cualquier película con tus créditos CasTV en la cartelera y podrás verla inmediatamente aquí.",
                                color = Color(0xFF94A3B8),
                                fontSize = if (esTv) 12.sp else 11.sp
                            )
                        }
                    }
                }
            }
        }

        // =================================================================
        // 🟢 GUÍA EN PANTALLA COMPLETA NATIVA (LIMPIA, SIN RECUADROS EXTRA)
        // =================================================================
        AnimatedVisibility(
            visible = mostrarGuiaPantallaCompleta,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.zIndex(150f)
        ) {
            OverlayGuiaCineParchePantallaCompleta(
                onDismiss = { mostrarGuiaPantallaCompleta = false }
            )
        }
    }
}

// =========================================================================
// 🟢 TARJETA DE QRs GIGANTES (128.dp)
// =========================================================================
@Composable
fun TarjetaQRsGigantesLimpios(
    esTv: Boolean,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFocused) Color(0xFF161E2E) else ColorCardPerfil
        ),
        modifier = modifier
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) ColorDoradoPerfil else Color(0xFF1E2638),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ENCABEZADO
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📲 COMUNIDAD Y REDES OFICIALES",
                    color = ColorDoradoPerfil,
                    fontSize = if (esTv) 13.5.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Text(
                    text = "📷 Escanea con tu móvil",
                    color = ColorDoradoBrillante,
                    fontSize = if (esTv) 11.sp else 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // CONTENIDO: DISTRIBUCIÓN RESPONSIVA AUTOMÁTICA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. QR Canal Oficial
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0E17))
                        .border(1.2.dp, Color(0xFF1E2638), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Contenedor responsivo: aprovecha todo el alto sobrante y mantiene el QR cuadrado
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.canal),
                            contentDescription = "Canal Oficial",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Canal Oficial",
                        color = Color.White,
                        fontSize = if (esTv) 14.sp else 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }

                // 2. QR Grupo Parche
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0E17))
                        .border(1.2.dp, Color(0xFF1E2638), RoundedCornerShape(10.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Contenedor responsivo: aprovecha todo el alto sobrante y mantiene el QR cuadrado
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.grupo),
                            contentDescription = "Grupo Parche",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .padding(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Grupo Parche",
                        color = Color.White,
                        fontSize = if (esTv) 14.sp else 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// =========================================================================
// 🟢 TARJETA DE ESTADÍSTICAS ENFOCADA EN NÚMEROS GIGANTES
// =========================================================================
@Composable
fun TarjetaEstadisticasNumerosGrandes(
    esTv: Boolean,
    totalPeliculasValidadas: Int,
    usuariosOnline: Int,
    usuariosOffline: Int,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF161E2E) else ColorCardPerfil),
        modifier = modifier
            .scale(if (isFocused) 1.02f else 1.0f)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) ColorDoradoPerfil else Color(0xFF1E2638),
                shape = RoundedCornerShape(12.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // SECCIÓN SUPERIOR: ESTRENO DESTACADO GIGANTE Y BIEN ALINEADO
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$totalPeliculasValidadas",
                    color = ColorDoradoBrillante,
                    fontSize = if (esTv) 44.sp else 26.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = if (esTv) 44.sp else 26.sp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "PELÍCULAS ALQUILAR YA ",
                        color = Color.White,
                        fontSize = if (esTv) 18.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "Pestaña ACTIVAR ",
                        color = Color(0xFF34D399),
                        fontSize = if (esTv) 18.sp else 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

            // SECCIÓN INFERIOR: DISTRIBUCIÓN 50/50 CON CONTENEDORES EQUITATIVOS (SIN HUECOS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bloque En Vivo (Ocupa exactamente la mitad)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1410B981))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$usuariosOnline",
                        color = Color(0xFF34D399),
                        fontSize = if (esTv) 44.sp else 19.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = if (esTv) 44.sp else 19.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "🟢",
                            color = Color.White,
                            fontSize = if (esTv) 11.5.sp else 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Online",
                            color = Color(0xFF94A3B8),
                            fontSize = if (esTv) 9.5.sp else 8.5.sp
                        )
                    }
                }

                // Bloque Comunidad (Ocupa exactamente la otra mitad)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x1B0277BD))
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$usuariosOffline",
                        color = Color(0xFF0277BD),
                        fontSize = if (esTv) 44.sp else 19.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = if (esTv) 44.sp else 19.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "🔵",
                            color = Color.White,
                            fontSize = if (esTv) 11.5.sp else 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "comunidad",
                            color = Color(0xFF94A3B8),
                            fontSize = if (esTv) 9.5.sp else 8.5.sp
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 🟢 BOTÓN COMPACTO DE ACCESO A GUÍA Y REGLAS
// =========================================================================
@Composable
fun BotonAccesoGuiaCompacto(
    esTv: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .scale(if (isFocused) 1.03f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF261D09) else ColorCardPerfil)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) ColorDoradoBrillante else Color(0xFF1E2638),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(text = "📖", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "GUÍA Y REGLAS (PULSA OK)",
                color = if (isFocused) ColorDoradoBrillante else Color.White,
                fontSize = if (esTv) 12.5.sp else 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

// =========================================================================
// 🟢 PÓSTER VERTICAL DE ALQUILER (225.dp ESPACIOSO)
// =========================================================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TarjetaAlquilerPosterVertical(
    item: Modelo.AlquilerItem,
    esTv: Boolean,
    onClick: () -> Unit,
    onExpirado: () -> Unit
) {
    val durationMillis = remember(item.movie.id, item.countdownMinutes) {
        TimeUnit.MINUTES.toMillis(item.countdownMinutes.toLong())
    }
    val createdAtMillis = remember(item.movie.id, item.createdAt) {
        if (item.createdAt in 1..999999999999L) item.createdAt * 1000 else item.createdAt
    }
    var millisRestantes by remember(item.movie.id, createdAtMillis, item.countdownMinutes) {
        val transcurrido = System.currentTimeMillis() - createdAtMillis
        mutableLongStateOf(maxOf(0L, durationMillis - transcurrido))
    }

    LaunchedEffect(item.movie.id, createdAtMillis) {
        while (millisRestantes > 0) {
            delay(1000)
            val transcurrido = System.currentTimeMillis() - createdAtMillis
            val rest = durationMillis - transcurrido
            if (rest <= 0) {
                millisRestantes = 0L
                onExpirado()
                break
            } else {
                millisRestantes = rest
            }
        }
    }

    val h = TimeUnit.MILLISECONDS.toHours(millisRestantes)
    val m = TimeUnit.MILLISECONDS.toMinutes(millisRestantes) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(millisRestantes) % 60
    val contadorTexto = String.format("▶ %02d:%02d:%02d", h, m, s)

    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(if (esTv) 120.dp else 100.dp)
            .fillMaxHeight()
            .scale(if (isFocused) 1.05f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF141923))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) ColorDoradoBrillante else Color(0xFF262E3D),
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ImageView(ctx).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        setImageResource(R.drawable.pelifondo)
                    }
                },
                update = { imageView ->
                    if (!item.movie.imageUrl.isNullOrBlank()) {
                        Glide.with(imageView.context)
                            .load(item.movie.imageUrl)
                            .placeholder(R.drawable.pelifondo)
                            .error(R.drawable.pelifondo)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(imageView)
                    }
                }
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
                    text = contadorTexto,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        val marqueeMod = if (isFocused) {
            Modifier.basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 600, velocity = 35.dp)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) ColorDoradoPerfil else Color(0xFF0C1017))
                .padding(vertical = 4.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.movie.title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = if (isFocused) TextOverflow.Clip else TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().then(marqueeMod)
            )
        }
    }
}

// =========================================================================
// 🟢 OVERLAY GUÍA COMPLETA OFICIAL CINEPARCHE (LIMPIA, SIN RECUADROS PESADOS)
// =========================================================================
@Composable
fun OverlayGuiaCineParchePantallaCompleta(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val primerItemRequester = remember { FocusRequester() }

    // Foco inicial automático para el control remoto
    LaunchedEffect(Unit) {
        delay(150)
        try { primerItemRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5060911)) // Fondo cinema ultra oscuro
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        // Tarjeta principal limpia y elegante (sin borde naranja fijo)
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1523)),
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = Color(0x1FFFFFFF), // Borde tenue premium tipo cristal
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 20.dp)
            ) {
                // ENCABEZADO SUPERIOR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 GUÍA Y POLÍTICAS DE CONTENIDO",
                        color = ColorDoradoBrillante,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF192233))
                            .border(1.dp, Color(0x33FFD56B), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "CineParche OFICIAL",
                            color = ColorDoradoBrillante,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CONTENIDO LIMPIO: EL RECUADRO SOLO APARECE CON EL FOCUS DEL CONTROL
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // SECCIÓN INICIAL: RESUMEN DE NAVEGACIÓN
                    ItemSeccionGuiaTvLimpio(
                        icono = "🧭",
                        titulo = "GUÍA RÁPIDA DE OPCIONES DEL MENÚ",
                        cuerpo = "• <b>📺 TV:</b> Canales en vivo 100% gratuitos de listas abiertas.<br>" +
                                "• <b>🎬 ALQUILA:</b> Explora el catálogo general, ve tráilers y programa tus alquileres.<br>" +
                                "• <b>⚡ ACTIVAR:</b> Películas listas para ver al instante con contador activo.<br>" +
                                "• <b>📦 PEDIR:</b> Solicita películas de la red o personalizadas por Nombre y Año.<br>" +
                                "• <b>🔍 BUSCAR:</b> Buscador rápido de contenido en nuestra red.<br>" +
                                "• <b>💳 PAQUETE:</b> Selecciona tu PAQUETE y recarga saldo CasTV por código QR.<br>" +
                                "• <b>👤 PERFIL:</b> Revisa tu saldo disponible, películas activas y esta guía.<br>" +
                                "• <b>🚪 CERRAR:</b> Cambia de cuenta si usas varios perfiles en el hogar.",
                        modifier = Modifier.focusRequester(primerItemRequester)
                    )

                    // SECCIÓN 1: PROCESO DE RECARGA Y PAQUETES
                    ItemSeccionGuiaTvLimpio(
                        icono = "💳",
                        titulo = "1. CÓMO RECARGAR SALDO CASTV",
                        cuerpo = "<b>Pasos para recargar:</b><br>" +
                                "1. Ve a <b>'PAQUETES'</b> en el menú principal y elige tu PAQUETE.<br>" +
                                "2. Escanea con la cámara de tu móvil el <b>código QR</b> en pantalla.<br>" +
                                "3. Realiza el pago y <b>sube el comprobante</b> desde tu celular.<br>" +
                                "4. Validamos tu pago y <b>activamos tu saldo CasTV al instante</b>.<br><br>" +
                                "<b>Rendimiento de los paquetes (saldo acumulable sin vencimiento):</b><br>" +
                                "• <b>Bronce (50 CasTV):</b> Básico.<br>" +
                                "• <b>Plata (120 CasTV):</b> Más Popular.<br>" +
                                "• <b>Oro VIP (250 CasTV):</b> VIP."
                    )

                    // SECCIÓN 2: TIEMPOS DE ALQUILER Y CONTADOR REGRESIVO
                    ItemSeccionGuiaTvLimpio(
                        icono = "⏱️",
                        titulo = "2. MODALIDADES DE ALQUILER",
                        cuerpo = "• <b>⚡ ACTIVAR (Ver al Instante):</b> Para películas disponibles ya. Al activar, se inicia el contador de <b>5 horas continuas</b> para disfrutar la reproducción.<br>" +
                                "• <b>📅 ALQUILAR (Programar Fecha):</b> Agenda la película para la fecha y hora que prefieras. <b>El contador de 5 horas NO empieza a correr</b> hasta llegada la fecha programada.<br>" +
                                "• <b>🛡️ Cero Dobles Cobros:</b> Reproduce tu película activa cuantas veces quieras mientras el contador continúe activo en tu perfil."
                    )

                    // SECCIÓN 3: TV EN VIVO Y CINE EN CORTESÍA
                    ItemSeccionGuiaTvLimpio(
                        icono = "📺",
                        titulo = "3. TV EN VIVO GRATIS & CINE COMUNITARIO",
                        cuerpo = "• <b>TV En Vivo Gratis:</b> Más de 1,300 canales de televisión abierta sin suscripciones, pagos ni registros.<br>" +
                                "• <b>Listas IPTV de Terceros:</b> CineParche retransmite y organiza listas IPTV públicas de internet sin costo comercial.<br>" +
                                "• <b>Cine en Cortesía:</b> Disfruta de películas promocionales en rotación gratuita para toda la comunidad (señalizadas con etiqueta <b>ONLINE</b>)."
                    )

                    // SECCIÓN 4: SOLICITUDES Y SOPORTE OFICIAL
                    ItemSeccionGuiaTvLimpio(
                        icono = "📲",
                        titulo = "4. PEDIDOS DE PELÍCULAS Y SOPORTE 24/7",
                        cuerpo = "• <b>Opción 'PEDIR':</b> Si buscas una película no disponible en catálogo, solicítala con <b>Nombre y Año</b> para que la subamos a la cartelera.<br>" +
                                "• <b>Soporte Telegram:</b> Atención activa 24/7 en nuestro Canal Oficial y Grupo Parche.<br>" +
                                "• <b>Web Oficial:</b> <font color='#FFD56B'><b>https://onnline.web.app/CineParche</b></font>"
                    )

                    // SECCIÓN 5: MARCO LEGAL Y RESPONSABILIDAD
                    ItemSeccionGuiaTvLimpio(
                        icono = "⚖️",
                        titulo = "5. AVISO LEGAL Y PROPIEDAD DE CONTENIDO",
                        cuerpo = "• <b>Televisión Libre:</b> Las señales de TV abierta provienen de enlaces IPTV públicos en la red. CineParche no produce ni distribuye estas señales, solo las organiza de forma gratuita.<br>" +
                                "• <b>Alquiler por Tiempo:</b> No se cobra por la venta o propiedad del material audiovisual. Los créditos CasTV cubren exclusivamente el servicio de acceso y uso de la plataforma por tiempo limitado (contador regresivo).<br>" +
                                "• <b>Derechos de Autor:</b> CineParche no almacena ni aloja archivos multimedia en servidores propios; actúa estrictamente como un indexador y reproductor de enlaces multimedia de la red."
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // BOTONES DE ACCIÓN INFERIORES NAVEGABLES
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Sitio Web
                    var isWebFocused by remember { mutableStateOf(false) }
                    val webScale by animateFloatAsState(if (isWebFocused) 1.03f else 1.0f, label = "scaleWeb")
                    val webBorderColor by animateColorAsState(
                        if (isWebFocused) ColorDoradoBrillante else Color(0x33FFFFFF),
                        label = "colorWeb"
                    )

                    Box(
                        modifier = Modifier
                            .scale(webScale)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isWebFocused) Color(0xFF222C3D) else Color(0xFF141A26))
                            .border(width = if (isWebFocused) 2.dp else 1.dp, color = webBorderColor, shape = RoundedCornerShape(10.dp))
                            .onFocusChanged { isWebFocused = it.isFocused }
                            .focusable()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://onnline.web.app/CineParche"))
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 22.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "🌐 ABRIR ONNLINE.WEB.APP/CINEPARCHE",
                            color = if (isWebFocused) ColorDoradoBrillante else Color(0xFFDDDDDD),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Botón Volver / Entendido
                    var isCerrarFocused by remember { mutableStateOf(false) }
                    val cerrarScale by animateFloatAsState(if (isCerrarFocused) 1.04f else 1.0f, label = "scaleCerrar")
                    val cerrarBgColor by animateColorAsState(
                        if (isCerrarFocused) ColorDoradoBrillante else ColorDoradoPerfil,
                        label = "colorCerrar"
                    )

                    Box(
                        modifier = Modifier
                            .scale(cerrarScale)
                            .clip(RoundedCornerShape(10.dp))
                            .background(cerrarBgColor)
                            .border(
                                width = if (isCerrarFocused) 2.dp else 0.dp,
                                color = if (isCerrarFocused) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .onFocusChanged { isCerrarFocused = it.isFocused }
                            .focusable()
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 30.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "ENTENDIDO / VOLVER AL PERFIL",
                            color = Color(0xFF0B0E14),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

// 🟢 COMPONENTE LIMPIO SIN RECUADRO FIJO (SOLO MARCA EL FOCO CON BORDE DORADO AL PASAR)
@Composable
fun ItemSeccionGuiaTvLimpio(
    icono: String,
    titulo: String,
    cuerpo: String,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Color(0xFF161E2E) else Color.Transparent)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) ColorDoradoBrillante else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icono, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = titulo,
                    color = if (isFocused) ColorDoradoBrillante else ColorDoradoPerfil,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = HtmlCompat.fromHtml(cuerpo, HtmlCompat.FROM_HTML_MODE_LEGACY).toString(),
                color = Color(0xFFE2E8F0),
                fontSize = 14.5.sp,
                lineHeight = 19.sp
            )
        }
    }
}

// =========================================================================
// 🟢 BOTÓN CIRCULAR COMPACTO
// =========================================================================
@Composable
fun BotonCircularCompacto(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(38.dp)
            .scale(if (isFocused) 1.1f else 1.0f)
            .clip(CircleShape)
            .background(if (isFocused) Color(0xFF2B354C) else Color(0xFF161C2C))
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) ColorDoradoPerfil else Color(0xFF26324D),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}