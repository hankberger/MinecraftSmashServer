package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.server.level.ServerPlayer;

/** Lobby identity and the personal progression summary; no survival-XP authority. */
public final class PlayerLevels {
    private final VanillaSmash game;
    public PlayerLevels(VanillaSmash game){this.game=game;}
    public String label(ServerPlayer p){var v=game.points.progress(p.getUUID());return "Lv. "+v.level()+" · "+v.into()+"/"+v.required()+" XP";}
    public Component tabName(ServerPlayer p){
        var v=game.points.progress(p.getUUID());
        var name=Component.literal("["+v.level()+"] ").withColor(v.tier().color);
        if(game.points.member(p.getUUID()))name.append(Component.literal("[PLUS] ").withColor(0x98dfb9));
        return name.append(Component.literal(p.getPlainTextName()).withColor(UiTheme.CREAM));
    }
    public void tick(){
        if(game.network.arena()||game.ticks%20!=0)return;
        for(var p:game.server.getPlayerList().getPlayers()){
            var v=game.points.progress(p.getUUID());
            boolean lobby=p.level().dimension().equals(MvpWorlds.LOBBY)&&!game.arriving(p);
            p.connection.send(new ClientboundSetExperiencePacket(lobby?v.fraction():0,0,lobby?(int)Math.min(Integer.MAX_VALUE,v.level()):0));
        }
    }
    public int show(ServerPlayer p){
        if(!game.hub.available(p)||!p.level().dimension().equals(MvpWorlds.LOBBY)){
            game.hub.notice(p.getUUID(),"View your level in the lobby");return 1;
        }
        game.rankings.close(p);game.hub.social.close(p);
        var v=game.points.progress(p.getUUID());var next=LevelRules.nextTier(v.level());
        if(game.uiPack.ready(p)){
            game.hub.menu.showPanel(p,"Your level",LevelCanvas.render(v),LevelCanvas.WIDTH+8,"Back to lobby",()->game.hub.menu.clear(p));return 1;
        }
        String body=v.tier().label+"\n\n"+"▰".repeat(Math.round(v.fraction()*24))+"▱".repeat(24-Math.round(v.fraction()*24))
                +"\n"+PointRules.format(v.into())+" / "+PointRules.format(v.required())+" XP\n"+PointRules.format(v.remaining())+" XP to Level "+(v.level()+1)
                +(next==null?"":"\n\nNext milestone: "+next.label+" · Level "+next.level)
                +"\n\n"+PointRules.format(v.total())+" lifetime XP";
        game.hub.menu.show(p,"Level "+v.level(),body,List.of(),false,"Back to lobby",()->game.hub.menu.clear(p));return 1;
    }
}
