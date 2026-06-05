package com.taskforge.jobservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskforge.jobservice.config.JobProperties;
import com.taskforge.jobservice.domain.Job;
import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobStatus;
import com.taskforge.jobservice.domain.JobType;
import com.taskforge.jobservice.dto.JobResponse;
import com.taskforge.jobservice.dto.JobSubmitRequest;
import com.taskforge.jobservice.exception.InvalidJobTransitionException;
import com.taskforge.jobservice.exception.PayloadTooLargeException;
import com.taskforge.jobservice.repository.JobRepository;
import com.taskforge.jobservice.repository.JobResultRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

	@Mock
	private JobRepository jobRepository;

	@Mock
	private JobResultRepository jobResultRepository;

	@Mock
	private OutboxService outboxService;

	private JobService jobService;
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
		jobService = new JobService(
				jobRepository,
				jobResultRepository,
				new JobMapper(objectMapper),
				new JobStateMachine(),
				outboxService,
				new JobProperties(16, 20, 100));
	}

	@Test
	void submitPersistsQueuedJobAndWritesOutboxEvent() throws Exception {
		UUID userId = UUID.randomUUID();
		when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> {
			Job job = invocation.getArgument(0);
			return job;
		});

		JobResponse response = jobService.submitJob(
				new JobSubmitRequest(JobType.DATA_EXPORT, JobPriority.HIGH, objectMapper.readTree("{\"rows\":10}")),
				userId,
				"corr-1");

		ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
		verify(jobRepository).save(jobCaptor.capture());
		Job saved = jobCaptor.getValue();
		assertThat(saved.getUserId()).isEqualTo(userId);
		assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
		assertThat(response.status()).isEqualTo(JobStatus.QUEUED);
		verify(outboxService).writeJobSubmitted(saved, "corr-1");
	}

	@Test
	void submitRejectsOversizedPayload() throws Exception {
		assertThatThrownBy(() -> jobService.submitJob(
				new JobSubmitRequest(JobType.DATA_EXPORT, JobPriority.NORMAL, objectMapper.readTree("{\"value\":\"this is much too long\"}")),
				UUID.randomUUID(),
				"corr-1"))
				.isInstanceOf(PayloadTooLargeException.class);
	}

	@Test
	void cancelQueuedJobTransitionsToCancelled() {
		UUID userId = UUID.randomUUID();
		Job job = new Job(userId, JobType.EMAIL_DISPATCH, JobPriority.NORMAL, "{}");
		job.setStatus(JobStatus.QUEUED);
		when(jobRepository.findByIdAndUserIdAndArchivedFalse(job.getId(), userId)).thenReturn(Optional.of(job));

		jobService.cancelJob(job.getId(), userId);

		assertThat(job.getStatus()).isEqualTo(JobStatus.CANCELLED);
		assertThat(job.getCompletedAt()).isNotNull();
	}

	@Test
	void cancelCompletedJobFails() {
		UUID userId = UUID.randomUUID();
		Job job = new Job(userId, JobType.EMAIL_DISPATCH, JobPriority.NORMAL, "{}");
		job.setStatus(JobStatus.COMPLETED);
		when(jobRepository.findByIdAndUserIdAndArchivedFalse(job.getId(), userId)).thenReturn(Optional.of(job));

		assertThatThrownBy(() -> jobService.cancelJob(job.getId(), userId))
				.isInstanceOf(InvalidJobTransitionException.class);
	}
}
