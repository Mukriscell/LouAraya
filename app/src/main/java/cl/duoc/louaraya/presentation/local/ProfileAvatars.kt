package cl.duoc.louaraya.presentation.local

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.AvatarStyle
import cl.duoc.louaraya.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal val avatarNames = AvatarStyle.entries.map { it.label }
internal val avatarArtwork = listOf(R.drawable.avatar_flor, R.drawable.avatar_sol, R.drawable.avatar_hoja, R.drawable.avatar_luna, R.drawable.avatar_nudo, R.drawable.avatar_aurora, R.drawable.avatar_mar, R.drawable.avatar_tierra)

@Composable
internal fun AvatarChoices(selected: Int, compact: Boolean = false, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (if (compact) listOf(0, 1, 2, 3, 6, 7, 5, 4) else avatarNames.indices.toList()).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { i ->
                    Column(Modifier.weight(1f).selectable(selected == i, enabled = !LocalBusy.current, role = Role.RadioButton, onClick = { onSelect(i) }).testTag("avatar-$i").padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.widthIn(max = if (compact) 64.dp else 72.dp).fillMaxWidth().aspectRatio(1f).testTag("avatar-artwork-$i").border(2.dp, if (selected == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background, CircleShape), contentAlignment = Alignment.Center) {
                            Image(painterResource(avatarArtwork[i]), "Avatar ${avatarNames[i]}", Modifier.fillMaxSize().padding(4.dp))
                        }
                        Text(avatarNames[i], style = if (compact) MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp) else MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProfileAvatar(profile: UserProfile, storage: ProductImageStorage, modifier: Modifier = Modifier) {
    val image by avatarBitmap(profile.avatarImageReference, storage, false)
    if (image != null) Image(requireNotNull(image), "Foto de perfil de ${profile.name}", modifier.clip(CircleShape), contentScale = ContentScale.Crop)
    else Image(painterResource(avatarArtwork.getOrElse(profile.avatar) { avatarArtwork[0] }), "Avatar ${avatarNames.getOrElse(profile.avatar) { avatarNames[0] }}", modifier)
}

@Composable
internal fun avatarBitmap(uri: String?, storage: ProductImageStorage, preview: Boolean): State<ImageBitmap?> = produceState<ImageBitmap?>(null, uri, preview, storage) {
    value = null
    if (uri != null) value = try {
        withContext(Dispatchers.IO) {
            val bytes = if (preview) storage.loadPreview(uri, 640) else storage.loadFromReference(uri, 640)
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

@Composable
internal fun AvatarPhotoScreen(vm: AvatarPhotoViewModel, storage: ProductImageStorage, profile: UserProfile, done: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cancel: () -> Unit = { if (!state.busy) scope.launch { if (vm.discard()) done() } }
    BackHandler { cancel() }
    val capture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), vm::cameraResult)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.takePhoto() else vm.showError("Permite el acceso a la cámara para tomar tu foto. También puedes elegir una ilustración en tu perfil.")
    }
    LaunchedEffect(state.launchUri) {
        state.launchUri?.let { uri -> if (vm.claimLaunch(uri)) try { capture.launch(uri.toUri()) } catch (_: Exception) { vm.cameraUnavailable() } }
    }
    LaunchedEffect(state.saved) { if (state.saved) done() }
    val preview by avatarBitmap(state.previewUri, storage, true)
    Scaffold(topBar = { BrandHeader("Foto de perfil", back = cancel, profile = cancel) }) { padding ->
        CompositionLocalProvider(LocalBusy provides state.busy) {
            Box(Modifier.padding(padding)) {
                Page(maxWidth = 600.dp, footer = {
                    if (state.previewUri != null && state.pendingUri == null) Action("Usar esta foto", enabled = preview != null, onClick = vm::save)
                    SecondaryAction("Cancelar y volver", onClick = cancel)
                }) {
                    Intro("Tu esencia, a tu manera", if (state.previewUri == null) "Un retrato\nmuy tuyo." else "¿Te gusta\nesta foto?", if (state.previewUri == null) "Toma una foto con tu cámara. Podrás revisarla antes de guardarla." else "Este es el recorte que se verá en tu perfil.")
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (preview != null) Image(requireNotNull(preview), "Vista previa de tu foto", Modifier.size(224.dp).clip(CircleShape).testTag("avatar-photo-preview"), contentScale = ContentScale.Crop)
                        else ProfileAvatar(profile, storage, Modifier.size(224.dp))
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("avatar-photo-error")) }
                    Action(if (state.pendingUri != null) "Continuar captura" else if (state.previewUri != null) "Tomar otra foto" else "Abrir cámara") {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) vm.takePhoto()
                        else permission.launch(Manifest.permission.CAMERA)
                    }
                    Note("Tu foto se guarda en este teléfono y se asocia a tu perfil. Puedes volver a una ilustración cuando quieras.")
                }
            }
        }
    }
}
