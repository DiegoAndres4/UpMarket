# Despliegue de UPMarket en AWS (EC2 + Docker Compose)

Es la ruta más simple y barata: **una sola máquina** que corre cuatro contenedores
(`web` nginx+frontend, `authcore`, `domain`, `db` MySQL). Solo el puerto 80 (y el 443 si activa HTTPS) queda abierto al público.

```
Internet ─▶ :80 nginx ─┬─ /api/auth/login, /register ─▶ authcore:8081 ─┐
                       ├─ /api/*                      ─▶ domain:8084 ──┼─▶ MySQL (authcoredb, upmarket)
                       └─ /  (frontend estático)                       │   volumen fotos-data (imágenes)
```

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
git clone https://github.com/DiegoAndres4/upmarket.git && cd upmarket
./deploy/generar-env.sh                       # crea .env con contraseñas y clave JWT aleatorias
docker compose up -d --build                  # primera vez: 5–10 min (compila los dos servicios)
docker compose ps                             # los 4 deben estar "running"
```
- AuthCore se construye desde `github.com/DiegoAndres4/auth-core-upmarket`, rama **`main`** (su `master` quedó desactualizada). Si prefiere la copia local:
  `git clone <url-authcore> ../Auth-Core-UpMarket` y añada `AUTHCORE_CONTEXT=../Auth-Core-UpMarket` al `.env`.
- Abra `http://<IP>` en el navegador: debe verse la pantalla de ingreso.

## 4. Primer uso
1. **Crear cuenta** (con correo, nombre y contraseña; el celular es opcional) y entrar; publique algo de prueba con foto.
2. Cuenta administradora: AuthCore crea sola el usuario `admin` al primer arranque. La contraseña inicial es la de `ADMIN_PASSWORD` en `.env`; si se deja vacía, AuthCore genera una aleatoria y la muestra una sola vez en `docker compose logs authcore`. Con ella se entra (en el campo de correo se escribe `admin`) y aparece la pestaña **Categorías**.
3. **Cambiar la contraseña de `admin` de inmediato**:
   ```bash
   ./deploy/cambiar-admin.sh
   ```
   Genera un hash BCrypt y lo escribe en la base de datos de AuthCore. No modifica ese repositorio y es permanente: el bootstrap solo crea el usuario si no existe.

> ⚠️ **Limitación heredada de AuthCore:** no existe endpoint para cambiar contraseñas,
> y nginx no publica `/api/auth/users`, así que **el único ADMIN posible es `admin`**. El script anterior
> es la forma soportada de asegurarlo sin tocar el repositorio del otro grupo.

## 5. Operación
| Tarea | Comando |
|---|---|
| Ver logs | `docker compose logs -f domain` (o `authcore`, `web`, `db`) |
| Actualizar tras un `git pull` | `docker compose up -d --build` |
| Reiniciar todo | `docker compose restart` |
| Respaldo (bases + fotos) | `./deploy/respaldo.sh` (guarda en `respaldos/`, conserva 14 días) |
| Dar rol ADMIN a un usuario | `./deploy/dar-admin.sh correo@dominio` |
| Crear bases en un volumen ya existente | `./deploy/crear-bases.sh` |
| Copia de las fotos | `docker run --rm -v upmarket_fotos-data:/d -v $PWD:/b alpine tar czf /b/fotos.tgz -C /d .` |

Los datos viven en los volúmenes `mysql-data` y `fotos-data`: sobreviven a reinicios y reconstrucciones; **no** a `docker compose down -v`.
Guarde una copia del `.env`: si se pierde `JWT_SECRET`, todas las sesiones se invalidan (los usuarios solo vuelven a ingresar).

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
| Todo da 401 tras ingresar | `JWT_SECRET_BASE64` no corresponde a `JWT_SECRET`. Use `generar-env.sh`, no los edite a mano. |
| `domain` se reinicia | `docker compose logs domain`: falta una variable o MySQL aún no estaba lista. |
| No sube fotos | Debe ser JPG/PNG de ≤ 5 MB; el contenido se verifica, no solo la extensión. |
| `authcore` no compila | El contexto de GitHub no es accesible: use `AUTHCORE_CONTEXT` con la copia local. |
| Sin memoria (OOM) | Use t3.medium o mayor. Cada servicio tiene `mem_limit` y heap de 300 MB; el tope total es ~2.3 GB. |
| Login devuelve **429** | Limitador de nginx: 10 intentos/min por IP (3 registros/min). Es intencional; espere un minuto. |
| Las fotos dan 404 | El volumen `fotos-data` solo se llena con subidas de esa instalación; una base nueva no trae fotos. |


## 8. Endurecimiento y límites conocidos

- **Respaldos automáticos:** `crontab -e` → `15 3 * * * cd ~/UpMarket && ./deploy/respaldo.sh >> respaldos.log 2>&1`. Copie `respaldos/` fuera del servidor (S3) y/o active snapshots diarios del volumen EBS. **Nunca** use `docker compose down -v` en producción: borra la base y las fotos.
- **Esquema de BD:** ambos servicios usan `ddl-auto=update` por defecto (configurable con `DDL_AUTO`). Después del primer arranque exitoso puede poner `DDL_AUTO=validate` en `.env` y reiniciar. No hay Flyway: los cambios de esquema futuros requieren migración manual.
- **Sesiones:** AuthCore no tiene refresh ni revocación; el token dura 1 h (`JWT_EXPIRATION_MS` en `.env`). Una cuenta eliminada se bloquea en el servicio de dominio.
- **Healthchecks:** `web` solo arranca cuando `authcore` y `domain` aceptan conexiones. AuthCore corre sin root y sin SQL en logs.
- **Contraseña admin:** defina `ADMIN_PASSWORD` en `.env` o ejecute `./deploy/cambiar-admin.sh` tras el primer arranque.
- **Algoritmo JWT:** el token es HS512; la clave del dominio es `JWT_SECRET_BASE64` (Base64 de los mismos bytes de `JWT_SECRET`). Si cambia una, regenere la otra con `generar-env.sh`.
