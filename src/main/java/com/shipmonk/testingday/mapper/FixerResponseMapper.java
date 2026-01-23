package com.shipmonk.testingday.mapper;

import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import com.shipmonk.testingday.entity.ExchangeRateValue;
import com.shipmonk.testingday.entity.ExchangeRateValueId;
import com.shipmonk.testingday.external.FixerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Mapper class for converting FixerResponse to ExchangeRatesCache and ExchangeRateValue entities
 */
public class FixerResponseMapper {

    private static final Logger logger = LoggerFactory.getLogger(FixerResponseMapper.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Converts a FixerResponse to ExchangeRatesCache entity with all exchange rate values
     *
     * @param fixerResponse The Fixer API response
     * @return ExchangeRatesCache entity populated with data from the response
     * @throws IllegalArgumentException if fixerResponse is null or invalid
     */
    public static ExchangeRatesCache toExchangeRatesCache(FixerResponse fixerResponse) {
        if (fixerResponse == null) {
            throw new IllegalArgumentException("FixerResponse cannot be null");
        }

        if (!fixerResponse.isSuccess()) {
            throw new IllegalArgumentException("FixerResponse indicates failure - cannot convert");
        }

        if (fixerResponse.getDate() == null || fixerResponse.getDate().isEmpty()) {
            throw new IllegalArgumentException("FixerResponse date cannot be null or empty");
        }

        if (fixerResponse.getBase() == null || fixerResponse.getBase().isEmpty()) {
            throw new IllegalArgumentException("FixerResponse base currency cannot be null or empty");
        }

        // Parse the date string to LocalDate
        LocalDate date;
        try {
            date = LocalDate.parse(fixerResponse.getDate(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            logger.error("Failed to parse date from FixerResponse: {}", fixerResponse.getDate(), e);
            throw new IllegalArgumentException("Invalid date format in FixerResponse: " + fixerResponse.getDate(), e);
        }

        String baseCurrency = fixerResponse.getBase();

        // Create the cache entity with composite ID
        ExchangeRatesCacheId cacheId = new ExchangeRatesCacheId(date, baseCurrency);
        ExchangeRatesCache cache = new ExchangeRatesCache(cacheId);

        // Convert all rates to ExchangeRateValue entities
        if (fixerResponse.getRates() != null && !fixerResponse.getRates().isEmpty()) {
            for (Map.Entry<String, Double> rateEntry : fixerResponse.getRates().entrySet()) {
                String targetCurrency = rateEntry.getKey();
                Double rateValue = rateEntry.getValue();

                if (targetCurrency != null && !targetCurrency.isEmpty() && rateValue != null) {
                    ExchangeRateValue exchangeRateValue = toExchangeRateValue(
                        date,
                        baseCurrency,
                        targetCurrency,
                        rateValue
                    );
                    cache.addExchangeRateValue(exchangeRateValue);
                } else {
                    logger.warn("Skipping invalid rate entry: currency={}, rate={}", targetCurrency, rateValue);
                }
            }

            logger.info("Converted FixerResponse to ExchangeRatesCache with {} rate values",
                cache.getExchangeRateValues().size());
        } else {
            logger.warn("FixerResponse contains no rates data");
        }

        return cache;
    }

    /**
     * Creates an ExchangeRateValue entity
     *
     * @param date The date of the exchange rate
     * @param baseCurrency The base currency
     * @param targetCurrency The target currency
     * @param rate The exchange rate value
     * @return ExchangeRateValue entity
     */
    public static ExchangeRateValue toExchangeRateValue(
            LocalDate date,
            String baseCurrency,
            String targetCurrency,
            Double rate) {

        if (date == null) {
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (baseCurrency == null || baseCurrency.isEmpty()) {
            throw new IllegalArgumentException("Base currency cannot be null or empty");
        }
        if (targetCurrency == null || targetCurrency.isEmpty()) {
            throw new IllegalArgumentException("Target currency cannot be null or empty");
        }
        if (rate == null || rate <= 0) {
            throw new IllegalArgumentException("Rate must be a positive number");
        }

        ExchangeRateValueId valueId = new ExchangeRateValueId(date, baseCurrency, targetCurrency);
        BigDecimal rateAsBigDecimal = BigDecimal.valueOf(rate);

        return new ExchangeRateValue(valueId, rateAsBigDecimal);
    }

    /**
     * Converts a FixerResponse to ExchangeRatesCache with validation
     * Returns null if conversion fails instead of throwing exception
     *
     * @param fixerResponse The Fixer API response
     * @return ExchangeRatesCache entity or null if conversion fails
     */
    public static ExchangeRatesCache toExchangeRatesCacheSafe(FixerResponse fixerResponse) {
        try {
            return toExchangeRatesCache(fixerResponse);
        } catch (Exception e) {
            logger.error("Failed to convert FixerResponse to ExchangeRatesCache", e);
            return null;
        }
    }

    /**
     * Validates that a FixerResponse can be converted to entities
     *
     * @param fixerResponse The response to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidForConversion(FixerResponse fixerResponse) {
        if (fixerResponse == null) {
            return false;
        }

        if (!fixerResponse.isSuccess()) {
            logger.debug("FixerResponse validation failed: success=false");
            return false;
        }

        if (fixerResponse.getDate() == null || fixerResponse.getDate().isEmpty()) {
            logger.debug("FixerResponse validation failed: date is null or empty");
            return false;
        }

        if (fixerResponse.getBase() == null || fixerResponse.getBase().isEmpty()) {
            logger.debug("FixerResponse validation failed: base currency is null or empty");
            return false;
        }

        // Try to parse the date
        try {
            LocalDate.parse(fixerResponse.getDate(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            logger.debug("FixerResponse validation failed: invalid date format: {}", fixerResponse.getDate());
            return false;
        }

        if (fixerResponse.getRates() == null || fixerResponse.getRates().isEmpty()) {
            logger.debug("FixerResponse validation failed: no rates data");
            return false;
        }

        return true;
    }

    /**
     * Converts FixerResponse back to entity format (reverse mapping)
     * Useful for testing and data transformation
     *
     * @param cache The ExchangeRatesCache entity
     * @return FixerResponse object
     */
    public static FixerResponse toFixerResponse(ExchangeRatesCache cache) {
        if (cache == null || cache.getId() == null) {
            throw new IllegalArgumentException("ExchangeRatesCache or its ID cannot be null");
        }

        FixerResponse response = new FixerResponse();
        response.setSuccess(true);
        response.setHistorical(true);
        response.setDate(cache.getId().getDate().format(DATE_FORMATTER));
        response.setBase(cache.getId().getBaseCurrency());

        // Convert exchange rate values to map
        Map<String, Double> rates = new java.util.HashMap<>();
        if (cache.getExchangeRateValues() != null) {
            for (ExchangeRateValue value : cache.getExchangeRateValues()) {
                if (value.getId() != null && value.getRate() != null) {
                    rates.put(
                        value.getId().getTargetCurrency(),
                        value.getRate().doubleValue()
                    );
                }
            }
        }
        response.setRates(rates);

        // Set timestamp (optional - can be null)
        response.setTimestamp(System.currentTimeMillis() / 1000);

        return response;
    }
}
