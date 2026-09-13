package dev.hanks.vanilla;

public final class LobbyRules {
    public static final double SPAWN_X = .549, SPAWN_Y = 101, SPAWN_Z = -105.631;
    public static final float SPAWN_YAW = 0; // South (+Z), looking into the garden.
    public static final double RESCUE_X = .5, RESCUE_Y = 101, RESCUE_Z = -2.5;
    private LobbyRules() {}
    /** All four satellites and bridges fit; return before a fall reaches the void. */
    public static boolean needsReturn(double x, double y, double z) {
        return !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || y < 92 || y > 250 || Math.abs(x) > 128 || z < -128 || z > 120;
    }
}
