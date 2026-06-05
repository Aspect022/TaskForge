package com.taskforge.jobservice.dto;

import tools.jackson.databind.JsonNode;

public record JobResultResponse(
		JsonNode output,
		JsonNode errorDetail,
		Long durationMs
) {
}
