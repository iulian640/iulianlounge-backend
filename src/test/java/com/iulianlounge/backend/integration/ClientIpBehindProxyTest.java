package com.iulianlounge.backend.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtcXVlLXRpZW5lLW1hcy1kZS0zMi1ieXRlcyEh",
        "auth.rate-limit.max-per-minute=2",
        "server.forward-headers-strategy=native"
})
class ClientIpBehindProxyTest {

    @LocalServerPort
    private int port;

    @Test
    void aForwardedHeaderSentByTheClientDoesNotChangeItsRateLimitKey() throws Exception {
        try (HttpClient client = newClient()) {
            int[] statuses = {
                    login(client, "Forwarded", "for=10.0.0.1"),
                    login(client, "Forwarded", "for=10.0.0.2"),
                    login(client, "Forwarded", "for=10.0.0.3")
            };

            assertArrayEquals(new int[] {400, 400, 429}, statuses);
        }
    }

    @Test
    void theClientIpThatTheProxySendsIsTheRateLimitKey() throws Exception {
        try (HttpClient client = newClient()) {
            int[] statuses = {
                    login(client, "X-Forwarded-For", "203.0.113.1"),
                    login(client, "X-Forwarded-For", "203.0.113.1"),
                    login(client, "X-Forwarded-For", "203.0.113.2"),
                    login(client, "X-Forwarded-For", "203.0.113.1")
            };

            assertArrayEquals(new int[] {400, 400, 400, 429}, statuses);
        }
    }

    @Test
    void productionUsesTheNativeForwardHeadersStrategy() throws IOException {
        Properties production = new Properties();
        try (InputStream file = getClass().getResourceAsStream("/application-prod.properties")) {
            assertNotNull(file, "application-prod.properties is missing");
            production.load(file);
        }

        assertEquals("native", production.getProperty("server.forward-headers-strategy"));
    }

    private static HttpClient newClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    private int login(HttpClient client, String header, String value) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(header, value)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
