package com.shipmonk.testingday.controller;

import com.shipmonk.testingday.config.AsyncConfiguration;
import com.shipmonk.testingday.dto.ExchangeRatesCacheDto;
import com.shipmonk.testingday.exception.CachedRatesNotFoundException;
import com.shipmonk.testingday.service.CachedExchangeRatesService;
import com.shipmonk.testingday.service.ExchangeRatesApiService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ExchangeRatesController
 * Tests the full request/response flow with mocked external dependencies
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Tag("integration")
class ExchangeRatesControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExchangeRatesApiService apiService;

    @MockBean
    private CachedExchangeRatesService cachedService;


    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Replace the Fixer API base URL with our mock server
        String mockUrl = mockWebServer.url("").toString();
        String baseUrl = mockUrl.substring(0, mockUrl.length() - 1);
        ReflectionTestUtils.setField(apiService, "fixerBaseUrl", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ==================== Successful Request Tests ====================

    @Test
    void testGetRates_WithCachedData_ReturnsFromCache() throws Exception {
        // Given
        String date = "2024-01-15";
        String symbols = "USD,GBP,CAD";
        String apiKey = "test-api-key";

        ExchangeRatesCacheDto cachedDto = new ExchangeRatesCacheDto(LocalDate.parse(date), "EUR");
        cachedDto.addRate("USD", new BigDecimal("1.0950"));
        cachedDto.addRate("GBP", new BigDecimal("0.8573"));
        cachedDto.addRate("CAD", new BigDecimal("1.4521"));

        when(cachedService.getCachedRates(eq(LocalDate.parse(date)), eq("EUR")))
            .thenReturn(cachedDto);

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .param("symbols", symbols)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.date").value(date))
            .andExpect(jsonPath("$.base").value("EUR"))
            .andExpect(jsonPath("$.rates.USD").value(1.095))
            .andExpect(jsonPath("$.rates.GBP").value(0.8573))
            .andExpect(jsonPath("$.rates.CAD").value(1.4521));

        // Verify that cache was checked and API was not called (no requests to mock server)
        verify(cachedService, times(1)).getCachedRates(eq(LocalDate.parse(date)), eq("EUR"));
        verify(cachedService, never()).saveToCache(any(), any(), any());
        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
    }

    @Test
    void testGetRates_WithCacheMiss_FetchesFromApiAndCaches() throws Exception {
        // Given
        String date = "2024-01-15";
        String symbols = "USD,GBP,CAD";
        String apiKey = "test-api-key";

        // Mock cache miss
        when(cachedService.getCachedRates(eq(LocalDate.parse(date)), eq("EUR")))
            .thenThrow(new CachedRatesNotFoundException("Cache entry not found"));

        // Mock successful API response
        String successResponse = createSuccessResponseJson(date, "EUR", 1.095, 0.8573, 1.4521);
        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .param("symbols", symbols)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.date").value(date))
            .andExpect(jsonPath("$.base").value("EUR"))
            .andExpect(jsonPath("$.rates.USD").value(1.095))
            .andExpect(jsonPath("$.rates.GBP").value(0.8573))
            .andExpect(jsonPath("$.rates.CAD").value(1.4521));

        // Verify that cache was checked, API was called, and result was saved to cache
        verify(cachedService, times(1)).getCachedRates(eq(LocalDate.parse(date)), eq("EUR"));
        verify(cachedService, times(1)).saveToCache(eq(LocalDate.parse(date)), eq("EUR"), any(ExchangeRatesCacheDto.class));
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void testGetRates_WithDefaultParameters_UsesDefaults() throws Exception {
        // Given
        String date = "2024-01-15";
        String apiKey = "test-api-key";

        // Mock cache miss
        when(cachedService.getCachedRates(eq(LocalDate.parse(date)), eq("EUR")))
            .thenThrow(new CachedRatesNotFoundException("Cache entry not found"));

        // Mock successful API response with default symbols
        String successResponse = createSuccessResponseJson(date, "EUR", 1.095, 0.8573, 1.4521);
        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When & Then - Not specifying symbols parameter
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.date").value(date))
            .andExpect(jsonPath("$.base").value("EUR"));

        verify(cachedService, times(1)).getCachedRates(any(), any());
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    void testGetRates_WithPartialCachedData_FetchesMissingSymbols() throws Exception {
        // Given
        String date = "2024-01-15";
        String symbols = "USD,GBP,CAD,JPY"; // Requesting 4 symbols
        String apiKey = "test-api-key";

        // Mock partial cache hit (only USD, GBP, CAD cached, but JPY is missing)
        ExchangeRatesCacheDto cachedDto = new ExchangeRatesCacheDto(LocalDate.parse(date), "EUR");
        cachedDto.addRate("USD", new BigDecimal("1.0950"));
        cachedDto.addRate("GBP", new BigDecimal("0.8573"));
        cachedDto.addRate("CAD", new BigDecimal("1.4521"));

        when(cachedService.getCachedRates(eq(LocalDate.parse(date)), eq("EUR")))
            .thenReturn(cachedDto);

        // Mock API response for missing symbol (JPY)
        String successResponse = createSuccessResponseJsonWithJPY(date, "EUR", 157.32);
        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .param("symbols", symbols)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.date").value(date))
            .andExpect(jsonPath("$.base").value("EUR"))
            .andExpect(jsonPath("$.rates.USD").value(1.095))
            .andExpect(jsonPath("$.rates.GBP").value(0.8573))
            .andExpect(jsonPath("$.rates.CAD").value(1.4521));

        // Verify that cache was checked and API was called for missing symbol
        verify(cachedService, times(1)).getCachedRates(eq(LocalDate.parse(date)), eq("EUR"));
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    // ==================== Validation Error Tests ====================

    @Test
    void testGetRates_WithInvalidDateFormat_ReturnsBadRequest() throws Exception {
        // Given
        String invalidDate = "2024-13-45"; // Invalid month and day
        String apiKey = "test-api-key";

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", invalidDate)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        verify(cachedService, never()).getCachedRates(any(), any());
    }

    @Test
    void testGetRates_WithInvalidDateFormat_WrongPattern_ReturnsBadRequest() throws Exception {
        // Given
        String invalidDate = "01-15-2024"; // Wrong format (MM-DD-YYYY instead of YYYY-MM-DD)
        String apiKey = "test-api-key";

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", invalidDate)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        verify(cachedService, never()).getCachedRates(any(), any());
    }

    @Test
    void testGetRates_WithMissingApiKey_ReturnsBadRequest() throws Exception {
        // Given
        String date = "2024-01-15";

        // When & Then - Not providing access_key parameter
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        verify(cachedService, never()).getCachedRates(any(), any());
    }

    @Test
    void testGetRates_WithEmptyApiKey_ReturnsBadRequest() throws Exception {
        // Given
        String date = "2024-01-15";

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .param("access_key", "")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        verify(cachedService, never()).getCachedRates(any(), any());
    }

    @Test
    void testGetRates_WithInvalidSymbols_ReturnsBadRequest() throws Exception {
        // Given
        String date = "2024-01-15";
        String invalidSymbols = "US,GB,CA"; // Invalid - too short
        String apiKey = "test-api-key";

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .param("symbols", invalidSymbols)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        verify(cachedService, never()).getCachedRates(any(), any());
    }

    @Test
    void testGetRates_WithInvalidSymbolsContainingNumbers_ReturnsBadRequest() throws Exception {
        // Given
        String date = "2024-01-15";
        String invalidSymbols = "USD,GB1,CAD"; // Invalid - contains number
        String apiKey = "test-api-key";

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .param("symbols", invalidSymbols)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest());

        verify(cachedService, never()).getCachedRates(any(), any());
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testGetRates_WhenApiCallFails_ReturnsBadGateway() throws Exception {
        // Given
        String date = "2024-01-15";
        String symbols = "USD,GBP,CAD";
        String apiKey = "test-api-key";

        // Mock cache miss
        when(cachedService.getCachedRates(eq(LocalDate.parse(date)), eq("EUR")))
            .thenThrow(new CachedRatesNotFoundException("Cache entry not found"));

        // Mock API failure
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .param("symbols", symbols)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError());

        verify(cachedService, times(1)).getCachedRates(eq(LocalDate.parse(date)), eq("EUR"));
        verify(cachedService, never()).saveToCache(any(), any(), any());
    }

    @Test
    void testGetRates_WhenApiReturnsInvalidJson_ReturnsInternalServerError() throws Exception {
        // Given
        String date = "2024-01-15";
        String symbols = "USD,GBP,CAD";
        String apiKey = "test-api-key";

        // Mock cache miss
        when(cachedService.getCachedRates(eq(LocalDate.parse(date)), eq("EUR")))
            .thenThrow(new CachedRatesNotFoundException("Cache entry not found"));

        // Mock API response with invalid JSON
        mockWebServer.enqueue(new MockResponse()
            .setBody("{ invalid json }")
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When & Then
        mockMvc.perform(get("/api/v1/rates/{day}", date)
                .param("symbols", symbols)
                .param("access_key", apiKey)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError());

        verify(cachedService, times(1)).getCachedRates(eq(LocalDate.parse(date)), eq("EUR"));
        verify(cachedService, never()).saveToCache(any(), any(), any());
    }

    // ==================== Helper Methods ====================

    private String createSuccessResponseJson(String date, String base, double usd, double gbp, double cad) {
        return String.format(java.util.Locale.US, """
            {
              "success": true,
              "historical": true,
              "date": "%s",
              "timestamp": 1705276800,
              "base": "%s",
              "rates": {
                "USD": %f,
                "GBP": %f,
                "CAD": %f
              }
            }
            """, date, base, usd, gbp, cad);
    }

    private String createSuccessResponseJsonWithJPY(String date, String base, double jpy) {
        return String.format(java.util.Locale.US, """
            {
              "success": true,
              "historical": true,
              "date": "%s",
              "timestamp": 1705276800,
              "base": "%s",
              "rates": {
                "JPY": %f
              }
            }
            """, date, base, jpy);
    }
}
