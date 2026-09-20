package dev.hanks.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.*;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.*;
import com.velocitypowered.api.event.proxy.*;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.proxy.*;
import com.velocitypowered.api.proxy.server.*;
import dev.hanks.network.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/** One coordinator per network. Worker admission and transitions are serialized here. */
@Plugin(id = "smash-network", name = "Smash Network", version = "0.3.0",
        dependencies = {@Dependency(id = "viaversion")})
public final class SmashProxy {
    private final ProxyServer proxy;
    private final Logger log;
    private final ScheduledExecutorService loop = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "smash-matchmaker"));
    private final Map<String, Wire.Node> nodes = new LinkedHashMap<>();
    private final Map<String, Watch> workers = new LinkedHashMap<>();
    private final Map<String, Assignment> assignments = new LinkedHashMap<>();
    private final MatchQueue queue = new MatchQueue();
    private final Map<UUID, UUID> consumed = new HashMap<>();
    private final ConcurrentMap<UUID, String> admitted = new ConcurrentHashMap<>();
    private final Set<UUID> connecting = new HashSet<>();
    private final Set<String> draining = new HashSet<>();
    private final AtomicReference<Object> report = new AtomicReference<>(Map.of("ready", false));
    private PrivateHttp.Client client;
    private PrivateHttp admin;
    private volatile RegisteredServer lobby;
    private String lobbyId;
    private long revision;
    private volatile long coordinatedAt;
    private final UUID coordinator = UUID.randomUUID();
    private static final class Watch {
        Wire.Status status;
        long received, progressed;
        void update(Wire.Status next) {
            long now = System.nanoTime();
            if (status == null || !status.boot().equals(next.boot()) || status.tick() != next.tick()) progressed = now;
            status = next; received = now;
        }
        boolean healthy() { long now = System.nanoTime(); return status != null && now - received < TimeUnit.SECONDS.toNanos(5) && now - progressed < TimeUnit.SECONDS.toNanos(10); }
    }
    private static final class Assignment {
        final Wire.Node node;
        final Wire.Reservation reservation;
        final UUID boot;
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(42);
        boolean running, returning, failed;
        Wire.MatchResult result;
        Assignment(Wire.Node node, Wire.Reservation reservation, UUID boot) { this.node = node; this.reservation = reservation; this.boot = boot; }
    }
    @Inject public SmashProxy(ProxyServer proxy, Logger log) { this.proxy = proxy; this.log = log; }
    @Subscribe public void initialize(ProxyInitializeEvent event) throws Exception {
        var config = Path.of(System.getenv().getOrDefault("SMASH_TOPOLOGY_FILE", "network.json"));
        var topology = Wire.JSON.fromJson(Files.readString(config), Wire.Node[].class);
        for (var node : topology) {
            if (!node.id().matches("[a-zA-Z0-9_-]{1,64}") || nodes.putIfAbsent(node.id(), node) != null) throw new IllegalArgumentException("Invalid node id");
            if (!Set.of("LOBBY", "ARENA").contains(node.role())) throw new IllegalArgumentException("Invalid node role");
            // Let Velocity resolve on connection; Docker can assign a new IP when a worker is replaced.
            var info = new ServerInfo(node.id(), InetSocketAddress.createUnresolved(node.host(), node.port()));
            proxy.getServer(node.id()).ifPresent(s -> proxy.unregisterServer(s.getServerInfo()));
            var registered = proxy.registerServer(info); workers.put(node.id(), new Watch());
            if (node.role().equals("LOBBY")) {
                if (lobby != null) throw new IllegalArgumentException("This coordinator supports one lobby");
                lobby = registered; lobbyId = node.id();
            }
        }
        if (lobby == null || nodes.size() < 2) throw new IllegalArgumentException("Configure one lobby and at least one arena");
        client = new PrivateHttp.Client(PrivateHttp.secret("SMASH_CONTROL_SECRET"));
        admin = new PrivateHttp(System.getenv().getOrDefault("SMASH_ADMIN_BIND", "127.0.0.1"),
                Integer.parseInt(System.getenv().getOrDefault("SMASH_ADMIN_PORT", "8080")),
                PrivateHttp.secret("SMASH_ADMIN_SECRET"), (method, path, body) -> {
            if (method.equals("GET") && path.equals("/status")) return new PrivateHttp.Response(200, report.get());
            if (method.equals("GET") && path.equals("/health")) {
                boolean alive = coordinatedAt != 0 && System.nanoTime() - coordinatedAt < TimeUnit.SECONDS.toNanos(15);
                return new PrivateHttp.Response(alive ? 200 : 503, new Wire.Reply(alive, alive ? "Coordinating" : "Coordinator stalled"));
            }
            if (method.equals("POST") && (path.equals("/drain") || path.equals("/resume"))) {
                String nodeId = Wire.JSON.fromJson(body, com.google.gson.JsonObject.class).get("node").getAsString();
                return loop.submit(() -> {
                    var node = nodes.get(nodeId);
                    boolean ok = node != null && node.role().equals("ARENA");
                    if (ok) {
                        boolean enabled = path.equals("/drain");
                        if (enabled) draining.add(nodeId); // Survives worker restarts during a rollout.
                        ok = client.post(node.controlUrl(), "/drain", new Wire.Drain(enabled));
                        if (ok && !enabled) draining.remove(nodeId);
                    }
                    return new PrivateHttp.Response(ok ? 200 : 409, new Wire.Reply(ok, ok ? "Updated" : "Worker unavailable"));
                }).get(8, TimeUnit.SECONDS);
            }
            return new PrivateHttp.Response(404, new Wire.Reply(false, "Unknown route"));
        });
        loop.scheduleWithFixedDelay(this::safeTick, 0, 500, TimeUnit.MILLISECONDS);
        log.info("SMASH_PROXY_READY nodes={}", nodes.keySet());
    }
    @Subscribe public void initial(PlayerChooseInitialServerEvent event) { if (lobby != null) event.setInitialServer(lobby); }
    @Subscribe(order = PostOrder.LAST) public void versionPing(ProxyPingEvent event) {
        int client = event.getConnection().getProtocolVersion().getProtocol();
        // Only advertise a matching protocol when we actually support it.
        event.setPing(event.getPing().asBuilder().version(new ServerPing.Version(
                ClientVersions.advertisedProtocol(client), ClientVersions.LABEL)).build());
    }
    @Subscribe(order = PostOrder.LAST) public void versionAdmission(PreLoginEvent event) {
        if (!ClientVersions.supports(event.getConnection().getProtocolVersion().getProtocol()))
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(ClientVersions.INSTRUCTIONS)));
    }
    @Subscribe(order = PostOrder.LAST) public void admission(ServerPreConnectEvent event) {
        String target = event.getOriginalServer().getServerInfo().getName();
        if (!target.equals(lobbyId) && !target.equals(admitted.get(event.getPlayer().getUniqueId())))
            event.setResult(ServerPreConnectEvent.ServerResult.denied());
    }
    @Subscribe public void disconnected(DisconnectEvent event) {
        UUID id = event.getPlayer().getUniqueId(); admitted.remove(id);
        loop.execute(() -> { queue.remove(id); consumed.remove(id); connecting.remove(id); });
    }
    @Subscribe public void kicked(KickedFromServerEvent event) {
        if (lobby != null && !event.getServer().getServerInfo().getName().equals(lobbyId))
            event.setResult(KickedFromServerEvent.RedirectPlayer.create(lobby));
    }
    @Subscribe public void shutdown(ProxyShutdownEvent event) {
        if (admin != null) admin.close(); loop.shutdownNow(); if (client != null) client.close();
    }
    private void safeTick() {
        try { tick(); } catch (Exception e) { log.error("Matchmaker tick failed", e); }
    }
    private String location(UUID id) {
        return proxy.getPlayer(id).flatMap(Player::getCurrentServer).map(c -> c.getServerInfo().getName()).orElse("");
    }
    private void refresh() {
        var requests = nodes.values().stream().map(node -> client.call(node.controlUrl(), "/status", null).handle((response, failure) -> {
            if (failure != null || response.statusCode() != 200) return null;
            try {
                var status = Wire.JSON.fromJson(response.body(), Wire.Status.class);
                if (status.protocol() != Wire.PROTOCOL || !status.id().equals(node.id()) || !status.role().equals(node.role())) return null;
                return Map.entry(node.id(), status);
            } catch (RuntimeException e) { return null; }
        })).toList();
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).join();
        for (var request : requests) { var entry = request.join(); if (entry != null) workers.get(entry.getKey()).update(entry.getValue()); }
    }
    private void tick() {
        refresh();
        var lobbyWatch = workers.get(lobbyId);
        Map<UUID, Wire.Ticket> desired = new LinkedHashMap<>();
        if (lobbyWatch.healthy()) for (var ticket : lobbyWatch.status.selections()) {
            if (location(ticket.player()).equals(lobbyId) && !admitted.containsKey(ticket.player())) desired.put(ticket.player(), ticket);
        }
        for (var ticket : queue.tickets()) if (!ticket.equals(desired.get(ticket.player()))) queue.remove(ticket.player());
        for (var ticket : desired.values()) if (!ticket.selection().equals(consumed.get(ticket.player()))) queue.offer(ticket);

        for (var entry : new ArrayList<>(assignments.entrySet())) progress(entry.getValue());
        // Recover late arrivals and disconnected/reserved workers even after a coordinator restart.
        for (var node : nodes.values()) if (node.role().equals("ARENA")) {
            var watch = workers.get(node.id());
            if (watch.healthy()) for (var id : watch.status.returning()) if (location(id).equals(node.id())) transfer(id, lobbyId, null);
        }
        if (lobbyWatch.healthy() && !lobbyWatch.status.draining()) {
            for (var node : nodes.values()) {
                var watch = workers.get(node.id());
                if (!node.role().equals("ARENA") || draining.contains(node.id()) || assignments.containsKey(node.id()) || !watch.healthy() || !watch.status.ready()) continue;
                var reservation = queue.reserve(); if (reservation == null) break;
                // Persist the reservation in the worker before any player's connection is changed.
                if (!client.post(node.controlUrl(), "/reserve", reservation)) { queue.restore(reservation); continue; }
                var assignment = new Assignment(node, reservation, watch.status.boot()); assignments.put(node.id(), assignment);
                reservation.roster().forEach(t -> consumed.put(t.player(), t.selection()));
                // Serialize the final ready check against lobby cancellation before transferring anyone.
                if (!client.post(nodes.get(lobbyId).controlUrl(), "/claim-selections", reservation)) {
                    assignment.failed = true; assignment.returning = true;
                    client.post(node.controlUrl(), "/cancel", new Wire.Id(reservation.id()));
                    continue;
                }
                for (var ticket : reservation.roster()) {
                    admitted.put(ticket.player(), node.id());
                    transfer(ticket.player(), node.id(), assignment);
                }
                log.info("SMASH_ASSIGN match={} worker={} players={}", reservation.id(), node.id(), reservation.roster().size());
            }
        }
        if (lobbyWatch.healthy()) {
            Map<UUID, String> messages = new HashMap<>();
            for (var t : queue.tickets()) {
                long count = queue.tickets().stream().filter(other -> other.mode().equals(t.mode())).count();
                messages.put(t.player(), Wire.label(t.mode()) + " · Queued " + count + "/" + Wire.capacity(t.mode()) + "    /smash unqueue");
            }
            for (var a : assignments.values()) if (!a.running) a.reservation.roster().forEach(t -> messages.put(t.player(), "Joining match…"));
            var online = proxy.getAllPlayers().stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet());
            client.post(nodes.get(lobbyId).controlUrl(), "/queue-view", new Wire.QueueView(coordinator, ++revision, messages, online));
        }
        var nodeReports = new LinkedHashMap<String, Object>();
        workers.forEach((id, watch) -> nodeReports.put(id, Map.of("healthy", watch.healthy(), "status", watch.status == null ? Map.of() : watch.status)));
        report.set(Map.of("protocol", Wire.PROTOCOL, "ready", lobbyWatch.healthy(), "draining", Set.copyOf(draining), "queue", queue.tickets(), "nodes", nodeReports,
                "matches", assignments.values().stream().map(a -> Map.of("id", a.reservation.id(), "worker", a.node.id(), "running", a.running, "returning", a.returning, "roster", a.reservation.roster())).toList()));
        coordinatedAt = System.nanoTime();
    }
    private void progress(Assignment a) {
        var watch = workers.get(a.node.id()); var status = watch.status;
        boolean same = status != null && status.boot().equals(a.boot) && a.reservation.id().equals(status.reservation());
        if (same && status.result() != null) a.result = status.result();
        if (same && status.phase().equals("PLAYING")) a.running = true;
        if (same && status.phase().equals("RETURNING")) a.returning = true;
        if (watch.healthy() && !same) a.returning = true;
        if (!watch.healthy() && (System.nanoTime() - watch.received > TimeUnit.SECONDS.toNanos(15)
                || System.nanoTime() - watch.progressed > TimeUnit.SECONDS.toNanos(15))) a.failed = true;
        if (!a.running && (a.failed || System.nanoTime() > a.deadline)) {
            client.post(a.node.controlUrl(), "/cancel", new Wire.Id(a.reservation.id())); a.returning = true;
        }
        if (a.running && a.failed) a.returning = true;
        if (a.returning) {
            // Clearing by selection ID cannot erase a newer selection after a fast reconnect.
            boolean cleared = client.post(nodes.get(lobbyId).controlUrl(), "/clear-selections", new Wire.ClearSelections(a.reservation.roster(), a.failed ? "Match interrupted · /smash join" : "Match ended · /smash join", a.failed ? null : a.result));
            for (var t : a.reservation.roster()) {
                admitted.remove(t.player(), a.node.id());
                if (location(t.player()).equals(a.node.id())) transfer(t.player(), lobbyId, null);
            }
            boolean allHome = a.reservation.roster().stream().noneMatch(t -> location(t.player()).equals(a.node.id()));
            if (cleared && allHome && (!same || status.players().isEmpty() || !watch.healthy())) {
                assignments.remove(a.node.id()); log.info("SMASH_RELEASE match={} worker={}", a.reservation.id(), a.node.id());
            }
        } else if (!a.running) {
            boolean allArrived = same && a.reservation.roster().stream().allMatch(t -> status.arrived().contains(t.player()) && location(t.player()).equals(a.node.id()));
            if (allArrived) client.post(a.node.controlUrl(), "/start", new Wire.Id(a.reservation.id()));
        } else {
            for (var t : a.reservation.roster()) if (location(t.player()).equals(lobbyId)) admitted.remove(t.player(), a.node.id());
        }
    }
    private void transfer(UUID id, String target, Assignment assignment) {
        var player = proxy.getPlayer(id); var server = proxy.getServer(target);
        if (player.isEmpty() || server.isEmpty()) { if (assignment != null) assignment.failed = true; return; }
        if (location(id).equals(target) || !connecting.add(id)) return;
        player.get().createConnectionRequest(server.get()).connect().whenComplete((result, failure) -> {
            if (loop.isShutdown()) return;
            loop.execute(() -> {
                connecting.remove(id);
                if (failure != null || !result.isSuccessful()) {
                    if (assignment != null) assignment.failed = true;
                    log.warn("SMASH_TRANSFER_FAILED player={} target={}", id, target);
                }
            });
        });
    }
}
