package cl.duoc.louaraya.presentation.productdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.presentation.common.formatClp
import cl.duoc.louaraya.presentation.common.formatCm
import cl.duoc.louaraya.ui.theme.LouArayaTheme

/**
 * Pantalla de detalle conectada a [ProductDetailViewModel].
 *
 * @param onBack se invoca al pulsar el botón de volver; quien aloja la
 * pantalla decide cómo navegar.
 */
@Composable
fun ProductDetailScreen(
    viewModel: ProductDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    ProductDetailContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::loadProduct,
        modifier = modifier
    )
}

/**
 * Contenido de la pantalla sin dependencia del ViewModel (facilita previews).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailContent(
    uiState: ProductDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Detalle del producto") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        val contentModifier = Modifier.padding(innerPadding)
        when (uiState) {
            ProductDetailUiState.Loading -> CenteredBox(contentModifier) {
                CircularProgressIndicator()
            }

            ProductDetailUiState.NotFound -> CenteredBox(contentModifier) {
                Text(
                    text = "El producto no existe o ya no está disponible.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }

            is ProductDetailUiState.Error -> CenteredBox(contentModifier) {
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

            is ProductDetailUiState.Success -> ProductDetail(
                product = uiState.product,
                modifier = contentModifier
            )
        }
    }
}

@Composable
private fun ProductDetail(
    product: Product,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = product.name,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = formatClp(product.price),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow(label = "Material", value = product.material)
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DetailRow(label = "Ancho", value = "${formatCm(product.widthCm)} cm")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DetailRow(label = "Largo", value = "${formatCm(product.lengthCm)} cm")
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
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
private fun ProductDetailContentPreview() {
    LouArayaTheme {
        ProductDetailContent(
            uiState = ProductDetailUiState.Success(
                Product(1L, "Tapiz Preview", 60.0, 90.0, "Algodón", 45000L)
            ),
            onBack = {},
            onRetry = {}
        )
    }
}
