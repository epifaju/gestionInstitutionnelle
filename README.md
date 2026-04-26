## Gestion Institutionnelle — Production checklist

### Démarrage rapide (local via Docker)
- **Pré-requis**: Docker + Docker Compose.
- **Configurer**: copier `.env.example` vers `.env` et renseigner au minimum `JWT_PRIVATE_KEY_B64` / `JWT_PUBLIC_KEY_B64`.
- **Lancer**:

```bash
docker compose up --build
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
- **Lancer**:

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

- **TLS**:
  - déposer les certificats dans `nginx/certs/`:
    - `nginx/certs/fullchain.pem`
    - `nginx/certs/privkey.pem`
  - l’HTTP (80) redirige automatiquement vers HTTPS (443).

- **Variables importantes**:
  - **DB**: `DB_*`
  - **JWT (RS256)**: `JWT_PRIVATE_KEY_B64`, `JWT_PUBLIC_KEY_B64`
  - **MinIO**: `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `MINIO_BUCKET`, `MINIO_PUBLIC_ENDPOINT`
  - **CORS**: `FRONTEND_URL`
  - **Cookie refresh**: mettre `REFRESH_COOKIE_SECURE=true` en HTTPS

### Observabilité
- **Logs**: fichier par défaut `/var/log/gestion/application.log` (voir `LOGGING_FILE_NAME`).
- **Metrics Prometheus (backend direct)**: `GET http://localhost:8080/actuator/prometheus` (à protéger en prod selon ton infra).

### CI
- **GitHub Actions**: workflow `.github/workflows/ci.yml` (backend tests + frontend build + build images Docker).

### À faire avant une mise en prod “sérieuse” (P0)
- **HTTPS/TLS**: activer le bloc 443 dans `nginx/nginx.prod.conf` + certificats + HSTS.
- **Secrets**: utiliser un secret store (pas de secrets en clair dans `.env` en prod).
- **Sauvegardes**: stratégie backup/restore Postgres + MinIO (et test de restore).
- **Alerting**: dashboards + alertes (health, erreurs 5xx, latence, DB).
