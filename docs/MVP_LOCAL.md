# Alcance funcional de LouAraya

La entrega implementa un catálogo y un flujo de atención comercial con datos persistidos en una instalación Android.

## Cliente

El cliente puede registrarse, iniciar sesión, editar su perfil, elegir una ilustración o tomar una fotografía. Puede buscar piezas, filtrar por categoría, explorar colecciones y guardar favoritos. Desde una pieza puede registrar una solicitud de compra, reserva o cotización personalizada y consultar su estado e importe acordado.

## Administración

El perfil administrador puede crear y editar productos, reemplazar su fotografía con cámara o galería y desactivar piezas conservando el historial. También puede organizar colecciones, publicar campañas, consultar resultados y actualizar solicitudes e importes.

La bodega permite registrar entradas, ventas directas y retiros por producto, con historial y control de saldo. Entregar una compra o reserva descuenta una unidad una sola vez. Las cotizaciones personalizadas no consumen su producto de referencia. El detalle está en [Gestión de stock](STOCK_2026-10-25.md).

## Funciones complementarias

- QR desde cámara, imagen o código escrito, con validación del destino y recuperación ante errores.
- Ferias ficticias en mapa OpenStreetMap y en un listado persistido localmente.
- Avisos internos y notificaciones de Android para el usuario activo.
- Animaciones de entrada de página, mensajes y carga.

## Persistencia y acceso

Room/SQLite conserva catálogo, perfiles, sesión, favoritos, colecciones, campañas, solicitudes, avisos y recorridos. Las fotografías se guardan en directorios privados. Los casos de uso comprueban el rol y la pertenencia de las solicitudes antes de las operaciones. Las contraseñas se almacenan como claves derivadas con PBKDF2 y sal individual.

## Límites

Los datos pertenecen a la instalación. El cliente y el administrador pueden demostrarse cambiando de cuenta en el mismo dispositivo. No se implementa intercambio de datos entre teléfonos, API comercial, push remoto ni pagos. El mapa requiere Internet para descargar mosaicos nuevos; el listado de ferias se conserva localmente.

## Recorrido de demostración

1. Iniciar sesión con cliente.demo y explorar piezas y categorías.
2. Guardar una pieza como favorita y comprobar la pestaña Guardados.
3. Solicitar una compra, reserva o cotización. Probar un campo inválido y corregirlo.
4. Consultar la solicitud y cerrar sesión desde Perfil.
5. Iniciar sesión con admin.demo, revisar la solicitud, modificar su estado e indicar un importe.
6. Volver al cliente para consultar los cambios y los avisos.
7. Leer un QR válido y uno inválido; cancelar el selector de fotos para demostrar la recuperación.
8. Tomar y confirmar una foto de perfil o producto y comprobar que se conserva al volver a abrir la pantalla.

Ambas cuentas de demostración utilizan la contraseña ficticia LouDemo2026!.
