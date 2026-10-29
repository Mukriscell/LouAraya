# Registro y animaciones

## Registro

Las cuentas nuevas requieren una contraseña de 8 a 20 caracteres con al menos una mayúscula, un número y un carácter especial. Se admiten signos de puntuación y símbolos; los espacios y los caracteres invisibles no cuentan como carácter especial. El formulario muestra los requisitos y un mensaje específico para cada incumplimiento. La confirmación debe coincidir exactamente.

La validación se ejecuta tanto en LocalUseCases.register como antes de importar una imagen en RegisterWithPhotoUseCase. Una contraseña rechazada no crea la cuenta ni importa la fotografía. No se cambia la autenticación de las cuentas que ya estaban guardadas.

## Stock

Se conserva y verifica el comportamiento existente: entregar una compra o reserva descuenta una unidad del producto correspondiente. El movimiento, el estado y su aviso se guardan en una misma transacción. Repetir Entregado o volver a ese estado no duplica la salida. Sin unidades disponibles se rechaza la entrega. Una cotización personalizada no descuenta la pieza de referencia.

## Animaciones

- Los botones de acceso, registro, solicitudes y bodega sustituyen la flecha por un spinner durante su operación. Conservan el texto y el espacio del icono, se deshabilitan mientras esperan y exponen el estado Procesando.
- Los campos animan su altura cuando aparece o desaparece la validación, sin cambiar su contenido escrito.
- Las etiquetas de estado combinan fundido, desplazamiento vertical y cambio de tamaño. La semántica accesible expone siempre el estado actual.
- El aviso de bodega sin existencias aparece y desaparece con fundido y expansión o contracción.
- Continúan las transiciones de navegación, entradas escalonadas, respuesta con resorte al pulsar, corazones seleccionados, cambios de color y cifras de bodega existentes.

Se utilizan las API de Compose y Material 3 ya incluidas, sin nuevas dependencias ni cambios de versión. No se introducen demoras artificiales para alargar una animación de carga.

## Verificación

Resultado final del 28 de octubre de 2026:

- 199 pruebas JVM aprobadas, sin fallos ni errores. Incluyen validación y corrección de contraseñas, límites de longitud, conservación del valor escrito, prevención de envíos duplicados y accesibilidad durante las transiciones.
- Compilación de la app y del APK de pruebas aprobada. Lint terminó sin errores; conserva 25 advertencias y 3 sugerencias.
- 7 pruebas físicas aprobadas en el Motorola Edge 50 Fusion con Android 16: registro, stock y animaciones, entrega y entrega repetida, regreso desde colecciones, regreso desde Descubrir a Inicio, conservación de filtros y accesos del administrador.
- 9 capturas revisadas: requisitos y errores del registro, registro correcto, transición y estado entregado, dos fotogramas distintos del spinner y transición y saldo final de bodega.

La prueba del spinner mantiene temporalmente ocupada la escritura de la base de datos desde el código de prueba y compara dos capturas del indicador antes de liberar la operación. Ese bloqueo solo existe en la prueba.

Las comprobaciones físicas se ejecutaron en una instalación separada para no alterar los perfiles, solicitudes ni stock de la app de uso habitual. Los resultados y las capturas están en tmp/actualizacion-motorola-2026-10-28. El registro definitivo del dispositivo es pruebas-motorola-final.txt, que termina con OK (7 tests).

Después de las pruebas se actualizó la aplicación habitual mediante instalación conservando datos y se comprobó su apertura. El APK normal entregable está en output/apk/LouAraya-2026-10-28.apk.

Referencias: [animaciones y modificadores de Compose](https://developer.android.com/develop/ui/compose/animation/composables-modifiers), [indicadores de progreso](https://developer.android.com/develop/ui/compose/components/progress).
