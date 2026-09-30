package cl.duoc.louaraya.presentation.catalog

import cl.duoc.louaraya.domain.model.Product

/**
 * Estados posibles de la pantalla de catálogo.
 */
sealed interface CatalogUiState {

    data object Loading : CatalogUiState

    data class Success(val products: List<Product>) : CatalogUiState

    data object Empty : CatalogUiState

    data class Error(val message: String) : CatalogUiState
}
