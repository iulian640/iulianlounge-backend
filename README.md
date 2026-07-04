# IulianLounge — Backend

![CI](https://github.com/iulian640/iulianlounge-backend/actions/workflows/ci.yml/badge.svg?branch=dev)

> Un speakeasy virtual navegable en 3D con un backend serio detrás.
> A walkable 3D virtual speakeasy with a serious backend behind it.

**Estado / Status:** Sprint 1 en curso — esqueleto Spring Boot funcionando contra PostgreSQL dockerizado. / Sprint 1 in progress — Spring Boot skeleton running against dockerized PostgreSQL.

## Arranque / Getting started

Requisitos / Requirements: JDK 21+, Docker Desktop.

```bash
# 1. Variable de entorno con la contraseña de la BD (una sola vez) /
#    DB password env var (once)
setx DB_PASSWORD tu_password        # Windows (abrir terminal nueva después / reopen terminal)

# 2. Levantar PostgreSQL / Start PostgreSQL
docker compose up -d

# 3. Arrancar la app / Run the app
./mvnw spring-boot:run
```

Verificación / Check: `http://localhost:8080/actuator/health` → `{"status":"UP"}`.

Estructura / Layout: `controller` → `service` → `repository` / `domain` (capas clásicas Spring; ver [architecture.md](docs/architecture.md)). Migraciones Flyway en `src/main/resources/db/migration` (desde IUL-17).

- **Frontend:** [iulianlounge-frontend](https://github.com/iulian640/iulianlounge-frontend) (Vue + Three.js)
- **Stack backend:** Java 21 · Spring Boot · Spring Data JPA · Spring Security (JWT) · PostgreSQL (Docker) · Flyway
- **Dominio / Domain:** iulianlounge.com

## Documentación / Documentation

Toda la documentación existe en español (original) e inglés (`*.en.md`).
All documentation exists in Spanish (original) and English (`*.en.md`).

| Documento | ES | EN |
|---|---|---|
| Concepto de producto / Product concept | [CONCEPT.md](CONCEPT.md) | [CONCEPT.en.md](CONCEPT.en.md) |
| Arquitectura técnica / Technical architecture | [docs/architecture.md](docs/architecture.md) | [docs/architecture.en.md](docs/architecture.en.md) |
| La ficción del club / The club's fiction | [docs/ficcion.md](docs/ficcion.md) | [docs/ficcion.en.md](docs/ficcion.en.md) |
| Enunciado del bootcamp / Bootcamp brief | [docs/enunciado.md](docs/enunciado.md) | [docs/enunciado.en.md](docs/enunciado.en.md) |

### ADRs (Architecture Decision Records)

Los ADR 01-03 y 07 viven resumidos en [architecture.md §4](docs/architecture.md) y se expanden al implementarse. / ADRs 01-03 and 07 live summarized in [architecture.md §4](docs/architecture.en.md) and get expanded as they are implemented.

| ADR | ES | EN |
|---|---|---|
| 04 — Ledger append-only + saldo materializado | [ES](docs/adr/ADR-04-ledger-append-only-saldo-materializado.md) | [EN](docs/adr/ADR-04-ledger-append-only-saldo-materializado.en.md) |
| 05 — Barman LLM, function calling en lista blanca | [ES](docs/adr/ADR-05-barman-llm-function-calling-lista-blanca.md) | [EN](docs/adr/ADR-05-barman-llm-function-calling-lista-blanca.en.md) |
| 06 — i18n: claves en backend, textos en frontend | [ES](docs/adr/ADR-06-i18n-claves-backend-textos-frontend.md) | [EN](docs/adr/ADR-06-i18n-claves-backend-textos-frontend.en.md) |
| 08 — JWT stateless con refresh token | [ES](docs/adr/ADR-08-jwt-stateless-con-refresh-token.md) | [EN](docs/adr/ADR-08-jwt-stateless-con-refresh-token.en.md) |
| 09 — Fichas como enteros, prohibido double | [ES](docs/adr/ADR-09-fichas-enteros-long-prohibido-double.md) | [EN](docs/adr/ADR-09-fichas-enteros-long-prohibido-double.en.md) |

## Gestión / Project management

Proyecto gestionado en Jira (proyecto **IUL**, 7 sprints, entrega 13-oct-2026). / Managed in Jira (project **IUL**, 7 sprints, delivery 13 Oct 2026).

## Instalación y uso / Setup & usage

*Pendiente: se documenta con el esqueleto Spring Boot (IUL-14).*
*Pending: documented alongside the Spring Boot skeleton (IUL-14).*
