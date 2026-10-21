# Supervision ops (minimal)

Objectif : détecter une panne en moins de ~2 minutes sans stack monitoring lourde.

## Probes

| Cible | URL | Attendu |
|-------|-----|---------|
| Health public | `https://<host>/healthz` (nginx → actuator) | JSON `{"status":"UP"}` |
| Health direct (réseau interne) | `http://backend:8080/actuator/health` | `UP` |
| Page login | `https://<host>/login` | HTTP 200 |

Le healthcheck Compose prod interroge déjà `http://localhost:8080/actuator/health` dans le conteneur backend.

## Sonde externe (recommandé)

Configurer un check toutes les **1–2 min** (UptimeRobot, Healthchecks.io, Better Stack, etc.) :

1. **Health** : `GET https://votre-domaine/healthz` — alerte si status ≠ 200 ou body sans `"UP"`.
2. **Login** : `GET https://votre-domaine/login` — alerte si status ≠ 200 ou timeout.

Exemple cron hôte (alerte mail via `mail`) :

```bash
*/2 * * * * curl -fsS https://votre-domaine/healthz | grep -q '"status":"UP"' || echo "HEALTH DOWN" | mail -s "gestion DOWN" ops@example.com
```

## 5xx nginx

Fichier d’accès typique (conteneur nginx) : `/var/log/nginx/access.log`.

Compter les 5xx sur les 5 dernières minutes :

```bash
docker compose -f docker-compose.prod.yml exec -T nginx \
  sh -c "grep ' 5[0-9][0-9] ' /var/log/nginx/access.log | tail -n 200"
```

Alerte simple (cron) : si le compte dépasse un seuil, envoyer un mail.

## Logs applicatifs

- Backend : volume `backend_logs` → `/var/log/gestion/application.log`
- Chercher erreurs : `ERROR`, `Exception`

```bash
docker compose -f docker-compose.prod.yml exec -T backend \
  sh -c "tail -n 100 /var/log/gestion/application.log"
```

## Prometheus (optionnel)

Le profil Spring `prod` n’expose pas `prometheus` par défaut. Pour un scrape contrôlé, activer temporairement hors profil ou via config ops dédiée — ne pas ouvrir `/actuator/prometheus` publiquement sans auth réseau.

## Critère de done

Une alerte (externe ou cron) se déclenche si la page login ou le health backend est down pendant ≥ 2 minutes.
