package cl.duoc.louaraya.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.domain.model.Collection
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test


class NavigationTabsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun discoverReturnsToHomeRepeatedlyAndAfterRestoration() {
        compose.signInDemo("cliente.demo")
        assertDashboard()

        discoverCatalog()
        compose.onNodeWithTag("tab-home").performClick()
        assertDashboard()


        discoverCatalog()
        compose.onNodeWithTag("tab-home").performClick()
        assertDashboard()


        compose.onNodeWithTag("tab-catalog").performClick()
        waitForText("Encuentra tu pieza.")
        compose.activityRule.scenario.recreate()
        waitForText("Encuentra tu pieza.")
        compose.onNodeWithTag("tab-home").performClick()
        assertDashboard()


        compose.onNodeWithTag("tab-favorites").performClick()
        compose.onNodeWithTag("tab-favorites").assertIsSelected()
        compose.onNodeWithTag("tab-home").performClick()
        assertDashboard()

        compose.onNodeWithTag("tab-requests").performClick()
        compose.onNodeWithTag("tab-requests").assertIsSelected()
        compose.onNodeWithTag("tab-home").performClick()
        assertDashboard()
    }

    @Test fun catalogFiltersSurviveTabSwitchAndActivityRecreation() {
        compose.signInDemo("cliente.demo")
        discoverCatalog()

        compose.onNodeWithText("Buscar una pieza").performScrollTo().performTextReplacement("Aurora")
        compose.onNodeWithText("Festivales").performScrollTo().performClick().assertIsSelected()
        waitForText("No encontramos\ncoincidencias.")

        compose.onNodeWithTag("tab-home").performClick()
        assertDashboard()
        compose.onNodeWithTag("tab-catalog").performClick()
        waitForText("Encuentra tu pieza.")
        waitForText("No encontramos\ncoincidencias.")
        compose.onNodeWithText("Festivales").assertIsSelected()

        compose.activityRule.scenario.recreate()
        waitForText("Encuentra tu pieza.")
        waitForText("No encontramos\ncoincidencias.")
        compose.onNodeWithText("Festivales").assertIsSelected()
    }

    @Test fun administratorDashboardShortcutsAndBackReturnToTheIntendedRoot() {
        compose.signInDemo("admin.demo")
        assertAdminDashboard()

        openAdminShortcut("Productos del taller", "tab-products")
        returnToAdminHome()

        openAdminShortcut("Colecciones y campañas", "tab-campaigns")
        returnToAdminHome()

        openAdminShortcut("Ver todas", "tab-requests")
        returnToAdminHome()

        compose.onNodeWithText("Explorar catálogo").performScrollTo().performClick()
        waitForText("Encuentra tu pieza.")
        compose.onNodeWithContentDescription("Volver").performClick()
        assertAdminDashboard()
    }

    @Test fun collectionBackReturnsToCatalogAndHomeDoesNotRestoreCollection() {
        val collectionName = "Colección de navegación ${System.currentTimeMillis()}"
        val cases = (compose.activity.application as LouArayaApplication).container.localUseCases
        runBlocking {
            cases.initialize()
            cases.signIn("admin.demo", "LouDemo2026!")
            cases.saveCollection(Collection(name = collectionName, productIds = listOf(1L)))
            cases.signIn("cliente.demo", "LouDemo2026!")
        }
        compose.signInDemo("cliente.demo")
        compose.onNodeWithTag("tab-catalog").performClick()
        waitForText("Encuentra tu pieza.")
        openCollection(collectionName)
        compose.onNodeWithTag("tab-catalog").assertIsSelected()

        compose.onNodeWithContentDescription("Volver").performClick()
        waitForText("Encuentra tu pieza.")
        compose.onNodeWithTag("tab-catalog").assertIsSelected()
        openCollection(collectionName)

        compose.onNodeWithTag("tab-home").performClick()
        assertDashboard()
        compose.onNodeWithText(collectionName).assertDoesNotExist()
        compose.onNodeWithTag("tab-catalog").performClick()
        waitForText("Encuentra tu pieza.")
        compose.onNodeWithTag("tab-catalog").assertIsSelected()
        compose.onNodeWithText("Colección").assertDoesNotExist()
        compose.onNodeWithText("Guardar colección").assertDoesNotExist()
    }

    private fun discoverCatalog() {
        compose.onNodeWithText("Explorar piezas").performScrollTo().performClick()
        waitForText("Encuentra tu pieza.")
        compose.onNodeWithTag("tab-catalog").assertIsSelected()
    }

    private fun assertDashboard() {
        waitForText("Texturas que\nhacen hogar.")
        compose.onNodeWithTag("tab-home").assertIsSelected()
        compose.onNodeWithText("Encuentra tu pieza.").assertDoesNotExist()
    }

    private fun assertAdminDashboard() {
        waitForText("Hoy, en el taller.")
        compose.onNodeWithTag("tab-home").assertIsSelected()
    }

    private fun returnToAdminHome() {
        compose.onNodeWithTag("tab-home").performClick()
        assertAdminDashboard()
    }

    private fun openAdminShortcut(label: String, selectedTab: String) {
        compose.onNodeWithText(label).performScrollTo().performClick()
        compose.onNodeWithTag(selectedTab).assertIsSelected()
    }

    private fun openCollection(name: String) {
        compose.onNodeWithText(name).performScrollTo().performClick()
        waitForText("Colección")
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("Volver") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
