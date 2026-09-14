package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import java.util.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
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

/** A server-owned preview session. Draft choices never enter matchmaking. */
public final class CharacterStage {
    private static final FighterClass[] ROSTER = FighterClass.values();
    private final VanillaSmash game;
    private final Map<UUID, Session> sessions = new LinkedHashMap<>();
    public CharacterStage(VanillaSmash game) { this.game = game; }
    public static final class Session {
        public final ServerPlayer player;
        public final VanillaSmash.Mode mode;
        public final int room, openedAt;
        public final List<Entity> entities = new ArrayList<>();
        public final List<Display.TextDisplay> labels = new ArrayList<>();
        public FighterClass selected = FighterClass.STEVE;
        public LivingEntity preview;
        public ArmorStand camera;
        private Display.TextDisplay name;
        private int changedAt, lastUseAt;
        private boolean armed;
        private Session(ServerPlayer player, VanillaSmash.Mode mode, int room, int now) {
            this.player = player; this.mode = mode; this.room = room; openedAt = changedAt = lastUseAt = now;
        }
        public double origin() { return room * ShowcaseBuilder.SPACING; }
    }
    public Session session(UUID id) { return sessions.get(id); }
    public boolean active(ServerPlayer p) { return sessions.containsKey(p.getUUID()); }
    public boolean owns(Entity e) { return sessions.values().stream().anyMatch(s -> s.entities.contains(e)); }
    public void open(ServerPlayer p, VanillaSmash.Mode mode) {
        close(p);
        int room = 0;
        var occupied = new HashSet<Integer>(); sessions.values().forEach(s -> occupied.add(s.room));
        while (occupied.contains(room)) room++;
        var level = game.server.getLevel(MvpWorlds.SHOWCASE);
        ShowcaseBuilder.ensureBuilt(level, room);
        var s = new Session(p, mode, room, game.ticks);
        sessions.put(p.getUUID(), s);
        try {
            p.closeContainer(); p.stopUsingItem();
            p.setGameMode(GameType.ADVENTURE); p.setInvisible(true); p.setInvulnerable(true); p.setNoGravity(true);
            p.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
            p.getAttribute(Attributes.GRAVITY).setBaseValue(0);
            p.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0);
            NativeUi.combatInventory(p, FighterClass.STEVE);
            heldSlot(s, 0);
            p.teleportTo(level, s.origin(), 104.5, 14, Set.of(), 180, 8, false);
            p.setDeltaMovement(Vec3.ZERO); p.setLastClientInput(Input.EMPTY);
            s.camera = new ArmorStand(EntityTypes.ARMOR_STAND, level);
            s.camera.setInvisible(true); s.camera.setNoGravity(true); s.camera.setInvulnerable(true);
            s.camera.snapTo(s.origin(), 104.5, 14, 180, 8);
            s.camera.setYHeadRot(180); s.camera.yBodyRot = 180;
            add(s, s.camera);
            text(s, "CHOOSE YOUR FIGHTER", -5, 107.3, 1.4, 2.3f, 0xf4eadc);
            text(s, mode == VanillaSmash.Mode.MATCH ? "MATCH" : mode == VanillaSmash.Mode.SANDBOX ? "SANDBOX" : "PRACTICE", -5, 108.7, 1.4, 1.3f, 0xffffff);
            for (int i = 0; i < ROSTER.length; i++) {
                var model = model(s, ROSTER[i], 1.1, ShowcaseBuilder.rosterX(i), ShowcaseBuilder.rosterY(i), ShowcaseBuilder.rosterZ(i));
                pose(model, 0);
                var label = text(s, "", ShowcaseBuilder.rosterX(i), ShowcaseBuilder.rosterY(i) - .8, i < 3 ? 1.6 : 3.4, 1.8f, 0xffffff);
                s.labels.add(label);
            }
            s.name = text(s, "", 4, 100.65, 4.2, 3.7f, 0xffffff);
            text(s, "Default", 4, 109.4, 1.2, 1.65f, 0xffffff);
            update(s, false);
            hint(p);
            VanillaSmash.LOG.info("SMASH_PICKER_OPEN player={} room={} mode={}", p.getPlainTextName(), room, mode);
        } catch (RuntimeException failure) {
            close(p); game.returnFromPicker(p);
            throw failure;
        }
    }
    private <T extends Entity> T add(Session s, T entity) {
        s.entities.add(entity); entity.addTag(VanillaSmash.TEMP);
        entity.level().addFreshEntity(entity);
        return entity;
    }
    private LivingEntity model(Session s, FighterClass kind, double scale, double x, double y, double z) {
        var body = FighterModels.create(game.server.getLevel(MvpWorlds.SHOWCASE), kind);
        body.setNoGravity(true); body.setInvulnerable(true); body.setSilent(true);
        body.getAttribute(Attributes.SCALE).setBaseValue(scale);
        if (body instanceof Mob mob) { mob.setNoAi(true); mob.setPersistenceRequired(); }
        if (kind == FighterClass.STEVE) body.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        if (kind == FighterClass.ALEX) body.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        if (kind == FighterClass.SKELETON) body.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        body.snapTo(s.origin() + x, y, z, 20, 0);
        return add(s, body);
    }
    private Display.TextDisplay text(Session s, String value, double x, double y, double z, float scale, int color) {
        var text = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, game.server.getLevel(MvpWorlds.SHOWCASE));
        text.setText(Component.literal(value).withStyle(style -> style.withColor(color)));
        text.setBackgroundColor(0); text.setTextOpacity((byte)255);
        text.setFlags(Display.TextDisplay.FLAG_SHADOW);
        text.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        text.setTransformation(new Transformation(null, null, new Vector3f(scale), null));
        text.setPos(s.origin() + x, y, z); text.setViewRange(2); text.setLineWidth(240);
        return add(s, text);
    }
    private void update(Session s, boolean effect) {
        if (s.preview != null) { s.preview.discard(); s.entities.remove(s.preview); }
        s.preview = model(s, s.selected, 3.5, 4, 102, 0);
        s.changedAt = game.ticks;
        for (int i = 0; i < ROSTER.length; i++) {
            var kind = ROSTER[i]; boolean selected = kind == s.selected;
            s.labels.get(i).setText(Component.literal((i + 1) + " " + kind.label)
                    .withStyle(style -> style.withColor(selected ? kind.accent & 0xffffff : 0xd5cabc).withBold(selected)));
        }
        s.name.setText(Component.literal(s.selected.label).withStyle(style -> style.withColor(s.selected.accent & 0xffffff).withBold(true)));
        if (effect) {
            var level = game.server.getLevel(MvpWorlds.SHOWCASE);
            level.sendParticles(s.player, ParticleTypes.HAPPY_VILLAGER, false, false, s.origin() + 4, 102.4, 0, 10, 1.2, .2, 1.2, .02);
            sound(s, SoundEvents.UI_BUTTON_CLICK.value(), .25f, 1.15f);
        }
    }
    /** Vanilla scroll wraps across nine slots. Fold its two boundary steps into a five-fighter ring. */
    public boolean selectSlot(ServerPlayer p, int slot) {
        var s = sessions.get(p.getUUID()); if (s == null) return false;
        int index = s.selected.ordinal();
        if (slot >= 0 && slot < ROSTER.length) index = slot;
        else if (index == 0 && slot == 8) index = 4;
        else if (index == 4 && slot == 5) index = 0;
        if (s.selected != ROSTER[index]) { s.selected = ROSTER[index]; update(s, true); }
        heldSlot(s, index);
        return true;
    }
    private void heldSlot(Session s, int slot) {
        s.player.getInventory().setSelectedSlot(slot);
        s.player.connection.send(new ClientboundSetHeldSlotPacket(slot));
    }
    public void confirm(ServerPlayer p) {
        var s = sessions.get(p.getUUID()); if (s == null) return;
        s.lastUseAt = game.ticks;
        if (!s.armed || p.containerMenu != p.inventoryMenu) return;
        var kind = s.selected; var mode = s.mode;
        sound(s, SoundEvents.NOTE_BLOCK_CHIME.value(), .55f, 1.25f);
        close(p);
        game.returnFromPicker(p);
        game.choose(p, kind, mode);
        VanillaSmash.LOG.info("SMASH_PICKER_CONFIRMED player={} class={} mode={}", p.getPlainTextName(), kind, mode);
    }
    public void cancel(ServerPlayer p) {
        if (!active(p)) return;
        close(p); game.returnFromPicker(p);
    }
    /** Disconnect, dimension changes, and server shutdown all discard the same owned entities. */
    public void close(ServerPlayer p) {
        var s = sessions.remove(p.getUUID());
        if (s == null) return;
        p.connection.send(new ClientboundSetCameraPacket(p));
        s.entities.forEach(Entity::discard); s.entities.clear();
    }
    public void closeAll() { for (var s : List.copyOf(sessions.values())) close(s.player); }
    public void tick() {
        for (var s : List.copyOf(sessions.values())) {
            var p = s.player;
            if (p.isRemoved() || !p.level().dimension().equals(MvpWorlds.SHOWCASE)) { close(p); continue; }
            if (game.ticks >= s.openedAt + 20 && game.ticks - s.lastUseAt >= 8) s.armed = true;
            if (game.ticks == s.openedAt + 15 || game.ticks % 40 == 0) p.connection.send(new ClientboundSetCameraPacket(s.camera));
            p.setDeltaMovement(Vec3.ZERO); p.getFoodData().setFoodLevel(20);
            if (p.position().distanceToSqr(new Vec3(s.origin(), 104.5, 14)) > 1)
                p.teleportTo(p.level(), s.origin(), 104.5, 14, Set.of(), 180, 8, false);
            for (var entity : s.entities) if (entity instanceof LivingEntity body && entity != s.camera) {
                body.setDeltaMovement(Vec3.ZERO); body.clearFire();
                if (body == s.preview) pose(body, 20 + Math.max(0, game.ticks - s.changedAt - 30) * .45f);
            }
            if (game.ticks % 20 == 0) hint(p);
        }
    }
    private static void pose(LivingEntity body, float yaw) {
        body.setYRot(yaw); body.setXRot(0); body.setYHeadRot(yaw); body.setYBodyRot(yaw);
    }
    public void hint(ServerPlayer p) {
        p.sendOverlayMessage(Component.literal("Scroll / 1–5  ·  Right-click: confirm  ·  Q: back"));
    }
    private void sound(Session s, SoundEvent sound, float volume, float pitch) {
        s.player.connection.send(new ClientboundSoundPacket(net.minecraft.core.Holder.direct(sound), SoundSource.MASTER,
                s.camera.getX(), s.camera.getY(), s.camera.getZ(), volume, pitch, game.ticks));
    }
}
