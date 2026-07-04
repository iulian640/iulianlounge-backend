# IulianLounge — Backend

🌐 English | [Español](README.es.md)

![CI](https://github.com/iulian640/iulianlounge-backend/actions/workflows/ci.yml/badge.svg?branch=dev)

> A walkable 3D virtual speakeasy with a serious backend behind it.

**Status:** Sprint 1 in progress — Spring Boot skeleton running against dockerized PostgreSQL.

## Getting started

Requirements: JDK 21+, Docker Desktop.

```bash
# 1. DB password env var (once)
setx DB_PASSWORD your_password      # Windows (reopen terminal afterwards)

# 2. Start PostgreSQL
docker compose up -d

# 3. Run the app
./mvnw spring-boot:run
```

Check: `http://localhost:8080/actuator/health` → `{"status":"UP"}`.

Layout: `controller` → `service` → `repository` / `domain` (classic Spring layers; see [architecture.en.md](docs/architecture.en.md)). Flyway migrations in `src/main/resources/db/migration` (since IUL-17).

- **Frontend:** [iulianlounge-frontend](https://github.com/iulian640/iulianlounge-frontend) (Vue + Three.js)
- **Backend stack:** Java 21 · Spring Boot · Spring Data JPA · Spring Security (JWT) · PostgreSQL (Docker) · Flyway
- **Domain:** iulianlounge.com

## Documentation

All documentation exists in Spanish (original) and English (`*.en.md`).

| Document | EN | ES |
|---|---|---|
| Product concept | [CONCEPT.en.md](CONCEPT.en.md) | [CONCEPT.md](CONCEPT.md) |
| Technical architecture | [docs/architecture.en.md](docs/architecture.en.md) | [docs/architecture.md](docs/architecture.md) |
| The club's fiction | [docs/ficcion.en.md](docs/ficcion.en.md) | [docs/ficcion.md](docs/ficcion.md) |
| Bootcamp brief | [docs/enunciado.en.md](docs/enunciado.en.md) | [docs/enunciado.md](docs/enunciado.md) |

### ADRs (Architecture Decision Records)

ADRs 01-03 and 07 live summarized in [architecture.en.md §4](docs/architecture.en.md) and get expanded as they are implemented.

| ADR | EN | ES |
|---|---|---|
| 04 — Append-only ledger + materialized balance | [EN](docs/adr/ADR-04-ledger-append-only-saldo-materializado.en.md) | [ES](docs/adr/ADR-04-ledger-append-only-saldo-materializado.md) |
| 05 — LLM bartender, allowlisted function calling | [EN](docs/adr/ADR-05-barman-llm-function-calling-lista-blanca.en.md) | [ES](docs/adr/ADR-05-barman-llm-function-calling-lista-blanca.md) |
| 06 — i18n: keys in backend, texts in frontend | [EN](docs/adr/ADR-06-i18n-claves-backend-textos-frontend.en.md) | [ES](docs/adr/ADR-06-i18n-claves-backend-textos-frontend.md) |
| 08 — Stateless JWT with refresh token | [EN](docs/adr/ADR-08-jwt-stateless-con-refresh-token.en.md) | [ES](docs/adr/ADR-08-jwt-stateless-con-refresh-token.md) |
| 09 — Chips as integers, double forbidden | [EN](docs/adr/ADR-09-fichas-enteros-long-prohibido-double.en.md) | [ES](docs/adr/ADR-09-fichas-enteros-long-prohibido-double.md) |

## Project management

Managed in Jira (project **IUL**, 7 sprints, delivery 13 Oct 2026).
