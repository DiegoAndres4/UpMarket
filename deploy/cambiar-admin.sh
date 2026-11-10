#!/usr/bin/env bash
# Cambia la contraseña del usuario "admin" de AuthCore sin modificar el repositorio de AuthCore.
#
# Por qué existe: AuthCore crea "admin"/"admin123" al arrancar y no expone ningún endpoint
# para cambiar la contraseña. La buena noticia es que el bootstrap solo crea el usuario si
# NO existe, así que cambiar el hash en la base de datos es permanente.
#
# Uso:  ./deploy/cambiar-admin.sh
#       ./deploy/cambiar-admin.sh 'MiClaveNueva'
#
# Requisitos: los contenedores "db" y "authcore" ya levantados, y Docker funcionando.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "ERROR: falta .env. Ejecuta antes ./deploy/generar-env.sh" >&2
  exit 1
fi

CLAVE="${1:-}"
if [ -z "$CLAVE" ]; then
  read -r -s -p "Nueva contraseña para el usuario admin: " CLAVE
  echo
  read -r -s -p "Repítela: " CLAVE2
  echo
  [ "$CLAVE" = "$CLAVE2" ] || { echo "ERROR: no coinciden." >&2; exit 1; }
fi

if [ "${#CLAVE}" -lt 12 ]; then
  echo "ERROR: usa al menos 12 caracteres." >&2
  exit 1
fi

# El hash se genera con la herramienta oficial de Apache dentro de un contenedor desechable:
# es BCrypt ($2y$) y Spring Security lo acepta igual que sus propios $2a$/$2b$.
echo ">> Generando hash BCrypt..."
HASH="$(docker run --rm httpd:2-alpine htpasswd -bnBC 10 "" "$CLAVE" | tr -d ':\n')"

if [ -z "$HASH" ]; then
  echo "ERROR: no se pudo generar el hash." >&2
  exit 1
fi

ROOT_PW="$(sed -n 's/^DB_ROOT_PASSWORD=//p' .env | head -1)"
if [ -z "$ROOT_PW" ]; then
  echo "ERROR: DB_ROOT_PASSWORD no está en .env" >&2
  exit 1
fi

# La sentencia se arma con comillas simples alrededor del hash para que el símbolo '$'
# de BCrypt no se expanda en bash.
SQL="UPDATE users SET passwordHash='"$HASH"' WHERE username='admin';"

echo ">> Actualizando el usuario admin en la base de datos..."
docker compose exec -T db mysql -uroot -p"$ROOT_PW" authcoredb -e "$SQL"

FILAS="$(docker compose exec -T db mysql -uroot -p"$ROOT_PW" -N -B authcoredb \
  -e "SELECT COUNT(*) FROM users WHERE username='admin';" 2>/dev/null | tr -d '[:space:]')"

if [ "$FILAS" != "1" ]; then
  echo "ERROR: no existe el usuario admin en la base (¿AuthCore ya arrancó alguna vez?)." >&2
  exit 1
fi

echo ">> Listo: la contraseña de 'admin' fue cambiada."
echo "   Inicia sesión con el usuario 'admin' y la contraseña nueva."
