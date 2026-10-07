package cl.duoc.louaraya.presentation.requestdetail

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.Product

sealed interface CommercialRequestDetailUiState {
    data object Loading : CommercialRequestDetailUiState
    data object NotFound : CommercialRequestDetailUiState
    data class Error(val message: String) : CommercialRequestDetailUiState
    data class Success(val request: CommercialRequest, val product: Product?) : CommercialRequestDetailUiState
}
