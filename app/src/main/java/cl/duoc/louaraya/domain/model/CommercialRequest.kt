package cl.duoc.louaraya.domain.model

/**
 * Registro de pedido o intención de compra/reserva.
 *
 * @property id identificador de la solicitud.
 * @property date fecha de la solicitud en milisegundos desde epoch (UTC).
 * @property anonymizedClient identificador ficticio o anonimizado del cliente.
 * @property total total acordado en pesos chilenos (CLP, sin decimales).
 * @property status estado actual de la solicitud.
 */
data class CommercialRequest(
    val id: Long,
    val date: Long,
    val anonymizedClient: String,
    val total: Long,
    val status: RequestStatus
)
