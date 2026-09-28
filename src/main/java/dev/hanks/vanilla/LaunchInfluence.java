package dev.hanks.vanilla;

/** One angle adjustment at the end of hit pause. Never adds launch speed or cancels stun. */
public final class LaunchInfluence {
    public static final double MAX_ANGLE = Math.toRadians(12);
    private LaunchInfluence() {}

    public static CombatRules.Launch apply(CombatRules.Launch launch, int horizontal, int vertical) {
        double speed = Math.hypot(launch.x(), launch.y());
        double input = Math.hypot(horizontal, vertical);
        if (speed < .001 || input == 0) return launch;
        double cross = (launch.x() * vertical - launch.y() * horizontal) / (speed * input);
        double angle = Math.clamp(cross, -1, 1) * MAX_ANGLE;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new CombatRules.Launch(launch.x() * cos - launch.y() * sin,
                launch.x() * sin + launch.y() * cos, launch.stun());
    }
}
