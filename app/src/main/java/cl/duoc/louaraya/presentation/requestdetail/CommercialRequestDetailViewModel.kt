package cl.duoc.louaraya.presentation.requestdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestByIdUseCase
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommercialRequestDetailViewModel(
    private val requestId: Long,
    private val getRequestById: GetCommercialRequestByIdUseCase,
    private val getProductById: GetProductByIdUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow<CommercialRequestDetailUiState>(CommercialRequestDetailUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { loadRequest() }

    fun loadRequest() {
        loadJob?.cancel()
        _uiState.value = CommercialRequestDetailUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val request = getRequestById(requestId)
                if (request == null) CommercialRequestDetailUiState.NotFound
                else CommercialRequestDetailUiState.Success(request, getProductById(request.productId))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                CommercialRequestDetailUiState.Error("No fue posible cargar la solicitud.")
            }
        }
    }
}
