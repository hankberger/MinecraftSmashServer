package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.network.chat.*;

/** Large party roster and friend finder, using native text hit regions. */
final class PartyCanvas {
    static final int WIDTH=392, ROWS=26;
    record Row(String name,String fighter,String status,String label,int action,String alternate,int alternateAction) {}
    record State(PartyBook.View party,int invites,String tab,String query,List<Row> rows,int page,int pages,String note,
                 Row member,Set<Integer> actions,IntFunction<ClickEvent.Custom> event) {}
    private final State s;
    private final MutableComponent body=Component.empty();
    private PartyCanvas(State s){this.s=s;}
    static Component render(State s){return new PartyCanvas(s).render();}
    private Component gap(int n){return UiPack.space(n);}
    private MutableComponent art(String key,int action){
        var value=UiPack.strip("party_"+key);
        if(s.actions.contains(action))value.withStyle(style->style.withClickEvent(s.event.apply(action)));
        return value;
    }
    private void text(String value,int x,int offset,int color){
        body.append(gap(x)).append(UiPack.dialogText(value,offset).withColor(color)).append(gap(-x-UiPack.textWidth(value)));
    }
    private void cell(int width,int height,int part,String style,int action,String label,int offset){
        body.append(art(part==0?width+"_"+height+"_"+style:"hit_"+width,action));
        if(part==0 && label!=null){body.append(gap(-width));text(label,Math.max(6,(width-UiPack.textWidth(label))/2),offset,
                style.equals("primary")?UiTheme.FOREST:style.equals("header")||s.actions.contains(action)?UiTheme.CREAM:UiTheme.MUTED);body.append(gap(width));}
    }
    private void card(Row row,int width,int part,int action){
        cell(width,36,part,"card",action,null,0);
        if(part!=0)return;
        body.append(gap(-width)).append(gap(6)).append(art("head_"+safeFighter(row.fighter),-1)).append(gap(-36));
        text(row.name,40,6,UiTheme.CREAM);
        boolean leader=row.status.startsWith("Leader");
        if(leader)body.append(gap(40)).append(art("crown",-1)).append(gap(-48));
        text(row.status,leader?51:40,18,leader?0xefc863:UiTheme.MINT);
        if(width==144&&s.actions.contains(action))text("...",122,18,UiTheme.MINT);
        body.append(gap(width));
    }
    private static String safeFighter(String value){return value!=null && Wire.CLASSES.contains(value)?value.toLowerCase(Locale.ROOT):"steve";}
    private void left(int row){
        if(row<3){
            cell(144,27,row,"header",-1,null,0);
            if(row==0){body.append(gap(-144));text("Your party",8,9,UiTheme.CREAM);text(s.party.members().size()+"/4",119,9,UiTheme.MINT);body.append(gap(144));}
        }else if(row<19){
            int index=(row-3)/4,part=(row-3)%4;
            if(index<s.party.members().size()){
                var m=s.party.members().get(index);
                String status=m.id().equals(s.party.leader())?m.ready()?"Leader / Ready":"Leader":m.ready()?"Ready":s.party.phase()==PartyBook.Phase.QUEUED?"In queue":s.party.phase()==PartyBook.Phase.IDLE?"In lobby":"Selecting";
                card(new Row(m.name(),m.fighter(),status,"",-1,"",-1),144,part,10+index);
            }else{
                boolean first=index==s.party.members().size();
                cell(144,36,part,first?"empty":"base",first?3:-1,first?"+ "+(4-s.party.members().size())+" open slots":null,14);
            }
        }else if(row==19)cell(144,9,0,"base",-1,null,0);
        else if(row<23)cell(144,27,row-20,"base",-1,null,0);
        else cell(144,27,row-23,s.party.party()?"footer":"base",7,s.party.party()?"Leave party":null,9);
    }
    private void right(int row){
        if(row<3){cell(240,27,row,"header",-1,s.member==null?"Invite friends":"Party member",9);return;}
        if(row<5){
            String[] labels={"Search","Recent","Invites"+(s.invites>0?" "+s.invites:"")};
            for(int i=0;i<3;i++)cell(80,18,row-3,s.tab.equals(new String[]{"SEARCH","RECENT","INVITES"}[i])?"tab_on":"tab",i,labels[i],5);
            return;
        }
        if(row<8){cell(240,27,row-5,"field",3,s.query.isEmpty()?"Find a player...":"Search: "+s.query,9);return;}
        if(row<20){
            int index=(row-8)/4,part=(row-8)%4;
            if(s.member!=null){
                if(index==0)card(s.member,240,part,-1);
                else cell(240,36,part,"action",index==1?40:41,index==1?"Make leader":"Remove from party",14);
            }else if(index<s.rows.size()){
                var item=s.rows.get(index);
                if(item.alternate.isEmpty()){
                    card(item,178,part,-1);cell(62,36,part,s.actions.contains(item.action)?"primary":"base",item.action,item.label,14);
                }else{
                    // Invite sender's name still has the full 16-character budget.
                    card(item,184,part,-1);
                    cell(56,18,part%2,part<2?"primary":"action",part<2?item.action:item.alternateAction,part<2?item.label:item.alternate,5);
                }
            }else cell(240,36,part,"base",-1,index==0?(s.tab.equals("INVITES")?"No invitations":s.tab.equals("RECENT")?"Play a match to meet players":s.query.isEmpty()?"Search for a friend":"No players found"):null,14);
            return;
        }
        if(row<22){
            if(s.pages<=1){cell(240,18,row-20,"base",-1,null,0);return;}
            cell(56,18,row-20,"action",4,s.pages>1?"<":"",5);
            cell(128,18,row-20,"base",-1,s.pages>1?(s.page+1)+" / "+s.pages:"",5);
            cell(56,18,row-20,"action",5,s.pages>1?">":"",5);return;
        }
        if(row==22){
            cell(240,9,0,"base",-1,null,0);body.append(gap(-240));
            String note=s.note;while(UiPack.textWidth(note)>224)note=note.substring(0,note.length()-1);
            text(note,8,0,0xefc863);body.append(gap(240));return;
        }
        cell(240,27,row-23,"base",-1,null,0);
    }
    private Component render(){
        for(int row=0;row<ROWS;row++){
            if(row>0)body.append("\n");
            else body.append(art("backdrop_144",-1)).append(gap(8)).append(art("backdrop_240",-1)).append(gap(-196)).append(gap(-196));
            left(row);body.append(gap(8));right(row);
        }
        return body;
    }
    static Component nativeButton(String label,boolean primary){
        int pad=(150-UiPack.textWidth(label))/2;
        // Keep the background glyph and lettering in the same shadow-free font pass.
        // Vanilla button shadows otherwise overlap the bitmap backing and break up dark labels.
        return Component.empty().withStyle(style->style.withShadowColor(0))
                .append(UiPack.space(-pad)).append(UiPack.strip("party_native_"+(primary?"primary":"base")))
                .append(UiPack.space(pad-150)).append(UiPack.dialogText(label,0).withColor(primary?UiTheme.FOREST:UiTheme.CREAM));
    }
}
