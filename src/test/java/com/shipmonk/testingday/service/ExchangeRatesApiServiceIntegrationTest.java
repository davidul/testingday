package com.shipmonk.testingday.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipmonk.testingday.config.AsyncConfiguration;
import com.shipmonk.testingday.external.FixerResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for ExchangeRatesApiService
 * Uses MockWebServer to simulate Fixer.io API responses
 * Tests actual HTTP calls, retry logic, and Spring integration
 */
@SpringBootTest(classes = {ExchangeRatesApiService.class})
@Import({ExchangeRatesApiServiceIntegrationTest.TestConfig.class, AsyncConfiguration.class})
@ActiveProfiles("test")
class ExchangeRatesApiServiceIntegrationTest {

    /**
     * Test configuration to provide required beans
     */
    @TestConfiguration
    static class TestConfig {

        @Bean
        public RestTemplate restTemplate() {
            return new RestTemplate();
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private ExchangeRatesApiService service;

    @Autowired
    private ObjectMapper objectMapper;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Replace the Fixer API base URL with our mock server
        String mockUrl = mockWebServer.url("").toString();
        // Remove trailing slash
        String baseUrl = mockUrl.substring(0, mockUrl.length() - 1);
        ReflectionTestUtils.setField(service, "fixerBaseUrl", baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ==================== Successful API Call Tests ====================

    @Test
    void testFetchExchangeRatesAsync_SuccessfulResponse() throws Exception {
        // Given
        String successResponse = createSuccessResponseJson();
        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR,GBP,JPY",
            "test-api-key"
        );

        // Then
        FixerResponse result = future.get(10, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getBase()).isEqualTo("USD");
        assertThat(result.getDate()).isEqualTo("2024-01-15");
        assertThat(result.getRates()).hasSize(3);
        assertThat(result.getRates()).containsKeys("EUR", "GBP", "JPY");

        // Verify the request
        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).contains("2024-01-15");
        assertThat(request.getPath()).contains("access_key=test-api-key");
        assertThat(request.getPath()).contains("base=USD");
        assertThat(request.getPath()).contains("symbols=EUR,GBP,JPY");
    }

    @Test
    void testFetchExchangeRatesFreePlanWithRetry_Success() throws Exception {
        // Given
        String successResponse = createSuccessResponseJson();
        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesFreePlanWithRetry(
            "2024-01-15",
            "free-api-key",
            0
        );

        // Then
        FixerResponse result = future.get(10, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();

        // Verify request doesn't include base parameter (free plan)
        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).contains("access_key=free-api-key");
        assertThat(request.getPath()).doesNotContain("base=");
    }

    // ==================== Error Handling Tests ====================

    @Test
    void testFetchExchangeRatesAsync_InvalidApiKey_401Error() throws Exception {
        // Given
        String errorResponse = createErrorResponseJson(101, "invalid_access_key");
        mockWebServer.enqueue(new MockResponse()
            .setBody(errorResponse)
            .setResponseCode(401)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR,GBP",
            "invalid-key"
        );

        // Then
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasRootCauseInstanceOf(HttpClientErrorException.class);

        // Verify request was made
        RecordedRequest request = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
    }

    @Test
    void testFetchExchangeRatesAsync_BaseCurrencyRestricted_SwitchesToFreeMode() throws Exception {
        // Given - First request fails with 105 error
        String errorResponse = createErrorResponseJson(105, "base_currency_access_restricted");
        mockWebServer.enqueue(new MockResponse()
            .setBody(errorResponse)
            .setResponseCode(403)
            .addHeader("Content-Type", "application/json"));

        // Second request succeeds (free plan)
        String successResponse = createSuccessResponseJson();
        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR,GBP",
            "restricted-key"
        );

        // Then
        FixerResponse result = future.get(15, TimeUnit.SECONDS); // Longer timeout for retry
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();

        // Verify two requests were made
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);

        RecordedRequest firstRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(firstRequest.getPath()).contains("base=USD");

        RecordedRequest secondRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(secondRequest.getPath()).doesNotContain("base=USD"); // Free plan
    }

    // ==================== Retry Logic Tests ====================

    @Test
    void testFetchExchangeRatesFreePlanWithRetry_RateLimitError_Retries() throws Exception {
        // Given - First request fails with rate limit
        String rateLimitError = createErrorResponseJson(104, "rate_limit_reached");
        mockWebServer.enqueue(new MockResponse()
            .setBody(rateLimitError)
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json"));

        // Second request succeeds
        String successResponse = createSuccessResponseJson();
        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesFreePlanWithRetry(
            "2024-01-15",
            "test-key",
            0
        );

        // Then
        FixerResponse result = future.get(15, TimeUnit.SECONDS); // Longer timeout for retry
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();

        // Verify two requests were made (initial + 1 retry)
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    void testFetchExchangeRatesFreePlanWithRetry_MaxRetriesExceeded() throws Exception {
        // Given - All requests fail
        String errorResponse = createErrorResponseJson(104, "rate_limit_reached");

        // Enqueue 4 error responses (1 initial + 3 retries)
        for (int i = 0; i < 4; i++) {
            mockWebServer.enqueue(new MockResponse()
                .setBody(errorResponse)
                .setResponseCode(429)
                .addHeader("Content-Type", "application/json"));
        }

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesFreePlanWithRetry(
            "2024-01-15",
            "test-key",
            0
        );

        // Then
        assertThatThrownBy(() -> future.get(30, TimeUnit.SECONDS)) // Longer timeout for multiple retries
            .isInstanceOf(ExecutionException.class);

        // Verify 4 requests were made (initial + 3 retries)
        assertThat(mockWebServer.getRequestCount()).isEqualTo(4);
    }

    @Test
    void testFetchExchangeRatesFreePlanWithRetry_GenericError_Retries() throws Exception {
        // Given - First request returns server error
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        // Second request succeeds
        String successResponse = createSuccessResponseJson();
        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesFreePlanWithRetry(
            "2024-01-15",
            "test-key",
            0
        );

        // Then
        FixerResponse result = future.get(15, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();

        // Verify two requests were made
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    // ==================== Validation Tests ====================

    @Test
    void testFetchExchangeRatesAsync_InvalidDateFormat_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            "2024/01/15", // Wrong format
            "USD",
            "EUR",
            "test-key"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid date format");

        // Verify no request was made
        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
    }

    @Test
    void testFetchExchangeRatesAsync_NullApiKey_ThrowsException() {
        // When/Then
        assertThatThrownBy(() -> service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR",
            null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("API key cannot be null");

        // Verify no request was made
        assertThat(mockWebServer.getRequestCount()).isEqualTo(0);
    }

    // ==================== HTTP Status Code Tests ====================

    @Test
    void testFetchExchangeRatesAsync_BadRequest_400() throws Exception {
        // Given
        String errorResponse = createErrorResponseJson(400, "invalid_request");
        mockWebServer.enqueue(new MockResponse()
            .setBody(errorResponse)
            .setResponseCode(400)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR",
            "test-key"
        );

        // Then
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);
    }

    @Test
    void testFetchExchangeRatesAsync_Forbidden_403() throws Exception {
        // Given
        String errorResponse = createErrorResponseJson(403, "subscription_plan_restricted");
        mockWebServer.enqueue(new MockResponse()
            .setBody(errorResponse)
            .setResponseCode(403)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR",
            "test-key"
        );

        // Then
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);
    }

    @Test
    void testFetchExchangeRatesAsync_NotFound_404() throws Exception {
        // Given
        String errorResponse = createErrorResponseJson(404, "not_found");
        mockWebServer.enqueue(new MockResponse()
            .setBody(errorResponse)
            .setResponseCode(404)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR",
            "test-key"
        );

        // Then
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class);
    }

    // ==================== Response Parsing Tests ====================

    @Test
    void testFetchExchangeRatesAsync_ValidatesSuccessFlag_False() throws Exception {
        // Given - Response with success=false
        String unsuccessfulResponse = "{\"success\":false,\"error\":{\"code\":999,\"type\":\"test_error\"}}";
        mockWebServer.enqueue(new MockResponse()
            .setBody(unsuccessfulResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR",
            "test-key"
        );

        // Then
        assertThatThrownBy(() -> future.get(10, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasMessageContaining("Failed to fetch exchange rates");
    }

    @Test
    void testFetchExchangeRatesAsync_HandlesMultipleCurrencies() throws Exception {
        // Given
        String response = """
            {
                "success": true,
                "historical": true,
                "date": "2024-01-15",
                "timestamp": 1705276800,
                "base": "USD",
                "rates": {
                    "EUR": 0.85,
                    "GBP": 0.73,
                    "JPY": 110.50,
                    "CAD": 1.25,
                    "CHF": 0.92,
                    "AUD": 1.35
                }
            }
            """;
        mockWebServer.enqueue(new MockResponse()
            .setBody(response)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR,GBP,JPY,CAD,CHF,AUD",
            "test-key"
        );

        // Then
        FixerResponse result = future.get(10, TimeUnit.SECONDS);
        assertThat(result.getRates()).hasSize(6);
        assertThat(result.getRates()).containsKeys("EUR", "GBP", "JPY", "CAD", "CHF", "AUD");
    }

    // ==================== Async Behavior Tests ====================

    @Test
    void testFetchExchangeRatesAsync_ExecutesAsynchronously() throws Exception {
        // Given
        String successResponse = createSuccessResponseJson();
        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBodyDelay(100, TimeUnit.MILLISECONDS)); // Add delay to verify async

        // When
        CompletableFuture<FixerResponse> future = service.fetchExchangeRatesAsync(
            "2024-01-15",
            "USD",
            "EUR",
            "test-key"
        );


        // Wait for completion
        FixerResponse result = future.get(10, TimeUnit.SECONDS);
        assertThat(result).isNotNull();

        // Verify request was made
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    // ==================== Helper Methods ====================

    private String createSuccessResponseJson() {
        return """
            {
                "success": true,
                "historical": true,
                "date": "2024-01-15",
                "timestamp": 1705276800,
                "base": "USD",
                "rates": {
                    "EUR": 0.85,
                    "GBP": 0.73,
                    "JPY": 110.50
                }
            }
            """;
    }

    private String createErrorResponseJson(int code, String type) {
        return String.format(
            "{\"success\":false,\"error\":{\"code\":%d,\"type\":\"%s\",\"info\":\"Test error information\"}}",
            code, type
        );
    }
}
