package cl.duoc.louaraya.presentation.local

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.model.FairEvent
import kotlinx.coroutines.delay
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

internal data class FairMapReadiness(val validatedInternet: Boolean)

internal fun interface FairMapReadinessProvider { fun read(): FairMapReadiness }
internal fun interface FairMapDirectionsOpener { fun open(url: String): Boolean }

@Composable
internal fun FairMapScreen(events: List<FairEvent>) {
    val context = LocalContext.current
    val provider = remember(context) { FairMapReadinessProvider { readFairMapReadiness(context) } }
    val opener = remember(context) {
        FairMapDirectionsOpener { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            true
        }
    }
    FairMapScreenContent(events, provider, opener, observeConnectivity = true)
}


@Composable
internal fun FairMapScreen(
    events: List<FairEvent>,
    readinessProvider: FairMapReadinessProvider,
    directionsOpener: FairMapDirectionsOpener
) {
    FairMapScreenContent(events, readinessProvider, directionsOpener, observeConnectivity = false)
}

internal fun validFairEvents(events: List<FairEvent>): List<FairEvent> = events.filter { event ->
    event.latitude.isFinite() && event.longitude.isFinite() &&
        event.latitude in -90.0..90.0 && event.longitude in -180.0..180.0
}

internal fun fairDirectionsUrl(event: FairEvent): String? {
    if (validFairEvents(listOf(event)).isEmpty()) return null
    return Uri.parse("https://www.google.com/maps/dir/")
        .buildUpon()
        .appendQueryParameter("api", "1")
        .appendQueryParameter("destination", "${event.latitude},${event.longitude}")
        .build()
        .toString()
}

internal fun formatFairEventDate(raw: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    val position = ParsePosition(0)
    val parsed = parser.parse(raw, position)
    if (parsed == null || position.index != raw.length) raw
    else SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CL")).format(parsed)
} catch (_: RuntimeException) {
    raw
}

private fun readFairMapReadiness(context: Context): FairMapReadiness {
    val validatedInternet = try {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val capabilities = if (manager != null && network != null) manager.getNetworkCapabilities(network) else null
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: RuntimeException) {
        false
    }
    return FairMapReadiness(validatedInternet)
}

private fun FairMapReadinessProvider.readSafely() = try {
    read()
} catch (_: RuntimeException) {
    FairMapReadiness(false)
}

@Composable
private fun FairMapScreenContent(
    events: List<FairEvent>,
    readinessProvider: FairMapReadinessProvider,
    directionsOpener: FairMapDirectionsOpener,
    observeConnectivity: Boolean
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val markers = remember(events) { validFairEvents(events) }
    var readiness by remember(readinessProvider) { mutableStateOf(readinessProvider.readSafely()) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var directionsError by remember { mutableStateOf(false) }
    var cameraLatitude by rememberSaveable { mutableStateOf(Double.NaN) }
    var cameraLongitude by rememberSaveable { mutableStateOf(Double.NaN) }
    var cameraZoom by rememberSaveable { mutableStateOf(12f) }
    var framedSignature by rememberSaveable { mutableStateOf<String?>(null) }
    var mapLoaded by remember { mutableStateOf(false) }
    var mapTimedOut by rememberSaveable { mutableStateOf(false) }
    var mapAttempt by rememberSaveable { mutableIntStateOf(0) }
    var focusRequest by rememberSaveable { mutableStateOf<Long?>(null) }
    val refresh by rememberUpdatedState(newValue = { retry: Boolean ->
        val current = readinessProvider.readSafely()
        readiness = current
        if (!current.validatedInternet) {
            mapLoaded = false
            mapTimedOut = false
        } else if (retry) {
            mapLoaded = false
        }
    })
    val selectedEvent = events.firstOrNull { it.id == selectedId } ?: events.firstOrNull()
    val selectedEventId = selectedEvent?.id
    val canShowMap = markers.isNotEmpty() && readiness.validatedInternet

    LaunchedEffect(events.map { it.id }) {
        if (selectedId !in events.map { it.id }) selectedId = events.firstOrNull()?.id
    }
    LaunchedEffect(selectedEventId) { directionsError = false }
    LaunchedEffect(canShowMap, mapLoaded, mapTimedOut, mapAttempt) {
        if (canShowMap && !mapLoaded && !mapTimedOut) {
            delay(18_000)
            if (!mapLoaded) mapTimedOut = true
        }
    }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh(false)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context, readinessProvider, observeConnectivity) {
        if (!observeConnectivity) {
            onDispose { }
        } else {
            val manager = try {
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            } catch (_: RuntimeException) { null }
            if (manager == null) onDispose { } else {
                val executor = try { ContextCompat.getMainExecutor(context) } catch (_: RuntimeException) { null }
                val callback = object : ConnectivityManager.NetworkCallback() {
                    private fun update() {
                        try { executor?.execute { refresh(false) } } catch (_: RuntimeException) { }
                    }
                    override fun onAvailable(network: Network) = update()
                    override fun onLost(network: Network) = update()
                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = update()
                }
                val registered = try {
                    manager.registerDefaultNetworkCallback(callback)
                    true
                } catch (_: RuntimeException) { false }
                onDispose {
                    if (registered) try { manager.unregisterNetworkCallback(callback) } catch (_: RuntimeException) { }
                }
            }
        }
    }

    Page {
        Intro("Nos vemos en la feria", "Conoce las fibras\nde cerca.", "Explora las ferias y showrooms y consulta sus fechas y ubicaciones.")
        if (events.isEmpty()) {
            Column(Modifier.fillMaxWidth().testTag("fair-events-empty")) {
                StateContent("Pronto habrá nuevas ferias", "Aquí podrás consultar sus fechas y ubicaciones.", R.drawable.la_map_pin)
            }
        } else {
            when {
                markers.isEmpty() -> FairMapUnavailable("Aún no hay ubicaciones disponibles para mostrar.", retry = null)
                !canShowMap || mapTimedOut -> FairMapUnavailable("El mapa no está disponible ahora.") {
                    mapLoaded = false
                    mapTimedOut = false
                    mapAttempt++
                    refresh(true)
                }
                else -> {
                    val signature = remember(markers) { markers.joinToString("|") { "${it.id}:${it.latitude}:${it.longitude}" } }
                    Box {
                        key(mapAttempt) {
                            FairOsmMap(
                                events = markers,
                                signature = signature,
                                selectedId = selectedEventId,
                                initialLatitude = cameraLatitude,
                                initialLongitude = cameraLongitude,
                                initialZoom = cameraZoom,
                                framedSignature = framedSignature,
                                focusRequest = focusRequest,
                                isLoaded = mapLoaded,
                                onLoaded = { mapLoaded = true },
                                onCameraChanged = { lat, lon, zoom ->
                                    cameraLatitude = lat
                                    cameraLongitude = lon
                                    cameraZoom = zoom
                                },
                                onFramed = { framedSignature = it },
                                onFocusConsumed = { focusRequest = null },
                                onFailure = { mapTimedOut = true },
                                onSelect = { selectedId = it }
                            )
                        }
                    }
                }
            }

            selectedEvent?.let { event ->
                FairEventDetail(event, directionsError) {
                    val url = fairDirectionsUrl(event)
                    directionsError = url == null || try { !directionsOpener.open(url) } catch (_: RuntimeException) { true }
                }
            }
            Text("Ferias y showrooms", style = MaterialTheme.typography.titleLarge)
            events.forEach { event ->
                FairEventRow(event, selected = event.id == selectedEventId) {
                    selectedId = event.id
                    directionsError = false
                    focusRequest = if (markers.any { it.id == event.id }) event.id else null
                }
            }
        }
    }
}

@Composable
private fun FairMapUnavailable(message: String, retry: (() -> Unit)?) {
    Surface(Modifier.fillMaxWidth().testTag("fair-map-unavailable"), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DesignIcon(R.drawable.la_map_pin, primary = true)
            Text(message, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text("Puedes consultar las fechas y ubicaciones en la lista.", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.secondary)
            if (retry != null) TextButton(retry, modifier = Modifier.testTag("fair-map-retry")) { Text("Reintentar") }
        }
    }
}

@Composable
private fun FairEventDetail(event: FairEvent, error: Boolean, onDirections: () -> Unit) {
    Surface(Modifier.fillMaxWidth().testTag("fair-event-detail-${event.id}"), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.name, style = MaterialTheme.typography.titleLarge)
            Text(event.date.takeIf { it.isNotBlank() }?.let(::formatFairEventDate) ?: "Fecha por confirmar", style = MaterialTheme.typography.bodyMedium)
            Text(event.location.takeIf { it.isNotBlank() } ?: "Ubicación por confirmar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            if (fairDirectionsUrl(event) != null) Button(onDirections, Modifier.fillMaxWidth().testTag("fair-directions-${event.id}")) { Text("Cómo llegar") }
            if (error) Text("No pudimos abrir las indicaciones. Intenta de nuevo.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FairEventRow(event: FairEvent, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics(mergeDescendants = true) {
            this.selected = selected
            testTag = "fair-event-${event.id}"
        },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DesignIcon(R.drawable.la_map_pin, primary = true)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(event.name, style = MaterialTheme.typography.labelLarge)
                Text(event.date.takeIf { it.isNotBlank() }?.let(::formatFairEventDate) ?: "Fecha por confirmar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Text(event.location.takeIf { it.isNotBlank() } ?: "Ubicación por confirmar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
