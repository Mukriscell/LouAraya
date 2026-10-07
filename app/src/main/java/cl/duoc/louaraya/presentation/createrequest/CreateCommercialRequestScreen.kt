package cl.duoc.louaraya.presentation.createrequest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.usecase.CreateCommercialRequestUseCase
import cl.duoc.louaraya.presentation.common.AdaptiveContentContainer
import cl.duoc.louaraya.presentation.common.CommercialRequestScaffold
import cl.duoc.louaraya.presentation.common.LouArayaContentWidths
import cl.duoc.louaraya.presentation.common.LouArayaWindowSizeClass
import cl.duoc.louaraya.presentation.common.RequestCenteredContent
import cl.duoc.louaraya.presentation.common.RequestMessage
import cl.duoc.louaraya.presentation.common.displayName
import cl.duoc.louaraya.presentation.common.formatClp

@Composable
fun CreateCommercialRequestScreen(
    viewModel: CreateCommercialRequestViewModel,
    onBack: () -> Unit,
    onViewRequests: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: LouArayaWindowSizeClass = LouArayaWindowSizeClass.COMPACT
) {
    val state by viewModel.uiState.collectAsState()
    CreateCommercialRequestContent(
        state, onBack, onViewRequests, viewModel::loadProduct,
        viewModel::setIntention, viewModel::setComment,
        viewModel::submit, modifier, windowSizeClass
    )
}

@Composable
fun CreateCommercialRequestContent(
    state: CreateCommercialRequestUiState,
    onBack: () -> Unit,
    onViewRequests: () -> Unit,
    onRetry: () -> Unit,
    onIntentionChange: (RequestIntention) -> Unit,
    onCommentChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    windowSizeClass: LouArayaWindowSizeClass = LouArayaWindowSizeClass.COMPACT
) {
    CommercialRequestScaffold("Compra o reserva", onBack, modifier) { padding ->
        AdaptiveContentContainer(
            windowSizeClass = windowSizeClass,
            maxWidth = LouArayaContentWidths.requestForm,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            when (state) {
                CreateCommercialRequestUiState.Loading -> RequestCenteredContent {
                    CircularProgressIndicator()
                }
                CreateCommercialRequestUiState.NotFound -> RequestMessage(
                    "El producto no existe o ya no está disponible."
                )
                is CreateCommercialRequestUiState.Error -> RequestMessage(state.message, onRetry = onRetry)
                is CreateCommercialRequestUiState.Saved -> RequestCenteredContent {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Intención registrada", style = MaterialTheme.typography.headlineSmall)
                        Text("Solicitud #${state.request.id}")
                        Text("Estado: ${state.request.status.displayName()}")
                        Text("Total de referencia registrado: ${formatClp(state.request.total)}")
                        Text("Campaña: ${state.request.campaignCategory?.displayName ?: "Sin categoría"}")
                        Text("Tu intención queda pendiente de revisión comercial. No registra un pago ni confirma disponibilidad.")
                        Button(onClick = onViewRequests) { Text("Ver solicitudes") }
                    }
                }
                is CreateCommercialRequestUiState.Form -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(state.product.name, style = MaterialTheme.typography.headlineSmall)
                    Text("Total de la pieza: ${formatClp(state.product.price)}", style = MaterialTheme.typography.titleMedium)
                    Text("Campaña asociada: ${state.product.campaignCategory.displayName}")
                    Text("Esta intención no constituye un pago ni garantiza disponibilidad.")
                    Text("Tipo de intención", style = MaterialTheme.typography.titleMedium)
                    RequestIntention.entries.forEach { intention ->
                        FilterChip(
                            selected = state.intention == intention,
                            onClick = { onIntentionChange(intention) },
                            label = { Text(intention.displayName()) },
                            enabled = !state.isSaving
                        )
                    }
                    Text("Cliente ficticio: ${CreateCommercialRequestUseCase.DEMO_CLIENT_ID}")
                    OutlinedTextField(
                        value = state.comment,
                        onValueChange = onCommentChange,
                        label = { Text("Comentario o requerimiento (opcional)") },
                        supportingText = {
                            Text("Solo requerimientos ficticios, sin datos personales reales. Máximo ${CreateCommercialRequestUseCase.MAX_COMMENT_LENGTH} caracteres.")
                        },
                        enabled = !state.isSaving,
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = onSubmit, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.isSaving) "Guardando…" else "Registrar intención")
                    }
                }
            }
        }
    }
}
