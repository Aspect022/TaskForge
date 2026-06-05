package com.taskforge.jobservice.service;

import com.taskforge.jobservice.domain.Job;
import com.taskforge.jobservice.domain.JobResult;
import com.taskforge.jobservice.dto.JobDetailResponse;
import com.taskforge.jobservice.dto.JobResponse;
import com.taskforge.jobservice.dto.JobResultResponse;
import com.taskforge.jobservice.dto.JobSummaryResponse;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JobMapper {

	private final ObjectMapper objectMapper;

	public JobMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public JobResponse toResponse(Job job) {
		return new JobResponse(job.getId(), job.getStatus(), job.getCreatedAt(), Map.of(
				"self", "/api/v1/jobs/" + job.getId(),
				"ws", "/topic/jobs/" + job.getId()
		));
	}

	public JobSummaryResponse toSummary(Job job) {
		return new JobSummaryResponse(job.getId(), job.getJobType(), job.getPriority(), job.getStatus(), job.getCreatedAt());
	}

	public JobDetailResponse toDetail(Job job, JobResult result) {
		return new JobDetailResponse(
				job.getId(),
				job.getUserId(),
				job.getJobType(),
				job.getPriority(),
				job.getStatus(),
				readJson(job.getPayload()),
				job.getAttemptCount(),
				job.getMaxRetries(),
				toResult(result),
				job.getStartedAt(),
				job.getCompletedAt(),
				job.getCreatedAt());
	}

	public String writeJson(JsonNode payload) {
		try {
			return objectMapper.writeValueAsString(payload);
		} catch (JacksonException ex) {
			throw new IllegalArgumentException("Invalid JSON payload", ex);
		}
	}

	private JobResultResponse toResult(JobResult result) {
		if (result == null) {
			return null;
		}
		return new JobResultResponse(readJsonNullable(result.getOutput()), readJsonNullable(result.getErrorDetail()), result.getDurationMs());
	}

	private JsonNode readJson(String value) {
		try {
			return objectMapper.readTree(value);
		} catch (JacksonException ex) {
			throw new IllegalStateException("Persisted JSON is invalid", ex);
		}
	}

	private JsonNode readJsonNullable(String value) {
		return value == null ? null : readJson(value);
	}
}
