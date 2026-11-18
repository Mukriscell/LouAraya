package cl.duoc.louaraya.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.model.UserRole
import cl.duoc.louaraya.presentation.common.formatClp
import cl.duoc.louaraya.presentation.local.Action
import cl.duoc.louaraya.presentation.local.Choices
import cl.duoc.louaraya.presentation.local.Intro
import cl.duoc.louaraya.presentation.local.LocalBusy
import cl.duoc.louaraya.presentation.local.Note
import cl.duoc.louaraya.presentation.local.Page
import cl.duoc.louaraya.presentation.local.SecondaryAction
import cl.duoc.louaraya.presentation.local.StatusBadge
import cl.duoc.louaraya.ui.theme.LouArayaTheme

class SyncActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[SyncViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = getSharedPreferences("prototype", MODE_PRIVATE)
        setContent {
            val state by model.state.collectAsState()
            LouArayaTheme {
                Surface(Modifier.fillMaxSize()) {
                    Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                        val defaultAddress = getString(R.string.prototype_server)
                        SyncScreen(state, model, settings.getString("host", defaultAddress).orEmpty()) { host ->
                            settings.edit().putString("host", host).apply()
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.startPolling()
    }

    override fun onStop() {
        model.stopPolling()
        super.onStop()
    }
}

@Composable
private fun SyncScreen(state: SyncState, model: SyncViewModel, savedAddress: String, saveAddress: (String) -> Unit) {
    val snapshot = state.snapshot
    var address by rememberSaveable { mutableStateOf(savedAddress) }
    val focus = LocalFocusManager.current

    Page(maxWidth = 600.dp, topPadding = 24.dp, animated = false) {
        Intro("Lou Araya", "Solicitudes compartidas")
        if (snapshot == null) {
            var alias by rememberSaveable { mutableStateOf("cliente.demo") }
            var password by remember { mutableStateOf("") }
            Text("Prototipo con datos ficticios. Usa tu cuenta de demostración.")
            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Servidor") },
                singleLine = true, enabled = !state.busy)
            OutlinedTextField(alias, { alias = it }, Modifier.fillMaxWidth(), label = { Text("Cuenta demo") },
                singleLine = true, enabled = !state.busy)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Contraseña demo") },
                singleLine = true, enabled = !state.busy, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            state.error?.let { Note(it, error = true) }
            Action("Conectar", enabled = address.isNotBlank() && alias.isNotBlank() && password.isNotBlank(), loading = state.busy) {
                focus.clearFocus()
                saveAddress(address)
                model.signIn(address, alias.trim(), password)
            }
        } else {
            val admin = snapshot.profile.role == UserRole.ADMIN
            Text(if (admin) "Taller · ${snapshot.profile.alias}" else "Cliente · ${snapshot.profile.alias}",
                style = MaterialTheme.typography.titleMedium)
            if (state.refreshFailed) Note("Se perdió la conexión. Mostramos la última lista recibida.", error = true)
            state.error?.let { Note(it, error = true) }
            state.message?.let { Note(it) }
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) { SecondaryAction("Actualizar", !state.busy, model::refresh) }
                Box(Modifier.weight(1f)) { SecondaryAction("Cambiar cuenta", !state.busy, model::signOut) }
            }
            if (!admin) {
                CompositionLocalProvider(LocalBusy provides state.busy) {
                    RequestForm(snapshot.products, state.busy, model::create)
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(if (admin) "Solicitudes del taller" else "Mis solicitudes", style = MaterialTheme.typography.headlineSmall)
            if (snapshot.requests.isEmpty()) Text("Todavía no hay solicitudes.")
            snapshot.requests.forEach { request ->
                RequestCard(request, admin, state.busy) { model.confirm(request.id) }
            }
        }
    }
}

@Composable
private fun RequestForm(products: List<SyncProduct>, busy: Boolean, submit: (Long, Int) -> Unit) {
    var selectedId by rememberSaveable { mutableStateOf(products.firstOrNull()?.id) }
    var quantity by rememberSaveable { mutableStateOf("1") }
    val product = products.firstOrNull { it.id == selectedId }
    val units = quantity.toIntOrNull()?.takeIf { it in 1..99 }
    val focus = LocalFocusManager.current
    Text("Nueva solicitud", style = MaterialTheme.typography.headlineSmall)
    Choices(products, product, SyncProduct::name) { selectedId = it.id }
    OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(2) }, Modifier.fillMaxWidth(),
        label = { Text("Cantidad") }, singleLine = true, enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    if (product != null && units != null) Text("Total de referencia · ${formatClp(product.price * units)}")
    Action("Enviar solicitud", enabled = product != null && units != null, loading = busy) {
        focus.clearFocus()
        submit(checkNotNull(product).id, checkNotNull(units))
    }
}

@Composable
private fun RequestCard(request: SyncRequest, admin: Boolean, busy: Boolean, confirm: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Solicitud #${request.id}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                StatusBadge(request.status)
            }
            Text(request.product)
            Text("${request.quantity} uds. · ${formatClp(request.total)}")
            if (admin) Text(request.owner, style = MaterialTheme.typography.bodySmall)
            if (admin && request.status == RequestStatus.PENDING) {
                Spacer(Modifier.height(12.dp))
                Action("Confirmar solicitud #${request.id}", enabled = !busy, onClick = confirm)
            }
        }
    }
}
