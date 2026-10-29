package cl.duoc.louaraya.data.local

import cl.duoc.louaraya.data.local.entity.*
import cl.duoc.louaraya.data.local.dao.LocalDao
import androidx.room.Database
import androidx.room.RoomDatabase
import cl.duoc.louaraya.data.local.dao.ProductDao
import cl.duoc.louaraya.data.local.dao.CommercialRequestDao
import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity
import cl.duoc.louaraya.data.local.entity.ProductEntity




@Database(
    entities = [ProductEntity::class, CommercialRequestEntity::class, UserEntity::class, SessionEntity::class, FavoriteEntity::class, CollectionEntity::class, CollectionProductEntity::class, CampaignEntity::class, FairEventEntity::class, NoticeEntity::class, ScanEntity::class, FavoriteCollectionEntity::class, ExplorationJourneyEntity::class, ExplorationEventEntity::class, StockMovementEntity::class, ListDismissalEntity::class],
    version = 10,
    exportSchema = false
)
abstract class LouArayaDatabase : RoomDatabase() {

    abstract fun localDao(): LocalDao

    abstract fun productDao(): ProductDao

    abstract fun commercialRequestDao(): CommercialRequestDao
}
