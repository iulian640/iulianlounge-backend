# IulianLounge — Backend

🌐 [English](README.md) | Español

![CI](https://github.com/iulian640/iulianlounge-backend/actions/workflows/ci.yml/badge.svg?branch=dev)

> Un speakeasy virtual navegable en 3D con un backend serio detrás.

**Estado:** Sprint 1 en curso — esqueleto Spring Boot funcionando contra PostgreSQL dockerizado.

## Arranque

Requisitos: JDK 21+, Docker Desktop.

```bash
# 1. Variable de entorno con la contraseña de la BD (una sola vez)
setx DB_PASSWORD tu_password        # Windows (abrir terminal nueva después)

# 2. Levantar PostgreSQL
docker compose up -d

# 3. Arrancar la app
./mvnw spring-boot:run
```

Verificación: `http://localhost:8080/actuator/health` → `{"status":"UP"}`.

Estructura: `controller` → `service` → `repository` / `domain` (capas clásicas Spring; ver [architecture.md](docs/architecture.md)). Migraciones Flyway en `src/main/resources/db/migration` (desde IUL-17).

- **Frontend:** [iulianlounge-frontend](https://github.com/iulian640/iulianlounge-frontend) (Vue + Three.js)
- **Stack backend:** Java 21 · Spring Boot · Spring Data JPA · Spring Security (JWT) · PostgreSQL (Docker) · Flyway
- **Dominio:** iulianlounge.com

## Documentación

Toda la documentación existe en español (original) e inglés (`*.en.md`).

| Documento | ES | EN |
|---|---|---|
| Concepto de producto | [CONCEPT.md](CONCEPT.md) | [CONCEPT.en.md](CONCEPT.en.md) |
| Arquitectura técnica | [docs/architecture.md](docs/architecture.md) | [docs/architecture.en.md](docs/architecture.en.md) |
| La ficción del club | [docs/ficcion.md](docs/ficcion.md) | [docs/ficcion.en.md](docs/ficcion.en.md) |
| Enunciado del bootcamp | [docs/enunciado.md](docs/enunciado.md) | [docs/enunciado.en.md](docs/enunciado.en.md) |

### ADRs (Architecture Decision Records)

Los ADR 01-03 y 07 viven resumidos en [architecture.md §4](docs/architecture.md) y se expanden al implementarse.

| ADR | ES | EN |
|---|---|---|
| 04 — Ledger append-only + saldo materializado | [ES](docs/adr/ADR-04-ledger-append-only-saldo-materializado.md) | [EN](docs/adr/ADR-04-ledger-append-only-saldo-materializado.en.md) |
| 05 — Barman LLM, function calling en lista blanca | [ES](docs/adr/ADR-05-barman-llm-function-calling-lista-blanca.md) | [EN](docs/adr/ADR-05-barman-llm-function-calling-lista-blanca.en.md) |
| 06 — i18n: claves en backend, textos en frontend | [ES](docs/adr/ADR-06-i18n-claves-backend-textos-frontend.md) | [EN](docs/adr/ADR-06-i18n-claves-backend-textos-frontend.en.md) |
| 08 — JWT stateless con refresh token | [ES](docs/adr/ADR-08-jwt-stateless-con-refresh-token.md) | [EN](docs/adr/ADR-08-jwt-stateless-con-refresh-token.en.md) |
| 09 — Fichas como enteros, prohibido double | [ES](docs/adr/ADR-09-fichas-enteros-long-prohibido-double.md) | [EN](docs/adr/ADR-09-fichas-enteros-long-prohibido-double.en.md) |

## Gestión

Proyecto gestionado en Jira (proyecto **IUL**, 7 sprints, entrega 13-oct-2026).
