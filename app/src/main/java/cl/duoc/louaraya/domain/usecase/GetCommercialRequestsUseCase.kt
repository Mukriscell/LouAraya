package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository




class GetCommercialRequestsUseCase(
    private val commercialRequestRepository: CommercialRequestRepository
) {

    suspend operator fun invoke(): List<CommercialRequest> =
        commercialRequestRepository.getRequests()
}
