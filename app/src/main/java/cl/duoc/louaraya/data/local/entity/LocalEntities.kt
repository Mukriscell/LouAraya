package cl.duoc.louaraya.data.local.entity

import androidx.room.*

@Entity(tableName = "users", indices = [Index(value = ["alias"], unique = true)])
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, val alias: String, val passwordHash: String, val passwordSalt: String,
    val role: String, val avatar: Int = 0, val contact: String = "",
    val notificationsEnabled: Boolean = true, val preferredCategory: String? = null,
    val avatarImageReference: String? = null
)
@Entity(tableName = "app_session")
data class SessionEntity(@PrimaryKey val id: Int = 1, val userId: Long)
@Entity(tableName = "favorites", primaryKeys = ["userId", "productId"])
data class FavoriteEntity(val userId: Long, val productId: Long)
@Entity(tableName = "collections")
data class CollectionEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val description: String)
@Entity(tableName = "collection_products", primaryKeys = ["collectionId", "productId"])
data class CollectionProductEntity(val collectionId: Long, val productId: Long)
@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String,
    val collectionId: Long, val startsOn: String, val endsOn: String, val published: Boolean,
    val targetCategory: String? = null
)
@Entity(tableName = "fair_events")
data class FairEventEntity(@PrimaryKey val id: Long, val name: String, val date: String, val location: String, val latitude: Double, val longitude: Double)
@Entity(tableName = "local_notices", indices = [Index(value = ["userId", "eventKey"], unique = true)])
data class NoticeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val userId: Long,
    val title: String, val body: String, val destination: String, val date: Long,
    val read: Boolean, val eventKey: String, val delivered: Boolean = false
)
@Entity(tableName = "qr_scans", indices = [Index(value = ["sessionKey"], unique = true)])
data class ScanEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val userId: Long, val destination: String, val date: Long, val sessionKey: String)

@Entity(tableName = "favorite_collections", primaryKeys = ["userId", "collectionId"])
data class FavoriteCollectionEntity(val userId: Long, val collectionId: Long)
@Entity(tableName = "exploration_journeys")
data class ExplorationJourneyEntity(@PrimaryKey val id: String, val userId: Long, val campaignId: Long, val collectionId: Long, val date: Long)
@Entity(tableName = "exploration_events")
data class ExplorationEventEntity(
    @PrimaryKey val eventKey: String, val userId: Long, val kind: String, val date: Long,
    val journeyId: String?, val campaignId: Long?, val collectionId: Long?,
    val productId: Long?, val requestId: Long?, val amount: Long?
)

@Entity(tableName = "list_dismissals", primaryKeys = ["userId", "kind", "itemId"])
data class ListDismissalEntity(val userId: Long, val kind: String, val itemId: Long)
