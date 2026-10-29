package cl.duoc.louaraya.data.local.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.entity.FavoriteEntity
import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity
import cl.duoc.louaraya.data.repository.RoomLocalRepository
import cl.duoc.louaraya.domain.model.CampaignCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoSeedCallbackTest {

    @Test
    fun updatingOriginalLabelsPreservesCustomizationsCredentialsAndLinkedData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "catalog-label-update-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, LouArayaDatabase::class.java, name)
            .addCallback(DemoSeedCallback()).allowMainThreadQueries().build()
        var database = open()
        try {
            val repository = RoomLocalRepository(database)
            repository.initialize()
            repository.signIn("cliente.demo", "LouDemo2026!")
            val client = requireNotNull(database.localDao().user(1)).copy(name = "Cliente de demostración", avatarImageReference = "avatar-preserved.jpg")
            val administrator = requireNotNull(database.localDao().user(2)).copy(name = "Mi taller")
            database.localDao().updateUser(client)
            database.localDao().updateUser(administrator)
            val products = database.productDao().getAll().associateBy { it.id }
            val original = products.getValue(1).copy(name = "Tapiz Demo Aurora", price = 59000, imageReference = "photo-preserved.jpg")
            val custom = products.getValue(2).copy(name = "Brisa de verano")
            database.productDao().save(original)
            database.productDao().save(custom)
            database.localDao().favorite(FavoriteEntity(1, 1))
            val request = CommercialRequestEntity(productId = 1, date = 1234, intention = "RESERVATION", anonymizedClient = "CLIENTE-1", comment = "Conservar", total = 45000, status = "PENDING", campaignCategory = "INTERIOR_DECORATION")
            val requestId = database.commercialRequestDao().insert(request)
            database.openHelper.writableDatabase.execSQL("UPDATE fair_events SET name = ?, location = ? WHERE id = 1", arrayOf<Any>("Feria Textil de Primavera · Demo", "Parque Bicentenario, Santiago · punto ficticio"))
            database.openHelper.writableDatabase.execSQL("UPDATE fair_events SET name = ?, location = ? WHERE id = 2", arrayOf<Any>("Showroom personalizado", "Mi ubicación"))
            database.close()

            database = open()
            val updated = RoomLocalRepository(database)
            updated.initialize()
            assertEquals(original.copy(name = "Tapiz Aurora"), database.productDao().getById(1))
            assertEquals(custom, database.productDao().getById(2))
            assertEquals(client.copy(name = "Cliente Lou Araya"), database.localDao().user(1))
            assertEquals(administrator, database.localDao().user(2))
            assertEquals(1L, updated.session()?.id)
            assertEquals(setOf(1L), updated.favoriteIds(1))
            assertEquals(request.copy(id = requestId), database.commercialRequestDao().getById(requestId))
            assertEquals("Feria Textil de Primavera", updated.events().single { it.id == 1L }.name)
            assertEquals("Parque Bicentenario, Santiago", updated.events().single { it.id == 1L }.location)
            assertEquals("Showroom personalizado", updated.events().single { it.id == 2L }.name)
            assertEquals("Mi ubicación", updated.events().single { it.id == 2L }.location)
            assertEquals(2L, updated.signIn("admin.demo", "LouDemo2026!").id)
            updated.initialize()
            assertEquals(administrator, database.localDao().user(2))
            assertEquals(custom, database.productDao().getById(2))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun callbackPersistsOneFictitiousProductPerOfficialCategory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "demo-seed-categories-test.db"
        context.deleteDatabase(name)
        val database = Room.databaseBuilder(context, LouArayaDatabase::class.java, name)
            .addCallback(DemoSeedCallback())
            .allowMainThreadQueries()
            .build()
        try {
            val products = database.productDao().getAll()
            assertEquals(4, products.size)
            assertEquals(
                CampaignCategory.entries.map { it.name }.toSet(),
                products.map { it.campaignCategory }.toSet()
            )
            assertEquals(
                listOf("Tapiz Aurora", "Colgante Brisa", "Camino de Mesa", "Portamacetero"),
                products.map { it.name }
            )
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
