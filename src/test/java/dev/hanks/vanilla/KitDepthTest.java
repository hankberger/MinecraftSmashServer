package dev.hanks.vanilla;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KitDepthTest {
    private static CombatState active(FighterClass kind, AttackDirection aim, boolean air) {
        var s = new CombatState(); s.fighterClass = kind;
        assertTrue(s.beginMove(100, 1, FighterMoves.light(kind, aim, air)));
        s.impactAt = -1; s.activeUntil = 107;
        return s;
    }
    @Test void steveRewardsSwordSpacingWithoutBuffingOtherDirectionsOrClasses() {
        var sword = FighterMoves.light(FighterClass.STEVE, AttackDirection.FORWARD, false);
        assertEquals(7, FighterMoves.contact(FighterClass.STEVE, sword, 1.5).damage());
        assertEquals(9, FighterMoves.contact(FighterClass.STEVE, sword, 2.1).damage());
        assertTrue(FighterMoves.contact(FighterClass.STEVE, sword, 2.1).launch(60,1,1).x() > sword.launch(60,1,1).x());
        for (var c : FighterClass.values()) for (var aim : AttackDirection.values()) {
            var move = FighterMoves.light(c, aim, true);
            if (c == FighterClass.STEVE && aim == AttackDirection.FORWARD) continue;
            assertEquals(move, FighterMoves.contact(c, move, 2.2));
        }
        assertEquals(17, FighterMoves.contact(FighterClass.STEVE, FighterMoves.special(FighterClass.STEVE,false,false), 2.3).damage());
    }
    @Test void onlyStevesGroundShovelOpensAFasterPickaxe() {
        for (var aim : AttackDirection.values()) for (boolean air : new boolean[]{false,true}) {
            var s = active(FighterClass.STEVE, aim, air);
            boolean shovel = aim == AttackDirection.DOWN && !air;
            assertEquals(shovel, s.confirm(104,s.move));
            assertEquals(shovel, s.beginMove(104,1,FighterMoves.special(FighterClass.STEVE,air,false)));
            if (shovel) { assertEquals(108,s.impactAt); assertEquals(122,s.readyAt); }
        }
        var low = FighterMoves.light(FighterClass.STEVE,AttackDirection.DOWN,false).launch(40,1,1);
        assertTrue(low.y() > low.x(), "Shovel pops up instead of sending the target out of follow-up range");
    }
    @Test void alexCanConfirmEveryLightIntoDashButCannotCancelAWhiff() {
        for (var aim : AttackDirection.values()) {
            var s = active(FighterClass.ALEX, aim, true);
            assertFalse(s.beginMove(103,1,FighterMoves.special(FighterClass.ALEX,true,false)));
            assertTrue(s.confirm(103,s.move));
            assertTrue(s.beginMove(103,1,FighterMoves.special(FighterClass.ALEX,true,false)));
            assertFalse(s.specialConfirm(103)); assertEquals(107,s.impactAt);
        }
    }
    @Test void confirmCannotCancelShieldOrHitPauseAndExpiresOrClearsOnHit() {
        var s = active(FighterClass.ALEX,AttackDirection.FORWARD,false); s.confirm(103,s.move);
        s.guardUntil = 106; long ready = s.readyAt;
        assertFalse(s.beginMove(104,1,FighterMoves.special(FighterClass.ALEX,false,false)));
        assertEquals(ready,s.readyAt); s.guardUntil = 0;
        s.pause(104,2);
        assertFalse(s.beginMove(105,1,FighterMoves.special(FighterClass.ALEX,false,false)));
        assertTrue(s.buffer(105,new AttackIntent(AttackKind.HEAVY)));
        assertTrue(s.beginMove(107,1,FighterMoves.special(FighterClass.ALEX,false,false)));
        s = active(FighterClass.ALEX,AttackDirection.NEUTRAL,true); s.confirm(103,s.move);
        assertFalse(s.specialConfirm(111));
        s.receiveHit(104,UUID.randomUUID(),1,FighterMoves.arrow(8));
        assertFalse(s.specialConfirm(104)); assertFalse(s.confirm(104,s.move));
        s.respawn(110); assertEquals(0,s.confirmedUntil);
    }
    @Test void alexDashContactShortensRecoveryWithoutInterruptingItsMotion() {
        var s = new CombatState(); s.fighterClass = FighterClass.ALEX;
        s.beginMove(100,1,FighterMoves.special(FighterClass.ALEX,true,false));
        s.impactAt = -1; s.activeUntil = 107; s.motionUntil = 107;
        s.confirm(105,s.move); assertEquals(109,s.readyAt); assertEquals(107,s.motionUntil);
        s.pause(105,2); assertEquals(111,s.readyAt); assertEquals(109,s.motionUntil);
        assertFalse(s.specialConfirm(105));
    }
    @Test void zombieClawsAreQuickSetupsRatherThanSlowArmoredFinishers() {
        for (boolean air : new boolean[]{false,true}) {
            var claws = FighterMoves.light(FighterClass.ZOMBIE,AttackDirection.FORWARD,air);
            var heavy = FighterMoves.special(FighterClass.ZOMBIE,air,false);
            assertTrue(claws.startup()<=3 && claws.lockout()<=12);
            assertTrue(claws.launch(40,1,1).x()<heavy.launch(40,1,1).x());
            var s=new CombatState(); s.fighterClass=FighterClass.ZOMBIE;
            s.beginMove(0,1,heavy); s.releaseSpecial(16);
            assertNotNull(s.receiveHit(17,UUID.randomUUID(),1,claws,true).launch());
            assertEquals(-1,s.impactAt);
        }
    }
    @Test void fullyDrawnArrowsTradeCommitmentForLaunchAndGuardPressure() {
        var quick = FighterMoves.arrow(8); var full = FighterMoves.arrow(20);
        assertEquals(5,quick.launch(100,1,1).stun()); assertTrue(full.launch(100,1,1).stun() > 8);
        assertTrue(full.launch(100,1,1).x() > quick.launch(100,1,1).x());
        assertTrue(full.launch(100,1,1).y() > quick.launch(100,1,1).y());
        assertEquals(10,full.damage()); assertEquals(20,full.shieldDamage());
        assertEquals(full,FighterMoves.arrow(999));
    }
    @Test void bellBattingHasDistinctDirectionalTrajectories() {
        var forward = BellRules.bat(AttackDirection.FORWARD,1); var up = BellRules.bat(AttackDirection.UP,1);
        var down = BellRules.bat(AttackDirection.DOWN,1);
        assertTrue(forward.x() > up.x()); assertTrue(up.y() > forward.y()); assertTrue(down.y() < 0);
        for (var aim : AttackDirection.values()) {
            assertEquals(-BellRules.bat(aim,1).x(),BellRules.bat(aim,-1).x());
            assertEquals(BellRules.bat(aim,1).y(),BellRules.bat(aim,-1).y());
        }
    }
}
