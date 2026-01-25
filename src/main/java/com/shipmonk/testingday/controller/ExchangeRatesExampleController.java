package com.shipmonk.testingday.controller;

import com.shipmonk.testingday.external.FixerResponse;
import com.shipmonk.testingday.service.ExchangeRatesApiService;
import com.shipmonk.testingday.service.ExchangeRatesWebClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

/**
 * Example controller demonstrating both RestTemplate and WebClient approaches
 * for fetching exchange rates from Fixer.io API
 */
@RestController
@RequestMapping("/api/v1/exchange-rates")
public class ExchangeRatesExampleController {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRatesExampleController.class);
    private static final String DEFAULT_SYMBOLS = "USD,GBP,EUR";

    private final ExchangeRatesApiService restTemplateService;
    private final ExchangeRatesWebClientService webClientService;

    public ExchangeRatesExampleController(
            ExchangeRatesApiService restTemplateService,
            ExchangeRatesWebClientService webClientService) {
        this.restTemplateService = restTemplateService;
        this.webClientService = webClientService;
    }

    /**
     * Fetch exchange rates using RestTemplate (blocking + @Async)
     * Returns CompletableFuture which Spring MVC handles
     *
     * Example: GET /api/v1/exchange-rates/resttemplate/2024-01-15?access_key=YOUR_KEY
     *
     * @param date The date in YYYY-MM-DD format
     * @param apiKey Fixer.io API key
     * @param symbols Comma-separated currency symbols (optional)
     * @param baseCurrency Base currency (optional, for paid plans)
     * @return CompletableFuture with FixerResponse
     */
    @GetMapping("/resttemplate/{date}")
    public CompletableFuture<FixerResponse> getExchangeRatesRestTemplate(
            @PathVariable String date,
            @RequestParam(name = "access_key") String apiKey,
            @RequestParam(name = "symbols", required = false, defaultValue = DEFAULT_SYMBOLS) String symbols,
            @RequestParam(name = "base", required = false) String baseCurrency) {

        logger.info("RestTemplate endpoint called for date: {}", date);

        // For free plan (no base currency parameter)
        if (baseCurrency == null || baseCurrency.isEmpty()) {
            // Note: This will use @Async and return CompletableFuture
            return restTemplateService.fetchExchangeRatesAsync(date, null, symbols, apiKey);
        }

        // For paid plan (with base currency)
        return restTemplateService.fetchExchangeRatesAsync(date, baseCurrency, symbols, apiKey);
    }

    /**
     * Fetch exchange rates using WebClient (reactive, non-blocking)
     * Returns Mono<FixerResponse> which Spring MVC/WebFlux can handle
     *
     * Example: GET /api/v1/exchange-rates/webclient/2024-01-15?access_key=YOUR_KEY
     *
     * @param date The date in YYYY-MM-DD format
     * @param apiKey Fixer.io API key
     * @param symbols Comma-separated currency symbols (optional)
     * @param baseCurrency Base currency (optional, for paid plans)
     * @return Mono with FixerResponse (reactive)
     */
    @GetMapping("/webclient/{date}")
    public Mono<FixerResponse> getExchangeRatesWebClient(
            @PathVariable String date,
            @RequestParam(name = "access_key") String apiKey,
            @RequestParam(name = "symbols", required = false, defaultValue = DEFAULT_SYMBOLS) String symbols,
            @RequestParam(name = "base", required = false) String baseCurrency) {

        logger.info("WebClient endpoint called for date: {}", date);

        // For free plan (no base currency parameter)
        if (baseCurrency == null || baseCurrency.isEmpty()) {
            return webClientService.fetchExchangeRatesFreePlan(date, apiKey);
        }

        // For paid plan (with base currency)
        return webClientService.fetchExchangeRates(date, baseCurrency, symbols, apiKey);
    }

    /**
     * Fetch exchange rates using WebClient but block to get result
     * This is a hybrid approach - uses WebClient but returns synchronously
     *
     * Example: GET /api/v1/exchange-rates/webclient-blocking/2024-01-15?access_key=YOUR_KEY
     *
     * NOTE: Blocking defeats the purpose of WebClient's non-blocking nature.
     * Use this only when you must integrate with synchronous code.
     *
     * @param date The date in YYYY-MM-DD format
     * @param apiKey Fixer.io API key
     * @param symbols Comma-separated currency symbols (optional)
     * @param baseCurrency Base currency (optional, for paid plans)
     * @return FixerResponse (blocking)
     */
    @GetMapping("/webclient-blocking/{date}")
    public FixerResponse getExchangeRatesWebClientBlocking(
            @PathVariable String date,
            @RequestParam(name = "access_key") String apiKey,
            @RequestParam(name = "symbols", required = false, defaultValue = DEFAULT_SYMBOLS) String symbols,
            @RequestParam(name = "base", required = false) String baseCurrency) {

        logger.info("WebClient (blocking) endpoint called for date: {}", date);

        // For free plan
        if (baseCurrency == null || baseCurrency.isEmpty()) {
            return webClientService.fetchExchangeRatesFreePlan(date, apiKey)
                .block(); // Block here to get the result
        }

        // For paid plan
        return webClientService.fetchExchangeRates(date, baseCurrency, symbols, apiKey)
            .block(); // Block here to get the result
    }

    /**
     * Compare performance: Fetch rates using both methods
     * This endpoint demonstrates the difference in approach
     *
     * Example: GET /api/v1/exchange-rates/compare/2024-01-15?access_key=YOUR_KEY
     *
     * @param date The date in YYYY-MM-DD format
     * @param apiKey Fixer.io API key
     * @return ComparisonResult showing both approaches
     */
    @GetMapping("/compare/{date}")
    public Mono<ComparisonResult> compareApproaches(
            @PathVariable String date,
            @RequestParam(name = "access_key") String apiKey) {

        logger.info("Comparison endpoint called for date: {}", date);

        long startTime = System.currentTimeMillis();

        // Using WebClient (reactive)
        Mono<FixerResponse> webClientResult = webClientService
            .fetchExchangeRatesFreePlan(date, apiKey)
            .doOnSuccess(response -> {
                long webClientTime = System.currentTimeMillis() - startTime;
                logger.info("WebClient completed in {} ms", webClientTime);
            });

        // Using RestTemplate (async)
        long restTemplateStartTime = System.currentTimeMillis();
        CompletableFuture<FixerResponse> restTemplateResult = restTemplateService
            .fetchExchangeRatesAsync(date, null, DEFAULT_SYMBOLS, apiKey)
            .whenComplete((response, error) -> {
                long restTemplateTime = System.currentTimeMillis() - restTemplateStartTime;
                logger.info("RestTemplate completed in {} ms", restTemplateTime);
            });

        // Combine results
        return webClientResult.map(webClientResponse -> {
            try {
                FixerResponse restTemplateResponse = restTemplateResult.get();
                return new ComparisonResult(
                    "Both approaches completed successfully",
                    webClientResponse,
                    restTemplateResponse
                );
            } catch (Exception e) {
                logger.error("Error in RestTemplate call", e);
                return new ComparisonResult(
                    "WebClient succeeded, RestTemplate failed: " + e.getMessage(),
                    webClientResponse,
                    null
                );
            }
        });
    }

    /**
     * DTO for comparison results
     */
    public static class ComparisonResult {
        private String message;
        private FixerResponse webClientResponse;
        private FixerResponse restTemplateResponse;

        public ComparisonResult(String message, FixerResponse webClientResponse, FixerResponse restTemplateResponse) {
            this.message = message;
            this.webClientResponse = webClientResponse;
            this.restTemplateResponse = restTemplateResponse;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public FixerResponse getWebClientResponse() {
            return webClientResponse;
        }

        public void setWebClientResponse(FixerResponse webClientResponse) {
            this.webClientResponse = webClientResponse;
        }

        public FixerResponse getRestTemplateResponse() {
            return restTemplateResponse;
        }

        public void setRestTemplateResponse(FixerResponse restTemplateResponse) {
            this.restTemplateResponse = restTemplateResponse;
        }
    }
}
