package cl.duoc.louaraya.domain.model

/**
 * Estados posibles de una solicitud de compra o reserva.
 */
enum class RequestStatus {
    EXPLORATION,
    PENDING,
    CONFIRMED,
    DELIVERED
}
