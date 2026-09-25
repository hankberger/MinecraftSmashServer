package dev.hanks.network;

import com.google.gson.Gson;
import java.util.*;

/** Private server-to-proxy protocol. No Minecraft client channel participates. */
public final class Wire {
    public static final int PROTOCOL = 5;
    public static final Gson JSON = new Gson();
    public static final Set<String> CLASSES = Set.of("STEVE", "ALEX", "ZOMBIE", "SKELETON", "VILLAGER");
    public static final Set<String> MODES = Set.of("DUEL", "MATCH", "PRACTICE", "SANDBOX");
    public static int capacity(String mode) {
        return switch (mode) { case "DUEL" -> 2; case "MATCH" -> 4; case "PRACTICE", "SANDBOX" -> 1; default -> throw new IllegalArgumentException("Invalid mode"); };
    }
    public static String label(String mode) {
        return switch (mode) { case "DUEL" -> "1v1"; case "MATCH" -> "Free-for-all"; case "PRACTICE" -> "Practice"; case "SANDBOX" -> "Sandbox"; default -> "Choose a mode"; };
    }
    public record Ticket(UUID player, String fighter, String mode, UUID selection, UUID group, int groupSize, UUID rematch, String skin) {
        public Ticket(UUID player, String fighter, String mode, UUID selection, UUID group, int groupSize, UUID rematch) { this(player,fighter,mode,selection,group,groupSize,rematch,Cosmetics.DEFAULT); }
        public Ticket(UUID player, String fighter, String mode, UUID selection, UUID group, int groupSize) { this(player, fighter, mode, selection, group, groupSize, null); }
        public Ticket forRematch(UUID match) { return new Ticket(player, fighter, mode, selection, group, groupSize, match, skin); }
        public Ticket withSkin(String skin) { return new Ticket(player,fighter,mode,selection,group,groupSize,rematch,skin); }
        public Ticket(UUID player, String fighter, String mode, UUID selection) { this(player, fighter, mode, selection, selection, 1); }
        public Ticket {
            Objects.requireNonNull(player); Objects.requireNonNull(selection); Objects.requireNonNull(group);
            if (!CLASSES.contains(fighter) || !MODES.contains(mode)) throw new IllegalArgumentException("Invalid selection");
            skin = Cosmetics.skin(fighter,skin).id();
            if (groupSize < 1 || groupSize > capacity(mode)) throw new IllegalArgumentException("Invalid party size");
        }
    }
    public static boolean completeGroup(List<Ticket> group) {
        if (group.isEmpty()) return false;
        var first = group.getFirst();
        return group.size() == first.groupSize() && group.stream().map(Ticket::player).distinct().count() == group.size()
                && group.stream().allMatch(t -> t.group().equals(first.group()) && t.groupSize() == first.groupSize() && t.mode().equals(first.mode()) && Objects.equals(t.rematch(), first.rematch()));
    }
    public record Reservation(UUID id, List<Ticket> roster) {
        public Reservation {
            Objects.requireNonNull(id); roster = List.copyOf(roster);
            if (roster.isEmpty() || roster.size() > 4 || roster.stream().map(Ticket::player).distinct().count() != roster.size())
                throw new IllegalArgumentException("Invalid roster");
            String mode = roster.getFirst().mode(); UUID pool = roster.getFirst().rematch();
            if (roster.stream().anyMatch(t -> !t.mode().equals(mode) || !Objects.equals(t.rematch(), pool)) || roster.size() != capacity(mode))
                throw new IllegalArgumentException("Wrong roster size for mode");
            if (roster.stream().collect(java.util.stream.Collectors.groupingBy(Ticket::group)).values().stream().anyMatch(g -> !completeGroup(g)))
                throw new IllegalArgumentException("Reservation splits a party");
        }
    }
    public record Status(int protocol, String id, UUID boot, String role, String version, long tick, boolean ready,
                         boolean draining, boolean drained, UUID reservation, String phase, List<UUID> players,
                         List<UUID> arrived, List<UUID> returning, List<Ticket> selections, MatchResult result, List<MatchResult> completed) {}
    public record Id(UUID id) {}
    public record Drain(boolean enabled) {}
    public record QueueView(UUID coordinator, long revision, Map<UUID, String> messages, Set<UUID> online) {}
    public record ClearSelections(List<Ticket> tickets, String message, MatchResult result) {}
    public record ResultRow(UUID player, String name, String fighter, int slot, int stocks, int knockouts, int falls, int damage, boolean forfeited, String skin) {
        public ResultRow { skin=Cosmetics.skin(fighter,skin).id(); }
        public ResultRow(UUID player,String name,String fighter,int slot,int stocks,int knockouts,int falls,int damage,boolean forfeited) {
            this(player,name,fighter,slot,stocks,knockouts,falls,damage,forfeited,Cosmetics.DEFAULT);
        }
        public ResultRow(UUID player, String name, String fighter, int slot, int stocks, int knockouts, int falls, int damage) {
            this(player,name,fighter,slot,stocks,knockouts,falls,damage,false);
        }
    }
    public record MatchResult(UUID id, String mode, UUID winner, List<Ticket> roster, List<ResultRow> rows) {
        public MatchResult {
            Objects.requireNonNull(id); roster = List.copyOf(roster); rows = List.copyOf(rows);
            new Reservation(id, roster);
            if (capacity(mode) < 2 || roster.stream().anyMatch(t -> !t.mode().equals(mode))
                    || !new HashSet<>(rows.stream().map(ResultRow::player).toList()).equals(new HashSet<>(roster.stream().map(Ticket::player).toList()))
                    || rows.size() != roster.size() || winner != null && rows.stream().noneMatch(r -> r.player().equals(winner)))
                throw new IllegalArgumentException("Invalid match result");
        }
    }
    public record Reply(boolean ok, String message) {}
    public record Node(String id, String role, String host, int port, String controlUrl) {}
    private Wire() {}
}
