package cl.duoc.louaraya.presentation.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.R

/** Capture IDs on opening the dialog so new items arriving meanwhile are not cleared. */
@Composable
internal fun ClearListControl(label: String, itemIds: List<Long>, userId: Long, requests: Boolean, vm: LocalAppViewModel, iconOnly: Boolean = false) {
    var pending by rememberSaveable(userId) { mutableStateOf<List<Long>?>(null) }
    if (iconOnly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        DesignIconButton(R.drawable.la_trash_2, label, enabled = itemIds.isNotEmpty()) { pending = itemIds.toList() }
    } else TextButton(onClick = { pending = itemIds.toList() }, enabled = itemIds.isNotEmpty() && !LocalBusy.current,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
    pending?.let { ids ->
        val requestsSummary = if (ids.size == 1) "Se quitará una solicitud" else "Se quitarán ${ids.size} solicitudes"
        val noticesSummary = if (ids.size == 1) "Se quitará una notificación" else "Se quitarán ${ids.size} notificaciones"
        AlertDialog(onDismissRequest = { pending = null },
            title = { Text(if (requests) "¿Limpiar solicitudes?" else "¿Limpiar notificaciones?") },
            text = { Text(if (requests)
                "$requestsSummary de tu lista, de todos los estados. El historial de negocio y los movimientos de stock se conservarán. Las otras cuentas mantendrán sus listas."
                else "$noticesSummary de tu bandeja y de la barra de Android. Las solicitudes seguirán disponibles y podrás recibir avisos nuevos.", Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(enabled = !LocalBusy.current, onClick = {
                pending = null
                vm.execute(message = if (requests) "Lista de solicitudes limpiada." else "Notificaciones limpiadas.") {
                    if (requests) clearRequests(ids, userId) else clearNotices(ids, userId)
                }
            }) { Text("Limpiar") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancelar") } })
    }
}
