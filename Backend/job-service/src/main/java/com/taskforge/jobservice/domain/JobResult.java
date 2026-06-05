package com.taskforge.jobservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_results")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobResult {

	@Id
	private UUID id;

	@OneToOne(optional = false)
	@JoinColumn(name = "job_id", nullable = false, unique = true)
	private Job job;

	private String output;

	@Column(name = "error_detail")
	private String errorDetail;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	void prePersist() {
		if (id == null) {
			id = UUID.randomUUID();
		}
		createdAt = Instant.now();
	}
}
