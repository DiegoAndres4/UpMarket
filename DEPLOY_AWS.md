# Despliegue de UPMarket en AWS (EC2 + Docker Compose)

Es la ruta más simple y barata: **una sola máquina** que corre cuatro contenedores
(`web` nginx+frontend, `authcore`, `domain`, `db` MySQL). Solo el puerto 80 queda abierto al público.

```
Internet ─▶ :80 nginx ─┬─ /api/auth/login, /register ─▶ authcore:8081 ─┐
                       ├─ /api/*                      ─▶ domain:8084 ──┼─▶ MySQL (authcoredb, upmarket)
                       └─ /  (frontend estático)                       │   volumen fotos-data (imágenes)
```

## 1. Crear la instancia
1. EC2 → *Launch instance* → **Ubuntu 24.04**, tipo **t3.medium** (4 GB; con 2 GB las tres JVM + MySQL quedan justas), disco **20 GB gp3**.
2. Par de llaves: cree o elija una y guarde el `.pem`.
3. *Security group*: **22** (SSH) solo desde *Mi IP*; **80** desde cualquier lugar. No abra 3306, 8081 ni 8084.
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
- AuthCore se construye desde `github.com/DiegoAndres4/auth-core-upmarket`. Si ese repositorio es **privado** o prefiere la copia local:
  `git clone <url-authcore> ../auth-core-upmarket` y añada `AUTHCORE_CONTEXT=../auth-core-upmarket` al `.env`.
- Abra `http://<IP>` en el navegador: debe verse la pantalla de ingreso.

## 4. Primer uso
1. **Crear cuenta** y entrar; publique algo de prueba con foto.
2. Cuenta administradora: AuthCore crea sola el usuario `admin` (contraseña inicial **`admin123`**, está en su código). Con ella se entra y aparece la pestaña **Categorías**.

> ⚠️ **Riesgo conocido:** `admin123` es público (está en el repositorio de AuthCore) y AuthCore no ofrece cambiarla.
> Antes de abrir el sitio a más gente hay que corregirlo **en AuthCore** (leer la contraseña de una variable de entorno), que es del docente/grupo de autenticación y por eso aquí no se tocó.
> Mientras tanto, cualquiera que conozca la dirección podría entrar como administrador y crear categorías.

## 5. Operación
| Tarea | Comando |
|---|---|
| Ver logs | `docker compose logs -f domain` (o `authcore`, `web`, `db`) |
| Actualizar tras un `git pull` | `docker compose up -d --build` |
| Reiniciar todo | `docker compose restart` |
| Copia de seguridad de la base | `docker compose exec db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --databases authcoredb upmarket' > respaldo.sql` |
| Copia de las fotos | `docker run --rm -v upmarket_fotos-data:/d -v $PWD:/b alpine tar czf /b/fotos.tgz -C /d .` |

Los datos viven en los volúmenes `mysql-data` y `fotos-data`: sobreviven a reinicios y reconstrucciones; **no** a `docker compose down -v`.
Guarde una copia del `.env`: si se pierde `JWT_SECRET`, todas las sesiones se invalidan (los usuarios solo vuelven a ingresar).

## 6. HTTPS y dominio (cuando lo necesiten)
El sitio sale en HTTP. Para HTTPS: Route 53 (o su proveedor) apuntando al Elastic IP, y delante un *Application Load Balancer* con certificado de ACM, o Caddy/nginx con Let's Encrypt. Es obligatorio antes de poner contraseñas reales: ahora viajan sin cifrar.

## 7. Si algo falla
| Síntoma | Causa probable |
|---|---|
| Todo da 401 tras ingresar | `JWT_SECRET_BASE64` no corresponde a `JWT_SECRET`. Use `generar-env.sh`, no los edite a mano. |
| `domain` se reinicia | `docker compose logs domain`: falta una variable o MySQL aún no estaba lista. |
| No sube fotos | Debe ser JPG/PNG de ≤ 5 MB; el contenido se verifica, no solo la extensión. |
| `authcore` no compila | El contexto de GitHub no es accesible: use `AUTHCORE_CONTEXT` con la copia local. |
| Sin memoria (OOM) | Use t3.medium o mayor; cada servicio ya limita su heap a 300 MB. |
