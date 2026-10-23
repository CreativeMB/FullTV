package com.creativem.fulltv.principal

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
val ColorFondoOscuroPerfil = Color(0xFF0A122A)
val ColorCardPerfil = Color(0xFF141C33)

class Perfil : AppCompatActivity() {

    private val databaseRef by lazy { FirebaseDatabase.getInstance().reference }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        // Pantalla completa inmersiva para TV
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MaterialTheme {
                PerfilScreen(
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

// ==========================================
// 🟢 PANTALLA PRINCIPAL DE PERFIL EN COMPOSE
// ==========================================
@Composable
fun PerfilScreen(
    auth: FirebaseAuth,
    databaseRef: DatabaseReference,
    versionApp: String,
    onRegresar: () -> Unit,
    onReproducir: (Modelo.AlquilerItem) -> Unit
) {
    val configuration = LocalConfiguration.current
    val esHorizontal = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var nombreUsuario by remember { mutableStateOf("Usuario") }
    var fotoUsuarioUrl by remember { mutableStateOf<String?>(null) }
    var saldoCastv by remember { mutableStateOf("0") }
    var estaCargando by remember { mutableStateOf(true) }

    // Estadísticas
    var totalPeliculas by remember { mutableIntStateOf(Validacioneslista.obtenerPeliculasValidas().size) }
    var usuariosOnline by remember { mutableIntStateOf(0) }
    var usuariosOffline by remember { mutableIntStateOf(0) }

    // Reloj y fecha en vivo
    var horaEnVivo by remember { mutableStateOf("") }
    var fechaEnVivo by remember { mutableStateOf("") }

    val alquileresVigentes = remember { mutableStateListOf<Modelo.AlquilerItem>() }
    val cachePeliculas = remember { mutableMapOf<String, Modelo>() }

    val user = auth.currentUser
    val email = user?.email
    val correoKey = remember(email) { email?.replace(".", "_")?.replace("@", "_") ?: "" }

    BackHandler { onRegresar() }

    fun normalizarClave(texto: String): String {
        return texto.replace(".", "_")
            .replace("$", "_")
            .replace("#", "_")
            .replace("[", "_")
            .replace("]", "_")
    }

    // 🕒 1. RELOJ, FECHA Y CONTADOR DE PELÍCULAS EN TIEMPO REAL
    LaunchedEffect(Unit) {
        val formatoHora = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
        val formatoFecha = SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault())
        while (isActive) {
            val ahora = Date()
            horaEnVivo = formatoHora.format(ahora).uppercase(Locale.getDefault())
            fechaEnVivo = formatoFecha.format(ahora).replaceFirstChar { it.uppercase() }

            // 🟢 Actualizamos las películas validadas de forma dinámica
            totalPeliculas = Validacioneslista.obtenerPeliculasValidas().size
            delay(1000)
        }
    }

    // 👤 2. ESCUCHAR DATOS DEL USUARIO Y ESTADÍSTICAS EXACTAS EN TIEMPO REAL
    DisposableEffect(correoKey) {
        fotoUsuarioUrl = user?.photoUrl?.toString()

        val refUsuarios = databaseRef.child("usuarios")

        val listenerUsuarios = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                var conectadosReales = 0
                var desconectadosReales = 0

                // 1. Escanear toda la rama de usuarios leyendo el campo exacto "enlinea"
                for (child in snapshot.children) {
                    // Lee directamente tu campo booleano 'enlinea: true'
                    val isOnline = child.child("enlinea").value as? Boolean ?: false

                    if (isOnline) {
                        conectadosReales++
                    } else {
                        desconectadosReales++
                    }
                }

                // 2. 🟢 APLICAR TUS REGLAS Y ACTUALIZAR UI AL INSTANTE
                // Mostramos los reales en línea
                usuariosOnline = conectadosReales

                // Le sumamos 100 usuarios falsos siempre fuera de línea para hacer bulto
                usuariosOffline = desconectadosReales + 100

                // 3. Obtener datos personales del usuario actual
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

        // 🟢 Garantiza la reacción en tiempo real al conectarse o desconectarse alguien
        refUsuarios.addValueEventListener(listenerUsuarios)

        onDispose {
            refUsuarios.removeEventListener(listenerUsuarios)
        }
    }

    // 🍿 3. CARGA DE PELÍCULAS Y ALQUILERES
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
                                                child.ref.removeValue() // Expirado
                                            }
                                        }
                                    }
                                }
                                estaCargando = false
                            }

                            override fun onCancelled(error: DatabaseError) {
                                estaCargando = false
                            }
                        })
                } else {
                    estaCargando = false
                }
            }

            override fun onCancelled(error: DatabaseError) {
                estaCargando = false
            }
        })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(ColorFondoOscuroPerfil, Color(0xFF04060E))
                )
            )
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {

            // ==========================================
            // 🟢 HEADER COMPLETO (SISTEMA ANTI-OVERLAP)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF10172A))
                    .border(2.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // SECCIÓN IZQUIERDA: Botón, Foto y Textos
                Row(
                    modifier = Modifier.weight(1f), // 🟢 WEIGHT = 1f EVITA QUE EL TEXTO DESPLACE AL RELOJ
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BotonCircularInteractivo(onClick = onRegresar) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = ColorDoradoPerfil,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(3.dp, ColorDoradoPerfil, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!fotoUsuarioUrl.isNullOrBlank()) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { ctx ->
                                    ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
                                },
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
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(20.dp))

                    Column(
                        modifier = Modifier.weight(1f) // 🟢 Limita el tamaño de los textos
                    ) {
                        Text(
                            text = nombreUsuario,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, // 🟢 Nunca usará más de 1 línea
                            overflow = TextOverflow.Ellipsis // 🟢 Puntos suspensivos si no cabe
                        )
                        Text(
                            text = email ?: "invitado@fulltv.com",
                            color = Color.LightGray,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Versión: v$versionApp",
                            color = ColorDoradoPerfil,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(24.dp)) // Separador seguro

                // SECCIÓN DERECHA: Reloj y Saldo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 24.dp)) {
                        Text(text = horaEnVivo, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(text = fechaEnVivo, color = ColorDoradoPerfil, fontSize = 16.sp)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF221A08))
                            .border(2.dp, ColorDoradoPerfil, RoundedCornerShape(24.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "💰", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "$saldoCastv CasTV", color = ColorDoradoPerfil, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 🟢 TARJETAS DE ESTADÍSTICAS EN VIVO
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TarjetaEstadistica(
                    titulo = "Películas Validadas",
                    valor = "$totalPeliculas",
                    icono = "🎬",
                    colorBorde = ColorDoradoPerfil,
                    modifier = Modifier.weight(1f)
                )
                TarjetaEstadistica(
                    titulo = "Usuarios En Línea",
                    valor = "$usuariosOnline",
                    icono = "🟢",
                    colorBorde = Color(0xFF34D399),
                    modifier = Modifier.weight(1f)
                )
                TarjetaEstadistica(
                    titulo = "Usuarios Desconectados",
                    valor = "$usuariosOffline",
                    icono = "🔴",
                    colorBorde = Color(0xFFF87171),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ==========================================
            // 🟢 MIS ALQUILERES ACTIVOS
            // ==========================================
            if (estaCargando) {
                Box(modifier = Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ColorDoradoPerfil, modifier = Modifier.size(60.dp))
                }
            } else if (alquileresVigentes.isNotEmpty()) {
                Text(
                    text = "🍿 MIS ALQUILERES ACTIVOS",
                    color = ColorDoradoPerfil,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(alquileresVigentes, key = { it.movie.id }) { item ->
                        TarjetaAlquilerItem(
                            item = item,
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

            // ==========================================
            // 🟢 INFORMACIÓN, BENEFICIOS Y CÓDIGOS QR
            // ==========================================
            Text(
                text = "ℹ️ COMUNIDAD Y BENEFICIOS",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (esHorizontal) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TarjetaHtmlInfo(htmlResId = R.string.columna_1, modifier = Modifier.weight(1f).fillMaxHeight())
                    TarjetaHtmlInfo(htmlResId = R.string.columna_2, modifier = Modifier.weight(1f).fillMaxHeight())
                    TarjetaHtmlInfo(htmlResId = R.string.columna_3, modifier = Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TarjetaHtmlInfo(htmlResId = R.string.columna_1, modifier = Modifier.fillMaxWidth())
                    TarjetaHtmlInfo(htmlResId = R.string.columna_2, modifier = Modifier.fillMaxWidth())
                    TarjetaHtmlInfo(htmlResId = R.string.columna_3, modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 🟢 TARJETAS DE CÓDIGOS QR (CANAL Y GRUPO DESDE DRAWABLE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TarjetaQRInfo(
                    titulo = "Canal Oficial",
                    subtitulo = "Escanea para unirte y enterarte de nuestros últimos estrenos",
                    drawableRes = R.drawable.canal, // 🟢 Carga desde drawable local
                    modifier = Modifier.weight(1f)
                )
                TarjetaQRInfo(
                    titulo = "Grupo del Parche",
                    subtitulo = "Comparte y pide tus películas favoritas con la comunidad",
                    drawableRes = R.drawable.grupo, // 🟢 Carga desde drawable local
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// ==========================================
// 🟢 TARJETA DE ESTADÍSTICA
// ==========================================
@Composable
fun TarjetaEstadistica(
    titulo: String,
    valor: String,
    icono: String,
    colorBorde: Color,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1.0f, label = "scaleStat")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF1B243D) else ColorCardPerfil),
        modifier = modifier
            .scale(scale)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) colorBorde else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icono, fontSize = 36.sp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = valor, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(text = titulo, color = Color.LightGray, fontSize = 14.sp)
            }
        }
    }
}

// ==========================================
// 🟢 TARJETA DE CÓDIGO QR CON DRAWABLES LOCALES
// ==========================================
@Composable
fun TarjetaQRInfo(
    titulo: String,
    subtitulo: String,
    drawableRes: Int,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.03f else 1.0f, label = "scaleQR")

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF1B243D) else ColorCardPerfil),
        modifier = modifier
            .scale(scale)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) ColorDoradoPerfil else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Row(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Carga la imagen estática desde res/drawable/
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "Código QR $titulo",
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(4.dp)
            )

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                Text(text = titulo, color = ColorDoradoPerfil, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = subtitulo, color = Color.LightGray, fontSize = 16.sp, lineHeight = 22.sp)
            }
        }
    }
}

// ==========================================
// 🟢 TARJETA DE PELÍCULA ALQUILADA
// ==========================================
@Composable
fun TarjetaAlquilerItem(
    item: Modelo.AlquilerItem,
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
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(150),
        label = "scaleAlquiler"
    )

    Column(
        modifier = Modifier
            .width(180.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isFocused) Color(0xFF232D48) else ColorCardPerfil)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) ColorDoradoPerfil else Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .clip(RoundedCornerShape(10.dp))
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
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contadorTexto,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = item.movie.title,
            color = if (isFocused) ColorDoradoPerfil else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) ColorDoradoPerfil else Color(0xFF1E2842))
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "VER AHORA",
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==========================================
// 🟢 TARJETAS DE INFORMACIÓN HTML
// ==========================================
@Composable
fun TarjetaHtmlInfo(
    htmlResId: Int,
    modifier: Modifier = Modifier
) {
    val htmlString = stringResource(id = htmlResId)
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f,
        animationSpec = tween(150),
        label = "scaleCardHtml"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF1B243D) else ColorCardPerfil),
        modifier = modifier
            .scale(scale)
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) ColorDoradoPerfil else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        Box(modifier = Modifier.padding(24.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx ->
                    TextView(ctx).apply {
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 18f
                        setLineSpacing(8f, 1.2f)
                    }
                },
                update = { textView ->
                    textView.text = HtmlCompat.fromHtml(
                        htmlString,
                        HtmlCompat.FROM_HTML_MODE_LEGACY
                    )
                }
            )
        }
    }
}

// ==========================================
// 🟢 BOTÓN CIRCULAR
// ==========================================
@Composable
fun BotonCircularInteractivo(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(56.dp)
            .scale(if (isFocused) 1.15f else 1.0f)
            .clip(CircleShape)
            .background(if (isFocused) Color(0xFF2C3859) else Color(0xFF141C33))
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) ColorDoradoPerfil else Color.White.copy(alpha = 0.2f),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}