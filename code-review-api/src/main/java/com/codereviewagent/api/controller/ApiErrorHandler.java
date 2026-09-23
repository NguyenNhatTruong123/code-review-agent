package com.codereviewagent.api.controller;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> business(ResponseStatusException error) {
        return response(HttpStatus.valueOf(error.getStatusCode().value()), error.getReason());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException error) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed");
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> malformed(Exception error) {
        return response(HttpStatus.BAD_REQUEST, "Request format is invalid");
    }
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> login(BadCredentialsException error) {
        return response(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> unexpected(Exception error) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }
    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.<String, Object>of("code", status.name(),
                "message", message == null ? status.getReasonPhrase() : message,
                "correlationId", UUID.randomUUID().toString()));
    }
}
