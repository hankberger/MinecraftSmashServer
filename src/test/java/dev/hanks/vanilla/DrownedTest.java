package dev.hanks.vanilla;

import java.util.UUID;
import dev.hanks.network.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DrownedTest {
    @Test void oneWeaponMustReturnBeforeCooldownStarts() {
        var s=new DrownedState();assertTrue(s.canThrow(0));s.launch();assertFalse(s.armed());assertFalse(s.canThrow(100));
        var target=UUID.randomUUID();s.hook(5,target);assertEquals(target,s.target(44));assertNull(s.target(45));
        s.returning();assertNull(s.target(6));assertFalse(s.canThrow(100));s.returned(50);
        assertTrue(s.armed());assertFalse(s.canThrow(73));assertTrue(s.canThrow(74));
    }
    @Test void awayInputIsRelativeToTargetAndMirrorsAcrossStage() {
        assertTrue(DrownedState.pullSelf(-1,5,0));assertTrue(DrownedState.pullSelf(1,-5,0));
        assertFalse(DrownedState.pullSelf(1,5,0));assertFalse(DrownedState.pullSelf(-1,-5,0));assertFalse(DrownedState.pullSelf(0,5,0));
    }
    @Test void aerialSelfReelDoesNotRefreshWhenWeaponReturns() {
        var s=new DrownedState();s.selfReel();s.launch();s.returned(10);assertFalse(s.canSelfReel());
        s.grounded(true);assertFalse(s.canSelfReel(),"The cast frame before takeoff is not a landing");
        s.grounded(false);assertFalse(s.canSelfReel());s.grounded(true);assertTrue(s.canSelfReel());
        assertFalse(s.canThrow(33),"Landing cannot bypass throw cooldown");
    }
    @Test void tridentTipIsStrongButUnarmedCannotGetTipBonus() {
        var light=FighterMoves.light(FighterClass.DROWNED,AttackDirection.FORWARD,false);
        assertEquals(6,FighterMoves.contact(FighterClass.DROWNED,light,1).damage());
        assertEquals(9,FighterMoves.contact(FighterClass.DROWNED,light,2.4).damage());
        var unarmed=DrownedState.unarmed(light);assertEquals(3,FighterMoves.contact(FighterClass.DROWNED,unarmed,2.4).damage());
        assertTrue(unarmed.reach()<light.reach());
        assertNull(CombatGeometry.shape(unarmed,1,0,0).contact(CombatGeometry.body(2.4,0,.6,1.8)));
        assertNotNull(CombatGeometry.shape(light,1,0,0).contact(CombatGeometry.body(2.4,0,.6,1.8)));
    }
    @Test void harpoonIsInstantDetachedAndHasSetupLaunchAtAllPercentages() {
        var s=new CombatState();s.fighterClass=FighterClass.DROWNED;
        var move=FighterMoves.special(FighterClass.DROWNED,false,false);
        assertTrue(s.beginMove(10,1,move));assertFalse(s.chargingSpecial());assertEquals(14,s.impactAt);
        assertFalse(s.releaseSpecial(20));assertTrue(move.detached());assertFalse(move.melee());
        for(int percent:new int[]{0,100,200})assertTrue(Math.abs(move.launch(percent,-1,1).x())<.3);
    }
    @Test void shieldAndProtectionStopHarpoonDamage() {
        var move=FighterMoves.special(FighterClass.DROWNED,false,false);var s=new CombatState();s.guardUntil=20;
        assertTrue(s.receiveHit(10,UUID.randomUUID(),1,move,true).blocked());assertEquals(0,s.percent);
        s=new CombatState();s.protectedUntil=20;
        assertNull(s.receiveHit(10,UUID.randomUUID(),1,move,true).launch());assertEquals(0,s.percent);
    }
    @Test void reelDoesNoDamageAndRiptideSpendsTheNormalRecovery() {
        assertEquals(0,FighterMoves.special(FighterClass.DROWNED,AttackDirection.DOWN,true,false).damage());
        var air=new RecoveryState();air.recover(false);assertFalse(air.recoveryAvailable());assertFalse(air.available());
        assertTrue(FighterMoves.recoveryY(FighterClass.DROWNED)>1);
    }
    @Test void rosterKeepsExistingIdsAndPagesSeventhFighter() {
        assertSame(FighterClass.DROWNED,FighterClass.fromId(12));assertSame(FighterClass.ENDERMAN,FighterClass.fromId(11));
        assertTrue(Wire.CLASSES.contains("DROWNED"));assertEquals(1,Cosmetics.forFighter("DROWNED").size());
        assertTrue(FighterClass.values().length <= FighterMenu.PAGE_SIZE);
    }
}
