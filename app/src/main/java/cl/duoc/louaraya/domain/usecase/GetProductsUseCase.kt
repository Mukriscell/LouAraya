package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository




class GetProductsUseCase(
    private val productRepository: ProductRepository
) {

    suspend operator fun invoke(): List<Product> =
        productRepository.getProducts()
}
