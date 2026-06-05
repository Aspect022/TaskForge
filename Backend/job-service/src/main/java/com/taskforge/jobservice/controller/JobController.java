package com.taskforge.jobservice.controller;

import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobStatus;
import com.taskforge.jobservice.domain.JobType;
import com.taskforge.jobservice.dto.JobDetailResponse;
import com.taskforge.jobservice.dto.JobResponse;
import com.taskforge.jobservice.dto.JobSubmitRequest;
import com.taskforge.jobservice.dto.PagedJobResponse;
import com.taskforge.jobservice.service.JobService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

	private final JobService jobService;
	private final UserContext userContext;

	public JobController(JobService jobService, UserContext userContext) {
		this.jobService = jobService;
		this.userContext = userContext;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public JobResponse submit(
			@Valid @RequestBody JobSubmitRequest request,
			@RequestHeader("X-User-Id") String userIdHeader,
			@RequestHeader(value = "X-Correlation-ID", required = false) String correlationIdHeader) {
		return jobService.submitJob(request, userContext.requireUserId(userIdHeader), userContext.correlationId(correlationIdHeader));
	}

	@GetMapping
	public PagedJobResponse list(
			@RequestHeader("X-User-Id") String userIdHeader,
			@RequestParam(required = false) JobStatus status,
			@RequestParam(required = false) JobType jobType,
			@RequestParam(required = false) JobPriority priority,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant after,
			@RequestParam(defaultValue = "20") int limit) {
		return jobService.listJobs(userContext.requireUserId(userIdHeader), status, jobType, priority, after, limit);
	}

	@GetMapping("/{jobId}")
	public JobDetailResponse get(
			@PathVariable UUID jobId,
			@RequestHeader("X-User-Id") String userIdHeader) {
		return jobService.getJob(jobId, userContext.requireUserId(userIdHeader));
	}

	@DeleteMapping("/{jobId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cancel(
			@PathVariable UUID jobId,
			@RequestHeader("X-User-Id") String userIdHeader) {
		jobService.cancelJob(jobId, userContext.requireUserId(userIdHeader));
	}
}
