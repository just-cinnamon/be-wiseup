# be-wiseup – Backend API (Java 21 · Quarkus 3)

A clean-architecture, cloud-ready backend API built with **Java 21** and **Quarkus 3**.
Port of the original Node.js/Express API (`be-wiseup-one-api`) with improved structure,
type safety, and observability.

---

## Table of Contents

- [Architecture](#architecture)
- [API Endpoints](#api-endpoints)
- [API Collection & Testing](#api-collection--testing)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [Docker](#docker)
- [Deploy (Railway)](#deploy-railway)
- [Database](#database)
- [Security](#security)
- [Scheduled Jobs](#scheduled-jobs)

---

## Architecture

```
src/main/java/com/wiseup/api/
├── config/                 # Startup, scheduling, typed config
├── domain/
│   ├── model/              # Pure Java records (no framework annotations)
│   ├── repository/         # Port interfaces (implemented in infrastructure)
│   └── service/            # Business logic (Auth, Videos, Moderation)
├── application/
│   ├── dto/                # Request/response records
│   └── mapper/             # JSON → DTO mapping (VideoMapper)
├── infrastructure/
│   ├── cache/              # In-memory TTL cache (upstream responses)
│   ├── external/           # HTTP clients (Webmasters API, reCAPTCHA)
│   ├── persistence/        # JDBC repository implementations
│   └── security/           # Crypto utilities (PBKDF2, SHA-256, tokens)
└── presentation/
    ├── filter/             # JAX-RS name-binding filters (Auth, Age, Terms, Admin)
    └── resource/           # REST endpoints (Auth, Catalogue, Reports)
```

**Layer rules:**
- `domain` → no framework imports, pure Java
- `application` → no HTTP concerns, no SQL
- `infrastructure` → implements domain ports
- `presentation` → HTTP only, delegates to services

---

## API Endpoints

### Public

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/health` | Health check (SmallRye Health) |
| `POST` | `/api/auth/guest` | **Guest session** → `{ user, token, expiresAt }`. No body. Records the adult self-declaration (date + IP) and accepts the current legal versions in one transaction. Rate-limited per IP (`GUEST_MAX_PER_HOUR`) |
| `POST` | `/api/auth/login` | Login → `{ user, token, expiresAt }` |

### Auth-required (Bearer token)

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/auth/me` | Current session + legal versions |
| `POST` | `/api/auth/accept` | Age self-declaration + term acceptance |
| `POST` | `/api/auth/logout` | Invalidate session |
| `DELETE` | `/api/auth/me` | Delete account (LGPD) |

### Catalogue (auth + ageVerified + termsAccepted)

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/home` | Featured, top-10, and curated rows |
| `GET`  | `/api/videos` | Search/list (`q`, `category`, `ordering`, `period`, `page`) |
| `GET`  | `/api/videos/{id}` | Video detail + embed URL |
| `GET`  | `/api/categories` | Full category list |

### Reports (auth + ageVerified + termsAccepted)

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/reports/reasons` | Valid report reasons |
| `POST` | `/api/reports` | Submit report `{ videoId, reason, note }` |

### Admin-only

| Method | Path | Description |
|--------|------|-------------|
| `GET`  | `/api/reports/open` | Open report queue |
| `GET`  | `/api/reports/blocked` | Blocked videos list |
| `POST` | `/api/reports/{id}/review` | Review decision `{ procede: bool }` |
| `POST` | `/api/reports/block` | Manual block `{ videoId }` |

---

## API Collection & Testing

Two ready-to-use collection formats are included:

1. **Postman / Insomnia / Bruno**:
   - [`wiseup-api.postman_collection.json`](file:///Users/checkmaathy/dev/wiseup/be-wiseup/wiseup-api.postman_collection.json) – Full v2.1 collection covering all 16 endpoints with auto-token extraction on login.
   - [`wiseup-api.postman_environment.json`](file:///Users/checkmaathy/dev/wiseup/be-wiseup/wiseup-api.postman_environment.json) – Pre-configured environment file (`baseUrl`, `authToken`, `adminToken`, etc.).

2. **VS Code & IntelliJ IDEA (REST Client)**:
   - [`requests.http`](file:///Users/checkmaathy/dev/wiseup/be-wiseup/requests.http) – Plaintext HTTP file with request chaining: run requests with one click directly inside your IDE.

---

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+ (or use the included `./mvnw` wrapper)
- PostgreSQL 14+ (or Docker)

### Run locally

```bash
# 1. Copy and configure environment
cp .env.example .env
# Edit .env with your DATABASE_URL etc.

# 2. Start PostgreSQL (optional, via Docker)
docker compose up postgres -d

# 3. Run in dev mode (hot-reload)
./mvnw quarkus:dev

# API available at http://localhost:8080/api
```

### Run tests

```bash
./mvnw test
```

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8080` | HTTP port (injected by the cloud provider, e.g. Railway) |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/wiseup` | PostgreSQL JDBC URL (must be a `jdbc:` URL, not `postgresql://`) |
| `DATABASE_USER` | `postgres` | DB username |
| `DATABASE_PASSWORD` | `postgres` | DB password |
| `CORS_ORIGINS` | `http://localhost:5173` | Allowed CORS origins (comma-separated) |
| `UPSTREAM_BASE_URL` | `https://www.pornhub.com/webmasters` | Webmasters API base URL |
| `UPSTREAM_TIMEOUT_MS` | `8000` | Upstream HTTP timeout in ms |
| `CACHE_TTL_SECONDS` | `600` | In-memory cache TTL |
| `SESSION_TTL_HOURS` | `12` | Session lifetime |
| `LOGIN_MAX_ATTEMPTS` | `5` | Failed attempts before lock |
| `LOGIN_LOCK_MINUTES` | `15` | Lock duration |
| `SEED_USERS` | — | `email:pass,email:pass` pairs created at boot |
| `GUEST_MAX_PER_HOUR` | `30` | Guest sessions allowed per IP per hour; `0` disables the limit |
| `RECAPTCHA_SECRET` | — | Google reCAPTCHA v2 secret; leave empty to disable |
| `LEGAL_VERSION_TERMOS` | `2026-11-05` | Current Terms version |
| `LEGAL_VERSION_PRIVACIDADE` | `2026-11-05` | Current Privacy Policy version |
| `ACCESS_LOG_RETENTION_DAYS` | `180` | Access log retention (Marco Civil) |

---

## Docker

### JVM image (recommended)

```bash
./mvnw package -DskipTests
docker build -t be-wiseup:latest .
docker run -p 8080:8080 --env-file .env be-wiseup:latest
```

### Native image (faster startup, larger build time)

```bash
./mvnw package -Pnative -DskipTests
docker build -f Dockerfile.native -t be-wiseup:native .
```

### Docker Compose (full stack)

```bash
docker compose up --build
```

---

## Deploy (Railway)

A API sobe no Railway a partir do `Dockerfile` (JVM mode). O arquivo
[`railway.json`](railway.json) já fixa builder, health check e política de
restart — o painel não precisa ser configurado à mão para isso.

### 1. Serviços necessários

O projeto Railway precisa de **dois** serviços no mesmo environment:

| Serviço | Origem |
|---------|--------|
| `be-wiseup` | Este repositório (branch `develop`), builder Dockerfile |
| `Postgres` | Template oficial "PostgreSQL" do Railway |

A API fala com o banco pela **rede privada** do projeto (`*.railway.internal`),
então o Postgres não precisa de domínio público.

### 2. Variáveis de ambiente

Copie [`.env.railway.example`](.env.railway.example) para o **Raw Editor** de
*Variables* do serviço `be-wiseup` e ajuste os três itens marcados no arquivo
(`CORS_ORIGINS`, `SEED_USERS` e o nome do serviço Postgres nas referências).

Dois pontos que costumam quebrar o deploy:

- **`PORT`** — o Railway roteia o tráfego para a porta que a aplicação escuta.
  `quarkus.http.port=${PORT:8080}` já lê essa variável; mantenha `PORT=8080`
  definido para o valor ser determinístico.
- **`DATABASE_URL`** — o Railway publica a URL do Postgres no formato
  `postgresql://user:pass@host/db`, que o driver JDBC **não** aceita. Por isso a
  URL é remontada no formato `jdbc:postgresql://host:port/db` a partir de
  `${{Postgres.PGHOST}}`, `${{Postgres.PGPORT}}` e `${{Postgres.PGDATABASE}}`,
  com usuário e senha em variáveis separadas.

### 3. Primeiro deploy

1. `git push origin develop` — o Railway builda automaticamente a cada push.
2. Acompanhe *Deploy Logs*. O build do Quarkus leva alguns minutos (download das
   dependências Maven na primeira vez).
3. O Flyway roda `V1__initial_schema.sql` no boot
   (`quarkus.flyway.migrate-at-start=true`); não há passo manual de migração.
4. Gere o domínio público em *Settings → Networking → Generate Domain*
   (target port `8080`).
5. Valide: `curl https://<dominio>/api/health/live` → `{"status":"UP"}`.

### 4. Pós-deploy

- Promova o usuário seed a admin no banco (não existe endpoint para isso):
  ```sql
  UPDATE users SET is_admin = true WHERE email = '<seu-email>';
  ```
- Aponte o front-end (`be-wiseup-one`) para a API publicada:
  `VITE_API_BASE_URL=https://<dominio>/api`, e inclua a URL do front em
  `CORS_ORIGINS` na API.

---

## Database

Migrations are managed by **Flyway** and run automatically at startup
(`quarkus.flyway.migrate-at-start=true`).

Schema files live in `src/main/resources/db/migration/`.

> **Note:** `is_admin` is set directly in the database (`UPDATE users SET is_admin = true WHERE email = '...'`).
> There is no HTTP endpoint to promote a user to admin — by design.

---

## Security

| Concern | Implementation |
|---------|----------------|
| Password hashing | PBKDF2+HMAC-SHA512 (310k iterations, OWASP 2024) |
| Session tokens | 32-byte random → URL-safe base64; stored as SHA-256 hash |
| Brute-force protection | Account lock after N failures, configurable window |
| reCAPTCHA | Google v2 siteverify; disabled when no secret is set |
| Age gate | Self-declaration with IP + timestamp (atomic with term acceptance) |
| Blocked videos | In-memory set (O(1) lookup per catalogue request) |
| Immediate block | `menor` and `sem_consentimento` reports block before human review |
| CORS | Configurable origins; methods GET/POST/DELETE only |
| Cache headers | `no-store` on auth, `private` on catalogue |

---

## Scheduled Jobs

| Job | Schedule | Purpose |
|-----|----------|---------|
| `purgeAccessLogs` | Daily 03:00 UTC | Delete access logs > 180 days (Marco Civil) |
| `syncOrigin` | Every 6 hours | Block videos deleted from upstream |
