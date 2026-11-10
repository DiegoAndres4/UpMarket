#!/usr/bin/env bash
# Da el rol ADMIN a un usuario ya registrado (nginx no publica /api/auth/users*, así que se hace por SQL).
# Uso: ./deploy/dar-admin.sh correo@unipamplona.edu.co
set -euo pipefail
cd "$(dirname "$0")/.."
USUARIO="${1:?Uso: $0 usuario_o_correo}"
[[ "$USUARIO" =~ ^[A-Za-z0-9._@+-]+$ ]] || { echo "ERROR: caracteres no permitidos en el usuario." >&2; exit 1; }
sed -i 's/\r$//' .env 2>/dev/null || true
ROOT_PW="$(sed -n 's/^DB_ROOT_PASSWORD=//p' .env | head -1)"
q() { docker compose exec -T db mysql -uroot -p"$ROOT_PW" -N -B authcoredb -e "$1" 2>/dev/null; }
ID="$(q "SELECT id FROM users WHERE username='$USUARIO';" | tr -d '[:space:]')"
[ -n "$ID" ] || { echo "ERROR: el usuario '$USUARIO' no existe (debe registrarse antes)." >&2; exit 1; }
q "INSERT IGNORE INTO user_roles (user_id, role) VALUES ($ID, 'ADMIN');" >/dev/null
echo ">> Roles de $USUARIO: $(q "SELECT GROUP_CONCAT(role) FROM user_roles WHERE user_id=$ID;")"
echo "   (debe cerrar sesión y volver a entrar para que el token incluya el rol)"
