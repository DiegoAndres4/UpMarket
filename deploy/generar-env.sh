#!/usr/bin/env bash
# Genera .env con secretos aleatorios. JWT_SECRET (AuthCore) y JWT_SECRET_BASE64 (servicio de dominio)
# representan EXACTAMENTE los mismos bytes: si no coinciden, el dominio rechaza todos los tokens.
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -f .env ]; then echo ".env ya existe; no se sobrescribe (bórralo si quieres regenerarlo)."; exit 0; fi
JWT_SECRET="$(openssl rand -hex 32)"
cat > .env <<ENV
DB_ROOT_PASSWORD=$(openssl rand -hex 16)
DB_PASSWORD=$(openssl rand -hex 16)
JWT_SECRET=${JWT_SECRET}
JWT_SECRET_BASE64=$(printf '%s' "${JWT_SECRET}" | base64 -w0)
# Opcional: ruta local de AuthCore si no se quiere construir desde GitHub
# AUTHCORE_CONTEXT=../Auth-Core-UpMarket
ENV
chmod 600 .env
echo ".env creado. Guárdalo en un lugar seguro: NO se sube a Git."
