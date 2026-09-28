package dev.hanks.vanilla;

import dev.hanks.network.*;
import java.util.*;
import java.util.function.IntFunction;
import net.minecraft.network.chat.*;

final class RankingsCanvas {
    static final int WIDTH=384,ROWS=28;
    private final MutableComponent body=Component.empty();
    private Component gap(int n){return n< -256?Component.empty().append(UiPack.space(-256)).append(UiPack.space(n+256)):UiPack.space(n);}
    private void text(String value,int x,int offset,int color){body.append(gap(x)).append(UiPack.dialogText(value,offset).withColor(color)).append(gap(-x-UiPack.textWidth(value)));}
    private void entry(Rankings.Entry e,boolean own,String name,Rankings.Board board){
        if(e==null){text("You",18,6,UiTheme.MINT);text(name,72,6,UiTheme.CREAM);text("Unranked",305,6,UiTheme.MUTED);return;}
        int color=e.rank()==1?0xefc863:e.rank()==2?0xdbe3eb:e.rank()==3?0xd8a982:UiTheme.MINT;
        String rank="#"+PointRules.compact(e.rank());text(rank,18,5,color);
        String fighter=Wire.CLASSES.contains(e.fighter())?e.fighter().toLowerCase(Locale.ROOT):"steve";
        body.append(UiPack.space(49)).append(UiPack.strip("rankings_head_"+fighter)).append(UiPack.space(-67));
        text(own?name:e.name(),72,5,own?UiTheme.READY:UiTheme.CREAM);
        if(own)text("YOU",183,5,UiTheme.READY);
        String score=PointRules.compact(e.score())+" "+board.unit(e.score());
        text(score,365-UiPack.textWidth(score),5,UiTheme.CREAM);
    }
    static Component render(Rankings.Snapshot data,Rankings.Board board,UUID player,String name,String message,IntFunction<ClickEvent.Custom> event){
        var c=new RankingsCanvas();var standing=data==null?null:data.boards().get(board);
        for(int row=0;row<ROWS;row++){
            if(row>0)c.body.append("\n");
            if(row==0){
                c.body.append(UiPack.strip("rankings_panel_0")).append(UiPack.space(-1)).append(UiPack.strip("rankings_panel_1")).append(UiPack.space(-192)).append(UiPack.space(-192));
                c.text("Rankings",14,9,0xefc863);
                String subtitle=board==Rankings.Board.WEEKLY_WINS?"Resets Mon, 00:00 UTC":"All-time";
                c.text(subtitle,370-UiPack.textWidth(subtitle),9,UiTheme.MINT);
            }
            if(row==3||row==4){
                for(var tab:Rankings.Board.values()){
                    var art=UiPack.strip(row==4?"rankings_hit":"rankings_tab_"+(board==tab?"on":"off"));
                    c.body.append(art.withStyle(s->s.withClickEvent(event.apply(tab.ordinal()))));
                    if(row==3){c.body.append(UiPack.space(-128));c.text(tab.label,(128-UiPack.textWidth(tab.label))/2,5,board==tab?UiTheme.CREAM:UiTheme.MUTED);c.body.append(UiPack.space(128));}
                }
                continue;
            }
            if(row>=5&&row<25&&(row-5)%2==0){
                int index=(row-5)/2;
                if(standing!=null&&index<standing.leaders().size()){
                    var e=standing.leaders().get(index);c.entry(e,e.player().equals(player),name,board);
                }else if(index==0){
                    String empty=message.isEmpty()?"No "+board.unit+" yet" :message;
                    c.text(empty,(WIDTH-UiPack.textWidth(empty))/2,5,UiTheme.MINT);
                }
            }
            if(row==25){
                if(standing==null)c.text(message.isEmpty()?"Loading...":message,18,6,UiTheme.MINT);
                else c.entry(standing.own(),true,name,board);
            }
            c.body.append(UiPack.space(WIDTH));
        }
        return c.body;
    }
    private RankingsCanvas(){}
}
