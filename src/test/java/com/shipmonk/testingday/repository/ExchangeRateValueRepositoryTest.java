package com.shipmonk.testingday.repository;

import com.shipmonk.testingday.entity.ExchangeRateValue;
import com.shipmonk.testingday.entity.ExchangeRateValueId;
import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ExchangeRateValueRepository
 * Uses @DataJpaTest for lightweight repository testing with an embedded database
 */
@DataJpaTest
@ActiveProfiles("test")
class ExchangeRateValueRepositoryTest {

    @Autowired
    private ExchangeRateValueRepository repository;

    @Autowired
    private ExchangeRatesCacheRepository cacheRepository;

    private LocalDate testDate;
    private String testBaseCurrency;
    private String testTargetCurrency1;
    private String testTargetCurrency2;
    private ExchangeRateValueId testId1;
    private ExchangeRateValueId testId2;
    private ExchangeRateValue testValue1;
    private ExchangeRateValue testValue2;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        repository.deleteAll();
        cacheRepository.deleteAll();

        // Setup test data
        testDate = LocalDate.of(2024, 1, 15);
        testBaseCurrency = "USD";
        testTargetCurrency1 = "EUR";
        testTargetCurrency2 = "GBP";

        // Create cache entry first (required for foreign key)
        ExchangeRatesCacheId cacheId = new ExchangeRatesCacheId(testDate, testBaseCurrency);
        ExchangeRatesCache cache = new ExchangeRatesCache(cacheId);
        cacheRepository.save(cache);

        // Create test exchange rate values
        testId1 = new ExchangeRateValueId(testDate, testBaseCurrency, testTargetCurrency1);
        testValue1 = new ExchangeRateValue(testId1, new BigDecimal("0.85"));

        testId2 = new ExchangeRateValueId(testDate, testBaseCurrency, testTargetCurrency2);
        testValue2 = new ExchangeRateValue(testId2, new BigDecimal("0.73"));
    }

    @Test
    void testSaveAndFindById() {
        // Given
        repository.save(testValue1);

        // When
        Optional<ExchangeRateValue> found = repository.findById(testId1);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId().getDate()).isEqualTo(testDate);
        assertThat(found.get().getId().getBaseCurrency()).isEqualTo(testBaseCurrency);
        assertThat(found.get().getId().getTargetCurrency()).isEqualTo(testTargetCurrency1);
        assertThat(found.get().getRate()).isEqualByComparingTo(new BigDecimal("0.85"));
    }

    @Test
    void testFindByIdDateAndIdBaseCurrency() {
        // Given
        repository.save(testValue1);
        repository.save(testValue2);

        // When
        List<ExchangeRateValue> found = repository.findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);

        // Then
        assertThat(found).hasSize(2);
        assertThat(found).extracting(erv -> erv.getId().getTargetCurrency())
                .containsExactlyInAnyOrder(testTargetCurrency1, testTargetCurrency2);
    }

    @Test
    void testFindByIdDateAndIdBaseCurrency_NoResults() {
        // When
        List<ExchangeRateValue> found = repository.findByIdDateAndIdBaseCurrency(
                LocalDate.of(2024, 12, 31),
                "EUR"
        );

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void testFindByIdDateAndIdBaseCurrencyAndIdTargetCurrency_Found() {
        // Given
        repository.save(testValue1);

        // When
        Optional<ExchangeRateValue> found = repository.findByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
                testDate, testBaseCurrency, testTargetCurrency1
        );

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getRate()).isEqualByComparingTo(new BigDecimal("0.85"));
    }

    @Test
    void testFindByIdDateAndIdBaseCurrencyAndIdTargetCurrency_NotFound() {
        // Given
        repository.save(testValue1);

        // When
        Optional<ExchangeRateValue> found = repository.findByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
                testDate, testBaseCurrency, "JPY"
        );

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void testFindByIdDate() {
        // Given
        repository.save(testValue1);
        repository.save(testValue2);

        // Create another cache and value for different base currency
        ExchangeRatesCacheId cacheId2 = new ExchangeRatesCacheId(testDate, "EUR");
        cacheRepository.save(new ExchangeRatesCache(cacheId2));

        ExchangeRateValueId id3 = new ExchangeRateValueId(testDate, "EUR", "USD");
        repository.save(new ExchangeRateValue(id3, new BigDecimal("1.18")));

        // When
        List<ExchangeRateValue> found = repository.findByIdDate(testDate);

        // Then
        assertThat(found).hasSize(3);
    }

    @Test
    void testFindByIdDate_NoResults() {
        // When
        List<ExchangeRateValue> found = repository.findByIdDate(LocalDate.of(2099, 12, 31));

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void testDeleteByIdDateAndIdBaseCurrency() {
        // Given
        repository.save(testValue1);
        repository.save(testValue2);
        assertThat(repository.count()).isEqualTo(2);

        // When
        repository.deleteByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
        repository.flush();

        // Then
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void testDeleteByIdDateAndIdBaseCurrency_NoEffect() {
        // Given
        repository.save(testValue1);

        // When - delete with different criteria
        repository.deleteByIdDateAndIdBaseCurrency(LocalDate.of(2099, 1, 1), "EUR");
        repository.flush();

        // Then
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void testExistsByIdDateAndIdBaseCurrencyAndIdTargetCurrency_Exists() {
        // Given
        repository.save(testValue1);

        // When
        boolean exists = repository.existsByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
                testDate, testBaseCurrency, testTargetCurrency1
        );

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void testExistsByIdDateAndIdBaseCurrencyAndIdTargetCurrency_NotExists() {
        // When
        boolean exists = repository.existsByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
                testDate, testBaseCurrency, "JPY"
        );

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void testCountByIdDateAndIdBaseCurrency() {
        // Given
        repository.save(testValue1);
        repository.save(testValue2);

        // When
        long count = repository.countByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);

        // Then
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testCountByIdDateAndIdBaseCurrency_NoResults() {
        // When
        long count = repository.countByIdDateAndIdBaseCurrency(
                LocalDate.of(2099, 12, 31),
                "EUR"
        );

        // Then
        assertThat(count).isEqualTo(0);
    }

    @Test
    void testFindRatesByDateAndBaseCurrency() {
        // Given
        repository.save(testValue1);
        repository.save(testValue2);

        // When
        List<ExchangeRateValue> rates = repository.findRatesByDateAndBaseCurrency(testDate, testBaseCurrency);

        // Then
        assertThat(rates).hasSize(2);
        assertThat(rates).extracting(ExchangeRateValue::getRate)
                .containsExactlyInAnyOrder(new BigDecimal("0.85"), new BigDecimal("0.73"));
    }

    @Test
    void testFindRatesByDateAndBaseCurrency_NoResults() {
        // When
        List<ExchangeRateValue> rates = repository.findRatesByDateAndBaseCurrency(
                LocalDate.of(2099, 12, 31),
                "EUR"
        );

        // Then
        assertThat(rates).isEmpty();
    }

    @Test
    void testSaveMultipleRatesForSameDate() {
        // Given
        ExchangeRateValueId id3 = new ExchangeRateValueId(testDate, testBaseCurrency, "JPY");
        ExchangeRateValue value3 = new ExchangeRateValue(id3, new BigDecimal("110.50"));

        // When
        repository.save(testValue1);
        repository.save(testValue2);
        repository.save(value3);

        // Then
        assertThat(repository.count()).isEqualTo(3);
        List<ExchangeRateValue> allForDate = repository.findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
        assertThat(allForDate).hasSize(3);
    }

    @Test
    void testUpdateExistingRate() {
        // Given
        repository.save(testValue1);
        ExchangeRateValueId savedId = testValue1.getId();

        // When - Update rate
        ExchangeRateValue updatedValue = new ExchangeRateValue(savedId, new BigDecimal("0.90"));
        repository.save(updatedValue);

        // Then
        Optional<ExchangeRateValue> found = repository.findById(savedId);
        assertThat(found).isPresent();
        assertThat(found.get().getRate()).isEqualByComparingTo(new BigDecimal("0.90"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void testCompositeKeyUniqueness() {
        // Given
        repository.save(testValue1);

        // When - Try to save with same composite key but different rate
        ExchangeRateValue duplicateKey = new ExchangeRateValue(testId1, new BigDecimal("0.99"));
        repository.save(duplicateKey);

        // Then - Should replace, not create duplicate
        assertThat(repository.count()).isEqualTo(1);
        Optional<ExchangeRateValue> found = repository.findById(testId1);
        assertThat(found).isPresent();
        assertThat(found.get().getRate()).isEqualByComparingTo(new BigDecimal("0.99"));
    }

    @Test
    void testFindAll() {
        // Given
        repository.save(testValue1);
        repository.save(testValue2);

        // When
        List<ExchangeRateValue> allValues = repository.findAll();

        // Then
        assertThat(allValues).hasSize(2);
    }

    @Test
    void testDeleteAll() {
        // Given
        repository.save(testValue1);
        repository.save(testValue2);
        assertThat(repository.count()).isEqualTo(2);

        // When
        repository.deleteAll();

        // Then
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void testMultipleDatesAndBaseCurrencies() {
        // Given
        LocalDate date2 = LocalDate.of(2024, 1, 16);
        ExchangeRatesCacheId cacheId2 = new ExchangeRatesCacheId(date2, testBaseCurrency);
        cacheRepository.save(new ExchangeRatesCache(cacheId2));

        ExchangeRatesCacheId cacheId3 = new ExchangeRatesCacheId(testDate, "EUR");
        cacheRepository.save(new ExchangeRatesCache(cacheId3));

        repository.save(testValue1); // 2024-01-15, USD -> EUR

        ExchangeRateValueId id2 = new ExchangeRateValueId(date2, testBaseCurrency, testTargetCurrency1);
        repository.save(new ExchangeRateValue(id2, new BigDecimal("0.86"))); // 2024-01-16, USD -> EUR

        ExchangeRateValueId id3 = new ExchangeRateValueId(testDate, "EUR", "USD");
        repository.save(new ExchangeRateValue(id3, new BigDecimal("1.18"))); // 2024-01-15, EUR -> USD

        // When
        List<ExchangeRateValue> date1Usd = repository.findByIdDateAndIdBaseCurrency(testDate, "USD");
        List<ExchangeRateValue> date2Usd = repository.findByIdDateAndIdBaseCurrency(date2, "USD");
        List<ExchangeRateValue> date1Eur = repository.findByIdDateAndIdBaseCurrency(testDate, "EUR");

        // Then
        assertThat(date1Usd).hasSize(1);
        assertThat(date2Usd).hasSize(1);
        assertThat(date1Eur).hasSize(1);
        assertThat(repository.count()).isEqualTo(3);
    }

    @Test
    void testBigDecimalPrecision() {
        // Given - Test with high precision rate
        ExchangeRateValueId id = new ExchangeRateValueId(testDate, testBaseCurrency, "BTC");
        BigDecimal highPrecisionRate = new BigDecimal("0.0000234567");
        ExchangeRateValue value = new ExchangeRateValue(id, highPrecisionRate);

        // When
        repository.save(value);
        Optional<ExchangeRateValue> found = repository.findById(id);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getRate()).isEqualByComparingTo(highPrecisionRate);
    }

    @Test
    void testFindByIdDate_MultipleBaseCurrencies() {
        // Given
        repository.save(testValue1); // USD -> EUR

        ExchangeRatesCacheId cacheId2 = new ExchangeRatesCacheId(testDate, "GBP");
        cacheRepository.save(new ExchangeRatesCache(cacheId2));

        ExchangeRateValueId id2 = new ExchangeRateValueId(testDate, "GBP", "EUR");
        repository.save(new ExchangeRateValue(id2, new BigDecimal("1.16"))); // GBP -> EUR

        // When
        List<ExchangeRateValue> allForDate = repository.findByIdDate(testDate);

        // Then
        assertThat(allForDate).hasSize(2);
        assertThat(allForDate).extracting(erv -> erv.getId().getBaseCurrency())
                .containsExactlyInAnyOrder("USD", "GBP");
    }

    @Test
    void testCountByIdDateAndIdBaseCurrency_AfterDelete() {
        // Given
        repository.save(testValue1);
        repository.save(testValue2);
        assertThat(repository.countByIdDateAndIdBaseCurrency(testDate, testBaseCurrency)).isEqualTo(2);

        // When
        repository.delete(testValue1);
        repository.flush();

        // Then
        assertThat(repository.countByIdDateAndIdBaseCurrency(testDate, testBaseCurrency)).isEqualTo(1);
    }
}
