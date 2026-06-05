package com.taskforge.jobservice.dto;

import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobStatus;
import com.taskforge.jobservice.domain.JobType;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record JobDetailResponse(
		UUID jobId,
		UUID userId,
		JobType jobType,
		JobPriority priority,
		JobStatus status,
		JsonNode payload,
		int attemptCount,
		int maxRetries,
		JobResultResponse result,
		Instant startedAt,
		Instant completedAt,
		Instant createdAt
) {
}
