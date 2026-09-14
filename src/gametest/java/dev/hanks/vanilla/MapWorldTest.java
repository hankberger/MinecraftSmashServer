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
        var arena = server.getLevel(MvpWorlds.ARENA);
        // Simulate remnants and markers from the deployed revision, then migrate again.
        lobby.setBlock(new BlockPos(0,108,-48), Blocks.PURPUR_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        lobby.setBlock(new BlockPos(0,140,21), Blocks.DARK_OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
        lobby.setBlock(new BlockPos(0,96,-3), Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        arena.setBlock(new BlockPos(-22,85,-10), Blocks.PURPUR_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        arena.setBlock(new BlockPos(1,60,0), Blocks.LODESTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        try { LobbyBuilder.ensureBuilt(lobby); } catch (java.io.IOException e) { throw new AssertionError(e); }
        ArenaBuilder.ensureBuilt(arena);
        check(lobby.getBlockState(new BlockPos(0,108,-48)).isAir(), "Old lotus is removed from the new library");
        check(lobby.getBlockState(new BlockPos(0,140,21)).isAir(), "Old tree trunk is removed above the relocated lotus");
        check(lobby.getBlockState(new BlockPos(0,103,21)).is(Blocks.GOLD_BLOCK), "Lotus is at the old tree site");
        check(!lobby.getBlockState(new BlockPos(0,140,-48)).isAir(), "Tree is at the old lotus site");
        check(arena.getBlockState(new BlockPos(-22,85,-10)).isAir(), "Old arena halo removed");
        for (int step=1; step<=8; step++) {
            var p = new BlockPos(3,100+step,16+step-69);
            check(lobby.getBlockState(p).getBlock() instanceof StairBlock, "Library staircase is continuous");
            check(lobby.getBlockState(p.above()).isAir() && lobby.getBlockState(p.above(2)).isAir(), "Stair headroom");
        }
        for (int x=-26; x<=27; x++) for (int y=80; y<=105; y++) {
            var p = new BlockPos(x,y,0);
            var shape = arena.getBlockState(p).getCollisionShape(arena,p);
            boolean deck = y == 80 && x >= -16 && x <= 16 || ArenaRules.platform(x,y,0);
            check(deck ? !shape.isEmpty() && shape.max(Direction.Axis.Y) == 1 : shape.isEmpty(),
                    "Only the intended floors collide in the fighting plane: " + p);
        }
        check(arena.getBlockState(new BlockPos(6,76,2)).is(Blocks.BUDDING_AMETHYST), "Geode built");
        // Markers also make repeated preparation safe and inexpensive.
        try { LobbyBuilder.ensureBuilt(lobby); } catch (java.io.IOException e) { throw new AssertionError(e); }
        ArenaBuilder.ensureBuilt(arena);
        VanillaSmash.LOG.info("MAP_MIGRATION_AND_COLLISION_CHECKS_PASSED");
    }
}
