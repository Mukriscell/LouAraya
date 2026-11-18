# Desarrollo de LouAraya para el Control 3

La app principal usa Retrofit para comunicarse con Identidad y Taller cuando se configuran las dos URL. Identidad registra cuentas, verifica credenciales, administra sesiones y actualiza el perfil. Taller mantiene el catálogo, las colecciones, campañas, solicitudes, favoritos, avisos y bodega en PostgreSQL. La app conserva las pantallas Compose. El prototipo de Python queda disponible como variante de referencia.

## Iniciar los servicios y el emulador

El backend tiene su propio Maven Wrapper. Desde la raíz, con Docker Desktop, PowerShell 7 y el JDK configurados, sigue el procedimiento de [backend/README.md](../backend/README.md). Los servicios locales escuchan en `127.0.0.1:8081` y `127.0.0.1:8082`. Inicia el emulador de Android Studio y ejecuta:

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest `
  '-PlouArayaIdentityUrl=http://10.0.2.2:8081/' `
  '-PlouArayaTallerUrl=http://10.0.2.2:8082/'
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell am start -n cl.duoc.louaraya/.MainActivity
```

`10.0.2.2` es la dirección del anfitrión accesible desde el emulador. `debug` permite HTTP solo para esa dirección. Para las pruebas JVM con Room, deja ambas URL sin configurar. [Red del emulador Android](https://developer.android.com/studio/run/emulator-networking-address)

Las cuentas `cliente.demo`, `cliente2.demo` y `admin.demo` se cargan cuando se habilitan los datos de demostración en el backend. Usa la contraseña ficticia que configures en el arranque. En el taller puedes crear, editar y dar de baja una pieza. El cliente puede generar una solicitud y consultar su estado. El saldo baja al entregar una compra o reserva, en la misma transacción que registra el movimiento de bodega.

En **Inicio → Ver ferias**, selecciona una feria para consultar el pronóstico con Retrofit en `GET https://api.open-meteo.com/v1/forecast`. LouAraya muestra la fecha de la feria cuando está dentro del horizonte de la API. Para las ferias fuera del horizonte o sin fecha válida, muestra el pronóstico de hoy para la misma ubicación, con la fecha consultada. La ficha y las indicaciones siguen disponibles si falla Open-Meteo. [Documentación de Open‑Meteo](https://open-meteo.com/en/docs)

### Avisos de campaña con la app cerrada

Arranca los servicios con `pwsh -File backend/start-local.ps1 -WithPush`. Esto habilita ntfy en `127.0.0.1:8090`, con la dirección `http://10.0.2.2:8090` para el emulador. Instala el APK oficial de ntfy en el emulador, permite sus notificaciones y pon esa dirección en **Settings → Default server**. [Distribuidor ntfy para Android](https://docs.ntfy.sh/subscribe/phone/#unifiedpush) · [APK oficial](https://github.com/binwiederhier/ntfy-android/releases/tag/v1.25.2)

En LouAraya entra como cliente, habilita **Recibir avisos** y pulsa **Activar avisos de campañas con ntfy**. Taller guarda el aviso al publicar la campaña y envía a los dispositivos registrados un identificador cifrado con Web Push. LouAraya verifica el aviso con la sesión del cliente antes de mostrar la notificación de Android. El perfil permite desactivar la suscripción y cerrar sesión la desconecta.

Para reproducir la prueba, deja activa la suscripción del cliente y comprueba en Android que ntfy está escuchando notificaciones. Pulsa Inicio y usa `adb -s emulator-5554 shell am kill cl.duoc.louaraya.control3`. Publica una campaña de prueba desde otra sesión de taller. Comprueba que Android muestre el aviso antes de volver a abrir LouAraya. El APK de depuración usa el paquete `cl.duoc.louaraya`. [Protocolo UnifiedPush](https://unifiedpush.org/developers/intro/)

El APK firmado recibió un aviso real de campaña con el proceso cliente detenido. Taller registró la entrega en la bandeja de la cuenta. La evidencia del recorrido está en `output/control3/push-android-proof.json` y `output/control3/push-campana-emulador.png`. Si ntfy aún está conectándose después de reiniciar Android, el aviso se conserva en la bandeja de Taller y aparece al sincronizar.

## Pruebas y cobertura

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:reportControl3LogicCoverage '-PlouArayaCoverage=true'
.\gradlew.bat :app:verifyControl3LogicCoverage '-PlouArayaCoverage=true'
```

JaCoCo mide las líneas de casos de uso, repositorios, seguridad, mapeo, integración remota, pronóstico, ViewModels y lógica de avisos. El reporte se guarda en `app/build/reports/jacoco/reportControl3LogicCoverage/`. La comprobación falla si la cobertura del conjunto es inferior al 80 %. Las pruebas instrumentadas verifican las pantallas Compose y los servicios de entrega de Android.

Para verificar el recorrido conectado, instala los dos APK de depuración compilados con las URL anteriores y ejecuta la clase optativa contra el backend de prueba:

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e control3 true `
  -e class cl.duoc.louaraya.presentation.Control3ConnectedDeviceTest `
  cl.duoc.louaraya.test/androidx.test.runner.AndroidJUnitRunner
```

## APK firmado

Para un entorno con servicios HTTPS accesibles, utiliza `scripts/Build-Control3Release.ps1` con `-IdentityUrl` y `-TallerUrl`. El script crea una llave académica en `output/control3/private/`, verifica el certificado y guarda `output/control3/LouAraya-Control3.apk`. Conserva el archivo `.jks` y su contraseña por el canal acordado con el docente. [Firma y verificación Android](https://developer.android.com/tools/apksigner)

Para probar un APK firmado en el emulador con los servicios locales, ejecuta `python scripts/control3_emulator_tls.py` con OpenSSL en `PATH`, o indica su ruta con `--openssl`. Mantén el puente en ejecución y compila:

```powershell
pwsh -File scripts/Build-Control3Release.ps1 `
  -IdentityUrl https://10.0.2.2:8441/ -TallerUrl https://10.0.2.2:8442/ `
  -EmulatorTlsResources output/control3/emulator-tls-res
adb -s emulator-5554 install -r output/control3/LouAraya-Control3.apk
adb -s emulator-5554 shell am start -n cl.duoc.louaraya.control3/cl.duoc.louaraya.MainActivity
```

La variante firmada para el emulador usa el identificador `cl.duoc.louaraya.control3`, así se puede instalar junto al APK habitual. La confianza en el certificado local queda limitada a `10.0.2.2`. Para un APK que funcione fuera del emulador, se necesitan las URL HTTPS accesibles desde el dispositivo de destino.

### Instalar el APK evaluado en otro emulador

Arranca Identidad y Taller en el PC del emulador como se indica arriba. Recibe por el canal privado los tres archivos TLS correspondientes a ese APK (`ca.pem`, `server.pem` y `server-key.pem`) y guárdalos en `output/control3/private/evaluacion-tls/`. Mantén el puente en ejecución e instala el APK firmado recibido.

```powershell
python scripts/control3_emulator_tls.py --tls-directory output/control3/private/evaluacion-tls
```

En otra terminal, instala y abre `LouAraya-Control3.apk` con los comandos anteriores. Con estos tres archivos el puente puede arrancar sin OpenSSL ni la clave de la CA. Genera certificados nuevos solo cuando vayas a generar y firmar otro APK con la nueva CA.

## Evidencia para la pauta del encargo

La revisión de entrega corrigió tres casos que podían afectar el recorrido comercial.

- El formulario mantiene visible el botón completo de compra, reserva o cotización cuando el teclado reduce el espacio disponible.
- Una solicitud o un movimiento de stock con respuesta incierta conserva su referencia de reintento aunque venza la sesión. Al volver a entrar, el servidor puede reconocer el reintento y evitar un duplicado.
- Una campaña dirigida por categoría también llega a los clientes que guardaron la colección anunciada. La bandeja y el envío push aplican la misma regla.

La revisión pasó tres pruebas en un Motorola Edge 50 Fusion con Android 16. La variante de prueba conectada por ADB completó una reserva en horizontal con el botón visible, el recorrido de catálogo y solicitudes con cambio de estado y stock, y la lectura de una etiqueta QR. El APK firmado del paquete está configurado para el emulador.

| Indicador | Peso | Evidencia |
| --- | --- | --- |
| IE 3.1.1 · Pantallas, formularios y navegación | 15 % | Se verificaron 16 casos en el emulador entre la ejecución inicial y las repeticiones de casos de interfaz. Incluyen acceso, registro, perfil, favoritos, cámara, QR, Ferias, formularios comerciales, editores del taller, avisos y el formulario de reserva en horizontal con teclado. |
| IE 3.1.2 · Microservicios | 15 % | Nueve pruebas de Spring Boot aprobadas. `backend/verify-postgres.ps1` verificó el CRUD, los permisos, los reintentos y un único descuento de stock. PostgreSQL conservó la baja de la pieza y la solicitud entregada tras reiniciar Taller. |
| IE 3.1.3 · CRUD conectado | 15 % | `Control3ConnectedDeviceTest` recorre alta, edición y baja, ingreso de cinco unidades, compra de dos, confirmación y entrega. Comprueba saldo de tres unidades, un único movimiento y aislamiento entre cuentas. |
| IE 3.1.4 · API externa | 15 % | Retrofit consulta Open-Meteo y muestra temperaturas y probabilidad de lluvia en la ficha de Ferias. El APK firmado comprobó el pronóstico en vivo para una feria próxima y el pronóstico de hoy para la ubicación de una feria pasada. Las pruebas ejercitan la consulta por fecha y el manejo de errores. |
| IE 3.2.1 · Pruebas unitarias | 15 % | Pasaron 245 pruebas JVM. JaCoCo midió 1608 de 1980 líneas de lógica Android (81,21 %) y `verifyControl3LogicCoverage` aprobó el umbral del 80 %. |
| IE 3.3.1 · APK firmado | 10 % | El APK release usa el JKS académico. `apksigner` valida la firma v2 y `keytool` identifica el mismo certificado en el almacén. |
| IE 3.3.2 · GitHub y Trello | 15 % | El repositorio de origen es público. La rama local `codex/control-3` contiene los commits de microservicios e integración Android. Falta acreditar en Trello las tareas distribuidas y asociarlas con los commits técnicos de los dos integrantes. |

La pauta de entrega también solicita los datos de ambos integrantes y el número de equipo, el paquete grupal en AVA y la activación individual de la entrega. Consulta [requisitos del encargo](PLAN_CONTROL_3.md#entrega-del-encargo).

El RAR de entrega reúne el código, el APK firmado, el informe JaCoCo y las evidencias. `LEEME_PRIMERO.md`, en la raíz del archivo, indica cómo ejecutar el APK. El JKS académico y los archivos TLS están en `04_Archivos_privados.rar`. Comunica su contraseña por el canal acordado con el docente.
