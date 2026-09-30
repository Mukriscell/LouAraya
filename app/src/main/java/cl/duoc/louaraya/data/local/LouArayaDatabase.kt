package cl.duoc.louaraya.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import cl.duoc.louaraya.data.local.dao.ProductDao
import cl.duoc.louaraya.data.local.entity.ProductEntity

/**
 * Base de datos local de la aplicación.
 */
@Database(
    entities = [ProductEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LouArayaDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
}
