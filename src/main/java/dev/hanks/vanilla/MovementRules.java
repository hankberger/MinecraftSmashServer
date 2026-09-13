package dev.hanks.vanilla;

/** Arena-only tuning; vanilla sprint adds its usual multiplier to the faster base. */
public final class MovementRules {
    public static final double RUN_BONUS = .40;
    public static final double AIR_ACCELERATION = .055;
    public static final double AIR_SPEED = .60, SPRINT_AIR_SPEED = .75;
    public static final double FAST_FALL_START = -.50;
    private MovementRules() {}
    public static double fastFallVelocity(double current) {
        // Never soften an existing downward launch that already exceeds this speed.
        return Math.min(current, Math.max(-1.20, current - .045));
    }
}
