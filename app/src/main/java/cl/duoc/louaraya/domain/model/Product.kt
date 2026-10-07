package cl.duoc.louaraya.domain.model

/**
 * Ficha de producto de macramé.
 *
 * @property id código correlativo único del producto.
 * @property name nombre del diseño.
 * @property widthCm ancho en centímetros.
 * @property lengthCm largo en centímetros.
 * @property material tipo de material (por ejemplo, algodón o cáñamo).
 * @property price precio ficticio en pesos chilenos (CLP, sin decimales).
 * @property campaignCategory campaña oficial asociada a la pieza.
 */
data class Product(
    val id: Long,
    val name: String,
    val widthCm: Double,
    val lengthCm: Double,
    val material: String,
    val price: Long,
    val campaignCategory: CampaignCategory
)
