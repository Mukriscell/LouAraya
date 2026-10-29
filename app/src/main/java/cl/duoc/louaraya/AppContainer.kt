package cl.duoc.louaraya

import cl.duoc.louaraya.data.local.migration.MIGRATION_4_5
import cl.duoc.louaraya.data.local.migration.MIGRATION_5_6
import cl.duoc.louaraya.data.local.migration.MIGRATION_6_7
import cl.duoc.louaraya.data.local.migration.MIGRATION_7_8
import cl.duoc.louaraya.data.local.migration.MIGRATION_8_9
import cl.duoc.louaraya.data.local.migration.MIGRATION_9_10
import cl.duoc.louaraya.data.repository.RoomLocalRepository
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import android.content.Context
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.data.local.migration.MIGRATION_1_2
import cl.duoc.louaraya.data.local.migration.MIGRATION_2_3
import cl.duoc.louaraya.data.local.migration.MIGRATION_3_4
import cl.duoc.louaraya.data.image.LocalProductImageStorage
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.image.QrImageDecoder
import cl.duoc.louaraya.data.image.MlKitQrImageDecoder
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
import cl.duoc.louaraya.presentation.local.AndroidSystemNotifications
import cl.duoc.louaraya.presentation.local.LocalNotificationCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob







class AppContainer(context: Context) {

    private val database: LouArayaDatabase by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            LouArayaDatabase::class.java,
            DATABASE_NAME
        )
            .addCallback(DemoSeedCallback())
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
            .build()
    }

    val localUseCases: LocalUseCases by lazy { LocalUseCases(RoomLocalRepository(database)) }
    internal val localNotificationCoordinator by lazy {
        LocalNotificationCoordinator(
            snapshots = { localUseCases.observeSnapshots() },
            currentProfile = { localUseCases.profile() },
            acknowledge = { localUseCases.markNoticesDelivered(it) },
            system = AndroidSystemNotifications(context.applicationContext),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        )
    }
    val qrImageDecoder: QrImageDecoder by lazy { MlKitQrImageDecoder(context.applicationContext) }

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

    val avatarImageStorage: ProductImageStorage by lazy {
        LocalProductImageStorage(context.applicationContext, "avatar-images", "avatar-captures", 640)
    }

    val updateProductImageUseCase: UpdateProductImageUseCase by lazy {
        UpdateProductImageUseCase(productRepository, productImageStorage) { localUseCases.requireAdministrator() }
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
