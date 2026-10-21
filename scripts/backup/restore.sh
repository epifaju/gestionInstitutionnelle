#!/usr/bin/env bash
# Restaure un dump Postgres + miroir MinIO produits par backup.sh.
# Usage: ./scripts/backup/restore.sh <dossier_backup>   # ex. backups/20261021T120000Z
# Demande confirmation interactive.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/docker-compose.prod.yml")
SRC="${1:-}"

if [[ -z "$SRC" || ! -d "$SRC" ]]; then
  echo "Usage: $0 <dossier_backup>" >&2
  exit 1
fi
if [[ ! -f "$SRC/postgres.dump" ]]; then
  echo "ERREUR: $SRC/postgres.dump manquant" >&2
  exit 1
fi

if [[ -f "$ROOT/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ROOT/.env"
  set +a
fi

DB_NAME="${DB_NAME:-gestion}"
DB_USER="${DB_USER:-gestion}"
MINIO_BUCKET="${MINIO_BUCKET:-gestion}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:-}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-}"

echo "ATTENTION: cette opération écrase la base $DB_NAME et le bucket MinIO $MINIO_BUCKET."
read -r -p "Tapez RESTORE pour confirmer: " CONFIRM
if [[ "$CONFIRM" != "RESTORE" ]]; then
  echo "Annulé."
  exit 1
fi

echo "==> Restauration Postgres"
"${COMPOSE[@]}" cp "$SRC/postgres.dump" postgres:/tmp/gestion.dump
"${COMPOSE[@]}" exec -T postgres \
  pg_restore -U "$DB_USER" -d "$DB_NAME" --clean --if-exists /tmp/gestion.dump || true
"${COMPOSE[@]}" exec -T postgres rm -f /tmp/gestion.dump

if [[ -d "$SRC/minio" ]] && command -v docker >/dev/null 2>&1; then
  if [[ -z "$MINIO_ROOT_USER" || -z "$MINIO_ROOT_PASSWORD" ]]; then
    echo "ATTENTION: credentials MinIO manquants — skip restore objets" >&2
  else
    echo "==> Restauration MinIO"
    NET="$("${COMPOSE[@]}" ps -q minio | xargs -I{} docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' {} | head -n1)"
    docker run --rm --network "$NET" \
      -e MC_HOST_dst="http://${MINIO_ROOT_USER}:${MINIO_ROOT_PASSWORD}@minio:9000" \
      -v "$SRC/minio:/backup:ro" \
      minio/mc:latest \
      mirror --overwrite /backup/ "dst/${MINIO_BUCKET}"
  fi
else
  echo "ATTENTION: pas de dossier minio/ ou docker indisponible — objets non restaurés"
fi

echo "Restore terminé. Redémarrez le backend si besoin: ${COMPOSE[*]} restart backend"
echo "Test manuel: login + ouverture d'un document GED."
