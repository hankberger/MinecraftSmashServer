package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** One-page roster and party previews over vanilla's native, revision-checked click regions. */
public final class FighterMenu {
    public static final int COLUMNS=4, PAGE_SIZE=8, TILE=36, GRID_Y=53;
    public static final int WIDTH=176, HEIGHT=222, ROWS=6;
    private final VanillaSmash game;
    private final Map<UUID,Open> open=new HashMap<>();
    private final Map<UUID,Boolean> dialogLayouts=new HashMap<>();
    private static final class Open {
        int lastClick=-100,queuedAt=-1; String signature="",interaction=""; UUID token;
        final UUID exitToken=UUID.randomUUID();
        final Map<Integer,Runnable> actions=new HashMap<>();
        ChestMenu container;
        final boolean dialog;
        Open(boolean dialog){this.dialog=dialog;}
    }
    public FighterMenu(VanillaSmash game) { this.game=game; }
    public boolean active(ServerPlayer p) { return open.containsKey(p.getUUID()); }
    public void close(ServerPlayer p) { release(p,true); }
    /** Leave the dialog visible until the next menu replaces it, preserving the native cursor. */
    public void handoff(ServerPlayer p) { release(p,false); }
    private void release(ServerPlayer p,boolean closeDialog) {
        var o=open.remove(p.getUUID());if(o==null)return;
        if(o.dialog){if(closeDialog)p.connection.send(net.minecraft.network.protocol.common.ClientboundClearDialogPacket.INSTANCE);}
        else if(p.containerMenu==o.container)p.closeContainer();
    }
    public void show(ServerPlayer p) {
        game.hub.social.forget(p.getUUID());
        if(!active(p)){
            game.hub.menu.handoff(p);
            if(p.containerMenu!=p.inventoryMenu)p.closeContainer();
            NativeUi.menuInputInventory(p);
            boolean dialog=dialogLayouts.getOrDefault(p.getUUID(),Boolean.parseBoolean(System.getProperty("smash_vanilla.widePicker","true")));
            open.put(p.getUUID(),new Open(dialog));
        }
        paint(p,open.get(p.getUUID()),true);
    }
    public void layout(ServerPlayer p,boolean dialog) {
        dialogLayouts.put(p.getUUID(),dialog);
        var old=open.get(p.getUUID());if(old==null || old.dialog==dialog)return;
        close(p);var next=new Open(dialog);next.queuedAt=old.queuedAt;open.put(p.getUUID(),next);
    }
    public void refresh(ServerPlayer p) {var o=open.get(p.getUUID());if(o!=null)paint(p,o,false);}
    private void draw(MutableComponent canvas,Open o,String asset,int x,int action) {
        String name="menu_picker_"+asset;var art=UiPack.strip(name);
        if(o.actions.containsKey(action))art.withStyle(style->style.withClickEvent(MenuActions.event(MenuActions.FIGHTER,action==30?o.exitToken:o.token,action)));
        canvas.append(UiPack.space(x)).append(art).append(UiPack.space(-x-UiPack.artWidth(name)));
    }
    private void text(MutableComponent canvas,String value,int x,int y,int width,int color) {
        while(UiPack.pickerNameWidth(value)>width && !value.isEmpty())value=value.substring(0,value.length()-1);
        int advance=UiPack.pickerNameWidth(value);
        canvas.append(UiPack.space(x)).append(UiPack.pickerText(value,y,true).withColor(color)).append(UiPack.space(-x-advance));
    }
    private void centered(MutableComponent canvas,String value,int x,int y,int width,int color) {
        text(canvas,value,x+Math.max(0,(width-UiPack.pickerNameWidth(value))/2),y,width,color);
    }
    private void memberName(MutableComponent canvas,String name,int x,boolean own) {
        int split=name.length();while(UiPack.partyNameWidth(name.substring(0,split))>32)split--;
        String[] lines={name.substring(0,split),name.substring(split)};
        for(int i=0;i<2;i++) {
            int width=UiPack.partyNameWidth(lines[i]),left=x+(36-width)/2;
            canvas.append(UiPack.space(left)).append(UiPack.partyName(lines[i],26+8*i).withColor(own?UiTheme.CREAM:UiTheme.MINT)).append(UiPack.space(-left-width));
        }
    }
    private void paint(ServerPlayer p,Open o,boolean force) {
        var s=game.stage.session(p.getUUID());var party=game.hub.parties.view(p.getUUID());if(s==null || party==null)return;
        var own=party.members().stream().filter(m->m.id().equals(p.getUUID())).findFirst().orElseThrow();
        s.mode=VanillaSmash.Mode.valueOf(party.mode());s.round=party.round();
        boolean queued=party.phase()==PartyBook.Phase.QUEUED,claimed=party.phase()==PartyBook.Phase.PLAYING;
        boolean waiting=party.phase()==PartyBook.Phase.IDLE && !party.leader().equals(p.getUUID());
        if(queued && o.queuedAt<0)o.queuedAt=game.ticks;if(!queued)o.queuedAt=-1;
        var skin=Cosmetics.skin(s.selected.name(),s.skin);var wardrobe=game.points.wardrobe(p.getUUID());
        boolean owned=wardrobe.owns(skin),equipped=s.skin.equals(wardrobe.equipped(s.selected.name()));
        int price=Cosmetics.price(wardrobe,skin);long balance=game.points.account(p.getUUID()).balance();
        boolean affordable=balance>=price,editable=!claimed && !queued && !s.cosmeticBusy;
        boolean results=party.phase()==PartyBook.Phase.IDLE && game.hub.results.book.result(p.getUUID())!=null;
        int invites=game.hub.parties.invites(p.getUUID(),game.ticks).size();
        String interaction=s.selected+"/"+s.mode+"/"+party+"/"+results+"/"+s.skin+"/"+wardrobe+"/"+s.cosmeticBusy+"/"+affordable+"/"+price+"/"+invites;
        String queueTitle=queued?"In Queue  "+queueTime(game.ticks-o.queuedAt):claimed?"Match found":"";
        String queueDetail=queued?"Finding players"+".".repeat(1+(game.ticks/10)%3):"Joining arena...";
        String notice=game.hub.currentNotice(p);
        String signature=interaction+"/"+queueTitle+"/"+queueDetail+"/"+balance+"/"+notice;
        if(!force && signature.equals(o.signature))return;o.signature=signature;
        // Time/animation changes don't invalidate a click in flight.
        if(!interaction.equals(o.interaction)){o.token=UUID.randomUUID();o.interaction=interaction;if(o.container!=null)o.container.incrementStateId();}
        o.actions.clear();var roster=FighterClass.values();
        if(roster.length>PAGE_SIZE)throw new IllegalStateException("Expand the single-page roster before adding a ninth fighter");
        for(int i=0;i<roster.length;i++){var kind=roster[i];if(!claimed && !s.cosmeticBusy)o.actions.put(i,()->game.hub.preview(p,kind));}
        var modes=new VanillaSmash.Mode[]{VanillaSmash.Mode.DUEL,VanillaSmash.Mode.MATCH,VanillaSmash.Mode.PRACTICE};
        String[] modeAssets={"duel","ffa","practice"},modeLabels={"1v1","4 Player","Practice"};
        for(int i=0;i<3;i++){
            var mode=modes[i];boolean allowed=!claimed && !s.cosmeticBusy && party.leader().equals(p.getUUID()) && party.members().size()<=Wire.capacity(mode.name());
            modeAssets[i]+=s.mode==mode?"_on":allowed?"":"_disabled";
            if(allowed)o.actions.put(20+i,()->game.hub.selectMode(p,mode));
        }
        if(!claimed && !waiting && !s.cosmeticBusy)o.actions.put(31,()->game.hub.pickerAction(p));
        if(editable){
            if(Cosmetics.forFighter(s.selected.name()).size()>1){o.actions.put(35,()->game.hub.cycleSkin(p,-1));o.actions.put(36,()->game.hub.cycleSkin(p,1));}
            if(!equipped && (owned || affordable))o.actions.put(37,()->game.hub.confirmSkin(p));
            o.actions.put(38,()->game.hub.pickerStore(p));
            for(int i=0;i<4;i++){
                if(i>=party.members().size() && party.leader().equals(p.getUUID()))o.actions.put(40+i,()->game.hub.pickerInvite(p));
                else if(i<party.members().size())o.actions.put(40+i,()->game.hub.partyPanel(p));
            }
        }
        o.actions.put(30,()->game.hub.exitPicker(p));
        if(!claimed)o.actions.put(44,()->game.hub.partyPanel(p));
        if(results)o.actions.put(32,()->{game.stage.close(p);game.hub.results.show(p);});
        if(o.dialog){
            String skinAction=s.cosmeticBusy?"Saving...":equipped?"Equipped":owned?"Equip":affordable?"Unlock "+PointRules.format(price):"Need "+PointRules.format(price-balance);
            String primary=claimed?"Joining...":waiting?"Waiting":queued?"Cancel queue":own.ready()?"Unready":!equipped?(party.members().size()>1?"Ready":"Play")+" with "+Cosmetics.skin(s.selected.name(),wardrobe.equipped(s.selected.name())).label():party.members().size()>1?"READY UP":"PLAY";
            var canvas=WideFighterCanvas.render(new WideFighterCanvas.State(p.getUUID(),party,invites,s.selected,skin.label(),skinAction,primary,
                    queueTitle,queueDetail,balance,owned,!owned && wardrobe.owned().isEmpty(),Set.copyOf(o.actions.keySet()),
                    id->MenuActions.event(MenuActions.FIGHTER,id==30?o.exitToken:o.token,id)));
            showDialog(p,o,canvas);return;
        }
        var body=Component.empty().append(UiPack.space(-8));
        draw(body,o,"party",0,-1);draw(body,o,"main",0,-1);draw(body,o,"wallet",0,-1);
        text(body,"Party "+party.members().size()+"/4",7,5,66,UiTheme.CREAM);
        text(body,party.readyCount()+"/"+party.members().size()+" ready",96,5,55,UiTheme.MINT);
        for(int i=0;i<4;i++){
            int x=7+36*i;
            if(i>=party.members().size()){
                if(o.actions.containsKey(40+i)){draw(body,o,"invite",x,40+i);centered(body,"+ Invite",x,22,36,UiTheme.MINT);}
                continue;
            }
            var member=party.members().get(i);boolean local=member.id().equals(p.getUUID());
            draw(body,o,"member_"+(local?"own":"other"),x,40+i);
            String fighter=member.fighter();
            draw(body,o,fighter==null?"unknown":"party_head_"+fighter.toLowerCase(Locale.ROOT),x+11,-1);
            if(member.id().equals(party.leader()))draw(body,o,"crown",x+25,-1);
            if(member.ready())draw(body,o,"ready",x+1,-1);
            memberName(body,member.name(),x,local);
        }
        for(int i=0;i<roster.length;i++)draw(body,o,"card_"+roster[i].name().toLowerCase(Locale.ROOT)+(roster[i]==s.selected?"_on":"")+"_"+i,7+36*(i%4),i);
        String info=notice!=null?notice:waiting?"Leader chooses mode":!owned?"Skin · Not owned"+ (wardrobe.owned().isEmpty()?" · 50% off":""):"Skin";
        text(body,info.replace("…","..."),7,128,144,UiTheme.MINT);
        if(queued || claimed){
            draw(body,o,"queue",7,-1);centered(body,queueTitle,7,143,144,UiTheme.CREAM);centered(body,queueDetail,7,161,144,UiTheme.MINT);
        }else{
            draw(body,o,"previous",7,35);centered(body,"<",7,143,18,o.actions.containsKey(35)?UiTheme.CREAM:UiTheme.MUTED);
            centered(body,skin.label(),25,143,54,owned?UiTheme.CREAM:0xefc863);
            draw(body,o,"next",79,36);centered(body,">",79,143,18,o.actions.containsKey(36)?UiTheme.CREAM:UiTheme.MUTED);
            draw(body,o,"skin"+(o.actions.containsKey(37)?"":"_disabled"),97,37);
            String label=s.cosmeticBusy?"Saving...":equipped?"Equipped":owned?"Equip":affordable?"Unlock "+PointRules.format(price):"Need "+PointRules.format(price-balance);
            centered(body,label,97,143,54,o.actions.containsKey(37)?UiTheme.CREAM:UiTheme.MUTED);
            for(int i=0;i<3;i++){
                int x=7+54*i,w=i==2?36:54;draw(body,o,modeAssets[i],x,20+i);
                centered(body,modeLabels[i],x,161,w,o.actions.containsKey(20+i) || s.mode==modes[i]?UiTheme.CREAM:UiTheme.MUTED);
            }
        }
        draw(body,o,"back",7,30);centered(body,"Back",7,179,36,UiTheme.CREAM);
        draw(body,o,"primary"+(o.actions.containsKey(31)?"":"_disabled"),43,31);
        String action=claimed?"Joining...":waiting?"Waiting":queued?"Cancel queue":own.ready()?"Unready":!equipped?(party.members().size()>1?"Ready":"Play")+" with "+Cosmetics.skin(s.selected.name(),wardrobe.equipped(s.selected.name())).label():party.members().size()>1?"READY UP":"PLAY";
        centered(body,action,43,179,108,o.actions.containsKey(31)?UiTheme.FOREST:UiTheme.MUTED);
        text(body,PointRules.compact(balance)+" credits",18,201,60,UiTheme.CREAM);
        if(results){draw(body,o,"results",79,32);centered(body,"Results",79,201,36,UiTheme.CREAM);}
        draw(body,o,"store",115,38);centered(body,"Store >",115,201,36,o.actions.containsKey(38)?UiTheme.MINT:UiTheme.MUTED);
        if(o.container==null){
            p.openMenu(new SimpleMenuProvider((id,inventory,player)->{
                o.container=new ChestMenu(MenuType.GENERIC_9x6,id,inventory,new SimpleContainer(54),ROWS){
                    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player){return true;}
                };return o.container;
            },body));
        }else{p.connection.send(new ClientboundOpenScreenPacket(o.container.containerId,MenuType.GENERIC_9x6,body));sync(p,o);}
    }
    private void showDialog(ServerPlayer p,Open o,Component body){
        var ops=p.level().registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var json=new com.google.gson.JsonObject();json.addProperty("type","minecraft:notice");
        json.addProperty("title","Choose fighter");json.addProperty("pause",false);json.addProperty("after_action","none");
        var message=new com.google.gson.JsonObject();message.addProperty("type","minecraft:plain_message");
        message.addProperty("width",WideFighterCanvas.WIDTH+8);
        message.add("contents",ComponentSerialization.CODEC.encodeStart(ops,body).getOrThrow());json.add("body",message);
        var back=new com.google.gson.JsonObject();back.addProperty("label","Back to lobby");back.addProperty("width",150);
        back.add("action",MenuActions.dialogAction(MenuActions.FIGHTER,o.exitToken,30));json.add("action",back);
        p.openDialog(net.minecraft.server.dialog.Dialog.CODEC.parse(ops,json).getOrThrow());
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
        if(slot>=0 && slot<54){int col=slot%9,row=slot/9;if(col>=8)return -1;
            return row<2?40+col/2:(row-2)/2*COLUMNS+col/2;}
        if(slot==54)return 35;
        if(slot==58)return 36;
        if(slot>=59 && slot<=61)return 37;
        if(slot>=63 && slot<=70)return 20+Math.min(2,(slot-63)/3);
        if(slot>=72 && slot<=73)return 30;
        if(slot>=74 && slot<=79)return 31;
        if(slot>=85 && slot<=86)return 32;
        if(slot>=87 && slot<=88)return 38;
        return -1;
    }
    static String queueTime(int ticks){int seconds=Math.max(0,ticks)/20;return "%d:%02d".formatted(seconds/60,seconds%60);}
}
