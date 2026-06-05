# TaskForge Auth Service

Spring Boot 4 authentication service for TaskForge.

## Implemented Endpoints

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /.well-known/jwks.json`

## Notes

- Access tokens are RS256 JWTs with `sub`, `email`, `role`, `jti`, `iat`, and `exp` claims.
- Refresh tokens are opaque values persisted in PostgreSQL and cached in Redis under `refresh:{token}`.
- Logout revokes the refresh token and blacklists the access-token `jti` in Redis under `blacklist:{jti}`.
- OpenAPI UI is deferred for now because Spring Boot 4 support in the Springdoc ecosystem should be confirmed before adding it.
