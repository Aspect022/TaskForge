# TaskForge Job Service

Spring Boot 4 job management service for TaskForge.

## Responsibilities

- Submit jobs for authenticated users.
- List jobs with filters.
- Retrieve full job detail.
- Cancel jobs when the state machine allows it.
- Persist job state and result placeholders.
- Write transactional outbox events for future Kafka publishing.

## Endpoints

All endpoints expect a gateway-provided `X-User-Id` header.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/jobs` | Submit a job |
| `GET` | `/api/v1/jobs` | List jobs for the current user |
| `GET` | `/api/v1/jobs/{jobId}` | Get job detail |
| `DELETE` | `/api/v1/jobs/{jobId}` | Cancel a job |

## Job Submission Example

```json
{
  "jobType": "DATA_EXPORT",
  "priority": "HIGH",
  "payload": {
    "rows": 10000,
    "format": "csv"
  }
}
```

Supported job types:

- `DATA_EXPORT`
- `REPORT_GENERATION`
- `EMAIL_DISPATCH`
- `IMAGE_PROCESSING`
- `WEBHOOK_CALL`
- `CUSTOM_SCRIPT`

Supported priorities:

- `LOW`
- `NORMAL`
- `HIGH`

## State Machine

Valid transitions:

- `PENDING -> QUEUED`
- `PENDING -> CANCELLED`
- `QUEUED -> PROCESSING`
- `QUEUED -> CANCELLED`
- `PROCESSING -> COMPLETED`
- `PROCESSING -> FAILED`
- `PROCESSING -> CANCELLED`
- `FAILED -> QUEUED`

Terminal states:

- `COMPLETED`
- `CANCELLED`

## Configuration

The service defaults to port `8082`.

Important environment-backed properties:

| Property | Default |
|---|---|
| `SERVER_PORT` | `8082` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/taskforge` |
| `SPRING_DATASOURCE_USERNAME` | `taskforge` |
| `SPRING_DATASOURCE_PASSWORD` | `taskforge` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `JOB_PAYLOAD_MAX_BYTES` | `1048576` |

## Database

Flyway creates:

- `jobs`
- `job_results`
- `outbox`

Job submission currently writes a `JOB_SUBMITTED` outbox row in the same transaction as the job update.

## Tests

```powershell
mvn test
```

Verified result: 13 passing tests.

## Current Limitations

- Outbox rows are not published to Kafka yet.
- Worker processing is not implemented yet.
- The service trusts `X-User-Id` until the API Gateway is implemented.
- JSON payloads are accepted and returned as JSON, but persisted as serialized JSON text in this first pass.
