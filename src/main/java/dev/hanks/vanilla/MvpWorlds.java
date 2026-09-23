package dev.hanks.vanilla;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;

/** Datapack dimensions are synchronized by Minecraft's standard configuration protocol. */
public final class MvpWorlds {
    public static final ResourceKey<Level> LOBBY = key("lobby"), ARENA = key("arena"), SHOWCASE = key("showcase");
    public static ResourceKey<Level> arena(BattleStage stage) { return key(stage.id); }
    public static BattleStage stage(Level level) {
        for (var stage : BattleStage.values()) if (level.dimension().equals(arena(stage))) return stage;
        throw new IllegalArgumentException("Not a battle dimension: " + level.dimension());
    }
    public static boolean battle(Level level) {
        for (var stage : BattleStage.values()) if (level.dimension().equals(arena(stage))) return true;
        return false;
    }
    private static ResourceKey<Level> key(String name) { return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("smash_vanilla", name)); }
    public static boolean managed(Level level) { return level.dimension().equals(LOBBY) || battle(level) || level.dimension().equals(SHOWCASE); }
    public static void prepare(MinecraftServer server, boolean buildLobby, boolean buildArena) {
        var lobby = java.util.Objects.requireNonNull(server.getLevel(LOBBY), "Lobby dimension unavailable");
        var showcase = java.util.Objects.requireNonNull(server.getLevel(SHOWCASE), "Showcase dimension unavailable");
        var levels = new java.util.ArrayList<net.minecraft.server.level.ServerLevel>();
        levels.add(lobby); levels.add(showcase);
        for (var stage : BattleStage.values()) levels.add(java.util.Objects.requireNonNull(server.getLevel(arena(stage)), stage.label+" dimension unavailable"));
        for (var level : levels) {
            level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
            level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
            level.getGameRules().set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
            level.getGameRules().set(GameRules.ENTITY_DROPS, false, server);
        }
        try { if (buildLobby) LobbyBuilder.ensureBuilt(lobby); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot load Mythical Garden", e); }
        if (buildArena) for (var stage : BattleStage.values()) ArenaBuilder.ensureBuilt(server.getLevel(arena(stage)));
    }
}
