package cl.duoc.louaraya.presentation.admin

import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.FakeProductRepository
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import cl.duoc.louaraya.domain.usecase.TestData
import cl.duoc.louaraya.domain.usecase.UpdateProductImageUseCase
import androidx.lifecycle.SavedStateHandle
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

    @Test
    fun cameraResultIsOnlyPersistedAfterConfirmationAndCleansCapture() = runTest(dispatcher) {
        val oldReference = "product-images/old.jpg"
        val product = TestData.product1.copy(imageReference = oldReference)
        val repository = FakeProductRepository(listOf(product))
        val storage = CameraStorage()
        val viewModel = createViewModel(repository, storage)
        advanceUntilIdle()

        viewModel.prepareCameraCapture()
        advanceUntilIdle()
        val prepared = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertEquals(storage.captureUri, prepared.pendingCameraUri)
        assertEquals(storage.captureUri, prepared.cameraLaunchUri)
        assertTrue(viewModel.consumeCameraLaunchRequest(storage.captureUri))
        assertFalse(viewModel.consumeCameraLaunchRequest(storage.captureUri))

        viewModel.onCameraCaptureResult(saved = true)
        advanceUntilIdle()
        val preview = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertEquals(storage.captureUri, preview.previewSourceUri)
        assertEquals(storage.captureUri, preview.selectedCameraUri)
        assertEquals(oldReference, repository.getProductById(product.id)?.imageReference)
        assertTrue(storage.captureUri in storage.liveCaptures)

        viewModel.saveImage()
        advanceUntilIdle()
        val saved = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertTrue(saved.isSaved)
        assertEquals("product-images/updated.jpg", repository.getProductById(product.id)?.imageReference)
        assertFalse(storage.captureUri in storage.liveCaptures)
    }

    @Test
    fun cameraCancellationKeepsPreviousSelectionAndStoredReference() = runTest(dispatcher) {
        val oldReference = "product-images/old.jpg"
        val product = TestData.product1.copy(imageReference = oldReference)
        val repository = FakeProductRepository(listOf(product))
        val storage = CameraStorage()
        val viewModel = createViewModel(repository, storage)
        advanceUntilIdle()
        viewModel.selectImage("content://previous-gallery-selection")

        viewModel.prepareCameraCapture()
        advanceUntilIdle()
        viewModel.onCameraCaptureResult(saved = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertEquals("content://previous-gallery-selection", state.previewSourceUri)
        assertEquals(null, state.pendingCameraUri)
        assertEquals(oldReference, repository.getProductById(product.id)?.imageReference)
        assertFalse(storage.captureUri in storage.liveCaptures)
    }

    @Test
    fun cameraSaveFailureDiscardsCameraDraftButPreservesOldImage() = runTest(dispatcher) {
        val oldReference = "product-images/old.jpg"
        val product = TestData.product1.copy(imageReference = oldReference)
        val repository = FakeProductRepository(listOf(product))
        val storage = CameraStorage(failSave = true)
        val viewModel = createViewModel(repository, storage)
        advanceUntilIdle()
        viewModel.prepareCameraCapture()
        advanceUntilIdle()
        viewModel.onCameraCaptureResult(saved = true)
        advanceUntilIdle()

        viewModel.saveImage()
        advanceUntilIdle()

        val state = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertFalse(state.isSaving)
        assertEquals("camera save failed", state.error)
        assertEquals(null, state.previewSourceUri)
        assertEquals(oldReference, repository.getProductById(product.id)?.imageReference)
        assertFalse(storage.captureUri in storage.liveCaptures)
    }

    @Test
    fun cameraPreparationAndLaunchFailuresKeepPreviousPreviewAndStoredImage() = runTest(dispatcher) {
        val oldReference = "product-images/old.jpg"
        val product = TestData.product1.copy(imageReference = oldReference)
        val repository = FakeProductRepository(listOf(product))
        val storage = CameraStorage(failPreparation = true)
        val viewModel = createViewModel(repository, storage)
        advanceUntilIdle()
        viewModel.selectImage("content://previous-gallery-selection")

        viewModel.prepareCameraCapture()
        advanceUntilIdle()
        val preparationFailed = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertFalse(preparationFailed.isPreparingCameraCapture)
        assertEquals("content://previous-gallery-selection", preparationFailed.previewSourceUri)
        assertEquals(oldReference, repository.getProductById(product.id)?.imageReference)
        assertTrue(storage.liveCaptures.isEmpty())

        storage.failPreparation = false
        viewModel.prepareCameraCapture()
        advanceUntilIdle()
        viewModel.onCameraLaunchFailed(storage.captureUri)
        advanceUntilIdle()
        val launchFailed = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertEquals("content://previous-gallery-selection", launchFailed.previewSourceUri)
        assertEquals(null, launchFailed.pendingCameraUri)
        assertEquals(oldReference, repository.getProductById(product.id)?.imageReference)
        assertTrue(storage.liveCaptures.isEmpty())
    }

    @Test
    fun invalidCameraResultCleansCaptureAndKeepsPreviousPreview() = runTest(dispatcher) {
        val oldReference = "product-images/old.jpg"
        val product = TestData.product1.copy(imageReference = oldReference)
        val repository = FakeProductRepository(listOf(product))
        val storage = CameraStorage(invalidCameraPreview = true)
        val viewModel = createViewModel(repository, storage)
        advanceUntilIdle()
        viewModel.selectImage("content://previous-gallery-selection")
        viewModel.prepareCameraCapture()
        advanceUntilIdle()

        viewModel.onCameraCaptureResult(saved = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertEquals("content://previous-gallery-selection", state.previewSourceUri)
        assertEquals(null, state.pendingCameraUri)
        assertTrue(state.error?.isNotBlank() == true)
        assertEquals(oldReference, repository.getProductById(product.id)?.imageReference)
        assertTrue(storage.liveCaptures.isEmpty())
    }

    @Test
    fun replacingCameraPreviewWithGallerySelectionDeletesCameraDraft() = runTest(dispatcher) {
        val repository = FakeProductRepository(listOf(TestData.product1))
        val storage = CameraStorage()
        val viewModel = createViewModel(repository, storage)
        advanceUntilIdle()
        viewModel.prepareCameraCapture()
        advanceUntilIdle()
        viewModel.onCameraCaptureResult(saved = true)
        advanceUntilIdle()
        assertEquals(storage.captureUri, (viewModel.uiState.value as AdminProductImageUiState.Ready).previewSourceUri)

        viewModel.selectImage("content://replacement-gallery-image")
        advanceUntilIdle()

        val replaced = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertEquals("content://replacement-gallery-image", replaced.previewSourceUri)
        assertEquals(null, replaced.selectedCameraUri)
        assertTrue(storage.liveCaptures.isEmpty())
        viewModel.cancelSelection()
        advanceUntilIdle()
        assertEquals(null, (viewModel.uiState.value as AdminProductImageUiState.Ready).previewSourceUri)
    }

    @Test
    fun cameraResultArrivingBeforeReloadCompletesIsRestoredFromSavedState() = runTest(dispatcher) {
        val product = TestData.product1
        val repository = FakeProductRepository(listOf(product))
        val storage = CameraStorage()
        val savedStateHandle = SavedStateHandle(mapOf(
            AdminProductImageSavedStateKeys.PENDING_CAMERA_URI to storage.captureUri,
            AdminProductImageSavedStateKeys.CAMERA_LAUNCH_URI to null
        ))
        storage.liveCaptures += storage.captureUri

        val viewModel = createViewModel(repository, storage, savedStateHandle)
        viewModel.onCameraCaptureResult(saved = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as AdminProductImageUiState.Ready
        assertEquals(storage.captureUri, state.previewSourceUri)
        assertEquals(storage.captureUri, state.selectedCameraUri)
        assertTrue(storage.preservedCaptureSets.any { storage.captureUri in it })
    }

    private fun createViewModel(
        repository: ProductRepository,
        storage: ProductImageStorage = object : ProductImageStorage {
            override suspend fun saveFromSource(sourceUri: String) = "product-images/updated.jpg"
            override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? = null
            override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? = null
            override suspend fun delete(reference: String) = Unit
        },
        savedStateHandle: SavedStateHandle = SavedStateHandle()
    ): AdminProductImageViewModel {
        return AdminProductImageViewModel(
            TestData.product1.id, GetProductByIdUseCase(repository),
            UpdateProductImageUseCase(repository, storage), storage, savedStateHandle
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

    private class CameraStorage(
        private val failSave: Boolean = false,
        var failPreparation: Boolean = false,
        var invalidCameraPreview: Boolean = false
    ) : ProductImageStorage {
        val liveCaptures = mutableSetOf<String>()
        val preservedCaptureSets = mutableListOf<Set<String>>()
        val captureUri = "content://cl.duoc.louaraya.fileprovider/camera-captures/00000000-0000-0000-0000-000000000001.jpg"

        override suspend fun saveFromSource(sourceUri: String): String {
            if (failSave) error("camera save failed")
            return "product-images/updated.jpg"
        }
        override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? = null
        override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? =
            if (invalidCameraPreview && sourceUri == captureUri) null
            else byteArrayOf(1).takeIf { sourceUri == captureUri || sourceUri.startsWith("content://") }
        override suspend fun delete(reference: String) = Unit
        override suspend fun createCameraCaptureUri(): String {
            if (failPreparation) error("capture preparation failed")
            liveCaptures += captureUri
            return captureUri
        }
        override suspend fun deleteCameraCapture(uri: String) {
            liveCaptures -= uri
        }
        override suspend fun cleanupAbandonedCameraCaptures(preserveUris: Set<String>) {
            preservedCaptureSets += preserveUris
            liveCaptures.retainAll(preserveUris)
        }
    }
}
