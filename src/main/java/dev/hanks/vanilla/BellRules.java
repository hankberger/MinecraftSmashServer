package dev.hanks.vanilla;

/** Batting changes placement, never the bell's damage or number of simultaneous traps. */
public final class BellRules {
    public static final double RADIUS = 1.5;
    public record Toss(double x, double y) {}
    private BellRules() {}
    public static Toss bat(AttackDirection aim, int facing) {
        double sign = facing < 0 ? -1 : 1;
        return switch (aim) {
            case FORWARD -> new Toss(.60 * sign, .22);
            case UP -> new Toss(.18 * sign, .72);
            case DOWN -> new Toss(.10 * sign, -.55);
            case NEUTRAL -> new Toss(.28 * sign, .35);
        };
    }
}
