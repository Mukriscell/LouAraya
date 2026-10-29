# Splash y limpieza por perfil · 28 de octubre de 2026

> Revisión histórica: la limpieza de solicitudes del cliente se sustituyó por cancelación. Consulta [Cantidad y cancelación](SOLICITUDES_CANTIDAD_CANCELACION_2026-10-28.md) para el comportamiento actual.

La splash se implementa desde el nodo [43:82 de Figma](https://www.figma.com/design/f5c62e52GgJRSzDqlfzMXG?node-id=43-82). Conserva el fondo crema, LouAraya en Cormorant Garamond Medium con sombra, el origen artesanal en DM Sans Medium y los siete SVG originales. Los recursos completos se guardan en `res/raw`; sus trazos originales se dibujan con PathParser y PathMeasure, sin sustituir las cuerdas por aproximaciones.

El ciclo de Figma dura 3,6 segundos. En Android se reproduce una sola vez al iniciar la actividad: primero las cuerdas, luego las letras desde el centro, la lazada, los nudos y los cabos. El título se asienta y aparece «MACRAMÉ · DISEÑO CHILENO». La animación se adapta al ancho y alto disponibles y respeta movimiento reducido. Se guardan tanto el progreso como la finalización para que una recreación no vuelva a empezar la splash. Después continúa el recorrido de bienvenida o la sesión que ya estaba abierta; los enlaces de notificaciones mantienen su destino.

La ventana inicial de Android 12 y posteriores utiliza el mismo fondo crema y el nudo existente para acompañar la transición al contenido de Compose.

Solicitudes y avisos incorporan los botones «Limpiar solicitudes» y «Limpiar notificaciones» para clientes y administradores. Antes de limpiar se muestra el alcance y se puede cancelar. Las acciones deshabilitan el doble envío y quedan deshabilitadas cuando la lista está vacía. La confirmación captura los identificadores presentes al abrirla, por lo que las novedades que lleguen después no se limpian por accidente.

Se aplica la opción recomendada comunicada durante la implementación: limpiar significa quitar de las listas de la cuenta actual, conservando el historial de negocio. Cada cuenta tiene sus propias marcas de limpieza; no elimina ni oculta registros para otros usuarios, no revierte movimientos de stock y no elimina la trazabilidad de campañas. Los detalles conservan sus permisos de acceso. Los avisos limpiados se marcan como atendidos y se conservan sus claves de evento para impedir que reaparezcan al regenerarlos. Los avisos nuevos sí se reciben. Android retira únicamente las alertas descartadas del perfil activo.

Room pasa de la versión 8 a la 9 con una migración que añade `list_dismissals`. Conserva usuarios, sesión, fotografías, productos, solicitudes, avisos, stock y recorridos. La observación reactiva incluye la tabla nueva.

Los cambios están guardados en los archivos del proyecto que abre Android Studio. No requieren servicios remotos nuevos.

## Verificación

- Suite completa: 216 pruebas JVM aprobadas, sin fallos ni errores, incluidas las migraciones desde versiones anteriores y los controles de stock, sesión, registro y trazabilidad.
- Tras los ajustes visuales se repitieron y aprobaron las siete pruebas específicas de splash y limpieza: restauración del progreso, movimiento reducido, permisos por perfil, persistencia y migración 8 → 9.
- Dos recorridos físicos aprobados en el Motorola Edge 50 Fusion: entrada animada y recreación de la actividad; confirmación/cancelación de limpieza para ambos perfiles, retirada de alertas de Android, conservación de solicitudes y llegada de avisos nuevos. Se revisaron las capturas contra Figma.
- Compilación final y lint aprobados. Lint sin errores, con 30 advertencias y 5 sugerencias.
- La app habitual se actualizó con instalación conservando datos y abrió correctamente hasta la bienvenida después de la splash. Se mantuvieron el identificador de usuario Android y la fecha de instalación original. Las dos aplicaciones temporales de prueba se retiraron.
- Archivos guardados en el proyecto de Android Studio. No se ejecutó ninguna limpieza sobre sus datos habituales: los botones quedan disponibles para usar desde cada cuenta.

APK: `output/apk/LouAraya-splash-limpieza-2026-10-28.apk`.
SHA-256: `883643B8E0CE07BBE140B51474D9352AC75A694F35DABAE6A2470E91B2D9306D`.
Vista de comprobación: `output/design/splash-limpieza-2026-10-28/vista-motorola.png`.
Capturas y registros: `tmp/splash-limpieza-2026-10-28/` (excluidos del repositorio).
