#!/usr/bin/env bash
# Sauvegarde Postgres (pg_dump custom) + bucket MinIO via le stack docker-compose.prod.yml.
# Prérequis: docker compose, mc (MinIO Client) installé sur l'hôte pour MinIO.
# Usage: ./scripts/backup/backup.sh [dossier_sortie]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMPOSE=(docker compose -f "$ROOT/docker-compose.prod.yml")
OUT_ROOT="${1:-$ROOT/backups}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$OUT_ROOT/$STAMP"
mkdir -p "$OUT"

# Charger .env pour MinIO (noms de bucket / credentials)
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

echo "==> Postgres dump → $OUT/postgres.dump"
"${COMPOSE[@]}" exec -T postgres \
  pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc -f /tmp/gestion.dump
"${COMPOSE[@]}" cp postgres:/tmp/gestion.dump "$OUT/postgres.dump"
"${COMPOSE[@]}" exec -T postgres rm -f /tmp/gestion.dump

echo "==> MinIO mirror → $OUT/minio/"
if ! command -v mc >/dev/null 2>&1; then
  echo "ATTENTION: 'mc' (MinIO Client) introuvable — dump Postgres OK, MinIO non sauvegardé." >&2
  echo "Installez mc: https://min.io/docs/minio/linux/reference/minio-mc.html" >&2
else
  if [[ -z "$MINIO_ROOT_USER" || -z "$MINIO_ROOT_PASSWORD" ]]; then
    echo "ERREUR: MINIO_ROOT_USER / MINIO_ROOT_PASSWORD requis dans .env" >&2
    exit 1
  fi
  # Port MinIO non exposé en prod compose — on passe par le réseau docker
  ENDPOINT="http://127.0.0.1:9000"
  # Si MinIO n'est pas publié, utiliser un conteneur éphémère sur le même réseau
  NET="$("${COMPOSE[@]}" ps -q minio | xargs -I{} docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}' {} | head -n1)"
  mkdir -p "$OUT/minio"
  docker run --rm --network "$NET" \
    -e MC_HOST_src="http://${MINIO_ROOT_USER}:${MINIO_ROOT_PASSWORD}@minio:9000" \
    -v "$OUT/minio:/backup" \
    minio/mc:latest \
    mirror --overwrite "src/${MINIO_BUCKET}" /backup/
fi

echo "$STAMP" > "$OUT/MANIFEST.txt"
echo "Backup terminé: $OUT"
