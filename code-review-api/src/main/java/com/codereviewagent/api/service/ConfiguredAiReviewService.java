package com.codereviewagent.api.service;

import com.codereviewagent.ai.service.AiReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.Duration;

/** Shared OpenRouter-compatible AI provider used by review and suggestion workflows. */
@Service
public class ConfiguredAiReviewService extends AiReviewService {
    private static final Logger LOG = LoggerFactory.getLogger(ConfiguredAiReviewService.class);
    private static final String DEFAULT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openrouter/free";

    /**
     * Creates the provider client from server-side runtime configuration.
     *
     * @param mapper JSON serializer used for provider requests and responses
     * @param environment runtime properties and environment variables for provider configuration
     */
    public ConfiguredAiReviewService(ObjectMapper mapper, Environment environment) {
        super(
                mapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                firstPresent(
                        environment.getProperty("OPENROUTER_BASE_URL"),
                        environment.getProperty("spring.ai.openai.base-url"),
                        DEFAULT_ENDPOINT),
                firstPresent(
                        environment.getProperty("OPENROUTER_API_KEY"),
                        environment.getProperty("spring.ai.openai.api-key"),
                        ""),
                firstPresent(
                        environment.getProperty("OPENROUTER_MODEL"),
                        environment.getProperty("spring.ai.openai.chat.options.model"),
                        DEFAULT_MODEL));
    }

    /** Logs provider readiness without exposing credentials or submitted source. */
    @PostConstruct
    void logReadiness() {
        LOG.info(
                "AI provider readiness: provider={} model={} credentialPresent={}",
                provider(),
                model(),
                available());
    }

    private static String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
