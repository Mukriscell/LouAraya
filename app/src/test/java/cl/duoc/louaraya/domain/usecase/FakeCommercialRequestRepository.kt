package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository

/**
 * Implementación en memoria de [CommercialRequestRepository] para pruebas unitarias.
 */
class FakeCommercialRequestRepository(
    initialRequests: List<CommercialRequest> = emptyList()
) : CommercialRequestRepository {

    val requests = initialRequests.toMutableList()

    override suspend fun getRequests(): List<CommercialRequest> = requests.toList()

    override suspend fun getRequestById(id: Long): CommercialRequest? =
        requests.firstOrNull { it.id == id }

    override suspend fun createRequest(request: CommercialRequest): CommercialRequest {
        require(request.id == 0L)
        val saved = request.copy(id = (requests.maxOfOrNull { it.id } ?: 0L) + 1)
        requests.add(saved)
        return saved
    }

    override suspend fun updateRequestStatus(
        id: Long,
        status: RequestStatus
    ): CommercialRequest {
        val index = requests.indexOfFirst { it.id == id }
        if (index == -1) throw NoSuchElementException("La solicitud no existe.")
        val updated = requests[index].copy(status = status)
        requests[index] = updated
        return updated
    }
}
