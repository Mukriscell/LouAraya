# Animaciones y presentación · 25 de octubre de 2026

Se incorporó la dependencia explícita `androidx.compose.animation:animation`, gestionada por el BOM existente del proyecto.

## Cambios visibles

- Navegación con desplazamiento corto y desvanecimiento; el regreso utiliza el sentido inverso y los cambios de pestaña usan un fundido breve.
- Entrada de páginas y primeras filas del catálogo o historial con movimiento vertical suave.
- Botones, accesos, tarjetas e iconos responden a la presión con un cambio de escala mediante `spring`.
- Favoritos con corazón relleno, cambio de fondo y resorte al seleccionar.
- Campos que cambian suavemente el color de su borde al recibir foco o mostrar un error.
- Indicadores de solicitudes con color animado, etapa actual destacada y línea de progreso.
- Saldo de bodega y métricas del taller con transición entre cifras completas. La semántica accesible contiene siempre el valor actual, incluso durante la transición.
- Tarjetas del catálogo con superficie, borde suave y esquinas redondeadas; panel de existencias con jerarquía visual y color según el saldo.
- Accentos en los encabezados y degradado suave sobre la fotografía destacada del inicio.

## Comportamiento

Los fundidos y desplazamientos usan duraciones de entre 140 y 300 ms. Las entradas escalonadas tienen un retraso máximo de 135 ms y se limitan a las primeras filas. Las animaciones de escala utilizan resortes de Compose. Los controles conservan sus tamaños de disposición y sus reglas de habilitación.

Se utilizan las API de animación estándar de Compose, vinculadas a la escala de animación de Android. El movimiento de los números representa el valor anterior y el actual; no interpola cantidades ficticias de stock.

## Verificación

Se ejecutó la suite completa con `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: **189 pruebas en 35 clases**, sin fallos, errores ni omitidas. El APK y el APK de pruebas Android compilaron correctamente.

Después de ajustar el recorte de las cifras a su área visual, se repitieron las ocho pruebas de animación y bodega, la compilación del APK y lint. Las ocho pasaron. Lint terminó con **0 errores, 35 advertencias y 3 sugerencias**.

Las pruebas nuevas verifican la respuesta visual a la presión, una sola ejecución del botón al soltarlo, la cancelación del gesto al quedar ocupado el formulario, las etiquetas y selección de favoritos durante el resorte, y el valor accesible del contador antes de terminar su transición. Se comprueba además el saldo visible después de una venta real de prueba.

La prueba del contador utiliza el reloj manual de Compose y espera que Android aplique la actualización de estado antes de avanzar los fotogramas. Se revisaron capturas de la transición y del resultado final para comprobar que las cifras no crucen las etiquetas del panel.

No había un dispositivo conectado por ADB. La comprobación en teléfono queda pendiente.

Las capturas de revisión incluyen inicio, catálogo, favoritos, bodega e historial con datos ficticios.

## Referencias

- [Guía oficial de animaciones de Compose](https://developer.android.com/develop/ui/compose/animation/quick-guide).
- [Pruebas de animaciones con el reloj de Compose](https://developer.android.com/develop/ui/compose/animation/testing).
