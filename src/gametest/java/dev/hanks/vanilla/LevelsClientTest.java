package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.world.entity.Entity;

/** Actual completed match, live reward animation and native menu/lobby interactions. */
@SuppressWarnings("UnstableApiUsage")
public final class LevelsClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void run(ClientGameTestContext c){
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()){
            c.getInput().resizeWindow(1280,720);c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);connection.waitForChunksRender();c.waitTicks(35);
            var friend=new AtomicReference<MatchmakingClientTest.Peer>();server.runOnServer(s->friend.set(MatchmakingClientTest.Peer.join(s,"XpRival")));c.waitTicks(65);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            server.runOnServer(s->{
                game().uiPack.response(friend.get().player(),new ServerboundResourcePackPacket(UiPack.ID,ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                game().choose(connection.getServerPlayer(),FighterClass.STEVE,VanillaSmash.Mode.DUEL);
                game().choose(friend.get().player(),FighterClass.ZOMBIE,VanillaSmash.Mode.DUEL);
            });
            server.waitFor(s->game().match.phase()==MatchState.Phase.ACTIVE,300);
            server.runOnServer(s->{PointsClientTest.qualifyMatch();game().match.finish(id,"XP fixture");});
            server.waitFor(s->game().hub.results.scene.session(id)!=null&&game().hub.results.scene.session(id).ready(),300);
            MatchmakingClientTest.winnerReady(c);
            var container=c.computeOnClient(mc->mc.player.containerMenu.containerId);
            c.getInput().setCursorPos(80,80);
            server.waitFor(s->game().hub.results.scene.session(id).progression.shown()>10,120);
            c.takeScreenshot("levels-01-filling");
            server.waitFor(s->game().hub.results.scene.session(id).progression.shown()==100,120);
            c.waitTicks(3);c.takeScreenshot("levels-02-level-up");
            server.waitFor(s->game().hub.results.scene.session(id).progression.finished(),150);
            c.waitTicks(5);c.takeScreenshot("levels-03-complete");
            c.runOnClient(mc->{check(mc.player.containerMenu.containerId==container,"Animation retains the result container");check(Math.abs(mc.mouseHandler.xpos()-80)<1&&Math.abs(mc.mouseHandler.ypos()-80)<1,"XP reveal never recenters the cursor");});
            var result=server.computeOnServer(s->game().hub.results.book.result(id));
            server.runOnServer(s->{
                check(game().points.progress(id).total()==140&&game().points.progress(id).level()==2,"A completed win grants XP and the first level");
                check(game().points.progress(friend.get().player().getUUID()).total()==100,"A completed loss also levels up");
                game().points.settle(result).join();check(game().points.progress(id).total()==140,"Duplicate receipt cannot add XP");
            });
            c.getInput().resizeWindow(960,720);c.waitTicks(8);c.takeScreenshot("levels-04-four-three");
            c.getInput().resizeWindow(1920,1080);c.runOnClient(mc->{mc.options.guiScale().set(3);mc.resizeGui();});c.waitTicks(8);c.takeScreenshot("levels-05-large-gui");
            var displays=server.computeOnServer(s->game().hub.results.scene.session(id).progression.entities());
            MatchmakingClientTest.winnerAction(c,3);c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY)&&mc.gui.screen()==null,150);
            server.runOnServer(s->check(displays.stream().allMatch(Entity::isRemoved),"Leaving results cleans up every XP entity"));
            c.waitTicks(30);c.runOnClient(mc->check(mc.player.experienceLevel==2&&Math.abs(mc.player.experienceProgress-40f/150)<.001,"Lobby XP bar shows account progress"));
            c.takeScreenshot("levels-06-lobby");
            c.runOnClient(mc->mc.player.connection.sendCommand("smash level"));
            c.waitFor(mc->mc.gui.screen()!=null&&mc.gui.screen().getTitle().getString().equals("Your level"),100);
            c.runOnClient(mc->check(mc.font.split(WidePickerClientTest.body(mc.gui.screen()).getMessage(),LevelCanvas.WIDTH).size()==LevelCanvas.ROWS,"Level profile fits the canvas without wrapping"));
            c.takeScreenshot("levels-07-profile");
            var exit=c.computeOnClient(mc->{var b=PartyMenuClientTest.widget(mc.gui.screen(),net.minecraft.client.gui.components.Button.class,"Back to lobby");double scale=mc.getWindow().getGuiScale();return new double[]{(b.getX()+75)*scale,(b.getY()+10)*scale};});
            c.getInput().setCursorPos(exit[0],exit[1]);c.getInput().pressMouse(0);c.waitFor(mc->mc.gui.screen()==null,100);
            server.runOnServer(s->game().hub.results.show(connection.getServerPlayer()));MatchmakingClientTest.winnerReady(c);
            server.waitFor(s->game().hub.results.scene.session(id).progression.finished(),100);
            server.runOnServer(s->check(game().hub.results.scene.session(id).progression.shown()==140,"Reopening shows final reward without another count-up"));
            MatchmakingClientTest.winnerAction(c,3);c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),150);
            server.runOnServer(s->{
                for(int i=0;i<3;i++)game().points.settle(WinnerStageClientTest.result(id,FighterClass.STEVE,false)).join();
                check(game().points.progress(id).total()==695,"Milestone fixture starts just below Level 5");
                game().hub.results.receive(WinnerStageClientTest.result(id,FighterClass.STEVE,false));
            });
            MatchmakingClientTest.winnerReady(c);
            server.waitFor(s->game().hub.results.scene.session(id).progression.shown()==700,120);c.waitTicks(4);
            c.takeScreenshot("levels-08-milestone");
            server.runOnServer(s->{
                var card=game().hub.results.scene.session(id).progression;
                check(!card.finished(),"Results remain actionable during the reveal");
                check(card.entities().stream().anyMatch(e->e instanceof net.minecraft.world.entity.Display.TextDisplay d&&d.getText().getString().contains("Brawler unlocked")),"A milestone announces the newly earned title");
            });
            MatchmakingClientTest.winnerAction(c,3);c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),150);
            server.runOnServer(s->{
                check(game().points.progress(id).total()==880,"Leaving during a level-up retains the entire committed award");
                var shortMatch=WinnerStageClientTest.result(id,FighterClass.STEVE,false);
                var activity=new HashMap<UUID,Wire.Participation>();shortMatch.rows().forEach(r->activity.put(r.player(),new Wire.Participation(20,20)));
                game().hub.results.receive(new Wire.MatchResult(shortMatch.id(),shortMatch.mode(),shortMatch.winner(),shortMatch.roster(),shortMatch.rows(),new Wire.MatchEvidence(20,activity)));
            });
            MatchmakingClientTest.winnerReady(c);server.waitFor(s->game().hub.results.scene.session(id).progression.finished(),100);
            server.runOnServer(s->check(game().hub.results.scene.session(id).progression.entities().stream().anyMatch(e->e instanceof net.minecraft.world.entity.Display.TextDisplay d&&d.getText().getString().equals("No XP · Short match")),"Excluded rewards are explained and do not animate fake earnings"));
            MatchmakingClientTest.winnerAction(c,3);c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),150);
            server.runOnServer(s->{
                game().points.close();game().points.start(s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("smash/points.db"));
                check(game().points.progress(id).total()==880,"XP survives persistent-store restart");friend.get().leave();
            });
        }
        VanillaSmash.LOG.info("LEVELS_NATIVE_CLIENT_TEST_PASSED");
    }
}
