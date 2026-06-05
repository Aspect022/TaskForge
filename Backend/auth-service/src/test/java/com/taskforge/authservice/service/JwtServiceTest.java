package com.taskforge.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.taskforge.authservice.config.JwtProperties;
import com.taskforge.authservice.domain.User;
import com.taskforge.authservice.security.AccessTokenInfo;
import com.taskforge.authservice.security.KeyPairProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	@Test
	void issueAccessTokenIncludesExpectedClaims() {
		JwtService jwtService = new JwtService(
				new JwtProperties(Duration.ofMinutes(15), Duration.ofDays(7), "", ""),
				new KeyPairProvider(new JwtProperties(Duration.ofMinutes(15), Duration.ofDays(7), "", "")));
		User user = new User("jayesh@example.com", "hash");

		String token = jwtService.issueAccessToken(user);
		AccessTokenInfo info = jwtService.parse(token);

		assertThat(info.userId()).isEqualTo(user.getId());
		assertThat(info.email()).isEqualTo("jayesh@example.com");
		assertThat(info.role()).isEqualTo("USER");
		assertThat(info.jwtId()).isNotBlank();
		assertThat(jwtService.publicJwk()).containsEntry("alg", "RS256");
	}
}
