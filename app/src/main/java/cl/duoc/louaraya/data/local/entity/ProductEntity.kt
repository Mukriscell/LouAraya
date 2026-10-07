package cl.duoc.louaraya.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representación de un producto en la tabla local `products` de Room.
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "width_cm")
    val widthCm: Double,
    @ColumnInfo(name = "length_cm")
    val lengthCm: Double,
    @ColumnInfo(name = "material")
    val material: String,
    @ColumnInfo(name = "price")
    val price: Long,
    @ColumnInfo(name = "campaign_category", defaultValue = "'INTERIOR_DECORATION'")
    val campaignCategory: String
)
