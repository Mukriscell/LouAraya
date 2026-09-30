package cl.duoc.louaraya.data.local.seed

import cl.duoc.louaraya.data.local.entity.ProductEntity

/**
 * DATOS DEMO TEMPORALES.
 *
 * Productos ficticios usados solo para visualizar el catálogo mientras no se
 * disponga del listado sintético oficial. No provienen del documento del caso
 * y deben reemplazarse cuando exista la fuente definitiva (listado oficial o API).
 */
object DemoProductSeed {

    val products: List<ProductEntity> = listOf(
        ProductEntity(
            id = 1L,
            name = "Tapiz Demo Aurora",
            widthCm = 60.0,
            lengthCm = 90.0,
            material = "Algodón",
            price = 45000L
        ),
        ProductEntity(
            id = 2L,
            name = "Colgante Demo Brisa",
            widthCm = 30.0,
            lengthCm = 50.0,
            material = "Cáñamo",
            price = 25000L
        ),
        ProductEntity(
            id = 3L,
            name = "Camino de Mesa Demo",
            widthCm = 35.0,
            lengthCm = 180.0,
            material = "Algodón",
            price = 38000L
        ),
        ProductEntity(
            id = 4L,
            name = "Portamacetero Demo",
            widthCm = 25.0,
            lengthCm = 110.0,
            material = "Yute",
            price = 18000L
        )
    )
}
