package com.shipmonk.testingday.dto;

import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import com.shipmonk.testingday.entity.ExchangeRateValue;
import com.shipmonk.testingday.entity.ExchangeRateValueId;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper class to convert between ExchangeRatesCache entities and DTOs
 */
public class ExchangeRatesCacheMapper {

    /**
     * Converts an ExchangeRatesCache entity to a DTO
     *
     * @param entity The entity to convert
     * @return The corresponding DTO
     */
    public static ExchangeRatesCacheDto toDto(ExchangeRatesCache entity) {
        if (entity == null) {
            return null;
        }

        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto();

        if (entity.getId() != null) {
            dto.setDate(entity.getId().getDate());
            dto.setBaseCurrency(entity.getId().getBaseCurrency());
        }

        if (entity.getExchangeRateValues() != null) {
            List<ExchangeRateValueDto> rateDtos = entity.getExchangeRateValues().stream()
                    .map(ExchangeRatesCacheMapper::toRateDto)
                    .collect(Collectors.toList());
            dto.setRates(rateDtos);
        }

        return dto;
    }

    /**
     * Converts an ExchangeRateValue entity to a DTO
     *
     * @param entity The entity to convert
     * @return The corresponding DTO
     */
    public static ExchangeRateValueDto toRateDto(ExchangeRateValue entity) {
        if (entity == null) {
            return null;
        }

        ExchangeRateValueDto dto = new ExchangeRateValueDto();

        if (entity.getId() != null) {
            dto.setTargetCurrency(entity.getId().getTargetCurrency());
        }
        dto.setRate(entity.getRate());

        return dto;
    }

    /**
     * Converts a DTO to an ExchangeRatesCache entity
     *
     * @param dto The DTO to convert
     * @return The corresponding entity
     */
    public static ExchangeRatesCache toEntity(ExchangeRatesCacheDto dto) {
        if (dto == null) {
            return null;
        }

        ExchangeRatesCacheId id = new ExchangeRatesCacheId(dto.getDate(), dto.getBaseCurrency());
        ExchangeRatesCache entity = new ExchangeRatesCache(id);

        if (dto.getRates() != null) {
            for (ExchangeRateValueDto rateDto : dto.getRates()) {
                ExchangeRateValue rateEntity = toRateEntity(rateDto, dto.getDate(), dto.getBaseCurrency());
                entity.addExchangeRateValue(rateEntity);
            }
        }

        return entity;
    }

    /**
     * Converts a DTO to an ExchangeRateValue entity
     *
     * @param dto The DTO to convert
     * @param date The date for the exchange rate
     * @param baseCurrency The base currency
     * @return The corresponding entity
     */
    public static ExchangeRateValue toRateEntity(ExchangeRateValueDto dto,
                                                   java.time.LocalDate date,
                                                   String baseCurrency) {
        if (dto == null) {
            return null;
        }

        ExchangeRateValueId id = new ExchangeRateValueId(
            date,
            baseCurrency,
            dto.getTargetCurrency()
        );

        return new ExchangeRateValue(id, dto.getRate());
    }

    /**
     * Converts a list of entities to DTOs
     *
     * @param entities The list of entities to convert
     * @return The list of corresponding DTOs
     */
    public static List<ExchangeRatesCacheDto> toDtoList(List<ExchangeRatesCache> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }

        return entities.stream()
                .map(ExchangeRatesCacheMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Converts a list of DTOs to entities
     *
     * @param dtos The list of DTOs to convert
     * @return The list of corresponding entities
     */
    public static List<ExchangeRatesCache> toEntityList(List<ExchangeRatesCacheDto> dtos) {
        if (dtos == null) {
            return new ArrayList<>();
        }

        return dtos.stream()
                .map(ExchangeRatesCacheMapper::toEntity)
                .collect(Collectors.toList());
    }
}
