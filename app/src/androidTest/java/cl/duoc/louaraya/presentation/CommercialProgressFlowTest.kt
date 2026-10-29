package cl.duoc.louaraya.presentation

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.model.Collection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class CommercialProgressFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val cases get() = (compose.activity.application as LouArayaApplication).container.localUseCases
    private fun waitText(value: String) = compose.waitUntil(25_000) { compose.onAllNodesWithText(value).fetchSemanticsNodes().isNotEmpty() }
    private fun click(value: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(value) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(value).performScrollTo().performClick()
    }
    private fun fill(label: String, value: String) {
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        closeSoftKeyboard()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "commercial-progress-evidence").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun campaignJourneyCollectionFavoriteRequestAgreementAndResultsRemainLinked() {
        val unique = System.currentTimeMillis()
        val campaignName = "Recorrido demo $unique"
        val collectionName = "Colección demo $unique"
        val campaign = runBlocking {
            cases.initialize(); cases.signIn("admin.demo", "LouDemo2026!")
            val collection = cases.saveCollection(Collection(name = collectionName, productIds = listOf(1)))
            val draft = Campaign(name = campaignName, collectionId = collection, startsOn = "2000-01-01", endsOn = "2099-12-31", published = true)
            draft.copy(id = cases.saveCampaign(draft))
        }
        compose.signInDemo("cliente.demo")
        runBlocking { cases.setFavorite(1, false) }
        click(campaignName)
        waitText("Guardar colección")
        click("Guardar colección")
        compose.waitUntil(15_000) { runBlocking { campaign.collectionId in cases.snapshot().favoriteCollectionIds } }
        click("Tapiz Aurora")
        waitText("La pieza")
        compose.waitUntil(15_000) { compose.onAllNodes(hasContentDescription("Guardar favorito") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Guardar favorito").performClick()
        click("Solicitar esta pieza")
        compose.onNodeWithText("Reserva").onParent().performScrollTo()
        compose.onNodeWithText("Reserva").performClick().assertIsSelected()
        click("Solicitar reserva")
        waitText("Solicitud registrada.")
        click("Ver solicitud")
        waitText("Origen y seguimiento")
        val request = runBlocking { cases.snapshot().requests.single { it.campaignId == campaign.id } }
        assertEquals(campaign.collectionId, request.sourceCollectionId)
        assertNotNull(request.journeyId)
        val eventsBefore = runBlocking { cases.snapshot().explorationEvents.filter { it.journeyId == request.journeyId } }
        assertTrue(eventsBefore.any { it.kind == ExplorationKind.PRODUCT_FAVORITE })
        compose.activityRule.scenario.recreate()
        waitText("Origen y seguimiento")
        assertEquals(eventsBefore, runBlocking { cases.snapshot().explorationEvents.filter { it.journeyId == request.journeyId } })
        compose.onNodeWithText("Campaña de origen").performScrollTo().assertIsDisplayed()
        shot("01-solicitud-con-origen")
        compose.signInDemo("admin.demo")
        compose.onNodeWithTag("tab-requests").performClick()
        click("Solicitud #${request.id} · Reserva")
        fill("Importe acordado · CLP", "62000")
        click("Guardar importe acordado")
        waitText("Importe acordado guardado")
        val updated = runBlocking { cases.snapshot().requests.single { it.id == request.id } }
        assertEquals(request.total, updated.total)
        assertEquals(62000L, updated.agreedTotal)
        compose.onNodeWithText("Total acordado").performScrollTo().assertIsDisplayed()
        shot("02-importe-acordado")
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("tab-campaigns").performClick()
        click("Ver resultados: $campaignName")
        waitText("Resultados de campaña")
        compose.onNodeWithText("Solicitud #${request.id}").performScrollTo().assertIsDisplayed()
        shot("03-resultados-campana")
        compose.signInDemo("cliente.demo")
        compose.onNodeWithTag("tab-favorites").performClick()
        compose.onNodeWithText(collectionName).performScrollTo().assertIsDisplayed()
    }

    @Test fun campaignEditorPublishesOnlyToMatchingSubscribedClients() {
        val unique = System.currentTimeMillis()
        val campaignName = "Segmentada demo $unique"
        val fixture = runBlocking {
            cases.initialize()
            val a = cases.register("Cliente ficticio A", "segmento.a.$unique", "LouDemo2026!", 0, "")
            cases.editProfile(a.name, 0, "", true, CampaignCategory.TRAVEL.name)
            val b = cases.register("Cliente ficticio B", "segmento.b.$unique", "LouDemo2026!", 0, "")
            cases.editProfile(b.name, 0, "", true, CampaignCategory.WEDDINGS.name)
            val c = cases.register("Cliente ficticio C", "segmento.c.$unique", "LouDemo2026!", 0, "")
            cases.editProfile(c.name, 0, "", false, CampaignCategory.TRAVEL.name)
            cases.signIn("admin.demo", "LouDemo2026!")
            val collection = cases.saveCollection(Collection(name = "Grupo segmento $unique", productIds = listOf(1)))
            val id = cases.saveCampaign(Campaign(name = campaignName, collectionId = collection, startsOn = "2000-01-01", endsOn = "2099-12-31"))
            listOf(id, a.id, b.id, c.id)
        }
        compose.signInDemo("admin.demo")
        compose.onNodeWithTag("tab-campaigns").performClick()
        click(campaignName)
        compose.onNodeWithText("Viajes").onParent().performScrollTo()
        compose.onNodeWithText("Viajes").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        shot("04-destinatarios-campana")
        click("Guardar campaña")
        waitText("Crear campaña")
        val campaign = runBlocking { cases.snapshot().campaigns.single { it.id == fixture[0] } }
        assertEquals(CampaignCategory.TRAVEL, campaign.targetCategory)
        assertTrue(campaign.published)
        SQLiteDatabase.openDatabase(instrumentation.targetContext.getDatabasePath("louaraya.db").path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            for ((index, user) in fixture.drop(1).withIndex()) db.rawQuery("SELECT COUNT(*) FROM local_notices WHERE userId = ? AND destination = ?",
                arrayOf(user.toString(), "campaign/${campaign.id}")).use { cursor ->
                cursor.moveToFirst(); assertEquals(if (index == 0) 1 else 0, cursor.getInt(0))
            }
        }
    }
}
