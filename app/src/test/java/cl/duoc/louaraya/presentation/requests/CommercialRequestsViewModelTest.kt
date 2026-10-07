package cl.duoc.louaraya.presentation.requests

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository
import cl.duoc.louaraya.domain.usecase.FakeCommercialRequestRepository
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestsUseCase
import cl.duoc.louaraya.domain.usecase.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommercialRequestsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun loadsStoredRequests() = runTest(dispatcher) {
        val requests = listOf(TestData.request1, TestData.request2)
        val vm = CommercialRequestsViewModel(GetCommercialRequestsUseCase(FakeCommercialRequestRepository(requests)))
        assertEquals(CommercialRequestsUiState.Loading, vm.uiState.value)
        advanceUntilIdle()
        assertEquals(CommercialRequestsUiState.Success(requests), vm.uiState.value)
    }

    @Test
    fun emptyRepositoryShowsEmptyState() = runTest(dispatcher) {
        val vm = CommercialRequestsViewModel(GetCommercialRequestsUseCase(FakeCommercialRequestRepository()))
        advanceUntilIdle()
        assertEquals(CommercialRequestsUiState.Empty, vm.uiState.value)
    }

    @Test
    fun loadFailureCanBeRetried() = runTest(dispatcher) {
        val fake = FakeCommercialRequestRepository(listOf(TestData.request1))
        var fail = true
        val repository = object : CommercialRequestRepository by fake {
            override suspend fun getRequests(): List<CommercialRequest> =
                if (fail) error("Error de lectura") else fake.getRequests()
        }
        val vm = CommercialRequestsViewModel(GetCommercialRequestsUseCase(repository))
        advanceUntilIdle()
        assertTrue(vm.uiState.value is CommercialRequestsUiState.Error)
        fail = false
        vm.loadRequests()
        advanceUntilIdle()
        assertEquals(CommercialRequestsUiState.Success(fake.requests), vm.uiState.value)
    }
}
