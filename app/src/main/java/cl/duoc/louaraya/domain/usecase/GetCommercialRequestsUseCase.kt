package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository

/**
 * Obtiene todas las solicitudes de compra o reserva registradas.
 */
class GetCommercialRequestsUseCase(
    private val commercialRequestRepository: CommercialRequestRepository
) {

    suspend operator fun invoke(): List<CommercialRequest> =
        commercialRequestRepository.getRequests()
}
