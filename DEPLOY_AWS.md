# Despliegue de UPMarket en AWS (EC2 + Docker Compose)

Es la ruta más simple y barata: **una sola máquina** que corre cuatro contenedores
(`web` nginx+frontend, `authcore`, `domain`, `db` MySQL). Solo el puerto 80 (y el 443 si activa HTTPS) queda abierto al público.

Si en lugar de MySQL local usa **RDS**, salte el servicio `db` con la capa `docker-compose.rds.yml`
(ver [sección 3.1](#31-despliegue-contra-rds-en-lugar-del-contenedor-mysql)): quedan tres contenedores.

```
Internet ─▶ :80 nginx ─┬─ /api/auth/login, /register ─▶ authcore:8081 ─┐
                       ├─ /api/*                      ─▶ domain:8084 ──┼─▶ MySQL (authcoredb, upmarket)
                       └─ /  (frontend estático)                       │   volumen fotos-data (imágenes)
```

Los servicios Java no publican puertos en el servidor: nginx es la única puerta y habla con
`authcore` y `domain` por la red interna de Docker.

## 1. Crear la instancia
1. EC2 → *Launch instance* → **Ubuntu 24.04**, tipo **t3.medium** (4 GB; con 2 GB las tres JVM + MySQL quedan justas), disco **20 GB gp3**.
2. Par de llaves: cree o elija una y guarde el `.pem`.
3. *Security group*: **22** (SSH) solo desde *Mi IP*; **80** (y **443** si usa HTTPS) desde cualquier lugar. No abra 3306, 8081 ni 8084.
4. (Recomendado) *Elastic IP* asociada, para que la dirección no cambie al reiniciar.

## 2. Instalar Docker
```bash
ssh -i llave.pem ubuntu@<IP>
sudo apt-get update && sudo apt-get install -y docker.io docker-compose-v2 git
sudo usermod -aG docker ubuntu && exit        # vuelva a entrar por SSH
```

## 3. Traer el proyecto y levantarlo
```bash
git clone https://github.com/DiegoAndres4/UpMarket.git && cd UpMarket
./deploy/generar-env.sh                       # crea .env con contraseñas y clave JWT aleatorias
grep ADMIN_PASSWORD .env                      # contraseña inicial del usuario admin
docker compose up -d --build                  # primera vez: 5–10 min (compila los dos servicios)
docker compose ps                             # los 4 deben estar "healthy" o "running"
```

`generar-env.sh` crea `.env` con permisos `600` y genera, con `openssl`, `DB_ROOT_PASSWORD`,
`DB_PASSWORD`, `ADMIN_PASSWORD`, `JWT_SECRET` y su `JWT_SECRET_BASE64` (los mismos bytes en Base64).
**Guarde una copia de `.env` fuera del servidor**: sin él la pila no vuelve a arrancar y, si cambia
`JWT_SECRET`, todas las sesiones se invalidan y la contraseña de MySQL deja de coincidir con el volumen ya creado.

- AuthCore se construye desde `github.com/DiegoAndres4/Auth-Core-UpMarket`, rama **`main`**. Para que el
  despliegue sea reproducible (o si el repositorio no es accesible desde la instancia), clone la copia local
  al lado del proyecto y añada al `.env`:
  ```bash
  git clone https://github.com/DiegoAndres4/Auth-Core-UpMarket.git ../Auth-Core-UpMarket
  echo 'AUTHCORE_CONTEXT=../Auth-Core-UpMarket' >> .env
  ```
- Abra `http://<IP>` en el navegador: debe verse la pantalla de ingreso.

### Verificación después del primer arranque
```bash
docker compose ps                                                    # 4 servicios arriba
curl -s -o /dev/null -w 'web=%{http_code}\n' http://localhost/       # web=200
curl -s -o /dev/null -w 'api=%{http_code}\n' http://localhost/api/publicaciones   # api=401 (falta token)
docker compose exec -T domain   curl -fsS http://127.0.0.1:8084/actuator/health   # {"status":"UP"}
docker compose exec -T authcore curl -fsS http://127.0.0.1:8081/actuator/health   # status UP
```
Un `401` en `/api/publicaciones` es lo correcto: significa que nginx está reenviando al servicio de dominio y que la API exige token.

## 3.1 Despliegue contra RDS (en lugar del contenedor MySQL)

Si usa una instancia MySQL administrada (RDS), no levante el servicio `db`: las aplicaciones deben
conectarse al endpoint del RDS. El archivo `docker-compose.rds.yml` del repositorio hace el cambio sin
tocar el compose principal.

**Requisitos previos**
- RDS MySQL 8.x accesible desde la EC2: el *security group* del RDS debe permitir el puerto **3306**
  desde el security group de la instancia (no abra 3306 a internet).
- Las dos bases creadas (RDS no las crea solo):
  ```sql
  CREATE DATABASE upmarket   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  CREATE DATABASE authcoredb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```
- Un usuario con permisos sobre ambas bases. Para el laboratorio sirve el usuario maestro; en un entorno
  real cree uno dedicado con permisos solo sobre `upmarket` y `authcoredb`.

**`.env` para RDS.** No use `generar-env.sh`: ese script genera contraseñas aleatorias que no existen en RDS.
```bash
DB_HOST=<endpoint>.rds.amazonaws.com
DB_USER=<usuario de RDS>
DB_PASSWORD=<contraseña de ese usuario>
DB_ROOT_PASSWORD=<valor cualquiera; el servicio db está desactivado>
JWT_SECRET=<openssl rand -hex 32>
JWT_SECRET_BASE64=<base64 del JWT_SECRET anterior>
ADMIN_PASSWORD=<contraseña inicial del admin de la aplicación>
AUTHCORE_CONTEXT=../Auth-Core-UpMarket
```
`DB_PASSWORD` y `DB_USER` deben ser los del RDS, no los que inventa el script. El `ADMIN_PASSWORD` sigue
siendo el de la **aplicación** (usuario `admin`), no el de MySQL.

**Qué cambia el override**
- `db` queda con `profiles: ["local-db"]`: no se crea ni se arranca.
- `authcore` y `domain` pierden el `depends_on` del contenedor (`!reset {}`) y apuntan a
  `jdbc:mysql://${DB_HOST}:3306/...`.
- El usuario de las dos bases pasa a `${DB_USER}` en lugar del `upmarket` local.

**Levantar y verificar**
```bash
docker compose -f docker-compose.yml -f docker-compose.rds.yml config --services   # authcore, domain, web
docker compose -f docker-compose.yml -f docker-compose.rds.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.rds.yml ps                  # authcore y domain "healthy"
docker compose -f docker-compose.yml -f docker-compose.rds.yml logs domain | grep Flyway
curl -s -o /dev/null -w 'web=%{http_code}\n' http://localhost/                     # web=200
```
Flyway crea el esquema en el primer arranque (`Successfully applied 1 migration`). No cree las tablas a mano.

**Notas propias de RDS**
- Los datos viven en RDS, no en el volumen `mysql-data`: `deploy/respaldo.sh` y `deploy/crear-bases.sh`
  **no aplican**. El respaldo es el de RDS (snapshots o *automated backups*).
- Detener la instancia RDS la conserva, pero AWS la reinicia sola a los 7 días; para volver, arránquela y
  espere a que esté `available` antes de levantar los contenedores.
- En una `t3.small` (2 GB) los builds de Maven y Gradle compiten por memoria: añada 2–4 GB de swap
  (`fallocate` + `mkswap` + `swapon`, con línea en `/etc/fstab`) y compile en serie con
  `COMPOSE_PARALLEL_LIMIT=1`. Si la memoria sigue justa, use `t3.medium` o construya las imágenes fuera.
- Mantenga el mismo criterio de seguridad: solo nginx publica el 80/443; 8081, 8084 y 3306 no se exponen.

## 4. Primer uso
1. **Crear cuenta** (con correo, nombre y contraseña; el celular es opcional) y entrar; publique algo de prueba con foto.
2. **Cuenta administradora:** AuthCore crea sola el usuario `admin` en el primer arranque, con la contraseña de
   `ADMIN_PASSWORD` en `.env`. No se genera ninguna clave aleatoria: si `ADMIN_PASSWORD` falta, AuthCore
   **no arranca** y el error queda en `docker compose logs authcore`.
   Para entrar, escriba `admin` en el campo "Correo o usuario". Es la única cuenta que no usa un correo:
   el resto de usuarios ingresa con el correo con el que se registró.
3. **Cambiar la contraseña de `admin` de inmediato**:
   ```bash
   ./deploy/cambiar-admin.sh
   ```
   Pide la clave nueva (mínimo 12 caracteres), genera un hash BCrypt y lo escribe en la base de datos de
   AuthCore. No modifica ese repositorio y es permanente: el bootstrap solo crea el usuario si no existe.
   `ADMIN_PASSWORD` del `.env` queda obsoleto después de este paso; no hace falta cambiarlo, pero tampoco sirve.

> **Roles ADMIN:** nginx no publica la administración de usuarios de AuthCore (`/api/auth/users*` devuelve 404),
> así que el rol se asigna por SQL con `./deploy/dar-admin.sh correo@dominio`. El usuario debe estar registrado
> antes y tiene que cerrar sesión y volver a entrar para que el token incluya el rol.

## 5. Operación
| Tarea | Comando |
|---|---|
| Ver estado | `docker compose ps` |
| Ver logs | `docker compose logs -f domain` (o `authcore`, `web`, `db`) |
| Actualizar tras un `git pull` | `docker compose up -d --build` |
| Reiniciar todo | `docker compose restart` |
| Aplicar una migración nueva | añada `V2__descripcion.sql` en `src/main/resources/db/migration/` y `docker compose up -d --build` |
| Respaldo (bases + fotos) | `./deploy/respaldo.sh` (guarda en `respaldos/`, conserva 14 días) |
| Dar rol ADMIN a un usuario | `./deploy/dar-admin.sh correo@dominio` |
| Crear bases en un volumen ya existente | `./deploy/crear-bases.sh` |
| Copia manual de las fotos | `docker run --rm -v upmarket_fotos-data:/d -v $PWD:/b alpine tar czf /b/fotos.tgz -C /d .` |

Los datos viven en los volúmenes `mysql-data` y `fotos-data`: sobreviven a reinicios y reconstrucciones; **no** a
`docker compose down -v`.

### Esquema de base de datos (Flyway)
- El esquema lo manda **Flyway**. Cada servicio trae su línea base en `src/main/resources/db/migration/V1__baseline.sql`.
- En producción `ddl-auto=validate`: Hibernate comprueba el esquema pero no lo modifica. Si la validación falla,
  el servicio no arranca (falla rápido, a propósito).
- `baseline-on-migrate=true` y `baseline-version=1`: una base que ya tenía las tablas (creadas por versiones
  anteriores con `ddl-auto=update`) se marca como V1 aplicada y no se toca.
- Para cambiar el esquema: cree `V2__...sql`, nunca edite `V1`. Los archivos ya aplicados no se pueden modificar
  (Flyway valida el checksum).
- `FLYWAY_ENABLED` en AuthCore lo fija el compose de producción; en el servicio de dominio lo activa el perfil
  `prod` (`application-prod.yml`). No los apague.

## 6. HTTPS (obligatorio antes de usar contraseñas reales)
El sitio sale en HTTP plano por defecto: las contraseñas y los tokens viajan sin cifrar.
Para activar TLS con Let's Encrypt usando Caddy (sin costo adicional):

1. Apunte un registro DNS **A** de su dominio a la IP elástica y espere a que propague.
2. Añada al `.env` del servidor:
   ```
   DOMAIN=upmarket.midominio.com
   ACME_EMAIL=su-correo@dominio.com
   ```
   El correo es solo para avisos de vencimiento del certificado.
3. Levante con la capa HTTPS:
   ```bash
   docker compose -f docker-compose.yml -f docker-compose.https.yml up -d --build
   ```
   Caddy escucha en 80/443, emite el certificado y renueva solo. nginx deja de estar expuesto.

> Requiere Docker Compose **≥ 2.24** (se usa la etiqueta `!reset` para quitarle el puerto 80 a `web`).
> Si su versión es anterior: borre `ports: - "80:80"` de `docker-compose.yml` y use el mismo comando.

Verificación: `docker compose -f docker-compose.yml -f docker-compose.https.yml ps` debe mostrar
`caddy` en estado `running` y `curl -I https://su-dominio` debe devolver `HTTP/2 200`.

## 7. Si algo falla
| Síntoma | Causa probable |
|---|---|
| `authcore` reinicia y el log dice "AUTHCORE_ADMIN_PASSWORD es obligatorio" | Falta `ADMIN_PASSWORD` en `.env`. Ejecute `./deploy/generar-env.sh` o añádala a mano. |
| `authcore` o `domain` fallan con "Schema-validation: missing table" | La migración no se aplicó o el volumen quedó a medias. Revise `docker compose logs authcore` y, si la base está vacía, `docker compose down -v` (destructivo) y vuelva a arrancar. |
| `domain` no arranca y el log menciona Flyway checksum | Se editó un archivo `V*.sql` ya aplicado. Restaure el original y cree un `V2` para el cambio. |
| Todo da 401 tras ingresar | `JWT_SECRET_BASE64` no corresponde a `JWT_SECRET`. Use `generar-env.sh`, no los edite a mano. |
| No sube fotos | Debe ser JPG/PNG de ≤ 5 MB; el contenido se verifica por los bytes, no por la extensión. |
| `authcore` no compila | El contexto de GitHub no es accesible: use `AUTHCORE_CONTEXT` con la copia local. |
| Sin memoria (OOM) | Use t3.medium o mayor. Cada servicio tiene `mem_limit` y heap de 256 MB; el tope total es ~2.7 GB. |
| Login devuelve **429** | Limitador de nginx: 20 intentos/min por IP (10 registros/min y 20 subidas/min). Es intencional; espere un minuto. |
| Las fotos dan 404 | El volumen `fotos-data` solo se llena con subidas de esa instalación; una base nueva no trae fotos. |

## 8. Endurecimiento y límites conocidos

- **Respaldos automáticos:** `crontab -e` → `15 3 * * * cd ~/UpMarket && ./deploy/respaldo.sh >> respaldos.log 2>&1`. Copie `respaldos/` fuera del servidor (S3) y/o active snapshots diarios del volumen EBS. **Nunca** use `docker compose down -v` en producción: borra la base y las fotos.
- **Sesiones:** AuthCore no tiene refresh ni revocación; el token dura 1 h (`JWT_EXPIRATION_MS` en `.env`). Una cuenta eliminada se bloquea en el servicio de dominio y sus credenciales se borran en AuthCore (`DELETE /api/auth/users/me`).
- **Healthchecks:** `authcore` y `domain` se consideran sanos cuando `/actuator/health` responde 200 (incluye la consulta a la base). `web` solo arranca cuando ambos están sanos.
- **Contraseña admin:** `ADMIN_PASSWORD` es obligatoria en el primer arranque; después se cambia con `./deploy/cambiar-admin.sh`.
- **Algoritmo JWT:** el token es HS512; la clave del dominio es `JWT_SECRET_BASE64` (Base64 de los mismos bytes de `JWT_SECRET`). Si cambia una, regenere la otra con `generar-env.sh`.
- **Aislamiento de datos:** ambos servicios comparten el usuario MySQL `upmarket`, con permisos sobre las dos bases. Es aceptable en una sola máquina, pero no aísla `authcoredb` de `upmarket`.
- **Sin CI ni monitorización:** no hay pipeline ni alertas. El despliegue es `git clone` + compose, y la única señal de vida son los healthchecks de Docker. Añada al menos un chequeo externo (por ejemplo UptimeRobot) contra `https://su-dominio/`.
- **Logs de Docker:** no tienen rotación configurada en el compose; en un servidor de larga vida pueden llenar el disco. Añada `log-driver`/`max-size` en `/etc/docker/daemon.json` o en cada servicio.
- **Una sola máquina:** no hay alta disponibilidad. Si la instancia cae, el sitio cae. El respaldo y la Elastic IP reducen el tiempo de recuperación, no la caída.
- **Token en `localStorage`:** el frontend guarda el JWT ahí; la mitigación es la CSP estricta y que todo el texto se inserta con `textContent`. Migrar a cookies `HttpOnly` requeriría cambios en AuthCore y nginx.
