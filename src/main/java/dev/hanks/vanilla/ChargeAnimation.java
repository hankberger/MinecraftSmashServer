package dev.hanks.vanilla;

import java.util.*;
import com.mojang.math.Transformation;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.Identifier;
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
            stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("air"));
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
            if (f.eliminated || !battle.game.fighting(f) || !f.state.chargingSpecial() || f.kind == FighterClass.SKELETON) continue;
            var w = windups.get(f.id);
            boolean spawn = w == null;
            if (spawn) {
                w = new Windup(f, Set.copyOf(battle.game.viewers.keySet())); windups.put(f.id, w);
                int count = f.kind == FighterClass.ZOMBIE ? 3 : 1;
                for (int i = 0; i < count; i++) {
                    var d = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, battle.level);
                    Item item = switch (f.kind) {
                        case STEVE -> Items.IRON_PICKAXE; case ALEX -> Items.GOLDEN_SWORD;
                        case ZOMBIE -> i == 0 ? Items.COARSE_DIRT : Items.MOSSY_COBBLESTONE;
                        case VILLAGER -> Items.BELL; default -> throw new IllegalStateException();
                    };
                    d.setItemStack(new ItemStack(item)); d.setItemTransform(ItemDisplayContext.FIXED);
                    d.setNoGravity(true); d.setBrightnessOverride(new Brightness(15, 15));
                    d.setViewRange(2); d.setWidth(6); d.setHeight(6);
                    d.setPosRotInterpolationDuration(1); d.setTransformationInterpolationDuration(2);
                    w.pieces.add(d);
                }
            }
            var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
            for (int i = 0; i < w.pieces.size(); i++) {
                var d = w.pieces.get(i); position(w, i);
                if (spawn) {
                    packets.add(new ClientboundAddEntityPacket(d.getId(), d.getUUID(), d.getX(), d.getY(), d.getZ(),
                            0, 0, EntityTypes.ITEM_DISPLAY, 0, Vec3.ZERO, 0));
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
        int dir = f.facing;
        double x, y, scale, angle;
        switch (f.kind) {
            case STEVE -> { x = -.18 - .1 * ease; y = 1.55 + .8 * ease; scale = 1.65 + .35 * ease; angle = -25 + 80 * ease + idle * 3; }
            case ALEX -> { x = -.25 - .45 * ease; y = 1.2 + .23 * ease; scale = 1.65 + .2 * ease; angle = 85 + 50 * ease + idle * 2; }
            case ZOMBIE -> {
                if (index == 0) { x = .55; y = 1.1 + 1.0 * ease; scale = .75 + 1.15 * ease; angle = -10 - 25 * ease + idle * 3; }
                else { double side = index == 1 ? -1 : 1; x = .55 + side * (.7 - .25 * ease); y = .55 + .85 * ease;
                    scale = .35 + .15 * ease; angle = side * (30 + 80 * ease); }
            }
            case VILLAGER -> { x = .5 - .45 * ease; y = 1.3 + 1.25 * ease; scale = 1.15 + .45 * ease; angle = 12 + 22 * ease + idle * 7; }
            default -> throw new IllegalStateException();
        }
        var d = w.pieces.get(index);
        d.setPos(f.pose.x, f.pose.y, .82);
        d.setTransformation(new Transformation(new Vector3f((float)(dir * x), (float)y, 0),
                new Quaternionf().rotationZ((float)Math.toRadians(dir * angle)), new Vector3f((float)scale),
                new Quaternionf().rotationY((dir < 0 ? (float)Math.PI : 0)
                        + (f.kind == FighterClass.ZOMBIE || f.kind == FighterClass.VILLAGER ? dir * .45f : 0))));
        d.setTransformationInterpolationDelay(0);
    }
    void remove(Battle.Actor f) { var w = windups.remove(f.id); if (w != null) discard(w); }
    void close() { windups.values().forEach(this::discard); windups.clear(); }
    private void discard(Windup w) { send(w, new ClientboundRemoveEntitiesPacket(w.pieces.stream().mapToInt(Entity::getId).toArray())); }
    private void send(Windup w, Packet<? super ClientGamePacketListener> packet) {
        for (var id : w.audience) { var view = battle.game.viewers.get(id); if (view != null) view.player().connection.send(packet); }
    }
}
