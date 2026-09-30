package cl.duoc.louaraya.data.mapper

import cl.duoc.louaraya.data.local.entity.ProductEntity
import cl.duoc.louaraya.domain.model.Product

/**
 * Convierte una entidad de Room en un modelo de dominio.
 */
fun ProductEntity.toDomain(): Product = Product(
    id = id,
    name = name,
    widthCm = widthCm,
    lengthCm = lengthCm,
    material = material,
    price = price
)

/**
 * Convierte un modelo de dominio en una entidad de Room.
 */
fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id,
    name = name,
    widthCm = widthCm,
    lengthCm = lengthCm,
    material = material,
    price = price
)
