package dev.hanks.vanilla;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.hanks.vanilla.FighterMoves.Technique.*;

class RosterRulesTest {
    @Test void rangedFightersDoNotUseMeleeSweepsForBasicPressure() {
        assertEquals(QUICK_ARROW,FighterMoves.light(FighterClass.SKELETON,AttackDirection.FORWARD,false).technique());
        assertEquals(PARCEL,FighterMoves.light(FighterClass.VILLAGER,AttackDirection.FORWARD,true).technique());
        assertFalse(FighterMoves.light(FighterClass.SKELETON,AttackDirection.UP,false).melee());
        assertEquals(FISSURE,FighterMoves.light(FighterClass.ZOMBIE,AttackDirection.DOWN,false).technique());
        assertEquals(ANVIL,FighterMoves.light(FighterClass.STEVE,AttackDirection.DOWN,true).technique());
        assertEquals(SAPLING,FighterMoves.light(FighterClass.VILLAGER,AttackDirection.DOWN,false).technique());
        assertEquals(POT,FighterMoves.light(FighterClass.VILLAGER,AttackDirection.DOWN,true).technique());
    }
    @Test void everyFighterHasADifferentSecondarySpecialOnTheSameInput() {
        var types = new HashSet<FighterMoves.Technique>();
        for(var kind:FighterClass.values()) for(boolean air:new boolean[]{false,true}) {
            var move = FighterMoves.special(kind,AttackDirection.DOWN,air,false);
            assertEquals(20,move.id()); assertEquals(AttackKind.HEAVY,move.kind()); assertEquals(air,move.aerial());
            if(!air) assertTrue(types.add(move.technique()));
            assertEquals(7,FighterMoves.recovery(kind).id());
        }
        assertEquals(5,types.size());
    }
    @Test void biteBeatsShieldButHasShortReachAndPunishableCommitment() {
        var bite=FighterMoves.special(FighterClass.ZOMBIE,AttackDirection.DOWN,false,false);
        var target=new CombatState(); target.requestGuard(0,true,true);
        var hit=target.receiveHit(1,UUID.randomUUID(),-1,bite,true);
        assertFalse(hit.blocked()); assertNotNull(hit.launch()); assertEquals(10,target.percent); assertFalse(target.blocking(1));
        for(int facing:new int[]{-1,1}) {
            var shape=CombatGeometry.shape(bite,facing,0,0);
            assertNotNull(shape.contact(CombatGeometry.body(facing*.8,0,.6,1.8)));
            assertNull(shape.contact(CombatGeometry.body(facing*1.7,0,.6,1.8)));
            assertNull(shape.contact(CombatGeometry.body(facing*.8,1.8,.6,1.8)));
        }
        assertTrue(bite.startup() >= 6 && bite.lockout()-bite.startup() >= 18);
    }
    @Test void alexChainRequiresContactAndCannotLoopBackToTheFirstHitEarly() {
        var s=new CombatState(); s.fighterClass=FighterClass.ALEX;
        s.beginMove(0,1,FighterMoves.light(FighterClass.ALEX,AttackDirection.FORWARD,false));
        s.impactAt=-1; s.activeUntil=4;
        assertFalse(s.beginMove(2,1,FighterMoves.combo(2)));
        s.confirm(2,s.move); assertTrue(s.beginMove(2,1,FighterMoves.combo(2)));
        assertEquals(6,s.impactAt,"Cancel must not put every active frame inside the victim's hit immunity");
        s.impactAt=-1; s.activeUntil=8; s.confirm(6,s.move);
        assertTrue(s.beginMove(6,1,FighterMoves.combo(3)));
        s.impactAt=-1; s.activeUntil=12; s.confirm(10,s.move);
        assertFalse(s.beginMove(10,1,FighterMoves.light(FighterClass.ALEX,AttackDirection.FORWARD,false)));
    }
    @Test void airStepAndDiveBounceAreOncePerLandingAndIndependentOfRecovery() {
        var kit=new KitState(); var air=new RecoveryState();
        kit.step(); assertFalse(kit.stepAvailable()); assertTrue(kit.bounce()); assertFalse(kit.bounce());
        kit.grounded(true); assertFalse(kit.stepAvailable(),"Ground frame before takeoff is not a landing");
        kit.grounded(false); kit.grounded(true); assertTrue(kit.stepAvailable()); assertTrue(kit.bounce());
        air.recover(false); kit.reset(); assertTrue(air.helpless(),"Kit changes never refund recovery");
    }
    @Test void alexCanBufferTheNextChainHitThroughPauseWithoutHittingInsideImmunity() {
        var s=new CombatState(); s.fighterClass=FighterClass.ALEX;
        s.beginMove(0,1,FighterMoves.light(FighterClass.ALEX,AttackDirection.FORWARD,false));
        s.impactAt=-1; s.activeUntil=4; s.confirm(2,s.move); s.pause(2,2);
        assertTrue(s.buffer(3,new AttackIntent(AttackKind.LIGHT)),"Contact chain is buffered even before normal recovery ends");
        assertFalse(s.beginMove(3,1,FighterMoves.combo(2)));
        assertNotNull(s.pending(5)); assertTrue(s.beginMove(5,1,FighterMoves.combo(2)));
        assertEquals(8,s.impactAt,"The target's hit immunity was also extended by hit pause");
        var utility=new AttackIntent(AttackKind.HEAVY,AttackDirection.DOWN,0,false);
        assertFalse(s.buffer(5,utility),"A secondary special does not inherit the dash cancel");
    }
    @Test void arrowPokesDoNotKillLikeAFullyDrawnShot() {
        var poke=FighterMoves.light(FighterClass.SKELETON,AttackDirection.FORWARD,false).launch(200,1,1);
        var power=FighterMoves.arrow(20).launch(200,1,1);
        assertEquals(7,poke.stun()); assertTrue(power.stun() > 20); assertTrue(power.x() > poke.x()*2);
        assertTrue(FighterMoves.arrow(20).launch(200,1,1).stun() > FighterMoves.arrow(20).launch(20,1,1).stun());
    }
    @Test void steveHiltAndTipCreateDifferentSpacingOutcomes() {
        var sword=FighterMoves.light(FighterClass.STEVE,AttackDirection.FORWARD,false);
        assertEquals(4,FighterMoves.contact(FighterClass.STEVE,sword,.7).damage());
        assertEquals(7,FighterMoves.contact(FighterClass.STEVE,sword,1.4).damage());
        assertEquals(9,FighterMoves.contact(FighterClass.STEVE,sword,2.2).damage());
    }
}
