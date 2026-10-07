package cl.duoc.louaraya.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cl.duoc.louaraya.data.local.entity.ProductEntity

/**
 * Operaciones de acceso a la tabla `products`.
 */
@Dao
interface ProductDao {

    @Query("SELECT * FROM products ORDER BY id")
    suspend fun getAll(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    @Query("UPDATE products SET image_reference = :imageReference WHERE id = :id")
    suspend fun updateImageReference(id: Long, imageReference: String): Int

    /**
     * Inserta los productos; si ya existe uno con el mismo id, lo reemplaza.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun deleteAll()
}
