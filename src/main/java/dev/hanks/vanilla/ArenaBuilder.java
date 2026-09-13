package dev.hanks.vanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Construction is restricted to the mod's empty dimension. */
public final class ArenaBuilder {
    private ArenaBuilder() {}

    public static void ensureBuilt(ServerLevel level) {
        if (!level.dimension().equals(MvpWorlds.ARENA)) throw new IllegalArgumentException("Not the arena dimension");
        // Marker for this layout revision; subsequent joins do not rebuild the stage.
        if (level.getBlockState(new BlockPos(1, 60, 0)).is(Blocks.LODESTONE)) return;
        fill(level, -16, 79, -2, 16, 79, 2, Blocks.POLISHED_DEEPSLATE);
        fill(level, -16, 80, -2, 16, 80, 2, Blocks.SMOOTH_QUARTZ);
        fill(level, -16, 79, 3, 16, 79, 3, Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.CYAN));
        fill(level, -16, 80, 3, 16, 80, 3, Blocks.SEA_LANTERN);
        for (int y = 74; y <= 78; y++) {
            int half = (y - 72) * 2;
            fill(level, -half, y, -1, half, y, 1, y % 2 == 0 ? Blocks.DEEPSLATE_TILES : Blocks.CRYING_OBSIDIAN);
        }
        platform(level, -12, -5, 84);
        platform(level, 5, 12, 84);
        platform(level, -3, 3, 88);
        Block[] colors = {Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.RED), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.BLUE), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.LIME), Blocks.CONCRETE.pick(net.minecraft.world.item.DyeColor.YELLOW)};
        for (int slot = 0; slot < ArenaRules.CAPACITY; slot++) {
            int x = (int) Math.floor(ArenaRules.spawnX(slot));
            fill(level, x, 80, -1, x, 80, 1, colors[slot]);
            put(level, x, 79, 3, colors[slot]);
        }
        // The halo is behind the fighters, outside the movement plane.
        for (int degree = 0; degree < 360; degree++) {
            double a = Math.toRadians(degree);
            int x = (int) Math.round(22 * Math.cos(a));
            int y = 85 + (int) Math.round(13 * Math.sin(a));
            put(level, x, y, -10, degree % 30 < 6 ? Blocks.SEA_LANTERN : Blocks.PURPUR_BLOCK);
        }
        for (int x : new int[]{-21, 21}) {
            fill(level, x - 1, 73, -7, x + 1, 74, -5, Blocks.POLISHED_DEEPSLATE);
            fill(level, x, 75, -6, x, 91, -6, Blocks.QUARTZ_PILLAR);
            put(level, x, 92, -6, Blocks.SEA_LANTERN);
            put(level, x, 93, -6, Blocks.AMETHYST_BLOCK);
        }
        put(level, 1, 60, 0, Blocks.LODESTONE);
    }

    private static void platform(ServerLevel level, int left, int right, int y) {
        fill(level, left, y, -1, right, y, 1, Blocks.SMOOTH_QUARTZ);
        fill(level, left, y, 2, right, y, 2, Blocks.SEA_LANTERN);
        fill(level, left, y - 1, -1, right, y - 1, 2, Blocks.AIR);
    }
    private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2, Block block) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++)
            for (int z = z1; z <= z2; z++) put(level, x, y, z, block);
    }
    private static void put(ServerLevel level, int x, int y, int z, Block block) {
        level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
    }
}




