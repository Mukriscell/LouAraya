package cl.duoc.louaraya.prototype

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

internal data class SyncState(
    val snapshot: SyncSnapshot? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val refreshFailed: Boolean = false,
)

internal class SyncViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(SyncState())
    val state = mutableState.asStateFlow()
    private var session: SyncSession? = null
    private var polling: Job? = null
    private var revision = 0
    private var draft: SyncDraft? = null

    fun signIn(address: String, alias: String, password: String) = runAction {
        val connection = SyncSession(address, alias, password)
        val snapshot = connection.snapshot()
        session = connection
        mutableState.value = SyncState(snapshot = snapshot)
    }

    fun signOut() {
        if (mutableState.value.busy) return
        session = null
        draft = null
        mutableState.value = SyncState()
    }

    fun startPolling() {
        if (polling?.isActive == true) return
        polling = viewModelScope.launch {
            while (true) {
                delay(3_000)
                val connection = session ?: continue
                val currentRevision = revision
                if (mutableState.value.busy) continue
                try {
                    val snapshot = connection.snapshot()
                    if (session === connection && revision == currentRevision) {
                        mutableState.value = mutableState.value.copy(snapshot = snapshot, refreshFailed = false)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (session === connection && revision == currentRevision) {
                        mutableState.value = mutableState.value.copy(refreshFailed = true)
                    }
                }
            }
        }
    }

    fun stopPolling() {
        polling?.cancel()
        polling = null
    }

    fun refresh() = runAction {
        val connection = checkNotNull(session)
        mutableState.value = SyncState(snapshot = connection.snapshot())
    }

    fun create(productId: Long, quantity: Int) = runAction {
        val connection = checkNotNull(session)
        val currentDraft = draft?.takeIf { it.productId == productId && it.quantity == quantity }
            ?: SyncDraft(UUID.randomUUID().toString(), productId, quantity)
        draft = currentDraft
        val saved = connection.create(currentDraft)
        draft = null
        update(saved, "Solicitud #${saved.id} enviada.")
    }

    fun confirm(id: Long) = runAction {
        val saved = checkNotNull(session).confirm(id)
        update(saved, "Solicitud #${saved.id} confirmada.")
    }

    private fun update(saved: SyncRequest, message: String) {
        val snapshot = checkNotNull(mutableState.value.snapshot)
        mutableState.value = SyncState(
            snapshot = snapshot.copy(requests = (snapshot.requests.filterNot { it.id == saved.id } + saved)
                .sortedByDescending { it.id }),
            message = message,
        )
    }

    private fun runAction(action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        revision++
        mutableState.value = mutableState.value.copy(busy = true, error = null, message = null)
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val message = when (error) {
                    is HttpException -> when (error.code()) {
                        400 -> "Revisa la pieza y la cantidad."
                        401 -> "La cuenta o contraseña de demostración no coincide."
                        403 -> "Esta cuenta no tiene permiso para realizar la acción."
                        404 -> "La solicitud no está disponible."
                        409 -> "Los datos de la solicitud cambiaron. Actualiza la lista."
                        else -> "El servidor respondió con un error (${error.code()})."
                    }
                    is IOException -> "No se pudo conectar. Comprueba Internet y que el servidor esté activo."
                    is IllegalArgumentException -> error.message ?: "Revisa la dirección del servidor."
                    else -> "No se pudo completar la operación. Reintenta."
                }
                mutableState.value = mutableState.value.copy(error = message)
            } finally {
                mutableState.value = mutableState.value.copy(busy = false)
            }
        }
    }
}
