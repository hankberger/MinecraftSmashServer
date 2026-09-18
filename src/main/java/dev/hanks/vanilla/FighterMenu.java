package dev.hanks.vanilla;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.hanks.network.PartyBook;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.level.ServerPlayer;

/** Vanilla dialog text supplies real mouse regions for each nine-pixel artwork strip. */
public final class FighterMenu {
    public static final int COLUMNS=4, PAGE_SIZE=8, CANVAS_WIDTH=324;
    private final VanillaSmash game;
    private final Map<UUID,Open> open=new HashMap<>();
    private static final class Open {
        int page,lastClick=-100,queuedAt=-1; String signature="",interaction=""; UUID token;
        final UUID exitToken=UUID.randomUUID();
        final Map<Integer,Runnable> actions=new HashMap<>();
        final PickerSidebar sidebar=new PickerSidebar();
    }
    public FighterMenu(VanillaSmash game) { this.game=game; }
    public boolean active(ServerPlayer p) { return open.containsKey(p.getUUID()); }
    public void close(ServerPlayer p) { var o=open.remove(p.getUUID());if(o!=null){o.sidebar.close(p);p.connection.send(ClientboundClearDialogPacket.INSTANCE);} }
    public void show(ServerPlayer p) {
        if(!active(p)) {p.closeContainer(); NativeUi.combatInventory(p,FighterClass.STEVE);open.put(p.getUUID(),new Open());}
        paint(p,open.get(p.getUUID()),true);
    }
    public void refresh(ServerPlayer p) {var o=open.get(p.getUUID());if(o!=null) paint(p,o,false);}
    private Component art(Open o,String asset,int action) {
        var text=UiPack.strip(asset);
        if(o.actions.containsKey(action)) text.withStyle(s->s.withClickEvent(MenuActions.event(MenuActions.FIGHTER,o.token,action)));
        return text;
    }
    private Component words(String value,int width) {
        while(UiPack.textWidth(value)>width) value=value.substring(0,value.length()-1);
        return Component.empty().append(Component.literal(value).withStyle(s->s.withColor(0xf2ead9))).append(UiPack.space(width-UiPack.textWidth(value)));
    }
    private void paint(ServerPlayer p,Open o,boolean force) {
        var s=game.stage.session(p.getUUID());var party=game.hub.parties.view(p.getUUID());if(s==null || party==null)return;
        var own=party.members().stream().filter(m->m.id().equals(p.getUUID())).findFirst().orElseThrow();
        s.mode=VanillaSmash.Mode.valueOf(party.mode());s.round=party.round();
        boolean queued=party.phase()==PartyBook.Phase.QUEUED,claimed=party.phase()==PartyBook.Phase.PLAYING;
        boolean waiting=party.phase()==PartyBook.Phase.IDLE && !party.leader().equals(p.getUUID());
        if(queued && o.queuedAt<0)o.queuedAt=game.ticks;
        if(!queued)o.queuedAt=-1;
        o.sidebar.show(p,party);
        String status=claimed?"Joining match...":party.members().size()>1?party.readyCount()+"/"+party.members().size()+" ready":s.selected.label;
        if(queued && game.network.enabled())status=game.network.lobbyMessage(p.getUUID()).split("    ")[0].replace("·","/").replace("…","...");
        if(waiting)status="Leader chooses mode";
        String notice=game.hub.currentNotice(p);if(notice!=null)status=notice.replace("…","...");
        boolean results=party.phase()==PartyBook.Phase.IDLE && game.hub.results.book.result(p.getUUID())!=null;
        String interaction=s.selected+"/"+s.mode+"/"+o.page+"/"+party+"/"+results;
        String queueTitle=queued?"In Queue  "+queueTime(game.ticks-o.queuedAt):claimed?"Match found":"";
        String queueDetail=queued?"Finding players"+".".repeat(1+(game.ticks/10)%3):claimed?"Joining arena...":"";
        String signature=interaction+"/"+status+"/"+queueTitle+"/"+queueDetail;
        if(!force && signature.equals(o.signature))return;
        o.signature=signature;
        // Visual queue animation must not invalidate a click already in flight.
        if(!interaction.equals(o.interaction)){o.token=UUID.randomUUID();o.interaction=interaction;}
        o.actions.clear();
        var roster=FighterClass.values();int pages=pageCount(roster.length);o.page=Math.min(o.page,pages-1);
        for(int i=0;i<PAGE_SIZE && o.page*PAGE_SIZE+i<roster.length;i++) {
            var kind=roster[o.page*PAGE_SIZE+i];if(!claimed)o.actions.put(i,()->game.hub.preview(p,kind));
        }
        var modes=new VanillaSmash.Mode[]{VanillaSmash.Mode.DUEL,VanillaSmash.Mode.MATCH,VanillaSmash.Mode.PRACTICE};
        String[] modeNames={"duel","ffa","practice"};
        for(int i=0;i<3;i++) {
            var mode=modes[i];boolean allowed=!claimed && party.leader().equals(p.getUUID()) && party.members().size()<=dev.hanks.network.Wire.capacity(mode.name());
            modeNames[i]+=!allowed?"_disabled":s.mode==mode?"_on":"";
            if(allowed)o.actions.put(20+i,()->game.hub.selectMode(p,mode));
        }
        if(!claimed && !waiting)o.actions.put(31,()->game.hub.pickerAction(p));
        if(party.phase()==PartyBook.Phase.IDLE && results)o.actions.put(32,()->{game.stage.close(p);game.hub.results.show(p);});
        if(pages>1) {
            o.actions.put(33,()->{o.page=pageStep(o.page,-1,roster.length);paint(p,o,true);});
            o.actions.put(34,()->{o.page=pageStep(o.page,1,roster.length);paint(p,o,true);});
        }
        String action=claimed || waiting?"waiting":queued?"cancel":own.ready()?"unready":party.members().size()>1?"ready":"play";
        var body=Component.empty();
        for(int row=0;row<18;row++) {
            if(row>0)body.append("\n");
            if(row<2) {
                body.append(art(o,(pages>1?"heading_paged_":"heading_")+row,-1));
                if(pages>1)body.append(art(o,"button_previous_"+row,33)).append(art(o,"button_next_"+row,34));
            }
            else if(row<10) {
                body.append(art(o,"edge_0",-1));
                for(int col=0;col<4;col++) {
                    int index=(row-2)/4*4+col,part=(row-2)%4,absolute=o.page*PAGE_SIZE+index;
                    String asset=absolute<roster.length?"card_"+roster[absolute].name().toLowerCase(Locale.ROOT)+(roster[absolute]==s.selected?"_on":"")+"_"+part:"empty_"+part;
                    body.append(art(o,asset,index));
                }
                body.append(art(o,"edge_0",-1));
            } else if(row==11 || row==12) {
                for(int i=0;i<3;i++)body.append(art(o,"button_"+modeNames[i]+"_"+(row-11),20+i));
            } else if(row==10)body.append(words(status,162));
            else if(row>=13) {
                if(row==14 || row==15) {
                    if(queued || claimed)body.append(UiPack.strip("queue_"+(row-14))).append(UiPack.space(-156)).append(words(row==14?queueTitle:queueDetail,150)).append(UiPack.space(6));
                    else body.append(UiPack.space(162));
                } else if(row==16 || row==17) {
                    body.append(results?art(o,"button_results_"+(row-16),32):UiPack.space(54)).append(UiPack.space(54));
                    body.append(art(o,"button_"+action+"_on_"+(row-16),31));
                } else body.append(UiPack.space(162));
            } else body.append(art(o,"gap_0",-1));
            body.append(UiPack.space(CANVAS_WIDTH-162));
        }
        var json=new JsonObject();json.addProperty("type","minecraft:notice");json.addProperty("title","");
        json.addProperty("pause",false);json.addProperty("after_action","none");
        var message=new JsonObject();message.addProperty("type","minecraft:plain_message");message.addProperty("width",CANVAS_WIDTH+20);
        var ops=p.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
        message.add("contents",ComponentSerialization.CODEC.encodeStart(ops,body).getOrThrow());json.add("body",message);
        var exit=new JsonObject();exit.addProperty("label","Back to lobby");exit.addProperty("width",100);
        exit.add("action",MenuActions.dialogAction(MenuActions.FIGHTER,o.exitToken,30));json.add("action",exit);
        p.openDialog(Dialog.CODEC.parse(ops,json).getOrThrow());
    }
    public int action(ServerPlayer p,UUID token,int id) {
        var o=open.get(p.getUUID());if(o==null)return 0;
        // Escape must remain valid across redraws and immediate repeated inputs.
        if(id==30 && token.equals(o.exitToken)){game.hub.exitPicker(p);return 1;}
        if(!token.equals(o.token) || game.ticks-o.lastClick<4)return 0;
        var action=o.actions.get(id);if(action==null)return 0;
        o.lastClick=game.ticks;action.run();return 1;
    }
    public boolean click(ServerPlayer p,ServerboundContainerClickPacket packet) {return active(p);}
    public boolean clientClose(ServerPlayer p,int id) {return false;}
    static int pageCount(int count) {return Math.max(1,(count+PAGE_SIZE-1)/PAGE_SIZE);}
    static String queueTime(int ticks) {int seconds=Math.max(0,ticks)/20;return "%d:%02d".formatted(seconds/60,seconds%60);}
    static int pageStep(int page,int delta,int count) {return Math.floorMod(page+delta,pageCount(count));}
}
