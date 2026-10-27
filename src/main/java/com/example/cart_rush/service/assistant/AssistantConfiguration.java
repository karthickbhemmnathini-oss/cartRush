package com.example.cart_rush.service.assistant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
public class AssistantConfiguration {

	@Bean(name = "assistantStreamExecutor")
	Executor assistantStreamExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(32);
		executor.setThreadNamePrefix("assistant-stream-");
		executor.initialize();
		return executor;
	}
}