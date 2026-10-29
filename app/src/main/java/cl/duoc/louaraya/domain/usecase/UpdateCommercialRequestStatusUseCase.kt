package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository




class UpdateCommercialRequestStatusUseCase(
    private val commercialRequestRepository: CommercialRequestRepository
) {

    suspend operator fun invoke(id: Long, status: RequestStatus): CommercialRequest {
        val request = commercialRequestRepository.getRequestById(id) ?: throw NoSuchElementException("La solicitud no existe.")
        require(request.status != RequestStatus.CANCELLED || status == RequestStatus.CANCELLED) { "La solicitud está cancelada y no puede reactivarse." }
        if (status == RequestStatus.CANCELLED && request.status != RequestStatus.CANCELLED) {
            require(request.canBeCancelled) { "Una solicitud entregada no se puede cancelar." }
        }
        return commercialRequestRepository.updateRequestStatus(id, status)
    }
}
