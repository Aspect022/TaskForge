# TaskForge Auth Service

Spring Boot 4 authentication service for TaskForge.

## Responsibilities

- Register users with BCrypt-hashed passwords.
- Authenticate users and issue RS256 JWT access tokens.
- Issue opaque refresh tokens and rotate them on refresh.
- Revoke refresh tokens on logout.
- Blacklist access-token `jti` values in Redis on logout.
- Expose a JWKS endpoint for downstream JWT verification.

## Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/auth/register` | Create a user |
| `POST` | `/api/v1/auth/login` | Authenticate and return token pair |
| `POST` | `/api/v1/auth/refresh` | Rotate refresh token and return new token pair |
| `POST` | `/api/v1/auth/logout` | Revoke refresh token and blacklist access token |
| `GET` | `/.well-known/jwks.json` | Return RSA public key as JWKS |

## Configuration

The service defaults to port `8081`.

Important environment-backed properties:

| Property | Default |
|---|---|
| `SERVER_PORT` | `8081` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/taskforge` |
| `SPRING_DATASOURCE_USERNAME` | `taskforge` |
| `SPRING_DATASOURCE_PASSWORD` | `taskforge` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `JWT_PRIVATE_KEY_PATH` | empty, dev key generated |
| `JWT_PUBLIC_KEY_PATH` | empty, dev key generated |

## Token Model

- Access token TTL: 15 minutes.
- Refresh token TTL: 7 days.
- Access token claims: `sub`, `email`, `role`, `jti`, `iat`, `exp`.
- Redis refresh key: `refresh:{token}`.
- Redis blacklist key: `blacklist:{jti}`.

If RSA key paths are not configured, the service generates an in-memory development key pair at startup. This is useful locally but not suitable for production.

## Database

Flyway creates:

- `users`
- `refresh_tokens`

## Tests

```powershell
mvn test
```

Verified result: 18 passing tests.

## Current Limitations

- OpenAPI UI is deferred until Spring Boot 4 compatibility in the Springdoc ecosystem is confirmed.
- Docker Compose infrastructure is not committed yet.
