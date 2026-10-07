package cl.duoc.louaraya

import android.content.Context
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.data.local.migration.MIGRATION_1_2
import cl.duoc.louaraya.data.local.migration.MIGRATION_2_3
import cl.duoc.louaraya.data.local.migration.MIGRATION_3_4
import cl.duoc.louaraya.data.image.LocalProductImageStorage
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.data.repository.CommercialRequestRepositoryImpl
import cl.duoc.louaraya.domain.repository.CommercialRequestRepository
import cl.duoc.louaraya.domain.usecase.CreateCommercialRequestUseCase
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestsUseCase
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestByIdUseCase
import cl.duoc.louaraya.data.repository.ProductRepositoryImpl
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase
import cl.duoc.louaraya.domain.usecase.UpdateProductImageUseCase

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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }

    private val productRepository: ProductRepository by lazy {
        ProductRepositoryImpl(database.productDao())
    }

    val getProductsUseCase: GetProductsUseCase by lazy {
        GetProductsUseCase(productRepository)
    }

    val getProductByIdUseCase: GetProductByIdUseCase by lazy {
        GetProductByIdUseCase(productRepository)
    }

    val productImageStorage: ProductImageStorage by lazy {
        LocalProductImageStorage(context.applicationContext)
    }

    val updateProductImageUseCase: UpdateProductImageUseCase by lazy {
        UpdateProductImageUseCase(productRepository, productImageStorage)
    }

    private val commercialRequestRepository: CommercialRequestRepository by lazy {
        CommercialRequestRepositoryImpl(database.commercialRequestDao())
    }

    val createCommercialRequestUseCase: CreateCommercialRequestUseCase by lazy {
        CreateCommercialRequestUseCase(commercialRequestRepository, productRepository)
    }

    val getCommercialRequestsUseCase: GetCommercialRequestsUseCase by lazy {
        GetCommercialRequestsUseCase(commercialRequestRepository)
    }

    val getCommercialRequestByIdUseCase: GetCommercialRequestByIdUseCase by lazy {
        GetCommercialRequestByIdUseCase(commercialRequestRepository)
    }

    private companion object {
        const val DATABASE_NAME = "louaraya.db"
    }
}
