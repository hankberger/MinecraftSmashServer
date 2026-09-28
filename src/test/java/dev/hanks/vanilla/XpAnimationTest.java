package dev.hanks.vanilla;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class XpAnimationTest {
    @Test void revealsTwoBoundariesOnceAndFinishesAtCommittedTotal(){
        var animation=new XpAnimation(90,290,false);long last=90;int levels=0;
        for(int tick=0;tick<200;tick++){
            animation.tick();assertTrue(animation.shown()>=last);assertTrue(animation.shown()<=290);last=animation.shown();
            if(animation.leveled())levels++;
        }
        assertEquals(2,levels);assertEquals(290,animation.shown());assertTrue(animation.done());
    }
    @Test void replayAndNoRewardSkipCelebration(){
        for(var animation:new XpAnimation[]{new XpAnimation(90,290,true),new XpAnimation(290,290,false)}){
            assertEquals(290,animation.shown());assertTrue(animation.done());
            for(int i=0;i<100;i++){animation.tick();assertFalse(animation.leveled());assertEquals(0,animation.pulse());}
        }
    }
    @Test void exactBoundaryWaitsForItsLevelUpPulse(){
        var animation=new XpAnimation(0,100,false);int pops=0;
        for(int i=0;i<150;i++){animation.tick();if(animation.leveled()){pops++;assertFalse(animation.done());assertEquals(100,animation.shown());}}
        assertTrue(animation.done());assertEquals(1,pops);
    }
}
