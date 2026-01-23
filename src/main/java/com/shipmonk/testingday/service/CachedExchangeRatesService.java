package com.shipmonk.testingday.service;

import com.shipmonk.testingday.dto.ExchangeRatesCacheDto;
import com.shipmonk.testingday.dto.ExchangeRatesCacheMapper;
import com.shipmonk.testingday.exception.CachedRatesNotFoundException;
import com.shipmonk.testingday.external.FixerResponse;
import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import com.shipmonk.testingday.entity.ExchangeRateValue;
import com.shipmonk.testingday.entity.ExchangeRateValueId;
import com.shipmonk.testingday.repository.ExchangeRatesCacheRepository;
import com.shipmonk.testingday.repository.ExchangeRateValueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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


    public CachedExchangeRatesService(ExchangeRatesCacheRepository cacheRepository,
                                      ExchangeRateValueRepository valueRepository) {
        this.cacheRepository = cacheRepository;
        this.valueRepository = valueRepository;
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
    public void getExchangeRates(String dateStr, String baseCurrency) {
        LocalDate date = LocalDate.parse(dateStr);

        logger.info("Fetching exchange rates for date: {} and base currency: {}", date, baseCurrency);
    }

    /**
     * Get cached rates from database
     */
    @Transactional
    public ExchangeRatesCacheDto getCachedRates(LocalDate date, String baseCurrency) throws CachedRatesNotFoundException {
        Optional<ExchangeRatesCache> cacheOpt = cacheRepository
            .findByIdDateAndIdBaseCurrency(date, baseCurrency);

        if (cacheOpt.isEmpty()) {
            throw new CachedRatesNotFoundException("Cache entry not found");
        }

        // Ensure rates are loaded
        return ExchangeRatesCacheMapper.toDto(cacheOpt.get());
    }

    /**
     * Save exchange rates to cache
     */
    @Transactional
    public void saveToCache(LocalDate date, String baseCurrency, ExchangeRatesCacheDto exchangeRatesCacheDto) {
        logger.debug("Saving to cache: date={}, baseCurrency={}, ratesCount={}",
            date, baseCurrency, exchangeRatesCacheDto.getRates().size());

        ExchangeRatesCache cache = ExchangeRatesCacheMapper.toEntity(exchangeRatesCacheDto);

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
