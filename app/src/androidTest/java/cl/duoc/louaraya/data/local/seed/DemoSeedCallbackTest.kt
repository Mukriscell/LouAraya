package cl.duoc.louaraya.data.local.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.domain.model.CampaignCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemoSeedCallbackTest {

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
                listOf("Tapiz Demo Aurora", "Colgante Demo Brisa", "Camino de Mesa Demo", "Portamacetero Demo"),
                products.map { it.name }
            )
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
