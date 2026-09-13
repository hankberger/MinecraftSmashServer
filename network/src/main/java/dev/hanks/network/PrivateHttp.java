package dev.hanks.network;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.*;

/** Bounded, authenticated JSON RPC on the private container network. */
public final class PrivateHttp implements AutoCloseable {
    public record Response(int code, Object value) {}
    @FunctionalInterface public interface Handler { Response handle(String method, String path, String body) throws Exception; }
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore inFlight = new Semaphore(32);
    public PrivateHttp(String bind, int port, String token, Handler handler) throws IOException {
        if (token.length() < 32) throw new IllegalArgumentException("Control secret must be at least 32 characters");
        server = HttpServer.create(new InetSocketAddress(bind, port), 32);
        server.setExecutor(executor);
        byte[] expected = ("Bearer " + token).getBytes(StandardCharsets.UTF_8);
        server.createContext("/", exchange -> {
            boolean acquired = false;
            try (exchange) {
                var authorization = exchange.getRequestHeaders().getFirst("Authorization");
                Response result;
                if (authorization == null || !MessageDigest.isEqual(expected, authorization.getBytes(StandardCharsets.UTF_8))) {
                    result = new Response(401, new Wire.Reply(false, "Unauthorized"));
                } else if (!(acquired = inFlight.tryAcquire())) {
                    result = new Response(503, new Wire.Reply(false, "Busy"));
                } else {
                    byte[] bytes = exchange.getRequestBody().readNBytes(32769);
                    if (bytes.length > 32768) result = new Response(413, new Wire.Reply(false, "Request too large"));
                    else try { result = handler.handle(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), new String(bytes, StandardCharsets.UTF_8)); }
                    catch (IllegalArgumentException e) { result = new Response(400, new Wire.Reply(false, "Invalid request")); }
                    catch (Exception e) { result = new Response(503, new Wire.Reply(false, "Control operation unavailable")); }
                }
                byte[] response = Wire.JSON.toJson(result.value()).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(result.code(), response.length);
                exchange.getResponseBody().write(response);
            } finally { if (acquired) inFlight.release(); }
        });
        server.start();
    }
    public static String secret(String environment) {
        String file = System.getenv(environment + "_FILE");
        try {
            String value = file == null ? System.getenv(environment) : java.nio.file.Files.readString(java.nio.file.Path.of(file)).strip();
            if (value == null || value.length() < 32) throw new IllegalArgumentException("Set " + environment + "_FILE to a strong secret");
            return value;
        } catch (IOException e) { throw new IllegalArgumentException("Cannot read " + environment + "_FILE", e); }
    }
    int port() { return server.getAddress().getPort(); }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
    public static final class Client implements AutoCloseable {
        private final String token;
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        public Client(String token) { this.token = token; }
        public CompletableFuture<HttpResponse<String>> call(String base, String path, Object body) {
            var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
            if (body == null) request.GET(); else request.POST(HttpRequest.BodyPublishers.ofString(Wire.JSON.toJson(body)));
            return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        public boolean post(String base, String path, Object body) {
            try { return call(base, path, body).get(4, TimeUnit.SECONDS).statusCode() == 200; }
            catch (Exception e) { return false; }
        }
        @Override public void close() { client.close(); }
    }
}
