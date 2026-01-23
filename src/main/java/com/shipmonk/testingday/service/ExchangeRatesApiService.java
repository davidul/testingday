package com.shipmonk.testingday.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipmonk.testingday.external.FixerErrorResponse;
import com.shipmonk.testingday.external.FixerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Service
public class ExchangeRatesApiService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRatesApiService.class);
    private static final String FIXER_BASE_URL = "https://data.fixer.io/api";

    private final Set<String> freeModeSet;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ExchangeRatesApiService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        freeModeSet = new HashSet<>();
        freeModeSet.add("a0fbcdca0a84fbc406fab744066bb166"); // Default free API key
    }

    /**
     * Fetches exchange rates from Fixer.io API asynchronously
     *
     * @param date The date for which to fetch rates (format: YYYY-MM-DD)
     * @param baseCurrency The base currency (e.g., "USD")
     * @return CompletableFuture containing the FixerResponse
     */
    @Async("taskExecutor")
    public CompletableFuture<FixerResponse> fetchExchangeRatesAsync(String date, String baseCurrency, String symbols, String apiKey) {
        logger.info("Fetching exchange rates for date: {} and base currency: {} on thread: {}",
            date, baseCurrency, Thread.currentThread().getName());

        if (freeModeSet.contains(apiKey)) {
            return fetchExchangeRatesFreePlanAsync(date, apiKey);
        }

        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(FIXER_BASE_URL + "/" + date)
                .queryParam("access_key", apiKey)
                .queryParam("base", baseCurrency)
                .queryParam("symbols", symbols)
                .toUriString();

            logger.debug("Making request to Fixer.io: {}", url.replace(apiKey, "***"));

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

        } catch (HttpClientErrorException httpEx) {
            // Parse the error response from Fixer.io
            logger.error("HTTP error fetching exchange rates from Fixer.io for date: {}, Status: {}",
                date, httpEx.getStatusCode());

            try {
                String errorBody = httpEx.getResponseBodyAsString();
                logger.error("Fixer.io error response: {}", errorBody);

                // Parse the error JSON
                FixerErrorResponse errorResponse = objectMapper.readValue(errorBody, FixerErrorResponse.class);

                if (errorResponse.getError() != null) {
                    FixerErrorResponse.ErrorDetail errorDetail = errorResponse.getError();
                    logger.error("Fixer.io API Error - Code: {}, Type: {}, Info: {}",
                        errorDetail.getCode(),
                        errorDetail.getType(),
                        errorDetail.getInfo());

                    // Create a more descriptive error message
                    String errorMessage = String.format(
                        "Fixer.io API error [%d]: %s - %s",
                        errorDetail.getCode(),
                        errorDetail.getType(),
                        errorDetail.getInfo() != null ? errorDetail.getInfo() : "No additional info"
                    );

                    if (errorDetail.getCode() == 105) {
                        errorMessage += " - This feature is not supported on your current subscription plan.";
                        logger.error(errorMessage);
                        logger.warn("Switching to free mode for API key due to subscription limitations.");
                        freeModeSet.add(apiKey);

                        logger.info("Retrying with free plan after 5 seconds (respecting API rate limit)...");
                        try {
                            Thread.sleep(5000); // Wait 5 seconds for rate limiter
                        } catch (InterruptedException ie) {
                            logger.error("Wait interrupted", ie);
                            Thread.currentThread().interrupt();
                            return CompletableFuture.failedFuture(ie);
                        }

                        return fetchExchangeRatesFreePlanAsync(date, apiKey);
                    }

                    return CompletableFuture.failedFuture(new RuntimeException(errorMessage));
                }
            } catch (Exception parseEx) {
                logger.error("Failed to parse Fixer.io error response", parseEx);
            }

            return CompletableFuture.failedFuture(httpEx);

        } catch (Exception e) {
            logger.error("Error fetching exchange rates from Fixer.io for date: {}", date, e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("taskExecutor")
    public CompletableFuture<FixerResponse> fetchExchangeRatesFreePlanAsync(String date, String apiKey) {
        return fetchExchangeRatesFreePlanWithRetry(date, apiKey, 0);
    }

    private CompletableFuture<FixerResponse> fetchExchangeRatesFreePlanWithRetry(String date, String apiKey, int retryCount) {
        final int MAX_RETRIES = 3;
        final int RETRY_DELAY_MS = 5000; // 5 seconds

        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(FIXER_BASE_URL + "/" + date)
                .queryParam("access_key", apiKey)
                .toUriString();

            logger.debug("Making request to Fixer.io (attempt {}/{}): {}",
                retryCount + 1, MAX_RETRIES + 1, url.replace(apiKey, "***"));

            FixerResponse response = restTemplate.getForObject(url, FixerResponse.class);
            if (response != null && response.isSuccess()) {
                logger.info("Successfully fetched exchange rates for date: {}", date);
                logger.debug("Response: {}", response);
                return CompletableFuture.completedFuture(response);
            } else {
                logger.error("Fixer.io API returned unsuccessful response for date: {}", date);
                return CompletableFuture.failedFuture(
                    new RuntimeException("Failed to fetch exchange rates from Fixer.io")
                );
            }
        } catch (HttpClientErrorException httpEx) {
            logger.error("HTTP error in free plan mode for date: {}, Status: {}", date, httpEx.getStatusCode());

            try {
                String errorBody = httpEx.getResponseBodyAsString();
                FixerErrorResponse errorResponse = objectMapper.readValue(errorBody, FixerErrorResponse.class);

                if (errorResponse.getError() != null) {
                    FixerErrorResponse.ErrorDetail errorDetail = errorResponse.getError();
                    int errorCode = errorDetail.getCode();

                    logger.error("Free plan API Error - Code: {}, Type: {}", errorCode, errorDetail.getType());

                    // Retry on rate limiting (error code 104) or too many requests
                    if ((errorCode == 104 || httpEx.getStatusCode().value() == 429) && retryCount < MAX_RETRIES) {
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
                }
            } catch (Exception parseEx) {
                logger.error("Failed to parse free plan error response", parseEx);
            }

            return CompletableFuture.failedFuture(httpEx);

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
}
