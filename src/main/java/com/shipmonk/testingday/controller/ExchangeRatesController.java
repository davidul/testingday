package com.shipmonk.testingday.controller;

import com.shipmonk.testingday.dto.ExchangeRatesCacheDto;
import com.shipmonk.testingday.exception.CachedRatesNotFoundException;
import com.shipmonk.testingday.exception.InvalidInputException;
import com.shipmonk.testingday.external.FixerResponse;
import com.shipmonk.testingday.mapper.FixerResponseMapper;
import com.shipmonk.testingday.service.CachedExchangeRatesService;
import com.shipmonk.testingday.service.ExchangeRatesApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.CompletableFuture;

import static com.shipmonk.testingday.validators.ApiKeyValidator.validateInputs;
import static com.shipmonk.testingday.validators.DateValidator.validateDateFormat;

@RestController
@RequestMapping(
    path = "/api/v1/rates"
)
public class ExchangeRatesController {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRatesController.class);
    private static final String DEFAULT_BASE_CURRENCY = "EUR";
    private static final String DEFAULT_SYMBOLS = "USD,GBP,CAD";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExchangeRatesApiService apiService;

    private final CachedExchangeRatesService cachedService;

    public ExchangeRatesController(ExchangeRatesApiService apiService,
                                   CachedExchangeRatesService cachedService) {
        this.apiService = apiService;
        this.cachedService = cachedService;
    }

    /**
     * Get exchange rates for a specific day <br/>
     * Example: GET /api/v1/rates/2013-12-24?symbols=USD,GBP,EUR <br/>
     * This endpoint uses asynchronous API calls to Fixer.io
     *
     * @param day The date in YYYY-MM-DD format
     * @param symbols Comma-separated list of currency symbols (e.g., "USD,GBP,EUR"). Optional, defaults to "EUR,GBP,CAD"
     * @return Exchange rates response
     */
    @RequestMapping(method = RequestMethod.GET,
        path = "/{day}",
        produces = "application/json")
    public FixerResponse getRates(
            @PathVariable String day,
            @RequestParam(name = "symbols", required = false, defaultValue = DEFAULT_SYMBOLS) String symbols,
            @RequestParam(name = "access_key") String apiKey,
            @RequestParam(name = "base", required = false, defaultValue = DEFAULT_BASE_CURRENCY) String baseCurrency){
        logger.info("Received request for exchange rates on day: {} with symbols: {}", day, symbols);

        // Validate date format
        validateDateFormat(day);

        // Validate symbols parameter
        validateSymbols(symbols);

        validateInputs(day, apiKey);

        LocalDate date = LocalDate.parse(day, DATE_FORMATTER);

        try {
            ExchangeRatesCacheDto cachedRates = cachedService.getCachedRates(date, baseCurrency);
            logger.info("Returning cached rates for date: {} and base currency: {}", day, baseCurrency);

            return FixerResponseMapper.toFixerResponse(cachedRates);

        } catch (CachedRatesNotFoundException e) {
            logger.info("Cache miss for date: {} and base currency: {}. Fetching from Fixer.io...", day, DEFAULT_BASE_CURRENCY);

            CompletableFuture<FixerResponse> fixerResponseCompletableFuture =
                apiService.fetchExchangeRatesAsync(day, DEFAULT_BASE_CURRENCY, symbols, apiKey);

            try {
                // Wait for the CompletableFuture to complete and get the result
                FixerResponse fixerResponse = fixerResponseCompletableFuture.join();

                logger.info("Successfully fetched rates from Fixer.io for date: {}", day);
                logger.debug("Fixer.io response: {}", fixerResponse);

                ExchangeRatesCacheDto dto = FixerResponseMapper.toDto(fixerResponse);
                cachedService.saveToCache(date, DEFAULT_BASE_CURRENCY, dto);

                return fixerResponse;

            } catch (Exception ex) {
                logger.error("Error fetching rates from Fixer.io for date: {}", day, ex);
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    String.format("Failed to fetch exchange rates for date: %s. Error: %s", day, ex.getMessage()),
                    ex
                );
            }
        }
    }


    /**
     * Validates the symbols parameter format
     * Symbols should be comma-separated, 3-letter uppercase currency codes
     *
     * @param symbols The symbols string to validate
     * @throws ResponseStatusException if the symbols format is invalid
     */
    private void validateSymbols(String symbols) {
        if (symbols == null || symbols.trim().isEmpty()) {
            logger.error("Symbols parameter is null or empty");
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Symbols parameter cannot be empty"
            );
        }

        // Split by comma and validate each symbol
        String[] symbolArray = symbols.split(",");

        for (String symbol : symbolArray) {
            String trimmedSymbol = symbol.trim();

            if (trimmedSymbol.isEmpty()) {
                logger.error("Empty symbol found in symbols parameter: {}", symbols);
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Symbols parameter contains empty values"
                );
            }

            // Validate that symbol is 3 letters and uppercase
            if (!trimmedSymbol.matches("^[A-Z]{3}$")) {
                logger.error("Invalid symbol format: {}. Expected 3 uppercase letters", trimmedSymbol);
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("Invalid currency symbol: '%s'. Expected 3 uppercase letters (e.g., USD, EUR, GBP)", trimmedSymbol)
                );
            }
        }

        logger.debug("Symbols validation passed for: {}", symbols);
    }



}
