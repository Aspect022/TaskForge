package com.taskforge.authservice.service;

import com.taskforge.authservice.domain.RefreshToken;
import com.taskforge.authservice.domain.User;
import com.taskforge.authservice.dto.LoginRequest;
import com.taskforge.authservice.dto.LogoutRequest;
import com.taskforge.authservice.dto.RefreshRequest;
import com.taskforge.authservice.dto.RegisterRequest;
import com.taskforge.authservice.dto.RegisterResponse;
import com.taskforge.authservice.dto.TokenResponse;
import com.taskforge.authservice.exception.DuplicateEmailException;
import com.taskforge.authservice.exception.InvalidAccessTokenException;
import com.taskforge.authservice.exception.InvalidCredentialsException;
import com.taskforge.authservice.exception.InvalidRefreshTokenException;
import com.taskforge.authservice.repository.RefreshTokenRepository;
import com.taskforge.authservice.repository.UserRepository;
import com.taskforge.authservice.security.AccessTokenInfo;
import io.jsonwebtoken.JwtException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final TokenStateService tokenStateService;

	public AuthService(
			UserRepository userRepository,
			RefreshTokenRepository refreshTokenRepository,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			TokenStateService tokenStateService) {
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.tokenStateService = tokenStateService;
	}

	@Transactional
	public RegisterResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new DuplicateEmailException(email);
		}

		User user = new User(email, passwordEncoder.encode(request.password()));
		userRepository.save(user);
		return new RegisterResponse(user.getId(), user.getEmail(), user.getCreatedAt());
	}

	@Transactional
	public TokenResponse login(LoginRequest request) {
		User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
				.filter(candidate -> candidate.isActive() && passwordEncoder.matches(request.password(), candidate.getPasswordHash()))
				.orElseThrow(InvalidCredentialsException::new);
		return issueTokenPair(user);
	}

	@Transactional
	public TokenResponse refresh(RefreshRequest request) {
		RefreshToken current = refreshTokenRepository.findByToken(request.refreshToken())
				.orElseThrow(InvalidRefreshTokenException::new);

		Instant now = Instant.now();
		if (current.isRevoked() || current.isExpired(now) || !current.getUser().isActive()) {
			throw new InvalidRefreshTokenException();
		}

		current.setRevoked(true);
		tokenStateService.removeRefreshToken(current.getToken());
		return issueTokenPair(current.getUser());
	}

	@Transactional
	public void logout(LogoutRequest request, String authorizationHeader) {
		refreshTokenRepository.findByToken(request.refreshToken()).ifPresent(refreshToken -> {
			refreshToken.setRevoked(true);
			tokenStateService.removeRefreshToken(refreshToken.getToken());
		});

		String token = extractBearerToken(authorizationHeader);
		if (StringUtils.hasText(token)) {
			try {
				AccessTokenInfo info = jwtService.parse(token);
				Duration ttl = Duration.between(Instant.now(), info.expiresAt());
				tokenStateService.blacklistAccessToken(info.jwtId(), ttl);
			} catch (JwtException | IllegalArgumentException ex) {
				throw new InvalidAccessTokenException();
			}
		}
	}

	private TokenResponse issueTokenPair(User user) {
		String accessToken = jwtService.issueAccessToken(user);
		String refreshTokenValue = UUID.randomUUID().toString();
		Instant expiresAt = Instant.now().plus(jwtService.refreshTokenTtl());
		RefreshToken refreshToken = new RefreshToken(refreshTokenValue, user, expiresAt);
		refreshTokenRepository.save(refreshToken);
		tokenStateService.storeRefreshToken(refreshTokenValue, user.getId().toString(), jwtService.refreshTokenTtl());
		return new TokenResponse(accessToken, refreshTokenValue, jwtService.accessTokenTtl().toSeconds());
	}

	private String extractBearerToken(String authorizationHeader) {
		if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
			return null;
		}
		return authorizationHeader.substring("Bearer ".length());
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}
}
