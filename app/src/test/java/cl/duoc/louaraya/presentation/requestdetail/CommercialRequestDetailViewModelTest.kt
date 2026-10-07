package cl.duoc.louaraya.presentation.requestdetail

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository
import cl.duoc.louaraya.domain.usecase.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommercialRequestDetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val requests = FakeCommercialRequestRepository(listOf(TestData.request1))
    private val products = FakeProductRepository(listOf(TestData.product1))
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun detailLinksRequestToCorrectProduct() = runTest(dispatcher) {
        val vm = CommercialRequestDetailViewModel(1, GetCommercialRequestByIdUseCase(requests), GetProductByIdUseCase(products))
        assertEquals(CommercialRequestDetailUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(CommercialRequestDetailUiState.Success(TestData.request1, TestData.product1), vm.uiState.value)
    }

    @Test
    fun missingRequestShowsNotFound() = runTest(dispatcher) {
        val vm = CommercialRequestDetailViewModel(99, GetCommercialRequestByIdUseCase(requests), GetProductByIdUseCase(products))
        advanceUntilIdle()
        assertEquals(CommercialRequestDetailUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun removedProductDoesNotHideHistoricalRequest() = runTest(dispatcher) {
        val vm = CommercialRequestDetailViewModel(1, GetCommercialRequestByIdUseCase(requests),
            GetProductByIdUseCase(FakeProductRepository()))
        advanceUntilIdle()
        assertEquals(CommercialRequestDetailUiState.Success(TestData.request1, null), vm.uiState.value)
    }

    @Test
    fun loadFailureCanBeRetried() = runTest(dispatcher) {
        var fail = true
        val repository = object : CommercialRequestRepository by requests {
            override suspend fun getRequestById(id: Long): CommercialRequest? =
                if (fail) error("Error de lectura") else requests.getRequestById(id)
        }
        val vm = CommercialRequestDetailViewModel(1, GetCommercialRequestByIdUseCase(repository), GetProductByIdUseCase(products))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CommercialRequestDetailUiState.Error)
        fail = false
        vm.loadRequest()
        advanceUntilIdle()
        assertEquals(CommercialRequestDetailUiState.Success(TestData.request1, TestData.product1), vm.uiState.value)
    }
}
