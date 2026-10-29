package cl.duoc.louaraya.presentation.local

import org.junit.Assert.*
import org.junit.Test

class AuthTimelineTest {
    @Test fun carouselHoldsEachPhotoAndUsesOnePositionForBothIndicators() {
        assertEquals(0f, AuthTimeline.carousel(0f), .001f)
        assertEquals(0f, AuthTimeline.carousel(3_000f), .001f)
        assertEquals(.5f, AuthTimeline.carousel(3_500f), .005f)
        assertEquals(1f, AuthTimeline.carousel(4_000f), .001f)
        assertEquals(1f, AuthTimeline.carousel(7_000f), .001f)
        assertEquals(.5f, AuthTimeline.carousel(7_500f), .005f)
        assertEquals(0f, AuthTimeline.carousel(8_000f), .001f)
    }
    @Test fun movementContinuesSeamlesslyAfterTheFigmaTimelineEnds() {
        listOf(0f, 1_000f, 3_500f, 4_000f, 7_500f).forEach { time ->
            assertEquals(AuthTimeline.carousel(time), AuthTimeline.carousel(time + 64_000f), .001f)
            assertEquals(AuthTimeline.thread(time), AuthTimeline.thread(time + 64_000f), .001f)
            AuthScene.entries.forEach { scene ->
                assertEquals(AuthTimeline.sealDegrees(time, scene), AuthTimeline.sealDegrees(time + 64_000f, scene), .001f)
            }
        }
        assertNotEquals(AuthTimeline.thread(64_000f), AuthTimeline.thread(66_000f))
    }
    @Test fun threadsAndSealsHaveTheOriginalSymmetricTimingAndRestingPoses() {
        assertEquals(0f, AuthTimeline.thread(0f), .001f)
        assertEquals(1f, AuthTimeline.thread(4_000f), .001f)
        assertEquals(0f, AuthTimeline.thread(8_000f), .001f)
        assertTrue(AuthTimeline.sealDegrees(0f, AuthScene.WELCOME) > 0f)
        assertTrue(AuthTimeline.sealDegrees(0f, AuthScene.REGISTER) < 0f)
        assertTrue(AuthTimeline.sealDegrees(2_000f, AuthScene.WELCOME) < AuthTimeline.sealDegrees(6_000f, AuthScene.WELCOME))
    }
}
