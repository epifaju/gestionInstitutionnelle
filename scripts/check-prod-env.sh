#!/usr/bin/env bash
# Vérifie qu'un fichier .env (défaut: ./.env) est apte à une mise en prod.
# Usage: ./scripts/check-prod-env.sh [.env]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${1:-$ROOT/.env}"
ERR=0

fail() { echo "ERREUR: $*" >&2; ERR=1; }
ok() { echo "OK: $*"; }
warn() { echo "ATTENTION: $*" >&2; }

if [[ ! -f "$ENV_FILE" ]]; then
  fail "Fichier introuvable: $ENV_FILE"
  exit 1
fi

# shellcheck disable=SC1090
set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

require_nonempty() {
  local name="$1"
  local val="${!name-}"
  if [[ -z "${val// }" ]]; then
    fail "$name est vide"
  else
    ok "$name renseigné"
  fi
}

forbid_default() {
  local name="$1"
  local bad="$2"
  local val="${!name-}"
  if [[ "$val" == "$bad" ]]; then
    fail "$name utilise encore la valeur d'exemple ($bad)"
  fi
}

require_nonempty DB_NAME
require_nonempty DB_USER
require_nonempty DB_PASSWORD
forbid_default DB_PASSWORD "gestion"

require_nonempty JWT_PRIVATE_KEY_B64
require_nonempty JWT_PUBLIC_KEY_B64

require_nonempty MINIO_ROOT_USER
require_nonempty MINIO_ROOT_PASSWORD
forbid_default MINIO_ROOT_PASSWORD "minio12345"
forbid_default MINIO_ROOT_USER "minio"

require_nonempty FRONTEND_URL
if [[ "${FRONTEND_URL}" != https://* ]]; then
  fail "FRONTEND_URL doit commencer par https:// (actuel: ${FRONTEND_URL})"
else
  ok "FRONTEND_URL en HTTPS"
fi

if [[ "${REFRESH_COOKIE_SECURE:-false}" != "true" ]]; then
  fail "REFRESH_COOKIE_SECURE doit être true en HTTPS"
else
  ok "REFRESH_COOKIE_SECURE=true"
fi

CERT_DIR="$ROOT/nginx/certs"
if [[ ! -f "$CERT_DIR/fullchain.pem" || ! -f "$CERT_DIR/privkey.pem" ]]; then
  fail "Certificats manquants: nginx/certs/fullchain.pem et privkey.pem"
else
  ok "Certificats TLS présents"
fi

if [[ $ERR -ne 0 ]]; then
  echo "" >&2
  echo "Corrigez les erreurs ci-dessus avant: docker compose -f docker-compose.prod.yml up -d --build" >&2
  exit 1
fi

echo ""
echo "Environnement prod OK."
