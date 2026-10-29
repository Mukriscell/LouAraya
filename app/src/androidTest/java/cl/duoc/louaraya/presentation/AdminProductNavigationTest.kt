package cl.duoc.louaraya.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.CreateCommercialRequestUseCase
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestByIdUseCase
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestsUseCase
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase
import cl.duoc.louaraya.domain.usecase.UpdateProductImageUseCase
import cl.duoc.louaraya.data.image.LocalProductImageStorage
import cl.duoc.louaraya.presentation.navigation.LouArayaNavHost
import cl.duoc.louaraya.ui.theme.LouArayaTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.io.File
import androidx.core.app.ActivityOptionsCompat

class AdminProductNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun demoAdminImageSaveReturnsToFilteredCatalogAndDetailUsesUpdatedReference() {
        val product = Product(
            id = 1L, name = "Tapiz académico", widthCm = 60.0, lengthCm = 90.0,
            material = "Algodón", price = 45000L, campaignCategory = CampaignCategory.WEDDINGS
        )
        val productRepository = InMemoryProducts(product)
        val imageBytes = InstrumentationRegistry.getInstrumentation().targetContext.resources
            .openRawResource(R.drawable.product_tapiz_aurora).use { it.readBytes() }
        val imageStorage = FakeImageStorage(imageBytes)
        val requestRepository = EmptyRequests()
        val pickerRegistry = FakePickerRegistry()

        compose.setContent {
            LouArayaTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides pickerRegistry) {
                    LouArayaNavHost(
                        getProductsUseCase = GetProductsUseCase(productRepository),
                        getProductByIdUseCase = GetProductByIdUseCase(productRepository),
                        createCommercialRequestUseCase = CreateCommercialRequestUseCase(requestRepository, productRepository),
                        getCommercialRequestsUseCase = GetCommercialRequestsUseCase(requestRepository),
                        getCommercialRequestByIdUseCase = GetCommercialRequestByIdUseCase(requestRepository),
                        productImageStorage = imageStorage,
                        updateProductImageUseCase = UpdateProductImageUseCase(productRepository, imageStorage)
                    )
                }
            }
        }

        compose.onNodeWithText("Matrimonios").performClick().assertIsSelected()
        compose.onNodeWithText("Administración (demo)").performClick()
        compose.onNodeWithTag("edit-image-1").performClick()
        pickerRegistry.nextUri = null
        compose.onNodeWithTag("pick-product-image").performClick()
        assertTrue(compose.onAllNodesWithTag("save-product-image").fetchSemanticsNodes().isEmpty())
        assertEquals(null, productRepository.product.imageReference)
        pickerRegistry.nextUri = Uri.parse("content://fictional-test-image")
        compose.onNodeWithTag("pick-product-image").performClick()
        compose.waitUntil(5_000) { imageStorage.previewLoads > 0 }
        compose.onNodeWithTag("product-image-preview").assertIsDisplayed()
        compose.onNodeWithTag("cancel-image-selection").performClick()
        assertTrue(compose.onAllNodesWithTag("save-product-image").fetchSemanticsNodes().isEmpty())
        assertEquals(null, productRepository.product.imageReference)
        pickerRegistry.nextUri = Uri.parse("content://fictional-test-image")
        compose.onNodeWithTag("pick-product-image").performClick()
        compose.waitUntil(5_000) { imageStorage.previewLoads > 1 }
        compose.onNodeWithTag("save-product-image").performClick()
        compose.onNodeWithText("Imagen guardada.").assertIsDisplayed()
        compose.onNodeWithText("Volver a administración").performClick()
        compose.waitUntil(5_000) { imageStorage.loadedReferences.contains(NEW_REFERENCE) }
        imageStorage.loadedReferences.clear()
        compose.onNodeWithContentDescription("Volver").performClick()

        compose.onNodeWithText("Matrimonios").assertIsSelected()
        assertEquals(NEW_REFERENCE, productRepository.product.imageReference)
        compose.waitUntil(5_000) { imageStorage.loadedReferences.contains(NEW_REFERENCE) }

        imageStorage.loadedReferences.clear()
        compose.onNodeWithTag("catalog-product-1").performClick()
        compose.onNodeWithText("Detalle del producto").assertIsDisplayed()
        compose.waitUntil(5_000) { imageStorage.loadedReferences.contains(NEW_REFERENCE) }
        assertTrue(imageStorage.loadedReferences.contains(NEW_REFERENCE))
        assertEquals(3, pickerRegistry.launchCount)
    }

    @Test
    fun adminCatalogKeepsStableUiAcrossLoadingRefreshEmptyAndErrorRetry() {
        val product = Product(
            id = 1L, name = "Tapiz controlado", widthCm = 60.0, lengthCm = 90.0,
            material = "Algodón", price = 45000L, campaignCategory = CampaignCategory.WEDDINGS
        )
        val productRepository = ControlledProducts(product)
        val requestRepository = EmptyRequests()
        val imageStorage = FakeImageStorage(byteArrayOf())

        compose.setContent {
            LouArayaTheme {
                LouArayaNavHost(
                    getProductsUseCase = GetProductsUseCase(productRepository),
                    getProductByIdUseCase = GetProductByIdUseCase(productRepository),
                    createCommercialRequestUseCase = CreateCommercialRequestUseCase(requestRepository, productRepository),
                    getCommercialRequestsUseCase = GetCommercialRequestsUseCase(requestRepository),
                    getCommercialRequestByIdUseCase = GetCommercialRequestByIdUseCase(requestRepository),
                    productImageStorage = imageStorage,
                    updateProductImageUseCase = UpdateProductImageUseCase(productRepository, imageStorage)
                )
            }
        }

        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("catalog-product-1").fetchSemanticsNodes().isNotEmpty()
        }

        val adminLoad = CompletableDeferred<List<Product>>()
        productRepository.suspendedProducts = adminLoad
        compose.onNodeWithText("Administración (demo)").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("admin-products-loading").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("admin-products-loading").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("admin-product-list").fetchSemanticsNodes().isEmpty())
        productRepository.suspendedProducts = null
        adminLoad.complete(listOf(product))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("edit-image-1").fetchSemanticsNodes().isNotEmpty()
        }

        val refreshLoad = CompletableDeferred<List<Product>>()
        productRepository.suspendedProducts = refreshLoad
        compose.onNodeWithTag("edit-image-1").performClick()
        compose.onNodeWithText("Fotografía").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("admin-products-loading").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodesWithTag("admin-product-list").fetchSemanticsNodes().isEmpty())
        productRepository.suspendedProducts = null
        refreshLoad.complete(listOf(product))
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("edit-image-1").fetchSemanticsNodes().isNotEmpty()
        }

        productRepository.nextProducts = emptyList()
        compose.onNodeWithTag("edit-image-1").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("No hay productos disponibles.").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithTag("admin-product-list").fetchSemanticsNodes().isEmpty())

        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Administración (demo)").assertIsDisplayed()
        productRepository.loadFailure = IllegalStateException("Fallo de carga controlado")
        compose.onNodeWithText("Administración (demo)").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Fallo de carga controlado").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Fallo de carga controlado").assertIsDisplayed()
        productRepository.loadFailure = null
        productRepository.nextProducts = listOf(product)
        compose.onNodeWithText("Reintentar").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("edit-image-1").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("catalog-product-1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun cameraCaptureUsesScopedFileProviderThenPersistsAfterConfirmation() = runBlocking {
        val product = Product(
            id = 1L, name = "Tapiz con cámara", widthCm = 60.0, lengthCm = 90.0,
            material = "Algodón", price = 45000L, campaignCategory = CampaignCategory.WEDDINGS
        )
        val productRepository = InMemoryProducts(product)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, android.Manifest.permission.CAMERA)
        val imageStorage = TrackingLocalImageStorage(context)
        val fixture = context.resources.openRawResource(R.drawable.product_tapiz_aurora).use { it.readBytes() }
        val registry = CameraCaptureRegistry(context, fixture)
        val requests = EmptyRequests()
        val captureDirectory = File(context.filesDir, "camera-captures")
        val capturesBefore = captureDirectory.list()?.toSet().orEmpty()
        var savedReference: String? = null

        compose.setContent {
            LouArayaTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registry) {
                    LouArayaNavHost(
                        getProductsUseCase = GetProductsUseCase(productRepository),
                        getProductByIdUseCase = GetProductByIdUseCase(productRepository),
                        createCommercialRequestUseCase = CreateCommercialRequestUseCase(requests, productRepository),
                        getCommercialRequestsUseCase = GetCommercialRequestsUseCase(requests),
                        getCommercialRequestByIdUseCase = GetCommercialRequestByIdUseCase(requests),
                        productImageStorage = imageStorage,
                        updateProductImageUseCase = UpdateProductImageUseCase(productRepository, imageStorage)
                    )
                }
            }
        }

        try {
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("catalog-product-1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Matrimonios").performClick().assertIsSelected()
            compose.onNodeWithText("Administración (demo)").performClick()
            compose.onNodeWithTag("edit-image-1").performClick()
            compose.onNodeWithTag("take-product-photo").performScrollTo().performClick()
            compose.waitUntil(5_000) { registry.cameraLaunchCount == 1 }

            val launchedUri = requireNotNull(registry.lastCameraUri)
            assertEquals("${context.packageName}.fileprovider", launchedUri.authority)
            assertEquals("camera-captures", launchedUri.pathSegments.first())
            assertNull("A photograph is not persisted before confirmation", productRepository.product.imageReference)
            compose.onNodeWithTag("product-image-preview").assertIsDisplayed()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("save-product-image").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("save-product-image").performClick()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("Imagen guardada.").fetchSemanticsNodes().isNotEmpty()
            }

            val persistedReference = requireNotNull(productRepository.product.imageReference)
            savedReference = persistedReference
            assertNotNull(imageStorage.loadFromReference(persistedReference, 512))
            compose.onNodeWithText("Imagen guardada.").assertIsDisplayed()
            compose.waitUntil(5_000) {
                captureDirectory.list()?.toSet().orEmpty() == capturesBefore
            }

            compose.onNodeWithText("Volver a administración").performClick()
            compose.waitUntil(5_000) { imageStorage.loadedReferences.contains(persistedReference) }
            imageStorage.loadedReferences.clear()
            compose.onNodeWithContentDescription("Volver").performClick()
            compose.onNodeWithText("Matrimonios").assertIsSelected()
            compose.waitUntil(5_000) { imageStorage.loadedReferences.contains(persistedReference) }
            imageStorage.loadedReferences.clear()
            compose.onNodeWithTag("catalog-product-1").performClick()
            compose.onNodeWithText("Detalle del producto").assertIsDisplayed()
            compose.waitUntil(5_000) { imageStorage.loadedReferences.contains(persistedReference) }
        } finally {
            registry.lastCameraUri?.let { imageStorage.deleteCameraCapture(it.toString()) }
            savedReference?.let { imageStorage.delete(it) }
        }
    }

    private class InMemoryProducts(initial: Product) : ProductRepository {
        var product = initial
        override suspend fun getProducts(): List<Product> = listOf(product)
        override suspend fun getProductById(id: Long): Product? = product.takeIf { it.id == id }
        override suspend fun updateImageReference(id: Long, imageReference: String): Boolean {
            if (product.id != id) return false
            product = product.copy(imageReference = imageReference)
            return true
        }
    }

    private class ControlledProducts(initial: Product) : ProductRepository {
        @Volatile var product = initial
        @Volatile var nextProducts: List<Product> = listOf(initial)
        @Volatile var loadFailure: Exception? = null
        @Volatile var suspendedProducts: CompletableDeferred<List<Product>>? = null

        override suspend fun getProducts(): List<Product> {
            suspendedProducts?.let { return it.await() }
            loadFailure?.let { throw it }
            return nextProducts
        }

        override suspend fun getProductById(id: Long): Product? = product.takeIf { it.id == id }

        override suspend fun updateImageReference(id: Long, imageReference: String): Boolean {
            if (product.id != id) return false
            product = product.copy(imageReference = imageReference)
            return true
        }
    }

    private class FakeImageStorage(private val fixture: ByteArray) : ProductImageStorage {
        val loadedReferences = CopyOnWriteArrayList<String>()
        @Volatile var previewLoads = 0
        override suspend fun saveFromSource(sourceUri: String) = NEW_REFERENCE
        override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? {
            loadedReferences += reference
            return fixture
        }
        override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? {
            previewLoads++
            return fixture
        }
        override suspend fun delete(reference: String) = Unit
    }

    private class FakePickerRegistry : ActivityResultRegistry(), ActivityResultRegistryOwner {
        @Volatile var nextUri: Uri? = null
        @Volatile var launchCount = 0
        override val activityResultRegistry: ActivityResultRegistry get() = this

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            assertTrue(contract is ActivityResultContracts.PickVisualMedia)
            val request = input as PickVisualMediaRequest
            assertEquals(ActivityResultContracts.PickVisualMedia.ImageOnly, request.mediaType)
            launchCount++
            val selected = nextUri
            if (selected == null) {
                dispatchResult(requestCode, Activity.RESULT_CANCELED, null)
            } else {
                dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(selected))
            }
        }
    }

    private class CameraCaptureRegistry(
        private val context: Context,
        private val captureBytes: ByteArray
    ) : ActivityResultRegistry(), ActivityResultRegistryOwner {
        @Volatile var lastCameraUri: Uri? = null
        @Volatile var cameraLaunchCount = 0
        override val activityResultRegistry: ActivityResultRegistry get() = this

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?
        ) {
            assertTrue("Only the real camera capture contract is expected", contract is ActivityResultContracts.TakePicture)
            val outputUri = input as Uri
            lastCameraUri = outputUri
            cameraLaunchCount++
            requireNotNull(context.contentResolver.openOutputStream(outputUri)).use { it.write(captureBytes) }
            dispatchResult(requestCode, Activity.RESULT_OK, Intent())
        }
    }

    private class TrackingLocalImageStorage(context: Context) : ProductImageStorage {
        private val delegate = LocalProductImageStorage(context)
        val loadedReferences = CopyOnWriteArrayList<String>()

        override suspend fun saveFromSource(sourceUri: String): String = delegate.saveFromSource(sourceUri)
        override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? {
            loadedReferences += reference
            return delegate.loadFromReference(reference, maxDimensionPx)
        }
        override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? =
            delegate.loadPreview(sourceUri, maxDimensionPx)
        override suspend fun delete(reference: String) = delegate.delete(reference)
        override suspend fun createCameraCaptureUri(): String = delegate.createCameraCaptureUri()
        override suspend fun deleteCameraCapture(uri: String) = delegate.deleteCameraCapture(uri)
        override suspend fun cleanupAbandonedCameraCaptures(preserveUris: Set<String>) =
            delegate.cleanupAbandonedCameraCaptures(preserveUris)
    }

    private class EmptyRequests : CommercialRequestRepository {
        override suspend fun getRequests(): List<CommercialRequest> = emptyList()
        override suspend fun getRequestById(id: Long): CommercialRequest? = null
        override suspend fun createRequest(request: CommercialRequest): CommercialRequest = request.copy(id = 1L)
        override suspend fun updateRequestStatus(id: Long, status: RequestStatus): CommercialRequest =
            error("No hay solicitudes en este flujo de prueba")
    }

    private companion object {
        const val NEW_REFERENCE = "product-images/00000000-0000-0000-0000-000000000001.jpg"
    }
}
