package cl.duoc.louaraya.data.remote

import android.app.Application
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.repository.RoomLocalRepository
import cl.duoc.louaraya.domain.model.Campaign
import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.LocalNotice
import cl.duoc.louaraya.domain.model.LocalSnapshot
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.model.StockMovement
import cl.duoc.louaraya.domain.model.StockMovementType
import cl.duoc.louaraya.domain.model.UserProfile
import cl.duoc.louaraya.domain.model.UserRole
import cl.duoc.louaraya.domain.usecase.FormValidationException
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import com.google.gson.GsonBuilder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ConnectedLocalRepositoryTest {
    private lateinit var db: LouArayaDatabase
    private lateinit var local: RoomLocalRepository
    private val identity = mockk<IdentityApi>()
    private val workshop = mockk<WorkshopApi>()
    private val storage = MemoryConnectedStorage()
    private val gson = GsonBuilder().serializeNulls().create()
    private val client = UserProfile(1, "Cliente", "cliente.demo")
    private val admin = UserProfile(2, "Taller", "admin.demo", UserRole.ADMIN)
    private val clientAuth = "Bearer client-token"
    private val adminAuth = "Bearer admin-token"
    private val product = Product(9, "Tapiz", 40.0, 60.0, "Lino", 28_000, CampaignCategory.INTERIOR_DECORATION, stock = 5)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LouArayaDatabase::class.java).build()
        local = RoomLocalRepository(db)
        coEvery { identity.login(Credentials(client.alias, PASSWORD)) } returns AccountResponse("client-token", client)
        coEvery { identity.login(Credentials(admin.alias, PASSWORD)) } returns AccountResponse("admin-token", admin)
        coEvery { identity.logout(any()) } returns Unit
        coEvery { workshop.snapshot(clientAuth) } returns snapshot(client)
        coEvery { workshop.snapshot(adminAuth) } returns snapshot(admin)
    }

    @After fun close() = db.close()

    @Test fun cachedSnapshotSurvivesProcessRecreationButOfflineWriteCannotChangeProducts() = runBlocking {
        val connected = cases()
        connected.initialize()
        connected.signIn(admin.alias, PASSWORD)
        assertEquals(28_000L, local.products().single().price)
        assertEquals(listOf(product), connected.snapshot().products)

        coEvery { workshop.snapshot(adminAuth) } throws IOException()
        coEvery { workshop.editProduct(adminAuth, product.id, any()) } throws IOException()
        val reopened = cases()
        reopened.initialize()
        assertEquals(admin.id, reopened.snapshot().profile!!.id)
        assertEquals(28_000L, reopened.snapshot().products.single().price)
        assertTrue(reopened.connectionWarning!!.contains("Sin conexión"))
        try {
            reopened.saveProduct(product.copy(price = 31_000))
            fail("A failed server update cannot become a local save")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Sin conexión"))
        }
        assertEquals(28_000L, reopened.snapshot().products.single().price)
        assertEquals(28_000L, local.products().single().price)

        coEvery { workshop.snapshot(adminAuth) } returns snapshot(admin, listOf(product.copy(price = 37_000)))
        reopened.refreshConnection()
        assertNull(reopened.connectionWarning)
        assertEquals(37_000L, reopened.snapshot().products.single().price)
    }

    @Test fun expiredSessionClearsPreviousAccountAndClientFiltersOtherUsersRecords() = runBlocking {
        val own = order(27, client.id)
        val other = order(28, 3)
        val mine = notice(1, client.id)
        coEvery { workshop.snapshot(clientAuth) } returns snapshot(client, requests = listOf(own, other), notices = listOf(mine, notice(2, 3)))
        val connected = cases()
        connected.initialize()
        connected.signIn(client.alias, PASSWORD)
        val initial = connected.snapshot()
        assertEquals(listOf(own), initial.requests)
        assertEquals(listOf(mine), initial.notices)

        coEvery { workshop.snapshot(clientAuth) } throws httpError(401, """{"message":"Sesión vencida"}""")
        connected.refreshConnection()
        assertNull(connected.profile())
        assertNull(storage.session())
        assertNull(storage.snapshot(client.id))
        assertEquals(LocalSnapshot(), connected.snapshot())

        connected.signIn(admin.alias, PASSWORD)
        assertEquals(admin.id, connected.snapshot().profile!!.id)
        assertTrue(connected.snapshot().requests.isEmpty())
    }

    @Test fun idempotencyKeySurvivesUncertainNetworkFailureAndRecreation() = runBlocking {
        val bodies = mutableListOf<NewRequest>()
        val saved = order(51, client.id)
        coEvery { workshop.createRequest(clientAuth, capture(bodies)) } throws IOException() andThen saved andThen saved.copy(id = 52)
        val connected = cases()
        connected.initialize()
        connected.signIn(client.alias, PASSWORD)
        connected.snapshot()
        try {
            connected.createRequest(product.id, RequestIntention.PURCHASE, product.price, "Demo", null, quantity = 2)
            fail("Expected the uncertain response to surface as a connection error")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("Sin conexión"))
        }
        assertTrue(connected.snapshot().requests.isEmpty())
        val reopened = cases()
        reopened.initialize()
        reopened.snapshot()
        assertEquals(51L, reopened.createRequest(product.id, RequestIntention.PURCHASE, product.price, "Demo", null, quantity = 2).id)
        assertEquals(52L, reopened.createRequest(product.id, RequestIntention.PURCHASE, product.price, "Demo", null, quantity = 2).id)
        assertEquals(3, bodies.size)
        assertEquals(bodies[0].idempotencyKey, bodies[1].idempotencyKey)
        assertNotEquals(bodies[1].idempotencyKey, bodies[2].idempotencyKey)
        assertEquals(2, bodies[0].quantity)
        assertTrue(local.requests(null).isEmpty())
    }

    @Test fun stockAndStatusUseServerTransactionsAndExposeFieldErrors() = runBlocking {
        val order = order(22, client.id)
        coEvery { workshop.snapshot(adminAuth) } returns snapshot(admin, requests = listOf(order))
        val stockResponses = mutableListOf<StockUpdate>()
        val movement = StockMovement(8, product.id, 3, 8, StockMovementType.ENTRY, 100, admin.id, "agregar")
        coEvery { workshop.moveStock(adminAuth, product.id, capture(stockResponses)) } returns movement
        coEvery { workshop.changeStatus(adminAuth, order.id, StatusUpdate(RequestStatus.DELIVERED)) } throws
            httpError(409, """{"message":"Stock insuficiente","fieldErrors":{"quantity":"Saldo insuficiente"}}""")
        val connected = cases()
        connected.initialize()
        connected.signIn(admin.alias, PASSWORD)
        connected.snapshot()
        connected.changeStock(product.id, 3, StockMovementType.ENTRY, "agregar")
        coEvery { workshop.snapshot(adminAuth) } throws IOException()
        val cached = connected.snapshot()
        assertEquals(8, cached.products.single().stock)
        assertEquals(listOf(movement), cached.stockMovements)
        try {
            connected.updateStatus(order.id, RequestStatus.DELIVERED)
            fail("Expected the server to reject the delivery")
        } catch (expected: FormValidationException) {
            assertEquals(mapOf("quantity" to "Saldo insuficiente"), expected.fields)
        }
        assertEquals(RequestStatus.PENDING, connected.snapshot().requests.single().status)
        assertEquals(8, connected.snapshot().products.single().stock)
        assertEquals(3, stockResponses.single().quantity)
        assertTrue(stockResponses.single().idempotencyKey.isNotBlank())
        assertTrue(local.stockMovements().isEmpty())
    }

    @Test fun deactivateUsesDeleteEndpointAndPreservesStoredRequest() = runBlocking {
        val order = order(44, client.id)
        coEvery { workshop.snapshot(adminAuth) } returns snapshot(admin, requests = listOf(order))
        coEvery { workshop.deactivateProduct(adminAuth, product.id) } returns Unit
        val connected = cases()
        connected.initialize()
        connected.signIn(admin.alias, PASSWORD)
        connected.snapshot()
        connected.deactivateProduct(product.id)
        coEvery { workshop.snapshot(adminAuth) } throws IOException()
        assertFalse(connected.snapshot().products.single().active)
        assertEquals(order, connected.snapshot().requests.single())
        coVerify(exactly = 1) { workshop.deactivateProduct(adminAuth, product.id) }
    }

    @Test fun avatarStaysOnDeviceWithServerProfilePreferences() = runBlocking {
        val newer = client.copy(preferredCategory = CampaignCategory.WEDDINGS.name)
        val muted = newer.copy(notificationsEnabled = false)
        coEvery { identity.updateProfile(clientAuth, ProfileChanges(client.name, client.alias, 0, "", true, CampaignCategory.WEDDINGS.name)) } returns newer
        coEvery { identity.updateProfile(clientAuth, ProfileChanges(client.name, client.alias, 0, "", false, CampaignCategory.WEDDINGS.name)) } returns muted
        val preferenceChanges = mutableListOf<UserProfile>()
        val connected = LocalUseCases(ConnectedLocalRepository(local, identity, workshop, storage, gson,
            campaignPreferencesChanged = { preferenceChanges += it }))
        connected.initialize()
        connected.signIn(client.alias, PASSWORD)
        connected.snapshot()
        val photo = "avatar-images/12345678-0000-0000-0000-123456789abc.jpg"
        connected.setAvatarPhoto(client.id, photo)
        assertTrue(preferenceChanges.isEmpty())
        connected.editProfile(client.name, 0, "", true, CampaignCategory.WEDDINGS.name)
        assertEquals(photo, connected.profile()!!.avatarImageReference)
        assertEquals(CampaignCategory.WEDDINGS.name, connected.profile()!!.preferredCategory)
        connected.editProfile(client.name, 0, "", false, CampaignCategory.WEDDINGS.name)
        assertEquals(listOf(newer, muted), preferenceChanges)
        coVerify(exactly = 2) { identity.updateProfile(any(), any()) }
        connected.signOut()
        connected.signIn(admin.alias, PASSWORD)
        assertNull(connected.snapshot().profile!!.avatarImageReference)
    }

    @Test fun catalogEditorsUseServerIdsWhileDeviceKeepsItsOwnImageReference() = runBlocking {
        val createdProduct = product.copy(id = 16, name = "Local image sample", stock = 0)
        coEvery { workshop.createProduct(adminAuth, any()) } returns createdProduct
        coEvery { workshop.editProduct(adminAuth, 16, any()) } returns createdProduct.copy(price = 5000)
        val collection = Collection(19, "Colección", productIds = listOf(16))
        coEvery { workshop.createCollection(adminAuth, collection.copy(id = 0)) } returns collection
        coEvery { workshop.editCollection(adminAuth, 19, collection.copy(name = "Nueva colección")) } returns collection.copy(name = "Nueva colección")
        val campaign = Campaign(21, "Campaña", 19, "2099-01-01", "2099-01-30")
        coEvery { workshop.createCampaign(adminAuth, campaign.copy(id = 0)) } returns campaign
        coEvery { workshop.editCampaign(adminAuth, 21, campaign.copy(published = true)) } returns campaign.copy(published = true)
        val repository = ConnectedLocalRepository(local, identity, workshop, storage, gson)
        val connected = LocalUseCases(repository)
        connected.initialize()
        connected.signIn(admin.alias, PASSWORD)
        connected.snapshot()
        coEvery { workshop.snapshot(adminAuth) } throws IOException()

        assertEquals(16L, connected.saveProduct(createdProduct.copy(id = 0)))
        val image = "product-images/12345678-0000-0000-0000-123456789abc.jpg"
        assertTrue(repository.productImages().updateImageReference(16, image))
        assertFalse(repository.productImages().updateImageReference(100, image))
        assertEquals(16L, connected.saveProduct(createdProduct.copy(price = 5000)))
        assertEquals(5000L, repository.productImages().getProductById(16)!!.price)
        assertEquals(image, repository.productImages().getProductById(16)!!.imageReference)
        assertEquals(2, repository.productImages().getProducts().size)
        assertEquals(19L, connected.saveCollection(collection.copy(id = 0)))
        assertEquals(19L, connected.saveCollection(collection.copy(name = "Nueva colección")))
        assertEquals(21L, connected.saveCampaign(campaign.copy(id = 0)))
        assertEquals(21L, connected.saveCampaign(campaign.copy(published = true)))
        val snapshot = connected.snapshot()
        assertEquals("Nueva colección", snapshot.collections.single().name)
        assertTrue(snapshot.campaigns.single().published)
        assertTrue(local.collections().isEmpty())
        assertTrue(local.campaigns().isEmpty())
    }

    @Test fun accountOwnsFavoritesAndNoticeOperationsAndCancellationUsesServer() = runBlocking {
        val order = order(27, client.id)
        val collection = Collection(13, "Taller", productIds = listOf(product.id))
        val notice = notice(15, client.id)
        coEvery { workshop.snapshot(clientAuth) } returns snapshot(client, requests = listOf(order), notices = listOf(notice))
            .copy(collections = listOf(collection))
        coEvery { workshop.favoriteProduct(clientAuth, product.id, any()) } returns Unit
        coEvery { workshop.favoriteCollection(clientAuth, collection.id, any()) } returns Unit
        coEvery { workshop.cancelRequest(clientAuth, order.id) } returns order.copy(status = RequestStatus.CANCELLED)
        coEvery { workshop.markNoticeRead(clientAuth, notice.id) } returns Unit
        coEvery { workshop.markNoticeDelivered(clientAuth, notice.id) } returns Unit
        coEvery { workshop.dismissNotices(clientAuth, Dismissal(listOf(notice.id))) } returns Unit
        val connected = cases()
        connected.initialize()
        connected.signIn(client.alias, PASSWORD)
        connected.snapshot()
        coEvery { workshop.snapshot(clientAuth) } throws IOException()

        connected.setFavorite(product.id, true)
        connected.setFavoriteCollection(collection.id, true)
        assertEquals(setOf(product.id), connected.snapshot().favoriteIds)
        assertEquals(setOf(collection.id), connected.snapshot().favoriteCollectionIds)
        connected.setFavorite(product.id, false)
        connected.setFavoriteCollection(collection.id, false)
        assertTrue(connected.snapshot().favoriteIds.isEmpty())
        assertTrue(connected.snapshot().favoriteCollectionIds.isEmpty())
        assertEquals("request/27", connected.openNotice(notice.id))
        connected.markNoticesDelivered(listOf(notice.id))
        assertTrue(connected.snapshot().notices.single().read)
        assertTrue(connected.snapshot().notices.single().delivered)
        connected.clearNotices(listOf(notice.id), client.id)
        connected.cancelRequest(order.id, client.id)
        assertTrue(connected.snapshot().notices.isEmpty())
        assertEquals(RequestStatus.CANCELLED, connected.snapshot().requests.single().status)
        assertTrue(local.favoriteIds(client.id).isEmpty())
    }

    @Test fun pushRegistrationValidatesCurrentAccountAndRevokesBeforeLogout() = runBlocking {
        val instance = "637cdc12-8c5d-47df-93b0-afcaa48b24f2"
        val callbackUsers = mutableListOf<Long>()
        val repository = ConnectedLocalRepository(local, identity, workshop, storage, gson,
            forgetPushDevice = { userId ->
                callbackUsers += userId
                instance
            })
        val connected = LocalUseCases(repository)
        val device = PushDevice("https://push.example/upDemo?up=1", "test-public", "test-auth")
        coEvery { workshop.registerPushDevice(clientAuth, instance, device) } returns Unit
        coEvery { workshop.unregisterPushDevice(clientAuth, instance) } returns Unit
        connected.initialize()
        connected.signIn(client.alias, PASSWORD)
        assertFalse(repository.registerPushDevice(admin.id, instance, device) { true })
        assertFalse(repository.registerPushDevice(client.id, instance, device) { false })
        assertTrue(repository.registerPushDevice(client.id, instance, device) { true })
        connected.signOut()
        assertEquals(listOf(client.id), callbackUsers)
        assertFalse(repository.registerPushDevice(client.id, instance, device) { true })
        coVerify(exactly = 1) { workshop.registerPushDevice(any(), any(), any()) }
        coVerifyOrder {
            workshop.unregisterPushDevice(clientAuth, instance)
            identity.logout(clientAuth)
        }
    }

    @Test fun campaignPushNeedsFreshUndeliveredOwnedNoticeAndValidSession() = runBlocking {
        val original = notice(99L, client.id).copy(destination = "campaign/17")
        val repository = ConnectedLocalRepository(local, identity, workshop, storage, gson)
        val connected = LocalUseCases(repository)
        coEvery { workshop.snapshot(clientAuth) } returns snapshot(client, notices = listOf(original))
        coEvery { workshop.markNoticeDelivered(clientAuth, original.id) } returns Unit
        connected.initialize()
        connected.signIn(client.alias, PASSWORD)
        connected.snapshot()
        assertEquals(original, repository.pendingCampaignNotice(client.id, original.id))
        repository.acknowledgeCampaignNotice(client.id, original.id)
        repository.acknowledgeCampaignNotice(admin.id, original.id)
        coVerify(exactly = 1) { workshop.markNoticeDelivered(any(), original.id) }

        for (ineligible in listOf(
            original.copy(userId = admin.id), original.copy(read = true), original.copy(delivered = true),
            original.copy(destination = "request/17"), original.copy(destination = "campaign/0"),
            original.copy(id = 100)
        )) {
            coEvery { workshop.snapshot(clientAuth) } returns snapshot(client, notices = listOf(ineligible))
            assertNull(repository.pendingCampaignNotice(client.id, original.id))
        }
        coEvery { workshop.snapshot(clientAuth) } returns snapshot(client.copy(notificationsEnabled = false), notices = listOf(original))
        assertNull(repository.pendingCampaignNotice(client.id, original.id))
        coEvery { workshop.snapshot(clientAuth) } returns snapshot(admin, notices = listOf(original))
        assertNull(repository.pendingCampaignNotice(client.id, original.id))
        connected.signOut()
        assertNull(repository.pendingCampaignNotice(client.id, original.id))
    }

    private fun cases() = LocalUseCases(ConnectedLocalRepository(local, identity, workshop, storage, gson))

    private fun snapshot(
        profile: UserProfile,
        products: List<Product> = listOf(product),
        requests: List<CommercialRequest> = emptyList(),
        notices: List<LocalNotice> = emptyList()
    ) = WorkshopSnapshot(profile, products, requests, null, null, null, notices, null, null, null, null, null)

    private fun order(id: Long, owner: Long) = CommercialRequest(
        id, 100, "CLIENTE-$owner", product.price * 2, RequestStatus.PENDING, product.id,
        RequestIntention.PURCHASE, ownerId = owner, quantity = 2
    )

    private fun notice(id: Long, owner: Long) = LocalNotice(id, owner, "Aviso", "Texto", "request/27", 100, eventKey = "notice-$id")

    private fun httpError(status: Int, body: String) = HttpException(
        Response.error<Unit>(status, ResponseBody.create(MediaType.parse("application/json"), body))
    )

    private class MemoryConnectedStorage : ConnectedStorage {
        private var current: ConnectedSession? = null
        private val snapshots = mutableMapOf<Long, LocalSnapshot>()
        private val keys = mutableMapOf<String, String>()
        private var keyCount = 0
        override fun session() = current
        override fun saveSession(session: ConnectedSession?) { current = session }
        override fun snapshot(userId: Long) = snapshots[userId]
        override fun saveSnapshot(userId: Long, snapshot: LocalSnapshot) { snapshots[userId] = snapshot }
        override fun clearSnapshot(userId: Long) { snapshots.remove(userId) }
        override fun requestKey(fingerprint: String) = keys.getOrPut(fingerprint) { "request-${++keyCount}" }
        override fun clearRequestKey(fingerprint: String) { keys.remove(fingerprint) }
    }

    private companion object {
        const val PASSWORD = "LouDemo2026!"
    }
}
