package dev.hanks.vanilla;

/** Player slots, not character choices, determine identity throughout a round. */
public final class PlayerIdentity {
    private static final int[] COLORS = {0xff7373, 0x65bcff, 0xffd866, 0x81e6ad};
    public static int color(int slot) { return COLORS[Math.floorMod(slot - 1, COLORS.length)]; }
    private PlayerIdentity() {}
}
