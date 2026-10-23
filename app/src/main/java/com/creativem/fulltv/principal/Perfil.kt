package com.creativem.fulltv.principal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                PerfilScreenDashboard(
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
            CastvHelper.regresarAPeliculas(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

// =========================================================================
// 🟢 PANTALLA DASHBOARD: TODO EN 1 SOLA PANTALLA DE TV SIN VACÍOS
// =========================================================================
@Composable
fun PerfilScreenDashboard(
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

    var totalPeliculas by remember { mutableIntStateOf(Validacioneslista.obtenerPeliculasValidas().size) }
    var usuariosOnline by remember { mutableIntStateOf(0) }
    var usuariosOffline by remember { mutableIntStateOf(0) }

    var horaEnVivo by remember { mutableStateOf("") }
    var fechaEnVivo by remember { mutableStateOf("") }

    var mostrarDialogoInfo by remember { mutableStateOf(false) }

    val alquileresVigentes = remember { mutableStateListOf<Modelo.AlquilerItem>() }
    val cachePeliculas = remember { mutableMapOf<String, Modelo>() }

    val user = auth.currentUser
    val email = user?.email
    val correoKey = remember(email) { email?.replace(".", "_")?.replace("@", "_") ?: "" }

    val backButtonFocusRequester = remember { FocusRequester() }

    BackHandler {
        if (mostrarDialogoInfo) {
            mostrarDialogoInfo = false
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

    // 🕒 Reloj y estadísticas en tiempo real
    LaunchedEffect(Unit) {
        val formatoHora = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val formatoFecha = SimpleDateFormat("EEE, dd MMM", Locale.getDefault())
        while (isActive) {
            val ahora = Date()
            horaEnVivo = formatoHora.format(ahora).uppercase(Locale.getDefault())
            fechaEnVivo = formatoFecha.format(ahora).replaceFirstChar { it.uppercase() }
            totalPeliculas = Validacioneslista.obtenerPeliculasValidas().size
            delay(1000)
        }
    }

    // 👤 Escucha de usuarios en tiempo real
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

                usuariosOnline = conectadosReales
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

    // 🍿 Alquileres activos
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
                                        val createdAt = (child.child("createdAt").value as? Number)?.toLong() ?: 0L
                                        val countdownMinutes = (child.child("countdownMinutes").value as? Number)?.toInt() ?: 0

                                        val tiempoTranscurrido = ahora - createdAt

                                        if (tiempoTranscurrido >= 0) {
                                            val durationMillis = TimeUnit.MINUTES.toMillis(countdownMinutes.toLong())
                                            val tiempoRestante = durationMillis - tiempoTranscurrido

                                            if (tiempoRestante > 0) {
                                                val movie = cachePeliculas[tituloKey]
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
            // 🟢 1. ENCABEZADO SUPERIOR: PERFIL, ESTADÍSTICAS EN 1 LÍNEA Y SALDO
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ColorCardPerfil)
                    .border(1.dp, Color(0xFF1E2638), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp),
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
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(38.dp)
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

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.widthIn(max = 175.dp)) {
                    Text(
                        text = nombreUsuario,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "v$versionApp • $email",
                        color = Color(0xFF8C95A8),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Estadísticas en 1 sola pastilla
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF161C2C))
                        .border(0.8.dp, Color(0xFF26324D), RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎬 $totalPeliculas Películas", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("   |   ", color = Color(0xFF404E6B), fontSize = 10.sp)
                    Text("🟢 $usuariosOnline en línea", color = Color(0xFF34D399), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("   |   ", color = Color(0xFF404E6B), fontSize = 10.sp)
                    Text("🔴 $usuariosOffline comunidad", color = Color(0xFFF87171), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = horaEnVivo, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(text = fechaEnVivo, color = Color(0xFF8C95A8), fontSize = 10.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF221A08))
                        .border(1.dp, ColorDoradoPerfil, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💰 $saldoCastv CasTV",
                        color = ColorDoradoPerfil,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // =================================================================
            // 🟢 2. CENTRO: COMUNIDAD QRs (IZQ) Y GUÍA/BENEFICIOS COMPLETA (DER)
            // =================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tarjeta QRs que LLENA el espacio por completo
                TarjetaQRsModernaCompleta(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxHeight()
                )

                // Tarjeta de Guía y Beneficios rica en contenido
                TarjetaInfoExpandiblePreview(
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxHeight(),
                    onClick = { mostrarDialogoInfo = true }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // =================================================================
            // 🟢 3. ABAJO: ALQUILERES ACTIVOS EN PÓSTER VERTICAL (COMO EL CATÁLOGO)
            // =================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (esTv) 210.dp else 175.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ColorCardPerfil)
                    .border(1.dp, Color(0xFF1E2638), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                if (estaCargando) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ColorDoradoPerfil, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                    }
                } else if (alquileresVigentes.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp, start = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🍿 TUS ALQUILERES ACTIVOS",
                                color = ColorDoradoPerfil,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${alquileresVigentes.size} disponibles para reproducir",
                                color = Color(0xFF8C95A8),
                                fontSize = 11.sp
                            )
                        }

                        // 🟢 Carrusel horizontal con PÓSTERS VERTICALES idénticos al catálogo
                        LazyRow(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(text = "🍿", fontSize = 32.sp)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Aquí saldrán tus alquileres activos",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Canjea cualquier película con tus créditos CasTV en la cartelera y podrás verla inmediatamente aquí.",
                                color = Color(0xFF8C95A8),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Diálogo con indicaciones completas
        if (mostrarDialogoInfo) {
            DialogoInformacionCompleta(onDismiss = { mostrarDialogoInfo = false })
        }
    }
}

// =========================================================================
// 🟢 PÓSTER VERTICAL DE ALQUILER (IGUAL AL DE LAS OTRAS PANTALLAS)
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
    var millisRestantes by remember(item.movie.id, item.createdAt, item.countdownMinutes) {
        val transcurrido = System.currentTimeMillis() - item.createdAt
        mutableLongStateOf(maxOf(0L, durationMillis - transcurrido))
    }

    LaunchedEffect(item.movie.id, item.createdAt) {
        while (millisRestantes > 0) {
            delay(1000)
            val transcurrido = System.currentTimeMillis() - item.createdAt
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
            .width(if (esTv) 115.dp else 95.dp)
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

            // Insignia verde de cuenta regresiva en el borde inferior del póster
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xE6107C41))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contadorTexto,
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Título con marquesina cuando está enfocado
        val marqueeMod = if (isFocused) {
            Modifier.basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 600, velocity = 35.dp)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) ColorDoradoPerfil else Color(0xFF0C1017))
                .padding(vertical = 3.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.movie.title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 10.5.sp,
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
// 🟢 TARJETA DE QRs QUE LLENA EL ESPACIO POR COMPLETO
// =========================================================================
@Composable
fun TarjetaQRsModernaCompleta(
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF161E2E) else ColorCardPerfil),
        modifier = modifier
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) ColorDoradoPerfil else Color(0xFF1E2638),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "📲 COMUNIDAD Y REDES OFICIALES",
                color = ColorDoradoPerfil,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // QR Canal Oficial
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0B0E17))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.canal),
                        contentDescription = "Canal Oficial",
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .padding(2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Canal Oficial", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text("Cartelera y estrenos diarios al instante.", color = Color(0xFF8C95A8), fontSize = 10.sp, lineHeight = 13.sp)
                    }
                }

                // QR Grupo Parche
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0B0E17))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.grupo),
                        contentDescription = "Grupo Parche",
                        modifier = Modifier
                            .size(62.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .padding(2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Grupo Parche", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        Text("Comparte opiniones y pide tus películas favoritas.", color = Color(0xFF8C95A8), fontSize = 10.sp, lineHeight = 13.sp)
                    }
                }
            }

            Text(
                text = "Escanea con la cámara de tu celular para unirte a los grupos oficiales.",
                color = Color(0xFF6B7280),
                fontSize = 10.5.sp
            )
        }
    }
}

// =========================================================================
// 🟢 TARJETA DE GUÍA Y BENEFICIOS RICA EN CONTENIDO
// =========================================================================
@Composable
fun TarjetaInfoExpandiblePreview(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF181F2E) else ColorCardPerfil),
        modifier = modifier
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) ColorDoradoPerfil else Color(0xFF1E2638),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ℹ️ GUÍA, BENEFICIOS E INDICACIONES",
                    color = ColorDoradoPerfil,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isFocused) ColorDoradoPerfil else Color(0xFF1E2638))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "PULSA OK PARA EXPANDIR 🔍",
                        color = if (isFocused) Color.Black else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "• 1. Activaciones Inmediatas: Canjea con tus créditos CasTV y reproduce al instante sin esperas ni límites.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "• 2. Estrenos Programados: Pide tus películas preferidas y prográmalas en cartelera para toda la comunidad.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "• 3. Soporte y Comunidad: Actualizaciones constantes, cartelera verificada a diario y atención activa 24/7.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = "Presiona el botón de tu control para leer todas las instrucciones y reglas detalladas del servicio.",
                color = if (isFocused) ColorDoradoPerfil else Color(0xFF8C95A8),
                fontSize = 10.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

// =========================================================================
// 🟢 DIÁLOGO COMPLETO DE INFORMACIÓN (SIN CORTES)
// =========================================================================
@Composable
fun DialogoInformacionCompleta(
    onDismiss: () -> Unit
) {
    val botonCerrarFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(150)
        try { botonCerrarFocusRequester.requestFocus() } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
            modifier = Modifier
                .widthIn(max = 750.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .border(2.dp, ColorDoradoPerfil, RoundedCornerShape(14.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                Text(
                    text = "📋 GUÍA COMPLETA E INDICACIONES DEL SERVICIO",
                    color = ColorDoradoPerfil,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ItemIndicacionDetalladaHtml(htmlResId = R.string.columna_1, titulo = "PUNTO 1: BENEFICIOS Y REGLAS")
                    HorizontalDivider(color = Color(0xFF1E2638), thickness = 1.dp)
                    ItemIndicacionDetalladaHtml(htmlResId = R.string.columna_2, titulo = "PUNTO 2: SOLICITUDES Y PEDIDOS")
                    HorizontalDivider(color = Color(0xFF1E2638), thickness = 1.dp)
                    ItemIndicacionDetalladaHtml(htmlResId = R.string.columna_3, titulo = "PUNTO 3: SOPORTE Y ACTIVACIONES")
                }

                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    var isBtnFocused by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .focusRequester(botonCerrarFocusRequester)
                            .scale(if (isBtnFocused) 1.05f else 1.0f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isBtnFocused) Color.White else ColorDoradoPerfil)
                            .onFocusChanged { isBtnFocused = it.isFocused }
                            .focusable()
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "ENTENDIDO / CERRAR",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ItemIndicacionDetalladaHtml(
    htmlResId: Int,
    titulo: String
) {
    val htmlString = stringResource(id = htmlResId)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = titulo,
            color = ColorDoradoPerfil,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                TextView(ctx).apply {
                    setTextColor(android.graphics.Color.parseColor("#CBD5E1"))
                    textSize = 13f
                    setLineSpacing(4f, 1.2f)
                }
            },
            update = { textView ->
                textView.text = HtmlCompat.fromHtml(htmlString, HtmlCompat.FROM_HTML_MODE_LEGACY)
            }
        )
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
            .size(34.dp)
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