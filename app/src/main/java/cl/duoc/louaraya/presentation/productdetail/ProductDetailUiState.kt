package cl.duoc.louaraya.presentation.productdetail

import cl.duoc.louaraya.domain.model.Product




sealed interface ProductDetailUiState {

    data object Loading : ProductDetailUiState

    data class Success(val product: Product) : ProductDetailUiState

    data object NotFound : ProductDetailUiState

    data class Error(val message: String) : ProductDetailUiState
}
