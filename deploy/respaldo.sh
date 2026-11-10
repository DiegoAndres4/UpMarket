#!/usr/bin/env bash
# Respaldo de las bases (authcoredb y upmarket) y de las fotos. Conserva los últimos N días.
# Uso: ./deploy/respaldo.sh [carpeta_destino] [dias_a_conservar]
# Cron diario (3:15am):  15 3 * * * cd /home/ubuntu/UpMarket && ./deploy/respaldo.sh >> respaldos.log 2>&1
set -euo pipefail
cd "$(dirname "$0")/.."
DEST="${1:-$PWD/respaldos}"; DIAS="${2:-14}"; FECHA="$(date +%Y%m%d-%H%M%S)"
sed -i 's/\r$//' .env 2>/dev/null || true
ROOT_PW="$(sed -n 's/^DB_ROOT_PASSWORD=//p' .env | head -1)"
mkdir -p "$DEST"; chmod 700 "$DEST"
docker compose exec -T db mysqldump -uroot -p"$ROOT_PW" --single-transaction --databases authcoredb upmarket 2>/dev/null | gzip > "$DEST/bd-$FECHA.sql.gz"
docker compose run --rm --no-deps -T -v "$DEST":/respaldo --entrypoint sh domain -c "tar czf /respaldo/fotos-$FECHA.tar.gz -C /data/fotos ." 
[ -s "$DEST/bd-$FECHA.sql.gz" ] || { echo "ERROR: respaldo de BD vacío" >&2; exit 1; }
find "$DEST" -type f \( -name 'bd-*' -o -name 'fotos-*' \) -mtime +"$DIAS" -delete
echo ">> Respaldo listo en $DEST ($FECHA)"
echo "   Restaurar BD:  gunzip -c $DEST/bd-$FECHA.sql.gz | docker compose exec -T db mysql -uroot -p\"\$DB_ROOT_PASSWORD\""
echo "   Copia también a S3/otra máquina: un respaldo en el mismo disco no protege si el disco falla."
