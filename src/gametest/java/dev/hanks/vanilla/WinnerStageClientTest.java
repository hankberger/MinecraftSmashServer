package dev.hanks.vanilla;

import dev.hanks.network.Wire;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.*;

/** Render every victory model and use native inputs to exercise the new post-match scene. */
@SuppressWarnings("UnstableApiUsage")
public final class WinnerStageClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    private static Wire.MatchResult result(UUID player, FighterClass kind, boolean draw) {
        var roster = new ArrayList<Wire.Ticket>(); var rows = new ArrayList<Wire.ResultRow>();
        for (int i=0;i<(draw?4:2);i++) {
            var id=i==0?player:UUID.randomUUID(); var fighter=i==0?kind:FighterClass.values()[i];
            roster.add(new Wire.Ticket(id,fighter.name(),draw?"MATCH":"DUEL",UUID.randomUUID()));
            rows.add(new Wire.ResultRow(id,i==0?"Player0":"Rival"+i,fighter.name(),i+1,draw?1:i==0?2:0,i==0?3:1,i==0?1:3,i==0?247:123));
        }
        return new Wire.MatchResult(UUID.randomUUID(),draw?"MATCH":"DUEL",draw?null:player,roster,rows);
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties(); props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("simulation-distance","5");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            connection.waitForChunksRender();c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            server.waitFor(s->game().hub.available(connection.getServerPlayer()),200);
            for(var kind:FighterClass.values()) {
                var cameraId=new AtomicInteger();var modelId=new AtomicInteger();var initialX=new AtomicReference<Double>();
                server.runOnServer(s->{
                    var p=connection.getServerPlayer();game().hub.parties.ensure(p.getUUID(),p.getPlainTextName());
                    game().hub.results.receive(result(p.getUUID(),kind,false));
                });
                server.waitFor(s->game().hub.results.scene.active(connection.getServerPlayer()),100);
                server.runOnServer(s->{
                    var session=game().hub.results.scene.session(connection.getServerPlayer().getUUID());
                    cameraId.set(session.camera.getId());modelId.set(session.models.getFirst().getId());initialX.set(session.camera.getX());
                    check(session.room<0,"Victory rooms cannot collide with character-picker rooms");
                    check(game().battle==null && !session.ready(),"Presentation leaves arena free and gates replay inputs");
                    game().hub.results.scene.confirm(connection.getServerPlayer());
                    check(game().hub.results.book.votes(session.result.id())==0,"Clicking during the reveal cannot accept a rematch");
                });
                c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.getCameraEntity()!=null && mc.getCameraEntity().getId()==cameraId.get() && mc.gui.screen()==null,300);
                if(kind==FighterClass.STEVE) { c.waitTicks(8);c.takeScreenshot("winner-01-camera-sweep"); }
                server.waitFor(s->game().hub.results.scene.session(connection.getServerPlayer().getUUID()).ready(),160);
                c.waitFor(mc->mc.level.getEntity(modelId.get())!=null);c.waitTicks(10);
                server.runOnServer(s->{
                    var session=game().hub.results.scene.session(connection.getServerPlayer().getUUID());
                    check(Math.abs(session.camera.getX()-initialX.get())>5,"Camera moves from close-up into the replay composition");
                    check(session.entities.stream().noneMatch(Entity::isRemoved),"Winner entities survive deferred chunk registration");
                });
                c.runOnClient(mc->{
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
                    c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.getCameraEntity()!=mc.player && mc.gui.screen()==null && Math.abs(mc.player.getX())<30,300);
                    c.waitTicks(20);c.getInput().pressKey(o->o.keyDrop);MatchmakingClientTest.menuReady(c);MatchmakingClientTest.click(c,"Back");
                } else {
                    c.getInput().pressKey(o->o.keyDrop);
                    c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity()==mc.player && mc.gui.screen()==null,200);
                    c.waitTicks(10);
                }
                server.runOnServer(s->check(!game().hub.results.scene.active(connection.getServerPlayer()) && game().network.selections.tickets().isEmpty(),"Leaving victory restores the lobby without selecting a match"));
            }
            server.runOnServer(s->game().hub.results.receive(result(connection.getServerPlayer().getUUID(),FighterClass.STEVE,true)));
            MatchmakingClientTest.winnerReady(c);
            server.runOnServer(s->check(game().hub.results.scene.session(connection.getServerPlayer().getUUID()).models.size()==4,"Draw presents all tied fighters"));
            c.takeScreenshot("winner-05-draw");
            c.getInput().holdMouse(1);c.waitTicks(16);c.getInput().releaseMouse(1);
            server.runOnServer(s->check(game().network.selections.tickets().isEmpty(),"Missing opponents cannot be rematched from the podium"));
            c.getInput().pressKey(o->o.keyDrop);c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200);
        }
        VanillaSmash.LOG.info("WINNER_STAGE_NATIVE_CLIENT_TEST_PASSED");
    }
}
