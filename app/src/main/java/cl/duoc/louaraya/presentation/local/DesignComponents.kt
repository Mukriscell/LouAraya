package cl.duoc.louaraya.presentation.local

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import cl.duoc.louaraya.ui.theme.AtelierSerif
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.presentation.common.displayName


@Composable
internal fun DesignIcon(@DrawableRes icon: Int, description: String? = null, modifier: Modifier = Modifier, primary: Boolean = false) {
    Icon(painterResource(icon), description, modifier.size(24.dp), tint = if (primary) MaterialTheme.colorScheme.primary else LocalContentColor.current)
}

@Composable
internal fun DesignIconButton(@DrawableRes icon: Int, label: String, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val background by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background, tween(AtelierMotion.STANDARD), label = "icon-background")
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessMedium), label = "favorite-scale")
    IconButton(onClick, modifier.size(48.dp).atelierPress(source, enabled && !LocalBusy.current, .9f).clip(CircleShape).background(background).semantics { this.selected = selected }, enabled = enabled && !LocalBusy.current, interactionSource = source) {
        DesignIcon(if (selected && icon == R.drawable.la_heart) R.drawable.la_heart_filled else icon, label,
            Modifier.graphicsLayer { scaleX = scale; scaleY = scale }, primary = enabled && !LocalBusy.current)
    }
}

@Composable
internal fun BrandHeader(title: String, back: (() -> Unit)? = null, profile: (() -> Unit)? = null, notices: (() -> Unit)? = null, unread: Int = 0) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).heightIn(min = 60.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (back != null) DesignIconButton(R.drawable.la_arrow_left, "Volver", onClick = back)
        else {
            Icon(painterResource(R.drawable.atelier_knot), null, Modifier.size(28.dp, 32.dp), tint = Color.Unspecified)
        }
        Text(if (back == null) "lou araya" else title, Modifier.weight(1f), style = if (back == null) MaterialTheme.typography.headlineMedium.copy(fontFamily = AtelierSerif) else MaterialTheme.typography.titleMedium, textAlign = if (back == null) TextAlign.Start else TextAlign.Center)
        if (profile != null) DesignIconButton(R.drawable.la_user_round, "Mi perfil", onClick = profile)
        if (notices != null) Box {
            DesignIconButton(R.drawable.la_bell, "Avisos ($unread)", onClick = notices)
            androidx.compose.animation.AnimatedVisibility(unread > 0, modifier = Modifier.align(Alignment.TopEnd), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                Box(Modifier.padding(7.dp).size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            }
        }
        if (profile == null && notices == null) Spacer(Modifier.width(48.dp))
    }
}

@Composable
internal fun Page(title: String? = null, maxWidth: Dp = 800.dp, footer: (@Composable () -> Unit)? = null, topPadding: Dp = 12.dp, footerDivider: Boolean = true, horizontalPadding: Dp = 24.dp, spacing: Dp = 12.dp, animated: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val progress by animateFloatAsState(if (!animated || entered) 1f else 0f, tween(AtelierMotion.PAGE, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "page-entry")
    BoxWithConstraints(Modifier.fillMaxSize().graphicsLayer { alpha = progress; translationY = (1f - progress) * 14.dp.toPx() }, contentAlignment = Alignment.TopCenter) {


        val horizontalFooter = footer != null && maxHeight < 240.dp && this.maxWidth >= 600.dp
        Layout(modifier = Modifier.widthIn(max = maxWidth).fillMaxSize(), content = {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = if (horizontalFooter) 16.dp else horizontalPadding).padding(top = topPadding, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(spacing)) {
                    if (title != null) Text(title, style = MaterialTheme.typography.headlineMedium)
                    content()
                }
                if (footer != null) Column(Modifier.background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
                    if (footerDivider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.padding(horizontal = if (horizontalFooter) 16.dp else 24.dp).padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { footer() }
                }
        }) { children, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val footerWidth = if (horizontalFooter) 220.dp.roundToPx().coerceAtMost(width) else width
            val action = children.getOrNull(1)?.measure(Constraints(minWidth = footerWidth, maxWidth = footerWidth, maxHeight = height))
            val gap = if (horizontalFooter) 16.dp.roundToPx() else 0
            val bodyWidth = if (horizontalFooter) (width - footerWidth - gap).coerceAtLeast(0) else width
            val bodyHeight = if (horizontalFooter) height else (height - (action?.height ?: 0)).coerceAtLeast(0)
            val body = children[0].measure(Constraints.fixed(bodyWidth, bodyHeight))
            layout(width, height) {
                body.placeRelative(0, 0)
                action?.placeRelative(if (horizontalFooter) bodyWidth + gap else 0, if (horizontalFooter) 0 else bodyHeight)
            }
        }
    }
}

@Composable
internal fun Intro(eyebrow: String, title: String, description: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(22.dp).height(2.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (description != null) Text(description, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
}

@Composable
internal fun Action(label: String, enabled: Boolean = true, loading: Boolean = false, centered: Boolean = false, cornerRadius: Dp = 14.dp, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp).atelierPress(source, enabled && !LocalBusy.current && !loading)
        .semantics { if (loading) stateDescription = "Procesando" }, enabled = enabled && !LocalBusy.current && !loading, interactionSource = source, shape = RoundedCornerShape(cornerRadius), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        if (centered) Spacer(Modifier.width(36.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        Spacer(Modifier.width(12.dp))
        Crossfade(loading, modifier = Modifier.size(24.dp), animationSpec = tween(AtelierMotion.QUICK), label = "action-loading") { pending ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (pending) CircularProgressIndicator(Modifier.size(20.dp).testTag("action-loading"), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                else if (!centered) DesignIcon(R.drawable.la_arrow_up_right)
            }
        }
    }
}

@Composable
internal fun SecondaryAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    TextButton(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp).atelierPress(source, enabled && !LocalBusy.current), interactionSource = source, enabled = enabled && !LocalBusy.current) { Text(label, textAlign = TextAlign.Center) }
}

@Composable
internal fun Field(label: String, value: String, change: (String) -> Unit, number: Boolean = false, password: Boolean = false, multiline: Boolean = false, error: String? = null, hint: String? = null, search: Boolean = false, labelBadge: String? = null, background: Color? = null) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(when { error != null -> MaterialTheme.colorScheme.error; focused -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.outlineVariant }, tween(AtelierMotion.QUICK), label = "field-border")
    BasicTextField(value, change,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.semantics { if (error != null) error(error) },
        enabled = !LocalBusy.current, singleLine = !multiline, minLines = if (multiline) 3 else 1,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else if (number) KeyboardType.Decimal else KeyboardType.Text),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        decorationBox = { input ->
            Column(Modifier.animateContentSize(tween(AtelierMotion.STANDARD)), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!search) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, style = MaterialTheme.typography.labelMedium)
                    if (labelBadge != null) Text(labelBadge, Modifier.clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth().heightIn(min = if (multiline) 112.dp else 52.dp).clip(RoundedCornerShape(12.dp)).background(background ?: MaterialTheme.colorScheme.surfaceContainerLow).border(1.dp, border, RoundedCornerShape(12.dp)).padding(horizontal = if (search) 12.dp else 16.dp, vertical = if (password) 0.dp else 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (search) DesignIcon(R.drawable.la_search)
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && search) Text(label, color = MaterialTheme.colorScheme.secondary)
                        input()
                    }
                    if (password) TextButton(onClick = { visible = !visible }, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp)) { Text(if (visible) "Ocultar" else "Ver", style = MaterialTheme.typography.labelMedium) }
                    else if (search && value.isNotEmpty()) DesignIconButton(R.drawable.la_x, "Limpiar búsqueda") { change("") }
                    if (error != null) CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                        DesignIcon(R.drawable.la_circle_alert, "Error en $label", Modifier.size(20.dp))
                    }
                }
                if (error != null || hint != null) Text(error ?: hint.orEmpty(), style = MaterialTheme.typography.bodySmall, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
            }
        })
}

@Composable
internal fun <T> Choices(values: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { item -> Choice(label(item), item == selected) { onSelect(item) } }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    FilterChip(selected, onClick, label = { Text(label) }, interactionSource = source, enabled = !LocalBusy.current, modifier = modifier.heightIn(min = 48.dp).atelierPress(source, !LocalBusy.current), shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant))
}

@Composable
internal fun StatusChoices(selected: RequestStatus, onSelect: (RequestStatus) -> Unit) {
    RequestStatus.entries.filterNot { it == RequestStatus.CANCELLED }.chunked(2).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        row.forEach { status -> Choice(status.displayName(), selected == status, Modifier.weight(1f)) { onSelect(status) } }
    } }
}

@Composable
internal fun Note(message: String, error: Boolean = false) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            DesignIcon(R.drawable.la_info)
            Text(message, style = MaterialTheme.typography.bodySmall, color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun DesignRow(title: String, detail: String? = null, meta: String? = null, @DrawableRes icon: Int = R.drawable.la_leaf, onClick: (() -> Unit)? = null) {
    val source = remember { MutableInteractionSource() }
    Surface(Modifier.fillMaxWidth().atelierPress(source, onClick != null && !LocalBusy.current).then(if (onClick != null) Modifier.clickable(interactionSource = source, indication = ripple(), enabled = !LocalBusy.current, onClick = onClick) else Modifier), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f))) {
        Row(Modifier.heightIn(min = 76.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { DesignIcon(icon, primary = true) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!detail.isNullOrBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                if (!meta.isNullOrBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (onClick != null) DesignIcon(R.drawable.la_arrow_up_right, primary = true)
        }
    }
}

@Composable
internal fun StateContent(title: String, detail: String, @DrawableRes icon: Int = R.drawable.la_leaf, loading: Boolean = false, action: String? = null, onAction: () -> Unit = {}) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val iconScale by animateFloatAsState(if (entered) 1f else .75f, spring(dampingRatio = .65f, stiffness = Spring.StiffnessMedium), label = "state-icon")
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(80.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(32.dp)) else DesignIcon(icon, primary = true)
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(detail, color = MaterialTheme.colorScheme.secondary, textAlign = TextAlign.Center)
        if (action != null) Action(action, onClick = onAction)
    }
}

@Composable
internal fun StatusBadge(status: RequestStatus) {
    val color by animateColorAsState(when (status) {
        RequestStatus.CANCELLED -> MaterialTheme.colorScheme.errorContainer
        RequestStatus.CONFIRMED, RequestStatus.DELIVERED -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }, tween(AtelierMotion.STANDARD), label = "status-color")
    Surface(color = color, shape = CircleShape) {
        AnimatedContent(status,
            modifier = Modifier.testTag("request-status-badge").clearAndSetSemantics { text = AnnotatedString(status.displayName()) },
            transitionSpec = {
                ((fadeIn(tween(AtelierMotion.STANDARD)) + slideInVertically(tween(AtelierMotion.STANDARD)) { it / 3 }) togetherWith
                    (fadeOut(tween(AtelierMotion.QUICK)) + slideOutVertically(tween(AtelierMotion.STANDARD)) { -it / 3 }))
                    .using(SizeTransform(clip = true))
            }, label = "request-status") { current ->
            Text(current.displayName(), Modifier.padding(horizontal = 16.dp, vertical = 8.dp).clearAndSetSemantics {}, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun RequestProgress(status: RequestStatus) {
    if (status == RequestStatus.CANCELLED) {
        Note("Solicitud cancelada. Se conserva en tu historial y no continuará a entrega.")
        return
    }
    val stages = RequestStatus.entries.filterNot { it == RequestStatus.CANCELLED }
    Text("Así va tu pieza", style = MaterialTheme.typography.titleLarge)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        stages.forEachIndexed { index, stage ->
            val reached = stage.ordinal <= status.ordinal
            val color by animateColorAsState(if (reached) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainer, tween(AtelierMotion.STANDARD), label = "stage-color")
            val lineColor by animateColorAsState(if (stage.ordinal < status.ordinal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainer, tween(AtelierMotion.STANDARD), label = "stage-line-color")
            val scale by animateFloatAsState(if (stage == status) 1.12f else 1f, spring(dampingRatio = .7f), label = "stage-scale")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(28.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                    if (stage.ordinal < status.ordinal) CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onTertiary) { DesignIcon(R.drawable.la_check, modifier = Modifier.size(16.dp)) }
                    else Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = if (reached) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.secondary)
                }
                Text(stage.displayName(), style = MaterialTheme.typography.bodyLarge, color = if (stage == status) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.secondary)
            }
            if (index < stages.lastIndex) Box(Modifier.padding(start = 13.dp).width(2.dp).height(24.dp).background(lineColor, CircleShape))
        }
    }
}

internal fun priceLabel(price: Long) = cl.duoc.louaraya.presentation.common.formatClp(price).removeSuffix(" CLP")
internal fun quantityLabel(quantity: Int) = "$quantity ${if (quantity == 1) "unidad" else "unidades"}"
