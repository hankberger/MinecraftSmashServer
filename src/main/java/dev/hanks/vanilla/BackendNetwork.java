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
    public final SelectionRegistry selections = new SelectionRegistry();
    private Map<UUID, String> queueMessages = Map.of();
    private final Map<UUID, String> notices = new HashMap<>();
    private final Set<UUID> arrived = new HashSet<>(), returning = new HashSet<>();
    private final String version = FabricLoader.getInstance().getModContainer("smash_vanilla").orElseThrow().getMetadata().getVersion().getFriendlyString();
    private Wire.Reservation reservation;
    private Wire.MatchResult result;
    public Wire.Reservation reservation() { return reservation; }
    public void result(Wire.MatchResult result) { this.result = result; }
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
        closing = false;
        if (!enabled()) return;
        if (!id.matches("[a-zA-Z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid node id");
        publish();
        try {
            http = new PrivateHttp(System.getenv().getOrDefault("SMASH_CONTROL_BIND", "127.0.0.1"),
                    Integer.parseInt(System.getenv().getOrDefault("SMASH_CONTROL_PORT", "8081")),
                    PrivateHttp.secret("SMASH_CONTROL_SECRET"), (method, path, body) -> {
                if (method.equals("GET") && path.equals("/status")) return new PrivateHttp.Response(200, status);
                if(method.equals("GET") && path.equals("/economy") && lobby())return new PrivateHttp.Response(200,game.points.economyReport().get(2,TimeUnit.SECONDS));
                if (method.equals("GET") && path.equals("/health")) {
                    boolean alive = System.nanoTime() - publishedAt < TimeUnit.SECONDS.toNanos(10);
                    return new PrivateHttp.Response(alive ? 200 : 503, new Wire.Reply(alive, alive ? "Ticking" : "Server tick stalled"));
                }
                if (!method.equals("POST")) return new PrivateHttp.Response(404, new Wire.Reply(false, "Unknown route"));
                if(path.equals("/store/delivery") && lobby() && "true".equals(System.getenv("SMASH_STORE_SANDBOX"))) {
                    var request=Wire.JSON.fromJson(body,PointsStore.StoreDelivery.class);
                    var account=game.points.deliver(request).get(3,TimeUnit.SECONDS);
                    return new PrivateHttp.Response(200,Map.of("ok",true,"id",request.id(),"balance",account.balance()));
                }
                // Acknowledge only after SQLite commits, without blocking the Minecraft tick.
                if(path.equals("/match-result") && lobby()) {
                    var delivered=Wire.JSON.fromJson(body,Wire.MatchResult.class);
                    game.points.settle(delivered).get(2,TimeUnit.SECONDS);
                    return game.server.submit(()->{game.hub.results.receive(delivered);return response(true,"Points saved");}).get(2,TimeUnit.SECONDS);
                }
                if(path.equals("/ack-result") && arena()) {
                    game.points.acknowledge(Wire.JSON.fromJson(body,Wire.Id.class).id()).get(2,TimeUnit.SECONDS);
                    return new PrivateHttp.Response(200,new Wire.Reply(true,"Acknowledged"));
                }
                if(path.equals("/test/outfit") && lobby() && "true".equals(System.getenv("SMASH_TEST_CONTROL"))) {
                    var request=Wire.JSON.fromJson(body,OutfitProbe.class);
                    var result=game.points.outfit(request.player(),request.fighter(),request.skin(),request.purchase()).get(2,TimeUnit.SECONDS);
                    return new PrivateHttp.Response(200,Map.of("result",result,"account",game.points.account(request.player()),"wardrobe",game.points.wardrobe(request.player())));
                }
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
                result = null; reservation = Objects.requireNonNull(next); phase = "RESERVED"; arrived.clear(); returning.clear();
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
                    game.hub.presence(view.online());
                }
                return response(true, "Updated");
            }
            case "/clear-selections" -> {
                if (!lobby()) return response(false, "Not a lobby");
                var clear = Wire.JSON.fromJson(body, Wire.ClearSelections.class);
                var cleared = selections.clear(clear.tickets()); game.hub.finished(cleared);
                for (var t : clear.tickets()) if (cleared.contains(t.group())) notices.put(t.player(), clear.message());
                return response(true, "Cleared");
            }
            case "/claim-selections" -> {
                if (!lobby() || draining) return response(false, "Lobby unavailable");
                var claim = Wire.JSON.fromJson(body, Wire.Reservation.class);
                if (claim.roster().stream().anyMatch(t -> game.server.getPlayerList().getPlayer(t.player()) == null)
                        || !selections.claim(claim)) return response(false, "Selection changed");
                game.hub.claim(claim.roster()); return response(true, "Claimed");
            }
            case "/test/finish" -> {
                if (!"true".equals(System.getenv("SMASH_TEST_CONTROL")) || !arena() || game.battle == null)
                    return new PrivateHttp.Response(404, new Wire.Reply(false, "Unknown route"));
                game.match.finish(game.battle.actors.keySet().stream().findFirst().orElse(null), "Integration test");
                return response(true, "Results started");
            }
            case "/test/points" -> {
                if(!"true".equals(System.getenv("SMASH_TEST_CONTROL")))return new PrivateHttp.Response(404,new Wire.Reply(false,"Unknown route"));
                var request=Wire.JSON.fromJson(body,PointsProbe.class);
                if(request.action().equals("record") && arena()) {
                    game.points.record(Objects.requireNonNull(request.result()));return response(true,"Recorded for delivery");
                }
                if(request.action().equals("status"))return new PrivateHttp.Response(200,Map.of("account",game.points.account(request.player()),"wardrobe",game.points.wardrobe(request.player()),"completed",game.points.completed(),"pending",game.points.pending()));
                return response(false,"Unknown points test action");
            }
            case "/test/matchmaking" -> {
                if (!"true".equals(System.getenv("SMASH_TEST_CONTROL")) || !lobby())
                    return new PrivateHttp.Response(404, new Wire.Reply(false, "Unknown route"));
                var request = Wire.JSON.fromJson(body, TestAction.class);
                var p = game.server.getPlayerList().getPlayerByName(request.player());
                if (p == null || !game.hub.available(p)) return response(false, "Player unavailable");
                game.hub.parties.ensure(p.getUUID(), p.getPlainTextName());
                switch (request.action()) {
                    case "status" -> { }
                    case "rematch" -> {
                        var result = game.hub.results.book.result(p.getUUID());
                        if (result == null) return response(false, "No result yet");
                        game.hub.results.rematch(p, result.id());
                    }
                    case "replay" -> game.hub.results.replay(p, false);
                    case "ready-saved" -> {
                        var party = game.hub.parties.view(p.getUUID());
                        var own = party.members().stream().filter(m -> m.id().equals(p.getUUID())).findFirst().orElseThrow();
                        game.hub.confirm(p, FighterClass.valueOf(own.fighter()), party.round());
                    }
                    case "create", "invite", "accept", "leave" -> game.hub.partyCommand(p, request.action(), request.argument());
                    case "select" -> game.hub.selectMode(p, VanillaSmash.Mode.valueOf(request.argument()));
                    case "ready" -> {
                        if(game.uiPack.enabled()) { game.hub.preview(p,FighterClass.valueOf(request.argument())); game.hub.pickerAction(p); }
                        else { game.stage.selectSlot(p, FighterClass.valueOf(request.argument()).ordinal()); game.stage.confirm(p); }
                    }
                    case "change" -> game.hub.change(p);
                    case "cancel" -> game.hub.cancel(p, false);
                    default -> { return response(false, "Unknown test action"); }
                }
                publish();
                var report = new HashMap<String,Object>(); report.put("party",game.hub.parties.ensure(p.getUUID(), p.getPlainTextName()));
                report.put("stage",game.stage.active(p)); report.put("selected",selections.selected(p.getUUID()));
                report.put("packReady",game.uiPack.ready(p));
                var winnerScene = game.hub.results.scene.session(p.getUUID());
                report.put("winnerStage",winnerScene != null); report.put("winnerReady",winnerScene != null && winnerScene.ready());
                var result = game.hub.results.book.result(p.getUUID());
                if (result != null) { report.put("result", result); report.put("votes", game.hub.results.book.votes(result.id())); }
                report.put("points",game.points.account(p.getUUID()));
                if(result!=null)report.put("reward",game.points.receipt(result.id(),p.getUUID()));
                return new PrivateHttp.Response(200, report);
            }
            default -> { return new PrivateHttp.Response(404, new Wire.Reply(false, "Unknown route")); }
        }
    }
    public boolean offerSelections(List<Wire.Ticket> group) {
        // Only the lobby's committed wardrobe can enter a match. Client drafts and ticket skin fields are not authority.
        if(group.stream().anyMatch(t -> game.points.dressing(t.player())))return false;
        group=group.stream().map(t -> t.withSkin(game.points.wardrobe(t.player()).equipped(t.fighter()))).toList();
        if (arena() || draining || closing || group.stream().anyMatch(t -> game.server.getPlayerList().getPlayer(t.player()) == null) || !selections.offer(group)) return false;
        for (var t : group) { notices.remove(t.player()); game.choices.put(t.player(), FighterClass.valueOf(t.fighter())); if (!enabled()) game.match.enqueue(t.player()); }
        publish(); return true;
    }
    public boolean selected(UUID player) { return selections.selected(player); }
    public boolean cancelSelection(UUID player) {
        var previous = selections.tickets();
        if (!selections.cancel(player)) return false;
        for (var t : previous) if (!selections.selected(t.player())) { game.match.dequeue(t.player()); game.choices.remove(t.player()); }
        var removedGroups = previous.stream().filter(t -> t.rematch() != null && !selections.selected(t.player())).map(Wire.Ticket::group).collect(java.util.stream.Collectors.toSet());
        game.hub.finished(removedGroups);
        notices.remove(player); publish(); return true;
    }
    public String lobbyMessage(UUID player) {
        if (draining) return "Lobby updating";
        if (selections.selected(player)) return queueMessages.getOrDefault(player, "Finding a match…    /smash unqueue");
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
        if (!selections.claimed(player)) cancelSelection(player);
        arrived.remove(player); returning.remove(player); notices.remove(player);
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
                reservation = null; result = null; arrived.clear(); returning.clear(); phase = "IDLE";
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
                !closing && !draining && (lobby() || empty), draining, !closing && draining && empty && !game.points.pending(),
                reservation == null ? null : reservation.id(), phase, players, List.copyOf(arrived), List.copyOf(returning), selections.tickets(), result, game.points.completed());
        publishedAt = System.nanoTime();
    }
    @Override public void close() { closing = true; publish(); if (http != null) http.close(); }
    private record TestAction(String player, String action, String argument) {}
    private record PointsProbe(String action,UUID player,Wire.MatchResult result) {}
    private record OutfitProbe(UUID player,String fighter,String skin,boolean purchase) {}
}
