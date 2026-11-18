# Prototipo de solicitudes compartidas de Lou Araya

**Lou Araya · Prototipo** usa un identificador Android propio. Instala el APK junto a la versión habitual. El paquete incluye una pantalla de cliente y otra de taller. Usa las dos piezas ficticias del servidor de muestra.

## Probar por Internet

El APK remoto trae una dirección HTTPS para el servidor de demostración. Abre el APK en Android, elige `cliente.demo` o `admin.demo` y usa la contraseña facilitada con la descarga. El acceso funciona mientras el equipo anfitrión y el túnel estén activos y tengan Internet. Si se reinicia el túnel, escribe la nueva dirección HTTPS en el campo **Servidor**.

Para crear una versión con otra dirección, ejecuta `.\gradlew.bat :app:assemblePrototype "-PlouArayaSyncServer=https://servidor-ejemplo"`. Define `LOUARAYA_PROTOTYPE_PASSWORD` en el entorno del servidor antes de exponer la demo por HTTPS.

## Probar con el PC

1. Activa Depuración USB en Android, conecta el teléfono al PC y acepta la autorización.
2. Abre PowerShell en la raíz del proyecto y ejecuta `pwsh -File .\prototype\start.ps1`. Mantén abierta esa terminal. El script usa Python 3 y ADB de Android Studio.
3. Descarga el APK en Android, ábrelo e instálalo. Android puede pedir autorización de instalación a la aplicación que abrió el archivo.
4. Abre **Lou Araya · Prototipo** y escribe `localhost` en **Servidor**. Entra como `cliente.demo`, elige una pieza y envía una solicitud.
5. En otra instalación entra como `admin.demo` y pulsa **Confirmar solicitud**. La pantalla del cliente actualiza el estado automáticamente mientras esté abierta. También puedes pulsar **Actualizar**.

Si inicias el servidor sin configurar otra clave, las cuentas de demostración usan `LouDemo2026!`. La cuenta `cliente2.demo` sirve para revisar que cada cliente ve sus propias solicitudes. Con un solo Android, **Cambiar cuenta** permite recorrer ambos roles. Si conectas otro dispositivo después de iniciar el servidor, ejecuta `adb -s <serie> reverse tcp:8765 tcp:8765`. Puedes ver la serie en `adb devices`.

El prototipo también admite una IP privada del PC. Para una prueba por Wi-Fi, inicia `python prototype/server.py --host <IP-del-PC>`, usa esa misma dirección en el APK y conecta el teléfono a la misma red. Esa opción requiere que el PC permita conexiones entrantes al puerto TCP 8765.

El servidor calcula el total, comprueba el rol y guarda las solicitudes en SQLite bajo `output/prototype/`. El prototipo cubre creación, consulta y confirmación de solicitudes de compra con datos ficticios. La ejecución local usa HTTP en la red de pruebas. Para compilar el APK local ejecuta `.\gradlew.bat :app:assemblePrototype`.
