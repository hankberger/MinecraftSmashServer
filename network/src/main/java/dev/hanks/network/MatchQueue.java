package dev.hanks.network;

import java.util.*;

/** Single-writer queue. Mode-specific matches contain complete, indivisible parties. */
public final class MatchQueue {
    private final LinkedHashMap<UUID, Wire.Ticket> waiting = new LinkedHashMap<>();
    public List<Wire.Ticket> tickets() { return List.copyOf(waiting.values()); }
    public void offer(Wire.Ticket ticket) { waiting.putIfAbsent(ticket.player(), ticket); }
    public void remove(UUID player) {
        var ticket = waiting.get(player);
        if (ticket != null) waiting.values().removeIf(t -> t.group().equals(ticket.group()));
    }
    public int size() { return waiting.size(); }
    public List<Wire.Ticket> next() {
        var groups = new LinkedHashMap<UUID, List<Wire.Ticket>>();
        for (var t : waiting.values()) groups.computeIfAbsent(t.group(), k -> new ArrayList<>()).add(t);
        var position = new HashMap<UUID, Integer>(); int index = 0;
        for (var t : waiting.values()) position.put(t.player(), index++);
        Comparator<List<Wire.Ticket>> oldest = (a, b) -> {
            for (int i = 0; i < Math.min(a.size(), b.size()); i++) {
                int comparison = Integer.compare(position.get(a.get(i).player()), position.get(b.get(i).player()));
                if (comparison != 0) return comparison;
            }
            return Integer.compare(a.size(), b.size());
        };
        List<Wire.Ticket> best = List.of();
        for (String mode : Wire.MODES) {
            int capacity = Wire.capacity(mode);
            var fits = new ArrayList<List<Wire.Ticket>>(Collections.nCopies(capacity + 1, null)); fits.set(0, List.of());
            for (var group : groups.values()) if (group.getFirst().mode().equals(mode) && Wire.completeGroup(group)) {
                for (int size = capacity; size >= group.size(); size--) {
                    var prefix = fits.get(size - group.size()); if (prefix == null) continue;
                    var candidate = new ArrayList<>(prefix); candidate.addAll(group);
                    if (fits.get(size) == null || oldest.compare(candidate, fits.get(size)) < 0) fits.set(size, candidate);
                }
            }
            var ready = fits.get(capacity);
            if (ready != null && (best.isEmpty() || oldest.compare(ready, best) < 0)) best = ready;
        }
        return List.copyOf(best);
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
