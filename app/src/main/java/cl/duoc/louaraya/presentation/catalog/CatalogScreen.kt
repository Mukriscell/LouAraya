package cl.duoc.louaraya.presentation.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.presentation.common.AdaptiveContentContainer
import cl.duoc.louaraya.presentation.common.LouArayaContentWidths
import cl.duoc.louaraya.presentation.common.LouArayaWindowSizeClass
import cl.duoc.louaraya.presentation.common.ProductImage
import cl.duoc.louaraya.presentation.common.formatClp
import cl.duoc.louaraya.presentation.common.formatCm
import cl.duoc.louaraya.ui.theme.LouArayaTheme







@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onProductClick: (Long) -> Unit,
    onViewRequests: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: LouArayaWindowSizeClass = LouArayaWindowSizeClass.COMPACT,
    imageStorage: ProductImageStorage? = null,
    onAdmin: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    CatalogContent(
        uiState = uiState,
        selectedCategory = selectedCategory,
        onRetry = viewModel::loadProducts,
        onCategorySelected = viewModel::selectCategory,
        onProductClick = onProductClick,
        onViewRequests = onViewRequests,
        modifier = modifier,
        windowSizeClass = windowSizeClass,
        imageStorage = imageStorage,
        onAdmin = onAdmin
    )
}




@Composable
fun CatalogContent(
    uiState: CatalogUiState,
    selectedCategory: CampaignCategory?,
    onRetry: () -> Unit,
    onCategorySelected: (CampaignCategory?) -> Unit,
    onProductClick: (Long) -> Unit,
    onViewRequests: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: LouArayaWindowSizeClass = LouArayaWindowSizeClass.COMPACT,
    imageStorage: ProductImageStorage? = null,
    onAdmin: () -> Unit = {}
) {
    AdaptiveContentContainer(
        windowSizeClass = windowSizeClass,
        maxWidth = LouArayaContentWidths.catalog,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Catálogo", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
                    TextButton(onClick = onViewRequests) { Text("Solicitudes") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onAdmin) { Text("Administración") }
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { onCategorySelected(null) },
                        label = { Text("Todos") }
                    )
                }
                items(CampaignCategory.entries.toList(), key = { it.name }) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category.displayName) }
                    )
                }
            }
            val contentModifier = Modifier.weight(1f)
            when (uiState) {
                CatalogUiState.Loading -> CenteredBox(contentModifier) {
                    CircularProgressIndicator()
                }

                CatalogUiState.Empty -> CenteredBox(contentModifier) {
                    Text(
                        text = if (selectedCategory == null) {
                            "No hay productos disponibles."
                        } else {
                            "No hay productos en ${selectedCategory.displayName}."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }

                is CatalogUiState.Error -> CenteredBox(contentModifier) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRetry) {
                            Text("Reintentar")
                        }
                    }
                }

                is CatalogUiState.Success -> ProductList(
                    products = uiState.products,
                    windowSizeClass = windowSizeClass,
                    imageStorage = imageStorage,
                    onProductClick = onProductClick,
                    modifier = contentModifier
                )
            }
        }
    }
}

@Composable
private fun ProductList(
    products: List<Product>,
    windowSizeClass: LouArayaWindowSizeClass,
    onProductClick: (Long) -> Unit,
    imageStorage: ProductImageStorage?,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(windowSizeClass.catalogColumns),
        modifier = modifier.fillMaxSize().testTag("catalog-product-grid"),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(products, key = { it.id }) { product ->
            ProductCard(
                product = product,
                imageStorage = imageStorage,
                onClick = { onProductClick(product.id) },
                modifier = Modifier.testTag("catalog-product-${product.id}")
            )
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    imageStorage: ProductImageStorage?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ProductImage(
                productId = product.id,
                imageReference = product.imageReference,
                storage = imageStorage,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
                contentDescription = "Imagen de ${product.name}",
                contentScale = ContentScale.Crop,
                testTag = "product-image-${product.id}"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Material: ${product.material}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Medidas: ${formatCm(product.widthCm)} × ${formatCm(product.lengthCm)} cm",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatClp(product.price),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CenteredBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Preview(name = "Compacto vertical", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun CatalogCompactPortraitPreview() = CatalogContentPreview(LouArayaWindowSizeClass.COMPACT)

@Preview(name = "Compacto horizontal", showBackground = true, widthDp = 480, heightDp = 320)
@Composable
private fun CatalogCompactLandscapePreview() = CatalogContentPreview(LouArayaWindowSizeClass.COMPACT)

@Preview(name = "Medio", showBackground = true, widthDp = 720, heightDp = 960)
@Composable
private fun CatalogMediumPreview() = CatalogContentPreview(LouArayaWindowSizeClass.MEDIUM)

@Preview(name = "Expandido", showBackground = true, widthDp = 1024, heightDp = 800)
@Composable
private fun CatalogExpandedPreview() = CatalogContentPreview(LouArayaWindowSizeClass.EXPANDED)

@Composable
private fun CatalogContentPreview(windowSizeClass: LouArayaWindowSizeClass) {
    LouArayaTheme {
        CatalogContent(
            uiState = CatalogUiState.Success(
                listOf(
                    Product(1L, "Tapiz Preview", 60.0, 90.0, "Algodón", 45000L, CampaignCategory.INTERIOR_DECORATION),
                    Product(2L, "Colgante Preview", 30.5, 50.0, "Cáñamo", 25000L, CampaignCategory.FESTIVALS),
                    Product(3L, "Camino Preview", 35.0, 120.0, "Algodón", 38000L, CampaignCategory.WEDDINGS)
                )
            ),
            selectedCategory = null,
            onRetry = {},
            onCategorySelected = {},
            onProductClick = {},
            onViewRequests = {},
            windowSizeClass = windowSizeClass
        )
    }
}
