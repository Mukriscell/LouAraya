package cl.duoc.louaraya.presentation.createrequest

import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.model.RequestIntention

sealed interface CreateCommercialRequestUiState {
    data object Loading : CreateCommercialRequestUiState
    data object NotFound : CreateCommercialRequestUiState
    data class Error(val message: String) : CreateCommercialRequestUiState
    data class Form(
        val product: Product,
        val intention: RequestIntention = RequestIntention.PURCHASE,
        val comment: String = "",
        val isSaving: Boolean = false,
        val error: String? = null
    ) : CreateCommercialRequestUiState
    data class Saved(val request: CommercialRequest) : CreateCommercialRequestUiState
}
