# TaskForge Backend

This folder contains the backend services for TaskForge.

## Services

| Service | Description | Port | Status |
|---|---|---:|---|
| `auth-service` | Registration, login, JWTs, refresh rotation, logout | 8081 | Implemented |
| `job-service` | Job CRUD, state machine, transactional outbox | 8082 | Implemented |
| `worker-service` | Kafka consumer and job executors | 8083 | Planned |
| `api-gateway` | JWT validation, rate limiting, routing | 8080 | Planned |
| `websocket-service` | STOMP job status updates | 8084 | Planned |

## Implemented Service Test Commands

```powershell
cd .\auth-service
mvn test
```

```powershell
cd .\job-service
mvn test
```

## Current Integration Shape

- `auth-service` owns users, refresh tokens, JWT issuing, and JWKS.
- `job-service` currently trusts gateway-style `X-User-Id` headers for tenant scoping.
- `job-service` writes outbox events but does not publish them yet.
- Infrastructure is not committed yet; local Docker Compose is the next backend foundation step.
