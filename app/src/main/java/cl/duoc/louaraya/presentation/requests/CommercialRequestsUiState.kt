package cl.duoc.louaraya.presentation.requests

import cl.duoc.louaraya.domain.model.CommercialRequest

sealed interface CommercialRequestsUiState {
    data object Loading : CommercialRequestsUiState
    data object Empty : CommercialRequestsUiState
    data class Success(val requests: List<CommercialRequest>) : CommercialRequestsUiState
    data class Error(val message: String) : CommercialRequestsUiState
}
