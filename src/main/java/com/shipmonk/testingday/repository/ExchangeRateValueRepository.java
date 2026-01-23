package com.shipmonk.testingday.repository;

import com.shipmonk.testingday.entity.ExchangeRateValue;
import com.shipmonk.testingday.entity.ExchangeRateValueId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRateValueRepository extends JpaRepository<ExchangeRateValue, ExchangeRateValueId> {

    /**
     * Find all exchange rate values for a specific date and base currency
     */
    List<ExchangeRateValue> findByIdDateAndIdBaseCurrency(LocalDate date, String baseCurrency);

    /**
     * Find specific exchange rate value by date, base currency, and target currency
     */
    Optional<ExchangeRateValue> findByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
            LocalDate date, String baseCurrency, String targetCurrency);

    /**
     * Find all exchange rate values for a specific date
     */
    List<ExchangeRateValue> findByIdDate(LocalDate date);

    /**
     * Delete all exchange rate values for a specific date and base currency
     */
    void deleteByIdDateAndIdBaseCurrency(LocalDate date, String baseCurrency);

    /**
     * Check if exchange rate exists for specific currencies and date
     */
    boolean existsByIdDateAndIdBaseCurrencyAndIdTargetCurrency(
            LocalDate date, String baseCurrency, String targetCurrency);

    /**
     * Count exchange rate values for a specific date and base currency
     */
    long countByIdDateAndIdBaseCurrency(LocalDate date, String baseCurrency);

    /**
     * Custom query to find all rates as a map-like structure
     */
    @Query("SELECT erv FROM ExchangeRateValue erv WHERE erv.id.date = :date AND erv.id.baseCurrency = :baseCurrency")
    List<ExchangeRateValue> findRatesByDateAndBaseCurrency(
            @Param("date") LocalDate date,
            @Param("baseCurrency") String baseCurrency);
}
