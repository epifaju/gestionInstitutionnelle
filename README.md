## Gestion Institutionnelle — Production checklist

### Démarrage rapide (local via Docker)
- **Pré-requis**: Docker + Docker Compose (≥ 2.24 recommandé pour le profil perf).
- **Configurer**: copier `.env.example` vers `.env` et renseigner au minimum `JWT_PRIVATE_KEY_B64` / `JWT_PUBLIC_KEY_B64`.
- **Lancer (dev — `next dev`, hot reload)**:

```bash
docker compose up --build
```

  Le frontend n’exécute `npm ci` qu’au premier démarrage (ou si `package-lock.json` a changé) ; le volume `frontend_node_modules` conserve les deps entre redémarrages. Nginx dev active gzip (JSON/JS/CSS).

- **Profil perf (frontend précompilé, sans bind-mount)** — pour mesurer la latence de navigation réelle (~élimine 1–4 s de compile Webpack) :

```bash
docker compose -f docker-compose.yml -f docker-compose.perf.yml up --build
```

  Rebuild frontend uniquement après changement UI :

```bash
docker compose -f docker-compose.yml -f docker-compose.perf.yml up --build frontend
```

### Build sans Docker
- **Backend**: nécessite **JDK 21** (sinon Maven échoue avec “release version 21 not supported”).
- **Frontend**: `npm ci` puis `npm run build` (la build “standalone” est la plus fiable sous Linux/CI; sous Windows, Next peut avoir des particularités de chemins).

#### Lancer les tests backend (Windows)
Si ton `mvn` utilise Java 17 alors que le projet est en Java 21, `mvn test` peut échouer (classfile 65 vs 61).

- **Solution recommandée**: forcer l’usage de Java 21 pour Maven :

```powershell
.\backend\scripts\mvn-test-java21.cmd
```

- **Option**: passer des arguments Maven :

```powershell
.\backend\scripts\mvn-test-java21.cmd test
```

- **Accès**:
  - **App**: `http://localhost`
  - **API**: `http://localhost/api`
  - **Health (backend direct)**: `http://localhost:8080/actuator/health`

### Prod (docker-compose.prod.yml)

#### 1. Secrets & TLS (P0.3)

```bash
# Générer les clés JWT
./scripts/generate-jwt-keys.sh          # ou .\scripts\generate-jwt-keys.ps1

# Copier .env.example → .env, renseigner DB_*, MinIO, FRONTEND_URL=https://…,
# REFRESH_COOKIE_SECURE=true, coller les JWT_*.

# Déposer les certificats (non versionnés) :
#   nginx/certs/fullchain.pem
#   nginx/certs/privkey.pem

# Vérifier avant démarrage (échoue si valeurs d’exemple / certs absents)
./scripts/check-prod-env.sh             # ou .\scripts\check-prod-env.ps1
```

#### 2. Lancer

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

- HTTP (80) redirige vers HTTPS (443).
- Nginx `client_max_body_size` = **55M** (aligné GED 50 MiB).
- Sonde publique : `https://<host>/healthz` → actuator health.

#### 3. Comptes fixture (P0.2)

En profil `prod`, le backend **désactive au démarrage** les emails Flyway/E2E :
`admin@test.com`, `rh@test.com`, `financier@test.com`, `employe@test.com`
(mot de passe documenté `AdminTest123!` — ne jamais les laisser actifs en prod).

**Bootstrap du premier ADMIN réel** (une fois) :

```sql
-- Temporaire : réactiver le compte fixture pour créer un vrai ADMIN via l’UI /admin/users,
-- puis changer son mot de passe. Au redémarrage suivant, les fixtures sont à nouveau désactivés.
UPDATE utilisateurs SET actif = true WHERE lower(email) = lower('admin@test.com');
```

Ensuite créer `admin@votre-domaine.org` (rôle ADMIN) dans l’UI, puis vous connecter uniquement avec ce compte.

#### 4. Sauvegardes (P0.4)

```bash
./scripts/backup/backup.sh              # → backups/<timestamp>/postgres.dump + minio/
./scripts/backup/restore.sh backups/<timestamp>   # demande confirmation « RESTORE »
```

**Test de restore obligatoire** sur un environnement de staging (instance vide) : login + ouverture d’un document GED.

#### 5. Supervision (P0.5)

Voir [docs/ops-supervision.md](docs/ops-supervision.md) : sonde `/healthz` + `/login`, détection 5xx nginx, logs applicatifs.

### Observabilité
- **Logs**: fichier par défaut `/var/log/gestion/application.log` (voir `LOGGING_FILE_NAME`).
- **Health public**: `GET /healthz`.
- **Metrics Prometheus (backend direct)**: non exposés par défaut en profil `prod` (à activer côté ops si besoin, derrière un réseau privé).

### CI
- **GitHub Actions**: workflow `.github/workflows/ci.yml` (backend tests + frontend build/tests + images Docker + Trivy). Playwright E2E opt-in.

### Checklist P0 / R1–R5 (ops)

Séquence locale validée (certs auto-signés + `.env.prod`) ; sur serveur réel, remplacer par certs Let’s Encrypt / secrets définitifs.

```powershell
.\scripts\ops\prepare-local-prod.ps1          # R1 local
.\scripts\check-prod-env.ps1 -EnvFile .\.env.prod
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build   # R2
.\scripts\ops\bootstrap-admin.ps1             # R3
.\scripts\backup\backup.ps1                   # R4
.\scripts\backup\restore.ps1 -BackupDir backups\<stamp> -ConfirmRestore
.\scripts\ops\probe-healthz.ps1               # R5
```

- [x] nginx body size aligné GED (55M)
- [x] comptes fixture désactivés en profil prod
- [x] scripts JWT + `check-prod-env` + certs gitignorés
- [x] scripts backup/restore Postgres + MinIO (+ `.ps1` Windows)
- [x] runbook supervision (`docs/ops-supervision.md`) + `probe-healthz.ps1`
- [x] R1–R5 dry-run local (prepare → deploy → bootstrap → backup/restore → probe)
- [ ] **Sur serveur réel** : certs non auto-signés + sonde externe (UptimeRobot / cron) + cron backup quotidien (`scripts/backup/CRON.example.txt`)
