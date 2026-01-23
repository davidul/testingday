package com.shipmonk.testingday.dto;

import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import com.shipmonk.testingday.entity.ExchangeRateValue;
import com.shipmonk.testingday.entity.ExchangeRateValueId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExchangeRatesCache DTO and Mapper
 */
class ExchangeRatesCacheDtoTest {

    @Test
    void testEntityToDto() {
        // Given
        LocalDate date = LocalDate.of(2024, 1, 15);
        String baseCurrency = "USD";

        ExchangeRatesCacheId cacheId = new ExchangeRatesCacheId(date, baseCurrency);
        ExchangeRatesCache entity = new ExchangeRatesCache(cacheId);

        ExchangeRateValueId valueId1 = new ExchangeRateValueId(date, baseCurrency, "EUR");
        ExchangeRateValue value1 = new ExchangeRateValue(valueId1, new BigDecimal("0.85"));
        entity.addExchangeRateValue(value1);

        ExchangeRateValueId valueId2 = new ExchangeRateValueId(date, baseCurrency, "GBP");
        ExchangeRateValue value2 = new ExchangeRateValue(valueId2, new BigDecimal("0.73"));
        entity.addExchangeRateValue(value2);

        // When
        ExchangeRatesCacheDto dto = ExchangeRatesCacheMapper.toDto(entity);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getDate()).isEqualTo(date);
        assertThat(dto.getBaseCurrency()).isEqualTo(baseCurrency);
        assertThat(dto.getRates()).hasSize(2);
        assertThat(dto.getRates()).extracting(ExchangeRateValueDto::getTargetCurrency)
                .containsExactlyInAnyOrder("EUR", "GBP");
    }

    @Test
    void testDtoToEntity() {
        // Given
        LocalDate date = LocalDate.of(2024, 1, 15);
        String baseCurrency = "USD";

        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto(date, baseCurrency);
        dto.addRate("EUR", new BigDecimal("0.85"));
        dto.addRate("GBP", new BigDecimal("0.73"));

        // When
        ExchangeRatesCache entity = ExchangeRatesCacheMapper.toEntity(dto);

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getId().getDate()).isEqualTo(date);
        assertThat(entity.getId().getBaseCurrency()).isEqualTo(baseCurrency);
        assertThat(entity.getExchangeRateValues()).hasSize(2);
    }

    @Test
    void testRoundTrip() {
        // Given
        LocalDate date = LocalDate.of(2024, 1, 15);
        String baseCurrency = "USD";

        ExchangeRatesCacheId cacheId = new ExchangeRatesCacheId(date, baseCurrency);
        ExchangeRatesCache originalEntity = new ExchangeRatesCache(cacheId);

        ExchangeRateValueId valueId = new ExchangeRateValueId(date, baseCurrency, "EUR");
        ExchangeRateValue value = new ExchangeRateValue(valueId, new BigDecimal("0.85"));
        originalEntity.addExchangeRateValue(value);

        // When - Convert to DTO and back
        ExchangeRatesCacheDto dto = ExchangeRatesCacheMapper.toDto(originalEntity);
        ExchangeRatesCache convertedEntity = ExchangeRatesCacheMapper.toEntity(dto);

        // Then
        assertThat(convertedEntity.getId().getDate()).isEqualTo(originalEntity.getId().getDate());
        assertThat(convertedEntity.getId().getBaseCurrency()).isEqualTo(originalEntity.getId().getBaseCurrency());
        assertThat(convertedEntity.getExchangeRateValues()).hasSize(originalEntity.getExchangeRateValues().size());
    }

    @Test
    void testDtoCreation() {
        // Given
        LocalDate date = LocalDate.of(2024, 1, 15);
        String baseCurrency = "USD";

        // When
        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto();
        dto.setDate(date);
        dto.setBaseCurrency(baseCurrency);
        dto.addRate(new ExchangeRateValueDto("EUR", new BigDecimal("0.85")));

        // Then
        assertThat(dto.getDate()).isEqualTo(date);
        assertThat(dto.getBaseCurrency()).isEqualTo(baseCurrency);
        assertThat(dto.getRates()).hasSize(1);
        assertThat(dto.getRates().get(0).getTargetCurrency()).isEqualTo("EUR");
        assertThat(dto.getRates().get(0).getRate()).isEqualByComparingTo(new BigDecimal("0.85"));
    }

    @Test
    void testNullEntity() {
        // When
        ExchangeRatesCacheDto dto = ExchangeRatesCacheMapper.toDto(null);

        // Then
        assertThat(dto).isNull();
    }

    @Test
    void testNullDto() {
        // When
        ExchangeRatesCache entity = ExchangeRatesCacheMapper.toEntity(null);

        // Then
        assertThat(entity).isNull();
    }

    @Test
    void testEmptyRates() {
        // Given
        LocalDate date = LocalDate.of(2024, 1, 15);
        String baseCurrency = "USD";

        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto(date, baseCurrency);

        // When
        ExchangeRatesCache entity = ExchangeRatesCacheMapper.toEntity(dto);

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getExchangeRateValues()).isEmpty();
    }
}
