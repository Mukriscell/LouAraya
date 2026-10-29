package cl.duoc.louaraya.domain.repository

import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.model.Collection
import kotlinx.coroutines.flow.Flow


interface LocalRepository {

    fun observeChanges(): Flow<Unit>
    suspend fun <T> transaction(block: suspend () -> T): T
    suspend fun initialize()
    suspend fun session(): UserProfile?
    suspend fun signIn(alias: String, password: String): UserProfile
    suspend fun register(name: String, alias: String, password: String, avatar: Int, contact: String): UserProfile
    suspend fun signOut()
    suspend fun updateProfile(profile: UserProfile)
    suspend fun profiles(): List<UserProfile>
    suspend fun products(): List<Product>
    suspend fun saveProduct(product: Product): Long
    suspend fun adjustStock(productId: Long, delta: Int): Boolean
    suspend fun addStockMovement(movement: StockMovement)
    suspend fun stockMovements(): List<StockMovement>
    suspend fun hasStockMovementForRequest(requestId: Long): Boolean
    suspend fun requests(ownerId: Long?): List<CommercialRequest>
    suspend fun hiddenRequestIds(userId: Long): Set<Long>
    suspend fun dismissRequests(userId: Long, ids: List<Long>)
    suspend fun dismissNotices(userId: Long, ids: List<Long>)
    suspend fun request(id: Long): CommercialRequest?
    suspend fun saveRequest(request: CommercialRequest): CommercialRequest
    suspend fun updateStatus(id: Long, status: RequestStatus)
    suspend fun favoriteIds(userId: Long): Set<Long>
    suspend fun setFavorite(userId: Long, productId: Long, selected: Boolean)
    suspend fun collections(): List<Collection>
    suspend fun saveCollection(collection: Collection): Long
    suspend fun campaigns(): List<Campaign>
    suspend fun saveCampaign(campaign: Campaign): Long
    suspend fun events(): List<FairEvent>
    suspend fun notices(userId: Long): List<LocalNotice>
    suspend fun addNotice(notice: LocalNotice)
    suspend fun markNoticeRead(userId: Long, noticeId: Long)
    suspend fun markNoticeDelivered(userId: Long, noticeId: Long)
    suspend fun recordScan(scan: ScanRecord)
    suspend fun favoriteCollectionIds(userId: Long): Set<Long>
    suspend fun setFavoriteCollection(userId: Long, collectionId: Long, selected: Boolean)
    suspend fun saveJourney(journey: ExplorationJourney)
    suspend fun journey(id: String): ExplorationJourney?
    suspend fun journeys(userId: Long?): List<ExplorationJourney>
    suspend fun recordExploration(event: ExplorationEvent)
    suspend fun explorationEvents(userId: Long?): List<ExplorationEvent>
    suspend fun updateAgreement(requestId: Long, total: Long, date: Long)
}
