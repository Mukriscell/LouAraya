package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.repository.LocalRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

data class QuotationDetails(val event: String, val widthCm: Double, val lengthCm: Double, val material: String, val colors: String, val requiredOn: String) {
    fun summary() = "Evento: $event\nMedidas solicitadas: $widthCm × $lengthCm cm\nMaterial solicitado: $material\nColores: $colors\nFecha deseada para el evento: $requiredOn"
}


class LocalUseCases(private val repository: LocalRepository, private val now: () -> Long = System::currentTimeMillis) {
    suspend fun initialize() = repository.initialize()
    suspend fun profile() = repository.session()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSnapshots(): Flow<LocalSnapshot> = repository.observeChanges()
        .mapLatest { snapshot() }
        .distinctUntilChanged()

    suspend fun snapshot(): LocalSnapshot = repository.transaction {
        val actor = repository.session() ?: return@transaction LocalSnapshot()
        LocalSnapshot(actor, repository.products(), repository.requests(if (actor.role == UserRole.ADMIN) null else actor.id),
            if (actor.role == UserRole.CLIENT) repository.favoriteIds(actor.id) else emptySet(),
            repository.collections(), repository.campaigns(), repository.events(), repository.notices(actor.id),
            if (actor.role == UserRole.CLIENT) repository.favoriteCollectionIds(actor.id) else emptySet(),
            repository.journeys(if (actor.role == UserRole.ADMIN) null else actor.id),
            repository.explorationEvents(if (actor.role == UserRole.ADMIN) null else actor.id),
            if (actor.role == UserRole.ADMIN) repository.stockMovements() else emptyList(),
            if (actor.role == UserRole.ADMIN) repository.hiddenRequestIds(actor.id) else emptySet())
    }
    suspend fun signIn(alias: String, password: String): UserProfile {
        fieldRequire(alias.isNotBlank(), "alias", "Escribe tu nombre de usuario.")
        fieldRequire(password.isNotBlank(), "password", "Escribe tu contraseña.")
        return try { repository.signIn(normalizeAlias(alias), password) }
        catch (error: IllegalArgumentException) { throw FormValidationException(mapOf("password" to (error.message ?: "El nombre de usuario o la contraseña no coinciden."))) }
    }
    suspend fun register(name: String, alias: String, password: String, avatar: Int, contact: String, avatarImageReference: String? = null): UserProfile = repository.transaction {
        fieldRequire(name.trim().length in 2..80, "name", "Escribe un nombre de entre 2 y 80 caracteres.")
        val normalized = normalizeAlias(alias)
        fieldRequire(normalized.matches(Regex("[a-z0-9._-]{3,40}")), "alias", "El nombre de usuario debe tener 3–40 letras, números, puntos o guiones.")
        FormValidation.registrationPassword(password)
        require(avatar in AvatarStyle.entries.indices) { "Selecciona un avatar válido." }
        validateContact(contact)
        fieldRequire(repository.profiles().none { it.alias == normalized }, "alias", "Este nombre de usuario ya está registrado. Prueba con uno diferente.")
        require(avatarImageReference == null || avatarImageReference.matches(Regex("avatar-images/[0-9a-fA-F-]{36}\\.jpg"))) { "La fotografía no es válida." }
        val profile = repository.register(name.trim(), normalized, password, avatar, contact.trim())
        if (avatarImageReference == null) profile
        else profile.copy(avatarImageReference = avatarImageReference).also { repository.updateProfile(it) }
    }
    suspend fun signOut() = repository.signOut()
    suspend fun editProfile(name: String, avatar: Int, contact: String, notifications: Boolean, preferredCategory: String?, alias: String? = null, usePresetAvatar: Boolean = false) = repository.transaction {
        val actor = actor()
        fieldRequire(name.trim().length in 2..80, "name", "Escribe un nombre de entre 2 y 80 caracteres.")
        require(avatar in AvatarStyle.entries.indices) { "Selecciona un avatar válido." }
        require(preferredCategory == null || CampaignCategory.entries.any { it.name == preferredCategory })
        validateContact(contact)
        val normalized = alias?.let(::normalizeAlias) ?: actor.alias
        fieldRequire(normalized.matches(Regex("[a-z0-9._-]{3,40}")), "alias", "El nombre de usuario debe tener 3–40 letras, números, puntos o guiones.")
        fieldRequire(repository.profiles().none { it.id != actor.id && it.alias == normalized }, "alias", "El nombre de usuario ya está registrado. Tus cambios aún no se guardan.")
        repository.updateProfile(actor.copy(alias = normalized, name = name.trim(), avatar = avatar, contact = contact.trim(), notificationsEnabled = notifications, preferredCategory = preferredCategory, avatarImageReference = if (usePresetAvatar) null else actor.avatarImageReference))
        actor.avatarImageReference.takeIf { usePresetAvatar }
    }


    suspend fun setAvatarPhoto(expectedUserId: Long, reference: String): String? = repository.transaction {
        val actor = actor()
        require(actor.id == expectedUserId) { "La sesión cambió. Vuelve a tu perfil para tomar la fotografía." }
        require(reference.matches(Regex("avatar-images/[0-9a-fA-F-]{36}\\.jpg"))) { "La fotografía no es válida." }
        repository.updateProfile(actor.copy(avatarImageReference = reference))
        actor.avatarImageReference
    }
    suspend fun setFavorite(productId: Long, selected: Boolean, journeyId: String? = null) = repository.transaction {
        val actor = actor(UserRole.CLIENT)
        require(repository.products().any { it.id == productId && (it.active || !selected) }) { "El producto ya no está disponible." }
        val journey = sourceJourney(actor, journeyId, productId = productId)
        val changed = selected && productId !in repository.favoriteIds(actor.id)
        repository.setFavorite(actor.id, productId, selected)
        if (changed) trace(actor, ExplorationKind.PRODUCT_FAVORITE, journey, productId = productId)
    }

    suspend fun setFavoriteCollection(collectionId: Long, selected: Boolean, journeyId: String? = null) = repository.transaction {
        val actor = actor(UserRole.CLIENT)
        require(repository.collections().any { it.id == collectionId }) { "La colección ya no está disponible." }
        val journey = sourceJourney(actor, journeyId, collectionId = collectionId)
        val changed = selected && collectionId !in repository.favoriteCollectionIds(actor.id)
        repository.setFavoriteCollection(actor.id, collectionId, selected)
        if (changed) trace(actor, ExplorationKind.COLLECTION_FAVORITE, journey, collectionId = collectionId)
    }

    suspend fun startCampaignJourney(campaignId: Long, journeyId: String = UUID.randomUUID().toString(), expectedCollectionId: Long? = null): ExplorationJourney = repository.transaction {
        val actor = actor(UserRole.CLIENT)
        val campaign = repository.campaigns().firstOrNull { it.id == campaignId && it.published }
        require(campaign != null) { "La campaña ya no está disponible." }
        require(expectedCollectionId == null || expectedCollectionId == campaign.collectionId) { "La colección cambió. Abre la campaña de nuevo." }
        require(repository.collections().any { it.id == campaign.collectionId }) { "La colección ya no está disponible." }
        val journey = ExplorationJourney(journeyId, actor.id, campaign.id, campaign.collectionId, now())
        repository.saveJourney(journey)
        trace(actor, ExplorationKind.CAMPAIGN_VIEW, journey)
        trace(actor, ExplorationKind.COLLECTION_VIEW, journey, collectionId = journey.collectionId)
        journey
    }

    suspend fun recordCollectionView(collectionId: Long, journeyId: String? = null) = repository.transaction {
        val actor = actor(UserRole.CLIENT)
        require(repository.collections().any { it.id == collectionId }) { "La colección ya no está disponible." }
        trace(actor, ExplorationKind.COLLECTION_VIEW, sourceJourney(actor, journeyId, collectionId = collectionId), collectionId = collectionId)
    }

    suspend fun recordProductView(productId: Long, journeyId: String? = null) = repository.transaction {
        val actor = actor(UserRole.CLIENT)
        require(repository.products().any { it.id == productId }) { "El producto ya no está disponible." }
        trace(actor, ExplorationKind.PRODUCT_VIEW, sourceJourney(actor, journeyId, productId = productId), productId = productId)
    }
    suspend fun saveProduct(product: Product): Long = repository.transaction {
        actor(UserRole.ADMIN)
        val errors = buildMap {
            if (product.name.trim().length !in 2..100) put("name", "Escribe un nombre de entre 2 y 100 caracteres.")
            if (product.material.isBlank()) put("material", "Completa el material.")
            if (product.description.length > 2000) put("description", "La descripción admite hasta 2000 caracteres.")
            if (!product.widthCm.isFinite() || product.widthCm <= 0) put("width", "Escribe un ancho positivo.")
            if (!product.lengthCm.isFinite() || product.lengthCm <= 0) put("length", "Escribe un largo positivo.")
            if (product.price < 0) put("price", "El precio debe ser igual o mayor a cero.")
        }
        if (errors.isNotEmpty()) throw FormValidationException(errors)
        require(product.availability in setOf("AVAILABLE", "MADE_TO_ORDER")) { "Selecciona disponibilidad." }
        val previous = repository.products().firstOrNull { it.id == product.id }
        require(product.id == 0L || previous != null) { "El producto no existe." }
        repository.saveProduct(product.copy(name = product.name.trim(), material = product.material.trim(), imageReference = previous?.imageReference, stock = previous?.stock ?: 0))
    }
    suspend fun changeStock(productId: Long, quantity: Int, type: StockMovementType, note: String = "") = repository.transaction {
        val actor = actor(UserRole.ADMIN)
        fieldRequire(quantity > 0, "quantity", "Escribe una cantidad entera mayor a cero.")
        fieldRequire(note.trim().length <= 250, "stockNote", "La observación admite hasta 250 caracteres.")
        require(type in setOf(StockMovementType.ENTRY, StockMovementType.DIRECT_SALE, StockMovementType.WITHDRAWAL)) { "Selecciona un movimiento válido." }
        val product = requireNotNull(repository.products().firstOrNull { it.id == productId }) { "El producto no existe." }
        val delta = if (type == StockMovementType.ENTRY) quantity else -quantity
        fieldRequire(repository.adjustStock(productId, delta), "quantity",
            if (delta < 0) "Stock insuficiente: hay ${product.stock} unidades en bodega." else "La cantidad supera el máximo permitido en bodega.")
        repository.addStockMovement(StockMovement(productId = productId, quantityDelta = delta,
            stockAfter = (product.stock.toLong() + delta).toInt(), type = type, date = now(), actorId = actor.id, note = note.trim()))
    }
    suspend fun saveCollection(collection: Collection): Long = repository.transaction {
        actor(UserRole.ADMIN)
        fieldRequire(collection.name.trim().length in 2..100, "name", "Escribe el nombre de la colección (2–100 caracteres).")
        fieldRequire(collection.description.length <= 2000, "description", "La descripción admite hasta 2000 caracteres.")
        val existingIds = repository.products().map { it.id }.toSet()
        require(collection.productIds.all { it in existingIds }) { "Una pieza no existe." }
        require(collection.id == 0L || repository.collections().any { it.id == collection.id })
        repository.saveCollection(collection.copy(name = collection.name.trim(), productIds = collection.productIds.distinct()))
    }
    suspend fun saveCampaign(campaign: Campaign): Long = repository.transaction {
        actor(UserRole.ADMIN)
        fieldRequire(campaign.name.trim().length in 2..100, "name", "Escribe el nombre de la campaña (2–100 caracteres).")
        require(repository.collections().any { it.id == campaign.collectionId }) { "Selecciona una colección." }
        fieldRequire(formDay(campaign.startsOn, "start") <= formDay(campaign.endsOn, "end"), "end", "La fecha final debe ser posterior o igual al inicio.")
        val old = repository.campaigns().firstOrNull { it.id == campaign.id }
        require(campaign.id == 0L || old != null)
        val id = repository.saveCampaign(campaign.copy(name = campaign.name.trim()))
        if (campaign.published && old?.published != true) {
            val products = repository.products()
            val collections = repository.collections()
            repository.profiles().filter { it.role == UserRole.CLIENT && it.notificationsEnabled }.forEach { client ->
                val target = campaign.targetCategory
                val favoriteProducts = if (target != null) repository.favoriteIds(client.id) else emptySet()
                val favoriteCollections = if (target != null) repository.favoriteCollectionIds(client.id) else emptySet()
                val relevant = target == null || client.preferredCategory == target.name ||
                    products.any { it.id in favoriteProducts && it.campaignCategory == target } ||
                    collections.any { collection -> collection.id in favoriteCollections &&
                        (collection.id == campaign.collectionId || products.any { it.id in collection.productIds && it.campaignCategory == target }) }
                if (relevant) notice(client.id, "Nueva campaña", campaign.name, "campaign/$id", "campaign/$id/${UUID.randomUUID()}")
            }
        }
        id
    }
    suspend fun createRequest(productId: Long, intention: RequestIntention, displayedPrice: Long, comment: String, details: QuotationDetails?, journeyId: String? = null, quantity: Int = 1): CommercialRequest = repository.transaction {
        val actor = actor(UserRole.CLIENT)
        val journey = sourceJourney(actor, journeyId, requireViewedProduct = productId)
        val product = repository.products().firstOrNull { it.id == productId && it.active }
        require(product != null) { "La pieza ya no está disponible." }
        require(product.price == displayedPrice) { "El precio cambió. Vuelve al detalle de la pieza." }
        val total = FormValidation.requestTotal(product.price, quantity)
        fieldRequire(comment.trim().length <= 500, "comment", "Las observaciones admiten hasta 500 caracteres.")
        if (intention == RequestIntention.QUOTATION) {
            requireNotNull(details) { "Completa los datos de personalización." }
            FormValidation.quotationPiece(details.widthCm, details.lengthCm, details.material, details.colors)
            fieldRequire(details.event.trim().length in 1..100, "event", "Escribe el tipo de evento (máximo 100 caracteres).")
            val today = Calendar.getInstance().apply { timeInMillis = now(); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            fieldRequire(formDay(details.requiredOn, "date") >= today, "date", "La fecha deseada no puede ser anterior a hoy.")
        }
        val request = repository.saveRequest(CommercialRequest(0, now(), "CLIENTE-${actor.id}", total, RequestStatus.PENDING,
            product.id, intention, comment.trim().takeIf { it.isNotEmpty() }, product.campaignCategory, actor.id, if (intention == RequestIntention.QUOTATION) details?.summary() else null,
            campaignId = journey?.campaignId, sourceCollectionId = journey?.collectionId, journeyId = journey?.id, quantity = quantity))
        trace(actor, ExplorationKind.REQUEST_CREATED, journey, productId = product.id, requestId = request.id)
        repository.profiles().filter { it.role == UserRole.ADMIN }.forEach { notice(it.id, "Nueva solicitud #${request.id}", product.name, "request/${request.id}", "request/${request.id}") }
        request
    }

    suspend fun agreeTotal(requestId: Long, amount: Long) = repository.transaction {
        actor(UserRole.ADMIN)
        fieldRequire(amount in 0..9_999_999_999L, "agreedTotal", "Escribe un importe entero entre 0 y 9.999.999.999 CLP.")
        val request = requireNotNull(repository.request(requestId)) { "La solicitud no existe." }
        require(request.status != RequestStatus.CANCELLED) { "La solicitud está cancelada. No se puede modificar su importe." }
        if (request.agreedTotal != amount) {
            val date = now()
            repository.updateAgreement(request.id, amount, date)
            repository.recordExploration(ExplorationEvent(UUID.randomUUID().toString(), request.ownerId, ExplorationKind.AMOUNT_AGREED, date,
                request.journeyId, request.campaignId, request.sourceCollectionId, request.productId, request.id, amount))
            notice(request.ownerId, "Importe de solicitud #${request.id}", "El taller registró un importe acordado de $$amount CLP. Consulta tu solicitud.", "request/${request.id}", "agreement/${request.id}/${UUID.randomUUID()}")
        }
    }
    suspend fun updateStatus(id: Long, status: RequestStatus) = repository.transaction {
        val actor = actor(UserRole.ADMIN)
        val request = requireNotNull(repository.request(id)) { "La solicitud no existe." }
        require(request.status != RequestStatus.CANCELLED || status == RequestStatus.CANCELLED) { "La solicitud está cancelada y no puede reactivarse." }
        if (request.status != status) {
            if (status == RequestStatus.CANCELLED) validateCancellation(request)
            if (status == RequestStatus.DELIVERED && request.intention != RequestIntention.QUOTATION && !repository.hasStockMovementForRequest(id)) {
                val product = requireNotNull(repository.products().firstOrNull { it.id == request.productId }) { "El producto de la solicitud no existe." }
                require(repository.adjustStock(product.id, -request.quantity)) { "Stock insuficiente para entregar esta solicitud. Cantidad requerida: ${request.quantity}. Registra una entrada en bodega antes de continuar." }
                repository.addStockMovement(StockMovement(productId = product.id, quantityDelta = -request.quantity,
                    stockAfter = product.stock - request.quantity, type = StockMovementType.REQUEST_SALE, date = now(), actorId = actor.id,
                    note = "Entrega de solicitud #$id", requestId = id))
            }
            repository.updateStatus(id, status)
            notice(request.ownerId, "Solicitud #$id actualizada", "La atención de tu solicitud cambió. Consulta su nuevo estado.", "request/$id", "status/$id/${UUID.randomUUID()}")
        }
    }
    suspend fun resolveQr(raw: String, sessionKey: String): String = repository.transaction {
        val actor = actor()
        val match = Regex("louaraya://(product|collection)/([1-9][0-9]*)").matchEntire(raw.trim())
        require(match != null) { "El QR no corresponde a Lou Araya." }
        val id = match.groupValues[2].toLongOrNull()
        require(id != null) { "El identificador del QR no es válido." }
        val type = match.groupValues[1]
        require(if (type == "product") repository.products().any { it.id == id && it.active } else repository.collections().any { it.id == id }) { "El código no existe o ya no está disponible." }
        val destination = "$type/$id"
        repository.recordScan(ScanRecord(userId = actor.id, destination = destination, date = now(), sessionKey = sessionKey))
        destination
    }
    suspend fun openNotice(id: Long): String = repository.transaction {
        val actor = actor()
        val notice = requireNotNull(repository.notices(actor.id).firstOrNull { it.id == id }) { "Este aviso pertenece a otra sesión o ya no existe." }
        if (notice.destination.startsWith("request/")) {
            val request = repository.request(notice.destination.substringAfter('/').toLong())
            require(request != null && (actor.role == UserRole.ADMIN || request.ownerId == actor.id)) { "No tienes acceso a esta solicitud." }
        }
        if (notice.destination.startsWith("campaign/")) require(repository.campaigns().any {
            it.id == notice.destination.substringAfter('/').toLongOrNull() && it.published
        }) { "La campaña ya no está disponible." }
        repository.markNoticeRead(actor.id, id)
        notice.destination
    }
    suspend fun markNoticesDelivered(ids: List<Long>) = repository.transaction {
        val actor = actor()
        ids.forEach { repository.markNoticeDelivered(actor.id, it) }
    }
    suspend fun requireAdministrator() { actor(UserRole.ADMIN) }
    suspend fun clearRequests(ids: List<Long>, expectedUserId: Long) = repository.transaction {
        val actor = actor(UserRole.ADMIN)
        require(actor.id == expectedUserId) { "La sesión cambió. Abre de nuevo tus solicitudes." }
        val accessible = repository.requests(if (actor.role == UserRole.ADMIN) null else actor.id).map { it.id }.toSet()
        require(ids.all { it in accessible }) { "Solo puedes limpiar las solicitudes de tu perfil." }
        repository.dismissRequests(actor.id, ids)
    }
    suspend fun cancelRequest(id: Long, expectedUserId: Long) = repository.transaction {
        val actor = actor(UserRole.CLIENT)
        require(actor.id == expectedUserId) { "La sesión cambió. Abre de nuevo tu solicitud." }
        val request = requireNotNull(repository.request(id)) { "La solicitud no existe." }
        require(request.ownerId == actor.id) { "Solo puedes cancelar tus propias solicitudes." }
        if (request.status != RequestStatus.CANCELLED) {
            validateCancellation(request)
            repository.updateStatus(id, RequestStatus.CANCELLED)
            repository.recordExploration(ExplorationEvent(UUID.randomUUID().toString(), actor.id, ExplorationKind.REQUEST_CANCELLED, now(),
                request.journeyId, request.campaignId, request.sourceCollectionId, request.productId, request.id))
            repository.profiles().filter { it.role == UserRole.ADMIN }.forEach {
                notice(it.id, "Solicitud #$id cancelada", "El cliente canceló su solicitud. No se descontaron existencias.", "request/$id", "cancel/$id")
            }
        }
    }
    private suspend fun validateCancellation(request: CommercialRequest) {
        require(request.canBeCancelled && !repository.hasStockMovementForRequest(request.id)) { "Una solicitud entregada no se puede cancelar." }
    }
    suspend fun clearNotices(ids: List<Long>, expectedUserId: Long) = repository.transaction {
        val actor = actor()
        require(actor.id == expectedUserId) { "La sesión cambió. Abre de nuevo tus notificaciones." }
        val accessible = repository.notices(actor.id).map { it.id }.toSet()
        require(ids.all { it in accessible }) { "Los avisos cambiaron o pertenecen a otro perfil. Actualiza la lista." }
        repository.dismissNotices(actor.id, ids)
    }
    private suspend fun actor(role: UserRole? = null): UserProfile {
        val actor = requireNotNull(repository.session()) { "Inicia sesión para continuar." }
        require(role == null || actor.role == role) { "Tu perfil no tiene permiso para esta operación." }
        return actor
    }
    private suspend fun notice(userId: Long, title: String, body: String, destination: String, key: String) =
        repository.addNotice(LocalNotice(userId = userId, title = title, body = body, destination = destination, date = now(), eventKey = key))

    private suspend fun sourceJourney(actor: UserProfile, id: String?, collectionId: Long? = null, productId: Long? = null, requireViewedProduct: Long? = null): ExplorationJourney? {
        if (id == null) return null
        val journey = repository.journey(id)
        require(journey != null && journey.userId == actor.id) { "El recorrido pertenece a otra sesión o ya no existe. Abre la campaña de nuevo." }
        require(collectionId == null || collectionId == journey.collectionId) { "La colección no pertenece a este recorrido." }
        if (productId != null) require(repository.collections().any { it.id == journey.collectionId && productId in it.productIds }) { "La pieza no pertenece a esta colección." }
        if (requireViewedProduct != null) require(repository.explorationEvents(actor.id).any {
            it.journeyId == id && it.productId == requireViewedProduct && it.kind == ExplorationKind.PRODUCT_VIEW
        }) { "Abre primero la pieza desde la campaña para conservar su origen." }
        return journey
    }

    private suspend fun trace(actor: UserProfile, kind: ExplorationKind, journey: ExplorationJourney?, collectionId: Long? = null, productId: Long? = null, requestId: Long? = null) {
        repository.recordExploration(ExplorationEvent(UUID.randomUUID().toString(), actor.id, kind, now(), journey?.id,
            journey?.campaignId, collectionId ?: journey?.collectionId, productId, requestId))
    }
    private fun validateContact(contact: String) {
        fieldRequire(contact.trim().length <= 100, "contact", "El contacto no puede superar los 100 caracteres.")
    }
    private fun normalizeAlias(alias: String) = alias.trim().lowercase(Locale.ROOT)
    private fun formDay(value: String, field: String): Long = try { day(value) }
    catch (error: IllegalArgumentException) { throw FormValidationException(mapOf(field to (error.message ?: "Revisa la fecha."))) }
    private fun day(value: String): Long {
        require(value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "Usa una fecha AAAA-MM-DD." }
        return requireNotNull(runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(value)?.time }.getOrNull()) { "La fecha no es válida." }
    }
}
