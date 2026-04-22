#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  if [[ -f .env.example ]]; then
    cp .env.example .env
    echo "Fichier .env créé à partir de .env.example — complétez les variables puis relancez ce script."
    exit 1
  else
    echo "Erreur : .env absent et .env.example introuvable."
    exit 1
  fi
fi

set -a
# shellcheck source=/dev/null
source .env
set +a

need_keys=0
if [[ -z "${JWT_PRIVATE_KEY_B64:-}" || -z "${JWT_PUBLIC_KEY_B64:-}" ]]; then
  need_keys=1
fi

if [[ "$need_keys" -eq 1 ]]; then
  echo "Génération des clés RSA JWT (2048 bits)…"
  TMPDIR_KEYS="$(mktemp -d)"
  trap 'rm -rf "$TMPDIR_KEYS"' EXIT
  openssl genrsa -out "$TMPDIR_KEYS/private.pem" 2048
  openssl rsa -in "$TMPDIR_KEYS/private.pem" -pubout -out "$TMPDIR_KEYS/public.pem"
  PRIV_B64="$(base64 -w0 "$TMPDIR_KEYS/private.pem" 2>/dev/null || base64 "$TMPDIR_KEYS/private.pem" | tr -d '\n')"
  PUB_B64="$(base64 -w0 "$TMPDIR_KEYS/public.pem" 2>/dev/null || base64 "$TMPDIR_KEYS/public.pem" | tr -d '\n')"
  if grep -q '^JWT_PRIVATE_KEY_B64=' .env; then
    sed -i.bak "s|^JWT_PRIVATE_KEY_B64=.*|JWT_PRIVATE_KEY_B64=$PRIV_B64|" .env
    sed -i.bak "s|^JWT_PUBLIC_KEY_B64=.*|JWT_PUBLIC_KEY_B64=$PUB_B64|" .env
    rm -f .env.bak
  else
    printf '\nJWT_PRIVATE_KEY_B64=%s\nJWT_PUBLIC_KEY_B64=%s\n' "$PRIV_B64" "$PUB_B64" >>.env
  fi
  echo "Clés JWT insérées dans .env"
fi

echo "Démarrage Docker (docker-compose.prod.yml)…"
docker compose -f docker-compose.prod.yml up -d --build

echo "Attente du backend (actuator health)…"
for i in $(seq 1 60); do
  if docker compose -f docker-compose.prod.yml exec -T backend wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "Backend prêt."
    break
  fi
  if [[ "$i" -eq 60 ]]; then
    echo "Le backend n'est pas passé UP à temps — vérifiez les logs : docker compose -f docker-compose.prod.yml logs backend"
    exit 1
  fi
  sleep 2
done

echo ""
echo "Application : ${FRONTEND_URL:-http://localhost}"
echo "Compte admin de démo (après migrations) : admin@test.com / AdminTest123!"
echo ""
