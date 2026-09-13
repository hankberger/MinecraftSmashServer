package dev.hanks.network;

import com.google.gson.Gson;
import java.util.*;

/** Private server-to-proxy protocol. No Minecraft client channel participates. */
public final class Wire {
    public static final int PROTOCOL = 1;
    public static final Gson JSON = new Gson();
    public static final Set<String> CLASSES = Set.of("STEVE", "ALEX", "ZOMBIE", "SKELETON", "VILLAGER");
    public static final Set<String> MODES = Set.of("MATCH", "PRACTICE", "SANDBOX");
    public record Ticket(UUID player, String fighter, String mode, UUID selection) {
        public Ticket {
            Objects.requireNonNull(player); Objects.requireNonNull(selection);
            if (!CLASSES.contains(fighter) || !MODES.contains(mode)) throw new IllegalArgumentException("Invalid selection");
        }
    }
    public record Reservation(UUID id, List<Ticket> roster) {
        public Reservation {
            Objects.requireNonNull(id); roster = List.copyOf(roster);
            if (roster.isEmpty() || roster.size() > 4 || roster.stream().map(Ticket::player).distinct().count() != roster.size())
                throw new IllegalArgumentException("Invalid roster");
            String mode = roster.getFirst().mode();
            if (roster.stream().anyMatch(t -> !t.mode().equals(mode)) || roster.size() != (mode.equals("MATCH") ? 4 : 1))
                throw new IllegalArgumentException("Wrong roster size for mode");
        }
    }
    public record Status(int protocol, String id, UUID boot, String role, String version, long tick, boolean ready,
                         boolean draining, boolean drained, UUID reservation, String phase, List<UUID> players,
                         List<UUID> arrived, List<UUID> returning, List<Ticket> selections) {}
    public record Id(UUID id) {}
    public record Drain(boolean enabled) {}
    public record QueueView(UUID coordinator, long revision, Map<UUID, String> messages) {}
    public record ClearSelections(List<Ticket> tickets, String message) {}
    public record Reply(boolean ok, String message) {}
    public record Node(String id, String role, String host, int port, String controlUrl) {}
    private Wire() {}
}
