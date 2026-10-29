package cl.duoc.louaraya.presentation.local

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.usecase.RegisterWithPhotoUseCase
import cl.duoc.louaraya.domain.usecase.FormValidation

@Composable
internal fun RegisterScreen(vm: LocalAppViewModel, photoVm: RegistrationPhotoViewModel, storage: ProductImageStorage, back: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var alias by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var avatar by rememberSaveable { mutableIntStateOf(0) }
    val state by vm.uiState.collectAsState()
    val photo by photoVm.state.collectAsState()
    val preview by avatarBitmap(photo.previewUri, storage, true)
    val context = LocalContext.current
    val registerAccount = remember(vm, storage) { RegisterWithPhotoUseCase(vm.useCases, storage) }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), photoVm::cameraResult)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) photoVm.takePhoto()
        else photoVm.showError("Permite el acceso a la cámara para tomar tu foto. También puedes elegir uno de los iconos.")
    }
    LaunchedEffect(photo.launchUri) {
        photo.launchUri?.let { uri ->
            if (photoVm.claimLaunch(uri)) try { capture.launch(uri.toUri()) }
            catch (_: Exception) { photoVm.cameraUnavailable() }
        }
    }
    val takePhoto: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) photoVm.takePhoto()
        else permission.launch(Manifest.permission.CAMERA)
    }
    CompositionLocalProvider(LocalBusy provides (LocalBusy.current || photo.busy)) {
        Page(maxWidth = 600.dp, topPadding = 0.dp, horizontalPadding = 0.dp, spacing = 0.dp, animated = false, footerDivider = false, footer = {
            Box(Modifier.authEntrance(280, 6f)) {
                Action(if (state.fieldErrors.isEmpty()) "Crear mi cuenta" else "Corregir mis datos", enabled = photo.pendingUri == null && (photo.previewUri == null || preview != null), loading = state.busy, centered = true) {
                    vm.execute { registerAccount(name, alias, password, avatar, contact, photo.previewUri, confirm) }
                }
            }
        }) {
            RegistrationHero()
            AuthThread(AuthScene.REGISTER)
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.authEntrance(120, 7f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuthSectionLabel("DATOS PERSONALES")
                    FormField(vm, "name", "Nombre", name, { name = it }, background = MaterialTheme.colorScheme.background)
                    FormField(vm, "alias", "Nombre de Usuario", alias, { alias = it }, background = MaterialTheme.colorScheme.background)
                    FormField(vm, "contact", "Contacto", contact, { contact = it }, labelBadge = "opcional", background = MaterialTheme.colorScheme.background, hint = "Puedes dejarlo vacío o indicar un correo o teléfono.")
                }
                Column(Modifier.authEntrance(180, 7f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuthDivider()
                    AuthSectionLabel("SEGURIDAD")
                    FormField(vm, "password", "Contraseña", password, { password = it; vm.clearFieldError("confirm") }, password = true, background = MaterialTheme.colorScheme.background)
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(FormValidation.REGISTRATION_PASSWORD_HINT, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.primary)
                    }
                    FormField(vm, "confirm", "Repetir contraseña", confirm, { confirm = it }, password = true, background = MaterialTheme.colorScheme.background)
                }
                Column(Modifier.authEntrance(240, 6f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AuthDivider()
                    AuthSectionLabel("TU IMAGEN DE PERFIL")
                    Text("Elige un icono o toma una foto con la cámara.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.secondary)
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (preview != null) Image(requireNotNull(preview), "Foto elegida para tu cuenta", Modifier.size(72.dp).clip(CircleShape).testTag("registration-photo-preview"), contentScale = ContentScale.Crop)
                            else Image(painterResource(avatarArtwork[avatar]), "Icono ${avatarNames[avatar]}", Modifier.size(72.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (photo.previewUri != null) "Tu fotografía" else avatarNames[avatar], style = editorial(20, 22))
                                Text(if (photo.previewUri != null) "Tu foto será tu imagen de perfil" else "Icono seleccionado", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                TextButton(takePhoto, enabled = !LocalBusy.current, contentPadding = PaddingValues(0.dp)) {
                                    Text(if (photo.pendingUri != null) "Continuar captura" else if (photo.previewUri != null) "Tomar otra foto" else "Tomar una foto", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                                }
                            }
                        }
                    }
                    photo.error?.let { Note(it, error = true) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Iconos disponibles", style = MaterialTheme.typography.bodySmall)
                        Image(painterResource(R.drawable.auth_v2_register_linea_relleno), null, Modifier.weight(1f).height(1.dp), contentScale = ContentScale.FillBounds)
                    }
                    AvatarChoices(if (photo.previewUri == null) avatar else -1, compact = true) { avatar = it; photoVm.useIllustration() }
                    if (photo.previewUri != null || photo.pendingUri != null) SecondaryAction("Usar un icono en lugar de la foto", onClick = photoVm::useIllustration)
                    SecondaryAction("Ya tengo cuenta", onClick = back)
                }
            }
            Image(painterResource(R.drawable.auth_v2_register_hilo_ondulado_separador_inferior), null, Modifier.fillMaxWidth().height(12.dp), contentScale = ContentScale.FillBounds)
        }
    }
}

@Composable
private fun RegistrationHero() {
    Column(Modifier.fillMaxWidth().authEntrance(40, 8f)) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
            Box(Modifier.fillMaxWidth()) {
                Image(painterResource(R.drawable.auth_v2_register_hilo_decorativo), null, Modifier.align(Alignment.TopEnd).width(49.dp).height(180.dp), contentScale = ContentScale.FillBounds)
                AuthSeal(AuthScene.REGISTER, Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 8.dp))
                Column(Modifier.padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(end = 64.dp).heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(R.drawable.atelier_knot), null, Modifier.size(32.dp, 37.dp))
                        Text("TU PRÓXIMA HISTORIA", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = MaterialTheme.colorScheme.primary)
                    }
                    AuthTitle("Hagamos espacio\na tus ideas.", editorial(44, 44))
                    Text("Crea tu cuenta y guarda lo que te inspira.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
        Image(painterResource(R.drawable.auth_v2_register_borde_organico_de_papel), null, Modifier.fillMaxWidth().aspectRatio(393f / 36f), contentScale = ContentScale.FillBounds)
    }
}
