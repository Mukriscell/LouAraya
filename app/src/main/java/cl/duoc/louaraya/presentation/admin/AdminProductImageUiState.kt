package cl.duoc.louaraya.presentation.admin

import cl.duoc.louaraya.domain.model.Product

sealed interface AdminProductImageUiState {
    data object Loading : AdminProductImageUiState
    data class Error(val message: String) : AdminProductImageUiState
    data class Ready(
        val product: Product,
        val previewSourceUri: String? = null,
        val isSaving: Boolean = false,
        val error: String? = null,
        val isSaved: Boolean = false
    ) : AdminProductImageUiState
}
