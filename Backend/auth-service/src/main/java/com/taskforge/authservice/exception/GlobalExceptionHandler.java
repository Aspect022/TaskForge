package com.taskforge.authservice.exception;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(DuplicateEmailException.class)
	ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
		return problem(HttpStatus.CONFLICT, "Email already exists", ex.getMessage());
	}

	@ExceptionHandler({InvalidCredentialsException.class, InvalidRefreshTokenException.class, InvalidAccessTokenException.class})
	ProblemDetail handleUnauthorized(RuntimeException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
	ProblemDetail handleValidation(Exception ex) {
		return problem(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage());
	}

	private ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create("https://taskforge.local/problems/" + title.toLowerCase().replace(' ', '-')));
		return problem;
	}
}
