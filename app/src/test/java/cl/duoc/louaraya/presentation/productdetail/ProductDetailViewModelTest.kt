package cl.duoc.louaraya.presentation.productdetail

import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.FakeProductRepository
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
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
class ProductDetailViewModelTest {

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
    fun init_withExistingProduct_goesFromLoadingToSuccess() = runTest(testDispatcher) {
        val repository = FakeProductRepository(listOf(TestData.product1, TestData.product2))
        val viewModel = createViewModel(productId = 2L, repository = repository)

        assertEquals(ProductDetailUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(ProductDetailUiState.Success(TestData.product2), viewModel.uiState.value)
    }

    @Test
    fun init_withMissingProduct_goesFromLoadingToNotFound() = runTest(testDispatcher) {
        val repository = FakeProductRepository(listOf(TestData.product1))
        val viewModel = createViewModel(productId = 99L, repository = repository)

        assertEquals(ProductDetailUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(ProductDetailUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun init_whenRepositoryFails_emitsError() = runTest(testDispatcher) {
        val viewModel = createViewModel(
            productId = 1L,
            repository = FailingProductRepository("Error de lectura")
        )

        assertEquals(ProductDetailUiState.Loading, viewModel.uiState.value)

        advanceUntilIdle()

        assertEquals(ProductDetailUiState.Error("Error de lectura"), viewModel.uiState.value)
    }

    @Test
    fun reloadReflectsImageUpdatedInAdministration() = runTest(testDispatcher) {
        var product = TestData.product1
        val repository = object : ProductRepository by FakeProductRepository(listOf(product)) {
            override suspend fun getProductById(id: Long): Product? = product.takeIf { it.id == id }
        }
        val viewModel = createViewModel(product.id, repository)
        advanceUntilIdle()
        product = product.copy(imageReference = "product-images/admin.jpg")

        viewModel.loadProduct()
        advanceUntilIdle()

        assertEquals(ProductDetailUiState.Success(product), viewModel.uiState.value)
    }

    private fun createViewModel(productId: Long, repository: ProductRepository) =
        ProductDetailViewModel(productId, GetProductByIdUseCase(repository))




    private class FailingProductRepository(
        private val message: String
    ) : ProductRepository {

        override suspend fun getProducts(): List<Product> =
            throw IllegalStateException(message)

        override suspend fun getProductById(id: Long): Product? =
            throw IllegalStateException(message)

        override suspend fun updateImageReference(id: Long, imageReference: String): Boolean =
            throw IllegalStateException(message)
    }
}
