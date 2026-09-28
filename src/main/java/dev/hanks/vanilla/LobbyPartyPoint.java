package dev.hanks.vanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** A zombie duo behind Play, on the left side of the courtyard from arrival. */
public final class LobbyPartyPoint extends LobbyLandmark {
    public static final BlockPos PODIUM = new BlockPos(9,101,-66);
    public static final Vec3 POSITION = new Vec3(9.5,102,-65.5);
    private Zombie baby;
    public LobbyPartyPoint(VanillaSmash game) { super(game,PODIUM); }
    public static void buildPodium(ServerLevel level) { buildPodium(level,PODIUM,Blocks.AMETHYST_BLOCK); }
    @Override protected LivingEntity createFighter(ServerLevel level) { return FighterModels.create(level,FighterClass.ZOMBIE); }
    @Override protected void addCompanions(ServerLevel level,LivingEntity fighter) {
        baby=(Zombie)FighterModels.create(level,FighterClass.ZOMBIE);
        baby.setBaby(true);baby.setNoAi(true);baby.setPersistenceRequired();
        baby.setInvulnerable(true);baby.setNoGravity(true);baby.setSilent(true);
        baby.getAttribute(Attributes.SCALE).setBaseValue(1.2);
        baby.snapTo(POSITION.x,POSITION.y+2,POSITION.z,180,0);
        add(baby);
        if(!baby.startRiding(fighter,true,false))throw new IllegalStateException("Cannot attach Party's baby zombie");
    }
    public Zombie baby() { return baby; }
    @Override protected float labelHeight() { return 4.2f; }
    @Override protected String title() { return "PARTY"; }
    @Override protected String subtitle() { return "Invite friends"; }
    @Override protected int color() { return 0xd1adff; }
    @Override protected void activate(ServerPlayer player) { game.hub.partyPanel(player); }
    @Override public void close() { super.close();baby=null; }
}
