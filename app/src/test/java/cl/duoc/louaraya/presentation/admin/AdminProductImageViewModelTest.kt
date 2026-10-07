package cl.duoc.louaraya.presentation.admin

import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.FakeProductRepository
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import cl.duoc.louaraya.domain.usecase.TestData
import cl.duoc.louaraya.domain.usecase.UpdateProductImageUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminProductImageViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test
    fun pickerCancellationAndCancelSelectionKeepPersistedImageUntouched() = runTest(dispatcher) {
        val product = TestData.product1.copy(imageReference = "product-images/old.jpg")
        val repository = FakeProductRepository(listOf(product))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        viewModel.selectImage("content://first")
        viewModel.selectImage(null)
        assertEquals("content://first", (viewModel.uiState.value as AdminProductImageUiState.Ready).previewSourceUri)
        viewModel.cancelSelection()
        val state = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertEquals(null, state.previewSourceUri)
        assertEquals("product-images/old.jpg", repository.getProductById(product.id)?.imageReference)
    }

    @Test
    fun saveShowsPersistedReferenceAndClearsTemporaryPreview() = runTest(dispatcher) {
        val repository = FakeProductRepository(listOf(TestData.product1))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        viewModel.selectImage("content://picked")
        viewModel.saveImage()
        advanceUntilIdle()

        val state = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertTrue(state.isSaved)
        assertEquals(null, state.previewSourceUri)
        assertEquals("product-images/updated.jpg", state.product.imageReference)
        assertEquals(state.product, repository.getProductById(state.product.id))
    }

    @Test
    fun storageFailureKeepsPreviewAndOldReferenceThenRetrySucceeds() = runTest(dispatcher) {
        val oldReference = "product-images/old.jpg"
        val repository = FakeProductRepository(listOf(TestData.product1.copy(imageReference = oldReference)))
        val storage = RetryableStorage()
        val viewModel = createViewModel(repository, storage)
        advanceUntilIdle()
        viewModel.selectImage("content://temporary-preview")

        viewModel.saveImage()
        advanceUntilIdle()
        val failed = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertFalse(failed.isSaving)
        assertEquals("copy failed", failed.error)
        assertEquals("content://temporary-preview", failed.previewSourceUri)
        assertEquals(oldReference, repository.getProductById(failed.product.id)?.imageReference)

        viewModel.saveImage()
        advanceUntilIdle()
        val retried = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertTrue(retried.isSaved)
        assertEquals("product-images/updated.jpg", repository.getProductById(retried.product.id)?.imageReference)
    }

    private fun createViewModel(
        repository: ProductRepository,
        storage: ProductImageStorage = object : ProductImageStorage {
            override suspend fun saveFromSource(sourceUri: String) = "product-images/updated.jpg"
            override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? = null
            override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? = null
            override suspend fun delete(reference: String) = Unit
        }
    ): AdminProductImageViewModel {
        return AdminProductImageViewModel(
            TestData.product1.id, GetProductByIdUseCase(repository),
            UpdateProductImageUseCase(repository, storage)
        )
    }

    private class RetryableStorage : ProductImageStorage {
        private var failFirstCopy = true
        override suspend fun saveFromSource(sourceUri: String): String {
            if (failFirstCopy) {
                failFirstCopy = false
                error("copy failed")
            }
            return "product-images/updated.jpg"
        }
        override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? = null
        override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? = null
        override suspend fun delete(reference: String) = Unit
    }
}
