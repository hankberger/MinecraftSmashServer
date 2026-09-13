package dev.hanks.vanilla;

/** Pure layout/rules shared by the server, camera, and regression tests. */
public final class ArenaRules {
    public static final int CAPACITY = 4;
    public static final double PLANE_Z = 0.5;
    public static final int FLOOR_Y = 80;
    public static final double CAMERA_X = 0.5;
    public static final double CAMERA_Y = 84.0;
    public static final float CAMERA_FOV = 50.0f;
    private static final double[] SPAWN_X = {-14.5, -2.5, 3.5, 15.5};
    private ArenaRules() {}

    public static double spawnX(int slot) {
        if (slot < 0 || slot >= CAPACITY) throw new IllegalArgumentException("Invalid arena slot");
        return SPAWN_X[slot];
    }

    public static boolean outside(double x, double y, double z) {
        return !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || x < -26 || x > 27 || y < 67 || y > 105 || Math.abs(z - PLANE_Z) > 8;
    }

    public static boolean platform(int x, int y, int z) {
        return z >= -1 && z <= 2 && ((y == 84 && ((x >= -12 && x <= -5) || (x >= 5 && x <= 12)))
                || (y == 88 && x >= -3 && x <= 3));
    }

    public static boolean standingOnPlatform(double x, double y, double z) {
        return Math.abs(y - Math.rint(y)) < .08 && platform((int) Math.floor(x), (int) Math.round(y) - 1, (int) Math.floor(z));
    }

    /** Fit the whole stage even in a narrow window, independent of user FOV. */
    public static double cameraDistance(double aspect) {
        double tan = Math.tan(Math.toRadians(CAMERA_FOV / 2.0));
        return Math.max(17.0 / tan, 28.0 / (tan * Math.max(0.25, aspect)));
    }
}


