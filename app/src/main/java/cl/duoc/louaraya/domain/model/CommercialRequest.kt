package cl.duoc.louaraya.domain.model

/**
 * Registro de pedido o intención de compra/reserva.
 *
 * @property id identificador local de la solicitud; 0 antes de persistir una nueva.
 * @property productId pieza a la que corresponde la intención.
 * @property date fecha de la solicitud en milisegundos desde epoch (UTC).
 * @property anonymizedClient identificador ficticio o anonimizado del cliente.
 * @property total importe de la pieza al registrar la intención, en CLP sin decimales.
 * No representa un pago ni una confirmación comercial de la administradora.
 * @property status estado actual de la solicitud.
 */
data class CommercialRequest(
    val id: Long,
    val date: Long,
    val anonymizedClient: String,
    val total: Long,
    val status: RequestStatus,
    val productId: Long,
    val intention: RequestIntention,
    val comment: String? = null,
    val campaignCategory: CampaignCategory? = null
)
