package cl.duoc.louaraya.presentation.productdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase

/**
 * Crea [ProductDetailViewModel] para un producto concreto mientras no se use
 * un framework de inyección de dependencias.
 */
class ProductDetailViewModelFactory(
    private val productId: Long,
    private val getProductByIdUseCase: GetProductByIdUseCase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ProductDetailViewModel::class.java)) {
            "ViewModel desconocido: ${modelClass.name}"
        }
        return ProductDetailViewModel(productId, getProductByIdUseCase) as T
    }
}
