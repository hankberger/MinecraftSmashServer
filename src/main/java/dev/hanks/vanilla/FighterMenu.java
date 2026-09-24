package dev.hanks.vanilla;

import dev.hanks.network.PartyBook;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** A centered vanilla menu provides mouse regions; the pack paints a portrait UI over empty slots. */
public final class FighterMenu {
    public static final int COLUMNS=3, PAGE_SIZE=6;
    public static final int WIDTH=176, HEIGHT=222, ROWS=6;
    private final VanillaSmash game;
    private final Map<UUID,Open> open=new HashMap<>();
    private static final class Open {
        int page,lastClick=-100,queuedAt=-1; String signature="",interaction=""; UUID token;
        final UUID exitToken=UUID.randomUUID();
        final Map<Integer,Runnable> actions=new HashMap<>();
        ChestMenu container;
    }
    public FighterMenu(VanillaSmash game) { this.game=game; }
    public boolean active(ServerPlayer p) { return open.containsKey(p.getUUID()); }
    public void close(ServerPlayer p) { var o=open.remove(p.getUUID());if(o!=null && p.containerMenu==o.container)p.closeContainer(); }
    public void show(ServerPlayer p) {
        if(!active(p)) {p.closeContainer();NativeUi.menuInputInventory(p);open.put(p.getUUID(),new Open());}
        paint(p,open.get(p.getUUID()),true);
    }
    public void refresh(ServerPlayer p) {var o=open.get(p.getUUID());if(o!=null) paint(p,o,false);}
    private Component art(Open o,String asset,int action) {
        var text=UiPack.strip(asset);
        if(o.actions.containsKey(action)) text.withStyle(s->s.withClickEvent(MenuActions.event(MenuActions.FIGHTER,action==30?o.exitToken:o.token,action)));
        return text;
    }
    private void draw(MutableComponent canvas,Open o,String asset,int x,int action) {
        String name="menu_"+asset;
        canvas.append(UiPack.space(x)).append(art(o,name,action)).append(UiPack.space(-x-UiPack.artWidth(name)));
    }
    private void text(MutableComponent canvas,String value,int x,int y,int width,int color,boolean narrow) {
        while((narrow?UiPack.pickerNameWidth(value):UiPack.textWidth(value))>width)value=value.substring(0,value.length()-1);
        int advance=narrow?UiPack.pickerNameWidth(value):UiPack.textWidth(value);
        canvas.append(UiPack.space(x)).append(UiPack.pickerText(value,y,narrow).withStyle(s->s.withColor(color))).append(UiPack.space(-x-advance));
    }
    private void paint(ServerPlayer p,Open o,boolean force) {
        var s=game.stage.session(p.getUUID());var party=game.hub.parties.view(p.getUUID());if(s==null || party==null)return;
        var own=party.members().stream().filter(m->m.id().equals(p.getUUID())).findFirst().orElseThrow();
        s.mode=VanillaSmash.Mode.valueOf(party.mode());s.round=party.round();
        boolean queued=party.phase()==PartyBook.Phase.QUEUED,claimed=party.phase()==PartyBook.Phase.PLAYING;
        boolean waiting=party.phase()==PartyBook.Phase.IDLE && !party.leader().equals(p.getUUID());
        if(queued && o.queuedAt<0)o.queuedAt=game.ticks;
        if(!queued)o.queuedAt=-1;
        String status=claimed?"Joining match...":party.members().size()>1?party.readyCount()+"/"+party.members().size()+" ready":s.selected.label;
        if(queued && game.network.enabled())status=game.network.lobbyMessage(p.getUUID()).split("    ")[0].replace("·","/").replace("…","...");
        if(waiting)status="Leader chooses mode";
        String notice=game.hub.currentNotice(p);if(notice!=null)status=notice.replace("…","...");
        boolean results=party.phase()==PartyBook.Phase.IDLE && game.hub.results.book.result(p.getUUID())!=null;
        String interaction=s.selected+"/"+s.mode+"/"+o.page+"/"+party+"/"+results;
        String queueTitle=queued?"In Queue  "+queueTime(game.ticks-o.queuedAt):claimed?"Match found":"";
        String queueDetail=queued?"Finding players"+".".repeat(1+(game.ticks/10)%3):claimed?"Joining arena...":"";
        String signature=interaction+"/"+status+"/"+queueTitle+"/"+queueDetail+"/"+game.points.account(p.getUUID()).balance();
        if(!force && signature.equals(o.signature))return;
        o.signature=signature;
        // Visual queue animation must not invalidate a click already in flight.
        if(!interaction.equals(o.interaction)){o.token=UUID.randomUUID();o.interaction=interaction;if(o.container!=null)o.container.incrementStateId();}
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
        o.actions.put(30,()->game.hub.exitPicker(p));
        if(party.phase()==PartyBook.Phase.IDLE && results)o.actions.put(32,()->{game.stage.close(p);game.hub.results.show(p);});
        if(pages>1) {
            o.actions.put(33,()->{o.page=pageStep(o.page,-1,roster.length);paint(p,o,true);});
            o.actions.put(34,()->{o.page=pageStep(o.page,1,roster.length);paint(p,o,true);});
        }
        String action=claimed || waiting?"waiting":queued?"cancel":own.ready()?"unready":party.members().size()>1?"ready":"play";
        var body=Component.empty().append(UiPack.space(-8));
        draw(body,o,"panel",0,-1);draw(body,o,"party",-68,-1);
        for(int i=0;i<PAGE_SIZE && o.page*PAGE_SIZE+i<roster.length;i++) {
            var kind=roster[o.page*PAGE_SIZE+i];
            draw(body,o,"card_"+kind.name().toLowerCase(Locale.ROOT)+(kind==s.selected?"_on":"")+"_"+i,7+(i%COLUMNS)*54,i);
        }
        if(pages>1){draw(body,o,"button_previous",7,33);draw(body,o,"button_next",151,34);}
        text(body,status,8,128,160,0xf2ead9,false);
        for(int i=0;i<3;i++)draw(body,o,"button_"+modeNames[i],7+i*54,20+i);
        if(queued || claimed) {
            draw(body,o,"queue",27,-1);text(body,queueTitle,31,161,114,0xb9e590,false);text(body,queueDetail,31,176,114,0xf2ead9,false);
        }
        draw(body,o,"button_back",7,30);
        if(results)draw(body,o,"button_results",61,32);
        draw(body,o,"button_"+action+"_on",115,31);
        for(int i=0;i<party.members().size();i++) {
            var member=party.members().get(i);int y=25+i*34;
            int split=member.name().length();
            while(UiPack.pickerNameWidth(member.name().substring(0,split))>54)split--;
            int color=member.id().equals(party.leader())?0xe6c784:0xf2ead9;
            text(body,member.name().substring(0,split),-63,y,54,color,true);
            text(body,member.name().substring(split),-63,y+9,54,color,true);
            text(body,member.ready()?"Ready":"Choosing",-63,y+20,54,member.ready()?0xb9e590:0x8eaaa2,true);
        }
        text(body,"Points",-63,162,54,0xe6c784,false);
        text(body,dev.hanks.network.PointRules.compact(game.points.account(p.getUUID()).balance()),-63,176,54,0xffdf9e,true);
        if(o.container==null) {
            p.openMenu(new SimpleMenuProvider((id,inventory,player)-> {
                o.container=new ChestMenu(MenuType.GENERIC_9x6,id,inventory,new SimpleContainer(54),ROWS) {
                    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player){return true;}
                };
                return o.container;
            },body));
        } else {
            p.connection.send(new ClientboundOpenScreenPacket(o.container.containerId,MenuType.GENERIC_9x6,body));
            sync(p,o);
        }
    }
    public int action(ServerPlayer p,UUID token,int id) {
        var o=open.get(p.getUUID());if(o==null)return 0;
        // Escape must remain valid across redraws and immediate repeated inputs.
        if(id==30 && token.equals(o.exitToken)){game.hub.exitPicker(p);return 1;}
        if(!token.equals(o.token) || game.ticks-o.lastClick<4)return 0;
        var action=o.actions.get(id);if(action==null)return 0;
        o.lastClick=game.ticks;action.run();return 1;
    }
    private void sync(ServerPlayer p,Open o) {
        p.connection.send(new ClientboundContainerSetContentPacket(o.container.containerId,o.container.getStateId(),o.container.getItems(),ItemStack.EMPTY));
    }
    public boolean click(ServerPlayer p,ServerboundContainerClickPacket packet) {
        var o=open.get(p.getUUID());if(o==null)return false;
        if(o.container==null || p.containerMenu!=o.container || packet.containerId()!=o.container.containerId)return true;
        if(packet.stateId()==o.container.getStateId() && packet.containerInput()==ContainerInput.PICKUP && (packet.buttonNum()==0 || packet.buttonNum()==1)) {
            int id=slotAction(packet.slotNum());action(p,id==30?o.exitToken:o.token,id);
        }
        if(open.get(p.getUUID())==o)sync(p,o);
        return true;
    }
    public boolean clientClose(ServerPlayer p,int id) {
        var o=open.get(p.getUUID());if(o==null || o.container==null || o.container.containerId!=id)return false;
        game.hub.exitPicker(p);return true;
    }
    static int slotAction(int slot) {
        if(slot>=0 && slot<54) {int col=slot%9,row=slot/9;return (row/3)*COLUMNS+col/3;}
        if(slot>=54 && slot<63)return 20+(slot-54)/3;
        if(slot==63)return 33;
        if(slot==71)return 34;
        if(slot>=81 && slot<84)return 30;
        if(slot>=84 && slot<87)return 32;
        if(slot>=87 && slot<90)return 31;
        return -1;
    }
    static int pageCount(int count) {return Math.max(1,(count+PAGE_SIZE-1)/PAGE_SIZE);}
    static String queueTime(int ticks) {int seconds=Math.max(0,ticks)/20;return "%d:%02d".formatted(seconds/60,seconds%60);}
    static int pageStep(int page,int delta,int count) {return Math.floorMod(page+delta,pageCount(count));}
}
