package com.shipmonk.testingday;

import com.shipmonk.testingday.dto.FixerResponse;
import com.shipmonk.testingday.service.ExchangeRatesApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping(
    path = "/api/v1/rates"
)
public class ExchangeRatesController {

    private static final Logger logger = LoggerFactory.getLogger(ExchangeRatesController.class);
    private static final String DEFAULT_BASE_CURRENCY = "USD";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private ExchangeRatesApiService apiService;

    public ExchangeRatesController(ExchangeRatesApiService apiService) {
        this.apiService = apiService;
    }

    /**
     * Get exchange rates for a specific day <br/>
     * Example: GET /api/v1/rates/2013-12-24 <br/>
     * This endpoint uses asynchronous API calls to Fixer.io
     */
    @RequestMapping(method = RequestMethod.GET, path = "/{day}")
    public FixerResponse getRates(@PathVariable String day) {
        logger.info("Received request for exchange rates on day: {}", day);

        // Validate date format
        validateDateFormat(day);

        return new FixerResponse();
    }

    /**
     * Validates that the date string follows the YYYY-MM-DD pattern
     *
     * @param date The date string to validate
     * @throws ResponseStatusException if the date format is invalid
     */
    private void validateDateFormat(String date) {
        if (date == null || date.trim().isEmpty()) {
            logger.error("Date parameter is null or empty");
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Date parameter is required"
            );
        }

        try {
            LocalDate.parse(date, DATE_FORMATTER);
            logger.debug("Date format validation passed for: {}", date);
        } catch (DateTimeParseException e) {
            logger.error("Invalid date format provided: {}. Expected format: YYYY-MM-DD", date);
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                String.format("Invalid date format: '%s'. Expected format: YYYY-MM-DD (e.g., 2013-12-24)", date)
            );
        }
    }

}
