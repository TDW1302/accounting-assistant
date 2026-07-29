# Déploiement en production — NAS UGREEN (Docker)

Ce document décrit la procédure pour mettre `accounting-assistant` en production sur le NAS UGREEN, via Docker.

**Déploiement déjà effectué** sur le NAS. Si tu reviens ici après une longue pause pour juste mettre à jour ou vérifier que tout tourne, va directement à la section **[Reprise après une longue pause](#reprise-après-une-longue-pause)**. Le reste du document (étapes 0 à 5) ne sert qu'en cas de réinstallation complète.

## Vue d'ensemble de la chaîne existante

Le dépôt contient déjà tout le nécessaire :

1. **[Dockerfile](Dockerfile)** — build multi-stage (frontend Angular → backend Spring Boot → image finale nginx + JRE).
2. **[.github/workflows/docker.yml](.github/workflows/docker.yml)** — à chaque push sur `main`, GitHub Actions build l'image et la pousse sur **GHCR** :
   `ghcr.io/tdw1302/accounting-assistant:latest` (+ tag `:<sha>`).
3. **[docker-compose.prod.yml](docker-compose.prod.yml)** — stack de prod : le conteneur `app` (image GHCR) + un reverse-proxy **Caddy** qui gère le HTTPS via DuckDNS, exposé sur le port **8999**.
4. **[Caddyfile](Caddyfile)** — config Caddy pointant sur `tdw1302.duckdns.org:8999`.

➡️ **Il n'y a rien à construire manuellement.** Le seul travail restant est côté NAS : récupérer l'image et lancer la stack.

---

## Étape 0 — Vérifier la visibilité du package GHCR

Va sur `https://github.com/TDW1302?tab=packages` (ou l'onglet **Packages** du repo) et regarde si `accounting-assistant` est **Public** ou **Private**.

- **Si Public** → passe directement à l'étape 1, aucune authentification n'est nécessaire pour `docker pull` sur le NAS.
- **Si Private** → il faut un token pour que le NAS puisse tirer l'image :
  1. Sur GitHub : **Settings → Developer settings → Personal access tokens → Tokens (classic)** → générer un token avec le scope `read:packages` uniquement.
  2. Sur le NAS (en SSH, cf. étape 2) :
     ```bash
     echo "<TON_TOKEN>" | docker login ghcr.io -u TDW1302 --password-stdin
     ```
  3. Ce login est à refaire uniquement si le token expire ou change de conteneur Docker host.

  *(Alternative plus simple : rendre le package public une fois pour toutes, via **Package settings → Change visibility**, si le code n'a rien de sensible — les vraies clés/API restent dans `.env`, jamais dans l'image.)*

---

## Étape 1 — Préparer le dossier sur le NAS

Sur le NAS, crée un dossier dédié, par exemple :

```
/volume1/docker/accounting-assistant/
```

Copies-y (via SFTP, `scp`, ou l'interface de fichiers de l'UGOS) ces 2 fichiers depuis le repo :

- [docker-compose.prod.yml](docker-compose.prod.yml)
- [Caddyfile](Caddyfile)

Tu n'as **pas besoin** de copier le code source ni le Dockerfile : l'image est déjà construite et publiée sur GHCR.

---

## Étape 2 — Créer le fichier `.env` de production

Toujours dans ce même dossier sur le NAS, crée un fichier `.env` (ne jamais le committer dans git). Base-toi sur [.env.example](.env.example) mais complète-le, car le `.env` local actuel n'a que les clés IA/Falco — il manque plusieurs variables nécessaires en prod :

```dotenv
# Admin (créé au premier démarrage)
ADMIN_USERNAME=admin
ADMIN_PASSWORD=<mot_de_passe_fort>
ADMIN_EMAIL=vercauteren.vinc@gmail.com

# Base de données H2
DB_PASSWORD=<mot_de_passe_fort>
H2_CONSOLE_ENABLED=false

# Cookies de session — TRUE obligatoire en prod (HTTPS via Caddy)
SESSION_COOKIE_SECURE=true

# IA extraction (reprendre les valeurs du .env actuel)
ANTHROPIC_API_KEY=...
ANTHROPIC_MODEL=claude-sonnet-4-5-20250929

# Falco (Peppol) — laisser désactivé si non utilisé
FALCO_ENABLED=false
FALCO_API_KEY=...
FALCO_APP_SECRET=...
FALCO_BASE_URL=https://api.sandbox.falco-app.be/v1

# CORS — domaine réel de prod, pas localhost
CORS_ALLOWED_ORIGINS=https://tdw1302.duckdns.org:8999

# Reverse-proxy Caddy / DuckDNS
DUCKDNS_TOKEN=<ton_token_duckdns>
```

Points d'attention :
- `ADMIN_PASSWORD` doit respecter la politique de mot de passe (8+ car., maj., min., chiffre, spécial) — c'est celui du compte admin créé au démarrage.
- `SESSION_COOKIE_SECURE=true` est indispensable ici puisque l'accès se fait en HTTPS derrière Caddy (contrairement à `.env.example` qui met `false` pour du dev local en HTTP).
- `CORS_ALLOWED_ORIGINS` doit correspondre exactement à l'URL publique utilisée, sinon le frontend ne pourra pas appeler l'API.
- `DUCKDNS_TOKEN` : récupérable sur [duckdns.org](https://www.duckdns.org) une fois connecté avec le compte qui gère le domaine `tdw1302.duckdns.org`.

---

## Étape 3 — Ouvrir le port sur la box/routeur

Le seul port exposé publiquement est **8999** (HTTPS géré par Caddy). Sur ta box/routeur, configure une redirection de port :

```
Port externe 8999 → IP du NAS : port 8999 (TCP)
```

Vérifie aussi que le NAS a une IP locale fixe (réservation DHCP) pour que la redirection reste valide après un redémarrage.

---

## Étape 4A — Lancer la stack via SSH (recommandé)

1. Active SSH sur le NAS si ce n'est pas déjà fait (Panneau de configuration → Terminal & SNMP, ou équivalent UGOS).
2. Connecte-toi :
   ```bash
   ssh <user>@<ip_du_nas>
   cd /volume1/docker/accounting-assistant
   ```
3. Lance la stack :
   ```bash
   docker compose -f docker-compose.prod.yml up -d
   ```
4. Vérifie que tout tourne :
   ```bash
   docker compose -f docker-compose.prod.yml ps
   docker compose -f docker-compose.prod.yml logs -f
   ```
   Le conteneur `app` doit passer `healthy` (healthcheck sur `/`), et les logs Caddy doivent montrer l'obtention du certificat TLS via DuckDNS sans erreur.

## Étape 4B — Lancer la stack via l'interface Docker/Container Manager de l'UGOS

1. Ouvre l'app **Docker** (ou **Container Manager**) dans UGOS.
2. Section **Projets** (ou équivalent) → **Créer** → pointe vers le dossier `/volume1/docker/accounting-assistant` contenant `docker-compose.prod.yml`.
3. L'interface doit détecter le fichier `.env` du même dossier automatiquement (sinon, certaines versions demandent de coller les variables manuellement dans l'UI).
4. Lance le projet, puis vérifie dans l'onglet **Journaux/Logs** que les deux conteneurs (`app` et `caddy`) démarrent sans erreur et que `app` passe en état sain (healthy).

---

## Étape 5 — Vérification finale

1. Ouvre `https://tdw1302.duckdns.org:8999` depuis un navigateur externe (pas depuis le réseau local, pour valider la redirection de port).
2. Connecte-toi avec `ADMIN_USERNAME` / `ADMIN_PASSWORD` définis dans le `.env`.
3. **Change immédiatement le mot de passe admin** depuis l'app (menu profil → changer le mot de passe) — le mot de passe du `.env` ne sert qu'à l'initialisation.
4. Teste un cas d'usage simple (créer une facture test) pour valider que l'upload de fichier et la base H2 fonctionnent bien sur les volumes montés.

---

## Reprise après une longue pause

C'est la stack qui tourne déjà sur le NAS (`docker-compose.prod.yml` + `.env` + `Caddyfile` sont déjà en place dans `/volume1/docker/accounting-assistant`). Voici le fil à suivre quand tu reviens dessus après plusieurs mois, avant de toucher à quoi que ce soit.

### 1. Vérifier que tout tourne encore

```bash
ssh <user>@<ip_du_nas>
cd /volume1/docker/accounting-assistant
docker compose -f docker-compose.prod.yml ps
```

- Les 2 conteneurs (`app`, `caddy`) doivent être `Up` / `healthy`. Grâce à `restart: unless-stopped`, ils redémarrent automatiquement après un reboot du NAS ou une mise à jour de l'OS UGOS — normalement rien à faire même après des mois d'inactivité.
- Si un conteneur est arrêté ou en boucle de redémarrage :
  ```bash
  docker compose -f docker-compose.prod.yml logs --tail=100 app
  docker compose -f docker-compose.prod.yml logs --tail=100 caddy
  ```

### 2. Choses qui peuvent avoir « expiré » entretemps

| Élément | Risque après une longue pause | Action |
|---|---|---|
| Certificat TLS | Aucun — Caddy le renouvelle automatiquement tout seul (DNS challenge DuckDNS) | Rien à faire |
| `DUCKDNS_TOKEN` | Ne change quasi jamais, le domaine DuckDNS n'expire pas tant que le token est valide | À revérifier seulement si le site n'est plus joignable en HTTPS |
| Token GHCR (si le package est privé et qu'un `docker login` a été fait avec un PAT à expiration définie) | Un PAT classique peut avoir une date d'expiration → le prochain `docker pull`/`up` échouera avec une erreur d'auth | Regénérer un PAT (`read:packages`) et refaire `docker login ghcr.io -u TDW1302 --password-stdin` |
| `ANTHROPIC_API_KEY` / `FALCO_API_KEY` | Clés côté fournisseur externe, peuvent être révoquées/tourner sans que tu le saches | Si l'extraction IA ou Peppol échoue silencieusement (comportement voulu par design — best effort), vérifier les logs `app` et régénérer la clé si besoin |
| Image `:latest` | Peut être très en retard par rapport à `main` si aucun déploiement n'a eu lieu depuis longtemps | Voir mise à jour ci-dessous |

### 3. Mettre à jour vers la dernière version

À chaque push sur `main`, GitHub Actions republie automatiquement `ghcr.io/tdw1302/accounting-assistant:latest`. `docker compose up -d` seul ne re-télécharge pas l'image (le tag `latest` ne change pas de nom) — il faut un `pull` explicite :

```bash
cd /volume1/docker/accounting-assistant
docker compose -f docker-compose.prod.yml pull app
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```

Le conteneur `app` redémarre avec la nouvelle image ; les volumes `accounting-data` et `accounting-uploads` (donc les factures et la base) ne sont pas touchés. Pense à faire une sauvegarde (section suivante) avant une mise à jour si beaucoup de temps a passé, au cas où une migration de schéma (`ddl-auto=update`) se serait accumulée depuis la dernière fois.

### 4. Nettoyage optionnel

Après plusieurs mises à jour espacées, de vieilles images/couches Docker s'accumulent sur le NAS :

```bash
docker image prune -f
```

---

## Sauvegardes

Les données persistantes vivent dans 2 volumes Docker nommés :
- `accounting-data` → fichier H2 (`/data/db`)
- `accounting-uploads` → fichiers PDF des factures (`/data/uploads`)

Pour sauvegarder, par exemple :

```bash
docker run --rm -v accounting-assistant_accounting-data:/data -v /volume1/backups:/backup alpine \
  tar czf /backup/accounting-data-$(date +%F).tar.gz -C /data .
docker run --rm -v accounting-assistant_accounting-uploads:/data -v /volume1/backups:/backup alpine \
  tar czf /backup/accounting-uploads-$(date +%F).tar.gz -C /data .
```

*(Le préfixe `accounting-assistant_` dépend du nom du dossier/projet Compose — vérifier avec `docker volume ls`.)*

À planifier idéalement via une tâche planifiée UGOS (cron) plutôt qu'à la main.
