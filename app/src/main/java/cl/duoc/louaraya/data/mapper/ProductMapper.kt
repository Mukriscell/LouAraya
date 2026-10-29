package cl.duoc.louaraya.data.mapper

import cl.duoc.louaraya.data.local.entity.ProductEntity
import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.Product




fun ProductEntity.toDomain(): Product = Product(
    id = id,
    name = name,
    widthCm = widthCm,
    lengthCm = lengthCm,
    material = material,
    price = price,
    campaignCategory = CampaignCategory.valueOf(campaignCategory),
    imageReference = imageReference,
    description = description, availability = availability, active = active, stock = stock
)




fun Product.toEntity(): ProductEntity = ProductEntity(
    id = id,
    name = name,
    widthCm = widthCm,
    lengthCm = lengthCm,
    material = material,
    price = price,
    campaignCategory = campaignCategory.name,
    imageReference = imageReference,
    description = description, availability = availability, active = active, stock = stock
)
