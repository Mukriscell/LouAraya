package cl.duoc.louaraya.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.presentation.catalog.CatalogContent
import cl.duoc.louaraya.presentation.catalog.CatalogUiState
import cl.duoc.louaraya.presentation.common.ADAPTIVE_CONTENT_TEST_TAG
import cl.duoc.louaraya.presentation.common.currentLouArayaWindowSizeClass
import cl.duoc.louaraya.presentation.common.ProductImage
import cl.duoc.louaraya.presentation.createrequest.CreateCommercialRequestContent
import cl.duoc.louaraya.presentation.createrequest.CreateCommercialRequestUiState
import cl.duoc.louaraya.presentation.productdetail.ProductDetailContent
import cl.duoc.louaraya.presentation.productdetail.ProductDetailUiState
import cl.duoc.louaraya.presentation.requestdetail.CommercialRequestDetailContent
import cl.duoc.louaraya.presentation.requestdetail.CommercialRequestDetailUiState
import cl.duoc.louaraya.presentation.requests.CommercialRequestsContent
import cl.duoc.louaraya.presentation.requests.CommercialRequestsUiState
import cl.duoc.louaraya.ui.theme.LouArayaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdaptiveScreensTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun missingOrCorruptImagesFallBackAndBadPreviewIsNotMisrepresented() {
        val brokenStorage = object : ProductImageStorage {
            override suspend fun saveFromSource(sourceUri: String): String = error("unused")
            override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? = null
            override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? = null
            override suspend fun delete(reference: String) = Unit
        }
        compose.setContent {
            LouArayaTheme {
                androidx.compose.foundation.layout.Column {
                    ProductImage(1L, "product-images/missing.jpg", brokenStorage,
                        Modifier.size(120.dp), "Demo fallback", testTag = "broken-product-image")
                    ProductImage(99L, null, null,
                        Modifier.size(120.dp), "Unknown fallback", testTag = "unknown-product-image")
                    ProductImage(1L, null, brokenStorage,
                        Modifier.size(120.dp), "Invalid preview", previewSourceUri = "content://invalid",
                        testTag = "invalid-preview-image")
                }
            }
        }

        compose.onNodeWithTag("broken-product-image").assertIsDisplayed()
        compose.onNodeWithContentDescription("Demo fallback").assertIsDisplayed()
        compose.onNodeWithText("Imagen no disponible").assertIsDisplayed()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("No se pudo previsualizar. Elige otra imagen.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No se pudo previsualizar. Elige otra imagen.").assertIsDisplayed()
    }

    @Test
    fun catalogRecomposesGridForWindowSizeAndPreservesFiltersAndProductNavigation() {
        var windowSize by mutableStateOf(DpSize(480.dp, 800.dp))
        var selectedCategory by mutableStateOf<CampaignCategory?>(null)
        var clickedProductId by mutableStateOf<Long?>(null)

        compose.setContent {
            LouArayaTheme {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowSize)) {
                    CatalogContent(
                        uiState = CatalogUiState.Success(sampleProducts),
                        selectedCategory = selectedCategory,
                        onRetry = {},
                        onCategorySelected = { selectedCategory = it },
                        onProductClick = { clickedProductId = it },
                        onViewRequests = {},
                        windowSizeClass = currentLouArayaWindowSizeClass()
                    )
                }
            }
        }

        compose.onNodeWithText("Matrimonios").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(CampaignCategory.WEDDINGS, selectedCategory) }
        assertCompactGridHasOneColumn()

        // A short compact window still exposes later products through vertical scrolling.
        compose.runOnIdle { windowSize = DpSize(480.dp, 320.dp) }
        compose.waitForIdle()
        compose.onNodeWithTag("catalog-product-grid")
            .performScrollToNode(hasTestTag("catalog-product-6"))
        compose.onNodeWithTag("catalog-product-6").assertIsDisplayed()

        compose.runOnIdle { windowSize = DpSize(720.dp, 960.dp) }
        compose.waitForIdle()
        compose.onNodeWithTag("catalog-product-grid")
            .performScrollToNode(hasTestTag("catalog-product-1"))
        assertGridItemsShareRow(listOf("catalog-product-1", "catalog-product-2"))

        compose.runOnIdle { windowSize = DpSize(1024.dp, 900.dp) }
        compose.waitForIdle()
        compose.onNodeWithTag("catalog-product-grid")
            .performScrollToNode(hasTestTag("catalog-product-1"))
        assertGridItemsShareRow(
            listOf("catalog-product-1", "catalog-product-2", "catalog-product-3")
        )

        compose.onNodeWithTag("catalog-product-1").performClick()
        compose.onNodeWithText("Matrimonios").assertIsSelected()
        compose.runOnIdle { assertEquals(1L, clickedProductId) }
    }

    @Test
    fun detailFormAndRequestScreensKeepReadableWidthsAndScrollableActions() {
        var screen by mutableStateOf(AdaptiveScreen.PRODUCT_DETAIL)
        val request = sampleRequest

        compose.setContent {
            LouArayaTheme {
                DeviceConfigurationOverride(
                    DeviceConfigurationOverride.ForcedSize(DpSize(1280.dp, 320.dp))
                ) {
                    val windowSizeClass = currentLouArayaWindowSizeClass()
                    when (screen) {
                        AdaptiveScreen.PRODUCT_DETAIL -> ProductDetailContent(
                            uiState = ProductDetailUiState.Success(sampleProduct),
                            onBack = {},
                            onRetry = {},
                            onCreateRequest = {},
                            windowSizeClass = windowSizeClass
                        )

                        AdaptiveScreen.REQUEST_FORM -> CreateCommercialRequestContent(
                            state = CreateCommercialRequestUiState.Form(sampleProduct),
                            onBack = {},
                            onViewRequests = {},
                            onRetry = {},
                            onIntentionChange = {},
                            onCommentChange = {},
                            onSubmit = {},
                            windowSizeClass = windowSizeClass
                        )

                        AdaptiveScreen.REQUEST_LIST -> CommercialRequestsContent(
                            state = CommercialRequestsUiState.Success(listOf(request)),
                            onBack = {},
                            onRequestClick = {},
                            onRetry = {},
                            windowSizeClass = windowSizeClass
                        )

                        AdaptiveScreen.REQUEST_DETAIL -> CommercialRequestDetailContent(
                            state = CommercialRequestDetailUiState.Success(request, sampleProduct),
                            onBack = {},
                            onProductClick = {},
                            onRetry = {},
                            windowSizeClass = windowSizeClass
                        )
                    }
                }
            }
        }

        assertAdaptiveWidthAtMost(760.dp)
        compose.onNodeWithText("Solicitar compra o reserva")
            .performScrollTo().assertIsDisplayed()

        compose.runOnIdle { screen = AdaptiveScreen.REQUEST_FORM }
        compose.waitForIdle()
        assertAdaptiveWidthAtMost(720.dp)
        compose.onNodeWithText("Registrar intención")
            .performScrollTo().assertIsDisplayed()

        compose.runOnIdle { screen = AdaptiveScreen.REQUEST_LIST }
        compose.waitForIdle()
        assertAdaptiveWidthAtMost(960.dp)
        compose.onNodeWithText("Solicitud #1 · Reserva", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()

        compose.runOnIdle { screen = AdaptiveScreen.REQUEST_DETAIL }
        compose.waitForIdle()
        assertAdaptiveWidthAtMost(840.dp)
        compose.onNodeWithText("Requerimiento: Prueba ficticia")
            .performScrollTo().assertIsDisplayed()
    }

    private fun assertCompactGridHasOneColumn() {
        val first = topOf("catalog-product-1")
        val second = topOf("catalog-product-2")
        assertNotEquals(first, second)
    }

    private fun assertGridItemsShareRow(tags: List<String>) {
        val positions = tags.map(::topOf)
        positions.forEach { assertEquals(positions.first(), it, 1f) }
    }

    private fun topOf(tag: String): Float =
        compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top

    private fun assertAdaptiveWidthAtMost(maxWidth: androidx.compose.ui.unit.Dp) {
        val bounds = compose.onNodeWithTag(ADAPTIVE_CONTENT_TEST_TAG)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("El ancho $width supera el límite $maxWidth", width <= maxWidth)
    }

    private enum class AdaptiveScreen {
        PRODUCT_DETAIL,
        REQUEST_FORM,
        REQUEST_LIST,
        REQUEST_DETAIL
    }

    private companion object {
        val sampleProduct = Product(
            id = 1L,
            name = "Tapiz de prueba",
            widthCm = 60.0,
            lengthCm = 90.0,
            material = "Algodón",
            price = 45_000L,
            campaignCategory = CampaignCategory.INTERIOR_DECORATION
        )

        val sampleProducts = (1L..6L).map { id ->
            sampleProduct.copy(
                id = id,
                name = "Pieza $id",
                campaignCategory = if (id % 2L == 0L) {
                    CampaignCategory.FESTIVALS
                } else {
                    CampaignCategory.WEDDINGS
                }
            )
        }

        val sampleRequest = CommercialRequest(
            id = 1L,
            date = 1_700_000_000_000L,
            anonymizedClient = "CLIENTE-DEMO",
            total = 45_000L,
            status = RequestStatus.PENDING,
            productId = sampleProduct.id,
            intention = RequestIntention.RESERVATION,
            comment = "Prueba ficticia",
            campaignCategory = sampleProduct.campaignCategory
        )
    }
}
