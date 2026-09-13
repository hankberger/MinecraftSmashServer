package dev.hanks.vanilla;

public final class RespawnRules {
    public static final int FLOAT_TICKS = 32, LANDING_PROTECTION = 25;
    public static final double X = .5, TOP_Y = 95, LANDING_Y = 89;
    private RespawnRules() {}
    public static double y(long elapsed) {
        double t = Math.clamp(elapsed / (double) FLOAT_TICKS, 0, 1);
        return TOP_Y + (LANDING_Y - TOP_Y) * t * t * (3 - 2 * t);
    }
    public static boolean dim(int protectionTicks) { return protectionTicks > 0 && protectionTicks % 8 < 4; }
}
