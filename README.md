# Domain Service — UPMarket (Proyecto Integrador, Universidad de Pamplona)

Servicio de dominio que se apoya en **AuthCore** (JWT HS384) para autenticar. Gestiona el **mercado de artículos entre estudiantes**:
publicar un artículo con fotos, categorías, búsqueda con filtros, ver el detalle y contactar al vendedor, y administrar las propias publicaciones.

Paquete base: `com.upmarket.catalogo` · Java 17 · Spring Boot 3.5.6 · Maven · H2 (dev) / MySQL (prod) · JUnit 5.

> Los documentos del grupo piden persistencia con JPA (HU-7), así que se usa el **estándar del curso** (Java/Spring).
> Si el grupo eligió otra pila, la estructura hexagonal y las reglas se conservan.

## Estado honesto

| Parte | Estado |
|---|---|
| Dominio + aplicación | Compilado y probado: **44 pruebas puras** pasan |
| Infraestructura (Spring, JPA, REST, seguridad) | Escrita y revisada, **sin ejecutar** (sin acceso a Maven en el entorno de generación) |
| Pruebas Spring (contrato JPA, unicidad de categoría, REST de punta a punta, arquitectura, JWT) | Escritas, **sin ejecutar**: son la primera tarea del grupo con `mvn verify` |

## Arquitectura (hexagonal)

```
domain/          Publicacion, Categoria, Foto, Contacto, Precio, Actor, PoliticaAcceso, excepciones
application/     casos de uso (PublicacionUseCase, CategoriaUseCase), servicios, puertos de salida
infrastructure/  adaptadores: REST (in), JPA (out), seguridad JWT, configuración
```

`domain` y `application` no importan Spring ni JPA (lo verifica `ArquitecturaHexagonalTest`). La transacción se pide por el puerto
`TransaccionPort`; el reloj se inyecta (`Clock`); los permisos viven en `PoliticaAcceso`, no en los controladores.
Los repositorios tienen una prueba de contrato (`contract/`) que se ejecuta contra el doble en memoria **y** contra el adaptador JPA.

## Historias de usuario → código

| HU | Qué hace | Dónde |
|---|---|---|
| HU-1..7 | Autenticación AuthCore | `infrastructure/security` (valida el JWT) |
| HU-8 | Publicar un artículo (título, descripción, precio, estado, categoría, hasta 5 fotos, contacto) | `PublicacionService.publicar`, `Publicacion`, `POST /api/publicaciones` |
| HU-9 | Categorías: iniciales, listar con conteo, crear (ADMIN) | `CategoriaService`, `Categoria`, `/api/categorias` |
| HU-10 | Buscar por palabra clave y filtros combinados | `PublicacionService.buscar`, `GET /api/publicaciones` |
| HU-11 | Ver el detalle y contactar al vendedor | `PublicacionService.detalle/contacto`, `GET /api/publicaciones/{id}` y `/contacto` |
| HU-12 | Editar, marcar como vendido, eliminar y ver mis publicaciones | `PublicacionService`, `PUT/DELETE /api/publicaciones/{id}`, `POST .../sold`, `GET .../mias` |

## Ejecutar

```bash
export JWT_SECRET_BASE64=<el mismo secreto Base64 que usa AuthCore>   # nunca en el repositorio
mvn verify                  # pruebas
mvn spring-boot:run         # REST en http://localhost:8084 (H2 en memoria)
```

Producción: perfil `prod` con `DB_URL`, `DB_USER`, `DB_PASSWORD` como variables de entorno.
Pruebas manuales: `requests.http` (los tokens los emite AuthCore; no los guarde en el repositorio).
Al arrancar se crean las categorías iniciales que falten (Tecnología, Libros, Útiles académicos, Alimentos, Servicios, Otros).

## Endpoints

| Método y ruta | Quién | Respuestas |
|---|---|---|
| `GET /api/categorias` | autenticado | 200 con nombre y publicaciones disponibles |
| `POST /api/categorias` | ADMIN | 201 · 400 · 403 · 409 (nombre repetido sin importar tildes ni mayúsculas) |
| `POST /api/publicaciones` | autenticado | 201 · 400 (lista todos los faltantes) · 404 (categoría) |
| `GET /api/publicaciones?texto&categoria&precioMin&precioMax&estado` | autenticado | 200 (solo disponibles, más recientes primero) · 400 |
| `GET /api/publicaciones/mias` | autenticado | 200 (todas las propias, vendidas incluidas) |
| `GET /api/publicaciones/{id}` | autenticado | 200 (sin contacto) · 404 |
| `GET /api/publicaciones/{id}/contacto` | autenticado | 200 · 404 · 409 (ya vendida) |
| `PUT /api/publicaciones/{id}` | solo el autor | 200 · 400 · 403 · 404 · 409 (vendida) |
| `POST /api/publicaciones/{id}/sold` | solo el autor | 200 · 403 · 404 · 409 (ya vendida) |
| `DELETE /api/publicaciones/{id}` | solo el autor | 204 · 403 · 404 |

## Reglas de negocio clave

- Roles: **USER = estudiante**, **ADMIN = administrador**. Solo el ADMIN crea categorías. Editar, vender y eliminar son **solo del autor**: ni el ADMIN puede (no hay moderación en este alcance).
- El vendedor sale del token, nunca del cuerpo. La publicación nace `DISPONIBLE`; al venderla pasa a `VENDIDA` (ya no se edita ni se contacta).
- Precio: número mayor que cero, hasta 999 999 999.99, dos decimales; llega como texto y lo valida el dominio. Título 3–100 y descripción 5–2000 caracteres.
- Fotos: hasta 5 por publicación, JPG o PNG, máximo 5 MB. **El servicio guarda la dirección y los datos de la foto, no los bytes**: el almacenamiento de archivos queda como ejercicio.
- Contacto: WhatsApp y/o correo (al menos uno). El detalle **no** lo incluye; se pide aparte y solo mientras la publicación está disponible.
- Categoría: se identifica por su nombre sin tildes ni mayúsculas («libros» = «Libros»); la base tiene una restricción única como última defensa.
- Búsqueda: palabra clave sin importar tildes ni mayúsculas, en título y descripción; categoría, rango de precio (inclusivo) y estado (NUEVO/USADO) combinables. Los comodines `%` y `_` se tratan como texto.
- Orden de validación: 400 → 404 → 403 → 409.
- Concurrencia: `@Version` en la publicación; si dos operaciones la cambian a la vez, la segunda recibe 409.
- Eliminar es definitivo (borrado físico).

## Pendiente para el grupo

1. Ejecutar `mvn verify` y corregir lo que aparezca en las pruebas Spring.
2. Adjuntar la evidencia de `UpmarketRestTest`.
3. Ejercicios: puerto de almacenamiento de fotos con un adaptador real; moderación por el ADMIN; paginación de la búsqueda; perfil público del vendedor.
4. Confirmar con el profesor las decisiones del Excel (contacto guardado en la publicación, ADMIN sin moderación, borrado definitivo).

Los datos de contacto son datos personales: mantenga su acceso restringido a usuarios autenticados.
No incluye credenciales ni secretos: todo llega por variables de entorno.
