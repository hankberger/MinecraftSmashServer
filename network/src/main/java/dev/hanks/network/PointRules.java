package dev.hanks.network;

import java.util.Locale;
import java.util.UUID;

/** Earnable currency, separate from match damage and any future competitive rating. */
public final class PointRules {
    public static final int FINISH = 50, WIN = 25;
    public record Reward(int finish, int win) { public int total() { return finish + win; } }
    public static Reward reward(Wire.MatchResult match, UUID player) {
        var row = match.rows().stream().filter(r -> r.player().equals(player)).findFirst().orElseThrow();
        if (row.forfeited() || !Wire.MODES.contains(match.mode()) || Wire.capacity(match.mode()) < 2) return new Reward(0,0);
        return new Reward(FINISH, player.equals(match.winner()) ? WIN : 0);
    }
    public static String format(long points) { return String.format(Locale.ROOT,"%,d",points); }
    public static String compact(long points) {
        if (points < 1_000_000) return format(points);
        if (points < 1_000_000_000) return String.format(Locale.ROOT,"%.1fM",points / 1_000_000.0);
        return String.format(Locale.ROOT,"%.1fB",points / 1_000_000_000.0);
    }
    private PointRules() {}
}
