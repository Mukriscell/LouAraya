package cl.duoc.louaraya.domain.model













data class Product(
    val id: Long,
    val name: String,
    val widthCm: Double,
    val lengthCm: Double,
    val material: String,
    val price: Long,
    val campaignCategory: CampaignCategory,
    val imageReference: String? = null,
    val description: String = "",
    val availability: String = "AVAILABLE",
    val active: Boolean = true,
    val stock: Int = 0
)
