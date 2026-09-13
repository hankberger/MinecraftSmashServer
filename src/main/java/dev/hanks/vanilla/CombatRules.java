package dev.hanks.vanilla;

/** Initial tuning, in server ticks (20/second), blocks, and blocks/tick. */
public final class CombatRules {
    public static final int DAMAGE = AttackKind.LIGHT.damage;
    public static final int WINDUP = AttackKind.LIGHT.windup;
    public static final int COOLDOWN = AttackKind.LIGHT.cooldown;
    public static final int HIT_IMMUNITY = 4;
    public static final int GUARD_CAPACITY = 100, GUARD_LEASE = 12, GUARD_REGEN_DELAY = 20, GUARD_BREAK_STUN = 30;
    public static final int CREDIT_WINDOW = 200;
    public static final int MAX_PERCENT = 999;
    public static final double REACH = AttackKind.LIGHT.reach;
    private CombatRules() {}

    public record Launch(double x, double y, int stun) {}
    public static Launch launch(int percent, int direction) {
        int p = Math.clamp(percent, 0, MAX_PERCENT);
        return new Launch((direction < 0 ? -1 : 1) * Math.min(3.2, 0.50 + p * 0.012),
                Math.min(1.0, 0.28 + p * 0.003), Math.min(26, 8 + p / 8));
    }

    public static int direction(float yaw) { return -Math.sin(Math.toRadians(yaw)) < 0 ? -1 : 1; }
}
