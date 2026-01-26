package com.shipmonk.testingday.validators;

import com.shipmonk.testingday.controller.ExchangeRatesController;
import com.shipmonk.testingday.exception.InvalidInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static java.time.LocalDate.of;


public class DateValidator {

    private static final Logger logger = LoggerFactory.getLogger(DateValidator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void validateDateFormat(String date) {
        // Validate date is not null or empty
        if (date == null || date.trim().isEmpty()) {
            throw new InvalidInputException(
                "Date parameter is required",
                "The 'date' parameter cannot be null or empty. Please provide a date in YYYY-MM-DD format."
            );
        }

        // Validate date format
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new InvalidInputException(
                "Invalid date format: " + date,
                String.format("The date '%s' does not match the required format YYYY-MM-DD. Example: 2024-01-15", date)
            );
        }

        // Validate that the date is actually valid (e.g., not 2024-13-01 or 2024-01-32)
        try {
            String[] parts = date.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);

            // Check month is valid (1-12)
            if (month < 1 || month > 12) {
                throw new InvalidInputException(
                    "Invalid month: " + month,
                    String.format("The month value '%d' in date '%s' is invalid. Month must be between 1 and 12.", month, date)
                );
            }

            // Check day is valid for the given month and year
            of(year, month, day); // This validates the actual date

        } catch (java.time.DateTimeException e) {
            throw new InvalidInputException(
                "Invalid date: " + date,
                String.format("The date '%s' is not valid. %s", date, e.getMessage()),
                e
            );
        } catch (NumberFormatException e) {
            throw new InvalidInputException(
                "Invalid date format: " + date,
                String.format("The date '%s' contains non-numeric values. Expected format: YYYY-MM-DD with numeric values.", date),
                e
            );
        }
    }
}
