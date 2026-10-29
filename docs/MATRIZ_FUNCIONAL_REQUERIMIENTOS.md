# Requisitos del Parcial 2

Proyecto: LouAraya. Estudiante: Lucas Vasquez. Fecha: 20 de octubre de 2026.

| Requisito | Implementación | Ubicación principal |
|---|---|---|
| Formularios validados | Acceso, registro, perfil, productos, colecciones, campañas, solicitudes e importes; mensajes, bordes e íconos de error | domain/usecase y presentation/local |
| Validaciones desacopladas | Reglas de negocio y reglas reutilizables de formulario en dominio; errores observados desde el ViewModel | LocalUseCases, FormValidation, RegisterWithPhotoUseCase |
| Navegación funcional | Acceso, registro, pestañas por rol, detalles, formularios, regreso y destinos de avisos/QR | LocalApp |
| Gestión de estado | ViewModel, StateFlow, observación de Room, estado guardado de formularios y fotografías | LocalAppViewModel y ViewModels de imágenes |
| Almacenamiento local | Room/SQLite, migraciones v1–v7 y archivos privados para fotos | data/local, data/repository, data/image |
| Dos o más recursos nativos | Cámara, selector de imágenes y notificaciones del sistema con permisos | NativeScreens, RegistrationScreen, AdminProductScreens, SystemNotifications |
| Animaciones | Entrada por opacidad, mensajes con AnimatedVisibility y progreso de carga | DesignComponents y LocalApp |
| Arquitectura | Separación de presentación, dominio y datos; repositorios y casos de uso | app/src/main/java/cl/duoc/louaraya |
| README | Identificación del estudiante, descripción, tecnologías, funcionalidades y ejecución | README.md |

Los resultados de las comprobaciones están en [Verificación de entrega](VERIFICACION_ENTREGA.md).

La publicación en GitHub, los accesos del docente, el tablero de planificación y las entregas en AVA se realizan en los servicios correspondientes. El archivo fuente no acredita por sí solo esas acciones.

El [caso original](<../documentacion/037 SEC005D_Formato_de_Caso_Lou_Araya.pdf>) también contempla integración remota. El alcance implementado para esta entrega es local, como se explica en el README y en [MVP local](MVP_LOCAL.md).
