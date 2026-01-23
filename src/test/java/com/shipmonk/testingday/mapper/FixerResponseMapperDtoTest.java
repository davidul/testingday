package com.shipmonk.testingday.mapper;

import com.shipmonk.testingday.dto.ExchangeRatesCacheDto;
import com.shipmonk.testingday.dto.ExchangeRateValueDto;
import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.external.FixerResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for FixerResponseMapper DTO conversion methods
 */
class FixerResponseMapperDtoTest {

    @Test
    void testFixerResponseToDto_Success() {
        // Given
        FixerResponse response = createValidFixerResponse();

        // When
        ExchangeRatesCacheDto dto = FixerResponseMapper.toDto(response);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(dto.getBaseCurrency()).isEqualTo("USD");
        assertThat(dto.getRates()).hasSize(3);
        assertThat(dto.getRates())
            .extracting(ExchangeRateValueDto::getTargetCurrency)
            .containsExactlyInAnyOrder("EUR", "GBP", "JPY");
    }

    @Test
    void testFixerResponseToDto_VerifyRateValues() {
        // Given
        FixerResponse response = createValidFixerResponse();

        // When
        ExchangeRatesCacheDto dto = FixerResponseMapper.toDto(response);

        // Then
        ExchangeRateValueDto eurRate = dto.getRates().stream()
            .filter(r -> r.getTargetCurrency().equals("EUR"))
            .findFirst()
            .orElse(null);

        assertThat(eurRate).isNotNull();
        assertThat(eurRate.getRate()).isEqualByComparingTo(new BigDecimal("0.85"));
    }

    @Test
    void testFixerResponseToDto_NullResponse() {
        // When/Then
        assertThatThrownBy(() -> FixerResponseMapper.toDto(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("FixerResponse cannot be null");
    }

    @Test
    void testFixerResponseToDto_FailedResponse() {
        // Given
        FixerResponse response = createValidFixerResponse();
        response.setSuccess(false);

        // When/Then
        assertThatThrownBy(() -> FixerResponseMapper.toDto(response))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("failure");
    }

    @Test
    void testDtoToFixerResponse_Success() {
        // Given
        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto(
            LocalDate.of(2024, 1, 15),
            "USD"
        );
        dto.addRate("EUR", new BigDecimal("0.85"));
        dto.addRate("GBP", new BigDecimal("0.73"));

        // When
        FixerResponse response = FixerResponseMapper.toFixerResponse(dto);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getDate()).isEqualTo("2024-01-15");
        assertThat(response.getBase()).isEqualTo("USD");
        assertThat(response.getRates()).hasSize(2);
        assertThat(response.getRates()).containsKeys("EUR", "GBP");
    }

    @Test
    void testDtoToFixerResponse_NullDto() {
        // When/Then
        assertThatThrownBy(() -> FixerResponseMapper.toFixerResponse((ExchangeRatesCacheDto) null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ExchangeRatesCacheDto cannot be null");
    }

    @Test
    void testEntityToDto_NullEntity() {
        // When/Then
        assertThatThrownBy(() -> FixerResponseMapper.entityToDto(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cannot be null");
    }

    @Test
    void testDtoToEntity_Success() {
        // Given
        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto(
            LocalDate.of(2024, 1, 15),
            "USD"
        );
        dto.addRate("EUR", new BigDecimal("0.85"));
        dto.addRate("GBP", new BigDecimal("0.73"));

        // When
        ExchangeRatesCache entity = FixerResponseMapper.dtoToEntity(dto);

        // Then
        assertThat(entity).isNotNull();
        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getId().getDate()).isEqualTo(LocalDate.of(2024, 1, 15));
        assertThat(entity.getId().getBaseCurrency()).isEqualTo("USD");
        assertThat(entity.getExchangeRateValues()).hasSize(2);
    }

    @Test
    void testDtoToEntity_NullDto() {
        // When/Then
        assertThatThrownBy(() -> FixerResponseMapper.dtoToEntity(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ExchangeRatesCacheDto cannot be null");
    }

    @Test
    void testToDtoSafe_Success() {
        // Given
        FixerResponse response = createValidFixerResponse();

        // When
        ExchangeRatesCacheDto dto = FixerResponseMapper.toDtoSafe(response);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getDate()).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void testToDtoSafe_InvalidResponse() {
        // Given
        FixerResponse response = createValidFixerResponse();
        response.setDate("invalid-date");

        // When
        ExchangeRatesCacheDto dto = FixerResponseMapper.toDtoSafe(response);

        // Then
        assertThat(dto).isNull();
    }

    @Test
    void testEntitiesToDtos_NullList() {
        // When
        List<ExchangeRatesCacheDto> dtos = FixerResponseMapper.entitiesToDtos(null);

        // Then
        assertThat(dtos).isNotNull();
        assertThat(dtos).isEmpty();
    }

    @Test
    void testDtosToEntities_Success() {
        // Given
        ExchangeRatesCacheDto dto1 = new ExchangeRatesCacheDto(LocalDate.of(2024, 1, 15), "USD");
        dto1.addRate("EUR", new BigDecimal("0.85"));

        ExchangeRatesCacheDto dto2 = new ExchangeRatesCacheDto(LocalDate.of(2024, 1, 16), "USD");
        dto2.addRate("EUR", new BigDecimal("0.86"));

        List<ExchangeRatesCacheDto> dtos = List.of(dto1, dto2);

        // When
        List<ExchangeRatesCache> entities = FixerResponseMapper.dtosToEntities(dtos);

        // Then
        assertThat(entities).hasSize(2);
        assertThat(entities).extracting(e -> e.getId().getDate())
            .containsExactlyInAnyOrder(
                LocalDate.of(2024, 1, 15),
                LocalDate.of(2024, 1, 16)
            );
    }

    @Test
    void testDtosToEntities_NullList() {
        // When
        List<ExchangeRatesCache> entities = FixerResponseMapper.dtosToEntities(null);

        // Then
        assertThat(entities).isNotNull();
        assertThat(entities).isEmpty();
    }

    @Test
    void testRoundTrip_FixerResponseToDtoToFixerResponse() {
        // Given
        FixerResponse original = createValidFixerResponse();

        // When
        ExchangeRatesCacheDto dto = FixerResponseMapper.toDto(original);
        FixerResponse converted = FixerResponseMapper.toFixerResponse(dto);

        // Then
        assertThat(converted.getDate()).isEqualTo(original.getDate());
        assertThat(converted.getBase()).isEqualTo(original.getBase());
        assertThat(converted.getRates()).hasSize(original.getRates().size());
    }


    // Helper method to create a valid FixerResponse for testing
    private FixerResponse createValidFixerResponse() {
        FixerResponse response = new FixerResponse();
        response.setSuccess(true);
        response.setHistorical(true);
        response.setDate("2024-01-15");
        response.setTimestamp(1705276800L);
        response.setBase("USD");

        Map<String, Double> rates = new HashMap<>();
        rates.put("EUR", 0.85);
        rates.put("GBP", 0.73);
        rates.put("JPY", 110.50);
        response.setRates(rates);

        return response;
    }
}
