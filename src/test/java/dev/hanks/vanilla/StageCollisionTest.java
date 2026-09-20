package dev.hanks.vanilla;

import java.util.List;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StageCollisionTest {
    private AABB body(double x, double y) { return new AABB(x-.3,y,.25,x+.3,y+1.8,.75); }
    @Test void upwardRecoveryCannotTunnelThroughUndersideEvenAtHighSpeed() {
        var deck = Shapes.create(new AABB(-16,80,-2,17,81,4));
        var step = StageCollision.move(body(0,77), 0, 8, List.of(deck));
        assertTrue(step.ceiling()); assertFalse(step.landed());
        assertEquals(1.2,step.y(),1e-6);
    }
    @Test void diagonalRecoveryStopsAtSideAndCanRiseOutsideIt() {
        var island = Shapes.create(new AABB(-16,75,-2,17,81,4));
        for (int side : new int[]{-1,1}) {
            double x = side<0 ? -17 : 18;
            var step = StageCollision.move(body(x,77), -side*3, 1, List.of(island));
            assertTrue(step.wall()); assertFalse(step.ceiling());
            assertEquals(-side*.7,step.x(),1e-6); assertEquals(1,step.y(),1e-6);
        }
    }
    @Test void landingIsSolidButUpperPlatformsStayPassThrough() {
        var deck = Shapes.create(new AABB(-16,80,-2,17,81,4));
        var platform = Shapes.create(new AABB(-3,88,-1,4,89,3));
        var landing = StageCollision.move(body(0,82), 0,-5,List.of(deck));
        assertTrue(landing.landed()); assertEquals(-1,landing.y(),1e-6);
        var rise = StageCollision.move(body(0,86),0,2,List.of(platform));
        assertFalse(rise.ceiling()); assertEquals(2,rise.y());
    }
}
