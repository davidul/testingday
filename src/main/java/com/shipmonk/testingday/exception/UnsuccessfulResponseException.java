package com.shipmonk.testingday.exception;

/**
 * Exception thrown when Fixer.io API returns a response with success=false
 * This exception is retryable as it may be a transient issue
 */
public class UnsuccessfulResponseException extends RuntimeException {

    public UnsuccessfulResponseException(String message) {
        super(message);
    }

    public UnsuccessfulResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
