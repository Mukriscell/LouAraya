package cl.duoc.louaraya.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass

enum class LouArayaWindowSizeClass(
    val catalogColumns: Int,
    val horizontalGutter: Dp
) {
    COMPACT(catalogColumns = 1, horizontalGutter = 16.dp),
    MEDIUM(catalogColumns = 2, horizontalGutter = 24.dp),
    EXPANDED(catalogColumns = 3, horizontalGutter = 32.dp)
}

object LouArayaContentWidths {
    val catalog = 1_280.dp
    val productDetail = 760.dp
    val requestForm = 720.dp
    val requestList = 960.dp
    val requestDetail = 840.dp
}


@Suppress("DEPRECATION")
@Composable
fun currentLouArayaWindowSizeClass(): LouArayaWindowSizeClass =
    louArayaWindowSizeClass(
        currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true).windowSizeClass
    )


fun louArayaWindowSizeClass(windowSizeClass: WindowSizeClass): LouArayaWindowSizeClass =
    when {
        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND
        ) -> LouArayaWindowSizeClass.EXPANDED

        windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        ) -> LouArayaWindowSizeClass.MEDIUM

        else -> LouArayaWindowSizeClass.COMPACT
    }

@Composable
fun AdaptiveContentContainer(
    windowSizeClass: LouArayaWindowSizeClass,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = windowSizeClass.horizontalGutter)
                .testTag(ADAPTIVE_CONTENT_TEST_TAG),
            content = content
        )
    }
}

const val ADAPTIVE_CONTENT_TEST_TAG = "adaptive-content-container"
