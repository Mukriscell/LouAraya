package cl.duoc.louaraya.data.local.seed

import cl.duoc.louaraya.data.local.entity.ProductEntity
import cl.duoc.louaraya.domain.model.CampaignCategory









object DemoProductSeed {


    val previousDisplayNames = mapOf(
        1L to "Tapiz Demo Aurora",
        2L to "Colgante Demo Brisa",
        3L to "Camino de Mesa Demo",
        4L to "Portamacetero Demo"
    )

    val products: List<ProductEntity> = listOf(
        ProductEntity(
            id = 1L,
            name = "Tapiz Aurora",
            widthCm = 60.0,
            lengthCm = 90.0,
            material = "Algodón",
            price = 45000L,
            campaignCategory = CampaignCategory.INTERIOR_DECORATION.name
        ),
        ProductEntity(
            id = 2L,
            name = "Colgante Brisa",
            widthCm = 30.0,
            lengthCm = 50.0,
            material = "Cáñamo",
            price = 25000L,
            campaignCategory = CampaignCategory.FESTIVALS.name
        ),
        ProductEntity(
            id = 3L,
            name = "Camino de Mesa",
            widthCm = 35.0,
            lengthCm = 180.0,
            material = "Algodón",
            price = 38000L,
            campaignCategory = CampaignCategory.WEDDINGS.name
        ),
        ProductEntity(
            id = 4L,
            name = "Portamacetero",
            widthCm = 25.0,
            lengthCm = 110.0,
            material = "Yute",
            price = 18000L,
            campaignCategory = CampaignCategory.TRAVEL.name
        )
    )
}
