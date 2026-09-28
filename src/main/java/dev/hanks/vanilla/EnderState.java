package dev.hanks.vanilla;

import java.util.UUID;

/** One visible prey, one aerial pursuit. Marking never refunds movement resources. */
public final class EnderState {
    public static final int MARK_TICKS = 80, PURSUIT_COOLDOWN = 28;
    public static final double PURSUIT_RANGE = 14, PHASE_DISTANCE = 4, PEARL_UP = 11, PEARL_SIDE = 8;
    public record Point(double x, double y) {}
    private UUID prey;
    private long expires, pursuitReadyAt;
    private boolean airPursuitUsed, departed;
    public UUID prey(long now) { if (now >= expires) prey = null; return prey; }
    public long remaining(long now) { return prey(now) == null ? 0 : expires - now; }
    public void mark(long now, UUID target) { prey = target; expires = now + MARK_TICKS; }
    public void forget() { prey = null; expires = 0; }
    public boolean canPursue(long now, boolean grounded) { return now >= pursuitReadyAt && (grounded || !airPursuitUsed); }
    public long cooldown(long now) { return Math.max(0, pursuitReadyAt - now); }
    public void pursue(long now) { pursuitReadyAt = now + PURSUIT_COOLDOWN; spendAir(); forget(); }
    public void spendAir() { airPursuitUsed = departed = true; }
    public boolean airAvailable() { return !airPursuitUsed; }
    public void grounded(boolean grounded) {
        if (!grounded) departed = true;
        else if (departed) { departed = false; airPursuitUsed = false; }
    }
    public void reset() { forget(); pursuitReadyAt = 0; airPursuitUsed = departed = false; }
}
