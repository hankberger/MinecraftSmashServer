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
        if (!level.getBlockState(new BlockPos(builder.origin, 93, 0)).is(Blocks.EMERALD_BLOCK)) builder.buildFighterSet();
    }
    private void clearFighterSet() {
        // Includes both the old roots/canopies and the new room's entire footprint.
        // These are generated private sets in the showcase dimension only.
        fill(-38,91,-30,34,128,18,Blocks.AIR);
    }
    private void buildFighterSet() {
        clearFighterSet();
        // Heartwood Pavilion: a continuous garden court, with a warm raised dais
        // and an open timber proscenium. Every detail is outside the fighter's silhouette.
        fill(-32,96,-27,30,98,16,Blocks.ROOTED_DIRT);
        fill(-32,99,-27,30,99,16,Blocks.MOSS_BLOCK);
        for(int x=-17;x<=23;x++) for(int z=-12;z<=16;z++) {
            boolean edge=x==-17 || x==23 || z==-12;
            put(x,99,z,edge?Blocks.MOSSY_STONE_BRICKS:Blocks.OAK_PLANKS);
            if(!edge && (Math.floorMod(x+3,8)==0 || Math.floorMod(z+2,8)==0))
                put(x,99,z,state("stripped_spruce_log[axis=x]"));
        }
        // Lower stone paths weave between the deck, planters and garden slopes.
        for(int z=-26;z<=16;z++) for(int dx=-1;dx<=1;dx++)
            put(21+dx,99,z,Math.floorMod(z+dx,5)==0?Blocks.MOSSY_STONE_BRICKS:Blocks.STONE_BRICKS);
        // The small octagonal dais has a bamboo parquet center and cream stone rim.
        for(int x=0;x<=8;x++) for(int z=-4;z<=4;z++) {
            int dx=Math.abs(x-4),dz=Math.abs(z);if(dx+dz>6)continue;
            put(x,100,z,Blocks.DARK_OAK_PLANKS);
            put(x,101,z,dx==4 || dz==4 || dx+dz==6?Blocks.SMOOTH_SANDSTONE:Blocks.BAMBOO_MOSAIC);
            if((dx==4 || dz==4 || dx+dz==6) && Math.floorMod(x+z,3)==0)
                put(x,100,z,state("waxed_weathered_cut_copper"));
        }
        for(int x=1;x<=7;x++)put(x,100,5,state("smooth_sandstone_stairs[facing=north]"));
        // Four substantial posts: stone shoes, visible grain, copper collars and corbels.
        for(int x:new int[]{-4,12})for(int z:new int[]{-8,3}) {
            fill(x,100,z,x,101,z,Blocks.MOSSY_STONE_BRICKS);
            fill(x,102,z,x,111,z,Blocks.STRIPPED_SPRUCE_LOG);
            put(x,102,z,state("waxed_weathered_cut_copper"));
            put(x,110,z,state("waxed_weathered_cut_copper"));
            int inward=x<4?1:-1;
            for(int i=1;i<=3;i++)put(x+inward*i,108+i,z,Blocks.DARK_OAK_PLANKS);
        }
        for(int x=-5;x<=13;x++) {
            put(x,112,-8,state("spruce_log[axis=x]"));
            put(x,112,3,state("spruce_log[axis=x]"));
        }
        for(int x:new int[]{-4,12})for(int z=-9;z<=4;z++)put(x,112,z,state("spruce_log[axis=z]"));
        // Layered copper gable, a dark eave, and a capped ridge rather than a flat lid.
        for(int x=-7;x<=15;x++)for(int z=-11;z<=5;z++) {
            int dx=Math.abs(x-4),y=116-dx/3;
            put(x,y,z,state((Math.floorMod(x+z,7)==0?"waxed_weathered_cut_copper":"waxed_oxidized_cut_copper")));
            if(z==-11 || z==5)put(x,y-1,z,Blocks.DARK_OAK_SLAB);
            if(dx==11)put(x,y,z,state("dark_oak_stairs[facing="+(x<4?"east":"west")+"]"));
        }
        for(int z=-12;z<=6;z++)put(4,117,z,state("waxed_weathered_cut_copper_slab"));
        // Lantern clusters hang toward the edges; the central model remains unobstructed.
        for(int x:new int[]{-1,9})for(int z:new int[]{-6,2}) {
            fill(x,109,z,x,115-Math.abs(x-4)/3,z,state("iron_chain[axis=y]").getBlock());
            put(x,108,z,state("lantern[hanging=true]"));
        }
        // A low garden balustrade and a pair of built-in bench/weapon-workshop alcoves.
        for(int x=-16;x<=22;x++)if(x<0 || x>8) {
            put(x,100,-11,Blocks.STONE_BRICKS);
            if(Math.floorMod(x,3)==0) {put(x,101,-11,Blocks.SMOOTH_SANDSTONE);put(x,102,-11,Blocks.LANTERN);}
            else put(x,101,-11,state("spruce_fence[east=true,west=true]"));
        }
        fill(13,100,-8,17,102,-8,Blocks.BOOKSHELF);
        fill(13,103,-8,17,103,-8,Blocks.DARK_OAK_SLAB);
        put(14,100,-6,Blocks.SMITHING_TABLE);put(16,100,-6,state("anvil[facing=west]"));
        put(17,100,-7,state("barrel[facing=north]"));put(17,101,-7,Blocks.LANTERN);
        for(int x=-13;x<=-8;x++)put(x,100,-7,state("spruce_stairs[facing=north]"));
        // Shallow lily pool on the right, enclosed so its water never spreads.
        for(int x=14;x<=19;x++)for(int z=-1;z<=5;z++) {
            boolean edge=x==14 || x==19 || z==-1 || z==5;
            put(x,99,z,Blocks.STONE_BRICKS);
            put(x,100,z,edge?state("waxed_weathered_cut_copper"):Blocks.WATER.defaultBlockState());
        }
        for(int[] p:new int[][]{{15,0},{17,3},{18,1}})put(p[0],101,p[1],Blocks.LILY_PAD);
        planter(-7,0,2,4);planter(9,-10,3,2);planter(-13,-13,5,3);planter(19,-15,4,3);
        // Layered foliage beyond the open arch gives depth and dappled color.
        gardenTree(-12,-17,10,false);gardenTree(19,-19,12,true);gardenTree(26,-7,11,false);
        gardenTree(-23,-9,9,true);
        for(int x=-14;x<=20;x++)for(int z=-26;z<=-16;z++) {
            if(Math.floorMod(x*7+z*3,13)==0)put(x,100,z,Blocks.FLOWERING_AZALEA);
            else if(Math.floorMod(x*11+z,9)==0)put(x,100,z,state("short_grass"));
        }
        for(int x:new int[]{-4,12}) {
            put(x,108,4,state("cyan_wall_banner[facing=south]"));
            for(int y=109;y<=112;y++)put(x-1,y,-8,state("vine[east=true]"));
        }
        put(4,108,0,state("light[level=15]"));
        put(4,103,1,state("light[level=15]"));
        put(0,93,0,Blocks.EMERALD_BLOCK);
        VanillaSmash.LOG.info("Built Heartwood fighter pavilion room={}",origin/SPACING);
    }
    private void planter(int x,int z,int width,int depth) {
        for(int dx=0;dx<width;dx++)for(int dz=0;dz<depth;dz++) {
            put(x+dx,100,z+dz,Blocks.MOSSY_STONE_BRICKS);
            put(x+dx,101,z+dz,Blocks.MOSS_BLOCK);
            put(x+dx,102,z+dz,Math.floorMod(dx+dz,3)==0?Blocks.FLOWERING_AZALEA:Blocks.AZALEA);
        }
    }
    private void gardenTree(int x,int z,int height,boolean cherry) {
        var wood=cherry?Blocks.CHERRY_LOG:Blocks.OAK_LOG;
        fill(x,100,z,x,99+height,z,wood);
        fill(x-2,105,z,x+2,105,z,wood);
        for(int dy=-3;dy<=3;dy++)for(int dx=-5;dx<=5;dx++)for(int dz=-4;dz<=4;dz++) {
            double shape=dx*dx/26.0+dz*dz/18.0+dy*dy/10.0;
            if(shape<1.15)put(x+dx,98+height+dy,z+dz,state((cherry?"cherry_leaves":"oak_leaves")+"[persistent=true]"));
        }
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
