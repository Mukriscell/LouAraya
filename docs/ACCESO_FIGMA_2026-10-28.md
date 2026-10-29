# Separación del acceso de LouAraya

> Registro de la primera composición visual. La implementación editorial vigente y sus nodos de Figma se describen en [Acceso editorial](ACCESO_EDITORIAL_2026-10-28.md).

## Alcance acordado

Crear mockups editables en Figma y después implementar tres pantallas distintas: bienvenida, inicio de sesión y registro. El PDF de pantallas aportado sirve como referencia flexible. Se conserva el almacenamiento local y la identificación de permisos mediante la cuenta autenticada.

## Recorrido

- Sin sesión, la bienvenida muestra la identidad artesanal, una fotografía textil y las acciones Comenzar y Crear cuenta. No contiene campos de credenciales.
- Comenzar abre el inicio de sesión con alias y contraseña. Desde allí se puede ir al registro o volver a la bienvenida.
- El registro mantiene nombre, alias, contacto opcional, contraseña, confirmación y elección de avatar o fotografía. Conserva la política de contraseña de 8 a 20 caracteres, mayúscula, número y símbolo.
- Ya tengo cuenta conduce al inicio de sesión. Atrás respeta el origen del registro.
- Una cuenta autenticada conserva el acceso directo a su panel. Cerrar sesión devuelve al recorrido público.

## Dirección visual

Se mantienen Cormorant Garamond para titulares, DM Sans para formularios y la paleta crema, terracota y verde del producto. La bienvenida tiene una composición fotográfica protagonista; el acceso y el registro priorizan la lectura y la escritura. Los adornos no son controles interactivos ni deben interrumpir la accesibilidad.

## Movimiento previsto

Entrada breve de las palabras del titular; aparición y giro leve del nudo y de elementos decorativos; acercamiento sutil de la fotografía. Formularios, controles y campos mantienen su semántica, foco y tamaño táctil. Las animaciones decorativas no bloquean la navegación ni introducen esperas artificiales.

## Preparación verificada

El PDF se leyó completo y se revisó visualmente la página que muestra las pantallas de bienvenida, registro e inicio de sesión.

Los tres mockups se crearon en el equipo Desarrollo Lucas: [LouAraya · Bienvenida, acceso y registro](https://www.figma.com/design/f5c62e52GgJRSzDqlfzMXG). Los nodos son 3:2422 (bienvenida), 3:2449 (acceso) y 3:2505 (registro). Incluyen textos editables, instancias del kit Material 3, fotografías originales del proyecto, los avatares vectoriales y un nuevo adorno de hilo creado en Figma. Se verificaron las familias Cormorant Garamond y DM Sans y se corrigieron los recortes de filas antes de implementar.

## Implementación

AuthScreens contiene la bienvenida y el acceso. El registro conserva su cámara, fotografía y selección de iconos. AuthNavigation inicia sin sesión en welcome y conserva las rutas login y register como destinos distintos. Ya tengo cuenta respeta un acceso anterior o abre login desde la bienvenida.

AuthMotion traslada las entradas por palabras de 620 ms, el acercamiento de la fotografía de 750 ms, el giro inicial del nudo de 700 ms y el adorno de 600 ms. Usa un reloj compartido de Compose y la curva de Figma (0, 0, 0.58, 1). La aplicación reproduce la entrada una vez al abrir cada pantalla, para que los títulos no vuelvan a desaparecer mientras se completa el formulario. Las escalas de animación del sistema se aplican mediante Animatable. El texto mantiene una sola etiqueta accesible y sus medidas, mientras cada palabra se dibuja con su opacidad y desplazamiento.

El recurso auth_fiber_flourish.png se exportó directamente desde el nodo de adorno de Figma. Las fotografías, el nudo y los avatares coinciden con los recursos existentes que se usaron en los mockups. No hay referencias temporales de Figma en el código de ejecución ni nuevas dependencias.

## Verificación final

- 201 pruebas JVM aprobadas, sin fallos ni errores. La suite completa está registrada en verificacion-final.txt y en los XML de testDebugUnitTest.
- Compilación de la aplicación y del APK de pruebas aprobada. Lint terminó sin errores, con 27 advertencias y 3 sugerencias.
- 7 pruebas físicas aprobadas en el Motorola Edge 50 Fusion: separación de las pantallas y borradores, roles y sesión persistente, comparación de fotogramas del título, teclado en horizontal, requisitos de contraseña, indicador de carga y stock al entregar sin duplicar el descuento.
- Tras corregir el fondo del adorno exportado se repitió y aprobó el recorrido público en el teléfono y se revisó la nueva captura de inicio de sesión. La corrección solo cambia el recurso gráfico.
- Se revisaron las capturas de bienvenida, acceso, registro, teclado en horizontal y dos momentos de la entrada del título. Los resultados, 6 capturas de acceso y 9 de regresión están en tmp/acceso-figma-2026-10-28.

La comprobación usó una instalación separada con datos ficticios. Después se actualizó la aplicación habitual mediante instalación conservando datos, se verificó su apertura y se retiraron los dos paquetes temporales.

Entregables locales: output/apk/LouAraya-acceso-2026-10-28.apk y output/design/LouAraya-acceso-mockups.png. Los mockups editables permanecen en el equipo Desarrollo Lucas de Figma.
