package com.shipmonk.testingday.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Global exception handler for the application
 * Catches exceptions and returns standardized error responses
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle custom ExchangeRatesException
     */
    @ExceptionHandler(ExchangeRatesException.class)
    public ResponseEntity<ErrorResponse> handleExchangeRatesException(
            ExchangeRatesException ex,
            WebRequest request) {

        logger.error("Exchange rates exception: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);

        ErrorResponse errorResponse = new ErrorResponse(
            ex.getHttpStatusCode(),
            ex.getHttpStatus().getReasonPhrase(),
            ex.getErrorCode(),
            ex.getMessage(),
            ex.getErrorDescription(),
            request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, ex.getHttpStatus());
    }

    /**
     * Handle InvalidInputException (specific case of ExchangeRatesException)
     */
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInputException(
            InvalidInputException ex,
            WebRequest request) {

        logger.warn("Invalid input: {} - {}", ex.getErrorCode(), ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            ex.getHttpStatusCode(),
            ex.getHttpStatus().getReasonPhrase(),
            ex.getErrorCode(),
            ex.getMessage(),
            ex.getErrorDescription(),
            request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, ex.getHttpStatus());
    }

    /**
     * Handle IllegalArgumentException (fallback for validation errors)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            WebRequest request) {

        logger.warn("Illegal argument: {}", ex.getMessage());

        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "INVALID_ARGUMENT",
            ex.getMessage(),
            "The provided argument is invalid or malformed",
            request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle MissingServletRequestParameterException
     * Thrown when a required @RequestParam is missing (e.g., access_key)
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            WebRequest request) {

        logger.warn("Missing required parameter: {} of type {}", ex.getParameterName(), ex.getParameterType());

        String parameterName = ex.getParameterName();
        String message;
        String description;

        // Customize message based on parameter name
        if ("access_key".equals(parameterName)) {
            message = "API key is required";
            description = "The 'access_key' query parameter is required. Please provide a valid Fixer.io API key. " +
                         "Example: /api/v1/rates/2024-01-15?access_key=YOUR_API_KEY";
        } else {
            message = "Required parameter '" + parameterName + "' is missing";
            description = String.format("The required query parameter '%s' of type '%s' is not present in the request.",
                                       parameterName, ex.getParameterType());
        }

        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "MISSING_PARAMETER",
            message,
            description,
            request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle MethodArgumentTypeMismatchException
     * Thrown when a request parameter cannot be converted to the expected type
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            WebRequest request) {

        logger.warn("Type mismatch for parameter: {} - expected type: {}, provided value: {}",
                   ex.getName(), ex.getRequiredType(), ex.getValue());

        String message = String.format("Invalid value for parameter '%s'", ex.getName());
        String description = String.format(
            "The parameter '%s' expects a value of type '%s', but received '%s'",
            ex.getName(),
            ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown",
            ex.getValue()
        );

        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.BAD_REQUEST.value(),
            HttpStatus.BAD_REQUEST.getReasonPhrase(),
            "INVALID_PARAMETER_TYPE",
            message,
            description,
            request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle generic exceptions (catch-all)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            WebRequest request) {

        logger.error("Unexpected error occurred", ex);

        ErrorResponse errorResponse = new ErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            ex.getMessage(),
            request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
