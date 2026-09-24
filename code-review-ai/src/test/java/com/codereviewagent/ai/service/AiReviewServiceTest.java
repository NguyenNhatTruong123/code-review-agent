package com.codereviewagent.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

class AiReviewServiceTest {
    @Test
    void unavailableProviderCannotReviewSemanticRule() {
        var service =
                new AiReviewService(
                        new ObjectMapper(),
                        HttpClient.newHttpClient(),
                        "https://openrouter.ai/api/v1",
                        "",
                        "unused");
        assertFalse(service.available());
        assertThrows(
                IllegalStateException.class,
                () ->
                        service.review(
                                "x.java",
                                "JAVA",
                                "class X {}",
                                List.of(new AiReviewService.RuleSpec("id", "Check naming"))));
    }

    @Test
    void emptyRulesNeedNoProvider() throws Exception {
        var service =
                new AiReviewService(
                        new ObjectMapper(),
                        HttpClient.newHttpClient(),
                        "https://openrouter.ai/api/v1",
                        "",
                        "unused");
        assertEquals(List.of(), service.review("x.java", "JAVA", "class X {}", List.of()));
    }

    @Test
    void parsesStructuredProviderFindings() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        ObjectMapper mapper = new ObjectMapper();
        String content =
                mapper.writeValueAsString(
                        Map.of(
                                "findings",
                                List.of(
                                        Map.of(
                                                "ruleId",
                                                "r1",
                                                "title",
                                                "Issue",
                                                "explanation",
                                                "Why",
                                                "evidence",
                                                "TODO",
                                                "suggestedFix",
                                                "Fix",
                                                "lineStart",
                                                1,
                                                "lineEnd",
                                                1))));
        when(response.body())
                .thenReturn(
                        mapper.writeValueAsString(
                                Map.of(
                                        "choices",
                                        List.of(Map.of("message", Map.of("content", content))))));
        doAnswer(inv -> response).when(client).send(any(), any());
        var service =
                new AiReviewService(
                        mapper,
                        client,
                        "https://openrouter.ai/api/v1",
                        "test-key",
                        "test-model");
        var results =
                service.review(
                        "x.java",
                        "JAVA",
                        "// TODO",
                        List.of(new AiReviewService.RuleSpec("r1", "Flag TODO")));
        assertEquals(1, results.size());
        assertEquals("r1", results.get(0).ruleId());
        assertEquals(1, results.get(0).lineStart());
    }

    @Test
    void rejectsProviderFailureAndMalformedOutput() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(503);
        doAnswer(inv -> response).when(client).send(any(), any());
        var service =
                new AiReviewService(
                        new ObjectMapper(),
                        client,
                        "https://openrouter.ai/api/v1",
                        "test-key",
                        "test-model");
        assertThrows(
                java.io.IOException.class,
                () ->
                        service.review(
                                "x.java",
                                "JAVA",
                                "class X {}",
                                List.of(new AiReviewService.RuleSpec("r1", "Check"))));
    }
}
