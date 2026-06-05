package com.taskforge.jobservice.dto;

import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobStatus;
import com.taskforge.jobservice.domain.JobType;
import java.time.Instant;
import java.util.UUID;

public record JobSummaryResponse(
		UUID jobId,
		JobType jobType,
		JobPriority priority,
		JobStatus status,
		Instant createdAt
) {
}
