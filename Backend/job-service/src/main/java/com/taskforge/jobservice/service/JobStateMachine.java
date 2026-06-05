package com.taskforge.jobservice.service;

import static com.taskforge.jobservice.domain.JobStatus.CANCELLED;
import static com.taskforge.jobservice.domain.JobStatus.COMPLETED;
import static com.taskforge.jobservice.domain.JobStatus.FAILED;
import static com.taskforge.jobservice.domain.JobStatus.PENDING;
import static com.taskforge.jobservice.domain.JobStatus.PROCESSING;
import static com.taskforge.jobservice.domain.JobStatus.QUEUED;

import com.taskforge.jobservice.domain.JobStatus;
import com.taskforge.jobservice.exception.InvalidJobTransitionException;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class JobStateMachine {

	private static final Map<JobStatus, Set<JobStatus>> VALID_TRANSITIONS = Map.of(
			PENDING, Set.of(QUEUED, CANCELLED),
			QUEUED, Set.of(PROCESSING, CANCELLED),
			PROCESSING, Set.of(COMPLETED, FAILED, CANCELLED),
			COMPLETED, Set.of(),
			FAILED, Set.of(QUEUED),
			CANCELLED, Set.of()
	);

	public void assertTransition(JobStatus from, JobStatus to) {
		if (!VALID_TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
			throw new InvalidJobTransitionException("Cannot transition job from " + from + " to " + to);
		}
	}
}
