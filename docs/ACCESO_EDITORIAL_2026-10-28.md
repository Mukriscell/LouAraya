# Acceso editorial de Lou Araya · 28 de octubre de 2026

Esta revisión sustituye la composición visual de la primera entrega de acceso. La referencia actual está en [Figma, equipo Desarrollo Lucas](https://www.figma.com/design/f5c62e52GgJRSzDqlfzMXG): bienvenida `17:125`, inicio de sesión `3:2449` y registro `3:2505`.

Se conservaron las fotografías Tapiz Aurora y Colgante Brisa, el nudo, los ocho iconos de perfil, DM Sans y Cormorant Garamond. Se incorporaron las variantes Medium y Medium Italic de Cormorant y su licencia OFL. Los 23 adornos de papel, hilo, sellos, puntadas y separadores proceden de las exportaciones originales de Figma, convertidas completas a PNG a tres veces su tamaño. La aplicación los carga desde sus recursos, sin depender de enlaces temporales.

La bienvenida alterna las dos fotografías cada cuatro segundos de una frase de ocho: tres segundos inmóvil y uno de transición por pieza. Ambos indicadores leen la misma posición del carrusel. Los hilos avanzan y regresan, los sellos oscilan sobre su punto de suspensión y la fotografía de acceso tiene un acercamiento leve. Los valores y curvas siguen el contexto de movimiento de Figma. Las ocho frases forman el ciclo de 64 segundos, que se repite continuamente en Compose.

Las entradas de titulares y bloques están escalonadas una sola vez por visita. Los campos mantienen sus medidas y su semántica mientras aparecen, y no vuelven a desaparecer al cumplirse un ciclo decorativo. Se observa la preferencia de animaciones del sistema y MotionDurationScale: con movimiento reducido los contenidos se muestran inmediatamente, los adornos quedan quietos y la fotografía se puede cambiar manualmente. Los cambios del ajuste se observan durante la sesión. La navegación y las animaciones de controles existentes siguen la escala de movimiento de Compose.

El contenido se adapta al ancho, puede crecer con el tamaño de letra y se desplaza verticalmente. Los botones no cubren los campos: Page mide primero el pie y reserva el espacio de lectura. Se conserva la distribución lateral de acciones con teclado abierto en horizontal.

Por la instrucción posterior del usuario, el texto visible «Alias» se sustituyó por «Nombre de Usuario» en acceso, registro, perfil y mensajes. La propiedad `alias` de los modelos y la columna SQLite conservan su nombre para mantener compatibles las cuentas existentes. El orden visual de los iconos coincide con Figma sin cambiar sus identificadores persistidos.

El acceso y el registro usan LocalAppViewModel, LocalUseCases, RoomLocalRepository y RegisterWithPhotoUseCase. Se mantienen la cámara real, los permisos, la recuperación de la captura, el contacto opcional y los rechazos de credenciales incorrectas. La contraseña requiere de 8 a 20 caracteres, mayúscula, número y carácter especial; la confirmación debe coincidir. No se agregan servicios ni accesos simulados.

No se diseñaron ni añadieron pantallas de administrador. Las funciones de administración que ya existían mantienen sus rutas y permisos.

La autenticación y los datos continúan en SQLite en el dispositivo. La API, la sincronización entre teléfonos y las notificaciones push remotas siguen pendientes, según el alcance local acordado.

La guía de publicación está en `docs/CREAR_REPOSITORIO_GITHUB.txt`. Incluye instalación de herramientas, autenticación, revisión del commit, creación privada del repositorio, publicación y manejo de un remoto ya existente. Preparar esa guía no crea ni publica ningún repositorio.

## Validación

- 207 pruebas JVM aprobadas, sin fallos ni errores. Incluyen el ciclo de 64 segundos, sincronización del carrusel y sus indicadores, movimiento reducido del sistema, texto al 200% en un móvil de 320 × 640 dp, validación y recorridos completos de acceso y registro.
- Compilación de aplicación y pruebas aprobada. Lint sin errores: 29 advertencias y 5 sugerencias; incluyen avisos de recursos duplicados entre las exportaciones originales de Figma y código/dependencias del proyecto.
- Nueve pruebas físicas distintas aprobadas en el Motorola Edge 50 Fusion: seis de acceso y diseño y tres de regresión de contraseña, stock e indicador de carga. La prueba de carrusel se repitió después de configurar el reloj manual antes de crear InfiniteTransition; el modo automático del entorno de pruebas cancela intencionalmente las animaciones infinitas. No hubo cambios de código de producto entre ambas ejecuciones físicas.
- Se revisaron las capturas de bienvenida, ambas fotografías, acceso, registro, seguridad, iconos y teclado horizontal. La grabación de doce segundos de la bienvenida confirma también el movimiento con el reloj real del teléfono, fuera de instrumentación.
- La aplicación habitual se actualizó mediante `adb install -r`, abrió correctamente y mantuvo el identificador de usuario y la fecha de instalación original. Las dos instalaciones temporales se retiraron después de guardar la evidencia.

APK de entrega: `output/apk/LouAraya-editorial-2026-10-28.apk`.
Vista conjunta: `output/design/editorial-2026-10-28/tres-pantallas-motorola.png`.
Video: `output/design/editorial-2026-10-28/movimiento-motorola.mp4`.
Guía solicitada: `docs/CREAR_REPOSITORIO_GITHUB.txt`.
Registros de verificación: `tmp/acceso-editorial-2026-10-28/`, excluidos del repositorio junto con las entregas compiladas.
