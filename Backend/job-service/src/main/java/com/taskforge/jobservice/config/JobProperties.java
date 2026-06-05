package com.taskforge.jobservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "taskforge.jobs")
public record JobProperties(
		int payloadMaxBytes,
		int defaultLimit,
		int maxLimit
) {
	public JobProperties {
		if (payloadMaxBytes <= 0) {
			payloadMaxBytes = 1_048_576;
		}
		if (defaultLimit <= 0) {
			defaultLimit = 20;
		}
		if (maxLimit <= 0) {
			maxLimit = 100;
		}
	}
}
