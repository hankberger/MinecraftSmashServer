package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A clickable garden landmark; all matchmaking authority remains in GameHub. */
public final class LobbyPlayPoint {
    public static final BlockPos PODIUM = new BlockPos(4,101,-72);
    public static final Vec3 POSITION = new Vec3(PODIUM.getX()+.5, PODIUM.getY()+1, PODIUM.getZ()+.5);
    private final VanillaSmash game;
    private final List<Entity> entities = new ArrayList<>();
    private final Map<UUID, Integer> lastClick = new HashMap<>();
    private LivingEntity fighter;
    public LobbyPlayPoint(VanillaSmash game) { this.game = game; }
    public LivingEntity fighter() { return fighter; }
    public boolean owns(Entity entity) { return entities.contains(entity); }

    /** Small additive footprint; leaves the five-block-wide arrival route clear. */
    public static void buildPodium(ServerLevel level) {
        if (!level.dimension().equals(MvpWorlds.LOBBY)) throw new IllegalArgumentException("Not the lobby");
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var top=PODIUM.offset(dx,0,dz);
            level.setBlock(top.below(), Blocks.COPPER_BLOCK.waxed().oxidized().defaultBlockState(), flags);
            level.setBlock(top, (dx == 0 && dz == 0 ? Blocks.CHISELED_QUARTZ_BLOCK : Blocks.SMOOTH_QUARTZ_SLAB).defaultBlockState(), flags);
        }
    }

    public void tick() {
        if (game.ticks % 20 != 0 || game.network.arena()) return;
        var level = game.server.getLevel(MvpWorlds.LOBBY);
        var nearby = level.players().stream().filter(p -> !p.isRemoved() && p.position().distanceToSqr(POSITION) < 64 * 64)
                .min(Comparator.comparingDouble(p -> p.position().distanceToSqr(POSITION))).orElse(null);
        // No forced chunks or saved NPC duplicates when everyone leaves the garden.
        if (nearby == null) { close(); return; }
        if (fighter == null || entities.stream().anyMatch(Entity::isRemoved)) { close(); spawn(level); }
        fighter.setDeltaMovement(Vec3.ZERO); fighter.clearFire();
        fighter.setPos(POSITION); fighter.setYRot(180); fighter.setYBodyRot(180);
        double dx = nearby.getX() - POSITION.x, dz = nearby.getZ() - POSITION.z;
        float yaw = nearby.position().distanceToSqr(POSITION) < 100 ? (float)Math.toDegrees(Math.atan2(-dx,dz)) : 180;
        fighter.setYHeadRot(yaw);
        lastClick.entrySet().removeIf(e -> game.ticks - e.getValue() > 20);
    }
    private void spawn(ServerLevel level) {
        fighter = FighterModels.create(level,FighterClass.STEVE);
        fighter.setInvulnerable(true); fighter.setNoGravity(true); fighter.setSilent(true);
        fighter.getAttribute(Attributes.SCALE).setBaseValue(1.3);
        fighter.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        fighter.snapTo(POSITION.x,POSITION.y,POSITION.z,180,0);
        fighter.setYHeadRot(180); fighter.setYBodyRot(180); add(fighter);
        label(level,"PLAY",105.1f,1.4f,0xffd66b,true);
        label(level,"Click to play",104.75f,.65f,0xffffff,false);
    }
    private void label(ServerLevel level, String text, float y, float scale, int color, boolean bold) {
        var label = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY,level);
        label.setText(Component.literal(text).withStyle(s -> s.withColor(color).withBold(bold)));
        label.setBackgroundColor(0); label.setTextOpacity((byte)255); label.setFlags(Display.TextDisplay.FLAG_SHADOW);
        label.setBrightnessOverride(new net.minecraft.util.Brightness(15,15));
        label.setBillboardConstraints(Display.BillboardConstraints.CENTER); label.setViewRange(1);
        label.setTransformation(new Transformation(null,null,new Vector3f(scale),null));
        label.setPos(POSITION.x,y,POSITION.z); add(label);
    }
    private void add(Entity entity) {
        entities.add(entity); entity.addTag(VanillaSmash.TEMP); entity.level().addFreshEntity(entity);
    }
    public boolean click(ServerPlayer p, Entity target, InteractionHand hand) {
        if (target != fighter || fighter == null) return false;
        if (p.level().dimension().equals(MvpWorlds.LOBBY) && p.distanceToSqr(target) <= 25) open(p,hand);
        return true;
    }
    public boolean click(ServerPlayer p, BlockPos block, InteractionHand hand) {
        if (!p.level().dimension().equals(MvpWorlds.LOBBY) || Math.abs(block.getX()-PODIUM.getX())>1
                || Math.abs(block.getZ()-PODIUM.getZ())>1 || block.getY()!=PODIUM.getY()) return false;
        if (p.position().distanceToSqr(Vec3.atCenterOf(block)) <= 25) open(p,hand);
        return true;
    }
    private void open(ServerPlayer p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !game.hub.available(p) || p.containerMenu != p.inventoryMenu
                || game.ticks - lastClick.getOrDefault(p.getUUID(), -100) < 8) return;
        lastClick.put(p.getUUID(),game.ticks); game.hub.open(p);
    }
    public void close() {
        entities.forEach(Entity::discard); entities.clear(); fighter = null; lastClick.clear();
    }
}
