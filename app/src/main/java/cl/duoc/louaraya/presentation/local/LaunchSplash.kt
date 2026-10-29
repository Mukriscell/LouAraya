package cl.duoc.louaraya.presentation.local

import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import android.graphics.PathMeasure
import android.util.Xml
import androidx.annotation.RawRes
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import androidx.core.graphics.withScale
import cl.duoc.louaraya.R
import org.xmlpull.v1.XmlPullParser
import kotlin.math.abs
import kotlin.math.min

/** Values from Figma 43:82. The launch plays one 3.6-second phrase, then releases navigation. */
internal object SplashTimeline {
    const val DURATION = 3_600
    private val ease = CubicBezierEasing(.42f, 0f, .58f, 1f)
    private val out = CubicBezierEasing(0f, 0f, .58f, 1f)
    fun reveal(time: Float, start: Float, duration: Float) = ease.transform(((time - start) / duration).coerceIn(0f, 1f))
    fun fade(time: Float, start: Float, duration: Float) = out.transform(((time - start) / duration).coerceIn(0f, 1f))
    fun character(time: Float, index: Int): Float {
        val rank = (abs(index - 3.5f) - .5f).toInt()
        return fade(time, 300f + rank * 162.195f, 463.415f)
    }
    fun titleY(time: Float): Float = if (time < 1_650f) 10f - 13f * reveal(time, 1_250f, 400f)
        else -3f + 3f * reveal(time, 1_650f, 450f)
    fun knotScale(time: Float): Float = if (time < 1_850f) 1.32f - .38f * reveal(time, 1_150f, 700f)
        else .94f + .06f * reveal(time, 1_850f, 250f)
    fun knotDegrees(time: Float, left: Boolean): Float {
        val radians = if (time < 1_800f) .244f - .273f * reveal(time, 1_150f, 650f)
            else -.029f + .029f * reveal(time, 1_800f, 300f)
        return radians * 180f / Math.PI.toFloat() * if (left) 1f else -1f
    }
}

private val SplashClockSaver = Saver<Animatable<Float, AnimationVector1D>, Float>(
    save = { it.value }, restore = { Animatable(it) })

@Composable
internal fun LaunchSplash(onFinished: () -> Unit, reducedMotion: Boolean? = null) {
    val reduced = reducedMotion ?: systemReducedMotion()
    val clock = rememberSaveable(saver = SplashClockSaver) { Animatable(0f) }
    val finish by rememberUpdatedState(onFinished)
    LaunchedEffect(clock, reduced) {
        if (reduced) {
            clock.snapTo(SplashTimeline.DURATION.toFloat())
            withFrameNanos { }
        } else clock.animateTo(SplashTimeline.DURATION.toFloat(), tween(
            (SplashTimeline.DURATION - clock.value).toInt().coerceAtLeast(0), easing = LinearEasing))
        finish()
    }
    val time = clock.asState()
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.systemBars).clipToBounds().testTag("launch-splash"), contentAlignment = Alignment.Center) {
        // In portrait, crop only the reference's empty margins so the suspension
        // cords meet the screen edges. Landscape keeps the whole composition.
        val scale = if (maxWidth <= maxHeight) maxWidth.value / 393f
            else min(maxWidth.value / 393f, maxHeight.value / 852f)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
            Box(Modifier.requiredSize(393.dp * scale, 852.dp * scale).clipToBounds()) {
                // Original SVGs are parsed as complete assets. PathMeasure reveals the exact
                // exported paths, preserving Figma's stroke geometry instead of redrawing cords.
                SplashVector(R.raw.splash_left_cord, time, 0f, 850f,
                    Modifier.offset((-14.6f * scale).dp, (169.4f * scale).dp).size((57.2002f * scale).dp, (251.2f * scale).dp))
                SplashVector(R.raw.splash_right_cord, time, 120f, 850f,
                    Modifier.offset((350.4f * scale).dp, (118.4f * scale).dp).size((59.2002f * scale).dp, (302.2f * scale).dp))
                SplashVector(R.raw.splash_support, time, 850f, 600f,
                    Modifier.offset((46.4f * scale).dp, (437.4f * scale).dp).size((300.2f * scale).dp, (17.2002f * scale).dp))
                SplashVector(R.raw.splash_left_tail, time, 1_650f, 500f,
                    Modifier.offset((28.4f * scale).dp, (438.4f * scale).dp).size((13.2002f * scale).dp, (44.2003f * scale).dp))
                SplashVector(R.raw.splash_right_tail, time, 1_750f, 500f,
                    Modifier.offset((350.4f * scale).dp, (438.4f * scale).dp).size((16.2001f * scale).dp, (63.2002f * scale).dp))
                SplashTitle(time, scale)
                listOf(true, false).forEach { left ->
                    SplashVector(if (left) R.raw.splash_left_knot else R.raw.splash_right_knot, time, 0f, 1f,
                        Modifier.offset(((if (left) 29.25f else 339.25f) * scale).dp, (409.935f * scale).dp)
                            .size((24.5f * scale).dp, (31.8143f * scale).dp).graphicsLayer {
                                alpha = SplashTimeline.fade(time.value, if (left) 1_050f else 1_150f, 300f)
                                val zoom = SplashTimeline.knotScale(time.value)
                                scaleX = zoom; scaleY = zoom
                                rotationZ = SplashTimeline.knotDegrees(time.value, left)
                                transformOrigin = TransformOrigin(.5f, 16.065f / 31.8143f)
                            })
                }
                Text("MACRAMÉ · DISEÑO CHILENO", Modifier.offset((64f * scale).dp, (489f * scale).dp)
                    .width(265.dp * scale).graphicsLayer {
                        val progress = SplashTimeline.fade(time.value, 1_850f, 550f)
                        alpha = progress; translationY = (5.dp * scale).toPx() * (1f - progress)
                    }, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = (9f * scale).sp, lineHeight = (15f * scale).sp))
            }
        }
    }
}

@Composable
private fun SplashTitle(time: State<Float>, scale: Float) {
    val density = LocalDensity.current
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text("LouAraya", Modifier.offset((58f * scale).dp, (358f * scale).dp).size(285.dp * scale, 90.dp * scale)
        .semantics { heading() }.testTag("splash-wordmark").graphicsLayer {
            translationY = (SplashTimeline.titleY(time.value).dp * scale).toPx()
        }.drawWithContent {
            val result = layout
            if (result == null || time.value >= 1_250f) drawContent()
            else for (i in 0..7) {
                val progress = SplashTimeline.character(time.value, i)
                if (progress > 0f) {
                    val rect = result.getBoundingBox(i)
                    drawContext.canvas.saveLayer(Rect(0f, -20.dp.toPx(), size.width, size.height + 20.dp.toPx()), Paint().apply { alpha = progress })
                    translate(top = (12.dp * scale).toPx() * (1f - progress)) {
                        clipRect(rect.left - 1f, 0f, rect.right + 1f, size.height) { this@drawWithContent.drawContent() }
                    }
                    drawContext.canvas.restore()
                }
            }
        }, style = editorial(72, 90).copy(fontSize = (72f * scale).sp, lineHeight = (90f * scale).sp,
            shadow = Shadow(Color.Black.copy(alpha = .25f), Offset(0f, with(density) { (4.dp * scale).toPx() }), with(density) { (2.dp * scale).toPx() })),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1, softWrap = false, textAlign = TextAlign.Center, onTextLayout = { layout = it })
}

private data class SplashStroke(val path: NativePath, val paint: NativePaint, val length: Float)
private data class SplashSvg(val width: Float, val height: Float, val strokes: List<SplashStroke>)

@Composable
private fun SplashVector(@RawRes resource: Int, time: State<Float>, start: Float, duration: Float, modifier: Modifier) {
    val context = LocalContext.current
    val svg = remember(context, resource) {
        context.resources.openRawResource(resource).use { stream ->
            val parser = Xml.newPullParser().apply { setInput(stream, "UTF-8") }
            var width = 0f; var height = 0f
            val strokes = mutableListOf<SplashStroke>()
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) when (parser.name) {
                    "svg" -> { width = parser.getAttributeValue(null, "width").toFloat(); height = parser.getAttributeValue(null, "height").toFloat() }
                    "path" -> {
                        val path = requireNotNull(PathParser.createPathFromPathData(parser.getAttributeValue(null, "d")))
                        val paint = NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                            style = NativePaint.Style.STROKE
                            color = android.graphics.Color.parseColor(parser.getAttributeValue(null, "stroke"))
                            strokeWidth = parser.getAttributeValue(null, "stroke-width").toFloat()
                            strokeCap = NativePaint.Cap.ROUND
                            strokeJoin = NativePaint.Join.MITER
                        }
                        strokes += SplashStroke(path, paint, PathMeasure(path, false).length)
                    }
                }
                parser.next()
            }
            SplashSvg(width, height, strokes)
        }
    }
    val segment = remember { NativePath() }
    val measure = remember { PathMeasure() }
    Canvas(modifier) {
        val progress = SplashTimeline.reveal(time.value, start, duration)
        if (progress > 0f) drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.withScale(size.width / svg.width, size.height / svg.height) {
                svg.strokes.forEach { stroke ->
                    if (progress >= 1f) native.drawPath(stroke.path, stroke.paint)
                    else {
                        segment.reset(); measure.setPath(stroke.path, false)
                        measure.getSegment(0f, stroke.length * progress, segment, true)
                        native.drawPath(segment, stroke.paint)
                    }
                }
            }
        }
    }
}
