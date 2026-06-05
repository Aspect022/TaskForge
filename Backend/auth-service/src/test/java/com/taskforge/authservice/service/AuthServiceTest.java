package com.taskforge.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskforge.authservice.domain.RefreshToken;
import com.taskforge.authservice.domain.User;
import com.taskforge.authservice.dto.LoginRequest;
import com.taskforge.authservice.dto.RefreshRequest;
import com.taskforge.authservice.dto.RegisterRequest;
import com.taskforge.authservice.dto.TokenResponse;
import com.taskforge.authservice.exception.DuplicateEmailException;
import com.taskforge.authservice.exception.InvalidCredentialsException;
import com.taskforge.authservice.exception.InvalidRefreshTokenException;
import com.taskforge.authservice.repository.RefreshTokenRepository;
import com.taskforge.authservice.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@Mock
	private JwtService jwtService;

	@Mock
	private TokenStateService tokenStateService;

	private PasswordEncoder passwordEncoder;
	private AuthService authService;

	@BeforeEach
	void setUp() {
		passwordEncoder = new BCryptPasswordEncoder(12);
		authService = new AuthService(userRepository, refreshTokenRepository, passwordEncoder, jwtService, tokenStateService);
	}

	@Test
	void registerHashesPasswordAndNormalizesEmail() {
		when(userRepository.existsByEmailIgnoreCase("jayesh@example.com")).thenReturn(false);
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		authService.register(new RegisterRequest(" Jayesh@Example.com ", "password123"));

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		User saved = userCaptor.getValue();
		assertThat(saved.getEmail()).isEqualTo("jayesh@example.com");
		assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
	}

	@Test
	void registerRejectsDuplicateEmail() {
		when(userRepository.existsByEmailIgnoreCase("jayesh@example.com")).thenReturn(true);

		assertThatThrownBy(() -> authService.register(new RegisterRequest("jayesh@example.com", "password123")))
				.isInstanceOf(DuplicateEmailException.class);
	}

	@Test
	void loginRejectsInvalidCredentials() {
		when(userRepository.findByEmailIgnoreCase("jayesh@example.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(new LoginRequest("jayesh@example.com", "bad-password")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void loginIssuesTokenPairForValidCredentials() {
		String passwordHash = passwordEncoder.encode("password123");
		User user = new User("jayesh@example.com", passwordHash);
		when(userRepository.findByEmailIgnoreCase("jayesh@example.com")).thenReturn(Optional.of(user));
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtService.issueAccessToken(user)).thenReturn("access-token");
		when(jwtService.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
		when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));

		TokenResponse response = authService.login(new LoginRequest("jayesh@example.com", "password123"));

		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isNotBlank();
		assertThat(response.expiresIn()).isEqualTo(900);
		verify(tokenStateService).storeRefreshToken(any(String.class), any(String.class), any(Duration.class));
	}

	@Test
	void refreshRotatesRefreshToken() {
		User user = new User("jayesh@example.com", "hash");
		RefreshToken oldToken = new RefreshToken("old-token", user, Instant.now().plus(Duration.ofDays(1)));
		when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(oldToken));
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(jwtService.issueAccessToken(user)).thenReturn("access-token");
		when(jwtService.refreshTokenTtl()).thenReturn(Duration.ofDays(7));
		when(jwtService.accessTokenTtl()).thenReturn(Duration.ofMinutes(15));

		TokenResponse response = authService.refresh(new RefreshRequest("old-token"));

		assertThat(oldToken.isRevoked()).isTrue();
		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.refreshToken()).isNotBlank();
		verify(tokenStateService).removeRefreshToken("old-token");
		verify(tokenStateService).storeRefreshToken(any(String.class), any(String.class), any(Duration.class));
	}

	@Test
	void refreshRejectsRevokedToken() {
		User user = new User("jayesh@example.com", "hash");
		RefreshToken oldToken = new RefreshToken("old-token", user, Instant.now().plus(Duration.ofDays(1)));
		oldToken.setRevoked(true);
		when(refreshTokenRepository.findByToken("old-token")).thenReturn(Optional.of(oldToken));

		assertThatThrownBy(() -> authService.refresh(new RefreshRequest("old-token")))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}
}
