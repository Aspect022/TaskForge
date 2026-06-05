package com.taskforge.jobservice.exception;

public class PayloadTooLargeException extends RuntimeException {

	public PayloadTooLargeException(int actualBytes, int maxBytes) {
		super("Job payload is " + actualBytes + " bytes, max allowed is " + maxBytes + " bytes");
	}
}
