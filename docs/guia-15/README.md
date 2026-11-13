# Guía 15 aplicada a LouAraya

Las pruebas unitarias usan Kotest, MockK y kotlinx-coroutines-test. La prueba de interfaz usa JUnit Jupiter y Compose UI Test con la pantalla de solicitudes de la aplicación. JUnit Vintage permite seguir ejecutando las pruebas JVM de JUnit 4.

## Ejecución

En Android Studio, sincroniza Gradle y ejecuta `testDebugUnitTest` desde la ventana Gradle. Para instrumentación, inicia un dispositivo de prueba con Android 15 y ejecuta estos comandos desde la terminal de la raíz:

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=cl.duoc.louaraya.presentation.RequestsScreenJupiterTest"
```

El registro adjunto de la prueba Jupiter se obtuvo con los APK de depuración y el mismo AndroidJUnitRunner:

```powershell
adb shell am instrument -w -r -e runnerBuilder de.mannodermaus.junit5.AndroidJUnitFrameworkBuilder -e class cl.duoc.louaraya.presentation.RequestsScreenJupiterTest cl.duoc.louaraya.test/androidx.test.runner.AndroidJUnitRunner
```

## Evidencias

- [Reporte de las 227 pruebas JVM](reporte-jvm.png)
- [Salida de Gradle](pruebas-jvm.txt)
- [Prueba JUnit 5 de la pantalla de solicitudes](RequestsScreenJupiterTest.txt)
- [Pruebas de cuentas, catálogo y avisos](RequirementsAuditTest.txt)
- [Pruebas de cantidad, cancelación y bodega](RequestQuantityDeviceTest.txt)
- [Pruebas de consulta y navegación QR](QrReturnNavigationTest.txt)
- [Captura del formulario con tres unidades](cantidad-y-total.png)

Las pruebas instrumentadas usan datos ficticios. La ejecución documentada cubre las cuatro clases listadas arriba.
