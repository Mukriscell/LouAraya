package cl.duoc.louaraya.presentation.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.image.ProductImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/** Shared image rendering for catalog, detail, and the demo administration flow. */
@Composable
fun ProductImage(
    productId: Long,
    imageReference: String?,
    storage: ProductImageStorage?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    previewSourceUri: String? = null,
    testTag: String? = null
) {
    val loadedImage by produceState<ProductImageLoadState>(
        initialValue = ProductImageLoadState.Loading,
        previewSourceUri,
        imageReference,
        storage
    ) {
        value = ProductImageLoadState.Loading
        val bytes = try {
            when {
                storage == null -> null
                previewSourceUri != null -> storage.loadPreview(previewSourceUri, MAX_RENDER_DIMENSION)
                imageReference != null -> storage.loadFromReference(imageReference, MAX_RENDER_DIMENSION)
                else -> null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val bitmap = bytes?.let { data ->
            withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }
        }
        value = when {
            bitmap != null -> ProductImageLoadState.Loaded(bitmap)
            previewSourceUri != null -> ProductImageLoadState.PreviewUnavailable
            else -> ProductImageLoadState.DemoFallback
        }
    }
    val resource = demoProductImageResource(productId)
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        when (val imageState = loadedImage) {
            is ProductImageLoadState.Loaded -> Image(
                bitmap = imageState.bitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale
            )

            else -> when {
                previewSourceUri != null -> Text(
                    text = if (imageState == ProductImageLoadState.PreviewUnavailable) {
                        "No se pudo previsualizar. Elige otra imagen."
                    } else {
                        "Cargando vista previa…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                resource != null -> Image(
                    painter = painterResource(resource),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )

                else -> Text("Imagen no disponible", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun demoProductImageResource(productId: Long): Int? = when (productId) {
    1L -> R.drawable.product_tapiz_aurora
    2L -> R.drawable.product_colgante_brisa
    3L -> R.drawable.product_camino_mesa
    4L -> R.drawable.product_portamacetero
    else -> null
}

private const val MAX_RENDER_DIMENSION = 960

private sealed interface ProductImageLoadState {
    data object Loading : ProductImageLoadState
    data object DemoFallback : ProductImageLoadState
    data object PreviewUnavailable : ProductImageLoadState
    data class Loaded(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : ProductImageLoadState
}
