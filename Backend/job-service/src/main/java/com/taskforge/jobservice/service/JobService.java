package com.taskforge.jobservice.service;

import static com.taskforge.jobservice.repository.JobRepository.afterCursor;
import static com.taskforge.jobservice.repository.JobRepository.belongsTo;
import static com.taskforge.jobservice.repository.JobRepository.hasJobType;
import static com.taskforge.jobservice.repository.JobRepository.hasPriority;
import static com.taskforge.jobservice.repository.JobRepository.hasStatus;
import static com.taskforge.jobservice.repository.JobRepository.notArchived;

import com.taskforge.jobservice.config.JobProperties;
import com.taskforge.jobservice.domain.Job;
import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobStatus;
import com.taskforge.jobservice.domain.JobType;
import com.taskforge.jobservice.dto.JobDetailResponse;
import com.taskforge.jobservice.dto.JobResponse;
import com.taskforge.jobservice.dto.JobSubmitRequest;
import com.taskforge.jobservice.dto.JobSummaryResponse;
import com.taskforge.jobservice.dto.PagedJobResponse;
import com.taskforge.jobservice.exception.JobNotFoundException;
import com.taskforge.jobservice.exception.PayloadTooLargeException;
import com.taskforge.jobservice.repository.JobRepository;
import com.taskforge.jobservice.repository.JobResultRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

	private final JobRepository jobRepository;
	private final JobResultRepository jobResultRepository;
	private final JobMapper jobMapper;
	private final JobStateMachine stateMachine;
	private final OutboxService outboxService;
	private final JobProperties properties;

	public JobService(
			JobRepository jobRepository,
			JobResultRepository jobResultRepository,
			JobMapper jobMapper,
			JobStateMachine stateMachine,
			OutboxService outboxService,
			JobProperties properties) {
		this.jobRepository = jobRepository;
		this.jobResultRepository = jobResultRepository;
		this.jobMapper = jobMapper;
		this.stateMachine = stateMachine;
		this.outboxService = outboxService;
		this.properties = properties;
	}

	@Transactional
	public JobResponse submitJob(JobSubmitRequest request, UUID userId, String correlationId) {
		String payload = jobMapper.writeJson(request.payload());
		validatePayloadSize(payload);

		Job job = new Job(userId, request.jobType(), request.priority(), payload);
		jobRepository.save(job);

		stateMachine.assertTransition(JobStatus.PENDING, JobStatus.QUEUED);
		job.setStatus(JobStatus.QUEUED);
		outboxService.writeJobSubmitted(job, correlationId);

		return jobMapper.toResponse(job);
	}

	@Transactional(readOnly = true)
	public PagedJobResponse listJobs(
			UUID userId,
			JobStatus status,
			JobType jobType,
			JobPriority priority,
			Instant after,
			int limit) {
		int effectiveLimit = normalizeLimit(limit);
		Specification<Job> spec = Specification
				.allOf(belongsTo(userId), notArchived(), hasStatus(status), hasJobType(jobType), hasPriority(priority), afterCursor(after));

		List<Job> jobs = jobRepository.findAll(spec, PageRequest.of(0, effectiveLimit + 1, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
		boolean hasNext = jobs.size() > effectiveLimit;
		List<Job> page = hasNext ? jobs.subList(0, effectiveLimit) : jobs;
		UUID nextCursor = hasNext ? page.get(page.size() - 1).getId() : null;
		List<JobSummaryResponse> data = page.stream().map(jobMapper::toSummary).toList();
		long total = jobRepository.count(Specification.allOf(belongsTo(userId), notArchived(), hasStatus(status), hasJobType(jobType), hasPriority(priority)));

		return new PagedJobResponse(data, nextCursor, total);
	}

	@Transactional(readOnly = true)
	public JobDetailResponse getJob(UUID jobId, UUID userId) {
		Job job = findOwnedJob(jobId, userId);
		return jobMapper.toDetail(job, jobResultRepository.findByJobId(jobId).orElse(null));
	}

	@Transactional
	public void cancelJob(UUID jobId, UUID userId) {
		Job job = findOwnedJob(jobId, userId);
		stateMachine.assertTransition(job.getStatus(), JobStatus.CANCELLED);
		job.setStatus(JobStatus.CANCELLED);
		job.setCompletedAt(Instant.now());
	}

	private Job findOwnedJob(UUID jobId, UUID userId) {
		return jobRepository.findByIdAndUserIdAndArchivedFalse(jobId, userId)
				.orElseThrow(() -> new JobNotFoundException(jobId));
	}

	private void validatePayloadSize(String payload) {
		int bytes = payload.getBytes(StandardCharsets.UTF_8).length;
		if (bytes > properties.payloadMaxBytes()) {
			throw new PayloadTooLargeException(bytes, properties.payloadMaxBytes());
		}
	}

	private int normalizeLimit(int limit) {
		if (limit <= 0) {
			return properties.defaultLimit();
		}
		return Math.min(limit, properties.maxLimit());
	}
}
