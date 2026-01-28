package com.shipmonk.testingday.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipmonk.testingday.external.FixerErrorResponse;
import com.shipmonk.testingday.external.FixerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static com.shipmonk.testingday.validators.ApiKeyValidator.*;
import static com.shipmonk.testingday.validators.DateValidator.validateDateFormat;

@Service
public class ExchangeRatesApiService {

    // Error code constants
    private static final int ERROR_CODE_RATE_LIMIT = 104;
    private static final int ERROR_CODE_BASE_CURRENCY_RESTRICTED = 105;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    // Retry configuration
    public static final int MAX_RETRIES = 3;
    public static final int RETRY_DELAY_MS = 5000; // 5 seconds

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRatesApiService.class);

    // Base URL - non-static and non-final to allow modification in tests
    private String fixerBaseUrl = "https://data.fixer.io/api";

    private final Set<String> freeModeSet;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${fixer.api.free-key:a0fbcdca0a84fbc406fab744066bb166}")
    private String defaultFreeApiKey;

    /**
     * Constructor with dependency injection
     *
     * @param restTemplate Injected RestTemplate for HTTP calls
     * @param objectMapper Injected ObjectMapper for JSON parsing
     */
    public ExchangeRatesApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
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
     * Fetches exchange rates from Fixer.io API asynchronously
     *
     * @param date         The date for which to fetch rates (format: YYYY-MM-DD)
     * @param baseCurrency The base currency (e.g., "USD")
     * @param symbols      Comma-separated list of currency codes
     * @param apiKey       Fixer.io API key
     * @return CompletableFuture containing the FixerResponse
     */
    @Async("taskExecutor")
    public CompletableFuture<FixerResponse> fetchExchangeRatesAsync(String date,
                                                                    String baseCurrency,
                                                                    String symbols,
                                                                    String apiKey) {
        logger.info("Fetching exchange rates for date: {} and base currency: {} on thread: {}",
            date, baseCurrency, Thread.currentThread().getName());

        validateDateFormat(date);

        validateInputs(apiKey);

        if (freeModeSet.contains(apiKey)) {
            return fetchExchangeRatesFreePlanWithRetry(date, apiKey, 0);
        }

        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(fixerBaseUrl + "/" + date)
                .queryParam("access_key", apiKey)
                .queryParam("base", baseCurrency)
                .queryParam("symbols", symbols)
                .toUriString();

            logger.debug("Making request to Fixer.io: {}", url.replace(apiKey, "***"));

            return execHttpCall(date, url);

        } catch (HttpClientErrorException httpEx) {
            // Parse the error response from Fixer.io
            logger.error("HTTP error fetching exchange rates from Fixer.io for date: {}, Status: {}",
                date, httpEx.getStatusCode());

            return parseError(date, apiKey, 0, httpEx); // FIX: Return the result

        } catch (Exception e) {
            logger.error("Error fetching exchange rates from Fixer.io for date: {}", date, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Fetches exchange rates using the free plan API (no base currency parameter)
     * Includes retry logic for rate limiting
     *
     * @param date       The date for which to fetch rates (format: YYYY-MM-DD)
     * @param apiKey     Fixer.io API key
     * @param retryCount Current retry attempt number
     * @return CompletableFuture containing the FixerResponse
     */
    @Async("taskExecutor")
    public CompletableFuture<FixerResponse> fetchExchangeRatesFreePlanWithRetry(String date, String apiKey, int retryCount) {

        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(fixerBaseUrl + "/" + date)
                .queryParam("access_key", apiKey)
                .toUriString();

            logger.debug("Making request to Fixer.io (attempt {}/{}): {}",
                retryCount + 1, MAX_RETRIES + 1, url.replace(apiKey, "***"));

            return execHttpCall(date, url);

        } catch (HttpClientErrorException httpEx) {
            logger.error("HTTP error in free plan mode for date: {}, Status: {}", date, httpEx.getStatusCode());

            return parseError(date, apiKey, retryCount, httpEx);

        } catch (Exception e) {
            logger.error("Error fetching exchange rates from Fixer.io for date: {}", date, e);

            // Retry on generic exceptions if we haven't exceeded max retries
            if (retryCount < MAX_RETRIES) {
                logger.warn("Retrying due to error... (attempt {}/{})", retryCount + 1, MAX_RETRIES);

                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    logger.error("Retry wait interrupted", ie);
                    Thread.currentThread().interrupt();
                    return CompletableFuture.failedFuture(ie);
                }

                return fetchExchangeRatesFreePlanWithRetry(date, apiKey, retryCount + 1);
            }

            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Parses error response from Fixer.io and handles retry logic
     *
     * @param date       The date being requested
     * @param apiKey     The API key being used
     * @param retryCount Current retry attempt number
     * @param httpEx     The HTTP client error exception
     * @return CompletableFuture with error or retry result
     */
    private CompletableFuture<FixerResponse> parseError(String date,
                                                        String apiKey,
                                                        int retryCount,
                                                        HttpClientErrorException httpEx) {
        try {
            String errorBody = httpEx.getResponseBodyAsString();
            FixerErrorResponse errorResponse = objectMapper.readValue(errorBody, FixerErrorResponse.class);
            HttpStatus statusCode = httpEx.getStatusCode();

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

            if (errorResponse.getError() != null) {
                FixerErrorResponse.ErrorDetail errorDetail = errorResponse.getError();
                logger.error("Fixer.io API Error - Code: {}, Type: {}, Info: {}",
                    errorDetail.getCode(),
                    errorDetail.getType(),
                    errorDetail.getInfo());
                int errorCode = errorDetail.getCode();

                // Retry on rate limiting (error code 104) or too many requests
                if ((errorCode == ERROR_CODE_RATE_LIMIT || httpEx.getStatusCode().value() == HTTP_TOO_MANY_REQUESTS)
                    && retryCount < MAX_RETRIES) {
                    logger.warn("Rate limit reached. Retrying in {} seconds... (attempt {}/{})",
                        RETRY_DELAY_MS / 1000, retryCount + 1, MAX_RETRIES);

                    try {
                        Thread.sleep(RETRY_DELAY_MS);
                    } catch (InterruptedException ie) {
                        logger.error("Retry wait interrupted", ie);
                        Thread.currentThread().interrupt();
                        return CompletableFuture.failedFuture(ie);
                    }

                    return fetchExchangeRatesFreePlanWithRetry(date, apiKey, retryCount + 1);
                }

                String errorMessage = String.format(
                    "Fixer.io API error [%d]: %s - %s",
                    errorDetail.getCode(),
                    errorDetail.getType(),
                    errorDetail.getInfo() != null ? errorDetail.getInfo() : "No additional info"
                );

                if (errorDetail.getCode() == ERROR_CODE_BASE_CURRENCY_RESTRICTED) {
                    errorMessage += " - This feature is not supported on your current subscription plan.";
                    logger.error(errorMessage);
                    logger.warn("Switching to free mode for API key due to subscription limitations.");
                    freeModeSet.add(apiKey);

                    logger.info("Retrying with free plan after {} seconds (respecting API rate limit)...",
                        RETRY_DELAY_MS / 1000);
                    try {
                        Thread.sleep(RETRY_DELAY_MS); // Use constant instead of hardcoded 5000
                    } catch (InterruptedException ie) {
                        logger.error("Wait interrupted", ie);
                        Thread.currentThread().interrupt();
                        return CompletableFuture.failedFuture(ie);
                    }

                    return fetchExchangeRatesFreePlanWithRetry(date, apiKey, 0);
                }
            }
        } catch (Exception parseEx) {
            logger.error("Failed to parse free plan error response", parseEx);
        }

        return CompletableFuture.failedFuture(httpEx);
    }

    /**
     * Executes HTTP call to Fixer.io API and wraps the response in CompletableFuture
     *
     * @param date The date being requested
     * @param url  The complete URL with all query parameters
     * @return CompletableFuture containing the FixerResponse
     */
    private CompletableFuture<FixerResponse> execHttpCall(String date, String url) {

        FixerResponse response = restTemplate.getForObject(url, FixerResponse.class);

        if (response != null && response.isSuccess()) {
            logger.info("Successfully fetched exchange rates for date: {}", date);
            return CompletableFuture.completedFuture(response);
        } else {
            logger.error("Fixer.io API returned unsuccessful response for date: {}", date);
            return CompletableFuture.failedFuture(
                new RuntimeException("Failed to fetch exchange rates from Fixer.io")
            );
        }
    }
}
