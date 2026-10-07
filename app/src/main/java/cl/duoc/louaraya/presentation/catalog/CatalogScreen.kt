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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.presentation.common.formatClp
import cl.duoc.louaraya.presentation.common.formatCm
import cl.duoc.louaraya.ui.theme.LouArayaTheme

/**
 * Pantalla de catálogo conectada a [CatalogViewModel].
 *
 * @param onProductClick se invoca con el id del producto seleccionado; quien
 * aloja la pantalla decide cómo navegar.
 */
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onProductClick: (Long) -> Unit,
    onViewRequests: () -> Unit,
    modifier: Modifier = Modifier
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
        modifier = modifier
    )
}

/**
 * Contenido de la pantalla sin dependencia del ViewModel (facilita previews).
 */
@Composable
fun CatalogContent(
    uiState: CatalogUiState,
    selectedCategory: CampaignCategory?,
    onRetry: () -> Unit,
    onCategorySelected: (CampaignCategory?) -> Unit,
    onProductClick: (Long) -> Unit,
    onViewRequests: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Catálogo", style = MaterialTheme.typography.headlineMedium)
            TextButton(onClick = onViewRequests) { Text("Solicitudes") }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
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
                onProductClick = onProductClick,
                modifier = contentModifier
            )
        }
    }
}

@Composable
private fun ProductList(
    products: List<Product>,
    onProductClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(products, key = { it.id }) { product ->
            ProductCard(
                product = product,
                onClick = { onProductClick(product.id) }
            )
        }
    }
}

@Composable
private fun ProductCard(
    product: Product,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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

@Preview(showBackground = true)
@Composable
private fun CatalogContentPreview() {
    LouArayaTheme {
        CatalogContent(
            uiState = CatalogUiState.Success(
                listOf(
                    Product(1L, "Tapiz Preview", 60.0, 90.0, "Algodón", 45000L, CampaignCategory.INTERIOR_DECORATION),
                    Product(2L, "Colgante Preview", 30.5, 50.0, "Cáñamo", 25000L, CampaignCategory.FESTIVALS)
                )
            ),
            selectedCategory = null,
            onRetry = {},
            onCategorySelected = {},
            onProductClick = {},
            onViewRequests = {}
        )
    }
}
