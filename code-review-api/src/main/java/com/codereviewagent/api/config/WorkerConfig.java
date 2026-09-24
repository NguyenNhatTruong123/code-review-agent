package com.codereviewagent.api.config;

import com.codereviewagent.ai.service.StaticReviewService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Creates the bounded review executor and external review service clients. */
@Configuration
@EnableAsync
public class WorkerConfig {
    @Bean(name = "reviewExecutor")
    ThreadPoolTaskExecutor reviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("review-");
        executor.initialize();
        return executor;
    }

    @Bean
    StaticReviewService staticReviewService() {
        return new StaticReviewService();
    }
}
