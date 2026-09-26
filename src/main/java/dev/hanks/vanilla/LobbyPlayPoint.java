package dev.hanks.vanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Clickable Play landmark; matchmaking authority remains in GameHub. */
public final class LobbyPlayPoint extends LobbyLandmark {
    public static final BlockPos PODIUM = new BlockPos(4,101,-72);
    public static final Vec3 POSITION = new Vec3(4.5,102,-71.5);
    public LobbyPlayPoint(VanillaSmash game) { super(game,PODIUM); }
    public static void buildPodium(ServerLevel level) { buildPodium(level,PODIUM,Blocks.CHISELED_QUARTZ_BLOCK); }
    @Override protected LivingEntity createFighter(ServerLevel level) {
        var fighter=FighterModels.create(level,FighterClass.STEVE);
        fighter.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        return fighter;
    }
    @Override protected String title() { return "PLAY"; }
    @Override protected String subtitle() { return "Click to play"; }
    @Override protected int color() { return 0xffd66b; }
    @Override protected void activate(ServerPlayer player) { game.hub.open(player); }
}
