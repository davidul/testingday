package com.shipmonk.testingday.repository;

import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ExchangeRatesCacheRepository
 * Uses @DataJpaTest for lightweight repository testing with an embedded database
 */
@DataJpaTest
@ActiveProfiles("test")
class ExchangeRatesCacheRepositoryTest {

    @Autowired
    private ExchangeRatesCacheRepository repository;

    private LocalDate testDate;
    private String testBaseCurrency;
    private ExchangeRatesCacheId testId;
    private ExchangeRatesCache testCache;

    @BeforeEach
    void setUp() {
        // Clean up before each test
        repository.deleteAll();

        // Setup test data
        testDate = LocalDate.of(2024, 1, 15);
        testBaseCurrency = "USD";
        testId = new ExchangeRatesCacheId(testDate, testBaseCurrency);
        testCache = new ExchangeRatesCache(testId);
    }

    @Test
    void testSaveAndFindById() {
        // Given
        repository.save(testCache);

        // When
        Optional<ExchangeRatesCache> found = repository.findById(testId);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId().getDate()).isEqualTo(testDate);
        assertThat(found.get().getId().getBaseCurrency()).isEqualTo(testBaseCurrency);
    }

    @Test
    void testFindByIdDateAndIdBaseCurrency_Found() {
        // Given
        repository.save(testCache);

        // When
        Optional<ExchangeRatesCache> found = repository.findByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getId().getDate()).isEqualTo(testDate);
        assertThat(found.get().getId().getBaseCurrency()).isEqualTo(testBaseCurrency);
    }

    @Test
    void testFindByIdDateAndIdBaseCurrency_NotFound() {
        // When
        Optional<ExchangeRatesCache> found = repository.findByIdDateAndIdBaseCurrency(
            LocalDate.of(2024, 12, 31),
            "EUR"
        );

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void testFindByIdDateAndIdBaseCurrency_WrongDate() {
        // Given
        repository.save(testCache);

        // When
        Optional<ExchangeRatesCache> found = repository.findByIdDateAndIdBaseCurrency(
            LocalDate.of(2024, 12, 31),
            testBaseCurrency
        );

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void testFindByIdDateAndIdBaseCurrency_WrongBaseCurrency() {
        // Given
        repository.save(testCache);

        // When
        Optional<ExchangeRatesCache> found = repository.findByIdDateAndIdBaseCurrency(
            testDate,
            "EUR"
        );

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    void testExistsByIdDateAndIdBaseCurrency_Exists() {
        // Given
        repository.save(testCache);

        // When
        boolean exists = repository.existsByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);

        // Then
        assertThat(exists).isTrue();
    }

    @Test
    void testExistsByIdDateAndIdBaseCurrency_NotExists() {
        // When
        boolean exists = repository.existsByIdDateAndIdBaseCurrency(
            LocalDate.of(2024, 12, 31),
            "EUR"
        );

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void testExistsByIdDateAndIdBaseCurrency_WrongDate() {
        // Given
        repository.save(testCache);

        // When
        boolean exists = repository.existsByIdDateAndIdBaseCurrency(
            LocalDate.of(2024, 12, 31),
            testBaseCurrency
        );

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void testExistsByIdDateAndIdBaseCurrency_WrongBaseCurrency() {
        // Given
        repository.save(testCache);

        // When
        boolean exists = repository.existsByIdDateAndIdBaseCurrency(testDate, "EUR");

        // Then
        assertThat(exists).isFalse();
    }

    @Test
    void testDeleteByIdDateAndIdBaseCurrency() {
        // Given
        repository.save(testCache);
        assertThat(repository.existsByIdDateAndIdBaseCurrency(testDate, testBaseCurrency)).isTrue();

        // When
        repository.deleteByIdDateAndIdBaseCurrency(testDate, testBaseCurrency);
        repository.flush();

        // Then
        assertThat(repository.existsByIdDateAndIdBaseCurrency(testDate, testBaseCurrency)).isFalse();
    }

    @Test
    void testDeleteByIdDateAndIdBaseCurrency_NotExisting() {
        // When/Then - should not throw exception
        repository.deleteByIdDateAndIdBaseCurrency(
            LocalDate.of(2024, 12, 31),
            "EUR"
        );
        repository.flush();

        // Verify nothing was affected
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void testSaveMultipleEntriesWithDifferentDates() {
        // Given
        LocalDate date1 = LocalDate.of(2024, 1, 1);
        LocalDate date2 = LocalDate.of(2024, 1, 2);

        ExchangeRatesCacheId id1 = new ExchangeRatesCacheId(date1, "USD");
        ExchangeRatesCacheId id2 = new ExchangeRatesCacheId(date2, "USD");

        ExchangeRatesCache cache1 = new ExchangeRatesCache(id1);
        ExchangeRatesCache cache2 = new ExchangeRatesCache(id2);

        // When
        repository.save(cache1);
        repository.save(cache2);

        // Then
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.existsByIdDateAndIdBaseCurrency(date1, "USD")).isTrue();
        assertThat(repository.existsByIdDateAndIdBaseCurrency(date2, "USD")).isTrue();
    }

    @Test
    void testSaveMultipleEntriesWithDifferentBaseCurrencies() {
        // Given
        ExchangeRatesCacheId id1 = new ExchangeRatesCacheId(testDate, "USD");
        ExchangeRatesCacheId id2 = new ExchangeRatesCacheId(testDate, "EUR");

        ExchangeRatesCache cache1 = new ExchangeRatesCache(id1);
        ExchangeRatesCache cache2 = new ExchangeRatesCache(id2);

        // When
        repository.save(cache1);
        repository.save(cache2);

        // Then
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.existsByIdDateAndIdBaseCurrency(testDate, "USD")).isTrue();
        assertThat(repository.existsByIdDateAndIdBaseCurrency(testDate, "EUR")).isTrue();
    }

    @Test
    void testUpdateExistingEntry() {
        // Given
        ExchangeRatesCache savedCache = repository.save(testCache);
        ExchangeRatesCacheId savedId = savedCache.getId();

        // When - Update by saving again with same ID
        ExchangeRatesCache updatedCache = new ExchangeRatesCache(savedId);
        repository.save(updatedCache);

        // Then
        assertThat(repository.count()).isEqualTo(1);
        Optional<ExchangeRatesCache> found = repository.findById(savedId);
        assertThat(found).isPresent();
    }

    @Test
    void testDeleteAll() {
        // Given
        repository.save(testCache);

        LocalDate date2 = LocalDate.of(2024, 1, 2);
        ExchangeRatesCacheId id2 = new ExchangeRatesCacheId(date2, "EUR");
        repository.save(new ExchangeRatesCache(id2));

        assertThat(repository.count()).isEqualTo(2);

        // When
        repository.deleteAll();

        // Then
        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void testCompositeKeyUniqueness() {
        // Given
        ExchangeRatesCache cache1 = new ExchangeRatesCache(
            new ExchangeRatesCacheId(testDate, "USD")
        );
        repository.save(cache1);

        // When - Try to save with same composite key
        ExchangeRatesCache cache2 = new ExchangeRatesCache(
            new ExchangeRatesCacheId(testDate, "USD")
        );
        repository.save(cache2);

        // Then - Should replace, not create duplicate
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void testFindAll() {
        // Given
        repository.save(testCache);

        ExchangeRatesCacheId id2 = new ExchangeRatesCacheId(
            LocalDate.of(2024, 1, 2),
            "EUR"
        );
        repository.save(new ExchangeRatesCache(id2));

        // When
        var allCaches = repository.findAll();

        // Then
        assertThat(allCaches).hasSize(2);
    }
}
