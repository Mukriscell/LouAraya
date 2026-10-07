package cl.duoc.louaraya.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import cl.duoc.louaraya.domain.usecase.UpdateProductImageUseCase

class AdminProductImageViewModelFactory(
    private val productId: Long,
    private val getProductById: GetProductByIdUseCase,
    private val updateProductImage: UpdateProductImageUseCase,
    private val imageStorage: ProductImageStorage
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        createViewModel(SavedStateHandle()) as T

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        createViewModel(extras.createSavedStateHandle()) as T

    private fun createViewModel(savedStateHandle: SavedStateHandle) = AdminProductImageViewModel(
        productId, getProductById, updateProductImage, imageStorage, savedStateHandle
    )
}
