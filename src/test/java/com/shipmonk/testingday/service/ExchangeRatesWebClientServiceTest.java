package com.shipmonk.testingday.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipmonk.testingday.exception.InvalidInputException;
import com.shipmonk.testingday.external.FixerResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for ExchangeRatesWebClientService using MockWebServer
 * Tests reactive HTTP calls, retry logic, and error handling
 */
class ExchangeRatesWebClientServiceTest {

    private MockWebServer mockWebServer;
    private ExchangeRatesWebClientService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        // Create WebClient pointing to mock server
        String baseUrl = mockWebServer.url("/").toString();
        WebClient webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();

        objectMapper = new ObjectMapper();
        service = new ExchangeRatesWebClientService(webClient, objectMapper);

        // Configure service to use mock server URL (remove trailing slash)
        service.setFixerBaseUrl(baseUrl.substring(0, baseUrl.length() - 1));
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void testFetchExchangeRatesFreePlan_SuccessfulResponse() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "success": true,
                "timestamp": 1519296206,
                "base": "EUR",
                "date": "2024-01-15",
                "rates": {
                    "USD": 1.23,
                    "GBP": 0.85,
                    "JPY": 130.45
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        Mono<FixerResponse> result = service.fetchExchangeRatesFreePlan("2024-01-15", "test-api-key");

        // Assert using StepVerifier
        StepVerifier.create(result)
            .assertNext(response -> {
                assertTrue(response.isSuccess());
                assertEquals("EUR", response.getBase());
                assertEquals("2024-01-15", response.getDate());
                assertNotNull(response.getRates());
                assertEquals(3, response.getRates().size());
                assertEquals(new BigDecimal("1.23"), response.getRates().get("USD"));
                assertEquals(new BigDecimal("0.85"), response.getRates().get("GBP"));
                assertEquals(new BigDecimal("130.45"), response.getRates().get("JPY"));
            })
            .verifyComplete();

        // Verify request
        RecordedRequest request = mockWebServer.takeRequest();
        assertEquals("GET", request.getMethod());
        assertTrue(request.getPath().contains("2024-01-15"));
        assertTrue(request.getPath().contains("access_key=test-api-key"));
    }

    @Test
    void testFetchExchangeRates_WithBaseCurrencyAndSymbols() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "success": true,
                "timestamp": 1519296206,
                "base": "USD",
                "date": "2024-01-15",
                "rates": {
                    "EUR": 0.85,
                    "GBP": 0.73
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        Mono<FixerResponse> result = service.fetchExchangeRates(
            "2024-01-15",
            "USD",
            "EUR,GBP",
            "paid-api-key"
        );

        // Assert
        StepVerifier.create(result)
            .assertNext(response -> {
                assertTrue(response.isSuccess());
                assertEquals("USD", response.getBase());
                assertEquals(2, response.getRates().size());
            })
            .verifyComplete();

        // Verify request parameters
        RecordedRequest request = mockWebServer.takeRequest();
        String path = request.getPath();
        assertTrue(path.contains("base=USD"));
        assertTrue(path.contains("symbols=EUR,GBP"));
    }

    @Test
    void testFetchExchangeRatesFreePlan_RateLimitWithRetry() {
        // Arrange - First 2 requests fail with 429, third succeeds
        String errorResponse = """
            {
                "success": false,
                "error": {
                    "code": 104,
                    "type": "rate_limit_reached",
                    "info": "Your monthly API request volume has been reached."
                }
            }
            """;

        String successResponse = """
            {
                "success": true,
                "timestamp": 1519296206,
                "base": "EUR",
                "date": "2024-01-15",
                "rates": {
                    "USD": 1.23
                }
            }
            """;

        // Queue responses: 429, 429, success
        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(429)
            .setBody(errorResponse)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(429)
            .setBody(errorResponse)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        mockWebServer.enqueue(new MockResponse()
            .setBody(successResponse)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        Mono<FixerResponse> result = service.fetchExchangeRatesFreePlan("2024-01-15", "test-api-key");

        // Assert - Should eventually succeed after retries
        StepVerifier.create(result)
            .assertNext(response -> {
                assertTrue(response.isSuccess());
                assertEquals(new BigDecimal("1.23"), response.getRates().get("USD"));
            })
            .verifyComplete();

        // Verify 3 requests were made (2 failures + 1 success)
        assertEquals(3, mockWebServer.getRequestCount());
    }

    @Test
    void testFetchExchangeRatesFreePlan_UnauthorizedError() {
        // Arrange
        String errorResponse = """
            {
                "success": false,
                "error": {
                    "code": 101,
                    "type": "invalid_access_key",
                    "info": "You have not supplied a valid API Access Key."
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(401)
            .setBody(errorResponse)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        Mono<FixerResponse> result = service.fetchExchangeRatesFreePlan("2024-01-15", "invalid-key");

        // Assert - Should fail with error
        StepVerifier.create(result)
            .expectErrorMatches(throwable ->
                throwable instanceof RuntimeException &&
                throwable.getMessage().contains("Fixer.io API error") &&
                throwable.getMessage().contains("invalid_access_key"))
            .verify();
    }

    @Test
    void testFetchExchangeRatesFreePlan_ForbiddenError() {
        // Arrange
        String errorResponse = """
            {
                "success": false,
                "error": {
                    "code": 105,
                    "type": "function_access_restricted",
                    "info": "Your current subscription plan does not support this API Function."
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(403)
            .setBody(errorResponse)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        Mono<FixerResponse> result = service.fetchExchangeRates(
            "2024-01-15",
            "USD",
            "EUR",
            "free-api-key"
        );

        // Assert
        StepVerifier.create(result)
            .expectErrorMatches(throwable ->
                throwable instanceof RuntimeException &&
                throwable.getMessage().contains("function_access_restricted"))
            .verify();
    }

    @Test
    void testValidateInputs_NullDate() {
        // Act & Assert
        assertThrows(InvalidInputException.class, () ->
            service.fetchExchangeRatesFreePlan(null, "test-api-key").block()
        );
    }

    @Test
    void testValidateInputs_EmptyDate() {
        // Act & Assert
        assertThrows(InvalidInputException.class, () ->
            service.fetchExchangeRatesFreePlan("", "test-api-key").block()
        );
    }

    @Test
    void testValidateInputs_InvalidDateFormat() {
        // Act & Assert
        assertThrows(InvalidInputException.class, () ->
            service.fetchExchangeRatesFreePlan("01-15-2024", "test-api-key").block()
        );
    }

    @Test
    void testValidateInputs_InvalidMonth() {
        // Act & Assert
        assertThrows(InvalidInputException.class, () ->
            service.fetchExchangeRatesFreePlan("2024-13-01", "test-api-key").block()
        );
    }

    @Test
    void testValidateInputs_InvalidDay() {
        // Act & Assert
        assertThrows(InvalidInputException.class, () ->
            service.fetchExchangeRatesFreePlan("2024-01-32", "test-api-key").block()
        );
    }

    @Test
    void testValidateInputs_InvalidLeapYear() {
        // Act & Assert
        assertThrows(InvalidInputException.class, () ->
            service.fetchExchangeRatesFreePlan("2023-02-29", "test-api-key").block()
        );
    }

    @Test
    void testValidateInputs_NullApiKey() {
        // Act & Assert
        assertThrows(InvalidInputException.class, () ->
            service.fetchExchangeRatesFreePlan("2024-01-15", null).block()
        );
    }

    @Test
    void testValidateInputs_EmptyApiKey() {
        // Act & Assert
        assertThrows(InvalidInputException.class, () ->
            service.fetchExchangeRatesFreePlan("2024-01-15", "").block()
        );
    }

    @Test
    void testFetchExchangeRatesFreePlan_NetworkError() {
        // Arrange - Shutdown server to simulate network error
        try {
            mockWebServer.shutdown();
        } catch (IOException e) {
            fail("Failed to shutdown mock server");
        }

        // Act
        Mono<FixerResponse> result = service.fetchExchangeRatesFreePlan("2024-01-15", "test-api-key");

        // Assert - Should fail with connection error
        StepVerifier.create(result)
            .expectError()
            .verify();
    }

    @Test
    void testFetchExchangeRatesFreePlan_MalformedJsonResponse() {
        // Arrange
        mockWebServer.enqueue(new MockResponse()
            .setBody("{ invalid json }")
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        Mono<FixerResponse> result = service.fetchExchangeRatesFreePlan("2024-01-15", "test-api-key");

        // Assert - Should fail with parsing error
        StepVerifier.create(result)
            .expectError()
            .verify();
    }

    @Test
    void testFetchExchangeRatesFreePlan_EmptyRatesMap() throws InterruptedException {
        // Arrange
        String jsonResponse = """
            {
                "success": true,
                "timestamp": 1519296206,
                "base": "EUR",
                "date": "2024-01-15",
                "rates": {}
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setBody(jsonResponse)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        Mono<FixerResponse> result = service.fetchExchangeRatesFreePlan("2024-01-15", "test-api-key");

        // Assert
        StepVerifier.create(result)
            .assertNext(response -> {
                assertTrue(response.isSuccess());
                assertNotNull(response.getRates());
                assertTrue(response.getRates().isEmpty());
            })
            .verifyComplete();
    }

    @Test
    void testFetchExchangeRatesFreePlan_NotFoundError() {
        // Arrange
        String errorResponse = """
            {
                "success": false,
                "error": {
                    "code": 301,
                    "type": "invalid_date",
                    "info": "You have entered an invalid date."
                }
            }
            """;

        mockWebServer.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody(errorResponse)
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE));

        // Act
        Mono<FixerResponse> result = service.fetchExchangeRatesFreePlan("2024-99-99", "test-api-key");

        // Assert
        StepVerifier.create(result)
            .expectErrorMatches(throwable ->
                throwable instanceof RuntimeException &&
                throwable.getMessage().contains("invalid_date"))
            .verify();
    }
}
