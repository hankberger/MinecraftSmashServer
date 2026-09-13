package dev.hanks.vanilla;

public enum AttackDirection {
    FORWARD, UP, DOWN;
    public static AttackDirection input(boolean up, boolean down) {
        return up == down ? FORWARD : up ? UP : DOWN;
    }
}
