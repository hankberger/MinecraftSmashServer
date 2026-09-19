package dev.hanks.vanilla;

/** Releasing jump trims only a voluntary jump, never a launch or a recovery. */
public final class JumpHeight {
    private boolean rising;
    public void start() { rising = true; }
    public void clear() { rising = false; }
    public double apply(double velocity, boolean held) {
        if (velocity <= 0) rising = false;
        if (!rising || held) return velocity;
        rising = false;
        return Math.min(velocity, MovementRules.SHORT_HOP_CUTOFF);
    }
}
