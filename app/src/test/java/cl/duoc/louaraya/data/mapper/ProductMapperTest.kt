package cl.duoc.louaraya.data.mapper

import cl.duoc.louaraya.data.local.entity.ProductEntity
import cl.duoc.louaraya.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductMapperTest {

    private val entity = ProductEntity(
        id = 1L,
        name = "Tapiz Luna",
        widthCm = 60.5,
        lengthCm = 90.25,
        material = "Algodón",
        price = 45000L
    )

    private val product = Product(
        id = 1L,
        name = "Tapiz Luna",
        widthCm = 60.5,
        lengthCm = 90.25,
        material = "Algodón",
        price = 45000L
    )

    @Test
    fun toDomain_mapsAllFields() {
        assertEquals(product, entity.toDomain())
    }

    @Test
    fun toEntity_mapsAllFields() {
        assertEquals(entity, product.toEntity())
    }

    @Test
    fun roundTrip_preservesProduct() {
        assertEquals(product, product.toEntity().toDomain())
    }
}
