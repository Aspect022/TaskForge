package com.taskforge.authservice.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.taskforge.authservice.domain.RefreshToken;
import com.taskforge.authservice.domain.User;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AuthRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@Test
	void persistsUserAndRefreshTokenWithFlywaySchema() {
		User user = userRepository.save(new User("jayesh@example.com", "bcrypt-hash"));
		RefreshToken refreshToken = refreshTokenRepository.save(
				new RefreshToken("refresh-token", user, Instant.now().plus(Duration.ofDays(7))));

		assertThat(userRepository.findByEmailIgnoreCase("JAYESH@example.com")).contains(user);
		assertThat(refreshTokenRepository.findByToken("refresh-token")).contains(refreshToken);
	}
}
