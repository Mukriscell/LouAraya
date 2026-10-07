package cl.duoc.louaraya.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun RequestIntention.displayName(): String = when (this) {
    RequestIntention.PURCHASE -> "Compra"
    RequestIntention.RESERVATION -> "Reserva"
}

fun RequestStatus.displayName(): String = when (this) {
    RequestStatus.EXPLORATION -> "Exploración"
    RequestStatus.PENDING -> "Pendiente"
    RequestStatus.CONFIRMED -> "Confirmado"
    RequestStatus.DELIVERED -> "Entregado"
}

fun formatRequestDate(date: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-CL")).format(Date(date))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommercialRequestScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Volver")
                    }
                }
            )
        },
        content = content
    )
}

@Composable
fun RequestCenteredContent(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun RequestMessage(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    RequestCenteredContent(modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(message, textAlign = TextAlign.Center)
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry) { Text("Reintentar") }
            }
        }
    }
}
