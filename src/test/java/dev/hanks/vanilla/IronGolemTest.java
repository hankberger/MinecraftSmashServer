package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IronGolemTest {
    private static final FighterClass GOLEM=FighterClass.IRON_GOLEM;
    private static final UUID ATTACKER=UUID.randomUUID();
    private CombatState braced() {
        var s=new CombatState();s.fighterClass=GOLEM;s.percent=100;
        assertTrue(s.beginMove(100,1,FighterMoves.special(GOLEM,AttackDirection.DOWN,false,false)));
        s.impactAt=-1;s.plantFeet(102,true);return s;
    }
    @Test void braceTakesFullDamageButConsumesOnlyOneLightAndAllowsACounter() {
        var light=FighterMoves.light(FighterClass.STEVE,AttackDirection.FORWARD,false);
        var s=braced();var result=s.receiveHit(104,ATTACKER,-1,light,true);
        assertTrue(result.armored());assertEquals(107,s.percent);assertEquals(ATTACKER,s.creditedAttacker(104));
        assertTrue(result.launch().x()<0&&Math.abs(result.launch().x())<=.22);
        assertEquals(0,result.launch().y());assertEquals(0,result.launch().stun());
        assertFalse(s.bracing(104,true));assertTrue(s.beginMove(107,1,FighterMoves.special(GOLEM,false,false)));
        var second=s.receiveHit(120,ATTACKER,1,light,true);
        assertFalse(second.armored());assertTrue(second.launch().x()>.5);assertTrue(s.stunUntil>120);
    }
    @Test void heavyBreaksBraceAndInterruptsCounter() {
        var s=braced();var heavy=FighterMoves.special(FighterClass.STEVE,false,false);
        var hit=s.receiveHit(104,ATTACKER,1,heavy,true);
        assertFalse(hit.armored());assertEquals(114,s.percent);assertTrue(hit.launch().y()>0);
        assertFalse(s.bracing(105,true));assertFalse(s.beginMove(105,1,FighterMoves.special(GOLEM,false,false)));
    }
    @Test void braceExpiresAndCannotBeCarriedIntoAirOrThroughRespawn() {
        var s=braced();assertTrue(s.bracing(113,true));assertFalse(s.bracing(114,true));
        s=braced();assertFalse(s.bracing(103,false));s.leaveGround();assertFalse(s.bracing(104,true));
        s=braced();s.respawn(103);assertFalse(s.bracing(103,true));
        s=braced();s.interrupt();assertFalse(s.bracing(103,true));
        s=braced();s.beginFloat(103);assertFalse(s.bracing(103,true));
        s=new CombatState();s.plantFeet(100,true);assertFalse(s.bracing(101,true));
    }
    @Test void airborneAndExpiredStancesDoNotReduceIncomingLaunch() {
        var light=FighterMoves.light(FighterClass.ALEX,AttackDirection.FORWARD,false);
        for(boolean airborne:new boolean[]{false,true}) {
            var s=braced();var hit=s.receiveHit(airborne?104:114,ATTACKER,1,light,!airborne);
            assertFalse(hit.armored());assertTrue(hit.launch().y()>0);assertTrue(hit.launch().stun()>0);
        }
    }
    @Test void protectedContactDoesNotSpendBraceAndPausePreservesItsWindow() {
        var s=braced();s.protectedUntil=108;
        assertNull(s.receiveHit(104,ATTACKER,1,FighterMoves.light(FighterClass.STEVE,AttackDirection.FORWARD,false),true).launch());
        assertTrue(s.bracing(105,true));assertEquals(100,s.percent);
        s.pause(106,2);assertTrue(s.bracing(115,true));assertFalse(s.bracing(116,true));
    }
    @Test void uppercutIsCommittedUnchargedAndLaunchesMostlyUpInBothDirections() {
        var move=FighterMoves.special(GOLEM,false,false);var s=new CombatState();s.fighterClass=GOLEM;
        assertTrue(s.beginMove(100,1,move));assertFalse(s.chargingSpecial());assertFalse(s.releaseSpecial(120));
        assertEquals(108,s.impactAt);assertTrue(s.readyAt-s.impactAt>=20);
        for(int facing:new int[]{-1,1}) {
            var launch=move.launch(100,facing,1);assertTrue(launch.y()>Math.abs(launch.x())*2);
            var shape=CombatGeometry.shape(move,facing,0,0);
            assertNotNull(shape.contact(CombatGeometry.body(facing*1.4,0,.6,1.8)));
            assertNotNull(shape.contact(CombatGeometry.body(facing*.55,3,.6,1.8)));
            assertNull(shape.contact(CombatGeometry.body(facing*3.5,0,.6,1.8)));
            assertNull(shape.contact(CombatGeometry.body(0,4,.6,1.8)));
        }
    }
    @Test void weightAndRecoveryHaveMobilityTradeoffs() {
        assertTrue(FighterMoves.weight(GOLEM)<FighterMoves.weight(FighterClass.STEVE));
        assertTrue(FighterMoves.run(GOLEM)<FighterMoves.run(FighterClass.STEVE));
        assertTrue(FighterMoves.air(GOLEM)<FighterMoves.air(FighterClass.STEVE));
        assertTrue(FighterMoves.recoveryY(GOLEM)>FighterMoves.recoveryY(FighterClass.STEVE));
        assertTrue(FighterMoves.recoveryX(GOLEM)<FighterMoves.recoveryX(FighterClass.STEVE));
        assertEquals(GOLEM,FighterClass.fromId(13));
    }
}
