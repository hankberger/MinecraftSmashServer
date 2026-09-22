package dev.hanks.vanilla;

import dev.hanks.network.Wire;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.world.entity.*;

/** Render every victory model and use native inputs to exercise the new post-match scene. */
@SuppressWarnings("UnstableApiUsage")
public final class WinnerStageClientTest {
    private record Sample(double x,double y,double z,float yaw,float pitch,double viewY,float fov) {}
    private static void checkMotion(List<Sample> samples) {
        check(samples.size()>WinnerCamera.DURATION,"Camera sampled throughout the reveal");
        int moving=0;double largest=0;
        for(int i=1;i<samples.size();i++) {
            var a=samples.get(i-1);var b=samples.get(i);
            double dx=b.x-a.x,dz=b.z-a.z,step=Math.hypot(dx,dz);largest=Math.max(largest,step);
            check(dx<=.001 && dz>=-.001,"Camera never reverses or receives a competing correction");
            check(step<.22,"Camera never jumps between network updates: "+step);
            check(Math.abs(b.y-a.y)<.001 && Math.abs(b.yaw-a.yaw)<.001 && Math.abs(b.pitch-a.pitch)<.001,"Heading and height stay locked");
            check(Math.abs(b.viewY-WinnerCamera.START.y())<.02,"Rendered camera has no eye-height hop on attachment: "+b.viewY);
            check(Math.abs(b.fov-70)<.05,"Rendered FOV stays stable across attachment: "+b.fov);
            if(step>.001)moving++;
        }
        check(moving>=WinnerCamera.DURATION-1 && moving<=WinnerCamera.DURATION+1,"Client advances smoothly for the full interpolation: "+moving);
        var first=samples.getFirst();var last=samples.getLast();
        check(Math.abs(last.z-first.z-(WinnerCamera.END.z()-WinnerCamera.START.z()))<.01,"Full pullback reaches the destination");
        VanillaSmash.LOG.info("WINNER_CAMERA_NATIVE_MOTION_PASSED samples={} movingTicks={} largestStep={}",samples.size(),moving,largest);
    }
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    static Wire.MatchResult result(UUID player, FighterClass kind, boolean draw) {
        var roster = new ArrayList<Wire.Ticket>(); var rows = new ArrayList<Wire.ResultRow>();
        for (int i=0;i<(draw?4:2);i++) {
            var id=i==0?player:UUID.randomUUID(); var fighter=i==0?kind:FighterClass.values()[i];
            roster.add(new Wire.Ticket(id,fighter.name(),draw?"MATCH":"DUEL",UUID.randomUUID()));
            rows.add(new Wire.ResultRow(id,i==0?"Player0":"Rival"+i,fighter.name(),i+1,draw?1:i==0?2:0,i==0?3:1,i==0?1:3,i==0?247:123));
        }
        return new Wire.MatchResult(UUID.randomUUID(),draw?"MATCH":"DUEL",draw?null:player,roster,rows);
    }
    public static void run(ClientGameTestContext c) {
        var captureId=new AtomicInteger(-1);var trace=new ArrayList<Sample>();
        ClientTickEvents.END_CLIENT_TICK.register(mc->{
            var camera=mc.getCameraEntity();
            if(camera!=null && camera.getId()==captureId.get())trace.add(new Sample(camera.getX(),camera.getY(),camera.getZ(),camera.getViewYRot(1),camera.getViewXRot(1),mc.gameRenderer.mainCamera().position().y,mc.gameRenderer.mainCamera().getFov()));
        });
        var props=new Properties(); props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("simulation-distance","5");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            connection.waitForChunksRender();c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            server.waitFor(s->game().hub.available(connection.getServerPlayer()),200);
            for(var kind:FighterClass.values()) {
                var cameraId=new AtomicInteger();var modelId=new AtomicInteger();var initialX=new AtomicReference<Double>();
                c.runOnClient(mc->trace.clear());
                server.runOnServer(s->{
                    var p=connection.getServerPlayer();game().hub.parties.ensure(p.getUUID(),p.getPlainTextName());
                    game().hub.results.receive(result(p.getUUID(),kind,false));
                });
                server.waitFor(s->game().hub.results.scene.active(connection.getServerPlayer()),100);
                server.runOnServer(s->{
                    var session=game().hub.results.scene.session(connection.getServerPlayer().getUUID());
                    cameraId.set(session.camera.getId());modelId.set(session.models.getFirst().getId());initialX.set(session.camera.getX());
                    captureId.set(session.camera.getId());
                    check(session.room<0,"Victory rooms cannot collide with character-picker rooms");
                    check(Math.abs(session.camera.getEyeHeight()-session.player.getEyeHeight())<.001,"Camera eye height matches the player before attachment");
                    check(game().battle==null && !session.ready(),"Presentation leaves arena free and gates replay inputs");
                    game().hub.results.scene.confirm(connection.getServerPlayer());
                    check(game().hub.results.book.votes(session.result.id())==0,"Clicking during the reveal cannot accept a rematch");
                });
                c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.getCameraEntity()!=null && mc.getCameraEntity().getId()==cameraId.get() && mc.gui.screen()==null,300);
                if(kind==FighterClass.STEVE) { c.waitTicks(8);c.takeScreenshot("winner-01-pullback"); }
                server.waitFor(s->game().hub.results.scene.session(connection.getServerPlayer().getUUID()).ready(),160);
                c.waitFor(mc->mc.level.getEntity(modelId.get())!=null);c.waitTicks(10);
                server.runOnServer(s->{
                    var session=game().hub.results.scene.session(connection.getServerPlayer().getUUID());
                    check(Math.abs(session.camera.getX()-initialX.get()-(WinnerCamera.END.x()-WinnerCamera.START.x()))<.001,"Server publishes only the destination");
                    check(session.entities.stream().noneMatch(Entity::isRemoved),"Winner entities survive deferred chunk registration");
                });
                c.runOnClient(mc->{
                    checkMotion(trace);captureId.set(-1);
                    var model=mc.level.getEntity(modelId.get());
                    check(model.getType()==switch(kind) {case STEVE,ALEX->EntityTypes.MANNEQUIN;case ZOMBIE->EntityTypes.ZOMBIE;case SKELETON->EntityTypes.SKELETON;case VILLAGER->EntityTypes.VILLAGER;},"Winning class uses its real vanilla model");
                    check(((LivingEntity)model).getScale()>3,"Winner is presented as the main character");
                });
                c.takeScreenshot("winner-02-"+kind.label.toLowerCase());
                if(kind==FighterClass.VILLAGER) {
                    c.getInput().resizeWindow(960,720);c.waitTicks(8);c.takeScreenshot("winner-03-four-three");
                    c.runOnClient(mc->mc.options.fov().set(90));c.waitTicks(8);c.takeScreenshot("winner-04-wide-fov");
                    c.runOnClient(mc->mc.options.fov().set(70));c.getInput().resizeWindow(1280,720);
                    MatchmakingClientTest.winnerAction(c,2);
                    server.waitFor(s->game().stage.active(connection.getServerPlayer()),120);
                    server.runOnServer(s->check(!game().hub.results.scene.active(connection.getServerPlayer()),"Change fighter closes the victory scene"));
                    c.waitFor(mc->mc.level.getEntity(cameraId.get())==null && mc.level.getEntity(modelId.get())==null,200);
                    c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.getCameraEntity()==mc.player && mc.gui.screen()==null && Math.abs(mc.player.getX())<30,300);
                    c.waitTicks(20);c.getInput().pressKey(o->o.keyDrop);MatchmakingClientTest.menuReady(c);MatchmakingClientTest.click(c,"Back");
                } else {
                    MatchmakingClientTest.winnerAction(c,3);
                    c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity()==mc.player && mc.gui.screen()==null,200);
                    c.waitTicks(10);
                }
                server.runOnServer(s->check(!game().hub.results.scene.active(connection.getServerPlayer()) && game().network.selections.tickets().isEmpty(),"Leaving victory restores the lobby without selecting a match"));
                server.runOnServer(s->check(connection.getServerPlayer().getAbilities().getWalkingSpeed()==.1f,"Leaving victory restores normal movement abilities"));
            }
            server.runOnServer(s->game().hub.results.receive(result(connection.getServerPlayer().getUUID(),FighterClass.STEVE,true)));
            MatchmakingClientTest.winnerReady(c);
            server.runOnServer(s->check(game().hub.results.scene.session(connection.getServerPlayer().getUUID()).models.size()==4,"Draw presents all tied fighters"));
            c.takeScreenshot("winner-05-draw");
            MatchmakingClientTest.winnerAction(c,0);
            server.runOnServer(s->check(game().network.selections.tickets().isEmpty(),"Missing opponents cannot be rematched from the podium"));
            MatchmakingClientTest.winnerAction(c,3);c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200);
        }
        VanillaSmash.LOG.info("WINNER_STAGE_NATIVE_CLIENT_TEST_PASSED");
    }
}
