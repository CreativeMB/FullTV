package com.creativem.fulltv.peliculas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.creativem.fulltv.principal.Modelo
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit

@Composable
fun DialogoAlquileresActivos(
    alquileres: List<Modelo.AlquilerItem>,
    correoKey: String,
    onReproducir: (Modelo) -> Unit,
    onDismiss: () -> Unit
) {
    val ahoraInicial = remember { System.currentTimeMillis() }

    // Reconstrucción y filtrado de alquileres activos
    val listaAlquileres = remember {
        mutableStateListOf<Modelo.AlquilerItem>().apply {
            addAll(
                alquileres.filter { item ->
                    val tiempoTranscurrido = ahoraInicial - item.createdAt
                    val durationMillis = TimeUnit.MINUTES.toMillis(item.countdownMinutes.toLong())
                    tiempoTranscurrido >= 0 && (durationMillis - tiempoTranscurrido) > 0
                }
            )
        }
    }

    // Si la lista está vacía de entrada, se cierra de inmediato
    LaunchedEffect(listaAlquileres.size) {
        if (listaAlquileres.isEmpty()) {
            onDismiss()
        }
    }

    if (listaAlquileres.isEmpty()) return

    val botonCerrarFocusRequester = remember { FocusRequester() }
    val primerElementoFocusRequester = remember { FocusRequester() }

    // Asignar foco inicial en Android TV
    LaunchedEffect(Unit) {
        delay(300)
        try {
            primerElementoFocusRequester.requestFocus()
        } catch (_: Exception) {
            botonCerrarFocusRequester.requestFocus()
        }
    }

    Dialog(
        onDismissRequest = { /* No cancelable por fuera */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ColorFondoOscuro),
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(2.dp, ColorDorado, RoundedCornerShape(16.dp))
                .padding(2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // TÍTULO
                Text(
                    text = "🍿 ALQUILERES ACTIVOS",
                    color = ColorDorado,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // MENSAJE INFORMATIVO
                Text(
                    text = "Tienes películas listas con tiempo de visualización activo:\n(contenido en Perfil)",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // LISTA HORIZONTAL DE TARJETAS DE ALQUILER
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    items(listaAlquileres, key = { it.movie.id }) { item ->
                        val isFirst = listaAlquileres.indexOf(item) == 0

                        ItemAlquilerCard(
                            item = item,
                            modifier = if (isFirst) Modifier.focusRequester(primerElementoFocusRequester) else Modifier,
                            onExpired = {
                                // Limpieza en Firebase
                                val clave = item.movie.title.replace(".", "_")
                                    .replace("$", "_")
                                    .replace("#", "_")
                                    .replace("[", "_")
                                    .replace("]", "_")

                                FirebaseDatabase.getInstance().reference
                                    .child("usuarios")
                                    .child(correoKey)
                                    .child("alquileres")
                                    .child(clave)
                                    .removeValue()

                                listaAlquileres.remove(item)
                            },
                            onClick = {
                                onDismiss()
                                onReproducir(item.movie)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // BOTÓN ENTENDIDO
                BotonEntendido(
                    focusRequester = botonCerrarFocusRequester,
                    onClick = onDismiss
                )
            }
        }
    }
}

// ==========================================
// 🟢 TARJETA INDIVIDUAL CON TEMPORIZADOR
// ==========================================
@Composable
private fun ItemAlquilerCard(
    item: Modelo.AlquilerItem,
    modifier: Modifier = Modifier,
    onExpired: () -> Unit,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.1f else 1.0f,
        animationSpec = tween(150),
        label = "alquilerScale"
    )

    val durationMillis = remember(item) { TimeUnit.MINUTES.toMillis(item.countdownMinutes.toLong()) }
    var millisRestantes by remember(item) {
        val transcurrido = System.currentTimeMillis() - item.createdAt
        mutableLongStateOf(maxOf(0L, durationMillis - transcurrido))
    }

    // Cuenta regresiva por tarjeta en tiempo real
    LaunchedEffect(item.movie.id) {
        while (isActive && millisRestantes > 0) {
            delay(1000)
            val transcurrido = System.currentTimeMillis() - item.createdAt
            val rest = durationMillis - transcurrido
            if (rest <= 0L) {
                millisRestantes = 0L
                onExpired()
                break
            } else {
                millisRestantes = rest
            }
        }
    }

    val horas = TimeUnit.MILLISECONDS.toHours(millisRestantes)
    val minutos = TimeUnit.MILLISECONDS.toMinutes(millisRestantes) % 60
    val segundos = TimeUnit.MILLISECONDS.toSeconds(millisRestantes) % 60
    val tiempoFormateado = String.format("⏱ %02d:%02d:%02d", horas, minutos, segundos)

    Column(
        modifier = modifier
            .width(135.dp)
            .scale(scale)
            .zIndex(if (isFocused) 5f else 0f)
            .onFocusChanged { isFocused = it.isFocused }
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
                .width(135.dp)
                .height(190.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) ColorDorado else Color.Gray.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                )
        ) {
            GlideImageCustom(
                url = item.movie.imageUrl,
                modifier = Modifier.fillMaxSize()
            )

            // Etiqueta de tiempo sobrepuesta en la parte inferior del póster
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tiempoFormateado,
                    color = ColorDorado,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.movie.title,
            color = if (isFocused) ColorDorado else Color.White,
            fontSize = 12.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ==========================================
// 🟢 BOTÓN ENTENDIDO (COMPOSABLE ADAPTADO A TV)
// ==========================================
@Composable
private fun BotonEntendido(
    focusRequester: FocusRequester,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(150),
        label = "btnEntendidoScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .background(
                color = if (isFocused) Color.White else ColorDorado,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 42.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "ENTENDIDO",
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}