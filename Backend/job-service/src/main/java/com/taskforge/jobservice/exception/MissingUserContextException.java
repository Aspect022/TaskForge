package com.taskforge.jobservice.exception;

public class MissingUserContextException extends RuntimeException {

	public MissingUserContextException() {
		super("Missing X-User-Id request header");
	}
}
