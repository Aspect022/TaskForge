package com.taskforge.authservice.security;

import java.time.Instant;
import java.util.UUID;

public record AccessTokenInfo(UUID userId, String email, String role, String jwtId, Instant expiresAt) {
}
