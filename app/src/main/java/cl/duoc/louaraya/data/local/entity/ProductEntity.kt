package cl.duoc.louaraya.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey




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
    val campaignCategory: String,
    @ColumnInfo(name = "image_reference")
    val imageReference: String? = null,
    @ColumnInfo(defaultValue = "''") val description: String = "",
    @ColumnInfo(defaultValue = "'AVAILABLE'") val availability: String = "AVAILABLE",
    @ColumnInfo(defaultValue = "1") val active: Boolean = true,
    @ColumnInfo(defaultValue = "0") val stock: Int = 0
)
