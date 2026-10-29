package cl.duoc.louaraya.presentation.local

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.usecase.FormValidation
import cl.duoc.louaraya.presentation.common.formatRequestDate

@Composable
internal fun StockScreen(product: Product, movements: List<StockMovement>, vm: LocalAppViewModel) {
    val state by vm.uiState.collectAsState()
    var type by rememberSaveable(product.id) { mutableStateOf(StockMovementType.ENTRY.name) }
    var quantity by rememberSaveable(product.id) { mutableStateOf("") }
    var note by rememberSaveable(product.id) { mutableStateOf("") }
    val selected = StockMovementType.valueOf(type)
    val balanceColor by animateColorAsState(if (product.stock == 0) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer, tween(AtelierMotion.STANDARD), label = "stock-balance-color")
    val label = when (selected) {
        StockMovementType.ENTRY -> "Registrar entrada"
        StockMovementType.DIRECT_SALE -> "Registrar venta directa"
        else -> "Registrar retiro"
    }
    Page(maxWidth = 720.dp, footer = {
        Action(label, loading = state.busy) {
            vm.execute(message = "Movimiento registrado", onSuccess = { quantity = ""; note = "" }) {
                changeStock(product.id, FormValidation.stockQuantity(quantity), selected, note)
            }
        }
    }) {
        Intro("Bodega del taller", product.name, if (product.active) null else "Producto desactivado en el catálogo.")
        Surface(color = balanceColor, shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("EXISTENCIAS DISPONIBLES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    AnimatedCount(product.stock, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("stock-balance"))
                    Text(if (product.stock == 1) "unidad en bodega" else "unidades en bodega", style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                    DesignIcon(R.drawable.la_leaf, primary = true)
                }
            }
        }
        AnimatedVisibility(product.stock == 0,
            enter = fadeIn(tween(AtelierMotion.STANDARD)) + expandVertically(tween(AtelierMotion.STANDARD)),
            exit = fadeOut(tween(AtelierMotion.QUICK)) + shrinkVertically(tween(AtelierMotion.STANDARD))) {
            Note("Sin existencias. Registra una entrada antes de vender o entregar esta pieza.")
        }
        Choices(listOf(StockMovementType.ENTRY, StockMovementType.DIRECT_SALE, StockMovementType.WITHDRAWAL).map { it.name }, type,
            { StockMovementType.valueOf(it).label }) { type = it; vm.dismissError() }
        FormField(vm, "quantity", "Cantidad de unidades", quantity, { quantity = it }, number = true, hint = "Cantidad entera mayor a cero")
        FormField(vm, "stockNote", "Observación del movimiento · opcional", note, { note = it }, multiline = true, hint = "Máximo 250 caracteres")
        Text(when (selected) {
            StockMovementType.ENTRY -> "Suma unidades recibidas, terminadas o devueltas a bodega."
            StockMovementType.DIRECT_SALE -> "Descuenta una venta sin solicitud asociada. Para una compra o reserva registrada, marca su solicitud como Entregada."
            else -> "Descuenta unidades por daño, pérdida u otra salida que no sea una venta."
        }, style = MaterialTheme.typography.bodySmall)
        Note("Las compras y reservas descuentan una unidad al marcarse Entregadas. Las cotizaciones personalizadas no descuentan la pieza de referencia. Cambiar después el estado no devuelve unidades; registra una entrada si la pieza vuelve a bodega.")
        HorizontalDivider()
        Text("Historial de movimientos", style = MaterialTheme.typography.titleLarge)
        if (movements.isEmpty()) Text("Aún no hay movimientos para esta pieza.")
        movements.forEachIndexed { index, movement -> key(movement.id) {
            val detail = if (movement.type == StockMovementType.LEGACY_SALE) "Sin descuento retroactivo"
                else "${if (movement.quantityDelta > 0) "+" else ""}${movement.quantityDelta} unidades · Saldo: ${movement.stockAfter}"
            val row: @Composable () -> Unit = { DesignRow(movement.type.label + (movement.requestId?.let { " · Solicitud #$it" } ?: ""), detail,
                listOf(formatRequestDate(movement.date), movement.note).filter { it.isNotBlank() }.joinToString(" · ")) }
            if (index < 4) MotionItem(index, row) else row()
        } }
    }
}
