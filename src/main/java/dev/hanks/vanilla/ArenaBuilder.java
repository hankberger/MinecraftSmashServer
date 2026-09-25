package dev.hanks.vanilla;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Skybound Grove: the website's sunlit, root-bound island, built entirely with vanilla blocks. */
public final class ArenaBuilder {
    private static final BlockPos MARKER = new BlockPos(1, 40, 0);
    private static final Block REVISION = Blocks.EMERALD_BLOCK;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private final ServerLevel level;
    private final java.util.Map<String, BlockState> states = new java.util.HashMap<>();
    private ArenaBuilder(ServerLevel level) { this.level = level; }

    public static void ensureBuilt(ServerLevel level) {
        var stage = MvpWorlds.stage(level);
        if (stage != BattleStage.SKYBOUND_GROVE) { AlternateArenaBuilder.ensureBuilt(level, stage); return; }
        // Different marker material also detects a rollback through the revision-3 builder.
        if (level.getBlockState(MARKER).is(REVISION)
                && level.getBlockState(new BlockPos(1, 60, 0)).isAir()) return;
        new ArenaBuilder(level).build();
    }

    private void build() {
        long started = System.nanoTime();
        put(1, 40, 0, Blocks.AIR);
        // Union of the old halo, mine/homestead, and this revision's distant scenery.
        fill(-84, 43, -88, 84, 122, 5, Blocks.AIR);
        island();
        for (var p : BattleStage.SKYBOUND_GROVE.platforms) platform(p);
        backdrop();
        put(1, 40, 0, REVISION); // Publish only after the entire build succeeds.
        VanillaSmash.LOG.info("Built Skybound Grove arena revision 4 in {} ms", (System.nanoTime() - started) / 1_000_000);
    }

    private void island() {
        // Retain the solid collision core at the fighting plane, including its underside.
        for (int y = 71; y <= 79; y++) {
            int half = Math.min(16, 4 + (y - 71) * 2);
            for (int x = -half; x <= half; x++) for (int z = -4; z <= 1; z++) {
                if (y < 76 && Math.abs(x) == half && Math.floorMod(x + z, 3) == 0) continue;
                put(x, y, z, y >= 78 ? Blocks.DIRT : rock(x, y, z));
            }
        }
        fill(-16, 80, -2, 16, 80, 3, Blocks.GRASS_BLOCK);
        // A jagged face instead of a rectangular slice. Teeth and roots extend
        // toward the camera only; they cannot catch a recovering fighter.
        for (int x = -16; x <= 16; x++) {
            int bottom = 64 + Math.abs(x) / 2 + noise(x, 3, 0, 4);
            for (int y = bottom; y <= 79; y++) for (int z = 2; z <= 3; z++) {
                if (y < bottom + 2 && z == 3) continue;
                boolean root = noise(Math.floorDiv(x + 1, 3), 0, 0, 11) < 7;
                Block material = y >= 79 - noise(Math.floorDiv(x, 3), 0, 0, 3) ? Blocks.DIRT
                        : root ? Blocks.OAK_WOOD : rock(x / 2, y / 2, z);
                put(x, y, z, material);
            }
            if (noise(x, 0, 0, 5) == 0) put(x, 78, 3, Blocks.ROOTED_DIRT);
        }
        for (int[] root : new int[][]{{-13,71},{-10,66},{-7,69},{-4,64},{5,65},{8,68},{11,69},{13,73}}) {
            int x = root[0], bottom = root[1];
            line(x, 78, 3, x - Integer.signum(x), bottom + 2, 3, Blocks.OAK_WOOD);
            line(x - Integer.signum(x), bottom + 2, 3, x - 2 * Integer.signum(x), bottom, 2, Blocks.OAK_WOOD);
            put(x, 79, 3, Blocks.ROOTED_DIRT);
            if (x % 2 == 0) vine(x, 77, bottom + 2, 4);
            put(x - Integer.signum(x), bottom - 1, 2, Blocks.HANGING_ROOTS);
        }
        // A recessed, luminous geode is the central landmark underneath the turf.
        for (int x = -3; x <= 3; x++) for (int y = 69; y <= 78; y++) {
            double d = x * x / 12.0 + (y - 74) * (y - 74) / 23.0;
            if (d > 1) continue;
            put(x, y, 3, d > .73 ? Blocks.SMOOTH_BASALT : d > .5 ? Blocks.CALCITE : Blocks.AIR);
            if (d <= .5) put(x, y, 2, (x + y) % 3 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.AMETHYST_BLOCK);
        }
        put(0, 74, 2, Blocks.BUDDING_AMETHYST);
        put(0, 74, 3, state("amethyst_cluster[facing=south]"));
        put(1, 76, 3, state("large_amethyst_bud[facing=south]"));
        put(-1, 72, 3, state("small_amethyst_bud[facing=south]"));
        for (int[] p : new int[][]{{-12,75},{-6,74},{7,72},{11,76}})
            put(p[0], p[1], 3, Blocks.MOSSY_COBBLESTONE);
        line(-9, 78, 4, -12, 70, 3, Blocks.OAK_WOOD);
        line(9, 78, 4, 12, 71, 3, Blocks.OAK_WOOD);
        for (int[] p : new int[][]{{-8,76},{7,76},{-12,73},{11,74}}) {
            put(p[0], p[1], 4, state("azalea_leaves[persistent=true]"));
            vine(p[0], p[1] - 1, p[1] - 4, 4);
        }
        // Back-edge planting never occupies the fighter/projectile lane.
        fill(-14, 80, -4, 14, 80, -3, Blocks.GRASS_BLOCK);
        for (int x = -14; x <= 14; x++) {
            if (Math.floorMod(x, 3) != 0) put(x, 81, -3, Blocks.SHORT_GRASS);
            if (Math.floorMod(x, 7) == 0) put(x, 81, -4, Blocks.OXEYE_DAISY);
        }
        put(-11, 81, -3, Blocks.POPPY); put(9, 81, -3, Blocks.AZURE_BLUET);
        // Keep the same beveled, unobscured ledges and no decoration ahead of them.
        fill(-16, 77, 1, -15, 80, 5, Blocks.AIR);
        fill(15, 77, 1, 16, 80, 5, Blocks.AIR);
    }

    private void platform(BattleStage.Platform p) {
        int left = p.left(), right = p.right(), y = p.blockY();
        fill(left, y, -1, right, y, 2, Blocks.SPRUCE_PLANKS);
        fill(left + 1, y, 2, right - 1, y, 2, state("spruce_log[axis=x]"));
        for (int x : new int[]{left, right}) {
            fill(x, y, -1, x, y, 2, state("waxed_oxidized_copper"));
            put(x, y, 3, state("waxed_oxidized_cut_copper_slab[type=top]"));
            // Front-facing lanterns read clearly, without touching the fight lane.
            put(x, y - 1, 2, state("iron_chain[axis=y]"));
            put(x, y - 2, 2, state("lantern[hanging=true]"));
        }
        for (int x = left + 1; x < right; x++) put(x, y - 1, 2, state("spruce_slab[type=top]"));
    }

    private void backdrop() {
        // Three depth layers leave sky behind the central combat silhouettes.
        skyIsland(-46, 74, -20, 9, 6, 14);
        tree(-48, 75, -23, true, 19);
        meadow(-46, 74, -20, 7, 4, true);
        skyIsland(48, 73, -22, 10, 7, 16);
        tree(50, 74, -25, false, 22);
        meadow(48, 73, -22, 8, 5, false);

        // Smaller trees behind the side platforms visually root them in the grove.
        skyIsland(-18, 76, -18, 7, 5, 12);
        tree(-19, 77, -20, false, 6);
        meadow(-18, 76, -18, 5, 3, false);
        skyIsland(20, 77, -21, 7, 5, 13);
        tree(21, 78, -23, false, 7);
        meadow(20, 77, -21, 5, 3, false);

        // The lower island catches the stream. Water stays contained behind invisible
        // barriers, so it never floods chunks or the fighting plane.
        skyIsland(-44, 85, -28, 7, 4, 10);
        skyIsland(-42, 62, -25, 8, 5, 12);
        tree(-46, 86, -29, false, 5);
        meadow(-44, 85, -28, 5, 3, false);
        meadow(-42, 62, -25, 6, 3, false);
        waterfall(-43, 86, 63, -23, 1);

        skyIsland(42, 78, -27, 8, 5, 12);
        portal(42, 79, -24);
        meadow(42, 78, -27, 6, 3, false);
        skyIsland(53, 91, -32, 6, 4, 9);
        skyIsland(52, 70, -29, 6, 4, 10);
        tree(54, 92, -33, false, 5);
        waterfall(52, 92, 71, -27, 1);

        // Distant islands fill the lower horizon, not the space over the deck.
        for (int[] p : new int[][]{
                {-67,86,-68,10,6,14},{-61,66,-62,9,6,14},{-23,62,-62,11,7,15},
                {-10,48,-46,6,4,5},{12,52,-57,9,6,9},{63,60,-67,11,7,16},
                {70,80,-71,8,5,12},{24,64,-73,10,6,15},{-4,69,-82,7,4,11}}) {
            skyIsland(p[0], p[1], p[2], p[3], p[4], p[5]);
            meadow(p[0], p[1], p[2], p[3] - 2, p[4] - 2, false);
            if (p[0] % 2 == 0) tree(p[0], p[1] + 1, p[2] - 1, false, 5);
        }
    }

    private void skyIsland(int cx, int top, int cz, int rx, int rz, int depth) {
        for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) {
            double edge = dx * dx / (double)(rx * rx) + dz * dz / (double)(rz * rz);
            if (edge > 1 || edge > .88 && noise(cx + dx, 0, cz + dz, 4) == 0) continue;
            int column = Math.max(2, (int)(depth * Math.pow(1 - edge, .65)) - noise(dx / 2, 0, dz / 2, 3));
            for (int dy = 0; dy <= column; dy++) {
                Block material = dy == 0 ? Blocks.GRASS_BLOCK : dy <= 2 ? Blocks.DIRT
                        : dy >= column - 1 && noise(dx, 1, dz, 4) == 0 ? Blocks.MOSSY_COBBLESTONE : rock(dx / 2, dy / 3, dz / 2);
                put(cx + dx, top - dy, cz + dz, material);
            }
            if (dz > 0 && edge > .7 && noise(dx, 0, dz, 6) == 0) vine(cx + dx, top - 1, top - Math.min(column, 5), cz + dz + 1);
        }
    }

    private void tree(int x, int y, int z, boolean cherry, int height) {
        Block wood = cherry ? Blocks.CHERRY_WOOD : Blocks.OAK_WOOD;
        BlockState leaves = state((cherry ? "cherry" : "oak") + "_leaves[persistent=true]");
        int thick = height >= 12 ? 1 : 0;
        fill(x - thick, y, z - thick, x + thick, y + height - 3, z + thick, wood);
        int spread = Math.max(3, height / 3);
        for (int side : new int[]{-1, 1}) {
            int crownY = y + height - (side < 0 ? 2 : 0);
            line(x, y + height / 2, z, x + side * spread, crownY - 1, z + side, wood);
            line(x, y + 2, z, x + side * (thick + 2), y, z + 1, wood);
            blob(x + side * spread, crownY, z + side, spread + 1, Math.max(3, spread - 1), spread, leaves);
        }
        line(x, y + height - 4, z, x - 1, y + height + 2, z - 1, wood);
        blob(x - 2, y + height + 4, z - 2, spread, Math.max(3, spread - 1), spread, leaves);
        blob(x + 2, y + height - 2, z + 3, spread, Math.max(2, spread / 2), spread, leaves);
        if (!cherry && height >= 12)
            for (int dx : new int[]{-spread + 1, spread - 1}) vine(x + dx, y + height, y + height - 4, z + spread);
    }

    private void meadow(int x, int y, int z, int rx, int rz, boolean cherry) {
        for (int dx = -rx; dx <= rx; dx++) for (int dz = -rz; dz <= rz; dz++) {
            var p = new BlockPos(x + dx, y, z + dz);
            if (!level.getBlockState(p).is(Blocks.GRASS_BLOCK) || !level.getBlockState(p.above()).isAir()) continue;
            int n = noise(x + dx, y, z + dz, 17);
            if (n < 5) put(x + dx, y + 1, z + dz, Blocks.SHORT_GRASS);
            else if (n == 6) put(x + dx, y + 1, z + dz, cherry ? Blocks.PINK_PETALS : Blocks.OXEYE_DAISY);
            else if (n == 8) put(x + dx, y + 1, z + dz, Blocks.AZURE_BLUET);
            else if (n == 12) put(x + dx, y + 1, z + dz, Blocks.MOSS_CARPET);
        }
    }

    private void waterfall(int x, int top, int bottom, int z, int width) {
        // A bounded animated vanilla-water ribbon, with a stone-rimmed plunge pool.
        fill(x - 2, bottom - 1, z - 2, x + width + 1, bottom - 1, z + 2, Blocks.MOSSY_COBBLESTONE);
        fill(x - 1, bottom - 1, z - 1, x + width, bottom - 1, z + 1, Blocks.WATER);
        fill(x - 2, bottom - 2, z - 2, x + width + 1, bottom - 2, z + 2, Blocks.STONE);
        for (int y = bottom; y <= top; y++) {
            put(x - 1, y, z, Blocks.BARRIER); put(x + width, y, z, Blocks.BARRIER);
            for (int dx = 0; dx < width; dx++) {
                put(x + dx, y, z - 1, Blocks.BARRIER); put(x + dx, y, z + 1, Blocks.BARRIER);
                put(x + dx, y, z, state(y == top ? "water[level=0]" : "water[level=8]"));
            }
        }
        fill(x, top + 1, z, x + width - 1, top + 1, z, Blocks.BARRIER);
    }

    private void portal(int x, int y, int z) {
        fill(x - 5, y - 1, z - 2, x + 5, y - 1, z + 2, Blocks.MOSSY_STONE_BRICKS);
        fill(x - 3, y, z, x + 3, y + 8, z, Blocks.OBSIDIAN);
        fill(x - 2, y + 1, z, x + 2, y + 7, z, state("nether_portal[axis=x]"));
        // Keep the functional frame intact; glowing accents sit in front of it.
        for (int[] p : new int[][]{{-3,1},{3,6},{1,8},{-2,0}}) put(x + p[0], y + p[1], z + 1, Blocks.CRYING_OBSIDIAN);
        fill(x - 5, y, z - 1, x - 5, y + 3, z, Blocks.CRACKED_STONE_BRICKS);
        fill(x + 5, y, z, x + 5, y + 1, z, Blocks.MOSSY_STONE_BRICKS);
        blob(x - 4, y + 1, z + 1, 2, 2, 2, state("azalea_leaves[persistent=true]"));
        put(x + 4, y, z + 1, Blocks.POPPY);
    }

    private static int noise(int x, int y, int z, int bound) { return Math.floorMod(x * 7349 + y * 1999 + z * 9151, bound); }
    private static Block rock(int x, int y, int z) {
        int n = noise(x, y, z, 19);
        return n < 3 ? Blocks.ANDESITE : n < 5 ? Blocks.TUFF : n == 8 ? Blocks.COBBLESTONE : Blocks.STONE;
    }
    private void vine(int x, int top, int bottom, int z) {
        for (int y = bottom; y <= top; y++) if (level.getBlockState(new BlockPos(x, y, z)).isAir())
            put(x, y, z, state("vine[north=true]"));
    }
    private void blob(int x, int y, int z, int rx, int ry, int rz, BlockState state) {
        for (int dx = -rx; dx <= rx; dx++) for (int dy = -ry; dy <= ry; dy++) for (int dz = -rz; dz <= rz; dz++)
            if ((double)dx * dx / (rx * rx) + (double)dy * dy / (ry * ry) + (double)dz * dz / (rz * rz) <= 1
                    && level.getBlockState(new BlockPos(x + dx, y + dy, z + dz)).isAir()) put(x + dx, y + dy, z + dz, state);
    }
    private void line(int x, int y, int z, int toX, int toY, int toZ, Block block) {
        int steps = Math.max(Math.max(Math.abs(toX - x), Math.abs(toY - y)), Math.abs(toZ - z));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double)i / steps;
            put((int)Math.round(x + (toX - x) * t), (int)Math.round(y + (toY - y) * t), (int)Math.round(z + (toZ - z) * t), block);
        }
    }
    private BlockState state(String name) {
        return states.computeIfAbsent(name, key -> {
            try { return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), "minecraft:" + key, false).blockState(); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalArgumentException("Unknown arena block: " + key, e); }
        });
    }
    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block) { fill(x1, y1, z1, x2, y2, z2, block.defaultBlockState()); }
    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        var pos = new BlockPos.MutableBlockPos();
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++)
            if (level.getBlockState(pos.set(x, y, z)) != state) level.setBlock(pos, state, FLAGS);
    }
    private void put(int x, int y, int z, Block block) { put(x, y, z, block.defaultBlockState()); }
    private void put(int x, int y, int z, BlockState state) { level.setBlock(new BlockPos(x, y, z), state, FLAGS); }
}
