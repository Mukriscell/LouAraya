package cl.duoc.louaraya.domain.repository

import cl.duoc.louaraya.domain.model.Product




interface ProductRepository {




    suspend fun getProducts(): List<Product>




    suspend fun getProductById(id: Long): Product?


    suspend fun updateImageReference(id: Long, imageReference: String): Boolean
}
