package com.taskforge.authservice.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TokenStateService {

	private final StringRedisTemplate redisTemplate;

	public TokenStateService(StringRedisTemplate redisTemplate) {
		this.redisTemplate = redisTemplate;
	}

	public void storeRefreshToken(String token, String userId, Duration ttl) {
		redisTemplate.opsForValue().set(refreshKey(token), userId, ttl);
	}

	public void removeRefreshToken(String token) {
		redisTemplate.delete(refreshKey(token));
	}

	public void blacklistAccessToken(String jwtId, Duration ttl) {
		if (!ttl.isNegative() && !ttl.isZero()) {
			redisTemplate.opsForValue().set(blacklistKey(jwtId), "revoked", ttl);
		}
	}

	public boolean isAccessTokenBlacklisted(String jwtId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(blacklistKey(jwtId)));
	}

	private String refreshKey(String token) {
		return "refresh:" + token;
	}

	private String blacklistKey(String jwtId) {
		return "blacklist:" + jwtId;
	}
}
