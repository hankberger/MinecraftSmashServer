package dev.hanks.network;

import java.util.*;

/** Single-writer shared queue. Reservations remove an entire group atomically. */
public final class MatchQueue {
    private final LinkedHashMap<UUID, Wire.Ticket> waiting = new LinkedHashMap<>();
    public List<Wire.Ticket> tickets() { return List.copyOf(waiting.values()); }
    public void offer(Wire.Ticket ticket) { waiting.putIfAbsent(ticket.player(), ticket); }
    public void remove(UUID player) { waiting.remove(player); }
    public int size() { return waiting.size(); }
    public List<Wire.Ticket> next() {
        var publicMatch = waiting.values().stream().filter(t -> t.mode().equals("MATCH")).limit(4).toList();
        var training = waiting.values().stream().filter(t -> !t.mode().equals("MATCH")).findFirst();
        if (publicMatch.size() < 4) return training.map(List::of).orElse(List.of());
        if (training.isPresent()) {
            for (var t : waiting.values()) {
                if (t.equals(publicMatch.getFirst())) break;
                if (t.equals(training.get())) return List.of(training.get());
            }
        }
        return publicMatch;
    }
    public Wire.Reservation reserve() {
        var group = next();
        if (group.isEmpty()) return null;
        group.forEach(t -> waiting.remove(t.player()));
        return new Wire.Reservation(UUID.randomUUID(), group);
    }
    public void restore(Wire.Reservation reservation) {
        var previous = tickets(); waiting.clear();
        reservation.roster().forEach(this::offer); previous.forEach(this::offer);
    }
}
