package dev.hanks.network;

import java.util.Locale;
import java.util.UUID;

/** Earnable currency, separate from match damage and any future competitive rating. */
public final class PointRules {
    public static final int FINISH = 50, WIN = 25;
    public record Reward(int finish, int win) { public int total() { return finish + win; } }
    public enum Reason {
        EARNED(""), LEFT_EARLY("No points · Left early"), TOO_SHORT("No points · Short match"), INACTIVE("No points · Inactive"), TRAINING("No points · Practice");
        public final String label;
        Reason(String label){this.label=label;}
    }
    public static Reason reason(Wire.MatchResult match,UUID player) {
        var row = match.rows().stream().filter(r -> r.player().equals(player)).findFirst().orElseThrow();
        if(row.forfeited())return Reason.LEFT_EARLY;
        if(!Wire.MODES.contains(match.mode()) || Wire.capacity(match.mode())<2)return Reason.TRAINING;
        if(match.evidence()!=null) {
            if(match.evidence().roundTicks()<EconomyRules.MIN_MATCH_TICKS)return Reason.TOO_SHORT;
            if(match.evidence().players().get(player).activeTicks()<EconomyRules.MIN_ACTIVE_TICKS)return Reason.INACTIVE;
        }
        return Reason.EARNED;
    }
    public static Reward reward(Wire.MatchResult match, UUID player) {
        if(reason(match,player)!=Reason.EARNED)return new Reward(0,0);
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
