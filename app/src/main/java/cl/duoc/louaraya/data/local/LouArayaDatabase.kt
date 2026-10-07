package cl.duoc.louaraya.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import cl.duoc.louaraya.data.local.dao.ProductDao
import cl.duoc.louaraya.data.local.dao.CommercialRequestDao
import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity
import cl.duoc.louaraya.data.local.entity.ProductEntity

/**
 * Base de datos local de la aplicación.
 */
@Database(
    entities = [ProductEntity::class, CommercialRequestEntity::class],
    version = 3,
    exportSchema = false
)
abstract class LouArayaDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao

    abstract fun commercialRequestDao(): CommercialRequestDao
}
