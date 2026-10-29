package cl.duoc.louaraya.data.repository

import androidx.room.withTransaction
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.entity.*
import cl.duoc.louaraya.data.mapper.toDomain
import cl.duoc.louaraya.data.mapper.toEntity
import cl.duoc.louaraya.data.security.PasswordHasher
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.repository.LocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomLocalRepository(private val db: LouArayaDatabase) : LocalRepository {
    private val dao get() = db.localDao()
    private val passwords = PasswordHasher()
    override fun observeChanges(): Flow<Unit> = db.invalidationTracker
        .createFlow(*SNAPSHOT_TABLES, emitInitialState = true)
        .map { Unit }

    override suspend fun <T> transaction(block: suspend () -> T): T = db.withTransaction { block() }

    override suspend fun initialize() = withContext(Dispatchers.IO) {
        db.withTransaction {
            seedUser(1, "Cliente Lou Araya", "cliente.demo", "CLIENT", "Cliente de demostración")
            seedUser(2, "Administración Lou Araya", "admin.demo", "ADMIN", "Administración demo")

            val events = listOf(
                FairEventEntity(1, "Feria Textil de Primavera", "2026-11-14", "Parque Bicentenario, Santiago", -33.401, -70.601),
                FairEventEntity(2, "Showroom Lou Araya", "2026-11-21", "Barrio Italia, Santiago", -33.447, -70.625),
                FairEventEntity(3, "Encuentro de Diseño", "2026-12-05", "Centro de Santiago", -33.437, -70.650)
            )
            dao.seedEvents(events)
            updatePreviousEventLabels(db, events)
        }
    }

    private suspend fun seedUser(id: Long, name: String, alias: String, role: String, previousName: String) {
        val existing = dao.user(id)
        if (existing == null) {
            val salt = passwords.salt()
            dao.insertUser(UserEntity(id, name, alias, passwords.hash("LouDemo2026!", salt), salt, role))
        } else if (existing.alias == alias && existing.name == previousName) {
            dao.updateUser(existing.copy(name = name))
        }
    }


    private fun updatePreviousEventLabels(db: LouArayaDatabase, events: List<FairEventEntity>) {
        val previousLabels = listOf(
            PreviousEventLabel(1L, "Feria Textil de Primavera · Demo", "Parque Bicentenario, Santiago · punto ficticio"),
            PreviousEventLabel(2L, "Showroom Lou Araya · Demo", "Barrio Italia, Santiago · punto ficticio"),
            PreviousEventLabel(3L, "Encuentro de Diseño · Demo", "Centro de Santiago · punto ficticio")
        )
        val sqlDb = db.openHelper.writableDatabase
        previousLabels.forEach { previous ->
            val current = events.first { it.id == previous.id }
            sqlDb.execSQL(
                "UPDATE fair_events SET name = ?, location = ? WHERE id = ? AND name = ? AND location = ?",
                arrayOf<Any>(current.name, current.location, previous.id, previous.name, previous.location)
            )
        }
    }

    private data class PreviousEventLabel(val id: Long, val name: String, val location: String)
    override suspend fun session(): UserProfile? = dao.sessionUserId()?.let { dao.user(it)?.profile() }
    override suspend fun signIn(alias: String, password: String): UserProfile = withContext(Dispatchers.IO) {
        val user = dao.userByAlias(alias)
        require(user != null && passwords.verify(password, user.passwordSalt, user.passwordHash)) { "Nombre de usuario o contraseña incorrectos." }
        dao.saveSession(SessionEntity(userId = user.id))
        user.profile()
    }
    override suspend fun register(name: String, alias: String, password: String, avatar: Int, contact: String): UserProfile = withContext(Dispatchers.IO) {
        db.withTransaction {
            require(dao.userByAlias(alias) == null) { "El nombre de usuario ya está registrado." }
            val salt = passwords.salt()
            val user = UserEntity(name = name, alias = alias, passwordHash = passwords.hash(password, salt), passwordSalt = salt, role = "CLIENT", avatar = avatar, contact = contact)
            val id = dao.insertUser(user)
            dao.saveSession(SessionEntity(userId = id))
            user.copy(id = id).profile()
        }
    }
    override suspend fun signOut() = dao.clearSession()
    override suspend fun updateProfile(profile: UserProfile) {
        val old = requireNotNull(dao.user(profile.id))
        dao.updateUser(old.copy(alias = profile.alias, name = profile.name, avatar = profile.avatar, contact = profile.contact, notificationsEnabled = profile.notificationsEnabled, preferredCategory = profile.preferredCategory, avatarImageReference = profile.avatarImageReference))
    }
    override suspend fun profiles() = dao.users().map { it.profile() }
    override suspend fun products() = db.productDao().getAll().map { it.toDomain() }
    override suspend fun adjustStock(productId: Long, delta: Int) = dao.adjustStock(productId, delta) == 1
    override suspend fun addStockMovement(movement: StockMovement) = dao.addStockMovement(StockMovementEntity(
        movement.id, movement.productId, movement.quantityDelta, movement.stockAfter, movement.type.name,
        movement.date, movement.actorId, movement.note, movement.requestId
    ))
    override suspend fun stockMovements() = dao.stockMovements().map {
        StockMovement(it.id, it.productId, it.quantityDelta, it.stockAfter, StockMovementType.valueOf(it.type), it.date, it.actorId, it.note, it.requestId)
    }
    override suspend fun hasStockMovementForRequest(requestId: Long) = dao.hasStockMovementForRequest(requestId)
    override suspend fun saveProduct(product: Product): Long = db.withTransaction {
        val id = product.id.takeIf { it > 0 } ?: db.productDao().nextId()
        db.productDao().save(product.copy(id = id).toEntity())
        id
    }
    override suspend fun requests(ownerId: Long?) =
        (if (ownerId == null) db.commercialRequestDao().getAll() else db.commercialRequestDao().getForOwner(ownerId)).map { it.toDomain() }
    override suspend fun request(id: Long) = db.commercialRequestDao().getById(id)?.toDomain()
    override suspend fun hiddenRequestIds(userId: Long) = dao.hiddenRequestIds(userId).toSet()
    override suspend fun dismissRequests(userId: Long, ids: List<Long>) {
        dao.dismiss(ids.distinct().map { ListDismissalEntity(userId, "REQUEST", it) })
    }
    override suspend fun dismissNotices(userId: Long, ids: List<Long>) = db.withTransaction {
        dao.dismiss(ids.distinct().map { ListDismissalEntity(userId, "NOTICE", it) })
        dao.settleNotices(userId, ids)
    }
    override suspend fun saveRequest(request: CommercialRequest): CommercialRequest {
        val id = db.commercialRequestDao().insert(request.toEntity())
        return request.copy(id = id)
    }
    override suspend fun updateStatus(id: Long, status: RequestStatus) { db.commercialRequestDao().updateStatusAndGet(id, status.name) }
    override suspend fun favoriteIds(userId: Long) = dao.favorites(userId).toSet()
    override suspend fun setFavorite(userId: Long, productId: Long, selected: Boolean) {
        if (selected) dao.favorite(FavoriteEntity(userId, productId)) else dao.removeFavorite(userId, productId)
    }
    override suspend fun collections(): List<Collection> {
        val links = dao.collectionProducts().groupBy { it.collectionId }
        return dao.collections().map { Collection(it.id, it.name, it.description, links[it.id].orEmpty().map { link -> link.productId }) }
    }
    override suspend fun saveCollection(collection: Collection) = dao.saveCollectionWithProducts(CollectionEntity(collection.id, collection.name, collection.description), collection.productIds)
    override suspend fun campaigns() = dao.campaigns().map { Campaign(it.id, it.name, it.collectionId, it.startsOn, it.endsOn, it.published, it.targetCategory?.let(CampaignCategory::valueOf)) }
    override suspend fun saveCampaign(campaign: Campaign) = dao.saveCampaign(CampaignEntity(campaign.id, campaign.name, campaign.collectionId, campaign.startsOn, campaign.endsOn, campaign.published, campaign.targetCategory?.name)).let { if (campaign.id == 0L) it else campaign.id }
    override suspend fun events() = dao.events().map { FairEvent(it.id, it.name, it.date, it.location, it.latitude, it.longitude) }
    override suspend fun notices(userId: Long) = dao.notices(userId).map { LocalNotice(it.id, it.userId, it.title, it.body, it.destination, it.date, it.read, it.eventKey, it.delivered) }
    override suspend fun addNotice(notice: LocalNotice) = dao.addNotice(NoticeEntity(notice.id, notice.userId, notice.title, notice.body, notice.destination, notice.date, notice.read, notice.eventKey, notice.delivered))
    override suspend fun markNoticeRead(userId: Long, noticeId: Long) = dao.readNotice(userId, noticeId)
    override suspend fun markNoticeDelivered(userId: Long, noticeId: Long) = dao.deliveredNotice(userId, noticeId)
    override suspend fun recordScan(scan: ScanRecord) = dao.recordScan(ScanEntity(scan.id, scan.userId, scan.destination, scan.date, scan.sessionKey))
    override suspend fun favoriteCollectionIds(userId: Long) = dao.favoriteCollections(userId).toSet()
    override suspend fun setFavoriteCollection(userId: Long, collectionId: Long, selected: Boolean) {
        if (selected) dao.favoriteCollection(FavoriteCollectionEntity(userId, collectionId)) else dao.removeFavoriteCollection(userId, collectionId)
    }
    override suspend fun saveJourney(journey: ExplorationJourney) = dao.insertJourney(ExplorationJourneyEntity(journey.id, journey.userId, journey.campaignId, journey.collectionId, journey.date))
    override suspend fun journey(id: String) = dao.journey(id)?.let { ExplorationJourney(it.id, it.userId, it.campaignId, it.collectionId, it.date) }
    override suspend fun journeys(userId: Long?) = dao.journeys(userId).map { ExplorationJourney(it.id, it.userId, it.campaignId, it.collectionId, it.date) }
    override suspend fun recordExploration(event: ExplorationEvent) = dao.recordExploration(ExplorationEventEntity(event.eventKey, event.userId, event.kind.name, event.date, event.journeyId, event.campaignId, event.collectionId, event.productId, event.requestId, event.amount))
    override suspend fun explorationEvents(userId: Long?) = dao.explorationEvents(userId).map { ExplorationEvent(it.eventKey, it.userId, ExplorationKind.valueOf(it.kind), it.date, it.journeyId, it.campaignId, it.collectionId, it.productId, it.requestId, it.amount) }
    override suspend fun updateAgreement(requestId: Long, total: Long, date: Long) {
        check(db.commercialRequestDao().updateAgreement(requestId, total, date) == 1) { "La solicitud no existe." }
    }
    private fun UserEntity.profile() = UserProfile(id, name, alias, UserRole.valueOf(role), avatar, contact, notificationsEnabled, preferredCategory, avatarImageReference)

    private companion object {
        val SNAPSHOT_TABLES = arrayOf(
            "users", "app_session", "products", "commercial_requests", "favorites",
            "collections", "collection_products", "campaigns", "fair_events", "local_notices",
            "favorite_collections", "exploration_journeys", "exploration_events", "stock_movements", "list_dismissals"
        )
    }
}
