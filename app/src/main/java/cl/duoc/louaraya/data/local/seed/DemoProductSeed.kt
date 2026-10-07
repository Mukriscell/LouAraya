package cl.duoc.louaraya.data.local.seed

import cl.duoc.louaraya.data.local.entity.ProductEntity
import cl.duoc.louaraya.domain.model.CampaignCategory

/**
 * DATOS DEMO TEMPORALES.
 *
 * Productos ficticios usados solo para visualizar el catálogo mientras no se
 * disponga del listado sintético oficial. No provienen del documento del caso
 * y deben reemplazarse cuando exista la fuente definitiva (listado oficial o API).
 * Su asignación cubre una categoría oficial por producto solo para demostrar filtros.
 */
object DemoProductSeed {

    val products: List<ProductEntity> = listOf(
        ProductEntity(
            id = 1L,
            name = "Tapiz Demo Aurora",
            widthCm = 60.0,
            lengthCm = 90.0,
            material = "Algodón",
            price = 45000L,
            campaignCategory = CampaignCategory.INTERIOR_DECORATION.name
        ),
        ProductEntity(
            id = 2L,
            name = "Colgante Demo Brisa",
            widthCm = 30.0,
            lengthCm = 50.0,
            material = "Cáñamo",
            price = 25000L,
            campaignCategory = CampaignCategory.FESTIVALS.name
        ),
        ProductEntity(
            id = 3L,
            name = "Camino de Mesa Demo",
            widthCm = 35.0,
            lengthCm = 180.0,
            material = "Algodón",
            price = 38000L,
            campaignCategory = CampaignCategory.WEDDINGS.name
        ),
        ProductEntity(
            id = 4L,
            name = "Portamacetero Demo",
            widthCm = 25.0,
            lengthCm = 110.0,
            material = "Yute",
            price = 18000L,
            campaignCategory = CampaignCategory.TRAVEL.name
        )
    )
}
