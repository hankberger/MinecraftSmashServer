package dev.hanks.vanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;

/** Real-world migration and collision checks, also run against already-built maps. */
final class MapWorldTest {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void verify(MinecraftServer server) {
        var lobby = server.getLevel(MvpWorlds.LOBBY);
        // Simulate remnants and markers from the deployed revision, then migrate again.
        lobby.setBlock(new BlockPos(0,108,-48), Blocks.PURPUR_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        lobby.setBlock(new BlockPos(0,140,21), Blocks.DARK_OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
        lobby.setBlock(new BlockPos(0,96,-3), Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
        try { LobbyBuilder.ensureBuilt(lobby); } catch (java.io.IOException e) { throw new AssertionError(e); }
        check(lobby.getBlockState(new BlockPos(0,108,-48)).isAir(), "Old lotus is removed from the new library");
        check(lobby.getBlockState(new BlockPos(0,140,21)).isAir(), "Old tree trunk is removed above the relocated lotus");
        check(lobby.getBlockState(new BlockPos(48,103,0)).is(Blocks.GOLD_BLOCK), "Lotus has its own side garden");
        check(!lobby.getBlockState(new BlockPos(0,140,-15)).isAir(), "Tree stands behind the new arrival court");
        for(int x=-9;x<=9;x++) for(int z=-70;z<=-55;z++) {
            check(!lobby.getBlockState(new BlockPos(x,100,z)).isAir(),"Court has a walkable floor");
            check(lobby.getBlockState(new BlockPos(x,101,z)).isAir() && lobby.getBlockState(new BlockPos(x,102,z)).isAir(),"Court has clear headroom");
        }
        for (int step=1; step<=8; step++) {
            var p = new BlockPos(3,100+step,16+step-36);
            check(lobby.getBlockState(p).getBlock() instanceof StairBlock, "Library staircase is continuous");
            check(lobby.getBlockState(p.above()).isAir() && lobby.getBlockState(p.above(2)).isAir(), "Stair headroom");
        }
        verifyArena(server);
        try { LobbyBuilder.ensureBuilt(lobby); } catch (java.io.IOException e) { throw new AssertionError(e); }
        VanillaSmash.LOG.info("MAP_MIGRATION_AND_COLLISION_CHECKS_PASSED");
    }
    static void verifyArena(MinecraftServer server) {
        var arena = server.getLevel(MvpWorlds.ARENA);
        arena.setBlock(new BlockPos(-22,85,-10), Blocks.PURPUR_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        arena.setBlock(new BlockPos(1,60,0), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        // Revision 3 (including after a rollback) must migrate instead of trusting its old marker.
        arena.setBlock(new BlockPos(1,40,0), Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
        ArenaBuilder.ensureBuilt(arena);
        check(arena.getBlockState(new BlockPos(-22,85,-10)).isAir(), "Old arena halo removed");
        check(arena.getBlockState(new BlockPos(1,60,0)).isAir(), "Old marker no longer floats inside the wider camera frame");
        check(arena.getBlockState(new BlockPos(1,40,0)).is(Blocks.EMERALD_BLOCK), "Revision 4 marker stays below the blast zone");
        check(arena.getBlockState(new BlockPos(5,114,-42)).isAir(), "Old homestead chimney removed");
        for (int x : new int[]{-16,-15,15,16}) for (int y=77; y<=80; y++) for (int z=1; z<=3; z++)
            check(arena.getBlockState(new BlockPos(x,y,z)).isAir(), "Front ledge corners leave hanging fighters visible");
        for (int x=(int)ArenaRules.BLAST_LEFT; x<=ArenaRules.BLAST_RIGHT; x++) for (int y=80; y<=ArenaRules.BLAST_TOP; y++) {
            var p = new BlockPos(x,y,0);
            var shape = arena.getBlockState(p).getCollisionShape(arena,p);
            boolean deck = y == 80 && x >= -16 && x <= 16 || ArenaRules.platform(x,y,0);
            check(deck ? !shape.isEmpty() && shape.max(Direction.Axis.Y) == 1 : shape.isEmpty(),
                    "Only the intended floors collide in the fighting plane: " + p);
        }
        check(arena.getBlockState(new BlockPos(0,74,2)).is(Blocks.BUDDING_AMETHYST), "Recessed central geode built");
        for (int x=-16; x<=16; x++) check(arena.getBlockState(new BlockPos(x,80,0)).is(Blocks.GRASS_BLOCK), "Continuous grass deck");
        // Decorative water must remain in closed columns/pools, even if fluid ticks resume.
        for (int[] fall : new int[][]{{-43,86,63,-23,1},{52,92,71,-27,1}}) {
            int x=fall[0],top=fall[1],bottom=fall[2],z=fall[3],width=fall[4];
            for (int y=bottom; y<=top; y++) {
                check(arena.getBlockState(new BlockPos(x-1,y,z)).is(Blocks.BARRIER), "Waterfall left containment");
                check(arena.getBlockState(new BlockPos(x+width,y,z)).is(Blocks.BARRIER), "Waterfall right containment");
                for (int dx=0; dx<width; dx++) for (int side : new int[]{-1,1})
                    check(arena.getBlockState(new BlockPos(x+dx,y,z+side)).is(Blocks.BARRIER), "Waterfall front/back containment");
            }
        }
        // Markers also make repeated preparation safe and inexpensive.
        ArenaBuilder.ensureBuilt(arena);
        // A revision-2 rollback leaves a newer marker outside its clearing bounds.
        arena.setBlock(new BlockPos(1,60,0), Blocks.REINFORCED_DEEPSLATE.defaultBlockState(), Block.UPDATE_CLIENTS);
        arena.setBlock(new BlockPos(-22,85,-10), Blocks.PURPUR_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        ArenaBuilder.ensureBuilt(arena);
        check(arena.getBlockState(new BlockPos(1,60,0)).isAir() && arena.getBlockState(new BlockPos(-22,85,-10)).isAir(), "Rollback remnants are cleared even when the revision-4 marker survived");
        VanillaSmash.LOG.info("ARENA_LEDGE_MIGRATION_AND_COLLISION_CHECKS_PASSED");
    }
}
