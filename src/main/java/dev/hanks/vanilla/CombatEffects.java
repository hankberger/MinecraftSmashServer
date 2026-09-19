package dev.hanks.vanilla;

import java.util.*;
import com.mojang.math.Transformation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Short, untextured vanilla ribbons: readable with or without the UI resource pack. */
public final class CombatEffects {
    private static final int SEGMENTS = 12;
    private static final class Slash {
        final Battle.Actor actor;
        final FighterMoves.Move move;
        final int direction, born;
        final long started;
        final List<Display.BlockDisplay> pieces = new ArrayList<>();
        final Set<UUID> audience;
        double x, y;
        long expires;
        Slash(Battle.Actor f, int now, Set<UUID> audience) {
            actor = f; move = f.state.move; direction = f.state.attackDirection; started = f.state.startedAt;
            born = now; expires = now + 3; x = f.pose.x; y = f.pose.y; this.audience = audience;
        }
    }
    private final Battle battle;
    private final List<Slash> slashes = new ArrayList<>();
    CombatEffects(Battle battle) { this.battle = battle; }
    public void strike(Battle.Actor f) {
        remove(f);
        var slash = new Slash(f, battle.now(), Set.copyOf(battle.game.viewers.keySet()));
        var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
        for (int i = 0; i < SEGMENTS; i++) {
            var d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, battle.level);
            d.setBlockState((f.state.move.kind() == AttackKind.HEAVY ? Blocks.CONCRETE.yellow() : Blocks.CONCRETE.white()).defaultBlockState());
            d.setNoGravity(true); d.setBrightnessOverride(new Brightness(15, 15));
            d.setViewRange(2); d.setWidth(8); d.setHeight(8); d.setPosRotInterpolationDuration(1);
            slash.pieces.add(d); position(slash, i, 1);
            // Packet-only, like the battle HUD: spawn + metadata arrive atomically on the contact tick.
            // A tracked world entity can spend most of this very short effect waiting to be paired.
            packets.add(new ClientboundAddEntityPacket(d.getId(), d.getUUID(), d.getX(), d.getY(), d.getZ(),
                    0, 0, EntityTypes.BLOCK_DISPLAY, 0, Vec3.ZERO, 0));
            packets.add(new ClientboundSetEntityDataPacket(d.getId(), d.getEntityData().getNonDefaultValues()));
            d.getEntityData().packDirty();
        }
        send(slash, new ClientboundBundlePacket(packets));
        slashes.add(slash);
    }
    private void position(Slash slash, int index, double fade) {
        var shape = CombatGeometry.shape(slash.move, slash.direction, slash.x, slash.y);
        var a = shape.arc(index / (double)SEGMENTS); var b = shape.arc((index + 1) / (double)SEGMENTS);
        double dx = b.x() - a.x(), dy = b.y() - a.y(), length = Math.hypot(dx, dy);
        // Taper both ends; the brief curved blade describes the filled sweep, not a character outline.
        double taper = Math.pow(Math.sin(Math.PI * (index + .5) / SEGMENTS), 1.4);
        double width = (slash.move.kind() == AttackKind.HEAVY ? .24 : .16) * taper * fade;
        var d = slash.pieces.get(index);
        d.setPos(a.x() + dy / length * width / 2, a.y() - dx / length * width / 2, .87);
        d.setTransformation(new Transformation(null, new Quaternionf().rotationZ((float)Math.atan2(dy, dx)),
                new Vector3f((float)(length + .015), (float)width, .025f), null));
    }
    public void tick() {
        for (var it = slashes.iterator(); it.hasNext();) {
            var slash = it.next(); var f = slash.actor;
            boolean active = f.state.startedAt == slash.started && f.state.activeUntil > battle.now();
            if (active) { slash.expires = Math.max(slash.expires, f.state.activeUntil + 1); slash.x = f.pose.x; slash.y = f.pose.y; }
            if (f.eliminated || !battle.game.fighting(f) || battle.now() >= slash.expires) {
                discard(slash); it.remove(); continue;
            }
            if (slash.born == battle.now()) continue;
            var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
            for (int i = 0; i < SEGMENTS; i++) {
                position(slash, i, active ? 1 : .35);
                var d = slash.pieces.get(i);
                packets.add(ClientboundEntityPositionSyncPacket.of(d));
                var dirty = d.getEntityData().packDirty();
                if (dirty != null) packets.add(new ClientboundSetEntityDataPacket(d.getId(), dirty));
            }
            send(slash, new ClientboundBundlePacket(packets));
        }
    }
    public void contact(CombatGeometry.Point point, boolean heavy, boolean blocked) {
        battle.level.sendParticles(blocked ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CRIT,
                true, false, point.x(), point.y(), 1, blocked ? 4 : heavy ? 7 : 4, .08, .10, .02, .025);
    }
    public void remove(Battle.Actor f) {
        slashes.removeIf(s -> { if (s.actor != f) return false; discard(s); return true; });
    }
    private void send(Slash slash, Packet<? super ClientGamePacketListener> packet) {
        for (var id : slash.audience) { var view = battle.game.viewers.get(id); if (view != null) view.player().connection.send(packet); }
    }
    private void discard(Slash slash) { send(slash, new ClientboundRemoveEntitiesPacket(slash.pieces.stream().mapToInt(Entity::getId).toArray())); }
    public void close() { slashes.forEach(this::discard); slashes.clear(); }
}
