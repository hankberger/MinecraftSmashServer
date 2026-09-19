package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Items;

/** End-to-end native-input checks; no Smash client mixins or custom client messages. */
@SuppressWarnings("UnstableApiUsage")
public final class VanillaClientTest implements FabricClientGameTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext c) {
        if (Boolean.getBoolean("smash_vanilla.combatTest")) CombatPrecisionClientTest.run(c);
        if (Boolean.getBoolean("smash_vanilla.packedTests")) { PackedMenuClientTest.run(c); return; }
        if (Boolean.getBoolean("smash_vanilla.networkTest")) { networkTest(c); return; }
        if (Boolean.getBoolean("smash_vanilla.showcaseTest")) { ShowcaseClientTest.run(c); return; }
        if (!Boolean.getBoolean("smash_vanilla.mapTest") && !Boolean.getBoolean("smash_vanilla.combatTest")) {
            LobbyPlayPointClientTest.run(c);
            if (Boolean.getBoolean("smash_vanilla.lobbyTests")) return;
            WinnerStageClientTest.run(c);
            if (Boolean.getBoolean("smash_vanilla.winnerTest")) return;
            MatchmakingClientTest.run(c);
            if (Boolean.getBoolean("smash_vanilla.matchmakingTest")) return;
            ShowcaseClientTest.run(c);
        }
        check(!FabricLoader.getInstance().isModLoaded("smash_arena"), "Original client mod absent");
        var props = new Properties(); props.setProperty("online-mode", "false"); props.setProperty("server-ip", "127.0.0.1");
        props.setProperty("view-distance", Boolean.getBoolean("smash_vanilla.mapTest") ? "14" : "6"); props.setProperty("simulation-distance", "5"); props.setProperty("allow-flight", "true");
        try (var server = c.worldBuilder().createServer(props)) {
            if (Boolean.getBoolean("smash_vanilla.mapTest")) server.runOnServer(MapWorldTest::verify);
            try (var connection = server.connect()) {
                connection.waitForChunksRender(); c.getInput().resizeWindow(1280, 720);
                c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui(); });
                c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY), 300);
                server.runOnServer(s -> {
                    var p = connection.getServerPlayer();
                    check(Math.abs(p.getX()-LobbyRules.SPAWN_X)<.01 && Math.abs(p.getZ()-LobbyRules.SPAWN_Z)<.01 && p.getYRot()==LobbyRules.SPAWN_YAW, "Exact south-facing garden arrival");
                });
                c.waitTicks(40); c.takeScreenshot("01-garden-spawn");
                if (Boolean.getBoolean("smash_vanilla.mapTest")) {
                    c.runOnClient(mc -> { mc.options.renderDistance().set(14); mc.options.broadcastOptions(); });
                    c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
                    server.runOnServer(s -> connection.getServerPlayer().teleportTo(s.getLevel(MvpWorlds.LOBBY), .5,106,-83,Set.of(),0,12,false));
                    c.waitTicks(35); c.takeScreenshot("map-00-arrival-court");
                    server.runOnServer(s -> connection.getServerPlayer().teleportTo(s.getLevel(MvpWorlds.LOBBY), .5,101,-43,Set.of(),0,-8,false));
                    c.waitTicks(20); c.takeScreenshot("map-01-tree-approach");
                    c.getInput().holdKeyFor(o -> o.keyUp, 88);
                    server.runOnServer(s -> check(connection.getServerPlayer().getZ() > -26, "Arrival path reaches the tree on foot"));
                    server.runOnServer(s -> connection.getServerPlayer().teleportTo(s.getLevel(MvpWorlds.LOBBY), .5,101,-20.5,Set.of(),0,-12,false));
                    c.waitTicks(12); c.takeScreenshot("map-02-heartwood-library");
                    server.runOnServer(s -> connection.getServerPlayer().teleportTo(s.getLevel(MvpWorlds.LOBBY), 3.5,101,-19.8,Set.of(),0,0,false));
                    c.waitTicks(6); c.getInput().holdKeyFor(o -> o.keyUp, 80);
                    c.takeScreenshot("map-02b-stair-landing");
                    server.runOnServer(s -> check(connection.getServerPlayer().getY() >= 109, "Staircase reaches the reading loft without jumping: " + connection.getServerPlayer().position()));
                    server.runOnServer(s -> connection.getServerPlayer().teleportTo(s.getLevel(MvpWorlds.LOBBY), -.5,109,-11.5,Set.of(),180,-8,false));
                    c.waitTicks(12); c.takeScreenshot("map-03-reading-loft");
                    server.runOnServer(s -> connection.getServerPlayer().teleportTo(s.getLevel(MvpWorlds.LOBBY), 19.5,101,-34.5,Set.of(),-70.3f,0,false));
                    c.waitTicks(8); c.getInput().holdKeyFor(o -> o.keyUp,90);
                    server.runOnServer(s -> check(connection.getServerPlayer().getX()>34 && Math.abs(connection.getServerPlayer().getY()-101)<.1,"Lotus branch is walkable without jumping"));
                    server.runOnServer(s -> connection.getServerPlayer().teleportTo(s.getLevel(MvpWorlds.LOBBY), 48.5,101,-27,Set.of(),0,-12,false));
                    c.waitTicks(20); c.takeScreenshot("map-03b-relocated-lotus");
                    server.runOnServer(s -> {
                        var p = connection.getServerPlayer(); p.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
                        p.teleportTo(s.getLevel(MvpWorlds.LOBBY), .5,245,-26,Set.of(),0,90,false);
                    });
                    c.runOnClient(mc -> mc.options.fov().set(95));
                    c.waitFor(mc -> mc.level.hasChunkAt(new net.minecraft.core.BlockPos(-80,100,64)) && mc.level.hasChunkAt(new net.minecraft.core.BlockPos(80,100,64)), 300);
                    c.waitTicks(40); c.takeScreenshot("map-04-garden-overview");
                    server.runOnServer(s -> {
                        var p = connection.getServerPlayer(); p.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
                        p.teleportTo(s.getLevel(MvpWorlds.LOBBY), LobbyRules.SPAWN_X,LobbyRules.SPAWN_Y,LobbyRules.SPAWN_Z,Set.of(),LobbyRules.SPAWN_YAW,0,false);
                    });
                    c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_F1);
                    c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.renderDistance().set(6); mc.options.broadcastOptions(); });
                    c.waitTicks(12);
                }
                // Normal item interaction enters the 3D stage without typing a command.
                c.getInput().pressMouse(1);
                MatchmakingClientTest.menuReady(c); MatchmakingClientTest.click(c,"Free-for-all");
                waitForStage(c);
                server.runOnServer(s -> check(game().match.queue().isEmpty(), "Browsing never queues"));
                c.takeScreenshot("02-five-class-picker");
                c.getInput().pressKey(o -> o.keyDrop);
                c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity() == mc.player);
                server.runOnServer(s -> check(!game().stage.active(connection.getServerPlayer()) && game().match.queue().isEmpty(), "Cancel closes pending selection"));
                command(c, "smash join"); select(c, FighterClass.ALEX);
                server.waitFor(s -> game().match.queue().size() == 1);
                server.runOnServer(s -> connection.getServerPlayer().teleportTo(.5, 90, -2.5));
                server.waitFor(s -> Math.abs(connection.getServerPlayer().getY() - 101) < .1);
                server.runOnServer(s -> check(game().match.queue().size() == 1, "Garden fall preserves queue position"));
                command(c, "smash unqueue"); server.waitFor(s -> game().match.queue().isEmpty());
                command(c, "smash practice"); select(c, FighterClass.STEVE);
                c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.ARENA) && mc.getCameraEntity() != mc.player, 300);
                server.runOnServer(s -> check(game().match.phase() == MatchState.Phase.COUNTDOWN && game().actor(connection.getServerPlayer()).state.protectedUntil == 0, "Countdown without spawn flash"));
                c.getInput().holdKeyFor(o -> o.keyRight, 8);
                c.getInput().pressMouse(0);
                server.runOnServer(s -> check(game().actor(connection.getServerPlayer()).x == -14.5 && game().actor(connection.getServerPlayer()).lights == 0, "Countdown locks inputs"));
                server.waitFor(s -> game().match.phase() == MatchState.Phase.ACTIVE, 200);
                server.runOnServer(s -> check(game().actor(connection.getServerPlayer()).state.protectedUntil == 0, "GO has no invincibility"));
                c.waitTicks(35); c.takeScreenshot("03-battle-arena-and-hud");
                command(c, "smash leave"); c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity() == mc.player);

                for (var kind : FighterClass.values()) {
                    command(c, "smash sandbox"); select(c, kind);
                    c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.ARENA) && mc.getCameraEntity() != mc.player, 250);
                    c.waitTicks(10);
                    var id = c.computeOnClient(mc -> mc.player.getUUID());
                    var bodyId = new AtomicInteger();
                    server.runOnServer(s -> {
                        var f = game().battle.actors.get(id); bodyId.set(f.body.getId());
                        check(game().battle.dummy().x == 14.5, "Dummy starts on the right");
                        game().battle.reset(f, 0, 81); game().battle.reset(game().battle.dummy(), 2, 81);
                    });
                    c.waitTicks(12); c.getInput().pressMouse(0);
                    server.waitFor(s -> game().battle.dummy().state.percent > 0);
                    server.runOnServer(s -> check(game().battle.dummy().state.percent == FighterMoves.light(kind, AttackDirection.FORWARD, false).damage(), "Class-specific light damage"));
                    c.takeScreenshot("04-" + kind.label.toLowerCase() + "-light");
                    server.runOnServer(s -> { game().battle.reset(game().battle.actors.get(id), -8, 81); game().battle.reset(game().battle.dummy(), 14.5, 81); });
                    c.waitTicks(12);
                    if (kind == FighterClass.SKELETON) {
                        c.getInput().holdMouse(1); c.waitTicks(5);
                        server.runOnServer(s -> check(game().battle.actors.get(id).state.drawingBow() && game().battle.objects.arrows.isEmpty(), "Bow draws, no immediate shot"));
                        c.getInput().releaseMouse(1);
                        server.waitFor(s -> !game().battle.objects.arrows.isEmpty());
                        server.runOnServer(s -> {
                            var shot = game().battle.objects.arrows.get(id);
                            check(shot.charge() < 20 && Math.abs(shot.entity().getDeltaMovement().x) < 3, "Short charge uses reduced native bow power");
                        });
                        server.waitFor(s -> game().battle.objects.arrows.isEmpty());
                        c.waitTicks(15); c.getInput().holdMouse(1); c.waitTicks(24);
                        c.takeScreenshot("05-skeleton-draw"); c.getInput().releaseMouse(1);
                        server.waitFor(s -> !game().battle.objects.arrows.isEmpty());
                        var arrowId = new AtomicInteger();
                        server.runOnServer(s -> {
                            var shot = game().battle.objects.arrows.get(id); arrowId.set(shot.entity().getId());
                            check(shot.charge() == 20 && shot.entity().getType() == EntityTypes.ARROW, "Full-charge shot is an ordinary arrow");
                        });
                        c.waitFor(mc -> mc.level.getEntity(arrowId.get()) != null);
                        c.takeScreenshot("06-native-arrow-flight");
                        server.waitFor(s -> game().battle.objects.arrows.isEmpty());
                    } else if (kind == FighterClass.VILLAGER) {
                        server.runOnServer(s -> game().battle.reset(game().battle.actors.get(id), 0, 95));
                        c.waitTicks(4); c.getInput().pressMouse(1);
                        server.waitFor(s -> !game().battle.objects.bells.isEmpty());
                        c.waitTicks(21);
                        server.runOnServer(s -> check(!game().battle.objects.bells.isEmpty(), "An aerial bell does not vanish after one second"));
                        server.waitFor(s -> game().battle.objects.bellArmed(game().battle.actors.get(id)), 120);
                        server.runOnServer(s -> {
                            var b = game().battle.objects.bells.get(id);
                            game().battle.reset(game().battle.dummy(), b.pos.x + .7, b.pos.y - .3);
                        });
                        c.takeScreenshot("07-villager-bell"); c.getInput().pressMouse(1);
                        server.waitFor(s -> game().battle.objects.bells.isEmpty());
                        server.runOnServer(s -> check(game().battle.dummy().state.percent == 12, "Bell pulse deals combat damage"));
                    } else if (kind == FighterClass.ZOMBIE) {
                        server.runOnServer(s -> { game().battle.reset(game().battle.actors.get(id), 0, 100); game().battle.reset(game().battle.dummy(), 1, 89); });
                        c.waitTicks(3); c.getInput().pressMouse(1);
                        server.waitFor(s -> game().battle.actors.get(id).state.motionType == 4);
                        c.takeScreenshot("08-zombie-grave-slam");
                        server.waitFor(s -> game().battle.dummy().state.percent >= 22, 100);
                    } else {
                        c.getInput().pressMouse(1);
                        server.waitFor(s -> game().battle.actors.get(id).specials > 0);
                        if (kind == FighterClass.ALEX) server.waitFor(s -> game().battle.actors.get(id).x > -6);
                        c.takeScreenshot("09-" + kind.label.toLowerCase() + "-special");
                    }
                    server.runOnServer(s -> { game().battle.reset(game().battle.actors.get(id), -6, 81); game().battle.reset(game().battle.dummy(), 14.5, 81); });
                    c.waitTicks(12);
                    c.getInput().holdKey(o -> o.keyRight); c.waitTicks(8); c.getInput().releaseKey(o -> o.keyRight);
                    server.runOnServer(s -> check(game().battle.actors.get(id).x > -5, "Class moves using native direction keys"));
                    c.getInput().holdKeyFor(o -> o.keyJump, 2); c.waitTicks(4); c.getInput().holdKeyFor(o -> o.keyJump, 2);
                    server.runOnServer(s -> check(!game().battle.actors.get(id).recovery.available(), "Native double jump spends air jump"));
                    server.runOnServer(s -> game().battle.reset(game().battle.actors.get(id), -6, 81));
                    c.waitTicks(12); c.getInput().holdKey(o -> o.keyUp); c.waitTicks(2);
                    c.getInput().pressMouse(1); c.waitTicks(2); c.getInput().releaseKey(o -> o.keyUp);
                    server.waitFor(s -> game().battle.actors.get(id).recoveries == 1);
                    server.runOnServer(s -> check(!game().battle.actors.get(id).recovery.recoveryAvailable(), "Recovery budget consumed"));
                    c.takeScreenshot("10-" + kind.label.toLowerCase() + "-recovery");
                    command(c, "smash leave"); c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity() == mc.player);
                    server.waitFor(s -> game().battle == null);
                }

                command(c, "smash sandbox"); select(c, FighterClass.STEVE);
                c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.ARENA) && mc.getCameraEntity() != mc.player);
                var id = c.computeOnClient(mc -> mc.player.getUUID());
                // Up light reaches the next platform and visibly launches above the actor.
                server.runOnServer(s -> {
                    game().battle.reset(game().battle.actors.get(id), 6, 81);
                    // Inside the curved overhead sweep, not a corner of its old rectangular bounds.
                    game().battle.reset(game().battle.dummy(), 6.5, 85);
                    var f = game().battle.actors.get(id);
                    for (var kind : FighterClass.values()) {
                        var up = FighterMoves.light(kind, AttackDirection.UP, false);
                        check(CombatGeometry.shape(up, 1, f.pose.x, f.pose.y).contact(CombatGeometry.body(6.5, 85, .6, 1.95)) != null, kind + " reaches overhead platform");
                        check(!Battle.hitbox(f, FighterMoves.light(kind, AttackDirection.FORWARD, false), 1).intersects(game().battle.dummy().box()), "Forward attack does not reach that platform");
                    }
                });
                c.waitTicks(10); c.getInput().holdKey(o -> o.keyUp); c.waitTicks(2); c.getInput().pressMouse(0);
                server.waitFor(s -> game().battle.dummy().state.percent > 0);
                c.getInput().releaseKey(o -> o.keyUp);
                server.runOnServer(s -> check(game().battle.dummy().vy > 0 && game().battle.dummy().state.percent == 6, "Native up chord hits the overhead target once and lifts it"));
                c.waitTicks(3); c.takeScreenshot("14-overhead-arc");

                // A finishing hit crosses the blast zone even with an unused recovery budget.
                var falls = new AtomicInteger(); var kos = new AtomicInteger();
                var heard = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
                net.minecraft.client.sounds.SoundEventListener listener = (sound, event, range) -> heard.add(sound.getIdentifier().toString());
                c.runOnClient(mc -> mc.getSoundManager().addListener(listener));
                server.runOnServer(s -> {
                    var f = game().battle.actors.get(id); var d = game().battle.dummy();
                    game().battle.reset(f, .5, 81); game().battle.reset(d, -4, 81);
                    falls.set(f.state.falls); kos.set(d.state.knockouts); f.state.percent = 180;
                    game().battle.hit(d, f, 1, FighterMoves.special(FighterClass.STEVE, false, false));
                    check(f.recovery.recoveryAvailable(), "Recovery starts available");
                    check(!game().battle.request(f, true, new net.minecraft.world.entity.player.Input(true, false, true, false, true, false, false)), "Recovery cannot cancel launch stun");
                });
                c.waitTicks(2); c.takeScreenshot("15-finishing-launch");
                server.waitFor(s -> game().battle.actors.get(id).state.falls == falls.get() + 1, 60);
                c.waitFor(mc -> heard.contains("minecraft:entity.generic.explode") && heard.contains("minecraft:entity.firework_rocket.blast"), 40);
                check(heard.contains("minecraft:entity.player.attack.knockback"), "Client hears the strong-launch cue at the side camera");
                c.runOnClient(mc -> mc.getSoundManager().removeListener(listener));
                c.waitTicks(2);
                c.takeScreenshot("16-ko-burst");
                server.runOnServer(s -> {
                    var f = game().battle.actors.get(id);
                    check(game().battle.dummy().state.knockouts == kos.get() + 1, "Launch KO credits attacker exactly once");
                    check(f.state.floating(game().ticks) && f.state.percent == 0 && !f.state.strongLaunch, "KO clears launch and begins protected respawn");
                });

                server.runOnServer(s -> {
                    var f = game().battle.actors.get(id); var d = game().battle.dummy();
                    game().battle.reset(f, .5, 81); game().battle.reset(d, .5, 81);
                    d.state.percent = 240; falls.set(d.state.falls);
                    game().battle.hit(f, d, 1, FighterMoves.light(FighterClass.STEVE, AttackDirection.UP, false));
                });
                server.waitFor(s -> game().battle.dummy().y > 101 && !game().battle.dummy().state.floating(game().ticks), 40);
                c.takeScreenshot("17-upward-launch");
                server.waitFor(s -> game().battle.dummy().state.falls == falls.get() + 1, 40);
                server.runOnServer(s -> { game().battle.reset(game().battle.actors.get(id), 6, 85); game().battle.reset(game().battle.dummy(), 14.5, 81); });
                c.waitTicks(12); c.getInput().holdKey(o -> o.keyShift);
                server.waitFor(s -> game().battle.actors.get(id).state.blocking(game().ticks));
                c.runOnClient(mc -> check(mc.getCameraEntity() != mc.player, "Shift guard retains camera"));
                c.takeScreenshot("11-guard");
                server.runOnServer(s -> {
                    var f = game().battle.actors.get(id); var d = game().battle.dummy();
                    game().battle.hit(d, f, 1, FighterMoves.special(FighterClass.ZOMBIE, false, false));
                    check(f.state.percent == 0 && f.state.guard < 60, "Shield absorbs damage and spends energy");
                });
                c.getInput().releaseKey(o -> o.keyShift); c.waitTicks(4);
                c.getInput().holdKeyFor(o -> o.keyDown, 4);
                server.waitFor(s -> game().battle.actors.get(id).y < 84);
                // Native down chord takes precedence over a platform drop.
                server.runOnServer(s -> game().battle.reset(game().battle.actors.get(id), 6, 85));
                c.waitTicks(12); c.getInput().holdKey(o -> o.keyDown); c.waitTicks(1);
                c.getInput().pressMouse(0); c.waitTicks(1); c.getInput().releaseKey(o -> o.keyDown);
                server.waitFor(s -> game().battle.actors.get(id).state.move != null && game().battle.actors.get(id).state.move.aim() == AttackDirection.DOWN);
                server.runOnServer(s -> check(game().battle.actors.get(id).y == 85, "Down attack does not also drop"));
                server.runOnServer(s -> game().battle.ringOut(game().battle.actors.get(id)));
                server.runOnServer(s -> check(game().battle.actors.get(id).state.floating(game().ticks), "KO starts protected descent"));
                c.waitTicks(12); c.takeScreenshot("12-protected-respawn");
                server.waitFor(s -> !game().battle.actors.get(id).state.floating(game().ticks));
                server.runOnServer(s -> check(game().battle.actors.get(id).state.protectedUntil > game().ticks, "Protection persists briefly after landing"));
                // Inventory controls must not let input props escape or change class equipment.
                c.getInput().pressKey(o -> o.keyDrop); c.waitTicks(4);
                server.runOnServer(s -> check(connection.getServerPlayer().getMainHandItem().is(Items.STICK), "Dropping input item is rejected"));
                command(c, "smash leave"); c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY));
                command(c, "smash practice"); select(c, FighterClass.ALEX);
                server.waitFor(s -> game().match.phase() == MatchState.Phase.ACTIVE, 240);
                for (int stock = 0; stock < 3; stock++) server.runOnServer(s -> game().battle.ringOut(game().battle.dummy()));
                server.waitFor(s -> game().match.phase() == MatchState.Phase.RESULTS);
                c.takeScreenshot("13-results");
                server.waitFor(s -> game().battle == null, 160);
                c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity() == mc.player);
                command(c, "smash join"); select(c, FighterClass.VILLAGER); server.waitFor(s -> game().match.queue().size() == 1);
            }
            server.waitFor(s -> game().match.queue().isEmpty() && game().battle == null && game().viewers.isEmpty());
            try (var connection = server.connect()) {
                connection.waitForChunksRender(); c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY), 240);
                server.waitFor(s -> game().hub.available(connection.getServerPlayer()), 100);
                server.runOnServer(s -> check(game().match.queue().isEmpty() && game().choices.isEmpty(), "Reconnect restores lobby without stale class or queue"));
                command(c, "smash practice"); select(c, FighterClass.ZOMBIE);
                server.waitFor(s -> game().match.phase() == MatchState.Phase.COUNTDOWN);
            }
            server.waitFor(s -> game().battle == null && game().match.phase() == MatchState.Phase.IDLE && game().viewers.isEmpty());
        }
        VanillaSmash.LOG.info("VANILLA_MVP_CLIENT_TEST_PASSED");
    }
    private static void command(ClientGameTestContext c, String command) {
        var previousScreen = c.computeOnClient(mc -> mc.gui.screen());
        c.runOnClient(mc -> mc.player.connection.sendCommand(command));
        if (command.equals("smash join")) { c.waitFor(mc -> mc.gui.screen() != previousScreen); MatchmakingClientTest.menuReady(c); MatchmakingClientTest.click(c,"Free-for-all"); }
    }
    private static void networkTest(ClientGameTestContext c) {
        c.runOnClient(mc -> net.minecraft.client.gui.screens.ConnectScreen.startConnecting(
                new net.minecraft.client.gui.screens.TitleScreen(), mc,
                net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25577"),
                new net.minecraft.client.multiplayer.ServerData("Local network test", "127.0.0.1:25577", net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null));
        c.waitFor(mc -> mc.level != null && mc.level.dimension().equals(MvpWorlds.LOBBY), 1200);
        c.getInput().resizeWindow(1280, 720);
        c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui(); });
        c.waitTicks(40); command(c, "smash unqueue"); c.waitTicks(12);
        c.takeScreenshot("network-01-lobby");
        for (var kind : List.of(FighterClass.STEVE, FighterClass.ZOMBIE)) {
            command(c, "smash sandbox");
            waitForStage(c);
            c.takeScreenshot("network-02-picker"); select(c, kind);
            c.waitFor(mc -> mc.level != null && mc.level.dimension().equals(MvpWorlds.ARENA) && mc.getCameraEntity() != mc.player, 1000);
            c.waitTicks(40);
            var actor = c.computeOnClient(mc -> {
                for (var e : mc.level.entitiesForRendering())
                    if (e.getType() == (kind == FighterClass.STEVE ? EntityTypes.MANNEQUIN : EntityTypes.ZOMBIE)) return e.getId();
                throw new AssertionError("Selected class model did not transfer");
            });
            double before = c.computeOnClient(mc -> mc.level.getEntity(actor).getX());
            c.getInput().holdKeyFor(o -> o.keyRight, 12); c.waitTicks(10);
            check(c.computeOnClient(mc -> mc.level.getEntity(actor).getX()) > before + 1, "Native movement works after proxy transfer");
            c.takeScreenshot("network-03-" + kind.label.toLowerCase() + "-arena");
            command(c, "smash leave");
            c.waitFor(mc -> mc.level != null && mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity() == mc.player, 1000);
            c.waitTicks(40);
        }
        c.takeScreenshot("network-04-returned");
        c.runOnClient(mc -> mc.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false));
        c.waitFor(mc -> mc.level == null && mc.getConnection() == null, 200);
        VanillaSmash.LOG.info("NETWORK_NATIVE_CLIENT_TEST_PASSED: picker, class transfer, camera, movement, leave, and rejoin");
    }
    private static void select(ClientGameTestContext c, FighterClass kind) {
        waitForStage(c);
        c.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_1 + kind.ordinal());
        c.waitTicks(22); c.getInput().pressMouse(1);
        c.waitFor(mc -> !mc.level.dimension().equals(MvpWorlds.SHOWCASE), 300);
    }
    private static void waitForStage(ClientGameTestContext c) {
        c.waitFor(mc -> mc.level != null && mc.level.dimension().equals(MvpWorlds.SHOWCASE)
                && mc.getCameraEntity() == mc.player && mc.gui.screen() == null, 400);
    }
}
