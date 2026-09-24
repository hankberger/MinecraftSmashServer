package dev.hanks.vanilla;

import dev.hanks.network.Wire;
import java.util.*;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Mouse controls over the winner stage, using the same vanilla canvas as fighter select. */
public final class ResultMenu {
    private final VanillaSmash game;
    private final Map<UUID,Open> open = new HashMap<>();
    private static final class Open {
        ChestMenu container;
        List<MatchMenu.Button> buttons = List.of();
        int lastClick = -100;
    }
    public ResultMenu(VanillaSmash game) { this.game=game; }
    public boolean active(ServerPlayer p) { return open.containsKey(p.getUUID()); }
    public void close(ServerPlayer p) {
        var o=open.remove(p.getUUID()); if(o==null)return;
        if(o.container!=null && p.containerMenu==o.container)p.closeContainer();
        if(o.container==null)game.hub.menu.clear(p);
    }
    public void show(ServerPlayer p,Wire.MatchResult result,List<MatchMenu.Button> buttons,String votes) {
        var o=open.computeIfAbsent(p.getUUID(),id->new Open());o.buttons=List.copyOf(buttons);
        var winner=result.rows().stream().filter(r->r.player().equals(result.winner())).findFirst().orElse(null);
        if(!game.uiPack.ready(p)) {
            String stats=result.rows().stream().map(r->r.name()+"  ·  "+r.knockouts()+" KOs  ·  "+r.damage()+"% dealt")
                    .collect(java.util.stream.Collectors.joining("\n"));
            game.hub.menu.show(p,winner==null?"Draw":winner.name()+" wins!",game.points.reward(result,p.getUUID())+"  ·  "+game.points.balance(p.getUUID())+"\n\n"+stats+"\n\n"+votes,buttons,false,"Lobby",()->game.hub.results.dismiss(p));
            return;
        }
        var body=Component.empty().append(UiPack.space(-8));
        draw(body,"results_panel",0);
        text(body,winner==null?"DRAW":"WINNER",8,8,88,0xffd66b);
        text(body,winner==null?"Evenly matched":winner.name(),8,22,88,0xf2ead9,true);
        text(body,"Points "+dev.hanks.network.PointRules.compact(game.points.account(p.getUUID()).balance()),8,34,88,0xffd66b);
        var rows=result.rows().stream().sorted(Comparator.comparing((Wire.ResultRow r)->!r.player().equals(result.winner()))
                .thenComparing(Comparator.comparingInt(Wire.ResultRow::stocks).reversed())).toList();
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i);int y=44+18*i;
            draw(body,"results_head_"+row.fighter().toLowerCase(Locale.ROOT)+"_"+i,8);
            text(body,row.name(),28,y,68,PlayerIdentity.color(row.slot()),true);
            text(body,row.knockouts()+" KOs  "+row.damage()+"%",28,y+9,68,0xb9c9c2,true);
        }
        text(body,game.points.reward(result,p.getUUID()),8,116,88,0xffd66b);
        text(body,votes.replace(" · "," ").replace(" ready",""),8,128,88,0xb9e590);
        for(int row=0;row<3;row++) {
            if(row<buttons.size()) {
                draw(body,"results_button_"+row,7);
                text(body,buttons.get(row).label(),11,143+row*18,86,row==0?0xffdf9e:0xf2ead9);
            }
        }
        draw(body,"results_button_lobby",7);text(body,"Lobby",11,201,86,0xc6d6cf);
        if(o.container==null) {
            NativeUi.menuInputInventory(p);
            p.openMenu(new SimpleMenuProvider((id,inventory,player)->{
                o.container=new ChestMenu(MenuType.GENERIC_9x6,id,inventory,new SimpleContainer(54),6) {
                    @Override public boolean stillValid(net.minecraft.world.entity.player.Player player){return true;}
                };
                return o.container;
            },body));
        } else {
            o.container.incrementStateId();
            p.connection.send(new ClientboundOpenScreenPacket(o.container.containerId,MenuType.GENERIC_9x6,body));
            sync(p,o);
        }
    }
    private static void draw(MutableComponent body,String asset,int x) {
        String name="menu_"+asset;
        body.append(UiPack.space(x)).append(UiPack.strip(name)).append(UiPack.space(-x-UiPack.artWidth(name)));
    }
    private static void text(MutableComponent body,String value,int x,int y,int width,int color) {
        text(body,value,x,y,width,color,false);
    }
    private static void text(MutableComponent body,String value,int x,int y,int width,int color,boolean narrow) {
        while((narrow?UiPack.pickerNameWidth(value):UiPack.textWidth(value))>width)value=value.substring(0,value.length()-1);
        int advance=narrow?UiPack.pickerNameWidth(value):UiPack.textWidth(value);
        body.append(UiPack.space(x)).append(UiPack.pickerText(value,y,narrow).withStyle(s->s.withColor(color)))
                .append(UiPack.space(-x-advance));
    }
    static int slotAction(int slot) {
        return slot>=54 && slot<90 && slot%9<5 ? (slot-54)/9 : -1;
    }
    public boolean click(ServerPlayer p,ServerboundContainerClickPacket packet) {
        var o=open.get(p.getUUID());if(o==null || o.container==null)return false;
        if(p.containerMenu!=o.container || packet.containerId()!=o.container.containerId)return true;
        if(packet.stateId()==o.container.getStateId() && packet.containerInput()==ContainerInput.PICKUP
                && (packet.buttonNum()==0 || packet.buttonNum()==1)) {
            int action=slotAction(packet.slotNum());
            if(action==3)game.hub.results.dismiss(p);
            else if(action>=0 && action<o.buttons.size() && game.ticks-o.lastClick>=5) {
                o.lastClick=game.ticks;o.buttons.get(action).action().run();
            }
        }
        if(open.get(p.getUUID())==o)sync(p,o);
        return true;
    }
    private static void sync(ServerPlayer p,Open o) {
        p.connection.send(new ClientboundContainerSetContentPacket(o.container.containerId,o.container.getStateId(),o.container.getItems(),ItemStack.EMPTY));
    }
    public boolean clientClose(ServerPlayer p,int id) {
        var o=open.get(p.getUUID());if(o==null || o.container==null || o.container.containerId!=id)return false;
        game.hub.results.dismiss(p);return true;
    }
}
