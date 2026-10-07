package cl.duoc.louaraya.presentation.createrequest

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateCommercialRequestViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val requests = FakeCommercialRequestRepository()
    private val products = FakeProductRepository(listOf(TestData.product1))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(
        repository: CommercialRequestRepository = requests,
        productRepository: ProductRepository = products,
        productId: Long = 1L
    ) = CreateCommercialRequestViewModel(productId, GetProductByIdUseCase(productRepository),
        CreateCommercialRequestUseCase(repository, productRepository) { 123L })

    @Test
    fun loadsProductAndSubmitsReservationWithCategory() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(CreateCommercialRequestUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(TestData.product1, (vm.uiState.value as CreateCommercialRequestUiState.Form).product)
        vm.setIntention(RequestIntention.RESERVATION)
        vm.setComment("Evento ficticio")
        vm.submit()
        assertTrue((vm.uiState.value as CreateCommercialRequestUiState.Form).isSaving)
        advanceUntilIdle()
        val saved = (vm.uiState.value as CreateCommercialRequestUiState.Saved).request
        assertEquals(RequestIntention.RESERVATION, saved.intention)
        assertEquals(TestData.product1.campaignCategory, saved.campaignCategory)
        assertEquals("Evento ficticio", saved.comment)
        assertEquals(listOf(saved), requests.requests)
    }

    @Test
    fun repeatedClicksAndEditsDuringSaveDoNotDuplicateOrAlterRequest() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.submit()
        vm.setIntention(RequestIntention.RESERVATION)
        vm.submit()
        advanceUntilIdle()
        vm.submit()
        vm.loadProduct()
        advanceUntilIdle()
        assertEquals(1, requests.requests.size)
        assertEquals(RequestIntention.PURCHASE, requests.requests.single().intention)
        assertTrue(vm.uiState.value is CreateCommercialRequestUiState.Saved)
    }

    @Test
    fun invalidCommentKeepsFormAndDoesNotPersist() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.setComment("x".repeat(501))
        vm.submit()
        advanceUntilIdle()
        val form = vm.uiState.value as CreateCommercialRequestUiState.Form
        assertFalse(form.isSaving)
        assertNotNull(form.error)
        assertEquals(501, form.comment.length)
        assertTrue(requests.requests.isEmpty())
    }

    @Test
    fun saveFailureKeepsFieldsAndAllowsSuccessfulRetry() = runTest(dispatcher) {
        var fail = true
        val repository = object : CommercialRequestRepository by requests {
            override suspend fun createRequest(request: CommercialRequest): CommercialRequest {
                if (fail) error("Disco lleno")
                return requests.createRequest(request)
            }
        }
        val vm = viewModel(repository)
        advanceUntilIdle()
        vm.setComment("Decoración ficticia")
        vm.submit()
        advanceUntilIdle()
        val form = vm.uiState.value as CreateCommercialRequestUiState.Form
        assertFalse(form.isSaving)
        assertNotNull(form.error)
        assertEquals("Decoración ficticia", form.comment)
        fail = false
        vm.submit()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CreateCommercialRequestUiState.Saved)
        assertEquals(1, requests.requests.size)
    }

    @Test
    fun missingProductCannotBeSubmitted() = runTest(dispatcher) {
        val vm = viewModel(productId = 99)
        advanceUntilIdle()
        assertEquals(CreateCommercialRequestUiState.NotFound, vm.uiState.value)
        vm.submit()
        advanceUntilIdle()
        assertTrue(requests.requests.isEmpty())
    }

    @Test
    fun deletedProductWhileFormIsOpenShowsNotFoundAndDoesNotPersist() = runTest(dispatcher) {
        var currentProduct: Product? = TestData.product1
        val mutableProducts = object : ProductRepository by products {
            override suspend fun getProductById(id: Long): Product? =
                currentProduct?.takeIf { it.id == id }
        }
        val vm = viewModel(productRepository = mutableProducts)
        advanceUntilIdle()
        vm.setComment("Requerimiento ficticio")
        currentProduct = null

        vm.submit()
        advanceUntilIdle()

        assertEquals(CreateCommercialRequestUiState.NotFound, vm.uiState.value)
        assertTrue(requests.requests.isEmpty())
    }

    @Test
    fun changedPriceIsShownAndRequiresAnotherSubmit() = runTest(dispatcher) {
        var currentProduct: Product? = TestData.product1
        val mutableProducts = object : ProductRepository by products {
            override suspend fun getProductById(id: Long): Product? =
                currentProduct?.takeIf { it.id == id }
        }
        val vm = viewModel(productRepository = mutableProducts)
        advanceUntilIdle()
        vm.setIntention(RequestIntention.RESERVATION)
        vm.setComment("Decoración ficticia")
        val updatedPrice = TestData.product1.price + 1000
        currentProduct = TestData.product1.copy(price = updatedPrice)

        vm.submit()
        advanceUntilIdle()

        val updatedForm = vm.uiState.value as CreateCommercialRequestUiState.Form
        assertFalse(updatedForm.isSaving)
        assertEquals(updatedPrice, updatedForm.product.price)
        assertNotNull(updatedForm.error)
        assertTrue(requests.requests.isEmpty())

        vm.submit()
        advanceUntilIdle()

        val saved = (vm.uiState.value as CreateCommercialRequestUiState.Saved).request
        assertEquals(updatedPrice, saved.total)
        assertEquals(RequestIntention.RESERVATION, saved.intention)
        assertEquals(TestData.product1.campaignCategory, saved.campaignCategory)
        assertEquals("Decoración ficticia", saved.comment)
    }

    @Test
    fun productLoadFailureCanBeRetried() = runTest(dispatcher) {
        var fail = true
        val repository = object : ProductRepository by products {
            override suspend fun getProductById(id: Long) =
                if (fail) error("Error de lectura") else products.getProductById(id)
        }
        val vm = viewModel(productRepository = repository)
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CreateCommercialRequestUiState.Error)
        fail = false
        vm.loadProduct()
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CreateCommercialRequestUiState.Form)
    }
}
