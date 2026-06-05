# TaskForge — Distributed Job Processing Platform
### Product Requirements Document (PRD) · v1.0

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Motivation & Problem Statement](#2-motivation--problem-statement)
3. [Business Requirements](#3-business-requirements)
4. [Functional Requirements](#4-functional-requirements)
5. [Non-Functional Requirements (NFRs)](#5-non-functional-requirements-nfrs)
6. [System Architecture — High-Level Design (HLD)](#6-system-architecture--high-level-design-hld)
7. [Low-Level Design (LLD)](#7-low-level-design-lld)
8. [Data Models & Database Schema](#8-data-models--database-schema)
9. [API Contract](#9-api-contract)
10. [Kafka Event Schemas](#10-kafka-event-schemas)
11. [Security Design](#11-security-design)
12. [Caching Strategy](#12-caching-strategy)
13. [Rate Limiting Design](#13-rate-limiting-design)
14. [WebSocket Real-Time Layer](#14-websocket-real-time-layer)
15. [Resilience & Fault Tolerance](#15-resilience--fault-tolerance)
16. [Observability Stack](#16-observability-stack)
17. [Infrastructure & DevOps](#17-infrastructure--devops)
18. [Implementation Roadmap](#18-implementation-roadmap)
19. [Trade-offs & Design Decisions](#19-trade-offs--design-decisions)
20. [Resume Talking Points](#20-resume-talking-points)

---

## 1. Executive Summary

**TaskForge** is a production-grade, distributed job processing platform built on Java Spring Boot. It enables clients to submit arbitrary background jobs via a REST API, track their execution in real time via WebSockets, and retrieve results once processing is complete — all backed by a horizontally scalable microservices architecture.

The system is designed to reflect real-world backend infrastructure patterns found in companies like Stripe, Uber, and LinkedIn: event-driven processing, resilient async pipelines, layered caching, and comprehensive observability.

| Attribute | Value |
|---|---|
| Architecture Style | Microservices, Event-Driven |
| Primary Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.x, Spring Cloud |
| Message Broker | Apache Kafka |
| Cache | Redis (Lettuce client) |
| Database | PostgreSQL 15 |
| Auth | JWT (RS256) |
| Containerization | Docker + Docker Compose |
| Monitoring | Prometheus + Grafana + ELK Stack |

---

## 2. Motivation & Problem Statement

Modern applications frequently need to offload heavy, time-consuming, or unreliable operations — sending emails, generating reports, processing images, calling third-party APIs, running ML inference — from the request-response cycle into background workers.

Doing this naively (e.g., spawning threads inline or running a cron job on a single server) introduces several failure modes:

- **No durability**: If the server crashes mid-job, the work is lost.
- **No visibility**: Callers have no way to track job progress.
- **No scalability**: A single machine is a bottleneck and a single point of failure.
- **No backpressure**: Uncontrolled job submission can overwhelm the system.

TaskForge solves these problems by providing a platform where:

- Jobs are **persisted immediately** upon submission (durable).
- Jobs are **queued in Kafka** and processed by stateless, scalable workers.
- Clients can **track real-time progress** via WebSocket subscriptions.
- The system enforces **rate limits**, **circuit breaking**, and **dead-letter queuing**.

---

## 3. Business Requirements

### BR-01 · Job Submission
Authenticated clients must be able to submit a job of a declared type with an arbitrary JSON payload. The system must immediately acknowledge submission with a unique job ID.

### BR-02 · Asynchronous Processing
Job execution must be fully decoupled from job submission. The submitter must never block waiting for job completion.

### BR-03 · Real-Time Tracking
Clients must be able to subscribe to live job status updates without polling. Latency from status change to client notification must be under 500 ms under normal load.

### BR-04 · Result Persistence & Retrieval
Job results (output payload, errors, execution metadata) must be persisted and retrievable for at least 7 days after completion.

### BR-05 · Multi-Tenancy
Each user operates within their own namespace. A user may only view and manage their own jobs. Admin users have cross-tenant visibility.

### BR-06 · Fair Resource Usage
No single user should be able to monopolize the processing queue. Per-user rate limiting must cap both submission rate and concurrent in-flight jobs.

### BR-07 · Failure Handling
Jobs that fail must be retried with exponential backoff. Jobs that exhaust retries must land in a Dead-Letter Queue (DLQ) and trigger an alert.

### BR-08 · Observability
The platform must expose metrics (throughput, queue depth, processing latency, error rates) consumable by an operations dashboard.

---

## 4. Functional Requirements

### 4.1 Authentication & Authorization

- `FR-AUTH-01`: Users register with email + password. Passwords hashed with BCrypt (cost factor 12).
- `FR-AUTH-02`: Login returns a short-lived Access Token (JWT, 15 min) and a long-lived Refresh Token (opaque, 7 days, stored in Redis).
- `FR-AUTH-03`: All job endpoints require a valid Bearer token.
- `FR-AUTH-04`: Token refresh invalidates the old refresh token (rotation).
- `FR-AUTH-05`: Logout blacklists the access token (stored in Redis with TTL matching remaining token lifetime).

### 4.2 Job Management

- `FR-JOB-01`: Submit a job with a `jobType` (enum) and a `payload` (JSON object, max 1 MB).
- `FR-JOB-02`: Job types are: `DATA_EXPORT`, `REPORT_GENERATION`, `EMAIL_DISPATCH`, `IMAGE_PROCESSING`, `WEBHOOK_CALL`, `CUSTOM_SCRIPT`.
- `FR-JOB-03`: Each job has a `priority` field: `LOW`, `NORMAL`, `HIGH`. High-priority jobs are placed on a separate Kafka partition.
- `FR-JOB-04`: Each job tracks: `PENDING → QUEUED → PROCESSING → COMPLETED | FAILED | CANCELLED`.
- `FR-JOB-05`: Users can cancel a `PENDING` or `QUEUED` job. Cancellation of an in-progress job emits a cancellation signal; the worker must honour it within one polling cycle.
- `FR-JOB-06`: List jobs with filters (`status`, `jobType`, `priority`, `dateRange`) and cursor-based pagination.
- `FR-JOB-07`: Retrieve full job detail including result payload and error detail.
- `FR-JOB-08`: Jobs that have been `COMPLETED` or `FAILED` for more than 7 days are soft-deleted (archived flag set).

### 4.3 Worker Processing

- `FR-WORK-01`: Worker instances are stateless and horizontally scalable.
- `FR-WORK-02`: Each worker polls a Kafka topic partition and processes one job at a time per thread.
- `FR-WORK-03`: Processing timeout is configurable per job type (default: 5 min). Timed-out jobs are marked `FAILED` and retried.
- `FR-WORK-04`: Workers emit progress events (0–100%) which are relayed to WebSocket subscribers.
- `FR-WORK-05`: On failure, the worker increments `attempt_count`. If `attempt_count < max_retries`, the job is re-queued with a delay. Otherwise, it goes to DLQ.

### 4.4 Rate Limiting

- `FR-RATE-01`: API endpoints are rate-limited per user per minute (configurable; default: 60 req/min).
- `FR-RATE-02`: Job submission is additionally rate-limited: max 10 jobs/min per user.
- `FR-RATE-03`: Max 20 concurrent `PROCESSING` jobs per user.
- `FR-RATE-04`: Rate limit headers (`X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`) are returned on every response.
- `FR-RATE-05`: Exceeding the limit returns HTTP 429 with a `Retry-After` header.

### 4.5 WebSocket Notifications

- `FR-WS-01`: Clients connect to `ws://host/ws` with a JWT query param for auth.
- `FR-WS-02`: Clients subscribe to a specific job: `SUBSCRIBE /topic/jobs/{jobId}`.
- `FR-WS-03`: Events pushed: `JOB_QUEUED`, `JOB_STARTED`, `JOB_PROGRESS`, `JOB_COMPLETED`, `JOB_FAILED`, `JOB_CANCELLED`.
- `FR-WS-04`: On reconnect, the client receives the latest known job state immediately.

### 4.6 Admin

- `FR-ADMIN-01`: Admin API to list all jobs across users, view DLQ, manually retry DLQ jobs, and revoke user tokens.
- `FR-ADMIN-02`: Admin dashboard page (single HTML page with Thymeleaf or React) for real-time queue depth and worker health.

---

## 5. Non-Functional Requirements (NFRs)

| Category | Requirement |
|---|---|
| **Throughput** | Sustain 500 job submissions/sec at peak |
| **Latency (API)** | p99 < 200 ms for all REST endpoints |
| **Latency (Processing Start)** | `NORMAL` jobs start processing within 2 sec of submission at nominal load |
| **Availability** | 99.9% (< 8.7 hrs downtime/year) per service |
| **Scalability** | Worker tier scales horizontally; adding a worker instance requires zero config changes |
| **Durability** | Zero job loss; every submitted job that is acknowledged is guaranteed to be processed or land in DLQ |
| **Security** | OWASP Top 10 compliance; all secrets via environment variables or Vault |
| **Observability** | Full distributed traces (OpenTelemetry), structured logs (JSON), Prometheus metrics |
| **Portability** | Fully runnable locally via `docker-compose up` |
| **Maintainability** | Test coverage ≥ 80% (unit + integration); all services have OpenAPI docs |

---

## 6. System Architecture — High-Level Design (HLD)

### 6.1 Component Map

```
                          ┌─────────────────────────────────────────────────────┐
                          │                   CLIENT LAYER                       │
                          │   REST Clients (curl, Postman, Browser)              │
                          │   WebSocket Clients (browser, mobile apps)           │
                          └────────────────────┬────────────────────────────────┘
                                               │ HTTPS / WSS
                          ┌────────────────────▼────────────────────────────────┐
                          │               API GATEWAY SERVICE                    │
                          │  • JWT validation (Spring Security)                  │
                          │  • Rate limiting (Redis sliding window)              │
                          │  • Request routing (Spring Cloud Gateway)            │
                          │  • Request/Response logging                          │
                          │  • OpenTelemetry instrumentation                     │
                          └───┬──────────────┬──────────────┬───────────────────┘
                              │              │              │
               ┌──────────────▼──┐  ┌────────▼────────┐  ┌▼──────────────────┐
               │  AUTH SERVICE   │  │   JOB SERVICE   │  │ WEBSOCKET SERVICE  │
               │                 │  │                 │  │                    │
               │ • Register/Login│  │ • Submit job    │  │ • STOMP broker     │
               │ • JWT issuance  │  │ • List/Get jobs │  │ • Job subscriptions│
               │ • Token refresh │  │ • Cancel job    │  │ • Event fan-out    │
               │ • Token revoke  │  │ • Publish event │  │                    │
               └────────┬────────┘  └────────┬────────┘  └────────────────────┘
                        │                    │                     ▲
                   ┌────▼────┐          ┌────▼────┐               │
                   │  Redis  │          │  Kafka  │───────────────►│
                   │         │          │         │     notification
                   │ • Token │          │ • job.  │     events
                   │   store │          │   topic │
                   │ • Rate  │          │ • dlq   │
                   │   limit │          │   topic │
                   └─────────┘          └────┬────┘
                                             │ consume
                          ┌──────────────────▼─────────────────────────────────┐
                          │              WORKER SERVICE  (N instances)          │
                          │                                                      │
                          │  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
                          │  │ Worker-1 │  │ Worker-2 │  │   Worker-N       │  │
                          │  │          │  │          │  │                  │  │
                          │  │ Job      │  │ Job      │  │ Job              │  │
                          │  │ Executor │  │ Executor │  │ Executor         │  │
                          │  └────┬─────┘  └────┬─────┘  └────────┬─────────┘  │
                          └───────┼─────────────┼─────────────────┼────────────┘
                                  │             │                 │
                          ┌───────▼─────────────▼─────────────────▼────────────┐
                          │               PostgreSQL  (Primary + Replica)        │
                          │  • users  • jobs  • job_results  • audit_log        │
                          └─────────────────────────────────────────────────────┘
                          
                          ┌─────────────────────────────────────────────────────┐
                          │           OBSERVABILITY STACK                        │
                          │  Prometheus ← Micrometer  │  Grafana Dashboards      │
                          │  ELK Stack (Logstash/ES)  │  Jaeger (Traces)         │
                          └─────────────────────────────────────────────────────┘
```

### 6.2 Service Responsibilities

| Service | Port | Responsibility |
|---|---|---|
| `api-gateway` | 8080 | Single ingress point; JWT validation; rate limiting; routing |
| `auth-service` | 8081 | User management; token issuance and rotation |
| `job-service` | 8082 | Job CRUD; Kafka publishing; state machine transitions |
| `worker-service` | 8083 | Kafka consumer; job execution; result persistence |
| `websocket-service` | 8084 | STOMP WebSocket broker; event fan-out from Kafka |

### 6.3 Data Flow — Job Submission to Completion

```
Client          Gateway         Job-Service         Kafka           Worker          WS-Service      Client (WS)
  │                │                │                 │               │                 │               │
  │─ POST /jobs ──►│                │                 │               │                 │               │
  │                │─ validate JWT ─│                 │               │                 │               │
  │                │─ rate limit ───│                 │               │                 │               │
  │                │─ route ───────►│                 │               │                 │               │
  │                │                │─ write PENDING ─┤               │                 │               │
  │                │                │─ publish ──────►│               │                 │               │
  │                │                │    job.submitted│               │                 │               │
  │◄── 202 {jobId}─┤◄───────────────┤                 │               │                 │               │
  │                │                │                 │◄─ consume ────│                 │               │
  │                │                │                 │               │─ update PROCESS─┤               │
  │                │                │                 │               │─ publish ───────►─ push event ──►│
  │                │                │                 │               │   job.processing│  JOB_STARTED  │
  │                │                │                 │               │─ execute job ───│               │
  │                │                │                 │               │─ publish ───────►─ push event ──►│
  │                │                │                 │               │   job.progress  │  JOB_PROGRESS │
  │                │                │                 │               │─ persist result─┤               │
  │                │                │                 │               │─ update COMPLETE┤               │
  │                │                │                 │               │─ publish ───────►─ push event ──►│
  │                │                │                 │               │   job.completed │  JOB_COMPLETED│
```

---

## 7. Low-Level Design (LLD)

### 7.1 API Gateway Service

Built with **Spring Cloud Gateway** (not Spring MVC — reactive, non-blocking).

```
api-gateway/
├── GatewayApplication.java
├── config/
│   ├── GatewayConfig.java            # Route definitions
│   ├── SecurityConfig.java           # JWT filter chain
│   └── RateLimitConfig.java          # Redis rate limiter beans
├── filter/
│   ├── JwtAuthenticationFilter.java  # Validates Bearer token, sets SecurityContext
│   ├── RateLimitFilter.java          # Sliding window check, sets X-RateLimit headers
│   ├── RequestLoggingFilter.java     # Structured JSON request/response log
│   └── CorrelationIdFilter.java      # Injects X-Correlation-ID header
└── exception/
    └── GlobalExceptionHandler.java   # RFC 7807 ProblemDetail responses
```

**GatewayConfig.java — Route definitions:**
```java
@Bean
public RouteLocator routes(RouteLocatorBuilder builder) {
    return builder.routes()
        .route("auth-service", r -> r
            .path("/api/v1/auth/**")
            .filters(f -> f.filter(correlationIdFilter))
            .uri("lb://auth-service"))        // Service discovery via Eureka
        .route("job-service", r -> r
            .path("/api/v1/jobs/**")
            .filters(f -> f
                .filter(jwtAuthFilter)
                .filter(rateLimitFilter))
            .uri("lb://job-service"))
        .route("websocket-service", r -> r
            .path("/ws/**")
            .uri("lb://websocket-service"))
        .build();
}
```

**JwtAuthenticationFilter:**
- Extracts `Authorization: Bearer <token>` header.
- Verifies signature using RSA public key (loaded from `auth-service`'s JWK Set endpoint).
- Rejects blacklisted tokens by checking Redis key `blacklist:{jti}`.
- Propagates claims as `X-User-Id`, `X-User-Role` headers to downstream services.
- Downstream services trust these headers (inter-service communication only, never from external).

### 7.2 Auth Service

```
auth-service/
├── controller/AuthController.java
├── service/
│   ├── AuthService.java
│   ├── TokenService.java
│   └── UserService.java
├── domain/
│   ├── User.java                     # JPA Entity
│   └── RefreshToken.java             # JPA Entity
├── repository/
│   ├── UserRepository.java
│   └── RefreshTokenRepository.java
├── security/
│   ├── JwtProperties.java            # RSA key paths, TTLs
│   └── KeyPairConfig.java            # Loads RSA private/public key at startup
└── dto/
    ├── RegisterRequest.java
    ├── LoginRequest.java
    └── TokenResponse.java
```

**TokenService — JWT issuance with RS256:**
```java
public String issueAccessToken(User user) {
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .id(UUID.randomUUID().toString())       // jti — used for blacklisting
        .issuedAt(new Date())
        .expiration(Date.from(Instant.now().plus(ACCESS_TOKEN_TTL)))
        .signWith(keyPairConfig.getPrivateKey())
        .compact();
}

public String issueRefreshToken(User user) {
    String token = UUID.randomUUID().toString();
    RefreshToken entity = RefreshToken.builder()
        .token(token)
        .userId(user.getId())
        .expiresAt(Instant.now().plus(REFRESH_TOKEN_TTL))
        .build();
    refreshTokenRepository.save(entity);
    // Also store in Redis with TTL for fast lookup
    redisTemplate.opsForValue().set(
        "refresh:" + token, user.getId().toString(), REFRESH_TOKEN_TTL);
    return token;
}
```

### 7.3 Job Service

This is the most complex service — it owns the job state machine.

```
job-service/
├── controller/
│   ├── JobController.java            # REST endpoints
│   └── AdminJobController.java       # Admin-only endpoints
├── service/
│   ├── JobService.java               # Business logic
│   ├── JobStateMachine.java          # Valid state transitions
│   └── KafkaPublisherService.java    # Publishes to Kafka topics
├── domain/
│   ├── Job.java                      # JPA Entity
│   ├── JobResult.java                # JPA Entity (OneToOne with Job)
│   ├── JobStatus.java                # Enum: PENDING, QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED
│   ├── JobType.java                  # Enum: DATA_EXPORT, REPORT_GENERATION, etc.
│   └── JobPriority.java              # Enum: LOW, NORMAL, HIGH
├── repository/
│   ├── JobRepository.java
│   └── JobResultRepository.java
├── mapper/JobMapper.java             # MapStruct — Entity ↔ DTO
├── kafka/
│   └── KafkaConfig.java              # Topic definitions, producer config
├── cache/
│   └── JobCacheService.java          # Redis get/set for job detail
└── dto/
    ├── JobSubmitRequest.java
    ├── JobResponse.java
    ├── JobDetailResponse.java
    └── PagedJobResponse.java
```

**JobStateMachine — enforces valid transitions:**
```java
@Component
public class JobStateMachine {

    private static final Map<JobStatus, Set<JobStatus>> VALID_TRANSITIONS = Map.of(
        PENDING,    Set.of(QUEUED, CANCELLED),
        QUEUED,     Set.of(PROCESSING, CANCELLED),
        PROCESSING, Set.of(COMPLETED, FAILED),
        COMPLETED,  Set.of(),
        FAILED,     Set.of(QUEUED),               // Manual retry from DLQ
        CANCELLED,  Set.of()
    );

    public void assertTransition(JobStatus from, JobStatus to) {
        if (!VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new InvalidJobTransitionException(
                "Cannot transition job from %s to %s".formatted(from, to));
        }
    }
}
```

**JobService — submit flow:**
```java
@Transactional
public JobResponse submitJob(JobSubmitRequest request, UUID userId) {
    // 1. Validate payload size
    validatePayloadSize(request.payload());

    // 2. Check concurrent job limit
    long inFlight = jobRepository.countByUserIdAndStatus(userId, PROCESSING);
    if (inFlight >= MAX_CONCURRENT_JOBS) {
        throw new ConcurrentJobLimitException("Max concurrent jobs reached: " + MAX_CONCURRENT_JOBS);
    }

    // 3. Persist with PENDING status
    Job job = Job.builder()
        .id(UUID.randomUUID())
        .userId(userId)
        .jobType(request.jobType())
        .priority(request.priority())
        .payload(request.payload())
        .status(PENDING)
        .maxRetries(jobTypeConfig.maxRetriesFor(request.jobType()))
        .build();
    job = jobRepository.save(job);

    // 4. Publish to Kafka (transactional outbox pattern — see below)
    kafkaPublisher.publishJobSubmitted(job);

    // 5. Update status to QUEUED after successful publish
    stateMachine.assertTransition(PENDING, QUEUED);
    job.setStatus(QUEUED);
    jobRepository.save(job);

    // 6. Invalidate any cached job list for this user
    jobCacheService.evictUserJobList(userId);

    return jobMapper.toResponse(job);
}
```

**Transactional Outbox Pattern** (prevents dual-write problem between DB and Kafka):

Rather than writing to the DB and Kafka in the same method and risking partial failure, the job service writes to an `outbox` table within the same DB transaction. A separate `OutboxPublisher` component polls this table and publishes to Kafka, deleting rows on success. This guarantees at-least-once delivery.

```
outbox table:
  id UUID PRIMARY KEY
  aggregate_type VARCHAR (e.g. 'Job')
  aggregate_id UUID
  event_type VARCHAR (e.g. 'JOB_SUBMITTED')
  payload JSONB
  created_at TIMESTAMPTZ
  published_at TIMESTAMPTZ NULL   -- NULL means not yet published
```

### 7.4 Worker Service

```
worker-service/
├── consumer/
│   ├── JobConsumer.java              # Kafka listener - dispatches to executor
│   └── DlqConsumer.java             # Dead letter queue listener
├── executor/
│   ├── JobExecutorRegistry.java      # Map<JobType, JobExecutor>
│   ├── JobExecutor.java              # Interface: execute(Job) → JobResult
│   └── impl/
│       ├── DataExportExecutor.java
│       ├── ReportGenerationExecutor.java
│       ├── EmailDispatchExecutor.java
│       ├── ImageProcessingExecutor.java
│       ├── WebhookCallExecutor.java
│       └── CustomScriptExecutor.java
├── retry/
│   └── RetryPolicy.java              # Exponential backoff config per job type
├── cancellation/
│   └── CancellationRegistry.java     # ConcurrentHashMap<jobId, AtomicBoolean>
├── kafka/
│   ├── KafkaConsumerConfig.java
│   └── KafkaProducerConfig.java      # For publishing status/progress events
└── service/
    ├── JobResultPersistenceService.java
    └── ProgressPublisherService.java
```

**JobConsumer — main consumer loop:**
```java
@KafkaListener(
    topics = {"job.normal", "job.high"},
    groupId = "worker-group",
    concurrency = "5"                 // 5 threads per worker instance
)
@Transactional
public void consume(ConsumerRecord<String, JobEvent> record, Acknowledgment ack) {
    JobEvent event = record.value();
    UUID jobId = event.jobId();

    // Register cancellation token for this job
    AtomicBoolean cancelFlag = cancellationRegistry.register(jobId);

    try {
        // Update DB: PROCESSING
        jobRepository.updateStatus(jobId, PROCESSING);
        progressPublisher.publishStarted(jobId);

        // Execute with timeout
        JobExecutor executor = executorRegistry.get(event.jobType());
        JobResult result = executeWithTimeout(executor, event, cancelFlag,
            jobTypeConfig.timeoutFor(event.jobType()));

        // Persist result and update status
        resultService.persist(jobId, result);
        jobRepository.updateStatus(jobId, COMPLETED);
        progressPublisher.publishCompleted(jobId, result);

        ack.acknowledge();    // Commit Kafka offset only on success

    } catch (CancellationException e) {
        jobRepository.updateStatus(jobId, CANCELLED);
        progressPublisher.publishCancelled(jobId);
        ack.acknowledge();

    } catch (Exception e) {
        handleFailure(jobId, event, e);
        ack.acknowledge();    // Still ack — retry is managed by our logic, not Kafka redelivery

    } finally {
        cancellationRegistry.deregister(jobId);
    }
}

private void handleFailure(UUID jobId, JobEvent event, Exception e) {
    Job job = jobRepository.findById(jobId).orElseThrow();
    int nextAttempt = job.getAttemptCount() + 1;

    if (nextAttempt <= job.getMaxRetries()) {
        // Re-queue with exponential backoff delay
        long delayMs = retryPolicy.backoffDelay(nextAttempt);
        jobRepository.updateAttemptCount(jobId, nextAttempt);
        kafkaProducer.sendWithDelay("job.retry", event, delayMs);
        progressPublisher.publishRetry(jobId, nextAttempt, delayMs);
    } else {
        // Exhaust retries → DLQ
        jobRepository.updateStatus(jobId, FAILED);
        kafkaProducer.send("job.dlq", event, e.getMessage());
        progressPublisher.publishFailed(jobId, e.getMessage());
    }
}
```

**Java 21 Virtual Threads:** The worker service uses `spring.threads.virtual.enabled=true` to run each Kafka consumer handler on a virtual thread, eliminating the overhead of platform thread pool management and enabling high concurrency with low footprint.

### 7.5 WebSocket Service

Uses **Spring WebSocket** with the **STOMP** subprotocol and an in-memory broker (upgradeable to Redis Pub/Sub for multi-instance deployments).

```
websocket-service/
├── config/
│   ├── WebSocketConfig.java          # STOMP endpoint, message broker config
│   └── WebSocketSecurityConfig.java  # JWT validation on handshake
├── consumer/
│   └── JobEventConsumer.java         # Kafka listener → broadcasts to STOMP
├── handler/
│   └── StompConnectEventHandler.java # On connect: push latest job state
└── dto/
    └── JobStatusMessage.java
```

**WebSocketConfig:**
```java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");  // Upgrade to RabbitMQ for production
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .setHandshakeHandler(new JwtHandshakeHandler())   // JWT validation
            .withSockJS();
    }
}
```

**JobEventConsumer — bridges Kafka → WebSocket:**
```java
@KafkaListener(topics = {
    "job.processing", "job.progress", "job.completed", "job.failed", "job.cancelled"
}, groupId = "websocket-group")
public void onJobEvent(JobEvent event) {
    String destination = "/topic/jobs/" + event.jobId();
    JobStatusMessage message = JobStatusMessage.builder()
        .jobId(event.jobId())
        .status(event.status())
        .progress(event.progress())
        .message(event.message())
        .timestamp(Instant.now())
        .build();
    messagingTemplate.convertAndSend(destination, message);
}
```

---

## 8. Data Models & Database Schema

### 8.1 Entity Relationship Overview

```
users (1) ──────────────────► (*) jobs
                                     │
                                     │ (1)
                                     ▼
                               job_results (1)
                               
jobs (1) ──────────────────► (*) outbox
users (1) ──────────────────► (*) audit_log
```

### 8.2 Table Definitions

```sql
-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email        VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,          -- BCrypt hash
    role         VARCHAR(20)  NOT NULL DEFAULT 'USER',  -- USER | ADMIN
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- ============================================================
-- JOBS
-- ============================================================
CREATE TABLE jobs (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id),
    job_type      VARCHAR(50)  NOT NULL,
    priority      VARCHAR(10)  NOT NULL DEFAULT 'NORMAL',
    status        VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    payload       JSONB        NOT NULL,
    attempt_count INT          NOT NULL DEFAULT 0,
    max_retries   INT          NOT NULL DEFAULT 3,
    error_message TEXT,
    scheduled_at  TIMESTAMPTZ,                    -- For delayed jobs
    started_at    TIMESTAMPTZ,
    completed_at  TIMESTAMPTZ,
    archived      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_jobs_user_id       ON jobs(user_id);
CREATE INDEX idx_jobs_status        ON jobs(status);
CREATE INDEX idx_jobs_user_status   ON jobs(user_id, status);
CREATE INDEX idx_jobs_created_at    ON jobs(created_at DESC);
CREATE INDEX idx_jobs_payload_gin   ON jobs USING GIN (payload); -- For payload queries

-- ============================================================
-- JOB RESULTS
-- ============================================================
CREATE TABLE job_results (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id       UUID NOT NULL REFERENCES jobs(id) UNIQUE,
    output       JSONB,                          -- Successful result
    error_detail JSONB,                          -- Structured error on failure
    duration_ms  BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
-- OUTBOX (Transactional Outbox Pattern)
-- ============================================================
CREATE TABLE outbox (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSONB        NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    published_at   TIMESTAMPTZ                   -- NULL = unpublished
);

CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

-- ============================================================
-- AUDIT LOG
-- ============================================================
CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     UUID,
    action      VARCHAR(100) NOT NULL,
    resource    VARCHAR(100),
    resource_id UUID,
    ip_address  INET,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user_id    ON audit_log(user_id);
CREATE INDEX idx_audit_created_at ON audit_log(created_at DESC);

-- ============================================================
-- REFRESH TOKENS
-- ============================================================
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token      VARCHAR(255) NOT NULL UNIQUE,
    user_id    UUID         NOT NULL REFERENCES users(id),
    expires_at TIMESTAMPTZ  NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

### 8.3 Redis Key Schema

| Key Pattern | Type | TTL | Purpose |
|---|---|---|---|
| `refresh:{token}` | String | 7 days | Fast refresh token lookup |
| `blacklist:{jti}` | String | Matches JWT exp | Revoked access tokens |
| `rate:{userId}:api` | Sorted Set | 1 min sliding | API rate limit window |
| `rate:{userId}:submit` | Sorted Set | 1 min sliding | Job submit rate limit |
| `job:{jobId}` | Hash | 10 min | Cached job detail |
| `jobs:user:{userId}` | String (JSON) | 30 sec | Cached paginated job list |
| `cancel:{jobId}` | String | 10 min | Cancellation signal for workers |

---

## 9. API Contract

All responses follow RFC 7807 Problem Details for error payloads.

### 9.1 Auth Endpoints

```
POST /api/v1/auth/register
Body:  { "email": "string", "password": "string" }
201:   { "id": "uuid", "email": "string", "createdAt": "iso8601" }
409:   Email already exists

POST /api/v1/auth/login
Body:  { "email": "string", "password": "string" }
200:   { "accessToken": "jwt", "refreshToken": "uuid", "expiresIn": 900 }
401:   Invalid credentials

POST /api/v1/auth/refresh
Body:  { "refreshToken": "uuid" }
200:   { "accessToken": "jwt", "refreshToken": "uuid", "expiresIn": 900 }
401:   Token invalid or expired

POST /api/v1/auth/logout          [Requires Bearer]
Body:  { "refreshToken": "uuid" }
204:   No content
```

### 9.2 Job Endpoints

```
POST /api/v1/jobs                 [Requires Bearer]
Body: {
  "jobType":  "DATA_EXPORT | REPORT_GENERATION | EMAIL_DISPATCH | ...",
  "priority": "LOW | NORMAL | HIGH",
  "payload":  { ...arbitrary JSON... }
}
202: {
  "jobId":     "uuid",
  "status":    "QUEUED",
  "createdAt": "iso8601",
  "_links": { "self": "/api/v1/jobs/{jobId}", "ws": "/topic/jobs/{jobId}" }
}
400: Validation error (payload too large, unknown jobType)
429: Rate limit exceeded

GET /api/v1/jobs                  [Requires Bearer]
Query: status, jobType, priority, after (cursor), limit (default 20, max 100)
200: {
  "data":       [ ...JobSummary... ],
  "nextCursor": "uuid | null",
  "total":      1234
}

GET /api/v1/jobs/{jobId}          [Requires Bearer]
200: {
  "jobId":       "uuid",
  "userId":      "uuid",
  "jobType":     "string",
  "priority":    "string",
  "status":      "string",
  "payload":     { ... },
  "attemptCount": 0,
  "maxRetries":  3,
  "result":      { "output": {...} | null, "errorDetail": {...} | null, "durationMs": 1240 },
  "startedAt":   "iso8601 | null",
  "completedAt": "iso8601 | null",
  "createdAt":   "iso8601"
}
404: Job not found
403: Not your job

DELETE /api/v1/jobs/{jobId}       [Requires Bearer]
204: Cancelled
409: Cannot cancel a COMPLETED or FAILED job

GET /api/v1/jobs/{jobId}/result   [Requires Bearer]
200: { "output": {...}, "durationMs": 1240 }
404: No result yet (job still processing)
```

### 9.3 Admin Endpoints

```
GET  /api/v1/admin/jobs           [Requires ADMIN role]
GET  /api/v1/admin/dlq            [Requires ADMIN role]
POST /api/v1/admin/dlq/{jobId}/retry   [Requires ADMIN role]
DELETE /api/v1/admin/users/{userId}/tokens  [Revoke all tokens for user]
GET  /api/v1/admin/metrics/summary
```

### 9.4 WebSocket

```
Connect: ws://host/ws?token={accessToken}

Subscribe: SUBSCRIBE /topic/jobs/{jobId}
Receive:
{
  "jobId":     "uuid",
  "status":    "PROCESSING | COMPLETED | FAILED | ...",
  "progress":  75,            // 0-100
  "message":   "Exporting row 7500 of 10000",
  "timestamp": "iso8601"
}
```

---

## 10. Kafka Event Schemas

### Topics

| Topic | Partitions | Retention | Description |
|---|---|---|---|
| `job.submitted` | 6 | 24h | New job submitted (triggers Outbox publisher) |
| `job.normal` | 12 | 48h | Normal priority jobs for workers |
| `job.high` | 6 | 48h | High priority jobs (separate consumer group with more instances) |
| `job.low` | 6 | 72h | Low priority jobs |
| `job.retry` | 6 | 72h | Jobs being retried (with delay header) |
| `job.processing` | 6 | 1h | Worker started a job (→ WebSocket) |
| `job.progress` | 6 | 1h | Worker progress update (→ WebSocket) |
| `job.completed` | 6 | 24h | Job finished successfully |
| `job.failed` | 6 | 24h | Job failed (may retry) |
| `job.dlq` | 3 | 14d | Dead Letter Queue — jobs exhausted retries |
| `job.cancelled` | 3 | 24h | Job was cancelled by user |

### Event Schema (Avro / JSON)

```json
{
  "eventId":   "uuid",
  "jobId":     "uuid",
  "userId":    "uuid",
  "eventType": "JOB_SUBMITTED",
  "jobType":   "DATA_EXPORT",
  "priority":  "NORMAL",
  "payload":   { ... },
  "attemptCount": 0,
  "maxRetries": 3,
  "timestamp": "2024-01-15T10:30:00Z",
  "correlationId": "uuid"
}
```

**Partition Key:** `userId` — ensures all events for a user's jobs land on the same partition, preserving ordering within a user's scope.

---

## 11. Security Design

### 11.1 JWT Architecture

- **Algorithm:** RS256 (asymmetric). The Auth service holds the **private key** for signing; all other services use the **public key** (fetched from Auth's JWK Set endpoint: `GET /.well-known/jwks.json`) for verification.
- This means no other service ever touches the signing key, and key rotation is centralised.
- **Access Token TTL:** 15 minutes.
- **Refresh Token:** Opaque UUID, not a JWT. Stored in PostgreSQL and Redis. Rotated on every use.

### 11.2 Token Revocation

Access tokens are stateless (JWTs), so revocation works via a **JWT ID (jti) blacklist** in Redis with TTL matching the token's remaining lifetime. On every request, the gateway checks `EXISTS blacklist:{jti}` — a fast O(1) Redis operation.

### 11.3 Inter-Service Communication

- Services running in Docker network communicate via service names (`http://job-service:8082`).
- The gateway strips all `X-User-*` headers from inbound client requests before adding its own verified headers. This prevents header injection attacks.
- In a production environment, mTLS would be added between services.

### 11.4 Input Validation

- All request DTOs use `@Valid` with Jakarta Bean Validation annotations.
- JSON payload for job submission is validated: max 1 MB, valid JSON structure.
- SQL injection is prevented by JPA/Hibernate parameterized queries exclusively.
- XSS protection via Spring Security's default headers (`X-Content-Type-Options`, `X-Frame-Options`).

### 11.5 Secrets Management

- No secrets in code or Docker images. All secrets injected via environment variables.
- In production: HashiCorp Vault with Spring Cloud Vault.
- RSA key pair generated at deploy time, stored as Kubernetes secrets.

---

## 12. Caching Strategy

### 12.1 Cache Layers

```
Request → Redis L1 Cache → PostgreSQL
              ↑
          Cache Miss
```

### 12.2 What Gets Cached

| Data | Cache Key | TTL | Invalidation |
|---|---|---|---|
| Single job detail | `job:{jobId}` | 10 min | On any status change |
| User's job list | `jobs:user:{userId}` | 30 sec | On new submission or cancellation |
| User info (for auth) | `user:{userId}` | 5 min | On user update |
| Job type config | `config:jobtypes` | 10 min | On admin config change |

### 12.3 Cache-Aside Pattern

```java
// JobCacheService
public JobDetailResponse getJobDetail(UUID jobId, UUID userId) {
    String key = "job:" + jobId;
    
    // 1. Try cache
    String cached = (String) redisTemplate.opsForValue().get(key);
    if (cached != null) {
        return objectMapper.readValue(cached, JobDetailResponse.class);
    }
    
    // 2. Cache miss → DB
    Job job = jobRepository.findByIdAndUserId(jobId, userId)
        .orElseThrow(() -> new JobNotFoundException(jobId));
    JobDetailResponse response = jobMapper.toDetailResponse(job);
    
    // 3. Populate cache
    redisTemplate.opsForValue().set(key,
        objectMapper.writeValueAsString(response),
        Duration.ofMinutes(10));
    
    return response;
}
```

### 12.4 Cache Invalidation

Job status changes happen in the worker service (different process). Invalidation is event-driven: the worker publishes a `job.completed` Kafka event, and the Job service listens and evicts the Redis key.

```java
@KafkaListener(topics = {"job.completed", "job.failed", "job.cancelled"})
public void onJobStatusChange(JobEvent event) {
    redisTemplate.delete("job:" + event.jobId());
    redisTemplate.delete("jobs:user:" + event.userId());
}
```

---

## 13. Rate Limiting Design

### 13.1 Sliding Window Algorithm

A **Sorted Set** in Redis stores request timestamps (score = epoch ms, member = unique request ID). On each request:

1. Remove members older than the window (60 sec ago).
2. Count remaining members.
3. If count ≥ limit → reject with 429.
4. Add current request timestamp.

This is atomic using a Lua script to prevent race conditions:

```lua
-- rate_limit.lua
local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])    -- 60000 ms
local limit = tonumber(ARGV[3])
local requestId = ARGV[4]

-- Remove old entries
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- Count current requests
local count = redis.call('ZCARD', key)

if count >= limit then
    return 0   -- Rejected
end

-- Add this request
redis.call('ZADD', key, now, requestId)
redis.call('PEXPIRE', key, window)

return 1   -- Allowed
```

### 13.2 Rate Limit Tiers

| Tier | Requests/min | Job Submits/min | Max Concurrent Jobs |
|---|---|---|---|
| Default (USER) | 60 | 10 | 20 |
| Premium (future) | 300 | 50 | 100 |
| Admin | 1000 | 200 | Unlimited |

### 13.3 Response Headers

```
HTTP/1.1 200 OK
X-RateLimit-Limit: 60
X-RateLimit-Remaining: 47
X-RateLimit-Reset: 1705316460   (Unix epoch when the window resets)

HTTP/1.1 429 Too Many Requests
Retry-After: 23                  (seconds to wait)
X-RateLimit-Remaining: 0
```

---

## 14. WebSocket Real-Time Layer

### 14.1 Connection Lifecycle

```
Client                    WS-Service                  Kafka
  │                           │                          │
  │── WS Handshake ──────────►│                          │
  │   ?token=<jwt>            │── Validate JWT ──────────┤
  │                           │◄─ Auth OK ───────────────┤
  │◄─ 101 Switching Protocols─│                          │
  │                           │                          │
  │── STOMP CONNECT ─────────►│                          │
  │◄─ CONNECTED ──────────────│                          │
  │                           │                          │
  │── SUBSCRIBE               │                          │
  │   /topic/jobs/{id} ──────►│                          │
  │                           │── Push latest state ────►│
  │◄─ MESSAGE (current state)─│                          │
  │                           │                          │
  │                           │◄─ Kafka event ───────────│
  │◄─ MESSAGE (update) ───────│                          │
  │                           │                          │
  │── DISCONNECT ─────────────►│                         │
```

### 14.2 Scaling WebSocket Service

A single WebSocket service instance holds in-memory subscriptions. For horizontal scaling:

- Replace `enableSimpleBroker` with a **RabbitMQ STOMP broker relay** (`enableStompBrokerRelay`).
- All WS service instances connect to RabbitMQ.
- Kafka events are published to a `ws.events` Kafka topic; each WS instance consumes it and relays to its connected clients.
- Clients reconnect to any instance; the `StompConnectEventHandler` immediately pushes the latest job state from Redis/DB on reconnect.

---

## 15. Resilience & Fault Tolerance

### 15.1 Circuit Breaker (Resilience4j)

Applied on calls from the Gateway to downstream services:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      job-service:
        slidingWindowSize: 10
        failureRateThreshold: 50          # 50% failures → OPEN
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
  retry:
    instances:
      job-service:
        maxAttempts: 3
        waitDuration: 500ms
        exponentialBackoffMultiplier: 2
  timelimiter:
    instances:
      job-service:
        timeoutDuration: 3s
```

States: `CLOSED` (normal) → `OPEN` (failing, reject fast) → `HALF_OPEN` (probe recovery) → `CLOSED`.

### 15.2 Bulkhead Pattern

Limits concurrent calls to a downstream service to prevent cascading failures:

```java
@Bulkhead(name = "job-service", type = Bulkhead.Type.SEMAPHORE)
public JobResponse submitJob(JobSubmitRequest request) { ... }
```

### 15.3 Kafka Consumer Resilience

- **Manual Acknowledgment:** Offsets committed only after successful processing. A crashed worker does not lose the job — Kafka redelivers to another consumer.
- **Retry Topic:** Failed jobs go to `job.retry` (not Kafka's auto-retry), allowing custom backoff delays via a scheduled re-publish.
- **DLQ:** Jobs exceeding `max_retries` go to `job.dlq`. Admins can inspect and retry manually.
- **Idempotency:** Workers check if a job is already `COMPLETED` before processing (idempotent consumer pattern). This handles duplicate deliveries safely.

### 15.4 Database Resilience

- **Connection Pool:** HikariCP with `maximumPoolSize=20`, `connectionTimeout=3000ms`, `keepaliveTime=60s`.
- **Read Replica:** List queries routed to a read replica via `@Transactional(readOnly=true)` with `AbstractRoutingDataSource`.
- **Optimistic Locking:** Job entities have a `@Version` field. Concurrent updates (e.g., two workers picking up the same job) throw `OptimisticLockException`, preventing data corruption.

```java
@Entity
public class Job {
    @Version
    private Long version;      // Prevents lost updates
    ...
}
```

---

## 16. Observability Stack

### 16.1 Metrics (Micrometer → Prometheus → Grafana)

Custom metrics registered in each service:

```java
// In JobService
Counter jobSubmitCounter = Counter.builder("taskforge.jobs.submitted")
    .tag("jobType", jobType.name())
    .tag("priority", priority.name())
    .register(meterRegistry);

Timer jobProcessingTimer = Timer.builder("taskforge.jobs.duration")
    .tag("jobType", jobType.name())
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(meterRegistry);

Gauge.builder("taskforge.queue.depth", kafkaAdmin, admin ->
    getKafkaLag("worker-group", "job.normal"))
    .register(meterRegistry);
```

**Grafana Dashboard Panels:**
- Job submission rate (jobs/sec by type)
- Queue depth per topic
- Processing latency distribution (p50/p95/p99)
- Worker throughput per instance
- Error rate and DLQ depth
- Redis hit/miss ratio
- Active WebSocket connections

### 16.2 Structured Logging (Logback → Logstash → Elasticsearch → Kibana)

Every log line is JSON with standard fields:

```json
{
  "timestamp": "2024-01-15T10:30:00.123Z",
  "level": "INFO",
  "service": "job-service",
  "traceId": "abc123",
  "spanId": "def456",
  "correlationId": "req-789",
  "userId": "user-uuid",
  "jobId": "job-uuid",
  "message": "Job submitted successfully",
  "jobType": "DATA_EXPORT",
  "durationMs": 45
}
```

**MDC (Mapped Diagnostic Context):** Correlation IDs, user IDs, and trace IDs are injected into the MDC at filter level and automatically included in every log line within that request scope.

### 16.3 Distributed Tracing (OpenTelemetry → Jaeger)

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

Traces propagated via `traceparent` header (W3C standard) across all services and into Kafka messages (via record headers). The Jaeger UI shows the full trace: Gateway → Job Service → Kafka → Worker Service → DB.

### 16.4 Health Checks

Each service exposes Spring Actuator endpoints:

```
GET /actuator/health          # Liveness + readiness
GET /actuator/health/liveness
GET /actuator/health/readiness
GET /actuator/prometheus      # Prometheus scrape endpoint
GET /actuator/info            # Git commit hash, build version
```

Docker Compose `healthcheck` uses `/actuator/health/readiness` to determine service startup order.

---

## 17. Infrastructure & DevOps

### 17.1 Docker Compose (Local Dev)

```yaml
# docker-compose.yml (abridged)
version: '3.9'
services:

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: taskforge
      POSTGRES_USER: taskforge
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U taskforge"]

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD}

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    depends_on: [zookeeper]
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
    healthcheck:
      test: ["CMD", "kafka-topics", "--bootstrap-server", "kafka:9092", "--list"]

  api-gateway:
    build: ./api-gateway
    ports: ["8080:8080"]
    depends_on: [auth-service, job-service, websocket-service]
    environment:
      REDIS_HOST: redis
      AUTH_SERVICE_URL: http://auth-service:8081

  auth-service:
    build: ./auth-service
    depends_on: [postgres, redis]
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/taskforge
      REDIS_HOST: redis
      JWT_PRIVATE_KEY_PATH: /run/secrets/jwt_private_key
    secrets: [jwt_private_key]

  job-service:
    build: ./job-service
    depends_on: [postgres, redis, kafka]

  worker-service:
    build: ./worker-service
    depends_on: [postgres, kafka]
    deploy:
      replicas: 3        # Scale horizontally

  websocket-service:
    build: ./websocket-service
    ports: ["8084:8084"]
    depends_on: [kafka, redis]

  prometheus:
    image: prom/prometheus
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana
    ports: ["3000:3000"]
    depends_on: [prometheus]
    volumes:
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards

volumes:
  postgres_data:
secrets:
  jwt_private_key:
    file: ./secrets/jwt_private.pem
```

### 17.2 Multi-Stage Dockerfile

```dockerfile
# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q
COPY src/ src/
RUN ./mvnw package -DskipTests -q

# Stage 2: Runtime (minimal image)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S taskforge && adduser -S taskforge -G taskforge
USER taskforge
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dspring.threads.virtual.enabled=true", \
  "-jar", "app.jar"]
```

### 17.3 Project Structure (Monorepo)

```
taskforge/
├── api-gateway/
│   ├── src/
│   ├── Dockerfile
│   └── pom.xml
├── auth-service/
├── job-service/
├── worker-service/
├── websocket-service/
├── common/                   # Shared DTOs, exceptions, Kafka event classes
│   └── pom.xml
├── monitoring/
│   ├── prometheus.yml
│   └── grafana/
│       └── dashboards/
│           └── taskforge.json
├── docker-compose.yml
├── docker-compose.override.yml   # Dev overrides (hot reload, etc.)
├── .github/
│   └── workflows/
│       └── ci.yml            # GitHub Actions: build, test, docker push
├── pom.xml                   # Parent POM (dependency management)
└── README.md
```

### 17.4 CI Pipeline (GitHub Actions)

```yaml
name: CI

on: [push, pull_request]

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env: { POSTGRES_PASSWORD: test, POSTGRES_DB: testdb }
      redis:
        image: redis:7

    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { java-version: '21', distribution: 'temurin' }
      - name: Run tests
        run: ./mvnw verify          # Unit + Integration tests (Testcontainers)
      - name: Build Docker images
        run: docker compose build
      - name: Upload coverage
        uses: codecov/codecov-action@v3
```

**Testing Strategy:**
- **Unit tests:** JUnit 5 + Mockito for service layer logic.
- **Integration tests:** `@SpringBootTest` + Testcontainers (real PostgreSQL, Redis, Kafka containers).
- **Contract tests:** Spring Cloud Contract for inter-service API compatibility.
- **Load tests:** k6 scripts in `/load-tests/` to validate NFRs (500 submissions/sec).

---

## 18. Implementation Roadmap

### Phase 1 — Core Foundation (Weeks 1–2)
- [ ] Set up Maven multi-module project and parent POM
- [ ] Implement Auth Service (register, login, JWT, refresh, logout)
- [ ] Implement Job Service (submit, list, get, cancel)
- [ ] PostgreSQL schema and Flyway migrations
- [ ] Basic Docker Compose with Postgres, Redis, Kafka
- [ ] Integration tests with Testcontainers

### Phase 2 — Async Processing (Weeks 3–4)
- [ ] Kafka topic configuration and producer in Job Service
- [ ] Implement Worker Service with `DATA_EXPORT` and `EMAIL_DISPATCH` executors
- [ ] Job state machine and transitions
- [ ] Retry logic and DLQ
- [ ] Transactional Outbox pattern
- [ ] Cancellation signal via Redis

### Phase 3 — API Gateway & Real-Time (Weeks 5–6)
- [ ] Spring Cloud Gateway with JWT filter and rate limiting (Redis)
- [ ] WebSocket service with STOMP
- [ ] Kafka → WebSocket bridge
- [ ] Redis caching with invalidation

### Phase 4 — Resilience & Observability (Weeks 7–8)
- [ ] Resilience4j Circuit Breaker, Retry, Bulkhead
- [ ] Micrometer custom metrics + Prometheus config
- [ ] Grafana dashboard JSON provisioning
- [ ] Structured JSON logging + Logstash config
- [ ] OpenTelemetry + Jaeger traces

### Phase 5 — Polish & Demo (Week 9)
- [ ] Admin API and basic dashboard page
- [ ] GitHub Actions CI pipeline
- [ ] README with architecture diagram and startup instructions
- [ ] Load test results documented
- [ ] Record a demo video

---

## 19. Trade-offs & Design Decisions

| Decision | Chosen | Alternative | Rationale |
|---|---|---|---|
| Message Broker | Kafka | RabbitMQ | Kafka's log retention and consumer group semantics suit the audit and replay requirements; higher throughput ceiling |
| Auth | JWT (RS256) | Session cookies | Stateless verification across services; RS256 avoids sharing a secret key |
| Rate Limiting | Redis Sliding Window | Token Bucket (in-memory) | Redis is shared across gateway instances; in-memory breaks under horizontal scaling |
| DB | PostgreSQL | MongoDB | Job data is relational and benefits from ACID guarantees; JSONB gives flexibility for payload/result |
| State Management | Optimistic Locking | Pessimistic Locking | Lower contention at scale; acceptable for job updates (low collision probability) |
| Outbox Pattern | Polling Outbox | Kafka Transactions | Simpler to implement and operate; Kafka transactions require idempotent producers configured precisely |
| Retry Strategy | Application-level retry | Kafka retry topics (native) | Allows custom backoff, per-job-type retry counts, and clean DLQ visibility |
| Virtual Threads | Java 21 Virtual Threads | Reactive (WebFlux) | Virtual threads give near-equivalent performance with a familiar imperative model; lower cognitive overhead |
| WebSocket Broker | In-memory (STOMP) | Redis Pub/Sub relay | Simpler for single-instance; README documents the upgrade path to RabbitMQ relay |

---

## 20. Resume Talking Points

This project is designed to demonstrate precisely the skills that backend engineering interviewers at tier-1 companies look for. Below are the narratives to use.

**"Tell me about a complex project you built."**
> "I built TaskForge, a distributed job processing platform from scratch — similar in concept to how Stripe runs background jobs or how LinkedIn manages async work. It's a full microservices system: a Spring Cloud Gateway handles auth and rate limiting, jobs are submitted to a Job Service and published to Kafka, stateless Worker instances consume and process them, and clients get real-time updates via WebSocket. I designed and implemented the full data flow, including a transactional outbox pattern to eliminate dual-write bugs, and Redis-based sliding window rate limiting. It's fully containerised with Docker Compose and has a Grafana observability dashboard."

**"How did you handle failures?"**
> "Every failure mode has an explicit owner. Kafka gives me durability — if a worker crashes mid-job, the message is redelivered. My workers use manual offset acknowledgment, so offsets only commit on successful processing. Failed jobs retry with exponential backoff, capped by a per-job-type max. Jobs that exhaust retries land in a Dead-Letter Queue. At the API level, I use Resilience4j circuit breakers between the gateway and downstream services, so a struggling Job Service doesn't cascade into a total outage."

**"How does your system scale?"**
> "Each service is stateless and horizontally scalable. Adding worker instances is zero-config — they join the same Kafka consumer group and Kafka rebalances partitions automatically. The gateway scales behind a load balancer, sharing rate limit state via Redis. The only stateful component is PostgreSQL, which I handle with a read replica for list queries and HikariCP connection pooling."

**"What system design patterns did you apply?"**
> "Several: the Transactional Outbox pattern to guarantee exactly-once Kafka delivery; Cache-aside with event-driven invalidation for Redis; a Job State Machine to enforce valid transitions and prevent corruption; the Bulkhead pattern to isolate service failures; and idempotent consumers in the worker layer to safely handle Kafka's at-least-once delivery semantics."

**"What would you do differently or improve?"**
> "For production, I'd replace the in-memory STOMP broker with a RabbitMQ relay for multi-instance WebSocket scaling. I'd also implement the Schema Registry for Kafka (Avro schemas), add Kubernetes manifests with HPA based on Kafka consumer lag, and explore Kafka Streams for real-time metrics aggregation directly in the pipeline."

---

*PRD Version 1.0 — TaskForge Distributed Job Processing Platform*
*Author: Jayesh R L | Portfolio Project | B.Tech CSE (AI & ML), Dayananda Sagar University*
