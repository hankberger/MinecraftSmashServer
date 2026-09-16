package dev.hanks.vanilla;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.UuidArgument;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.slf4j.*;

/** Dedicated minigame host. Every client-visible entity, menu and packet is vanilla. */
public final class VanillaSmash implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("smash_vanilla");
    public static final String TEMP = "smash_vanilla_temporary";
    public enum Mode { MATCH, DUEL, PRACTICE, SANDBOX;
        public boolean training() { return this == PRACTICE || this == SANDBOX; }
    }
    private static VanillaSmash instance;
    public static VanillaSmash instance() { return instance; }
    public final MatchState match = new MatchState();
    public final Map<UUID, FighterClass> choices = new HashMap<>();
    public final CharacterStage stage = new CharacterStage(this);
    public final UiPack uiPack = new UiPack();
    public final FighterMenu fighterMenu = new FighterMenu(this);
    public final GameHub hub = new GameHub(this);
    public final LobbyPlayPoint playPoint = new LobbyPlayPoint(this);
    public final Map<UUID, View> viewers = new LinkedHashMap<>();
    private final Map<UUID, Integer> arrivals = new HashMap<>();
    private final Map<UUID, Integer> cameraDistances = new HashMap<>();
    private final Set<UUID> autoSelected = new HashSet<>();
    public MinecraftServer server;
    public Battle battle;
    public BackendNetwork network;
    public int ticks;
    public record View(ServerPlayer player, ArmorStand camera, int switchAt) {}
    public boolean arriving(ServerPlayer p) { return arrivals.containsKey(p.getUUID()); }

    @Override public void onInitialize() {
        instance = this;
        network = new BackendNetwork(this);
        CommandRegistrationCallback.EVENT.register((d, r, env) -> d.register(Commands.literal("smash")
            .executes(c -> status(c.getSource().getPlayerOrException()))
            .then(Commands.literal("join").executes(c -> hub.open(c.getSource().getPlayerOrException())))
            .then(Commands.literal("fighter").then(Commands.argument("token", UuidArgument.uuid()).then(Commands.argument("button", IntegerArgumentType.integer(0, 100))
                    .executes(c -> fighterMenu.action(c.getSource().getPlayerOrException(), UuidArgument.getUuid(c, "token"), IntegerArgumentType.getInteger(c, "button"))))))
            .then(Commands.literal("duel").executes(c -> pick(c.getSource().getPlayerOrException(), Mode.DUEL)))
            .then(Commands.literal("ffa").executes(c -> pick(c.getSource().getPlayerOrException(), Mode.MATCH)))
            .then(Commands.literal("ui").then(Commands.argument("token", UuidArgument.uuid()).then(Commands.argument("button", IntegerArgumentType.integer(0, 100))
                    .executes(c -> hub.click(c.getSource().getPlayerOrException(), UuidArgument.getUuid(c, "token"), IntegerArgumentType.getInteger(c, "button"))))))
            .then(Commands.literal("party").executes(c -> hub.partyPanel(c.getSource().getPlayerOrException()))
                    .then(Commands.literal("create").executes(c -> hub.partyCommand(c.getSource().getPlayerOrException(), "create", "")))
                    .then(Commands.literal("leave").executes(c -> hub.partyCommand(c.getSource().getPlayerOrException(), "leave", "")))
                    .then(Commands.literal("invite").then(Commands.argument("player", StringArgumentType.word()).executes(c -> hub.partyCommand(c.getSource().getPlayerOrException(), "invite", StringArgumentType.getString(c, "player")))))
                    .then(Commands.literal("accept").then(Commands.argument("player", StringArgumentType.word()).executes(c -> hub.partyCommand(c.getSource().getPlayerOrException(), "accept", StringArgumentType.getString(c, "player"))))))
            .then(Commands.literal("practice").executes(c -> pick(c.getSource().getPlayerOrException(), Mode.PRACTICE)))
            .then(Commands.literal("sandbox").executes(c -> pick(c.getSource().getPlayerOrException(), Mode.SANDBOX)))
            .then(Commands.literal("leave").executes(c -> leave(c.getSource().getPlayerOrException())))
            .then(Commands.literal("lobby").executes(c -> leave(c.getSource().getPlayerOrException())))
            .then(Commands.literal("unqueue").executes(c -> unqueue(c.getSource().getPlayerOrException())))
            .then(Commands.literal("reset").executes(c -> {
                var p = c.getSource().getPlayerOrException(); var f = actor(p);
                if (f != null && fighting(f)) { if (battle.sandbox) battle.resetTraining(); else battle.ringOut(f); }
                return 1;
            }))
            .then(Commands.literal("camera").then(Commands.argument("distance", IntegerArgumentType.integer(18, 40)).executes(c -> {
                var p = c.getSource().getPlayerOrException(); int distance = IntegerArgumentType.getInteger(c, "distance");
                cameraDistances.put(p.getUUID(), distance);
                var view = viewers.get(p.getUUID());
                if (view != null) view.camera().setPos(.5, 84, distance);
                return 1;
            })))
            .then(Commands.literal("dummy")
                .executes(c -> dummy(c.getSource().getPlayerOrException(), false))
                .then(Commands.literal("spar").executes(c -> dummy(c.getSource().getPlayerOrException(), true)))
                .then(Commands.literal("percent").then(Commands.argument("value", IntegerArgumentType.integer(0, 300)).executes(c -> {
                    if (ownsTraining(c.getSource().getPlayerOrException()) && battle.dummy() != null)
                        battle.dummy().state.percent = IntegerArgumentType.getInteger(c, "value");
                    return 1;
                }))))));
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s; ticks = 0; arrivals.clear(); viewers.clear(); choices.clear();
            hub.reset(); network.selections.reset();
            playPoint.close();
            autoSelected.clear();
            match.clearRound(); for (var id : match.queue()) match.dequeue(id);
            battle = null;
            MvpWorlds.prepare(s, !network.arena(), !network.lobby());
            network.start();
            uiPack.start(network.enabled());
            LOG.info("VANILLA_PROBE_READY: Smash Vanilla 0.3.0 role={}, stock Java 26.2 clients", network.role);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { network.close(); uiPack.close(); playPoint.close(); stage.closeAll(); hub.results.scene.closeAll(); endRound(false); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { server = null; battle = null; });
        ServerTickEvents.START_SERVER_TICK.register(this::tick);
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (e.entityTags().contains(TEMP) && !playPoint.owns(e) && !stage.owns(e) && !hub.results.scene.owns(e) && viewers.values().stream().noneMatch(v -> v.camera == e)
                    && (battle == null || battle.timer != e && !battle.displays.contains(e) && battle.actors.values().stream().noneMatch(f -> f.body == e) && !battle.objects.owns(e))) e.discard();
            // Cold chunks can register fresh entities on a later tick. Keep the current session's objects.
        });
        ServerPlayConnectionEvents.JOIN.register((h, sender, s) -> s.execute(() -> arrivals.put(h.player.getUUID(), ticks + 30)));
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> s.execute(() -> {
            hub.disconnected(h.player); depart(h.player, true); uiPack.forget(h.player.getUUID()); arrivals.remove(h.player.getUUID()); cameraDistances.remove(h.player.getUUID()); network.departed(h.player.getUUID());
        }));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, source, amount) -> !MvpWorlds.managed(e.level()));
        PlayerBlockBreakEvents.BEFORE.register((l, p, pos, state, be) -> !MvpWorlds.managed(l));
        UseBlockCallback.EVENT.register((p, l, hand, hit) -> {
            if (p instanceof ServerPlayer sp && MvpWorlds.managed(l)) { playPoint.click(sp,hit.getBlockPos(),hand); return InteractionResult.FAIL; }
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((p, l, hand, e, hit) -> {
            if (p instanceof ServerPlayer sp && MvpWorlds.managed(l)) { if (!playPoint.click(sp,e,hand)) attack(sp, false); return InteractionResult.FAIL; }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((p, l, hand, e, hit) -> p instanceof ServerPlayer sp
                ? playPoint.click(sp,e,hand) ? InteractionResult.FAIL : use(sp, hand) : InteractionResult.PASS);
        UseItemCallback.EVENT.register((p, l, hand) -> p instanceof ServerPlayer sp ? use(sp, hand) : InteractionResult.PASS);
    }

    private InteractionResult use(ServerPlayer p, InteractionHand hand) {
        if (!MvpWorlds.managed(p.level())) return InteractionResult.PASS;
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.FAIL;
        if (hub.results.scene.active(p)) { hub.results.scene.confirm(p); return InteractionResult.FAIL; }
        if (stage.active(p)) { stage.confirm(p); return InteractionResult.FAIL; }
        if (viewers.containsKey(p.getUUID())) {
            boolean accepted = attack(p, true);
            var f = actor(p);
            // Let a real vanilla bow enter its normal use state so release is a native packet.
            if (accepted && f != null && f.state.drawingBow()) return InteractionResult.PASS;
            return InteractionResult.FAIL;
        }
        if (p.getMainHandItem().is(Items.COMPASS)) hub.open(p);
        else if (p.getMainHandItem().is(Items.ARMOR_STAND)) pick(p, Mode.PRACTICE);
        else if (p.getMainHandItem().is(Items.PLAYER_HEAD)) hub.partyPanel(p);
        return InteractionResult.FAIL;
    }

    public Battle.Actor actor(ServerPlayer p) { return battle == null ? null : battle.actors.get(p.getUUID()); }
    public boolean fighting(Battle.Actor f) { return battle != null && !f.eliminated && match.fighting(f.id); }
    public boolean attack(ServerPlayer p, boolean special) {
        var f = actor(p);
        return f != null && p.containerMenu == p.inventoryMenu && battle.request(f, special, p.getLastClientInput());
    }
    public void releaseBow(ServerPlayer p) { var f = actor(p); if (f != null) battle.releaseBow(f); p.stopUsingItem(); }

    public int pick(ServerPlayer p, Mode mode) {
        return hub.selectMode(p, mode);
    }
    public void choose(ServerPlayer p, FighterClass kind, Mode mode) {
        hub.chooseDirect(p, kind, mode);
    }
    private void startQueued() {
        hub.startQueued();
    }
    void begin(List<ServerPlayer> players, Mode mode) {
        battle = new Battle(this, server.getLevel(MvpWorlds.ARENA), mode == Mode.SANDBOX);
        try {
            for (int i = 0; i < players.size(); i++) {
                var p = players.get(i);
                battle.add(p, choices.getOrDefault(p.getUUID(), FighterClass.STEVE), mode == Mode.DUEL ? (i == 0 ? -9.5 : 10.5) : ArenaRules.spawnX(i));
                watch(p);
            }
            if (mode.training()) battle.addDummy(mode == Mode.PRACTICE);
            match.start(new ArrayList<>(battle.actors.keySet()), mode.training());
            if (battle.sandbox) for (int i = 0; i < MatchState.COUNTDOWN_TICKS; i++) match.tick(Map.of());
            LOG.info("VANILLA_PROBE_MATCH_STARTED players={} mode={}", players.size(), mode);
        } catch (RuntimeException failure) {
            LOG.error("Cannot start arena", failure); endRound(true);
        }
    }
    private void watch(ServerPlayer p) {
        hub.menu.clear(p); stage.close(p); p.closeContainer();
        p.setGameMode(GameType.ADVENTURE); p.setInvisible(true); p.setInvulnerable(true); p.setNoGravity(true);
        p.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        p.getAttribute(Attributes.GRAVITY).setBaseValue(0);
        p.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0);
        NativeUi.combatInventory(p, actor(p).kind);
        var level = server.getLevel(MvpWorlds.ARENA);
        p.teleportTo(level, .5, 78, 17, Set.of(), 180, 0, false);
        p.setLastClientInput(Input.EMPTY);
        var camera = new ArmorStand(EntityTypes.ARMOR_STAND, level);
        camera.setInvisible(true); camera.setNoGravity(true); camera.setInvulnerable(true);
        camera.snapTo(.5, 84, cameraDistances.getOrDefault(p.getUUID(), 24), 180, 0);
        camera.setYHeadRot(180); camera.yBodyRot = 180;
        level.addFreshEntity(camera); camera.addTag(TEMP);
        viewers.put(p.getUUID(), new View(p, camera, ticks + 15));
        LOG.info("VANILLA_PROBE_ENTER player={} class={} actor={} camera={}", p.getPlainTextName(), actor(p).kind, actor(p).body.getId(), camera.getId());
    }

    private void tick(MinecraftServer s) {
        if (server != s) return;
        ticks++;
        for (var entry : new ArrayList<>(arrivals.entrySet())) if (ticks >= entry.getValue()) {
            arrivals.remove(entry.getKey()); var p = s.getPlayerList().getPlayer(entry.getKey());
            if (p != null) {
                if (network.arena()) { network.arrival(p); continue; }
                lobby(p, false);
                uiPack.schedule(p,ticks+40);
                if (Boolean.getBoolean("smash_vanilla.autoQueue") && autoSelected.add(p.getUUID())) choose(p, FighterClass.values()[(autoSelected.size() - 1) % 5], Mode.MATCH);
                else if (Boolean.getBoolean("smash_vanilla.autoPractice")) choose(p, FighterClass.STEVE, Mode.SANDBOX);
            }
        }
        stage.tick();
        uiPack.tick(this);
        playPoint.tick();
        for (var p : s.getPlayerList().getPlayers()) {
            var view = viewers.get(p.getUUID());
            if (view != null) {
                if (ticks == view.switchAt || ticks % 40 == 0) p.connection.send(new ClientboundSetCameraPacket(view.camera));
                p.setDeltaMovement(Vec3.ZERO); p.getFoodData().setFoodLevel(20);
                // Prevent a modified client's movement packets from moving the hidden control body elsewhere.
                if (Math.abs(p.getX() - .5) > 1 || Math.abs(p.getY() - 78) > 1 || Math.abs(p.getZ() - 17) > 1)
                    p.teleportTo(server.getLevel(MvpWorlds.ARENA), .5, 78, 17, Set.of(), 180, 0, false);
            } else if (!arrivals.containsKey(p.getUUID()) && p.level().dimension().equals(MvpWorlds.LOBBY)) {
                if (LobbyRules.needsReturn(p.getX(), p.getY(), p.getZ())) lobby(p, true);
                if (ticks % 20 == 0 && p.containerMenu == p.inventoryMenu) status(p);
            }
        }
        if (battle != null) {
            var before = match.phase();
            battle.tick();
            if (!battle.sandbox) match.tick(battle.damage());
            if (before != match.phase()) {
                if (match.phase() == MatchState.Phase.ACTIVE) {
                    for (var f : battle.actors.values()) f.state.respawn(ticks);
                    title("GO!");
                    LOG.info("VANILLA_PROBE_ROUND_ACTIVE humans={} actors={} cameras={}", viewers.size(), battle.actors.size(), viewers.values().stream().filter(v -> !v.camera.isRemoved()).count());
                } else if (match.phase() == MatchState.Phase.RESULTS) {
                    battle.objects.clear();
                    var winner = match.winner() == null ? null : battle.actors.get(match.winner());
                    title(winner == null ? "Draw" : winner.name() + " wins");
                }
            }
            if (match.phase() == MatchState.Phase.COUNTDOWN && match.remaining() % 20 == 0) title(Integer.toString((match.remaining() + 19) / 20));
            if (match.phase() == MatchState.Phase.RESULTS) battle.captureResult();
            if (ticks % 5 == 0) NativeUi.battleHud(this);
            if (match.phase() == MatchState.Phase.RESULTS && match.remaining() == 0) endRound(true);
        }
        startQueued();
        network.tick();
        hub.results.tick();
    }

    private void title(String text) {
        for (var view : viewers.values()) {
            view.player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 5));
            view.player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(text)));
        }
    }
    public int status(ServerPlayer p) {
        if (hub.results.scene.active(p)) { hub.results.scene.hint(p); return 1; }
        if (stage.active(p)) { stage.hint(p); return 1; }
        String hubStatus = hub.status(p); if (hubStatus != null) return tell(p, hubStatus);
        if (network.lobby()) return tell(p, network.lobbyMessage(p.getUUID()));
        int q = match.queue().indexOf(p.getUUID());
        return tell(p, q >= 0 ? "Queued " + (q + 1) + "  ·  " + match.queue().size() + "/4    /smash unqueue" : "/smash join     /smash practice");
    }
    public int unqueue(ServerPlayer p) { hub.cancel(p, true); return status(p); }
    public int leave(ServerPlayer p) {
        if (!viewers.containsKey(p.getUUID()) && !network.arena() && !hub.cancel(p, true)) return 1;
        depart(p, false); if (network.arena()) network.returnPlayer(p); else lobby(p, false); return 1;
    }
    private void depart(ServerPlayer p, boolean disconnected) {
        UUID id = p.getUUID(); hub.results.dismiss(p); stage.close(p); match.dequeue(id); choices.remove(id);
        var view = viewers.remove(id); if (view != null) view.camera.discard();
        if (battle == null || !battle.actors.containsKey(id)) return;
        if (match.phase() == MatchState.Phase.COUNTDOWN) {
            match.cancelCountdown(id); // Includes only humans for public rounds; practice never requeues its dummy.
            endRound(true);
        } else {
            match.forfeit(id); battle.eliminate(battle.actors.get(id));
            if (viewers.isEmpty()) endRound(false);
        }
    }
    void endRound(boolean returnToLobby) {
        if (battle != null) battle.captureResult();
        var result = battle == null ? null : battle.result;
        if (battle != null) battle.close();
        battle = null;
        var old = new ArrayList<>(viewers.values()); viewers.clear();
        for (var view : old) {
            view.camera.discard();
            if (!match.queue().contains(view.player.getUUID())) choices.remove(view.player.getUUID());
            if (returnToLobby && !view.player.isRemoved()) { if (network.arena()) network.returnPlayer(view.player); else lobby(view.player, false); }
        }
        match.clearRound();
        hub.endRound();
        if (returnToLobby && !network.arena() && result != null) hub.results.receive(result);
        if (network.arena() && returnToLobby) network.finish();
    }
    void networkPark(ServerPlayer p) {
        stage.close(p);
        p.closeContainer(); p.stopUsingItem();
        p.connection.send(new ClientboundSetCameraPacket(p));
        p.connection.send(new ClientboundClearTitlesPacket(true));
        p.setGameMode(GameType.ADVENTURE); p.setInvisible(true); p.setInvulnerable(true); p.setNoGravity(true);
        p.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        p.getAttribute(Attributes.GRAVITY).setBaseValue(0);
        p.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0);
        p.getInventory().clearContent(); p.inventoryMenu.broadcastChanges();
        p.teleportTo(server.getLevel(MvpWorlds.ARENA), .5, 78, 17, Set.of(), 180, 0, false);
        p.setDeltaMovement(Vec3.ZERO); p.setLastClientInput(Input.EMPTY);
    }
    void returnFromPicker(ServerPlayer p) { lobby(p, false); }
    private void lobby(ServerPlayer p, boolean rescue) {
        hub.results.scene.close(p,false);
        hub.menu.clear(p);
        stage.close(p);
        p.closeContainer(); p.stopUsingItem();
        p.connection.send(new ClientboundSetCameraPacket(p));
        p.connection.send(new ClientboundClearTitlesPacket(true));
        p.setGameMode(GameType.ADVENTURE); p.setInvisible(false); p.setInvulnerable(false); p.setNoGravity(false);
        p.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.1);
        p.getAttribute(Attributes.GRAVITY).setBaseValue(.08);
        p.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(.42);
        p.setHealth(p.getMaxHealth()); p.getFoodData().setFoodLevel(20); p.fallDistance = 0;
        p.teleportTo(server.getLevel(MvpWorlds.LOBBY), rescue ? LobbyRules.RESCUE_X : LobbyRules.SPAWN_X, 101,
                rescue ? LobbyRules.RESCUE_Z : LobbyRules.SPAWN_Z, Set.of(), 0, 0, true);
        p.setDeltaMovement(Vec3.ZERO); p.setLastClientInput(Input.EMPTY);
        NativeUi.lobbyInventory(p); status(p);
    }
    private boolean ownsTraining(ServerPlayer p) { return battle != null && battle.sandbox && viewers.containsKey(p.getUUID()); }
    private int dummy(ServerPlayer p, boolean spar) {
        if (!ownsTraining(p)) return tell(p, "Dummy controls: /smash sandbox");
        battle.resetTraining(); battle.dummySpar = spar; return 1;
    }
    private int tell(ServerPlayer p, String text) { p.sendOverlayMessage(Component.literal(text)); return 1; }
}
