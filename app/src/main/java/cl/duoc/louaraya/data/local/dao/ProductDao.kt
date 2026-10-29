package cl.duoc.louaraya.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cl.duoc.louaraya.data.local.entity.ProductEntity




@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY id")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM products")
    suspend fun nextId(): Long

    @androidx.room.Upsert
    suspend fun save(product: ProductEntity)

    @Query("UPDATE products SET image_reference = :imageReference WHERE id = :id")
    suspend fun updateImageReference(id: Long, imageReference: String): Int




    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}
