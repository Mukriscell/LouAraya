package cl.duoc.louaraya.presentation.local

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.currentCoroutineContext

internal enum class AuthScene { WELCOME, LOGIN, REGISTER }

/** Figma's 64-second timeline repeats eight identical, seamless eight-second phrases. */
internal object AuthTimeline {
    const val CYCLE_MILLIS = 64_000
    private val ease = CubicBezierEasing(.42f, 0f, .58f, 1f)
    val entranceEase = CubicBezierEasing(0f, 0f, .58f, 1f)
    private fun period(time: Float) = ((time % 8_000f) + 8_000f) % 8_000f
    fun carousel(time: Float): Float = when (val t = period(time)) {
        in 0f..3_000f -> 0f
        in 3_000f..4_000f -> ease.transform((t - 3_000f) / 1_000f)
        in 4_000f..7_000f -> 1f
        else -> 1f - ease.transform((t - 7_000f) / 1_000f)
    }
    fun thread(time: Float): Float {
        val t = period(time)
        return if (t <= 4_000f) ease.transform(t / 4_000f)
        else 1f - ease.transform((t - 4_000f) / 4_000f)
    }
    fun sealDegrees(time: Float, scene: AuthScene): Float {
        val keys = when (scene) {
            AuthScene.WELCOME -> floatArrayOf(.14f, .087f, .14f, .192f, .14f)
            AuthScene.LOGIN -> floatArrayOf(.14f, .105f, .14f, .175f, .14f)
            AuthScene.REGISTER -> floatArrayOf(-.14f, -.175f, -.14f, -.105f, -.14f)
        }
        val t = period(time) / 2_000f
        val index = t.toInt().coerceIn(0, 3)
        return (keys[index] + (keys[index + 1] - keys[index]) * ease.transform(t - index)) * 180f / Math.PI.toFloat()
    }
}

@Stable
internal class AuthMotionState(val reduced: Boolean, val entrance: State<Float>, val cycle: State<Float>)
internal val LocalAuthMotion = compositionLocalOf {
    AuthMotionState(false, mutableStateOf(2_000f), mutableStateOf(0f))
}

@Composable
internal fun systemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun animationsDisabled() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
    var systemDisabled by remember(resolver) { mutableStateOf(animationsDisabled()) }
    var contextDisabled by remember { mutableStateOf(false) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { systemDisabled = animationsDisabled() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(Unit) {
        currentCoroutineContext()[MotionDurationScale]?.let { scale ->
            snapshotFlow { scale.scaleFactor <= 0f }.collect { contextDisabled = it }
        }
    }
    return systemDisabled || contextDisabled
}

@Composable
internal fun AuthMotionScope(route: String, reducedMotion: Boolean? = null, content: @Composable () -> Unit) {
    val reduced = reducedMotion ?: systemReducedMotion()
    val clock = remember(route) { Animatable(if (reduced) 2_000f else 0f) }
    LaunchedEffect(clock, reduced) {
        if (reduced) clock.snapTo(2_000f)
        else clock.animateTo(2_000f, tween((2_000f - clock.value).toInt().coerceAtLeast(0), easing = LinearEasing))
    }
    val cycle = key(route, reduced) {
        if (reduced) rememberUpdatedState(0f)
        else rememberInfiniteTransition(label = "auth-decorative-cycle").animateFloat(
            0f, AuthTimeline.CYCLE_MILLIS.toFloat(),
            infiniteRepeatable(tween(AuthTimeline.CYCLE_MILLIS, easing = LinearEasing)), label = "auth-64-seconds")
    }
    val state = remember(clock, cycle, reduced) { AuthMotionState(reduced, clock.asState(), cycle) }
    CompositionLocalProvider(LocalAuthMotion provides state, content = content)
}

@Composable
internal fun Modifier.authEntrance(start: Int, rise: Float = 8f, fadeMillis: Int = 420, moveMillis: Int = 500): Modifier {
    val motion = LocalAuthMotion.current
    return graphicsLayer {
        val elapsed = motion.entrance.value - start
        alpha = if (motion.reduced) 1f else AuthTimeline.entranceEase.transform((elapsed / fadeMillis).coerceIn(0f, 1f))
        translationY = if (motion.reduced) 0f else rise.dp.toPx() *
            (1f - AuthTimeline.entranceEase.transform((elapsed / moveMillis).coerceIn(0f, 1f)))
    }
}

@Composable
internal fun Modifier.authSealMotion(scene: AuthScene): Modifier {
    val motion = LocalAuthMotion.current
    return graphicsLayer {
        transformOrigin = TransformOrigin(.5f, 0f)
        rotationZ = AuthTimeline.sealDegrees(if (motion.reduced) 0f else motion.cycle.value, scene)
    }
}

@Composable
internal fun Modifier.authPhotoMotion(): Modifier {
    val motion = LocalAuthMotion.current
    return graphicsLayer {
        val scale = if (motion.reduced) 1f else 1f + .025f * AuthTimeline.thread(motion.cycle.value)
        scaleX = scale; scaleY = scale
    }
}

@Composable
internal fun AuthTitle(text: String, style: TextStyle = MaterialTheme.typography.displayMedium) {
    Text(text, style = style, modifier = Modifier.semantics { heading() })
}
