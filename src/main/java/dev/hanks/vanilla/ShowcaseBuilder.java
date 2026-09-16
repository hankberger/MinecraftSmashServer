package dev.hanks.vanilla;

import java.util.*;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Private presentation sets, separated beyond vanilla's maximum entity tracking distance. */
public final class ShowcaseBuilder {
    public static final int SPACING = 1024;
    // Session scenery crosses chunk boundaries. Keep it loaded through quick
    // menu/winner handoffs so an outstanding unload cannot swallow new models.
    private static final net.minecraft.server.level.TicketType PRESENTATION = new net.minecraft.server.level.TicketType(
            net.minecraft.server.level.TicketType.NO_TIMEOUT,
            net.minecraft.server.level.TicketType.FLAG_LOADING | net.minecraft.server.level.TicketType.FLAG_SIMULATION);
    public static void retain(ServerLevel level,int room) {
        var center=new net.minecraft.world.level.ChunkPos(room*SPACING/16,0);
        level.getChunkSource().addTicketWithRadius(PRESENTATION,center,2);
        for(int x=center.x()-1;x<=center.x()+1;x++) for(int z=-1;z<=1;z++) level.getChunk(x,z);
    }
    public static void release(ServerLevel level,int room) {
        level.getChunkSource().removeTicketWithRadius(PRESENTATION,new net.minecraft.world.level.ChunkPos(room*SPACING/16,0),2);
    }
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
        if (!level.getBlockState(new BlockPos(builder.origin, 93, 0)).is(room < 0 ? Blocks.GOLD_BLOCK : Blocks.LODESTONE)) builder.build();
    }
    /** Version marker replaces existing positive-room gardens on the first packed visit. */
    public static void ensureFighterBuilt(ServerLevel level, int room) {
        if (!level.dimension().equals(MvpWorlds.SHOWCASE) || room < 0) throw new IllegalArgumentException("Not a fighter room");
        var builder = new ShowcaseBuilder(level, room);
        if (!level.getBlockState(new BlockPos(builder.origin, 93, 0)).is(Blocks.DIAMOND_BLOCK)) builder.buildFighterSet();
    }
    private void clearFighterSet() {
        // Includes both the old roots/canopies and the new room's entire footprint.
        // These are generated private sets in the showcase dimension only.
        fill(-38,91,-10,34,120,12,Blocks.AIR);
    }
    private void buildFighterSet() {
        clearFighterSet();
        // A continuous studio floor and backing wall, with no island edge in frame.
        fill(-38,98,-10,34,99,12,state("gray_concrete").getBlock());
        fill(-38,100,-10,34,118,-9,state("gray_concrete").getBlock());
        fill(-38,100,-8,34,101,-8,Blocks.POLISHED_ANDESITE);
        for(int x=-38;x<=34;x++) for(int z=-8;z<=12;z++)
            if(Math.floorMod(x,7)==0 || Math.floorMod(z,7)==0) put(x,99,z,Blocks.POLISHED_ANDESITE);
        // A shallow octagonal frame with a quiet, light face behind every silhouette.
        // Offset the distant frame along the camera-to-fighter ray, so it reads
        // centered behind the model rather than drifting left in perspective.
        for(int x=3;x<=15;x++) for(int y=100;y<=114;y++) {
            int dx=Math.abs(x-9), dy=Math.abs(y-107);
            if(dx+dy>10) continue;
            boolean outer=dx==6 || dy==7 || dx+dy==10;
            boolean inner=dx==5 || dy==6 || dx+dy==9;
            put(x,y,-8,outer ? state("waxed_oxidized_copper").getBlock() : inner ? Blocks.SMOOTH_QUARTZ : state("light_gray_concrete").getBlock());
        }
        for(int[] light:new int[][]{{3,104},{3,110},{15,104},{15,110},{9,114}})
            put(light[0],light[1],-8,Blocks.SEA_LANTERN);
        // Chamfered dais with a pale top and a thin copper band under its rim.
        for(int x=1;x<=7;x++) for(int z=-3;z<=3;z++) {
            int dx=Math.abs(x-4), dz=Math.abs(z);
            if(dx+dz>5) continue;
            put(x,100,z,state("waxed_oxidized_copper").getBlock());
            put(x,101,z,dx==3 || dz==3 || dx+dz==5 ? Blocks.SMOOTH_QUARTZ : Blocks.SMOOTH_STONE);
        }
        // Low approach steps and floor inlays frame the silhouette without hiding feet.
        fill(2,100,4,6,100,4,Blocks.SMOOTH_QUARTZ);
        for(int x : new int[]{-3,11}) {
            fill(x,99,-7,x,99,9,state("waxed_oxidized_copper").getBlock());
            for(int z : new int[]{-4,1,6}) put(x,99,z,Blocks.SEA_LANTERN);
        }
        put(0,93,0,Blocks.DIAMOND_BLOCK);
        VanillaSmash.LOG.info("Built fighter presentation studio room={}",origin/SPACING);
    }
    private void build() {
        if (origin >= 0) clearFighterSet();
        if (origin < 0) fill(-10,100,-2,-1,110,3,Blocks.AIR);
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
        fill(-9, 100, -2, -1, origin < 0 ? 109 : 107, -2, Blocks.STRIPPED_DARK_OAK_LOG);
        for (int y : origin < 0 ? new int[]{100,109} : new int[]{100, 104, 107}) fill(-9, y, -1, -1, y, -1, Blocks.DARK_OAK_SLAB);
        for (int i = 0; origin >= 0 && i < 5; i++) {
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
        put(0, 93, 0, origin < 0 ? Blocks.GOLD_BLOCK : Blocks.LODESTONE);
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
