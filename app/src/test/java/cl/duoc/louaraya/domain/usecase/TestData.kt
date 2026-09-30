package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.model.RequestStatus

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
        price = 45000L
    )

    val product2 = Product(
        id = 2L,
        name = "Colgante Bohemio",
        widthCm = 30.0,
        lengthCm = 50.0,
        material = "Cáñamo",
        price = 25000L
    )

    val request1 = CommercialRequest(
        id = 1L,
        date = 1_700_000_000_000L,
        anonymizedClient = "CLIENTE-001",
        total = 45000L,
        status = RequestStatus.EXPLORATION
    )

    val request2 = CommercialRequest(
        id = 2L,
        date = 1_700_100_000_000L,
        anonymizedClient = "CLIENTE-002",
        total = 70000L,
        status = RequestStatus.PENDING
    )
}
