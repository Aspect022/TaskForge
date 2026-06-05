package com.taskforge.jobservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskforge.jobservice.domain.JobPriority;
import com.taskforge.jobservice.domain.JobStatus;
import com.taskforge.jobservice.domain.JobType;
import com.taskforge.jobservice.dto.JobDetailResponse;
import com.taskforge.jobservice.dto.JobResponse;
import com.taskforge.jobservice.dto.JobSummaryResponse;
import com.taskforge.jobservice.dto.PagedJobResponse;
import com.taskforge.jobservice.exception.GlobalExceptionHandler;
import com.taskforge.jobservice.service.JobService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

class JobControllerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private JobService jobService;
	private MockMvc mockMvc;
	private UUID userId;

	@BeforeEach
	void setUp() {
		jobService = Mockito.mock(JobService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new JobController(jobService, new UserContext()))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
		userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
	}

	@Test
	void submitReturnsAcceptedJob() throws Exception {
		UUID jobId = UUID.fromString("00000000-0000-0000-0000-000000000002");
		when(jobService.submitJob(any(), eq(userId), eq("corr-1")))
				.thenReturn(new JobResponse(jobId, JobStatus.QUEUED, Instant.parse("2026-06-04T00:00:00Z"), Map.of("self", "/api/v1/jobs/" + jobId)));

		mockMvc.perform(post("/api/v1/jobs")
						.header("X-User-Id", userId)
						.header("X-Correlation-ID", "corr-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"jobType":"DATA_EXPORT","priority":"HIGH","payload":{"rows":10}}
								"""))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.jobId").value(jobId.toString()))
				.andExpect(jsonPath("$.status").value("QUEUED"));
	}

	@Test
	void listReturnsPage() throws Exception {
		UUID jobId = UUID.fromString("00000000-0000-0000-0000-000000000002");
		when(jobService.listJobs(eq(userId), any(), any(), any(), any(), eq(20)))
				.thenReturn(new PagedJobResponse(List.of(new JobSummaryResponse(jobId, JobType.DATA_EXPORT, JobPriority.NORMAL, JobStatus.QUEUED, Instant.now())), null, 1));

		mockMvc.perform(get("/api/v1/jobs")
						.header("X-User-Id", userId)
						.param("status", "QUEUED"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].jobId").value(jobId.toString()))
				.andExpect(jsonPath("$.total").value(1));
	}

	@Test
	void getReturnsJobDetail() throws Exception {
		UUID jobId = UUID.fromString("00000000-0000-0000-0000-000000000002");
		when(jobService.getJob(jobId, userId)).thenReturn(new JobDetailResponse(
				jobId, userId, JobType.DATA_EXPORT, JobPriority.NORMAL, JobStatus.QUEUED,
				objectMapper.readTree("{\"rows\":10}"), 0, 3, null, null, null, Instant.now()));

		mockMvc.perform(get("/api/v1/jobs/{jobId}", jobId).header("X-User-Id", userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jobId").value(jobId.toString()))
				.andExpect(jsonPath("$.payload.rows").value(10));
	}

	@Test
	void cancelReturnsNoContent() throws Exception {
		UUID jobId = UUID.fromString("00000000-0000-0000-0000-000000000002");

		mockMvc.perform(delete("/api/v1/jobs/{jobId}", jobId).header("X-User-Id", userId))
				.andExpect(status().isNoContent());
	}

	@Test
	void missingUserHeaderReturnsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/jobs"))
				.andExpect(status().isUnauthorized());
	}
}
