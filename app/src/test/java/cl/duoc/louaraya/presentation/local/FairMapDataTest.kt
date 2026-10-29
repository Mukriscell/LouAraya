package cl.duoc.louaraya.presentation.local

import android.app.Application
import android.net.Uri
import cl.duoc.louaraya.domain.model.FairEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FairMapDataTest {
    private val event = FairEvent(1, "Feria", "2026-11-14", "Santiago", -33.401, -70.601)

    @Test fun invalidCoordinatesNeverBecomeMarkersOrExternalDestinations() {
        val invalid = listOf(
            event.copy(id = 2, latitude = Double.NaN), event.copy(id = 3, longitude = Double.POSITIVE_INFINITY),
            event.copy(id = 4, latitude = 90.001), event.copy(id = 5, longitude = -180.001)
        )
        assertEquals(listOf(event), validFairEvents(listOf(event) + invalid))
        invalid.forEach { assertNull(fairDirectionsUrl(it)) }
        val boundary = event.copy(latitude = -90.0, longitude = 180.0)
        assertEquals(listOf(boundary), validFairEvents(listOf(boundary)))
    }

    @Test fun directionsKeepCoordinateDecimalPointsInASpanishLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("es-CL"))
            val uri = Uri.parse(requireNotNull(fairDirectionsUrl(event)))
            assertEquals("-33.401,-70.601", uri.getQueryParameter("destination"))
            assertEquals("1", uri.getQueryParameter("api"))
            assertEquals("https", uri.scheme)
            assertNull(uri.getQueryParameter("key"))
        } finally { Locale.setDefault(previous) }
    }

    @Test fun validDateIsReadableAndInvalidDateIsNotSilentlyChanged() {
        assertNotEquals(event.date, formatFairEventDate(event.date))
        assertTrue(formatFairEventDate(event.date).contains("2026"))
        for (raw in listOf("2026-02-30", "2026-11-14-extra", "", "fecha pendiente")) {
            assertEquals(raw, formatFairEventDate(raw))
        }
    }
}
