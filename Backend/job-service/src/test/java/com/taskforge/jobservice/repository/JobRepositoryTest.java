package com.taskforge.jobservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.taskforge.jobservice.domain.Job;
import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobStatus;
import com.taskforge.jobservice.domain.JobType;
import com.taskforge.jobservice.domain.OutboxEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class JobRepositoryTest {

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Test
	void persistsJobAndOutboxEventWithFlywaySchema() {
		UUID userId = UUID.randomUUID();
		Job job = new Job(userId, JobType.DATA_EXPORT, JobPriority.HIGH, "{\"rows\":10}");
		job.setStatus(JobStatus.QUEUED);
		jobRepository.save(job);
		outboxEventRepository.save(new OutboxEvent("Job", job.getId(), "JOB_SUBMITTED", "{\"jobId\":\"" + job.getId() + "\"}"));

		assertThat(jobRepository.findByIdAndUserIdAndArchivedFalse(job.getId(), userId)).contains(job);
		assertThat(outboxEventRepository.findAll()).hasSize(1);
	}
}
