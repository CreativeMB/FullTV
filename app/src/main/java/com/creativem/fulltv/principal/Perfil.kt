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

                usuariosOnline = maxOf(1, conectadosReales)
                usuariosOffline = desconectadosReales + 100

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
                        fontSize = if (esTv) 15.sp else 13.sp,
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
                        fontSize = if (esTv) 15.5.sp else 13.5.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = fechaCompletaEnVivo,
                        color = Color.White,
                        fontSize = if (esTv) 12.sp else 10.5.sp,
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
                        fontSize = if (esTv) 13.5.sp else 12.sp,
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
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF161E2E) else ColorCardPerfil),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📲 COMUNIDAD Y REDES OFICIALES",
                    color = ColorDoradoPerfil,
                    fontSize = if (esTv) 14.sp else 12.5.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "📷 Escanea desde tu sillón con tu móvil",
                    color = ColorDoradoBrillante,
                    fontSize = if (esTv) 11.5.sp else 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // QR Canal Oficial
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0E17))
                        .border(1.2.dp, Color(0xFF1E2638), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.canal),
                        contentDescription = "Canal Oficial",
                        modifier = Modifier
                            .size(if (esTv) 128.dp else 95.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(4.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Canal Oficial",
                            color = Color.White,
                            fontSize = if (esTv) 16.sp else 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cartelera y estrenos diarios al instante en Telegram.",
                            color = Color(0xFFCBD5E1),
                            fontSize = if (esTv) 12.sp else 10.5.sp,
                            lineHeight = if (esTv) 16.sp else 14.sp
                        )
                    }
                }

                // QR Grupo Parche
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0B0E17))
                        .border(1.2.dp, Color(0xFF1E2638), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.grupo),
                        contentDescription = "Grupo Parche",
                        modifier = Modifier
                            .size(if (esTv) 128.dp else 95.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .padding(4.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Grupo Parche",
                            color = Color.White,
                            fontSize = if (esTv) 16.sp else 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Pide tus películas favoritas y debate con la comunidad.",
                            color = Color(0xFFCBD5E1),
                            fontSize = if (esTv) 12.sp else 10.5.sp,
                            lineHeight = if (esTv) 16.sp else 14.sp
                        )
                    }
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
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.SpaceAround
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$totalPeliculasValidadas",
                    color = ColorDoradoBrillante,
                    fontSize = if (esTv) 28.sp else 22.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Estrenos Listos para Ver",
                        color = Color.White,
                        fontSize = if (esTv) 13.5.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pestaña ACTIVAR • Inmediatos",
                        color = Color(0xFF34D399),
                        fontSize = if (esTv) 11.sp else 9.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E2638), thickness = 0.8.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$usuariosOnline",
                        color = Color(0xFF34D399),
                        fontSize = if (esTv) 24.sp else 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("🟢 En Vivo", color = Color.White, fontSize = if (esTv) 12.sp else 10.5.sp, fontWeight = FontWeight.Bold)
                        Text("Online", color = Color(0xFF94A3B8), fontSize = if (esTv) 10.sp else 8.5.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$usuariosOffline",
                        color = Color(0xFFF87171),
                        fontSize = if (esTv) 24.sp else 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("🔴 Comunidad", color = Color.White, fontSize = if (esTv) 12.sp else 10.5.sp, fontWeight = FontWeight.Bold)
                        Text("Total", color = Color(0xFF94A3B8), fontSize = if (esTv) 10.sp else 8.5.sp)
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

    LaunchedEffect(Unit) {
        delay(150)
        try { primerItemRequester.requestFocus() } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFA050810))
            .padding(horizontal = 26.dp, vertical = 18.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D121F)),
            modifier = Modifier
                .fillMaxSize()
                .border(2.dp, ColorDoradoPerfil, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // ENCABEZADO SUPERIOR
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📋 GUÍA OFICIAL CINEPARCHE",
                        color = ColorDoradoBrillante,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF261D09))
                            .border(1.dp, ColorDoradoPerfil, RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "SISTEMA CASTV OFICIAL",
                            color = ColorDoradoBrillante,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // CONTENIDO LIMPIO SIN RECUADROS: EL RECUADRO SOLO APARECE AL ENFOCAR
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // SECCIÓN 1: FLUJO DE PAGO Y COSTO POR CRÉDITO
                    ItemSeccionGuiaTvLimpio(
                        icono = "💳",
                        titulo = "1. CÓMO RECARGAR TU SALDO CASTV (PROCESO FÁCIL)",
                        cuerpo = "1. Vas a la opción <b>'PAQUETES'</b> en el menú principal y eliges el plan que prefieras.<br>" +
                                "2. Se generará un <b>código QR en la pantalla de tu TV</b>: lo escaneas con la cámara de tu móvil.<br>" +
                                "3. Realizas el pago y <b>cargas la foto del comprobante desde tu móvil</b>.<br>" +
                                "4. Nuestro equipo revisa el comprobante y <b>te activan el saldo CasTV de inmediato en tu cuenta</b>.<br><br>" +
                                "<b>¿Cómo rinden los paquetes?</b><br>" +
                                "Todos los créditos son iguales y <b>nunca vencen</b>. La única diferencia es que entre mayor sea el paquete, <b>más barato te sale cada CasTV</b>:<br>" +
                                "• <b>Bronce (50 CasTV):</b> Paquete base de entrada.<br>" +
                                "• <b>Plata (120 CasTV):</b> Cada CasTV te sale más barato que en el primero.<br>" +
                                "• <b>Oro VIP (250 CasTV):</b> Es el que tiene el costo más bajo por crédito (máximo ahorro por película).",
                        modifier = Modifier.focusRequester(primerItemRequester)
                    )

                    // SECCIÓN 2: ALQUILER POR TIEMPO Y CONTADOR REGRESIVO
                    ItemSeccionGuiaTvLimpio(
                        icono = "⏱️",
                        titulo = "2. ALQUILER POR TIEMPO (CONTADOR REGRESIVO)",
                        cuerpo = "• <b>⚡ ACTIVAR:</b> Películas verificadas listas para ver ya. Inicia el contador y dale play al instante.<br>" +
                                "• <b>📅 ALQUILAR:</b> Programa tu película para el fin de semana sin que el tiempo empiece a correr antes de la cita.<br>" +
                                "• <b>Cero Dobles Cobros:</b> Si ya tienes una película activa en tu perfil, reprodúcela cuantas veces quieras mientras el contador siga activo."
                    )

                    // SECCIÓN 3: TV EN VIVO Y CONTENIDO DE INTERNET
                    ItemSeccionGuiaTvLimpio(
                        icono = "📺",
                        titulo = "3. TELEVISIÓN EN VIVO 100% GRATIS & RED",
                        cuerpo = "• <b>TV En Vivo:</b> Más de 2,400 canales de televisión abierta sin ningún costo, sin saldo y sin suscripciones.<br>" +
                                "• <b>Contenido de Internet:</b> CineParche es una plataforma comunitaria que organiza y comparte transmisiones multimedia libres y públicas de la red.<br>" +
                                "• <b>Películas en Cortesía:</b> Disfruta periódicamente de estrenos en rotación gratuita para todos."
                    )

                    // SECCIÓN 4: PEDIDOS Y SOPORTE
                    ItemSeccionGuiaTvLimpio(
                        icono = "📲",
                        titulo = "4. PEDIDOS DE PELÍCULAS Y CANALES OFICIALES 24/7",
                        cuerpo = "• <b>Opción 'PEDIR':</b> Si no ves la película que buscas, usa el botón Pedir para que la subamos a la cartelera.<br>" +
                                "• <b>Atención en Telegram:</b> Soporte activo en nuestro Canal Oficial y Grupo Parche.<br>" +
                                "• <b>Sitio Web Oficial:</b> <font color='#FFD56B'><b>https://onnline.web.app/CineParche</b></font>"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // BOTONES DE ACCIÓN INFERIORES NAVEGABLES
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var isWebFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .scale(if (isWebFocused) 1.04f else 1.0f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isWebFocused) Color(0xFF2C394F) else Color(0xFF161F2E))
                            .border(
                                width = if (isWebFocused) 2.dp else 1.dp,
                                color = if (isWebFocused) ColorDoradoBrillante else Color(0xFF26324D),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { isWebFocused = it.isFocused }
                            .focusable()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://onnline.web.app/CineParche"))
                                context.startActivity(intent)
                            }
                            .padding(horizontal = 22.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "🌐 ABRIR ONNLINE.WEB.APP/CINEPARCHE",
                            color = if (isWebFocused) ColorDoradoBrillante else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    var isCerrarFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .scale(if (isCerrarFocused) 1.05f else 1.0f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCerrarFocused) Color.White else ColorDoradoPerfil)
                            .onFocusChanged { isCerrarFocused = it.isFocused }
                            .focusable()
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 30.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "ENTENDIDO / VOLVER AL PERFIL",
                            color = Color.Black,
                            fontSize = 14.sp,
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