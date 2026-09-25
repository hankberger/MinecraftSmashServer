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
    private static final Block REVISION = Blocks.LAPIS_BLOCK;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private final ServerLevel level;
    private final java.util.Map<String, BlockState> states = new java.util.HashMap<>();
    private ArenaBuilder(ServerLevel level) { this.level = level; }

    public static void ensureBuilt(ServerLevel level) {
        var stage = MvpWorlds.stage(level);
        if (stage != BattleStage.SKYBOUND_GROVE) { AlternateArenaBuilder.ensureBuilt(level, stage); return; }
        // Different marker material also detects a rollback through the revision-3/4 builders.
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
        lushBiome();
        put(1, 40, 0, REVISION); // Publish only after the entire build succeeds.
        VanillaSmash.LOG.info("Built Skybound Grove arena revision 5 in {} ms", (System.nanoTime() - started) / 1_000_000);
    }

    private void lushBiome() {
        // The old void biome gives foliage a dull olive tint. Apply the vanilla
        // jungle palette to existing chunks as well as newly generated worlds.
        var biome = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(net.minecraft.world.level.biome.Biomes.JUNGLE);
        var chunks = new java.util.ArrayList<net.minecraft.world.level.chunk.ChunkAccess>();
        for (int x = -6; x <= 5; x++) for (int z = -6; z <= 0; z++) {
            var chunk = level.getChunk(x, z);
            chunk.fillBiomesFromNoise((bx, by, bz, sampler) -> biome, level.getChunkSource().randomState().sampler());
            chunk.markUnsaved();
            chunks.add(chunk);
        }
        level.getChunkSource().chunkMap.resendBiomesForChunks(chunks);
    }

    private void island() {
        // Retain the solid collision core at the fighting plane, including its underside.
        for (int y = 71; y <= 79; y++) {
            int half = Math.min(16, 4 + (y - 71) * 2);
            for (int x = -half; x <= half; x++) for (int z = -4; z <= 0; z++) {
                if (y < 76 && Math.abs(x) == half && Math.floorMod(x + z, 3) == 0) continue;
                put(x, y, z, y >= 78 ? Blocks.DIRT : rock(x, y, z));
            }
        }
        // A thin turf shelf with a broken front edge, rather than a deep green tabletop.
        fill(-16, 80, -2, 16, 80, 0, Blocks.GRASS_BLOCK);
        for (int x = -14; x <= 14; x++) {
            if (noise(x, 4, 0, 7) > 1) put(x, 80, 1, Blocks.GRASS_BLOCK);
            int soilBottom = noise(Math.floorDiv(x + 2, 3), 5, 0, 4) == 0 ? 78 : 79;
            for (int y = soilBottom; y <= 79; y++) {
                // Stone protrusions interrupt the soil cap in a few deliberate groups.
                boolean outcrop = (x >= -10 && x <= -9) || (x >= 8 && x <= 9);
                put(x, y, 1, outcrop ? Blocks.ANDESITE : y == soilBottom && x % 3 == 0
                        ? Blocks.ROOTED_DIRT : Blocks.DIRT);
            }
        }
        // Recess the lower face toward the fighting plane. The collision core stays
        // intact, while its front receives clustered stone strata instead of stripes.
        for (int y = 71; y <= 78; y++) {
            int half = Math.min(14, 4 + (y - 71) * 2);
            for (int x = -half; x <= half; x++) {
                int band = noise(Math.floorDiv(x, 3), Math.floorDiv(y, 3), 9, 9);
                put(x, y, 0, band < 2 ? Blocks.ANDESITE : band == 3 ? Blocks.TUFF : Blocks.STONE);
            }
        }
        // Tapered stone teeth leave sky between the root bundles below the island.
        for (int[] tooth : new int[][]{{-8,69},{-2,66},{2,67},{7,69}}) {
            int x = tooth[0], bottom = tooth[1];
            for (int y = bottom; y <= 73; y++) {
                int width = y > bottom + 2 ? 1 : 0;
                fill(x - width, y, 0, x + width, y, 1, y < bottom + 2 ? Blocks.ANDESITE : Blocks.STONE);
            }
            put(x, bottom, 2, state("stone_slab[type=top]"));
        }
        // Each root has a direction, a fork, and a fine end. All bark faces outward;
        // no sawn log end caps or full-height wooden wall columns.
        root(new int[][]{{-13,79},{-12,76},{-10,74},{-9,71}}, -1, 3);
        root(new int[][]{{-9,78},{-8,76},{-7,74},{-8,70}}, 1, 2);
        root(new int[][]{{-5,79},{-6,76},{-4,73},{-3,69}}, -1, 3);
        root(new int[][]{{5,79},{6,77},{5,74},{7,71}}, 1, 3);
        root(new int[][]{{10,78},{9,76},{11,74},{10,70}}, -1, 2);
        root(new int[][]{{13,79},{12,77},{13,74},{12,72}}, 1, 2);

        // A narrow crystal pocket with a quiet calcite rim, set into the stone face.
        for (int x = -2; x <= 2; x++) for (int y = 71; y <= 77; y++) {
            double d = x * x / 6.0 + (y - 74) * (y - 74) / 12.0;
            if (d > 1) continue;
            put(x, y, 1, d > .65 ? Blocks.SMOOTH_BASALT : Blocks.AMETHYST_BLOCK);
            if (d > .65 && (x == -2 || y == 77)) put(x, y, 1, Blocks.CALCITE);
            put(x, y, 2, Blocks.AIR);
        }
        put(0, 74, 1, Blocks.CRYING_OBSIDIAN);
        put(0, 74, 2, Blocks.BUDDING_AMETHYST);
        put(0, 74, 3, state("amethyst_cluster[facing=south]"));
        put(1, 76, 2, state("large_amethyst_bud[facing=south]"));
        put(-1, 72, 2, state("small_amethyst_bud[facing=south]"));
        for (int[] p : new int[][]{{-12,77},{-5,76},{7,77},{11,75}}) {
            put(p[0], p[1], 2, state("azalea_leaves[persistent=true]"));
            vine(p[0], p[1] - 1, p[1] - 3, 2);
        }
        for (int[] p : new int[][]{{-12,77},{-6,78},{3,77},{11,78}}) {
            put(p[0], 79, 1, Blocks.MOSS_BLOCK);
            vine(p[0], 80, p[1], 2);
            vine(p[0] + 1, 79, p[1] + 1, 2);
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

    private void root(int[][] points, int forkSide, int tail) {
        for (int i = 1; i < points.length; i++)
            line(points[i-1][0], points[i-1][1], 1, points[i][0], points[i][1], 1, Blocks.OAK_WOOD);
        var fork = points[1]; var tip = points[points.length - 1];
        line(fork[0], fork[1], 1, fork[0] + forkSide * 2, fork[1] - 3, 2, Blocks.MANGROVE_ROOTS);
        for (int y = tip[1] - tail; y < tip[1]; y++) put(tip[0], y, 1, Blocks.OAK_FENCE);
        put(tip[0], tip[1] - tail - 1, 1, Blocks.HANGING_ROOTS);
        put(points[0][0], points[0][1], 1, Blocks.ROOTED_DIRT);
        vine(fork[0], fork[1], tip[1] - 1, 2);
    }

    private void platform(BattleStage.Platform p) {
        int left = p.left(), right = p.right(), y = p.blockY();
        fill(left, y, -1, right, y, 2, Blocks.SPRUCE_PLANKS);
        fill(left + 1, y, 2, right - 1, y, 2, state("stripped_spruce_log[axis=x]"));
        for (int x : new int[]{left, right}) {
            fill(x, y, -1, x, y, 2, state("waxed_oxidized_copper"));
            // Flush copper collars with a small warm rivet, not projecting slab wings.
            put(x, y, 2, state("waxed_weathered_cut_copper"));
            put(x, y, 3, state("oak_button[face=wall,facing=south]"));
            put(x, y - 1, 2, state("iron_chain[axis=y]"));
            put(x, y - 2, 2, state("lantern[hanging=true]"));
        }
        // Shallow corbels at the ends support the beam without a second full fascia.
        put(left + 1, y - 1, 2, state("spruce_stairs[facing=east,half=top]"));
        put(right - 1, y - 1, 2, state("spruce_stairs[facing=west,half=top]"));
    }

    private void backdrop() {
        // Three depth layers leave sky behind the central combat silhouettes.
        skyIsland(-46, 74, -20, 9, 6, 14);
        // Keep the trunk within the ordinary client chunk range as it bends.
        tree(-46, 75, -23, true, 19);
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
        int bend = cherry ? -1 : 1;
        int spread = Math.max(3, height / 3);
        // Buttress roots and a gently bending trunk split into exposed limbs well
        // below the crown. The large framing trees are not straight cylinders.
        if (height >= 12) {
            fill(x - 1, y, z - 1, x + 1, y + 3, z + 1, wood);
            for (int side : new int[]{-1, 1}) {
                line(x, y + 4, z, x + side * 4, y, z + 2, wood);
                put(x + side * 4, y - 1, z + 2, Blocks.ROOTED_DIRT);
            }
        }
        line(x, y, z, x + bend, y + height / 3, z, wood);
        line(x + bend, y + height / 3, z, x, y + height * 2 / 3, z - 1, wood);
        line(x, y + height * 2 / 3, z - 1, x - bend * 2, y + height, z - 1, wood);
        for (int side : new int[]{-1, 1}) {
            int branchY = y + height - (side < 0 ? 3 : 1);
            line(x + bend, y + height / 2, z, x + side * (spread - 1), branchY - 2, z + side, wood);
            line(x + side * (spread - 1), branchY - 2, z + side,
                    x + side * (spread + 2), branchY, z + side * 2, wood);
            foliage(x + side * (spread + 2), branchY + 1, z + side * 2,
                    spread, Math.max(2, spread / 2), spread, leaves, cherry);
            foliage(x + side * spread, branchY + 3, z + side,
                    Math.max(2, spread - 1), 3, Math.max(2, spread - 1), leaves, cherry);
        }
        foliage(x - bend * 2, y + height + 2, z - 1,
                spread, Math.max(2, spread / 2), spread, leaves, cherry);
        foliage(x + bend * 3, y + height - 4, z + 3,
                Math.max(2, spread - 1), 2, Math.max(2, spread - 1), leaves, cherry);
    }

    private void foliage(int x, int y, int z, int rx, int ry, int rz, BlockState leaves, boolean cherry) {
        for (int dx = -rx; dx <= rx; dx++) for (int dy = -ry; dy <= ry; dy++) for (int dz = -rz; dz <= rz; dz++) {
            double edge = dx * dx / (double)(rx * rx) + dy * dy / (double)(ry * ry) + dz * dz / (double)(rz * rz);
            if (edge > 1.08 || edge > .7 && noise(x + dx, y + dy, z + dz, 7) == 0) continue;
            var p = new BlockPos(x + dx, y + dy, z + dz);
            if (!level.getBlockState(p).isAir()) continue;
            put(p.getX(), p.getY(), p.getZ(), !cherry && dy < 0 && noise(dx, dy, dz, 13) == 0
                    ? state("azalea_leaves[persistent=true]") : leaves);
        }
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

    private static int noise(int x, int y, int z, int bound) {
        int hash = x * 7349 ^ y * 1999 ^ z * 9151;
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3b;
        return Math.floorMod(hash ^ (hash >>> 16), bound);
    }
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
        int lastX = x, lastZ = z;
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double)i / steps;
            int nextX = (int)Math.round(x + (toX - x) * t);
            int nextY = (int)Math.round(y + (toY - y) * t);
            int nextZ = (int)Math.round(z + (toZ - z) * t);
            // Connect the steps through faces, so thin bent trunks and roots never
            // look like floating cubes when viewed from the side.
            put(lastX, nextY, lastZ, block);
            put(nextX, nextY, lastZ, block);
            put(nextX, nextY, nextZ, block);
            lastX = nextX; lastZ = nextZ;
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
