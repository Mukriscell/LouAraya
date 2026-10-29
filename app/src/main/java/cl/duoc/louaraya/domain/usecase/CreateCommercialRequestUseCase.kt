package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.CreateCommercialRequestInput
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository
import cl.duoc.louaraya.domain.repository.ProductRepository




class CreateCommercialRequestUseCase(
    private val commercialRequestRepository: CommercialRequestRepository,
    private val productRepository: ProductRepository,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) {

    suspend operator fun invoke(input: CreateCommercialRequestInput): CommercialRequest {
        require(input.productId > 0) { "Selecciona una pieza válida." }
        require(input.displayedTotal >= 0) { "El total de la pieza no es válido." }
        val comment = input.comment?.trim()?.takeIf { it.isNotEmpty() }
        require((comment?.length ?: 0) <= MAX_COMMENT_LENGTH) {
            "El requerimiento debe tener como máximo $MAX_COMMENT_LENGTH caracteres."
        }
        val product = productRepository.getProductById(input.productId)
            ?: throw CommercialRequestProductUnavailableException()
        require(product.price >= 0) { "El precio de la pieza no es válido." }
        if (product.price != input.displayedTotal) {
            throw CommercialRequestProductPriceChangedException(product.price)
        }
        val request = CommercialRequest(
            id = 0,
            productId = product.id,
            date = currentTimeMillis(),
            intention = input.intention,
            anonymizedClient = DEMO_CLIENT_ID,
            comment = comment,
            total = FormValidation.requestTotal(product.price, input.quantity),

            status = RequestStatus.PENDING,
            campaignCategory = product.campaignCategory,
            quantity = input.quantity
        )
        return commercialRequestRepository.createRequest(request)
    }

    companion object {
        const val DEMO_CLIENT_ID = "CLIENTE-DEMO-001"
        const val MAX_COMMENT_LENGTH = 500
    }
}

class CommercialRequestProductUnavailableException : Exception(
    "El producto no existe o ya no está disponible."
)

class CommercialRequestProductPriceChangedException(val currentPrice: Long) : Exception(
    "El precio del producto cambió desde que se abrió el formulario."
)
