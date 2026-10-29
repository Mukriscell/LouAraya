package cl.duoc.louaraya.data.repository

import cl.duoc.louaraya.data.local.dao.ProductDao
import cl.duoc.louaraya.data.mapper.toDomain
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository




class ProductRepositoryImpl(
    private val productDao: ProductDao
) : ProductRepository {

    override suspend fun getProducts(): List<Product> =
        productDao.getAll().map { it.toDomain() }

    override suspend fun getProductById(id: Long): Product? =
        productDao.getById(id)?.toDomain()

    override suspend fun updateImageReference(id: Long, imageReference: String): Boolean =
        productDao.updateImageReference(id, imageReference) > 0
}
