package cl.duoc.louaraya.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


@Entity(tableName = "commercial_requests", indices = [Index(value = ["product_id"]), Index(value = ["owner_id"])])
data class CommercialRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    val date: Long,
    val intention: String,
    @ColumnInfo(name = "anonymized_client") val anonymizedClient: String,
    val comment: String?,
    val total: Long,
    val status: String,
    @ColumnInfo(name = "campaign_category") val campaignCategory: String?,
    @ColumnInfo(name = "owner_id", defaultValue = "1") val ownerId: Long = 1,
    val customization: String? = null,
    val campaignId: Long? = null,
    val sourceCollectionId: Long? = null,
    val journeyId: String? = null,
    val agreedTotal: Long? = null,
    val agreedAt: Long? = null,
    @ColumnInfo(defaultValue = "1") val quantity: Int = 1
)
