package cl.duoc.louaraya.data.repository

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.migration.MIGRATION_4_5
import cl.duoc.louaraya.data.local.migration.MIGRATION_5_6
import cl.duoc.louaraya.data.local.migration.MIGRATION_6_7
import cl.duoc.louaraya.data.local.migration.MIGRATION_7_8
import cl.duoc.louaraya.data.local.migration.MIGRATION_8_9
import cl.duoc.louaraya.data.local.migration.MIGRATION_9_10
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import cl.duoc.louaraya.domain.usecase.QuotationDetails
import cl.duoc.louaraya.domain.usecase.RegisterWithPhotoUseCase
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.repository.LocalRepository
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
class LocalPersistenceTest {
    private lateinit var db: LouArayaDatabase
    private lateinit var repository: RoomLocalRepository
    private lateinit var useCases: LocalUseCases
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LouArayaDatabase::class.java)
            .addCallback(DemoSeedCallback()).build()
        repository = RoomLocalRepository(db)
        useCases = LocalUseCases(repository)
        useCases.initialize()
    }
    @After fun close() { db.close() }
    private suspend fun client(alias: String = "cliente.a") = useCases.register("Cliente ficticio", alias, "ClaveFicticia2026!", 1, "cliente@demo.example")
    private suspend fun admin() = useCases.signIn("admin.demo", "LouDemo2026!")
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("La operación debió rechazarse") } catch (_: IllegalArgumentException) { }
    }

    @Test fun optionalContactAcceptsBlankEmailAndPhoneInRegistrationAndProfile() = runBlocking {
        listOf("", "   ", "cliente@correo.test", "+56 9 0000 0000").forEachIndexed { index, contact ->
            val created = useCases.register("Cuenta ficticia", "contacto.$index", "ClaveFicticia2026!", 0, contact)
            assertEquals(contact.trim(), created.contact)
            useCases.editProfile(created.name, created.avatar, "  $contact  ", true, null)
            assertEquals(contact.trim(), useCases.profile()!!.contact)
        }
    }

    @Test fun oversizedContactReportsOnlyLengthErrorAndDoesNotChangeAccount() = runBlocking {
        val previous = client()
        for (registration in listOf(true, false)) {
            try {
                if (registration) useCases.register("Cuenta ficticia", "contacto.largo", "ClaveFicticia2026!", 0, "x".repeat(101))
                else useCases.editProfile(previous.name, previous.avatar, "x".repeat(101), true, null)
                fail("El contacto largo debió rechazarse")
            } catch (error: cl.duoc.louaraya.domain.usecase.FormValidationException) {
                assertEquals(mapOf("contact" to "El contacto no puede superar los 100 caracteres."), error.fields)
            }
            assertEquals(previous, useCases.profile())
        }
        useCases.editProfile(previous.name, previous.avatar, "x".repeat(100), true, null)
        assertEquals(100, useCases.profile()!!.contact.length)
    }

    private class RegistrationImages : ProductImageStorage {
        var saveCalls = 0
        val reference = "avatar-images/00000000-0000-0000-0000-000000000001.jpg"
        val deleted = mutableListOf<String>()
        val capturesDeleted = mutableListOf<String>()
        override suspend fun saveFromSource(sourceUri: String): String { saveCalls++; return reference }
        override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? = null
        override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? = null
        override suspend fun delete(reference: String) { deleted += reference }
        override suspend fun deleteCameraCapture(uri: String) { capturesDeleted += uri }
    }

    @Test fun weakRegistrationPasswordsPreserveTheAccountAndDoNotImportPhotos() = runBlocking {
        val previous = client()
        val before = repository.profiles()
        val images = RegistrationImages()
        for ((index, password) in listOf("Abcde1!", "A1!" + "a".repeat(18), "abcdef1!", "Abcdefg!", "Abcdef12").withIndex()) {
            for (withPhoto in listOf(false, true)) {
                try {
                    if (withPhoto) RegisterWithPhotoUseCase(useCases, images)("Cuenta ficticia", "password.$index", password, 0, "", "content://capture/1")
                    else useCases.register("Cuenta ficticia", "password.$index", password, 0, "")
                    fail("La contraseña no cumple los requisitos")
                } catch (failure: cl.duoc.louaraya.domain.usecase.FormValidationException) {
                    assertEquals(setOf("password"), failure.fields.keys)
                }
                assertEquals(previous, useCases.profile())
                assertEquals(before, repository.profiles())
            }
        }
        assertEquals(0, images.saveCalls)
    }

    @Test fun registrationPasswordBoundariesCanAuthenticateAfterSigningOut() = runBlocking {
        for ((index, password) in listOf("Abcdef1!", "A1!" + "a".repeat(17)).withIndex()) {
            val profile = useCases.register("Cuenta ficticia", "limite.$index", password, 0, "")
            useCases.signOut()
            assertEquals(profile, useCases.signIn(profile.alias, password))
        }
    }

    @Test fun registrationStoresPhotoWithAccountAndRestoresItOnSignIn() = runBlocking {
        val images = RegistrationImages()
        val created = RegisterWithPhotoUseCase(useCases, images)("Cuenta ficticia", "con.foto", "ClaveFicticia2026!", 4, "", "content://capture/1")
        assertEquals(images.reference, created.avatarImageReference)
        assertEquals(images.reference, db.localDao().user(created.id)!!.avatarImageReference)
        assertEquals(created, useCases.profile())
        assertEquals(listOf("content://capture/1"), images.capturesDeleted)
        assertTrue(images.deleted.isEmpty())
        useCases.signOut()
        assertEquals(created, useCases.signIn("con.foto", "ClaveFicticia2026!"))
    }

    @Test fun mismatchedConfirmationKeepsTheSessionAndDoesNotImportThePhoto() = runBlocking {
        val previous = client()
        val count = repository.profiles().size
        var imported = false
        val images = RegistrationImages()
        val storage = object : ProductImageStorage by images {
            override suspend fun saveFromSource(sourceUri: String): String {
                imported = true
                return images.saveFromSource(sourceUri)
            }
        }
        try {
            RegisterWithPhotoUseCase(useCases, storage)("Cuenta ficticia", "confirmacion", "ClaveFicticia2026!", 0, "", "content://capture/1", "OtraClave2026!")
            fail("La confirmación diferente debió rechazarse")
        } catch (error: cl.duoc.louaraya.domain.usecase.FormValidationException) {
            assertEquals(setOf("confirm"), error.fields.keys)
        }
        assertFalse(imported)
        assertEquals(count, repository.profiles().size)
        assertEquals(previous, useCases.profile())
        assertTrue(images.deleted.isEmpty())
        assertTrue(images.capturesDeleted.isEmpty())
    }

    @Test fun rejectedRegistrationDeletesPreparedImageButKeepsCaptureForCorrection() = runBlocking {
        val existing = client()
        val images = RegistrationImages()
        rejected { RegisterWithPhotoUseCase(useCases, images)("Cuenta ficticia", existing.alias, "ClaveFicticia2026!", 0, "", "content://capture/1") }
        assertEquals(existing, useCases.profile())
        assertEquals(listOf(images.reference), images.deleted)
        assertTrue(images.capturesDeleted.isEmpty())
        assertEquals(3, repository.profiles().size)
    }

    @Test fun failedPhotoAssociationRollsBackNewAccountAndSession() = runBlocking {
        val previous = client()
        val images = RegistrationImages()
        val failing = object : LocalRepository by repository {
            override suspend fun updateProfile(profile: UserProfile) { throw IllegalArgumentException("Fallo de escritura de prueba") }
        }
        rejected { RegisterWithPhotoUseCase(LocalUseCases(failing), images)("Cuenta ficticia", "foto.fallida", "ClaveFicticia2026!", 0, "", "content://capture/1") }
        assertEquals(previous, useCases.profile())
        assertTrue(repository.profiles().none { it.alias == "foto.fallida" })
        assertEquals(listOf(images.reference), images.deleted)
        assertTrue(images.capturesDeleted.isEmpty())
    }

    @Test fun invalidProductReportsEachFieldAndDoesNotWrite() = runBlocking {
        admin()
        val before = useCases.snapshot().products
        try {
            useCases.saveProduct(before.first().copy(name = "", widthCm = 0.0, lengthCm = -1.0, material = "", price = -1))
            fail("Invalid product must not be stored")
        } catch (error: cl.duoc.louaraya.domain.usecase.FormValidationException) {
            assertEquals(setOf("name", "width", "length", "material", "price"), error.fields.keys)
        }
        assertEquals(before, useCases.snapshot().products)
    }

    @Test fun invalidCampaignDateAndDuplicateAliasReportTheirFields() = runBlocking {
        client()
        try { client(); fail("Alias must be unique") }
        catch (error: cl.duoc.louaraya.domain.usecase.FormValidationException) { assertEquals(setOf("alias"), error.fields.keys) }
        admin()
        val collection = useCases.saveCollection(cl.duoc.louaraya.domain.model.Collection(name = "Colección ficticia", productIds = listOf(1)))
        try {
            useCases.saveCampaign(cl.duoc.louaraya.domain.model.Campaign(name = "Campaña ficticia", collectionId = collection, startsOn = "2026-12-10", endsOn = "2026-12-01"))
            fail("End date must not precede start date")
        } catch (error: cl.duoc.louaraya.domain.usecase.FormValidationException) { assertEquals(setOf("end"), error.fields.keys) }
        assertTrue(useCases.snapshot().campaigns.isEmpty())
    }

    @Test fun registrationNormalizesAliasesAndNeverStoresPlainPasswords() = runBlocking {
        val profile = client(" CLIENTE.A ")
        assertEquals("cliente.a", profile.alias)
        assertEquals(UserRole.CLIENT, profile.role)
        val stored = db.localDao().user(profile.id)!!
        assertNotEquals("ClaveFicticia2026!", stored.passwordHash)
        assertEquals(64, stored.passwordHash.length)
        rejected { client("CLIENTE.A") }
        useCases.signOut()
        rejected { useCases.signIn("cliente.a", "incorrecta") }
        assertNull(useCases.profile())
        useCases.signIn("CLIENTE.A", "ClaveFicticia2026!")
        assertEquals(profile, useCases.profile())
    }

    @Test fun twoClientsKeepRequestsFavoritesAndNoticesSeparate() = runBlocking {
        val a = client()
        useCases.setFavorite(1, true)
        val request = useCases.createRequest(1, RequestIntention.RESERVATION, 45000, "Reserva ficticia", null)
        val b = client("cliente.b")
        assertNotEquals(a.id, b.id)
        assertTrue(useCases.snapshot().favoriteIds.isEmpty())
        assertTrue(useCases.snapshot().requests.isEmpty())
        rejected { useCases.updateStatus(request.id, RequestStatus.CONFIRMED) }
        admin()
        assertEquals(request.id, useCases.snapshot().requests.single().id)
        val adminNotice = useCases.snapshot().notices.single()
        useCases.updateStatus(request.id, RequestStatus.CONFIRMED)
        useCases.signIn("cliente.b", "ClaveFicticia2026!")
        assertTrue(useCases.snapshot().notices.isEmpty())
        rejected { useCases.openNotice(adminNotice.id) }
        useCases.signIn("cliente.a", "ClaveFicticia2026!")
        val snapshot = useCases.snapshot()
        assertEquals(setOf(1L), snapshot.favoriteIds)
        assertEquals(RequestStatus.CONFIRMED, snapshot.requests.single().status)
        assertEquals("request/${request.id}", useCases.openNotice(snapshot.notices.single().id))
        assertTrue(useCases.snapshot().notices.single().read)
    }

    @Test fun clientCannotMutateCatalogOrPublishCampaignsEvenWithKnownIds() = runBlocking {
        client()
        val product = useCases.snapshot().products.first()
        rejected { useCases.saveProduct(product.copy(name = "Nombre alterado")) }
        rejected { useCases.saveCollection(Collection(name = "Colección ficticia")) }
        rejected { useCases.saveCampaign(Campaign(name = "Campaña ficticia", collectionId = 1, startsOn = "2026-10-16", endsOn = "2026-10-30")) }
        rejected { useCases.requireAdministrator() }
        assertEquals(product, useCases.snapshot().products.first())
        useCases.signOut()
        rejected { useCases.setFavorite(product.id, true) }
        assertNull(useCases.snapshot().profile)
    }

    @Test fun deactivationKeepsHistoryAndRejectsNewRequests() = runBlocking {
        client()
        val original = useCases.snapshot().products.first()
        val request = useCases.createRequest(original.id, RequestIntention.PURCHASE, original.price, "", null)
        admin()
        useCases.saveProduct(original.copy(active = false))
        useCases.signIn("cliente.a", "ClaveFicticia2026!")
        assertEquals(request.id, useCases.snapshot().requests.single().id)
        rejected { useCases.createRequest(original.id, RequestIntention.PURCHASE, original.price, "", null) }
        rejected { useCases.setFavorite(original.id, true) }
    }

    @Test fun quotationValidatesDatesDimensionsFieldsAndPrice() = runBlocking {
        client()
        val product = useCases.snapshot().products.first()
        val valid = QuotationDetails("Matrimonio ficticio", 120.0, 200.0, "Algodón", "Crudo y terracota", "2099-12-01")
        rejected { useCases.createRequest(product.id, RequestIntention.QUOTATION, product.price, "", valid.copy(requiredOn = "2020-01-01")) }
        rejected { useCases.createRequest(product.id, RequestIntention.QUOTATION, product.price, "", valid.copy(requiredOn = "2099-02-31")) }
        rejected { useCases.createRequest(product.id, RequestIntention.QUOTATION, product.price, "", valid.copy(widthCm = Double.NaN)) }
        rejected { useCases.createRequest(product.id, RequestIntention.QUOTATION, product.price, "", valid.copy(colors = "")) }
        rejected { useCases.createRequest(product.id, RequestIntention.QUOTATION, product.price + 1, "", valid) }
        rejected { useCases.createRequest(product.id, RequestIntention.QUOTATION, product.price, "a".repeat(501), valid) }
        val request = useCases.createRequest(product.id, RequestIntention.QUOTATION, product.price, "Ficticio", valid)
        assertTrue(request.customization!!.contains("2099-12-01"))
        assertEquals(RequestStatus.PENDING, request.status)
        assertEquals(useCases.profile()!!.id, request.ownerId)
    }

    @Test fun campaignPublicationIsTransactionalAndDoesNotDuplicateNoticesOnSave() = runBlocking {
        val a = client()
        val b = client("cliente.b")
        useCases.editProfile(b.name, b.avatar, b.contact, false, null)
        admin()
        val collectionId = useCases.saveCollection(Collection(name = "Colección ficticia", productIds = listOf(1, 2)))
        val campaign = Campaign(name = "Temporada ficticia", collectionId = collectionId, startsOn = "2026-10-01", endsOn = "2099-12-01", published = true)
        val id = useCases.saveCampaign(campaign)
        useCases.saveCampaign(campaign.copy(id = id, name = "Temporada editada"))
        useCases.signIn("cliente.a", "ClaveFicticia2026!")
        assertEquals(1, useCases.snapshot().notices.size)
        assertEquals("campaign/$id", useCases.openNotice(useCases.snapshot().notices.single().id))
        assertEquals(a.id, useCases.snapshot().notices.single().userId)
        useCases.signIn("cliente.b", "ClaveFicticia2026!")
        assertTrue(useCases.snapshot().notices.isEmpty())
        admin()
        rejected { useCases.saveCampaign(campaign.copy(startsOn = "2027-01-01", endsOn = "2026-01-01")) }
    }

    @Test fun qrValidationAndDeduplicationWorkWithoutInternet() = runBlocking {
        client()
        assertEquals("product/1", useCases.resolveQr("louaraya://product/1", "one-scan"))
        useCases.resolveQr("louaraya://product/1", "one-scan")
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM qr_scans").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        rejected { useCases.resolveQr("https://external.example/", "two") }
        rejected { useCases.resolveQr("louaraya://product/999999", "three") }
        rejected { useCases.resolveQr("louaraya://product/99999999999999999999999999", "four") }
        admin()
        val collectionId = useCases.saveCollection(Collection(name = "Colección QR", productIds = listOf(1)))
        assertEquals("collection/$collectionId", useCases.resolveQr("louaraya://collection/$collectionId", "collection-scan"))
    }

    @Test fun sessionProfileAndDeliveryFlagsSurviveRepositoryRecreation() = runBlocking {
        val user = client()
        useCases.editProfile("Nombre ficticio nuevo", 3, "nuevo@demo.example", false, CampaignCategory.TRAVEL.name)
        val replacement = LocalUseCases(RoomLocalRepository(db))
        assertEquals(user.id, replacement.profile()!!.id)
        assertEquals(3, replacement.profile()!!.avatar)
        assertEquals(CampaignCategory.TRAVEL.name, replacement.profile()!!.preferredCategory)
        assertFalse(replacement.profile()!!.notificationsEnabled)
        admin()
        val requestOwner = user.id
        repository.addNotice(LocalNotice(userId = requestOwner, title = "Prueba", body = "Aviso ficticio", destination = "catalog", eventKey = "delivery-test"))
        useCases.signIn("cliente.a", "ClaveFicticia2026!")
        val notice = useCases.snapshot().notices.single()
        useCases.markNoticesDelivered(listOf(notice.id))
        assertTrue(replacement.snapshot().notices.single().delivered)
        replacement.signOut()
        assertNull(useCases.profile())
    }

    @Test fun editingAliasPreservesOwnershipAndRejectsDuplicates() = runBlocking {
        val user = client()
        rejected { useCases.editProfile(user.name, 0, "", true, null, "admin.demo") }
        useCases.editProfile(user.name, 2, "", true, null, " CLIENTE.NUEVO ")
        useCases.setFavorite(1, true)
        useCases.signOut()
        rejected { useCases.signIn("cliente.a", "ClaveFicticia2026!") }
        useCases.signIn("cliente.nuevo", "ClaveFicticia2026!")
        assertEquals(user.id, useCases.profile()!!.id)
        assertEquals(setOf(1L), useCases.snapshot().favoriteIds)
    }

    @Test fun migrationFromV4PreservesImagesRequestsAndCanReopenWithProfiles() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "legacy-v4-test.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE products (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, width_cm REAL NOT NULL, length_cm REAL NOT NULL, material TEXT NOT NULL, price INTEGER NOT NULL, campaign_category TEXT NOT NULL DEFAULT 'INTERIOR_DECORATION', image_reference TEXT)")
            old.execSQL("CREATE TABLE commercial_requests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, product_id INTEGER NOT NULL, date INTEGER NOT NULL, intention TEXT NOT NULL, anonymized_client TEXT NOT NULL, comment TEXT, total INTEGER NOT NULL, status TEXT NOT NULL, campaign_category TEXT)")
            old.execSQL("CREATE INDEX index_commercial_requests_product_id ON commercial_requests(product_id)")
            old.execSQL("INSERT INTO products VALUES (7, 'Pieza previa', 40, 60, 'Yute', 12000, 'TRAVEL', 'product-images/legacy.jpg')")
            old.execSQL("INSERT INTO commercial_requests VALUES (42, 7, 1234, 'RESERVATION', 'CLIENTE-DEMO-001', 'Comentario previo', 12000, 'PENDING', 'TRAVEL')")
            old.version = 4
        }
        try {
            Room.databaseBuilder(context, LouArayaDatabase::class.java, name).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build().let { migrated ->
                try {
                    val cases = LocalUseCases(RoomLocalRepository(migrated))
                    cases.initialize(); cases.signIn("cliente.demo", "LouDemo2026!")
                    val snapshot = cases.snapshot()
                    assertEquals("product-images/legacy.jpg", snapshot.products.single().imageReference)
                    assertEquals(42L, snapshot.requests.single().id)
                    assertEquals(1L, snapshot.requests.single().ownerId)
                    assertEquals("Comentario previo", snapshot.requests.single().comment)
                    assertEquals("CLIENTE-DEMO-001", snapshot.requests.single().anonymizedClient)
                } finally { migrated.close() }
            }
            val reopened = Room.databaseBuilder(context, LouArayaDatabase::class.java, name).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
            try { assertEquals(1L, RoomLocalRepository(reopened).session()!!.id); assertEquals(42L, reopened.commercialRequestDao().getAll().single().id) }
            finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun allEightAvatarsAndPersonalPhotoAreAccountScopedAndSurviveRepositoryRecreation() = runBlocking {
        val user = client()
        for (avatar in AvatarStyle.entries.indices) {
            useCases.editProfile(user.name, avatar, "", true, null)
            assertEquals(avatar, useCases.profile()!!.avatar)
        }
        val photo = "avatar-images/00000000-0000-0000-0000-000000000001.jpg"
        useCases.setAvatarPhoto(user.id, photo)
        useCases.editProfile("Nombre actualizado", 7, "", false, null)
        assertEquals(photo, LocalUseCases(RoomLocalRepository(db)).profile()!!.avatarImageReference)
        val other = client("cliente.b")
        assertNull(other.avatarImageReference)
        rejected { useCases.setAvatarPhoto(user.id, photo) }
        rejected { useCases.setAvatarPhoto(other.id, "product-images/00000000-0000-0000-0000-000000000001.jpg") }
        rejected { useCases.editProfile(other.name, 8, "", true, null) }
        useCases.signIn(user.alias, "ClaveFicticia2026!")
        assertEquals(photo, useCases.profile()!!.avatarImageReference)
        assertEquals(photo, useCases.editProfile(user.name, 6, "", true, null, usePresetAvatar = true))
        assertNull(useCases.profile()!!.avatarImageReference)
        assertEquals(6, useCases.profile()!!.avatar)
    }
}
