# TaskForge Job Service

Spring Boot 4 job management service for TaskForge.

## Implemented Endpoints

- `POST /api/v1/jobs`
- `GET /api/v1/jobs`
- `GET /api/v1/jobs/{jobId}`
- `DELETE /api/v1/jobs/{jobId}`

## Notes

- The service currently trusts the gateway-provided `X-User-Id` header for tenant scoping.
- Job submission persists a `QUEUED` job and writes a `JOB_SUBMITTED` row into the transactional outbox table.
- Kafka publishing from the outbox is intentionally deferred to the async-processing phase.
- JSON payloads are accepted and returned as JSON objects, while persisted as serialized JSON text in this first pass.
