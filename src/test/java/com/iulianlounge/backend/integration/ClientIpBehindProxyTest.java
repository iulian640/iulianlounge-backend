package com.iulianlounge.backend.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        HttpClient client = HttpClient.newHttpClient();
        int[] statuses = new int[3];

        for (int i = 0; i < statuses.length; i++) {
            HttpRequest login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("Forwarded", "for=10.0.0." + i)
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            statuses[i] = client.send(login, HttpResponse.BodyHandlers.discarding()).statusCode();
        }

        assertArrayEquals(new int[] {400, 400, 429}, statuses);
    }

    @Test
    void productionUsesTheStrategyThisTestChecks() throws IOException {
        Properties production = new Properties();
        try (InputStream file = getClass().getResourceAsStream("/application-prod.properties")) {
            production.load(file);
        }

        assertEquals("native", production.getProperty("server.forward-headers-strategy"));
    }
}
