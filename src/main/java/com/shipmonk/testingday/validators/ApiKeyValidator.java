package com.shipmonk.testingday.validators;

import com.shipmonk.testingday.exception.InvalidInputException;

public class ApiKeyValidator {

    /**
     * Validates API key parameter
     *
     * @param apiKey The API key to validate
     * @throws InvalidInputException if inputs are invalid
     */
    public static void validateInputs(String apiKey) {

        // Validate API key
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new InvalidInputException(
                "API key is required",
                "The 'apiKey' parameter cannot be null or empty. Please provide a valid Fixer.io API key."
            );
        }
    }
}
