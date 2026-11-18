package cl.duoc.louaraya.data.remote

import cl.duoc.louaraya.data.repository.RoomLocalRepository
import cl.duoc.louaraya.domain.model.Campaign
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.model.CommercialRequest
import cl.duoc.louaraya.domain.model.ExplorationJourney
import cl.duoc.louaraya.domain.model.LocalNotice
import cl.duoc.louaraya.domain.model.LocalSnapshot
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.model.StockMovementType
import cl.duoc.louaraya.domain.model.UserProfile
import cl.duoc.louaraya.domain.model.UserRole
import cl.duoc.louaraya.domain.repository.ConnectedBusinessRepository
import cl.duoc.louaraya.domain.repository.LocalRepository
import cl.duoc.louaraya.domain.repository.ProductRepository
import cl.duoc.louaraya.domain.usecase.FormValidationException
import cl.duoc.louaraya.domain.usecase.QuotationDetails
import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.security.GeneralSecurityException

internal class ConnectedLocalRepository(
    private val local: RoomLocalRepository,
    private val identity: IdentityApi,
    private val workshop: WorkshopApi,
    private val storage: ConnectedStorage,
    private val gson: Gson,
    private val clock: () -> Long = System::currentTimeMillis,
    private val forgetPushDevice: (Long) -> String? = { null },
    private val campaignPreferencesChanged: (UserProfile) -> Unit = {}
) : ConnectedBusinessRepository, LocalRepository by local {
    private data class State(
        val session: ConnectedSession? = null,
        val snapshot: LocalSnapshot = LocalSnapshot(),
        val revision: Long = 0,
        val nextRefresh: Long = 0,
        val warning: String? = null
    )

    @Volatile private var state = State()
    private val stateLock = Any()
    private val initializeLock = Mutex()
    private val refreshLock = Mutex()
    private val pushLock = Mutex()
    private var initialized = false
    override val connectionWarning get() = state.warning

    override fun observeChanges() = merge(local.observeChanges(), flow {
        while (true) {
            delay(REFRESH_INTERVAL)
            if (state.session != null) emit(Unit)
        }
    })

    override suspend fun initialize() = initializeLock.withLock {
        if (initialized) return@withLock
        local.initialize(connected = true)
        val session = onDisk { storage.session() }
        if (session == null || local.session()?.id != session.userId) {
            local.signOut()
            if (session != null) onDisk { storage.saveSession(null) }
        } else {
            val saved = onDisk { storage.snapshot(session.userId) }
            val cached = saved?.takeIf { it.profile?.id == session.userId } ?: LocalSnapshot()
            synchronized(stateLock) { state = State(session = session, snapshot = cached) }
        }
        initialized = true
    }

    override suspend fun session(): UserProfile? {
        val current = state
        val expected = current.session ?: return null
        val mirror = local.session()?.takeIf { it.id == expected.userId } ?: return null
        return current.snapshot.profile?.copy(avatarImageReference = mirror.avatarImageReference) ?: mirror
    }

    override suspend fun profiles() = listOfNotNull(session())

    override suspend fun signIn(alias: String, password: String): UserProfile {
        val response = try { call { identity.login(Credentials(alias, password)) } }
        catch (error: RemoteFailure) {
            if (error.status == 401) throw IllegalArgumentException(error.message)
            throw error
        }
        return activate(response)
    }

    override suspend fun register(name: String, alias: String, password: String, avatar: Int, contact: String): UserProfile = activate(call {
        identity.register(Registration(name, alias, password, avatar, contact))
    })

    private suspend fun activate(response: AccountResponse): UserProfile {
        val profile = response.profile.copy(avatarImageReference = null)
        require(profile.id > 0 && response.token.isNotBlank()) { "La respuesta de inicio de sesión no es válida." }
        val session = ConnectedSession(response.token, profile.id)
        local.startConnectedSession(profile)
        onDisk { storage.saveSession(session) }
        synchronized(stateLock) { state = State(session, LocalSnapshot(profile = profile)) }
        return local.session() ?: profile
    }

    override suspend fun signOut() {
        val previous = state.session
        // Close local access first so another screen cannot read this profile while logout is in flight.
        val pushDevice = forgetSession(previous)
        if (previous != null) {
            if (pushDevice != null) pushLock.withLock {
                try { call { workshop.unregisterPushDevice(previous.authorization, pushDevice) } }
                catch (_: RemoteFailure) { /* An offline device is already detached locally. */ }
            }
            try { call { identity.logout(previous.authorization) } }
            catch (_: RemoteFailure) { /* The server session expires even when the network is down. */ }
        }
    }

    private suspend fun forgetSession(expected: ConnectedSession?): String? {
        val cleared = synchronized(stateLock) {
            if (state.session != expected) false else { state = State(); true }
        }
        if (!cleared) return null
        onDisk {
            storage.saveSession(null)
            expected?.let { storage.clearSnapshot(it.userId) }
        }
        local.signOut()
        return expected?.let { forgetPushDevice(it.userId) }
    }

    override suspend fun refreshConnected(force: Boolean): Unit = refreshLock.withLock {
        val before = state
        val account = before.session ?: return@withLock
        if (!force && clock() < before.nextRefresh) return@withLock
        try {
            val received = call { workshop.snapshot(account.authorization) }.toDomain().let { snapshot ->
                val profile = checkNotNull(snapshot.profile)
                check(profile.id == account.userId) { "El servidor devolvió datos de otra sesión." }
                snapshot.copy(
                    profile = profile.copy(avatarImageReference = null),
                    products = snapshot.products.map { it.copy(imageReference = null) },
                    requests = snapshot.requests.filter { profile.role == UserRole.ADMIN || it.ownerId == account.userId },
                    notices = snapshot.notices.filter { it.userId == account.userId },
                    stockMovements = snapshot.stockMovements.takeIf { profile.role == UserRole.ADMIN }.orEmpty()
                )
            }
            val accepted = synchronized(stateLock) {
                if (state.session != account || state.revision != before.revision) false else {
                    state = before.copy(snapshot = received, nextRefresh = clock() + REFRESH_INTERVAL, warning = null)
                    true
                }
            }
            if (accepted) {
                // Cache durable data when a server value actually changes; a passive refresh should not
                // invalidate Room or rewrite encrypted storage on every tick.
                if (received != before.snapshot) persistSnapshot(account, received)
                if (received.products != before.snapshot.products) local.cacheConnectedProducts(received.products)
            }
        } catch (error: RemoteFailure) {
            if (error.status == 401) local.transaction<Unit> { forgetSession(account) }
            else synchronized(stateLock) {
                if (state.session == account && state.revision == before.revision) {
                    state = before.copy(nextRefresh = clock() + REFRESH_INTERVAL, warning = error.message)
                }
            }
        }
    }

    override suspend fun updateProfile(profile: UserProfile) {
        val current = requireNotNull(session()) { "Inicia sesión para continuar." }
        require(profile.id == current.id) { "La sesión cambió. Abre tu perfil de nuevo." }
        val changes = profile.changes()
        if (changes == current.changes()) {
            // Profile photos stay in private device storage.
            local.updateProfile(profile)
            return
        }
        val updated = write({ api -> identity.updateProfile(api, changes) }) { snapshot, saved -> snapshot.copy(profile = saved) }
        local.startConnectedSession(updated)
        local.updateProfile(updated.copy(avatarImageReference = profile.avatarImageReference))
        if (current.notificationsEnabled != updated.notificationsEnabled || current.preferredCategory != updated.preferredCategory) {
            campaignPreferencesChanged(updated)
        }
    }

    private fun UserProfile.changes() = ProfileChanges(name, alias, avatar, contact, notificationsEnabled, preferredCategory)

    override suspend fun products(): List<Product> {
        val references = local.products().associate { it.id to it.imageReference }
        return state.snapshot.products.map { it.copy(imageReference = references[it.id]) }
    }

    override suspend fun requests(ownerId: Long?) = state.snapshot.requests.filter { ownerId == null || it.ownerId == ownerId }
    override suspend fun request(id: Long) = state.snapshot.requests.find { it.id == id }
    override suspend fun collections() = state.snapshot.collections
    override suspend fun campaigns() = state.snapshot.campaigns
    override suspend fun stockMovements() = state.snapshot.stockMovements
    override suspend fun hasStockMovementForRequest(requestId: Long) = stockMovements().any { it.requestId == requestId }
    override suspend fun hiddenRequestIds(userId: Long) = state.snapshot.hiddenRequestIds.takeIf { userId == state.session?.userId }.orEmpty()
    override suspend fun favoriteIds(userId: Long) = state.snapshot.favoriteIds.takeIf { userId == state.session?.userId }.orEmpty()
    override suspend fun favoriteCollectionIds(userId: Long) = state.snapshot.favoriteCollectionIds.takeIf { userId == state.session?.userId }.orEmpty()
    override suspend fun notices(userId: Long) = state.snapshot.notices.filter { it.userId == userId }

    override suspend fun saveProduct(product: Product): Long {
        val changes = ProductChanges(product.name, product.widthCm, product.lengthCm, product.material, product.price,
            product.campaignCategory.name, product.description, product.availability, product.active)
        val saved = write({ auth ->
            if (product.id == 0L) workshop.createProduct(auth, changes) else workshop.editProduct(auth, product.id, changes)
        }) { current, result -> current.copy(products = current.products.replace(result, Product::id).sortedBy { it.id }) }
        local.cacheConnectedProducts(listOf(saved))
        return saved.id
    }

    override suspend fun deactivateProduct(id: Long) = write({ workshop.deactivateProduct(it, id) }) { current, _ ->
        current.copy(products = current.products.map { if (it.id == id) it.copy(active = false) else it })
    }

    override suspend fun saveCollection(collection: Collection): Long = write({ auth ->
        if (collection.id == 0L) workshop.createCollection(auth, collection) else workshop.editCollection(auth, collection.id, collection)
    }) { current, saved -> current.copy(collections = current.collections.replace(saved, Collection::id).sortedBy { it.name }) }.id

    override suspend fun saveCampaign(campaign: Campaign): Long = write({ auth ->
        if (campaign.id == 0L) workshop.createCampaign(auth, campaign) else workshop.editCampaign(auth, campaign.id, campaign)
    }) { current, saved -> current.copy(campaigns = current.campaigns.replace(saved, Campaign::id).sortedByDescending { it.startsOn }) }.id

    override suspend fun createRequest(
        productId: Long, intention: RequestIntention, displayedPrice: Long, comment: String,
        details: QuotationDetails?, journey: ExplorationJourney?, quantity: Int
    ): CommercialRequest {
        val body = NewRequest(productId, intention, displayedPrice, quantity, comment, details,
            journey?.id, journey?.campaignId, journey?.collectionId)
        return withRequestKey("request", body) { key ->
            writeRequest { workshop.createRequest(it, body.copy(idempotencyKey = key)) }
        }
    }

    override suspend fun updateStatus(id: Long, status: RequestStatus) {
        writeRequest { workshop.changeStatus(it, id, StatusUpdate(status)) }
    }

    override suspend fun cancelRequest(id: Long) {
        writeRequest { workshop.cancelRequest(it, id) }
    }

    override suspend fun agreeTotal(requestId: Long, amount: Long) {
        writeRequest { workshop.agreeTotal(it, requestId, Agreement(amount)) }
    }

    private suspend fun writeRequest(request: suspend (String) -> CommercialRequest): CommercialRequest = write(request) { snapshot, saved ->
        snapshot.copy(requests = snapshot.requests.replace(saved, CommercialRequest::id).sortedByDescending { it.date })
    }

    override suspend fun changeStock(productId: Long, quantity: Int, type: StockMovementType, note: String) {
        val body = StockUpdate(quantity, type, note)
        withRequestKey("stock/$productId", body) { key ->
            write({ workshop.moveStock(it, productId, body.copy(idempotencyKey = key)) }) { snapshot, movement ->
                snapshot.copy(
                    products = snapshot.products.map { if (it.id == productId) it.copy(stock = movement.stockAfter) else it },
                    stockMovements = snapshot.stockMovements.replace(movement) { it.id }.sortedByDescending { it.date }
                )
            }
        }
    }

    override suspend fun setFavorite(userId: Long, productId: Long, selected: Boolean) = write({
        workshop.favoriteProduct(it, productId, Selection(selected))
    }) { snapshot, _ -> snapshot.copy(favoriteIds = if (selected) snapshot.favoriteIds + productId else snapshot.favoriteIds - productId) }

    override suspend fun setFavoriteCollection(userId: Long, collectionId: Long, selected: Boolean) = write({
        workshop.favoriteCollection(it, collectionId, Selection(selected))
    }) { snapshot, _ -> snapshot.copy(favoriteCollectionIds = if (selected) snapshot.favoriteCollectionIds + collectionId else snapshot.favoriteCollectionIds - collectionId) }

    override suspend fun markNoticeRead(userId: Long, noticeId: Long) = write({ workshop.markNoticeRead(it, noticeId) }) { snapshot, _ ->
        snapshot.copy(notices = snapshot.notices.map { if (it.id == noticeId) it.copy(read = true) else it })
    }

    override suspend fun markNoticeDelivered(userId: Long, noticeId: Long) = write({ workshop.markNoticeDelivered(it, noticeId) }) { snapshot, _ ->
        snapshot.copy(notices = snapshot.notices.map { if (it.id == noticeId) it.copy(delivered = true) else it })
    }

    override suspend fun dismissRequests(userId: Long, ids: List<Long>) = write({ workshop.dismissRequests(it, Dismissal(ids)) }) { snapshot, _ ->
        snapshot.copy(hiddenRequestIds = snapshot.hiddenRequestIds + ids)
    }

    override suspend fun dismissNotices(userId: Long, ids: List<Long>) = write({ workshop.dismissNotices(it, Dismissal(ids)) }) { snapshot, _ ->
        snapshot.copy(notices = snapshot.notices.filterNot { it.id in ids })
    }

    suspend fun registerPushDevice(
        userId: Long, installationId: String, endpoint: PushDevice, consent: () -> Boolean
    ) = pushLock.withLock {
        val account = state.session?.takeIf { it.userId == userId } ?: return@withLock false
        if (!consent()) return@withLock false
        call { workshop.registerPushDevice(account.authorization, installationId, endpoint) }
        if (state.session == account && consent()) return@withLock true
        call { workshop.unregisterPushDevice(account.authorization, installationId) }
        false
    }

    suspend fun unregisterPushDevice(userId: Long, installationId: String) = pushLock.withLock {
        val account = state.session?.takeIf { it.userId == userId } ?: return@withLock
        call { workshop.unregisterPushDevice(account.authorization, installationId) }
    }

    /** Always consult an authenticated server response before displaying a notice from a push event. */
    suspend fun pendingCampaignNotice(userId: Long, noticeId: Long): LocalNotice? {
        val account = state.session?.takeIf { it.userId == userId } ?: return null
        val response = call { workshop.snapshot(account.authorization) }
        return eligibleCampaignNotice(response, userId, noticeId).takeIf { state.session == account }
    }

    suspend fun acknowledgeCampaignNotice(userId: Long, noticeId: Long) {
        val account = state.session?.takeIf { it.userId == userId } ?: return
        call { workshop.markNoticeDelivered(account.authorization, noticeId) }
        synchronized(stateLock) {
            if (state.session == account) state = state.copy(nextRefresh = 0, revision = state.revision + 1,
                snapshot = state.snapshot.copy(notices = state.snapshot.notices.map {
                    if (it.id == noticeId && it.userId == userId) it.copy(delivered = true) else it
                }))
        }
    }

    /** Browse traces remain device-local. All catalog reads and stock edits still use the server. */
    fun productImages(): ProductRepository = object : ProductRepository {
        override suspend fun getProducts() = products()
        override suspend fun getProductById(id: Long) = products().find { it.id == id }
        override suspend fun updateImageReference(id: Long, imageReference: String): Boolean = local.transaction {
            val product = getProductById(id) ?: return@transaction false
            local.saveProduct(product.copy(imageReference = imageReference))
            true
        }
    }

    private suspend fun <T> write(operation: suspend (String) -> T, update: (LocalSnapshot, T) -> LocalSnapshot): T {
        val account = requireNotNull(state.session) { "Inicia sesión para continuar." }
        val result = try { call { operation(account.authorization) } } catch (error: RemoteFailure) {
            // UI calls arrive inside a Room transaction, so clearing the Room session here would be
            // rolled back along with the failed write. Force a subsequent independent refresh instead.
            synchronized(stateLock) {
                if (state.session == account) state = state.copy(nextRefresh = 0, revision = state.revision + 1)
            }
            throw error
        }
        val snapshot = synchronized(stateLock) {
            if (state.session != account) null else {
                val changed = update(state.snapshot, result)
                state = state.copy(snapshot = changed, nextRefresh = 0, revision = state.revision + 1)
                changed
            }
        }
        if (snapshot != null) persistSnapshot(account, snapshot)
        return result
    }

    private suspend fun <T> withRequestKey(scope: String, body: Any, operation: suspend (String) -> T): T {
        val account = requireNotNull(state.session) { "Inicia sesión para continuar." }
        val fingerprint = connectionDigest("${account.userId}:$scope:${gson.toJson(body)}")
        val key = onDisk { storage.requestKey(fingerprint) }
        val result = try { operation(key) } catch (error: Exception) {
            if (error is FormValidationException || (error is RemoteFailure && error.status in 400..499 && error.status !in setOf(408, 429))) {
                onDisk { storage.clearRequestKey(fingerprint) }
            }
            throw error
        }
        onDisk { storage.clearRequestKey(fingerprint) }
        return result
    }

    private suspend fun <T> call(operation: suspend () -> T): T = try {
        operation()
    } catch (error: HttpException) {
        val body = runCatching { error.response()?.errorBody()?.string()?.let { gson.fromJson(it, ApiError::class.java) } }.getOrNull()
        val message = body?.message?.takeIf { it.isNotBlank() } ?: when (error.code()) {
            401 -> "La sesión expiró o los datos de acceso no coinciden."
            403 -> "Tu perfil no tiene permiso para esta operación."
            404 -> "El elemento ya no está disponible."
            409 -> "Los datos cambiaron en el servidor. Actualiza y vuelve a intentar."
            else -> "No se pudo completar la consulta al servidor. Vuelve a intentar."
        }
        if (!body?.fieldErrors.isNullOrEmpty()) throw FormValidationException(requireNotNull(body?.fieldErrors))
        throw RemoteFailure(error.code(), message)
    } catch (_: IOException) {
        throw RemoteFailure(0, "Sin conexión con el servidor. Comprueba la red e inténtalo de nuevo.")
    } catch (_: JsonParseException) {
        throw RemoteFailure(0, "El servidor respondió en un formato inesperado. Vuelve a intentar.")
    }

    private suspend fun persistSnapshot(account: ConnectedSession, snapshot: LocalSnapshot) = onDisk {
        if (state.session != account || state.snapshot != snapshot) return@onDisk
        try { storage.saveSnapshot(account.userId, snapshot) }
        catch (_: IOException) { /* The confirmed data remains available in memory. */ }
        catch (_: GeneralSecurityException) { /* Keep the server response usable if disk encryption is unavailable. */ }
    }

    private suspend fun <T> onDisk(operation: () -> T): T = withContext(Dispatchers.IO) { operation() }

    private fun <T> List<T>.replace(value: T, id: (T) -> Long) = filterNot { id(it) == id(value) } + value

    private companion object {
        const val REFRESH_INTERVAL = 3_000L
    }
}

internal class RemoteFailure(val status: Int, override val message: String) : IllegalStateException(message) {
    val canRetry get() = status == 0 || status >= 500 || status in setOf(408, 429)
}

internal fun eligibleCampaignNotice(snapshot: WorkshopSnapshot, userId: Long, noticeId: Long): LocalNotice? {
    val profile = snapshot.profile
    if (profile.id != userId || profile.role != UserRole.CLIENT || !profile.notificationsEnabled) return null
    return snapshot.notices.orEmpty().firstOrNull {
        it.id == noticeId && it.userId == userId && !it.delivered && !it.read &&
            it.destination.startsWith("campaign/") && (it.destination.substringAfter('/').toLongOrNull() ?: 0) > 0
    }
}
