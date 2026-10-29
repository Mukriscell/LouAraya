package cl.duoc.louaraya.presentation.local

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.presentation.common.formatRequestDate
import cl.duoc.louaraya.presentation.common.displayClientReference

private fun ExplorationKind.label() = when (this) {
    ExplorationKind.CAMPAIGN_VIEW -> "Campaña abierta"
    ExplorationKind.COLLECTION_VIEW -> "Colección consultada"
    ExplorationKind.PRODUCT_VIEW -> "Pieza consultada"
    ExplorationKind.PRODUCT_FAVORITE -> "Pieza guardada en favoritos"
    ExplorationKind.COLLECTION_FAVORITE -> "Colección guardada en favoritos"
    ExplorationKind.REQUEST_CREATED -> "Solicitud registrada"
    ExplorationKind.AMOUNT_AGREED -> "Importe acordado registrado"
    ExplorationKind.REQUEST_CANCELLED -> "Solicitud cancelada por el cliente"
}

@Composable
internal fun RequestTrace(request: CommercialRequest, snapshot: LocalSnapshot) {
    Text("Origen y seguimiento", style = MaterialTheme.typography.titleLarge)
    val campaign = snapshot.campaigns.firstOrNull { it.id == request.campaignId }
    if (request.campaignId != null) DesignRow("Campaña de origen", campaign?.name ?: "Campaña #${request.campaignId}",
        snapshot.collections.firstOrNull { it.id == request.sourceCollectionId }?.name)
    else Text("Sin campaña vinculada a esta solicitud.", style = MaterialTheme.typography.bodySmall)
    val events = snapshot.explorationEvents.filter {
        it.requestId == request.id || (request.journeyId != null && it.journeyId == request.journeyId && it.date <= request.date)
    }
    events.forEach { event ->
        DesignRow(event.kind.label(), formatRequestDate(event.date),
            event.amount?.let(::priceLabel) ?: event.productId?.let { id -> snapshot.products.firstOrNull { it.id == id }?.name }, R.drawable.la_clipboard_list)
    }
}

@Composable
internal fun CampaignResultsScreen(snapshot: LocalSnapshot, id: Long, open: (String) -> Unit) {
    val campaign = snapshot.campaigns.firstOrNull { it.id == id }
    Page {
        if (campaign == null) StateContent("Campaña no disponible", "Vuelve a la gestión de campañas.")
        else {
            val journeys = snapshot.journeys.filter { it.campaignId == id }
            val requests = snapshot.requests.filter { it.campaignId == id }
            val events = snapshot.explorationEvents.filter { it.campaignId == id }
            Intro("Resultados de campaña", campaign.name, "Conoce el interés que despierta tu colección y consulta las solicitudes vinculadas.")
            DesignRow("Exploración", "${journeys.size} recorridos · ${journeys.map { it.userId }.distinct().size} clientes",
                "${events.count { it.kind == ExplorationKind.PRODUCT_VIEW }} consultas de piezas")
            DesignRow("Intenciones registradas", "${requests.size} solicitudes", "${requests.count { it.agreedTotal != null && it.status != RequestStatus.CANCELLED }} con importe acordado · ${requests.count { it.status == RequestStatus.CANCELLED }} canceladas")
            Note("Estas cifras reflejan consultas e intenciones de compra. No representan pagos ni ventas cobradas.")
            Text("Solicitudes de esta campaña", style = MaterialTheme.typography.titleLarge)
            if (requests.isEmpty()) Text("Aún no hay solicitudes vinculadas.")
            requests.forEach { request -> DesignRow("Solicitud #${request.id}", displayClientReference(request.anonymizedClient),
                priceLabel(request.agreedTotal ?: request.total), R.drawable.la_clipboard_list) { open("request/${request.id}") } }
        }
    }
}
