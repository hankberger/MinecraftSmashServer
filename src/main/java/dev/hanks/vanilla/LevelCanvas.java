package dev.hanks.vanilla;

import dev.hanks.network.*;
import net.minecraft.network.chat.*;

final class LevelCanvas {
    static final int WIDTH=288,ROWS=20;
    static Component render(LevelRules.Progress p){
        var body=Component.empty();var next=LevelRules.nextTier(p.level());
        for(int row=0;row<ROWS;row++){
            if(row>0)body.append("\n");
            if(row==0)body.append(UiPack.strip("levels_profile_0")).append(UiPack.space(-1)).append(UiPack.strip("levels_profile_1")).append(UiPack.space(-144)).append(UiPack.space(-144));
            if(row==1){
                // The doubled font's space is 8px; the numeric width helper would count it as 7px.
                String label="LEVEL "+p.level();int width=UiPack.storeBalanceWidth(label)+1,x=(WIDTH-width)/2;
                body.append(UiPack.space(x)).append(UiPack.storeBalance(label).withColor(p.tier().color)).append(UiPack.space(-x-width));
            }
            if(row==4)center(body,p.tier().label,0,p.tier().color);
            if(row==7)body.append(UiPack.space(24)).append(UiPack.strip("levels_bar_"+Math.round(p.fraction()*24))).append(UiPack.space(-256)).append(UiPack.space(-10));
            if(row==9)center(body,PointRules.format(p.into())+" / "+PointRules.format(p.required())+" XP",0,UiTheme.CREAM);
            if(row==11)center(body,PointRules.format(p.remaining())+" XP to Level "+(p.level()+1),0,UiTheme.MINT);
            if(row==14)center(body,next==null?"Legend status":"Next milestone",0,UiTheme.MUTED);
            if(row==16)center(body,next==null?"Keep climbing":next.label+"  /  Level "+next.level,0,0xffd66b);
            if(row==18)center(body,PointRules.format(p.total())+" lifetime XP",0,UiTheme.MINT);
            body.append(UiPack.space(WIDTH));
        }
        return body;
    }
    private static void center(MutableComponent body,String label,int offset,int color){
        int width=UiPack.textWidth(label),x=(WIDTH-width)/2;
        body.append(UiPack.space(x)).append(UiPack.dialogText(label,offset).withColor(color)).append(UiPack.space(-x-width));
    }
    private LevelCanvas(){}
}
