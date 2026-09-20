package dev.hanks.vanilla;

import java.util.*;
import com.mojang.math.Transformation;
import net.minecraft.core.particles.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Class silhouettes share the exact combat arc; decoration is inset, never extra reach. */
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
    final ChargeAnimation charges;
    CombatEffects(Battle battle) { this.battle = battle; charges = new ChargeAnimation(battle); }
    public void strike(Battle.Actor f) {
        remove(f);
        var slash = new Slash(f, battle.now(), Set.copyOf(battle.game.viewers.keySet()));
        var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
        int layers = f.kind == FighterClass.ZOMBIE && !FighterMoves.isSlam(slash.move) ? 3 : f.kind == FighterClass.ALEX ? 2 : 1;
        for (int i = 0; i < SEGMENTS * layers; i++) {
            var d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, battle.level);
            d.setBlockState(material(f.kind, slash.move, i / SEGMENTS));
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
        if (FighterMoves.isSlam(slash.move)) {
            for (int i = -3; i <= 3; i++) battle.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                    true, false, f.pose.x + i * .7, f.pose.y + .12, .8, 2, .08, .06, .02, .03);
        }
    }
    private static BlockState material(FighterClass kind, FighterMoves.Move move, int layer) {
        return switch (kind) {
            case STEVE -> (move.kind() == AttackKind.HEAVY ? Blocks.CONCRETE.cyan() : Blocks.CONCRETE.lightBlue()).defaultBlockState();
            case ALEX -> (layer == 0 ? Blocks.CONCRETE.orange() : Blocks.CONCRETE.white()).defaultBlockState();
            case ZOMBIE -> (FighterMoves.isSlam(move) ? Blocks.COARSE_DIRT : layer == 1 ? Blocks.CONCRETE.green() : Blocks.CONCRETE.lime()).defaultBlockState();
            case SKELETON -> Blocks.BONE_BLOCK.defaultBlockState();
            case VILLAGER -> Blocks.GOLD_BLOCK.defaultBlockState();
        };
    }
    private void position(Slash slash, int index, double fade) {
        var shape = CombatGeometry.shape(slash.move, slash.direction, slash.x, slash.y);
        int segment = index % SEGMENTS, layer = index / SEGMENTS;
        double inset = 1 - layer * .14;
        var edgeA = shape.arc(segment / (double)SEGMENTS); var edgeB = shape.arc((segment + 1) / (double)SEGMENTS);
        var a = new CombatGeometry.Point(shape.x() + (edgeA.x() - shape.x()) * inset, shape.y() + (edgeA.y() - shape.y()) * inset);
        var b = new CombatGeometry.Point(shape.x() + (edgeB.x() - shape.x()) * inset, shape.y() + (edgeB.y() - shape.y()) * inset);
        double dx = b.x() - a.x(), dy = b.y() - a.y(), length = Math.hypot(dx, dy);
        double taper = Math.pow(Math.sin(Math.PI * (segment + .5) / SEGMENTS), 1.4);
        double thickness = switch (slash.actor.kind) {
            case STEVE -> .18; case ALEX -> layer == 0 ? .11 : .055;
            case ZOMBIE -> FighterMoves.isSlam(slash.move) ? .26 : .085;
            case SKELETON -> segment % 2 == 0 ? .13 : .07; case VILLAGER -> .20;
        };
        double width = thickness * (slash.move.kind() == AttackKind.HEAVY ? 1.25 : 1) * taper * fade;
        double fill = slash.actor.kind == FighterClass.VILLAGER ? .80 : slash.actor.kind == FighterClass.SKELETON ? .90 : 1;
        var d = slash.pieces.get(index);
        d.setPos(a.x() + dy / length * width / 2, a.y() - dx / length * width / 2, .87);
        d.setTransformation(new Transformation(null, new Quaternionf().rotationZ((float)Math.atan2(dy, dx)),
                new Vector3f((float)(length * fill + .015), (float)width, .025f), null));
    }
    public void tick() {
        charges.tick();
        for (var it = slashes.iterator(); it.hasNext();) {
            var slash = it.next(); var f = slash.actor;
            boolean active = f.state.startedAt == slash.started && f.state.activeUntil > battle.now();
            if (active) { slash.expires = Math.max(slash.expires, f.state.activeUntil + 1); slash.x = f.pose.x; slash.y = f.pose.y; }
            if (f.eliminated || !battle.game.fighting(f) || battle.now() >= slash.expires) {
                discard(slash); it.remove(); continue;
            }
            if (slash.born == battle.now()) continue;
            var packets = new ArrayList<Packet<? super ClientGamePacketListener>>();
            for (int i = 0; i < slash.pieces.size(); i++) {
                position(slash, i, active ? 1 : .35);
                var d = slash.pieces.get(i);
                packets.add(ClientboundEntityPositionSyncPacket.of(d));
                var dirty = d.getEntityData().packDirty();
                if (dirty != null) packets.add(new ClientboundSetEntityDataPacket(d.getId(), dirty));
            }
            send(slash, new ClientboundBundlePacket(packets));
        }
    }
    public void contact(CombatGeometry.Point point, FighterClass kind, FighterMoves.Move move, boolean blocked) {
        if (blocked) {
            battle.level.sendParticles(ParticleTypes.ELECTRIC_SPARK, true, false, point.x(), point.y(), 1, 4, .08, .1, .02, .025);
            return;
        }
        dust(kind, point.x(), point.y(), move.damage() >= 12 ? 7 : 4, .18, 1);
        if (kind == FighterClass.STEVE && (move.damage() == 9 || move.damage() == 18)) {
            battle.level.sendParticles(ParticleTypes.CRIT, true, false, point.x(), point.y(), 1, 5, .1, .15, .02, .03);
            battle.arenaSound(SoundEvents.ANVIL_HIT, .16f, 1.7f);
        }
    }
    public void swing(Battle.Actor f) {
        var sound = switch (f.kind) {
            case ZOMBIE -> FighterMoves.isSlam(f.state.move) ? SoundEvents.ROOTED_DIRT_BREAK : SoundEvents.PLAYER_ATTACK_SWEEP;
            case SKELETON -> SoundEvents.SKELETON_STEP;
            case VILLAGER -> SoundEvents.BAMBOO_WOOD_HIT;
            default -> SoundEvents.PLAYER_ATTACK_SWEEP;
        };
        battle.arenaSound(sound, .22f, f.kind == FighterClass.ALEX ? 1.65f : f.kind == FighterClass.ZOMBIE ? .75f : 1.15f);
    }
    public void anticipation(Battle.Actor f) {
        if (f.state.chargingSpecial()) {
            int age=f.state.chargeTicks(battle.now()); double power=ChargeRules.power(f.kind,age);
            if (power>=1 && !f.state.chargeFullShown) {
                f.state.chargeFullShown=true;
                dust(f.kind,f.pose.x+f.state.attackDirection*.48,f.pose.y+1,6,.16,1);
                battle.arenaSound(SoundEvents.EXPERIENCE_ORB_PICKUP,.2f,f.kind==FighterClass.ZOMBIE ? .7f : 1.5f);
            }
        }
        if (f.state.impactAt > battle.now() && f.state.move != null && f.state.move.technique() == FighterMoves.Technique.BITE)
            dust(FighterClass.ZOMBIE,f.pose.x+f.state.attackDirection*.6,f.pose.y+1,2,.12,1);
        if (f.state.armored(battle.now(), f.grounded) && battle.now() % 2 == 0)
            dust(FighterClass.ZOMBIE, f.pose.x, f.pose.y + .7, 3, .35, .7f);
    }
    public void confirmed(Battle.Actor f) {
        dust(f.kind, f.pose.x + f.state.attackDirection * .45, f.pose.y + 1.1, 4, .1, .8f);
        battle.arenaSound(SoundEvents.EXPERIENCE_ORB_PICKUP, .13f, f.kind == FighterClass.ALEX ? 1.8f : 1.3f);
    }
    public void armor(Battle.Actor f) {
        battle.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COARSE_DIRT.defaultBlockState()),
                true, false, f.pose.x, f.pose.y + .8, .8, 10, .35, .4, .05, .08);
    }
    public void bellPulse(Vec3 p, double radius, boolean bright) {
        int count = bright ? 24 : 16;
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            dust(FighterClass.VILLAGER, p.x + Math.cos(angle) * radius, p.y + Math.sin(angle) * radius, 1, 0, bright ? 1 : .55f);
        }
    }
    public void objectRing(Vec3 p, double radius, int color) {
        for(int i=0;i<16;i++) {
            double a = Math.PI*2*i/16;
            battle.level.sendParticles(new DustParticleOptions(color,.75f),true,false,p.x+Math.cos(a)*radius,p.y+Math.sin(a)*radius,.9,1,0,0,0,0);
        }
    }
    public void departure(Battle.Actor f) {
        if (f.kind == FighterClass.VILLAGER) { battle.particles(f, ParticleTypes.FIREWORK, 10); return; }
        if (f.kind == FighterClass.ZOMBIE) armor(f);
        dust(f.kind, f.pose.x, f.pose.y + .15, 6, .3, .9f);
    }
    private void dust(FighterClass kind, double x, double y, int count, double spread, float size) {
        battle.level.sendParticles(new DustParticleOptions(kind.accent & 0xffffff, size),
                true, false, x, y, .9, count, spread, spread, .02, 0);
    }
    public void remove(Battle.Actor f) {
        charges.remove(f);
        slashes.removeIf(s -> { if (s.actor != f) return false; discard(s); return true; });
    }
    private void send(Slash slash, Packet<? super ClientGamePacketListener> packet) {
        for (var id : slash.audience) { var view = battle.game.viewers.get(id); if (view != null) view.player().connection.send(packet); }
    }
    private void discard(Slash slash) { send(slash, new ClientboundRemoveEntitiesPacket(slash.pieces.stream().mapToInt(Entity::getId).toArray())); }
    public void close() { charges.close(); slashes.forEach(this::discard); slashes.clear(); }
}
