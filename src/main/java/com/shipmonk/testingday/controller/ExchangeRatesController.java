package com.shipmonk.testingday.controller;

import com.shipmonk.testingday.dto.ExchangeRateValueDto;
import com.shipmonk.testingday.dto.ExchangeRatesCacheDto;
import com.shipmonk.testingday.exception.CachedRatesNotFoundException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.shipmonk.testingday.validators.ApiKeyValidator.validateInputs;
import static com.shipmonk.testingday.validators.DateValidator.validateDateFormat;
import static com.shipmonk.testingday.validators.SymbolValidator.validateSymbols;

@RestController
@RequestMapping(
    path = "/api/v1/rates"
)
public class ExchangeRatesController {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRatesController.class);
    private static final String DEFAULT_BASE_CURRENCY = "EUR";
    private static final String DEFAULT_SYMBOLS = "USD,GBP,CAD";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_FORMAT);

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
     * @param day     The date in YYYY-MM-DD format
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
        @RequestParam(name = "base", required = false, defaultValue = DEFAULT_BASE_CURRENCY) String baseCurrency) {
        logger.info("Received request for exchange rates on day: {} with symbols: {}", day, symbols);

        // Validate date format
        validateDateFormat(day);

        // Validate symbols parameter
        validateSymbols(symbols);

        // Validate API key
        validateInputs(apiKey);

        LocalDate date = LocalDate.parse(day, DATE_FORMATTER);

        try {
            ExchangeRatesCacheDto cachedRates = cachedService.getCachedRates(date, baseCurrency);
            logger.info("Returning cached rates for date: {} and base currency: {}", day, baseCurrency);

            symbols = compareSymbols(symbols, cachedRates);
            // fetch only if there are missing symbols
            if (!symbols.isEmpty()) {
                logger.info("Cached rates do not contain all requested symbols. Fetching missing symbols from Fixer.io...");
                fetchFixerExchangeRates(day, symbols, apiKey, date);
            }

            return FixerResponseMapper.toFixerResponse(cachedRates);

        } catch (CachedRatesNotFoundException e) {
            logger.info("Cache miss for date: {} and base currency: {}. Fetching from Fixer.io...", day, DEFAULT_BASE_CURRENCY);

            return fetchFixerExchangeRates(day, symbols, apiKey, date);
        }
    }

    private FixerResponse fetchFixerExchangeRates(String day, String symbols, String apiKey, LocalDate date) {

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

    private String compareSymbols(String symbols, ExchangeRatesCacheDto cachedRates) {
        List<ExchangeRateValueDto> rates = cachedRates.getRates();
        List<String> symbolParams = new ArrayList<>(List.of(symbols.split(",")));
        if (symbolParams.isEmpty()) {
            return "";
        }
        List<String> cachedCurrencies = rates.stream()
            .map(ExchangeRateValueDto::getTargetCurrency)
            .toList();
        symbolParams.removeAll(cachedCurrencies);
        if (symbolParams.isEmpty()) {
            return "";
        }
        return String.join(",", symbolParams);
    }

}
