package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository

/**
 * Implementación en memoria de [ProductRepository] para pruebas unitarias.
 */
class FakeProductRepository(
    private val products: List<Product> = emptyList()
) : ProductRepository {

    private val mutableProducts = products.toMutableList()

    override suspend fun getProducts(): List<Product> = mutableProducts.toList()

    override suspend fun getProductById(id: Long): Product? =
        mutableProducts.firstOrNull { it.id == id }

    override suspend fun updateImageReference(id: Long, imageReference: String): Boolean {
        val index = mutableProducts.indexOfFirst { it.id == id }
        if (index < 0) return false
        mutableProducts[index] = mutableProducts[index].copy(imageReference = imageReference)
        return true
    }
}
