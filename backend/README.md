# Servicios de LouAraya

El backend del Control 3 contiene dos servicios Spring Boot. Identidad registra cuentas, autentica sesiones y gestiona el perfil. Taller conserva el catálogo, las colecciones, las campañas, las solicitudes, la bodega, los favoritos y los avisos. Taller consulta a Identidad para resolver cada sesión. Los roles y el dueño de una solicitud se resuelven en el servidor.

Ambos servicios usan PostgreSQL 17 y Flyway. El entorno de desarrollo usa una instancia de PostgreSQL con los esquemas `identity` y `taller`, cada uno con sus propias migraciones. La entrega de una solicitud confirma el estado, registra el movimiento y descuenta el stock en una transacción. Las escrituras de solicitudes y de bodega aceptan una clave de idempotencia por cuenta para resolver reintentos.

## Iniciar en Windows

Requisitos: JDK 17–25 en PATH, Docker Desktop iniciado y PowerShell 7. Maven Wrapper instala Maven automáticamente en la primera compilación.

Desde la raíz del repositorio, ejecuta:

```powershell
pwsh -File .\backend\start-local.ps1
```

El script crea una contraseña local de PostgreSQL en el archivo ignorado `backend/.env`, inicia el contenedor y compila ambos JAR. Los procesos se enlazan a `127.0.0.1` en los puertos 8081 y 8082. El script espera a que ambos servicios respondan. Usa `-SkipBuild` para reutilizar JAR ya compilados.

| Servicio | Puerto local | Comprobación |
| --- | --- | --- |
| Identidad | 8081 | `GET /actuator/health` |
| Taller | 8082 | `GET /actuator/health` y `GET /products` |
| PostgreSQL | 54329 | `docker compose --project-directory backend ps` |

Las cuentas ficticias del script son `cliente.demo`, `cliente2.demo` y `admin.demo`, con contraseña `LouDemo2026!`. Se crean en el primer inicio con `LOUARAYA_DEMO_PASSWORD` configurada. Puedes definir una contraseña diferente en el entorno antes de iniciar por primera vez. Para un despliegue real, la configuración base deja esta variable vacía. Mantén credenciales y Java KeyStores fuera del repositorio.

Para detener los servicios, identifica los dos procesos Java que imprime el script y ejecuta `Stop-Process -Id ID_IDENTIDAD,ID_TALLER`. Para detener PostgreSQL conservando los datos ejecuta `docker compose --project-directory backend stop`.

## Compilar y arrancar por separado

```powershell
Set-Location backend
.\mvnw.cmd -B -ntp package
```

Configura `LOUARAYA_DB_PASSWORD` en cada terminal e inicia `java -jar identity/target/identity-1.0.0.jar` y `java -jar taller/target/taller-1.0.0.jar`. Ambos usan la base de Compose por defecto. Los puertos se cambian con `LOUARAYA_IDENTITY_PORT` y `LOUARAYA_TALLER_PORT`. Configura `LOUARAYA_IDENTITY_URL` en Taller si cambias la URL de Identidad. `LOUARAYA_IDENTITY_DB_URL`, `LOUARAYA_TALLER_DB_URL` y `LOUARAYA_DB_USER` permiten usar otra instancia de PostgreSQL, con los esquemas indicados. Fuera de la máquina de desarrollo, usa HTTPS para las conexiones que transportan credenciales y sesiones.

## API

Las operaciones autenticadas usan `Authorization: Bearer <token>`. El login y registro devuelven `{token, profile}`. El token expira en 12 horas y el cierre de sesión revoca ese token. Los errores de aplicación devuelven `{message, fieldErrors}` con su código HTTP.

| Servicio | Operaciones |
| --- | --- |
| Identidad | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /profiles/me`, `PATCH /profiles/me` |
| Catálogo | `GET /products`, `GET /products/{id}`, `POST /products`, `PATCH /products/{id}`, `DELETE /products/{id}` |
| Solicitudes | `GET /requests`, `GET /requests/{id}`, `POST /requests`, `PATCH /requests/{id}/status`, `PATCH /requests/{id}/agreement`, `POST /requests/{id}/cancel`, `POST /requests/dismiss` |
| Bodega | `GET /stock-movements`, `POST /products/{id}/stock-movements` |
| Colecciones y campañas | `GET/POST /collections`, `PATCH /collections/{id}`, `GET/POST /campaigns`, `PATCH /campaigns/{id}` |
| Datos del perfil | `GET /snapshot`, `PUT /favorites/products/{id}`, `PUT /favorites/collections/{id}`, `PATCH /notices/{id}/read`, `PATCH /notices/{id}/delivered`, `POST /notices/dismiss` |
| Avisos push optativos | `PUT /push/devices/{installationId}` y `DELETE /push/devices/{installationId}` |

El catálogo público muestra piezas activas. La baja desde el taller archiva la pieza para conservar solicitudes e historial. El cuerpo de creación de solicitud incluye `productId`, `intention`, `displayedPrice`, `quantity` e `idempotencyKey`. La cotización acepta `details` con medidas, material, colores, tipo de evento y fecha. El servidor confirma el precio vigente antes de calcular el total. Una entrada o salida de stock manual incluye `quantity`, `type`, `note` e `idempotencyKey`. El servidor comprueba saldo y permisos. Los avisos compartidos se consultan en `/snapshot`.

## Activar avisos de campaña en el emulador

Usa `pwsh -File .\backend\start-local.ps1 -WithPush` y configura el distribuidor ntfy del emulador con `http://10.0.2.2:8090`. El inicio con `-WithPush` habilita el perfil de Compose y configura Taller para enviar al broker local. Para un arranque manual, define `LOUARAYA_PUSH_ORIGIN` como el origen que conoce el distribuidor y `LOUARAYA_PUSH_TRANSPORT` como el origen fijo al que se conecta Taller.

La cuenta de cliente registra una instalación con `{endpoint, publicKey, authSecret}`. Taller limita los receptores al origen configurado y al formato de tópico de UnifiedPush. Al publicar una campaña, guarda el aviso y, cuando se confirma la transacción, envía a las instalaciones que coinciden con la categoría. El envío asíncrono cifra únicamente el identificador del aviso con RFC 8291. Si el broker falla, la publicación y el aviso en la bandeja se conservan. [UnifiedPush en ntfy](https://docs.ntfy.sh/publish/#unifiedpush)

La comprobación del 18 de noviembre publicó una campaña en una instancia temporal de Taller con PostgreSQL. Un receptor de prueba se suscribió a ntfy local, descifró el identificador del aviso y confirmó que correspondía a la campaña guardada. Las pruebas de Taller también verifican que un error del distribuidor conserva el aviso, que se respetan los destinatarios y que el transporte no sigue redirecciones.

## Verificación

Ejecuta `.\mvnw.cmd -B -ntp test` en `backend`. Las pruebas MockMvc usan H2 aislada en modo PostgreSQL y cubren registro y roles, revocación de sesiones, CRUD, acceso por dueño, stock, reintentos y avisos.

Con los servicios locales arriba, `pwsh -File .\backend\verify-postgres.ps1` crea una base temporal en PostgreSQL, ejecuta un recorrido HTTP con los tres roles y reinicia su propio proceso Taller para verificar persistencia. Usa una instancia temporal para dejar disponibles los puertos de la aplicación.

La verificación del 18 de noviembre de 2026 pasó con PostgreSQL 17.12. Después de reiniciar Taller, la pieza de prueba conservó el precio de $27.000, dos unidades de stock y su baja lógica. La solicitud siguió entregada por $54.000 con una sola salida de dos unidades. Se comprobaron los rechazos por rol, acceso desde otra cuenta y stock insuficiente, así como la repetición de las operaciones con la misma clave. También pasaron las tres pruebas de Identidad y las seis de Taller.
