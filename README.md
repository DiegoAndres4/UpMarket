# UPMarket — Proyecto Integrador (Universidad de Pamplona)

Mercado web para estudiantes. Este repositorio contiene **todo lo necesario para desplegarlo**:

| Carpeta / archivo | Qué es |
|---|---|
| `src/`, `pom.xml` | Servicio de dominio (publicaciones, categorías, fotos), hexagonal, Spring Boot |
| `frontend/` | Aplicación web (HTML/CSS/JS sin compilación) + `nginx.conf` que une todo bajo un solo origen |
| `docker-compose.yml`, `Dockerfile` | Levanta web + AuthCore + dominio + MySQL con un comando |
| `deploy/` | Generador de secretos y script SQL de las bases |
| `DEPLOY_AWS.md` | Guía paso a paso para subirlo a AWS (EC2) |

AuthCore (autenticación) vive en su propio repositorio y **no se modifica**: el compose lo construye desde allí.
**Para desplegar, vaya directo a [`DEPLOY_AWS.md`](DEPLOY_AWS.md).** Lo que sigue describe el servicio de dominio.

---

# Domain Service — UPMarket

Servicio de dominio que se apoya en **AuthCore** (JWT HS512) para autenticar. Gestiona el **mercado de artículos entre estudiantes**:
publicar un artículo con fotos, categorías, búsqueda con filtros, ver el detalle y contactar al vendedor, y administrar las propias publicaciones.

Paquete base: `com.upmarket.catalogo` · Java 17 · Spring Boot 3.5.6 · Maven · H2 (dev) / MySQL (prod) · JUnit 5.

> Los documentos del grupo piden persistencia con JPA (HU-7), así que se usa el **estándar del curso** (Java/Spring).
> Si el grupo eligió otra pila, la estructura hexagonal y las reglas se conservan.

## Estado honesto

| Parte | Estado |
|---|---|
| Frontend | Probado de punta a punta en navegador (Playwright) contra un servidor simulado: registro, publicar con foto, detalle, contacto, vender, buscar |
| Subida de fotos (`/api/fotos`), CORS opcional, perfil prod | Añadidos en el cierre del proyecto; **sin compilar** en el entorno de generación (sin acceso a Maven): `mvn verify` es el primer paso |
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

Frontend en local sin Docker: sirva `frontend/` con cualquier servidor estático y arranque el servicio con `CORS_ORIGENES=http://localhost:5500`; en `frontend/js/app.js` cambie `API` a las URLs de los servicios (AuthCore está en otro puerto y no tiene CORS, por eso el despliegue usa nginx como puerta única).

Producción: perfil `prod` con `DB_URL`, `DB_USER`, `DB_PASSWORD` como variables de entorno.
Pruebas manuales: `requests.http` (los tokens los emite AuthCore; no los guarde en el repositorio).
Al arrancar se crean las categorías iniciales que falten (Tecnología, Libros, Útiles académicos, Alimentos, Servicios, Otros).

### Desarrollo con Docker

```bash
docker compose -f docker-compose.dev.yml up --build --watch
```

Levanta web, AuthCore, dominio y MySQL sin necesitar `.env`. AuthCore se toma de `../Auth-Core-UpMarket`; si está en otra ruta, defina `AUTHCORE_DEV_PATH`. Al guardar en `src/main` de cualquiera de los dos servicios, ese servicio se recompila y reinicia solo. Los cambios del frontend se ven al recargar el navegador (Ctrl+F5).

| Servicio | Dirección |
|---|---|
| Web | `http://localhost:8080` (502 hasta que terminen de compilar los servicios) |
| AuthCore | `http://localhost:8081`, depurador en `5005`. Usuario inicial: `admin` / `admin-dev-123` |
| Dominio | `http://localhost:8084`, depurador en `5006` |
| MySQL | `localhost:3307`, bases `upmarket` y `authcoredb`, usuario `upmarket` / `upmarket-dev` |

Borrar los datos y empezar de cero: `docker compose -f docker-compose.dev.yml down -v`. Este entorno usa volúmenes propios y no afecta al despliegue.

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
| `POST /api/fotos` (multipart, campo `archivo`) | autenticado | 201 con `{url,tipo,tamanoBytes}` · 400 (no es JPG/PNG por contenido, o pesa más de 5 MB) |
| `GET /api/perfil` · `PUT /api/perfil` | autenticado | Perfil propio (nombre, celular colombiano, foto). 400 si el nombre o el celular no son válidos |
| `GET /api/perfil/publico?usuario=` | autenticado | Nombre y foto de otra persona, nunca el celular |
| `DELETE /api/perfil` | autenticado | 204: borra publicaciones, fotos y datos; el token deja de servir (401 «cuenta eliminada») |
| `GET /api/fotos/{nombre}` | público (la etiqueta `img` no envía token) | 200 · 404 |

## Reglas de negocio clave

- Roles: **USER = estudiante**, **ADMIN = administrador**. Solo el ADMIN crea categorías. Editar, vender y eliminar son **solo del autor**: ni el ADMIN puede (no hay moderación en este alcance).
- El vendedor sale del token, nunca del cuerpo. La publicación nace `DISPONIBLE`; al venderla pasa a `VENDIDA` (ya no se edita ni se contacta).
- Precio: número mayor que cero, hasta 999 999 999.99, dos decimales; llega como texto y lo valida el dominio. Título 3–100 y descripción 5–2000 caracteres.
- Fotos: hasta 5 por publicación, JPG o PNG, máximo 5 MB. El archivo se sube primero a `POST /api/fotos` (se valida por su **contenido**, no por el nombre ni el `Content-Type`) y la `url` que devuelve se envía en `fotos` al publicar. Se guardan en disco (`FOTOS_DIR`; en Docker, el volumen `fotos-data`). Con más usuarios convendría pasarlas a S3 detrás de un puerto de salida.
- Contacto: celular colombiano y/o correo (al menos uno). Celular válido = 10 dígitos que empiezan por 3, con 57 o +57 opcional; el vendedor elige qué medios publica.
- Cuenta: el **correo es el nombre de usuario en AuthCore** (AuthCore no cambió). La contraseña y el borrado de credenciales son de AuthCore, que no los ofrece: eliminar la cuenta en UPMarket borra sus datos y la inhabilita aquí, pero ese correo no puede volver a registrarse. El detalle **no** lo incluye; se pide aparte y solo mientras la publicación está disponible.
- Categoría: se identifica por su nombre sin tildes ni mayúsculas («libros» = «Libros»); la base tiene una restricción única como última defensa.
- Búsqueda: palabra clave sin importar tildes ni mayúsculas, en título y descripción; categoría, rango de precio (inclusivo) y estado (NUEVO/USADO) combinables. Los comodines `%` y `_` se tratan como texto.
- Orden de validación: 400 → 404 → 403 → 409.
- Concurrencia: `@Version` en la publicación; si dos operaciones la cambian a la vez, la segunda recibe 409.
- Eliminar es definitivo (borrado físico).

## Pendiente para el grupo

1. Ejecutar `mvn verify` y corregir lo que aparezca en las pruebas Spring.
2. Adjuntar la evidencia de `UpmarketRestTest`.
3. Ejercicios: fotos en S3; moderación por el ADMIN; paginación de la búsqueda; perfil público del vendedor.
4. Confirmar con el profesor las decisiones del Excel (contacto guardado en la publicación, ADMIN sin moderación, borrado definitivo).

Los datos de contacto son datos personales: mantenga su acceso restringido a usuarios autenticados.
No incluye credenciales ni secretos: todo llega por variables de entorno.
