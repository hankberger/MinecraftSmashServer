package dev.hanks.vanilla;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.hanks.network.Rankings;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;

/** Read-only, async rankings. No selection, queue or currency changes while browsing. */
public final class RankingsMenu {
    private static final class Open {
        UUID token=UUID.randomUUID();final UUID exit=UUID.randomUUID();
        Rankings.Board board=Rankings.Board.WEEKLY_WINS;Rankings.Snapshot data;
        int lastClick=-100,requestedAt;boolean loading;String message="Loading...";
    }
    private final VanillaSmash game;
    private final Map<UUID,Open> open=new HashMap<>();
    public RankingsMenu(VanillaSmash game){this.game=game;}
    public boolean active(ServerPlayer p){return open.containsKey(p.getUUID());}
    private boolean available(ServerPlayer p){return game.hub.available(p)&&p.level().dimension().equals(MvpWorlds.LOBBY)&&!game.stage.active(p);}
    public void forget(UUID id){open.remove(id);}
    public void reset(){open.clear();}
    public void close(ServerPlayer p){if(open.remove(p.getUUID())!=null)p.connection.send(ClientboundClearDialogPacket.INSTANCE);}
    public int show(ServerPlayer p){
        if(!available(p)){game.hub.notice(p.getUUID(),"View rankings in the lobby");return 1;}
        if(game.uiPack.enabled()&&!game.uiPack.ready(p)){game.hub.notice(p.getUUID(),game.uiPack.status(p));return 1;}
        game.hub.social.forget(p.getUUID());game.hub.menu.handoff(p);
        var o=open.get(p.getUUID());
        if(o!=null){paint(p,o);return 1;}
        o=new Open();open.put(p.getUUID(),o);paint(p,o);request(p,o);return 1;
    }
    private void request(ServerPlayer p,Open o){
        if(o.loading)return;o.loading=true;o.requestedAt=game.ticks;
        game.points.rankings(p.getUUID(),p.getPlainTextName(),game.hub.lastFighter(p).name()).whenComplete((data,error)->game.server.execute(()->{
            if(open.get(p.getUUID())!=o||p.isRemoved()||!available(p))return;
            o.loading=false;
            if(error!=null){VanillaSmash.LOG.error("Cannot load rankings",error);o.message="Rankings unavailable. Retrying...";paint(p,o);return;}
            boolean changed=!data.equals(o.data)||!o.message.isEmpty();o.data=data;o.message="";
            if(changed)paint(p,o);
        }));
    }
    public void tick(){
        if(game.ticks%20!=0)return;
        for(var entry:List.copyOf(open.entrySet())){
            var p=game.server.getPlayerList().getPlayer(entry.getKey());
            if(p==null||p.isRemoved()){forget(entry.getKey());continue;}
            if(!available(p)){close(p);continue;}
            if(game.ticks-entry.getValue().requestedAt>=200)request(p,entry.getValue());
        }
    }
    public void action(ServerPlayer p,UUID token,int button){
        var o=open.get(p.getUUID());if(o==null||!available(p))return;
        if(button==3&&o.exit.equals(token)){close(p);return;}
        if(!o.token.equals(token)||button<0||button>=Rankings.Board.values().length||game.ticks-o.lastClick<4)return;
        o.lastClick=game.ticks;o.board=Rankings.Board.values()[button];paint(p,o);
    }
    private void paint(ServerPlayer p,Open o){
        o.token=UUID.randomUUID();
        if(!game.uiPack.ready(p)){
            var standing=o.data==null?null:o.data.boards().get(o.board);var body=new StringBuilder(o.message);
            if(standing!=null){for(var e:standing.leaders())body.append("\n#").append(e.rank()).append("  ").append(e.name()).append("  ").append(e.score()).append(' ').append(o.board.unit(e.score()));
                var e=standing.own();body.append("\n\nYou: ").append(e==null?"Unranked":"#"+e.rank()+" · "+e.score()+" "+o.board.unit(e.score()));}
            var buttons=new ArrayList<MatchMenu.Button>();for(var board:Rankings.Board.values())buttons.add(new MatchMenu.Button(board.label,()->{o.board=board;paint(p,o);}));
            game.hub.menu.show(p,"Rankings",body.toString(),buttons,false,"Back to lobby",()->{close(p);game.hub.menu.clear(p);});return;
        }
        var ops=p.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var json=new JsonObject();json.addProperty("type","minecraft:notice");json.addProperty("title","Rankings");json.addProperty("pause",false);json.addProperty("after_action","none");
        var body=RankingsCanvas.render(o.data,o.board,p.getUUID(),p.getPlainTextName(),o.message,id->MenuActions.event(MenuActions.RANKINGS,o.token,id));
        var message=new JsonObject();message.addProperty("type","minecraft:plain_message");message.addProperty("width",RankingsCanvas.WIDTH+8);message.add("contents",ComponentSerialization.CODEC.encodeStart(ops,body).getOrThrow());json.add("body",message);
        var button=new JsonObject();button.add("label",ComponentSerialization.CODEC.encodeStart(ops,PartyCanvas.nativeButton("Back to lobby",false)).getOrThrow());button.addProperty("width",150);button.add("action",MenuActions.dialogAction(MenuActions.RANKINGS,o.exit,3));json.add("action",button);
        p.openDialog(Dialog.CODEC.parse(ops,json).getOrThrow());
    }
}
