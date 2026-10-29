# Solicitudes: cantidad, cancelación y papelera · 28 de octubre de 2026

El cliente ya no dispone de «Limpiar solicitudes». Las solicitudes que hubiera ocultado con la versión anterior se recuperan en su lista. El administrador mantiene su propia acción de limpieza, que conserva el historial de negocio y los movimientos de stock.

En el detalle de una solicitud propia pendiente o confirmada aparece «Cancelar solicitud». La confirmación permite volver sin cambios. Al confirmar, la solicitud pasa a Cancelada, conserva sus datos y su trazabilidad y genera un aviso para el administrador. No se puede cancelar una entrega ni una solicitud que ya tenga una salida de stock registrada, aunque se haya cambiado después su estado. Una solicitud cancelada no se puede reactivar, entregar ni modificar su importe acordado. La repetición de una cancelación no genera avisos o eventos duplicados.

El formulario incorpora Cantidad para compras, reservas y cotizaciones. Se valida como entero positivo y se conserva al pasar entre pasos y al recrear la pantalla. El total de referencia es el precio unitario validado del producto multiplicado por la cantidad. Se comprueba el desbordamiento numérico. La confirmación, la lista y el detalle muestran la cantidad y el total; el precio unitario original se conserva implícitamente en ambos valores.

Solicitar y confirmar no reservan stock. Al marcar una compra o reserva como Entregado, la transacción descuenta toda la cantidad solicitada y registra una sola salida de bodega. Si el saldo es insuficiente, no cambia el estado ni se descuenta parcialmente. Cambiar posteriormente el estado no duplica la salida. Las cotizaciones a medida conservan su cantidad y no descuentan el stock del producto de referencia.

La limpieza de notificaciones se presenta como una papelera en ambos perfiles, con área táctil de 48 dp y descripción accesible «Limpiar notificaciones». Mantiene la confirmación y la retirada de avisos de la cuenta y de la barra de Android. Se deshabilita cuando no hay avisos.

Room pasa de la versión 9 a la 10: agrega quantity con valor 1 para solicitudes anteriores y elimina únicamente las marcas de ocultación de solicitudes del cliente. Se conservan cuentas, sesión, fotografías, importes, avisos, stock, recorridos y la limpieza realizada por administradores.

## Verificación

Pendiente de cerrar las pruebas automáticas y la revisión del Motorola.
