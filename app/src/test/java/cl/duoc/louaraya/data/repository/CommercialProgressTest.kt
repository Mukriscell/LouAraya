package cl.duoc.louaraya.data.repository

import android.app.Application
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CommercialProgressTest {
    private lateinit var db: LouArayaDatabase
    private lateinit var repository: RoomLocalRepository
    private lateinit var cases: LocalUseCases
    private var time = 1000L
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LouArayaDatabase::class.java).addCallback(DemoSeedCallback()).build()
        repository = RoomLocalRepository(db)
        cases = LocalUseCases(repository) { time++ }
        cases.initialize()
    }
    @After fun close() { db.close() }
    private suspend fun admin() = cases.signIn("admin.demo", "LouDemo2026!")
    private suspend fun client() = cases.signIn("cliente.demo", "LouDemo2026!")
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Operation should be rejected") } catch (_: IllegalArgumentException) { }
    }
    private suspend fun campaign(name: String = "Campaña de prueba", category: CampaignCategory? = null): Campaign {
        admin()
        val collection = cases.saveCollection(Collection(name = "Colección $name", productIds = listOf(1)))
        val campaign = Campaign(name = name, collectionId = collection, startsOn = "2000-01-01", endsOn = "2099-12-31", published = true, targetCategory = category)
        return campaign.copy(id = cases.saveCampaign(campaign))
    }

    @Test fun campaignCollectionProductFavoritesAndRequestKeepOneExplicitOrigin() = runBlocking {
        val campaign = campaign()
        client()
        val source = cases.startCampaignJourney(campaign.id)
        rejected { cases.createRequest(1, RequestIntention.RESERVATION, 45000, "", null, source.id) }
        cases.setFavoriteCollection(campaign.collectionId, true, source.id)
        cases.recordProductView(1, source.id)
        cases.setFavorite(1, true, source.id)
        val request = cases.createRequest(1, RequestIntention.RESERVATION, 45000, "Origen real", null, source.id)
        val snapshot = LocalUseCases(RoomLocalRepository(db)).snapshot()
        assertEquals(campaign.id, request.campaignId)
        assertEquals(campaign.collectionId, request.sourceCollectionId)
        assertEquals(source.id, request.journeyId)
        assertEquals(setOf(campaign.collectionId), snapshot.favoriteCollectionIds)
        assertEquals(listOf(ExplorationKind.CAMPAIGN_VIEW, ExplorationKind.COLLECTION_VIEW, ExplorationKind.COLLECTION_FAVORITE,
            ExplorationKind.PRODUCT_VIEW, ExplorationKind.PRODUCT_FAVORITE, ExplorationKind.REQUEST_CREATED), snapshot.explorationEvents.map { it.kind })
        assertEquals(request.id, snapshot.explorationEvents.last().requestId)
        assertEquals(1, snapshot.journeys.size)
        cases.register("Otro ficticio", "otro.demo", "ClaveFicticia2026!", 0, "")
        assertTrue(cases.snapshot().explorationEvents.isEmpty())
        assertTrue(cases.snapshot().favoriteCollectionIds.isEmpty())
        rejected { cases.recordProductView(1, source.id) }
        rejected { cases.createRequest(1, RequestIntention.PURCHASE, 45000, "", null, source.id) }
        admin()
        rejected { cases.startCampaignJourney(campaign.id) }
        assertEquals(1, cases.snapshot().journeys.size)
    }

    @Test fun twoCampaignsSharingACollectionAreNeverInferredFromTheCollectionAlone() = runBlocking {
        val first = campaign()
        val second = first.copy(id = 0, name = "Otra campaña")
        val secondId = cases.saveCampaign(second)
        client()
        val a = cases.startCampaignJourney(first.id)
        cases.recordProductView(1, a.id)
        val requestA = cases.createRequest(1, RequestIntention.PURCHASE, 45000, "", null, a.id)
        val b = cases.startCampaignJourney(secondId)
        cases.recordProductView(1, b.id)
        val requestB = cases.createRequest(1, RequestIntention.PURCHASE, 45000, "", null, b.id)
        val direct = cases.createRequest(1, RequestIntention.PURCHASE, 45000, "", null)
        assertEquals(first.id, requestA.campaignId)
        assertEquals(secondId, requestB.campaignId)
        assertNull(direct.campaignId)
        assertNull(direct.journeyId)
    }

    @Test fun historicAttributionSurvivesCampaignAndCollectionEdits() = runBlocking {
        val campaign = campaign()
        client()
        val journey = cases.startCampaignJourney(campaign.id)
        rejected { cases.recordProductView(2, journey.id) }
        cases.recordProductView(1, journey.id)
        admin()
        cases.saveCampaign(campaign.copy(published = false))
        cases.saveCollection(Collection(campaign.collectionId, "Colección editada", productIds = emptyList()))
        client()
        val request = cases.createRequest(1, RequestIntention.RESERVATION, 45000, "", null, journey.id)
        assertEquals(campaign.id, request.campaignId)
        rejected { cases.startCampaignJourney(campaign.id) }
    }

    @Test fun collectionFavoritesAreOwnedIdempotentAndRemovable() = runBlocking {
        val campaign = campaign()
        rejected { cases.setFavoriteCollection(campaign.collectionId, true) }
        client()
        cases.setFavoriteCollection(campaign.collectionId, true)
        cases.setFavoriteCollection(campaign.collectionId, true)
        assertEquals(1, cases.snapshot().explorationEvents.count { it.kind == ExplorationKind.COLLECTION_FAVORITE })
        assertEquals(setOf(campaign.collectionId), cases.snapshot().favoriteCollectionIds)
        cases.setFavoriteCollection(campaign.collectionId, false)
        assertTrue(cases.snapshot().favoriteCollectionIds.isEmpty())
        rejected { cases.setFavoriteCollection(99999, true) }
    }

    @Test fun targetedPublicationUsesPreferenceAndFavoritesAndHonorsOptOut() = runBlocking {
        admin()
        val collection = cases.saveCollection(Collection(name = "Grupo de prueba", productIds = listOf(1)))
        val preferred = cases.register("Preferencia ficticia", "preferencia.demo", "ClaveFicticia2026!", 0, "")
        cases.editProfile(preferred.name, 0, "", true, CampaignCategory.TRAVEL.name)
        val favorite = cases.register("Favorito ficticio", "favorito.demo", "ClaveFicticia2026!", 0, "")
        cases.setFavoriteCollection(collection, true)
        val piece = cases.register("Pieza ficticia", "pieza.demo", "ClaveFicticia2026!", 0, "")
        val travelProduct = cases.snapshot().products.first { it.campaignCategory == CampaignCategory.TRAVEL }
        cases.setFavorite(travelProduct.id, true)
        val uninterested = cases.register("Sin interés ficticio", "sininteres.demo", "ClaveFicticia2026!", 0, "")
        val muted = cases.register("Sin avisos ficticio", "sinavisos.demo", "ClaveFicticia2026!", 0, "")
        cases.editProfile(muted.name, 0, "", false, CampaignCategory.TRAVEL.name)
        admin()
        val campaign = Campaign(name = "Aviso de viajes", collectionId = collection, startsOn = "2000-01-01", endsOn = "2099-12-31", published = true, targetCategory = CampaignCategory.TRAVEL)
        val id = cases.saveCampaign(campaign)
        cases.saveCampaign(campaign.copy(id = id, name = "Mismo aviso editado"))
        for (user in listOf(preferred, favorite, piece)) assertEquals(1, repository.notices(user.id).count { it.destination == "campaign/$id" })
        for (user in listOf(uninterested, muted)) assertTrue(repository.notices(user.id).isEmpty())
        assertEquals(CampaignCategory.TRAVEL, repository.campaigns().single().targetCategory)
        client()
        assertTrue(cases.snapshot().notices.isEmpty())
    }

    @Test fun agreedAmountKeepsReferenceAndHasAnAuditTrailWithoutDuplicateUpdates() = runBlocking {
        client()
        val request = cases.createRequest(1, RequestIntention.QUOTATION, 45000, "", cl.duoc.louaraya.domain.usecase.QuotationDetails("Demo", 20.0, 30.0, "Algodón", "Crudo", "2099-12-01"))
        rejected { cases.agreeTotal(request.id, 62000) }
        admin()
        rejected { cases.agreeTotal(request.id, -1) }
        rejected { cases.agreeTotal(request.id, 10_000_000_000) }
        val product = cases.snapshot().products.first { it.id == 1L }
        cases.saveProduct(product.copy(price = 99000, active = false))
        cases.agreeTotal(request.id, 62000)
        val firstDate = repository.request(request.id)!!.agreedAt
        cases.agreeTotal(request.id, 62000)
        assertEquals(firstDate, repository.request(request.id)!!.agreedAt)
        cases.agreeTotal(request.id, 65000)
        client()
        val saved = cases.snapshot().requests.single()
        assertEquals(45000L, saved.total)
        assertEquals(65000L, saved.agreedTotal)
        assertEquals(listOf(62000L, 65000L), cases.snapshot().explorationEvents.filter { it.kind == ExplorationKind.AMOUNT_AGREED }.map { it.amount })
        assertEquals(2, cases.snapshot().notices.count { it.eventKey.startsWith("agreement/") })

        cases.recordProductView(1)
        rejected { cases.createRequest(1, RequestIntention.PURCHASE, 99000, "", null) }
    }
}
