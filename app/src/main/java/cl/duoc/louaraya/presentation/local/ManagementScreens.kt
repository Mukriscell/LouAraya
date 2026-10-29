package cl.duoc.louaraya.presentation.local

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.usecase.FormValidation
import cl.duoc.louaraya.presentation.common.ProductImage
import cl.duoc.louaraya.presentation.common.displayName
import cl.duoc.louaraya.presentation.common.formatCm

@Composable
internal fun ProductManagementScreen(snapshot: LocalSnapshot, storage: ProductImageStorage, open: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Page {
        Intro("Catálogo del taller", "Las piezas del taller.")
        Action("Crear producto") { open("edit-product/0") }
        Field("Buscar productos", query, { query = it }, search = true)
        val filtered = snapshot.products.filter { it.name.contains(query, true) }
        if (filtered.isEmpty()) StateContent("Sin piezas por aquí.", "Crea tu primera pieza o prueba con otro nombre.", action = if (query.isNotEmpty()) "Limpiar búsqueda" else null) { query = "" }
        filtered.forEach { product -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductImage(product.id, product.imageReference, storage, Modifier.width(80.dp).height(100.dp).clip(RoundedCornerShape(12.dp)).clickable { open("product/${product.id}") }, product.name)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(product.name, style = MaterialTheme.typography.labelLarge)
                    Text("${priceLabel(product.price)} · ${if (product.active) "Activo" else "Desactivado"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Text("Bodega: ${product.stock} ${if (product.stock == 1) "unidad" else "unidades"}", style = MaterialTheme.typography.bodySmall, color = if (product.stock == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                    TextButton(onClick = { open("edit-product/${product.id}") }) { Text("Editar ficha") }
                    TextButton(onClick = { open("stock/${product.id}") }, enabled = !LocalBusy.current) { Text("Gestionar stock") }
                }
                DesignIconButton(R.drawable.la_camera, "Editar fotografía: ${product.name}") { open("image/${product.id}") }
            }
        } }
    }
}

@Composable
internal fun ProductEditorScreen(product: Product?, vm: LocalAppViewModel, done: () -> Unit, open: (String) -> Unit, storage: ProductImageStorage? = null) {
    var name by rememberSaveable { mutableStateOf(product?.name.orEmpty()) }; var description by rememberSaveable { mutableStateOf(product?.description.orEmpty()) }; var width by rememberSaveable { mutableStateOf(product?.widthCm?.let(::formatCm).orEmpty()) }; var length by rememberSaveable { mutableStateOf(product?.lengthCm?.let(::formatCm).orEmpty()) }; var material by rememberSaveable { mutableStateOf(product?.material.orEmpty()) }; var price by rememberSaveable { mutableStateOf(product?.price?.toString().orEmpty()) }; var category by rememberSaveable { mutableStateOf(product?.campaignCategory?.name ?: CampaignCategory.INTERIOR_DECORATION.name) }; var availability by rememberSaveable { mutableStateOf(product?.availability ?: "AVAILABLE") }; var active by rememberSaveable { mutableStateOf(product?.active ?: true) }
    val state by vm.uiState.collectAsState()
    Page(maxWidth = 720.dp, footer = { Action(if (state.fieldErrors.isEmpty()) "Guardar producto" else "Corregir producto") { vm.execute(message = "Producto guardado", onSuccess = done) {
        saveProduct(Product(product?.id ?: 0, name, FormValidation.dimension(width), FormValidation.dimension(length), material, FormValidation.productPrice(price), CampaignCategory.valueOf(category), product?.imageReference, description, availability, active))
    } } }) {
        Intro(if (state.fieldErrors.isEmpty()) "Una pieza con tu sello" else "Antes de publicar", if (state.fieldErrors.isNotEmpty()) "Revisa los datos\nde tu pieza." else product?.name ?: "Un nuevo diseño.")
        if (product != null) {
            ProductImage(product.id, product.imageReference, storage, Modifier.fillMaxWidth().height(144.dp).clip(RoundedCornerShape(16.dp)), product.name)
            SecondaryAction("Cambiar fotografía") { open("image/${product.id}") }
            DesignRow("Bodega", "${product.stock} unidades", "Gestionar stock") { open("stock/${product.id}") }
        }
        FormField(vm, "name", "Nombre del diseño", name, { name = it }); FormField(vm, "description", "Descripción ornamental", description, { description = it }, multiline = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Box(Modifier.weight(1f)) { FormField(vm, "width", "Ancho · cm", width, { width = it }, number = true) }; Box(Modifier.weight(1f)) { FormField(vm, "length", "Largo · cm", length, { length = it }, number = true) } }
        FormField(vm, "material", "Material", material, { material = it }); FormField(vm, "price", "Precio de la pieza · CLP", price, { price = it }, number = true, hint = "Importe entero en pesos, sin puntos ni comas.")
        Text("Categoría", style = MaterialTheme.typography.labelLarge)
        Choices(designCategories.map { it.name }, category, { CampaignCategory.valueOf(it).displayName }) { category = it }
        Choices(listOf("AVAILABLE", "MADE_TO_ORDER"), availability, { if (it == "AVAILABLE") "Disponible" else "A pedido" }) { availability = it }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Publicar en catálogo", Modifier.weight(1f)); Switch(active, { active = it }, enabled = !LocalBusy.current) }
        if (product != null) {
            DesignRow("Fotografía de la pieza", "Cámara o galería", "Editar fotografía", R.drawable.la_camera) { open("image/${product.id}") }
            Note("La desactivación conserva la fotografía y las solicitudes anteriores.")
        }
    }
}

@Composable
internal fun CampaignManagementScreen(snapshot: LocalSnapshot, open: (String) -> Unit) {
    Page {
        if (snapshot.collections.isEmpty()) Intro("Agrupa tus ideas", "Tu primera colección\nestá por comenzar.", "Reúne piezas del catálogo para contar una historia en común.")
        else Intro("Historias en común", "Historias para compartir.")
        Text("Colecciones", style = MaterialTheme.typography.titleLarge)
        Action("Crear colección") { open("edit-collection/0") }
        snapshot.collections.forEach { collection ->
            DesignRow(collection.name, "${collection.productIds.size} piezas · ${collection.description}", "Editar colección") { open("edit-collection/${collection.id}") }
            SecondaryAction("Ver piezas de la colección") { open("collection/${collection.id}") }
        }
        Text("Campañas", style = MaterialTheme.typography.titleLarge)
        Action("Crear campaña", enabled = snapshot.collections.isNotEmpty()) { open("edit-campaign/0") }
        if (snapshot.collections.isEmpty()) Note("Crea una colección antes de preparar una campaña.")
        snapshot.campaigns.forEach { campaign ->
            DesignRow(campaign.name, "${campaign.startsOn} — ${campaign.endsOn}", "${if (campaign.published) "Publicada" else "Borrador"} · Editar campaña", R.drawable.la_megaphone) { open("edit-campaign/${campaign.id}") }
            Text("Destinatarios: ${campaign.targetCategory?.displayName ?: "Todos los clientes suscritos"}", style = MaterialTheme.typography.bodySmall)
            SecondaryAction("Ver resultados: ${campaign.name}") { open("campaign-results/${campaign.id}") }
        }
    }
}

@Composable
internal fun CollectionEditor(collection: Collection?, products: List<Product>, vm: LocalAppViewModel, done: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(collection?.name.orEmpty()) }; var description by rememberSaveable { mutableStateOf(collection?.description.orEmpty()) }; var selected by rememberSaveable { mutableStateOf(collection?.productIds.orEmpty()) }
    Page(maxWidth = 720.dp, footer = { Action("Guardar colección") { vm.execute(message = "Colección guardada", onSuccess = done) { saveCollection(Collection(collection?.id ?: 0, name, description, selected)) } } }) {
        Intro("Una historia compartida", "Reúne tus piezas.")
        FormField(vm, "name", "Nombre de colección", name, { name = it }); FormField(vm, "description", "Descripción", description, { description = it }, multiline = true)
        Text("Piezas de la colección", style = MaterialTheme.typography.labelLarge)
        if (products.isEmpty()) Note("Crea una pieza en Productos antes de añadirla a esta colección.")
        products.forEach { product -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(product.id in selected, { checked -> selected = if (checked) selected + product.id else selected - product.id }, enabled = !LocalBusy.current)
            Text(product.name + if (product.active) "" else " (desactivada)", Modifier.weight(1f))
        } }
        SecondaryAction("Cancelar", onClick = done)
    }
}

@Composable
internal fun CampaignEditor(campaign: Campaign?, collections: List<Collection>, vm: LocalAppViewModel, done: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(campaign?.name.orEmpty()) }; var collectionId by rememberSaveable { mutableLongStateOf(campaign?.collectionId ?: collections.firstOrNull()?.id ?: 0) }; var start by rememberSaveable { mutableStateOf(campaign?.startsOn.orEmpty()) }; var end by rememberSaveable { mutableStateOf(campaign?.endsOn.orEmpty()) }; var published by rememberSaveable { mutableStateOf(campaign?.published ?: false) }
    var audience by rememberSaveable { mutableStateOf(campaign?.targetCategory?.name ?: "ALL") }
    val state by vm.uiState.collectAsState()
    Page(maxWidth = 720.dp, footer = { Action(if (state.fieldErrors.isEmpty()) "Guardar campaña" else "Corregir campaña", enabled = collections.isNotEmpty()) { vm.execute(message = "Campaña guardada", onSuccess = done) { saveCampaign(Campaign(campaign?.id ?: 0, name, collectionId, start, end, published, audience.takeUnless { it == "ALL" }?.let(CampaignCategory::valueOf))) } } }) {
        Intro(if (state.fieldErrors.isEmpty()) "Lleva tu idea más lejos" else "Una fecha para cada historia", if (state.fieldErrors.isEmpty()) "Una nueva temporada." else "Revisa las fechas.")
        FormField(vm, "name", "Nombre de campaña", name, { name = it }); Text("Colección asociada", style = MaterialTheme.typography.labelLarge)
        if (collections.isEmpty()) Note("Crea una colección antes de preparar una campaña.")
        else Choices(collections.map { it.id }, collectionId, { id -> collections.first { it.id == id }.name }) { collectionId = it }
        FormField(vm, "start", "Inicio · AAAA-MM-DD", start, { start = it; vm.clearFieldError("end") }); FormField(vm, "end", "Fin · AAAA-MM-DD", end, { end = it })
        Text("Destinatarios del aviso", style = MaterialTheme.typography.labelLarge)
        Choices(listOf("ALL") + designCategories.map { it.name }, audience, { if (it == "ALL") "Todos" else CampaignCategory.valueOf(it).displayName }) { audience = it }
        Note("Una categoría dirige el aviso a clientes con esa preferencia o favoritos relacionados. También incluye a quienes guardaron esta colección. Se respetan sus preferencias de avisos.")
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Publicar campaña", Modifier.weight(1f)); Switch(published, { published = it }, enabled = !LocalBusy.current) }
        Note("Al publicar se genera un aviso para los clientes suscritos, según los destinatarios elegidos.")
        SecondaryAction("Cancelar", onClick = done)
    }
}
