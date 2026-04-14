# EventOps — Compliance-Grade Event Operations and Accounting Platform

## Overview

EventOps is a full-stack offline facility-network platform for managing internal trainings, certification sessions, and community events. It runs entirely within a local network with **zero dependency on external services** — no SaaS providers, no email/SMS, no maps, no third-party identity providers.

## Technology Stack

| Layer            | Technology                 | Version |
| ---------------- | -------------------------- | ------- |
| Frontend         | Vue.js 3 (Composition API) | 3.5.x   |
| State Management | Pinia                      | 2.3.x   |
| Routing          | Vue Router                 | 4.5.x   |
| Build Tool       | Vite                       | 6.x     |
| HTTP Client      | axios                      | 1.7.x   |
| Offline Storage  | localforage                | 1.10.x  |
| Backend          | Spring Boot                | 3.2.x   |
| Language         | Java 17 LTS                | 17      |
| Security         | Spring Security            | 6.x     |
| Database         | MySQL                      | 8.x     |
| Migrations       | Flyway                     | 10.x    |
| Containers       | Docker Compose             | latest  |

## Repository Structure

```
repo/
├── README.md                   # This file
├── .env.example                # All environment variables with documentation
├── .gitignore                  # Excludes keystores, .env, build artifacts
├── docker-compose.yml          # 3 services: frontend, backend, mysql
├── run_tests.sh                # Test orchestrator (all suites)
├── frontend/
│   ├── Dockerfile              # Multi-stage: Node 20 build + nginx serve
│   ├── nginx.conf              # SPA routing + HTTPS proxy to backend:8443
│   ├── package.json            # Vue 3, Pinia, axios, localforage
│   ├── vite.config.js          # Build config with API proxy and test setup
│   ├── .env.development        # Dev env: VITE_API_BASE_URL=https://localhost:8443/api
│   ├── .env.production         # Prod env: VITE_API_BASE_URL=/api (relative)
│   ├── src/
│   │   ├── main.js             # App bootstrap
│   │   ├── App.vue             # Root component
│   │   ├── router/             # Vue Router with role-based meta guards
│   │   ├── stores/             # Pinia stores (auth, events, registration, etc.)
│   │   ├── api/                # axios-based API service layer
│   │   ├── views/              # Feature-organized view components (19 views)
│   │   ├── components/         # Reusable components (common, layout, offline)
│   │   └── utils/              # Offline cache, signatures, constants, formatters
│   └── unit_tests/             # Frontend unit tests (Vitest)
└── backend/
    ├── Dockerfile              # Multi-stage: Maven build + JRE 17 runtime
    ├── pom.xml                 # Spring Boot 3.2, Java 17, MySQL, Flyway
    ├── database/
    │   └── migrations/         # Flyway versioned SQL migrations (V001, V002)
    ├── src/main/java/com/eventops/
    │   ├── EventOpsApplication.java
    │   ├── config/             # Web (CORS), async, constants configuration
    │   ├── common/             # DTOs, exception handling, validation, utilities
    │   ├── security/           # Auth, RBAC, encryption, signatures, rate limiting
    │   ├── domain/             # JPA entities organized by bounded context
    │   ├── repository/         # Spring Data JPA repositories
    │   ├── service/            # Business logic services
    │   ├── controller/         # REST API controllers
    │   ├── scheduler/          # Scheduled tasks (passcode, retry, backup, etc.)
    │   ├── integration/        # Adapters (shared-folder, notification, export)
    │   ├── finance/            # Allocation engine, revenue recognition, posting
    │   ├── audit/              # Immutable logging, diffs, CSV export
    │   └── backup/             # Backup execution and retention
    ├── src/main/resources/
    │   ├── application.yml     # Main configuration
    │   ├── application-dev.yml # Development profile overrides
    │   └── keystore/           # TLS keystore directory (see keystore/README.md)
    ├── unit_tests/             # Backend unit tests (JUnit 5)
    └── api_tests/              # Backend API/integration tests
```

## Services and Ports

| Service               | Container Name    | Internal Port | Default External Port |
| --------------------- | ----------------- | ------------- | --------------------- |
| Frontend (nginx)      | eventops-frontend | 80            | 443 (`FRONTEND_PORT`) |
| Backend (Spring Boot) | eventops-backend  | 8443          | 8443 (`BACKEND_PORT`) |
| MySQL                 | eventops-mysql    | 3306          | 3306 (`MYSQL_PORT`)   |

The nginx frontend proxies all `/api/*` requests to the backend over HTTPS
(`https://backend:8443/api/`) in the default secure deployment.

## Quick Start

### Prerequisites

- Docker and Docker Compose (for containerized startup)
- OR: Node.js 20+, Java 17+, Maven 3.9+, MySQL 8.x (for local development)

### Containerized Startup (recommended)

```bash
# 1. Copy and edit environment variables
cp .env.example .env
# Open .env and set ENCRYPTION_SECRET_KEY to a strong random value.
# The secure default path requires a real ENCRYPTION_SECRET_KEY.

# 2. Build and start the secure default stack
docker compose up --build

# Optional: local relaxed dev stack (dev profile, signature verification off)
# docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build

# 3. Access the application
#    Frontend:   http://localhost:443
#    Backend API: https://localhost:8443/api
#    MySQL:       localhost:3306  (eventops / eventops_dev)
```

To stop and remove containers:

```bash
docker compose down
# To also remove named volumes (loses database data):
docker compose down -v
```

### Local Development (without Docker)

```bash
# Backend
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend (separate terminal)
cd frontend
npm install
npm run dev   # Vite dev server at http://localhost:5173
```

## HTTPS / TLS Configuration

The default `docker-compose.yml` is the secure acceptance path: it does not enable the `dev` Spring profile, it requires a real `ENCRYPTION_SECRET_KEY`, and it enables backend TLS by default (`SSL_ENABLED=true`). Use `docker-compose.dev.yml` only for local relaxed development.

### Enabling Local HTTPS

If you need to rotate certs or re-provision local TLS assets:

1. Generate a PKCS12 keystore (see
   `backend/src/main/resources/keystore/README.md` for the exact `keytool`
   command and env-var reference).
2. Set `SSL_ENABLED=true` in `.env`.
3. Set `SSL_KEYSTORE` and `SSL_KEYSTORE_PASSWORD` in `.env`.
4. Update `frontend/nginx.conf` to proxy via `https://backend:8443/api/`
   (instructions are inline in the file).

### Request Signature Verification

HMAC-SHA256 request signature verification is **enabled by default** outside the explicit dev override and remains disabled in the `dev`
Spring profile (`SIGNATURE_ENABLED=false` in `application-dev.yml`). Enable it
in the secure default path. Use `docker compose -f docker-compose.yml -f docker-compose.dev.yml up --build` or set `SPRING_PROFILES_ACTIVE=dev` only for local relaxed development, and
use server-issued per-session signature tokens for browser clients.
The backend `SIGNATURE_SECRET_KEY` remains an optional fallback for
non-session clients.

> **Note**: Docker and tests have not been executed yet. The repository is
> statically complete and ready for execution after provisioning the
> `ENCRYPTION_SECRET_KEY` credential.

## Configuration Reference

All configuration is externalized as environment variables. Copy `.env.example`
to `.env` for a full documented reference. Key variables:

| Variable                 | Default                               | Required    | Description                                                       |
| ------------------------ | ------------------------------------- | ----------- | ----------------------------------------------------------------- |
| `ENCRYPTION_SECRET_KEY`  | none                                  | **Yes**     | AES-256 key for field encryption; startup fails when unset        |
| `DB_PASSWORD`            | `eventops_dev`                        | Production  | MySQL application user password                                   |
| `MYSQL_ROOT_PASSWORD`    | `eventops_root`                       | Production  | MySQL root password                                               |
| `SSL_ENABLED`            | `true`                                | No          | Enable backend HTTPS                                              |
| `SSL_KEYSTORE`           | `classpath:keystore/dev-keystore.p12` | If SSL=true | Keystore path                                                     |
| `SSL_KEYSTORE_PASSWORD`  | `changeit`                            | If SSL=true | Keystore password                                                 |
| `SPRING_PROFILES_ACTIVE` | empty                                 | No          | Empty for secure defaults; `dev` only for explicit local override |
| `CORS_ALLOWED_ORIGINS`   | `http://localhost:443,...`            | No          | Allowed CORS origins                                              |
| `BACKUP_PATH`            | `/var/eventops/backups`               | No          | Backup storage directory                                          |
| `SHARED_FOLDER_PATH`     | `/var/eventops/shared`                | No          | Import source directory                                           |
| `EXPORT_PATH`            | `/var/eventops/exports`               | No          | Export output directory                                           |
| `ATTACHMENT_PATH`        | `/var/eventops/attachments`           | No          | Attachment storage directory                                      |
| `BACKEND_PORT`           | `8443`                                | No          | Host-side backend port                                            |
| `FRONTEND_PORT`          | `443`                                 | No          | Host-side frontend port                                           |
| `MYSQL_PORT`             | `3306`                                | No          | Host-side MySQL port                                              |

## Running Tests

```bash
./run_tests.sh                  # All test suites
./run_tests.sh frontend         # Frontend unit tests only
./run_tests.sh backend          # Backend unit + API tests
./run_tests.sh backend-unit     # Backend unit tests only
./run_tests.sh backend-api      # Backend API/integration tests only
./run_tests.sh --coverage       # All suites with coverage reports
```

**Prerequisites**: Node.js 20+, Java 17+, Maven 3.9+, MySQL 8.x (for API tests)

### Test Suite Summary

| Suite         | Location               | Runner                   | Tests                    |
| ------------- | ---------------------- | ------------------------ | ------------------------ |
| Backend unit  | `backend/unit_tests/`  | JUnit 5 / Maven Surefire | ~195 methods in 34 files |
| Backend API   | `backend/api_tests/`   | JUnit 5 / Maven Failsafe | ~43 methods in 9 files   |
| Frontend unit | `frontend/unit_tests/` | Vitest                   | ~129 methods in 23 files |

**Coverage tools**: JaCoCo (backend), V8 via Vitest (frontend). Reports
generated with `--coverage` flag.

**Traceability**: See `../docs/test-traceability.md` for the complete
requirement-to-test mapping.

### Key Test Coverage Areas

- Authentication, password hashing, anti-bot lockout
- RBAC for all 4 roles with 7 endpoint group authorization checks
- AES-256 encryption round-trip and masking edge cases
- Registration duplicate blocking, quota enforcement, waitlist promotion
- Check-in window enforcement, passcode validation, device binding conflicts
- Notification retry backoff formula (5 attempts), idempotency deduplication
- Import concurrency cap, circuit breaker threshold, checkpoint resume
- Finance allocation math (proportional/fixed/tiered), revenue recognition, rule versioning
- Audit immutability (INSERT-only verification), field-level diff redaction
- Export watermark policy enforcement, download restrictions
- Backup retention (30-day expiry), cleanup logic
- Frontend offline queue, replay, roster cache, masked display
- Configuration properties defaults (ConfigPropertiesTest)

## Offline-Only Constraint

This platform operates entirely on a local/offline facility network:

- No outbound internet connectivity required
- HTTPS with locally provisioned TLS certificates (enabled by default in the secure stack)
- HMAC-SHA256 request-signature verification on all API calls (production)
- In-app notifications only — no email, SMS, or push
- File imports from local shared-folder paths (`SHARED_FOLDER_PATH`)
- Backups stored on local/network filesystem (`BACKUP_PATH`)

## Related Documentation

- `../docs/design.md` — System architecture and requirement traceability
- `../docs/api-spec.md` — REST API specification
- `../docs/test-traceability.md` — Requirement-to-test mapping
- `../questions.md` — Blocker-level ambiguity log with interpretations
- `backend/src/main/resources/keystore/README.md` — TLS keystore setup
- `.env.example` — All environment variables with documentation
