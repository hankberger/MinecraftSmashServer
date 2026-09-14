package dev.hanks.vanilla;

import com.mojang.math.Transformation;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Vanilla display packets visible only to their viewer, positioned relative to that viewer's camera. */
public final class BattleHud {
    private final Battle battle;
    private final Map<UUID, List<Display.TextDisplay>> rows = new HashMap<>();
    public BattleHud(Battle battle) { this.battle = battle; }
    public void update(VanillaSmash.View view) {
        if (battle.now() < view.switchAt() + 1) return;
        var ownRows = rows.computeIfAbsent(view.player().getUUID(), id -> new ArrayList<>());
        int index = 0, count = battle.actors.size();
        for (var f : battle.actors.values()) {
            boolean fresh = index >= ownRows.size();
            Display.TextDisplay d;
            if (fresh) {
                d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, battle.level); ownRows.add(d);
                d.setTextOpacity((byte)255); d.setBackgroundColor(0); d.setFlags(Display.TextDisplay.FLAG_SHADOW);
                d.setBrightnessOverride(new net.minecraft.util.Brightness(15,15)); d.setLineWidth(220); d.setViewRange(3);
                d.setTransformation(new Transformation(null,null,new Vector3f(1.1f),null));
            } else d = ownRows.get(index);
            double x = view.camera().getX() + (index - (count - 1) / 2.0) * 2.8;
            double y = view.camera().getEyeY() - 4.3, z = view.camera().getZ() - 8;
            boolean moved = d.getX() != x || d.getY() != y || d.getZ() != z;
            d.setPos(x,y,z);
            String name = f.name(); if (name.length() > 12) name = name.substring(0,12);
            boolean own = f.id.equals(view.player().getUUID());
            String stocks = battle.sandbox ? "∞" : "●".repeat(battle.game.match.stocks(f.id));
            var text = Component.literal("P" + f.slot + " · " + name + "\n" + (f.eliminated ? "OUT" : f.state.percent + "%  " + stocks))
                    .withStyle(s -> s.withColor(f.eliminated ? 0xaaaaaa : f.color()).withBold(own));
            d.setText(text);
            if (fresh) view.player().connection.send(new ClientboundAddEntityPacket(d.getId(),d.getUUID(),x,y,z,0,0,EntityTypes.TEXT_DISPLAY,0,Vec3.ZERO,0));
            else if (moved) view.player().connection.send(ClientboundEntityPositionSyncPacket.of(d));
            var data = fresh ? d.getEntityData().getNonDefaultValues() : d.getEntityData().packDirty();
            if (data != null) view.player().connection.send(new ClientboundSetEntityDataPacket(d.getId(),data));
            if (fresh) d.getEntityData().packDirty();
            index++;
        }
    }
    public void close() {
        rows.forEach((id, displays) -> {
            var p = battle.game.server.getPlayerList().getPlayer(id);
            if (p != null) p.connection.send(new ClientboundRemoveEntitiesPacket(displays.stream().mapToInt(Entity::getId).toArray()));
        });
        rows.clear();
    }
}
