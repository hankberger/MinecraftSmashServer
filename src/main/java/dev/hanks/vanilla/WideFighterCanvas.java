package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.network.chat.*;

/** Native text rows provide real mouse regions over a 316px-wide canvas. */
final class WideFighterCanvas {
    static final int ROWS=19,PRIMARY_ROW=17,MODE_HEIGHT=27;
    static final int WIDTH=316,HEIGHT=ROWS*9,GRID_X=88,TILE=54,STEP=58;
    record State(UUID player,PartyBook.View party,int invites,FighterClass fighter,String skin,String skinAction,
                 String primary,String queueTitle,String queueDetail,long balance,boolean owned,
                 boolean discount,Set<Integer> enabled,IntFunction<ClickEvent.Custom> event) {}
    private final State state;
    private final MutableComponent body=Component.empty();
    private WideFighterCanvas(State state){this.state=state;}
    static Component render(State state){return new WideFighterCanvas(state).render();}
    private MutableComponent art(String name,int action){
        var part=UiPack.strip("wide_"+name);
        if(state.enabled.contains(action))part.withStyle(s->s.withClickEvent(state.event.apply(action)));
        if(action==37 && state.discount)part.withStyle(s->s.withHoverEvent(new HoverEvent.ShowText(Component.literal("First skin · 50% off"))));
        return part;
    }
    private void overlay(Component value,int x,int width){body.append(UiPack.space(x)).append(value).append(UiPack.space(-x-width));}
    private void text(String value,int x,int offset,int color){overlay(UiPack.dialogText(value,offset).withColor(color),x,UiPack.textWidth(value));}
    private void button(String label,int width,int part,int action,String style){
        button(label,width,part,action,style,18);
    }
    private void button(String label,int width,int part,int action,String style,int height){
        body.append(art(part==0?"button_"+width+"_"+style:"hit_"+width,action));
        if(part==0){body.append(UiPack.space(-width));text(label,Math.max(2,(width-UiPack.textWidth(label))/2),(height-8)/2,
                action==-1?(state.owned?UiTheme.CREAM:0xefc863):state.enabled.contains(action)?style.equals("primary")?UiTheme.FOREST:UiTheme.CREAM:UiTheme.MUTED);body.append(UiPack.space(width));}
    }
    private void partyRow(int row){
        if(row==0){
            body.append(art("header",44)).append(UiPack.space(-80));text("Party "+state.party.members().size()+"/4",3,0,UiTheme.CREAM);
            if(state.invites>0)text("+"+state.invites,64,0,0xff876f);
            body.append(UiPack.space(80));return;
        }
        if(row<=12){
            int index=(row-1)/3,part=(row-1)%3;
            if(index>=state.party.members().size()){
                body.append(art(part==0?"empty":"hit_80",44));
                if(part==0 && index==state.party.members().size()){
                    body.append(UiPack.space(-80));text("+ Invite friends",3,10,UiTheme.MINT);body.append(UiPack.space(80));
                }return;
            }
            var member=state.party.members().get(index);boolean own=member.id().equals(state.player);
            body.append(art(part==0?"member_"+(own?"own":"other"):"hit_80",40+index));
            if(part==0){
                body.append(UiPack.space(-80));
                if(member.fighter()!=null)overlay(art("head_"+member.fighter().toLowerCase(Locale.ROOT),-1),3,19);
                if(member.id().equals(state.party.leader()))overlay(art("crown",-1),15,8);
                if(member.ready())overlay(art("ready",-1),4,8);
                String name=member.name();int split=name.length();while(UiPack.textWidth(name.substring(0,split))>50)split--;
                text(name.substring(0,split),26,4,own?UiTheme.CREAM:UiTheme.MINT);
                text(name.substring(split),26,13,own?UiTheme.CREAM:UiTheme.MINT);
                body.append(UiPack.space(80));
            }return;
        }
        if(row<PRIMARY_ROW-2){body.append(UiPack.space(80));return;}
        if(row==PRIMARY_ROW-2){
            body.append(art("wallet",-1)).append(UiPack.space(-80));
            String balance=PointRules.compact(state.balance);if(UiPack.textWidth(balance+" credits")<=62)balance+=" credits";
            text(balance,16,5,UiTheme.CREAM);body.append(UiPack.space(80));return;
        }
        if(row==PRIMARY_ROW-1){body.append(UiPack.space(80));return;}
        if(state.enabled.contains(32)){
            button("Store",40,row-PRIMARY_ROW,38,"base");button("Results",40,row-PRIMARY_ROW,32,"base");
        }else button("Store >",80,row-PRIMARY_ROW,38,"base");
    }
    private Component render(){
        var fighters=FighterClass.values();
        for(int row=0;row<ROWS;row++){
            if(row>0)body.append("\n");partyRow(row);body.append(UiPack.space(8));
            if(row<12){
                for(int col=0;col<4;col++){
                    if(col>0)body.append(UiPack.space(4));int index=row/6*4+col;
                    var fighter=fighters[index];
                    body.append(art(row%6==0?"card_"+fighter.name().toLowerCase(Locale.ROOT)+(fighter==state.fighter?"_on":""):"hit_54",index));
                }
            }else if(row<PRIMARY_ROW && !state.queueTitle.isEmpty()){
                body.append(art(row==12?"queue":"hit_228",-1));
                if(row==12){body.append(UiPack.space(-228));text(state.queueTitle,8,9,UiTheme.CREAM);text(state.queueDetail,8,23,UiTheme.MINT);body.append(UiPack.space(228));}
            }else if(row<14){
                button("<",18,row-12,35,"base");button(state.skin,90,row-12,-1,"base");button(">",18,row-12,36,"base");
                button(state.skinAction,102,row-12,37,state.enabled.contains(37)?"base":"disabled");
            }else if(row<PRIMARY_ROW){
                String[] modes={"DUEL","MATCH","PRACTICE"},labels={"1v1","4 Player","Practice"};
                for(int i=0;i<3;i++)button(labels[i],76,row-14,20+i,state.party.mode().equals(modes[i])?"selected":"base",MODE_HEIGHT);
            }else button(state.primary,228,row-PRIMARY_ROW,31,state.enabled.contains(31)?"primary":"disabled");
        }return body;
    }
}
