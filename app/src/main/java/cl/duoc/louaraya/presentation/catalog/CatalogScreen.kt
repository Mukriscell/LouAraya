package cl.duoc.louaraya.presentation.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.ui.theme.LouArayaTheme
import java.text.NumberFormat
import java.util.Locale

/**
 * Pantalla de catálogo conectada a [CatalogViewModel].
 */
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    CatalogContent(
        uiState = uiState,
        onRetry = viewModel::loadProducts,
        modifier = modifier
    )
}

/**
 * Contenido de la pantalla sin dependencia del ViewModel (facilita previews).
 */
@Composable
fun CatalogContent(
    uiState: CatalogUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState) {
        CatalogUiState.Loading -> CenteredBox(modifier) {
            CircularProgressIndicator()
        }

        CatalogUiState.Empty -> CenteredBox(modifier) {
            Text(
                text = "No hay productos disponibles.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }

        is CatalogUiState.Error -> CenteredBox(modifier) {
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
            modifier = modifier
        )
    }
}

@Composable
private fun ProductList(
    products: List<Product>,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Catálogo",
                style = MaterialTheme.typography.headlineMedium
            )
        }
        items(products, key = { it.id }) { product ->
            ProductCard(product)
        }
    }
}

@Composable
private fun ProductCard(product: Product) {
    Card(modifier = Modifier.fillMaxWidth()) {
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

private val chileanLocale: Locale = Locale.forLanguageTag("es-CL")

/** Formatea un precio en pesos chilenos, por ejemplo: "$45.000 CLP". */
private fun formatClp(price: Long): String =
    "$" + NumberFormat.getIntegerInstance(chileanLocale).format(price) + " CLP"

/** Muestra las medidas sin decimales cuando son enteras (60.0 → "60"). */
private fun formatCm(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Preview(showBackground = true)
@Composable
private fun CatalogContentPreview() {
    LouArayaTheme {
        CatalogContent(
            uiState = CatalogUiState.Success(
                listOf(
                    Product(1L, "Tapiz Preview", 60.0, 90.0, "Algodón", 45000L),
                    Product(2L, "Colgante Preview", 30.5, 50.0, "Cáñamo", 25000L)
                )
            ),
            onRetry = {}
        )
    }
}
