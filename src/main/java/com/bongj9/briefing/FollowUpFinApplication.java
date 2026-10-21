package com.bongj9.briefing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FollowUpFinApplication {

	public static void main(String[] args) {
		SpringApplication.run(FollowUpFinApplication.class, args);
	}

}
