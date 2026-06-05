package com.taskforge.jobservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

	@Id
	private UUID id;

	@Column(name = "user_id", nullable = false)
	private UUID userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "job_type", nullable = false)
	private JobType jobType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JobPriority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JobStatus status = JobStatus.PENDING;

	@Column(nullable = false)
	private String payload;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount;

	@Column(name = "max_retries", nullable = false)
	private int maxRetries = 3;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "scheduled_at")
	private Instant scheduledAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(nullable = false)
	private boolean archived;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private Long version;

	public Job(UUID userId, JobType jobType, JobPriority priority, String payload) {
		this.id = UUID.randomUUID();
		this.userId = userId;
		this.jobType = jobType;
		this.priority = priority == null ? JobPriority.NORMAL : priority;
		this.payload = payload;
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		if (id == null) {
			id = UUID.randomUUID();
		}
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		updatedAt = Instant.now();
	}
}
