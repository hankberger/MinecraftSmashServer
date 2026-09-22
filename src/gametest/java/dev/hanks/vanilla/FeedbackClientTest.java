package dev.hanks.vanilla;

import java.util.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.Display;

/** Real clients render effects; combat outcomes and lifetime limits remain server owned. */
@SuppressWarnings("UnstableApiUsage")
public final class FeedbackClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("simulation-distance","5");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            connection.waitForChunksRender();c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            server.waitFor(s->game().hub.available(connection.getServerPlayer()),240);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            for(var kind:FighterClass.values()) {
                server.runOnServer(s->{game().leave(connection.getServerPlayer());game().choose(connection.getServerPlayer(),kind,VanillaSmash.Mode.SANDBOX);});
                server.waitFor(s->game().battle!=null && game().match.phase()==MatchState.Phase.ACTIVE,300);c.waitTicks(20);
                server.runOnServer(s->{var b=game().battle;b.reset(b.actors.get(id),-1,81);b.reset(b.dummy(),1,81);});c.waitTicks(10);
                server.runOnServer(s->{
                    var b=game().battle;var f=b.actors.get(id);var target=b.dummy();
                    var move=FighterMoves.light(kind,AttackDirection.FORWARD,false);b.hit(f,target,1,move);
                    check(target.state.percent==move.damage(),"Feedback never adds damage");
                    check(target.state.paused(b.now()),"Light contact has a brief impact hold");
                    check(move.detached() || f.state.paused(b.now()),"Melee pauses its attacker");
                    if(move.detached())check(!f.state.paused(b.now()),"Distant projectiles never freeze their owner");
                    check(b.effects.impacts.activePieces()==8,"One actual hit creates one contact burst");
                    b.hit(f,target,1,move);check(b.effects.impacts.activePieces()==8,"Immunity does not replay feedback");
                });
                c.waitTicks(2);
                c.runOnClient(mc->check(java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                        .anyMatch(e->e instanceof Display.TextDisplay d && d.getText().getString().contains("P2")
                                && d.getText().getString().contains(FighterMoves.light(kind,AttackDirection.FORWARD,false).damage()+"%")),"HUD confirms damage immediately"));
                c.takeScreenshot("feedback-01-"+kind.name().toLowerCase()+"-hit");c.waitTicks(12);
                server.runOnServer(s->check(game().battle.effects.impacts.activePieces()==0,"Hit bursts expire"));
                c.runOnClient(mc->check(java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                        .noneMatch(e->e instanceof Display.BlockDisplay && e!=mc.getCameraEntity().getVehicle()),"Expired impact displays leave the client"));
            }
            server.runOnServer(s->{var b=game().battle;b.reset(b.actors.get(id),-1,81);b.reset(b.dummy(),1,81);});c.waitTicks(10);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);var target=b.dummy();
                target.state.percent=180;b.hit(f,target,1,FighterMoves.bell());
                check(target.state.strongLaunch && target.state.paused(b.now()),"Powerful launches get stronger feedback");
            });
            c.waitTicks(2);c.takeScreenshot("feedback-02-launch-contact");c.waitTicks(6);c.takeScreenshot("feedback-03-launch-trail");c.waitTicks(15);
            server.runOnServer(s->{var b=game().battle;b.reset(b.actors.get(id),-1,81);b.reset(b.dummy(),1,81);});c.waitTicks(10);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);var target=b.dummy();
                target.state.guardUntil=b.now()+20;target.state.guard=3;
                b.hit(f,target,1,FighterMoves.light(f.kind,AttackDirection.FORWARD,false));
                check(target.state.percent==0 && target.state.guard==0 && target.state.stunUntil>b.now(),"Shield shatter preserves break rules");
            });
            c.waitTicks(2);
            c.runOnClient(mc->check(java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                    .anyMatch(e->e instanceof Display.TextDisplay d && d.getText().getString().contains("SHIELD BREAK")),"Shield break callout replaces older notifications immediately"));
            c.takeScreenshot("feedback-04-shield-break");c.waitTicks(12);
            server.runOnServer(s->{
                var b=game().battle;var f=b.actors.get(id);b.reset(f,-1,81);b.reset(b.dummy(),14,81);
                for(int i=0;i<40;i++)b.effects.impacts.object(f,new net.minecraft.world.phys.Vec3(i*.02,82,1));
                check(b.effects.impacts.activePieces()<=96,"Effect storm is bounded even with many simultaneous contacts");
            });c.waitTicks(12);
            c.getInput().pressKey(o->o.keyJump);c.waitTicks(4);c.getInput().pressKey(o->o.keyJump);c.waitTicks(2);c.takeScreenshot("feedback-05-double-jump");c.waitTicks(30);
            server.runOnServer(s->{var b=game().battle;check(b.effects.impacts.activePieces()==0,"Burst cap cleanup completes");b.ringOut(b.dummy());});
            c.waitTicks(6);c.takeScreenshot("feedback-06-ko");
            c.runOnClient(mc->check(java.util.stream.StreamSupport.stream(mc.level.entitiesForRendering().spliterator(),false)
                    .anyMatch(e->e instanceof Display.TextDisplay d && d.getText().getString().contains("KO!")),"KO callout is visible even beyond the camera"));
            server.runOnServer(s->game().leave(connection.getServerPlayer()));
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200);
        }
        VanillaSmash.LOG.info("COMBAT_FEEDBACK_CLIENT_TEST_PASSED");
    }
}
