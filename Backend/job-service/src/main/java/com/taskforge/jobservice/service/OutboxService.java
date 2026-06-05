package com.taskforge.jobservice.service;

import com.taskforge.jobservice.domain.Job;
import com.taskforge.jobservice.domain.OutboxEvent;
import com.taskforge.jobservice.dto.JobSubmittedEvent;
import com.taskforge.jobservice.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class OutboxService {

	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;

	public OutboxService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
		this.outboxEventRepository = outboxEventRepository;
		this.objectMapper = objectMapper;
	}

	public void writeJobSubmitted(Job job, String correlationId) {
		JobSubmittedEvent event = new JobSubmittedEvent(
				UUID.randomUUID(),
				job.getId(),
				job.getUserId(),
				"JOB_SUBMITTED",
				job.getJobType(),
				job.getPriority(),
				readPayload(job),
				job.getAttemptCount(),
				job.getMaxRetries(),
				Instant.now(),
				correlationId);
		outboxEventRepository.save(new OutboxEvent("Job", job.getId(), "JOB_SUBMITTED", writeEvent(event)));
	}

	private JsonNode readPayload(Job job) {
		try {
			return objectMapper.readTree(job.getPayload());
		} catch (JacksonException ex) {
			throw new IllegalStateException("Persisted job payload is invalid", ex);
		}
	}

	private String writeEvent(JobSubmittedEvent event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JacksonException ex) {
			throw new IllegalStateException("Unable to serialize outbox event", ex);
		}
	}
}
