package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.*;
import org.joml.Vector3f;

/** Private displays ride the same interpolated carrier as the camera, keeping the HUD steady during pans. */
public final class BattleHud {
    private final Battle battle;
    private final Map<UUID, List<Display.TextDisplay>> rows = new HashMap<>();
    public BattleHud(Battle battle) { this.battle = battle; }
    public void update(VanillaSmash.View view, String timer) {
        if (battle.now() < view.switchAt() + 1) return;
        var ownRows = rows.computeIfAbsent(view.player().getUUID(), id -> new ArrayList<>());
        int index = 0, count = battle.actors.size(); boolean added = false;
        for (var f : battle.actors.values()) {
            if (index >= ownRows.size()) {
                ownRows.add(create(view,(index-(count-1)/2.0)*2.8,-4.3,1.1f)); added = true;
            }
            var d = ownRows.get(index++);
            String name = f.name(); if (name.length() > 12) name = name.substring(0,12);
            boolean own = f.id.equals(view.player().getUUID());
            String stocks = battle.sandbox ? "∞" : "●".repeat(battle.game.match.stocks(f.id));
            d.setText(Component.literal("P" + f.slot + " · " + name + "\n" + (f.eliminated ? "OUT" : f.state.percent + "%  " + stocks))
                    .withStyle(s -> s.withColor(f.eliminated ? 0xaaaaaa : f.color()).withBold(own)));
            metadata(view,d);
        }
        if (index >= ownRows.size()) { ownRows.add(create(view,0,4.6,.8f)); added=true; }
        var clock = ownRows.get(index); clock.setText(Component.literal(timer)); metadata(view,clock);
        if (added) view.rig().passengers();
    }
    private Display.TextDisplay create(VanillaSmash.View view, double x, double y, float scale) {
        var d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, battle.level);
        d.setTextOpacity((byte)255); d.setBackgroundColor(0); d.setFlags(Display.TextDisplay.FLAG_SHADOW);
        d.setBrightnessOverride(new net.minecraft.util.Brightness(15,15)); d.setLineWidth(220); d.setViewRange(3);
        if (!d.startRiding(view.rig().carrier,true,false)) throw new IllegalStateException("Cannot attach battle HUD");
        view.rig().carrier.positionRider(d);
        double eyeOffset = view.camera().getEyeY()-d.getY();
        d.setTransformation(new Transformation(new Vector3f((float)x,(float)(y+eyeOffset),-8),null,new Vector3f(scale),null));
        view.rig().spawn(d); return d;
    }
    private void metadata(VanillaSmash.View view, Display.TextDisplay d) {
        var data = d.getEntityData().packDirty();
        if (data != null) view.player().connection.send(new ClientboundSetEntityDataPacket(d.getId(),data));
    }
    public void close() {
        rows.forEach((id, displays) -> {
            displays.forEach(Entity::stopRiding);
            var p = battle.game.server.getPlayerList().getPlayer(id);
            if (p != null) p.connection.send(new ClientboundRemoveEntitiesPacket(displays.stream().mapToInt(Entity::getId).toArray()));
        });
        rows.clear();
    }
}
