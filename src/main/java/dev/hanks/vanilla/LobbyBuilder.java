package dev.hanks.vanilla;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class LobbyBuilder {
    private static final BlockPos MARKER = new BlockPos(0, 96, -3);
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
    private record Box(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {}
    private LobbyBuilder() {}
    public static void ensureBuilt(ServerLevel level) throws IOException {
        if (!level.dimension().equals(MvpWorlds.LOBBY)) throw new IllegalArgumentException("Not the lobby");
        if (level.getBlockState(MARKER).is(Blocks.LODESTONE)) return;
        // Resolve and validate the whole asset before touching this world.
        List<Box> boxes = read(level);
        long started = System.nanoTime();
        var pos = new BlockPos.MutableBlockPos();
        // Remove the complete 0.3 glass lobby, including its old marker.
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) for (int y = 78; y <= 83; y++)
            level.setBlock(pos.set(x, y, z), Blocks.AIR.defaultBlockState(), FLAGS);
        for (Box b : boxes) for (int x = b.x1; x <= b.x2; x++) for (int z = b.z1; z <= b.z2; z++) for (int y = b.y1; y <= b.y2; y++)
            level.setBlock(pos.set(x, y, z), b.state, FLAGS);
        // Publish the revision only after every block is placed. Interrupted builds retry.
        level.setBlock(MARKER, Blocks.LODESTONE.defaultBlockState(), FLAGS);
        VanillaSmash.LOG.info("Built mythical_garden spawn: 575027 blocks in {} ms", (System.nanoTime() - started) / 1_000_000);
    }

    private static List<Box> read(ServerLevel level) throws IOException {
        var resource = LobbyBuilder.class.getResourceAsStream("/data/smash_vanilla/structures/mythical_garden.bin.gz");
        if (resource == null) throw new IOException("Missing bundled mythical_garden");
        try (var in = new DataInputStream(new GZIPInputStream(resource))) {
            if (in.readInt() != 0x47415231) throw new IOException("Unknown garden format");
            int paletteSize = in.readUnsignedShort();
            if (paletteSize < 1 || paletteSize > 256) throw new IOException("Invalid garden palette");
            var palette = new BlockState[paletteSize];
            for (int i = 0; i < paletteSize; i++)
                palette[i] = BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), in.readUTF(), false).blockState();
            int count = in.readInt();
            if (count < 1 || count > 100_000) throw new IOException("Invalid garden box count");
            List<Box> boxes = new ArrayList<>(count);
            long blocks = 0;
            for (int i = 0; i < count; i++) {
                int x1 = in.readShort(), y1 = in.readShort(), z1 = in.readShort(), x2 = in.readShort(), y2 = in.readShort(), z2 = in.readShort(), index = in.readUnsignedShort();
                if (x1 < -112 || x2 > 112 || x1 > x2 || y1 < 58 || y2 > 232 || y1 > y2 || z1 < -112 || z2 > 104 || z1 > z2 || index >= paletteSize)
                    throw new IOException("Invalid garden cuboid");
                boxes.add(new Box(x1, y1, z1, x2, y2, z2, palette[index]));
                blocks += (long) (x2-x1+1) * (y2-y1+1) * (z2-z1+1);
            }
            if (blocks != 575027 || in.read() != -1) throw new IOException("Incomplete garden asset");
            return boxes;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IOException("Garden contains an unknown block state", e);
        }
    }
}
