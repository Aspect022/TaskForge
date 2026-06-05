package com.taskforge.jobservice.service;

import static com.taskforge.jobservice.domain.JobStatus.CANCELLED;
import static com.taskforge.jobservice.domain.JobStatus.COMPLETED;
import static com.taskforge.jobservice.domain.JobStatus.PENDING;
import static com.taskforge.jobservice.domain.JobStatus.PROCESSING;
import static com.taskforge.jobservice.domain.JobStatus.QUEUED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.taskforge.jobservice.exception.InvalidJobTransitionException;
import org.junit.jupiter.api.Test;

class JobStateMachineTest {

	private final JobStateMachine stateMachine = new JobStateMachine();

	@Test
	void allowsExpectedTransitions() {
		stateMachine.assertTransition(PENDING, QUEUED);
		stateMachine.assertTransition(QUEUED, CANCELLED);
		stateMachine.assertTransition(PROCESSING, COMPLETED);
		stateMachine.assertTransition(PROCESSING, CANCELLED);
	}

	@Test
	void rejectsInvalidTransitions() {
		assertThatThrownBy(() -> stateMachine.assertTransition(COMPLETED, QUEUED))
				.isInstanceOf(InvalidJobTransitionException.class);
	}
}
