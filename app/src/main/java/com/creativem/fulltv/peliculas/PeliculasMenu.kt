package com.creativem.fulltv.peliculas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.creativem.fulltv.R
import com.creativem.fulltv.menu.MenuPrincipalItem
import kotlinx.coroutines.delay

@Composable
fun MenuLateralVertical(
    saldoCasTV: String,
    isBannerVisible: Boolean,
    hasPromos: Boolean,
    menuFocusRequester: FocusRequester,
    bannerFocusRequester: FocusRequester,
    gridFocusRequester: FocusRequester,
    onMenuExpandChange: (Boolean) -> Unit,
    onItemClick: (String) -> Unit
) {
    var isMenuFocused by remember { mutableStateOf(false) }

    // Foco exclusivo para el primer elemento ("TV")
    val firstItemFocusRequester = remember { FocusRequester() }

    // Forzar el cursor en "TV" inmediatamente al abrir esta pantalla
    LaunchedEffect(Unit) {
        delay(250)
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
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

    // Ancho compacto: 68.dp contraído / 138.dp expandido
    val menuWidth by animateDpAsState(
        targetValue = if (isMenuFocused) 138.dp else 68.dp,
        animationSpec = tween(120),
        label = "menuWidth"
    )

    Box(
        modifier = Modifier
            .width(menuWidth)
            .fillMaxHeight()
            .zIndex(30f)
            .background(Color.Black.copy(alpha = 0.95f))
            .onFocusChanged {
                isMenuFocused = it.hasFocus
                onMenuExpandChange(it.hasFocus)
            }
            .focusRequester(menuFocusRequester)
            .focusProperties {
                right = if (isBannerVisible && hasPromos) bannerFocusRequester else gridFocusRequester
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(menuItems) { index, item ->
                MenuComposeItemRow(
                    item = item,
                    isExpanded = isMenuFocused,
                    itemModifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    onClick = { onItemClick(item.name) }
                )
            }
        }
    }
}

@Composable
fun MenuComposeItemRow(
    item: MenuPrincipalItem,
    isExpanded: Boolean,
    itemModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val esCasTV = remember(item.name) { item.name.contains("CasTV") }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .zIndex(if (isFocused) 5f else 0f)
            .then(itemModifier)
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
                .then(
                    if (isFocused && !esCasTV) {
                        Modifier
                            .background(
                                color = Color(0xFF262626),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .border(1.5.dp, ColorDorado, RoundedCornerShape(8.dp))
                    } else Modifier
                )
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = item.iconResId),
                contentDescription = item.name,
                modifier = Modifier.size(22.dp),
                contentScale = ContentScale.Fit
            )

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(80))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.name,
                        color = when {
                            esCasTV -> ColorDorado
                            isFocused -> ColorDorado
                            else -> Color.White
                        },
                        fontSize = 12.sp,
                        fontWeight = if (isFocused || esCasTV) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}