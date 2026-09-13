package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BowRulesTest {
    @Test void vanillaDrawCurveCapsAtOneSecondAndRejectsAnInstantClick() {
        assertFalse(BowRules.canFire(0)); assertFalse(BowRules.canFire(2)); assertTrue(BowRules.canFire(3));
        assertEquals(.1075,BowRules.power(3),.000001);
        assertEquals(1.25,BowRules.speed(10),.000001);
        assertEquals(3,BowRules.speed(20)); assertEquals(3,BowRules.speed(Integer.MAX_VALUE));
        assertEquals(0,BowRules.power(-100));
    }
    @Test void shortDrawHasLessRangeThroughFlightPhysicsRatherThanAnArbitraryRangeCap() {
        double weak = landingRange(4), full = landingRange(20);
        assertTrue(weak < 5); assertTrue(full > 20); assertTrue(full > weak*4);
    }
    private static double landingRange(int ticks) {
        double x=0,y=1.52,vx=BowRules.speed(ticks),vy=0;
        while(y>0) { x+=vx; y+=vy; vx*=BowRules.DRAG; vy=vy*BowRules.DRAG-BowRules.GRAVITY; }
        return x;
    }
}
