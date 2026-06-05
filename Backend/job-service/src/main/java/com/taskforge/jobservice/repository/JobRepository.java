package com.taskforge.jobservice.repository;

import com.taskforge.jobservice.domain.Job;
import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobStatus;
import com.taskforge.jobservice.domain.JobType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobRepository extends JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

	Optional<Job> findByIdAndUserIdAndArchivedFalse(UUID id, UUID userId);

	long countByUserIdAndStatus(UUID userId, JobStatus status);

	List<Job> findByUserIdAndArchivedFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

	static org.springframework.data.jpa.domain.Specification<Job> belongsTo(UUID userId) {
		return (root, query, cb) -> cb.equal(root.get("userId"), userId);
	}

	static org.springframework.data.jpa.domain.Specification<Job> notArchived() {
		return (root, query, cb) -> cb.isFalse(root.get("archived"));
	}

	static org.springframework.data.jpa.domain.Specification<Job> hasStatus(JobStatus status) {
		return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
	}

	static org.springframework.data.jpa.domain.Specification<Job> hasJobType(JobType jobType) {
		return (root, query, cb) -> jobType == null ? cb.conjunction() : cb.equal(root.get("jobType"), jobType);
	}

	static org.springframework.data.jpa.domain.Specification<Job> hasPriority(JobPriority priority) {
		return (root, query, cb) -> priority == null ? cb.conjunction() : cb.equal(root.get("priority"), priority);
	}

	static org.springframework.data.jpa.domain.Specification<Job> afterCursor(Instant createdAt) {
		return (root, query, cb) -> createdAt == null ? cb.conjunction() : cb.lessThan(root.get("createdAt"), createdAt);
	}
}
