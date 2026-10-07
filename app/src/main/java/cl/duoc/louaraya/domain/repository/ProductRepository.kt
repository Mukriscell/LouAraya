package cl.duoc.louaraya.domain.repository

import cl.duoc.louaraya.domain.model.Product

/**
 * Contrato de acceso a los productos del catálogo.
 */
interface ProductRepository {

    /**
     * Obtiene todos los productos del catálogo.
     */
    suspend fun getProducts(): List<Product>

    /**
     * Obtiene un producto por su identificador, o null si no existe.
     */
    suspend fun getProductById(id: Long): Product?

    /** Replaces only the optional private image reference; false means no product matched. */
    suspend fun updateImageReference(id: Long, imageReference: String): Boolean
}
