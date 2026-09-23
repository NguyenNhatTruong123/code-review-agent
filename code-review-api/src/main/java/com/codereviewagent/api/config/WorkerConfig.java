package com.codereviewagent.api.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.codereviewagent.ai.service.AiReviewService;
import com.codereviewagent.ai.service.StaticReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableAsync
public class WorkerConfig {
    @Bean(name = "reviewExecutor") ThreadPoolTaskExecutor reviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); executor.setMaxPoolSize(4); executor.setQueueCapacity(30);
        executor.setThreadNamePrefix("review-"); executor.initialize();
        return executor;
    }
    @Bean AiReviewService aiReviewService(ObjectMapper mapper, @Value("${app.openai-key:}") String key,
                                          @Value("${app.openai-model}") String model) {
        return new AiReviewService(mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), key, model);
    }
    @Bean StaticReviewService staticReviewService() { return new StaticReviewService(); }
}
