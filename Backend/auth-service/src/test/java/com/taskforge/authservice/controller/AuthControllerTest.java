package com.taskforge.authservice.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskforge.authservice.dto.LoginRequest;
import com.taskforge.authservice.dto.LogoutRequest;
import com.taskforge.authservice.dto.RefreshRequest;
import com.taskforge.authservice.dto.RegisterRequest;
import com.taskforge.authservice.dto.RegisterResponse;
import com.taskforge.authservice.dto.TokenResponse;
import com.taskforge.authservice.exception.DuplicateEmailException;
import com.taskforge.authservice.exception.GlobalExceptionHandler;
import com.taskforge.authservice.exception.InvalidCredentialsException;
import com.taskforge.authservice.service.AuthService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private AuthService authService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		authService = Mockito.mock(AuthService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new AuthController(authService))
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void registerReturnsCreatedUser() throws Exception {
		when(authService.register(any(RegisterRequest.class)))
				.thenReturn(new RegisterResponse(UUID.fromString("00000000-0000-0000-0000-000000000001"), "jayesh@example.com", Instant.parse("2026-06-04T00:00:00Z")));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest("jayesh@example.com", "password123"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000001"))
				.andExpect(jsonPath("$.email").value("jayesh@example.com"));
	}

	@Test
	void registerDuplicateReturnsConflict() throws Exception {
		when(authService.register(any(RegisterRequest.class))).thenThrow(new DuplicateEmailException("jayesh@example.com"));

		mockMvc.perform(post("/api/v1/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RegisterRequest("jayesh@example.com", "password123"))))
				.andExpect(status().isConflict());
	}

	@Test
	void loginReturnsTokenPair() throws Exception {
		when(authService.login(any(LoginRequest.class))).thenReturn(new TokenResponse("access", "refresh", 900));

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest("jayesh@example.com", "password123"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("access"))
				.andExpect(jsonPath("$.refreshToken").value("refresh"))
				.andExpect(jsonPath("$.expiresIn").value(900));
	}

	@Test
	void loginInvalidCredentialsReturnsUnauthorized() throws Exception {
		when(authService.login(any(LoginRequest.class))).thenThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LoginRequest("jayesh@example.com", "bad-password"))))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void refreshReturnsTokenPair() throws Exception {
		when(authService.refresh(any(RefreshRequest.class))).thenReturn(new TokenResponse("new-access", "new-refresh", 900));

		mockMvc.perform(post("/api/v1/auth/refresh")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new RefreshRequest("old-refresh"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value("new-access"))
				.andExpect(jsonPath("$.refreshToken").value("new-refresh"));
	}

	@Test
	void logoutReturnsNoContent() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout")
						.header("Authorization", "Bearer access")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(new LogoutRequest("refresh"))))
				.andExpect(status().isNoContent());
	}
}
