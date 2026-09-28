package dev.hanks.vanilla;

import java.util.Properties;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Real arena collision, native recovery inputs, combat resolution and command lifecycle. */
@SuppressWarnings("UnstableApiUsage")
public final class CombatAgencyClientTest {
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var props = new Properties(); props.setProperty("online-mode", "false"); props.setProperty("server-ip", "127.0.0.1");
        props.setProperty("view-distance", "6"); props.setProperty("simulation-distance", "5"); props.setProperty("allow-flight", "true");
        try (var server = c.worldBuilder().createServer(props); var connection = server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280, 720);
            c.runOnClient(mc -> { mc.options.fov().set(70); mc.options.guiScale().set(2); mc.resizeGui(); });
            server.waitFor(s -> game().hub.available(connection.getServerPlayer()), 240);
            var id = server.computeOnServer(s -> connection.getServerPlayer().getUUID());
            for (var kind : FighterClass.values()) {
                server.runOnServer(s -> {
                    if (game().battle != null) game().leave(connection.getServerPlayer());
                    game().choose(connection.getServerPlayer(), kind, VanillaSmash.Mode.SANDBOX);
                });
                server.waitFor(s -> game().battle != null && game().match.phase() == MatchState.Phase.ACTIVE, 300);
                c.waitFor(mc -> MvpWorlds.battle(mc.level) && mc.getCameraEntity() != mc.player, 300);
                c.runOnClient(mc -> mc.player.connection.sendCommand("smash challenge recovery"));
                server.waitFor(s -> game().battle.challenge != null, 30);
                if (kind == FighterClass.STEVE) {
                    c.waitTicks(8); c.takeScreenshot("agency-recovery-instructions");
                }
                for (int round = 0; round < 3; round++) {
                    final int expected = round + 1;
                    server.waitFor(s -> game().battle.challenge.phase() == RecoveryChallenge.Phase.ATTEMPT, 60);
                    final int side = server.computeOnServer(s -> game().battle.challenge.side());
                    c.getInput().holdKey(o -> side < 0 ? o.keyRight : o.keyLeft);
                    c.getInput().holdKeyFor(o -> o.keyJump, 6);
                    c.waitTicks(3);
                    c.getInput().holdKeyFor(o -> o.keyJump, 4);
                    server.waitFor(s -> game().battle.challenge.completed() >= expected, 150);
                    c.getInput().releaseKey(o -> side < 0 ? o.keyRight : o.keyLeft);
                    server.waitFor(s -> !connection.getServerPlayer().getLastClientInput().left()
                            && !connection.getServerPlayer().getLastClientInput().right(), 10);
                }
                server.runOnServer(s -> {
                    var b = game().battle; var f = b.actors.get(id);
                    check(!b.challenge.active() && b.challenge.attempts() == 3, kind + " completes every recovery with native inputs");
                    check(f.state.falls == 0 && f.state.percent == 0, "Lesson does not add deaths or damage");
                });
                VanillaSmash.LOG.info("AGENCY_RECOVERY_OK {}", kind);
            }
            c.takeScreenshot("agency-recovery-complete");
            c.runOnClient(mc -> mc.player.connection.sendCommand("smash reset"));
            server.waitFor(s -> game().battle.challenge.active(), 20);
            server.waitFor(s -> game().battle.challenge.phase() == RecoveryChallenge.Phase.ATTEMPT, 60);
            server.runOnServer(s -> {
                var b = game().battle; b.ringOut(b.actors.get(id));
                check(b.challenge.phase() == RecoveryChallenge.Phase.RETRY, "Fall starts automatic retry");
            });
            server.waitFor(s -> game().battle.challenge.attempts() == 2, 40);
            c.runOnClient(mc -> mc.player.connection.sendCommand("smash challenge stop"));
            server.waitFor(s -> game().battle.challenge == null, 20);
            server.runOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id); var dummy = b.dummy();
                b.reset(f, 0, 81); b.reset(dummy, 2, 81);
                f.state.requestGuard(b.now(), true, true);
                b.hit(dummy, f, -1, FighterMoves.light(FighterClass.ZOMBIE, AttackDirection.FORWARD, false));
                check(f.state.percent == 0 && f.state.guard == 96, "Parry reduces guard damage");
                check(!f.state.paused(b.now()) && dummy.state.paused(b.now()), "Melee parry gives defender time to act");
                f.state.requestGuard(b.now(), false, true);
                check(b.request(f, false, net.minecraft.world.entity.player.Input.EMPTY), "Can retaliate on parry release");
                b.reset(f, 0, 81); b.reset(dummy, 12, 81);
                f.state.requestGuard(b.now(), true, true);
                b.hit(dummy, f, -1, FighterMoves.special(FighterClass.ZOMBIE, AttackDirection.DOWN, false, false));
                check(!dummy.state.paused(b.now()) && dummy.state.readyAt == b.now(), "Detached parry never freezes distant owner");
                b.reset(f, 0, 95); b.reset(dummy, 12, 81);
            });
            c.getInput().holdKey(o -> o.keyLeft);
            server.waitFor(s -> connection.getServerPlayer().getLastClientInput().left(), 10);
            double originalX = server.computeOnServer(s -> {
                var b = game().battle; var f = b.actors.get(id); b.reset(f, 0, 95);
                b.hit(b.dummy(), f, 1, FighterMoves.light(FighterClass.STEVE, AttackDirection.FORWARD, false));
                check(f.state.launchInfluencePending, "Hit arms one influence adjustment"); return f.vx;
            });
            server.waitFor(s -> !game().battle.actors.get(id).state.launchInfluencePending, 12);
            server.runOnServer(s -> {
                var f = game().battle.actors.get(id);
                check(f.vx > 0 && f.vx < originalX * MovementRules.LAUNCH_DRAG, "Native inward input bends launch without reversing it");
                check(f.state.stunUntil > game().ticks, "Influence does not cancel stun");
            });
            c.getInput().releaseKey(o -> o.keyLeft);
            VanillaSmash.LOG.info("AGENCY_COMBAT_OK");
            c.runOnClient(mc -> mc.player.connection.sendCommand("smash leave"));
            server.waitFor(s -> game().battle == null, 80);
        }
    }
}
