package cl.duoc.louaraya.presentation.local

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cl.duoc.louaraya.domain.model.UserProfile
import cl.duoc.louaraya.push.CampaignPush

@Composable
internal fun CampaignPushPreferences(profile: UserProfile, push: CampaignPush) {
    val state by push.state.collectAsState()
    val registration = state.registration?.takeIf { it.userId == profile.id }
    Text("Avisos de campañas", style = MaterialTheme.typography.labelLarge)
    when {
        registration?.ready == true -> Text("Avisos de campañas activos en este dispositivo.", style = MaterialTheme.typography.bodySmall)
        registration != null -> Text("Conectando los avisos con ntfy…", style = MaterialTheme.typography.bodySmall)
        !profile.notificationsEnabled -> Text("Activa Recibir avisos y guarda tu perfil para conectar ntfy.", style = MaterialTheme.typography.bodySmall)
        else -> Text("Usa ntfy para recibir novedades del taller aunque la app esté cerrada.", style = MaterialTheme.typography.bodySmall)
    }
    state.issue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (registration == null) {
        SecondaryAction("Activar avisos de campañas con ntfy", enabled = profile.notificationsEnabled) { push.activate(profile.id) }
    } else {
        SecondaryAction("Desactivar avisos de campañas") { push.deactivate(profile.id) }
    }
}
