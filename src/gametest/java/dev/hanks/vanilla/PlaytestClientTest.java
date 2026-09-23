package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import dev.hanks.network.PartyBook;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Native pack/menu/input regression for the two-player playtest, plus swept-impact fixtures. */
@SuppressWarnings("UnstableApiUsage")
public final class PlaytestClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean value,String message) { if (!value) throw new AssertionError(message); }
    private static void command(ClientGameTestContext c,String value) { c.runOnClient(mc->mc.player.connection.sendCommand(value)); }
    private static void place(UUID id,double x,double y,double targetX,double targetY) {
        var b=game().battle; var f=b.actors.get(id); b.reset(f,x,y); b.reset(b.dummy(),targetX,targetY); f.facing=1;
        b.companions.tick();
    }
    public static void run(ClientGameTestContext c) {
        var props=new Properties(); props.setProperty("online-mode","false"); props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","8"); props.setProperty("simulation-distance","5"); props.setProperty("allow-flight","true");
        try (var server=c.worldBuilder().createServer(props); var connection=server.connect()) {
            c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc->{mc.options.fov().set(70);mc.options.guiScale().set(2);mc.resizeGui();});
            c.waitFor(mc->mc.gui.screen()!=null && mc.gui.screen().getClass().getSimpleName().equals("PackConfirmScreen"),400);
            MatchmakingClientTest.click(c,"Proceed");
            server.waitFor(s->game().uiPack.ready(connection.getServerPlayer()),1200);
            connection.waitForChunksRender(); c.waitTicks(35);
            var id=server.computeOnServer(s->connection.getServerPlayer().getUUID());
            var friend=new AtomicReference<MatchmakingClientTest.Peer>();
            server.runOnServer(s->friend.set(MatchmakingClientTest.Peer.join(s,"PlaytestFriend")));
            c.waitTicks(70);
            server.runOnServer(s->{
                game().uiPack.response(friend.get().player(),new ServerboundResourcePackPacket(UiPack.ID,ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                connection.getServerPlayer().teleportTo(12,90,0);
            });
            server.waitFor(s->Math.abs(connection.getServerPlayer().getZ()-LobbyRules.SPAWN_Z)<.01,60);
            server.runOnServer(s->check(Math.abs(connection.getServerPlayer().getX()-LobbyRules.SPAWN_X)<.01,"Lobby fall returns to original spawn"));
            command(c,"smash party"); MatchmakingClientTest.click(c,"Create party");
            MatchmakingClientTest.click(c,"Invite player"); MatchmakingClientTest.click(c,"PlaytestFriend");
            MatchmakingClientTest.click(c,"Back");
            c.runOnClient(mc->check(mc.gui.screen().getTitle().getString().equals("Party"),"Invite Back returns to Party"));
            server.runOnServer(s->{
                game().hub.partyCommand(friend.get().player(),"accept",connection.getServerPlayer().getPlainTextName());
                check(!game().stage.active(friend.get().player()) && !game().stage.active(connection.getServerPlayer()),"Accepting a party cannot pull either member into Play");
                game().hub.selectMode(friend.get().player(),VanillaSmash.Mode.DUEL);
                check(game().hub.parties.view(id).phase()==PartyBook.Phase.IDLE && !game().stage.active(friend.get().player()),"Rejected member action never opens an unrelated Play screen");
            });
            MatchmakingClientTest.click(c,"Manage party"); MatchmakingClientTest.click(c,"Back");
            c.runOnClient(mc->check(mc.gui.screen().getTitle().getString().equals("Party"),"Manage Back returns to Party"));
            MatchmakingClientTest.click(c,"Back"); c.waitFor(mc->mc.gui.screen()==null,100);
            server.runOnServer(s->check(connection.getServerPlayer().level().dimension().equals(MvpWorlds.LOBBY),"Root Party Back stays in lobby"));
            command(c,"smash join"); PackedMenuClientTest.click(c,21);
            server.runOnServer(s->check(game().stage.active(connection.getServerPlayer()) && game().stage.active(friend.get().player()),"Leader mode opens both stages"));
            c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE); c.waitTicks(15);
            server.runOnServer(s->{
                check(!game().stage.active(connection.getServerPlayer()) && !game().stage.active(friend.get().player()),"Back closes both party stages");
                game().hub.exitPicker(friend.get().player());
                check(!game().stage.active(connection.getServerPlayer()) && !game().stage.active(friend.get().player()),"Stale peer Back cannot invert party screens");
            });
            command(c,"smash join"); PackedMenuClientTest.click(c,21); PackedMenuClientTest.click(c,31);
            server.runOnServer(s->{
                check(game().hub.parties.view(id).readyCount()==1 && !game().network.selected(id),"First ready never queues a partial party");
                game().hub.pickerAction(friend.get().player());
                check(game().network.selected(id) && game().network.selected(friend.get().player().getUUID()),"Both ready queue together");
                game().hub.exitPicker(friend.get().player());
                check(!game().network.selected(id) && !game().stage.active(connection.getServerPlayer()),"Peer Back cancels queue and returns everyone");
            });
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200);
            command(c,"smash join"); PackedMenuClientTest.click(c,20); PackedMenuClientTest.click(c,31);
            server.runOnServer(s->game().hub.pickerAction(friend.get().player()));
            c.waitFor(mc->MvpWorlds.battle(mc.level)&&mc.getCameraEntity()!=mc.player,300); c.waitTicks(45);
            int peerId=server.computeOnServer(s->friend.get().player().getId());
            var fighterIds=server.computeOnServer(s->game().battle.actors.values().stream().map(a->a.body.getId()).toList());
            c.runOnClient(mc->{
                check(mc.level.getEntity(peerId)==null,"Peer controller and equipment never render at the camera");
                check(mc.player.getZ()>40 && mc.getCameraEntity().getZ()<mc.player.getZ()-5,"Own controller remains behind all camera positions");
                check(fighterIds.stream().allMatch(i->mc.level.getEntity(i)!=null),"Both actual fighters are visible");
            });
            c.takeScreenshot("playtest-two-player-camera");
            server.runOnServer(s->{game().endRound(false);game().returnFromPicker(connection.getServerPlayer());game().returnFromPicker(friend.get().player());game().hub.parties.leave(friend.get().player().getUUID());});
            c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200); c.waitTicks(15);
            c.runOnClient(mc->check(mc.level.getEntity(peerId)!=null,"Peer player becomes visible again in lobby"));
            server.runOnServer(s->friend.get().leave());
            for (var kind:FighterClass.values()) {
                server.runOnServer(s->{game().choices.put(id,kind);game().begin(List.of(connection.getServerPlayer()),VanillaSmash.Mode.SANDBOX,BattleStage.SKYBOUND_GROVE);});
                c.waitFor(mc->MvpWorlds.battle(mc.level)&&mc.getCameraEntity()!=mc.player,300); c.waitTicks(30);
                server.runOnServer(s->place(id,0,81,1.6,81)); c.waitTicks(5); c.getInput().pressMouse(0);
                server.waitFor(s->game().battle.dummy().state.percent>0,30);
                server.runOnServer(s->check(game().battle.actors.get(id).state.move.melee(),kind+" basic click is melee"));
                if (kind==FighterClass.SKELETON) {
                    server.runOnServer(s->place(id,0,81,4,81)); c.getInput().pressKey(o->o.keySwapOffhand); c.waitTicks(12);
                    server.runOnServer(s->{check(game().battle.actors.get(id).x<-.9,"Scatter Retreat creates actual distance");check(game().battle.dummy().state.percent>0,"Retreat also threatens pursuers");});
                }
                if (kind==FighterClass.STEVE || kind==FighterClass.VILLAGER) server.runOnServer(s->impactFixtures(id,kind));
                if (kind==FighterClass.ZOMBIE) {
                    server.runOnServer(s->place(id,0,81,1.0,81)); c.waitTicks(6); c.getInput().pressMouse(0);
                    server.waitFor(s->game().battle.dummy().state.percent>=9,40);
                    server.runOnServer(s->check(game().battle.companions.buddies.get(id).body.isBaby(),"Follow-up belongs to a real baby zombie model"));
                    c.takeScreenshot("playtest-zombie-duo");
                    server.runOnServer(s->place(id,0,81,4,81)); c.waitTicks(5); c.getInput().pressKey(o->o.keySwapOffhand);
                    server.waitFor(s->game().battle.dummy().state.percent==9,35);
                    server.runOnServer(s->{
                        var b=game().battle; var buddy=b.companions.buddies.get(id);
                        check(buddy.x>1,"F throws the partner forward");
                        b.companions.hurt(buddy,b.dummy(),FighterMoves.special(FighterClass.STEVE,false,false));
                        buddy.immuneUntil=0;
                        b.companions.hurt(buddy,b.dummy(),FighterMoves.arrow(20));
                        check(buddy.body==null && buddy.health<=0,"Partner can be knocked out without consuming a stock");
                        check(!b.companions.available(b.actors.get(id)),"No invisible partner attacks during knockout");
                    });
                    c.waitTicks(ZombieCompanions.RETURN_TICKS+5);
                    server.runOnServer(s->{
                        var b=game().battle;check(b.companions.buddies.get(id).body!=null,"Baby regroups after five seconds");
                        place(id,0,81,1,81);
                    });
                    c.waitTicks(50); server.runOnServer(s->check(game().battle.dummy().state.percent==0,"Companion never attacks without player input"));
                    server.runOnServer(s->game().battle.ringOut(game().battle.actors.get(id)));
                    c.waitTicks(RespawnRules.FLOAT_TICKS+8);
                    server.runOnServer(s->check(game().battle.companions.buddies.get(id).body!=null,"Partner returns with owner's next stock"));
                }
                server.runOnServer(s->place(id,0,88,12,81));
                c.getInput().holdKey(o->o.keyDown); c.waitTicks(3); c.getInput().pressMouse(0); c.waitTicks(5);
                server.runOnServer(s->{var f=game().battle.actors.get(id);check(f.state.move.aim()==AttackDirection.DOWN,"Native S+click chooses down attack");check(!f.recovery.fastFalling(),"Down attack suppresses fast-fall throughout this S press");});
                c.getInput().releaseKey(o->o.keyDown);
                var oldBattle=server.computeOnServer(s->game().battle);
                server.runOnServer(s->{game().endRound(false);game().returnFromPicker(connection.getServerPlayer());check(oldBattle.companions.buddies.isEmpty(),"Battle cleanup removes every partner");});
                c.waitFor(mc->mc.level.dimension().equals(MvpWorlds.LOBBY),200);c.waitTicks(8);
            }
            VanillaSmash.LOG.info("PLAYTEST_CLIENT_TEST_PASSED: party back/ready/queue, visible fighters/hidden controllers, lobby rescue, five melee basics, down chords, impact explosives, Skeleton retreat, Zombie duo lifecycle");
        }
    }
    private static void impactFixtures(UUID id,FighterClass kind) {
        var b=game().battle;var f=b.actors.get(id);var d=b.dummy();
        for (boolean air:new boolean[]{false,true}) {
            place(id,0,air?90:81,2,air?90:81);
            f.state.attackDirection=1;
            if (kind==FighterClass.STEVE) {
                f.state.move=FighterMoves.special(kind,AttackDirection.DOWN,air,false);b.objects.kits.spawn(f);
                var prop=b.objects.kits.props.getFirst(); prop.velocity=new Vec3(3,0,0);b.objects.kits.tick();
                check(b.objects.kits.props.isEmpty() && d.state.percent==16,"TNT detonates on swept fighter contact immediately, air="+air);
            } else {
                b.objects.bell(f);var bell=b.objects.bells.get(id);bell.velocity=new Vec3(3,0,0);b.objects.tick();
                check(!b.objects.hasBell(f) && d.state.percent==12,"Bell detonates on swept fighter contact immediately, air="+air);
            }
        }
        place(id,0,83,3,83);var wall=new BlockPos(1,84,0);var previous=b.level.getBlockState(wall);
        b.level.setBlock(wall,Blocks.STONE.defaultBlockState(),2);
        try {
            f.state.attackDirection=1;
            if (kind==FighterClass.STEVE) {
                f.state.move=FighterMoves.special(kind,AttackDirection.DOWN,true,false);b.objects.kits.spawn(f);
                b.objects.kits.props.getFirst().velocity=new Vec3(4,0,0);b.objects.kits.tick();
                check(!b.objects.kits.props.isEmpty(),"Wall bounces TNT before fighter contact");
            } else {b.objects.bell(f);b.objects.bells.get(id).velocity=new Vec3(4,0,0);b.objects.tick();check(b.objects.hasBell(f),"Wall bounces bell before fighter contact");}
            check(d.state.percent==0,"Impact explosions cannot hit through terrain");
        } finally {b.level.setBlock(wall,previous,2);b.objects.remove(f);}
    }
}
