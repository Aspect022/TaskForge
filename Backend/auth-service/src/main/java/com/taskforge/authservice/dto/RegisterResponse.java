package com.taskforge.authservice.dto;

import java.time.Instant;
import java.util.UUID;

public record RegisterResponse(UUID id, String email, Instant createdAt) {
}
