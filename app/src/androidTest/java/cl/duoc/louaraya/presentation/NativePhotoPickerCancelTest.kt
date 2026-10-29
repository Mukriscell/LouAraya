package cl.duoc.louaraya.presentation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue


class NativePhotoPickerCancelTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun nativePhotoPickerCanBeCancelledWithoutCreatingAnImageSelection() {
        compose.signInDemo("admin.demo")
        compose.onNodeWithTag("tab-products").performClick()
        compose.onAllNodesWithContentDescription("Editar fotografía:", substring = true)[0].performScrollTo().performClick()
        compose.onNodeWithTag("pick-product-image").performClick()

        val appPackage = compose.activity.packageName
        compose.waitUntil(10_000) {
            val rootPackage = InstrumentationRegistry.getInstrumentation().uiAutomation
                .rootInActiveWindow?.packageName?.toString()
            rootPackage != null && rootPackage != appPackage
        }
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent KEYCODE_BACK").use { }

        compose.waitUntil(10_000) {
            val rootPackage = InstrumentationRegistry.getInstrumentation().uiAutomation
                .rootInActiveWindow?.packageName?.toString()
            rootPackage == appPackage && compose.onAllNodesWithTag("pick-product-image")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodesWithTag("save-product-image").fetchSemanticsNodes().isEmpty())
    }
}
