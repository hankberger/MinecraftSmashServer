package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

/** Actual match completion, stock/forfeit distinctions, and native reward/balance rendering. */
@SuppressWarnings("UnstableApiUsage")
public final class PointsClientTest {
    private static VanillaSmash game(){return VanillaSmash.instance();}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void command(ClientGameTestContext c,String value){c.runOnClient(mc->mc.player.connection.sendCommand(value));}
    /** Accelerate known qualifying rounds in UI fixtures; eligibility boundaries are tested separately. */
    static void qualifyMatch() {
        game().battle.roundTicks=Math.max(game().battle.roundTicks,EconomyRules.MIN_MATCH_TICKS);
        game().battle.actors.values().forEach(f->{f.playedTicks=Math.max(f.playedTicks,EconomyRules.MIN_ACTIVE_TICKS);f.activeTicks=Math.max(f.activeTicks,EconomyRules.MIN_ACTIVE_TICKS);});
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("simulation-distance","5");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            c.getInput().resizeWindow(1280,720);c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().equals("PackConfirmScreen"),400);
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();c.waitTicks(35);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            var friends=new ArrayList<MatchmakingClientTest.Peer>();
            server.runOnServer(s->{for(int i=0;i<3;i++)friends.add(MatchmakingClientTest.Peer.join(s,"PointFriend"+i));});
            c.waitTicks(65);
            server.runOnServer(s->friends.forEach(f->game().uiPack.response(f.player(),new ServerboundResourcePackPacket(UiPack.ID,ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED))));
            command(c,"smash join");PackedMenuClientTest.click(c,31);
            server.runOnServer(s->game().choose(friends.getFirst().player(),FighterClass.ZOMBIE,VanillaSmash.Mode.DUEL));
            server.waitFor(s->game().match.phase()==MatchState.Phase.ACTIVE,300);
            c.getInput().holdKeyFor(o->o.keyShift,110);
            server.runOnServer(s->{
                check(game().actor(connection.getServerPlayer()).activeTicks>=100,"Native shielding counts as active participation");
                check(game().actor(friends.getFirst().player()).activeTicks==0,"Idle opponents do not accumulate active participation");
                qualifyMatch();game().match.finish(id,"Points fixture");
            });
            server.waitFor(s->game().points.account(id).balance()==75 && game().hub.results.book.result(id)!=null,200);
            var result=server.computeOnServer(s->game().hub.results.book.result(id));
            server.runOnServer(s->{
                check(game().points.account(friends.getFirst().player().getUUID()).balance()==50,"Losing a completed duel still awards points");
                game().hub.results.receive(result);game().points.record(result);
            });
            MatchmakingClientTest.winnerReady(c);c.waitTicks(40);
            c.runOnClient(mc->check(mc.gui.screen().getTitle().getString().contains("+75 Points"),"Results contain committed personal reward"));
            c.takeScreenshot("points-01-duel-reward");
            server.runOnServer(s->check(game().points.account(id).balance()==75,"Repeated result cannot pay twice"));
            MatchmakingClientTest.winnerAction(c,3);c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200);c.waitTicks(20);
            command(c,"smash points");c.waitTicks(30);
            server.runOnServer(s->check(game().hub.currentNotice(connection.getServerPlayer()).contains("75 Points"),"Points command survives the normal lobby HUD refresh"));
            c.takeScreenshot("points-02-lobby-balance");
            command(c,"smash join");c.waitTicks(40);c.takeScreenshot("points-03-picker-balance");
            c.runOnClient(mc->check(mc.gui.screen().getTitle().getString().contains("75"),"Picker shows earned balance"));
            PackedMenuClientTest.click(c,31);
            server.runOnServer(s->game().choose(friends.getFirst().player(),FighterClass.ALEX,VanillaSmash.Mode.DUEL));
            server.waitFor(s->game().match.phase()==MatchState.Phase.ACTIVE,300);
            server.runOnServer(s->game().leave(friends.getFirst().player()));
            server.waitFor(s->game().hub.results.book.result(id)!=null && game().points.receipt(game().hub.results.book.result(id).id(),id)!=null,200);
            server.runOnServer(s->{
                var r=game().hub.results.book.result(id);var quitter=friends.getFirst().player().getUUID();
                check(r.rows().stream().anyMatch(row->row.player().equals(quitter)&&row.forfeited()),"Live departure is recorded as forfeit");
                check(game().points.receipt(r.id(),quitter).total()==0 && game().points.account(quitter).balance()==50,"Leaving early awards no points");
                check(game().points.account(id).balance()==75 && game().points.receipt(r.id(),id).total()==0,"Instant quit/rematch cannot farm winner points");
                check(game().points.reward(r,id).contains("Short match"),"Result explains short-match exclusion");
            });
            MatchmakingClientTest.winnerReady(c);MatchmakingClientTest.winnerAction(c,3);
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200);
            server.runOnServer(s->{
                game().choose(connection.getServerPlayer(),FighterClass.STEVE,VanillaSmash.Mode.MATCH);
                for(var friend:friends)game().choose(friend.player(),FighterClass.SKELETON,VanillaSmash.Mode.MATCH);
            });
            server.waitFor(s->game().match.phase()==MatchState.Phase.ACTIVE,300);
            server.runOnServer(s->{
                qualifyMatch();
                var out=friends.getFirst().player();for(int i=0;i<3;i++)game().match.ringOut(out.getUUID());
                game().battle.eliminate(game().actor(out));game().leave(out);
                check(!game().battle.actors.get(out.getUUID()).forfeited,"Leaving after final stock is legitimate completion");
                game().match.finish(id,"FFA points fixture");
            });
            server.waitFor(s->game().points.account(id).balance()==150 && game().battle==null,200);
            server.runOnServer(s->check(game().points.account(friends.getFirst().player().getUUID()).balance()==100,"Eliminated player receives points even after returning to lobby"));
            MatchmakingClientTest.winnerReady(c);c.waitTicks(30);c.takeScreenshot("points-04-ffa-reward");
            c.getInput().resizeWindow(960,720);c.waitTicks(10);c.takeScreenshot("points-05-ffa-four-three");
            MatchmakingClientTest.winnerAction(c,3);c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200);
            server.runOnServer(s->{
                game().points.close();game().points.start(s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("smash/points.db"));
                check(game().points.account(id).balance()==150 && game().points.account(id).matches()==2,"Balance reloads from persistent account after store restart");
                game().choose(connection.getServerPlayer(),FighterClass.STEVE,VanillaSmash.Mode.PRACTICE);
            });
            server.waitFor(s->game().match.phase()==MatchState.Phase.ACTIVE,300);
            server.runOnServer(s->game().match.finish(id,"Practice fixture"));
            server.waitFor(s->game().battle==null,120);
            server.runOnServer(s->{check(game().points.account(id).balance()==150,"Practice cannot farm currency");friends.forEach(MatchmakingClientTest.Peer::leave);});
        }
        VanillaSmash.LOG.info("POINTS_NATIVE_CLIENT_TEST_PASSED: completed duels/FFA, duplicate delivery, forfeit versus elimination, persistent reload, practice exclusion, reward and balance UI");
    }
}
