package cl.duoc.louaraya.presentation.local

import androidx.lifecycle.SavedStateHandle
import cl.duoc.louaraya.domain.image.ProductImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegistrationPhotoViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun cleanup() { Dispatchers.resetMain() }

    private class Images : ProductImageStorage {
        var sequence = 0
        var valid = true
        val deleted = mutableListOf<String>()
        var preserved = emptySet<String>()
        override suspend fun createCameraCaptureUri() = "content://capture/${++sequence}"
        override suspend fun deleteCameraCapture(uri: String) { deleted += uri }
        override suspend fun cleanupAbandonedCameraCaptures(preserveUris: Set<String>) { preserved = preserveUris }
        override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int) = if (valid) byteArrayOf(1) else null
        override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? = null
        override suspend fun saveFromSource(sourceUri: String): String = error("A draft must not save a profile image")
        override suspend fun delete(reference: String) = Unit
    }

    @Test fun cameraCancellationKeepsPreviousSelectedPhoto() = runTest(dispatcher) {
        val images = Images()
        val vm = RegistrationPhotoViewModel(images)
        advanceUntilIdle()
        vm.takePhoto(); advanceUntilIdle()
        assertTrue(vm.claimLaunch("content://capture/1"))
        assertFalse(vm.claimLaunch("content://capture/1"))
        vm.cameraResult(true); advanceUntilIdle()
        vm.takePhoto(); advanceUntilIdle()
        vm.cameraResult(false); advanceUntilIdle()
        assertEquals("content://capture/1", vm.state.value.previewUri)
        assertNull(vm.state.value.pendingUri)
        assertEquals(listOf("content://capture/2"), images.deleted)
    }

    @Test fun choosingIconRemovesPhotoAndDoesNotKeepPendingCapture() = runTest(dispatcher) {
        val images = Images()
        val vm = RegistrationPhotoViewModel(images)
        advanceUntilIdle(); vm.takePhoto(); advanceUntilIdle(); vm.cameraResult(true); advanceUntilIdle()
        vm.useIllustration(); advanceUntilIdle()
        assertNull(vm.state.value.previewUri)
        assertNull(vm.state.value.pendingUri)
        assertEquals(listOf("content://capture/1"), images.deleted)
        assertFalse(vm.state.value.busy)
    }

    @Test fun restoredDraftPreservesItsPhotoAndCanContinuePendingCamera() = runTest(dispatcher) {
        val images = Images()
        val vm = RegistrationPhotoViewModel(images, SavedStateHandle(mapOf("preview" to "content://previous", "pending" to "content://pending")))
        advanceUntilIdle()
        assertEquals(setOf("content://previous", "content://pending"), images.preserved)
        vm.takePhoto()
        assertEquals("content://pending", vm.state.value.launchUri)
        assertEquals(0, images.sequence)
        images.valid = false
        vm.cameraResult(true); advanceUntilIdle()
        assertEquals("content://previous", vm.state.value.previewUri)
        assertNotNull(vm.state.value.error)
        assertEquals(listOf("content://pending"), images.deleted)
    }
}
