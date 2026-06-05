package com.taskforge.authservice.exception;

public class InvalidAccessTokenException extends RuntimeException {

	public InvalidAccessTokenException() {
		super("Access token is invalid or expired");
	}
}
