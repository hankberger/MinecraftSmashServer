package dev.hanks.vanilla;

/** Quiet close framing that makes room for opponents without chasing distant KO flights. */
public final class FollowCamera {
    public record Frame(double x, double eyeY, double distance) {}
    public record Focus(double x, double y) {}
    // Conservative safe frame at 70 FOV, including room for bodies and the bottom HUD.
    private static final double HALF_WIDTH = .82, HALF_HEIGHT = .55;
    private double x, y = ArenaRules.CAMERA_Y, z, goalX, goalY = ArenaRules.CAMERA_Y, distance;
    private double vx, vy, vz;
    public FollowCamera(double startX, double distance) {
        x = goalX = Math.clamp(startX, -6, 7); z = this.distance = distance;
    }
    public void distance(double value) { distance = Math.clamp(value,14,40); }
    public Frame frame() { return new Frame(x,y,z); }
    public Frame tick(double focusX, double focusY) {
        return tick(focusX,focusY,java.util.List.of());
    }
    public Frame tick(double focusX, double focusY, java.util.List<Focus> opponents) {
        double desiredDistance = target(focusX, focusY, opponents);
        vx = damp(vx,goalX-x,.95); vy = damp(vy,goalY-y,1.05); vz = damp(vz,desiredDistance-z,.3);
        x += vx; y += vy; z += vz;
        return frame();
    }
    /** Fit the opening roster before publishing the first camera frame. */
    public static FollowCamera opening(double distance, java.util.List<Focus> fighters) {
        var camera = new FollowCamera(ArenaRules.CAMERA_X, distance);
        if (!fighters.isEmpty()) {
            var first = fighters.getFirst();
            camera.z = camera.target(first.x, first.y, fighters.subList(1, fighters.size()));
            camera.x = camera.goalX; camera.y = camera.goalY;
        }
        return camera;
    }
    private double target(double focusX, double focusY, java.util.List<Focus> opponents) {
        double desiredDistance = distance;
        if (Double.isFinite(focusX) && Double.isFinite(focusY)) {
            double minX=focusX,maxX=focusX,minY=focusY,maxY=focusY;
            for(var other:opponents) if(Double.isFinite(other.x) && Double.isFinite(other.y)) {
                // Extreme separation must never pull the controlling fighter off their own screen.
                double ox=Math.clamp(other.x,focusX-36,focusX+36),oy=Math.clamp(other.y,focusY-20,focusY+20);
                minX=Math.min(minX,ox);maxX=Math.max(maxX,ox);minY=Math.min(minY,oy);maxY=Math.max(maxY,oy);
            }
            goalX = Math.clamp(deadZone(goalX, (minX+maxX)/2, 4), -25, 26);
            goalY = Math.clamp(deadZone(goalY, (minY+maxY)/2, 3.5), 62, 95);
            if(!opponents.isEmpty()) {
                double required=Math.max((maxX-minX+3)/(2*HALF_WIDTH),(maxY-minY+3)/(2*HALF_HEIGHT));
                desiredDistance=Math.clamp(required,distance,Math.max(distance,26));
                double halfX=desiredDistance*HALF_WIDTH-1.5,halfY=desiredDistance*HALF_HEIGHT-1.5;
                goalX=fit(goalX,minX,maxX,halfX);goalY=fit(goalY,minY,maxY,halfY);
            }
        }
        return desiredDistance;
    }
    private static double fit(double center,double low,double high,double radius) {
        double minimum=high-radius,maximum=low+radius;
        return minimum>=maximum ? (low+high)/2 : Math.clamp(center,minimum,maximum);
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
