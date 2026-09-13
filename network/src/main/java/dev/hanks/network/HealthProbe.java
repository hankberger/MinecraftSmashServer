package dev.hanks.network;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;

/** Small JDK-only Docker health probe; prints no credentials or response payloads. */
public final class HealthProbe {
    public static void main(String[] args) {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()) {
            String token = Files.readString(Path.of(args[1])).strip();
            var request = HttpRequest.newBuilder(URI.create(args[0] + "/health")).timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bearer " + token).GET().build();
            if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() != 200) System.exit(1);
        } catch (Exception e) { System.exit(1); }
    }
}
