package com.taskforge.jobservice.dto;

import java.util.List;
import java.util.UUID;

public record PagedJobResponse(
		List<JobSummaryResponse> data,
		UUID nextCursor,
		long total
) {
}
