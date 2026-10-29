package cl.duoc.louaraya.presentation.local

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.duoc.louaraya.R
import cl.duoc.louaraya.ui.theme.AtelierEditorial

@Composable
internal fun editorial(size: Int, line: Int = size, weight: FontWeight = FontWeight.Medium): TextStyle =
    MaterialTheme.typography.headlineMedium.copy(fontFamily = AtelierEditorial, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = 0.sp)

@Composable
internal fun AuthTopBar(route: String, back: () -> Unit) {
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars), contentAlignment = Alignment.TopCenter) {
        if (route == "welcome") Box(Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 66.dp).clipToBounds()) {
            Box(Modifier.matchParentSize().clipToBounds()) {
                Image(painterResource(R.drawable.auth_v2_welcome_hilo_suspendido), null,
                    Modifier.align(Alignment.TopEnd).offset(x = 10.dp).width(49.dp).wrapContentHeight(Alignment.Top, unbounded = true).requiredHeight(300.dp), contentScale = ContentScale.FillBounds)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 66.dp).padding(horizontal = 26.dp, vertical = 10.dp).authEntrance(50, 6f, 550, 650),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(painterResource(R.drawable.atelier_knot), null, Modifier.size(23.dp, 28.dp))
                Text("lou araya", Modifier.weight(1f), style = editorial(32, 40, FontWeight.SemiBold))
            }
        } else Row(Modifier.widthIn(max = 600.dp).fillMaxWidth().heightIn(min = 62.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            DesignIconButton(R.drawable.la_arrow_left, "Volver", onClick = back)
            Text(if (route == "login") "Iniciar sesión" else "Crear cuenta", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
    }
}

@Composable
internal fun AuthSeal(scene: AuthScene, modifier: Modifier = Modifier) {
    val size = when (scene) { AuthScene.WELCOME -> 76.dp; AuthScene.LOGIN -> 64.dp; AuthScene.REGISTER -> 60.dp }
    val background = when (scene) { AuthScene.WELCOME -> R.drawable.auth_v2_welcome_sello; AuthScene.LOGIN -> R.drawable.auth_v2_login_sello; AuthScene.REGISTER -> R.drawable.auth_v2_register_fondo_sello }
    val stitch = when (scene) { AuthScene.WELCOME -> R.drawable.auth_v2_welcome_puntada_artesanal; AuthScene.LOGIN -> R.drawable.auth_v2_login_puntada_artesanal; AuthScene.REGISTER -> R.drawable.auth_v2_register_puntada }
    // The decorative lettering scales with its seal, independently of form text scaling.
    Box(modifier.size(size).authSealMotion(scene)) {
        Image(painterResource(background), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        val density = androidx.compose.ui.platform.LocalDensity.current
        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 1f)) {
            Text("HECHO\nA MANO", Modifier.fillMaxWidth().padding(top = if (scene == AuthScene.WELCOME) 20.dp else 15.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = (if (scene == AuthScene.WELCOME) 9 else 8).sp, lineHeight = (if (scene == AuthScene.WELCOME) 14 else 12).sp),
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.background)
        }
        Image(painterResource(stitch), null, Modifier.align(Alignment.BottomCenter).padding(bottom = if (scene == AuthScene.WELCOME) 15.dp else 12.dp).size(16.dp, 7.dp))
    }
}

@Composable
internal fun AuthThread(scene: AuthScene, modifier: Modifier = Modifier) {
    val motion = LocalAuthMotion.current
    val drawable = when (scene) { AuthScene.WELCOME -> R.drawable.auth_v2_welcome_hilo_ondulado_pista; AuthScene.LOGIN -> R.drawable.auth_v2_login_hilo_pista; AuthScene.REGISTER -> R.drawable.auth_v2_register_hilo_ondulado_registro }
    val moving = scene != AuthScene.REGISTER
    Layout(modifier = modifier.fillMaxWidth().height(if (scene == AuthScene.WELCOME) 16.dp else 12.dp).clipToBounds(), content = {
        Image(painterResource(drawable), null, Modifier.graphicsLayer {
            if (moving) translationX = if (motion.reduced) 0f else -size.width / 2f * AuthTimeline.thread(motion.cycle.value)
            else alpha = if (motion.reduced) 1f else .5f + .5f * AuthTimeline.thread(motion.cycle.value)
        }, contentScale = ContentScale.FillBounds)
    }) { children, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val child = children.single().measure(Constraints.fixed(if (moving) width * 2 else width, height))
        layout(width, height) { child.placeRelative(0, 0) }
    }
}

@Composable
internal fun AuthSectionLabel(text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.auth_v2_register_linea_decorativa), null, Modifier.size(20.dp, 1.dp), contentScale = ContentScale.FillBounds)
        Text(text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
internal fun AuthDivider() {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.auth_v2_register_linea_izq), null, Modifier.weight(1f).height(1.dp), contentScale = ContentScale.FillBounds)
        Image(painterResource(R.drawable.auth_v2_register_nudo_separador), null, Modifier.size(12.dp, 6.dp))
        Image(painterResource(R.drawable.auth_v2_register_linea_izq), null, Modifier.weight(1f).height(1.dp), contentScale = ContentScale.FillBounds)
    }
}

@Composable
internal fun AuthFooterBow() {
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.auth_v2_login_separador_hilo), null, Modifier.size(48.dp, 6.dp), contentScale = ContentScale.FillBounds)
    }
}
