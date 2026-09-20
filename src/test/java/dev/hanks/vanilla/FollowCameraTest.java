package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FollowCameraTest {
    @Test void ordinaryShortHopAndSmallMovementDoNotMoveTheView() {
        var camera = new FollowCamera(0,18);
        for (int i=0;i<40;i++) camera.tick(Math.sin(i)*2,82+Math.abs(Math.sin(i))*2.4);
        assertEquals(new FollowCamera.Frame(0,84,18),camera.frame());
    }
    @Test void runningAndOffstageFallsFollowWithoutSnapsOrChangingZoom() {
        var camera = new FollowCamera(0,18); var previous=camera.frame();
        for (int i=0;i<70;i++) {
            var next=camera.tick(Math.min(32,i*.5),Math.max(64,82-i*.4));
            assertTrue(next.x()>=previous.x()); assertTrue(next.x()-previous.x()<=.95);
            assertTrue(Math.abs(next.eyeY()-previous.eyeY())<=1.05); assertEquals(18,next.distance()); previous=next;
        }
        assertTrue(camera.frame().x()>24); assertTrue(camera.frame().eyeY()<69);
    }
    @Test void settleAndRespawnReturnAreSmoothAndFinite() {
        var camera = new FollowCamera(0,18);
        for(int i=0;i<50;i++)camera.tick(24,65);
        var previous=camera.frame();
        for(int i=0;i<80;i++) {
            var next=camera.tick(0,82); assertTrue(Math.abs(next.x()-previous.x())<=.951); previous=next;
        }
        double settled=camera.frame().x();
        for(int i=0;i<20;i++)camera.tick(0,82);
        assertEquals(settled,camera.frame().x(),.003);
        camera.tick(Double.NaN,Double.POSITIVE_INFINITY); assertTrue(Double.isFinite(camera.frame().x()));
    }
}
