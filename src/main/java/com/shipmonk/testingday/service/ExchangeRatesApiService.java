package com.shipmonk.testingday.service;

import com.shipmonk.testingday.dto.FixerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.concurrent.CompletableFuture;

@Service
public class ExchangeRatesApiService {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRatesApiService.class);
    private static final String FIXER_BASE_URL = "https://data.fixer.io/api";

    @Value("${fixer.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate;

    public ExchangeRatesApiService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Fetches exchange rates from Fixer.io API asynchronously
     *
     * @param date The date for which to fetch rates (format: YYYY-MM-DD)
     * @param baseCurrency The base currency (e.g., "USD")
     * @return CompletableFuture containing the FixerResponse
     */
    @Async("taskExecutor")
    public CompletableFuture<FixerResponse> fetchExchangeRatesAsync(String date, String baseCurrency) {
        logger.info("Fetching exchange rates for date: {} and base currency: {} on thread: {}",
            date, baseCurrency, Thread.currentThread().getName());

        try {
            String url = UriComponentsBuilder
                .fromHttpUrl(FIXER_BASE_URL + "/" + date)
                .queryParam("access_key", apiKey)
                .queryParam("base", baseCurrency)
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

        } catch (Exception e) {
            logger.error("Error fetching exchange rates from Fixer.io for date: {}", date, e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
