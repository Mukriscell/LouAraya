package cl.duoc.louaraya.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.entity.ProductEntity
import cl.duoc.louaraya.domain.model.CampaignCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pruebas instrumentadas de [ProductDao] sobre una base Room real en memoria.
 */
@RunWith(AndroidJUnit4::class)
class ProductDaoTest {

    private lateinit var database: LouArayaDatabase
    private lateinit var productDao: ProductDao

    private val tapiz = ProductEntity(
        id = 1L,
        name = "Tapiz Luna",
        widthCm = 60.0,
        lengthCm = 90.0,
        material = "Algodón",
        price = 45000L,
        campaignCategory = CampaignCategory.INTERIOR_DECORATION.name
    )

    private val colgante = ProductEntity(
        id = 2L,
        name = "Colgante Raíz",
        widthCm = 30.0,
        lengthCm = 50.0,
        material = "Yute",
        price = 25000L,
        campaignCategory = CampaignCategory.FESTIVALS.name
    )

    private val cortina = ProductEntity(
        id = 3L,
        name = "Cortina Bosque",
        widthCm = 120.0,
        lengthCm = 200.0,
        material = "Algodón reciclado",
        price = 98000L,
        campaignCategory = CampaignCategory.WEDDINGS.name
    )

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LouArayaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productDao = database.productDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun insertAllAndGetAll() = runBlocking {
        productDao.insertAll(listOf(tapiz, colgante, cortina))

        val products = productDao.getAll()

        assertEquals(3, products.size)
        assertEquals(listOf(tapiz, colgante, cortina), products)
        assertEquals("Tapiz Luna", products[0].name)
        assertEquals(60.0, products[0].widthCm, 0.0)
        assertEquals(90.0, products[0].lengthCm, 0.0)
        assertEquals("Algodón", products[0].material)
        assertEquals(45000L, products[0].price)
    }

    @Test
    fun getByIdReturnsCorrectProduct() = runBlocking {
        productDao.insertAll(listOf(tapiz, colgante, cortina))

        val product = productDao.getById(2L)

        assertEquals(colgante, product)
    }

    @Test
    fun getByIdReturnsNullWhenMissing() = runBlocking {
        productDao.insertAll(listOf(tapiz))

        val product = productDao.getById(99L)

        assertNull(product)
    }

    @Test
    fun insertWithExistingIdReplacesProduct() = runBlocking {
        productDao.insertAll(listOf(tapiz))
        val updated = tapiz.copy(name = "Tapiz Luna Grande", widthCm = 80.0, price = 60000L)

        productDao.insertAll(listOf(updated))

        val products = productDao.getAll()
        assertEquals(1, products.size)
        assertEquals(updated, products[0])
        assertEquals(updated, productDao.getById(1L))
    }

    @Test
    fun deleteAllRemovesProducts() = runBlocking {
        productDao.insertAll(listOf(tapiz, colgante, cortina))

        productDao.deleteAll()

        assertTrue(productDao.getAll().isEmpty())
    }
}
