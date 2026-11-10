#!/usr/bin/env bash
# Cambia la contraseña del usuario "admin" de AuthCore sin modificar el repositorio de AuthCore.
# AuthCore crea admin/admin123 solo si NO existe, así que cambiar el hash en la BD es permanente.
# Uso:  ./deploy/cambiar-admin.sh      (pide la clave)   |   ./deploy/cambiar-admin.sh 'MiClaveNueva'
set -euo pipefail
cd "$(dirname "$0")/.."

[ -f .env ] || { echo "ERROR: falta .env. Ejecuta antes ./deploy/generar-env.sh" >&2; exit 1; }
sed -i 's/\r$//' .env 2>/dev/null || true

CLAVE="${1:-}"
if [ -z "$CLAVE" ]; then
  read -r -s -p "Nueva contraseña para el usuario admin: " CLAVE; echo
  read -r -s -p "Repítela: " CLAVE2; echo
  [ "$CLAVE" = "$CLAVE2" ] || { echo "ERROR: no coinciden." >&2; exit 1; }
fi
[ "${#CLAVE}" -ge 12 ] || { echo "ERROR: usa al menos 12 caracteres." >&2; exit 1; }

echo ">> Generando hash BCrypt..."
HASH="$(docker run --rm httpd:2-alpine htpasswd -bnBC 10 "" "$CLAVE" | tr -d ':\n')"
[ -n "$HASH" ] || { echo "ERROR: no se pudo generar el hash." >&2; exit 1; }

ROOT_PW="$(sed -n 's/^DB_ROOT_PASSWORD=//p' .env | head -1)"
[ -n "$ROOT_PW" ] || { echo "ERROR: DB_ROOT_PASSWORD no está en .env" >&2; exit 1; }

mysql_q() { docker compose exec -T db mysql -uroot -p"$ROOT_PW" -N -B authcoredb -e "$1" 2>/dev/null; }

# Spring nombra la columna password_hash; se detecta por si cambiara.
COL="$(mysql_q "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='authcoredb' AND TABLE_NAME='users' AND LOWER(REPLACE(COLUMN_NAME,'_','')) = 'passwordhash';" | tr -d '[:space:]')"
[ -n "$COL" ] || { echo "ERROR: no encontré la columna de contraseña en authcoredb.users (¿AuthCore ya arrancó alguna vez?)." >&2; exit 1; }

EXISTE="$(mysql_q "SELECT COUNT(*) FROM users WHERE username='admin';" | tr -d '[:space:]')"
[ "$EXISTE" = "1" ] || { echo "ERROR: no existe el usuario admin en la base." >&2; exit 1; }

echo ">> Actualizando el usuario admin (columna $COL)..."
mysql_q "UPDATE users SET \`$COL\`='$HASH' WHERE username='admin';" >/dev/null

# Verifica que el hash quedó guardado.
GUARDADO="$(mysql_q "SELECT \`$COL\`='$HASH' FROM users WHERE username='admin';" | tr -d '[:space:]')"
[ "$GUARDADO" = "1" ] || { echo "ERROR: el hash no quedó guardado." >&2; exit 1; }

echo ">> Listo: la contraseña de 'admin' fue cambiada. Ya no sirve admin123."
