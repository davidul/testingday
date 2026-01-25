package com.shipmonk.testingday.mapper;

import com.shipmonk.testingday.dto.ExchangeRatesCacheDto;
import com.shipmonk.testingday.dto.ExchangeRateValueDto;
import com.shipmonk.testingday.entity.ExchangeRatesCache;
import com.shipmonk.testingday.entity.ExchangeRatesCacheId;
import com.shipmonk.testingday.entity.ExchangeRateValue;
import com.shipmonk.testingday.entity.ExchangeRateValueId;
import com.shipmonk.testingday.external.FixerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper class for converting FixerResponse to ExchangeRatesCache and ExchangeRateValue entities
 */
public class FixerResponseMapper {

    private static final Logger logger = LoggerFactory.getLogger(FixerResponseMapper.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ==================== DTO Conversion Methods ====================

    /**
     * Converts a FixerResponse to ExchangeRatesCacheDto
     *
     * @param fixerResponse The Fixer API response
     * @return ExchangeRatesCacheDto populated with data from the response
     * @throws IllegalArgumentException if fixerResponse is null or invalid
     */
    public static ExchangeRatesCacheDto toDto(FixerResponse fixerResponse) {
        if (fixerResponse == null) {
            throw new IllegalArgumentException("FixerResponse cannot be null");
        }

        if (!fixerResponse.isSuccess()) {
            throw new IllegalArgumentException("FixerResponse indicates failure - cannot convert");
        }

        if (fixerResponse.getDate() == null || fixerResponse.getDate().isEmpty()) {
            throw new IllegalArgumentException("FixerResponse date cannot be null or empty");
        }

        if (fixerResponse.getBase() == null || fixerResponse.getBase().isEmpty()) {
            throw new IllegalArgumentException("FixerResponse base currency cannot be null or empty");
        }

        // Parse the date string to LocalDate
        LocalDate date;
        try {
            date = LocalDate.parse(fixerResponse.getDate(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            logger.error("Failed to parse date from FixerResponse: {}", fixerResponse.getDate(), e);
            throw new IllegalArgumentException("Invalid date format in FixerResponse: " + fixerResponse.getDate(), e);
        }

        String baseCurrency = fixerResponse.getBase();

        // Create the DTO
        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto(date, baseCurrency);

        // Convert all rates to ExchangeRateValueDto objects
        if (fixerResponse.getRates() != null && !fixerResponse.getRates().isEmpty()) {
            List<ExchangeRateValueDto> rateDtos = new ArrayList<>();

            for (Map.Entry<String, Double> rateEntry : fixerResponse.getRates().entrySet()) {
                String targetCurrency = rateEntry.getKey();
                Double rateValue = rateEntry.getValue();

                if (targetCurrency != null && !targetCurrency.isEmpty() && rateValue != null && rateValue > 0) {
                    ExchangeRateValueDto rateDto = new ExchangeRateValueDto(
                        targetCurrency,
                        BigDecimal.valueOf(rateValue)
                    );
                    rateDtos.add(rateDto);
                } else {
                    logger.warn("Skipping invalid rate entry: currency={}, rate={}", targetCurrency, rateValue);
                }
            }

            dto.setRates(rateDtos);
            logger.info("Converted FixerResponse to ExchangeRatesCacheDto with {} rate values", rateDtos.size());
        } else {
            logger.warn("FixerResponse contains no rates data");
        }

        return dto;
    }

    /**
     * Converts ExchangeRatesCacheDto to FixerResponse
     *
     * @param dto The ExchangeRatesCacheDto
     * @return FixerResponse object
     */
    public static FixerResponse toFixerResponse(ExchangeRatesCacheDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ExchangeRatesCacheDto cannot be null");
        }

        if (dto.getDate() == null) {
            throw new IllegalArgumentException("ExchangeRatesCacheDto date cannot be null");
        }

        if (dto.getBaseCurrency() == null || dto.getBaseCurrency().isEmpty()) {
            throw new IllegalArgumentException("ExchangeRatesCacheDto base currency cannot be null or empty");
        }

        FixerResponse response = new FixerResponse();
        response.setSuccess(true);
        response.setHistorical(true);
        response.setDate(dto.getDate().format(DATE_FORMATTER));
        response.setBase(dto.getBaseCurrency());

        // Convert rate DTOs to map
        Map<String, Double> rates = new java.util.HashMap<>();
        if (dto.getRates() != null) {
            for (ExchangeRateValueDto rateDto : dto.getRates()) {
                if (rateDto.getTargetCurrency() != null && rateDto.getRate() != null) {
                    rates.put(rateDto.getTargetCurrency(), rateDto.getRate().doubleValue());
                }
            }
        }
        response.setRates(rates);

        // Set timestamp
        response.setTimestamp(System.currentTimeMillis() / 1000);

        return response;
    }

    /**
     * Converts ExchangeRatesCache entity to ExchangeRatesCacheDto
     *
     * @param entity The ExchangeRatesCache entity
     * @return ExchangeRatesCacheDto
     */
    public static ExchangeRatesCacheDto entityToDto(ExchangeRatesCache entity) {
        if (entity == null || entity.getId() == null) {
            throw new IllegalArgumentException("ExchangeRatesCache or its ID cannot be null");
        }

        ExchangeRatesCacheDto dto = new ExchangeRatesCacheDto(
            entity.getId().getDate(),
            entity.getId().getBaseCurrency()
        );

        // Set execution date
        dto.setExecDate(entity.getExecDate());
        // Convert exchange rate values to DTOs
        if (entity.getExchangeRateValues() != null) {
            List<ExchangeRateValueDto> rateDtos = entity.getExchangeRateValues().stream()
                .filter(value -> value.getId() != null && value.getRate() != null)
                .map(value -> new ExchangeRateValueDto(
                    value.getId().getTargetCurrency(),
                    value.getRate()
                ))
                .collect(Collectors.toList());

            dto.setRates(rateDtos);
        }

        return dto;
    }

    /**
     * Converts ExchangeRatesCacheDto to ExchangeRatesCache entity
     *
     * @param dto The ExchangeRatesCacheDto
     * @return ExchangeRatesCache entity
     */
    public static ExchangeRatesCache dtoToEntity(ExchangeRatesCacheDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ExchangeRatesCacheDto cannot be null");
        }

        if (dto.getDate() == null) {
            throw new IllegalArgumentException("ExchangeRatesCacheDto date cannot be null");
        }

        if (dto.getBaseCurrency() == null || dto.getBaseCurrency().isEmpty()) {
            throw new IllegalArgumentException("ExchangeRatesCacheDto base currency cannot be null or empty");
        }

        // Create the cache entity with composite ID
        ExchangeRatesCacheId cacheId = new ExchangeRatesCacheId(dto.getDate(), dto.getBaseCurrency());
        ExchangeRatesCache cache = new ExchangeRatesCache(cacheId);

        // Set execution date
        cache.setExecDate(dto.getExecDate());

        // Convert rate DTOs to entities
        if (dto.getRates() != null) {
            for (ExchangeRateValueDto rateDto : dto.getRates()) {
                if (rateDto.getTargetCurrency() != null && rateDto.getRate() != null) {
                    ExchangeRateValueId valueId = new ExchangeRateValueId(
                        dto.getDate(),
                        dto.getBaseCurrency(),
                        rateDto.getTargetCurrency()
                    );
                    ExchangeRateValue value = new ExchangeRateValue(valueId, rateDto.getRate());
                    cache.addExchangeRateValue(value);
                }
            }
        }

        return cache;
    }

    /**
     * Converts a FixerResponse to ExchangeRatesCacheDto with validation
     * Returns null if conversion fails instead of throwing exception
     *
     * @param fixerResponse The Fixer API response
     * @return ExchangeRatesCacheDto or null if conversion fails
     */
    public static ExchangeRatesCacheDto toDtoSafe(FixerResponse fixerResponse) {
        try {
            return toDto(fixerResponse);
        } catch (Exception e) {
            logger.error("Failed to convert FixerResponse to ExchangeRatesCacheDto", e);
            return null;
        }
    }

    /**
     * Converts a list of ExchangeRatesCache entities to DTOs
     *
     * @param entities The list of entities
     * @return List of ExchangeRatesCacheDto
     */
    public static List<ExchangeRatesCacheDto> entitiesToDtos(List<ExchangeRatesCache> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }

        return entities.stream()
            .map(FixerResponseMapper::entityToDto)
            .collect(Collectors.toList());
    }

    /**
     * Converts a list of DTOs to ExchangeRatesCache entities
     *
     * @param dtos The list of DTOs
     * @return List of ExchangeRatesCache entities
     */
    public static List<ExchangeRatesCache> dtosToEntities(List<ExchangeRatesCacheDto> dtos) {
        if (dtos == null) {
            return new ArrayList<>();
        }

        return dtos.stream()
            .map(FixerResponseMapper::dtoToEntity)
            .collect(Collectors.toList());
    }
}
