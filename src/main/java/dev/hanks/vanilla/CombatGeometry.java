package dev.hanks.vanilla;

/** The same clipped ellipse describes a strike's visible arc and its filled collision area. */
public final class CombatGeometry {
    private CombatGeometry() {}
    public record Point(double x, double y) {}
    public record Box(double minX, double minY, double maxX, double maxY) {}
    public record Shape(double x, double y, double rx, double ry, double from, double to, Box bounds) {
        public Point arc(double progress) {
            double angle = from + (to - from) * progress;
            return new Point(x + rx * Math.cos(angle), y + ry * Math.sin(angle));
        }
        /** Nearest point in the clipped target rectangle; null means a genuine miss. */
        public Point contact(Box target) {
            double left = Math.max(bounds.minX, target.minX), right = Math.min(bounds.maxX, target.maxX);
            double bottom = Math.max(bounds.minY, target.minY), top = Math.min(bounds.maxY, target.maxY);
            if (left > right || bottom > top) return null;
            double px = Math.clamp(x, left, right), py = Math.clamp(y, bottom, top);
            double dx = (px - x) / rx, dy = (py - y) / ry;
            return dx * dx + dy * dy <= 1 + 1e-9 ? new Point(px, py) : null;
        }
    }
    public static Shape shape(FighterMoves.Move move, int direction, double x, double y) {
        double reach = move.reach();
        if (move.aim() == AttackDirection.NEUTRAL)
            return new Shape(x, y + .95, reach, 1.05, 0, Math.PI * 2,
                    new Box(x - reach, y - .1, x + reach, y + 2));
        if (move.kind() == AttackKind.RECOVERY) return upper(x, y + .7, 1.05, 1.7);
        if (FighterMoves.isSlam(move)) return upper(x, y + .05, reach, .8);
        if (move.aim() == AttackDirection.UP) return upper(x, y + 1.25, 1.15, .55 + reach);
        if (move.aim() == AttackDirection.DOWN && move.aerial())
            return new Shape(x, y + .25, .65, reach + .25, Math.PI, 2 * Math.PI,
                    new Box(x - .65, y - reach, x + .65, y + .25));
        double bottom = y + .1, top = y + (move.aim() == AttackDirection.DOWN ? .65 : 1.85);
        double root = x + direction * .1;
        return new Shape(root, (bottom + top) / 2, reach - .1, (top - bottom) / 2,
                direction > 0 ? -Math.PI / 2 : Math.PI / 2, direction > 0 ? Math.PI / 2 : 3 * Math.PI / 2,
                new Box(direction > 0 ? root : x - reach, bottom, direction > 0 ? x + reach : root, top));
    }
    private static Shape upper(double x, double y, double rx, double ry) {
        return new Shape(x, y, rx, ry, 0, Math.PI, new Box(x - rx, y, x + rx, y + ry));
    }
    public record Contact(Point point, double attackerX, double attackerY, double targetX, double targetY) {}
    public static Box body(double x, double y, double width, double height) {
        return new Box(x - width / 2, y, x + width / 2, y + height);
    }
    /** Sample relative movement together, never two independent unions that can create ghost hits. */
    public static Contact sweep(FighterMoves.Move move, int direction,
                                double ax0, double ay0, double ax1, double ay1,
                                double bx0, double by0, double bx1, double by1, double width, double height) {
        double travel = Math.hypot((bx1 - bx0) - (ax1 - ax0), (by1 - by0) - (ay1 - ay0));
        int steps = Math.max(1, (int)Math.ceil(travel / .10));
        for (int i = 0; i <= steps; i++) {
            double t = (double)i / steps;
            double ax = ax0 + (ax1 - ax0) * t, ay = ay0 + (ay1 - ay0) * t;
            double bx = bx0 + (bx1 - bx0) * t, by = by0 + (by1 - by0) * t;
            var point = shape(move, direction, ax, ay).contact(body(bx, by, width, height));
            if (point != null) return new Contact(point, ax, ay, bx, by);
        }
        return null;
    }
}
