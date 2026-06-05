package com.taskforge.jobservice.exception;

public class InvalidJobTransitionException extends RuntimeException {

	public InvalidJobTransitionException(String message) {
		super(message);
	}
}
