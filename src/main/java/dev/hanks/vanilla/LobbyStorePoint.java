package dev.hanks.vanilla;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A merchant across the arrival path from Play; browsing never changes matchmaking. */
public final class LobbyStorePoint extends LobbyLandmark {
    public static final BlockPos PODIUM = new BlockPos(-4,101,-72);
    public static final Vec3 POSITION = new Vec3(-3.5,102,-71.5);
    public LobbyStorePoint(VanillaSmash game) { super(game,PODIUM); }
    public static void buildPodium(ServerLevel level) { buildPodium(level,PODIUM,Blocks.EMERALD_BLOCK); }
    @Override protected LivingEntity createFighter(ServerLevel level) {
        return FighterModels.create(level,FighterClass.VILLAGER,"desert");
    }
    @Override protected String title() { return "STORE"; }
    @Override protected String subtitle() { return "Credits · Ringshift Plus"; }
    @Override protected int color() { return 0x8ce6b4; }
    @Override protected void activate(ServerPlayer player) {
        // This landmark is only for the ordinary courtyard, never a detached camera session.
        if (!player.level().dimension().equals(MvpWorlds.LOBBY) || game.stage.active(player)) return;
        var menu=game.hub.menu;
        List<MatchMenu.Button> buttons;
        try {
            var url=StoreMenu.url(System.getProperty("smash_vanilla.storeUrl",System.getenv("SMASH_STORE_URL")));
            buttons=List.of(MatchMenu.Button.link("Open store",url));
        } catch (IllegalArgumentException invalid) {
            VanillaSmash.LOG.warn("Store link unavailable: invalid configured store URL");
            buttons=List.of();
        }
        var body=StoreMenu.body(game.points.account(player.getUUID()).balance(),game.points.member(player.getUUID()));
        if (buttons.isEmpty()) body+="\n\nThe online store is temporarily unavailable.";
        menu.show(player,"Store",body,buttons,false,"Close",()->menu.clear(player));
    }
}
