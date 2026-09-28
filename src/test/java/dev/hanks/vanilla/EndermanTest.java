package dev.hanks.vanilla;

import java.util.UUID;
import dev.hanks.network.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EndermanTest {
    @Test void preyExpiresAndPursuitConsumesItWithoutRefillingAirOptions() {
        var state=new EnderState();var target=UUID.randomUUID();state.mark(10,target);
        assertEquals(target,state.prey(89));assertNull(state.prey(90));
        state.mark(100,target);assertTrue(state.canPursue(100,false));state.pursue(100);
        assertNull(state.prey(100));assertFalse(state.canPursue(128,false));
        state.mark(130,target);assertFalse(state.canPursue(130,false));
        state.grounded(false);state.grounded(true);assertTrue(state.canPursue(130,false));
        state.reset();assertNull(state.prey(130));assertTrue(state.airAvailable());
    }
    @Test void landingDoesNotBypassPursuitCooldown() {
        var state=new EnderState();state.pursue(20);state.grounded(true);
        assertFalse(state.canPursue(47,true));assertTrue(state.canPursue(48,true));
    }
    @Test void pursuitIsMovementWithNoAutomaticHitOrCharge() {
        var s=new CombatState();s.fighterClass=FighterClass.ENDERMAN;
        var move=FighterMoves.special(FighterClass.ENDERMAN,false,false);
        assertTrue(s.beginMove(10,-1,move));assertFalse(s.chargingSpecial());assertEquals(13,s.impactAt);
        assertEquals(0,move.damage());assertFalse(move.melee());
        s.receiveHit(11,UUID.randomUUID(),1,AttackKind.LIGHT);
        assertEquals(-1,s.impactAt);assertFalse(s.releaseSpecial(14));
    }
    @Test void realLightContactOpensImmediatePursuitButWhiffDoesNot() {
        var s=new CombatState();s.fighterClass=FighterClass.ENDERMAN;
        var light=FighterMoves.light(FighterClass.ENDERMAN,AttackDirection.FORWARD,false);
        assertTrue(s.beginMove(10,1,light));s.impactAt=-1;s.activeUntil=18;
        assertFalse(s.beginMove(14,1,FighterMoves.special(FighterClass.ENDERMAN,false,false)));
        assertTrue(s.confirm(14,light));assertTrue(s.beginMove(14,1,FighterMoves.special(FighterClass.ENDERMAN,false,false)));
        assertEquals(17,s.impactAt);
    }
    @Test void swingRespectsShieldAndSpawnProtectionWithBroadForwardCoverage() {
        var swing=FighterMoves.special(FighterClass.ENDERMAN,AttackDirection.DOWN,false,false);
        var target=new CombatState();target.requestGuard(10,true,true);
        var hit=target.receiveHit(14,UUID.randomUUID(),1,swing,true);
        assertNull(hit.launch());assertTrue(hit.blocked());assertEquals(0,target.percent);
        target.respawn(20);target.protectedUntil=30;
        assertNull(target.receiveHit(21,UUID.randomUUID(),1,swing,true).launch());
        var shape=CombatGeometry.shape(swing,1,0,81);
        assertNotNull(shape.contact(CombatGeometry.body(2.7,81,.6,1.8)));
        assertNull(shape.contact(CombatGeometry.body(3.7,81,.6,1.8)));
        assertNull(shape.contact(CombatGeometry.body(-1,81,.6,1.8)));
    }
    @Test void swingAlwaysLaunchesAwayAndUpOnBothSidesAndInTheAir() {
        for(boolean air:new boolean[]{false,true})for(int facing:new int[]{-1,1})for(int percent:new int[]{8,80,160}) {
            var swing=FighterMoves.special(FighterClass.ENDERMAN,AttackDirection.DOWN,air,false);
            var launch=swing.launch(percent,facing,1);
            assertTrue(launch.x()*facing>.7);assertTrue(launch.y()>.8);
        }
    }
    @Test void swingContactOpensPursuitButWhiffDoesNot() {
        var s=new CombatState();s.fighterClass=FighterClass.ENDERMAN;
        var swing=FighterMoves.special(FighterClass.ENDERMAN,AttackDirection.DOWN,false,false);
        assertTrue(s.beginMove(10,1,swing));s.impactAt=-1;s.activeUntil=18;
        assertFalse(s.beginMove(15,1,FighterMoves.special(FighterClass.ENDERMAN,false,false)));
        assertTrue(s.confirm(15,swing));assertTrue(s.beginMove(15,1,FighterMoves.special(FighterClass.ENDERMAN,false,false)));
        assertEquals(18,s.impactAt);
    }
    @Test void longArmsHaveNarrowVerticalCoverage() {
        var shape=CombatGeometry.shape(FighterMoves.light(FighterClass.ENDERMAN,AttackDirection.FORWARD,false),1,0,81);
        assertNotNull(shape.contact(CombatGeometry.body(2.8,81,.6,1.8)));
        assertNull(shape.contact(CombatGeometry.body(2.8,83,.6,1.8)));
    }
    @Test void stableIdentitySurvivesTheRework() {
        assertSame(FighterClass.ENDERMAN,FighterClass.fromId(11));
        for(int id=4;id<10;id++)assertNull(FighterClass.fromId(id));
        assertTrue(Wire.CLASSES.contains("ENDERMAN"));assertEquals(1,Cosmetics.forFighter("ENDERMAN").size());
    }
}
