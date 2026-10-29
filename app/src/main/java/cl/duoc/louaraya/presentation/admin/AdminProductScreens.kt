package cl.duoc.louaraya.presentation.admin

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import cl.duoc.louaraya.presentation.local.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.presentation.catalog.CatalogUiState
import cl.duoc.louaraya.presentation.catalog.CatalogViewModel
import cl.duoc.louaraya.presentation.common.AdaptiveContentContainer
import cl.duoc.louaraya.presentation.common.LouArayaContentWidths
import cl.duoc.louaraya.presentation.common.LouArayaWindowSizeClass
import cl.duoc.louaraya.presentation.common.ProductImage
import cl.duoc.louaraya.presentation.common.formatClp
import cl.duoc.louaraya.presentation.common.formatCm
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProductCatalogScreen(
    viewModel: CatalogViewModel,
    imageStorage: ProductImageStorage,
    onBack: () -> Unit,
    onSelectProduct: (Long) -> Unit,
    windowSizeClass: LouArayaWindowSizeClass
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Administración") },
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Volver")
            } }
        )
    }) { padding ->
        AdaptiveContentContainer(
            windowSizeClass, LouArayaContentWidths.catalog, Modifier.padding(padding)
        ) {
            when (val uiState = state) {
                CatalogUiState.Loading -> CenteredAdmin {
                    CircularProgressIndicator(modifier = Modifier.testTag("admin-products-loading"))
                }
                CatalogUiState.Empty -> CenteredAdmin { Text("No hay productos disponibles.") }
                is CatalogUiState.Error -> CenteredAdmin {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = viewModel::loadProducts) { Text("Reintentar") }
                    }
                }
                is CatalogUiState.Success -> {
                    val products = uiState.products
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("admin-product-list"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Administración del catálogo.", style = MaterialTheme.typography.bodyMedium)
                                Text("Selecciona una pieza para asignar su imagen.", style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        items(products, key = Product::id) { product ->
                            AdminProductCard(product, imageStorage) { onSelectProduct(product.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminProductCard(product: Product, storage: ProductImageStorage, onEdit: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductImage(
                productId = product.id,
                imageReference = product.imageReference,
                storage = storage,
                modifier = Modifier.width(112.dp).aspectRatio(1f),
                contentDescription = "Imagen de ${product.name}",
                contentScale = ContentScale.Fit
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium)
                Text("${formatCm(product.widthCm)} × ${formatCm(product.lengthCm)} cm · ${product.material}")
                Text(formatClp(product.price), color = MaterialTheme.colorScheme.primary)
                TextButton(onClick = onEdit, modifier = Modifier.testTag("edit-image-${product.id}")) {
                    Text("Asignar imagen")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminProductImageScreen(
    viewModel: AdminProductImageViewModel,
    imageStorage: ProductImageStorage,
    onBack: () -> Unit,
    windowSizeClass: LouArayaWindowSizeClass
) {
    val state by viewModel.uiState.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        viewModel.selectImage(uri?.toString())
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        viewModel.onCameraCaptureResult(saved)
    }
    var cameraDenied by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) {
            if ((state as? AdminProductImageUiState.Ready)?.pendingCameraUri == null) viewModel.prepareCameraCapture()
            else viewModel.retryCameraCapture()
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState = state
    val readyState = uiState as? AdminProductImageUiState.Ready
    val busy = readyState?.let { it.isSaving || it.isPreparingCameraCapture } == true
    val scope = rememberCoroutineScope()
    var isLeaving by remember { mutableStateOf(false) }
    val leaveEditor = {
        if (!busy && !isLeaving) {
            isLeaving = true
            scope.launch {
                viewModel.discardCameraDraft()
                onBack()
            }
        }
    }
    BackHandler { leaveEditor() }

    val launchUri = readyState?.cameraLaunchUri
    LaunchedEffect(viewModel, launchUri) {
        val uri = launchUri ?: return@LaunchedEffect
        if (!viewModel.consumeCameraLaunchRequest(uri)) return@LaunchedEffect
        try {
            camera.launch(uri.toUri())
        } catch (_: Exception) {
            viewModel.onCameraLaunchFailed(uri)
        }
    }

    Scaffold(topBar = { BrandHeader("Fotografía", back = leaveEditor) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                AdminProductImageUiState.Loading -> Page { StateContent("Preparando la fotografía.", "Un momento…", loading = true) }
                is AdminProductImageUiState.Error -> Page { StateContent("No pudimos abrir la fotografía.", uiState.message, action = "Reintentar", onAction = viewModel::loadProduct) }
                is AdminProductImageUiState.Ready -> {
                    val ready = uiState
                    Page(maxWidth = LouArayaContentWidths.productDetail, footer = {
                        if (ready.previewSourceUri != null && !ready.isSaved) {
                            Button(onClick = viewModel::saveImage, enabled = !busy && !isLeaving && ready.pendingCameraUri == null, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(56.dp).testTag("save-product-image")) { Text(if (ready.isSaving) "Guardando la imagen…" else if (ready.error != null) "Reintentar" else "Confirmar imagen") }
                            TextButton(onClick = viewModel::cancelSelection, enabled = !busy && !isLeaving && ready.pendingCameraUri == null, modifier = Modifier.fillMaxWidth().testTag("cancel-image-selection")) { Text(if (ready.error != null) "Conservar foto actual" else "Cancelar selección") }
                        }
                        if (ready.isSaved) Action("Volver a administración", enabled = !isLeaving, onClick = leaveEditor)
                    }) {
                        Intro(if (ready.isSaved) "Todo listo" else if (ready.previewSourceUri != null) "Vista previa" else "La primera impresión", when { ready.isSaved -> "Imagen guardada."; ready.isSaving -> "Guardando la imagen."; ready.error != null && ready.previewSourceUri != null -> "No pudimos guardar\nla imagen."; ready.previewSourceUri != null -> "¿Usamos esta foto?"; else -> "Muéstrala\ncon luz natural." })
                        ProductImage(ready.product.id, ready.product.imageReference, imageStorage, Modifier.fillMaxWidth().height(300.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).testTag("product-image-preview"), "Vista previa de ${ready.product.name}", ContentScale.Fit, previewSourceUri = ready.previewSourceUri)
                        Text(ready.product.name, style = MaterialTheme.typography.titleLarge)
                        if (ready.isSaving || ready.isPreparingCameraCapture) CircularProgressIndicator()
                        if (ready.previewSourceUri == null && !ready.isSaved) {
                            OutlinedButton(onClick = {
                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) cameraPermission.launch(android.Manifest.permission.CAMERA)
                                else if (ready.pendingCameraUri == null) viewModel.prepareCameraCapture() else viewModel.retryCameraCapture()
                            }, enabled = !busy && !isLeaving && (ready.pendingCameraUri == null || ready.cameraLaunchUri == null), modifier = Modifier.fillMaxWidth().height(56.dp).testTag("take-product-photo"), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { DesignIcon(R.drawable.la_camera); Spacer(Modifier.width(8.dp)); Text(if (ready.pendingCameraUri == null) "Tomar fotografía" else "Reintentar fotografía") }
                            OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy && !isLeaving && ready.pendingCameraUri == null, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("pick-product-image"), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { DesignIcon(R.drawable.la_image); Spacer(Modifier.width(8.dp)); Text("Elegir de galería") }
                        }
                        if (ready.pendingCameraUri != null && ready.cameraLaunchUri == null && !ready.isPreparingCameraCapture) {
                            Note("La captura está pendiente. Puedes volver a abrir la cámara o cancelarla.")
                            TextButton(onClick = viewModel::cancelPendingCameraCapture, enabled = !busy && !isLeaving, modifier = Modifier.fillMaxWidth().testTag("cancel-camera-capture")) { Text("Cancelar toma") }
                        }
                        if (cameraDenied) Note("Permite la cámara desde la configuración del perfil o elige una imagen de la galería.", error = true)
                        ready.error?.let { Note(it, error = true) }
                        if (ready.previewSourceUri == null && !ready.isSaved) Note("La fotografía se guarda al confirmar una imagen. Puedes cancelar y conservar la actual.")
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredAdmin(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center, content = { content() })
}
