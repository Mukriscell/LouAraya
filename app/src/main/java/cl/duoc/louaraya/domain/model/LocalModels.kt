package cl.duoc.louaraya.domain.model

enum class UserRole { CLIENT, ADMIN }

data class UserProfile(
    val id: Long = 0, val name: String, val alias: String,
    val role: UserRole = UserRole.CLIENT, val avatar: Int = 0,
    val contact: String = "", val notificationsEnabled: Boolean = true,
    val preferredCategory: String? = null,
    val avatarImageReference: String? = null
)

enum class AvatarStyle(val label: String) {
    FLOR("Flor"), SOL("Sol"), HOJA("Hoja"), LUNA("Luna"),
    NUDO("Nudo"), AURORA("Aurora"), MAR("Mar"), TIERRA("Tierra")
}

data class Collection(val id: Long = 0, val name: String, val description: String = "", val productIds: List<Long> = emptyList())
data class Campaign(
    val id: Long = 0, val name: String, val collectionId: Long,
    val startsOn: String, val endsOn: String, val published: Boolean = false,
    val targetCategory: CampaignCategory? = null
)
data class FairEvent(val id: Long, val name: String, val date: String, val location: String, val latitude: Double, val longitude: Double)
data class LocalNotice(
    val id: Long = 0, val userId: Long, val title: String, val body: String,
    val destination: String, val date: Long = System.currentTimeMillis(), val read: Boolean = false,
    val eventKey: String, val delivered: Boolean = false
)
data class ScanRecord(val id: Long = 0, val userId: Long, val destination: String, val date: Long, val sessionKey: String)
data class LocalSnapshot(
    val profile: UserProfile? = null, val products: List<Product> = emptyList(),
    val requests: List<CommercialRequest> = emptyList(), val favoriteIds: Set<Long> = emptySet(),
    val collections: List<Collection> = emptyList(), val campaigns: List<Campaign> = emptyList(),
    val events: List<FairEvent> = emptyList(), val notices: List<LocalNotice> = emptyList(),
    val favoriteCollectionIds: Set<Long> = emptySet(),
    val journeys: List<ExplorationJourney> = emptyList(),
    val explorationEvents: List<ExplorationEvent> = emptyList(),
    val stockMovements: List<StockMovement> = emptyList(),
    val hiddenRequestIds: Set<Long> = emptySet()
)

data class ExplorationJourney(val id: String, val userId: Long, val campaignId: Long, val collectionId: Long, val date: Long)
enum class ExplorationKind { CAMPAIGN_VIEW, COLLECTION_VIEW, PRODUCT_VIEW, PRODUCT_FAVORITE, COLLECTION_FAVORITE, REQUEST_CREATED, AMOUNT_AGREED, REQUEST_CANCELLED }
data class ExplorationEvent(
    val eventKey: String, val userId: Long, val kind: ExplorationKind, val date: Long,
    val journeyId: String? = null, val campaignId: Long? = null, val collectionId: Long? = null,
    val productId: Long? = null, val requestId: Long? = null, val amount: Long? = null
)
