package dev.hanks.vanilla;

import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Small reusable garden sets, separated beyond vanilla's maximum entity tracking distance. */
public final class ShowcaseBuilder {
    public static final int SPACING = 1024;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private final ServerLevel level;
    private final int origin;
    private final Map<String, BlockState> states = new HashMap<>();
    private ShowcaseBuilder(ServerLevel level, int room) { this.level = level; origin = room * SPACING; }
    public static double rosterX(int index) { return -8 + (index % 3) * 3; }
    public static double rosterY(int index) { return index < 3 ? 104.5 : 101; }
    public static double rosterZ(int index) { return index < 3 ? .15 : 2.1; }
    public static void ensureBuilt(ServerLevel level, int room) {
        if (!level.dimension().equals(MvpWorlds.SHOWCASE)) throw new IllegalArgumentException("Not the showcase dimension");
        var builder = new ShowcaseBuilder(level, room);
        if (!level.getBlockState(new BlockPos(builder.origin, 93, 0)).is(Blocks.LODESTONE)) builder.build();
    }
    private void build() {
        // An island with exposed roots, a stone presentation dais, and tiered garden shelves.
        for (int y = 94; y <= 99; y++) for (int x = -13; x <= 13; x++) for (int z = -8; z <= 8; z++) {
            double radius = (x * x / 169.0) + (z * z / 64.0);
            if (radius > 1 - (99 - y) * .11) continue;
            put(x, y, z, y == 99 ? Blocks.MOSS_BLOCK : y == 98 ? Blocks.ROOTED_DIRT
                    : Math.floorMod(x * 17 + z * 11 + y, 5) == 0 ? Blocks.TUFF : Blocks.STONE);
        }
        for (int x = -10; x <= 9; x++) for (int z = 3; z <= 5; z++)
            put(x, 99, z, Math.floorMod(x + z, 4) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
        // Main round plinth, low enough to expose the fighter's full silhouette.
        for (int x = 1; x <= 7; x++) for (int z = -3; z <= 3; z++) {
            double distance = Math.hypot(x - 4, z);
            if (distance > 3.4) continue;
            put(x, 100, z, Blocks.STONE_BRICKS);
            put(x, 101, z, distance > 2.5 ? Blocks.SMOOTH_QUARTZ : Blocks.POLISHED_ANDESITE);
            if (distance > 2.5 && z >= 0) put(x, 100, z, state("waxed_oxidized_copper"));
        }
        // Backing wall keeps the miniature roster legible against the foliage.
        fill(-9, 100, -2, -1, 107, -2, Blocks.STRIPPED_DARK_OAK_LOG);
        for (int y : new int[]{100, 104, 107}) fill(-9, y, -1, -1, y, -1, Blocks.DARK_OAK_SLAB);
        for (int i = 0; i < 5; i++) {
            int x = (int)Math.floor(rosterX(i)); int y = (int)Math.floor(rosterY(i)) - 1;
            int z = i < 3 ? -1 : 1;
            fill(x - 1, y, z, x, y, z + 1, Blocks.POLISHED_ANDESITE);
            if (i < 3) fill(x - 1, y + 1, -1, x, y + 1, 0, Blocks.SMOOTH_QUARTZ_SLAB);
        }
        // Timber lantern arch behind the main stage, with flowering vines.
        for (int x : new int[]{0, 8}) {
            fill(x, 100, -4, x, 109, -4, Blocks.STRIPPED_OAK_LOG);
            put(x, 110, -4, Blocks.MOSSY_STONE_BRICK_SLAB);
        }
        for (int x = 0; x <= 8; x++) put(x, 110, -4, state("oak_log[axis=x]"));
        for (int x : new int[]{1, 7}) {
            put(x, 109, -4, state("iron_chain[axis=y]"));
            put(x, 108, -4, state("lantern[hanging=true]"));
        }
        for (int x : new int[]{-12, 11}) tree(x, -5);
        for (int[] p : new int[][]{{-10,2},{-8,6},{-3,6},{9,3},{10,0},{7,-5},{-5,-5}}) {
            put(p[0], 100, p[1], state(p[0] % 2 == 0 ? "flowering_azalea" : "pink_tulip"));
            put(p[0] + 1, 100, p[1], state("short_grass"));
        }
        for (int x : new int[]{-11, 10}) { put(x, 100, 4, Blocks.MOSSY_STONE_BRICKS); put(x, 101, 4, Blocks.LANTERN); }
        for (int[] root : new int[][]{{-7,97,3},{9,96,-1},{-2,94,-2}})
            fill(root[0], root[1] - 2, root[2], root[0], root[1], root[2], Blocks.OAK_LOG);
        put(0, 93, 0, Blocks.LODESTONE);
        VanillaSmash.LOG.info("Built character garden stage room={}", origin / SPACING);
    }
    private void tree(int x, int z) {
        fill(x, 99, z, x, 108, z, Blocks.CHERRY_LOG);
        for (int y = 106; y <= 111; y++) for (int dx = -4; dx <= 4; dx++) for (int dz = -3; dz <= 3; dz++) {
            double d = dx * dx / 17.0 + dz * dz / 11.0 + Math.pow((y - 108.5) / 3, 2);
            if (d < 1.3 && (Math.abs(dx) > 0 || Math.abs(dz) > 0))
                put(x + dx, y, z + dz, state("cherry_leaves[persistent=true]"));
        }
    }
    private BlockState state(String name) {
        return states.computeIfAbsent(name, value -> {
            try { return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), "minecraft:" + value, false).blockState(); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalArgumentException(value, e); }
        });
    }
    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block b) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) put(x, y, z, b);
    }
    private void put(int x, int y, int z, Block block) { put(x, y, z, block.defaultBlockState()); }
    private void put(int x, int y, int z, BlockState state) { level.setBlock(new BlockPos(origin + x, y, z), state, FLAGS); }
}
