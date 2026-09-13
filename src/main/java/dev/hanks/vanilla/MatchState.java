package dev.hanks.vanilla;

import java.util.*;

/** Deterministic queue and round rules, independent of Minecraft and networking. */
public final class MatchState {
    public enum Phase { IDLE, COUNTDOWN, ACTIVE, RESULTS }
    public static final int STOCKS = 3, COUNTDOWN_TICKS = 100, MATCH_TICKS = 20 * 60 * 8, RESULT_TICKS = 120;
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();
    private final LinkedHashMap<UUID, Integer> stocks = new LinkedHashMap<>();
    private Phase phase = Phase.IDLE;
    private boolean practice;
    private int remaining;
    private UUID winner;
    private String reason = "";

    public Phase phase() { return phase; }
    public int remaining() { return remaining; }
    public boolean practice() { return practice; }
    public UUID winner() { return winner; }
    public String reason() { return reason; }
    public List<UUID> queue() { return List.copyOf(queue); }
    public List<UUID> roster() { return List.copyOf(stocks.keySet()); }
    public int stocks(UUID id) { return stocks.getOrDefault(id, 0); }
    public boolean contains(UUID id) { return stocks.containsKey(id); }
    public boolean fighting(UUID id) { return phase == Phase.ACTIVE && stocks(id) > 0; }
    public boolean enqueue(UUID id) { return !contains(id) && queue.add(id); }
    public void dequeue(UUID id) { queue.remove(id); }

    public List<UUID> takeFour() {
        if (phase != Phase.IDLE || queue.size() < ArenaRules.CAPACITY) return List.of();
        List<UUID> selected = queue.stream().limit(ArenaRules.CAPACITY).toList();
        selected.forEach(queue::remove);
        start(selected, false);
        return selected;
    }

    public void start(List<UUID> participants, boolean practice) {
        if (phase != Phase.IDLE || participants.size() < 2 || participants.size() > 4
                || new HashSet<>(participants).size() != participants.size()) throw new IllegalStateException("Invalid round roster");
        stocks.clear();
        participants.forEach(id -> { queue.remove(id); stocks.put(id, STOCKS); });
        this.practice = practice;
        phase = Phase.COUNTDOWN;
        remaining = COUNTDOWN_TICKS;
        winner = null;
        reason = "";
    }

    public boolean ringOut(UUID id) {
        if (!fighting(id)) return false;
        stocks.put(id, stocks(id) - 1);
        return true;
    }

    public void forfeit(UUID id) { queue.remove(id); if (contains(id)) stocks.put(id, 0); }

    /** Preserve queue priority for surviving players when a countdown is interrupted. */
    public List<UUID> cancelCountdown(UUID departed) {
        if (phase != Phase.COUNTDOWN) return List.of();
        List<UUID> survivors = roster().stream().filter(id -> !id.equals(departed)).toList();
        if (!practice) {
            LinkedHashSet<UUID> ordered = new LinkedHashSet<>(survivors);
            ordered.addAll(queue);
            queue.clear(); queue.addAll(ordered);
        }
        clearRound();
        return survivors;
    }

    /** Resolve all ring-outs together at tick end so simultaneous final falls can draw. */
    public void tick(Map<UUID, Integer> damage) {
        if (phase == Phase.COUNTDOWN && --remaining <= 0) { phase = Phase.ACTIVE; remaining = MATCH_TICKS; }
        else if (phase == Phase.ACTIVE) {
            List<UUID> alive = roster().stream().filter(id -> stocks(id) > 0).toList();
            if (alive.size() <= 1) finish(alive.isEmpty() ? null : alive.getFirst(), "Last fighter standing");
            else if (--remaining <= 0) {
                int bestStock = alive.stream().mapToInt(this::stocks).max().orElse(0);
                int lowestDamage = alive.stream().filter(id -> stocks(id) == bestStock).mapToInt(id -> damage.getOrDefault(id, 0)).min().orElse(0);
                List<UUID> leaders = alive.stream().filter(id -> stocks(id) == bestStock && damage.getOrDefault(id, 0) == lowestDamage).toList();
                finish(leaders.size() == 1 ? leaders.getFirst() : null, "Time limit");
            }
        } else if (phase == Phase.RESULTS && remaining > 0) remaining--;
    }

    public void finish(UUID winner, String reason) {
        this.winner = winner; this.reason = reason;
        phase = Phase.RESULTS; remaining = RESULT_TICKS;
    }

    public void clearRound() {
        stocks.clear(); phase = Phase.IDLE; remaining = 0; practice = false; winner = null; reason = "";
    }
}
