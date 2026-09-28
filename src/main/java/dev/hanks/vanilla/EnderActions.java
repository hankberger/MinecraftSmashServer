package dev.hanks.vanilla;

import java.util.*;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Mark -> toss -> deliberate pursuit. All destinations are visible and server validated. */
public final class EnderActions {
    private record Plan(long started, FighterMoves.Technique technique, EnderState.Point destination, UUID prey, int facing) {}
    private final Battle battle;
    private final Map<UUID,Plan> plans = new HashMap<>();
    final EnderVisuals visuals;
    EnderActions(Battle battle) { this.battle = battle; visuals = new EnderVisuals(battle); }
    public Battle.Actor prey(Battle.Actor f) {
        UUID id = f.ender.prey(battle.now());
        var target = id == null ? null : battle.actors.get(id);
        if (target == null || target.eliminated || !battle.game.fighting(target) || target.state.floating(battle.now())) {
            f.ender.forget(); return null;
        }
        return target;
    }
    public boolean winding(Battle.Actor f) {
        var p = plans.get(f.id);
        return p != null && p.started == f.state.startedAt && f.state.impactAt >= 0;
    }
    public boolean available(Battle.Actor f, AttackIntent intent) {
        if (f.kind != FighterClass.ENDERMAN || intent.kind() != AttackKind.HEAVY || intent.direction() == AttackDirection.DOWN) return true;
        if (!f.ender.canPursue(battle.now(),f.grounded)) return false;
        var target = prey(f);
        return target == null || Math.hypot(target.x-f.x,target.y-f.y) <= EnderState.PURSUIT_RANGE;
    }
    public void begin(Battle.Actor f, AttackIntent intent) {
        if (f.kind != FighterClass.ENDERMAN || intent.kind() == AttackKind.LIGHT) return;
        var type = f.state.move.technique();
        if (type == FighterMoves.Technique.RIFT_SWING) return;
        EnderState.Point destination; UUID id = null; int facing = f.state.attackDirection;
        if (type == FighterMoves.Technique.BLINK) {
            var target = prey(f);
            if (target != null) {
                id = target.id;
                // Predict only the short windup, then lock the tell. No homing after commitment.
                int side = intent.axis() != 0 ? intent.axis() : f.x <= target.x ? -1 : 1;
                double dx = Math.clamp(target.vx * 3,-2.5,2.5), dy = Math.clamp(target.vy * 3,-2,2);
                destination = new EnderState.Point(target.x + dx + side*1.65,target.y + dy);
                if(!clear(f,destination)) {
                    var otherSide=new EnderState.Point(target.x+dx-side*1.65,target.y+dy);
                    if(clear(f,otherSide)) { destination=otherSide;side=-side; }
                }
                facing = -side;
            } else destination = new EnderState.Point(f.x + facing*EnderState.PHASE_DISTANCE,f.y);
            f.ender.pursue(battle.now());
        } else if (type == FighterMoves.Technique.PEARL) {
            destination = safePhase(f,intent.axis()*EnderState.PEARL_SIDE,intent.axis()==0?EnderState.PEARL_UP:8.5);
        } else return;
        plans.put(f.id,new Plan(f.state.startedAt,type,destination,id,facing));
        visuals.rift(f.id,destination, f.state.move.startup()+7);
        battle.arenaSound(SoundEvents.ENDERMAN_AMBIENT,.25f,1.6f);
    }
    private EnderState.Point safePhase(Battle.Actor f,double dx,double dy) {
        for (double fraction=1;fraction>=.25;fraction-=.05) {
            var p=new EnderState.Point(f.x+dx*fraction,f.y+dy*fraction);
            if(clear(f,p))return p;
        }
        return new EnderState.Point(f.x+dx,f.y+dy);
    }
    public void resolve(Battle.Actor f) {
        var p=plans.remove(f.id);
        if(p==null || p.started!=f.state.startedAt)return;
        var target=p.prey==null?null:battle.actors.get(p.prey);
        if(!clear(f,p.destination) || p.prey!=null && (target==null || target.eliminated || target.state.floating(battle.now()))) {
            battle.hud.announce("P"+f.slot+"  RIFT BLOCKED",0xca8aff,14);return;
        }
        if(p.technique==FighterMoves.Technique.PEARL)f.recovery.recover(false);
        else f.ender.spendAir();
        visuals.afterimage(f);
        f.facing=f.state.attackDirection=p.facing;
        warp(f,p.destination);
        // Pursuit follows airborne prey without replenishing jumps; recovery finishes with a gentle fall.
        if(p.technique==FighterMoves.Technique.PEARL)f.vy=-.05;
        else if(target!=null) { f.vy=Math.clamp(target.vy,-.3,.65);f.recovery.cancelFastFall(); }
        visuals.arrive(f);
        battle.arenaSound(SoundEvents.ENDERMAN_TELEPORT,.75f,.85f);
    }
    boolean clear(Battle.Actor f,EnderState.Point p) {
        var size=f.body.getDimensions(Pose.STANDING);
        if(battle.stage.outside(p.x(),p.y(),.5)||p.y()+size.height()>=battle.stage.blastTop())return false;
        var box=new AABB(p.x()-size.width()/2+.001,p.y()+.001,.25,p.x()+size.width()/2-.001,p.y()+size.height()-.001,.75);
        for(var shape:battle.level.getBlockCollisions(f.body,box))if(!shape.isEmpty())return false;
        return true;
    }
    private void warp(Battle.Actor f,EnderState.Point p) {
        f.x=p.x();f.y=p.y();f.vx=0;f.grounded=false;
        f.jump.clear();f.jumpHeight.clear();f.down.clear();f.state.guardUntil=0;f.state.protectedUntil=0;
        f.pose.reset(f.x,f.y);f.body.setPos(f.x,f.y,.5);f.body.setOnGround(false);
        var packet=ClientboundTeleportEntityPacket.teleport(f.body.getId(),PositionMoveRotation.of(f.body),Set.of(),false);
        for(var view:battle.game.viewers.values())view.player().connection.send(packet);
    }
    /** Only actual damage marks: shields, parries and protected targets never grant a pursuit. */
    public void contact(Battle.Actor f,Battle.Actor victim,FighterMoves.Move move) {
        if(f.kind!=FighterClass.ENDERMAN)return;
        f.ender.mark(battle.now(),victim.id);visuals.mark(f,victim);
    }
    public void swing(Battle.Actor f) {
        f.body.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        visuals.armSwing(f);
        battle.arenaSound(SoundEvents.PLAYER_ATTACK_SWEEP,.75f,.65f);
    }
    public void pose(Battle.Actor f) {
        if(!(f.body instanceof EnderMan ender))return;
        var target=prey(f);
        if(ender.getTarget()!=(target==null?null:target.body))ender.setTarget(target==null?null:target.body);
        boolean reaching=f.state.move!=null&&f.state.move.technique()==FighterMoves.Technique.RIFT_SWING&&f.state.impactAt>=0;
        // AIR is encoded as an empty optional by the vanilla block-state serializer.
        // BARRIER has no rendered model but preserves the native carrying-arm pose.
        ender.setCarriedBlock(reaching?Blocks.BARRIER.defaultBlockState():null);
        if(reaching)f.body.setXRot(-15);
    }
    public void tick() {
        for(var f:battle.actors.values())if(f.kind==FighterClass.ENDERMAN) {
            if(!battle.game.fighting(f)){remove(f);continue;}
            var p=plans.get(f.id);
            if(p!=null&&(f.state.impactAt<0||p.started!=f.state.startedAt))plans.remove(f.id);
            visuals.track(f,prey(f));
        }
        visuals.tick();
    }
    public void remove(Battle.Actor f) {
        plans.remove(f.id);f.ender.reset();visuals.remove(f.id);
        for(var other:battle.actors.values())if(Objects.equals(other.ender.prey(battle.now()),f.id)) {
            other.ender.forget();visuals.unmark(other.id);
        }
        if(f.body instanceof EnderMan e){e.setTarget(null);e.setCarriedBlock(null);}
    }
    public void clear(){plans.clear();visuals.clear();for(var f:battle.actors.values())f.ender.reset();}
    int markerCount(){return visuals.markerCount();}
}
