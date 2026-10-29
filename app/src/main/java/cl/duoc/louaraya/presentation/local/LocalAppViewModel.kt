package cl.duoc.louaraya.presentation.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.model.LocalSnapshot
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import cl.duoc.louaraya.domain.usecase.FormValidationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LocalAppState(val initialized: Boolean = false, val busy: Boolean = false, val snapshot: LocalSnapshot = LocalSnapshot(), val error: String? = null, val message: String? = null, val fieldErrors: Map<String, String> = emptyMap(), val readError: String? = null)

class LocalAppViewModel(val useCases: LocalUseCases) : ViewModel() {
    private val state = MutableStateFlow(LocalAppState())
    val uiState = state.asStateFlow()
    private var observationJob: Job? = null
    private var observationGeneration = 0L
    init { initialize() }
    fun initialize() = execute(operation = { initialize() })
    fun refresh() = execute(readOperation = true, operation = {})
    fun dismissError() { state.value = state.value.copy(error = null, message = null, fieldErrors = emptyMap()) }
    fun clearFieldError(key: String) { state.value = state.value.copy(fieldErrors = state.value.fieldErrors - key, error = null) }

    fun recordNavigation(expectedUserId: Long, operation: suspend LocalUseCases.() -> Unit) = viewModelScope.launch {
        uiState.first { !it.busy }
        if (state.value.snapshot.profile?.id != expectedUserId) return@launch
        execute { if (profile()?.id == expectedUserId) operation() }
    }
    fun execute(message: String? = null, onSuccess: () -> Unit = {}, readOperation: Boolean = false, operation: suspend LocalUseCases.() -> Unit) {
        if (state.value.busy) return
        stopObserving()
        state.value = state.value.copy(busy = true, error = null, message = null, fieldErrors = emptyMap(), readError = null)
        viewModelScope.launch {
            try {
                useCases.operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.value = state.value.copy(busy = false, error = if (readOperation) null else error.message ?: "No fue posible completar la operación.", readError = if (readOperation) error.message ?: "No fue posible cargar los datos." else null, fieldErrors = (error as? FormValidationException)?.fields.orEmpty())
                if (state.value.initialized) observeSnapshots()
                return@launch
            }

            try {
                val snapshot = useCases.snapshot()
                state.value = LocalAppState(initialized = true, snapshot = snapshot, message = message)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state.value = state.value.copy(busy = false, message = message, readError = SNAPSHOT_READ_ERROR,
                    error = if (state.value.initialized) null else "No fue posible cargar los datos.")
            }
            observeSnapshots()
            onSuccess()
        }
    }





    private fun observeSnapshots() {
        if (observationJob?.isActive == true) return
        val generation = ++observationGeneration
        observationJob = viewModelScope.launch {
            while (isActive && generation == observationGeneration) {
                try {
                    useCases.observeSnapshots().collect { snapshot ->
                        if (generation == observationGeneration) {
                            state.value = state.value.copy(initialized = true, snapshot = snapshot, readError = null,
                                error = if (state.value.initialized) state.value.error else null)
                        }
                    }
                    if (isActive) throw IllegalStateException("Snapshot observation completed unexpectedly")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation != observationGeneration) return@launch
                    state.value = state.value.copy(readError = SNAPSHOT_READ_ERROR)
                    delay(1_000)
                }
            }
        }
    }

    private fun stopObserving() {
        observationGeneration++
        observationJob?.cancel()
        observationJob = null
    }

    private companion object {
        const val SNAPSHOT_READ_ERROR = "No pudimos actualizar los datos. Reintentaremos automáticamente."
    }
}
