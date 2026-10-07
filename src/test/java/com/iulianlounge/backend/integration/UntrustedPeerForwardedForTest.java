package com.iulianlounge.backend.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "jwt.secret=dGVzdC1zZWNyZXQtcXVlLXRpZW5lLW1hcy1kZS0zMi1ieXRlcyEh",
        "auth.rate-limit.max-per-minute=2",
        "server.forward-headers-strategy=native",
        "server.tomcat.remoteip.internal-proxies=192.0.2.1"
})
class UntrustedPeerForwardedForTest {

    @LocalServerPort
    private int port;

    @Test
    void anXForwardedForFromAPeerThatIsNotTheProxyIsIgnored() throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            int[] statuses = {
                    login(client, "198.51.100.1"),
                    login(client, "198.51.100.2"),
                    login(client, "198.51.100.3")
            };

            assertArrayEquals(new int[] {400, 400, 429}, statuses);
        }
    }

    private int login(HttpClient client, String forwardedFor) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", forwardedFor)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
