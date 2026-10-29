package cl.duoc.louaraya.presentation.catalog

import cl.duoc.louaraya.domain.model.Product




sealed interface CatalogUiState {

    data object Loading : CatalogUiState

    data class Success(val products: List<Product>) : CatalogUiState

    data object Empty : CatalogUiState

    data class Error(val message: String) : CatalogUiState
}
