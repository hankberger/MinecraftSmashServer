package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import dev.hanks.network.Wire;
import dev.hanks.vanilla.mixin.DisplayInterpolationMixin;
import java.util.*;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Private vanilla victory sets on the lobby backend. Arenas can be reused throughout the presentation. */
public final class WinnerStage {
    private final VanillaSmash game;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    public static final class Session {
        public final ServerPlayer player;
        public final Wire.MatchResult result;
        public final int room, openedAt;
        public final List<Entity> entities = new ArrayList<>();
        public final List<LivingEntity> models = new ArrayList<>();
        public LivingEntity camera;
        private Display.BlockDisplay cameraCarrier;
        public List<MatchMenu.Button> controls = List.of();
        private String votes;
        private boolean revealed;
        private Session(ServerPlayer p, Wire.MatchResult result, int room, int now) {
            player = p; this.result = result; this.room = room; openedAt = now;
        }
        public double origin() { return room * ShowcaseBuilder.SPACING; }
        public boolean ready() { return revealed; }
    }
    public WinnerStage(VanillaSmash game) { this.game = game; }
    public Session session(UUID id) { return sessions.get(id); }
    public boolean active(ServerPlayer p) { return sessions.containsKey(p.getUUID()); }
    public boolean owns(Entity e) { return sessions.values().stream().anyMatch(s -> s.entities.contains(e)); }
    public void show(ServerPlayer p, Wire.MatchResult result, List<MatchMenu.Button> controls, String voteText) {
        var s = sessions.get(p.getUUID());
        if (s == null || !s.result.id().equals(result.id())) {
            close(p, false);
            int room = -1; var occupied = new HashSet<Integer>(); sessions.values().forEach(v -> occupied.add(v.room));
            while (occupied.contains(room)) room--;
            var level = game.server.getLevel(MvpWorlds.SHOWCASE);
            s = new Session(p, result, room, game.ticks); sessions.put(p.getUUID(), s);
            try { ShowcaseBuilder.retain(level,room); ShowcaseBuilder.ensureBuilt(level,room); build(s); }
            catch (RuntimeException e) { close(p, true); throw e; }
        }
        s.controls = List.copyOf(controls); s.votes = voteText;
        update(s);
    }
    private void build(Session s) {
        var p = s.player; var level = game.server.getLevel(MvpWorlds.SHOWCASE);
        game.hub.menu.clear(p); p.closeContainer(); p.stopUsingItem();
        p.connection.send(new ClientboundClearTitlesPacket(true));
        p.setGameMode(GameType.ADVENTURE); p.setInvisible(true); p.setInvulnerable(true); p.setNoGravity(true);
        p.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
        p.getAttribute(Attributes.GRAVITY).setBaseValue(0);
        p.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0);
        // Match the detached camera's FOV while the player's view loads the set.
        p.getAbilities().setWalkingSpeed(0); p.getAbilities().setFlyingSpeed(0); p.onUpdateAbilities();
        NativeUi.menuInputInventory(p);
        var start = WinnerCamera.START;
        p.teleportTo(level, s.origin()+start.x(), start.y()-WinnerCamera.PLAYER_EYE_HEIGHT, start.z(), Set.of(), start.yaw(), start.pitch(), false);
        p.setDeltaMovement(Vec3.ZERO); p.setLastClientInput(Input.EMPTY);
        // The display drives one client-interpolated move. Its invisible rider
        // matches the player's eye height, avoiding vanilla's camera-height hop.
        s.cameraCarrier = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        s.cameraCarrier.setNoGravity(true); s.cameraCarrier.setInvulnerable(true);
        ((DisplayInterpolationMixin)s.cameraCarrier).smashInterpolationDuration(WinnerCamera.DURATION);
        s.camera = FighterModels.create(level,FighterClass.STEVE);
        s.camera.setInvisible(true); s.camera.setNoGravity(true); s.camera.setInvulnerable(true); s.camera.setSilent(true);
        s.camera.snapTo(s.origin()+start.x(),start.y()-s.camera.getEyeHeight(),start.z(),start.yaw(),start.pitch());
        pose(s.camera,start.yaw());
        camera(s,start); add(s,s.cameraCarrier); add(s,s.camera);
        if(!s.camera.startRiding(s.cameraCarrier,true,false)) throw new IllegalStateException("Could not mount winner camera");
        s.cameraCarrier.positionRider(s.camera);
        var winner = s.result.rows().stream().filter(r -> r.player().equals(s.result.winner())).findFirst().orElse(null);
        text(s, winner == null ? "DRAW" : "★ WINNER ★", 4, 110.1, 1.3, 3.0f, 0xffd66b);
        if (winner != null) {
            model(s, winner, 3.3, 4);
            text(s, winner.name(), 4, 101.2, 4, 3.2f, PlayerIdentity.color(winner.slot()));
            text(s, FighterClass.valueOf(winner.fighter()).label, 4, 100.4, 4, 1.5f, 0xffffff);
        } else {
            int n = s.result.rows().size();
            for (int i = 0; i < n; i++) model(s, s.result.rows().get(i), n <= 2 ? 2.4 : 1.7, 4 + (i - (n - 1) / 2.0) * 1.8);
            text(s, "DRAW", 4, 101.2, 4, 2.8f, 0xffffff);
        }
        VanillaSmash.LOG.info("SMASH_WINNER_STAGE player={} match={} fighter={} room={}", p.getPlainTextName(), s.result.id(), winner == null ? "DRAW" : winner.fighter(), s.room);
    }
    private void model(Session s, Wire.ResultRow row, double scale, double x) {
        var kind = FighterClass.valueOf(row.fighter()); var level = game.server.getLevel(MvpWorlds.SHOWCASE);
        var body = FighterModels.create(level, kind);
        body.setNoGravity(true); body.setInvulnerable(true); body.setSilent(true);
        body.getAttribute(Attributes.SCALE).setBaseValue(scale);
        if (body instanceof Mob mob) { mob.setNoAi(true); mob.setPersistenceRequired(); }
        Item tool = switch (kind) { case STEVE -> Items.IRON_SWORD; case ALEX -> Items.GOLDEN_SWORD; case SKELETON -> Items.BOW; default -> Items.AIR; };
        body.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        body.snapTo(s.origin() + x, 102, 0, -15, 0); pose(body, -15);
        s.models.add(body); add(s, body);
    }
    private <T extends Entity> T add(Session s, T entity) {
        s.entities.add(entity); entity.addTag(VanillaSmash.TEMP); entity.level().addFreshEntity(entity); return entity;
    }
    private Display.TextDisplay text(Session s, String value, double x, double y, double z, float scale, int color) {
        var d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, game.server.getLevel(MvpWorlds.SHOWCASE));
        d.setText(Component.literal(value).withStyle(style -> style.withColor(color))); d.setBackgroundColor(0); d.setTextOpacity((byte)255);
        d.setFlags((byte)(Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_SEE_THROUGH)); d.setBrightnessOverride(new net.minecraft.util.Brightness(15,15));
        d.setBillboardConstraints(Display.BillboardConstraints.FIXED); d.setViewRange(3); d.setLineWidth(400);
        d.setTransformation(new Transformation(null,null,new Vector3f(scale),null)); d.setPos(s.origin()+x,y,z);
        return add(s,d);
    }
    private void camera(Session s, WinnerCamera.Frame frame) {
        double mountOffset=s.camera.getVehicleAttachmentPoint(s.cameraCarrier).y;
        s.cameraCarrier.snapTo(s.origin()+frame.x(),frame.y()-s.camera.getEyeHeight()+mountOffset,frame.z(),frame.yaw(),frame.pitch());
    }
    public void tick() {
        for (var s : List.copyOf(sessions.values())) {
            var p = s.player;
            if (p.isRemoved() || !p.level().dimension().equals(MvpWorlds.SHOWCASE)) { close(p,false); continue; }
            int age = game.ticks - s.openedAt;
            if (age == WinnerCamera.ATTACH_TICK) {
                // Pairing can deliver the rider before its carrier; repeat their
                // relationship after both have had time to reach the client.
                p.connection.send(new ClientboundSetPassengersPacket(s.cameraCarrier));
                p.connection.send(new ClientboundSetCameraPacket(s.camera));
            }
            if (age == WinnerCamera.MOVE_TICK) camera(s,WinnerCamera.END);
            if (age == WinnerCamera.MOVE_TICK && s.result.winner() != null) {
                s.models.forEach(m -> m.swing(InteractionHand.MAIN_HAND));
                p.level().sendParticles(p, ParticleTypes.FIREWORK, true, false, s.origin()+4,105,0,24,2.5,2,.8,.08);
                sound(s,SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,.5f,1.2f);
            }
            if (age >= WinnerCamera.REVEAL_TICK && !s.revealed) { s.revealed = true; update(s); }
            p.setDeltaMovement(Vec3.ZERO); p.getFoodData().setFoodLevel(20);
            var start = WinnerCamera.START;
            var anchor = new Vec3(s.origin()+start.x(),start.y()-WinnerCamera.PLAYER_EYE_HEIGHT,start.z());
            if (p.position().distanceToSqr(anchor) > 1) p.teleportTo(p.level(),anchor.x,anchor.y,anchor.z,Set.of(),start.yaw(),start.pitch(),false);
            for (var model : s.models) { model.setDeltaMovement(Vec3.ZERO); model.clearFire(); pose(model,-15); }
            if (age % 20 == 0) hint(p);
        }
    }
    private static void pose(LivingEntity body, float yaw) { body.setYRot(yaw); body.setYHeadRot(yaw); body.setYBodyRot(yaw); }
    private void update(Session s) {
        if (s.revealed) game.hub.results.menu.show(s.player,s.result,s.controls,s.votes);
    }
    public boolean selectSlot(ServerPlayer p, int slot) {
        return active(p);
    }
    public void confirm(ServerPlayer p) {
        var s = sessions.get(p.getUUID()); if (s == null) return;
        if (s.ready() && !game.hub.results.menu.active(p)) update(s);
    }
    public void hint(ServerPlayer p) {
        var s = sessions.get(p.getUUID()); if (s == null) return;
        String notice=game.hub.currentNotice(p);
        p.sendOverlayMessage(notice==null?Component.empty():Component.literal(notice));
    }
    private void sound(Session s, SoundEvent sound, float volume, float pitch) {
        s.player.connection.send(new ClientboundSoundPacket(Holder.direct(sound),SoundSource.MASTER,s.camera.getX(),s.camera.getY(),s.camera.getZ(),volume,pitch,game.ticks));
    }
    public void close(UUID id, boolean home) { var s = sessions.get(id); if (s != null) close(s.player,home); }
    public void close(ServerPlayer p, boolean home) {
        var s = sessions.remove(p.getUUID()); if (s == null) return;
        game.hub.results.menu.close(p);
        p.getAbilities().setWalkingSpeed(.1f); p.getAbilities().setFlyingSpeed(.05f); p.onUpdateAbilities();
        p.connection.send(new ClientboundSetCameraPacket(p)); s.entities.forEach(Entity::discard); s.entities.clear();
        ShowcaseBuilder.release(game.server.getLevel(MvpWorlds.SHOWCASE),s.room);
        if (home && !p.isRemoved() && p.level().dimension().equals(MvpWorlds.SHOWCASE)) game.returnFromPicker(p);
    }
    public void closeAll() { for (var s : List.copyOf(sessions.values())) close(s.player,false); }
}
