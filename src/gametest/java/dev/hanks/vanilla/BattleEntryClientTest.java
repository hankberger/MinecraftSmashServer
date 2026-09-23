package dev.hanks.vanilla;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.Display;

/** Observe the first visible arena ticks, including the wait for the rest of a roster. */
@SuppressWarnings("UnstableApiUsage")
public final class BattleEntryClientTest {
    private record Sample(boolean self, boolean riding, int camera, double x, double eyeY, double z, double fov) {}
    private static VanillaSmash game() { return VanillaSmash.instance(); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void run(ClientGameTestContext c) {
        var capture = new AtomicBoolean(); var samples = new ArrayList<Sample>();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            var eye = mc.getCameraEntity();
            if (!capture.get() || eye == null || mc.level == null || !MvpWorlds.battle(mc.level)) return;
            samples.add(new Sample(eye == mc.player, eye.getVehicle() instanceof Display.BlockDisplay, eye.getId(),
                    eye.getX(), eye.getEyeY(), eye.getZ(), mc.gameRenderer.mainCamera().getFov()));
        });
        var props = new Properties(); props.setProperty("online-mode","false"); props.setProperty("server-ip","127.0.0.1");
        props.setProperty("view-distance","6"); props.setProperty("simulation-distance","5"); props.setProperty("allow-flight","true");
        try (var server = c.worldBuilder().createServer(props); var connection = server.connect()) {
            connection.waitForChunksRender(); c.getInput().resizeWindow(1280,720);
            c.runOnClient(mc -> mc.options.fov().set(70));
            server.waitFor(s -> game().hub.available(connection.getServerPlayer()),240); c.waitTicks(10);
            // Direct local/practice entry must attach on the first arena tick, not 15 ticks later.
            capture.set(true);
            server.runOnServer(s -> game().begin(List.of(connection.getServerPlayer()),VanillaSmash.Mode.SANDBOX));
            c.waitFor(mc -> MvpWorlds.battle(mc.level),200); c.waitTicks(35);
            capture.set(false); verify(samples,true);
            c.takeScreenshot("battle-entry-immediate");
            server.runOnServer(s -> game().leave(connection.getServerPlayer()));
            c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity()==mc.player,200);
            server.runOnServer(s -> check(connection.getServerPlayer().getAbilities().getWalkingSpeed()==.1f,"Lobby movement restored"));
            c.waitTicks(10); samples.clear(); capture.set(true);
            // The arena transfer waiting view and the live match reuse one camera entity.
            server.runOnServer(s -> game().networkPark(connection.getServerPlayer()));
            c.waitFor(mc -> MvpWorlds.battle(mc.level),200); c.waitTicks(12);
            int waitingEye = c.computeOnClient(mc -> mc.getCameraEntity().getId());
            server.runOnServer(s -> {
                game().networkPark(connection.getServerPlayer()); // Arrival acknowledgement must be idempotent.
                game().begin(List.of(connection.getServerPlayer()),VanillaSmash.Mode.SANDBOX);
                check(game().battle!=null,"Waiting player entered a valid match");
            });
            c.waitTicks(12); capture.set(false); verify(samples,false);
            check(samples.stream().allMatch(s -> s.camera==waitingEye),"Match starts without replacing or detaching the waiting camera");
            int carrier = c.computeOnClient(mc -> mc.getCameraEntity().getVehicle().getId());
            server.runOnServer(s -> game().leave(connection.getServerPlayer()));
            c.waitFor(mc -> mc.level.dimension().equals(MvpWorlds.LOBBY) && mc.getCameraEntity()==mc.player,200);
            c.runOnClient(mc -> check(mc.level.getEntity(waitingEye)==null && mc.level.getEntity(carrier)==null,"Leaving removes the waiting rig too"));
            VanillaSmash.LOG.info("BATTLE_ENTRY_CLIENT_TEST_PASSED: immediate camera, steady FOV, reused arrival rig, cleanup");
        } finally { capture.set(false); }
    }
    private static void verify(List<Sample> samples, boolean stablePosition) {
        check(samples.size()>=10,"Captured initial arena ticks");
        var first = samples.getFirst();
        for (int i=0; i<samples.size(); i++) {
            var s = samples.get(i);
            check(!s.self && s.riding,"First arena frame already uses the mounted match camera: tick "+i);
            // The renderer's FOV is from the preceding render, so skip the dimension-loading sample.
            if (i>0) check(Math.abs(s.fov-70)<.1,"No entry zoom: tick "+i+" FOV "+s.fov);
            if (stablePosition) check(Math.abs(s.x-first.x)<.01 && Math.abs(s.eyeY-first.eyeY)<.01 && Math.abs(s.z-first.z)<.01,"Opening frame is settled");
        }
    }
}
