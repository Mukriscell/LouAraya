package cl.duoc.louaraya.presentation.common

import androidx.window.core.layout.WindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("DEPRECATION")
class AdaptiveLayoutTest {
    @Test
    fun officialWindowBreakpointsSelectCompactMediumAndExpandedLayouts() {
        assertEquals(
            LouArayaWindowSizeClass.COMPACT,
            louArayaWindowSizeClass(WindowSizeClass.compute(599f, 800f))
        )
        assertEquals(
            LouArayaWindowSizeClass.MEDIUM,
            louArayaWindowSizeClass(WindowSizeClass.compute(600f, 800f))
        )
        assertEquals(
            LouArayaWindowSizeClass.MEDIUM,
            louArayaWindowSizeClass(WindowSizeClass.compute(839f, 800f))
        )
        assertEquals(
            LouArayaWindowSizeClass.EXPANDED,
            louArayaWindowSizeClass(WindowSizeClass.compute(840f, 800f))
        )
        assertEquals(
            LouArayaWindowSizeClass.EXPANDED,
            louArayaWindowSizeClass(WindowSizeClass.compute(1600f, 900f))
        )
    }

}
