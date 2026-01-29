package com.shipmonk.testingday.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipmonk.testingday.exception.UnsuccessfulResponseException;
import com.shipmonk.testingday.external.FixerErrorResponse;
import com.shipmonk.testingday.external.FixerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.Map;
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

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRatesApiService.class);

    // Base URL - non-static and non-final to allow modification in tests
    private String fixerBaseUrl = "https://data.fixer.io/api";

    private final Set<String> freeModeSet;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final RetryTemplate retryTemplate;

    @Value("${fixer.api.key}")
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
        this.retryTemplate = createRetryTemplate();
    }

    /**
     * Creates a RetryTemplate configured for retrying server errors and network issues
     *
     * @return Configured RetryTemplate
     */
    private RetryTemplate createRetryTemplate() {
        RetryTemplate template = new RetryTemplate();

        // Configure which exceptions to retry
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(HttpServerErrorException.class, true);
        retryableExceptions.put(ResourceAccessException.class, true);
        retryableExceptions.put(UnsuccessfulResponseException.class, true); // For unsuccessful responses
        // Don't retry generic RuntimeException - client errors throw RuntimeException and shouldn't be retried

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(4, retryableExceptions);
        template.setRetryPolicy(retryPolicy);

        // Configure exponential backoff
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500);  // 0.5 seconds for this demo very short
        backOffPolicy.setMultiplier(1.5);        // 1.5x multiplier
        backOffPolicy.setMaxInterval(15000);     // Max 15 seconds
        template.setBackOffPolicy(backOffPolicy);

        return template;
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
     * Fetches exchange rates from Fixer.io API asynchronously with automatic retry
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

        try {
            validateDateFormat(date);
            validateInputs(apiKey);

            // Use RetryTemplate to handle retries inside the async method
            FixerResponse response = retryTemplate.execute(context -> {
                logger.debug("Retry attempt {} for date: {}", context.getRetryCount(), date);

                // Check if we should use free plan mode
                if (freeModeSet.contains(apiKey)) {
                    return fetchExchangeRatesFreePlan(date, apiKey);
                }

                String url = UriComponentsBuilder
                    .fromHttpUrl(fixerBaseUrl + "/" + date)
                    .queryParam("access_key", apiKey)
                    .queryParam("base", baseCurrency)
                    .queryParam("symbols", symbols)
                    .toUriString();

                logger.debug("Making request to Fixer.io: {}", url.replace(apiKey, "***"));

                return executeHttpCall(date, url, apiKey);
            });

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            logger.error("Failed to fetch exchange rates for date: {} after all retries", date, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Fetches exchange rates using the free plan API (no base currency parameter) asynchronously with retry
     *
     * @param date   The date for which to fetch rates (format: YYYY-MM-DD)
     * @param apiKey Fixer.io API key
     * @param retryCount Kept for backward compatibility but not used (RetryTemplate handles retries)
     * @return CompletableFuture containing the FixerResponse
     */
    @Async("taskExecutor")
    public CompletableFuture<FixerResponse> fetchExchangeRatesFreePlanWithRetry(String date, String apiKey, int retryCount) {
        try {
            // Use RetryTemplate to handle retries
            FixerResponse response = retryTemplate.execute(context -> {
                logger.debug("Retry attempt {} for free plan date: {}", context.getRetryCount(), date);
                return fetchExchangeRatesFreePlan(date, apiKey);
            });

            return CompletableFuture.completedFuture(response);

        } catch (Exception e) {
            logger.error("Failed to fetch exchange rates (free plan) for date: {} after all retries", date, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Synchronous method to fetch exchange rates using free plan
     * Called by RetryTemplate for retry logic
     *
     * @param date   The date for which to fetch rates
     * @param apiKey Fixer.io API key
     * @return FixerResponse
     */
    private FixerResponse fetchExchangeRatesFreePlan(String date, String apiKey) {
        String url = UriComponentsBuilder
            .fromHttpUrl(fixerBaseUrl + "/" + date)
            .queryParam("access_key", apiKey)
            .toUriString();

        logger.debug("Making request to Fixer.io (free plan): {}", url.replace(apiKey, "***"));

        return executeHttpCall(date, url, apiKey);
    }

    /**
     * Executes HTTP call with error handling
     * Throws exceptions that RetryTemplate will catch and retry
     *
     * @param date   The date being requested
     * @param url    The complete URL with all query parameters
     * @param apiKey The API key being used
     * @return FixerResponse
     */
    private FixerResponse executeHttpCall(String date, String url, String apiKey) {
        try {
            FixerResponse response = restTemplate.getForObject(url, FixerResponse.class);

            if (response != null && response.isSuccess()) {
                logger.info("Successfully fetched exchange rates for date: {}", date);
                return response;
            } else {
                logger.error("Fixer.io API returned unsuccessful response for date: {}", date);
                throw new UnsuccessfulResponseException("Fixer.io API returned unsuccessful response");
            }

        } catch (HttpClientErrorException httpEx) {
            logger.error("HTTP client error for date: {}, Status: {}", date, httpEx.getStatusCode());
            return handleClientError(date, apiKey, httpEx);

        } catch (HttpServerErrorException httpServerEx) {
            logger.warn("HTTP server error for date: {}, will retry", date);
            throw httpServerEx; // Let RetryTemplate retry

        } catch (ResourceAccessException resourceEx) {
            logger.warn("Network error for date: {}, will retry", date);
            throw resourceEx; // Let RetryTemplate retry
        }
    }

    /**
     * Handles HTTP client errors (4xx) - these are not retried
     *
     * @param date   The date being requested
     * @param apiKey The API key being used
     * @param httpEx The HTTP client error exception
     * @return FixerResponse or throws exception
     */
    private FixerResponse handleClientError(String date, String apiKey, HttpClientErrorException httpEx) {
        try {
            String errorBody = httpEx.getResponseBodyAsString();
            FixerErrorResponse errorResponse = objectMapper.readValue(errorBody, FixerErrorResponse.class);
            HttpStatus statusCode = httpEx.getStatusCode();

            logHttpError(statusCode);

            if (errorResponse.getError() != null) {
                FixerErrorResponse.ErrorDetail errorDetail = errorResponse.getError();
                logger.error("Fixer.io API Error - Code: {}, Type: {}, Info: {}",
                    errorDetail.getCode(),
                    errorDetail.getType(),
                    errorDetail.getInfo());

                // Handle base currency restriction - switch to free mode
                if (errorDetail.getCode() == ERROR_CODE_BASE_CURRENCY_RESTRICTED) {
                    logger.warn("Subscription plan does not support base currency parameter. Switching to free mode.");
                    freeModeSet.add(apiKey);
                    return fetchExchangeRatesFreePlan(date, apiKey);
                }

                // For rate limit errors, throw exception to trigger retry
                if (errorDetail.getCode() == ERROR_CODE_RATE_LIMIT ||
                    httpEx.getStatusCode().value() == HTTP_TOO_MANY_REQUESTS) {
                    logger.warn("Rate limit reached for API key.");
                    throw new RuntimeException("Rate limit exceeded", httpEx);
                }
            }
        } catch (Exception parseEx) {
            logger.error("Failed to parse error response", parseEx);
        }

        throw new RuntimeException("HTTP client error: " + httpEx.getMessage(), httpEx);
    }

    /**
     * Logs HTTP error based on status code
     *
     * @param statusCode The HTTP status code
     */
    private void logHttpError(HttpStatus statusCode) {
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
    }
}
