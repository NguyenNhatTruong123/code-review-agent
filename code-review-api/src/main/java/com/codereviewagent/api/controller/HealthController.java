package com.codereviewagent.api.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Provides the unauthenticated liveness response used by deployment checks. */
@RestController
@RequestMapping("/api")
public class HealthController {

    /** Returns a stable liveness payload without requiring authentication.
     * @return map containing the {@code UP} health status
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
