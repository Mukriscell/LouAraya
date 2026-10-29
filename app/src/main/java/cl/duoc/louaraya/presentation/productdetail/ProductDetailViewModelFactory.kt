package cl.duoc.louaraya.presentation.productdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase





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
