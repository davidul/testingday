package com.shipmonk.testingday.service;

import com.shipmonk.testingday.dto.FixerResponse;
import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import com.shipmonk.testingday.entity.ExchangeRateValue;
import com.shipmonk.testingday.entity.ExchangeRateValueId;
import com.shipmonk.testingday.repository.ExchangeRatesCacheRepository;
import com.shipmonk.testingday.repository.ExchangeRateValueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for managing exchange rates with caching
 * Combines external API calls with database caching
 */
@Service
public class CachedExchangeRatesService {

    private static final Logger logger = LoggerFactory.getLogger(CachedExchangeRatesService.class);

    private final ExchangeRatesCacheRepository cacheRepository;

    private final ExchangeRateValueRepository valueRepository;

    private final ExchangeRatesApiService apiService;

    public CachedExchangeRatesService(
        ExchangeRatesCacheRepository cacheRepository,
        ExchangeRateValueRepository valueRepository,
        ExchangeRatesApiService apiService) {
        this.cacheRepository = cacheRepository;
        this.valueRepository = valueRepository;
        this.apiService = apiService;
    }

    /**
     * Get exchange rates with caching
     * First checks cache, then falls back to external API
     *
     * @param dateStr      The date in YYYY-MM-DD format
     * @param baseCurrency The base currency code (e.g., "USD")
     * @return CompletableFuture with exchange rates
     */
    @Transactional
    public CompletableFuture<FixerResponse> getExchangeRates(String dateStr, String baseCurrency) {
        LocalDate date = LocalDate.parse(dateStr);

        logger.info("Fetching exchange rates for date: {} and base currency: {}", date, baseCurrency);

        // Check if data is cached
        if (cacheRepository.existsByIdDateAndIdBaseCurrency(date, baseCurrency)) {
            logger.info("Cache hit for date: {} and base currency: {}", date, baseCurrency);
            return CompletableFuture.completedFuture(getCachedRates(date, baseCurrency));
        }

        logger.info("Cache miss for date: {} and base currency: {} - fetching from API", date, baseCurrency);

        // Fetch from external API asynchronously
        return apiService.fetchExchangeRatesAsync(dateStr, baseCurrency)
            .thenApply(fixerResponse -> {
                if (fixerResponse.isSuccess()) {
                    // Save to cache
                    saveToCache(date, baseCurrency, fixerResponse);
                    logger.info("Cached exchange rates for date: {} and base currency: {}", date, baseCurrency);
                }
                return fixerResponse;
            });
    }

    /**
     * Get cached rates from database
     */
    private FixerResponse getCachedRates(LocalDate date, String baseCurrency) {
        Optional<ExchangeRatesCache> cacheOpt = cacheRepository
            .findByIdDateAndIdBaseCurrency(date, baseCurrency);

        if (cacheOpt.isEmpty()) {
            throw new RuntimeException("Cache entry not found");
        }

        ExchangeRatesCache cache = cacheOpt.get();

        // Convert to FixerResponse
        FixerResponse response = new FixerResponse();
        response.setSuccess(true);
        response.setDate(date.toString());
        response.setBase(baseCurrency);
        response.setHistorical(true);

        // Convert exchange rate values to map
        Map<String, Double> rates = cache.getExchangeRateValues().stream()
            .collect(Collectors.toMap(
                v -> v.getId().getTargetCurrency(),
                v -> v.getRate().doubleValue()
            ));
        response.setRates(rates);

        return response;
    }

    /**
     * Save exchange rates to cache
     */
    @Transactional
    public void saveToCache(LocalDate date, String baseCurrency, FixerResponse fixerResponse) {
        logger.debug("Saving to cache: date={}, baseCurrency={}, ratesCount={}",
            date, baseCurrency, fixerResponse.getRates().size());

        // Create cache entry
        ExchangeRatesCacheId cacheId = new ExchangeRatesCacheId(date, baseCurrency);
        ExchangeRatesCache cache = new ExchangeRatesCache(cacheId);

        // Add rate values
        for (Map.Entry<String, Double> entry : fixerResponse.getRates().entrySet()) {
            ExchangeRateValueId valueId = new ExchangeRateValueId(
                date, baseCurrency, entry.getKey());
            ExchangeRateValue value = new ExchangeRateValue(
                valueId, BigDecimal.valueOf(entry.getValue()));
            cache.addExchangeRateValue(value);
        }

        // Save (cascades to values)
        cacheRepository.save(cache);
    }

    /**
     * Check if exchange rates are cached
     */
    public boolean isCached(String dateStr, String baseCurrency) {
        LocalDate date = LocalDate.parse(dateStr);
        return cacheRepository.existsByIdDateAndIdBaseCurrency(date, baseCurrency);
    }

    /**
     * Get specific exchange rate
     */
    public Optional<BigDecimal> getExchangeRate(String dateStr, String baseCurrency, String targetCurrency) {
        LocalDate date = LocalDate.parse(dateStr);
        return valueRepository
            .findByIdDateAndIdBaseCurrencyAndIdTargetCurrency(date, baseCurrency, targetCurrency)
            .map(ExchangeRateValue::getRate);
    }

    /**
     * Clear cache for specific date and base currency
     */
    @Transactional
    public void clearCache(String dateStr, String baseCurrency) {
        LocalDate date = LocalDate.parse(dateStr);
        logger.info("Clearing cache for date: {} and base currency: {}", date, baseCurrency);
        cacheRepository.deleteByIdDateAndIdBaseCurrency(date, baseCurrency);
    }

    /**
     * Get count of cached rates
     */
    public long getCachedRatesCount(String dateStr, String baseCurrency) {
        LocalDate date = LocalDate.parse(dateStr);
        return valueRepository.countByIdDateAndIdBaseCurrency(date, baseCurrency);
    }
}
