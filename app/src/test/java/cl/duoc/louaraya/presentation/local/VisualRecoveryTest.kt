package cl.duoc.louaraya.presentation.local

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import cl.duoc.louaraya.data.image.LocalProductImageStorage
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.data.repository.RoomLocalRepository
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.Campaign
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.model.UserProfile
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import cl.duoc.louaraya.presentation.common.ProductImage
import cl.duoc.louaraya.ui.theme.LouArayaTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualRecoveryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun waitFor(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun missingCustomProductFileDoesNotShowTheDemoPhotograph() {
        val storage = LocalProductImageStorage(RuntimeEnvironment.getApplication())
        compose.setContent { LouArayaTheme {
            ProductImage(1, "product-images/00000000-0000-0000-0000-000000000000.jpg", storage, contentDescription = "Fotografía personalizada")
        } }
        waitFor("Imagen no disponible")
        compose.onNodeWithContentDescription("Fotografía personalizada").assertDoesNotExist()
    }

    @Test fun loadingAndFailedCustomProductImageNeverFlashTheDemoPhotograph() {
        val storage = FailingImages(CompletableDeferred())
        compose.setContent { LouArayaTheme {
            ProductImage(1, "product-images/custom.jpg", storage, contentDescription = "Fotografía personalizada")
        } }
        waitFor("Cargando imagen…")
        compose.onNodeWithContentDescription("Fotografía personalizada").assertDoesNotExist()
        compose.runOnIdle { storage.release.complete(Unit) }
        waitFor("Imagen no disponible")
        compose.onNodeWithContentDescription("Fotografía personalizada").assertDoesNotExist()
    }

    @Test fun originalDemoProductStillShowsItsPhotograph() {
        compose.setContent { LouArayaTheme {
            ProductImage(1, null, null, contentDescription = "Tapiz Aurora")
        } }
        compose.onNodeWithContentDescription("Tapiz Aurora").assertIsDisplayed()
        compose.onNodeWithText("Imagen no disponible").assertDoesNotExist()
    }

    @Test fun avatarReadFailureKeepsTheIllustrationWithoutClosingTheScreen() {
        val storage = FailingImages(CompletableDeferred())
        compose.setContent { LouArayaTheme {
            ProfileAvatar(UserProfile(7, "Cuenta de prueba", "cuenta.prueba", avatarImageReference = "avatar-images/custom.jpg"), storage)
        } }
        compose.waitUntil(10_000) { storage.started.get() > 0 }
        compose.runOnIdle { storage.release.complete(Unit) }
        compose.waitUntil(10_000) { storage.finished.get() > 0 }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Avatar Flor").assertExists()
        compose.onNodeWithContentDescription("Foto de perfil de Cuenta de prueba").assertDoesNotExist()
    }

    @Test fun avatarArtworkStaysSquareAndInsideEachChoiceOnANarrowScreen() {
        compose.setContent { LouArayaTheme {
            Box(Modifier.width(272.dp)) { AvatarChoices(0) {} }
        } }
        for (index in avatarNames.indices) {
            val choice = compose.onNodeWithTag("avatar-$index").fetchSemanticsNode().boundsInRoot
            val image = compose.onNodeWithTag("avatar-artwork-$index", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(image.width, image.height, 1f)
            assertTrue(image.left >= choice.left && image.right <= choice.right)
        }
        compose.onNodeWithTag("avatar-7").performClick()
    }

    @Test fun correctingCampaignStartClearsTheDateRangeErrorAndSavesTheCampaign() {
        val database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LouArayaDatabase::class.java)
            .addCallback(DemoSeedCallback()).build()
        val useCases = LocalUseCases(RoomLocalRepository(database))
        val collection = runBlocking {
            useCases.initialize()
            useCases.signIn("admin.demo", "LouDemo2026!")
            val draft = Collection(name = "Colección de prueba")
            draft.copy(id = useCases.saveCollection(draft))
        }
        lateinit var vm: LocalAppViewModel
        val store = ViewModelStore()
        try {
            val saved = AtomicBoolean()
            compose.setContent { LouArayaTheme {
                val model = remember { LocalAppViewModel(useCases).also { vm = it; store.put("visual-recovery", it) } }
                val state by model.uiState.collectAsState()
                CompositionLocalProvider(LocalBusy provides state.busy) {
                    CampaignEditor(Campaign(name = "Campaña de prueba", collectionId = collection.id, startsOn = "2030-11-20", endsOn = "2030-11-10"), listOf(collection), model) { saved.set(true) }
                }
            } }
            compose.waitUntil(15_000) {
                compose.onRoot().fetchSemanticsNode()
                vm.uiState.value.initialized && !vm.uiState.value.busy
            }
            compose.onNodeWithText("Guardar campaña").performClick()
            waitFor("La fecha final debe ser posterior o igual al inicio.")
            assertTrue(runBlocking { useCases.snapshot().campaigns.isEmpty() })
            compose.onNodeWithText("Inicio · AAAA-MM-DD").performScrollTo().performTextReplacement("2030-11-01")
            compose.onNodeWithText("La fecha final debe ser posterior o igual al inicio.").assertDoesNotExist()
            compose.onNodeWithText("Guardar campaña").assertIsEnabled().performClick()
            try {
                compose.waitUntil(15_000) {

                    compose.onRoot().fetchSemanticsNode()
                    saved.get()
                }
            } catch (timeout: ComposeTimeoutException) {
                val state = vm.uiState.value
                println(compose.onRoot(useUnmergedTree = true).printToString())
                throw AssertionError("Campaign did not finish: busy=${state.busy}, error=${state.error}, fields=${state.fieldErrors}", timeout)
            }
            val campaign = runBlocking { useCases.snapshot().campaigns.single() }
            assertEquals("2030-11-01", campaign.startsOn)
            assertEquals("2030-11-10", campaign.endsOn)
        } finally { store.clear(); database.close() }
    }

    private class FailingImages(val release: CompletableDeferred<Unit>) : ProductImageStorage {
        val started = AtomicInteger()
        val finished = AtomicInteger()
        override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? {
            started.incrementAndGet()
            release.await()
            try { throw IOException("Almacenamiento no disponible") }
            finally { finished.incrementAndGet() }
        }
        override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? = null
        override suspend fun saveFromSource(sourceUri: String): String = error("Read-only test")
        override suspend fun delete(reference: String) = Unit
    }
}
