package com.taskforge.authservice.service;

import com.taskforge.authservice.config.JwtProperties;
import com.taskforge.authservice.domain.User;
import com.taskforge.authservice.security.AccessTokenInfo;
import com.taskforge.authservice.security.KeyPairProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private static final String KEY_ID = "taskforge-auth-dev";

	private final JwtProperties properties;
	private final KeyPairProvider keyPairProvider;

	public JwtService(JwtProperties properties, KeyPairProvider keyPairProvider) {
		this.properties = properties;
		this.keyPairProvider = keyPairProvider;
	}

	public String issueAccessToken(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(properties.accessTokenTtl());
		return Jwts.builder()
				.header().keyId(KEY_ID).and()
				.subject(user.getId().toString())
				.claim("email", user.getEmail())
				.claim("role", user.getRole().name())
				.id(UUID.randomUUID().toString())
				.issuedAt(Date.from(now))
				.expiration(Date.from(expiresAt))
				.signWith(keyPairProvider.privateKey(), Jwts.SIG.RS256)
				.compact();
	}

	public AccessTokenInfo parse(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(keyPairProvider.publicKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
		return new AccessTokenInfo(
				UUID.fromString(claims.getSubject()),
				claims.get("email", String.class),
				claims.get("role", String.class),
				claims.getId(),
				claims.getExpiration().toInstant());
	}

	public Duration accessTokenTtl() {
		return properties.accessTokenTtl();
	}

	public Duration refreshTokenTtl() {
		return properties.refreshTokenTtl();
	}

	public Map<String, String> publicJwk() {
		RSAPublicKey key = keyPairProvider.publicKey();
		return Map.of(
				"kty", "RSA",
				"use", "sig",
				"kid", KEY_ID,
				"alg", "RS256",
				"n", base64UrlUnsigned(key.getModulus()),
				"e", base64UrlUnsigned(key.getPublicExponent()));
	}

	private String base64UrlUnsigned(BigInteger value) {
		byte[] bytes = value.toByteArray();
		if (bytes.length > 1 && bytes[0] == 0) {
			bytes = java.util.Arrays.copyOfRange(bytes, 1, bytes.length);
		}
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
