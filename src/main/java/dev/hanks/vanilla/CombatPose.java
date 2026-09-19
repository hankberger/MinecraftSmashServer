package dev.hanks.vanilla;

/** Mirrors the stock living-entity three-tick interpolation, without guessing network latency. */
public final class CombatPose {
    public double x, y, previousX, previousY;
    private double targetX, targetY;
    private int remaining;
    public CombatPose(double x, double y) { reset(x, y); }
    public void reset(double x, double y) {
        this.x = previousX = targetX = x; this.y = previousY = targetY = y; remaining = 0;
    }
    public void advance(double destinationX, double destinationY) {
        previousX = x; previousY = y;
        // The entity tracker quantizes relative movement to 1/4096 block and omits subpixel changes.
        double dx = destinationX - targetX, dy = destinationY - targetY;
        if (dx * dx + dy * dy >= 7.62939453125E-6) {
            targetX = Math.round(destinationX * 4096) / 4096.0;
            targetY = Math.round(destinationY * 4096) / 4096.0;
            remaining = 3;
        }
        if (remaining > 0) {
            x += (targetX - x) / remaining; y += (targetY - y) / remaining; remaining--;
        }
    }
}
