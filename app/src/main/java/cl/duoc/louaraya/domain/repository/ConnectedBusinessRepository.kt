package cl.duoc.louaraya.domain.repository

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.ExplorationJourney
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.model.StockMovementType
import cl.duoc.louaraya.domain.usecase.QuotationDetails

/** Operations with server-owned transactions. The local demo keeps its original Room operations. */
internal interface ConnectedBusinessRepository : LocalRepository {
    val connectionWarning: String?
    suspend fun refreshConnected(force: Boolean = false)
    suspend fun deactivateProduct(id: Long)
    suspend fun changeStock(productId: Long, quantity: Int, type: StockMovementType, note: String)
    suspend fun createRequest(
        productId: Long, intention: RequestIntention, displayedPrice: Long, comment: String,
        details: QuotationDetails?, journey: ExplorationJourney?, quantity: Int
    ): CommercialRequest
    suspend fun cancelRequest(id: Long)
    suspend fun agreeTotal(requestId: Long, amount: Long)
}
