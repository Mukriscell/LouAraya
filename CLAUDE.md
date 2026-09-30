# LouAraya - Instrucciones para Claude Code

## 1. Contexto del proyecto

LouAraya es una aplicación móvil Android académica desarrollada para la asignatura
DSY1105 - Desarrollo de Aplicaciones Móviles.

El caso corresponde a Lou Araya Macramé SPA, un emprendimiento chileno de diseño
textil artesanal premium.

La documentación oficial del caso se encuentra en:

documentacion/037 SEC005D_Formato_de_Caso_Lou_Araya.pdf

Ese documento es la fuente principal de requisitos funcionales y restricciones.

No modificar, mover ni eliminar archivos dentro de documentacion/ sin autorización.

---

## 2. Objetivo del MVP

Construir una aplicación Android que permita principalmente:

- Explorar un catálogo de productos de macramé.
- Visualizar información detallada de los productos.
- Explorar productos organizados por campañas o categorías.
- Guardar productos o colecciones de interés.
- Registrar intenciones de compra o reserva.
- Consultar el estado de solicitudes.
- Permitir funciones administrativas relacionadas con catálogo, campañas y solicitudes.
- Soportar sincronización con una API REST externa.
- Mantener persistencia local para catálogo y datos necesarios.
- Soportar posteriormente notificaciones push relacionadas con campañas.

El proyecto es un MVP académico, no una aplicación comercial definitiva.

---

## 3. Tecnologías obligatorias

El proyecto debe utilizar:

- Kotlin
- Android Studio
- Jetpack Compose
- Material Design 3
- MVVM
- Room
- SQLite
- Retrofit
- API REST
- Pruebas unitarias

No reemplazar estas tecnologías por alternativas sin autorización.

---

## 4. Arquitectura

Utilizar una arquitectura MVVM con separación simplificada por capas:

presentation
domain
data

Estructura conceptual:

UI Compose
    ↓
ViewModel
    ↓
Use Case
    ↓
Repository
    ↓
Data Sources
    ├── Room
    └── Retrofit

Room será la persistencia local.

Retrofit será utilizado para consumir la API REST entregada por la cátedra.

No crear un backend propio salvo que se indique explícitamente.

---

## 5. Patrones

Usar cuando corresponda:

- MVVM
- Repository Pattern
- Use Case Pattern
- Mapper Pattern
- StateFlow / Flow para estados reactivos

Dependency Injection puede agregarse posteriormente si el proyecto realmente lo necesita.

No introducir patrones innecesarios.

---

## 6. Modelos principales previstos

Los principales conceptos del dominio son:

- Product
- Campaign
- Request
- Favorite

Los nombres definitivos pueden ajustarse durante el desarrollo.

Los estados previstos para solicitudes según el caso son:

- EXPLORATION
- PENDING
- CONFIRMED
- DELIVERED

---

## 7. Reglas de desarrollo

Trabajar incrementalmente.

NO desarrollar toda la aplicación de una vez.

Cada tarea debe ser pequeña y verificable.

Antes de realizar un cambio importante:

1. Analizar los archivos afectados.
2. Explicar brevemente qué se modificará.
3. Modificar solamente los archivos necesarios.
4. Compilar el proyecto.
5. Informar el resultado.

No realizar refactorizaciones masivas sin autorización.

No cambiar versiones de Gradle, Kotlin, AGP, compileSdk, targetSdk o minSdk
sin una razón concreta y autorización.

No actualizar dependencias simplemente porque exista una versión más nueva.

No introducir bibliotecas que no sean necesarias.

No modificar funcionalidades que estén fuera de la tarea solicitada.

---

## 8. Compilación

Después de cambios relevantes ejecutar:

Windows:

.\gradlew assembleDebug

La tarea no se considera terminada si la aplicación deja de compilar.

Si aparece un error:

- analizar la causa;
- no ocultarlo;
- no realizar múltiples cambios especulativos;
- explicar qué lo produjo.

---

## 9. Pruebas

Cuando se implemente lógica de negocio, priorizar pruebas de:

- Use Cases
- ViewModels
- Repositories cuando corresponda
- DAOs de Room cuando sea necesario

No crear pruebas artificiales sin valor.

---

## 10. Datos personales

Usar únicamente datos ficticios o anonimizados.

No utilizar:

- RUT reales
- correos personales reales
- nombres de clientes reales
- credenciales productivas
- información personal real

---

## 11. Git

La rama principal es:

main

No realizar commits automáticamente salvo solicitud explícita.

No ejecutar:

git push
git reset --hard
git clean -fd

sin autorización.

No eliminar archivos no rastreados sin autorización.

Antes de operaciones Git destructivas, solicitar confirmación.

---

## 12. Estado inicial conocido

El proyecto base Android:

- compila correctamente;
- se ejecuta correctamente en emulador;
- utiliza Jetpack Compose;
- utiliza Material Design 3;
- tiene Git inicializado;
- tiene un commit inicial estable.

Commit inicial:

chore: proyecto Android inicial funcional

---

## 13. Forma de trabajo

Cuando se solicite una nueva funcionalidad:

1. Analizar.
2. Proponer un plan breve.
3. Esperar autorización si el cambio es amplio.
4. Implementar únicamente el alcance solicitado.
5. Compilar.
6. Reportar archivos modificados.
7. Reportar resultado de compilación.
8. No continuar con la siguiente funcionalidad automáticamente.

Mantener el código comprensible para un proyecto académico.
Preferir claridad y mantenibilidad sobre complejidad innecesaria.