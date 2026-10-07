package cl.duoc.louaraya.presentation.requestdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.presentation.common.CommercialRequestScaffold
import cl.duoc.louaraya.presentation.common.RequestCenteredContent
import cl.duoc.louaraya.presentation.common.RequestMessage
import cl.duoc.louaraya.presentation.common.displayName
import cl.duoc.louaraya.presentation.common.formatClp
import cl.duoc.louaraya.presentation.common.formatRequestDate

@Composable
fun CommercialRequestDetailScreen(
    viewModel: CommercialRequestDetailViewModel,
    onBack: () -> Unit,
    onProductClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    CommercialRequestDetailContent(state, onBack, onProductClick, viewModel::loadRequest, modifier)
}

@Composable
fun CommercialRequestDetailContent(
    state: CommercialRequestDetailUiState,
    onBack: () -> Unit,
    onProductClick: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    CommercialRequestScaffold("Detalle de solicitud", onBack, modifier) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (state) {
            CommercialRequestDetailUiState.Loading -> RequestCenteredContent(contentModifier) { CircularProgressIndicator() }
            CommercialRequestDetailUiState.NotFound -> RequestMessage("La solicitud no existe.", contentModifier)
            is CommercialRequestDetailUiState.Error -> RequestMessage(state.message, contentModifier, onRetry)
            is CommercialRequestDetailUiState.Success -> {
                val request = state.request
                Column(
                    modifier = contentModifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Solicitud #${request.id}", style = MaterialTheme.typography.headlineSmall)
                    Text("Estado: ${request.status.displayName()}", style = MaterialTheme.typography.titleLarge)
                    Text("Categoría: ${request.campaignCategory?.displayName ?: "Sin categoría"}")
                    Text("Pieza #${request.productId}: ${state.product?.name ?: "Ya no está disponible en el catálogo"}")
                    if (state.product != null) {
                        TextButton(onClick = { onProductClick(request.productId) }) { Text("Ver pieza") }
                    }
                    Text("Intención: ${request.intention.displayName()}")
                    Text("Fecha: ${formatRequestDate(request.date)}")
                    Text("Cliente ficticio: ${request.anonymizedClient}")
                    Text("Total registrado: ${formatClp(request.total)}")
                    Text("Requerimiento: ${request.comment ?: "Sin comentario"}")
                }
            }
        }
    }
}
