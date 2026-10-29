package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.CreateCommercialRequestInput
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CreateCommercialRequestUseCaseTest {
    private val repository = FakeCommercialRequestRepository()
    private val productRepository = FakeProductRepository(listOf(TestData.product1))
    private val useCase = CreateCommercialRequestUseCase(repository, productRepository) { 123456L }

    @Test fun requestedQuantityMultipliesTheTrustedUnitPrice() = runBlocking {
        val result = useCase(input().copy(quantity = 4))
        assertEquals(4, result.quantity)
        assertEquals(TestData.product1.price * 4, result.total)
        assertEquals(TestData.product1.price, result.unitPrice)
    }

    @Test
    fun createsPendingPurchaseWithAssignedIdTrustedPriceAndProductCampaign() = runBlocking {
        val result = useCase(input())
        assertTrue(result.id > 0)
        assertEquals(1L, result.productId)
        assertEquals(123456L, result.date)
        assertEquals(TestData.product1.price, result.total)
        assertEquals(CreateCommercialRequestUseCase.DEMO_CLIENT_ID, result.anonymizedClient)
        assertEquals(RequestStatus.PENDING, result.status)
        assertNull(result.comment)
        assertEquals(TestData.product1.campaignCategory, result.campaignCategory)
        assertEquals(listOf(result), repository.requests)
    }

    @Test
    fun reservationUsesProductCampaignAndTrimsOptionalComment() = runBlocking {
        val result = useCase(CreateCommercialRequestInput(
            1L, RequestIntention.RESERVATION, TestData.product1.price,
            "  Decoración ficticia de evento  "
        ))
        assertEquals(RequestIntention.RESERVATION, result.intention)
        assertEquals(TestData.product1.campaignCategory, result.campaignCategory)
        assertEquals("Decoración ficticia de evento", result.comment)
    }

    @Test
    fun whitespaceCommentIsStoredAsNull() = runBlocking {
        assertNull(useCase(input(comment = "   ")).comment)
    }

    @Test
    fun missingProductDoesNotPersistRequest() = runBlocking {
        try {
            useCase(input(productId = 99L))
            fail("Debe informar que la pieza ya no existe")
        } catch (_: CommercialRequestProductUnavailableException) {
            assertTrue(repository.requests.isEmpty())
        }
    }

    @Test
    fun invalidProductIdDoesNotPersistRequest() = runBlocking {
        expectInvalid(input(productId = 0L, intention = RequestIntention.RESERVATION))
    }

    @Test
    fun overlongCommentDoesNotPersistRequest() = runBlocking {
        expectInvalid(input(comment = "x".repeat(501)))
    }

    @Test
    fun invalidPriceDoesNotPersistRequest() = runBlocking {
        val invalid = CreateCommercialRequestUseCase(repository,
            FakeProductRepository(listOf(TestData.product1.copy(price = -1))))
        try {
            invalid(input())
            fail("Debe rechazar un precio negativo")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.requests.isEmpty())
        }
    }

    @Test
    fun changedPriceIsReportedAndNotStoredUntilUserConfirmsUpdatedTotal() = runBlocking {
        val changedPrice = TestData.product1.price + 1000
        val changed = CreateCommercialRequestUseCase(
            repository,
            FakeProductRepository(listOf(TestData.product1.copy(price = changedPrice)))
        )

        try {
            changed(input())
            fail("Debe informar el nuevo total antes de registrar")
        } catch (error: CommercialRequestProductPriceChangedException) {
            assertEquals(changedPrice, error.currentPrice)
            assertTrue(repository.requests.isEmpty())
        }
    }

    @Test
    fun productRecategorizationDoesNotRewriteSavedRequestCampaignSnapshot() = runBlocking {
        val result = useCase(input())
        val previousCategory = result.campaignCategory
        val recategorizedProduct = TestData.product1.copy(campaignCategory = CampaignCategory.TRAVEL)
        val updatedUseCase = CreateCommercialRequestUseCase(
            repository,
            FakeProductRepository(listOf(recategorizedProduct))
        )

        updatedUseCase(input())

        assertEquals(previousCategory, repository.getRequestById(result.id)?.campaignCategory)
        assertEquals(CampaignCategory.TRAVEL, repository.requests.last().campaignCategory)
    }

    private suspend fun expectInvalid(input: CreateCommercialRequestInput) {
        try {
            useCase(input)
            fail("Debe rechazar la solicitud inválida")
        } catch (_: IllegalArgumentException) {
            assertTrue(repository.requests.isEmpty())
        }
    }

    private fun input(
        productId: Long = 1L,
        intention: RequestIntention = RequestIntention.PURCHASE,
        comment: String? = null
    ) = CreateCommercialRequestInput(
        productId = productId,
        intention = intention,
        displayedTotal = TestData.product1.price,
        comment = comment
    )
}
