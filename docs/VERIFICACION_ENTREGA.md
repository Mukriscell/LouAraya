# Verificación de entrega

Estudiante: Lucas Vasquez. Proyecto: LouAraya. Fecha: 20 de octubre de 2026.

## Cambios incluidos

- Reglas de confirmación de contraseña y conversiones numéricas trasladadas a dominio.
- Reglas de medidas, material y colores de cotización compartidas entre pasos y guardado.
- Ícono de error con descripción accesible en los campos inválidos.
- Selectores de las pruebas de pestañas actualizados a los textos actuales.
- README y documentación funcional preparados para abrir y demostrar el proyecto.
- Limpieza de comentarios de código y archivos auxiliares de desarrollo.
- Licencias de fuentes, íconos y Gradle Wrapper conservadas.

## Resultados

| Comprobación | Resultado |
|---|---|
| Compilación de aplicación y pruebas Android | Aprobada |
| Pruebas JVM | 155 pruebas, 30 clases, 0 fallos, 0 errores y 0 omitidas |
| Análisis estático lint | 0 errores, 35 advertencias y 3 sugerencias |
| Compilación final del APK | Aprobada tras la limpieza de fuentes y recursos |
| Revisión de fuentes | 163 archivos Kotlin y Gradle Kotlin sin comentarios de código |
| Pruebas físicas de esta preparación | No completadas: el Motorola estaba bloqueado y se interrumpió el intento |

Las pruebas JVM incluyen reglas de validación, persistencia con Room, recuperación de imágenes, cambios de estado y recorridos de la interfaz. Se añadieron casos para confirmación de contraseña sin crear cuentas ni importar fotos inválidas, medidas decimales, valores no finitos y errores numéricos. La interfaz comprueba además que el ícono de error aparece y desaparece al corregir el precio.

Las advertencias de lint corresponden a versiones de dependencias/herramientas, recursos sin uso y recomendaciones de estilo; no hay errores que impidan compilar.

La comprobación física utiliza una instalación temporal separada para no modificar los datos de uso habitual. La primera ejecución de esta preparación se interrumpió al detectar la pantalla bloqueada; ese intento no se contabiliza como una prueba aprobada ni como un defecto de la aplicación.

Los archivos CSV incluidos en la carpeta verificacion del paquete detallan las pruebas JVM y el análisis estático. El APK de la carpeta apk es una compilación debug instalable para la demostración académica.
