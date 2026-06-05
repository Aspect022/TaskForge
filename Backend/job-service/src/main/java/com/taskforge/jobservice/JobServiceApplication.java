package com.taskforge.jobservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.taskforge.jobservice.config.JobProperties;

@SpringBootApplication
@EnableConfigurationProperties(JobProperties.class)
public class JobServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(JobServiceApplication.class, args);
	}
}
