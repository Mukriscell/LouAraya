package cl.duoc.louaraya.presentation.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommercialRequestsViewModel(private val getRequests: GetCommercialRequestsUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow<CommercialRequestsUiState>(CommercialRequestsUiState.Loading)
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { loadRequests() }

    fun loadRequests() {
        loadJob?.cancel()
        _uiState.value = CommercialRequestsUiState.Loading
        loadJob = viewModelScope.launch {
            _uiState.value = try {
                val requests = getRequests()
                if (requests.isEmpty()) CommercialRequestsUiState.Empty else CommercialRequestsUiState.Success(requests)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                CommercialRequestsUiState.Error("No fue posible cargar las solicitudes.")
            }
        }
    }
}
