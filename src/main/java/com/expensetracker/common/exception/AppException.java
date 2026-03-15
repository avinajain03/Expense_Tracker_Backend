package com.expensetracker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Generic application exception that carries an HTTP status code.
 * Throw this to produce a specific HTTP response from the GlobalExceptionHandler.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;

    public AppException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
