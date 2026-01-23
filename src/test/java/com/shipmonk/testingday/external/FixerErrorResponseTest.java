package com.shipmonk.testingday.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FixerErrorResponse parsing
 */
class FixerErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testParseErrorResponse() throws Exception {
        // Given - JSON error response from Fixer.io
        String jsonError = """
            {
              "success": false,
              "error": {
                "code": 105,
                "type": "base_currency_access_restricted",
                "info": "Base currency USD is not supported"
              }
            }
            """;

        // When - Parse JSON to FixerErrorResponse
        FixerErrorResponse errorResponse = objectMapper.readValue(jsonError, FixerErrorResponse.class);

        // Then - Verify all fields are correctly parsed
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.isSuccess()).isFalse();
        assertThat(errorResponse.getError()).isNotNull();
        assertThat(errorResponse.getError().getCode()).isEqualTo(105);
        assertThat(errorResponse.getError().getType()).isEqualTo("base_currency_access_restricted");
        assertThat(errorResponse.getError().getInfo()).isEqualTo("Base currency USD is not supported");
    }

    @Test
    void testParseErrorResponseWithoutInfo() throws Exception {
        // Given - JSON error response without info field
        String jsonError = """
            {
              "success": false,
              "error": {
                "code": 101,
                "type": "invalid_access_key"
              }
            }
            """;

        // When
        FixerErrorResponse errorResponse = objectMapper.readValue(jsonError, FixerErrorResponse.class);

        // Then
        assertThat(errorResponse).isNotNull();
        assertThat(errorResponse.isSuccess()).isFalse();
        assertThat(errorResponse.getError().getCode()).isEqualTo(101);
        assertThat(errorResponse.getError().getType()).isEqualTo("invalid_access_key");
        assertThat(errorResponse.getError().getInfo()).isNull();
    }

    @Test
    void testParseMultipleErrorTypes() throws Exception {
        // Test error code 201 - invalid_base_currency
        String jsonError1 = """
            {
              "success": false,
              "error": {
                "code": 201,
                "type": "invalid_base_currency",
                "info": "You have entered an invalid base currency."
              }
            }
            """;

        FixerErrorResponse error1 = objectMapper.readValue(jsonError1, FixerErrorResponse.class);
        assertThat(error1.getError().getCode()).isEqualTo(201);
        assertThat(error1.getError().getType()).isEqualTo("invalid_base_currency");

        // Test error code 202 - invalid_currency_codes
        String jsonError2 = """
            {
              "success": false,
              "error": {
                "code": 202,
                "type": "invalid_currency_codes",
                "info": "You have provided one or more invalid currency codes."
              }
            }
            """;

        FixerErrorResponse error2 = objectMapper.readValue(jsonError2, FixerErrorResponse.class);
        assertThat(error2.getError().getCode()).isEqualTo(202);
        assertThat(error2.getError().getType()).isEqualTo("invalid_currency_codes");
    }

    @Test
    void testErrorDetailToString() {
        // Given
        FixerErrorResponse.ErrorDetail errorDetail = new FixerErrorResponse.ErrorDetail();
        errorDetail.setCode(105);
        errorDetail.setType("base_currency_access_restricted");
        errorDetail.setInfo("Test info");

        // When
        String result = errorDetail.toString();

        // Then
        assertThat(result).contains("code=105");
        assertThat(result).contains("type='base_currency_access_restricted'");
        assertThat(result).contains("info='Test info'");
    }

    @Test
    void testCreateDescriptiveErrorMessage() throws Exception {
        // Given
        String jsonError = """
            {
              "success": false,
              "error": {
                "code": 105,
                "type": "base_currency_access_restricted",
                "info": "Your current subscription plan does not support this feature."
              }
            }
            """;

        FixerErrorResponse errorResponse = objectMapper.readValue(jsonError, FixerErrorResponse.class);

        // When - Create descriptive error message (like in the service)
        String errorMessage = String.format(
            "Fixer.io API error [%d]: %s - %s",
            errorResponse.getError().getCode(),
            errorResponse.getError().getType(),
            errorResponse.getError().getInfo() != null ?
                errorResponse.getError().getInfo() : "No additional info"
        );

        // Then
        assertThat(errorMessage).isEqualTo(
            "Fixer.io API error [105]: base_currency_access_restricted - " +
            "Your current subscription plan does not support this feature."
        );
    }

    @Test
    void testCreateDescriptiveErrorMessageWithoutInfo() throws Exception {
        // Given
        String jsonError = """
            {
              "success": false,
              "error": {
                "code": 101,
                "type": "invalid_access_key"
              }
            }
            """;

        FixerErrorResponse errorResponse = objectMapper.readValue(jsonError, FixerErrorResponse.class);

        // When
        String errorMessage = String.format(
            "Fixer.io API error [%d]: %s - %s",
            errorResponse.getError().getCode(),
            errorResponse.getError().getType(),
            errorResponse.getError().getInfo() != null ?
                errorResponse.getError().getInfo() : "No additional info"
        );

        // Then
        assertThat(errorMessage).isEqualTo(
            "Fixer.io API error [101]: invalid_access_key - No additional info"
        );
    }
}
