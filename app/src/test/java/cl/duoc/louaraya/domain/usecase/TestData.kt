package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.model.RequestIntention

/**
 * Datos ficticios compartidos por las pruebas de Use Cases.
 */
object TestData {

    val product1 = Product(
        id = 1L,
        name = "Tapiz Luna",
        widthCm = 60.0,
        lengthCm = 90.0,
        material = "Algodón",
        price = 45000L,
        campaignCategory = CampaignCategory.INTERIOR_DECORATION
    )

    val product2 = Product(
        id = 2L,
        name = "Colgante Bohemio",
        widthCm = 30.0,
        lengthCm = 50.0,
        material = "Cáñamo",
        price = 25000L,
        campaignCategory = CampaignCategory.FESTIVALS
    )

    val product3 = Product(
        id = 3L,
        name = "Camino de Mesa Demo",
        widthCm = 35.0,
        lengthCm = 180.0,
        material = "Algodón",
        price = 38000L,
        campaignCategory = CampaignCategory.WEDDINGS
    )

    val product4 = Product(
        id = 4L,
        name = "Portamacetero Demo",
        widthCm = 25.0,
        lengthCm = 110.0,
        material = "Yute",
        price = 18000L,
        campaignCategory = CampaignCategory.TRAVEL
    )

    val request1 = CommercialRequest(
        id = 1L,
        date = 1_700_000_000_000L,
        anonymizedClient = "CLIENTE-001",
        total = 45000L,
        status = RequestStatus.EXPLORATION,
        productId = 1L,
        intention = RequestIntention.PURCHASE
    )

    val request2 = CommercialRequest(
        id = 2L,
        date = 1_700_100_000_000L,
        anonymizedClient = "CLIENTE-002",
        total = 70000L,
        status = RequestStatus.PENDING,
        productId = 2L,
        intention = RequestIntention.RESERVATION
    )
}
