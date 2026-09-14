package dev.hanks.network;

import java.util.*;

/** Lobby-side compare-and-claim closes the cancellation/assignment race. Single server-thread writer. */
public final class SelectionRegistry {
    private final Map<UUID, Wire.Ticket> tickets = new LinkedHashMap<>();
    private final Map<UUID, UUID> claims = new HashMap<>();
    public void reset() { tickets.clear(); claims.clear(); }
    public List<Wire.Ticket> tickets() { return List.copyOf(tickets.values()); }
    public boolean selected(UUID player) { return tickets.containsKey(player); }
    public boolean claimed(UUID player) { return claims.containsKey(player); }
    public boolean offer(List<Wire.Ticket> group) {
        if (group.isEmpty() || group.stream().map(Wire.Ticket::player).distinct().count() != group.size()
                || group.stream().anyMatch(t -> tickets.containsKey(t.player()))) return false;
        if (group.getFirst().rematch() == null) { if (!Wire.completeGroup(group)) return false; }
        else { try { new Wire.Reservation(group.getFirst().rematch(), group); } catch (IllegalArgumentException e) { return false; } }
        group.forEach(t -> tickets.put(t.player(), t)); return true;
    }
    public boolean claim(Wire.Reservation reservation) {
        if (reservation.roster().stream().anyMatch(t -> !t.equals(tickets.get(t.player()))
                || claims.containsKey(t.player()) && !claims.get(t.player()).equals(reservation.id()))) return false;
        reservation.roster().forEach(t -> claims.put(t.player(), reservation.id())); return true;
    }
    public boolean cancel(UUID player) {
        if (claimed(player)) return false;
        var selected = tickets.get(player);
        if (selected != null) tickets.values().removeIf(t -> t.group().equals(selected.group()) || selected.rematch() != null && selected.rematch().equals(t.rematch()));
        return true;
    }
    public Set<UUID> clear(List<Wire.Ticket> previous) {
        var groups = new HashSet<UUID>();
        for (var t : previous) if (tickets.remove(t.player(), t)) { claims.remove(t.player()); groups.add(t.group()); }
        return groups;
    }
}
