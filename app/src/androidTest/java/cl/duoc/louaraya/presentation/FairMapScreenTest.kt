package cl.duoc.louaraya.presentation

import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import cl.duoc.louaraya.domain.model.FairEvent
import cl.duoc.louaraya.presentation.local.*
import cl.duoc.louaraya.ui.theme.LouArayaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test


class FairMapScreenTest {
    @get:Rule val compose = createComposeRule()
    private val events = listOf(
        FairEvent(1, "Feria de Primavera", "2026-11-14", "Parque Bicentenario", -33.401, -70.601),
        FairEvent(2, "Showroom de Fibras", "2026-11-21", "Barrio Italia", -33.447, -70.625)
    )
    private val offline = FairMapReadiness(false)

    @Test fun selectedEventSurvivesRestorationAndDirectionsUseItsCoordinates() {
        val restoration = StateRestorationTester(compose)
        var opened: String? = null
        restoration.setContent {
            LouArayaTheme {
                FairMapScreen(events, FairMapReadinessProvider { offline }, FairMapDirectionsOpener { opened = it; true })
            }
        }
        compose.onNodeWithTag("fair-map-unavailable").assertExists()
        compose.onNodeWithTag("fair-event-2").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("fair-event-detail-2").performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("fair-event-2").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("fair-event-detail-2").assertExists()
        compose.onNodeWithTag("fair-directions-2").performScrollTo().performClick()
        compose.runOnIdle {
            val uri = Uri.parse(requireNotNull(opened))
            assertEquals("https", uri.scheme)
            assertEquals("www.google.com", uri.host)
            assertEquals("/maps/dir/", uri.path)
            assertEquals("1", uri.getQueryParameter("api"))
            assertEquals("-33.447,-70.625", uri.getQueryParameter("destination"))
            assertNull(uri.getQueryParameter("key"))
            assertNull(uri.getQueryParameter("origin"))
        }
    }

    @Test fun retryRechecksAvailabilityAndExternalFailureKeepsTheListUsable() {
        var reads = 0
        compose.setContent {
            LouArayaTheme {
                FairMapScreen(events, FairMapReadinessProvider { reads++; offline },
                    FairMapDirectionsOpener { throw SecurityException("Unavailable activity") })
            }
        }
        compose.onNodeWithTag("fair-map-unavailable").assertExists()
        val before = compose.runOnIdle { reads }
        compose.onNodeWithTag("fair-map-retry").performScrollTo().performClick()
        compose.waitUntil(5_000) { reads > before }
        compose.onNodeWithTag("fair-event-1").performScrollTo().performClick()
        compose.onNodeWithTag("fair-directions-1").performScrollTo().performClick()
        compose.onNodeWithText("No pudimos abrir", substring = true).assertExists()
        compose.onNodeWithTag("fair-event-2").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("fair-event-detail-2").assertExists()
    }

    @Test fun emptyCalendarHasAnExplicitEmptyState() {
        compose.setContent {
            LouArayaTheme {
                FairMapScreen(emptyList(), FairMapReadinessProvider { offline }, FairMapDirectionsOpener { error("No event to open") })
            }
        }
        compose.onNodeWithTag("fair-events-empty").assertExists()
        compose.onAllNodesWithTag("fair-directions-1").assertCountEquals(0)
    }

    @Test fun invalidCoordinatesKeepTheEventReadableWithoutCreatingAMapOrDirections() {
        compose.setContent {
            LouArayaTheme {
                FairMapScreen(listOf(events.first().copy(latitude = Double.NaN)),
                    FairMapReadinessProvider { FairMapReadiness(true) },
                    FairMapDirectionsOpener { error("Invalid destination") })
            }
        }
        compose.onNodeWithTag("fair-map-unavailable").assertExists()
        compose.onNodeWithTag("fair-event-detail-1").assertExists()
        compose.onNodeWithTag("fair-event-1").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithTag("fair-map-view").assertCountEquals(0)
        compose.onAllNodesWithTag("fair-directions-1").assertCountEquals(0)
    }

    @Test fun availabilityFailureLeavesLocalEventsUsable() {
        compose.setContent {
            LouArayaTheme {
                FairMapScreen(events, FairMapReadinessProvider { throw SecurityException("Network denied") },
                    FairMapDirectionsOpener { true })
            }
        }
        compose.onNodeWithTag("fair-map-unavailable").assertExists()
        compose.onNodeWithTag("fair-map-retry").performScrollTo().performClick()
        compose.onNodeWithTag("fair-event-2").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("fair-event-detail-2").assertExists()
    }
}
