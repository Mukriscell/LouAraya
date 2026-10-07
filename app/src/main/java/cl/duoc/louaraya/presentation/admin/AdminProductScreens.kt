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
            title = { Text("Administración demo") },
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
                                Text("MVP académico; entrada de demostración sin autenticación.", style = MaterialTheme.typography.bodyMedium)
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

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Imagen de producto") },
            navigationIcon = { IconButton(onClick = leaveEditor, enabled = !busy && !isLeaving) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Volver")
            } }
        )
    }) { padding ->
        AdaptiveContentContainer(
            windowSizeClass, LouArayaContentWidths.productDetail, Modifier.padding(padding)
        ) {
            when (uiState) {
                AdminProductImageUiState.Loading -> CenteredAdmin { CircularProgressIndicator() }
                is AdminProductImageUiState.Error -> CenteredAdmin {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = viewModel::loadProduct) { Text("Reintentar") }
                    }
                }
                is AdminProductImageUiState.Ready -> {
                    val ready = uiState
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(ready.product.name, style = MaterialTheme.typography.headlineSmall)
                        ProductImage(
                            productId = ready.product.id,
                            imageReference = ready.product.imageReference,
                            storage = imageStorage,
                            previewSourceUri = ready.previewSourceUri,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.15f).testTag("product-image-preview"),
                            contentDescription = "Vista previa de ${ready.product.name}",
                            contentScale = ContentScale.Fit
                        )
                        Text("MVP académico: esta entrada de demostración no autentica un rol real.")
                        Text("Las imágenes se guardan en el almacenamiento privado de esta aplicación.")
                        OutlinedButton(
                            enabled = !busy && !isLeaving && ready.pendingCameraUri == null,
                            onClick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.fillMaxWidth().testTag("pick-product-image")
                        ) { Text(if (ready.previewSourceUri == null) "Elegir desde galería" else "Elegir otra imagen desde galería") }
                        OutlinedButton(
                            enabled = !busy && !isLeaving &&
                                (ready.pendingCameraUri == null || ready.cameraLaunchUri == null),
                            onClick = {
                                if (ready.pendingCameraUri == null) viewModel.prepareCameraCapture()
                                else viewModel.retryCameraCapture()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("take-product-photo")
                        ) {
                            Text(if (ready.pendingCameraUri == null) "Tomar fotografía" else "Reintentar fotografía")
                        }
                        if (ready.isPreparingCameraCapture) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.width(24.dp).height(24.dp))
                                Text(" Preparando fotografía…")
                            }
                        } else if (ready.pendingCameraUri != null && ready.cameraLaunchUri == null) {
                            Text("La captura está pendiente. Puedes volver a abrir la cámara o cancelarla.")
                            TextButton(
                                onClick = viewModel::cancelPendingCameraCapture,
                                enabled = !isLeaving,
                                modifier = Modifier.fillMaxWidth().testTag("cancel-camera-capture")
                            ) { Text("Cancelar toma") }
                        }
                        ready.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (ready.isSaved) Text("Imagen guardada.", color = MaterialTheme.colorScheme.primary)
                        if (ready.previewSourceUri != null && !ready.isSaved) {
                            TextButton(
                                onClick = viewModel::cancelSelection,
                                enabled = !busy && !isLeaving && ready.pendingCameraUri == null,
                                modifier = Modifier.fillMaxWidth().testTag("cancel-image-selection")
                            ) { Text("Cancelar selección") }
                            Button(
                                onClick = viewModel::saveImage,
                                enabled = !busy && !isLeaving && ready.pendingCameraUri == null,
                                modifier = Modifier.fillMaxWidth().testTag("save-product-image")
                            ) {
                                if (ready.isSaving) CircularProgressIndicator()
                                else Text("Confirmar imagen")
                            }
                        }
                        if (ready.isSaved) Button(onClick = leaveEditor, enabled = !isLeaving, modifier = Modifier.fillMaxWidth()) {
                            Text("Volver a administración")
                        }
                        Spacer(Modifier.height(8.dp))
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
