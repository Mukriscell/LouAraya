package cl.duoc.louaraya.presentation.local

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import cl.duoc.louaraya.R
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun QrScannerScreen(vm: LocalAppViewModel, imageVm: QrImageViewModel, open: (String) -> Unit) {
    val context = LocalContext.current
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var paused by rememberSaveable { mutableStateOf(false) }
    var session by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var typed by rememberSaveable { mutableStateOf("") }
    var cameraUnavailable by remember { mutableStateOf(false) }
    var cameraRevision by remember { mutableIntStateOf(0) }
    var choosingImage by rememberSaveable { mutableStateOf(false) }
    var imageMode by rememberSaveable { mutableStateOf(false) }
    val imageState by imageVm.uiState.collectAsState()
    val state by vm.uiState.collectAsState()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        choosingImage = false
        if (uri != null) {
            imageMode = true
            paused = false
            session = UUID.randomUUID().toString()
            vm.dismissError()
            imageVm.selectImage(uri.toString())
        }
    }
    val decode: (String) -> Unit = { value ->
        if (!paused && !choosingImage && !imageVm.uiState.value.reading && !vm.uiState.value.busy) {
            paused = true
            var destination = ""
            vm.execute(onSuccess = { open(destination) }) { destination = resolveQr(value, session) }
        }
    }
    LaunchedEffect(imageState.value, state.busy) {
        val value = imageState.value
        if (value != null && !state.busy && !vm.uiState.value.busy) {
            decode(value)
            imageVm.consumeValue()
        }
    }
    val (eyebrow, title) = when {
        imageMode -> "Tu imagen, una nueva historia" to "Lee una etiqueta\ndesde tu imagen."
        paused && state.busy -> "Consultando tu etiqueta" to "Un momento\npara descubrir."
        paused && state.error != null -> "Probemos de nuevo" to "No encontramos\nesta etiqueta."
        paused -> "Más historias por descubrir" to "Consulta una\nnueva etiqueta."
        cameraUnavailable -> "Podemos usar el código" to "La cámara no\nestá disponible."
        permission -> "Un detalle por descubrir" to "Enfoca la etiqueta."
        else -> "Descubre su historia" to "De la etiqueta\na tu pieza."
    }
    CompositionLocalProvider(LocalBusy provides (LocalBusy.current || imageState.reading || choosingImage)) {
    Page(maxWidth = 600.dp) {
        Intro(eyebrow, title, "Escanea con la cámara, elige una imagen o ingresa el código de una pieza o colección.")
        if (permission && !paused && !cameraUnavailable && !choosingImage && !imageMode) key(cameraRevision) { Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))) { CameraPreview(decode) { cameraUnavailable = true } } }
        Action("Leer QR desde imagen") {
            choosingImage = true
            try { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch (_: RuntimeException) { choosingImage = false; imageVm.pickerUnavailable() }
        }
        Text("Selecciona una foto o captura con un solo QR. No requiere permiso de cámara ni se envía a Internet.", style = MaterialTheme.typography.bodySmall)
        if (imageState.reading) StateContent("Leyendo tu imagen", "Buscando la etiqueta de LouAraya…", R.drawable.la_scan_line, loading = true)
        imageState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (imageMode && paused) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!permission && !imageMode) {
            StateContent("Permite el acceso a la cámara", "Para leer el QR de una pieza o colección.", R.drawable.la_scan_line)
            Action("Permitir cámara") { launcher.launch(Manifest.permission.CAMERA) }
        }
        if (paused || imageMode && !imageState.reading) Action("Volver a escanear") { vm.dismissError(); imageVm.clear(); imageMode = false; session = UUID.randomUUID().toString(); paused = false }
        if (cameraUnavailable) SecondaryAction("Volver a intentar con cámara") { cameraUnavailable = false; cameraRevision++ }
        Field("Código de etiqueta", typed, { typed = it }, error = if (paused && !imageMode) state.error else null, hint = "louaraya://product/1 o louaraya://collection/1")
        Action("Consultar código", enabled = typed.isNotBlank() && !paused) { decode(typed) }
        if (paused) SecondaryAction("Explorar catálogo") { open("catalog") }
    }
    }
}

@androidx.annotation.OptIn(markerClass = [androidx.camera.core.ExperimentalGetImage::class])
@Composable
private fun CameraPreview(decode: (String) -> Unit, unavailable: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val onDecode by rememberUpdatedState(decode)
    val onUnavailable by rememberUpdatedState(unavailable)
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var failure by remember { mutableStateOf<String?>(null) }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(300.dp))
    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    DisposableEffect(lifecycle, view) {
        val alive = AtomicBoolean(true)
        val processing = AtomicBoolean(false)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val future = ProcessCameraProvider.getInstance(context)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(executor) { proxy ->
            val media = proxy.image
            if (!alive.get() || media == null || !processing.compareAndSet(false, true)) proxy.close()
            else {
                try {
                    scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                        .addOnSuccessListener { codes -> if (alive.get()) codes.firstOrNull { it.rawValue != null }?.rawValue?.let(onDecode) }
                        .addOnFailureListener { if (alive.get()) failure = "No se pudo leer la imagen. Intenta enfocar de nuevo." }
                        .addOnCompleteListener { proxy.close(); processing.set(false) }
                } catch (_: Exception) { proxy.close(); processing.set(false) }
            }
        }
        future.addListener({
            if (alive.get()) try { future.get().bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
            catch (_: Exception) { onUnavailable() }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            alive.set(false)
            analysis.clearAnalyzer()
            if (future.isDone) runCatching { future.get().unbind(preview, analysis) }
            executor.shutdown()
            scanner.close()
        }
    }
}

