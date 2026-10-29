# Correcciones de errores · 24 de octubre de 2026

Alcance: corrección de errores de navegación y revisión de los flujos existentes.

## Errores confirmados

### La flecha de retroceso podía ser consumida por otro manejador de Android

El encabezado de las solicitudes enviaba su pulsación al despachador global de Atrás. Si otro callback consumía ese evento, la flecha no cambiaba de pantalla.

Ahora el encabezado ejecuta directamente la acción de la pantalla. En una cotización, el segundo paso vuelve al primero conservando los datos; desde el primero se vuelve al detalle del producto. El botón Atrás de Android conserva el retroceso entre pasos.

Las pruebas reproducen un callback de Android que consume Atrás, tanto en Android 15 como en Android 16. El caso fallaba antes de la corrección.

### Un guardado tardío podía cerrar una pantalla distinta del editor

Recorrido reproducido: editar un producto, guardar con la base de datos temporalmente ocupada, salir con Atrás de Android y abrir Solicitudes. Al completarse el guardado, su callback ejecutaba un retroceso sobre la pantalla actual y sacaba al usuario de Solicitudes.

El regreso posterior al guardado ahora comprueba el identificador de la pantalla que inició la operación. El producto se guarda y la pantalla que el usuario eligió permanece abierta. La misma protección se aplica a los editores de colecciones, campañas y fotografías.

### Una consulta de QR o aviso podía abrir un destino después de salir

Se reprodujeron otros dos casos del mismo problema: consultar un QR y volver a Guardados antes de terminar la lectura, o abrir un aviso y pasar a Productos mientras la consulta estaba pendiente. Al completarse la operación, se abría el detalle del producto o de la solicitud sobre la pestaña recién elegida.

Ahora la apertura del destino también comprueba que sigue activa la pantalla de origen. Las dos pruebas fallaban antes del ajuste. Se protege además la apertura desde una notificación del sistema.

## Verificación

Ejecución completa final:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest --no-configuration-cache --console=plain
```

| Comprobación | Resultado |
|---|---|
| Suite JVM completa | 167 pruebas en 31 clases; 0 fallos, 0 errores y 0 omitidas |
| Análisis lint | 0 errores, 35 advertencias y 3 sugerencias; mismos totales que la preparación anterior |
| APK debug | Compilación aprobada |
| APK de pruebas Android | Compilación aprobada |
| Pruebas físicas | Pendientes: ADB no detectó dispositivos conectados |

Se añadieron 12 casos de prueba respecto de las 155 pruebas anteriores. Cubren el retroceso de solicitudes y cotizaciones, los callbacks que consumen Atrás, restauración de estado, regreso de fotografías conservando el borrador, cancelación de editores, guardados pendientes, consultas de QR y avisos, y la espera de registros de navegación. Los cuatro escenarios que reproducían errores fallaban antes de sus correcciones y pasan en la ejecución final.

APK generado: `app/build/outputs/apk/debug/app-debug.apk`.

Las pruebas usan datos ficticios en Robolectric y almacenamiento de prueba. No había un dispositivo conectado por ADB durante esta revisión.
