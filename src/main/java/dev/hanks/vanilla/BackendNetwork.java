package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Network role and reservation lifecycle. All game state mutations run on the server thread. */
public final class BackendNetwork implements AutoCloseable {
    public enum Role { STANDALONE, LOBBY, ARENA }
    public final Role role = Role.valueOf(System.getenv().getOrDefault("SMASH_ROLE", "STANDALONE").toUpperCase(Locale.ROOT));
    public final String id = System.getenv().getOrDefault("SMASH_NODE_ID", role.name().toLowerCase(Locale.ROOT));
    private final VanillaSmash game;
    private final UUID boot = UUID.randomUUID();
    private final Map<UUID, Wire.Ticket> selections = new LinkedHashMap<>();
    private Map<UUID, String> queueMessages = Map.of();
    private final Map<UUID, String> notices = new HashMap<>();
    private final Set<UUID> arrived = new HashSet<>(), returning = new HashSet<>();
    private final String version = FabricLoader.getInstance().getModContainer("smash_vanilla").orElseThrow().getMetadata().getVersion().getFriendlyString();
    private Wire.Reservation reservation;
    private String phase = "IDLE";
    private boolean draining, startRequested, closing;
    private long expiresAt, drainSandboxAt, queueRevision = -1;
    private UUID queueCoordinator;
    private PrivateHttp http;
    private volatile Wire.Status status;
    private volatile long publishedAt;
    public BackendNetwork(VanillaSmash game) { this.game = game; }
    public boolean lobby() { return role == Role.LOBBY; }
    public boolean arena() { return role == Role.ARENA; }
    public boolean enabled() { return role != Role.STANDALONE; }
    public void start() {
        if (!enabled()) return;
        if (!id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid node id");
        publish();
        try {
            http = new PrivateHttp(System.getenv().getOrDefault("SMASH_CONTROL_BIND", "127.0.0.1"),
                    Integer.parseInt(System.getenv().getOrDefault("SMASH_CONTROL_PORT", "8081")),
                    PrivateHttp.secret("SMASH_CONTROL_SECRET"), (method, path, body) -> {
                if (method.equals("GET") && path.equals("/status")) return new PrivateHttp.Response(200, status);
                if (method.equals("GET") && path.equals("/health")) {
                    boolean alive = System.nanoTime() - publishedAt < TimeUnit.SECONDS.toNanos(10);
                    return new PrivateHttp.Response(alive ? 200 : 503, new Wire.Reply(alive, alive ? "Ticking" : "Server tick stalled"));
                }
                if (!method.equals("POST")) return new PrivateHttp.Response(404, new Wire.Reply(false, "Unknown route"));
                return game.server.submit(() -> control(path, body)).get(2, TimeUnit.SECONDS);
            });
        } catch (java.io.IOException e) { throw new IllegalStateException("Cannot start backend control API", e); }
        VanillaSmash.LOG.info("SMASH_NODE_READY id={} role={} boot={}", id, role, boot);
    }
    private PrivateHttp.Response response(boolean ok, String message) {
        publish(); return new PrivateHttp.Response(ok ? 200 : 409, new Wire.Reply(ok, message));
    }
    private PrivateHttp.Response control(String path, String body) {
        switch (path) {
            case "/reserve" -> {
                if (!arena()) return response(false, "Not an arena");
                var next = Wire.JSON.fromJson(body, Wire.Reservation.class);
                if (reservation != null) return response(reservation.equals(next), "Reservation already present");
                if (draining || game.battle != null || !game.server.getPlayerList().getPlayers().isEmpty()) return response(false, "Arena unavailable");
                reservation = Objects.requireNonNull(next); phase = "RESERVED"; arrived.clear(); returning.clear();
                startRequested = false; expiresAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
                VanillaSmash.LOG.info("SMASH_RESERVED node={} match={} players={}", id, reservation.id(), reservation.roster().size());
                return response(true, "Reserved");
            }
            case "/start" -> {
                var request = Wire.JSON.fromJson(body, Wire.Id.class);
                if (reservation == null || !reservation.id().equals(request.id()) || phase.equals("RETURNING")) return response(false, "Unknown reservation");
                startRequested = true; return response(true, "Start requested");
            }
            case "/cancel" -> {
                var request = Wire.JSON.fromJson(body, Wire.Id.class);
                if (reservation == null) return response(true, "Already empty");
                if (!reservation.id().equals(request.id()) || phase.equals("PLAYING")) return response(false, "Reservation cannot be cancelled");
                finish(); return response(true, "Cancelled");
            }
            case "/drain" -> {
                boolean wasDraining = draining;
                draining = Wire.JSON.fromJson(body, Wire.Drain.class).enabled();
                if (draining && !wasDraining) {
                    drainSandboxAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                    if (game.battle != null && game.battle.sandbox)
                        game.server.getPlayerList().getPlayers().forEach(p -> p.sendOverlayMessage(Component.literal("Server update · returning to lobby in 30 seconds")));
                }
                return response(true, draining ? "Draining" : "Accepting matches");
            }
            case "/queue-view" -> {
                if (!lobby()) return response(false, "Not a lobby");
                var view = Wire.JSON.fromJson(body, Wire.QueueView.class);
                if (!view.coordinator().equals(queueCoordinator) || view.revision() >= queueRevision) {
                    queueCoordinator = view.coordinator(); queueRevision = view.revision(); queueMessages = Map.copyOf(view.messages());
                }
                return response(true, "Updated");
            }
            case "/clear-selections" -> {
                if (!lobby()) return response(false, "Not a lobby");
                var clear = Wire.JSON.fromJson(body, Wire.ClearSelections.class);
                for (var t : clear.tickets()) if (t.equals(selections.get(t.player()))) {
                    selections.remove(t.player()); notices.put(t.player(), clear.message());
                }
                return response(true, "Cleared");
            }
            case "/test/finish" -> {
                if (!"true".equals(System.getenv("SMASH_TEST_CONTROL")) || !arena() || game.battle == null)
                    return new PrivateHttp.Response(404, new Wire.Reply(false, "Unknown route"));
                game.match.finish(game.battle.actors.keySet().stream().findFirst().orElse(null), "Integration test");
                return response(true, "Results started");
            }
            default -> { return new PrivateHttp.Response(404, new Wire.Reply(false, "Unknown route")); }
        }
    }
    public boolean choose(ServerPlayer player, FighterClass fighter, VanillaSmash.Mode mode) {
        if (!lobby() || draining || selections.containsKey(player.getUUID())) return false;
        notices.remove(player.getUUID());
        selections.put(player.getUUID(), new Wire.Ticket(player.getUUID(), fighter.name(), mode.name(), UUID.randomUUID()));
        game.status(player); publish(); return true;
    }
    public boolean selected(UUID player) { return selections.containsKey(player); }
    public void cancelSelection(UUID player) { selections.remove(player); notices.remove(player); publish(); }
    public String lobbyMessage(UUID player) {
        if (draining) return "Lobby updating";
        if (selections.containsKey(player)) return queueMessages.getOrDefault(player, "Finding a match…    /smash unqueue");
        return notices.getOrDefault(player, "/smash join     /smash practice");
    }
    public void arrival(ServerPlayer player) {
        if (!arena()) return;
        game.networkPark(player);
        if (reservation != null && phase.equals("RESERVED") && reservation.roster().stream().anyMatch(t -> t.player().equals(player.getUUID()))) {
            arrived.add(player.getUUID());
            player.sendOverlayMessage(Component.literal("Waiting for fighters…"));
        } else returning.add(player.getUUID());
        publish();
    }
    public void departed(UUID player) {
        selections.remove(player); arrived.remove(player); returning.remove(player); notices.remove(player);
        publish();
    }
    public void returnPlayer(ServerPlayer player) { returning.add(player.getUUID()); game.networkPark(player); }
    public void finish() {
        if (!arena()) return;
        phase = "RETURNING"; startRequested = false;
        for (var player : game.server.getPlayerList().getPlayers()) returnPlayer(player);
        publish();
    }
    public void tick() {
        if (!enabled() || closing) return;
        if (arena()) {
            long now = System.nanoTime();
            if (reservation != null && phase.equals("RESERVED")) {
                if (now >= expiresAt) finish();
                else if (startRequested && reservation.roster().stream().allMatch(t -> arrived.contains(t.player()) && game.server.getPlayerList().getPlayer(t.player()) != null)) {
                    phase = "PLAYING";
                    var players = reservation.roster().stream().map(t -> {
                        game.choices.put(t.player(), FighterClass.valueOf(t.fighter()));
                        return game.server.getPlayerList().getPlayer(t.player());
                    }).toList();
                    game.begin(players, VanillaSmash.Mode.valueOf(reservation.roster().getFirst().mode()));
                    VanillaSmash.LOG.info("SMASH_MATCH_STARTED node={} match={}", id, reservation.id());
                }
            }
            if (draining && game.battle != null && game.battle.sandbox) {
                if (now >= drainSandboxAt) game.endRound(true);
                else if (game.ticks % 20 == 0) game.server.getPlayerList().getPlayers().forEach(p ->
                        p.sendOverlayMessage(Component.literal("Server update · returning in " + Math.max(1, TimeUnit.NANOSECONDS.toSeconds(drainSandboxAt - now)) + "s")));
            }
            if (phase.equals("PLAYING") && game.battle == null) finish();
            if (phase.equals("RETURNING") && game.server.getPlayerList().getPlayers().isEmpty()) {
                reservation = null; arrived.clear(); returning.clear(); phase = "IDLE";
            }
        }
        publish();
    }
    private void publish() {
        if (game.server == null) return;
        if (!game.server.isSameThread()) throw new IllegalStateException("Network state must be accessed on the server thread");
        var players = game.server.getPlayerList().getPlayers().stream().map(ServerPlayer::getUUID).toList();
        boolean empty = reservation == null && game.battle == null && players.isEmpty();
        status = new Wire.Status(Wire.PROTOCOL, id, boot, role.name(), version, game.ticks,
                !closing && !draining && (lobby() || empty), draining, !closing && draining && empty,
                reservation == null ? null : reservation.id(), phase, players, List.copyOf(arrived), List.copyOf(returning), List.copyOf(selections.values()));
        publishedAt = System.nanoTime();
    }
    @Override public void close() { closing = true; publish(); if (http != null) http.close(); }
}
