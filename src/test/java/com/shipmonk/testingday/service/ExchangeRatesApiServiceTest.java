package com.shipmonk.testingday.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipmonk.testingday.exception.InvalidInputException;
import com.shipmonk.testingday.external.FixerErrorResponse;
import com.shipmonk.testingday.external.FixerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExchangeRatesApiService
 * Tests async operations, retry logic, error handling, and API integration
 */
@ExtendWith(MockitoExtension.class)
@Tag("unit")
class ExchangeRatesApiServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    private ExchangeRatesApiService service;

    private String testDate;
    private String testBaseCurrency;
    private String testSymbols;
    private String testApiKey;

    @BeforeEach
    void setUp() {
        service = new ExchangeRatesApiService(restTemplate, objectMapper);

        // Set the default free API key via reflection and initialize the free mode set
        ReflectionTestUtils.setField(service, "defaultFreeApiKey", "test-free-key");
        // Manually invoke the PostConstruct method via reflection
        ReflectionTestUtils.invokeMethod(service, "init");

        testDate = "2024-01-15";
        testBaseCurrency = "USD";
        testSymbols = "EUR,GBP,JPY";
        testApiKey = "test-api-key";
    }

    // ==================== Happy Path Tests ====================

    @Test
    void testFetchExchangeRatesAsync_Success() throws ExecutionException, InterruptedException {
        // Given
        FixerResponse expectedResponse = createSuccessfulFixerResponse();
        when(restTemplate.getForObject(anyString(), eq(FixerResponse.class)))
            .thenReturn(expectedResponse);

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            testDate, testBaseCurrency, testSymbols, testApiKey
        );

        // Then
        FixerResponse result = future.get();
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBase()).isEqualTo("USD");
        assertThat(result.getRates()).hasSize(3);

        verify(restTemplate, times(1)).getForObject(anyString(), eq(FixerResponse.class));
    }

    @Test
    void testFetchExchangeRatesFreePlanWithRetry_Success() throws ExecutionException, InterruptedException {
        // Given
        FixerResponse expectedResponse = createSuccessfulFixerResponse();
        when(restTemplate.getForObject(anyString(), eq(FixerResponse.class)))
            .thenReturn(expectedResponse);

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesFreePlanWithRetry(
            testDate, testApiKey, 0
        );

        // Then
        FixerResponse result = future.get();
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();

        verify(restTemplate, times(1)).getForObject(anyString(), eq(FixerResponse.class));
    }

    // ==================== Input Validation Tests ====================

    @Test
    void testFetchExchangeRatesAsync_NullDate_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            null, testBaseCurrency, testSymbols, testApiKey
        ).get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(InvalidInputException.class)
            .hasMessageContaining("Date parameter is required");
    }

    @Test
    void testFetchExchangeRatesAsync_EmptyDate_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            "", testBaseCurrency, testSymbols, testApiKey
        ).get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(InvalidInputException.class)
            .hasMessageContaining("Date parameter is required");
    }

    @Test
    void testFetchExchangeRatesAsync_InvalidDateFormat_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            "2024/01/15", testBaseCurrency, testSymbols, testApiKey
        ).get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(InvalidInputException.class)
            .hasMessageContaining("Invalid date format");
    }

    @Test
    void testFetchExchangeRatesAsync_NullApiKey_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            testDate, testBaseCurrency, testSymbols, null
        ).get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(InvalidInputException.class)
            .hasMessageContaining("API key is required");
    }

    @Test
    void testFetchExchangeRatesAsync_EmptyApiKey_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            testDate, testBaseCurrency, testSymbols, "   "
        ).get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(InvalidInputException.class)
            .hasMessageContaining("API key is required");
    }

    // ==================== Error Handling Tests ====================

    @Test
    @SuppressWarnings("ConstantConditions")
    void testFetchExchangeRatesAsync_HttpClientErrorException_BadRequest() throws Exception {
        // Given
        HttpClientErrorException exception = HttpClientErrorException.create(
            HttpStatus.BAD_REQUEST,
            "Bad Request",
            null,
            createErrorResponseJson(400, "invalid_request").getBytes(),
            null
        );

        when(restTemplate.getForObject(anyString(), eq(FixerResponse.class)))
            .thenThrow(exception);

        FixerErrorResponse errorResponse = createFixerErrorResponse(400, "invalid_request");
        when(objectMapper.readValue(anyString(), eq(FixerErrorResponse.class)))
            .thenReturn(errorResponse);

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            testDate, testBaseCurrency, testSymbols, testApiKey
        );

        // Then
        assertThatThrownBy(future::get)
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void testFetchExchangeRatesAsync_HttpClientErrorException_Unauthorized() throws Exception {
        // Given
        HttpClientErrorException exception = HttpClientErrorException.create(
            HttpStatus.UNAUTHORIZED,
            "Unauthorized",
            null,
            createErrorResponseJson(101, "invalid_access_key").getBytes(),
            null
        );

        when(restTemplate.getForObject(anyString(), eq(FixerResponse.class)))
            .thenThrow(exception);

        FixerErrorResponse errorResponse = createFixerErrorResponse(101, "invalid_access_key");
        when(objectMapper.readValue(anyString(), eq(FixerErrorResponse.class)))
            .thenReturn(errorResponse);

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            testDate, testBaseCurrency, testSymbols, testApiKey
        );

        // Then
        assertThatThrownBy(future::get)
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(RuntimeException.class);
    }

    // ==================== Free Mode Tests ====================

    @Test
    void testFetchExchangeRatesAsync_FreeModeKey_UsesFreePlanEndpoint() throws ExecutionException, InterruptedException {
        // Given
        String freeApiKey = "test-free-key";
        FixerResponse expectedResponse = createSuccessfulFixerResponse();
        when(restTemplate.getForObject(anyString(), eq(FixerResponse.class)))
            .thenReturn(expectedResponse);

        // When - Use the free key that was initialized
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            testDate, testBaseCurrency, testSymbols, freeApiKey
        );

        // Then
        FixerResponse result = future.get();
        assertThat(result).isNotNull();

        // Verify it called the free plan endpoint (without base currency)
        verify(restTemplate, times(1)).getForObject(
            contains("access_key=" + freeApiKey),
            eq(FixerResponse.class)
        );
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    void testFetchExchangeRatesAsync_BaseCurrencyRestricted_SwitchesToFreeMode() throws Exception {
        // Given - Error 105 (base currency restricted)
        HttpClientErrorException exception = HttpClientErrorException.create(
            HttpStatus.FORBIDDEN,
            "Forbidden",
            null,
            createErrorResponseJson(105, "base_currency_access_restricted").getBytes(),
            null
        );

        FixerResponse successResponse = createSuccessfulFixerResponse();

        when(restTemplate.getForObject(anyString(), eq(FixerResponse.class)))
            .thenThrow(exception)
            .thenReturn(successResponse);

        FixerErrorResponse errorResponse = createFixerErrorResponse(105, "base_currency_access_restricted");
        when(objectMapper.readValue(anyString(), eq(FixerErrorResponse.class)))
            .thenReturn(errorResponse);

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            testDate, testBaseCurrency, testSymbols, testApiKey
        );

        // Then
        FixerResponse result = future.get();
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();

        // Should have made 2 calls: original + retry with free plan
        verify(restTemplate, times(2)).getForObject(anyString(), eq(FixerResponse.class));
    }

    // ==================== Edge Cases ====================

    @Test
    void testFetchExchangeRatesAsync_ValidDateFormats() throws ExecutionException, InterruptedException {
        // Given
        FixerResponse successResponse = createSuccessfulFixerResponse();
        when(restTemplate.getForObject(anyString(), eq(FixerResponse.class)))
            .thenReturn(successResponse);

        // Test various valid date formats
        String[] validDates = {"2024-01-01", "2024-12-31", "2000-01-01", "2099-12-31"};

        for (String validDate : validDates) {
            // When
            CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
                validDate, testBaseCurrency, testSymbols, testApiKey
            );

            // Then
            assertThat(future.get()).isNotNull();
        }
    }

    @Test
    void testFetchExchangeRatesAsync_InvalidDateFormats_ThrowException() {
        // Invalid date formats
        String[] invalidDates = {
            "2024-1-1",      // Missing leading zeros
            "24-01-01",      // Two-digit year
            "2024/01/01",    // Wrong separator
            "01-01-2024",    // Wrong order
            "not-a-date"     // Not a date
        };

        for (String invalidDate : invalidDates) {
            // When/Then
            assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
                invalidDate, testBaseCurrency, testSymbols, testApiKey
            ).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Invalid date format");
        }
    }

    @Test
    void testFetchExchangeRatesAsync_InvalidMonth13_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            "2024-13-01", testBaseCurrency, testSymbols, testApiKey
        ).get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(InvalidInputException.class)
            .hasMessageContaining("Invalid month: 13");
    }

    @Test
    void testFetchExchangeRatesAsync_InvalidDay32_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            "2024-01-32", testBaseCurrency, testSymbols, testApiKey
        ).get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(InvalidInputException.class)
            .hasMessageContaining("Invalid date: 2024-01-32");
    }

    @Test
    void testFetchExchangeRatesAsync_LeapYearValidation_Valid() throws ExecutionException, InterruptedException {
        // Given - 2024 is a leap year, so Feb 29 is valid
        FixerResponse expectedResponse = createSuccessfulFixerResponse();
        when(restTemplate.getForObject(anyString(), eq(FixerResponse.class)))
            .thenReturn(expectedResponse);

        // When - February 29, 2024 should be valid
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-02-29", testBaseCurrency, testSymbols, testApiKey
        );

        // Then - Should not throw exception
        FixerResponse result = future.get();
        assertThat(result).isNotNull();
    }

    @Test
    void testFetchExchangeRatesAsync_LeapYearValidation_Invalid() {
        // When/Then - 2023 is not a leap year
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            "2023-02-29", testBaseCurrency, testSymbols, testApiKey
        ).get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(InvalidInputException.class)
            .hasMessageContaining("Invalid date: 2023-02-29");
    }

    // ==================== Helper Methods ====================

    private FixerResponse createSuccessfulFixerResponse() {
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

    private FixerErrorResponse createFixerErrorResponse(int code, String type) {
        FixerErrorResponse errorResponse = new FixerErrorResponse();
        errorResponse.setSuccess(false);

        FixerErrorResponse.ErrorDetail errorDetail = new FixerErrorResponse.ErrorDetail();
        errorDetail.setCode(code);
        errorDetail.setType(type);
        errorDetail.setInfo("Test error info");

        errorResponse.setError(errorDetail);
        return errorResponse;
    }

    private String createErrorResponseJson(int code, String type) {
        return String.format(
            "{\"success\":false,\"error\":{\"code\":%d,\"type\":\"%s\",\"info\":\"Test error\"}}",
            code, type
        );
    }
}
