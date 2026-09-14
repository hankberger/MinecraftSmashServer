package dev.hanks.vanilla;

/** A short eased pullback from the winner to the complete podium and replay controls. */
public final class WinnerCamera {
    public static final int ATTACH_TICK = 20, REVEAL_TICK = 75;
    public record Frame(double x, double y, double z, float yaw, float pitch) {}
    public static Frame at(int age) {
        double t = Math.clamp((age - ATTACH_TICK) / 50.0, 0, 1);
        double ease = t * t * (3 - 2 * t);
        double x = 8 * (1 - ease), y = 104.8 - .3 * ease, z = 10 + 4 * ease;
        double targetX = 4 * (1 - ease), targetY = 105.5 - 1.5 * ease;
        double dx = targetX - x, dz = -z;
        return new Frame(x, y, z, (float)((Math.toDegrees(Math.atan2(-dx, dz)) + 360) % 360),
                (float)-Math.toDegrees(Math.atan2(targetY - y - 1.62, Math.hypot(dx, dz))));
    }
    private WinnerCamera() {}
}
