package cl.duoc.louaraya.domain.model


data class CreateCommercialRequestInput(
    val productId: Long,
    val intention: RequestIntention,

    val displayedTotal: Long,
    val comment: String? = null,
    val quantity: Int = 1
)
