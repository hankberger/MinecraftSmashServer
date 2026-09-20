package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import static dev.hanks.vanilla.FighterMoves.Technique.*;

/** Native clicks plus deterministic fixtures for object lifetime, shields, collision and ownership. */
@SuppressWarnings("UnstableApiUsage")
public final class RosterClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    private static void place(UUID id,double x,double y,double dx,double dy) {
        var b=game().battle; var f=b.actors.get(id); b.reset(f,x,y); b.reset(b.dummy(),dx,dy); f.facing=1;
    }
    private static void down(ClientGameTestContext c,int button) {
        c.getInput().holdKey(o->o.keyDown); c.waitTicks(1); c.getInput().pressMouse(button); c.getInput().releaseKey(o->o.keyDown);
    }
    private static void secondary(ClientGameTestContext c) { c.getInput().pressKey(o->o.keySwapOffhand); }
    public static void run(ClientGameTestContext c) {
        var props=new Properties(); props.setProperty("online-mode","false"); props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6"); props.setProperty("simulation-distance","5"); props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props); var connection=server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            server.waitFor(s->game().hub.available(connection.getServerPlayer()),240);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            for(var kind:FighterClass.values()) {
                server.runOnServer(s->{game().leave(connection.getServerPlayer());game().choose(connection.getServerPlayer(),kind,VanillaSmash.Mode.SANDBOX);});
                server.waitFor(s->game().battle!=null && game().match.phase()==MatchState.Phase.ACTIVE,300);
                c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.ARENA)&&mc.getCameraEntity()!=mc.player,300);c.waitTicks(20);
                switch(kind) {
                    case STEVE -> {
                        server.runOnServer(s->place(id,0,81,2.25,81)); c.waitTicks(10); c.getInput().pressMouse(0);
                        server.waitFor(s->game().battle.dummy().state.percent==9,20);
                        server.runOnServer(s->place(id,0,81,.7,81)); c.waitTicks(10); c.getInput().pressMouse(0);
                        server.waitFor(s->game().battle.dummy().state.percent==4,20);
                        server.runOnServer(s->place(id,0,81,14,81)); c.waitTicks(10);
                        c.getInput().holdKey(o->o.keyUp);c.waitTicks(1);secondary(c);c.getInput().releaseKey(o->o.keyUp);
                        server.waitFor(s->!game().battle.objects.kits.props.isEmpty(),20); c.waitTicks(5);
                        server.runOnServer(s->{
                            var b=game().battle;var f=b.actors.get(id);var p=b.objects.kits.props.getFirst();check(p.move.technique()==TNT&&p.pos.x>2,"TNT travels independently of Steve");
                            check(f.recoveries==0&&f.grounded&&!f.body.isCrouching(),"F selects utility while W is held without triggering recovery or crouching");
                            p.pos=new Vec3(4,81.4,.5);p.velocity=Vec3.ZERO;b.reset(b.dummy(),5,81);
                        });
                        c.takeScreenshot("roster-steve-tnt");server.waitFor(s->game().battle.dummy().state.percent==16,40);
                        server.runOnServer(s->{check(game().battle.objects.kits.props.isEmpty(),"TNT expires after exploding");place(id,0,87,0,81);});
                        down(c,0);server.waitFor(s->game().battle.objects.kits.props.stream().anyMatch(p->p.move.technique()==ANVIL),15);
                        c.takeScreenshot("roster-steve-anvil");server.waitFor(s->game().battle.dummy().state.percent==10,25);
                        server.runOnServer(s->place(id,0,92,14,81));secondary(c);
                        server.waitFor(s->{var f=game().battle.actors.get(id);return f.state.move!=null&&f.state.move.technique()==TNT;},15);
                        server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.state.move.aerial()&&!f.recovery.fastFalling(),"F works in the air without adding fast-fall input");});
                    }
                    case ALEX -> {
                        server.runOnServer(s->place(id,0,81,.9,81));c.waitTicks(10);c.getInput().pressMouse(0);
                        server.waitFor(s->game().battle.dummy().state.percent==5,15);c.getInput().pressMouse(0);
                        server.waitFor(s->game().battle.dummy().state.percent==9,15);c.getInput().pressMouse(0);
                        server.waitFor(s->game().battle.dummy().state.percent==16,20);
                        server.runOnServer(s->check(game().battle.actors.get(id).state.move.id()==12,"Three native clicks complete the contact chain"));
                        c.takeScreenshot("roster-alex-chain");
                        server.runOnServer(s->place(id,0,81,14,81));c.waitTicks(10);secondary(c);c.waitTicks(7);
                        server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.x < -2 && !f.kit.stepAvailable(),"Wind Step retreats and spends the air budget");check(f.recovery.available(),"Wind Step does not spend or refresh the double jump");});
                        server.runOnServer(s->place(id,0,92,.8,89));down(c,0);
                        server.waitFor(s->game().battle.dummy().state.percent>0,20);
                        server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.vy>.5,"A connected dive kick bounces Alex");check(f.recovery.available()&&f.recovery.recoveryAvailable(),"Bounce preserves existing air options");});
                        c.takeScreenshot("roster-alex-dive");
                    }
                    case ZOMBIE -> {
                        server.runOnServer(s->place(id,0,81,5,81));c.waitTicks(10);down(c,0);
                        server.waitFor(s->game().battle.objects.kits.props.stream().anyMatch(p->p.move.technique()==FISSURE),20);
                        c.takeScreenshot("roster-zombie-fissure");server.waitFor(s->game().battle.dummy().state.percent==6,20);
                        server.runOnServer(s->{place(id,0,81,.8,81);game().battle.actors.get(id).state.percent=30;});c.waitTicks(10);secondary(c);
                        server.waitFor(s->game().battle.dummy().state.percent==10,20);
                        server.runOnServer(s->check(game().battle.actors.get(id).state.percent==24,"Bite restores damage only on contact"));
                        server.runOnServer(s->place(id,0,81,3,81));c.waitTicks(10);down(c,1);c.waitTicks(10);
                        server.runOnServer(s->check(game().battle.dummy().state.percent==0,"Backing out of a bite avoids it"));
                        server.runOnServer(s->place(id,0,81,.8,81));c.getInput().holdKey(o->o.keyShift);c.waitTicks(4);
                        server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);check(f.state.blocking(b.now()),"Native shield held");b.hit(b.dummy(),f,-1,FighterMoves.special(FighterClass.ZOMBIE,AttackDirection.DOWN,false,false));check(f.state.percent==10&&!f.state.blocking(b.now()),"Bite beats the held shield");});
                        c.getInput().releaseKey(o->o.keyShift);
                    }
                    case SKELETON -> {
                        server.runOnServer(s->place(id,0,81,6,81));c.waitTicks(10);c.getInput().pressMouse(0);
                        server.waitFor(s->game().battle.objects.hasArrow(game().battle.actors.get(id)),15);c.takeScreenshot("roster-skeleton-quickshot");
                        server.waitFor(s->game().battle.dummy().state.percent==5,20);
                        server.runOnServer(s->place(id,0,81,3,81));c.waitTicks(10);secondary(c);c.waitTicks(16);
                        server.runOnServer(s->check(game().battle.dummy().state.percent==7,"A scatter volley hits each victim only once"));
                        server.runOnServer(s->{place(id,0,81,12,81);game().battle.dummy().state.percent=180;});c.waitTicks(10);
                        c.getInput().holdMouse(1);c.waitTicks(23);c.getInput().releaseMouse(1);
                        server.waitFor(s->game().battle.dummy().state.percent==190,25);
                        server.runOnServer(s->check(game().battle.dummy().state.strongLaunch,"Full draw is a finishing shot at high damage"));
                        server.runOnServer(s->place(id,6,81,6,85));c.waitTicks(10);c.getInput().holdKey(o->o.keyUp);c.waitTicks(1);c.getInput().pressMouse(0);c.getInput().releaseKey(o->o.keyUp);c.waitTicks(20);
                        server.runOnServer(s->check(game().battle.dummy().state.percent==0,"Sky Shot cannot shoot through a solid platform"));
                    }
                    case VILLAGER -> {
                        server.runOnServer(s->place(id,0,81,6,81));c.waitTicks(10);c.getInput().pressMouse(0);
                        server.waitFor(s->game().battle.dummy().state.percent==5,25);
                        server.runOnServer(s->place(id,0,81,14,81));c.waitTicks(10);down(c,0);
                        server.waitFor(s->game().battle.objects.kits.props.stream().anyMatch(p->p.move.technique()==SAPLING),15);
                        server.runOnServer(s->{var b=game().battle;var p=b.objects.kits.props.getFirst();check(!p.armed,"Sapling visibly grows before it can hurt");b.reset(b.dummy(),p.pos.x,81);});
                        c.takeScreenshot("roster-villager-sapling");server.waitFor(s->game().battle.dummy().state.percent==7,25);
                        server.runOnServer(s->place(id,0,81,2.8,81));c.waitTicks(10);secondary(c);
                        server.waitFor(s->game().battle.objects.kits.props.stream().anyMatch(p->p.move.technique()==GOLEM),15);
                        c.waitTicks(3);c.takeScreenshot("roster-villager-golem");server.waitFor(s->game().battle.dummy().state.percent==15,20);
                        server.runOnServer(s->place(id,0,81,.3,81));c.waitTicks(10);down(c,1);c.waitTicks(18);
                        server.runOnServer(s->check(game().battle.dummy().state.percent==0,"Golem's spaced punch misses a point-blank opponent"));
                        server.runOnServer(s->{
                            place(id,0,81,14,81);var b=game().battle;var f=b.actors.get(id);f.state.attackDirection=1;b.objects.bell(f);
                            var bell=b.objects.bells.get(id);bell.pos=new Vec3(6,81.3,.5);bell.entity.setPos(bell.pos);bell.velocity=Vec3.ZERO;bell.armedAt=b.now();bell.ringAt=b.now()+100;
                        });c.waitTicks(10);c.getInput().pressMouse(0);
                        server.waitFor(s->game().battle.objects.bells.get(id).armedAt<0,25);
                        server.runOnServer(s->check(game().battle.objects.bells.get(id).velocity.x>.5,"A parcel bats the bell from range"));
                        server.runOnServer(s->{
                            place(id,0,90,8,89);var b=game().battle;var f=b.actors.get(id);var d=b.dummy();d.state.attackDirection=-1;b.objects.arrow(d,8);
                            var shot=b.objects.arrows.get(d.id);shot.entity().setPos(5.5,90.7,.5);shot.entity().setDeltaMovement(-1,0,0);
                            check(b.request(f,false,Input.EMPTY),"Villager starts a neutral aerial");
                        });
                        server.waitFor(s->game().battle.objects.arrows.values().stream().anyMatch(a->a.owner().id.equals(id)),15);
                        server.runOnServer(s->check(game().battle.objects.arrows.values().stream().filter(a->a.owner().id.equals(id)).allMatch(a->a.entity().getDeltaMovement().x>0),"Nair reflects an incoming arrow and transfers credit"));
                    }
                }
                server.runOnServer(s->counterplay(id,kind));
                server.runOnServer(s->{var b=game().battle;var f=b.actors.get(id);b.ringOut(f);check(b.objects.kits.props.isEmpty()&&b.objects.bells.isEmpty()&&b.objects.arrowCount(f)==0,"KO cleans up the kit's owned objects");});
                VanillaSmash.LOG.info("ROSTER_NATIVE_OK {}",kind);
            }
        }
    }
    private static void counterplay(UUID id,FighterClass kind) {
        place(id,0,81,14,81);var b=game().battle;var f=b.actors.get(id);var d=b.dummy();
        switch(kind) {
            case STEVE -> {
                f.state.move=FighterMoves.special(kind,AttackDirection.DOWN,false,false); f.state.attackDirection=1;
                b.objects.kits.spawn(f);var tnt=b.objects.kits.props.getFirst();int fuse=tnt.born+tnt.lifetime;
                tnt.pos=new Vec3(2,82,.5);d.state.move=FighterMoves.light(d.kind,AttackDirection.FORWARD,false);d.state.startedAt=b.now();d.state.attackDirection=1;
                var area=CombatGeometry.shape(d.state.move,1,0,81);b.objects.kits.strike(d,area);
                check(tnt.owner==d&&tnt.born+tnt.lifetime==fuse,"A batted TNT transfers ownership without extending its fuse");
                var velocity=tnt.velocity;tnt.velocity=Vec3.ZERO;b.objects.kits.strike(d,area);
                check(tnt.velocity.equals(Vec3.ZERO),"One swing cannot bat the TNT repeatedly");tnt.velocity=velocity;
                var floor=net.minecraft.core.BlockPos.containing(0,80,.5);var original=b.level.getBlockState(floor);
                tnt.pos=new Vec3(1,81.4,.5);tnt.velocity=Vec3.ZERO;
                int saved=b.game.ticks;try { b.game.ticks=fuse;b.objects.kits.tick(); } finally { b.game.ticks=saved; }
                check(f.state.percent==16&&f.state.lastAttacker.equals(d.id),"The original thrower can be hit by a reflected TNT with the new owner's credit");
                check(!d.state.paused(b.now())&&b.level.getBlockState(floor).equals(original),"A remote explosion neither pauses its owner nor destroys terrain");
                place(id,0,81,1,81);d.state.requestGuard(b.now(),true,true);
                b.hit(f,d,1,FighterMoves.light(kind,AttackDirection.FORWARD,false));
                check(f.state.motionType==9&&f.state.motionX<0&&f.state.readyAt>=b.now()+7,"A blocked melee pushes the attacker away and leaves a punish window");
            }
            case ALEX -> {
                check(b.request(f,true,Input.EMPTY),"Dash starts");d.state.requestGuard(b.now(),true,true);
                b.hit(f,d,1,f.state.move);
                check(f.state.motionUntil==0&&f.vx==0&&f.state.readyAt>=b.now()+10,"A shield stops Alex's dash");
            }
            case ZOMBIE -> {
                check(b.request(f,true,Input.EMPTY),"Ground slam starts");int saved=b.game.ticks;
                try {
                    b.game.ticks++;long impact=f.state.impactAt;
                    b.hit(d,f,1,FighterMoves.light(FighterClass.ALEX,AttackDirection.FORWARD,false));
                    check(f.state.percent==5&&f.state.impactAt==impact&&f.state.stunUntil==0,"Ground slam absorbs one light without flinching");
                    f.state.hitImmuneUntil=0;b.hit(d,f,1,FighterMoves.arrow(8));
                    check(f.state.impactAt<0&&f.state.stunUntil>b.now(),"A bow special breaks slam armor");
                } finally { b.game.ticks=saved; }
                place(id,12,85,15,85);f.state.move=FighterMoves.light(kind,AttackDirection.DOWN,false);f.state.attackDirection=1;
                b.objects.kits.spawn(f);b.objects.kits.tick();
                check(b.objects.kits.props.isEmpty()&&d.state.percent==0,"A fissure stops at a platform edge instead of crossing the gap");
                place(id,0,81,2,83);f.state.move=FighterMoves.light(kind,AttackDirection.DOWN,false);
                b.objects.kits.spawn(f);for(int i=0;i<6;i++)b.objects.kits.tick();
                check(d.state.percent==0,"Jumping avoids the low fissure");b.objects.kits.clear();
                place(id,0,81,.8,81);f.state.move=FighterMoves.special(kind,AttackDirection.DOWN,false,false);
                f.state.impactAt=-1;f.state.activeStartedAt=b.now()-1;f.state.activeUntil=b.now()+2;f.state.attackDirection=1;
                f.state.hitTargets.add(UUID.randomUUID());b.tick();
                check(d.state.percent==0,"A grab that caught someone on its first frame cannot grab a second fighter on a later frame");
            }
            case SKELETON -> {
                f.state.move=FighterMoves.light(kind,AttackDirection.UP,false);f.state.attackDirection=1;
                b.objects.quickArrows(f);var arrow=b.objects.arrows.values().iterator().next();double initial=arrow.entity().getDeltaMovement().y;
                b.objects.tick();check(arrow.entity().getDeltaMovement().y<initial,"Quick directional arrows also have gravity");b.objects.remove(f);
            }
            case VILLAGER -> {
                var down=new Input(false,true,false,false,false,false,false);
                check(b.request(f,true,down),"Golem summon starts");check(b.objects.kits.props.size()==1,"Golem is visible during windup");
                b.hit(d,f,-1,FighterMoves.light(FighterClass.STEVE,AttackDirection.FORWARD,false));b.objects.kits.tick();
                check(b.objects.kits.props.isEmpty(),"Hitting the caster interrupts an uncommitted golem");
                place(id,0,81,3,81);f.state.move=FighterMoves.light(kind,AttackDirection.DOWN,false);f.state.attackDirection=1;b.objects.kits.spawn(f);
                var plant=b.objects.kits.props.getFirst();plant.pos=new Vec3(3,81.5,.5);plant.armed=true;d.state.protectedUntil=b.now()+100;
                b.objects.kits.tick();check(b.objects.kits.props.contains(plant)&&d.state.percent==0,"Respawn protection does not consume a sapling");
                b.reset(d,14,81);d.state.attackDirection=1;b.objects.arrow(d,8);var shot=b.objects.arrows.get(d.id);
                shot.entity().setPos(2,81.5,.5);shot.entity().setDeltaMovement(1.5,0,0);b.objects.tick();
                check(b.objects.kits.props.isEmpty()&&!b.objects.hasArrow(d),"An enemy arrow breaks and is stopped by a sapling");
                b.objects.kits.spawn(f);plant=b.objects.kits.props.getFirst();plant.pos=new Vec3(2,81.5,.5);
                d.state.move=FighterMoves.light(d.kind,AttackDirection.FORWARD,false);d.state.attackDirection=1;
                b.objects.kits.strike(d,CombatGeometry.shape(d.state.move,1,0,81));check(b.objects.kits.props.isEmpty(),"Enemy melee also breaks a sapling");
            }
        }
    }
}
