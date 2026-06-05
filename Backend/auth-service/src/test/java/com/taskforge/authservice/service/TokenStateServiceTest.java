package com.taskforge.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class TokenStateServiceTest {

	@Mock
	private StringRedisTemplate redisTemplate;

	@Mock
	private ValueOperations<String, String> valueOperations;

	@Test
	void storesRefreshTokenWithExpectedKeyAndTtl() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		TokenStateService service = new TokenStateService(redisTemplate);

		service.storeRefreshToken("refresh-token", "user-id", Duration.ofDays(7));

		verify(valueOperations).set("refresh:refresh-token", "user-id", Duration.ofDays(7));
	}

	@Test
	void blacklistsAccessTokenWithExpectedKeyAndTtl() {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		TokenStateService service = new TokenStateService(redisTemplate);

		service.blacklistAccessToken("jwt-id", Duration.ofMinutes(3));

		verify(valueOperations).set("blacklist:jwt-id", "revoked", Duration.ofMinutes(3));
	}

	@Test
	void detectsBlacklistedAccessToken() {
		TokenStateService service = new TokenStateService(redisTemplate);
		when(redisTemplate.hasKey("blacklist:jwt-id")).thenReturn(true);

		assertThat(service.isAccessTokenBlacklisted("jwt-id")).isTrue();
	}
}
