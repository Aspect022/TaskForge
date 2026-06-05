package com.taskforge.jobservice.controller;

import com.taskforge.jobservice.exception.MissingUserContextException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class UserContext {

	public UUID requireUserId(String userIdHeader) {
		if (!StringUtils.hasText(userIdHeader)) {
			throw new MissingUserContextException();
		}
		return UUID.fromString(userIdHeader);
	}

	public String correlationId(String correlationIdHeader) {
		return StringUtils.hasText(correlationIdHeader) ? correlationIdHeader : UUID.randomUUID().toString();
	}

	public String authorizationHeaderName() {
		return HttpHeaders.AUTHORIZATION;
	}
}
