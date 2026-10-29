package cl.duoc.louaraya.data.repository

import cl.duoc.louaraya.data.local.dao.CommercialRequestDao
import cl.duoc.louaraya.data.mapper.toDomain
import cl.duoc.louaraya.data.mapper.toEntity
import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository





class CommercialRequestRepositoryImpl(
    private val dao: CommercialRequestDao
) : CommercialRequestRepository {
    override suspend fun getRequests(): List<CommercialRequest> = dao.getAll().map { it.toDomain() }

    override suspend fun getRequestById(id: Long): CommercialRequest? = dao.getById(id)?.toDomain()

    override suspend fun createRequest(request: CommercialRequest): CommercialRequest {
        require(request.id == 0L) { "Una nueva solicitud no debe tener un ID asignado." }
        val id = dao.insert(request.toEntity())
        return request.copy(id = id)
    }

    override suspend fun updateRequestStatus(id: Long, status: RequestStatus): CommercialRequest =
        dao.updateStatusAndGet(id, status.name).toDomain()
}
