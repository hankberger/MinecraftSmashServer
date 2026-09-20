package dev.hanks.vanilla;

/** A bounded ledge visit. Only landing (or respawn) restores grabs; hanging restores no air resources. */
public final class LedgeState {
    public static final int SETTLE_TICKS = 4, PROTECTION_TICKS = 10, HANG_TICKS = 40;
    public static final int CLIMB_TICKS = 8, REGRAB_TICKS = 12, MAX_GRABS = 2;
    public static final double HANG_Y = ArenaRules.DECK_Y - 1.75;
    public enum Action { NONE, CLIMB, JUMP, DROP }
    private int side, grabbedAt, climbingAt = -1, nextGrabAt, grabs;
    public int side() { return side; }
    public boolean attached() { return side != 0; }
    public boolean climbing() { return climbingAt >= 0; }
    public int grabs() { return grabs; }
    public static double edge(int side) { return side < 0 ? ArenaRules.DECK_LEFT : ArenaRules.DECK_RIGHT; }
    public static double hangX(int side) { return edge(side) + side * .75; }
    public static double standX(int side) { return edge(side) - side * .8; }

    /** Swept catch area outside the main deck; never snaps a rising fighter or one underneath the island. */
    public int candidate(int now, double fromX, double fromY, double x, double y, double vy) {
        if (attached() || now < nextGrabAt || grabs >= MAX_GRABS || vy > 0
                || !Double.isFinite(fromX + fromY + x + y + vy)) return 0;
        for (int candidate : new int[]{-1, 1}) {
            double outsideFrom = (fromX - edge(candidate)) * candidate;
            double outsideTo = (x - edge(candidate)) * candidate;
            if (outsideFrom < .3) continue;
            double[] interval = {0, 1};
            if (clip(interval, outsideFrom, outsideTo, .3, 2.0)
                    && clip(interval, fromY, y, ArenaRules.DECK_Y - 3, ArenaRules.DECK_Y - .6)) return candidate;
        }
        return 0;
    }
    private static boolean clip(double[] interval, double from, double to, double min, double max) {
        double delta = to - from;
        if (Math.abs(delta) < 1e-9) return from >= min && from <= max;
        double a = (min - from) / delta, b = (max - from) / delta;
        interval[0] = Math.max(interval[0], Math.min(a, b));
        interval[1] = Math.min(interval[1], Math.max(a, b));
        return interval[0] <= interval[1];
    }
    public void grab(int side, int now) {
        if (side != -1 && side != 1) throw new IllegalArgumentException("Invalid ledge");
        this.side = side; grabbedAt = now; climbingAt = -1; grabs++;
    }
    public boolean firstGrab() { return grabs == 1; }
    public Action action(int now, boolean freshJump, boolean down, int axis) {
        if (!attached() || climbing() || now < grabbedAt + SETTLE_TICKS) return Action.NONE;
        if (down || now >= grabbedAt + HANG_TICKS) return Action.DROP;
        if (freshJump) return Action.JUMP;
        return axis == -side ? Action.CLIMB : Action.NONE;
    }
    public void climb(int now) { climbingAt = now; }
    public boolean climbFinished(int now) { return climbing() && now >= climbingAt + CLIMB_TICKS; }
    public double x(int now) {
        if (!climbing()) return hangX(side);
        double across = Math.clamp((now - climbingAt - CLIMB_TICKS / 2.0) / (CLIMB_TICKS / 2.0), 0, 1);
        return hangX(side) + (standX(side) - hangX(side)) * smooth(across);
    }
    public double y(int now) {
        if (!climbing()) return HANG_Y;
        double rise = Math.clamp((now - climbingAt) / (CLIMB_TICKS / 2.0), 0, 1);
        return HANG_Y + (ArenaRules.DECK_Y - HANG_Y) * smooth(rise);
    }
    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    public void release(int now) { side = 0; climbingAt = -1; nextGrabAt = now + REGRAB_TICKS; }
    public void land() { side = grabs = nextGrabAt = 0; climbingAt = -1; }
}
