package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChargeRulesTest {
    private CombatState start(FighterClass kind) {
        var s=new CombatState();s.fighterClass=kind;
        assertTrue(s.beginMove(100,1,FighterMoves.special(kind,false,false)));return s;
    }
    @Test void everyPrimaryWaitsForReleaseAndCapsWithoutAutoFiring() {
        for(var kind:FighterClass.values()) {
            var s=start(kind);
            assertTrue(s.chargingSpecial());assertFalse(s.facingLocked(500));
            assertFalse(s.buffer(500,new AttackIntent(AttackKind.LIGHT)));
            assertTrue(s.releaseSpecial(500)); assertFalse(s.chargingSpecial());
            assertEquals(ChargeRules.fullTicks(kind),s.releasedCharge);
            assertTrue(s.facingLocked(500));assertFalse(s.releaseSpecial(501));
            assertTrue(s.readyAt>s.impactAt);
        }
    }
    @Test void quickTapsKeepBasePowerAndLongerHoldsIncreaseTheReward() {
        for(var kind:new FighterClass[]{FighterClass.STEVE,FighterClass.ALEX,FighterClass.ZOMBIE}) {
            var tap=start(kind);tap.releaseSpecial(102);
            assertEquals(FighterMoves.special(kind,false,false).damage(),tap.move.damage());
            var partial=start(kind);partial.releaseSpecial(109);
            var full=start(kind);full.releaseSpecial(100+ChargeRules.fullTicks(kind));
            assertTrue(partial.move.damage()>tap.move.damage());assertTrue(full.move.damage()>partial.move.damage());
            assertTrue(full.move.launch(100,1,1).x()>tap.move.launch(100,1,1).x());
            assertTrue(full.move.shieldDamage()>tap.move.shieldDamage());
        }
        assertEquals(26,FighterMoves.contact(FighterClass.STEVE,ChargeRules.charged(FighterClass.STEVE,FighterMoves.special(FighterClass.STEVE,false,false),18),2.4).damage());
        assertEquals(18,ChargeRules.bell(16).damage());assertEquals(12,ChargeRules.bell(2).damage());
    }
    @Test void gettingHitCancelsEveryChargeAndReleaseCannotReviveIt() {
        for(var kind:FighterClass.values()) {
            var s=start(kind);
            assertNotNull(s.receiveHit(110,UUID.randomUUID(),1,FighterMoves.light(FighterClass.ALEX,AttackDirection.FORWARD,false),true).launch());
            assertFalse(s.chargingSpecial());assertFalse(s.releaseSpecial(115));assertEquals(-1,s.impactAt);
            s.respawn(120);assertEquals(0,s.releasedCharge);assertFalse(s.chargeFullShown);
        }
    }
    @Test void zombieArmorAndCommittedDiveStartOnlyAfterRelease() {
        var s=start(FighterClass.ZOMBIE);
        assertFalse(s.armored(120,true));assertFalse(s.slamCommitted(120));
        s.releaseSpecial(122);assertTrue(s.armored(123,true));assertTrue(s.slamCommitted(123));
        assertFalse(s.armored(123,false));assertFalse(s.armored(125,true));
    }
}
