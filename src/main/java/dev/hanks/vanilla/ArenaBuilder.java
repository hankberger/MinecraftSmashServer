package dev.hanks.vanilla;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Skybound Grove: a readable three-platform arena cut from a Minecraft sky island. */
public final class ArenaBuilder {
    private static final BlockPos MARKER = new BlockPos(1, 40, 0);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private final ServerLevel level;
    private final java.util.Map<String, BlockState> states = new java.util.HashMap<>();
    private ArenaBuilder(ServerLevel level) { this.level = level; }

    public static void ensureBuilt(ServerLevel level) {
        if (!level.dimension().equals(MvpWorlds.ARENA)) throw new IllegalArgumentException("Not the arena dimension");
        // Revision 2's builder does not clear the new, lower marker during a rollback.
        // Its old marker must also be absent before trusting the revision-3 geometry.
        if (level.getBlockState(MARKER).is(Blocks.REINFORCED_DEEPSLATE)
                && level.getBlockState(new BlockPos(1, 60, 0)).isAir()) return;
        new ArenaBuilder(level).build();
    }

    private void build() {
        long started = System.nanoTime();
        // Includes the previous halo/pillars and all scenery in this revision.
        fill(-45, 59, -48, 45, 114, 5, Blocks.AIR);
        island();
        platform(-12, -5, 84); platform(5, 12, 84); platform(-3, 3, 88);
        backdrop();
        put(MARKER.getX(), MARKER.getY(), MARKER.getZ(), Blocks.REINFORCED_DEEPSLATE);
        VanillaSmash.LOG.info("Built Skybound Grove arena revision 3 in {} ms", (System.nanoTime() - started) / 1_000_000);
    }

    private void island() {
        // An irregular geological cross-section, with a flat combat surface.
        for (int y = 71; y <= 79; y++) {
            int half = Math.min(16, 4 + (y - 71) * 2);
            for (int x = -half; x <= half; x++) for (int z = -4; z <= 3; z++) {
                if (y < 76 && Math.abs(x) == half && Math.floorMod(x + z, 3) == 0) continue;
                int texture = Math.floorMod(x * 31 + y * 17 + z * 13, 19);
                Block rock = y >= 79 ? Blocks.DIRT : y <= 73 ? Blocks.DEEPSLATE
                        : texture < 4 ? Blocks.TUFF : texture == 8 ? Blocks.ANDESITE : Blocks.STONE;
                put(x, y, z, rock);
            }
        }
        fill(-16, 80, -2, 16, 80, 3, Blocks.GRASS_BLOCK);
        for (int x = -15; x <= 15; x++) {
            if (Math.floorMod(x, 7) < 3) fill(x, 80, -1, x, 80, 1, Blocks.OAK_PLANKS);
            if (Math.floorMod(x, 6) == 0) put(x, 79, 3, Blocks.MOSSY_COBBLESTONE);
        }
        for (int x : new int[]{-16, 16}) {
            fill(x, 78, -2, x, 79, 3, Blocks.OAK_LOG);
            fill(x, 80, -2, x, 80, 3, Blocks.STRIPPED_OAK_LOG);
            put(x, 79, 3, state("waxed_oxidized_copper"));
        }
        // Bevel the front corners so perspective does not hide a fighter hanging beside the combat plane.
        // The deck at z=0 and its actual landing/ledge coordinates stay intact.
        fill(-16, 77, 1, -15, 80, 3, Blocks.AIR);
        fill(15, 77, 1, 16, 80, 3, Blocks.AIR);
        // The abandoned mine is exposed below the front edge of the stage.
        fill(-10, 75, 1, -2, 78, 4, Blocks.AIR);
        fill(-10, 74, 1, -2, 74, 3, Blocks.SPRUCE_PLANKS);
        for (int x : new int[]{-10, -6, -2}) {
            fill(x, 75, 3, x, 78, 3, Blocks.SPRUCE_LOG);
            for (int dx = 0; dx < 4 && x + dx <= -2; dx++) put(x + dx, 78, 3, state("spruce_log[axis=x]"));
        }
        for (int x = -9; x <= -3; x++) put(x, 75, 2, state("rail[shape=east_west]"));
        put(-8, 77, 3, state("lantern[hanging=true]"));
        put(-4, 77, 3, state("lantern[hanging=true]"));
        put(-9, 75, 3, state("barrel[facing=south]"));
        put(-3, 75, 3, Blocks.RAW_IRON_BLOCK);
        // Calcite-lined amethyst geode in the other half of the island.
        for (int x = 1; x <= 11; x++) for (int y = 73; y <= 79; y++) {
            double d = Math.pow((x - 6) / 4.7, 2) + Math.pow((y - 76) / 3.2, 2);
            if (d > 1) continue;
            put(x, y, 3, d > .67 ? Blocks.SMOOTH_BASALT : d > .43 ? Blocks.CALCITE : Blocks.AMETHYST_BLOCK);
            if (d < .2) { put(x, y, 3, Blocks.AIR); put(x, y, 2, Blocks.BUDDING_AMETHYST); }
        }
        put(6, 76, 3, state("amethyst_cluster[facing=south]"));
        put(5, 75, 3, state("large_amethyst_bud[facing=south]"));
        for (int[] p : new int[][]{{-13,77},{13,76},{0,74},{10,73},{-2,71}})
            put(p[0], p[1], 3, p[1] % 2 == 0 ? Blocks.COPPER_ORE : Blocks.DEEPSLATE_IRON_ORE);
        for (int[] root : new int[][]{{-13,77,-4,-10,72,-4},{12,78,-4,9,70,-4},{-2,77,-4,1,71,-4}})
            line(root[0], root[1], root[2], root[3], root[4], root[5], Blocks.OAK_LOG);
        for (int x : new int[]{-14,-8,0,8,14}) {
            put(x, 80, -3, Blocks.MOSS_BLOCK);
            put(x, 81, -3, state(x % 4 == 0 ? "short_grass" : "azure_bluet"));
        }
    }

    private void platform(int left, int right, int y) {
        fill(left, y, -1, right, y, 2, Blocks.SPRUCE_PLANKS);
        for (int x = left; x <= right; x++) {
            put(x, y, 2, state("stripped_spruce_log[axis=x]"));
            // The space beneath the fighting plane stays clear for up attacks.
            put(x, y - 1, -2, state("spruce_slab[type=top]"));
        }
        for (int x : new int[]{left, right}) {
            put(x, y, 2, state("waxed_oxidized_copper"));
            put(x, y - 1, -2, state("lantern[hanging=true]"));
            for (int z = -1; z <= 1; z++) put(x, y, z, Blocks.STRIPPED_OAK_LOG);
        }
    }

    private void backdrop() {
        // Scenery sits behind every actor and projectile, leaving the center open.
        skyRock(-27, 80, -20, 11); tree(-27, 81, -20);
        for (int[] p : new int[][]{{-34,81,-18},{-21,81,-21},{-29,81,-15}}) {
            put(p[0], p[1], p[2], Blocks.MOSS_BLOCK);
            put(p[0], p[1]+1, p[2], state("red_mushroom"));
        }
        skyRock(27, 82, -23, 10);
        // An overgrown, broken portal ruin with suspended masonry above it.
        fill(22, 83, -23, 31, 83, -21, Blocks.MOSSY_STONE_BRICKS);
        fill(23, 84, -22, 24, 95, -21, Blocks.STONE_BRICKS);
        fill(30, 84, -22, 31, 92, -21, Blocks.CRACKED_STONE_BRICKS);
        fill(24, 95, -22, 28, 96, -21, Blocks.STONE_BRICKS);
        fill(30, 97, -22, 31, 98, -21, Blocks.MOSSY_STONE_BRICKS);
        fill(24, 84, -20, 24, 93, -20, Blocks.CRYING_OBSIDIAN);
        fill(29, 84, -20, 29, 90, -20, Blocks.OBSIDIAN);
        fill(25, 84, -20, 28, 84, -20, Blocks.OBSIDIAN);
        fill(25, 92, -20, 27, 93, -20, Blocks.CRYING_OBSIDIAN);
        blob(23, 95, -21, 3, 2, 2, state("oak_leaves[persistent=true]"));
        blob(31, 85, -20, 2, 3, 2, state("azalea_leaves[persistent=true]"));
        put(27, 85, -20, Blocks.GOLD_BLOCK);
        // A distant homestead establishes depth beyond the fighting platforms.
        skyRock(4, 106, -43, 7);
        fill(1, 107, -43, 6, 109, -40, Blocks.OAK_PLANKS);
        for (int x = 0; x <= 7; x++) {
            int roofY = 110 + Math.min(x, 7-x) / 2;
            fill(x, roofY, -44, x, roofY, -39, Blocks.SPRUCE_PLANKS);
        }
        fill(5, 110, -42, 5, 114, -42, Blocks.COBBLESTONE);
        put(2, 108, -39, Blocks.SEA_LANTERN);
        fill(3, 107, -39, 3, 108, -39, Blocks.SPRUCE_LOG);
        skyRock(-13, 96, -38, 3); skyRock(16, 75, -32, 4);
    }

    private void skyRock(int cx, int top, int cz, int radius) {
        int depth = Math.max(2, (int)Math.ceil(radius * .65));
        for (int y = top - depth; y <= top; y++) {
            int r = Math.max(1, (int)Math.round(radius * Math.pow(1 - (double)(top-y)/(depth+1), .65)));
            for (int x = -r; x <= r; x++) for (int z = -r/2; z <= r/2; z++)
                if (x*x + z*z*3 <= r*r + 2)
                    put(cx+x, y, cz+z, y == top ? Blocks.GRASS_BLOCK : y > top-3 ? Blocks.DIRT : (x+y+z)%5 == 0 ? Blocks.TUFF : Blocks.STONE);
        }
    }

    private void tree(int x, int y, int z) {
        fill(x-1, y, z-1, x+1, y+12, z+1, Blocks.OAK_LOG);
        line(x, y+8, z, x-6, y+15, z, Blocks.OAK_LOG);
        line(x, y+9, z, x+7, y+14, z+1, Blocks.OAK_LOG);
        line(x, y+12, z, x-2, y+19, z-1, Blocks.OAK_LOG);
        for (int[] c : new int[][]{{-6,15,0,6,4},{6,15,1,6,4},{-1,20,-1,7,4},{0,14,2,7,4}})
            blob(x+c[0], y+c[1], z+c[2], c[3], c[4], 4, state("oak_leaves[persistent=true]"));
        for (int dx : new int[]{-4, 5}) {
            fill(x+dx, y+9, z+2, x+dx, y+12, z+2, state("iron_chain[axis=y]"));
            put(x+dx, y+8, z+2, state("lantern[hanging=true]"));
        }
        for (int dx : new int[]{-2, 2}) line(x, y+2, z, x+dx*2, y, z+2, Blocks.OAK_LOG);
    }

    private void blob(int x, int y, int z, int rx, int ry, int rz, BlockState state) {
        for (int dx=-rx; dx<=rx; dx++) for (int dy=-ry; dy<=ry; dy++) for (int dz=-rz; dz<=rz; dz++)
            if ((double)dx*dx/(rx*rx)+(double)dy*dy/(ry*ry)+(double)dz*dz/(rz*rz) <= 1
                    && level.getBlockState(new BlockPos(x+dx,y+dy,z+dz)).isAir()) put(x+dx,y+dy,z+dz,state);
    }
    private void line(int x, int y, int z, int toX, int toY, int toZ, Block block) {
        int steps = Math.max(Math.max(Math.abs(toX-x), Math.abs(toY-y)), Math.abs(toZ-z));
        for (int i=0; i<=steps; i++) {
            double t = steps == 0 ? 0 : (double)i/steps;
            put((int)Math.round(x+(toX-x)*t),(int)Math.round(y+(toY-y)*t),(int)Math.round(z+(toZ-z)*t),block);
        }
    }
    private BlockState state(String name) {
        return states.computeIfAbsent(name, key -> {
            try { return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), "minecraft:"+key, false).blockState(); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IllegalArgumentException("Unknown arena block: "+key, e); }
        });
    }
    private void fill(int x1,int y1,int z1,int x2,int y2,int z2,Block block) { fill(x1,y1,z1,x2,y2,z2,block.defaultBlockState()); }
    private void fill(int x1,int y1,int z1,int x2,int y2,int z2,BlockState state) {
        var pos = new BlockPos.MutableBlockPos();
        for (int x=x1;x<=x2;x++) for (int y=y1;y<=y2;y++) for (int z=z1;z<=z2;z++) level.setBlock(pos.set(x,y,z),state,FLAGS);
    }
    private void put(int x,int y,int z,Block block) { put(x,y,z,block.defaultBlockState()); }
    private void put(int x,int y,int z,BlockState state) { level.setBlock(new BlockPos(x,y,z),state,FLAGS); }
}
