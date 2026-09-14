package dev.hanks.vanilla;

/** Physics for the server-owned arena actors, in blocks per tick. */
public final class MovementRules {
    public static final double JUMP = 1.02;
    public static final double RISE_GRAVITY = .095, FALL_GRAVITY = .14;
    public static final double RUN_SPEED = .39, SPRINT_SPEED = .50;
    public static final double AIR_ACCELERATION = .085, GROUND_RESPONSE = .65;
    public static final double LAUNCH_DRAG = .985;
    public static final double FAST_FALL_START = -.80;
    private MovementRules() {}
    public static double gravity(double current) {
        // Preserve spikes that already exceed the ordinary terminal fall speed.
        return Math.min(current, Math.max(-1.8, current - (current > 0 ? RISE_GRAVITY : FALL_GRAVITY)));
    }
    public static double steer(double current, int axis, boolean sprint, boolean grounded, double run, double air, double slow) {
        double target = axis * (sprint ? SPRINT_SPEED : RUN_SPEED) * run * slow;
        return current + (grounded ? (target - current) * GROUND_RESPONSE
                : Math.clamp(target - current, -AIR_ACCELERATION * air, AIR_ACCELERATION * air));
    }
    public static double fastFallVelocity(double current) {
        // Never soften an existing downward launch that already exceeds this speed.
        return Math.min(current, Math.max(-2.0, current - .10));
    }
}
