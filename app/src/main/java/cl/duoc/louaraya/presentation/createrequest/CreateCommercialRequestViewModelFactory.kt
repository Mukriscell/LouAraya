package cl.duoc.louaraya.presentation.createrequest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.duoc.louaraya.domain.usecase.CreateCommercialRequestUseCase
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase

class CreateCommercialRequestViewModelFactory(
    private val productId: Long,
    private val getProductById: GetProductByIdUseCase,
    private val createRequest: CreateCommercialRequestUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CreateCommercialRequestViewModel::class.java))
        return CreateCommercialRequestViewModel(productId, getProductById, createRequest) as T
    }
}
