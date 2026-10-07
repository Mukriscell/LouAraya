package cl.duoc.louaraya.presentation.catalog

import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.FakeProductRepository
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase
import cl.duoc.louaraya.domain.usecase.TestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {

    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
    private val demoProducts = listOf(
        TestData.product1,
        TestData.product2,
        TestData.product3,
        TestData.product4
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initWithProductsLoadsAllCategories() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeProductRepository(demoProducts))

        assertEquals(CatalogUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(CatalogUiState.Success(demoProducts), viewModel.uiState.value)
        assertEquals(null, viewModel.selectedCategory.value)
    }

    @Test
    fun initWithoutProductsGoesFromLoadingToEmpty() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeProductRepository())

        assertEquals(CatalogUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(CatalogUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun initWhenLoadFailsEmitsError() = runTest(testDispatcher) {
        val viewModel = createViewModel(MutableProductRepository(demoProducts, shouldFail = true))

        assertEquals(CatalogUiState.Loading, viewModel.uiState.value)
        advanceUntilIdle()
        assertEquals(CatalogUiState.Error("Error de lectura"), viewModel.uiState.value)
    }

    @Test
    fun filtersEachOfficialCategoryAndTodosRestoresAllProducts() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeProductRepository(demoProducts))
        advanceUntilIdle()

        CampaignCategory.entries.forEach { category ->
            viewModel.selectCategory(category)
            assertEquals(category, viewModel.selectedCategory.value)
            assertEquals(
                CatalogUiState.Success(demoProducts.filter { it.campaignCategory == category }),
                viewModel.uiState.value
            )
        }

        viewModel.selectCategory(null)
        assertEquals(null, viewModel.selectedCategory.value)
        assertEquals(CatalogUiState.Success(demoProducts), viewModel.uiState.value)
    }

    @Test
    fun emptyCategoryCanReturnToTodos() = runTest(testDispatcher) {
        val viewModel = createViewModel(FakeProductRepository(listOf(TestData.product1)))
        viewModel.selectCategory(CampaignCategory.WEDDINGS)
        advanceUntilIdle()

        assertEquals(CampaignCategory.WEDDINGS, viewModel.selectedCategory.value)
        assertEquals(CatalogUiState.Empty, viewModel.uiState.value)

        viewModel.selectCategory(null)
        assertEquals(CatalogUiState.Success(listOf(TestData.product1)), viewModel.uiState.value)
    }

    @Test
    fun selectionSurvivesLoadingErrorAndRetry() = runTest(testDispatcher) {
        val repository = MutableProductRepository(demoProducts)
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.selectCategory(CampaignCategory.WEDDINGS)
        repository.shouldFail = true
        viewModel.loadProducts()
        assertEquals(CatalogUiState.Loading, viewModel.uiState.value)
        assertEquals(CampaignCategory.WEDDINGS, viewModel.selectedCategory.value)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CatalogUiState.Error)
        assertEquals(CampaignCategory.WEDDINGS, viewModel.selectedCategory.value)

        repository.shouldFail = false
        viewModel.loadProducts()
        advanceUntilIdle()

        assertEquals(CampaignCategory.WEDDINGS, viewModel.selectedCategory.value)
        assertEquals(CatalogUiState.Success(listOf(TestData.product3)), viewModel.uiState.value)
    }

    private fun createViewModel(repository: ProductRepository) =
        CatalogViewModel(GetProductsUseCase(repository))

    @Test
    fun olderLoadCannotReplaceNewerFilteredResults() = runTest(testDispatcher) {
        val firstLoad = CompletableDeferred<List<Product>>()
        val secondLoad = CompletableDeferred<List<Product>>()
        var loadCount = 0
        val repository = object : ProductRepository by FakeProductRepository() {
            override suspend fun getProducts(): List<Product> =
                if (loadCount++ == 0) firstLoad.await() else secondLoad.await()
        }
        val viewModel = createViewModel(repository)
        runCurrent()
        viewModel.selectCategory(CampaignCategory.WEDDINGS)
        viewModel.loadProducts()
        runCurrent()

        secondLoad.complete(listOf(TestData.product3))
        advanceUntilIdle()
        firstLoad.complete(listOf(TestData.product1))
        advanceUntilIdle()

        assertEquals(CampaignCategory.WEDDINGS, viewModel.selectedCategory.value)
        assertEquals(CatalogUiState.Success(listOf(TestData.product3)), viewModel.uiState.value)
    }

    @Test
    fun refreshAfterAdministrationKeepsFilterAndShowsUpdatedImageReference() = runTest(testDispatcher) {
        var current = demoProducts
        val repository = object : ProductRepository by FakeProductRepository() {
            override suspend fun getProducts(): List<Product> = current
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        viewModel.selectCategory(CampaignCategory.WEDDINGS)
        current = demoProducts.map { product ->
            if (product.id == TestData.product3.id) product.copy(imageReference = "product-images/admin.jpg") else product
        }

        viewModel.loadProducts()
        advanceUntilIdle()

        assertEquals(CampaignCategory.WEDDINGS, viewModel.selectedCategory.value)
        assertEquals(
            CatalogUiState.Success(listOf(TestData.product3.copy(imageReference = "product-images/admin.jpg"))),
            viewModel.uiState.value
        )
    }

    private class MutableProductRepository(
        private val products: List<Product>,
        var shouldFail: Boolean = false
    ) : ProductRepository {

        override suspend fun getProducts(): List<Product> =
            if (shouldFail) throw IllegalStateException("Error de lectura") else products

        override suspend fun getProductById(id: Long): Product? =
            products.firstOrNull { it.id == id }

        override suspend fun updateImageReference(id: Long, imageReference: String): Boolean = false
    }
}
