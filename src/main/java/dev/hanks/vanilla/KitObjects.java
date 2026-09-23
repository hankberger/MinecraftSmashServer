package dev.hanks.vanilla;

import java.util.*;
import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.*;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;
import static dev.hanks.vanilla.FighterMoves.Technique.*;

/** Bounded, server-owned kit objects. Terrain is never edited by combat. */
public final class KitObjects {
    public static final class Prop {
        public final Battle.Actor caster;
        public Battle.Actor owner;
        public final FighterMoves.Move move;
        public final Entity entity;
        public Vec3 pos, velocity;
        public final int born, lifetime, direction;
        public final long attack;
        public long lastSwing = -1;
        public UUID lastBatter;
        public boolean armed;
        public final Set<UUID> hit = new HashSet<>();
        Prop(Battle.Actor owner, FighterMoves.Move move, Entity entity, Vec3 pos, Vec3 velocity, int born, int lifetime) {
            this.caster = this.owner = owner; this.move = move; this.entity = entity; this.pos = pos; this.velocity = velocity;
            this.born = born; this.lifetime = lifetime; direction = owner.state.attackDirection; attack = owner.state.startedAt;
        }
        public AABB box() {
            double r = move.technique() == SAPLING ? .55 : .3;
            return new AABB(pos.x-r,pos.y-r,.2,pos.x+r,pos.y+r,.8);
        }
    }
    private final Battle battle;
    public final List<Prop> props = new ArrayList<>();
    KitObjects(Battle battle) { this.battle = battle; }
    public boolean owns(Entity e) { return props.stream().anyMatch(p -> p.entity == e); }
    public long count(Battle.Actor f, FighterMoves.Technique type) { return props.stream().filter(p -> p.caster == f && p.move.technique() == type).count(); }
    public boolean available(Battle.Actor f, FighterMoves.Move move) {
        return switch (move.technique()) {
            case TNT, ANVIL, SAPLING, GOLEM, FISSURE -> count(f,move.technique()) == 0;
            case PARCEL, POT -> count(f,move.technique()) < 2;
            default -> true;
        };
    }
    private Display.BlockDisplay block(BlockState state, float scale) {
        var d = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY,battle.level); d.setBlockState(state);
        d.setTransformation(new Transformation(new Vector3f(-scale/2,-scale/2,-scale/2),null,new Vector3f(scale),null));
        return d;
    }
    private Display.ItemDisplay item(Item item) {
        var d = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY,battle.level);
        d.setItemStack(new ItemStack(item)); d.setItemTransform(ItemDisplayContext.FIXED);
        d.setTransformation(new Transformation(null,null,new Vector3f(.7f),null)); return d;
    }
    private void add(Prop p) {
        p.entity.setNoGravity(true); p.entity.setInvulnerable(true); p.entity.setSilent(true); p.entity.setPos(p.pos);
        if (p.entity instanceof Display d) { d.setBrightnessOverride(new Brightness(15,15)); d.setViewRange(3); d.setWidth(4); d.setHeight(4); d.setPosRotInterpolationDuration(1); }
        battle.level.addFreshEntity(p.entity); p.entity.addTag(VanillaSmash.TEMP); props.add(p);
    }
    public void startGolem(Battle.Actor f) {
        var golem = new IronGolem(EntityTypes.IRON_GOLEM,battle.level); golem.setNoAi(true); golem.setPersistenceRequired();
        golem.getAttribute(Attributes.SCALE).setBaseValue(.70);
        golem.setYRot(f.state.attackDirection > 0 ? -90 : 90); golem.setYHeadRot(golem.getYRot()); golem.yBodyRot = golem.getYRot();
        add(new Prop(f,f.state.move,golem,new Vec3(f.pose.x+f.state.attackDirection*2.2,f.pose.y,.5),Vec3.ZERO,battle.now(),17));
    }
    public void spawn(Battle.Actor f) {
        var move = f.state.move; var type = move.technique(); int dir = f.state.attackDirection;
        Vec3 pos = new Vec3(f.pose.x+dir*.6,f.pose.y+1,.5), v = Vec3.ZERO;
        Entity entity; int life;
        switch (type) {
            case TNT -> { entity = block(Blocks.TNT.defaultBlockState(),.6f); v = new Vec3(dir*.46,.30,0); life = 30; }
            case ANVIL -> { entity = block(Blocks.ANVIL.defaultBlockState(),.70f); pos = new Vec3(f.pose.x,f.pose.y-.35,.5); v = new Vec3(f.vx*.45,-.55,0); life = 30; }
            case PARCEL -> { entity = item(Items.EMERALD); pos = new Vec3(f.pose.x+dir*.6,f.pose.y+.8,.5); v = new Vec3(dir*.62,.05,0); life = 20; }
            case POT -> { entity = item(Items.FLOWER_POT); pos = new Vec3(f.pose.x,f.pose.y-.3,.5); v = new Vec3(f.vx*.25,-.35,0); life = 28; }
            case SAPLING -> { entity = block(Blocks.OAK_SAPLING.defaultBlockState(),1); pos = new Vec3(f.pose.x+dir*.9,f.pose.y+.5,.5); life = 120; }
            case FISSURE -> { entity = block(Blocks.ROOTED_DIRT.defaultBlockState(),.55f); pos = new Vec3(f.pose.x+dir*.6,f.pose.y+.20,.5); v = new Vec3(dir*.55,0,0); life = 12; }
            default -> { return; }
        }
        if (type == SAPLING && battle.level.getBlockState(BlockPos.containing(pos.x,pos.y-.6,.5)).isAir()) return;
        add(new Prop(f,move,entity,pos,v,battle.now(),life));
        battle.arenaSound(type == TNT ? SoundEvents.TNT_PRIMED : type == SAPLING ? SoundEvents.GRASS_PLACE : SoundEvents.PLAYER_ATTACK_SWEEP,.35f,1.1f);
    }
    private void discard(Prop p) { p.entity.discard(); props.remove(p); }
    public void remove(Battle.Actor f) { for (var p : List.copyOf(props)) if (p.caster == f || p.owner == f) discard(p); }
    public void clear() { props.forEach(p -> p.entity.discard()); props.clear(); }
    public void strike(Battle.Actor f, CombatGeometry.Shape area) {
        for (var p : List.copyOf(props)) {
            var box = p.box();
            if (area.contact(new CombatGeometry.Box(box.minX,box.minY,box.maxX,box.maxY)) == null) continue;
            if (p.move.technique() == SAPLING && p.owner != f) { debris(p,Blocks.OAK_LEAVES.defaultBlockState()); discard(p); }
            if (p.move.technique() == TNT && f.state.move.kind() == AttackKind.LIGHT
                    && !(Objects.equals(p.lastBatter,f.id) && p.lastSwing == f.state.startedAt)) {
                p.lastBatter = f.id; p.lastSwing = f.state.startedAt; p.owner = f;
                var toss = BellRules.bat(f.state.move.aim(),f.state.attackDirection);
                p.velocity = new Vec3(toss.x(),toss.y(),0);
                battle.effects.impacts.object(f,p.pos);
            }
        }
    }
    /** Arrow interception participates in the same nearest-hit ordering as terrain and fighters. */
    public Prop obstacle(Battle.Actor shooter, Vec3 from, Vec3 to, double closerThan) {
        Prop nearest = null;
        for (var p : props) if (p.move.technique() == SAPLING && p.owner != shooter) {
            double distance = distance(p.box(),from,to);
            if (distance < closerThan) { closerThan = distance; nearest = p; }
        }
        return nearest;
    }
    public void breakPlant(Prop p) { debris(p,Blocks.OAK_LEAVES.defaultBlockState()); discard(p); }
    public void bloom(Battle.Actor owner, Vec3 center) {
        for (var p : props) if (p.owner == owner && p.move.technique() == SAPLING && p.pos.distanceToSqr(center) <= 9) grow(p);
    }
    private void grow(Prop p) {
        if (!p.armed) { p.armed = true; ((Display.BlockDisplay)p.entity).setBlockState(Blocks.OAK_LEAVES.defaultBlockState()); debris(p,Blocks.OAK_LEAVES.defaultBlockState()); }
    }
    public static double distance(AABB box, Vec3 from, Vec3 to) {
        return box.contains(from) ? 0 : box.clip(from,to).map(p -> p.distanceToSqr(from)).orElse(Double.POSITIVE_INFINITY);
    }
    private void debris(Prop p, BlockState block) {
        battle.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,block),true,false,p.pos.x,p.pos.y,.8,8,.25,.25,.03,.04);
    }
    private boolean clearLine(Vec3 from, Battle.Actor target) {
        return battle.objects.terrain(from,new Vec3(target.pose.x,target.pose.y+.8,.5)).getType() == HitResult.Type.MISS;
    }
    private void explode(Prop p) {
        battle.effects.objectRing(p.pos,2.25,0xffa33b);
        battle.level.sendParticles(ParticleTypes.EXPLOSION,true,false,p.pos.x,p.pos.y,.8,1,0,0,0,0);
        battle.arenaSound(SoundEvents.GENERIC_EXPLODE.value(),.5f,1.6f);
        battle.companions.blast(p.owner,p.pos,2.25,p.move);
        for (var target : battle.actors.values()) if (target != p.owner && battle.game.fighting(target)) {
            var b = target.box(); var near = new Vec3(Math.clamp(p.pos.x,b.minX,b.maxX),Math.clamp(p.pos.y,b.minY,b.maxY),.5);
            if (near.distanceToSqr(p.pos) <= 2.25*2.25 && clearLine(p.pos,target)) battle.hit(p.owner,target,target.pose.x < p.pos.x ? -1 : 1,p.move,new CombatGeometry.Point(near.x,near.y));
        }
        discard(p);
    }
    public void tick() {
        for (var p : List.copyOf(props)) {
            if (!battle.game.fighting(p.owner) || !battle.game.fighting(p.caster) || p.entity.isRemoved()) { discard(p); continue; }
            int age = battle.now()-p.born; var type = p.move.technique();
            if (age >= p.lifetime) { if (type == TNT) explode(p); else discard(p); continue; }
            if (type == GOLEM) {
                if (!p.armed && (p.caster.state.startedAt != p.attack || battle.now() < p.caster.state.stunUntil
                        || age < p.move.startup() && p.caster.state.impactAt < 0)) { discard(p); continue; }
                if (age == p.move.startup()) {
                    p.armed = true;
                    battle.level.broadcastEntityEvent(p.entity,(byte)4); battle.arenaSound(SoundEvents.IRON_GOLEM_ATTACK,.6f,.9f);
                }
                if (age >= p.move.startup() && age < p.move.startup()+3)
                    battle.companions.strike(p.owner,p.move,new CombatGeometry.Shape(p.pos.x,p.pos.y+1.1,1.05,1.05,0,Math.PI*2,
                            new CombatGeometry.Box(p.pos.x-1.05,p.pos.y+.05,p.pos.x+1.05,p.pos.y+2.15)));
                if (age >= p.move.startup() && age < p.move.startup()+3) for (var target : battle.actors.values()) {
                    if (target == p.owner || !battle.game.fighting(target) || !target.state.hittable(battle.now()) || p.hit.contains(target.id)) continue;
                    var area = new CombatGeometry.Shape(p.pos.x,p.pos.y+1.1,1.05,1.05,0,Math.PI*2,new CombatGeometry.Box(p.pos.x-1.05,p.pos.y+.05,p.pos.x+1.05,p.pos.y+2.15));
                    var b = target.box(); var contact = area.contact(new CombatGeometry.Box(b.minX,b.minY,b.maxX,b.maxY));
                    if (contact != null && clearLine(p.pos.add(0,1,0),target)) { p.hit.add(target.id); battle.hit(p.owner,target,p.direction,p.move,contact); }
                }
                continue;
            }
            if (type == SAPLING) {
                if (age >= 14) grow(p);
                if (p.armed) for (var target : battle.actors.values()) if (target != p.owner && battle.game.fighting(target) && target.state.hittable(battle.now()) && p.box().inflate(.15,.35,0).intersects(target.box())) {
                    battle.hit(p.owner,target,target.pose.x < p.pos.x ? -1 : 1,p.move); debris(p,Blocks.OAK_LEAVES.defaultBlockState()); discard(p); break;
                }
                if (p.armed && props.contains(p)) {
                    var buddy = battle.companions.contact(p.owner,p.pos,p.pos,.65,Double.POSITIVE_INFINITY);
                    if (buddy != null) { battle.companions.hurt(buddy.buddy(),p.owner,p.move); debris(p,Blocks.OAK_LEAVES.defaultBlockState()); discard(p); }
                }
                continue;
            }
            Vec3 from = p.pos, to = from.add(p.velocity); var wall = battle.objects.terrain(from,to);
            double nearest = wall.getType() == HitResult.Type.MISS ? Double.POSITIVE_INFINITY : wall.getLocation().distanceToSqr(from);
            if (type == FISSURE && battle.level.getBlockState(BlockPos.containing(to.x,to.y-.35,.5)).isAir()) { discard(p); continue; }
            if (type == TNT) {
                var contact = battle.objects.playerImpact(p.owner,from,to,.30,nearest);
                var buddy = battle.companions.contact(p.owner,from,to,.30,contact == null ? nearest : contact.distance());
                if (buddy != null) { p.pos = buddy.point(); explode(p); continue; }
                if (contact != null) { p.pos = contact.point(); explode(p); continue; }
                if (Double.isFinite(nearest)) {
                    var n = wall.getDirection(); p.pos = wall.getLocation().add(n.getStepX()*.31,n.getStepY()*.31,0);
                    p.velocity = new Vec3(n.getAxis() == Direction.Axis.X ? -p.velocity.x*.5 : p.velocity.x*.8,
                            n.getAxis() == Direction.Axis.Y ? Math.abs(p.velocity.y)*.45 : p.velocity.y,0);
                } else { p.pos = to; p.velocity = p.velocity.add(0,-.055,0); }
                if (age >= 22) { ((Display.BlockDisplay)p.entity).setBlockState((age%2 == 0 ? Blocks.CONCRETE.white() : Blocks.TNT).defaultBlockState()); if(age%3 == 0) battle.effects.objectRing(p.pos,2.25,0xffa33b); }
                battle.level.sendParticles(ParticleTypes.SMOKE,true,false,p.pos.x,p.pos.y+.4,.8,1,.02,.02,0,0);
            } else {
                Battle.Actor victim = null; BattleObjects.Bell bell = null;
                for (var target : battle.actors.values()) if (target != p.owner && battle.game.fighting(target) && !p.hit.contains(target.id) && target.state.hittable(battle.now())) {
                    double distance = distance(target.box().inflate(.18),from,to);
                    if (distance < nearest) { nearest = distance; victim = target; }
                }
                for (var b : battle.objects.bells.values()) {
                    if (type != PARCEL || b.owner != p.owner) continue;
                    double distance = distance(new AABB(b.pos.x-.45,b.pos.y-.45,.1,b.pos.x+.45,b.pos.y+.45,.9),from,to);
                    if (distance < nearest) { nearest = distance; bell = b; victim = null; }
                }
                var buddy = battle.companions.contact(p.owner,from,to,.18,nearest);
                var plant = obstacle(p.owner,from,to,buddy == null ? nearest : buddy.distance());
                if (plant != null) { breakPlant(plant); discard(p); continue; }
                if (buddy != null) { battle.companions.hurt(buddy.buddy(),p.owner,p.move); if (type != FISSURE) { discard(p); continue; } }
                if (bell != null) { battle.objects.batBell(bell,AttackDirection.FORWARD,p.direction); discard(p); continue; }
                if (victim != null) { p.hit.add(victim.id); battle.hit(p.owner,victim,p.velocity.x < 0 ? -1 : p.velocity.x > 0 ? 1 : p.direction,p.move); if (type != FISSURE) { discard(p); continue; } }
                if (wall.getType() != HitResult.Type.MISS) { if(type == ANVIL) { debris(p,Blocks.ANVIL.defaultBlockState()); battle.arenaSound(SoundEvents.ANVIL_LAND,.3f,1.4f); } discard(p); continue; }
                p.pos = to;
                if (type != FISSURE) p.velocity = p.velocity.add(0,type == ANVIL ? -.12 : type == POT ? -.075 : -.02,0);
                else if (age%2 == 0) debris(p,Blocks.ROOTED_DIRT.defaultBlockState());
            }
            if (battle.objects.outside(p.pos)) { discard(p); continue; }
            p.entity.setPos(p.pos); p.entity.needsSync = true;
        }
    }
}
