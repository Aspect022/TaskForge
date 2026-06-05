package com.taskforge.authservice.dto;

public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
