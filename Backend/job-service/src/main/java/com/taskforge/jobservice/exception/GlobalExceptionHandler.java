package com.taskforge.jobservice.exception;

import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(JobNotFoundException.class)
	ProblemDetail handleNotFound(JobNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Job not found", ex.getMessage());
	}

	@ExceptionHandler(InvalidJobTransitionException.class)
	ProblemDetail handleInvalidTransition(InvalidJobTransitionException ex) {
		return problem(HttpStatus.CONFLICT, "Invalid job transition", ex.getMessage());
	}

	@ExceptionHandler(PayloadTooLargeException.class)
	ProblemDetail handlePayloadTooLarge(PayloadTooLargeException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Payload too large", ex.getMessage());
	}

	@ExceptionHandler(MissingUserContextException.class)
	ProblemDetail handleMissingUser(MissingUserContextException ex) {
		return problem(HttpStatus.UNAUTHORIZED, "Missing user context", ex.getMessage());
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
		if ("X-User-Id".equals(ex.getHeaderName())) {
			return problem(HttpStatus.UNAUTHORIZED, "Missing user context", "Missing X-User-Id request header");
		}
		return problem(HttpStatus.BAD_REQUEST, "Validation failed", ex.getMessage());
	}

	@ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
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
