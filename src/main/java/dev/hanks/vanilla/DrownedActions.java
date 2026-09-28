package dev.hanks.vanilla;

import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.*;

/** Swept harpoon collision and bounded velocity reels. No teleport, terrain edits or movement refunds. */
final class DrownedActions {
    private static final class Harpoon {
        final Battle.Actor owner;
        final FighterMoves.Move move;
        final double originX,originY;
        final int born;
        Vec3 pos,velocity;
        Harpoon(Battle.Actor f,int now){owner=f;move=f.state.move;born=now;originX=f.x;originY=f.y;
            pos=new Vec3(f.x+f.state.attackDirection*.55,f.y+1.35,.5);velocity=new Vec3(f.state.attackDirection*1.15,f.grounded?0:Math.clamp(f.vy,-.8,.8),0);}
    }
    private record Reel(Battle.Actor owner,Battle.Actor target,boolean self,int born) {}
    private final Battle battle;
    private final Map<UUID,Harpoon> harpoons=new HashMap<>();
    private final Map<UUID,Reel> reels=new HashMap<>();
    private final Map<UUID,Boolean> selfPlans=new HashMap<>();
    final DrownedVisuals visuals;
    DrownedActions(Battle battle){this.battle=battle;visuals=new DrownedVisuals(battle);}
    Battle.Actor target(Battle.Actor f) {
        var id=f.drowned.target(battle.now());var target=id==null?null:battle.actors.get(id);
        return target!=null&&battle.game.fighting(target)&&!target.eliminated?target:null;
    }
    private boolean connected(Battle.Actor f,Battle.Actor target) {
        return target!=null&&!target.state.floating(battle.now())&&!target.state.blocking(battle.now())
                && Math.hypot(target.x-f.x,target.y-f.y)<=DrownedState.BREAK_RANGE
                && battle.objects.terrain(new Vec3(f.x,f.y+1.2,.5),new Vec3(target.x,target.y+1.2,.5)).getType()==HitResult.Type.MISS;
    }
    boolean available(Battle.Actor f,AttackIntent intent) {
        if(f.kind!=FighterClass.DROWNED || intent.kind()!=AttackKind.HEAVY)return true;
        if(intent.direction()!=AttackDirection.DOWN)return f.drowned.canThrow(battle.now());
        var target=target(f);
        return connected(f,target)&&(!DrownedState.pullSelf(intent.axis(),target.x,f.x)||f.drowned.canSelfReel());
    }
    void begin(Battle.Actor f,AttackIntent intent) {
        if(f.kind!=FighterClass.DROWNED)return;
        if(f.state.move.technique()==FighterMoves.Technique.REEL) {
            var target=target(f);selfPlans.put(f.id,target!=null&&DrownedState.pullSelf(intent.axis(),target.x,f.x));
        }
        if(intent.kind()==AttackKind.RECOVERY) {
            release(f);
            f.state.motionType=11;f.state.motionX=intent.axis()*.52;f.state.motionUntil=battle.now()+7;
            battle.arenaSound(SoundEvents.TRIDENT_RIPTIDE_1.value(),.65f,1.0f);
        }
    }
    void resolve(Battle.Actor f) {
        if(f.state.move.technique()==FighterMoves.Technique.HARPOON) {
            if(!f.drowned.canThrow(battle.now()))return;
            f.drowned.launch();harpoons.put(f.id,new Harpoon(f,battle.now()));
            f.body.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            battle.arenaSound(SoundEvents.TRIDENT_THROW.value(),.65f,.85f);
        } else {
            boolean self=Boolean.TRUE.equals(selfPlans.remove(f.id));var target=target(f);
            if(!connected(f,target) || self&&!f.drowned.canSelfReel()){release(f);return;}
            if(self)f.drowned.selfReel();
            f.facing=f.state.attackDirection=target.x>=f.x?1:-1;
            f.body.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            reels.put(f.id,new Reel(f,target,self,battle.now()));f.drowned.returning();
            battle.arenaSound(SoundEvents.FISHING_BOBBER_RETRIEVE,.65f,.8f);
        }
    }
    /** Runs before ordinary movement, so both terrain and platform collision remain authoritative. */
    void beforeMovement() {
        for(var it=reels.values().iterator();it.hasNext();) {
            var reel=it.next();var f=reel.owner;var target=reel.target;
            boolean end=battle.now()-reel.born>=DrownedState.REEL_TICKS || !battle.game.fighting(f)
                    || battle.now()<f.state.stunUntil || !connected(f,target);
            var mover=reel.self?f:target;var anchor=reel.self?target:f;
            double dx=anchor.x-mover.x,dy=anchor.y-mover.y,distance=Math.hypot(dx,dy),stop=reel.self?1.65:2.15;
            if(end || distance<=stop) {it.remove();continue;}
            double speed=Math.min(.85,Math.max(0,distance-stop));
            mover.vx=dx/distance*speed;mover.vy=Math.clamp(dy/distance*speed+.10,-.65,.8);mover.grounded=false;
            mover.jump.clear();mover.jumpHeight.clear();mover.recovery.cancelFastFall();
            if(mover.ledge.attached())mover.ledge.release(battle.now());
            if(reel.self){mover.state.motionType=12;mover.state.motionX=mover.vx;mover.state.motionUntil=battle.now()+1;}
            else {
                mover.state.interrupt();mover.state.stunUntil=Math.max(mover.state.stunUntil,battle.now()+2);
                mover.state.readyAt=Math.max(mover.state.readyAt,battle.now()+2);
                mover.state.lastAttacker=f.id;mover.state.lastHitAt=battle.now();
                if(mover.owner!=null)mover.owner.stopUsingItem();
            }
        }
    }
    void tick() {
        for(var f:battle.actors.values())if(f.kind==FighterClass.DROWNED)f.drowned.grounded(f.grounded);
        for(var it=harpoons.values().iterator();it.hasNext();) {
            var h=it.next();var f=h.owner;var state=f.drowned;
            if(!battle.game.fighting(f)||f.eliminated) {visuals.remove(f.id);state.reset();it.remove();continue;}
            if(battle.now()<f.state.stunUntil)release(f);
            var reel=reels.get(f.id);
            if(reel!=null)h.pos=new Vec3(reel.target.x,reel.target.y+1.1,.5);
            else if(state.phase()==DrownedState.Phase.OUTBOUND) {
                var from=h.pos;var to=from.add(h.velocity);var wall=battle.objects.terrain(from,to);
                double nearest=wall.getType()==HitResult.Type.MISS?Double.POSITIVE_INFINITY:wall.getLocation().distanceToSqr(from);
                Battle.Actor victim=null;
                for(var v:battle.actors.values())if(v!=f&&battle.game.fighting(v)) {
                    var box=v.box().inflate(.12);double distance=box.contains(from)?0:box.clip(from,to).map(p->p.distanceToSqr(from)).orElse(Double.POSITIVE_INFINITY);
                    if(distance<nearest){nearest=distance;victim=v;}
                }
                if(Double.isFinite(nearest)) {
                    h.pos=from.add(h.velocity.normalize().scale(Math.sqrt(nearest)));
                    if(victim!=null) {
                        var impact=battle.hit(f,victim,h.velocity.x<0?-1:1,h.move);
                        if(impact.launch()!=null) {
                            state.hook(battle.now(),victim.id);
                            battle.arenaSound(SoundEvents.TRIDENT_HIT,.6f,1.1f);
                        } else state.returning();
                    } else {state.returning();battle.arenaSound(SoundEvents.TRIDENT_HIT_GROUND,.4f,1.2f);}
                } else {
                    h.pos=to;h.velocity=h.velocity.add(0,-.035,0);
                    if(Math.hypot(to.x-h.originX,to.y-h.originY)>DrownedState.RANGE || battle.now()-h.born>16)state.returning();
                }
            } else if(state.phase()==DrownedState.Phase.HOOKED) {
                var target=target(f);
                if(!connected(f,target))state.returning();else h.pos=new Vec3(target.x,target.y+1.1,.5);
            } else if(state.phase()==DrownedState.Phase.RETURNING) {
                var hand=new Vec3(f.x,f.y+1.2,.5);var delta=hand.subtract(h.pos);
                if(delta.length()<1.5 || battle.now()-h.born>100) {
                    state.returned(battle.now());visuals.remove(f.id);it.remove();
                    battle.arenaSound(SoundEvents.TRIDENT_RETURN,.4f,1.4f);continue;
                }
                h.velocity=delta.normalize().scale(1.4);h.pos=h.pos.add(h.velocity);
            }
            var visualTarget=reel!=null?reel.target:target(f);
            var visualTip=visualTarget==null?h.pos:new Vec3(visualTarget.pose.x,visualTarget.pose.y+1.1,.5);
            visuals.tether(f,visualTip,h.velocity,state.phase()==DrownedState.Phase.HOOKED||reel!=null);
            if(battle.now()%2==0)battle.level.sendParticles(ParticleTypes.SPLASH,true,false,h.pos.x,h.pos.y,.8,2,.05,.05,.01,.02);
        }
        visuals.flush();
    }
    void release(Battle.Actor f){if(harpoons.containsKey(f.id))f.drowned.returning();reels.remove(f.id);selfPlans.remove(f.id);}
    void remove(Battle.Actor f) {
        harpoons.remove(f.id);reels.remove(f.id);selfPlans.remove(f.id);visuals.remove(f.id);f.drowned.reset();
        for(var other:battle.actors.values())if(Objects.equals(other.drowned.target(battle.now()),f.id)
                || reels.containsKey(other.id)&&reels.get(other.id).target==f)release(other);
        visuals.flush();
    }
    void clear(){harpoons.clear();reels.clear();selfPlans.clear();visuals.clear();for(var f:battle.actors.values())f.drowned.reset();}
    int count(){return harpoons.size();}
}
