package cl.duoc.louaraya.domain.repository

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.RequestStatus

/**
 * Contrato de acceso a las solicitudes de compra o reserva.
 */
interface CommercialRequestRepository {

    /**
     * Obtiene todas las solicitudes registradas.
     */
    suspend fun getRequests(): List<CommercialRequest>

    /**
     * Obtiene una solicitud por su identificador, o null si no existe.
     */
    suspend fun getRequestById(id: Long): CommercialRequest?

    /**
     * Registra una nueva solicitud (id = 0) y retorna su identificador asignado.
     * La generación local del ID pertenece a data, no al consumidor del contrato.
     * La integración futura debe distinguir estos IDs locales de los IDs del servidor.
     */
    suspend fun createRequest(request: CommercialRequest): CommercialRequest

    /**
     * Actualiza el estado de una solicitud y retorna la solicitud actualizada.
     */
    suspend fun updateRequestStatus(
        id: Long,
        status: RequestStatus
    ): CommercialRequest
}
