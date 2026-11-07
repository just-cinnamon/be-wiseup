# be-wiseup – Backend API (Java 21 · Quarkus 3)

A clean-architecture, cloud-ready backend API built with **Java 21** and **Quarkus 3**.
Port of the original Node.js/Express API (`be-wiseup-one-api`) with improved structure,
type safety, and observability.

---

## Table of Contents

- [Architecture](#architecture)
- [API Endpoints](#api-endpoints)
- [Getting Started](#getting-started)
- [Environment Variables](#environment-variables)
- [Docker](#docker)
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
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/wiseup` | PostgreSQL JDBC URL |
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
