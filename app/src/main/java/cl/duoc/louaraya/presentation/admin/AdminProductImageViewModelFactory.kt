package cl.duoc.louaraya.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import cl.duoc.louaraya.domain.usecase.UpdateProductImageUseCase

class AdminProductImageViewModelFactory(
    private val productId: Long,
    private val getProductById: GetProductByIdUseCase,
    private val updateProductImage: UpdateProductImageUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        AdminProductImageViewModel(productId, getProductById, updateProductImage) as T
}
