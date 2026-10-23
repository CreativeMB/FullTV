package com.creativem.fulltv.peliculasvalidas

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.creativem.fulltv.api.ApiPeliculaActivity
import com.creativem.fulltv.principal.CastvHelper
import com.creativem.fulltv.principal.Modelo
import com.creativem.fulltv.principal.ViewUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.creativem.fulltv.R
private val DoradoPrincipal = Color(0xFFC5A059)
private val DoradoBrillante = Color(0xFFFFD56B)
private val FondoNegroSala = Color(0xFF07090E)

class PeliculasValidasActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración de pantalla para Android TV

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)

        val columnas = ViewUtils.calcularColumnas(this).coerceAtLeast(3)

        setContent {
            PeliculasValidasScreen(
                columnas = columnas,
                onMovieClick = { movie -> irAlDetalle(movie) },
                onVolver = { CastvHelper.regresarAPeliculas(this) }
            )
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

    private fun irAlDetalle(modelo: Modelo) {
        val intent = Intent(this, ApiPeliculaActivity::class.java).apply {
            putExtra("EXTRA_STREAM_URL", modelo.streamUrl)
            putExtra("EXTRA_MOVIE_TITLE", modelo.title)
            putExtra("EXTRA_MOVIE_CASTV", modelo.castv)
            putExtra("EXTRA_MOVIE_IMAGE_URL", modelo.imageUrl)
            putExtra("EXTRA_ORIGINAL_TITLE", modelo.originalTitle)
            putExtra("EXTRA_COUNTDOWN", modelo.countdownMinutes)
            putExtra("EXTRA_CREATED_AT", modelo.createdAt / 1000)
            putExtra("EXTRA_IS_VALID", true)
            putExtra("EXTRA_MOVIE_DATA", modelo)
        }
        startActivity(intent)
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
// 🟢 PANTALLA COMPOSABLE ULTRA LIGERA PARA TV Y MÓVIL
// =========================================================================

@Composable
fun PeliculasValidasScreen(
    columnas: Int,
    onMovieClick: (Modelo) -> Unit,
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val esTv = config.screenWidthDp > 600

    var isLoading by remember { mutableStateOf(true) }
    val peliculasList = remember { mutableStateListOf<Modelo>() }
    var fondoActual by remember { mutableStateOf("") }

    val firstItemFocusRequester = remember { FocusRequester() }

    BackHandler { onVolver() }

    // 🟢 CARGA DE DATOS ASÍNCRONA (Conserva 100% de la lógica original)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (!Validacioneslista.yaCargado()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Verificando enlaces...", Toast.LENGTH_SHORT).show()
                }
                Validacioneslista.esperarCarga()
            }

            val validadas = Validacioneslista.obtenerPeliculasValidas()

            withContext(Dispatchers.Main) {
                if (validadas.isNotEmpty()) {
                    validadas.forEach { it.isValid = true }
                    val ordenadas = validadas.sortedByDescending { it.createdAt }
                    peliculasList.clear()
                    peliculasList.addAll(ordenadas)
                    fondoActual = ordenadas.firstOrNull()?.imageUrl ?: ""
                    isLoading = false

                    // Auto-foco en el primer ítem al terminar de cargar
                    delay(150)
                    try {
                        firstItemFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                } else {
                    Toast.makeText(
                        context,
                        "No hay películas disponibles actualmente",
                        Toast.LENGTH_LONG
                    ).show()
                    onVolver()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FondoNegroSala)
    ) {
        // 🟢 FONDO CINEMÁTICO DINÁMICO (Cambia según la película enfocada)
        Crossfade(
            targetState = fondoActual,
            animationSpec = tween(400),
            label = "bgValidasCrossfade"
        ) { url ->
            if (url.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Capa oscura para garantizar lectura perfecta sin saturar GPU
        // 🟢 AHORA (Translúcido: el fondo se ve con total claridad):
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x5507090E), // Muy claro arriba
                            Color(0x8007090E), // Se aprecia bien la imagen en el centro
                            Color(0xB507090E)  // Oscuro suave abajo para leer los títulos
                        )
                    )
                )
        )

        if (isLoading) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = DoradoPrincipal,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Verificando cartelera disponible...",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (esTv) 32.dp else 16.dp, vertical = 12.dp)
            ) {
                // 🟢 ENCABEZADO CON TÍTULO EXPLICATIVO
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x3300E676))
                                .border(1.dp, Color(0xFF00E676), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "⚡ DISPONIBILIDAD INMEDIATA",
                                color = Color(0xFF00E676),
                                fontSize = if (esTv) 11.sp else 9.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = "${peliculasList.size} Películas Listas",
                            color = DoradoBrillante,
                            fontSize = if (esTv) 13.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "PELÍCULAS LISTAS PARA ACTIVAR",
                        color = Color.White,
                        fontSize = if (esTv) 26.sp else 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "Activa tu función al instante con tus créditos CasTV y empieza a ver ahora mismo.",
                        color = Color(0xFFB0B8C8),
                        fontSize = if (esTv) 14.sp else 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }

                // 🟢 GRILLA DE PELÍCULAS CON FOCO ALTO CONTRASTE PARA TV
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnas),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(peliculasList, key = { index, movie -> "${movie.id}_$index" }) { index, movie ->
                        val itemModifier = if (index == 0) {
                            Modifier.focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier
                        }

                        PeliculaValidaCard(
                            movie = movie,
                            esTv = esTv,
                            modifier = itemModifier,
                            onFocus = {
                                if (!movie.imageUrl.isNullOrBlank()) {
                                    fondoActual = movie.imageUrl
                                }
                            },
                            onClick = { onMovieClick(movie) }
                        )
                    }
                }
            }
        }
    }
}
@Composable
fun ImagenConPelifondo(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .placeholder(R.drawable.pelifondo) // 🟢 Se muestra mientras descarga
            .error(R.drawable.pelifondo)       // 🟢 Se muestra si falla la URL o no hay internet
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
// =========================================================================
// 🟢 TARJETA DE PELÍCULA EFICIENTE PARA TV (MARQUESINA + BORDE DORADO)
// =========================================================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PeliculaValidaCard(
    movie: Modelo,
    esTv: Boolean,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.05f else 1.0f)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF141923))
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) DoradoBrillante else Color(0xFF262E3D),
                shape = RoundedCornerShape(10.dp)
            )
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    onFocus()
                }
            }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = 9.dp, topEnd = 9.dp))
        ) {
            ImagenConPelifondo(
                url = movie.imageUrl,
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // Insignia Activar en la esquina de la tarjeta
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        color = Color(0xEE2E7D32),
                        shape = RoundedCornerShape(topStart = 8.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = "ACTIVAR",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Título con marquesina cuando tiene el foco del TV
        val marqueeMod = if (isFocused) {
            Modifier.basicMarquee(iterations = Int.MAX_VALUE, delayMillis = 500, velocity = 35.dp)
        } else {
            Modifier
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) DoradoPrincipal else Color(0xFF0C1017))
                .padding(vertical = 5.dp, horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = movie.title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = if (esTv) 12.sp else 10.5.sp,
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