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
     * Registra una nueva solicitud y retorna la solicitud creada.
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
