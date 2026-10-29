package cl.duoc.louaraya.presentation.local

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

internal object AtelierMotion {
    const val QUICK = 140
    const val STANDARD = 240
    const val PAGE = 300

    fun enter(back: Boolean = false, tab: Boolean = false): EnterTransition {
        val fade = fadeIn(tween(if (tab) STANDARD else PAGE, easing = FastOutSlowInEasing))
        return if (tab) fade else fade + slideInHorizontally(tween(PAGE, easing = FastOutSlowInEasing)) { if (back) -it / 14 else it / 10 }
    }

    fun exit(back: Boolean = false, tab: Boolean = false): ExitTransition {
        val fade = fadeOut(tween(if (tab) QUICK else STANDARD, easing = FastOutSlowInEasing))
        return if (tab) fade else fade + slideOutHorizontally(tween(PAGE, easing = FastOutSlowInEasing)) { if (back) it / 10 else -it / 14 }
    }
}

@Composable
internal fun Modifier.atelierPress(source: InteractionSource, enabled: Boolean = true, pressedScale: Float = .97f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) pressedScale else 1f,
        spring(dampingRatio = .8f, stiffness = Spring.StiffnessMedium), label = "press-scale")
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
internal fun MotionItem(index: Int = 0, content: @Composable () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(if (entered) 1f else 0f,
        tween(AtelierMotion.STANDARD, delayMillis = index.coerceIn(0, 3) * 45, easing = FastOutSlowInEasing), label = "item-entry")
    Box(Modifier.graphicsLayer { alpha = progress; translationY = (1f - progress) * 10.dp.toPx() }) { content() }
}

@Composable
internal fun AnimatedCount(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, minimumDigits: Int = 1) {
    val currentLabel = value.toString().padStart(minimumDigits, '0')
    AnimatedContent(targetState = value, modifier = modifier.clearAndSetSemantics { text = AnnotatedString(currentLabel) }, transitionSpec = {
        val direction = if (targetState >= initialState) 1 else -1
        ((fadeIn(tween(AtelierMotion.STANDARD)) + slideInVertically(tween(AtelierMotion.STANDARD)) { it / 2 * direction }) togetherWith
            (fadeOut(tween(AtelierMotion.QUICK)) + slideOutVertically(tween(AtelierMotion.STANDARD)) { -it / 2 * direction }))
            .using(SizeTransform(clip = true))
    }, label = "count-change") { amount ->
        val label = amount.toString().padStart(minimumDigits, '0')
        Text(label, style = style, color = color, modifier = Modifier.clearAndSetSemantics {})
    }
}
