package com.shipmonk.testingday.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipmonk.testingday.exception.InvalidInputException;
import com.shipmonk.testingday.external.FixerErrorResponse;
import com.shipmonk.testingday.external.FixerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.time.LocalDate.of;

/**
 * Service for fetching exchange rates from Fixer.io API using WebClient (reactive approach)
 * This service provides an alternative to ExchangeRatesApiService using Spring WebFlux
 */
@Service
public class ExchangeRatesWebClientService {

    // Error code constants
    private static final int ERROR_CODE_RATE_LIMIT = 104;
    private static final int ERROR_CODE_BASE_CURRENCY_RESTRICTED = 105;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    // Retry configuration
    public static final int MAX_RETRIES = 3;
    public static final int RETRY_DELAY_SECONDS = 5;

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRatesWebClientService.class);

    // Base URL - non-static and non-final to allow modification in tests
    private String fixerBaseUrl = "https://data.fixer.io/api";

    private final Set<String> freeModeSet;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${fixer.api.free-key:a0fbcdca0a84fbc406fab744066bb166}")
    private String defaultFreeApiKey;

    /**
     * Constructor with dependency injection
     *
     * @param webClient    Injected WebClient for HTTP calls
     * @param objectMapper Injected ObjectMapper for JSON parsing
     */
    public ExchangeRatesWebClientService(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.freeModeSet = ConcurrentHashMap.newKeySet();
    }

    /**
     * Initialize the free mode set after properties are set
     */
    @javax.annotation.PostConstruct
    private void init() {
        if (defaultFreeApiKey != null && !defaultFreeApiKey.isEmpty()) {
            freeModeSet.add(defaultFreeApiKey);
            logger.debug("Initialized free mode set with default API key");
        }
    }

    /**
     * Fetches exchange rates from Fixer.io API reactively
     *
     * @param date         The date for which to fetch rates (format: YYYY-MM-DD)
     * @param baseCurrency The base currency (e.g., "USD")
     * @param symbols      Comma-separated list of currency codes
     * @param apiKey       Fixer.io API key
     * @return Mono containing the FixerResponse
     */
    public Mono<FixerResponse> fetchExchangeRates(String date,
                                                   String baseCurrency,
                                                   String symbols,
                                                   String apiKey) {
        logger.info("Fetching exchange rates for date: {} and base currency: {} on thread: {}",
            date, baseCurrency, Thread.currentThread().getName());

        // Validate inputs
        validateInputs(date, apiKey);

        if (freeModeSet.contains(apiKey)) {
            return fetchExchangeRatesFreePlan(date, apiKey);
        }

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                .scheme("https")
                .host("data.fixer.io")
                .path("/api/{date}")
                .queryParam("access_key", apiKey)
                .queryParam("base", baseCurrency)
                .queryParam("symbols", symbols)
                .build(date))
            .retrieve()
            .bodyToMono(FixerResponse.class)
            .doOnSuccess(response -> logger.info("Successfully fetched exchange rates for date: {}", date))
            .doOnError(error -> logger.error("Error fetching exchange rates for date: {}", date, error))
            .onErrorResume(WebClientResponseException.class, this::handleWebClientError);
    }

    /**
     * Fetches exchange rates using the free plan API (no base currency parameter)
     * Includes automatic retry logic for rate limiting
     *
     * @param date   The date for which to fetch rates (format: YYYY-MM-DD)
     * @param apiKey Fixer.io API key
     * @return Mono containing the FixerResponse
     */
    public Mono<FixerResponse> fetchExchangeRatesFreePlan(String date, String apiKey) {
        logger.debug("Fetching exchange rates in free plan mode for date: {}", date);

        return webClient
            .get()
            .uri(uriBuilder -> uriBuilder
                .scheme("https")
                .host("data.fixer.io")
                .path("/api/{date}")
                .queryParam("access_key", apiKey)
                .build(date))
            .retrieve()
            .bodyToMono(FixerResponse.class)
            .retryWhen(Retry.fixedDelay(MAX_RETRIES, Duration.ofSeconds(RETRY_DELAY_SECONDS))
                .filter(this::shouldRetry)
                .doBeforeRetry(retrySignal ->
                    logger.warn("Rate limit reached. Retrying in {} seconds... (attempt {}/{})",
                        RETRY_DELAY_SECONDS, retrySignal.totalRetries() + 1, MAX_RETRIES)))
            .doOnSuccess(response -> logger.info("Successfully fetched exchange rates for date: {} in free plan mode", date))
            .doOnError(error -> logger.error("Error fetching exchange rates for date: {} in free plan mode", date, error))
            .onErrorResume(WebClientResponseException.class, this::handleWebClientError);
    }

    /**
     * Determines if the error should trigger a retry
     *
     * @param throwable The error that occurred
     * @return true if retry should be attempted
     */
    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;

            // Retry on 429 Too Many Requests
            if (ex.getStatusCode().value() == HTTP_TOO_MANY_REQUESTS) {
                return true;
            }

            // Try to parse error response to check for rate limit error code
            try {
                String errorBody = ex.getResponseBodyAsString();
                FixerErrorResponse errorResponse = objectMapper.readValue(errorBody, FixerErrorResponse.class);
                if (errorResponse.getError() != null
                    && errorResponse.getError().getCode() == ERROR_CODE_RATE_LIMIT) {
                    return true;
                }
            } catch (Exception e) {
                logger.debug("Could not parse error response for retry decision", e);
            }
        }

        return false;
    }

    /**
     * Handles WebClient errors and provides detailed logging
     *
     * @param ex The WebClientResponseException
     * @return Mono error with appropriate message
     */
    private Mono<FixerResponse> handleWebClientError(WebClientResponseException ex) {
        HttpStatus statusCode = ex.getStatusCode();

        switch (statusCode) {
            case BAD_REQUEST:
                logger.error("Received 400 Bad Request from Fixer.io");
                break;
            case UNAUTHORIZED:
                logger.error("No API key provided or invalid API key - Received 401 Unauthorized from Fixer.io");
                break;
            case FORBIDDEN:
                logger.error("Current Subscription Plan does not support this endpoint - Received 403 Forbidden from Fixer.io");
                break;
            case NOT_FOUND:
                logger.error("Requested resource not found - Received 404 Not Found from Fixer.io");
                break;
            case TOO_MANY_REQUESTS:
                logger.error("Rate limit exceeded - Received 429 Too Many Requests from Fixer.io");
                break;
            default:
                logger.error("Received HTTP {} from Fixer.io", statusCode.value());
        }

        try {
            String errorBody = ex.getResponseBodyAsString();
            FixerErrorResponse errorResponse = objectMapper.readValue(errorBody, FixerErrorResponse.class);

            if (errorResponse.getError() != null) {
                FixerErrorResponse.ErrorDetail errorDetail = errorResponse.getError();
                logger.error("Fixer.io API Error - Code: {}, Type: {}, Info: {}",
                    errorDetail.getCode(),
                    errorDetail.getType(),
                    errorDetail.getInfo());

                String errorMessage = String.format(
                    "Fixer.io API error [%d]: %s - %s",
                    errorDetail.getCode(),
                    errorDetail.getType(),
                    errorDetail.getInfo()
                );

                return Mono.error(new RuntimeException(errorMessage, ex));
            }
        } catch (Exception parseEx) {
            logger.error("Failed to parse Fixer.io error response", parseEx);
        }

        return Mono.error(ex);
    }

    /**
     * Validates input parameters
     *
     * @param date   The date string to validate
     * @param apiKey The API key to validate
     * @throws InvalidInputException if inputs are invalid
     */
    private void validateInputs(String date, String apiKey) {
        // Validate date is not null or empty
        if (date == null || date.trim().isEmpty()) {
            throw new InvalidInputException(
                "Date parameter is required",
                "The 'date' parameter cannot be null or empty. Please provide a date in YYYY-MM-DD format."
            );
        }

        // Validate date format
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new InvalidInputException(
                "Invalid date format: " + date,
                String.format("The date '%s' does not match the required format YYYY-MM-DD. Example: 2024-01-15", date)
            );
        }

        // Validate that the date is actually valid (e.g., not 2024-13-01 or 2024-01-32)
        try {
            String[] parts = date.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            // Check month is valid (1-12)
            if (month < 1 || month > 12) {
                throw new InvalidInputException(
                    "Invalid month: " + month,
                    String.format("The month value '%d' in date '%s' is invalid. Month must be between 1 and 12.", month, date)
                );
            }

            // Check day is valid for the given month and year
            of(year, month, day); // This validates the actual date

        } catch (java.time.DateTimeException e) {
            throw new InvalidInputException(
                "Invalid date: " + date,
                String.format("The date '%s' is not valid. %s", date, e.getMessage()),
                e
            );
        } catch (NumberFormatException e) {
            throw new InvalidInputException(
                "Invalid date format: " + date,
                String.format("The date '%s' contains non-numeric values. Expected format: YYYY-MM-DD with numeric values.", date),
                e
            );
        }

        // Validate API key
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new InvalidInputException(
                "API key is required",
                "The 'apiKey' parameter cannot be null or empty. Please provide a valid Fixer.io API key."
            );
        }
    }

    /**
     * Sets the base URL for Fixer.io API (for testing purposes)
     *
     * @param fixerBaseUrl The base URL to use
     */
    public void setFixerBaseUrl(String fixerBaseUrl) {
        this.fixerBaseUrl = fixerBaseUrl;
    }
}
