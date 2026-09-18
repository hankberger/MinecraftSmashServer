package dev.hanks.vanilla;

/** Two endpoints for a short pullback interpolated entirely by the vanilla client. */
public final class WinnerCamera {
    public static final int ATTACH_TICK = 20, MOVE_TICK = 24, DURATION = 24;
    public static final int REVEAL_TICK = MOVE_TICK + DURATION + 6;
    public static final double PLAYER_EYE_HEIGHT = 1.62;
    public record Frame(double x, double y, double z, float yaw, float pitch) {}
    // Coordinates describe the viewer's eye. Keep the same heading and use an
    // exact protocol angle so attaching and the destination cannot change it.
    public static final Frame START = new Frame(2, 106.12, 10.5, 180, 8.4375f);
    public static final Frame END = new Frame(0, 106.12, 14, 180, 8.4375f);
    private WinnerCamera() {}
}
