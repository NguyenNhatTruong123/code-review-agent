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

/** Converts expected and unexpected failures to a stable response with a correlation ID. */
@RestControllerAdvice
public class ApiErrorHandler {
    /** Preserves the status and safe reason for expected business failures.
     * @param error expected business exception
     * @return normalized HTTP error response with correlation ID
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> business(ResponseStatusException error) {
        return response(HttpStatus.valueOf(error.getStatusCode().value()), error.getReason());
    }
    /** Maps bean-validation failures to the common client error shape.
     * @param error validation exception raised by request binding
     * @return HTTP 400 response without internal validation details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException error) {
        return response(HttpStatus.BAD_REQUEST, "Request validation failed");
    }
    /** Maps malformed request syntax and parameter types to HTTP 400.
     * @param error malformed-request exception
     * @return normalized HTTP 400 response
     */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> malformed(Exception error) {
        return response(HttpStatus.BAD_REQUEST, "Request format is invalid");
    }
    /** Hides authentication details while returning HTTP 401.
     * @param error authentication failure
     * @return normalized HTTP 401 response
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> login(BadCredentialsException error) {
        return response(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
    /** Returns a generic message so unexpected exception details are not exposed.
     * @param error unexpected server exception
     * @return normalized HTTP 500 response
     */
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
