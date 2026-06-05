package com.taskforge.jobservice.repository;

import com.taskforge.jobservice.domain.JobResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobResultRepository extends JpaRepository<JobResult, UUID> {

	Optional<JobResult> findByJobId(UUID jobId);
}
