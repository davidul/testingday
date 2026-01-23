package com.shipmonk.testingday.repository;

import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ExchangeRatesCacheRepository extends JpaRepository<ExchangeRatesCache, ExchangeRatesCacheId> {

    /**
     * Find exchange rates cache by date and base currency
     */
    Optional<ExchangeRatesCache> findByIdDateAndIdBaseCurrency(LocalDate date, String baseCurrency);

    /**
     * Check if exchange rates exist for a specific date and base currency
     */
    boolean existsByIdDateAndIdBaseCurrency(LocalDate date, String baseCurrency);

    /**
     * Delete exchange rates cache by date and base currency
     */
    void deleteByIdDateAndIdBaseCurrency(LocalDate date, String baseCurrency);
}
