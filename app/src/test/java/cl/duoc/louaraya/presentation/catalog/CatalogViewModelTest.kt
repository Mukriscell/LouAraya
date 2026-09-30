package cl.duoc.louaraya.presentation.catalog

import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.FakeProductRepository
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase
import cl.duoc.louaraya.domain.usecase.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_withProducts_goesFromLoadingToSuccess() = runTest(testDispatcher) {
        val products = listOf(TestData.product1, TestData.product2)
        val viewModel = createViewModel(FakeProductRepository(products))

        assertEquals(CatalogUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(CatalogUiState.Success(products), viewModel.uiState.value)
    }

    @Test
    fun init_withoutProducts_goesFromLoadingToEmpty() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeProductRepository())

        assertEquals(CatalogUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(CatalogUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun init_whenLoadFails_emitsError() = runTest(testDispatcher) {
        val viewModel = createViewModel(FailingProductRepository("Error de lectura"))

        assertEquals(CatalogUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(CatalogUiState.Error("Error de lectura"), viewModel.uiState.value)
    }

    private fun createViewModel(repository: ProductRepository) =
        CatalogViewModel(GetProductsUseCase(repository))

    /**
     * Repositorio que siempre falla, para simular un error de carga.
     */
    private class FailingProductRepository(
        private val message: String
    ) : ProductRepository {

        override suspend fun getProducts(): List<Product> =
            throw IllegalStateException(message)

        override suspend fun getProductById(id: Long): Product? =
            throw IllegalStateException(message)
    }
}
