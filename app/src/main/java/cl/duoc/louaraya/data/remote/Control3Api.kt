package cl.duoc.louaraya.data.remote

import cl.duoc.louaraya.domain.model.Campaign
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.ExplorationEvent
import cl.duoc.louaraya.domain.model.ExplorationJourney
import cl.duoc.louaraya.domain.model.LocalNotice
import cl.duoc.louaraya.domain.model.LocalSnapshot
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.model.StockMovement
import cl.duoc.louaraya.domain.model.StockMovementType
import cl.duoc.louaraya.domain.model.UserProfile
import cl.duoc.louaraya.domain.usecase.QuotationDetails
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

internal data class Credentials(val alias: String, val password: String)
internal data class Registration(val name: String, val alias: String, val password: String, val avatar: Int, val contact: String)
internal data class AccountResponse(val token: String, val profile: UserProfile)
internal data class ProfileChanges(
    val name: String, val alias: String, val avatar: Int, val contact: String,
    val notificationsEnabled: Boolean, val preferredCategory: String?
)

internal interface IdentityApi {
    @POST("auth/login") suspend fun login(@Body credentials: Credentials): AccountResponse
    @POST("auth/register") suspend fun register(@Body registration: Registration): AccountResponse
    @POST("auth/logout") suspend fun logout(@Header("Authorization") authorization: String)
    @PATCH("profiles/me") suspend fun updateProfile(@Header("Authorization") authorization: String, @Body changes: ProfileChanges): UserProfile
}

internal data class ProductChanges(
    val name: String, val widthCm: Double, val lengthCm: Double, val material: String,
    val price: Long, val campaignCategory: String, val description: String,
    val availability: String, val active: Boolean
)
internal data class NewRequest(
    val productId: Long, val intention: RequestIntention, val displayedPrice: Long,
    val quantity: Int, val comment: String, val details: QuotationDetails?,
    val journeyId: String?, val campaignId: Long?, val sourceCollectionId: Long?, val idempotencyKey: String = ""
)
internal data class StatusUpdate(val status: RequestStatus)
internal data class Agreement(val amount: Long)
internal data class StockUpdate(val quantity: Int, val type: StockMovementType, val note: String, val idempotencyKey: String = "")
internal data class Selection(val selected: Boolean)
internal data class Dismissal(val ids: List<Long>)
internal data class ApiError(val message: String?, val fieldErrors: Map<String, String>?)
internal data class PushDevice(val endpoint: String, val publicKey: String, val authSecret: String)

/** Nullable wire fields allow older servers to omit optional capability lists. */
internal data class WorkshopSnapshot(
    val profile: UserProfile,
    val products: List<Product>?,
    val requests: List<CommercialRequest>?,
    val favoriteIds: Set<Long>?,
    val collections: List<Collection>?,
    val campaigns: List<Campaign>?,
    val notices: List<LocalNotice>?,
    val favoriteCollectionIds: Set<Long>?,
    val journeys: List<ExplorationJourney>?,
    val explorationEvents: List<ExplorationEvent>?,
    val stockMovements: List<StockMovement>?,
    val hiddenRequestIds: Set<Long>?
) {
    fun toDomain() = LocalSnapshot(
        profile = profile,
        products = products.orEmpty(),
        requests = requests.orEmpty(),
        favoriteIds = favoriteIds.orEmpty(),
        collections = collections.orEmpty(),
        campaigns = campaigns.orEmpty(),
        notices = notices.orEmpty(),
        favoriteCollectionIds = favoriteCollectionIds.orEmpty(),
        journeys = journeys.orEmpty(),
        explorationEvents = explorationEvents.orEmpty(),
        stockMovements = stockMovements.orEmpty(),
        hiddenRequestIds = hiddenRequestIds.orEmpty()
    )
}

internal interface WorkshopApi {
    @GET("snapshot") suspend fun snapshot(@Header("Authorization") authorization: String): WorkshopSnapshot
    @POST("products") suspend fun createProduct(@Header("Authorization") authorization: String, @Body product: ProductChanges): Product
    @PATCH("products/{id}") suspend fun editProduct(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body product: ProductChanges): Product
    @DELETE("products/{id}") suspend fun deactivateProduct(@Header("Authorization") authorization: String, @Path("id") id: Long)
    @POST("products/{id}/stock-movements") suspend fun moveStock(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body stock: StockUpdate): StockMovement
    @POST("requests") suspend fun createRequest(@Header("Authorization") authorization: String, @Body request: NewRequest): CommercialRequest
    @PATCH("requests/{id}/status") suspend fun changeStatus(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body status: StatusUpdate): CommercialRequest
    @POST("requests/{id}/cancel") suspend fun cancelRequest(@Header("Authorization") authorization: String, @Path("id") id: Long): CommercialRequest
    @PATCH("requests/{id}/agreement") suspend fun agreeTotal(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body amount: Agreement): CommercialRequest
    @POST("collections") suspend fun createCollection(@Header("Authorization") authorization: String, @Body collection: Collection): Collection
    @PATCH("collections/{id}") suspend fun editCollection(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body collection: Collection): Collection
    @POST("campaigns") suspend fun createCampaign(@Header("Authorization") authorization: String, @Body campaign: Campaign): Campaign
    @PATCH("campaigns/{id}") suspend fun editCampaign(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body campaign: Campaign): Campaign
    @PUT("favorites/products/{id}") suspend fun favoriteProduct(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body selection: Selection)
    @PUT("favorites/collections/{id}") suspend fun favoriteCollection(@Header("Authorization") authorization: String, @Path("id") id: Long, @Body selection: Selection)
    @PATCH("notices/{id}/read") suspend fun markNoticeRead(@Header("Authorization") authorization: String, @Path("id") id: Long)
    @PATCH("notices/{id}/delivered") suspend fun markNoticeDelivered(@Header("Authorization") authorization: String, @Path("id") id: Long)
    @POST("notices/dismiss") suspend fun dismissNotices(@Header("Authorization") authorization: String, @Body dismissal: Dismissal)
    @POST("requests/dismiss") suspend fun dismissRequests(@Header("Authorization") authorization: String, @Body dismissal: Dismissal)
    @PUT("push/devices/{id}") suspend fun registerPushDevice(@Header("Authorization") authorization: String, @Path("id") id: String, @Body device: PushDevice)
    @DELETE("push/devices/{id}") suspend fun unregisterPushDevice(@Header("Authorization") authorization: String, @Path("id") id: String)
}
