package dev.hanks.vanilla;

/** Personal side camera: a quiet center area, damped travel, fixed heading and user-selected zoom. */
public final class FollowCamera {
    public record Frame(double x, double eyeY, double distance) {}
    private double x, y = ArenaRules.CAMERA_Y, z, goalX, goalY = ArenaRules.CAMERA_Y, distance;
    private double vx, vy, vz;
    public FollowCamera(double startX, double distance) {
        x = goalX = Math.clamp(startX, -6, 7); z = this.distance = distance;
    }
    public void distance(double value) { distance = Math.clamp(value,14,40); }
    public Frame frame() { return new Frame(x,y,z); }
    public Frame tick(double focusX, double focusY) {
        if (Double.isFinite(focusX) && Double.isFinite(focusY)) {
            goalX = Math.clamp(deadZone(goalX, focusX, 4), -25, 26);
            goalY = Math.clamp(deadZone(goalY, focusY, 3.5), 62, 95);
        }
        vx = damp(vx,goalX-x,.95); vy = damp(vy,goalY-y,1.05); vz = damp(vz,distance-z,.4);
        x += vx; y += vy; z += vz;
        return frame();
    }
    private static double deadZone(double center, double focus, double radius) {
        return center + Math.copySign(Math.max(0,Math.abs(focus-center)-radius),focus-center);
    }
    private static double damp(double velocity, double error, double limit) {
        if (Math.abs(error)<.001 && Math.abs(velocity)<.001) return 0;
        // Overdamped spring: continuous velocity without overshoot at ordinary combat speeds.
        return Math.clamp(velocity*.35 + error*.12,-limit,limit);
    }
}
