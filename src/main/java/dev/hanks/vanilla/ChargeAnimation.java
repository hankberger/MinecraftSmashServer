package dev.hanks.vanilla;

import java.util.*;
import com.mojang.math.Transformation;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Vanilla arm poses and interpolated, packet-only windup props. Never combat objects or hitboxes. */
final class ChargeAnimation {
    private static final Consumable OVERHEAD = Consumable.builder().consumeSeconds(3600)
            .animation(ItemUseAnimation.TRIDENT).sound(Holder.direct(SoundEvents.EMPTY)).hasConsumeParticles(false).build();
    private static final Consumable BRACED = Consumable.builder().consumeSeconds(3600)
            .animation(ItemUseAnimation.BLOCK).sound(Holder.direct(SoundEvents.EMPTY)).hasConsumeParticles(false).build();
    private static final class Windup {
        final Battle.Actor actor;
        final long started;
        final Set<UUID> audience;
        final List<Display.ItemDisplay> pieces = new ArrayList<>();
        Windup(Battle.Actor actor, Set<UUID> audience) {
            this.actor = actor; this.started = actor.state.startedAt; this.audience = audience;
        }
    }
    private final Battle battle;
    private final Map<UUID, Windup> windups = new HashMap<>();
    ChargeAnimation(Battle battle) { this.battle = battle; }

    static boolean posed(Battle.Actor f) {
        return f.state.chargingSpecial() && (f.kind == FighterClass.STEVE || f.kind == FighterClass.ALEX);
    }
    void equip(Battle.Actor f, Item tool) {
        boolean posed = posed(f);
        var held = f.body.getMainHandItem();
        // Replace only on entering/leaving the pose: swapping every tick restarts native use animation.
        if (held.is(tool) && held.has(DataComponents.CONSUMABLE) == posed) return;
        f.body.stopUsingItem();
        var stack = new ItemStack(tool);
        if (posed) {
            stack.set(DataComponents.CONSUMABLE, f.kind == FighterClass.STEVE ? OVERHEAD : BRACED);
        }
        f.body.setItemSlot(EquipmentSlot.MAINHAND, stack);
        if (posed) {
            // Native tracking normally sends use metadata before equipment. The client would
            // initialize a zero-length use from the old sword/pickaxe and leave the arm idle.
            // Deliver the usable stack first, before sync() starts the arm pose.
            var packet = new ClientboundSetEquipmentPacket(f.body.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, stack.copy())));
            for (var view : battle.game.viewers.values()) view.player().connection.send(packet);
        }
    }
    int pieces(Battle.Actor f) { var w = windups.get(f.id); return w == null ? 0 : w.pieces.size(); }
    int[] entityIds(Battle.Actor f) {
        var w = windups.get(f.id); return w == null ? new int[0] : w.pieces.stream().mapToInt(Entity::getId).toArray();
    }
    void tick() {
        for (var it = windups.values().iterator(); it.hasNext();) {
            var w = it.next(); var f = w.actor;
            if (f.eliminated || !battle.game.fighting(f) || !f.state.chargingSpecial() || w.started != f.state.startedAt) {
                discard(w); it.remove();
            }
        }
        for (var f : battle.actors.values()) {
            if (f.eliminated || !battle.game.fighting(f) || !f.state.chargingSpecial()
                    || f.kind != FighterClass.VILLAGER) continue;
            var w = windups.get(f.id);
            boolean spawn = w == null;
            if (spawn) {
                w = new Windup(f, Set.copyOf(battle.game.viewers.keySet())); windups.put(f.id, w);
                var d = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, battle.level);
                d.setItemStack(new ItemStack(f.kind == FighterClass.ZOMBIE ? Items.COARSE_DIRT : Items.BELL));
                d.setItemTransform(ItemDisplayContext.FIXED);
                d.setNoGravity(true); d.setBrightnessOverride(new Brightness(15, 15));
                d.setViewRange(2); d.setWidth(4); d.setHeight(4);
                // Match the living model's three-tick movement interpolation. Feeding the
                // already-interpolated CombatPose here adds another delay and separates hands/items.
                d.setPosRotInterpolationDuration(3); d.setTransformationInterpolationDuration(2);
                w.pieces.add(d);
            }
            var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
            for (int i = 0; i < w.pieces.size(); i++) {
                var d = w.pieces.get(i); position(w, i);
                if (spawn) {
                    packets.add(new ClientboundAddEntityPacket(d.getId(), d.getUUID(), d.getX(), d.getY(), d.getZ(),
                            d.getXRot(), d.getYRot(), EntityTypes.ITEM_DISPLAY, 0, Vec3.ZERO, 0));
                    packets.add(new ClientboundSetEntityDataPacket(d.getId(), d.getEntityData().getNonDefaultValues()));
                    d.getEntityData().packDirty();
                } else {
                    packets.add(ClientboundEntityPositionSyncPacket.of(d));
                    var dirty = d.getEntityData().packDirty();
                    if (dirty != null) packets.add(new ClientboundSetEntityDataPacket(d.getId(), dirty));
                }
            }
            send(w, new ClientboundBundlePacket(packets));
        }
    }
    private void position(Windup w, int index) {
        var f = w.actor;
        double p = ChargeRules.power(f.kind, f.state.chargeTicks(battle.now()));
        double ease = p * p * (3 - 2 * p);
        double idle = p >= 1 ? Math.sin((battle.now() - w.started) * .24) : 0;
        double x, y, scale, angle;
        switch (f.kind) {
            // Keep a fixed size and a hand-sized travel arc; charge rotates around the grip
            // instead of scaling an item from its centre or lifting it away from the model.
            case ZOMBIE -> { x = .60; y = 1.74 + .04 * ease; scale = .85; angle = -8 - 10 * ease + idle * 1.5; }
            case VILLAGER -> { x = .46; y = 1.14; scale = .80; angle = -8 - 16 * ease + idle * 2; }
            default -> throw new IllegalStateException();
        }
        var d = w.pieces.get(index);
        d.setPos(f.body.getX(), f.body.getY(), f.body.getZ());
        // Turn the whole hand anchor with the body instead of flipping a world-space X offset
        // through the torso. Local +Z is the fighter's forward direction under display yaw.
        d.setYRot(f.body.getYRot());
        var rotation = new Quaternionf().rotationX((float)Math.toRadians(-angle));
        // The bell's top handle stays at the crossed hands while its lower body winds back.
        var offset = f.kind == FighterClass.VILLAGER ? new Vector3f(0, -.20f, 0) : new Vector3f();
        rotation.transform(offset);
        d.setTransformation(new Transformation(new Vector3f(0, (float)y, (float)x).add(offset),
                rotation, new Vector3f((float)scale),
                new Quaternionf().rotationY((float)(-Math.PI / 2 + .45))));
        d.setTransformationInterpolationDelay(0);
    }
    void remove(Battle.Actor f) { var w = windups.remove(f.id); if (w != null) discard(w); }
    void close() { windups.values().forEach(this::discard); windups.clear(); }
    private void discard(Windup w) { send(w, new ClientboundRemoveEntitiesPacket(w.pieces.stream().mapToInt(Entity::getId).toArray())); }
    private void send(Windup w, Packet<? super ClientGamePacketListener> packet) {
        for (var id : w.audience) { var view = battle.game.viewers.get(id); if (view != null) view.player().connection.send(packet); }
    }
}
