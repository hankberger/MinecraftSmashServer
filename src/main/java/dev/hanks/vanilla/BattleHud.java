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
    private final Map<Integer,Double> eyeOffsets=new HashMap<>();
    private String callout="";
    private String timer="";
    private int calloutUntil,calloutColor;
    public BattleHud(Battle battle) { this.battle = battle; }
    public void announce(String text,int color,int ticks) {callout=text;calloutColor=color;calloutUntil=battle.now()+ticks;refresh();}
    public void refresh() { for(var view:battle.game.viewers.values())update(view,timer); }
    public void update(VanillaSmash.View view, String timer) {
        this.timer=timer;
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
            boolean hit=f.state.lastAttacker!=null && battle.now()-f.state.lastHitAt<8;
            int damageColor=hit?0xffffff:f.state.percent>=120?0xff6868:f.state.percent>=70?0xffc56b:0xf2ead9;
            var line=Component.empty();
            boolean artwork=artwork(view);
            if(artwork)line.append(UiPack.strip("menu_hud_head_"+f.kind.name().toLowerCase(Locale.ROOT))).append(UiPack.space(4));
            line.append(Component.literal("P" + f.slot + " · " + name + "\n").withColor(f.eliminated?0xaaaaaa:f.color()).withStyle(s->s.withBold(own)));
            if(artwork)line.append(UiPack.space(22));
            line.append(Component.literal(f.eliminated?"OUT":f.state.percent+"%  "+stocks).withColor(f.eliminated?0xaaaaaa:damageColor).withStyle(s->s.withBold(own)));
            d.setText(line);
            transform(view,d,(index-1-(count-1)/2.0)*2.8,-4.3,hit?1.22f:1.1f);
            metadata(view,d);
        }
        if (index >= ownRows.size()) { ownRows.add(create(view,0,4.6,.8f)); added=true; }
        var clock = ownRows.get(index);
        var fighter = battle.actors.get(view.player().getUUID());
        clock.setText(Component.literal(battle.challenge == null ? timer
                : battle.challenge.label(battle.now(), fighter != null && fighter.ledge.attached())));
        metadata(view,clock);
        if(++index>=ownRows.size()) {ownRows.add(create(view,0,3.45,1.15f));added=true;}
        var announcement=ownRows.get(index);
        if(battle.now()<calloutUntil && artwork(view) && (callout.endsWith("KO!") || callout.endsWith("OUT!")))
            announcement.setText(Component.empty().append(UiPack.strip("menu_hud_ko")).append(UiPack.space(5))
                    .append(Component.literal(callout.replace("  KO!","")).withColor(calloutColor).withStyle(s->s.withBold(true))));
        else announcement.setText(Component.literal(battle.now()<calloutUntil?callout:"").withColor(calloutColor).withStyle(s->s.withBold(true)));
        metadata(view,announcement);
        if (added) view.rig().passengers();
    }
    private boolean artwork(VanillaSmash.View view) {
        // Arena transfers retain the mandatory lobby pack without a second prompt.
        return battle.game.uiPack.enabled() && (battle.game.uiPack.ready(view.player()) || battle.game.network.enabled());
    }
    private Display.TextDisplay create(VanillaSmash.View view, double x, double y, float scale) {
        var d = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, battle.level);
        d.setTextOpacity((byte)255); d.setBackgroundColor(0); d.setFlags((byte)(Display.TextDisplay.FLAG_SHADOW | Display.TextDisplay.FLAG_ALIGN_LEFT));
        d.setBrightnessOverride(new net.minecraft.util.Brightness(15,15)); d.setLineWidth(220); d.setViewRange(3);
        if (!d.startRiding(view.rig().carrier,true,false)) throw new IllegalStateException("Cannot attach battle HUD");
        view.rig().carrier.positionRider(d);
        eyeOffsets.put(d.getId(),view.camera().getEyeY()-d.getY());
        transform(view,d,x,y,scale);
        view.rig().spawn(d); return d;
    }
    private void transform(VanillaSmash.View view,Display.TextDisplay d,double x,double y,float scale) {
        double eyeOffset=eyeOffsets.get(d.getId());
        d.setTransformation(new Transformation(new Vector3f((float)x,(float)(y+eyeOffset),-8),null,new Vector3f(scale),null));
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
        rows.clear();eyeOffsets.clear();
    }
}
