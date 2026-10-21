#!/usr/bin/env bash
# Génère une paire RSA 2048 pour JWT RS256 et affiche les variables .env (base64 une ligne).
set -euo pipefail

TMPDIR="${TMPDIR:-/tmp}"
PRIV="$TMPDIR/gestion-jwt-private.pem"
PUB="$TMPDIR/gestion-jwt-public.pem"

openssl genrsa -out "$PRIV" 2048 >/dev/null 2>&1
openssl rsa -in "$PRIV" -pubout -out "$PUB" >/dev/null 2>&1

if command -v base64 >/dev/null 2>&1; then
  if base64 --help 2>&1 | grep -q -- '-w'; then
    PRIV_B64=$(base64 -w0 < "$PRIV")
    PUB_B64=$(base64 -w0 < "$PUB")
  else
    PRIV_B64=$(base64 < "$PRIV" | tr -d '\n')
    PUB_B64=$(base64 < "$PUB" | tr -d '\n')
  fi
else
  echo "base64 introuvable" >&2
  exit 1
fi

rm -f "$PRIV" "$PUB"

echo "# Coller dans .env (échappez \$ en \$\$ sous Docker Compose si besoin)"
echo "JWT_PRIVATE_KEY_B64=${PRIV_B64}"
echo "JWT_PUBLIC_KEY_B64=${PUB_B64}"
