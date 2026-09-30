package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository

/**
 * Actualiza el estado de una solicitud y retorna la solicitud actualizada.
 */
class UpdateCommercialRequestStatusUseCase(
    private val commercialRequestRepository: CommercialRequestRepository
) {

    suspend operator fun invoke(id: Long, status: RequestStatus): CommercialRequest =
        commercialRequestRepository.updateRequestStatus(id, status)
}
