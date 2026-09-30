package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository

/**
 * Registra una nueva solicitud de compra o reserva y retorna la solicitud creada.
 */
class CreateCommercialRequestUseCase(
    private val commercialRequestRepository: CommercialRequestRepository
) {

    suspend operator fun invoke(request: CommercialRequest): CommercialRequest =
        commercialRequestRepository.createRequest(request)
}
