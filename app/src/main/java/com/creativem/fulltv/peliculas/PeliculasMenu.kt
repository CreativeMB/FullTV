package com.creativem.fulltv.peliculas

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.creativem.fulltv.R
import com.creativem.fulltv.menu.MenuPrincipalItem
import kotlinx.coroutines.delay

@Composable
fun MenuLateralVertical(
    saldoCasTV: String = "",
    isBannerVisible: Boolean,
    hasPromos: Boolean,
    menuFocusRequester: FocusRequester,
    bannerFocusRequester: FocusRequester,
    gridFocusRequester: FocusRequester,
    onMenuExpandChange: (Boolean) -> Unit = {},
    onItemClick: (String) -> Unit
) {
    // Detección responsiva de pantalla (TV vs Móvil)
    val config = LocalConfiguration.current
    val esTv = config.screenWidthDp > 600

    // Foco directo y asegurado en "TV" al iniciar la pantalla
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(250)
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // Lista fija de opciones
    val menuItems = remember {
        listOf(
            MenuPrincipalItem("TV", R.drawable.tv),
            MenuPrincipalItem("Alquila", R.drawable.cine),
            MenuPrincipalItem("Activar", R.drawable.cartelera),
            MenuPrincipalItem("Pedir", R.drawable.pedido),
            MenuPrincipalItem("Buscar", R.drawable.buscar),
            MenuPrincipalItem("Paquete", R.drawable.activacion),
            MenuPrincipalItem("Perfil", R.drawable.outline_contacts_product_24),
            MenuPrincipalItem("Cerrar", R.drawable.cerrrarp)
        )
    }

    // Ancho fijo ultra angosto (Sin animaciones de ensanchamiento)
    val menuWidth = if (esTv) 68.dp else 58.dp

    Box(
        modifier = Modifier
            .width(menuWidth)
            .fillMaxHeight()
            .zIndex(30f)
            .background(Color.Black.copy(alpha = 0.95f))
            .onFocusChanged {
                onMenuExpandChange(it.hasFocus)
            }
            .focusRequester(menuFocusRequester)
            .focusProperties {
                right = if (isBannerVisible && hasPromos) bannerFocusRequester else gridFocusRequester
            }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = if (esTv) 12.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (esTv) 4.dp else 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(menuItems) { index, item ->
                MenuComposeItemVertical(
                    item = item,
                    esTv = esTv,
                    itemModifier = if (index == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                    onClick = { onItemClick(item.name) }
                )
            }
        }
    }
}

// ====================================================================
// 🟢 ELEMENTO DE MENÚ: ICONO ARRIBA + TEXTO ABAJO (MÍNIMO RECURSO)
// ====================================================================
@Composable
fun MenuComposeItemVertical(
    item: MenuPrincipalItem,
    esTv: Boolean,
    itemModifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (esTv) 52.dp else 46.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .zIndex(if (isFocused) 5f else 0f)
            .then(itemModifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scale(if (isFocused) 1.05f else 1.0f)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isFocused) {
                        Modifier
                            .background(Color(0xFF262626))
                            .border(1.5.dp, ColorDorado, RoundedCornerShape(8.dp))
                    } else Modifier
                )
                .padding(horizontal = 2.dp, vertical = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icono arriba
            Image(
                painter = painterResource(id = item.iconResId),
                contentDescription = item.name,
                modifier = Modifier.size(if (esTv) 20.dp else 17.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Texto abajo (Siempre visible de forma instantánea)
            Text(
                text = item.name,
                color = if (isFocused) ColorDorado else Color.White,
                fontSize = if (esTv) 10.sp else 8.5.sp,
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}