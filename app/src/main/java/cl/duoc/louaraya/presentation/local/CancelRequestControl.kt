package cl.duoc.louaraya.presentation.local

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import cl.duoc.louaraya.domain.model.CommercialRequest

@Composable
internal fun CancelRequestControl(request: CommercialRequest, userId: Long, vm: LocalAppViewModel) {
    var confirm by rememberSaveable(request.id) { mutableStateOf(false) }
    SecondaryAction("Cancelar solicitud") { confirm = true }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("¿Cancelar solicitud?") },
        text = { Text("Cancelarás la solicitud #${request.id} de ${quantityLabel(request.quantity)}. Se conservará en el historial, el taller recibirá un aviso y no se descontará stock. Esta solicitud no podrá reactivarse.", Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(enabled = !LocalBusy.current, onClick = {
            confirm = false
            vm.execute(message = "Solicitud cancelada.") { cancelRequest(request.id, userId) }
        }) { Text("Sí, cancelar") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Seguir con la solicitud") } })
}
