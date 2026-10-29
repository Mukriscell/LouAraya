# LouAraya

Aplicación Android para explorar un catálogo de macramé y registrar solicitudes de compra, reserva y cotización. Incluye funciones para clientes y para la administración del taller Lou Araya.

## Datos de la entrega

- Estudiante: **Lucas Vasquez**.
- Asignatura: **DSY1105 · Desarrollo de Aplicaciones Móviles**.
- Evaluación: **Parcial 2 · Encargo**.
- Plataforma: Android, desde Android 7.0 (API 24).

## Funcionalidades

- Registro e inicio de sesión con nombre de usuario y contraseña, icono o fotografía de perfil.
- Catálogo con búsqueda, filtros por categoría, detalle de piezas y colecciones.
- Favoritos de productos y colecciones asociados a cada cliente.
- Solicitudes de compra, reserva y cotización personalizada con seguimiento de estado.
- Administración de productos, fotografías, colecciones, campañas y solicitudes.
- Bodega por producto con entradas, ventas directas, retiros e historial; descuento al entregar compras o reservas.
- Registro del importe acordado y consulta de resultados de campañas.
- Lectura de etiquetas QR desde cámara, imagen o código ingresado manualmente.
- Mapa de ferias con OpenStreetMap, listado y apertura de indicaciones externas.
- Bandeja de avisos por usuario y notificaciones locales de Android.
- Transiciones de navegación, entradas escalonadas del catálogo, respuesta al pulsar botones, favoritos animados y cambios de saldo en bodega.

Los datos de negocio se guardan en el dispositivo. Esta entrega no sincroniza información entre teléfonos ni procesa pagos. Las notificaciones se generan localmente.

## Tecnologías y estructura

Kotlin, Jetpack Compose, Material 3, Navigation Compose, ViewModel, StateFlow, Room/SQLite, CameraX, ML Kit y osmdroid.

La aplicación utiliza MVVM con repositorios y casos de uso:

- app/src/main/java/cl/duoc/louaraya/data: base de datos, DAO, repositorios, imágenes y seguridad.
- app/src/main/java/cl/duoc/louaraya/domain: modelos, contratos, casos de uso y validaciones.
- app/src/main/java/cl/duoc/louaraya/presentation: pantallas, navegación y ViewModels.
- app/src/main/java/cl/duoc/louaraya/ui/theme: colores, tipografía y tema.
- app/src/test: pruebas JVM y recorridos con Robolectric.
- app/src/androidTest: pruebas en dispositivo o emulador.

Room conserva perfiles, sesión, catálogo, favoritos, solicitudes, campañas, avisos, recorridos y movimientos de bodega. Las fotografías se guardan en archivos privados. La base de datos está en la versión 10 e incluye las migraciones desde la versión 1.

## Abrir y ejecutar

1. Extraer la carpeta completa LouAraya.
2. Abrir esa carpeta desde **Open** en Android Studio.
3. Instalar Android SDK Platform 37 y aceptar las licencias solicitadas por el IDE.
4. Configurar JDK 25 para Gradle y sincronizar. El proyecto declara esa versión en gradle/gradle-daemon-jvm.properties; Gradle puede descargarla automáticamente.
5. Conectar un dispositivo con depuración USB o iniciar un emulador con API 24 o superior.
6. Ejecutar la configuración app.

Android Studio genera local.properties con la ruta del SDK de cada equipo. No se incluye una ruta de SDK personal en la entrega. La primera sincronización requiere Internet para descargar Gradle y dependencias.

Desde PowerShell, con Java y el SDK configurados:

    .\gradlew.bat assembleDebug
    .\gradlew.bat testDebugUnitTest lintDebug

APK generado: app/build/outputs/apk/debug/app-debug.apk.

Configuración Android: minSdk 24, compileSdk 37, targetSdk 37.

## Cuentas de demostración

| Perfil | Nombre de Usuario | Contraseña |
|---|---|---|
| Cliente | cliente.demo | LouDemo2026! |
| Administrador | admin.demo | LouDemo2026! |

Son cuentas ficticias precargadas en el primer inicio. Desde Perfil se puede cerrar sesión y cambiar de usuario. El registro crea nuevas cuentas de cliente.

El acceso público tiene tres pantallas independientes: bienvenida, inicio de sesión y registro. Comenzar abre el acceso; Crear cuenta abre el registro. Las sesiones activas entran directamente al panel del rol correspondiente. Los [mockups editables de Figma](https://www.figma.com/design/f5c62e52GgJRSzDqlfzMXG) documentan el recorrido y su identidad visual.

## Formularios y estado

Las reglas se ejecutan en la capa de dominio y los errores llegan a la interfaz mediante el ViewModel. Los campos muestran borde, mensaje e ícono de error. Al corregir un campo se retira su aviso; al guardar se valida nuevamente.

- Registro: nombre de 2–80 caracteres, alias único de 3–40 caracteres, contraseña de 8–20 caracteres con al menos una mayúscula, un número y un carácter especial, y confirmación coincidente.
- Contacto: opcional, hasta 100 caracteres.
- Productos: nombre, material, dimensiones positivas y finitas, descripción de hasta 2.000 caracteres y precio entero no negativo.
- Colecciones y campañas: nombre, referencias existentes y fechas válidas con fin igual o posterior al inicio.
- Cotizaciones: medidas positivas, material, colores, evento y fecha desde el día actual; observaciones de hasta 500 caracteres.
- Importe acordado: entero entre 0 y 9.999.999.999 CLP.

Los formularios conservan sus valores al recrear la pantalla. Los cambios persistidos actualizan las vistas mediante Flow/StateFlow y las operaciones muestran estados de carga, error o confirmación.

## Recursos nativos

- **Cámara:** fotografía de perfil y de producto, y lectura de QR. El permiso se solicita cuando se usa la función. Se puede cancelar sin reemplazar la foto guardada.
- **Selector de fotos:** selección de imagen para productos o lectura de QR sin acceso general al almacenamiento.
- **Notificaciones Android:** avisos de solicitudes, cambios de estado y campañas para el perfil activo. En Android 13 o superior se solicita autorización; si se deniega, permanece disponible la bandeja interna.

Las fotos se confirman antes de guardarse. Se comparten con la cámara mediante FileProvider y permisos temporales.

## QR y mapa

Etiquetas de ejemplo para los productos precargados:

    louaraya://product/1
    louaraya://product/2

Para colecciones se usa louaraya://collection/ID, con el identificador de una colección existente. El lector acepta imágenes de hasta 20 MB y permite reintentar o escribir el código manualmente.

El mapa no requiere clave de API ni facturación. Los mosaicos nuevos necesitan Internet; el listado de ferias ficticias permanece disponible localmente. La app no solicita ubicación para consultar eventos. “Cómo llegar” abre una aplicación o página externa de mapas.

## Pruebas

    .\gradlew.bat testDebugUnitTest
    .\gradlew.bat assembleDebugAndroidTest
    .\gradlew.bat connectedDebugAndroidTest

La última orden requiere un teléfono o emulador de prueba y puede crear cuentas, fotografías y solicitudes ficticias en esa instalación. Los reportes se generan en app/build/reports/. Los resultados de la preparación de entrega están en [Verificación](docs/VERIFICACION_ENTREGA.md).

## Documentación

- [Caso del proyecto](<documentacion/037 SEC005D_Formato_de_Caso_Lou_Araya.pdf>).
- [Alcance funcional](docs/MVP_LOCAL.md).
- [Matriz de funcionalidades](docs/MATRIZ_FUNCIONAL_REQUERIMIENTOS.md).
- [Gestión de stock y bodega](docs/STOCK_2026-10-25.md).

Las licencias de las fuentes y los íconos están en app/src/main/res/raw/. Los avisos del Gradle Wrapper están en licenses/.

La revisión editorial de las tres pantallas de acceso, el carrusel y el movimiento reducido está documentada en [Acceso editorial](docs/ACCESO_EDITORIAL_2026-10-28.md). Para publicar el código, sigue [Crear repositorio en GitHub](docs/CREAR_REPOSITORIO_GITHUB.txt).

La [splash y la limpieza por perfil](docs/SPLASH_LIMPIEZA_2026-10-28.md) añaden la entrada animada y acciones para vaciar solicitudes y notificaciones de la cuenta actual conservando el historial de negocio.

La revisión de [cantidad y cancelación de solicitudes](docs/SOLICITUDES_CANTIDAD_CANCELACION_2026-10-28.md) sustituye la limpieza de solicitudes del cliente por cancelación, permite indicar unidades y utiliza una papelera para limpiar avisos.
