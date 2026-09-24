package com.codereviewagent.api.controller;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ApiErrorHandlerTest {
    private final ApiErrorHandler handler = new ApiErrorHandler();
    @Test void exposesSafeBusinessMessage() {
        var result = handler.business(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid ref"));
        assertEquals(400, result.getStatusCode().value());
        assertEquals("Invalid ref", result.getBody().get("message"));
        assertNotNull(result.getBody().get("correlationId"));
    }
    @Test void hidesUnexpectedExceptionDetails() {
        var result = handler.unexpected(new IllegalStateException("database password 123"));
        assertEquals(500, result.getStatusCode().value());
        assertFalse(result.getBody().get("message").toString().contains("database password"));
    }
}
