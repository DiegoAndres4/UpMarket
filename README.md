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
| Subida de fotos (`/api/fotos`), CORS opcional, perfil prod | Compilados y cubiertos por `mvn verify` (REST de punta a punta) |
| Dominio + aplicación | Compilado y probado: incluidas en las **145 pruebas** de `mvn verify` |
| Infraestructura (Spring, JPA, REST, seguridad) | Compilada y ejecutada: `mvn verify` en verde |
| Pruebas Spring (contrato JPA, unicidad de categoría, REST de punta a punta, arquitectura, JWT) | **167 pruebas, 0 fallos, 0 omitidas** con `mvn verify` |

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
| HU-13 | Reseñar al vendedor con comentario y 0–5 estrellas | `ResenaService`, `Resena`, `POST/GET /api/perfil/{usuario}/resenas` |
| Modelo de negocio | Publicar cuesta 3.000 COP (pago simulado) y el vendedor ve las visitas de su publicación | `PagoService`, `Pago`, `PasarelaPagoPort`, `POST /api/publicaciones/{id}/pago` |

## Ejecutar

```bash
export JWT_SECRET_BASE64=<el mismo secreto Base64 que usa AuthCore>   # nunca en el repositorio
mvn verify                  # pruebas
mvn spring-boot:run         # REST en http://localhost:8084 (H2 en memoria)
```

Frontend en local sin Docker: sirva `frontend/` con cualquier servidor estático y arranque el servicio con `CORS_ORIGENES=http://localhost:5500`; en `frontend/js/app.js` cambie `API` a las URLs de los servicios (AuthCore está en otro puerto y no tiene CORS, por eso el despliegue usa nginx como puerta única).

Producción: `./deploy/generar-env.sh` crea `.env` con secretos aleatorios coherentes (`JWT_SECRET` y su `JWT_SECRET_BASE64`, contraseñas de MySQL y `ADMIN_PASSWORD`). No copie `.env.example` literalmente: sus valores son marcadores y `JWT_SECRET_BASE64` no es Base64 válido; use `deploy/env.ejemplo` como referencia de formato. Las variables `DB_URL`, `DB_USER` y `DB_PASSWORD` las toma el perfil `prod` del compose.
En producción Flyway aplica `src/main/resources/db/migration` y Hibernate solo valida el esquema (`ddl-auto=validate`); en dev/test el esquema lo crea Hibernate. `GET /actuator/health` es público y sirve de healthcheck.
Pruebas manuales: `requests.http` (los tokens los emite AuthCore; no los guarde en el repositorio).
Al arrancar se crean las categorías iniciales que falten (Tecnología, Libros, Útiles académicos, Alimentos, Servicios, Otros).

### Verificación de correo

AuthCore envía el enlace de confirmación por SMTP. Para Gmail:

1. Cree una cuenta **dedicada** (no use la personal) y active la verificación en dos pasos.
2. En la cuenta de Google, genere una **contraseña de aplicación** (Seguridad → Contraseñas de aplicaciones) y úsela en `MAIL_PASSWORD`; la contraseña normal no sirve.
3. Complete en `.env`: `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME=<cuenta>`, `MAIL_PASSWORD=<contraseña de aplicación>`, `MAIL_FROM=<cuenta>` y `APP_PUBLIC_URL=<dirección pública>`.

`APP_PUBLIC_URL` debe ser la dirección real con la que se abre el sitio (`https://<dominio>` con Caddy o `http://<IP>` sin TLS): con ella se arman los enlaces del correo y con otra no funcionan. Una cuenta gratuita de Gmail permite unos **500 correos por día**; suficiente para el proyecto, pero no para un envío masivo.

Mientras la rama de AuthCore con verificación de correo no esté publicada en `main` de GitHub, el compose de producción construirá la versión anterior. Para probar en local, clone esa rama al lado del proyecto y defina en `.env` `AUTHCORE_CONTEXT=../Auth-Core-UpMarket`; el compose construye AuthCore desde esa carpeta.

### Desarrollo con Docker

```bash
docker compose -f docker-compose.dev.yml up --build --watch
```

Levanta web, AuthCore, dominio y MySQL sin necesitar `.env`. AuthCore se toma de `../Auth-Core-UpMarket`; si está en otra ruta, defina `AUTHCORE_DEV_PATH`. Al guardar en `src/main` de cualquiera de los dos servicios, ese servicio se recompila y reinicia solo. Los cambios del frontend se ven al recargar el navegador (Ctrl+F5).

El panel de administración (`frontend-admin/`) se sirve como archivos estáticos desde `frontend-admin/dist`. Antes de levantar el compose, compílelo una vez:

```bash
cd frontend-admin
npm install
npm run build          # o `npm run dev` con proxy a http://localhost:8084 mientras desarrolla
```

Sin ese `dist`, `http://localhost:8080/admin/` responde 404 hasta que lo compile (el resto del sitio funciona igual).

| Servicio | Dirección |
|---|---|
| Web | `http://localhost:8080` (502 hasta que terminen de compilar los servicios) |
| AuthCore | `http://localhost:8081`, depurador en `5005`. Usuario inicial: `admin` / `admin-dev-123` |
| Dominio | `http://localhost:8084`, depurador en `5006` |
| MySQL | `localhost:3307`, bases `upmarket` y `authcoredb`, usuario `upmarket` / `upmarket-dev` |

Borrar los datos y empezar de cero: `docker compose -f docker-compose.dev.yml down -v`. Este entorno usa volúmenes propios y no afecta al despliegue.

### Datos de ejemplo (seed)

El entorno de desarrollo siembra datos de demostración al arrancar el dominio: 4 perfiles (incluido `admin`) y 12 publicaciones con foto. Es idempotente: si ya existen, no se duplican.

- Se activa con el perfil `demo` y `UPMARKET_SEED_ENABLED=true` (ambos ya están en `docker-compose.dev.yml`).
- Para desactivarlo, quite esas variables del compose o póngalas en `false`.
- Para empezar de cero (base y fotos), use `docker compose -f docker-compose.dev.yml down -v`.
- En producción no se activa: el compose solo usa el perfil `prod` y la variable queda en `false` por defecto.
- Los vendedores demo no son cuentas de AuthCore; solo `admin` puede iniciar sesión. Si borra una publicación sembrada, reaparecerá al reiniciar mientras el seed esté activo.

## Endpoints

| Método y ruta | Quién | Respuestas |
|---|---|---|
| `GET /api/publico/cartelera?texto&categoria&limite` | público (sin token) | 200 con avisos disponibles y pagados, más recientes primero (`id`, `titulo`, `precio`, `categoria`, `estadoArticulo`, `fotoUrl`, `creadoEn`) · 400 (`limite` fuera de 1..24 o `texto` de más de 80) |
| `GET /api/publico/categorias` | público (sin token) | 200 con los nombres de las categorías |
| `GET /api/categorias` | autenticado | 200 con nombre y publicaciones disponibles |
| `POST /api/categorias` | ADMIN | 201 · 400 · 403 · 409 (nombre repetido sin importar tildes ni mayúsculas) |
| `POST /api/publicaciones` | autenticado | 201 · 400 (lista todos los faltantes) · 404 (categoría) |
| `GET /api/publicaciones?texto&categoria&precioMin&precioMax&estado` | autenticado | 200 (solo disponibles, más recientes primero) · 400 |
| `GET /api/publicaciones/mias` | autenticado | 200 (todas las propias, vendidas incluidas) |
| `POST /api/publicaciones/{id}/pago` | solo el autor | 200 con estado y referencia del pago simulado · 403 · 404 · 409 (ya pagada) |
| `GET /api/publicaciones/{id}` | autenticado | 200 (sin contacto; `vistas` solo para el autor o ADMIN) · 404 (también si está pendiente de pago y no es el autor) |
| `GET /api/publicaciones/{id}/contacto` | autenticado | 200 · 404 · 409 (ya vendida) |
| `PUT /api/publicaciones/{id}` | solo el autor | 200 · 400 · 403 · 404 · 409 (vendida) |
| `POST /api/publicaciones/{id}/sold` | solo el autor | 200 · 403 · 404 · 409 (ya vendida) |
| `DELETE /api/publicaciones/{id}` | autor o ADMIN (moderación) | 204 · 403 · 404 |
| `POST /api/fotos` (multipart, campo `archivo`) | autenticado | 201 con `{url,tipo,tamanoBytes}` · 400 (no es JPG/PNG por contenido, o pesa más de 5 MB) |
| `POST /api/perfil/foto` (multipart, campo `archivo`) | autenticado | 200 con el perfil y su nuevo `fotoUrl` · 400 (JPG/PNG inválido o mayor a 5 MB) |
| `DELETE /api/perfil/foto` | autenticado | 204: quita la foto del perfil y borra el archivo anterior |
| `GET /api/admin/estadisticas` | ADMIN | 200: totales por estado, pagos aprobados e ingresos, vistas, top 5, categorías, últimos 30 días, perfiles y reseñas · 403 |
| `GET /api/admin/publicaciones?texto&categoria&estadoPublicacion&estadoPago&vendedor&pagina&tamano` | ADMIN | 200 paginado con **todas** las publicaciones (incluye pendientes de pago), más recientes primero · 403 |
| `DELETE /api/admin/publicaciones/{id}` | ADMIN | 204 (reutiliza el caso de uso de eliminación) · 403 · 404 |
| `GET /api/admin/usuarios?pagina&tamano` | ADMIN | 200 paginado: usuario, nombre, correo, foto, eliminada y número de publicaciones. Solo lectura · 403 |
| `GET /api/admin/pagos?pagina&tamano` | ADMIN | 200 paginado, más recientes primero: referencia, monto, estado, fechas, publicación, comprador y si el recibo existe · 403 |
| `POST /api/admin/pagos/{pagoId}/recibo` | ADMIN | 201 con el recibo PDF nuevo; 200 con el existente (idempotente) · 404 · 409 si el pago no está aprobado · 403 |
| `GET /api/admin/documentos/{id}/descarga` | ADMIN | 302 a un enlace firmado de 10 minutos (Cloudinary) o adjunto en streaming (local) · 403 · 404 |
| `GET /api/admin/documentos/{id}/contenido` | ADMIN | 200 con el archivo en línea (`inline`, sin caché): PDF o XML con su `Content-Type` · 403 · 404 |
| `POST /api/admin/reportes/estadisticas` | ADMIN | 201 con el PDF y el XML generados desde una sola foto de las estadísticas · 403 |
| `GET /api/admin/reportes?pagina&tamano` | ADMIN | 200 paginado: número, fecha, autor y descargas PDF/XML del reporte · 403 |
| `GET /api/perfil` · `PUT /api/perfil` | autenticado | Perfil propio (nombre, celular colombiano, foto). 400 si el nombre o el celular no son válidos |
| `GET /api/perfil/publico?usuario=` | autenticado | Nombre y foto de otra persona, nunca el celular |
| `GET /api/perfil/{usuario}/resenas` | autenticado | 200 `{ vendedor, promedio, total, resenas[] }`; promedio con un decimal y reseñas más recientes primero |
| `POST /api/perfil/{usuario}/resenas` | autenticado (no el propio vendedor) | 201 · 400 (estrellas fuera de 0–5, comentario fuera de 3–500 o auto-reseña) · 409 (ya reseñó a ese vendedor) |
| `DELETE /api/perfil` | autenticado | 204: archiva el perfil en `perfiles_eliminados`, archiva sus publicaciones (conserva pagos y recibos), libera el usuario y elimina las credenciales en AuthCore; el token anterior deja de servir (401 «cuenta eliminada») |
| `GET /api/fotos/{nombre}` | público (la etiqueta `img` no envía token) | 200 · 404 |

## Reglas de negocio clave

- Roles: **USER = estudiante**, **ADMIN = administrador**. Solo el ADMIN crea categorías. Editar y vender son **solo del autor**; **eliminar** lo permite el autor o un **ADMIN**, que modera el catálogo (puede borrar publicaciones ajenas, pero no editarlas ni marcarlas como vendidas).
- El vendedor sale del token, nunca del cuerpo. La publicación nace `DISPONIBLE` pero `PENDIENTE` de pago: no aparece en búsquedas ni en el detalle de otras personas hasta que el autor paga la tarifa de 3.000 COP. El pago es simulado (`SIM-…`), solo lo puede hacer el autor y no se puede cobrar dos veces. Al venderla pasa a `VENDIDA` (ya no se edita ni se contacta).
- Vistas: cada `GET /api/publicaciones/{id}` de una persona distinta del autor suma una vista de forma atómica (`vistas = vistas + 1`). El contador solo se devuelve al autor y al ADMIN, en el detalle y en «Mis publicaciones».
- Precio: número mayor que cero, hasta 999 999 999.99, dos decimales; llega como texto y lo valida el dominio. Título 3–100 y descripción 5–2000 caracteres.
- Fotos: hasta 5 por publicación, JPG o PNG, máximo 5 MB. El archivo se sube primero a `POST /api/fotos` (se valida por su **contenido**, no por el nombre ni el `Content-Type`) y la `url` que devuelve se envía en `fotos` al publicar. El proveedor se elige con `FOTOS_PROVEEDOR`: `local` (por defecto; disco `FOTOS_DIR`, volumen `fotos-data`) o `cloudinary` (URLs `https://` absolutas). Si elige Cloudinary, defina `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY` y `CLOUDINARY_API_SECRET`, o `CLOUDINARY_URL`; sin credenciales la aplicación falla al arrancar con un mensaje claro. El navegador nunca ve el secreto: la subida siempre pasa por el backend.
- Las fotos guardadas como nombre local (`/api/fotos/...`) siguen sirviéndose igual; las URLs absolutas de Cloudinary se usan tal cual. No hay migración de datos entre proveedores.

### Documentos del panel (recibos y reportes)

- El recibo de pago (`POST /api/admin/pagos/{pagoId}/recibo`, solo PDF) y el reporte de estadísticas (`POST /api/admin/reportes/estadisticas`, PDF + XML) se dibujan con openhtmltopdf y las fuentes Archivo embebidas, con los mismos tokens visuales de la app. El XML valida contra `src/main/resources/xsd/estadisticas-1.xsd`.
- Se guardan **privados**: Cloudinary con `resource_type=raw` y `type=private`, o disco local en `DOCUMENTOS_DIR` (volumen `documentos-data` montado en `/data/documentos`). La descarga pasa siempre por `GET /api/admin/documentos/{id}/descarga` (ADMIN): 302 a un enlace firmado de 10 minutos en Cloudinary, o streaming del archivo en local. Para verlos en el panel sin descargarlos, `GET /api/admin/documentos/{id}/contenido` entrega los bytes `inline` (en Cloudinary los trae el backend por el enlace firmado). Nunca se guarda una URL pública permanente y el token jamás viaja en la URL.
- El recibo es informativo: el pago es simulado y el propio documento lo dice («no tiene validez fiscal ante la DIAN»). No hay NIT, IVA ni datos legales inventados.
- El proveedor se elige con el mismo `FOTOS_PROVEEDOR` de las fotos: un solo interruptor mueve todo el almacenamiento binario.

### Panel de administración (`/admin/`)

- Aplicación aparte en `frontend-admin/` (Vite + React + TypeScript + Tailwind + componentes shadcn/ui). El sitio público sigue en JavaScript sin compilación.
- Comparte el origen y el token del sitio: lee `upmarket.token` de `localStorage`. Si no hay token o el rol no es `ADMIN`, redirige a `/`.
- Se compila en la imagen Docker (`frontend/Dockerfile`, etapa Node) y nginx lo sirve bajo `/admin/` con respaldo de SPA.
- Los administradores ven el enlace «Admin» en la barra del sitio público.
- Página «Pagos»: listado paginado con estado, comprador y recibo; en los pagos aprobados se genera, se ve en línea o se descarga el recibo.
- Tablero: «Generar reporte (PDF y XML)» y el historial de reportes; cada PDF y XML se puede ver en línea (diálogo con el PDF o el XML indentado) o descargar.
- Contacto: celular colombiano y/o correo (al menos uno). Celular válido = 10 dígitos que empiezan por 3, con 57 o +57 opcional; el vendedor elige qué medios publica.
- Cuenta: el **correo es el nombre de usuario en AuthCore**. Eliminar la cuenta en UPMarket no pierde el registro: el perfil se copia a `perfiles_eliminados`, sus publicaciones se archivan (`archivada_en`) para conservar pagos, recibos y estadísticas, la fila activa se borra (el usuario queda libre para una cuenta nueva) y luego se eliminan las credenciales en AuthCore (`DELETE /api/auth/users/me`). Si AuthCore falla, todo se revierte. El detalle **no** incluye el contacto; se pide aparte y solo mientras la publicación está disponible.
- Categoría: se identifica por su nombre sin tildes ni mayúsculas («libros» = «Libros»); la base tiene una restricción única como última defensa.
- Búsqueda: palabra clave sin importar tildes ni mayúsculas, en título y descripción; categoría, rango de precio (inclusivo) y estado (NUEVO/USADO) combinables. Los comodines `%` y `_` se tratan como texto.
- Orden de validación: 400 → 404 → 403 → 409.
- Concurrencia: `@Version` en la publicación; si dos operaciones la cambian a la vez, la segunda recibe 409.
- Eliminar es definitivo (borrado físico).
- Reseñas: una sola por comprador y vendedor, y es definitiva (no se edita ni se borra). Estrellas enteras de 0 a 5 y comentario de 3 a 500 caracteres; el autor sale del token. El promedio se calcula al leer, con un decimal, y se muestra en el perfil público del vendedor y en el detalle de cada publicación.

## Pendiente para el grupo

1. Ejecutar la suite contra MySQL real: hoy los contratos JPA corren contra H2 en modo MySQL.
2. Adjuntar la evidencia de `UpmarketRestTest` (ya en verde con `mvn verify`).
3. Ejercicios: fotos en S3; moderación por el ADMIN; paginación de la búsqueda; perfil público del vendedor.
4. Confirmar con el profesor las decisiones del Excel (contacto guardado en la publicación, ADMIN sin moderación, borrado definitivo).

Los datos de contacto son datos personales: mantenga su acceso restringido a usuarios autenticados.
No incluye credenciales ni secretos: todo llega por variables de entorno.
