package com.shipmonk.testingday.service;

import com.shipmonk.testingday.dto.ExchangeRatesCacheDto;
import com.shipmonk.testingday.dto.ExchangeRateValueDto;
import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import com.shipmonk.testingday.entity.ExchangeRateValue;
import com.shipmonk.testingday.entity.ExchangeRateValueId;
import com.shipmonk.testingday.exception.CachedRatesNotFoundException;
import com.shipmonk.testingday.repository.ExchangeRatesCacheRepository;
import com.shipmonk.testingday.repository.ExchangeRateValueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CachedExchangeRatesService
 * Uses Mockito to mock repository dependencies
 */
@ExtendWith(MockitoExtension.class)
class CachedExchangeRatesServiceTest {

    @Mock
    private ExchangeRatesCacheRepository cacheRepository;

    @Mock
    private ExchangeRateValueRepository valueRepository;

    @InjectMocks
    private CachedExchangeRatesService service;

    private LocalDate testDate;
    private String testBaseCurrency;
    private String testTargetCurrency;
    private ExchangeRatesCache testCache;
    private ExchangeRateValue testValue;

    @BeforeEach
    void setUp() {
        testDate = LocalDate.of(2024, 1, 15);
        testBaseCurrency = "USD";
        testTargetCurrency = "EUR";

        // Create test cache entity
        ExchangeRatesCacheId cacheId = new ExchangeRatesCacheId(testDate, testBaseCurrency);
        testCache = new ExchangeRatesCache(cacheId);

        // Create test exchange rate value
        ExchangeRateValueId valueId = new ExchangeRateValueId(testDate, testBaseCurrency, testTargetCurrency);
        testValue = new ExchangeRateValue(valueId, new BigDecimal("0.85"));
        testCache.addExchangeRateValue(testValue);
    }

    // ==================== getCachedRates Tests ====================

    @Test
    void testGetCachedRates_Success() throws CachedRatesNotFoundException {
        // Given
        when(cacheRepository.findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency))
            .thenReturn(Optional.of(testCache));

        // When
        ExchangeRatesCacheDto result = service.getCachedRates(testDate, testBaseCurrency);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getDate()).isEqualTo(testDate);
        assertThat(result.getBaseCurrency()).isEqualTo(testBaseCurrency);
        assertThat(result.getRates()).hasSize(1);
        assertThat(result.getRates().get(0).getTargetCurrency()).isEqualTo(testTargetCurrency);
        assertThat(result.getRates().get(0).getRate()).isEqualByComparingTo(new BigDecimal("0.85"));

        verify(cacheRepository).findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
    }

    @Test
    void testGetCachedRates_NotFound() {
        // Given
        when(cacheRepository.findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> service.getCachedRates(testDate, testBaseCurrency))
            .isInstanceOf(CachedRatesNotFoundException.class)
            .hasMessageContaining("Cache entry not found");

        verify(cacheRepository).findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
    }

    @Test
    void testGetCachedRates_WithMultipleRates() throws CachedRatesNotFoundException {
        // Given
        ExchangeRateValueId valueId2 = new ExchangeRateValueId(testDate, testBaseCurrency, "GBP");
        ExchangeRateValue value2 = new ExchangeRateValue(valueId2, new BigDecimal("0.73"));
        testCache.addExchangeRateValue(value2);

        when(cacheRepository.findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency))
            .thenReturn(Optional.of(testCache));

        // When
        ExchangeRatesCacheDto result = service.getCachedRates(testDate, testBaseCurrency);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getRates()).hasSize(2);
        assertThat(result.getRates())
            .extracting(ExchangeRateValueDto::getTargetCurrency)
            .containsExactlyInAnyOrder("EUR", "GBP");
    }

    // ==================== saveToCache Tests ====================

    @Test
    void testSaveToCache_Success() {
        // Given
        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto(testDate, testBaseCurrency);
        dto.addRate("EUR", new BigDecimal("0.85"));
        dto.addRate("GBP", new BigDecimal("0.73"));

        when(cacheRepository.save(any(ExchangeRatesCache.class))).thenReturn(testCache);

        // When
        service.saveToCache(testDate, testBaseCurrency, dto);

        // Then
        verify(cacheRepository).save(any(ExchangeRatesCache.class));
    }

    @Test
    void testSaveToCache_WithNoRates() {
        // Given
        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto(testDate, testBaseCurrency);

        when(cacheRepository.save(any(ExchangeRatesCache.class))).thenReturn(testCache);

        // When
        service.saveToCache(testDate, testBaseCurrency, dto);

        // Then
        verify(cacheRepository).save(any(ExchangeRatesCache.class));
    }

    // ==================== isCached Tests ====================

    @Test
    void testIsCached_True() {
        // Given
        String dateStr = "2024-01-15";
        when(cacheRepository.existsByIdDateAndIdBaseCurrency(testDate, testBaseCurrency))
            .thenReturn(true);

        // When
        boolean result = service.isCached(dateStr, testBaseCurrency);

        // Then
        assertThat(result).isTrue();
        verify(cacheRepository).existsByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
    }

    @Test
    void testIsCached_False() {
        // Given
        String dateStr = "2024-01-15";
        when(cacheRepository.existsByIdDateAndIdBaseCurrency(testDate, testBaseCurrency))
            .thenReturn(false);

        // When
        boolean result = service.isCached(dateStr, testBaseCurrency);

        // Then
        assertThat(result).isFalse();
        verify(cacheRepository).existsByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
    }

    // ==================== getExchangeRate Tests ====================

    @Test
    void testGetExchangeRate_Found() {
        // Given
        String dateStr = "2024-01-15";
        when(valueRepository.findByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
            testDate, testBaseCurrency, testTargetCurrency))
            .thenReturn(Optional.of(testValue));

        // When
        Optional<BigDecimal> result = service.getExchangeRate(dateStr, testBaseCurrency, testTargetCurrency);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualByComparingTo(new BigDecimal("0.85"));
        verify(valueRepository).findByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
            testDate, testBaseCurrency, testTargetCurrency);
    }

    @Test
    void testGetExchangeRate_NotFound() {
        // Given
        String dateStr = "2024-01-15";
        when(valueRepository.findByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
            testDate, testBaseCurrency, testTargetCurrency))
            .thenReturn(Optional.empty());

        // When
        Optional<BigDecimal> result = service.getExchangeRate(dateStr, testBaseCurrency, testTargetCurrency);

        // Then
        assertThat(result).isEmpty();
        verify(valueRepository).findByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
            testDate, testBaseCurrency, testTargetCurrency);
    }

    // ==================== clearCache Tests ====================

    @Test
    void testClearCache_Success() {
        // Given
        String dateStr = "2024-01-15";
        doNothing().when(cacheRepository).deleteByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);

        // When
        service.clearCache(dateStr, testBaseCurrency);

        // Then
        verify(cacheRepository).deleteByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
    }

    @Test
    void testClearCache_WithDifferentDate() {
        // Given
        LocalDate differentDate = LocalDate.of(2024, 2, 20);
        String dateStr = "2024-02-20";
        doNothing().when(cacheRepository).deleteByIdDateAndIdBaseCurrency(differentDate, testBaseCurrency);

        // When
        service.clearCache(dateStr, testBaseCurrency);

        // Then
        verify(cacheRepository).deleteByIdDateAndIdBaseCurrency(differentDate, testBaseCurrency);
    }

    // ==================== getCachedRatesCount Tests ====================

    @Test
    void testGetCachedRatesCount_WithRates() {
        // Given
        String dateStr = "2024-01-15";
        when(valueRepository.countByIdDateAndIdBaseCurrency(testDate, testBaseCurrency))
            .thenReturn(5L);

        // When
        long result = service.getCachedRatesCount(dateStr, testBaseCurrency);

        // Then
        assertThat(result).isEqualTo(5L);
        verify(valueRepository).countByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
    }

    @Test
    void testGetCachedRatesCount_WithNoRates() {
        // Given
        String dateStr = "2024-01-15";
        when(valueRepository.countByIdDateAndIdBaseCurrency(testDate, testBaseCurrency))
            .thenReturn(0L);

        // When
        long result = service.getCachedRatesCount(dateStr, testBaseCurrency);

        // Then
        assertThat(result).isEqualTo(0L);
        verify(valueRepository).countByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
    }

    // ==================== getExchangeRates Tests ====================

    @Test
    void testGetExchangeRates_Success() {
        // Given
        String dateStr = "2024-01-15";

        // When
        service.getExchangeRates(dateStr, testBaseCurrency);

        // Then - Just verifies method executes without error
        // No assertions needed as method returns void
    }

    @Test
    void testGetExchangeRates_WithDifferentDate() {
        // Given
        String dateStr = "2024-12-31";

        // When
        service.getExchangeRates(dateStr, "EUR");

        // Then - Just verifies method executes without error
    }

    // ==================== Edge Cases & Integration Tests ====================

    @Test
    void testSaveAndRetrieve_IntegrationFlow() throws CachedRatesNotFoundException {
        // Given - Save to cache
        ExchangeRatesCacheDto dtoToSave = new ExchangeRatesCacheDto(testDate, testBaseCurrency);
        dtoToSave.addRate("EUR", new BigDecimal("0.85"));
        dtoToSave.addRate("GBP", new BigDecimal("0.73"));

        when(cacheRepository.save(any(ExchangeRatesCache.class))).thenReturn(testCache);
        when(cacheRepository.findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency))
            .thenReturn(Optional.of(testCache));

        // When - Save
        service.saveToCache(testDate, testBaseCurrency, dtoToSave);

        // Then - Retrieve
        ExchangeRatesCacheDto retrieved = service.getCachedRates(testDate, testBaseCurrency);
        assertThat(retrieved).isNotNull();

        verify(cacheRepository).save(any(ExchangeRatesCache.class));
        verify(cacheRepository).findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
    }

    @Test
    void testDateParsing_ValidFormat() {
        // Given
        String validDate = "2024-01-15";
        when(cacheRepository.existsByIdDateAndIdBaseCurrency(any(), any())).thenReturn(true);

        // When
        boolean result = service.isCached(validDate, testBaseCurrency);

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void testMultipleCurrencies_SameDate() throws CachedRatesNotFoundException {
        // Given
        ExchangeRatesCache cacheUSD = new ExchangeRatesCache(
            new ExchangeRatesCacheId(testDate, "USD")
        );
        ExchangeRatesCache cacheEUR = new ExchangeRatesCache(
            new ExchangeRatesCacheId(testDate, "EUR")
        );

        when(cacheRepository.findByIdDateAndIdBaseCurrency(testDate, "USD"))
            .thenReturn(Optional.of(cacheUSD));
        when(cacheRepository.findByIdDateAndIdBaseCurrency(testDate, "EUR"))
            .thenReturn(Optional.of(cacheEUR));

        // When
        ExchangeRatesCacheDto resultUSD = service.getCachedRates(testDate, "USD");
        ExchangeRatesCacheDto resultEUR = service.getCachedRates(testDate, "EUR");

        // Then
        assertThat(resultUSD.getBaseCurrency()).isEqualTo("USD");
        assertThat(resultEUR.getBaseCurrency()).isEqualTo("EUR");
    }
}
