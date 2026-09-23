package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.player.Input;

/** Native key/mouse input plus deterministic combat timing against a dedicated server. */
@SuppressWarnings("UnstableApiUsage")
public final class MovementClientTest {
    private record Frame(double y, double vx, double vy, boolean grounded, boolean blocking) {}
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var captured = new AtomicReference<UUID>(); var frames = new ConcurrentLinkedDeque<Frame>();
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (captured.get() == null || game().battle == null) return;
            var f = game().battle.actors.get(captured.get());
            if (f != null) frames.add(new Frame(f.y, f.vx, f.vy, f.grounded, f.state.blocking(game().ticks)));
        });
        var props = new Properties(); props.setProperty("online-mode", "false"); props.setProperty("server-ip", "127.0.0.1");
        props.setProperty("view-distance", "6"); props.setProperty("simulation-distance", "5"); props.setProperty("allow-flight", "true");
        try (var server = c.worldBuilder().createServer(props); var connection = server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280, 720);
            server.waitFor(s -> game().hub.available(connection.getServerPlayer()), 240);
            server.runOnServer(s -> game().choose(connection.getServerPlayer(), FighterClass.STEVE, VanillaSmash.Mode.SANDBOX));
            server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE, 300);
            c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.getCameraEntity() != mc.player, 300);
            var id = server.computeOnServer(s -> connection.getServerPlayer().getUUID()); captured.set(id);
            c.waitTicks(20);
            server.runOnServer(s -> game().battle.reset(game().battle.actors.get(id), -10, 81));
            c.getInput().holdKey(o -> o.keyRight); c.waitTicks(8);
            server.runOnServer(s -> check(Math.abs(game().battle.actors.get(id).vx - MovementRules.RUN_SPEED) < .01, "A/D reaches full combat speed without sprint"));
            c.getInput().releaseKey(o -> o.keyRight); c.waitTicks(4);
            server.runOnServer(s -> check(Math.abs(game().battle.actors.get(id).vx) < .02, "Releasing ground movement stops promptly"));

            server.runOnServer(s -> game().battle.reset(game().battle.actors.get(id), -8, 81));
            c.getInput().holdKey(o -> o.keyRight); c.waitTicks(8);
            c.getInput().holdKey(o -> o.keyJump); c.waitTicks(4);
            c.getInput().releaseKey(o -> o.keyRight); c.waitTicks(1);
            double nairStartX = server.computeOnServer(s -> game().battle.actors.get(id).x);
            c.getInput().pressMouse(0); c.waitTicks(4);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id);
                check(f.state.move.aim() == AttackDirection.NEUTRAL && f.state.move.aerial(), "Releasing A/D during a jump selects nair");
                check(f.vx > MovementRules.RUN_SPEED*.8 && f.x > nairStartX + MovementRules.RUN_SPEED*3, "Nair carries forward momentum instead of stopping in place");
            });
            c.getInput().releaseKey(o -> o.keyJump); c.waitTicks(25);

            server.runOnServer(s -> { game().battle.reset(game().battle.actors.get(id), 0, 81); frames.clear(); });
            c.getInput().holdKeyFor(o -> o.keyJump, 2); c.waitTicks(26);
            double shortPeak = frames.stream().mapToDouble(Frame::y).max().orElseThrow();
            check(shortPeak > 82 && shortPeak < 84.5, "A tapped jump stays low: " + shortPeak);
            server.runOnServer(s -> { game().battle.reset(game().battle.actors.get(id), 0, 81); frames.clear(); });
            c.getInput().holdKeyFor(o -> o.keyJump, 24); c.waitTicks(4);
            double fullPeak = frames.stream().mapToDouble(Frame::y).max().orElseThrow();
            check(fullPeak > 85.5 && fullPeak > shortPeak + 1.5, "Holding jump clears the next platform: " + fullPeak);
            VanillaSmash.LOG.info("MOVEMENT_NATIVE_JUMP_PEAKS short={} full={}", shortPeak, fullPeak);

            // W no longer turns the second Space press into recovery.
            server.runOnServer(s -> game().battle.reset(game().battle.actors.get(id), 0, 81));
            c.getInput().holdKey(o -> o.keyUp); c.waitTicks(2); c.getInput().holdKeyFor(o -> o.keyJump, 10);
            server.runOnServer(s -> check(game().battle.actors.get(id).recoveries == 0, "Ground jump chord does not auto recover"));
            c.waitTicks(2); c.getInput().holdKeyFor(o -> o.keyJump, 2);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id);
                check(f.recoveries == 0 && !f.recovery.available() && f.recovery.recoveryAvailable(), "W+Space still double jumps before recovery");
            });
            c.getInput().releaseKey(o -> o.keyUp);
            c.waitTicks(30);

            // Air shield follows the fall and existing drift, has one window, and preserves air options.
            c.getInput().holdKey(o -> o.keyShift);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id); game().battle.reset(f, 18, 100); f.vx = .4; f.vy = -.15; frames.clear();
            });
            server.waitFor(s -> game().battle.actors.get(id).state.blocking(game().ticks), 8);
            server.runOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id);
                b.hit(b.dummy(), f, -1, FighterMoves.light(FighterClass.STEVE, AttackDirection.FORWARD, true));
                check(f.state.percent == 0 && f.state.guard < 90, "Air shield blocks using the same guard energy");
                check(f.body.isUsingItem(), "Air shield has the native shield pose");
            });
            c.takeScreenshot("movement-air-shield"); c.waitTicks(9);
            check(frames.stream().anyMatch(f -> f.blocking && f.vx > .25 && f.vy < -.2), "Shield preserves drift and gravity");
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id); check(!f.state.blocking(game().ticks), "Holding air shield cannot extend its window");
                check(f.recovery.available() && f.recovery.recoveryAvailable(), "Air shield does not consume jumps or recovery");
            });
            c.getInput().releaseKey(o -> o.keyShift); c.waitTicks(25);

            server.runOnServer(s -> game().battle.reset(game().battle.actors.get(id), 0, 89));
            c.getInput().holdKeyFor(o -> o.keyDown, 9);
            server.runOnServer(s -> check(game().battle.actors.get(id).y < 88, "S drops through and accelerates the fall"));
            c.waitTicks(20);

            // Actual click packets select each class's nair; it pushes a target behind us outward once.
            for (var kind : FighterClass.values()) {
                server.runOnServer(s -> { game().leave(connection.getServerPlayer()); game().choose(connection.getServerPlayer(), kind, VanillaSmash.Mode.SANDBOX); });
                server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE, 300);
                c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.getCameraEntity() != mc.player, 300); c.waitTicks(15);
                // The whole sequence uses real Space presses, away from the upper platforms.
                int recoveries = server.computeOnServer(s -> {
                    var b = game().battle; var f = b.actors.get(id); b.reset(f,-14,81); b.reset(b.dummy(),14,81); return f.recoveries;
                });
                c.getInput().holdKeyFor(o -> o.keyJump,4); c.waitTicks(2);
                server.runOnServer(s -> {
                    var f=game().battle.actors.get(id);
                    check(!f.grounded&&f.recovery.available()&&f.recovery.recoveryAvailable(),kind+" first press is an ordinary ground jump");
                });
                c.getInput().holdKeyFor(o -> o.keyJump,4); c.waitTicks(2);
                server.runOnServer(s -> {
                    var f=game().battle.actors.get(id);
                    check(!f.recovery.available()&&f.recovery.recoveryAvailable()&&f.recoveries==recoveries,kind+" second press spends only the double jump");
                });
                c.getInput().holdKey(o -> o.keyJump);
                server.waitFor(s -> game().battle.actors.get(id).recoveries==recoveries+1,10);
                server.runOnServer(s -> {
                    var f=game().battle.actors.get(id);
                    check(f.state.move.equals(FighterMoves.recovery(kind))&&f.recovery.helpless()&&!f.recovery.available(),kind+" third press uses the existing class recovery and spends the air budget");
                });
                c.waitTicks(3); c.getInput().releaseKey(o -> o.keyJump); c.waitTicks(2);
                c.getInput().holdKey(o -> o.keyJump); c.waitTicks(3);
                server.runOnServer(s -> {
                    var f=game().battle.actors.get(id);
                    check(f.recoveries==recoveries+1&&f.recovery.helpless(),kind+" a fourth press cannot grant another boost");
                });
                server.waitFor(s -> {
                    var f=game().battle.actors.get(id);return f.grounded&&f.recovery.available()&&f.recovery.recoveryAvailable();
                },90);
                c.waitTicks(4);
                server.runOnServer(s -> check(game().battle.actors.get(id).grounded,kind+" holding Space through landing does not restart the sequence"));
                c.getInput().releaseKey(o -> o.keyJump); c.waitTicks(2); c.getInput().holdKeyFor(o -> o.keyJump,3);
                server.runOnServer(s -> {
                    var f=game().battle.actors.get(id);
                    check(!f.grounded&&f.recovery.available()&&f.recovery.recoveryAvailable(),kind+" a fresh press after landing starts a new ordinary jump");
                });
                VanillaSmash.LOG.info("JUMP_SEQUENCE_NATIVE_OK {}",kind);
                server.waitFor(s -> !connection.getServerPlayer().getLastClientInput().jump(),10);
                server.runOnServer(s -> {
                    var b = game().battle; var f = b.actors.get(id); b.reset(f, 0, 95); b.reset(b.dummy(), -1, 95); f.facing = 1;
                });
                c.getInput().pressMouse(0);
                server.waitFor(s -> game().battle.dummy().state.percent > 0, 18);
                server.runOnServer(s -> {
                    var f = game().battle.actors.get(id); var d = game().battle.dummy();
                    check(f.state.move.aim() == AttackDirection.NEUTRAL, kind + " native no-direction air click is nair");
                    check(d.vx < 0, "Nair launches a target behind the fighter away from them");
                    check(f.recovery.available() && f.recovery.recoveryAvailable(), "Nair keeps recovery available");
                });
                c.takeScreenshot("movement-nair-" + kind.name().toLowerCase());
                c.waitTicks(18);
                server.runOnServer(s -> check(game().battle.dummy().state.percent >= FighterMoves.light(kind, AttackDirection.NEUTRAL, true).damage() && game().battle.dummy().state.percent <= FighterMoves.light(kind, AttackDirection.NEUTRAL, true).damage()+(kind==FighterClass.ZOMBIE?3:0), "Nair hits once, with at most one companion follow-up"));
                if (FighterMoves.recovery(kind).damage() > 0) {
                    server.runOnServer(s -> {
                        var b = game().battle; var f = b.actors.get(id); b.reset(f, 6, 81); b.reset(b.dummy(), 6, 85);
                        check(b.request(f, true, new Input(true, false, false, false, false, false, false)), "Rising special starts");
                    });
                    server.waitFor(s -> game().battle.dummy().state.percent > 0, 15);
                    server.runOnServer(s -> check(game().battle.dummy().state.percent == FighterMoves.recovery(kind).damage(), "Recovery hits during ascent"));
                    c.waitTicks(20);
                }
            }

            server.runOnServer(s -> { game().leave(connection.getServerPlayer()); game().choose(connection.getServerPlayer(), FighterClass.STEVE, VanillaSmash.Mode.SANDBOX); });
            server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE, 300); c.waitTicks(20);
            // Fixed timing fixtures exercise the exact same request path as native mouse packets.
            int before = server.computeOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id); b.reset(f, 0, 81); b.reset(b.dummy(), 14.5, 81);
                f.facing = -1; f.state.readyAt = game().ticks + 2;
                check(b.request(f, false, new Input(true, false, false, false, false, false, false)), "Early up click is buffered");
                f.facing = 1; return f.lights;
            });
            server.waitFor(s -> game().battle.actors.get(id).lights > before, 8);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id); check(f.state.move.aim() == AttackDirection.UP && f.state.attackDirection == -1, "Buffer retains clicked direction and facing");
            });
            c.waitTicks(15);
            c.getInput().holdKey(o -> o.keyShift); c.waitTicks(3); c.getInput().pressMouse(0);
            c.getInput().releaseKey(o -> o.keyShift);
            server.waitFor(s -> game().battle.actors.get(id).lights >= before + 2, 10);
            c.waitTicks(15);
            c.getInput().holdKey(o -> o.keyShift); c.waitTicks(3); c.getInput().pressMouse(0); c.waitTicks(7);
            int expired = server.computeOnServer(s -> game().battle.actors.get(id).lights);
            c.getInput().releaseKey(o -> o.keyShift); c.waitTicks(6);
            server.runOnServer(s -> check(game().battle.actors.get(id).lights == expired, "An expired shield-buffer click cannot fire later"));
            int landing = server.computeOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id); b.reset(f, 0, 81.1); f.vy = -.2; f.state.readyAt = game().ticks + 2;
                check(b.request(f, false, Input.EMPTY), "Early landing click is buffered"); return f.lights;
            });
            server.waitFor(s -> game().battle.actors.get(id).lights > landing, 8);
            server.runOnServer(s -> check(game().battle.actors.get(id).state.move.id() == 0, "Directionless buffer landing becomes the grounded forward attack"));

            // A held Space does not hijack right-click; bow and jump can be combined.
            server.runOnServer(s -> { game().leave(connection.getServerPlayer()); game().choose(connection.getServerPlayer(), FighterClass.SKELETON, VanillaSmash.Mode.SANDBOX); });
            server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE, 300); c.waitTicks(20);
            c.getInput().holdKey(o -> o.keyJump); c.waitTicks(3); c.getInput().holdMouse(1); c.waitTicks(4);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id); check(f.state.drawingBow() && f.recoveries == 0, "Jump-held right-click still draws the bow");
            });
            c.getInput().releaseMouse(1); c.getInput().releaseKey(o -> o.keyJump); c.waitTicks(20);
            int shots = server.computeOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id); b.reset(f, -6, 81); f.state.readyAt = game().ticks + 2;
                check(b.request(f, true, Input.EMPTY), "Bow buffers near the end of an action"); b.releaseSpecial(f);
                return f.specials;
            });
            server.waitFor(s -> game().battle.actors.get(id).specials > shots && game().battle.actors.get(id).state.impactAt < 0, 12);
            server.runOnServer(s -> check(!game().battle.objects.hasArrow(game().battle.actors.get(id)), "Release before drawing cancels cleanly without inventing bow charge"));
            captured.set(null); server.runOnServer(s -> game().leave(connection.getServerPlayer()));
        } finally { captured.set(null); }
        VanillaSmash.LOG.info("MOVEMENT_CLIENT_TEST_PASSED");
    }
}
