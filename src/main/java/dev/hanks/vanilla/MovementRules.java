package dev.hanks.vanilla;

/** Physics for the server-owned arena actors, in blocks per tick. */
public final class MovementRules {
    public static final double JUMP = 1.02;
    public static final double RISE_GRAVITY = .095, FALL_GRAVITY = .14;
    public static final double RUN_SPEED = .38, SPRINT_SPEED = RUN_SPEED;
    public static final double SHORT_HOP_CUTOFF = .40;
    public static final double AIR_ACCELERATION = .06, GROUND_RESPONSE = .70;
    public static final double AIR_COAST_DRAG = .985;
    public static final double LAUNCH_DRAG = .985;
    public static final double FALL_LIMIT = -1.20, FAST_FALL_LIMIT = -1.45;
    private MovementRules() {}
    public static double gravity(double current) {
        // Preserve spikes that already exceed the ordinary terminal fall speed.
        return Math.min(current, Math.max(FALL_LIMIT, current - (current > 0 ? RISE_GRAVITY : FALL_GRAVITY)));
    }
    public static double steer(double current, int axis, boolean sprint, boolean grounded, double run, double air, double slow) {
        if (!grounded && axis == 0) {
            // Let go of a direction to attack without braking the jump. Opposite input still brakes normally.
            double drift = current * AIR_COAST_DRAG;
            double limit = RUN_SPEED * run * slow;
            // Drawing a bow must still slow the fighter even after releasing the movement key.
            if (slow < 1 && Math.abs(drift) > limit)
                drift = Math.copySign(Math.max(limit, Math.abs(drift) - AIR_ACCELERATION * air), drift);
            return Math.abs(drift) < .003 ? 0 : drift;
        }
        double target = axis * RUN_SPEED * run * slow;
        return current + (grounded ? (target - current) * GROUND_RESPONSE
                : Math.clamp(target - current, -AIR_ACCELERATION * air, AIR_ACCELERATION * air));
    }
    public static double fastFallVelocity(double current) {
        // Never soften an existing downward launch that already exceeds this speed.
        return Math.min(current, Math.max(FAST_FALL_LIMIT, current - .045));
    }
}
