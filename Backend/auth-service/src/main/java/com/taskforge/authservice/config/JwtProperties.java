package com.taskforge.authservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskforge.jwt")
public record JwtProperties(
		Duration accessTokenTtl,
		Duration refreshTokenTtl,
		String privateKeyPath,
		String publicKeyPath
) {
	public JwtProperties {
		if (accessTokenTtl == null) {
			accessTokenTtl = Duration.ofMinutes(15);
		}
		if (refreshTokenTtl == null) {
			refreshTokenTtl = Duration.ofDays(7);
		}
	}
}
