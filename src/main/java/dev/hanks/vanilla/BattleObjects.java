package dev.hanks.vanilla;

import java.util.*;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;

/** Normal arrow entity types and item displays; only the server decides combat hits. */
public final class BattleObjects {
    private final Battle battle;
    public final Map<UUID, Shot> arrows = new LinkedHashMap<>();
    public final Map<UUID, Bell> bells = new LinkedHashMap<>();
    public record Shot(Battle.Actor owner, NativeArrow entity, int charge, int born) {}
    public static final class Bell {
        public final Battle.Actor owner;
        public final Display.ItemDisplay entity;
        public Vec3 pos, velocity;
        public int armedAt = -1, ringAt = -1;
        public long lastBattedAt = Long.MIN_VALUE;
        Bell(Battle.Actor owner, Display.ItemDisplay entity, Vec3 position) {
            this.owner = owner; this.entity = entity; pos = position; velocity = new Vec3(owner.state.attackDirection * .27, .22, 0);
        }
    }
    public static final class NativeArrow extends Arrow {
        NativeArrow(Level level) { super(EntityTypes.ARROW, level); }
        @Override public void tick() { baseTick(); } // Flight and swept collision are advanced below, once per server tick.
    }
    BattleObjects(Battle battle) { this.battle = battle; }
    public boolean hasArrow(Battle.Actor f) { return arrows.containsKey(f.id); }
    public boolean owns(Entity entity) { return arrows.values().stream().anyMatch(s -> s.entity == entity) || bells.values().stream().anyMatch(b -> b.entity == entity); }
    public boolean hasBell(Battle.Actor f) { return bells.containsKey(f.id); }
    public boolean bellArmed(Battle.Actor f) { var b = bells.get(f.id); return b != null && b.armedAt >= 0 && battle.now() >= b.armedAt; }
    public void arrow(Battle.Actor f, int charge) {
        if (hasArrow(f) || !BowRules.canFire(charge)) return;
        var arrow = new NativeArrow(battle.level); arrow.setOwner(f.body);
        arrow.setCritArrow(charge >= BowRules.FULL_DRAW_TICKS);
        arrow.snapTo(f.pose.x + f.state.attackDirection * .5, f.pose.y + 1.45, .5, f.state.attackDirection * 90, 0);
        arrow.setDeltaMovement(f.state.attackDirection * BowRules.speed(charge) + f.vx, f.grounded ? 0 : f.vy, 0);
        arrows.put(f.id, new Shot(f, arrow, charge, battle.now()));
        battle.level.addFreshEntity(arrow); arrow.addTag(VanillaSmash.TEMP);
        battle.arenaSound(SoundEvents.ARROW_SHOOT, .4f, charge >= BowRules.FULL_DRAW_TICKS ? .85f : 1.25f);
    }
    public void bell(Battle.Actor f) {
        if (hasBell(f)) return;
        var display = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, battle.level);
        display.setItemStack(new ItemStack(Items.BELL)); display.setItemTransform(ItemDisplayContext.FIXED);
        display.setNoGravity(true); display.setViewRange(3); display.setWidth(1); display.setHeight(1); display.setPosRotInterpolationDuration(1);
        var position = new Vec3(f.pose.x + f.state.attackDirection * .5, f.pose.y + 1, .5); display.setPos(position);
        bells.put(f.id, new Bell(f, display, position));
        battle.level.addFreshEntity(display); display.addTag(VanillaSmash.TEMP);
    }
    public void ring(Battle.Actor f) { if (bellArmed(f)) bells.get(f.id).ringAt = battle.now(); }
    private void removeBell(UUID id) {
        var bell = bells.remove(id);
        if (bell != null) { bell.entity.discard(); bell.owner.state.bellReadyAt = battle.now() + 16; }
    }
    public void remove(Battle.Actor f) { var shot = arrows.remove(f.id); if (shot != null) shot.entity.discard(); removeBell(f.id); }
    public void clear() { for (var shot : arrows.values()) shot.entity.discard(); arrows.clear(); for (var bell : bells.values()) bell.entity.discard(); bells.clear(); }
    public void strikeBells(Battle.Actor attacker, CombatGeometry.Shape area) {
        for (var bell : List.copyOf(bells.values())) {
            if (area.contact(new CombatGeometry.Box(bell.pos.x - .4, bell.pos.y - .4, bell.pos.x + .4, bell.pos.y + .4)) == null) continue;
            if (bell.owner != attacker) { removeBell(bell.owner.id); continue; }
            if (attacker.state.move.kind() != AttackKind.LIGHT || bell.lastBattedAt == attacker.state.startedAt) continue;
            var toss = BellRules.bat(attacker.state.move.aim(), attacker.state.attackDirection);
            bell.velocity = new Vec3(toss.x(), toss.y(), 0);
            bell.lastBattedAt = attacker.state.startedAt; bell.armedAt = bell.ringAt = -1;
            battle.effects.bellPulse(bell.pos, .4, true);
            battle.arenaSound(SoundEvents.BELL_BLOCK, .25f, 1.7f);
        }
    }
    private BlockHitResult terrain(Vec3 from, Vec3 to) {
        return battle.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
    }
    private static AABB box(Vec3 p, double r) { return new AABB(p.x - r, p.y - r, p.z - r, p.x + r, p.y + r, p.z + r); }
    private static boolean outside(Vec3 p) { return p.x < -29 || p.x > 30 || p.y < 66 || p.y > 112 || !Double.isFinite(p.x + p.y + p.z); }
    public void tick() {
        for (var shot : List.copyOf(arrows.values())) {
            if (!battle.game.fighting(shot.owner) || shot.entity.isRemoved()) { remove(shot.owner); continue; }
            var from = shot.entity.position(); var velocity = shot.entity.getDeltaMovement(); var to = from.add(velocity);
            var wall = terrain(from, to);
            double nearest = wall.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : wall.getLocation().distanceToSqr(from);
            Battle.Actor victim = null; Bell destroyed = null;
            for (var target : battle.actors.values()) if (target != shot.owner && battle.game.fighting(target)) {
                var bounds = target.box().inflate(.12);
                double distance = bounds.contains(from) ? 0 : bounds.clip(from, to).map(p -> p.distanceToSqr(from)).orElse(Double.POSITIVE_INFINITY);
                if (distance < nearest) { nearest = distance; victim = target; }
            }
            for (var bell : bells.values()) if (bell.owner != shot.owner) {
                var bounds = box(bell.pos, .4);
                double distance = bounds.contains(from) ? 0 : bounds.clip(from, to).map(p -> p.distanceToSqr(from)).orElse(Double.POSITIVE_INFINITY);
                if (distance < nearest) { nearest = distance; victim = null; destroyed = bell; }
            }
            if (Double.isFinite(nearest)) {
                if (victim != null) battle.hit(shot.owner, victim, velocity.x < 0 ? -1 : 1, FighterMoves.arrow(shot.charge));
                if (destroyed != null) removeBell(destroyed.owner.id);
                shot.entity.discard(); arrows.remove(shot.owner.id); continue;
            }
            shot.entity.setYRot((float)Math.toDegrees(Math.atan2(velocity.x, velocity.z)));
            shot.entity.setXRot((float)Math.toDegrees(Math.atan2(velocity.y, velocity.horizontalDistance())));
            shot.entity.setPos(to); shot.entity.setDeltaMovement(velocity.scale(BowRules.DRAG).add(0, -BowRules.GRAVITY, 0));
            shot.entity.needsSync = true;
            if (shot.entity.isCritArrow() && battle.now() % 2 == 0)
                battle.level.sendParticles(ParticleTypes.CRIT, true, false, from.x, from.y, .8, 1, 0, 0, 0, 0);
            if (outside(to) || battle.now() - shot.born > 120) { shot.entity.discard(); arrows.remove(shot.owner.id); }
        }
        for (var bell : List.copyOf(bells.values())) {
            if (!battle.game.fighting(bell.owner) || bell.entity.isRemoved()) { removeBell(bell.owner.id); continue; }
            if (bell.armedAt < 0) {
                var to = bell.pos.add(bell.velocity); var wall = terrain(bell.pos, to);
                if (wall.getType() == HitResult.Type.MISS) { bell.pos = to; bell.velocity = bell.velocity.add(0, -.04, 0); }
                else if (wall.getDirection() == Direction.UP && bell.velocity.y < 0) {
                    bell.pos = wall.getLocation().add(0, .3, 0); bell.velocity = Vec3.ZERO;
                    bell.armedAt = battle.now() + 8; bell.ringAt = bell.armedAt + 60;
                } else {
                    var n = wall.getDirection(); bell.pos = wall.getLocation().add(n.getStepX() * .025, n.getStepY() * .025, n.getStepZ() * .025);
                    bell.velocity = new Vec3(n.getAxis() == Direction.Axis.X ? -bell.velocity.x * .25 : bell.velocity.x,
                            n.getAxis() == Direction.Axis.Y ? -Math.abs(bell.velocity.y) * .25 : bell.velocity.y, 0).add(0, -.04, 0);
                }
                // No short airborne lifetime: land, leave the arena, or finish the owner's session.
                if (outside(bell.pos)) { removeBell(bell.owner.id); continue; }
                bell.entity.setPos(bell.pos);
            } else if (battle.now() >= bell.ringAt) {
                battle.arenaSound(SoundEvents.BELL_BLOCK, .5f, 1.2f);
                for (var target : battle.actors.values()) if (target != bell.owner && battle.game.fighting(target)) {
                    var bounds = target.box();
                    var nearest = new Vec3(Math.clamp(bell.pos.x, bounds.minX, bounds.maxX), Math.clamp(bell.pos.y, bounds.minY, bounds.maxY), .5);
                    if (nearest.distanceToSqr(bell.pos) < BellRules.RADIUS * BellRules.RADIUS && terrain(bell.pos, new Vec3(target.pose.x, target.pose.y + .75, .5)).getType() == HitResult.Type.MISS)
                        battle.hit(bell.owner, target, target.pose.x < bell.pos.x ? -1 : 1, FighterMoves.bell(), new CombatGeometry.Point(nearest.x, nearest.y));
                }
                battle.effects.bellPulse(bell.pos, BellRules.RADIUS, true);
                removeBell(bell.owner.id);
            } else if (battle.now() >= bell.armedAt && battle.now() % 8 == 0)
                battle.effects.bellPulse(bell.pos, BellRules.RADIUS, bell.ringAt - battle.now() < 12);
        }
    }
}
