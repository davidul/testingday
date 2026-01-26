package com.shipmonk.testingday.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for GlobalExceptionHandler error responses
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testInvalidDateFormat_ReturnsErrorResponse() throws Exception {
        // When - Call endpoint with invalid date format
        MvcResult result = mockMvc.perform(get("/api/v1/rates/2024%2F01%2F15")
                .param("access_key", "test-key"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.description").exists())
            .andExpect(jsonPath("$.timestamp").exists())
            .andReturn();

        // Verify response structure
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);

        assertThat(errorResponse.getStatus()).isEqualTo(400);
        assertThat(errorResponse.getErrorCode()).isEqualTo("INVALID_INPUT");
        assertThat(errorResponse.getMessage()).contains("Invalid date format");
    }

    @Test
    void testMissingAccessKey_ReturnsCustomErrorMessage() throws Exception {
        // When - Call endpoint without access_key parameter
        MvcResult result = mockMvc.perform(get("/api/v1/rates/2024-01-15"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.errorCode").value("MISSING_PARAMETER"))
            .andExpect(jsonPath("$.message").value("API key is required"))
            .andExpect(jsonPath("$.description").exists())
            .andExpect(jsonPath("$.timestamp").exists())
            .andExpect(jsonPath("$.path").exists())
            .andReturn();

        // Verify response structure and content
        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);

        assertThat(errorResponse.getStatus()).isEqualTo(400);
        assertThat(errorResponse.getErrorCode()).isEqualTo("MISSING_PARAMETER");
        assertThat(errorResponse.getMessage()).isEqualTo("API key is required");
        assertThat(errorResponse.getDescription())
            .contains("access_key")
            .contains("required")
            .contains("Example:");
    }

    @Test
    void testInvalidMonth_ReturnsErrorResponse() throws Exception {
        // When - Call endpoint with invalid month
        MvcResult result = mockMvc.perform(get("/api/v1/rates/2024-13-01")
                .param("access_key", "test-key"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.message").value("Invalid month: 13"))
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);

        assertThat(errorResponse.getDescription()).contains("Month must be between 1 and 12");
    }

    @Test
    void testInvalidDay_ReturnsErrorResponse() throws Exception {
        // When - Call endpoint with invalid day
        mockMvc.perform(get("/api/v1/rates/2024-01-32")
                .param("access_key", "test-key"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.description").exists());
    }

    @Test
    void testMissingDate_ReturnsErrorResponse() throws Exception {
        // When - Call endpoint without date parameter
        mockMvc.perform(get("/api/v1/rates")
                .param("access_key", "test-key"))
            .andExpect(status().isNotFound());
    }
}
