package dev.hanks.vanilla;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.InputConstants;
import dev.hanks.network.PartyBook;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/** One rendered vanilla-input client plus server peers using Minecraft's own mock-player connection pattern. */
@SuppressWarnings("UnstableApiUsage")
public final class MatchmakingClientTest {
    record Peer(ServerPlayer player, EmbeddedChannel channel) {
        static Peer join(MinecraftServer server, String name) {
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
            var player = new ServerPlayer(server, server.getLevel(MvpWorlds.LOBBY), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            var channel = new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie); return new Peer(player, channel);
        }
        void leave() { player.connection.onDisconnect(new DisconnectionDetails(Component.literal("Test complete"))); channel.finishAndReleaseAll(); }
    }
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void menuReady(ClientGameTestContext c) { c.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getTitle().getString().startsWith("Smash"), 300); }
    // Dialog bodies use a scrolling event container, which Fabric's flat button helper does not visit.
    private static net.minecraft.client.gui.components.Button findButton(net.minecraft.client.gui.components.events.GuiEventListener node, String label) {
        if (node instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(label)) return button;
        if (node instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)
            for (var child : container.children()) { var found = findButton(child, label); if (found != null) return found; }
        return null;
    }
    public static void click(ClientGameTestContext c, String label) {
        if(c.computeOnClient(mc -> mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            c.waitFor(mc -> mc.player.containerMenu.slots.stream().anyMatch(slot -> slot.getItem().getHoverName().getString().equals(label)),200);
            var point=c.computeOnClient(mc -> {
                var screen=(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)mc.gui.screen();
                var slot=screen.getMenu().slots.stream().filter(s -> s.getItem().getHoverName().getString().equals(label)).findFirst().orElseThrow();
                // Generic 9x3 is 176x168 GUI pixels; native mouse coordinates are window pixels.
                double scale=mc.getWindow().getGuiScale();
                return new double[]{((screen.width-176)/2.0+slot.x+8)*scale,((screen.height-168)/2.0+slot.y+8)*scale};
            });
            c.getInput().setCursorPos(point[0],point[1]); c.getInput().pressMouse(0); c.waitTicks(5); return;
        }
        c.waitFor(mc -> mc.gui.screen() != null && findButton(mc.gui.screen(), label) != null, 200);
        c.runOnClient(mc -> findButton(mc.gui.screen(), label).onPress(new net.minecraft.client.input.MouseButtonInfo(0, 0)));
        c.waitTicks(3);
    }
    private static void command(ClientGameTestContext c, String command) { c.runOnClient(mc -> mc.player.connection.sendCommand(command)); }
    private static void stageReady(ClientGameTestContext c) { c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.getCameraEntity() == mc.player && mc.gui.screen() == null, 400); }
    public static void winnerReady(ClientGameTestContext c) {
        c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.SHOWCASE) && mc.getCameraEntity()!=mc.player && mc.gui.screen()!=null,400);
        c.waitTicks(5);
    }
    public static void winnerAction(ClientGameTestContext c, int index) {
        if(c.computeOnClient(mc->mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
            var point=c.computeOnClient(mc->{double scale=mc.getWindow().getGuiScale();
                return new double[]{((mc.gui.screen().width-176)/2+52)*scale,
                        ((mc.gui.screen().height-222)/2+(index==3?205:147+18*index))*scale};});
            c.getInput().setCursorPos(point[0],point[1]);c.getInput().pressMouse(0);c.waitTicks(8);
        } else click(c,new String[]{"Rematch","Play again","Change fighter","Lobby"}[index]);
    }
    private static void ready(ClientGameTestContext c, int index) { stageReady(c); c.getInput().pressKey(InputConstants.KEY_1 + index); c.waitTicks(22); c.getInput().pressMouse(1); }
    public static void run(ClientGameTestContext c) {
        var properties = new Properties(); properties.setProperty("online-mode","false"); properties.setProperty("server-ip","127.0.0.1");
        properties.setProperty("view-distance","6"); properties.setProperty("simulation-distance","5"); properties.setProperty("allow-flight","true");
        try (var server = c.worldBuilder().createServer(properties)) {
            var friend = new AtomicReference<Peer>(); var solo1 = new AtomicReference<Peer>(); var solo2 = new AtomicReference<Peer>();
            try (var connection = server.connect()) {
                connection.waitForChunksRender(); c.getInput().resizeWindow(1280,720);
                c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui(); });
                c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY),300);
                server.runOnServer(s -> {
                    friend.set(Peer.join(s,"Friend")); solo1.set(Peer.join(s,"SoloOne")); solo2.set(Peer.join(s,"SoloTwo"));
                    game().hub.selectMode(friend.get().player, VanillaSmash.Mode.DUEL);
                    check(!game().stage.active(friend.get().player) && !game().hub.available(friend.get().player), "Pending lobby arrival cannot open a stage that spawn initialization would erase");
                });
                server.waitFor(s -> Math.abs(friend.get().player.getY()-101) < .1 && Math.abs(solo2.get().player.getY()-101) < .1,150);
                c.waitTicks(25); command(c,"smash join"); menuReady(c); c.takeScreenshot("match-01-mode-menu");
                MatchmakingClientTest.click(c,"Create party"); menuReady(c); MatchmakingClientTest.click(c,"Invite player");
                c.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getTitle().getString().equals("Invite player"));
                c.takeScreenshot("match-02-invite-menu"); MatchmakingClientTest.click(c,"Friend"); menuReady(c);
                server.runOnServer(s -> {
                    var p = connection.getServerPlayer();
                    check(game().hub.parties.view(p.getUUID()).members().size() == 1,"Invitation does not force a player into a party");
                    game().hub.partyCommand(friend.get().player,"accept",p.getPlainTextName());
                    check(game().hub.parties.view(p.getUUID()).members().size() == 2,"Accepted friend joins party");
                });
                menuReady(c); c.takeScreenshot("match-03-party-menu"); MatchmakingClientTest.click(c,"1v1"); stageReady(c);
                var otherModel = new java.util.concurrent.atomic.AtomicInteger();
                server.runOnServer(s -> {
                    var a = game().stage.session(connection.getServerPlayer().getUUID()); var b = game().stage.session(friend.get().player.getUUID());
                    check(a.room != b.room && Math.abs(a.origin()-b.origin()) >= 1024,"Each party member gets their own stage"); otherModel.set(b.preview.getId());
                    check(game().network.selections.tickets().isEmpty(),"No selection is published while members browse");
                });
                c.runOnClient(mc -> check(mc.level.getEntity(otherModel.get()) == null,"Other party member's preview is not visible"));
                ready(c,1); menuReady(c); c.takeScreenshot("match-04-one-ready");
                server.runOnServer(s -> {
                    check(game().hub.parties.view(connection.getServerPlayer().getUUID()).readyCount() == 1,"Character confirmation readies only that member");
                    check(game().battle == null && game().match.queue().isEmpty(),"No partial ready group is matched");
                });
                MatchmakingClientTest.click(c,"Change fighter"); stageReady(c); ready(c,2); menuReady(c);
                server.runOnServer(s -> { game().stage.selectSlot(friend.get().player,3); game().stage.confirm(friend.get().player); });
                c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.getCameraEntity() != mc.player,300);
                server.runOnServer(s -> {
                    check(game().match.roster().size() == 2 && !game().match.practice() && game().battle.dummy() == null,"1v1 starts two humans without a dummy");
                    check(game().actor(connection.getServerPlayer()).kind == FighterClass.ZOMBIE && game().actor(friend.get().player).kind == FighterClass.SKELETON,"Ready selections reach the match");
                    check(game().actor(connection.getServerPlayer()).x < 0 && game().actor(friend.get().player).x > 0,"Duel starts on opposite sides");
                    check(game().battle.stage==BattleStage.select(game().hub.reservation().id(),false),"Local matchmaking uses the reservation's shared stage draw");
                    check(game().viewers.values().stream().allMatch(v->v.player().level()==game().battle.level),"Both duel players enter the selected stage");
                });
                c.takeScreenshot("match-05-duel");
                server.waitFor(s -> game().match.phase() == MatchState.Phase.ACTIVE,200);
                server.runOnServer(s -> {
                    var a = game().actor(connection.getServerPlayer()); var b = game().actor(friend.get().player);
                    check(a.slot != b.slot && a.color() != b.color() && !a.marker.isRemoved(), "Player slots and HUD entities remain owned and distinct");
                    var jumpInput = new net.minecraft.world.entity.player.Input(false,false,false,false,true,false,false);
                    game().battle.reset(a,game().battle.stage.edge(1)+.2,81); a.vx=.5; a.owner.setLastClientInput(net.minecraft.world.entity.player.Input.EMPTY);
                    game().battle.tick(); check(!a.grounded,"Walked off the stage edge");
                    a.owner.setLastClientInput(jumpInput); game().battle.tick();
                    check(a.vy>.8 && a.recovery.available(),"Edge grace jumps without spending the air jump");
                    game().battle.reset(a,0,81.3); a.vy=-.6; a.recovery.recover(false); a.owner.setLastClientInput(jumpInput);
                    game().battle.tick(); check(a.grounded && a.jump.pending(game().ticks),"Early jump waits for landing after all air options are spent");
                    a.owner.setLastClientInput(net.minecraft.world.entity.player.Input.EMPTY); game().battle.tick();
                    check(a.y>81 && Math.abs(a.vy-(MovementRules.SHORT_HOP_CUTOFF-MovementRules.RISE_GRAVITY))<.001 && a.recovery.available(),
                            "Released buffered landing jump fires as a short hop and restores the air budget");
                    game().battle.reset(a,-9.5,81); game().battle.reset(b,10.5,81);
                    var move = FighterMoves.light(a.kind, AttackDirection.FORWARD, false);
                    game().battle.hit(a,b,1,move); check(a.damageDealt == move.damage(), "Successful hits count actual damage");
                    game().battle.hit(a,b,1,move); check(a.damageDealt == move.damage(), "Invulnerable hits do not inflate damage stats");
                    b.state.hitImmuneUntil = 0; b.state.guardUntil = game().ticks + 10;
                    game().battle.hit(a,b,1,move); check(a.damageDealt == move.damage(), "Blocks do not count as damage dealt");
                    for(int i=0;i<3;i++) { b.state.lastAttacker=a.id; b.state.lastHitAt=game().ticks; game().battle.ringOut(b); }
                });
                server.waitFor(s -> game().match.phase() == MatchState.Phase.RESULTS);
                server.runOnServer(s -> { check(game().match.winner().equals(connection.getServerPlayer().getUUID()),"Duel awards the surviving player"); game().endRound(true); });
                winnerReady(c);
                c.takeScreenshot("experience-01-results");
                server.runOnServer(s -> {
                    var r = game().hub.results.book.result(connection.getServerPlayer().getUUID());
                    check(r.rows().getFirst().knockouts()==3 && r.rows().getFirst().damage()>0, "Results retain KOs and damage after arena cleanup");
                });
                winnerAction(c,0);
                server.runOnServer(s -> {
                    check(game().battle==null && game().network.selections.tickets().isEmpty(), "One rematch vote cannot queue the other player");
                    var r = game().hub.results.book.result(friend.get().player.getUUID());
                    game().hub.results.rematch(friend.get().player,r.id());
                    check(game().battle!=null && game().actor(connection.getServerPlayer()).kind==FighterClass.ZOMBIE, "Unanimous rematch preserves fighters");
                    check(game().hub.parties.view(connection.getServerPlayer().getUUID()).members().size()==2, "Rematch preserves the party");
                });
                c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.gui.screen()==null,300);
                server.waitFor(s -> game().match.phase()==MatchState.Phase.ACTIVE,200);
                c.waitTicks(10); c.takeScreenshot("experience-02-rematch-hud");
                server.runOnServer(s -> { game().match.finish(connection.getServerPlayer().getUUID(),"Test"); game().endRound(true); });
                winnerReady(c);
                winnerAction(c,1); menuReady(c);
                server.runOnServer(s -> {
                    var view = game().hub.parties.view(connection.getServerPlayer().getUUID());
                    check(game().battle==null && view.readyCount()==1 && view.phase()==PartyBook.Phase.SELECTING,"Play again waits for party consent");
                    game().hub.confirm(friend.get().player,FighterClass.SKELETON,view.round());
                });
                c.waitFor(mc -> MvpWorlds.battle(mc.level),300);
                server.runOnServer(s -> game().endRound(true)); c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY));
                command(c,"smash join"); menuReady(c); MatchmakingClientTest.click(c,"Free-for-all"); stageReady(c); ready(c,4); menuReady(c);
                server.runOnServer(s -> { game().stage.confirm(friend.get().player); check(game().match.queue().size()==2 && game().battle==null,"A ready pair waits for two FFA opponents"); });
                c.waitFor(mc -> mc.gui.screen() == null); c.takeScreenshot("match-06-queued-party");
                server.runOnServer(s -> {
                    game().choose(solo1.get().player,FighterClass.STEVE,VanillaSmash.Mode.MATCH);
                    check(game().battle == null,"Three players do not start a four-player FFA");
                    game().choose(solo2.get().player,FighterClass.ALEX,VanillaSmash.Mode.MATCH);
                    check(game().match.roster().size()==4 && game().battle.dummy()==null,"FFA fills party with two public opponents");
                    check(game().battle.stage==BattleStage.select(game().hub.reservation().id(),false),"Four-player matches use the same random stage policy");
                    check(game().viewers.values().stream().allMatch(v->v.player().level()==game().battle.level),"All four players enter the selected stage");
                });
                c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.getCameraEntity()!=mc.player,300); c.waitTicks(15); c.takeScreenshot("match-07-four-player-ffa");
                var foreignCameras=server.computeOnServer(s -> game().viewers.values().stream()
                        .filter(v -> v.player()!=connection.getServerPlayer())
                        .flatMap(v -> java.util.stream.Stream.concat(java.util.stream.Stream.of(v.rig().carrier),v.rig().carrier.getPassengers().stream()))
                        .mapToInt(net.minecraft.world.entity.Entity::getId).toArray());
                check(foreignCameras.length>=6,"Four players own separate camera rigs");
                c.runOnClient(mc -> {
                    for(int entityId:foreignCameras) check(mc.level.getEntity(entityId)==null,"Other players' cameras and HUDs remain private");
                    check(mc.getCameraEntity().getVehicle() instanceof net.minecraft.world.entity.Display.BlockDisplay,"FFA retains interpolated camera");
                });
                c.getInput().resizeWindow(960,720); command(c,"smash camera 18"); c.waitTicks(12); c.takeScreenshot("experience-03-four-three-close-hud");
                command(c,"smash camera 40"); c.waitTicks(12); c.takeScreenshot("experience-04-four-three-wide-hud");
                command(c,"smash camera 24"); c.getInput().resizeWindow(1280,720); c.waitTicks(6);
                server.runOnServer(s -> game().endRound(true)); c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY));
                command(c,"smash join"); menuReady(c); MatchmakingClientTest.click(c,"Leave party"); menuReady(c);
                server.runOnServer(s -> {
                    var p = connection.getServerPlayer();
                    check(game().hub.parties.view(friend.get().player.getUUID()).leader().equals(friend.get().player.getUUID()),"Leader leaving promotes their friend");
                    game().hub.partyCommand(friend.get().player,"invite",p.getPlainTextName());
                });
                c.waitTicks(3); menuReady(c); MatchmakingClientTest.click(c,"Invitations (1)");
                c.waitFor(mc -> mc.gui.screen() != null && mc.gui.screen().getTitle().getString().equals("Invitations"));
                c.takeScreenshot("match-08-incoming-invite"); MatchmakingClientTest.click(c,"Join Friend"); menuReady(c);
                command(c,"smash duel"); c.waitTicks(4);
                server.runOnServer(s -> check(!game().stage.active(connection.getServerPlayer()) && game().hub.parties.view(connection.getServerPlayer().getUUID()).phase()==PartyBook.Phase.IDLE,"Nonleader cannot start through a command"));
                server.runOnServer(s -> game().hub.selectMode(friend.get().player,VanillaSmash.Mode.DUEL)); stageReady(c);
                c.getInput().pressKey(o -> o.keyDrop); menuReady(c);
                server.runOnServer(s -> check(!game().stage.active(friend.get().player) && game().network.selections.tickets().isEmpty(),"Backing out cancels the group's ready round"));
                server.runOnServer(s -> game().hub.selectMode(friend.get().player,VanillaSmash.Mode.DUEL)); stageReady(c); ready(c,0); menuReady(c);
                server.runOnServer(s -> friend.get().leave());
                c.waitTicks(5); menuReady(c);
                server.runOnServer(s -> {
                    var view = game().hub.parties.view(connection.getServerPlayer().getUUID());
                    check(view.leader().equals(connection.getServerPlayer().getUUID()) && view.phase()==PartyBook.Phase.IDLE && view.readyCount()==0,"Leader disconnect cancels ready-up and promotes survivor");
                    check(game().match.queue().isEmpty() && game().battle==null,"Disconnected party does not launch a match");
                });
                c.takeScreenshot("match-09-leader-disconnected");
                server.runOnServer(s -> { solo1.get().leave(); solo2.get().leave(); });
            }
        }
        VanillaSmash.LOG.info("MATCHMAKING_NATIVE_CLIENT_TEST_PASSED");
    }
}
