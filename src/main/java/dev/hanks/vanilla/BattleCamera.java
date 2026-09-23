package dev.hanks.vanilla;

import dev.hanks.vanilla.mixin.DisplayInterpolationMixin;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

/** Private vanilla camera rig. One interpolated carrier moves the eye and HUD together on the client. */
public final class BattleCamera {
    public static final int INTERPOLATION_TICKS = 2;
    public final Display.BlockDisplay carrier;
    public final LivingEntity eye;
    public final FollowCamera follow;
    public final Vec3 anchor;
    private final ServerPlayer player;
    private int updatedAt;
    public BattleCamera(ServerPlayer player, FollowCamera follow, int now) {
        this.player = player; this.follow = follow; updatedAt = now;
        anchor = anchor(player, follow);
        carrier = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, player.level());
        carrier.setNoGravity(true); carrier.setInvulnerable(true);
        ((DisplayInterpolationMixin)carrier).smashInterpolationDuration(INTERPOLATION_TICKS);
        eye = FighterModels.create(player.level(),FighterClass.STEVE);
        eye.setInvisible(true); eye.setNoGravity(true); eye.setInvulnerable(true); eye.setSilent(true);
        eye.setYRot(180); eye.setYHeadRot(180); eye.setYBodyRot(180);
        position(follow.frame());
        if (!eye.startRiding(carrier,true,false)) throw new IllegalStateException("Cannot attach battle camera");
        carrier.positionRider(eye);
        // The client must create both entities, mount the eye and select it in
        // the same frame; there is no tracking delay for these private entities.
        var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
        spawnPackets(carrier, packets); spawnPackets(eye, packets);
        packets.add(new ClientboundSetPassengersPacket(carrier));
        packets.add(new ClientboundSetCameraPacket(eye));
        player.connection.send(new ClientboundBundlePacket(packets));
    }
    public static Vec3 anchor(ServerPlayer player, FollowCamera follow) {
        var frame = follow.frame();
        // The local player's equipment is rendered even while invisible. Keep every
        // control body behind the furthest camera position (40), outside its view.
        return new Vec3(frame.x(), frame.eyeY()-player.getEyeHeight(), 48);
    }
    public void spawn(Entity entity) {
        var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
        spawnPackets(entity, packets);
        player.connection.send(new ClientboundBundlePacket(packets));
    }
    private static void spawnPackets(Entity entity, List<Packet<? super ClientGamePacketListener>> packets) {
        packets.add(new ClientboundAddEntityPacket(entity.getId(),entity.getUUID(),entity.getX(),entity.getY(),entity.getZ(),
                entity.getXRot(),entity.getYRot(),entity.getType(),0,Vec3.ZERO,entity.getYHeadRot()));
        var data = entity.getEntityData().getNonDefaultValues();
        if (data != null) packets.add(new ClientboundSetEntityDataPacket(entity.getId(),data));
        entity.getEntityData().packDirty();
    }
    public void passengers() { player.connection.send(new ClientboundSetPassengersPacket(carrier)); }
    public void attach() { passengers(); player.connection.send(new ClientboundSetCameraPacket(eye)); }
    private void position(FollowCamera.Frame f) {
        double offset = eye.getVehicleAttachmentPoint(carrier).y;
        carrier.setPos(f.x(),f.eyeY()-eye.getEyeHeight()+offset,f.distance());
        carrier.positionRider(eye);
    }
    public void tick(Battle battle, int now) {
        var own = battle.actors.get(player.getUUID());
        double x = .5, y = ArenaRules.CAMERA_Y;
        var opponents = new java.util.ArrayList<FollowCamera.Focus>();
        if (own != null && !own.eliminated && !own.state.floating(now)) {
            x = own.pose.x; y = own.pose.y + 1;
            for(var f:battle.actors.values()) if(f!=own && !f.eliminated && !f.state.floating(now)
                    && !(f.state.strongLaunch && now<f.state.launchUntil))
                opponents.add(new FollowCamera.Focus(f.pose.x,f.pose.y+1));
        } else {
            var live = battle.actors.values().stream().filter(f -> !f.eliminated && !f.state.floating(now)).toList();
            if (!live.isEmpty()) { x=live.stream().mapToDouble(f->f.pose.x).average().orElse(.5); y=live.stream().mapToDouble(f->f.pose.y+1).average().orElse(y);
                live.forEach(f->opponents.add(new FollowCamera.Focus(f.pose.x,f.pose.y+1))); }
        }
        var frame = follow.tick(x,y,opponents);
        if (now - updatedAt < INTERPOLATION_TICKS) return;
        updatedAt = now;
        var before = carrier.position(); position(frame);
        if (before.distanceToSqr(carrier.position()) > 1e-8)
            player.connection.send(ClientboundEntityPositionSyncPacket.of(carrier));
    }
    public void close() {
        var ids = new java.util.ArrayList<Integer>(); ids.add(carrier.getId());
        for (var passenger : java.util.List.copyOf(carrier.getPassengers())) { ids.add(passenger.getId()); passenger.stopRiding(); passenger.discard(); }
        player.connection.send(new ClientboundRemoveEntitiesPacket(ids.stream().mapToInt(Integer::intValue).toArray()));
        carrier.discard();
    }
}
