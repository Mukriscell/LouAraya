package cl.duoc.louaraya.domain.model












data class CommercialRequest(
    val id: Long,
    val date: Long,
    val anonymizedClient: String,
    val total: Long,
    val status: RequestStatus,
    val productId: Long,
    val intention: RequestIntention,
    val comment: String? = null,
    val campaignCategory: CampaignCategory? = null,
    val ownerId: Long = 1,
    val customization: String? = null,
    val campaignId: Long? = null,
    val sourceCollectionId: Long? = null,
    val journeyId: String? = null,
    val agreedTotal: Long? = null,
    val agreedAt: Long? = null,
    val quantity: Int = 1
) {
    val unitPrice: Long get() = total / quantity
    val canBeCancelled: Boolean get() = status != RequestStatus.DELIVERED && status != RequestStatus.CANCELLED
}
