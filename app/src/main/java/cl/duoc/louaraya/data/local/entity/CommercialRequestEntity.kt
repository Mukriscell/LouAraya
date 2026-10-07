package cl.duoc.louaraya.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Mantiene la referencia a la pieza aun si el catálogo cambia posteriormente. */
@Entity(tableName = "commercial_requests", indices = [Index(value = ["product_id"])])
data class CommercialRequestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "product_id") val productId: Long,
    val date: Long,
    val intention: String,
    @ColumnInfo(name = "anonymized_client") val anonymizedClient: String,
    val comment: String?,
    val total: Long,
    val status: String,
    @ColumnInfo(name = "campaign_category") val campaignCategory: String?
)
