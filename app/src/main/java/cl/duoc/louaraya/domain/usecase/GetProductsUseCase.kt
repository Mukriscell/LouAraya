package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository

/**
 * Obtiene todos los productos del catálogo.
 */
class GetProductsUseCase(
    private val productRepository: ProductRepository
) {

    suspend operator fun invoke(): List<Product> =
        productRepository.getProducts()
}
