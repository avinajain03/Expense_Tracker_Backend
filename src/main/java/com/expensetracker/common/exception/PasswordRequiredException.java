package com.expensetracker.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a PDF bank statement is password-protected.
 * Carries an errorCode to distinguish between "no password given" and "wrong password".
 */
public class PasswordRequiredException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    /**
     * @param errorCode  PASSWORD_REQUIRED or INVALID_PASSWORD
     * @param message    human-readable error message
     */
    public PasswordRequiredException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = HttpStatus.UNPROCESSABLE_ENTITY; // 422
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
