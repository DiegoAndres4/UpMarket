#!/usr/bin/env bash
# Crea (si faltan) las bases y permisos en un volumen MySQL que YA existía.
# mysql-init solo corre cuando el volumen se crea por primera vez; esto es idempotente.
set -euo pipefail
cd "$(dirname "$0")/.."
sed -i 's/\r$//' .env 2>/dev/null || true
ROOT_PW="$(sed -n 's/^DB_ROOT_PASSWORD=//p' .env | head -1)"
docker compose exec -T db mysql -uroot -p"$ROOT_PW" -e "
CREATE DATABASE IF NOT EXISTS authcoredb CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS upmarket   CHARACTER SET utf8mb4;
GRANT ALL PRIVILEGES ON authcoredb.* TO 'upmarket'@'%';
GRANT ALL PRIVILEGES ON upmarket.*   TO 'upmarket'@'%';
FLUSH PRIVILEGES;" 2>/dev/null
echo ">> Bases y permisos verificados."
