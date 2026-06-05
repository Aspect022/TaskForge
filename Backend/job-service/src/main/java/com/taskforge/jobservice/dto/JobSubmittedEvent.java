package com.taskforge.jobservice.dto;

import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobType;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record JobSubmittedEvent(
		UUID eventId,
		UUID jobId,
		UUID userId,
		String eventType,
		JobType jobType,
		JobPriority priority,
		JsonNode payload,
		int attemptCount,
		int maxRetries,
		Instant timestamp,
		String correlationId
) {
}
