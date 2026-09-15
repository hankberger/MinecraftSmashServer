package dev.hanks.vanilla;

import dev.hanks.network.PartyBook;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;

/** Portrait artwork over vanilla click regions. The right side reveals the real world. */
public final class FighterMenu {
    public static final int PAGE_SIZE=6;
    private final VanillaSmash game;
    private final Map<UUID,Open> open=new HashMap<>();
    private static final class Open {
        ChestMenu menu; int page,lastClick=-100; String signature="";
        final Map<Integer,Runnable> actions=new HashMap<>();
    }
    public FighterMenu(VanillaSmash game) { this.game=game; }
    public boolean active(ServerPlayer p) { return open.containsKey(p.getUUID()); }
    public void close(ServerPlayer p) { var o=open.remove(p.getUUID()); if(o!=null && p.containerMenu==o.menu) p.closeContainer(); }
    public void show(ServerPlayer p) {
        var current=open.get(p.getUUID());
        if(current!=null && p.containerMenu==current.menu) { paint(p,current,true); return; }
        p.closeContainer(); NativeUi.combatInventory(p,FighterClass.STEVE);
        for(int i=0;i<9;i++) p.getInventory().getItem(i).set(net.minecraft.core.component.DataComponents.TOOLTIP_DISPLAY,new net.minecraft.world.item.component.TooltipDisplay(true,new LinkedHashSet<>()));
        p.inventoryMenu.broadcastChanges();
        var o=new Open(); open.put(p.getUUID(),o);
        var contents=new SimpleContainer(54);
        p.openMenu(new SimpleMenuProvider((id,inventory,player)-> {
            o.menu=new ChestMenu(MenuType.GENERIC_9x6,id,inventory,contents,6) {
                @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
            }; return o.menu;
        },UiPack.image("background")));
        paint(p,o,true);
    }
    public void refresh(ServerPlayer p) { var o=open.get(p.getUUID()); if(o!=null && p.containerMenu==o.menu) paint(p,o,false); }
    private void region(Open o,int first,int width,int height,Runnable action) {
        for(int row=0;row<height;row++) for(int col=0;col<width;col++) o.actions.put(first+row*9+col,action);
    }
    private void paint(ServerPlayer p,Open o,boolean force) {
        var s=game.stage.session(p.getUUID()); var party=game.hub.parties.view(p.getUUID());
        if(s==null || party==null) return;
        var own=party.members().stream().filter(m->m.id().equals(p.getUUID())).findFirst().orElseThrow();
        s.mode=VanillaSmash.Mode.valueOf(party.mode()); s.round=party.round();
        boolean queued=party.phase()==PartyBook.Phase.QUEUED, claimed=party.phase()==PartyBook.Phase.PLAYING;
        boolean waitingForLeader=party.phase()==PartyBook.Phase.IDLE && !party.leader().equals(p.getUUID());
        String status=claimed?"Joining match...":queued?game.network.enabled()?game.network.lobbyMessage(p.getUUID()).split("    ")[0].replace("·","/"):"Searching for players...":party.members().size()>1?party.readyCount()+"/"+party.members().size()+" ready":s.selected.label;
        if(waitingForLeader) status="Leader chooses mode";
        boolean hasResults=game.hub.results.book.result(p.getUUID())!=null;
        String signature=s.selected+"/"+s.mode+"/"+o.page+"/"+party.toString()+"/"+game.hub.currentNotice(p)+"/"+status+"/"+hasResults;
        if(!force && signature.equals(o.signature)) return;
        o.signature=signature; o.actions.clear();
        var title=UiPack.image("background_"+party.members().size()); var roster=FighterClass.values();
        for(int i=0;i<party.members().size();i++) {
            var member=party.members().get(i); String name=member.name(); if(name.length()>10) name=name.substring(0,10);
            title.append(UiPack.text((member.ready()?"+ ":"- ")+name,96,17+i*11));
        }
        int pages=pageCount(roster.length); o.page=Math.min(o.page,pages-1);
        for(int i=0;i<PAGE_SIZE && o.page*PAGE_SIZE+i<roster.length;i++) {
            var kind=roster[o.page*PAGE_SIZE+i];
            title.append(UiPack.image("card_"+i+"_"+kind.name().toLowerCase(Locale.ROOT)+(kind==s.selected?"_on":"")));
            if(!claimed) region(o,(i/2)*18+(i%2)*2,2,2,()->game.hub.preview(p,kind));
        }
        var modes=new VanillaSmash.Mode[]{VanillaSmash.Mode.DUEL,VanillaSmash.Mode.MATCH,VanillaSmash.Mode.PRACTICE};
        var modeNames=new String[]{"duel","ffa","practice"};
        for(int i=0;i<3;i++) {
            var mode=modes[i]; boolean allowed=!claimed && party.leader().equals(p.getUUID()) && party.members().size()<=dev.hanks.network.Wire.capacity(mode.name());
            title.append(UiPack.image(modeNames[i]+"_140"+(!allowed?"_disabled":s.mode==mode?"_on":""),8+i*54));
            if(!claimed) region(o,54+i*3,3,1,()->game.hub.selectMode(p,mode));
        }
        title.append(UiPack.image("party_158",8));
        if(!claimed) region(o,63,2,1,()->game.hub.partyPanel(p));
        if(pages>1) {
            title.append(UiPack.image("previous_158",98)).append(UiPack.image("next_158",134));
            region(o,68,2,1,()->{o.page=pageStep(o.page,-1,roster.length);paint(p,o,true);});
            region(o,70,2,1,()->{o.page=pageStep(o.page,1,roster.length);paint(p,o,true);});
        }
        title.append(UiPack.image("back_194",8));
        region(o,81,3,1,()->game.hub.exitPicker(p));
        if(party.phase()==PartyBook.Phase.IDLE && hasResults) {
            title.append(UiPack.image("results_194",62));
            region(o,84,3,1,()->{
                if(game.hub.results.book.result(p.getUUID())!=null) {game.stage.close(p);game.hub.results.show(p);}
                else paint(p,o,true);
            });
        }
        String action=claimed || waitingForLeader?"waiting":queued?"cancel":own.ready()?"unready":party.members().size()>1?"ready":"play";
        title.append(UiPack.image(action+"_194_on",116));
        if(!claimed && !waitingForLeader) region(o,87,3,1,()->game.hub.pickerAction(p));
        String notice=game.hub.currentNotice(p); if(notice!=null) status=notice;
        status=status.replace("…","..."); if(status.length()>28) status=status.substring(0,25)+"...";
        title.append(UiPack.text(status,8,178));
        p.connection.send(new ClientboundOpenScreenPacket(o.menu.containerId,MenuType.GENERIC_9x6,title));
        o.menu.sendAllDataToRemote();
    }
    public boolean click(ServerPlayer p,ServerboundContainerClickPacket packet) {
        var o=open.get(p.getUUID()); if(o==null) return false;
        if(p.containerMenu!=o.menu || packet.containerId()!=o.menu.containerId) return true;
        if(packet.stateId()!=o.menu.getStateId()) { o.menu.sendAllDataToRemote(); return true; }
        var action=o.actions.get((int)packet.slotNum());
        if(action!=null && packet.containerInput()==ContainerInput.PICKUP && packet.buttonNum()==0 && game.ticks-o.lastClick>=4) {
            o.lastClick=game.ticks; action.run();
        } else o.menu.sendAllDataToRemote();
        return true;
    }
    public boolean clientClose(ServerPlayer p,int id) {
        var o=open.get(p.getUUID()); if(o==null || id!=o.menu.containerId) return false;
        game.hub.exitPicker(p); return true;
    }
    static int pageCount(int count) { return Math.max(1,(count+PAGE_SIZE-1)/PAGE_SIZE); }
    static int pageStep(int page,int delta,int count) { return Math.floorMod(page+delta,pageCount(count)); }
}
