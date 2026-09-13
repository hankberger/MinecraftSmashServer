package dev.hanks.vanilla;

/** Minecraft 26.2 BowItem charge curve and AbstractArrow flight constants, shared with prediction. */
public final class BowRules {
    public static final int FULL_DRAW_TICKS = 20;
    public static final double DRAG = .99, GRAVITY = .05;
    public static final float DRAW_MOVEMENT = .2f;
    private BowRules() { }
    public static float power(int ticks) {
        float t = Math.clamp(ticks,0,FULL_DRAW_TICKS) / 20f;
        return (t*t + 2*t) / 3f;
    }
    public static double speed(int ticks) { return 3 * power(ticks); }
    public static boolean canFire(int ticks) { return power(ticks) >= .1f; }
    public static int damage(int ticks) { return 5 + (int)(5 * power(ticks)); }
}
