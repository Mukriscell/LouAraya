package cl.duoc.louaraya.presentation.local

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.duoc.louaraya.R

@Composable
internal fun WelcomeScreen(login: () -> Unit, register: () -> Unit) {
    Page(maxWidth = 600.dp, topPadding = 0.dp, horizontalPadding = 0.dp, spacing = 0.dp, animated = false) {
        WelcomeCarousel()
        Column(Modifier.padding(horizontal = 26.dp)) {
            Text("MACRAMÉ · DISEÑO CHILENO", Modifier.authEntrance(180, 6f, 550, 650), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clearAndSetSemantics { text = AnnotatedString("Cada nudo,\nuna historia."); heading() }) {
                Text("Cada nudo,", Modifier.authEntrance(300, 14f, 550, 650), style = editorial(52, 50))
                Text("una historia.", Modifier.padding(start = 22.dp).authEntrance(460, 14f, 550, 650), style = editorial(52, 52).copy(fontStyle = FontStyle.Italic), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(16.dp))
            Text("Piezas hechas despacio para dar vida a tus espacios. Descubre, guarda y crea a tu manera.", Modifier.authEntrance(620, 10f, 550, 650), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp), color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.authEntrance(760, 8f, 550, 650), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Piezas únicas", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.primary)
                Image(painterResource(R.drawable.auth_v2_welcome_nudo_separador), null, Modifier.size(12.dp, 6.dp))
                Text("A tu medida", style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(22.dp))
            Box(Modifier.authEntrance(900, 10f, 550, 650)) { Action("Comenzar", centered = true, cornerRadius = 4.dp, onClick = login) }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.authEntrance(1030, 8f, 550, 650)) { SecondaryAction("Crear cuenta", onClick = register) }
        }
    }
}

@Composable
private fun WelcomeCarousel() {
    val motion = LocalAuthMotion.current
    var offset by rememberSaveable { mutableFloatStateOf(0f) }
    var frozenPage by rememberSaveable { mutableIntStateOf(0) }
    val position by remember(motion) { derivedStateOf { if (motion.reduced) frozenPage.toFloat() else AuthTimeline.carousel(motion.cycle.value + offset) } }
    val selected by remember(motion) { derivedStateOf { if (position >= .5f) 1 else 0 } }
    SideEffect { if (!motion.reduced && frozenPage != selected) frozenPage = selected }
    fun show(index: Int) {
        frozenPage = index
        offset = (if (index == 0) 0f else 4_000f) - motion.cycle.value
    }
    val names = listOf("Tapiz Aurora", "Colgante Brisa")
    val photos = listOf(R.drawable.product_tapiz_aurora, R.drawable.product_colgante_brisa)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val ratio = maxWidth.value / 393f
        val photoHeight = 310.dp * ratio
        Box(Modifier.fillMaxWidth().height(photoHeight + 46.dp * ratio).testTag("welcome-artwork")) {
            Box(Modifier.fillMaxWidth().height(photoHeight).clipToBounds()
                .clickable(onClickLabel = "Mostrar siguiente pieza") { show(1 - selected) }
                .semantics(mergeDescendants = true) {
                    contentDescription = names[selected]
                    stateDescription = "${selected + 1} de 2"
                    customActions = names.mapIndexed { index, name -> CustomAccessibilityAction("Mostrar $name") { show(index); true } }
                }) {
                photos.forEachIndexed { index, photo ->
                    Box(Modifier.matchParentSize().graphicsLayer { translationX = (index - position) * size.width }.clearAndSetSemantics { }) {
                        Image(painterResource(photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        Box(Modifier.fillMaxWidth().height(140.dp * ratio).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .32f)))))
                        Text(names[index].uppercase(), Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 25.dp * ratio).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = .28f)).padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp), color = MaterialTheme.colorScheme.background)
                    }
                }
            }
            Image(painterResource(R.drawable.auth_v2_welcome_borde_organico_de_papel), null, Modifier.fillMaxWidth().offset(y = photoHeight - 26.dp * ratio).height(72.dp * ratio), contentScale = ContentScale.FillBounds)
            AuthSeal(AuthScene.WELCOME, Modifier.align(Alignment.TopEnd).padding(end = 28.dp).offset(y = photoHeight - 81.dp * ratio))
            Box(Modifier.align(Alignment.TopCenter).offset(y = photoHeight - 14.dp * ratio).size(76.dp, 48.dp)
                .clickable(onClickLabel = "Cambiar pieza destacada") { show(1 - selected) }.semantics { stateDescription = "${selected + 1} de 2" }, contentAlignment = Alignment.Center) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.auth_v2_welcome_dot_activo), null, Modifier.size(20.dp, 6.dp).testTag("welcome-dot-0").graphicsLayer { alpha = 1f - .75f * position })
                    Image(painterResource(R.drawable.auth_v2_welcome_dot_inactivo), null, Modifier.size(6.dp).testTag("welcome-dot-1").graphicsLayer { alpha = .25f + .75f * position })
                }
            }
            AuthThread(AuthScene.WELCOME, Modifier.offset(y = photoHeight + 26.dp * ratio))
        }
    }
}

@Composable
internal fun LoginScreen(vm: LocalAppViewModel, register: () -> Unit) {
    var alias by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val state by vm.uiState.collectAsState()
    Page(maxWidth = 600.dp, topPadding = 0.dp, horizontalPadding = 0.dp, spacing = 0.dp, animated = false, footerDivider = false, footer = {
        Column(Modifier.authEntrance(240, 6f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthFooterBow()
            Action("Iniciar sesión", loading = state.busy, centered = true) { vm.execute { signIn(alias, password) } }
            SecondaryAction("Crear cuenta", onClick = register)
        }
    }) {
        LoginHero()
        AuthThread(AuthScene.LOGIN)
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 20.dp).authEntrance(80, 9f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("DE VUELTA AL TALLER", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = MaterialTheme.colorScheme.primary)
            AuthTitle("Qué bueno\nverte de nuevo.", editorial(40, 42, FontWeight.SemiBold))
            Text("Tus piezas guardadas y tus solicitudes te esperan aquí.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 19.sp), color = MaterialTheme.colorScheme.secondary)
        }
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 20.dp).authEntrance(160, 8f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FormField(vm, "alias", "Nombre de Usuario", alias, { alias = it; vm.clearFieldError("password") })
            FormField(vm, "password", "Contraseña", password, { password = it }, password = true)
            Text("Ingresa con el nombre de usuario que elegiste al crear tu cuenta.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.product_colgante_brisa), "Colgante Brisa", Modifier.size(64.dp, 72.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Hecho despacio.\nPara quedarse.", style = editorial(18, 20, FontWeight.SemiBold))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Image(painterResource(R.drawable.auth_v2_login_nudo_separador), null, Modifier.size(10.dp, 5.dp))
                            Text("MACRAMÉ · CHILE", style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginHero() {
    Box(Modifier.fillMaxWidth().height(158.dp).clipToBounds()) {
        Image(painterResource(R.drawable.product_tapiz_aurora), null, Modifier.fillMaxWidth().wrapContentHeight(Alignment.Bottom, unbounded = true).requiredHeight(220.dp).authPhotoMotion(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxWidth().height(140.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
        AuthSeal(AuthScene.LOGIN, Modifier.align(Alignment.TopEnd).padding(end = 28.dp).offset(y = 66.dp))
        Row(Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Image(painterResource(R.drawable.atelier_knot), null, Modifier.size(16.dp, 20.dp))
            Text("lou araya", style = editorial(22, 28, FontWeight.SemiBold))
        }
    }
}
