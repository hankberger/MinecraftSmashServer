package dev.hanks.network;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class PrivateHttpTest {
    private static final String SECRET = "test-secret-that-is-at-least-thirty-two-characters";
    private HttpResponse<String> request(PrivateHttp server, String secret, String body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + "/status"));
            if (secret != null) request.header("Authorization", "Bearer " + secret);
            if (body != null) request.POST(HttpRequest.BodyPublishers.ofString(body));
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    @Test void credentialsAreRequiredBeforeHandlerExecutes() throws Exception {
        var calls = new AtomicInteger();
        try (var server = new PrivateHttp("127.0.0.1", 0, SECRET, (m,p,b) -> {
            calls.incrementAndGet(); return new PrivateHttp.Response(200, new Wire.Reply(true, "Ready"));
        })) {
            assertEquals(401, request(server, null, null).statusCode());
            assertEquals(401, request(server, SECRET + "wrong", null).statusCode());
            assertEquals(0, calls.get());
            assertEquals(200, request(server, SECRET, null).statusCode());
            assertEquals(1, calls.get());
        }
    }
    @Test void oversizedRequestsAreRejectedBeforeMutation() throws Exception {
        var calls = new AtomicInteger();
        try (var server = new PrivateHttp("127.0.0.1", 0, SECRET, (m,p,b) -> {
            calls.incrementAndGet(); return new PrivateHttp.Response(200, "ok");
        })) {
            assertEquals(413, request(server, SECRET, "x".repeat(32769)).statusCode());
            assertEquals(0, calls.get());
        }
    }
    @Test void invalidRequestsDoNotLeakExceptionDetails() throws Exception {
        try (var server = new PrivateHttp("127.0.0.1", 0, SECRET, (m,p,b) -> { throw new IllegalArgumentException("sensitive detail"); })) {
            var response = request(server, SECRET, "{}");
            assertEquals(400, response.statusCode());
            assertFalse(response.body().contains("sensitive"));
        }
    }
    @Test void weakSecretsFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> new PrivateHttp("127.0.0.1", 0, "short", (m,p,b) -> null));
    }
}
