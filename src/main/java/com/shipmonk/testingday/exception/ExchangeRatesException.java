package com.shipmonk.testingday.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for the exchange rates application
 * Contains error details including HTTP status code, error code, and message
 */
public class ExchangeRatesException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;
    private final String errorDescription;

    public ExchangeRatesException(HttpStatus httpStatus, String errorCode, String message, String errorDescription) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public ExchangeRatesException(HttpStatus httpStatus, String errorCode, String message, String errorDescription, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.errorDescription = errorDescription;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public int getHttpStatusCode() {
        return httpStatus.value();
    }
}
