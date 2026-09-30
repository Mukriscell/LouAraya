package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository

/**
 * Implementación en memoria de [ProductRepository] para pruebas unitarias.
 */
class FakeProductRepository(
    private val products: List<Product> = emptyList()
) : ProductRepository {

    override suspend fun getProducts(): List<Product> = products

    override suspend fun getProductById(id: Long): Product? =
        products.firstOrNull { it.id == id }
}
