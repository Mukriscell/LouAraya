package cl.duoc.louaraya.presentation

import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.MainActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File


class FairOsmMapIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realTilesMarkersSelectionAndCameraSurviveRecreation() {
        compose.signInDemo("cliente.demo")
        compose.onNodeWithText("Ver ferias").performScrollTo().performClick()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("fair-map-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fair-map-ready").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("fair-map-attribution").assertExists()
        capture("00-mapa-todos-los-eventos")
        var selectedPoint: GeoPoint? = null
        var selectedId: Long? = null
        var camera: GeoPoint? = null
        var zoom = 0.0
        onView(isAssignableFrom(MapView::class.java)).check { view, failure ->
            if (failure != null) throw failure
            val map = view as MapView
            val markers = map.overlays.filterIsInstance<Marker>()
            assertTrue("Every stored fair should have a marker", markers.size >= 2)
            markers.forEach { marker ->
                val point = map.projection.toPixels(marker.position, null)
                assertTrue("Marker ${marker.id} must fit above its anchor", point.y >= marker.icon.intrinsicHeight)
                assertTrue("Marker ${marker.id} must fit horizontally",
                    point.x >= marker.icon.intrinsicWidth / 2 && point.x + marker.icon.intrinsicWidth / 2 <= map.width)
                assertTrue("Marker ${marker.id} must fit below its anchor", point.y < map.height)
            }
            val target = markers.last()
            selectedPoint = GeoPoint(target.position)
            selectedId = target.id.toLong()
            val pixel = map.projection.toPixels(target.position, null)

            val tap = MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP,
                pixel.x.toFloat(), pixel.y - target.icon.intrinsicHeight / 2f, 0)
            try { assertTrue(target.onSingleTapConfirmed(tap, map)) } finally { tap.recycle() }
        }
        compose.onNodeWithTag("fair-event-detail-$selectedId").assertExists()
        compose.onNodeWithTag("fair-event-$selectedId").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("fair-map-ready").performScrollTo()
        compose.waitUntil(5_000) {
            var centered = false
            onView(isAssignableFrom(MapView::class.java)).check { view, _ ->
                val map = view as MapView
                centered = GeoPoint(map.mapCenter).distanceToAsDouble(selectedPoint) < 100
                if (centered) { camera = GeoPoint(map.mapCenter); zoom = map.zoomLevelDouble }
            }
            centered
        }
        capture("01-mapa-osm-ferias")
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithTag("fair-map-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("fair-event-$selectedId").assertIsSelected()
        onView(isAssignableFrom(MapView::class.java)).check { view, _ ->
            val map = view as MapView
            assertTrue(GeoPoint(map.mapCenter).distanceToAsDouble(camera) < 100)
            assertEquals(zoom, map.zoomLevelDouble, 0.1)
        }
        capture("02-mapa-restaurado")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "mapa-osm-evidence").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}
