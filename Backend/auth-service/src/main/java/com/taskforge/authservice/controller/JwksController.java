package com.taskforge.authservice.controller;

import com.taskforge.authservice.dto.JwkSetResponse;
import com.taskforge.authservice.service.JwtService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {

	private final JwtService jwtService;

	public JwksController(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@GetMapping("/.well-known/jwks.json")
	public JwkSetResponse jwks() {
		return new JwkSetResponse(List.of(jwtService.publicJwk()));
	}
}
