package dev.hanks.vanilla;

public enum AttackDirection {
    FORWARD, UP, DOWN, NEUTRAL;
    public static AttackDirection input(boolean up, boolean down) {
        return up == down ? FORWARD : up ? UP : DOWN;
    }
    public static AttackDirection input(boolean up, boolean down, boolean left, boolean right) {
        if (up != down) return up ? UP : DOWN;
        return !up && !down && !left && !right ? NEUTRAL : FORWARD;
    }
}
