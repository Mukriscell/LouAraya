package cl.duoc.louaraya.presentation.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cl.duoc.louaraya.domain.model.FairEvent
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.MapTileProviderBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private const val MAP_TILE_SUCCESS_ID = MapTileProviderBase.MAPTILE_SUCCESS_ID
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

private class FairMapSession(
    val mapView: MapView,
    val provider: MapTileProviderBasic,
    val tileHandler: Handler,
    val active: AtomicBoolean = AtomicBoolean(true),
    val markers: MutableMap<Long, Marker> = linkedMapOf()
)

private data class FairMapCreation(val session: FairMapSession?)

@Composable
internal fun FairOsmMap(
    events: List<FairEvent>,
    signature: String,
    selectedId: Long?,
    initialLatitude: Double,
    initialLongitude: Double,
    initialZoom: Float,
    framedSignature: String?,
    focusRequest: Long?,
    isLoaded: Boolean,
    onLoaded: () -> Unit,
    onCameraChanged: (Double, Double, Float) -> Unit,
    onFramed: (String) -> Unit,
    onFocusConsumed: () -> Unit,
    onFailure: () -> Unit,
    onSelect: (Long) -> Unit
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val borderPixels = with(density) { 64.dp.roundToPx() }
    val tileLoadedCallback = rememberUpdatedState(onLoaded)
    val cameraCallback = rememberUpdatedState(onCameraChanged)
    val selectCallback = rememberUpdatedState(onSelect)
    val framedCallback = rememberUpdatedState(onFramed)
    val focusCallback = rememberUpdatedState(onFocusConsumed)
    val failureCallback = rememberUpdatedState(onFailure)

    val creation = remember(context) {
        FairMapCreation(
            createFairMapSession(
                context = context,
                initialLatitude = initialLatitude,
                initialLongitude = initialLongitude,
                initialZoom = initialZoom,
                firstEvent = events.first(),
                loadedCallback = tileLoadedCallback
            )
        )
    }
    val session = creation.session

    LaunchedEffect(session) {
        if (session == null) failureCallback.value()
    }

    if (session != null) {
        val mapListener = remember(session) {
            object : MapListener {
                private fun saveCamera(map: MapView) {
                    if (!session.active.get()) return
                    try {
                        val center = map.mapCenter
                        cameraCallback.value(center.latitude, center.longitude, map.zoomLevelDouble.toFloat())
                    } catch (_: RuntimeException) {

                    }
                }

                override fun onScroll(event: ScrollEvent): Boolean {
                    saveCamera(event.source)
                    return false
                }

                override fun onZoom(event: ZoomEvent): Boolean {
                    saveCamera(event.source)
                    return false
                }
            }
        }

        DisposableEffect(session, owner, mapListener) {
            val observer = LifecycleEventObserver { _, event ->
                try {
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> session.mapView.onResume()
                        Lifecycle.Event.ON_PAUSE -> session.mapView.onPause()
                        else -> Unit
                    }
                } catch (_: RuntimeException) {

                }
            }
            owner.lifecycle.addObserver(observer)
            session.mapView.addMapListener(mapListener)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                try { session.mapView.onResume() } catch (_: RuntimeException) { }
            }
            onDispose {
                session.active.set(false)
                owner.lifecycle.removeObserver(observer)
                session.mapView.removeMapListener(mapListener)
                session.provider.tileRequestCompleteHandlers.remove(session.tileHandler)
                session.tileHandler.removeCallbacksAndMessages(null)
                try {
                    session.mapView.setDestroyMode(false)
                    session.mapView.onPause()
                    session.mapView.onDetach()
                } catch (_: RuntimeException) {

                }
            }
        }

        DisposableEffect(session, signature, framedSignature, focusRequest, events, borderPixels) {
            val mapView = session.mapView
            val pending = AtomicBoolean(true)
            val action = Runnable {
                if (!pending.get() || !session.active.get()) return@Runnable
                try {
                    val focused = events.firstOrNull { it.id == focusRequest }
                    when {
                        focused != null -> {
                            val target = GeoPoint(focused.latitude, focused.longitude)
                            val targetZoom = maxOf(mapView.zoomLevelDouble, 14.0).coerceAtMost(mapView.maxZoomLevel)
                            mapView.controller.animateTo(target, targetZoom, 300L)
                            session.markers[focused.id]?.showInfoWindow()
                            framedCallback.value(signature)
                            focusCallback.value()
                        }
                        focusRequest != null -> focusCallback.value()
                        framedSignature != signature -> {
                            frameAllEvents(mapView, events, borderPixels)
                            framedCallback.value(signature)
                        }
                    }
                } catch (_: RuntimeException) {
                    failureCallback.value()
                }
            }
            val firstLayoutListener = MapView.OnFirstLayoutListener { _, _, _, _, _ -> action.run() }
            if (mapView.isLayoutOccurred) mapView.post(action)
            else mapView.addOnFirstLayoutListener(firstLayoutListener)

            onDispose {
                pending.set(false)
                mapView.removeOnFirstLayoutListener(firstLayoutListener)
                mapView.removeCallbacks(action)
            }
        }

        val mapModifier = Modifier.fillMaxSize().testTag("fair-map-view")
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .then(if (isLoaded) Modifier.testTag("fair-map-ready") else Modifier)
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { session.mapView },
                modifier = mapModifier,
                update = { map ->
                    try {
                        syncFairMarkers(session, events, selectedId) { id -> selectCallback.value(id) }
                    } catch (_: RuntimeException) {
                        failureCallback.value()
                    }
                }
            )
            Text(
                text = "© OpenStreetMap contributors",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                    .clickable { openCopyrightPage(context) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("fair-map-attribution"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!isLoaded) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Cargando el mapa…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun createFairMapSession(
    context: Context,
    initialLatitude: Double,
    initialLongitude: Double,
    initialZoom: Float,
    firstEvent: FairEvent,
    loadedCallback: androidx.compose.runtime.State<() -> Unit>
): FairMapSession? {
    var provider: MapTileProviderBasic? = null
    var mapView: MapView? = null
    return try {
        val configuration = Configuration.getInstance()
        val osmDirectory = File(context.filesDir, "osmdroid")
        configuration.osmdroidBasePath = osmDirectory
        configuration.osmdroidTileCache = File(osmDirectory, "tiles")
        configuration.userAgentValue = fairMapUserAgent(context)
        configuration.expirationExtendedDuration = 0L
        configuration.expirationOverrideDuration = null

        val newProvider = MapTileProviderBasic(context.applicationContext, TileSourceFactory.MAPNIK)
        provider = newProvider
        val newMapView = MapView(context, newProvider)
        mapView = newMapView
        newMapView.setDestroyMode(false)
        newMapView.setMultiTouchControls(true)
        newMapView.setTilesScaledToDpi(true)
        newMapView.setBuiltInZoomControls(false)
        newMapView.setMinZoomLevel(2.0)
        newMapView.setMaxZoomLevel(19.0)
        val center = if (
            initialLatitude.isFinite() && initialLongitude.isFinite() &&
            initialLatitude in -90.0..90.0 && initialLongitude in -180.0..180.0
        ) {
            GeoPoint(initialLatitude, initialLongitude)
        } else {
            GeoPoint(firstEvent.latitude, firstEvent.longitude)
        }
        val zoom = initialZoom.takeIf { it.isFinite() }?.toDouble()?.coerceIn(2.0, 19.0) ?: 12.0
        newMapView.controller.setZoom(zoom)
        newMapView.controller.setCenter(center)

        val active = AtomicBoolean(true)
        val tileHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                if (active.get() && message.what == MAP_TILE_SUCCESS_ID) loadedCallback.value.invoke()
            }
        }
        newProvider.tileRequestCompleteHandlers.add(tileHandler)
        FairMapSession(newMapView, newProvider, tileHandler, active)
    } catch (_: RuntimeException) {
        try {
            if (mapView != null) {
                mapView.setDestroyMode(false)
                mapView.onDetach()
            } else {
                provider?.detach()
            }
        } catch (_: RuntimeException) { }
        null
    }
}

private fun fairMapUserAgent(context: Context): String {
    val version = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
            ?.takeIf { it.isNotBlank() }
    } catch (_: RuntimeException) {
        null
    }
    return "${context.packageName}/${version ?: "1"}"
}

private fun syncFairMarkers(
    session: FairMapSession,
    events: List<FairEvent>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    if (!session.active.get()) return
    val mapView = session.mapView
    val currentIds = events.mapTo(hashSetOf()) { it.id }
    session.markers.keys.filterNot(currentIds::contains).toList().forEach { id ->
        session.markers.remove(id)?.let { marker ->
            marker.closeInfoWindow()
            mapView.overlays.remove(marker)
        }
    }

    events.forEach { event ->
        val marker = session.markers[event.id] ?: Marker(mapView).apply {
            id = event.id.toString()
            setPanToView(true)
            setOnMarkerClickListener { clicked, source ->
                val clickedId = clicked.id?.toLongOrNull()
                if (clickedId == null || !session.active.get()) {
                    false
                } else {
                    onSelect(clickedId)
                    clicked.showInfoWindow()
                    source.controller.animateTo(clicked.position)
                    true
                }
            }
        }.also { created ->
            session.markers[event.id] = created
            mapView.overlays.add(created)
        }
        marker.position = GeoPoint(event.latitude, event.longitude)
        marker.title = event.name
        marker.snippet = listOfNotNull(
            event.date.takeIf(String::isNotBlank)?.let(::formatFairEventDate),
            event.location.takeIf(String::isNotBlank)
        ).joinToString(" · ")
        marker.alpha = if (selectedId == null || event.id == selectedId) 1f else 0.75f
    }
    mapView.invalidate()
}

private fun frameAllEvents(mapView: MapView, events: List<FairEvent>, borderPixels: Int) {
    if (events.isEmpty()) return
    val points = events.map { GeoPoint(it.latitude, it.longitude) }
    if (points.map { it.latitude to it.longitude }.distinct().size == 1) {
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(points.first())
        return
    }
    try {
        mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, borderPixels)
    } catch (_: RuntimeException) {
        val center = GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average())
        mapView.controller.setZoom(11.0)
        mapView.controller.setCenter(center)
    }
}

private fun openCopyrightPage(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OSM_COPYRIGHT_URL)))
    } catch (_: RuntimeException) {

    }
}
