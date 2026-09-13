package dev.hanks.vanilla;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/** Datapack dimensions are synchronized by Minecraft's standard configuration protocol. */
public final class MvpWorlds {
    public static final ResourceKey<Level> LOBBY = key("lobby"), ARENA = key("arena");
    private static ResourceKey<Level> key(String name) { return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("smash_vanilla", name)); }
    public static boolean managed(Level level) { return level.dimension().equals(LOBBY) || level.dimension().equals(ARENA); }
    public static void prepare(MinecraftServer server, boolean buildLobby, boolean buildArena) {
        var lobby = java.util.Objects.requireNonNull(server.getLevel(LOBBY), "Lobby dimension unavailable");
        var arena = java.util.Objects.requireNonNull(server.getLevel(ARENA), "Arena dimension unavailable");
        for (var level : new net.minecraft.server.level.ServerLevel[]{lobby, arena}) {
            level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
            level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
            level.getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
            level.getGameRules().set(GameRules.ENTITY_DROPS, false, server);
        }
        try { if (buildLobby) LobbyBuilder.ensureBuilt(lobby); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot load Mythical Garden", e); }
        if (buildArena) ArenaBuilder.ensureBuilt(arena);
    }
}
