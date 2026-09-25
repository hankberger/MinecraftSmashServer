package dev.hanks.vanilla;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;

/** One vulnerable, server-owned partner per Zombie. It only attacks in response to its owner. */
public final class ZombieCompanions {
    public static final int HEALTH = 18, RETURN_TICKS = 100, ECHO_DELAY = 10;
    public static final class Buddy {
        public final Battle.Actor owner;
        public Zombie body;
        public final CombatPose pose = new CombatPose(0,81);
        public double x, y, vx, vy;
        public boolean grounded;
        public int health = HEALTH, returnAt, immuneUntil, stunnedUntil, jumpAt, boostUntil;
        public int direction = 1, attackAt = -1, activeUntil;
        public FighterMoves.Move move;
        private long ownerHitAt;
        private final Set<UUID> hit = new HashSet<>();
        Buddy(Battle.Actor owner) { this.owner = owner; }
        public AABB box() { return new AABB(pose.x-.20,pose.y,.25,pose.x+.20,pose.y+.95,.75); }
    }
    public record Contact(Buddy buddy, Vec3 point, double distance) {}
    private final Battle battle;
    public final Map<UUID,Buddy> buddies = new LinkedHashMap<>();
    ZombieCompanions(Battle battle) { this.battle = battle; }
    public void add(Battle.Actor f) {
        if (f.kind != FighterClass.ZOMBIE || f.owner == null) return;
        var b = new Buddy(f); buddies.put(f.id,b); spawn(b);
    }
    public boolean owns(Entity entity) { return buddies.values().stream().anyMatch(b -> b.body == entity); }
    public boolean available(Battle.Actor f) {
        var b = buddies.get(f.id);
        return b != null && b.body != null && battle.now() >= b.stunnedUntil && b.attackAt < 0 && battle.now() >= b.activeUntil;
    }
    private void spawn(Buddy b) {
        var body = (Zombie)FighterModels.create(battle.level,FighterClass.ZOMBIE,b.owner.skin); body.setBaby(true);
        body.setNoAi(true); body.setPersistenceRequired(); body.setNoGravity(true); body.setInvulnerable(true); body.setSilent(true);
        body.setCustomName(Component.literal("P"+b.owner.slot+" · Buddy").withColor(b.owner.color())); body.setCustomNameVisible(true);
        b.body = body; b.health = HEALTH; b.x = b.owner.x-b.owner.facing*.85; b.y = b.owner.y;
        b.vx = b.vy = 0; b.grounded = b.owner.grounded; b.attackAt = -1; b.activeUntil = 0;
        b.pose.reset(b.x,b.y);
        b.immuneUntil = battle.now()+10; b.stunnedUntil = 0; b.ownerHitAt = b.owner.state.lastHitAt;
        sync(b); battle.level.addFreshEntity(body); body.addTag(VanillaSmash.TEMP);
    }
    private void retire(Buddy b) {
        if (b.body != null) {
            battle.level.sendParticles(ParticleTypes.POOF,true,false,b.x,b.y+.4,.6,8,.2,.3,.02,.015);
            b.body.discard(); b.body = null;
        }
        b.returnAt = battle.now()+RETURN_TICKS; b.attackAt = -1; b.activeUntil = 0;
    }
    public void remove(Battle.Actor f) { var b = buddies.remove(f.id); if (b != null && b.body != null) b.body.discard(); }
    public void clear() { for (var b : buddies.values()) if (b.body != null) b.body.discard(); buddies.clear(); }
    public void echo(Battle.Actor f, FighterMoves.Move source) {
        if (!available(f) || source.kind() == AttackKind.RECOVERY) return;
        var b = buddies.get(f.id); boolean heavy = source.kind() == AttackKind.HEAVY;
        b.move = new FighterMoves.Move(30,heavy ? "Little Trouble" : "Buddy Claws",source.kind(),source.aim(),source.aerial(),
                heavy ? 6+(int)Math.round(3*ChargeRules.power(f.kind,f.state.releasedCharge)) : 3,0,0,
                source.aim() == AttackDirection.NEUTRAL ? 1.1 : source.aim() == AttackDirection.FORWARD ? 1.65 : 1.2,
                heavy ? 1.10 : .52, source.vertical(),-3,heavy ? 18 : 8).style(FighterClass.ZOMBIE,FighterMoves.Technique.COMPANION);
        b.direction = f.state.attackDirection; b.attackAt = battle.now()+ECHO_DELAY; b.hit.clear(); b.ownerHitAt = f.state.lastHitAt;
    }
    public void toss(Battle.Actor f) {
        if (!available(f)) return;
        var b = buddies.get(f.id); b.move = f.state.move; b.direction = f.state.attackDirection;
        b.attackAt = battle.now(); b.hit.clear(); b.ownerHitAt = f.state.lastHitAt;
        b.vx = b.direction*.70; b.vy = .43; b.grounded = false;
        battle.arenaSound(SoundEvents.ZOMBIE_AMBIENT,.3f,1.9f);
    }
    public void recover(Battle.Actor f) {
        var b = buddies.get(f.id); if (b == null || b.body == null) return;
        b.attackAt = -1; b.activeUntil = 0; b.boostUntil = battle.now()+14;
        b.x = f.x-f.facing*.6; b.y = f.y+.25; b.vx = f.vx; b.vy = f.vy;
        b.pose.reset(b.x,b.y);
    }
    private boolean exposed(Buddy b, Battle.Actor attacker) {
        return b.owner != attacker && b.body != null && battle.game.fighting(b.owner)
                && b.owner.state.hittable(battle.now()) && battle.now() >= b.immuneUntil;
    }
    public void strike(Battle.Actor attacker, FighterMoves.Move move, CombatGeometry.Shape area) {
        for (var b : buddies.values()) if (exposed(b,attacker)) {
            var box = b.box();
            if (area.contact(new CombatGeometry.Box(box.minX,box.minY,box.maxX,box.maxY)) != null
                    && battle.objects.terrain(new Vec3(area.x(),area.y(),.5),new Vec3(b.pose.x,b.pose.y+.4,.5)).getType()==HitResult.Type.MISS)
                hurt(b,attacker,move);
        }
    }
    public Contact contact(Battle.Actor attacker, Vec3 from, Vec3 to, double radius, double closerThan) {
        Contact contact = null;
        for (var b : buddies.values()) if (exposed(b,attacker)) {
            var box = b.box().inflate(radius); var point = box.contains(from) ? from : box.clip(from,to).orElse(null);
            if (point != null && point.distanceToSqr(from) < closerThan) {
                closerThan = point.distanceToSqr(from); contact = new Contact(b,point,closerThan);
            }
        }
        return contact;
    }
    public void blast(Battle.Actor attacker, Vec3 point, double radius, FighterMoves.Move move) {
        for (var b : buddies.values()) if (exposed(b,attacker)) {
            var target = new Vec3(b.pose.x,b.pose.y+.4,.5);
            if (target.distanceToSqr(point) <= radius*radius && battle.objects.terrain(point,target).getType() == HitResult.Type.MISS) hurt(b,attacker,move);
        }
    }
    public void hurt(Buddy b, Battle.Actor attacker, FighterMoves.Move move) {
        if (!exposed(b,attacker)) return;
        b.immuneUntil = battle.now()+10;
        if (b.owner.state.blocking(battle.now()) && Math.abs(b.x-b.owner.x) < 2) {
            battle.arenaSound(SoundEvents.SHIELD_BLOCK.value(),.3f,1.5f); return;
        }
        b.health -= move.damage(); b.attackAt = -1; b.activeUntil = 0; b.boostUntil = 0;
        if (b.health <= 0) { retire(b); return; }
        b.stunnedUntil = battle.now()+14; b.vx = (b.x < attacker.x ? -1 : 1)*.45; b.vy = .35; b.grounded = false;
        battle.level.getChunkSource().sendToTrackingPlayers(b.body,new ClientboundHurtAnimationPacket(b.body));
        battle.arenaSound(SoundEvents.ZOMBIE_HURT,.35f,1.7f);
    }
    public void tick() {
        for (var owner : battle.actors.values()) if (!owner.eliminated && owner.owner != null && owner.kind == FighterClass.ZOMBIE
                && !buddies.containsKey(owner.id) && !owner.state.floating(battle.now())) add(owner);
        for (var b : buddies.values()) {
            var f = b.owner; int t = battle.now();
            if (b.body == null) {
                if (t >= b.returnAt && battle.game.fighting(f) && f.grounded && !f.state.floating(t)) {
                    spawn(b); battle.effects.objectRing(new Vec3(b.x,b.y+.2,.5),.6,0x99da70);
                }
                continue;
            }
            if (!battle.game.fighting(f)) { sync(b); continue; }
            if (f.state.lastHitAt != b.ownerHitAt || t < f.state.stunUntil) { b.attackAt = -1; b.activeUntil = 0; b.ownerHitAt = f.state.lastHitAt; }
            if (f.state.paused(t)) { b.pose.advance(b.x,b.y); sync(b); continue; }
            boolean toss = b.move != null && b.move.technique() == FighterMoves.Technique.BUDDY_TOSS;
            if (b.attackAt >= 0 && t >= b.attackAt) {
                b.attackAt = -1; b.activeUntil = t+(toss ? 22 : 5); b.body.swing(InteractionHand.MAIN_HAND);
                battle.arenaSound(SoundEvents.PLAYER_ATTACK_SWEEP,.2f,1.9f);
            }
            boolean attacking = t < b.activeUntil;
            if (t < b.boostUntil) {
                b.x = f.x-f.facing*.65; b.y = f.y+.25; b.vx = f.vx; b.vy = f.vy;
            } else {
                if (t >= b.stunnedUntil) {
                    if (attacking) b.vx = b.direction*(toss ? .58 : .48);
                    else {
                        double goal = f.x+(b.attackAt >= 0 || f.state.chargingSpecial() ? f.facing*.9 : -f.facing*1.05);
                        b.vx = Math.clamp((goal-b.x)*.38,-.48,.48);
                        if (b.grounded && f.y > b.y+.8 && t >= b.jumpAt) { b.vy = MovementRules.JUMP; b.grounded = false; b.jumpAt = t+16; }
                    }
                } else b.vx *= .93;
                if (!b.grounded) b.vy = MovementRules.gravity(b.vy);
                double before = b.y; var box = new AABB(b.x-.20,b.y,.25,b.x+.20,b.y+.95,.75);
                var step = StageCollision.move(box,b.vx,b.vy,battle.level.getBlockCollisions(b.body,box.expandTowards(b.vx,b.vy,0)));
                b.x += step.x(); b.y += step.y(); b.grounded = step.landed();
                if (step.wall()) b.vx = 0;
                if (step.ceiling() || step.landed()) b.vy = 0;
                double floor = battle.stage.landingFloor(b.x,before,b.y,f.y < b.y-.5);
                if (b.vy <= 0 && before >= floor && b.y <= floor) { b.y = floor; b.vy = 0; b.grounded = true; }
                if (!b.grounded && b.vy == 0) b.vy = -MovementRules.FALL_GRAVITY;
            }
            if (battle.stage.outside(b.x,b.y,.5) || Math.abs(b.x-f.x)>24) { retire(b); continue; }
            b.pose.advance(b.x,b.y);
            if (attacking && t >= b.stunnedUntil) {
                var shape = toss ? new CombatGeometry.Shape(b.pose.x,b.pose.y+.5,.65,.6,0,Math.PI*2,new CombatGeometry.Box(b.pose.x-.65,b.pose.y-.1,b.pose.x+.65,b.pose.y+1.1))
                        : CombatGeometry.shape(b.move,b.direction,b.pose.x,b.pose.y);
                for (var target : battle.actors.values()) if (target != f && battle.game.fighting(target) && target.state.hittable(t) && !b.hit.contains(target.id)) {
                    var box = target.box(); var point = shape.contact(new CombatGeometry.Box(box.minX,box.minY,box.maxX,box.maxY));
                    if (point != null && battle.objects.terrain(new Vec3(b.pose.x,b.pose.y+.5,.5),new Vec3(point.x(),point.y(),.5)).getType() == HitResult.Type.MISS) {
                        b.hit.add(target.id); battle.hit(f,target,b.direction,b.move,point);
                        if (toss) { b.activeUntil = t; b.vx = -b.direction*.18; b.vy = .35; }
                    }
                }
                var arc = shape.arc((t%5)/4.0);
                battle.level.sendParticles(ParticleTypes.HAPPY_VILLAGER,true,false,arc.x(),arc.y(),.9,2,.03,.03,0,0);
            }
            sync(b);
        }
    }
    private void sync(Buddy b) {
        var f = b.owner; int facing = b.attackAt >= 0 || battle.now()<b.activeUntil ? b.direction : f.facing;
        b.body.setPos(b.x,b.y,.5); b.body.setYRot(facing>0 ? -90 : 90); b.body.setYHeadRot(b.body.getYRot()); b.body.yBodyRot = b.body.getYRot();
        b.body.setDeltaMovement(Vec3.ZERO); b.body.setOnGround(b.grounded); b.body.clearFire(); b.body.needsSync = true;
        b.body.setAggressive(f.state.chargingSpecial() || b.attackAt>=0 || battle.now()<b.activeUntil);
        b.body.setPose(f.previous.backward() ? Pose.CROUCHING : Pose.STANDING);
        b.body.setInvisible(RespawnRules.dim((int)(f.state.protectedUntil-battle.now())));
        boolean guard = f.state.blocking(battle.now()) && Math.abs(b.x-f.x)<2;
        if (guard && !b.body.getOffhandItem().is(Items.SHIELD)) b.body.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
        if (!guard && !b.body.getOffhandItem().isEmpty()) b.body.setItemSlot(EquipmentSlot.OFFHAND,ItemStack.EMPTY);
        if (guard) { if (!b.body.isUsingItem()) b.body.startUsingItem(InteractionHand.OFF_HAND); } else b.body.stopUsingItem();
    }
}
