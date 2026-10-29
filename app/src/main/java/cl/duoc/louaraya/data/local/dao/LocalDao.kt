package cl.duoc.louaraya.data.local.dao

import androidx.room.*
import cl.duoc.louaraya.data.local.entity.*

@Dao
interface LocalDao {
    @Query("UPDATE products SET stock = stock + :delta WHERE id = :productId AND stock + :delta BETWEEN 0 AND 2147483647")
    suspend fun adjustStock(productId: Long, delta: Int): Int
    @Insert suspend fun addStockMovement(movement: StockMovementEntity)
    @Query("SELECT * FROM stock_movements ORDER BY date DESC, id DESC") suspend fun stockMovements(): List<StockMovementEntity>
    @Query("SELECT EXISTS(SELECT 1 FROM stock_movements WHERE requestId = :requestId)") suspend fun hasStockMovementForRequest(requestId: Long): Boolean
    @Query("SELECT * FROM users WHERE id = :id") suspend fun user(id: Long): UserEntity?
    @Query("SELECT * FROM users WHERE alias = :alias") suspend fun userByAlias(alias: String): UserEntity?
    @Query("SELECT * FROM users ORDER BY id") suspend fun users(): List<UserEntity>
    @Insert suspend fun insertUser(user: UserEntity): Long
    @Update suspend fun updateUser(user: UserEntity)
    @Query("SELECT userId FROM app_session WHERE id = 1") suspend fun sessionUserId(): Long?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSession(session: SessionEntity)
    @Query("DELETE FROM app_session") suspend fun clearSession()
    @Query("SELECT productId FROM favorites WHERE userId = :userId") suspend fun favorites(userId: Long): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun favorite(favorite: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE userId = :userId AND productId = :productId") suspend fun removeFavorite(userId: Long, productId: Long)
    @Query("SELECT * FROM collections ORDER BY name") suspend fun collections(): List<CollectionEntity>
    @Query("SELECT * FROM collection_products") suspend fun collectionProducts(): List<CollectionProductEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCollection(collection: CollectionEntity): Long
    @Query("DELETE FROM collection_products WHERE collectionId = :id") suspend fun clearCollectionProducts(id: Long)
    @Insert suspend fun insertCollectionProducts(products: List<CollectionProductEntity>)
    @Transaction suspend fun saveCollectionWithProducts(collection: CollectionEntity, productIds: List<Long>): Long {
        val id = saveCollection(collection).let { if (collection.id == 0L) it else collection.id }
        clearCollectionProducts(id)
        insertCollectionProducts(productIds.distinct().map { CollectionProductEntity(id, it) })
        return id
    }
    @Query("SELECT * FROM campaigns ORDER BY startsOn DESC") suspend fun campaigns(): List<CampaignEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveCampaign(campaign: CampaignEntity): Long
    @Query("SELECT * FROM fair_events ORDER BY date") suspend fun events(): List<FairEventEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun seedEvents(events: List<FairEventEntity>)
    @Query("SELECT * FROM local_notices WHERE userId = :userId AND id NOT IN (SELECT itemId FROM list_dismissals WHERE userId = :userId AND kind = 'NOTICE') ORDER BY date DESC, id DESC") suspend fun notices(userId: Long): List<NoticeEntity>
    @Query("SELECT itemId FROM list_dismissals WHERE userId = :userId AND kind = 'REQUEST'") suspend fun hiddenRequestIds(userId: Long): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun dismiss(items: List<ListDismissalEntity>)
    @Query("UPDATE local_notices SET read = 1, delivered = 1 WHERE userId = :userId AND id IN (:ids)") suspend fun settleNotices(userId: Long, ids: List<Long>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addNotice(notice: NoticeEntity)
    @Query("UPDATE local_notices SET read = 1 WHERE id = :id AND userId = :userId") suspend fun readNotice(userId: Long, id: Long)
    @Query("UPDATE local_notices SET delivered = 1 WHERE id = :id AND userId = :userId") suspend fun deliveredNotice(userId: Long, id: Long)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun recordScan(scan: ScanEntity)
    @Query("SELECT collectionId FROM favorite_collections WHERE userId = :userId") suspend fun favoriteCollections(userId: Long): List<Long>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun favoriteCollection(favorite: FavoriteCollectionEntity)
    @Query("DELETE FROM favorite_collections WHERE userId = :userId AND collectionId = :collectionId") suspend fun removeFavoriteCollection(userId: Long, collectionId: Long)
    @Insert suspend fun insertJourney(journey: ExplorationJourneyEntity)
    @Query("SELECT * FROM exploration_journeys WHERE id = :id") suspend fun journey(id: String): ExplorationJourneyEntity?
    @Query("SELECT * FROM exploration_journeys WHERE :userId IS NULL OR userId = :userId ORDER BY date DESC, id") suspend fun journeys(userId: Long?): List<ExplorationJourneyEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun recordExploration(event: ExplorationEventEntity)
    @Query("SELECT * FROM exploration_events WHERE :userId IS NULL OR userId = :userId ORDER BY date, rowid") suspend fun explorationEvents(userId: Long?): List<ExplorationEventEntity>
}
