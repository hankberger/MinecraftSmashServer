package dev.hanks.network;

import java.util.*;

/** Immutable results and a time-limited, unanimous rematch ballot. Single lobby-thread writer. */
public final class ResultBook {
    public static final int VOTE_TICKS = 1200, KEEP_TICKS = 12000;
    private static final class Entry {
        final Wire.MatchResult result; final long received;
        final Set<UUID> votes = new HashSet<>();
        boolean closed;
        Entry(Wire.MatchResult result, long now) { this.result = result; received = now; }
    }
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, UUID> latest = new HashMap<>();
    public void clear() { entries.clear(); latest.clear(); }
    public boolean receive(Wire.MatchResult result, long now) {
        if (entries.containsKey(result.id())) return false;
        entries.put(result.id(), new Entry(result, now));
        for (var row : result.rows()) { leave(row.player()); latest.put(row.player(), result.id()); }
        return true;
    }
    public Wire.MatchResult result(UUID player) {
        var entry = entries.get(latest.get(player)); return entry == null ? null : entry.result;
    }
    public boolean open(UUID match, long now) {
        var e = entries.get(match); return e != null && !e.closed && now - e.received < VOTE_TICKS;
    }
    public int votes(UUID match) { var e = entries.get(match); return e == null ? 0 : e.votes.size(); }
    public boolean voted(UUID match, UUID player) { var e = entries.get(match); return e != null && e.votes.contains(player); }
    public boolean vote(UUID match, UUID player, long now) {
        var e = entries.get(match);
        if (!open(match, now) || !match.equals(latest.get(player))) throw new IllegalStateException("Rematch is no longer available");
        e.votes.add(player);
        if (e.votes.size() != e.result.rows().size()) return false;
        e.closed = true; return true;
    }
    public void leave(UUID player) { var e = entries.get(latest.get(player)); if (e != null) e.closed = true; }
    public void forget(UUID player) { leave(player); latest.remove(player); }
    public void expire(long now) {
        entries.values().removeIf(e -> now - e.received >= KEEP_TICKS);
        latest.values().removeIf(id -> !entries.containsKey(id));
    }
}
