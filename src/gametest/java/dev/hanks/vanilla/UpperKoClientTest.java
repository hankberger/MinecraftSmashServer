package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Full-height native Space sequences from the highest platform, followed by a real launch KO. */
@SuppressWarnings("UnstableApiUsage")
public final class UpperKoClientTest {
    private record Fixture(BattleStage stage,FighterClass fighter) { }
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var capture=new AtomicBoolean();var peak=new AtomicReference<>(0d);var playerId=new AtomicReference<UUID>();
        ServerTickEvents.END_SERVER_TICK.register(s->{
            if(!capture.get() || game().battle==null)return;
            var f=game().battle.actors.get(playerId.get());if(f!=null)peak.accumulateAndGet(f.y,Math::max);
        });
        var props=new Properties();props.setProperty("online-mode","false");props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6");props.setProperty("allow-flight","true");
        try(var server=c.worldBuilder().createServer(props);var connection=server.connect()) {
            MatchmakingClientTest.click(c,"Proceed");server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender();c.waitTicks(35);
            playerId.set(server.computeOnServer(s->connection.getServerPlayer().getUUID()));
            var cases=new ArrayList<Fixture>();
            for(var stage:BattleStage.values())cases.add(new Fixture(stage,FighterClass.IRON_GOLEM));
            for(var fighter:FighterClass.values())if(fighter!=FighterClass.IRON_GOLEM)cases.add(new Fixture(BattleStage.CLOUDSPIRE,fighter));
            for(var fixture:cases) {
                server.runOnServer(s->{
                    var p=connection.getServerPlayer();
                    game().choices.put(p.getUUID(),fixture.fighter);
                    game().begin(List.of(p),VanillaSmash.Mode.PRACTICE,fixture.stage);
                    // Skip the presentation countdown, keeping real stocks and actor physics.
                    for(int i=0;i<MatchState.COUNTDOWN_TICKS;i++)game().match.tick(Map.of());
                    game().battle.dummySpar=false;
                });
                c.waitFor(mc->MvpWorlds.battle(mc.level)&&mc.getCameraEntity()!=mc.player,200);c.waitTicks(6);
                server.runOnServer(s->{
                    var b=game().battle;b.reset(game().actor(connection.getServerPlayer()),.5,fixture.stage.respawnLanding());
                    b.reset(b.dummy(),fixture.stage.dummyX(),81);peak.set(0d);capture.set(true);
                });
                c.getInput().holdKeyFor(o->o.keyJump,10);c.waitTicks(1);
                c.getInput().holdKeyFor(o->o.keyJump,10);c.waitTicks(1);
                c.getInput().holdKeyFor(o->o.keyJump,5);c.waitTicks(16);
                server.runOnServer(s->{
                    var f=game().actor(connection.getServerPlayer());
                    check(f.recoveries==1,fixture+" uses jump, double jump, then recovery");
                    check(f.state.falls==0 && game().match.stocks(f.id)==3,fixture+" full jump sequence cannot lose a stock");
                    if(fixture.fighter==FighterClass.IRON_GOLEM)check(peak.get()>fixture.stage.blastTop(),"Fixture crosses the old fatal top boundary: "+fixture+" peak="+peak.get());
                });
                server.waitFor(s->game().actor(connection.getServerPlayer()).grounded,120);
                server.runOnServer(s->{
                    check(game().actor(connection.getServerPlayer()).state.falls==0,fixture+" lands without a respawn");
                    capture.set(false);
                    VanillaSmash.LOG.info("UPPER_KO_JUMP_SAFE stage={} fighter={} peak={}",fixture.stage,fixture.fighter,peak.get());
                    game().leave(connection.getServerPlayer());
                });
                c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200);c.waitTicks(3);
            }
            server.runOnServer(s->{
                game().choices.put(playerId.get(),FighterClass.STEVE);
                game().begin(List.of(connection.getServerPlayer()),VanillaSmash.Mode.PRACTICE,BattleStage.SKYBOUND_GROVE);
                for(int i=0;i<MatchState.COUNTDOWN_TICKS;i++)game().match.tick(Map.of());
                game().battle.dummySpar=false;
            });
            c.waitFor(mc->MvpWorlds.battle(mc.level),200);c.waitTicks(5);
            server.runOnServer(s->{
                var b=game().battle;var f=game().actor(connection.getServerPlayer());
                b.reset(f,.5,b.stage.blastTop()-1);b.reset(b.dummy(),.5,b.stage.blastTop()-3);f.state.percent=180;
                check(b.hit(b.dummy(),f,1,FighterMoves.light(FighterClass.IRON_GOLEM,AttackDirection.UP,true)).launch()!=null,"Opponent applies a real upward hit");
            });
            server.waitFor(s->game().match.stocks(playerId.get())==2,40);
            server.runOnServer(s->{
                var f=game().actor(connection.getServerPlayer());
                check(f.state.falls==1&&game().battle.dummy().state.knockouts==1,"Upward launch still costs one stock and credits the attacker");
                game().battle.reset(f,game().battle.stage.blastRight()+1,110);
            });
            server.waitFor(s->game().match.stocks(playerId.get())==1,40);
            server.runOnServer(s->game().leave(connection.getServerPlayer()));
        } finally { capture.set(false); }
        VanillaSmash.LOG.info("UPPER_KO_NATIVE_CLIENT_TEST_PASSED");
    }
}
