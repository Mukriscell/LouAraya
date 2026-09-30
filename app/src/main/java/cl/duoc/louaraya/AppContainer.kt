package cl.duoc.louaraya

import android.content.Context
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.data.repository.ProductRepositoryImpl
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase

/**
 * Composición manual de dependencias de la aplicación.
 *
 * Se crea una única vez en [LouArayaApplication], por lo que la base de datos
 * y el repositorio se comparten durante toda la vida del proceso.
 */
class AppContainer(context: Context) {

    private val database: LouArayaDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            LouArayaDatabase::class.java,
            DATABASE_NAME
        )
            .addCallback(DemoSeedCallback())
            .build()
    }

    private val productRepository: ProductRepository by lazy {
        ProductRepositoryImpl(database.productDao())
    }

    val getProductsUseCase: GetProductsUseCase by lazy {
        GetProductsUseCase(productRepository)
    }

    private companion object {
        const val DATABASE_NAME = "louaraya.db"
    }
}
