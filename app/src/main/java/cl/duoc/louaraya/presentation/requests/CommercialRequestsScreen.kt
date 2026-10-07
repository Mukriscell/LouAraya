package cl.duoc.louaraya.presentation.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.presentation.common.AdaptiveContentContainer
import cl.duoc.louaraya.presentation.common.CommercialRequestScaffold
import cl.duoc.louaraya.presentation.common.LouArayaContentWidths
import cl.duoc.louaraya.presentation.common.LouArayaWindowSizeClass
import cl.duoc.louaraya.presentation.common.RequestCenteredContent
import cl.duoc.louaraya.presentation.common.RequestMessage
import cl.duoc.louaraya.presentation.common.displayName
import cl.duoc.louaraya.presentation.common.formatClp
import cl.duoc.louaraya.presentation.common.formatRequestDate

@Composable
fun CommercialRequestsScreen(
    viewModel: CommercialRequestsViewModel,
    onBack: () -> Unit,
    onRequestClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: LouArayaWindowSizeClass = LouArayaWindowSizeClass.COMPACT
) {
    val state by viewModel.uiState.collectAsState()
    CommercialRequestsContent(
        state, onBack, onRequestClick, viewModel::loadRequests, modifier, windowSizeClass
    )
}

@Composable
fun CommercialRequestsContent(
    state: CommercialRequestsUiState,
    onBack: () -> Unit,
    onRequestClick: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: LouArayaWindowSizeClass = LouArayaWindowSizeClass.COMPACT
) {
    CommercialRequestScaffold("Solicitudes", onBack, modifier) { padding ->
        AdaptiveContentContainer(
            windowSizeClass = windowSizeClass,
            maxWidth = LouArayaContentWidths.requestList,
            modifier = Modifier.padding(padding)
        ) {
            when (state) {
                CommercialRequestsUiState.Loading -> RequestCenteredContent { CircularProgressIndicator() }
                CommercialRequestsUiState.Empty -> RequestMessage(
                    "Aún no tienes solicitudes. Selecciona una pieza del catálogo para registrar una intención."
                )
                is CommercialRequestsUiState.Error -> RequestMessage(state.message, onRetry = onRetry)
                is CommercialRequestsUiState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.requests, key = { it.id }) { request ->
                        Card(onClick = { onRequestClick(request.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Solicitud #${request.id} · ${request.intention.displayName()}", style = MaterialTheme.typography.titleMedium)
                                Text("Pieza #${request.productId}")
                                Text("Categoría: ${request.campaignCategory?.displayName ?: "Sin categoría"}")
                                Text(formatRequestDate(request.date))
                                Text("Total: ${formatClp(request.total)}")
                                Text("Estado: ${request.status.displayName()}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
