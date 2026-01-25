package com.shipmonk.testingday.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when input validation fails
 * Returns HTTP 400 Bad Request
 */
public class InvalidInputException extends ExchangeRatesException {

    public InvalidInputException(String message, String errorDescription) {
        super(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message, errorDescription);
    }

    public InvalidInputException(String message, String errorDescription, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message, errorDescription, cause);
    }
}
