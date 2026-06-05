package com.taskforge.jobservice.dto;

import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobType;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record JobSubmitRequest(
		@NotNull JobType jobType,
		JobPriority priority,
		@NotNull JsonNode payload
) {
}
