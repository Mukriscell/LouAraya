package cl.duoc.louaraya.presentation.local

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.usecase.FormValidation
import cl.duoc.louaraya.presentation.common.ProductImage
import cl.duoc.louaraya.presentation.common.displayName
import cl.duoc.louaraya.presentation.common.displayClientReference
import cl.duoc.louaraya.presentation.common.formatCm
import cl.duoc.louaraya.presentation.common.formatRequestDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val designCategories = listOf(CampaignCategory.INTERIOR_DECORATION, CampaignCategory.WEDDINGS, CampaignCategory.FESTIVALS, CampaignCategory.TRAVEL)


private val AtelierArch = GenericShape { size, _ ->
    val arch = minOf(size.width / 2f, size.height * .65f)
    val r = size.width * .035f
    moveTo(0f, arch)
    cubicTo(0f, arch * .448f, size.width * .224f, 0f, size.width / 2f, 0f)
    cubicTo(size.width * .776f, 0f, size.width, arch * .448f, size.width, arch)
    lineTo(size.width, size.height - r)
    quadraticTo(size.width, size.height, size.width - r, size.height)
    lineTo(r, size.height)
    quadraticTo(0f, size.height, 0f, size.height - r)
    close()
}

@Composable
private fun PieceSummary(product: Product, storage: ProductImageStorage? = null, price: Long = product.price, open: (() -> Unit)? = null) {
    Surface(Modifier.fillMaxWidth().then(if (open != null) Modifier.clickable(onClick = open) else Modifier), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(product.id, product.imageReference, storage, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)), product.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text("${product.material} · ${formatCm(product.widthCm)} × ${formatCm(product.lengthCm)} cm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Text(priceLabel(price), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
internal fun FormField(vm: LocalAppViewModel, key: String, label: String, value: String, change: (String) -> Unit, number: Boolean = false, password: Boolean = false, multiline: Boolean = false, hint: String? = null, labelBadge: String? = null, background: Color? = null) {
    val state by vm.uiState.collectAsState()
    Field(label, value, { vm.clearFieldError(key); change(it) }, number, password, multiline, state.fieldErrors[key], hint, labelBadge = labelBadge, background = background)
}

@Composable
internal fun DashboardScreen(snapshot: LocalSnapshot, storage: ProductImageStorage, open: (String) -> Unit) {
    val actor = requireNotNull(snapshot.profile)
    val visibleRequests = snapshot.requests.filterNot { it.id in snapshot.hiddenRequestIds }
    val admin = actor.role == UserRole.ADMIN
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
    Page {
        if (admin) {
            Intro("Tu taller · Lou Araya", "Hoy, en el taller.", "Todo lo importante, listo para continuar.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(visibleRequests.count { it.status == RequestStatus.PENDING } to "Por revisar", snapshot.products.count { it.active } to "Piezas activas").forEachIndexed { index, (count, label) ->
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(16.dp), color = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AnimatedCount(count, style = MaterialTheme.typography.headlineLarge, color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, minimumDigits = 2)
                            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Primero, tus solicitudes", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { open("requests") }) { Text("Ver todas", style = MaterialTheme.typography.labelMedium) }
            }
            val pending = visibleRequests.firstOrNull { it.status == RequestStatus.PENDING }
            if (pending != null) Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("SOLICITUD #${pending.id}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        StatusBadge(pending.status)
                    }
                    Text(snapshot.products.firstOrNull { it.id == pending.productId }?.name ?: "Pieza #${pending.productId}", style = MaterialTheme.typography.titleMedium)
                    Text("${displayClientReference(pending.anonymizedClient)} · ${pending.intention.displayName()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Action("Revisar solicitud") { open("request/${pending.id}") }
                }
            } else DesignRow("Solicitudes por atender", "No hay solicitudes pendientes", "Revisar historial", R.drawable.la_clipboard_list) { open("requests") }
            Text("Haz crecer tu colección", style = MaterialTheme.typography.titleLarge)
            DesignRow("Productos del taller", "Crea, edita y organiza tus diseños") { open("products") }
            DesignRow("Colecciones y campañas", "Reúne piezas y prepara novedades", icon = R.drawable.la_megaphone) { open("campaigns") }
            SecondaryAction("Explorar catálogo") { open("catalog") }
        } else {
            Text("MACRAMÉ · HECHO A MANO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("Texturas que\nhacen hogar.", style = MaterialTheme.typography.headlineLarge)
            val featured = snapshot.products.firstOrNull { it.active && it.id == 1L } ?: snapshot.products.firstOrNull { it.active }
            if (featured != null) {
                val heroSource = remember { MutableInteractionSource() }
                Box(Modifier.fillMaxWidth().height(250.dp).atelierPress(heroSource, !LocalBusy.current, .985f).clip(AtelierArch).clickable(interactionSource = heroSource, indication = ripple(), enabled = !LocalBusy.current) { open("product/${featured.id}") }) {
                    ProductImage(featured.id, featured.imageReference, storage, Modifier.fillMaxSize(), featured.name)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .2f)))))
                    Text("LA PIEZA DEL MES", Modifier.align(Alignment.BottomStart).padding(12.dp).background(MaterialTheme.colorScheme.background).padding(10.dp, 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(featured.name, style = MaterialTheme.typography.titleMedium)
                        Text("${featured.material}, luz y una pared con historia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text(priceLabel(featured.price), style = MaterialTheme.typography.titleLarge)
                }
            }
            Action("Explorar piezas") { open("catalog") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Triple("Escanear QR", R.drawable.la_scan_line, "qr"), Triple("Ver ferias", R.drawable.la_map_pin, "map")).forEach { (label, icon, destination) ->
                FilledTonalButton(onClick = { open(destination) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(12.dp)) {
                    DesignIcon(icon, primary = true); Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        if (!admin) {
            Text("Una pieza, muchos espacios", style = MaterialTheme.typography.titleLarge)
            snapshot.products.filter { it.active && it.id != 1L }.take(2).forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { open("product/${p.id}") }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProductImage(p.id, p.imageReference, storage, Modifier.size(88.dp).clip(RoundedCornerShape(16.dp)), p.name)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(p.name, style = MaterialTheme.typography.titleMedium); Text(p.campaignCategory.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                    DesignIcon(R.drawable.la_arrow_up_right, primary = true)
                }
            }
        }
        snapshot.campaigns.filter { it.published && today >= it.startsOn && today <= it.endsOn }.forEach { c -> DesignRow(c.name, "Hasta ${c.endsOn}", "Descubrir colección", R.drawable.la_megaphone) { open("campaign/${c.id}") } }
    }
}


@Composable
internal fun CatalogScreen(snapshot: LocalSnapshot, storage: ProductImageStorage, vm: LocalAppViewModel, open: (String) -> Unit, favoritesOnly: Boolean = false, collection: Collection? = null, journeyId: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }; var category by rememberSaveable { mutableStateOf(if (collection == null) snapshot.profile?.preferredCategory else null) }
    val base = snapshot.products.filter { (it.active || favoritesOnly) && (!favoritesOnly || it.id in snapshot.favoriteIds) && (collection == null || it.id in collection.productIds) }
    val products = base.filter { (category == null || it.campaignCategory.name == category) && it.name.contains(query.trim(), true) }
    val state by vm.uiState.collectAsState()
    val collections = if (collection != null) emptyList() else snapshot.collections.filter { !favoritesOnly || it.id in snapshot.favoriteCollectionIds }
    Page(maxWidth = 1120.dp) {
        Intro(if (collection != null) "Colección" else if (favoritesOnly) "Tu selección" else "La colección", collection?.name ?: if (favoritesOnly) "Lo que te inspira." else "Encuentra tu pieza.", collection?.description?.takeIf { it.isNotBlank() })
        if (collection != null && snapshot.profile?.role == UserRole.CLIENT) SecondaryAction(if (collection.id in snapshot.favoriteCollectionIds) "Quitar colección de favoritos" else "Guardar colección") {
            vm.execute { setFavoriteCollection(collection.id, collection.id !in snapshot.favoriteCollectionIds, journeyId) }
        }
        if (state.readError != null) StateContent("No pudimos cargar\nlas piezas.", "Inténtalo de nuevo para continuar explorando.", action = "Reintentar", onAction = vm::refresh)
        else if (state.busy && base.isEmpty()) StateContent("Preparando tus piezas.", "Estamos cargando el catálogo.", loading = true)
        else if (base.isEmpty() && favoritesOnly && collections.isNotEmpty()) Text("Aún no guardas piezas individuales. Tus colecciones están más abajo.")
        else if (base.isEmpty()) StateContent(if (favoritesOnly) "Tu próxima favorita\nte espera." else "Pronto habrá\nnuevas historias.", if (favoritesOnly) "Aún no guardas piezas. Toca el corazón de un diseño para encontrarlo aquí." else "No hay productos disponibles.", if (favoritesOnly) R.drawable.la_heart else R.drawable.la_leaf, action = if (favoritesOnly) "Explorar catálogo" else "Volver al inicio") { open(if (favoritesOnly) "catalog" else "home") }
        else {
            Field("Buscar una pieza", query, { query = it }, search = true)
            Choices(listOf("ALL") + designCategories.map { it.name }, category ?: "ALL", { value -> CampaignCategory.entries.firstOrNull { it.name == value }?.displayName ?: "Todas" }) { category = it.takeUnless { it == "ALL" } }
            if (products.isEmpty()) StateContent("No encontramos\ncoincidencias.", "No hay piezas con estos filtros. Prueba con otro nombre o vuelve a todas las categorías.", R.drawable.la_search, action = "Limpiar búsqueda") { query = ""; category = null }
            else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("${products.size} ${if (products.size == 1) "pieza" else "piezas"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary); Text(if (favoritesOnly) "Guardadas en tu perfil" else "Hecho a mano", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                BoxWithConstraints {
                    val columns = if (maxWidth >= 712.dp) 3 else if (maxWidth < 280.dp) 1 else 2
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        products.chunked(columns).forEachIndexed { index, row -> key(row.map { it.id }) {
                            val content: @Composable () -> Unit = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { p -> key(p.id) { Box(Modifier.weight(1f)) { ProductCard(p, snapshot, storage, if (snapshot.profile?.role == UserRole.CLIENT) ({ vm.execute { setFavorite(p.id, p.id !in snapshot.favoriteIds, journeyId) } }) else null) { open("product/${p.id}") } } } }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            } }
                            if (index < 3) MotionItem(index, content) else content()
                        } }
                    }
                }
                if (query.isNotBlank() || category != null) SecondaryAction("Limpiar búsqueda") { query = ""; category = null }
            }
        }
        if (collections.isNotEmpty()) {
            Text(if (favoritesOnly) "Tus colecciones favoritas" else "Colecciones", style = MaterialTheme.typography.titleLarge)
            collections.forEach { item ->
                DesignRow(item.name, "${item.productIds.size} piezas · ${item.description}", "Abrir colección") { open("collection/${item.id}") }
                if (snapshot.profile?.role == UserRole.CLIENT) SecondaryAction((if (item.id in snapshot.favoriteCollectionIds) "Quitar colección: " else "Guardar colección: ") + item.name) {
                    vm.execute { setFavoriteCollection(item.id, item.id !in snapshot.favoriteCollectionIds) }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, snapshot: LocalSnapshot, storage: ProductImageStorage, favorite: (() -> Unit)?, open: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Surface(onClick = open, enabled = !LocalBusy.current, interactionSource = source,
        modifier = Modifier.fillMaxWidth().atelierPress(source, !LocalBusy.current, .985f),
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))) {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(.96f).clip(RoundedCornerShape(14.dp))) {
            ProductImage(product.id, product.imageReference, storage, Modifier.fillMaxSize())
            if (favorite != null) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { DesignIconButton(R.drawable.la_heart, if (product.id in snapshot.favoriteIds) "Quitar favorito: ${product.name}" else "Guardar favorito: ${product.name}", selected = product.id in snapshot.favoriteIds, onClick = favorite) }
        }
        Text(product.name, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${product.material} · ${formatCm(product.widthCm)} × ${formatCm(product.lengthCm)} cm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text(priceLabel(product.price), style = MaterialTheme.typography.titleLarge.copy(fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
        if (!product.active) Text("No disponible", style = MaterialTheme.typography.bodySmall)
    }
    }
}

@Composable
internal fun ProductScreen(product: Product, snapshot: LocalSnapshot, storage: ProductImageStorage, vm: LocalAppViewModel, open: (String) -> Unit, journeyId: String? = null) {
    val client = snapshot.profile?.role == UserRole.CLIENT
    val footer: @Composable () -> Unit = {
        if (client && product.active) { Action("Solicitar esta pieza") { open("quote/${product.id}") }; Text("Compra, reserva o cotización", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        else if (!client) { Action("Editar ficha") { open("edit-product/${product.id}") }; SecondaryAction("Fotografía de producto") { open("image/${product.id}") } }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp && maxWidth > maxHeight
        val photo: @Composable (Modifier) -> Unit = { modifier -> Box(modifier.clip(AtelierArch)) {
            ProductImage(product.id, product.imageReference, storage, Modifier.fillMaxSize(), product.name, ContentScale.Crop)
            if (client) Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { DesignIconButton(R.drawable.la_heart, if (product.id in snapshot.favoriteIds) "Quitar favorito" else "Guardar favorito", selected = product.id in snapshot.favoriteIds) { vm.execute { setFavorite(product.id, product.id !in snapshot.favoriteIds, journeyId) } } }
        } }
        if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) { photo(Modifier.weight(1f).fillMaxHeight().padding(vertical = 12.dp)); Box(Modifier.weight(1f)) { Page(footer = footer) { ProductFacts(product) } } }
        else Page(maxWidth = 760.dp, footer = footer) { photo(Modifier.fillMaxWidth().height(270.dp).padding(horizontal = 36.dp)); ProductFacts(product) }
    }
}

@Composable
private fun ProductFacts(product: Product) {
    Text(product.campaignCategory.displayName.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
    Text(product.name, style = MaterialTheme.typography.headlineMedium)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(priceLabel(product.price), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(24.dp)) {
            Text(if (!product.active) "Desactivada" else if (product.availability == "MADE_TO_ORDER") "A pedido" else "Disponible", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { DesignIcon(R.drawable.la_leaf, primary = true); Column { Text("Material", style = MaterialTheme.typography.bodySmall); Text(product.material) } }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { DesignIcon(R.drawable.la_ruler, primary = true); Column { Text("Medidas de la pieza", style = MaterialTheme.typography.bodySmall); Text("${formatCm(product.widthCm)} × ${formatCm(product.lengthCm)} cm") } }
    }
    if (product.description.isNotBlank()) Text(product.description, color = MaterialTheme.colorScheme.secondary)
    if (!product.active) Note("Pieza desactivada. Conservamos sus solicitudes anteriores.")
}

@Composable
internal fun QuoteScreen(product: Product, vm: LocalAppViewModel, open: (String) -> Unit, journeyId: String? = null, storage: ProductImageStorage? = null, onStepBackChanged: ((() -> Unit)?) -> Unit = {}) {
    val focus = LocalFocusManager.current
    var intention by rememberSaveable { mutableStateOf(RequestIntention.PURCHASE.name) }
    var step by rememberSaveable { mutableIntStateOf(0) }
    var event by rememberSaveable { mutableStateOf(product.campaignCategory.displayName) }
    var width by rememberSaveable { mutableStateOf(formatCm(product.widthCm)) }
    var length by rememberSaveable { mutableStateOf(formatCm(product.lengthCm)) }
    var material by rememberSaveable { mutableStateOf(product.material) }
    var colors by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var comment by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var savedId by rememberSaveable { mutableLongStateOf(0L) }
    val quote = intention == RequestIntention.QUOTATION.name
    val state by vm.uiState.collectAsState()
    val quantityNumber = quantity.trim().toIntOrNull()?.takeIf { it > 0 }
    val referenceTotal = remember(product.price, quantity) {
        quantityNumber?.let { runCatching { FormValidation.requestTotal(product.price, it) }.getOrNull() }
    }
    val savedRequest = state.snapshot.requests.firstOrNull { it.id == savedId }
    val canStepBack = quote && step == 1 && savedId == 0L
    val stepBack = { focus.clearFocus(); step = 0; vm.dismissError() }
    val currentStepBack by rememberUpdatedState(stepBack)
    DisposableEffect(canStepBack) {
        onStepBackChanged(if (canStepBack) ({ currentStepBack() }) else null)
        onDispose { onStepBackChanged(null) }
    }
    BackHandler(enabled = canStepBack, onBack = stepBack)
    val submit = {
        focus.clearFocus()
        var id = 0L
        vm.execute(onSuccess = { savedId = id }) {
            val details = if (quote) FormValidation.quotationDetails(event, width, length, material, colors, date) else null
            id = createRequest(product.id, RequestIntention.valueOf(intention), product.price, comment, details, journeyId,
                quantity = FormValidation.stockQuantity(quantity)).id
        }
    }

    LaunchedEffect(state.fieldErrors) {
        if (state.fieldErrors.keys.any { it in listOf("width", "length", "material", "colors", "quantity") }) step = 0
    }
    if (savedId != 0L) Page(maxWidth = 720.dp, footer = {
        Action("Ver solicitud") { open("request/$savedId") }
        SecondaryAction("Seguir descubriendo") { open("catalog") }
    }) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) { DesignIcon(R.drawable.la_check, primary = true) }
        Spacer(Modifier.height(12.dp))
        Text("SOLICITUD REGISTRADA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text("Solicitud registrada.", Modifier.widthIn(max = 250.dp), style = MaterialTheme.typography.headlineLarge)
        Text("Tu solicitud quedó registrada con estado Pendiente. Consulta sus actualizaciones en Mis solicitudes.", color = MaterialTheme.colorScheme.secondary)
        PieceSummary(product, storage, savedRequest?.unitPrice ?: product.price)
        DesignRow("Cantidad solicitada", quantityLabel(savedRequest?.quantity ?: quantityNumber ?: 1))
        DesignRow("Total de referencia", (savedRequest?.total ?: referenceTotal)?.let(::priceLabel) ?: "—")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Solicitud #$savedId", style = MaterialTheme.typography.titleMedium); StatusBadge(RequestStatus.PENDING) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        DesignRow("Total acordado", "Pendiente de definir")
        Text("Qué sucede ahora", style = MaterialTheme.typography.labelMedium)
        Text("1. Tu solicitud queda pendiente de revisión.\n2. Consulta el estado en Mis solicitudes.\n3. El total acordado aparecerá cuando se registre.", color = MaterialTheme.colorScheme.secondary)
    } else key(step, quote) {
        Page(maxWidth = 720.dp, footer = {
            if (quote && step == 0) {
                Action("Continuar con mi idea", loading = state.busy) {
                    focus.clearFocus()
                    vm.execute(onSuccess = { step = 1 }) {
                        FormValidation.requestTotal(product.price, FormValidation.stockQuantity(quantity))
                        FormValidation.quotationPiece(width, length, material, colors)
                    }
                }
                Text("Siguiente: ocasión, fecha y comentarios", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
            } else {
                Action(if (quote) "Registrar solicitud" else if (intention == RequestIntention.PURCHASE.name) "Solicitar compra" else "Solicitar reserva", loading = state.busy, onClick = submit)
                Text("Consulta el estado en Mis solicitudes", Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
            }
        }) {
            Intro(if (quote) "${if (step == 0) "01" else "02"} de 02 · ${if (step == 0) "Tu pieza" else "A tu medida"}" else "Una pieza para ti",
                if (quote) if (step == 0) "Primero, la idea." else "Ahora, tu historia." else if (intention == RequestIntention.PURCHASE.name) "Quiero esta pieza." else "Guardemos tu idea.",
                if (quote && step == 1) "Cuéntanos dónde imaginas esta pieza." else "Elige cómo te gustaría llevarla a tu espacio.")
            if (step == 0) {
                PieceSummary(product, storage) { open("product/${product.id}") }
                Choices(RequestIntention.entries.map { it.name }, intention, { RequestIntention.valueOf(it).displayName() }) { intention = it; vm.dismissError() }
                FormField(vm, "quantity", "Cantidad", quantity, { quantity = it }, number = true, hint = "Número de unidades que necesitas.")
                if (quote) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) { FormField(vm, "width", "Ancho · cm", width, { width = it }, number = true) }
                        Box(Modifier.weight(1f)) { FormField(vm, "length", "Largo · cm", length, { length = it }, number = true) }
                    }
                    FormField(vm, "material", "Material preferido", material, { material = it })
                    FormField(vm, "colors", "Colores", colors, { colors = it })
                    Note("Indica las medidas que deseas para esta cotización. Puedes consultar su estado en Mis solicitudes.")
                }
            }
            if (!quote || step == 1) {
                if (quote) {
                    FormField(vm, "event", "Tipo de evento", event, { event = it })
                    FormField(vm, "date", "Fecha deseada · AAAA-MM-DD", date, { date = it }, hint = "Fecha deseada para el evento")
                }
                FormField(vm, "comment", if (quote) "Observaciones · máximo 500 caracteres" else "Observaciones · opcional", comment, { comment = it }, multiline = true, hint = "${comment.length}/500")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (quote) "Precio unitario de referencia" else "Precio unitario", Modifier.weight(1f), color = MaterialTheme.colorScheme.secondary)
                    Text(priceLabel(product.price), style = MaterialTheme.typography.titleLarge)
                }
                DesignRow("Cantidad solicitada", quantityNumber?.let(::quantityLabel) ?: "Completa una cantidad válida")
                DesignRow("Total de referencia", referenceTotal?.let(::priceLabel) ?: "—")
                Note("Registrar la solicitud no confirma su aceptación ni realiza un pago.")
                if (quote) SecondaryAction("Volver a la pieza", onClick = stepBack)
            }
        }
    }
}


@Composable
internal fun RequestsScreen(snapshot: LocalSnapshot, vm: LocalAppViewModel, open: (String) -> Unit) {
    var selected by rememberSaveable { mutableStateOf("ALL") }; val admin = snapshot.profile?.role == UserRole.ADMIN
    val visibleRequests = snapshot.requests.filterNot { it.id in snapshot.hiddenRequestIds }
    val requests = visibleRequests.filter { selected == "ALL" || selected == it.status.name }
    Page {
        Intro(if (admin) "Atención con cuidado" else "Cada idea, un comienzo", if (admin) "Cada idea\nmerece respuesta." else "Sigamos tu idea.", if (!admin) "Consulta el estado de tus solicitudes." else null)
        if (admin) ClearListControl("Limpiar solicitudes", visibleRequests.map { it.id }, requireNotNull(snapshot.profile).id, requests = true, vm = vm)
        Choices(listOf("ALL") + RequestStatus.entries.map { it.name }, selected, { if (it == "ALL") "Todas" else RequestStatus.valueOf(it).displayName() }) { selected = it }
        if (requests.isEmpty()) StateContent(if (visibleRequests.isEmpty()) "Tu primera idea\nempieza aquí." else "Sin solicitudes\ncon este estado.", if (visibleRequests.isEmpty()) "No hay solicitudes en tu lista. Las nuevas solicitudes aparecerán aquí." else "Prueba con otro estado para seguir revisando.", R.drawable.la_clipboard_list, action = if (visibleRequests.isNotEmpty()) "Ver todas" else if (!admin) "Explorar catálogo" else null) { if (visibleRequests.isNotEmpty()) selected = "ALL" else open("catalog") }
        requests.forEach { r ->
            val piece = snapshot.products.firstOrNull { it.id == r.productId }
            Surface(Modifier.fillMaxWidth().clickable(enabled = !LocalBusy.current) { open("request/${r.id}") }, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Solicitud #${r.id} · ${r.intention.displayName()}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        StatusBadge(r.status)
                    }
                    Text(piece?.name ?: "Pieza #${r.productId}", style = MaterialTheme.typography.titleMedium)
                    Text("${quantityLabel(r.quantity)} · Total de referencia: ${priceLabel(r.total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Registrada el ${formatRequestDate(r.date)}" + if (admin) " · ${displayClientReference(r.anonymizedClient)}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(r.agreedTotal?.let { "Total acordado: ${priceLabel(it)}" } ?: "Total acordado: pendiente de definir", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun RequestScreen(request: CommercialRequest, snapshot: LocalSnapshot, storage: ProductImageStorage, vm: LocalAppViewModel, open: (String) -> Unit) {
    val admin = snapshot.profile?.role == UserRole.ADMIN; val product = snapshot.products.firstOrNull { it.id == request.productId }
    var agreement by rememberSaveable(request.id, request.agreedTotal) { mutableStateOf(request.agreedTotal?.toString().orEmpty()) }
    Page {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SOLICITUD #${request.id}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            StatusBadge(request.status)
        }
        Text(if (admin) "Una idea por tejer." else "Cada pieza tiene\nsu propio ritmo.", style = MaterialTheme.typography.headlineMedium)
        if (product != null) PieceSummary(product, storage, request.unitPrice)
        else DesignRow("Pieza #${request.productId}", "${priceLabel(request.unitPrice)} · Precio unitario al registrar")
        DesignRow("Cantidad solicitada", quantityLabel(request.quantity))
        DesignRow("Total de referencia", priceLabel(request.total))
        if (admin) Text("Cliente: ${displayClientReference(request.anonymizedClient)}", style = MaterialTheme.typography.bodySmall)
        if (!admin) {
            RequestProgress(request.status)
            if (request.status == RequestStatus.PENDING) Note("Tu solicitud está pendiente de revisión. Consulta las actualizaciones en Mis solicitudes.")
            if (request.canBeCancelled) CancelRequestControl(request, requireNotNull(snapshot.profile).id, vm)
        }
        if (request.agreedTotal != null) DesignRow("Total acordado", priceLabel(request.agreedTotal), request.agreedAt?.let(::formatRequestDate))
        else DesignRow("Total acordado", if (request.status == RequestStatus.CANCELLED) "Solicitud cancelada" else "Pendiente de definir")
        Text("Registrada el: ${formatRequestDate(request.date)} · ${request.intention.displayName()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        Text("Precio unitario al registrar: ${priceLabel(request.unitPrice)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        request.customization?.let { DesignRow("Personalización", it, icon = R.drawable.la_ruler) }; request.comment?.let { DesignRow("Observaciones", it, icon = R.drawable.la_clipboard_list) }
        if (product != null && (product.active || admin)) SecondaryAction("Ver la pieza") { open("product/${product.id}") } else Note("Esta pieza ya no se ofrece en el catálogo.")
        if (admin) {
            if (product != null) {
                val sale = snapshot.stockMovements.firstOrNull { it.requestId == request.id }
                DesignRow("Bodega de esta pieza", "${product.stock} unidades disponibles", "Gestionar stock") { open("stock/${product.id}") }
                Note(when {
                    request.intention == RequestIntention.QUOTATION -> "Esta cotización personalizada no descuenta stock de la pieza de referencia."
                    sale?.type == StockMovementType.LEGACY_SALE -> "Entrega anterior al control de stock. No genera un descuento retroactivo."
                    request.status == RequestStatus.CANCELLED -> "La solicitud fue cancelada. No se descontarán existencias."
                    sale != null -> "Las existencias de esta solicitud ya se descontaron de bodega. Cambiar el estado no vuelve a descontarlas ni devuelve existencias."
                    else -> "Al marcar Entregado se descontará de bodega la cantidad solicitada (${request.quantity}). Confirmar la solicitud no reserva existencias."
                })
            }
            if (request.status != RequestStatus.CANCELLED) {
                Text("Actualizar estado", style = MaterialTheme.typography.labelLarge)
                StatusChoices(request.status) { status -> vm.execute(message = "Estado actualizado") { updateStatus(request.id, status) } }
            }
            SecondaryAction("Volver a solicitudes") { open("requests") }
        }
        if (admin && request.status != RequestStatus.CANCELLED) {
            Text("Acuerdo comercial", style = MaterialTheme.typography.titleLarge)
            FormField(vm, "agreedTotal", "Importe acordado · CLP", agreement, { agreement = it }, number = true, hint = "Importe total para ${quantityLabel(request.quantity)}, sin puntos ni comas")
            Action("Guardar importe acordado") { vm.execute(message = "Importe acordado guardado") {
                agreeTotal(request.id, FormValidation.agreedTotal(agreement))
            } }
            Note("Registra el monto convenido con el cliente. Se conserva el precio de la pieza al registrar la solicitud.")
        }
        RequestTrace(request, snapshot)
    }
}

@Composable
internal fun ProfileScreen(profile: UserProfile, vm: LocalAppViewModel, open: (String) -> Unit, storage: ProductImageStorage) {
    val context = LocalContext.current
    var alias by rememberSaveable(profile.id) { mutableStateOf(profile.alias) }; var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }; var contact by rememberSaveable(profile.id) { mutableStateOf(profile.contact) }; var avatar by rememberSaveable(profile.id) { mutableIntStateOf(profile.avatar) }; var notices by rememberSaveable(profile.id) { mutableStateOf(profile.notificationsEnabled) }; var category by rememberSaveable(profile.id) { mutableStateOf(profile.preferredCategory ?: "ALL") }; var saved by rememberSaveable { mutableStateOf(false) }
    var usePreset by rememberSaveable(profile.id) { mutableStateOf(false) }
    var loadedPhoto by rememberSaveable(profile.id) { mutableStateOf(profile.avatarImageReference) }
    LaunchedEffect(profile.avatarImageReference) { if (loadedPhoto != profile.avatarImageReference) { usePreset = false; loadedPhoto = profile.avatarImageReference } }
    if (saved) Page(maxWidth = 600.dp) {
        StateContent("Perfil actualizado.", "Guardamos tus preferencias para la próxima vez.", R.drawable.la_circle_check)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProfileAvatar(profile, storage, Modifier.size(112.dp)) }
        DesignRow(profile.name, "@${profile.alias} · ${if (profile.avatarImageReference != null) "Foto personal" else "Avatar ${avatarNames[profile.avatar]}"}", if (profile.notificationsEnabled) "Avisos activados" else "Avisos desactivados"); Action("Volver a mi perfil") { saved = false }; SecondaryAction("Ir al inicio") { open("home") }
    } else Page(maxWidth = 600.dp, footer = { Action("Guardar perfil") { vm.execute(onSuccess = { saved = true }) { val previous = editProfile(name, avatar, contact, notices, category.takeUnless { it == "ALL" }, alias, usePreset); if (previous != null) runCatching { storage.delete(previous) } } } }) {
        Intro(if (profile.role == UserRole.ADMIN) "Perfil del taller" else "Hecho a tu medida", "Hola, ${profile.name}.", "@${profile.alias} · ${if (profile.role == UserRole.ADMIN) "Administrador" else "Cliente"}")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileAvatar(profile.copy(avatar = avatar, avatarImageReference = profile.avatarImageReference.takeUnless { usePreset }), storage, Modifier.size(88.dp))
            Column { Text("Tu imagen de perfil", style = MaterialTheme.typography.labelLarge); Text(if (profile.avatarImageReference != null && !usePreset) "Foto personal" else "Avatar ${avatarNames[avatar]}", style = MaterialTheme.typography.bodySmall) }
        }
        SecondaryAction(if (profile.avatarImageReference == null) "Tomar mi foto" else "Cambiar mi foto") { open("avatar-photo") }
        Text("O elige una ilustración", style = MaterialTheme.typography.labelLarge)
        AvatarChoices(if (profile.avatarImageReference == null || usePreset) avatar else -1) { avatar = it; usePreset = true }
        FormField(vm, "name", "Nombre", name, { name = it }); FormField(vm, "alias", "Nombre de Usuario", alias, { alias = it }); FormField(vm, "contact", "Contacto · opcional", contact, { contact = it }, hint = "Puedes dejarlo vacío o indicar un correo o teléfono.")
        if (profile.role == UserRole.CLIENT) { Text("Categoría preferida", style = MaterialTheme.typography.labelLarge); Choices(listOf("ALL") + designCategories.map { it.name }, category, { if (it == "ALL") "Todas" else CampaignCategory.valueOf(it).displayName }) { category = it } }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Recibir avisos", Modifier.weight(1f)); Switch(notices, { notices = it }, enabled = !LocalBusy.current) }
        NotificationPermissionButton(); PermissionSummary(); SecondaryAction("Cerrar sesión / cambiar usuario") { vm.execute(onSuccess = { AndroidSystemNotifications(context).clear() }) { signOut() } }
    }
}

@Composable
internal fun NoticesScreen(notices: List<LocalNotice>, userId: Long, vm: LocalAppViewModel, openNotice: (Long) -> Unit, open: (String) -> Unit) {
    Page {
        ClearListControl("Limpiar notificaciones", notices.map { it.id }, userId, requests = false, vm = vm, iconOnly = true)
        if (notices.isEmpty()) StateContent("Aún no tienes\navisos.", "Aquí encontrarás las novedades del taller y respuestas a tus solicitudes.", R.drawable.la_bell, action = "Volver al inicio") { open("home") }
        else { Intro("Novedades del taller", "Buenas noticias\npara tu espacio.", "Respuestas y novedades de tu perfil."); notices.forEach { n -> DesignRow((if (n.read) "" else "● ") + n.title, n.body, "${formatRequestDate(n.date)} · ${if (n.read) "Leído" else "Sin leer"}", R.drawable.la_bell) { openNotice(n.id) } } }
    }
}
