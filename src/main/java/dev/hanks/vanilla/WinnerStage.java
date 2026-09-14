package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import dev.hanks.network.Wire;
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
import net.minecraft.world.entity.decoration.ArmorStand;
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
        public final List<Display.TextDisplay> labels = new ArrayList<>();
        public ArmorStand camera;
        public List<MatchMenu.Button> controls = List.of();
        public int selected;
        private Display.TextDisplay votes;
        private int lastUseAt;
        private boolean armed, revealed;
        private Session(ServerPlayer p, Wire.MatchResult result, int room, int now) {
            player = p; this.result = result; this.room = room; openedAt = lastUseAt = now;
        }
        public double origin() { return room * ShowcaseBuilder.SPACING; }
        public boolean ready() { return revealed && armed; }
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
            ShowcaseBuilder.ensureBuilt(level, room);
            s = new Session(p, result, room, game.ticks); sessions.put(p.getUUID(), s);
            try { build(s); }
            catch (RuntimeException e) { close(p, true); throw e; }
        }
        s.controls = List.copyOf(controls); s.selected = Math.min(s.selected, Math.max(0, controls.size() - 1));
        s.votes.setText(Component.literal(voteText).withStyle(style -> style.withColor(0xd5cabc)));
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
        NativeUi.combatInventory(p, FighterClass.STEVE); held(s);
        p.teleportTo(level, s.origin(), 104.5, 14, Set.of(), 180, 8, false);
        p.setDeltaMovement(Vec3.ZERO); p.setLastClientInput(Input.EMPTY);
        s.camera = new ArmorStand(EntityTypes.ARMOR_STAND, level);
        s.camera.setInvisible(true); s.camera.setNoGravity(true); s.camera.setInvulnerable(true);
        camera(s, 0); add(s, s.camera);
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
        text(s, Wire.label(s.result.mode()), -5, 109.1, 1.4, 1.7f, 0xffffff);
        var rows = s.result.rows().stream().sorted(Comparator.comparing((Wire.ResultRow r) -> !r.player().equals(s.result.winner()))
                .thenComparing(Comparator.comparingInt(Wire.ResultRow::stocks).reversed())).toList();
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var label = text(s, "P" + row.slot() + " " + row.name() + "  ·  " + row.knockouts() + " KO  ·  " + row.damage() + "%",
                    -5, 107.5 - i * .9, 1.4, 1.35f, PlayerIdentity.color(row.slot()));
            if (row.player().equals(p.getUUID())) label.setText(label.getText().copy().withStyle(style -> style.withBold(true)));
        }
        s.votes = text(s, "", -5, 103.5, 1.4, 1.35f, 0xd5cabc);
        for (int i = 0; i < 4; i++) s.labels.add(text(s, "", -5, 102.3 - i * 1.05, 1.6, 1.8f, 0xffffff));
        VanillaSmash.LOG.info("SMASH_WINNER_STAGE player={} match={} fighter={} room={}", p.getPlainTextName(), s.result.id(), winner == null ? "DRAW" : winner.fighter(), s.room);
    }
    private void model(Session s, Wire.ResultRow row, double scale, double x) {
        var kind = FighterClass.valueOf(row.fighter()); var level = game.server.getLevel(MvpWorlds.SHOWCASE);
        var body = FighterModels.create(level, kind);
        body.setNoGravity(true); body.setInvulnerable(true); body.setSilent(true);
        body.getAttribute(Attributes.SCALE).setBaseValue(scale);
        if (body instanceof Mob mob) { mob.setNoAi(true); mob.setPersistenceRequired(); }
        Item tool = switch (kind) { case STEVE -> Items.IRON_SWORD; case ALEX -> Items.IRON_AXE; case SKELETON -> Items.BOW; default -> Items.AIR; };
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
    private void camera(Session s, int age) {
        var frame = WinnerCamera.at(age); s.camera.snapTo(s.origin()+frame.x(), frame.y(), frame.z(), frame.yaw(), frame.pitch());
        s.camera.setYHeadRot(frame.yaw()); s.camera.yBodyRot = frame.yaw();
    }
    public void tick() {
        for (var s : List.copyOf(sessions.values())) {
            var p = s.player;
            if (p.isRemoved() || !p.level().dimension().equals(MvpWorlds.SHOWCASE)) { close(p,false); continue; }
            int age = game.ticks - s.openedAt;
            if (age <= WinnerCamera.REVEAL_TICK) {
                camera(s,age);
                if (age >= WinnerCamera.ATTACH_TICK) p.connection.send(ClientboundEntityPositionSyncPacket.of(s.camera));
            }
            if (age == WinnerCamera.ATTACH_TICK || game.ticks % 40 == 0) p.connection.send(new ClientboundSetCameraPacket(s.camera));
            if (age == 32 && s.result.winner() != null) {
                s.models.forEach(m -> m.swing(InteractionHand.MAIN_HAND));
                p.level().sendParticles(p, ParticleTypes.FIREWORK, true, false, s.origin()+4,105,0,24,2.5,2,.8,.08);
                sound(s,SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,.5f,1.2f);
            }
            if (age >= WinnerCamera.REVEAL_TICK && !s.revealed) { s.revealed = true; update(s); }
            if (s.revealed && game.ticks - s.lastUseAt >= 8) s.armed = true;
            p.setDeltaMovement(Vec3.ZERO); p.getFoodData().setFoodLevel(20);
            if (p.position().distanceToSqr(new Vec3(s.origin(),104.5,14)) > 1) p.teleportTo(p.level(),s.origin(),104.5,14,Set.of(),180,8,false);
            for (var model : s.models) { model.setDeltaMovement(Vec3.ZERO); model.clearFire(); pose(model, (float)(-15 + Math.sin(Math.max(0,age-70)/55.0)*8)); }
            if (age % 20 == 0) hint(p);
        }
    }
    private static void pose(LivingEntity body, float yaw) { body.setYRot(yaw); body.setYHeadRot(yaw); body.setYBodyRot(yaw); }
    private void update(Session s) {
        for (int i = 0; i < s.labels.size(); i++) {
            boolean chosen = i == s.selected;
            s.labels.get(i).setText(Component.literal(!s.revealed || i >= s.controls.size() ? "" : (chosen ? "› " : "") + (i+1) + "  " + s.controls.get(i).label())
                    .withStyle(style -> style.withColor(chosen ? 0xffd66b : 0xffffff).withBold(chosen)));
        }
        held(s);
    }
    private void held(Session s) { s.player.getInventory().setSelectedSlot(s.selected); s.player.connection.send(new ClientboundSetHeldSlotPacket(s.selected)); }
    public boolean selectSlot(ServerPlayer p, int slot) {
        var s = sessions.get(p.getUUID()); if (s == null) return false;
        if (!s.revealed) { held(s); return true; }
        int count = s.controls.size(); if (count == 0) return true;
        if (slot >= 0 && slot < count) s.selected = slot;
        else if (s.selected == 0 && slot == 8) s.selected = count - 1;
        else if (s.selected == count - 1 && slot == count) s.selected = 0;
        update(s); sound(s,SoundEvents.UI_BUTTON_CLICK.value(),.2f,1.1f); return true;
    }
    public void confirm(ServerPlayer p) {
        var s = sessions.get(p.getUUID()); if (s == null) return;
        s.lastUseAt = game.ticks;
        if (!s.ready() || p.containerMenu != p.inventoryMenu || s.controls.isEmpty()) return;
        s.armed = false; sound(s,SoundEvents.UI_BUTTON_CLICK.value(),.4f,1.2f); s.controls.get(s.selected).action().run();
    }
    public void hint(ServerPlayer p) {
        var s = sessions.get(p.getUUID()); if (s == null) return;
        p.sendOverlayMessage(Component.literal(s.revealed ? "Scroll / 1–" + s.controls.size() + "  ·  Right-click  ·  Q: lobby" : ""));
    }
    private void sound(Session s, SoundEvent sound, float volume, float pitch) {
        s.player.connection.send(new ClientboundSoundPacket(Holder.direct(sound),SoundSource.MASTER,s.camera.getX(),s.camera.getY(),s.camera.getZ(),volume,pitch,game.ticks));
    }
    public void close(UUID id, boolean home) { var s = sessions.get(id); if (s != null) close(s.player,home); }
    public void close(ServerPlayer p, boolean home) {
        var s = sessions.remove(p.getUUID()); if (s == null) return;
        p.connection.send(new ClientboundSetCameraPacket(p)); s.entities.forEach(Entity::discard); s.entities.clear();
        if (home && !p.isRemoved() && p.level().dimension().equals(MvpWorlds.SHOWCASE)) game.returnFromPicker(p);
    }
    public void closeAll() { for (var s : List.copyOf(sessions.values())) close(s.player,false); }
}
