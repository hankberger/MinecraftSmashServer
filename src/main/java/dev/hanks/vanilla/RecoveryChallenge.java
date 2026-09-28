package dev.hanks.vanilla;

/** Three short recoveries, with a readable setup and automatic retries. No persistent rewards. */
public final class RecoveryChallenge {
    public enum Phase { READY, ATTEMPT, RETRY, SUCCESS, COMPLETE }
    public static final int TARGET = 3, READY_TICKS = 40, ATTEMPT_TICKS = 160, FEEDBACK_TICKS = 20;
    private Phase phase = Phase.READY;
    private int until, completed, attempts;
    public RecoveryChallenge(int now) { until = now + READY_TICKS; }
    public Phase phase() { return phase; }
    public int completed() { return completed; }
    public int attempts() { return attempts; }
    public int side() { return completed == 1 ? 1 : -1; }
    public double offset() { return 2.5 + completed * .5; }
    public double startY() { return ArenaRules.DECK_Y - 4 - completed; }
    public boolean frozen() { return phase == Phase.READY || phase == Phase.RETRY || phase == Phase.SUCCESS; }
    public boolean active() { return phase != Phase.COMPLETE; }
    public boolean tick(int now, boolean safeLanding, boolean usedRecovery, boolean fallen) {
        var before = phase;
        if (phase == Phase.ATTEMPT) {
            if (safeLanding && usedRecovery) {
                completed++;
                phase = completed == TARGET ? Phase.COMPLETE : Phase.SUCCESS;
                until = now + FEEDBACK_TICKS;
            } else if (fallen || now >= until) { phase = Phase.RETRY; until = now + FEEDBACK_TICKS; }
        } else if (frozen() && now >= until) {
            phase = Phase.ATTEMPT; attempts++; until = now + ATTEMPT_TICKS;
        }
        return before != phase;
    }
    public String label(int now, boolean hanging) {
        String progress = "RECOVERY  " + completed + "/" + TARGET;
        return switch (phase) {
            case READY -> progress + "  ·  Get ready " + Math.max(1, (until-now+19)/20) + "\nTap Space to jump; release, then tap again to recover";
            case ATTEMPT -> progress + "  ·  " + Math.max(0, (until-now+19)/20) + "s\n"
                    + (hanging ? "Hold toward the stage to climb" : "Steer toward the ledge · Space, release, Space");
            case RETRY -> progress + "\nTry again · Save your recovery for the ledge";
            case SUCCESS -> progress + "\nNice recovery! Next side…";
            case COMPLETE -> "RECOVERY COMPLETE!\n" + attempts + " attempts · /smash challenge recovery to retry";
        };
    }
}
