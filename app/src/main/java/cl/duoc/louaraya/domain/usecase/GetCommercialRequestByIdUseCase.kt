package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository

/**
 * Obtiene una solicitud por su identificador, o null si no existe.
 */
class GetCommercialRequestByIdUseCase(
    private val commercialRequestRepository: CommercialRequestRepository
) {

    suspend operator fun invoke(id: Long): CommercialRequest? =
        commercialRequestRepository.getRequestById(id)
}
