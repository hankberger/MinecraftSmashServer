package dev.hanks.vanilla;

import dev.hanks.network.LevelRules;

/** Tick-based reveal of an already committed receipt. Animation never grants XP. */
final class XpAnimation {
    private final long end;
    private long shown,start,target;
    private int tick,duration,hold=10,pulse;
    private boolean done,leveled;
    XpAnimation(long before,long after,boolean replay){
        if(before<0||after<before)throw new IllegalArgumentException("Invalid XP interval");
        shown=before;end=after;
        if(replay||before==after){shown=after;done=true;}else segment();
    }
    private void segment(){
        start=shown;var p=LevelRules.progress(shown);target=shown+Math.min(end-shown,p.remaining());
        duration=Math.clamp((int)(target-start)/3,16,42);tick=0;
    }
    void tick(){
        leveled=false;if(pulse>0)pulse--;
        if(hold>0){hold--;return;}if(done)return;
        double t=Math.min(1,++tick/(double)duration),eased=1-Math.pow(1-t,2);
        shown=start+Math.round((target-start)*eased);
        // Do not cross a boundary until its actual endpoint; rounding cannot fire an early level-up.
        if(t<1)shown=Math.min(shown,target-1);
        if(t>=1){
            leveled=LevelRules.progress(shown).level()>LevelRules.progress(start).level();
            if(leveled){pulse=16;hold=16;}
            if(shown==end)done=true;else segment();
        }
    }
    long shown(){return shown;}
    boolean leveled(){return leveled;}
    int pulse(){return pulse;}
    boolean done(){return done&&pulse==0;}
}
