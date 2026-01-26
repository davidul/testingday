package com.shipmonk.testingday.validators;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


public class SymbolValidator {

    private static final Logger logger = LoggerFactory.getLogger(SymbolValidator.class);

    /**
     * Validates the symbols parameter format
     * Symbols should be comma-separated, 3-letter uppercase currency codes
     *
     * @param symbols The symbols string to validate
     * @throws ResponseStatusException if the symbols format is invalid
     */
    public static void validateSymbols(String symbols) {
        if (symbols == null || symbols.trim().isEmpty()) {
            logger.error("Symbols parameter is null or empty");
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Symbols parameter cannot be empty"
            );
        }

        // Split by comma and validate each symbol
        String[] symbolArray = symbols.split(",");

        for (String symbol : symbolArray) {
            String trimmedSymbol = symbol.trim();

            if (trimmedSymbol.isEmpty()) {
                logger.error("Empty symbol found in symbols parameter: {}", symbols);
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Symbols parameter contains empty values"
                );
            }

            // Validate that symbol is 3 letters and uppercase
            if (!trimmedSymbol.matches("^[A-Z]{3}$")) {
                logger.error("Invalid symbol format: {}. Expected 3 uppercase letters", trimmedSymbol);
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    String.format("Invalid currency symbol: '%s'. Expected 3 uppercase letters (e.g., USD, EUR, GBP)", trimmedSymbol)
                );
            }
        }

        logger.debug("Symbols validation passed for: {}", symbols);
    }
}
