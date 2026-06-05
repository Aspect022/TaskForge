# TaskForge

TaskForge is a distributed job processing platform built with Java 21 and Spring Boot. It is designed to show production-style backend patterns: authentication, durable job submission, job state management, transactional outbox, Kafka-based async processing, Redis-backed token/rate state, and observability.

The full product direction is captured in [TaskForge_PRD.md](TaskForge_PRD.md). The repository is being built incrementally, with the currently implemented backend services listed below.

## Current Status

Implemented:

- `auth-service`: user registration, login, refresh-token rotation, logout, RS256 JWTs, JWKS endpoint, PostgreSQL/Flyway persistence, Redis token state.
- `job-service`: job submission, list/detail/cancel APIs, state machine, PostgreSQL/Flyway persistence, transactional outbox rows for `JOB_SUBMITTED`.

Not implemented yet:

- Outbox publisher to Kafka.
- Worker service.
- API gateway.
- WebSocket service.
- Docker Compose infrastructure.
- Observability stack.

## Architecture

```text
Client
  |
  | REST
  v
API Gateway                 [planned]
  |
  +--> Auth Service         [implemented]
  |
  +--> Job Service          [implemented]
          |
          +--> PostgreSQL
          +--> Outbox table
                  |
                  v
               Kafka        [planned publisher]
                  |
                  v
            Worker Service  [planned]
```

## Repository Layout

```text
TaskForge/
  Backend/
    auth-service/
    job-service/
  TaskForge_PRD.md
  README.md
```

## Technology Stack

- Java 21
- Spring Boot 4.0.6
- Spring Web MVC
- Spring Security
- Spring Data JPA
- PostgreSQL
- Redis
- Flyway
- Apache Kafka
- JUnit 6 / Mockito / H2 for tests

## Running Tests

The generated Maven wrapper may not work on every Windows shell. If it fails, use an installed Maven binary or a cached Maven distribution.

From `Backend/auth-service`:

```powershell
mvn test
```

From `Backend/job-service`:

```powershell
mvn test
```

Current verified results:

- `auth-service`: 18 tests passing.
- `job-service`: 13 tests passing.

## Service Ports

| Service | Port | Status |
|---|---:|---|
| Auth Service | 8081 | Implemented |
| Job Service | 8082 | Implemented |
| Worker Service | 8083 | Planned |
| WebSocket Service | 8084 | Planned |
| API Gateway | 8080 | Planned |

## Development Notes

- Services are currently independent Spring Boot projects under `Backend/`.
- `job-service` trusts `X-User-Id` as a gateway-provided user context header until the API gateway is implemented.
- JSON job payloads are accepted and returned as JSON, but persisted as serialized JSON text in the first pass.
- Kafka dependencies exist in `job-service`, but publishing is intentionally deferred until the async processing phase.

## Next Milestone

The next PRD-aligned milestone is the async processing foundation:

1. Add local Docker Compose for PostgreSQL, Redis, and Kafka.
2. Implement an outbox publisher in `job-service`.
3. Generate and implement `worker-service`.
4. Process `DATA_EXPORT` and `EMAIL_DISPATCH` jobs end to end.
