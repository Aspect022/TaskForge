package com.taskforge.authservice.controller;

import com.taskforge.authservice.dto.LoginRequest;
import com.taskforge.authservice.dto.LogoutRequest;
import com.taskforge.authservice.dto.RefreshRequest;
import com.taskforge.authservice.dto.RegisterRequest;
import com.taskforge.authservice.dto.RegisterResponse;
import com.taskforge.authservice.dto.TokenResponse;
import com.taskforge.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public TokenResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
		return authService.refresh(request);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(
			@Valid @RequestBody LogoutRequest request,
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
		authService.logout(request, authorizationHeader);
	}
}
