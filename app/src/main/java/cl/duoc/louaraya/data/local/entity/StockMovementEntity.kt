package cl.duoc.louaraya.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "stock_movements", indices = [Index("productId"), Index(value = ["requestId"], unique = true)])
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val quantityDelta: Int,
    val stockAfter: Int,
    val type: String,
    val date: Long,
    val actorId: Long?,
    val note: String,
    val requestId: Long?
)
