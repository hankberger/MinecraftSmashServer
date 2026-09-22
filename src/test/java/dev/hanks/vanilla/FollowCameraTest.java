package dev.hanks.vanilla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FollowCameraTest {
    @Test void openingFramesAlreadyFitTheRosterWithoutAnIntroZoom() {
        for (var positions : java.util.List.of(
                java.util.List.of(-9.5,10.5), java.util.List.of(-14.5,14.5), java.util.List.of(-14.5,-2.5,3.5,15.5))) {
            var fighters = positions.stream().map(x -> new FollowCamera.Focus(x,82)).toList();
            for (double distance : new double[]{14,18,24,40}) {
                var camera = FollowCamera.opening(distance, fighters);
                var opening = camera.frame();
                for (var own : fighters) {
                    var opponents = fighters.stream().filter(f -> f != own).toList();
                    for (int tick=0; tick<40; tick++) assertEquals(opening,camera.tick(own.x(),own.y(),opponents));
                    assertTrue(Math.abs(own.x()-opening.x())+1.49 < opening.distance()*.82);
                }
            }
        }
    }
    @Test void exactFitAtFractionalPositionsNeverInvertsTheClampBounds() {
        var camera=new FollowCamera(0,18);
        for(int i=0;i<5000;i++) {
            double left=-14.876123+i*.00137,right=17.32345+i*.00261;
            var frame=camera.tick(left,82,java.util.List.of(new FollowCamera.Focus(right,78.13)));
            assertTrue(Double.isFinite(frame.x()));
            assertTrue(frame.distance()>=18 && frame.distance()<=26);
        }
    }
    @Test void separatedOpponentsFitWithoutReturningToTheOldWideView() {
        var camera=new FollowCamera(-12,18);
        for(int i=0;i<100;i++)camera.tick(-16,82,java.util.List.of(new FollowCamera.Focus(17,82)));
        var frame=camera.frame();
        assertTrue(frame.distance()>20 && frame.distance()<22);
        for(double x:new double[]{-16,17})assertTrue(Math.abs(x-frame.x())<frame.distance()*.82);
    }
    @Test void aerialOpponentsAndFourFightersShareTheFrame() {
        var camera=new FollowCamera(0,18);
        var others=java.util.List.of(new FollowCamera.Focus(17,82),new FollowCamera.Focus(0,94),new FollowCamera.Focus(8,86));
        for(int i=0;i<100;i++)camera.tick(-16,78,others);
        for(var f:java.util.List.of(new FollowCamera.Focus(-16,78),others.get(0),others.get(1),others.get(2))) {
            assertTrue(Math.abs(f.x()-camera.frame().x())<camera.frame().distance()*.82);
            assertTrue(Math.abs(f.y()-camera.frame().eyeY())<camera.frame().distance()*.55);
        }
        for(int i=0;i<100;i++)camera.tick(0,82,java.util.List.of(new FollowCamera.Focus(4,84)));
        assertEquals(18,camera.frame().distance(),.01);
    }
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
