package com.taskforge.jobservice.dto;

import com.taskforge.jobservice.domain.JobStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JobResponse(
		UUID jobId,
		JobStatus status,
		Instant createdAt,
		Map<String, String> links
) {
}
