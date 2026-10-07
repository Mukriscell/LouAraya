package cl.duoc.louaraya.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity

@Dao
interface CommercialRequestDao {
    @Query("SELECT * FROM commercial_requests ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<CommercialRequestEntity>

    @Query("SELECT * FROM commercial_requests WHERE id = :id")
    suspend fun getById(id: Long): CommercialRequestEntity?

    // ABORT por defecto: una inserción nunca reemplaza una solicitud anterior.
    @Insert
    suspend fun insert(request: CommercialRequestEntity): Long

    @Query("UPDATE commercial_requests SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String): Int

    @Transaction
    suspend fun updateStatusAndGet(id: Long, status: String): CommercialRequestEntity {
        if (updateStatus(id, status) != 1) {
            throw NoSuchElementException("La solicitud no existe.")
        }
        return checkNotNull(getById(id)) { "La solicitud no existe." }
    }
}
