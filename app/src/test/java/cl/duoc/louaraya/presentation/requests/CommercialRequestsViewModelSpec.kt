package cl.duoc.louaraya.presentation.requests

import cl.duoc.louaraya.domain.repository.CommercialRequestRepository
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestsUseCase
import cl.duoc.louaraya.domain.usecase.TestData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CommercialRequestsViewModelSpec : FunSpec({
    test("emite carga y recupera la lista después de reintentar una lectura fallida") {
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        try {
            runTest(dispatcher) {
                val requests = listOf(TestData.request1)
                val repository = mockk<CommercialRequestRepository>()
                coEvery { repository.getRequests() } throws IllegalStateException("Lectura fallida")
                val viewModel = CommercialRequestsViewModel(GetCommercialRequestsUseCase(repository))

                viewModel.uiState.value shouldBe CommercialRequestsUiState.Loading
                advanceUntilIdle()
                viewModel.uiState.value shouldBe CommercialRequestsUiState.Error(
                    "No fue posible cargar las solicitudes."
                )

                coEvery { repository.getRequests() } returns requests
                viewModel.loadRequests()
                advanceUntilIdle()
                viewModel.uiState.value shouldBe CommercialRequestsUiState.Success(requests)
                coVerify(exactly = 2) { repository.getRequests() }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
})
