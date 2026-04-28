#!/usr/bin/env bash
# Tests API E2E module contrats (bash / Git Bash / WSL).
# Prérequis : curl, python3 (pour extraire le JWT du JSON). Optionnel : jq à la place de python3.
# Données : migration V21 (comptes + salarié). Mot de passe : AdminTest123!
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/scripts/e2e-contrats.env"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

API_BASE="${API_BASE:-http://localhost:8080/api/v1}"
API_BASE="${API_BASE%/}"
E2E_SALARIE_ID="${E2E_SALARIE_ID:-c0e2e0e0-0000-4000-8000-000000000001}"
E2E_RH_EMAIL="${E2E_RH_EMAIL:-rh@test.com}"
E2E_RH_PASSWORD="${E2E_RH_PASSWORD:-AdminTest123!}"
E2E_ADMIN_EMAIL="${E2E_ADMIN_EMAIL:-admin@test.com}"
E2E_ADMIN_PASSWORD="${E2E_ADMIN_PASSWORD:-AdminTest123!}"
E2E_FINANCIER_EMAIL="${E2E_FINANCIER_EMAIL:-financier@test.com}"
E2E_FINANCIER_PASSWORD="${E2E_FINANCIER_PASSWORD:-AdminTest123!}"
E2E_EMPLOYE_EMAIL="${E2E_EMPLOYE_EMAIL:-employe@test.com}"
E2E_EMPLOYE_PASSWORD="${E2E_EMPLOYE_PASSWORD:-AdminTest123!}"

json_token() {
  if command -v jq >/dev/null 2>&1; then
    jq -r '.data.accessToken'
  else
    python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])"
  fi
}

login() {
  local email="$1" password="$2"
  curl -sS -X POST "$API_BASE/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$password\"}" | json_token
}

require_http() {
  local code="$1" want="$2" msg="$3"
  if [[ "$code" != "$want" ]]; then
    echo "ECHEC: $msg (HTTP $code, attendu $want)" >&2
    exit 1
  fi
  echo "  OK $msg"
}

echo ""
echo "=== E2E contrats RH (API_BASE=$API_BASE) ==="
echo ""

echo "-- Non-régression (admin) --"
TOK_ADMIN="$(login "$E2E_ADMIN_EMAIL" "$E2E_ADMIN_PASSWORD")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X GET "$API_BASE/rh/salaries?page=0&size=5" \
  -H "Authorization: Bearer $TOK_ADMIN")"
require_http "$code" 200 "GET /rh/salaries"

TODAY="$(python3 -c "from datetime import date; print(date.today().isoformat())")"
TOMORROW="$(python3 -c "from datetime import date,timedelta; print((date.today()+timedelta(days=1)).isoformat())")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X POST "$API_BASE/rh/conges?draft=true" \
  -H "Authorization: Bearer $TOK_ADMIN" -H "Content-Type: application/json" \
  -d "{\"salarieId\":\"$E2E_SALARIE_ID\",\"typeConge\":\"MALADIE\",\"dateDebut\":\"$TODAY\",\"dateFin\":\"$TOMORROW\",\"commentaire\":\"e2e-contrats-api.sh\"}")"
require_http "$code" 200 "POST /rh/conges?draft=true"

YEAR="$(python3 -c "from datetime import date; print(date.today().year)")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X GET "$API_BASE/rh/paie/$E2E_SALARIE_ID/$YEAR" \
  -H "Authorization: Bearer $TOK_ADMIN")"
if [[ "$code" != "200" && "$code" != "404" ]]; then
  echo "ECHEC: GET paie (HTTP $code, attendu 200 ou 404)" >&2
  exit 1
fi
echo "  OK GET /rh/paie/{salarie}/{annee} -> $code"

echo ""
echo "-- Contrat CDD + échéance FIN_CDD --"
DEBUT="$(python3 -c "from datetime import date,timedelta; print((date.today()-timedelta(days=10)).isoformat())")"
FIN60="$(python3 -c "from datetime import date,timedelta; print((date.today()+timedelta(days=60)).isoformat())")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X POST "$API_BASE/rh/contrats/salaries/$E2E_SALARIE_ID" \
  -H "Authorization: Bearer $TOK_ADMIN" -H "Content-Type: application/json" \
  -d "{\"typeContrat\":\"CDD\",\"dateDebutContrat\":\"$DEBUT\",\"dateFinContrat\":\"$FIN60\",\"dateFinPeriodeEssai\":null,\"dureeEssaiMois\":null,\"numeroContrat\":null,\"intitulePoste\":\"E2E Poste\",\"motifCdd\":\"Remplacement\",\"conventionCollective\":null}")"
require_http "$code" 201 "POST contrat CDD"
CONTRAT_ID="$(python3 -c "import json; print(json.load(open('/tmp/e2e_body.json'))['data']['id'])")"

code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X GET "$API_BASE/rh/contrats/echeances?salarieId=$E2E_SALARIE_ID&page=0&size=50" \
  -H "Authorization: Bearer $TOK_ADMIN")"
require_http "$code" 200 "GET echeances"
python3 - <<PY
import json,sys
j=json.load(open("/tmp/e2e_body.json"))
rows=[x for x in j["data"]["content"] if x.get("typeEcheance")=="FIN_CDD" and str(x.get("dateEcheance"))=="$FIN60"]
sys.exit(0 if rows else 1)
PY
echo "  OK échéance FIN_CDD à $FIN60"

echo ""
echo "-- Renouvellement CDD (RH) --"
TOK_RH="$(login "$E2E_RH_EMAIL" "$E2E_RH_PASSWORD")"
NOUVELLE_FIN="$(python3 -c "from datetime import date,timedelta; print((date.today()+timedelta(days=120)).isoformat())")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X POST "$API_BASE/rh/contrats/$CONTRAT_ID/renouveler" \
  -H "Authorization: Bearer $TOK_RH" -H "Content-Type: application/json" \
  -d "{\"nouvelleDateFin\":\"$NOUVELLE_FIN\",\"motif\":\"Prolongation E2E\",\"commentaire\":null}")"
require_http "$code" 200 "POST renouveler"

code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X GET "$API_BASE/rh/contrats/echeances?salarieId=$E2E_SALARIE_ID&page=0&size=50" \
  -H "Authorization: Bearer $TOK_ADMIN")"
require_http "$code" 200 "GET echeances après renouvellement"
python3 - <<PY
import json,sys
j=json.load(open("/tmp/e2e_body.json"))
c=j["data"]["content"]
ann=[x for x in c if x.get("typeEcheance")=="FIN_CDD" and str(x.get("dateEcheance"))=="$FIN60" and x.get("statut")=="ANNULEE"]
nouv=[x for x in c if x.get("typeEcheance")=="FIN_CDD" and str(x.get("dateEcheance"))=="$NOUVELLE_FIN"]
sys.exit(0 if ann and nouv else 1)
PY
echo "  OK ancienne FIN_CDD ANNULEE + nouvelle à $NOUVELLE_FIN"

echo ""
echo "-- Permissions --"
TOK_EMP="$(login "$E2E_EMPLOYE_EMAIL" "$E2E_EMPLOYE_PASSWORD")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X GET "$API_BASE/rh/contrats?page=0&size=5" \
  -H "Authorization: Bearer $TOK_EMP")"
require_http "$code" 403 "EMPLOYE GET /rh/contrats -> 403"

TOK_FIN="$(login "$E2E_FINANCIER_EMAIL" "$E2E_FINANCIER_PASSWORD")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X GET "$API_BASE/rh/contrats/echeances/dashboard" \
  -H "Authorization: Bearer $TOK_FIN")"
require_http "$code" 200 "FINANCIER GET dashboard"

FUTURE="$(python3 -c "from datetime import date,timedelta; print((date.today()+timedelta(days=40)).isoformat())")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X POST "$API_BASE/rh/contrats/echeances" \
  -H "Authorization: Bearer $TOK_FIN" -H "Content-Type: application/json" \
  -d "{\"salarieId\":\"$E2E_SALARIE_ID\",\"contratId\":null,\"typeEcheance\":\"AUTRE\",\"titre\":\"E2E manuelle\",\"description\":null,\"dateEcheance\":\"$FUTURE\",\"priorite\":2,\"responsableId\":null}")"
require_http "$code" 403 "FINANCIER POST /echeances -> 403"

echo ""
echo "-- Visite médicale --"
DR="$(python3 -c "from datetime import date,timedelta; print((date.today()-timedelta(days=1)).isoformat())")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X POST "$API_BASE/rh/contrats/salaries/$E2E_SALARIE_ID/visites" \
  -H "Authorization: Bearer $TOK_ADMIN" -H "Content-Type: application/json" \
  -d "{\"typeVisite\":\"PERIODIQUE\",\"datePlanifiee\":\"$DR\",\"dateRealisee\":\"$DR\",\"medecin\":null,\"centreMedical\":null,\"resultat\":\"APTE\",\"restrictions\":null,\"periodiciteMois\":12}")"
require_http "$code" 201 "POST visite"
EXPECTED_PROCH="$(python3 - <<PY
from datetime import date
import calendar
from datetime import timedelta
d=date.fromisoformat("$DR")
# +12 mois (approximation calendrier comme Java plusMonths)
y, m = d.year, d.month
m += 12
while m > 12:
    m -= 12
    y += 1
last = calendar.monthrange(y, m)[1]
day = min(d.day, last)
print(date(y, m, day).isoformat())
PY
)"
PROCH="$(python3 -c "import json; print(json.load(open('/tmp/e2e_body.json'))['data']['prochaineVisite'])")"
if [[ "$PROCH" != "$EXPECTED_PROCH" ]]; then
  echo "ECHEC: prochaineVisite $PROCH != $EXPECTED_PROCH" >&2
  exit 1
fi
echo "  OK prochaineVisite = +12 mois ($PROCH)"

echo ""
echo "-- Titre de séjour (multipart) --"
EXP100="$(python3 -c "from datetime import date,timedelta; print((date.today()+timedelta(days=100)).isoformat())")"
EMIT="$(python3 -c "from datetime import date,timedelta; print((date.today()-timedelta(days=200)).isoformat())")"
TMPJSON="$(mktemp)"
python3 <<PY
import json
open("$TMPJSON", "w", encoding="utf-8").write(
    json.dumps(
        {
            "typeDocument": "TITRE_SEJOUR",
            "numeroDocument": "E2E-TS-001",
            "paysEmetteur": "FR",
            "dateEmission": "$EMIT",
            "dateExpiration": "$EXP100",
            "autoriteEmettrice": "Préfecture",
        },
        separators=(",", ":"),
    )
)
PY
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X POST "$API_BASE/rh/contrats/salaries/$E2E_SALARIE_ID/titres-sejour" \
  -H "Authorization: Bearer $TOK_ADMIN" -F "data=@${TMPJSON};type=application/json")"
rm -f "$TMPJSON"
require_http "$code" 201 "POST titres-sejour"

ATTENDU_ALERTE="$(python3 -c "from datetime import date,timedelta; print((date.today()+timedelta(days=10)).isoformat())")"
code="$(curl -sS -o /tmp/e2e_body.json -w "%{http_code}" -X GET "$API_BASE/rh/contrats/echeances?salarieId=$E2E_SALARIE_ID&type=TITRE_SEJOUR&page=0&size=20" \
  -H "Authorization: Bearer $TOK_ADMIN")"
require_http "$code" 200 "GET echeances TITRE_SEJOUR"
python3 - <<PY
import json,sys
j=json.load(open("/tmp/e2e_body.json"))
rows=[x for x in j["data"]["content"] if str(x.get("dateEcheance"))=="$ATTENDU_ALERTE" and x.get("statut")=="EN_ALERTE"]
sys.exit(0 if rows else 1)
PY
echo "  OK échéance titre $ATTENDU_ALERTE EN_ALERTE"

echo ""
echo "=== Tous les tests API scriptés sont passés ==="
echo "Manuel : UI, WebSocket congés, scheduler (cron ou tests unitaires)."
