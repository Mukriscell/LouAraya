package cl.duoc.louaraya.presentation.local

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.duoc.louaraya.LouArayaApplication


@Composable
private fun permissionRevision(): Int {
    val owner = LocalLifecycleOwner.current
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return revision
}

@Composable
internal fun NotificationPermissionButton() {
    val context = LocalContext.current
    val revision = permissionRevision()
    var requested by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        requested++
        (context.applicationContext as? LouArayaApplication)?.container?.localNotificationCoordinator?.refresh()
    }
    var settingsError by remember { mutableStateOf<String?>(null) }
    val enabled = remember(revision, requested) { AndroidSystemNotifications(context).enabled() }
    Text(if (enabled) "Notificaciones del sistema: habilitadas" else "Notificaciones del sistema: deshabilitadas. Tu bandeja sigue disponible.")
    if (!enabled) Action("Configurar notificaciones") {
        val opened = notificationSystemCall(false) {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && requested == 0) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            true
        }
        settingsError = if (opened) null else "No pudimos abrir la configuración. Puedes intentarlo desde Ajustes de Android. Tu bandeja sigue disponible."
    }
    settingsError?.let { Text(it) }
}

@Composable
internal fun PermissionSummary() {
    val context = LocalContext.current
    val revision = permissionRevision()
    val camera = remember(revision) { ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
    Text("Cámara: ${if (camera) "permitida" else "sin permiso"}\nUbicación: no requerida para consultar eventos.")
    var settingsError by remember { mutableStateOf(false) }
    Action("Configurar permisos") {
        settingsError = !notificationSystemCall(false) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            true
        }
    }
    if (settingsError) Text("No pudimos abrir los permisos. Puedes configurarlos desde Ajustes de Android.")
}


@Composable
internal fun LocalNotificationEffect(coordinator: LocalNotificationCoordinator, deferDelivery: Boolean = false) {
    val revision = permissionRevision()
    LaunchedEffect(coordinator, revision, deferDelivery) {
        coordinator.refresh(deferDelivery)
    }
}
