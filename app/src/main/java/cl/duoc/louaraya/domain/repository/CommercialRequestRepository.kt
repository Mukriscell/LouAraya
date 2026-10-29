package cl.duoc.louaraya.domain.repository

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.RequestStatus




interface CommercialRequestRepository {




    suspend fun getRequests(): List<CommercialRequest>




    suspend fun getRequestById(id: Long): CommercialRequest?






    suspend fun createRequest(request: CommercialRequest): CommercialRequest




    suspend fun updateRequestStatus(
        id: Long,
        status: RequestStatus
    ): CommercialRequest
}
