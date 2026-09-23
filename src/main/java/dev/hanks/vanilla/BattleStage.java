package dev.hanks.vanilla;

import java.util.List;
import java.util.UUID;

/** Immutable geometry shared by construction, fighters and reservation arrival cameras. */
public enum BattleStage {
    SKYBOUND_GROVE("arena", "Skybound Grove", -16, 16,
            List.of(new Platform(-12,-5,84), new Platform(5,12,84), new Platform(-3,3,88))),
    EMBERFORGE("emberforge", "Emberforge", -12, 12,
            List.of(new Platform(-4,4,85))),
    CLOUDSPIRE("cloudspire", "Cloudspire", -16, 16,
            List.of(new Platform(-13,-8,84), new Platform(8,13,84),
                    new Platform(-7,-3,88), new Platform(3,7,88), new Platform(-2,2,91)));

    public record Platform(int left, int right, int blockY) {
        public double top() { return blockY + 1; }
        public boolean supports(double x) { return x >= left - .3 && x <= right + 1.3; }
    }
    public final String id, label;
    public final int left, right;
    public final List<Platform> platforms;
    BattleStage(String id, String label, int left, int right, List<Platform> platforms) {
        this.id=id; this.label=label; this.left=left; this.right=right; this.platforms=List.copyOf(platforms);
    }
    /** A reservation UUID is generated once by matchmaking. Retries/arrivals use that same draw. */
    public static BattleStage select(UUID match, boolean training) {
        if (training || match == null) return SKYBOUND_GROVE;
        long seed = match.getMostSignificantBits() ^ match.getLeastSignificantBits();
        return values()[Math.floorMod(seed, values().length)];
    }
    public double edge(int side) { return side < 0 ? left : right + 1; }
    public double hangX(int side) { return edge(side) + side * .75; }
    public double standX(int side) { return edge(side) - side * .8; }
    public double blastLeft() { return left - 22; }
    public double blastRight() { return right + 23; }
    public double blastTop() { return Math.max(105, respawnLanding() + 16); }
    public double spawnX(int slot, boolean duel) {
        if (slot < 0 || slot >= (duel ? 2 : ArenaRules.CAPACITY)) throw new IllegalArgumentException("Invalid arena slot");
        if (duel) return .5 + (slot == 0 ? -1 : 1) * (this == EMBERFORGE ? 8 : 10);
        return switch(slot) { case 0 -> left+1.5; case 1 -> -2.5; case 2 -> 3.5; default -> right-.5; };
    }
    public double dummyX() { return right - 1.5; }
    public boolean platform(int x, int y, int z) {
        return z >= -1 && z <= 2 && platforms.stream().anyMatch(p -> y == p.blockY && x >= p.left && x <= p.right);
    }
    public boolean standingOnPlatform(double x, double y, double z) {
        return Math.abs(y-Math.rint(y)) < .08 && platform((int)Math.floor(x), (int)Math.round(y)-1, (int)Math.floor(z));
    }
    public double landingFloor(double x, double fromY, double toY, boolean dropping) {
        double floor = x >= left-.3 && x <= right+1.3 ? ArenaRules.DECK_Y : -100;
        if (!dropping) for (var p : platforms)
            if (p.supports(x) && fromY >= p.top() && toY <= p.top()) floor = Math.max(floor,p.top());
        return floor;
    }
    public boolean supported(double x, double y) { return Math.abs(y-landingFloor(x,y,y,false)) < .001; }
    public double respawnLanding() { return platforms.stream().filter(p -> p.supports(.5)).mapToDouble(Platform::top).max().orElse(ArenaRules.DECK_Y); }
    public double respawnTop() { return respawnLanding()+6; }
    public boolean outside(double x, double y, double z) {
        return !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || x < blastLeft() || x > blastRight() || y < ArenaRules.BLAST_BOTTOM || y > blastTop() || Math.abs(z-ArenaRules.PLANE_Z)>8;
    }
}
