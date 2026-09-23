package dev.hanks.vanilla;

import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BattleStageTest {
    @Test void matchDrawIsSharedByEveryArrivalAndIncludesAllStages() {
        var seen=EnumSet.noneOf(BattleStage.class);
        int[] count=new int[BattleStage.values().length];
        var random=new java.util.Random(173);
        for(int i=0;i<3000;i++) {
            var id=new UUID(random.nextLong(),random.nextLong());
            var stage=BattleStage.select(id,false); seen.add(stage); count[stage.ordinal()]++;
            for(int player=0;player<4;player++) assertEquals(stage,BattleStage.select(id,false));
            assertEquals(BattleStage.SKYBOUND_GROVE,BattleStage.select(id,true));
        }
        assertEquals(EnumSet.allOf(BattleStage.class),seen);
        for(int n:count) assertTrue(n>850&&n<1150,"Each stage receives approximately one third of reservations");
    }
    @Test void spawnAndBlastZonesFitEveryLayout() {
        for(var stage:BattleStage.values()) {
            for(int i=0;i<4;i++) assertTrue(stage.supported(stage.spawnX(i,false),81));
            for(int i=0;i<2;i++) assertTrue(stage.supported(stage.spawnX(i,true),81));
            assertTrue(stage.supported(stage.dummyX(),81));
            assertTrue(stage.standingOnPlatform(.5,stage.respawnLanding(),.5));
            assertFalse(stage.outside(.5,stage.respawnTop(),.5));
            assertTrue(stage.edge(-1)-stage.blastLeft()>=20);
            assertTrue(stage.blastRight()-stage.edge(1)>=20);
            assertTrue(stage.outside(stage.blastLeft()-.01,81,.5));
            assertTrue(stage.outside(stage.blastRight()+.01,81,.5));
            assertTrue(stage.outside(0,stage.blastTop()+.01,.5));
            assertTrue(stage.outside(Double.NaN,81,.5));
            assertEquals(stage.respawnTop(),RespawnRules.y(0,stage.respawnTop(),stage.respawnLanding()));
            assertEquals(stage.respawnLanding(),RespawnRules.y(32,stage.respawnTop(),stage.respawnLanding()));
        }
    }
    @Test void landOnFirstCrossedPlatformAndDropThroughTheActualLayout() {
        for(var stage:BattleStage.values()) for(var p:stage.platforms) {
            double x=(p.left()+p.right()+1)/2.0;
            assertEquals(p.top(),stage.landingFloor(x,p.top()+2,p.top()-1,false));
            assertEquals(81,stage.landingFloor(x,p.top()+2,p.top()-1,true));
            assertEquals(81,stage.landingFloor(x,p.top()-1,p.top()+2,false),"Rising through a one-way platform");
        }
        assertEquals(92,BattleStage.CLOUDSPIRE.landingFloor(.5,99,80,false));
        assertEquals(81,BattleStage.EMBERFORGE.landingFloor(-8,90,80,false),"No phantom Grove side platform");
        assertEquals(-100,BattleStage.EMBERFORGE.landingFloor(15,82,80,false),"No phantom Grove deck");
    }
    @Test void ledgesUseEachDeckWidthAndClimbToSolidGround() {
        for(var stage:BattleStage.values()) for(int side:new int[]{-1,1}) {
            var ledge=new LedgeState(stage); double x=stage.hangX(side);
            assertEquals(side,ledge.candidate(10,x,82,x,77,-5));
            assertEquals(0,ledge.candidate(10,stage.standX(side),79,x,78,-1));
            ledge.grab(side,10); ledge.climb(14);
            assertEquals(x,ledge.x(18));
            assertEquals(stage.standX(side),ledge.x(22));
            assertTrue(stage.supported(ledge.x(22),ledge.y(22)));
        }
    }
}
