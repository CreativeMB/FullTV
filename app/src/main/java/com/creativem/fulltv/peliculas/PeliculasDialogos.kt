package com.creativem.fulltv.peliculas

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.creativem.fulltv.api.TMDbApiService
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Modelo
import com.google.firebase.database.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

// ==========================================
// 🟢 UTILIDADES GLOBALES DE TEXTO Y QR
// ==========================================
private fun String.normalizarTexto(): String {
    val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    val temp = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
    return diacritics.replace(temp, "").lowercase()
}

suspend fun generarBitmapQROptimizado(texto: String): Bitmap? = withContext(Dispatchers.Default) {
    try {
        val size = 400
        val bitMatrix = MultiFormatWriter().encode(texto, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}

// ==========================================
// 🟢 BOTÓN CON ENMARCADOR DE FOCUS PARA TV
// ==========================================
@Composable
fun BotonDialogoTV(
    texto: String,
    esPrincipal: Boolean = false,
    colorBase: Color = if (esPrincipal) ColorDorado else Color(0xFF2A2A2A),
    colorTextoBase: Color = if (esPrincipal) Color.Black else Color.White,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .scale(if (isFocused) 1.05f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused && esPrincipal -> Color.White
                    isFocused -> ColorDorado
                    else -> colorBase
                }
            )
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) ColorDorado else Color.Gray.copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            color = when {
                isFocused -> Color.Black
                else -> colorTextoBase
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

// ==========================================
// 1. DIÁLOGO ALQUILERES ACTIVOS
// ==========================================
@Composable
fun DialogoAlquileresActivos(
    alquileres: List<Modelo.AlquilerItem>,
    onDismiss: () -> Unit,
    onSelectMovie: (Modelo) -> Unit
) {
    val config = LocalConfiguration.current
    val esTv = config.screenWidthDp > 600
    val anchoMax = if (esTv) 780.dp else (config.screenWidthDp * 0.94f).dp

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            modifier = Modifier
                .width(anchoMax)
                .wrapContentHeight()
                .padding(14.dp)
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🍿 TUS ALQUILERES ACTIVOS",
                    color = ColorDorado,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Películas disponibles en tu cuenta listas para ver:",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                                .width(if (esTv) 130.dp else 115.dp)
                                .scale(if (isCardFocused) 1.06f else 1.0f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isCardFocused) Color(0xFF2E2E2E) else Color(0xFF101010))
                                .border(
                                    width = if (isCardFocused) 2.5.dp else 1.dp,
                                    color = if (isCardFocused) ColorDorado else Color.Gray.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { isCardFocused = it.isFocused }
                                .focusable()
                                .clickable { onSelectMovie(item.movie) }
                                .padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (esTv) 165.dp else 145.dp)
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
                                        .padding(vertical = 2.dp),
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isCardFocused) ColorDorado else Color(0xFF262626))
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "VER AHORA",
                                    color = if (isCardFocused) Color.Black else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                BotonDialogoTV(
                    texto = "IR AL CATÁLOGO",
                    esPrincipal = true,
                    onClick = onDismiss
                )
            }
        }
    }
}

// ==========================================
// 2. DIÁLOGO BUSCADOR
// ==========================================
@Composable
fun DialogoBuscador(
    catalogoLocal: List<Modelo>,
    apiService: TMDbApiService,
    apiKey: String,
    onDismiss: () -> Unit,
    onSelectMovie: (Modelo) -> Unit
) {
    val config = LocalConfiguration.current
    val esTv = config.screenWidthDp > 600
    val anchoMax = if (esTv) 760.dp else (config.screenWidthDp * 0.95f).dp
    val altoMax = (config.screenHeightDp * 0.88f).dp

    var query by remember { mutableStateOf("") }
    val resultados = remember { mutableStateListOf<Modelo>() }
    var estaBuscando by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }

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
            } catch (_: Exception) {
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            modifier = Modifier
                .width(anchoMax)
                .heightIn(max = altoMax)
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Text(
                    text = "🔍 BUSCADOR DE PELÍCULAS",
                    color = ColorDorado,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Escribe el nombre de la película...", color = Color.Gray, fontSize = 14.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ColorDorado,
                        unfocusedBorderColor = if (isInputFocused) ColorDorado else Color.Gray,
                        cursorColor = ColorDorado
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isInputFocused = it.isFocused }
                        .border(
                            width = if (isInputFocused) 2.dp else 0.dp,
                            color = ColorDorado,
                            shape = RoundedCornerShape(8.dp)
                        )
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (estaBuscando) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = ColorDorado,
                        trackColor = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(resultados) { item ->
                        var isItemFocused by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(if (isItemFocused) 1.02f else 1.0f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isItemFocused) Color(0xFF2E2E2E) else Color(0xFF101010))
                                .border(
                                    width = if (isItemFocused) 2.dp else 0.5.dp,
                                    color = if (isItemFocused) ColorDorado else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .focusable()
                                .clickable { onSelectMovie(item) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlideImageCustom(
                                url = item.imageUrl,
                                modifier = Modifier
                                    .size(width = 44.dp, height = 66.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = item.title,
                                color = if (isItemFocused) ColorDorado else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isItemFocused) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    BotonDialogoTV(
                        texto = "CERRAR",
                        esPrincipal = false,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

// ==========================================
// 3. DIÁLOGO PEDIDO DE PELÍCULAS
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
    val config = LocalConfiguration.current
    val esTv = config.screenWidthDp > 600
    val anchoMax = if (esTv) 760.dp else (config.screenWidthDp * 0.95f).dp
    val altoMax = (config.screenHeightDp * 0.88f).dp

    var pasoConfirmacion by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedItem by remember { mutableStateOf<TmdbResultadoItem?>(null) }
    var mensajeError by remember { mutableStateOf("") }
    val sugerencias = remember { mutableStateListOf<TmdbResultadoItem>() }
    var nombreUsuario by remember { mutableStateOf("Usuario") }
    var isInputFocused by remember { mutableStateOf(false) }

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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            modifier = Modifier
                .width(anchoMax)
                .heightIn(max = altoMax)
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                if (!pasoConfirmacion) {
                    Text(
                        text = "🎬 SOLICITAR PELÍCULA",
                        color = ColorDorado,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            selectedItem = null
                            mensajeError = ""
                        },
                        placeholder = { Text("Escribe para buscar (Ej: Moana 2)", color = Color.Gray, fontSize = 14.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ColorDorado,
                            unfocusedBorderColor = if (isInputFocused) ColorDorado else Color.Gray,
                            cursorColor = ColorDorado
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isInputFocused = it.isFocused }
                            .border(
                                width = if (isInputFocused) 2.dp else 0.dp,
                                color = ColorDorado,
                                shape = RoundedCornerShape(8.dp)
                            )
                    )

                    if (mensajeError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = mensajeError,
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
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
                                        .background(if (isFocusRow) Color(0xFF2C2C2C) else Color(0xFF101010))
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
                                            .size(width = 40.dp, height = 60.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "${item.title} (${item.releaseDate})",
                                            color = if (isFocusRow) ColorDorado else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "⭐ ${if (item.voteAverage > 0) String.format(Locale.US, "%.1f", item.voteAverage) else "N/A"}",
                                            color = ColorDorado,
                                            fontSize = 11.sp
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
                                            .background(if (isCustomFocus) Color(0xFF2C2C2C) else Color(0xFF121212))
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
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "➕ Pedir: \"${query.trim()}\" (Personalizado)",
                                            color = ColorDorado,
                                            fontSize = 14.sp,
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
                                .background(Color(0xFF101010))
                                .border(1.5.dp, ColorDorado, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlideImageCustom(
                                url = selectedItem!!.posterPath,
                                modifier = Modifier
                                    .size(width = 85.dp, height = 130.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${selectedItem!!.title} (${selectedItem!!.releaseDate})",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "⭐ ${if (selectedItem!!.voteAverage > 0) String.format(Locale.US, "%.1f", selectedItem!!.voteAverage) else "N/A"}",
                                    color = ColorDorado,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = selectedItem!!.overview,
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 3,
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
                        BotonDialogoTV(
                            texto = "CANCELAR",
                            esPrincipal = false,
                            onClick = onDismiss
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        BotonDialogoTV(
                            texto = "SIGUIENTE",
                            esPrincipal = true,
                            onClick = {
                                if (selectedItem == null) {
                                    mensajeError = "Por favor selecciona una película de la lista sugerida."
                                    return@BotonDialogoTV
                                }
                                if (saldoActual < costoPedido) {
                                    mensajeError = "Saldo insuficiente en CasTV. Tienes $saldoActual y se requieren $costoPedido."
                                    return@BotonDialogoTV
                                }

                                if (selectedItem!!.releaseDate.isNotBlank() && selectedItem!!.releaseDate != "Personalizado") {
                                    try {
                                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                        val dateEstreno = sdf.parse(selectedItem!!.releaseDate)
                                        if (dateEstreno != null) {
                                            val diffMillis = System.currentTimeMillis() - dateEstreno.time
                                            val diffDias = TimeUnit.MILLISECONDS.toDays(diffMillis)
                                            if (diffDias < 40) {
                                                val faltan = if (diffDias < 0) 40 + abs(diffDias) else 40 - diffDias
                                                mensajeError = "Película muy reciente (${selectedItem!!.releaseDate}). Deben pasar 40 días. Faltan aprox. $faltan días."
                                                return@BotonDialogoTV
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }

                                pasoConfirmacion = true
                            }
                        )
                    }

                } else {
                    Text(
                        text = "📋 CONFIRMAR PEDIDO",
                        color = ColorDorado,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF101010))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlideImageCustom(
                                url = selectedItem?.posterPath,
                                modifier = Modifier
                                    .size(width = 60.dp, height = 90.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    selectedItem?.title ?: "",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "⭐ ${selectedItem?.voteAverage ?: 0.0}  |  📅 ${selectedItem?.releaseDate}",
                                    color = ColorDorado,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("👤 Solicitante: $nombreUsuario", color = Color.White, fontSize = 13.sp)
                            Text("💰 Saldo Actual: $saldoActual CasTV", color = Color.White, fontSize = 13.sp)
                            Text("📉 Costo Pedido: $costoPedido CasTV", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            HorizontalDivider(color = Color(0xFF333333))
                            Text("✅ Saldo Final: ${saldoActual - costoPedido} CasTV", color = ColorDorado, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BotonDialogoTV(
                            texto = "CORREGIR",
                            esPrincipal = false,
                            onClick = { pasoConfirmacion = false }
                        )

                        BotonDialogoTV(
                            texto = "CONFIRMAR",
                            esPrincipal = true,
                            onClick = {
                                CastvHelper.mostrarSelectorFechaHora(activity) { fecha, hora ->
                                    val item = selectedItem ?: return@mostrarSelectorFechaHora
                                    val moviesRef = databaseRef.child("movies")
                                    val correoUsuario = userEmail.ifBlank { correoKey.replace("_", ".") }

                                    moviesRef.orderByChild("originalTitle")
                                        .equalTo(item.originalTitle)
                                        .addListenerForSingleValueEvent(object : ValueEventListener {
                                            override fun onDataChange(snapshot: DataSnapshot) {
                                                val tiempoActual = System.currentTimeMillis()
                                                if (snapshot.exists()) {
                                                    val existingId = snapshot.children.firstOrNull()?.key ?: ""
                                                    if (existingId.isNotEmpty()) {
                                                        moviesRef.child(existingId).child("createdAt").setValue(tiempoActual)
                                                        val solRef = moviesRef.child(existingId).child("solicitudes").push()
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
                                                    moviesRef.child(newId).setValue(mapaNueva).addOnSuccessListener {
                                                        val solRef = moviesRef.child(newId).child("solicitudes").push()
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

                                                val userRef = databaseRef.child("usuarios").child(correoKey)
                                                userRef.child("castv").setValue(saldoActual - costoPedido).addOnSuccessListener {
                                                    CastvHelper.registrarConsumo(correoUsuario, "PEDIDO: ${item.title}", costoPedido)
                                                    Toast.makeText(activity, "¡Pedido realizado con éxito! 🎬", Toast.LENGTH_LONG).show()
                                                    onDismiss()
                                                }
                                            }

                                            override fun onCancelled(error: DatabaseError) {
                                                onDismiss()
                                            }
                                        })
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. DIÁLOGO PAQUETES (PASARELA Y QR)
// ==========================================
@Composable
fun DialogoPaquetes(
    correoKey: String,
    databaseRef: DatabaseReference,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val esTv = config.screenWidthDp > 600
    val anchoMax = if (esTv) 680.dp else (config.screenWidthDp * 0.95f).dp

    var pasoQR by remember { mutableStateOf(false) }

    var paqueteElegido by remember { mutableStateOf("Bronce") }
    var montoElegido by remember { mutableStateOf("10000") }
    var pedidoId by remember { mutableStateOf("") }

    var estadoPedido by remember { mutableStateOf("pendiente") }
    var urlPagosComprobante by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var urlPasarela by remember { mutableStateOf("") }
    var isQrFocused by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

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
                    coroutineScope.launch {
                        qrBitmap = generarBitmapQROptimizado(url)
                    }
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161616)),
            modifier = Modifier
                .width(anchoMax)
                .wrapContentHeight()
                .padding(14.dp)
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!pasoQR) {
                    Text(
                        text = "💎 SELECCIONAR PAQUETE",
                        color = ColorDorado,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Elige la cantidad de créditos CasTV a recargar:",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))

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
                                .background(if (isItemFocused) Color(0xFF2E2E2E) else if (isSelected) Color(0xFF222222) else Color(0xFF101010))
                                .border(
                                    width = if (isItemFocused) 2.dp else if (isSelected) 1.5.dp else 0.5.dp,
                                    color = if (isItemFocused) ColorDorado else if (isSelected) ColorDorado.copy(alpha = 0.7f) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .focusable()
                                .clickable {
                                    paqueteElegido = nombre
                                    montoElegido = precio
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                color = if (isSelected || isItemFocused) ColorDorado else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected || isItemFocused) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        BotonDialogoTV(
                            texto = "CANCELAR",
                            esPrincipal = false,
                            onClick = onDismiss
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        BotonDialogoTV(
                            texto = "SIGUIENTE",
                            esPrincipal = true,
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

                                coroutineScope.launch {
                                    qrBitmap = generarBitmapQROptimizado(url)
                                }
                                pasoQR = true
                            }
                        )
                    }

                } else {
                    Text(
                        text = "💎 PEDIDO REGISTRADO",
                        color = ColorDorado,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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
                        Text("ID Pedido: $pedidoId", color = Color.LightGray, fontSize = 13.sp)
                        Text("Paquete: $paqueteElegido ($$montoElegido)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Estado: $estadoVisual",
                            color = colorEstado,
                            fontSize = 14.sp,
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
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "✅ ¡Tu pago fue aprobado con éxito!\nTus créditos CasTV ya se encuentran disponibles.",
                                    color = Color(0xFF34D399),
                                    fontSize = 14.sp,
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
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "🔍 Comprobante de pago en revisión.\nTus créditos se sumarán automáticamente tras validar.",
                                    color = Color(0xFF34D399),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            qrBitmap?.let { bmp ->
                                Box(
                                    modifier = Modifier
                                        .scale(if (isQrFocused) 1.05f else 1.0f)
                                        .border(
                                            width = if (isQrFocused) 3.dp else 1.dp,
                                            color = if (isQrFocused) ColorDorado else Color.Gray.copy(alpha = 0.3f),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(6.dp)
                                        .onFocusChanged { isQrFocused = it.isFocused }
                                        .focusable()
                                        .clickable {
                                            if (urlPasarela.isNotEmpty()) {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlPasarela)))
                                            }
                                        }
                                ) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = "Código QR de Pago",
                                        modifier = Modifier
                                            .size(if (esTv) 175.dp else 150.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Código Bre-Be: @TMB833\nPulsa o escanea para pagar y subir comprobante",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    BotonDialogoTV(
                        texto = "CERRAR",
                        esPrincipal = true,
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

// ==========================================
// 5. DIÁLOGO CONFIRMACIÓN SALIDA
// ==========================================
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

    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = ColorFondoOscuro),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "¿Desea cerrar CineParche?",
                    color = ColorDorado,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Si sales ahora, te perderás lo mejor del parche.",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    BotonDialogoTV(
                        texto = "SÍ, SALIR",
                        esPrincipal = true,
                        onClick = onConfirmar
                    )
                    BotonDialogoTV(
                        texto = "VOLVER (${segundos}s)",
                        esPrincipal = false,
                        onClick = onCancelar
                    )
                }
            }
        }
    }
}