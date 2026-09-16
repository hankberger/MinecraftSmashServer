package dev.hanks.vanilla;

import dev.hanks.network.PartyBook;
import java.util.Optional;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Per-player native sidebar stays at the screen edge at every GUI scale. */
final class PickerSidebar {
    static final String ID="smash_picker";
    private final Objective objective=new Objective(new Scoreboard(),ID,ObjectiveCriteria.DUMMY,
            line("PARTY",0xe6c784,"party_top"),ObjectiveCriteria.RenderType.INTEGER,false,BlankFormat.INSTANCE);
    private String signature="";
    private boolean visible;
    private static Component line(String value,int color,String background) {
        while(UiPack.sidebarWidth(value)>80)value=value.substring(0,value.length()-1);
        return Component.empty().append(UiPack.strip(background+"_0")).append(UiPack.space(-84))
                .append(UiPack.sidebarText(value).withStyle(s->s.withColor(color).withShadowColor(0)))
                .append(UiPack.space(84-UiPack.sidebarWidth(value)));
    }
    void show(ServerPlayer p,PartyBook.View party) {
        if(signature.equals(party.toString()))return;
        signature=party.toString();
        if(!visible){p.connection.send(new ClientboundSetObjectivePacket(objective,0));p.connection.send(new ClientboundSetDisplayObjectivePacket(DisplaySlot.SIDEBAR,objective));visible=true;}
        for(int row=0;row<13;row++) {
            String value="";int color=0x8eaaa2;
            if(row<12) {
                int slot=row/3,part=row%3;
                if(slot<party.members().size()) {
                    var member=party.members().get(slot);
                    if(part==0){value=member.name();color=member.id().equals(party.leader())?0xe6c784:0xf2ead9;}
                    if(part==1){value=member.ready()?"Ready":"Choosing";color=member.ready()?0xb9e590:0x8eaaa2;}
                } else if(part==0)value="-";
            }
            p.connection.send(new ClientboundSetScorePacket("slot"+row,ID,13-row,
                    Optional.of(line(value,color,row==12?"party_bottom":"party_row")),Optional.of(BlankFormat.INSTANCE)));
        }
    }
    void close(ServerPlayer p) {if(visible){p.connection.send(new ClientboundSetObjectivePacket(objective,1));visible=false;}}
}
