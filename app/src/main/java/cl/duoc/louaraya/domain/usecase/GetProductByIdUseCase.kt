package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository




class GetProductByIdUseCase(
    private val productRepository: ProductRepository
) {

    suspend operator fun invoke(id: Long): Product? =
        productRepository.getProductById(id)
}
